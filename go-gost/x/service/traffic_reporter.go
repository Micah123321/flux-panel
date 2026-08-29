package service

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"strings"
	"sync"
	"time"

	"github.com/go-gost/core/observer/stats"
	"github.com/go-gost/x/config"
	"github.com/go-gost/x/internal/util/crypto"
	"github.com/go-gost/x/internal/util/nameshift"
	"github.com/go-gost/x/registry"
)

// reportTarget 单个主控的上报目标：流量上报、配置上报端点与该主控的加密器。
type reportTarget struct {
	ns        string
	flowURL   string
	configURL string
	aes       *crypto.AESCrypto
}

var (
	reportTargetMu sync.Mutex
	reportTargets  []*reportTarget
)

// AddReportTarget 注册一个主控上报目标（每个主控一个 ns，按服务名前缀路由）。
func AddReportTarget(addr string, secret string, ns string) {
	aes, err := crypto.NewAESCrypto(secret)
	if err != nil {
		fmt.Printf("❌ 创建 HTTP AES 加密器失败: %v\n", err)
		aes = nil
	} else {
		fmt.Printf("🔐 HTTP AES 加密器创建成功 (ns: %s)\n", ns)
	}
	t := &reportTarget{
		ns:        ns,
		flowURL:   "http://" + addr + "/flow/upload?secret=" + secret,
		configURL: "http://" + addr + "/flow/config?secret=" + secret,
		aes:       aes,
	}
	reportTargetMu.Lock()
	reportTargets = append(reportTargets, t)
	reportTargetMu.Unlock()
}

// targetForName 按服务名的命名空间段选择上报目标。
// ha-min: 主控数量个位数，线性查找足够；目标规模增长时可换 map 索引。
func targetForName(name string) *reportTarget {
	segment := nameshift.RouteSegment(name)
	reportTargetMu.Lock()
	defer reportTargetMu.Unlock()
	for _, t := range reportTargets {
		if segment == "" {
			if t.ns == "" {
				return t
			}
			continue
		}
		if t.ns != "" && t.ns[1:] == segment {
			return t
		}
	}
	return nil
}

// TrafficReportItem 流量报告项（压缩格式）
type TrafficReportItem struct {
	N string `json:"n"` // 服务名（name缩写）
	U int64  `json:"u"` // 上行流量（up缩写）
	D int64  `json:"d"` // 下行流量（down缩写）
}

// sendTrafficReport 将单服务流量报告发送到其所属主控的 HTTP 接口。无归属主控时返回错误，
// 调用方保留计数待后续重报（如主控被移除后其遗留服务会持续累积，不会误报给其他面板）。
func sendTrafficReport(ctx context.Context, reportItems TrafficReportItem) (bool, error) {
	t := targetForName(reportItems.N)
	if t == nil {
		return false, fmt.Errorf("服务 %s 无归属主控，跳过上报", reportItems.N)
	}

	jsonBytes, err := json.Marshal(reportItems)
	if err != nil {
		return false, fmt.Errorf("序列化报告数据失败: %v", err)
	}

	requestBody, err := encryptPayload(t, jsonBytes)
	if err != nil {
		return false, err
	}

	return postReport(ctx, t.flowURL, requestBody, 5*time.Second, "GOST-Traffic-Reporter/1.0")
}

// encryptPayload 使用目标主控的密钥加密载荷；无加密器时发送原始数据。
func encryptPayload(t *reportTarget, jsonData []byte) ([]byte, error) {
	if t.aes == nil {
		return jsonData, nil
	}
	encryptedData, err := t.aes.Encrypt(jsonData)
	if err != nil {
		fmt.Printf("⚠️ 加密上报数据失败，发送原始数据: %v\n", err)
		return jsonData, nil
	}
	encryptedMessage := map[string]interface{}{
		"encrypted": true,
		"data":      encryptedData,
		"timestamp": time.Now().Unix(),
	}
	wrapped, err := json.Marshal(encryptedMessage)
	if err != nil {
		fmt.Printf("⚠️ 序列化加密消息失败，发送原始数据: %v\n", err)
		return jsonData, nil
	}
	return wrapped, nil
}

// postReport 执行 HTTP POST 并校验面板应答为 ok。
func postReport(ctx context.Context, url string, requestBody []byte, timeout time.Duration, userAgent string) (bool, error) {
	req, err := http.NewRequestWithContext(ctx, "POST", url, bytes.NewBuffer(requestBody))
	if err != nil {
		return false, fmt.Errorf("创建HTTP请求失败: %v", err)
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("User-Agent", userAgent)

	client := &http.Client{Timeout: timeout}
	resp, err := client.Do(req)
	if err != nil {
		return false, fmt.Errorf("发送HTTP请求失败: %v", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return false, fmt.Errorf("HTTP响应错误: %d %s", resp.StatusCode, resp.Status)
	}

	var responseBytes bytes.Buffer
	if _, err := responseBytes.ReadFrom(resp.Body); err != nil {
		return false, fmt.Errorf("读取响应内容失败: %v", err)
	}
	responseText := strings.TrimSpace(responseBytes.String())
	if responseText == "ok" {
		return true, nil
	}
	return false, fmt.Errorf("服务器响应: %s (期望: ok)", responseText)
}

// StartConfigReporter 启动配置定时上报器（每10分钟一次）。
// 多主控下为每个主控发送其命名空间视图，面板只能看到并清理自己的配置条目。
func StartConfigReporter(ctx context.Context) {
	reportTargetMu.Lock()
	targetCount := len(reportTargets)
	reportTargetMu.Unlock()
	if targetCount == 0 {
		fmt.Printf("⚠️ 未注册任何上报目标，跳过配置定时上报\n")
		return
	}

	fmt.Printf("🚀 配置定时上报器已启动，每10分钟向 %d 个主控上报一次\n", targetCount)

	ticker := time.NewTicker(10 * time.Minute)
	defer ticker.Stop()

	// 立即执行一次配置上报
	go reportConfigToAll()

	for {
		select {
		case <-ticker.C:
			go reportConfigToAll()
		case <-ctx.Done():
			fmt.Printf("⏹️ 配置定时上报器已停止\n")
			return
		}
	}
}

func reportConfigToAll() {
	configData, err := getConfigData()
	if err != nil {
		fmt.Printf("❌ 获取配置数据失败: %v\n", err)
		return
	}

	var full map[string]interface{}
	if err := json.Unmarshal(configData, &full); err != nil {
		fmt.Printf("❌ 解析配置数据失败: %v\n", err)
		return
	}

	reportTargetMu.Lock()
	targets := make([]*reportTarget, len(reportTargets))
	copy(targets, reportTargets)
	reportTargetMu.Unlock()

	for _, t := range targets {
		view := nameshift.BuildPanelView(full, t.ns)
		viewBytes, err := json.Marshal(view)
		if err != nil {
			fmt.Printf("❌ 序列化面板视图失败 (ns: %s): %v\n", t.ns, err)
			continue
		}
		body, err := encryptPayload(t, viewBytes)
		if err != nil {
			continue
		}
		success, err := postReport(context.Background(), t.configURL, body, 10*time.Second, "Config-Reporter/1.0")
		if err != nil {
			fmt.Printf("❌ 配置上报失败 (ns: %s): %v\n", t.ns, err)
		} else if success {
			fmt.Printf("✅ 配置上报成功 (ns: %s)\n", t.ns)
		}
	}
}

// serviceStatus 接口定义
type serviceStatus interface {
	Status() *Status
}

// getConfigResponse 配置响应结构
type getConfigResponse struct {
	Config *config.Config `json:"config"`
}

// getConfigData 获取配置数据（避免循环依赖）
func getConfigData() ([]byte, error) {
	config.OnUpdate(func(c *config.Config) error {
		for _, svc := range c.Services {
			if svc == nil {
				continue
			}
			s := registry.ServiceRegistry().Get(svc.Name)
			ss, ok := s.(serviceStatus)
			if ok && ss != nil {
				status := ss.Status()
				svc.Status = &config.ServiceStatus{
					CreateTime: status.CreateTime().Unix(),
					State:      string(status.State()),
				}
				if st := status.Stats(); st != nil {
					svc.Status.Stats = &config.ServiceStats{
						TotalConns:   st.Get(stats.KindTotalConns),
						CurrentConns: st.Get(stats.KindCurrentConns),
						TotalErrs:    st.Get(stats.KindTotalErrs),
						InputBytes:   st.Get(stats.KindInputBytes),
						OutputBytes:  st.Get(stats.KindOutputBytes),
					}
				}
				for _, ev := range status.Events() {
					if !ev.Time.IsZero() {
						svc.Status.Events = append(svc.Status.Events, config.ServiceEvent{
							Time: ev.Time.Unix(),
							Msg:  ev.Message,
						})
					}
				}
			}
		}
		return nil
	})

	var resp getConfigResponse
	resp.Config = config.Global()

	buf := &bytes.Buffer{}
	resp.Config.Write(buf, "json")
	return buf.Bytes(), nil
}