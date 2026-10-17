package socket

import (
	"crypto/tls"
	"net"
	"testing"
	"time"

	"github.com/go-gost/core/logger"
	"github.com/go-gost/core/service"
	"github.com/go-gost/x/config"
	"github.com/go-gost/x/config/parsing"
	serviceparser "github.com/go-gost/x/config/parsing/service"
	_ "github.com/go-gost/x/connector/http"
	_ "github.com/go-gost/x/connector/relay"
	_ "github.com/go-gost/x/dialer/tcp"
	_ "github.com/go-gost/x/handler/forward/local"
	_ "github.com/go-gost/x/handler/relay"
	_ "github.com/go-gost/x/listener/tcp"
	_ "github.com/go-gost/x/listener/udp"
	"github.com/go-gost/x/registry"
)

func startUDPForwardTestService(t *testing.T, cfg *config.ServiceConfig) service.Service {
	t.Helper()
	svc, err := serviceparser.ParseService(cfg)
	if err != nil {
		t.Fatal(err)
	}
	done := make(chan struct{})
	go func() {
		defer close(done)
		svc.Serve()
	}()
	t.Cleanup(func() {
		svc.Close()
		select {
		case <-done:
		case <-time.After(2 * time.Second):
			t.Error("forward service did not stop")
		}
	})
	return svc
}

func TestUdpPingThroughForwarding(t *testing.T) {
	if logger.Default() == nil {
		logger.SetDefault(logger.LoggerGroup())
	}
	if parsing.DefaultTLSConfig() == nil {
		parsing.SetDefaultTLSConfig(&tls.Config{})
	}
	for _, mode := range []string{"port", "tunnel"} {
		t.Run(mode, func(t *testing.T) {
			target, _ := udpTestServer(t, "udp4", true)
			forwarder := &config.ForwarderConfig{Nodes: []*config.ForwardNodeConfig{{Name: "target", Addr: target.String()}}}
			entry := &config.ServiceConfig{
				Name: "udp-probe-entry", Addr: "127.0.0.1:0",
				Listener:  &config.ListenerConfig{Type: "udp", Metadata: map[string]interface{}{"keepAlive": true, "ttl": "100ms"}},
				Handler:   &config.HandlerConfig{Type: "udp"},
				Forwarder: forwarder,
			}
			if mode == "tunnel" {
				exit := startUDPForwardTestService(t, &config.ServiceConfig{
					Name: "udp-probe-exit", Addr: "127.0.0.1:0",
					Listener:  &config.ListenerConfig{Type: "tcp"},
					Handler:   &config.HandlerConfig{Type: "relay"},
					Forwarder: forwarder,
				})
				original := config.Global()
				config.Set(&config.Config{})
				t.Cleanup(func() { config.Set(original) })
				chainName := "udp-probe-test-chain"
				t.Cleanup(func() { registry.ChainRegistry().Unregister(chainName) })
				if err := (&WebSocketReporter{}).handleAddChain(map[string]interface{}{
					"name": chainName,
					"hops": []interface{}{map[string]interface{}{
						"name":     "relay-hop",
						"selector": map[string]interface{}{"strategy": "fifo", "maxFails": 1, "failTimeout": "600s"},
						"nodes": []interface{}{map[string]interface{}{
							"name": "relay-node", "addr": exit.Addr().String(),
							"connector": map[string]interface{}{"type": "relay"},
							"dialer":    map[string]interface{}{"type": "tcp"},
						}},
					}},
				}); err != nil {
					t.Fatal(err)
				}
				entry.Handler.Chain = chainName
				entry.Forwarder = nil
			}
			svc := startUDPForwardTestService(t, entry)
			addr := svc.Addr().(*net.UDPAddr)
			result, err := (&WebSocketReporter{}).handleUdpPing(map[string]interface{}{
				"ip": addr.IP.String(), "port": addr.Port, "count": 2, "timeout": 1000, "payload": "00ff1234",
			})
			if err != nil {
				t.Fatal(err)
			}
			if result.Status != udpReachable || result.ReceivedPackets != 2 {
				t.Fatalf("UDP probe through %s forwarding failed: %+v", mode, result)
			}
		})
	}
}
