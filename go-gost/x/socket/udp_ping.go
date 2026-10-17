package socket

import (
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"net"
	"net/netip"
	"runtime"
	"strings"
	"syscall"
	"time"
)

const (
	udpReachable   = "reachable"
	udpUnreachable = "unreachable"
	udpUnknown     = "unknown"
)

type UdpPingRequest struct {
	IP        string `json:"ip"`
	Port      int    `json:"port"`
	Count     int    `json:"count"`
	Timeout   int    `json:"timeout"`
	Payload   string `json:"payload,omitempty"`
	RequestId string `json:"requestId,omitempty"`
}

type UdpPingResponse struct {
	IP              string   `json:"ip"`
	Port            int      `json:"port"`
	Success         bool     `json:"success"`
	Status          string   `json:"status"`
	AverageTime     *float64 `json:"averageTime"`
	PacketLoss      *float64 `json:"packetLoss"`
	SentPackets     int      `json:"sentPackets"`
	ReceivedPackets int      `json:"receivedPackets"`
	ErrorMessage    string   `json:"errorMessage,omitempty"`
	RequestId       string   `json:"requestId,omitempty"`
}

func (w *WebSocketReporter) handleUdpPing(data interface{}) (UdpPingResponse, error) {
	encoded, err := json.Marshal(data)
	if err != nil {
		return UdpPingResponse{}, fmt.Errorf("序列化UDP探测请求失败: %w", err)
	}
	var req UdpPingRequest
	if err := json.Unmarshal(encoded, &req); err != nil {
		return UdpPingResponse{}, fmt.Errorf("解析UDP探测请求失败: %w", err)
	}
	req.IP = strings.TrimSpace(req.IP)
	if _, err := netip.ParseAddr(req.IP); err != nil && !isValidHostname(req.IP) {
		return UdpPingResponse{}, fmt.Errorf("无效的IP地址或主机名")
	}
	if req.Port < 1 || req.Port > 65535 {
		return UdpPingResponse{}, fmt.Errorf("无效的端口号，范围应为1-65535")
	}
	if req.Count == 0 {
		req.Count = 2
	}
	if req.Timeout == 0 {
		req.Timeout = 1500
	}
	if req.Count < 1 || req.Count > 4 || req.Timeout < 1 || req.Timeout > 3000 {
		return UdpPingResponse{}, fmt.Errorf("UDP探测次数范围为1-4，超时范围为1-3000毫秒")
	}
	hexPayload := strings.Join(strings.Fields(req.Payload), "")
	if len(hexPayload) > 2048 {
		return UdpPingResponse{}, fmt.Errorf("UDP探测数据最多1024字节")
	}
	payload := []byte("flux-panel-udp-probe")
	if hexPayload != "" {
		payload, err = hex.DecodeString(hexPayload)
		if err != nil {
			return UdpPingResponse{}, fmt.Errorf("UDP探测数据应为成对的十六进制字符")
		}
	}
	return udpPingHost(req, payload), nil
}

func udpPingHost(req UdpPingRequest, payload []byte) UdpPingResponse {
	result := UdpPingResponse{
		IP: req.IP, Port: req.Port, Status: udpUnknown, RequestId: req.RequestId,
	}
	timeout := time.Duration(req.Timeout) * time.Millisecond
	address := net.JoinHostPort(req.IP, fmt.Sprint(req.Port))
	var totalTime time.Duration
	var probeErr error
	buffer := make([]byte, 65535)
	for i := 0; i < req.Count; i++ {
		elapsed, sent, err := udpPingAttempt(address, payload, buffer, timeout)
		if sent {
			result.SentPackets++
		}
		if err != nil {
			var netErr net.Error
			if errors.As(err, &netErr) && netErr.Timeout() {
				continue
			}
			probeErr = err
			break
		}
		result.ReceivedPackets++
		totalTime += elapsed
	}

	if result.ReceivedPackets > 0 {
		result.Success = true
		result.Status = udpReachable
		average := float64(totalTime) / float64(time.Millisecond) / float64(result.ReceivedPackets)
		unanswered := 100 * float64(result.SentPackets-result.ReceivedPackets) / float64(result.SentPackets)
		result.AverageTime = &average
		result.PacketLoss = &unanswered
	} else if isUDPUnreachable(probeErr) {
		result.Status = udpUnreachable
		result.ErrorMessage = fmt.Sprintf("网络返回UDP端口或路径不可达: %v", probeErr)
	} else if probeErr != nil {
		result.ErrorMessage = fmt.Sprintf("UDP探测未完成: %v", probeErr)
	} else {
		result.ErrorMessage = "已发送UDP探测数据，未收到回包；目标可能静默或仅响应特定协议，尚未确认通断"
	}
	return result
}

func udpPingAttempt(address string, payload, buffer []byte, timeout time.Duration) (time.Duration, bool, error) {
	// Separate connected sockets keep late replies from affecting a later attempt.
	conn, err := net.DialTimeout("udp", address, timeout)
	if err != nil {
		return 0, false, err
	}
	defer conn.Close()
	if err := conn.SetDeadline(time.Now().Add(timeout)); err != nil {
		return 0, false, err
	}
	start := time.Now()
	if _, err := conn.Write(payload); err != nil {
		return 0, false, err
	}
	// The connected socket filters out datagrams from other peers.
	_, err = conn.Read(buffer)
	return time.Since(start), true, err
}

func isUDPUnreachable(err error) bool {
	// Windows reports ICMP port-unreachable on connected UDP as WSAECONNRESET.
	return errors.Is(err, syscall.ECONNREFUSED) ||
		(runtime.GOOS == "windows" && errors.Is(err, syscall.ECONNRESET)) ||
		errors.Is(err, syscall.EHOSTUNREACH) || errors.Is(err, syscall.ENETUNREACH)
}
