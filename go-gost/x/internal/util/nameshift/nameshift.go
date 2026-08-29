package nameshift

import (
	"strings"
)

// ReservedPrefix 节点内部保留前缀，永不参与命名空间翻译。
const ReservedPrefix = "web_api"

// AggregatePrefix 聚合转发服务名前缀，按普通名称参与翻译与路由。
const AggregatePrefix = "agf_"

// Namespaced 判断名称是否已带任何 pN 命名空间前缀。
func Namespaced(name string) bool {
	return RouteSegment(name) != ""
}

// RouteSegment 返回名称所属的命名空间段（如 p2 返回 "2"），无命名空间返回空串。
// 合法形式：p<正整数>_，其余一律视为无命名空间（含旧版无前缀主控与 agf_）。
func RouteSegment(name string) string {
	if !strings.HasPrefix(name, "p") {
		return ""
	}
	rest := name[len("p"):]
	us := strings.Index(rest, "_")
	if us <= 0 {
		return ""
	}
	num := rest[:us]
	for i := 0; i < len(num); i++ {
		if num[i] < '0' || num[i] > '9' {
			return ""
		}
	}
	if num[0] == '0' {
		return ""
	}
	return num
}

// Prefix 为单个名称加命名空间前缀。web_api 保留名、已带前缀的名称原样返回。
func Prefix(ns, name string) string {
	if ns == "" || name == ReservedPrefix || Namespaced(name) {
		return name
	}
	return ns + "_" + name
}

// Strip 去掉指定命名空间前缀；名称不属于该命名空间时原样返回。
func Strip(ns, name string) string {
	if ns == "" {
		return name
	}
	if RouteSegment(name) == ns[1:] {
		// name = ns + "_" + rest
		return name[len(ns)+1:]
	}
	return name
}

// PrefixNames 对批量名称加前缀。
func PrefixNames(ns string, names []string) []string {
	out := make([]string, len(names))
	for i, n := range names {
		out[i] = Prefix(ns, n)
	}
	return out
}

// StripNames 对批量名称去前缀。
func StripNames(ns string, names []string) []string {
	out := make([]string, len(names))
	for i, n := range names {
		out[i] = Strip(ns, n)
	}
	return out
}

// PrefixPayload 对命令载荷递归翻译：map 中 name/limiter/chain/services 键指向的名称
// 统一加 ns 前缀；嵌套数组（如 services 名称列表、hops/nodes 结构）同样处理。逐字段的
// 已加前缀判断保证重复翻译幂等。ha-min: 递归深度等于载荷 JSON 深度，实际配置约 10 层以内。
func PrefixPayload(ns string, data interface{}) interface{} {
	if ns == "" {
		return data
	}
	return prefixValue(ns, data)
}

func prefixValue(ns string, v interface{}) interface{} {
	switch t := v.(type) {
	case map[string]interface{}:
		out := make(map[string]interface{}, len(t))
		for k, val := range t {
			switch k {
			case "name", "limiter", "chain":
				if s, ok := val.(string); ok {
					out[k] = Prefix(ns, s)
					continue
				}
			case "services":
				if arr, ok := val.([]interface{}); ok {
					out[k] = prefixStringSlice(ns, arr)
					continue
				}
			}
			out[k] = prefixValue(ns, val)
		}
		return out
	case []interface{}:
		out := make([]interface{}, len(t))
		for i, val := range t {
			out[i] = prefixValue(ns, val)
		}
		return out
	default:
		return v
	}
}

func prefixStringSlice(ns string, arr []interface{}) []interface{} {
	out := make([]interface{}, len(arr))
	for i, val := range arr {
		if s, ok := val.(string); ok {
			out[i] = Prefix(ns, s)
		} else {
			out[i] = val
		}
	}
	return out
}

// BuildPanelView 生成某一面板的节点配置视图：保留无命名空间条目（当 ns 为空）或
// 属于该 ns 的条目（顶层 name 先 Strip 再判断），其余全部过滤。用于配置上报对账隔离。
func BuildPanelView(cfg map[string]interface{}, ns string) map[string]interface{} {
	if cfg == nil {
		return nil
	}
	view := make(map[string]interface{}, len(cfg))
	for collectionKey := range cfg {
		arr, ok := cfg[collectionKey].([]interface{})
		if !ok {
			view[collectionKey] = cfg[collectionKey]
			continue
		}
		kept := make([]interface{}, 0, len(arr))
		for _, item := range arr {
			m, ok := item.(map[string]interface{})
			if !ok {
				kept = append(kept, item)
				continue
			}
			name, _ := m["name"].(string)
			if ns == "" {
				if !Namespaced(name) {
					kept = append(kept, item)
				}
				continue
			}
			if RouteSegment(name) == ns[1:] {
				clone := make(map[string]interface{}, len(m))
				for k, v := range m {
					clone[k] = v
				}
				clone["name"] = Strip(ns, name)
				kept = append(kept, clone)
			}
		}
		view[collectionKey] = kept
	}
	return view
}