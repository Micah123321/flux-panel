package nameshift

import (
	"encoding/json"
	"testing"
)

func TestRouteSegment(t *testing.T) {
	cases := []struct {
		name string
		want string
	}{
		{"5_3_0_tcp", ""},
		{"agf_7_1200", ""},
		{"web_api", ""},
		{"p2_5_3_0_tcp", "2"},
		{"p10_agf_7_1200", "10"},
		{"pause_1_2_3", ""},
		{"p_5", ""},
		{"p0_5", ""},
		{"p01_5", ""},
		{"px_5", ""},
		{"port_x", ""},
	}
	for _, c := range cases {
		if got := RouteSegment(c.name); got != c.want {
			t.Errorf("RouteSegment(%q) = %q, want %q", c.name, got, c.want)
		}
	}
}

func TestPrefixStripRoundTrip(t *testing.T) {
	cases := []string{
		"5_3_0_tcp",
		"agf_7_1200",
		"web_api",
		"1",
	}
	for _, n := range cases {
		p := Prefix("p2", n)
		if s := Strip("p2", p); s != n {
			t.Errorf("round trip failed: %q -> %q -> %q", n, p, s)
		}
	}
	if got := Prefix("", "5_3_0_tcp"); got != "5_3_0_tcp" {
		t.Errorf("empty ns must not prefix, got %q", got)
	}
	if got := Prefix("p2", "p3_5_1_0_tcp"); got != "p3_5_1_0_tcp" {
		t.Errorf("already namespaced name must be kept, got %q", got)
	}
}

func TestPrefixPayload(t *testing.T) {
	const payload = `{"type":"DeleteService","data":{"services":["5_3_0_tcp","web_api"]},` +
		`"chain":{"name":"7_8_9_chains","limiter":"4","hop":{"name":"hop-7_8_9_chains"}}}`
	var data map[string]interface{}
	if err := json.Unmarshal([]byte(payload), &data); err != nil {
		t.Fatal(err)
	}
	out := PrefixPayload("p2", interface{}(data)).(map[string]interface{})
	services := out["data"].(map[string]interface{})["services"].([]interface{})
	if services[0] != "p2_5_3_0_tcp" || services[1] != "web_api" {
		t.Errorf("services translation wrong: %v", services)
	}
	chain := out["chain"].(map[string]interface{})
	if chain["name"] != "p2_7_8_9_chains" || chain["limiter"] != "p2_4" {
		t.Errorf("chain translation wrong: %v", chain)
	}
	hopName := chain["hop"].(map[string]interface{})["name"].(string)
	if hopName != "p2_hop-7_8_9_chains" {
		t.Errorf("nested name translation wrong: %q", hopName)
	}
	// idempotent: second pass keeps prefix
	out2 := PrefixPayload("p2", interface{}(out)).(map[string]interface{})
	services2 := out2["data"].(map[string]interface{})["services"].([]interface{})
	if services2[0] != "p2_5_3_0_tcp" {
		t.Errorf("translation must be idempotent, got %v", services2)
	}
}

func TestBuildPanelView(t *testing.T) {
	const cfgJSON = `{"services":[{"name":"5_3_0_tcp"},{"name":"p2_9_1_0_tcp"},{"name":"web_api"}],` +
		`"limiters":[{"name":"4"},{"name":"p2_8"}],"other":"x"}`
	var cfg map[string]interface{}
	if err := json.Unmarshal([]byte(cfgJSON), &cfg); err != nil {
		t.Fatal(err)
	}

	viewRoot := BuildPanelView(cfg, "")
	svcs := viewRoot["services"].([]interface{})
	if len(svcs) != 2 {
		t.Errorf("root view must keep unnamed + non-namespaced, got %d", len(svcs))
	}
	for _, s := range svcs {
		n := s.(map[string]interface{})["name"].(string)
		if Namespaced(n) {
			t.Errorf("root view must exclude namespaced entries, got %q", n)
		}
	}

	viewP2 := BuildPanelView(cfg, "p2")
	svcs2 := viewP2["services"].([]interface{})
	if len(svcs2) != 1 {
		t.Fatalf("p2 view must keep exactly its own entry, got %d", len(svcs2))
	}
	n2 := svcs2[0].(map[string]interface{})["name"].(string)
	if n2 != "9_1_0_tcp" {
		t.Errorf("p2 view name must be stripped, got %q", n2)
	}
	lim2 := viewP2["limiters"].([]interface{})
	if len(lim2) != 1 || lim2[0].(map[string]interface{})["name"] != "8" {
		t.Errorf("p2 limiters view wrong: %v", lim2)
	}
	if viewP2["other"] != "x" {
		t.Errorf("scalar keys must be preserved, got %v", viewP2["other"])
	}
}