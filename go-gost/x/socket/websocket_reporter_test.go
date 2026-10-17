package socket

import (
	"encoding/json"
	"strings"
	"testing"
	"time"

	"github.com/go-gost/core/logger"
	"github.com/go-gost/x/config"
	"github.com/go-gost/x/registry"
)

func TestChainCommandsDuration(t *testing.T) {
	if logger.Default() == nil {
		logger.SetDefault(logger.LoggerGroup())
	}
	for _, command := range []string{"add", "update", "update_direct"} {
		for _, value := range []struct {
			name    string
			timeout interface{}
			want    time.Duration
		}{
			{"string", "600s", 600 * time.Second},
			{"nanoseconds", int64(600 * time.Second), 600 * time.Second},
			{"precise_nanoseconds", int64(9007199254740993), time.Duration(9007199254740993)},
		} {
			t.Run(command+"/"+value.name, func(t *testing.T) {
				original := config.Global()
				config.Set(&config.Config{})
				t.Cleanup(func() { config.Set(original) })
				name := "duration-test-chain"
				t.Cleanup(func() { registry.ChainRegistry().Unregister(name) })
				reporter := &WebSocketReporter{}
				if command != "add" {
					if err := reporter.handleAddChain(map[string]interface{}{"name": name}); err != nil {
						t.Fatal(err)
					}
				}
				data := map[string]interface{}{
					"name": name,
					"hops": []interface{}{map[string]interface{}{
						"name": "hop",
						"selector": map[string]interface{}{
							"strategy": "fifo", "maxFails": 1, "failTimeout": value.timeout,
						},
					}},
				}
				var err error
				switch command {
				case "add":
					err = reporter.handleAddChain(data)
				case "update":
					err = reporter.handleUpdateChain(map[string]interface{}{"chain": name, "data": data})
				default:
					err = reporter.handleUpdateChain(data)
				}
				if err != nil {
					t.Fatalf("%s chain: %v", command, err)
				}
				if !registry.ChainRegistry().IsRegistered(name) {
					t.Fatal("chain was not registered")
				}
				chains := config.Global().Chains
				if len(chains) != 1 || chains[0].Name != name || len(chains[0].Hops) != 1 {
					t.Fatalf("unexpected stored chain: %+v", chains)
				}
				if got := chains[0].Hops[0].Selector.FailTimeout; got != value.want {
					t.Fatalf("failTimeout = %v, want %v", got, value.want)
				}
			})
		}
	}
}

func TestChainCommandsInvalidDuration(t *testing.T) {
	reporter := &WebSocketReporter{}
	data := map[string]interface{}{
		"name": "invalid-duration-chain",
		"hops": []interface{}{map[string]interface{}{
			"selector": map[string]interface{}{"failTimeout": "invalid"},
		}},
	}
	for _, command := range []string{"add", "update", "update_direct"} {
		t.Run(command, func(t *testing.T) {
			var err error
			switch command {
			case "add":
				err = reporter.handleAddChain(data)
			case "update":
				err = reporter.handleUpdateChain(map[string]interface{}{"chain": "invalid-duration-chain", "data": data})
			default:
				err = reporter.handleUpdateChain(data)
			}
			if err == nil || !strings.Contains(err.Error(), "failTimeout") {
				t.Fatalf("expected failTimeout validation error, got %v", err)
			}
			if registry.ChainRegistry().IsRegistered("invalid-duration-chain") {
				t.Fatal("invalid chain was registered")
			}
		})
	}
}

func TestPreprocessServiceDuration(t *testing.T) {
	reporter := &WebSocketReporter{}
	data := []byte(`[{"name":"service","forwarder":{"selector":{"failTimeout":"600s"}}}]`)
	processed, err := reporter.preprocessDurationFields(data)
	if err != nil {
		t.Fatal(err)
	}
	var services []config.ServiceConfig
	if err := json.Unmarshal(processed, &services); err != nil {
		t.Fatal(err)
	}
	if got := services[0].Forwarder.Selector.FailTimeout; got != 600*time.Second {
		t.Fatalf("failTimeout = %v, want 600s", got)
	}
}
