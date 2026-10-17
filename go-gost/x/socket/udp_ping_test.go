package socket

import (
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"net"
	"net/http"
	"net/http/httptest"
	"os"
	"runtime"
	"strings"
	"syscall"
	"testing"
	"time"

	"github.com/gorilla/websocket"
)

func udpTestServer(t *testing.T, network string, echo bool) (*net.UDPAddr, <-chan []byte) {
	t.Helper()
	host := "127.0.0.1:0"
	if network == "udp6" {
		host = "[::1]:0"
	}
	addr, err := net.ResolveUDPAddr(network, host)
	if err != nil {
		t.Fatal(err)
	}
	conn, err := net.ListenUDP(network, addr)
	if err != nil {
		if network == "udp6" {
			t.Skipf("IPv6 loopback is unavailable: %v", err)
		}
		t.Fatal(err)
	}
	packets := make(chan []byte, 8)
	done := make(chan struct{})
	go func() {
		defer close(done)
		buffer := make([]byte, 2048)
		for {
			n, peer, err := conn.ReadFromUDP(buffer)
			if err != nil {
				return
			}
			select {
			case packets <- append([]byte(nil), buffer[:n]...):
			default:
			}
			if echo {
				conn.WriteToUDP(buffer[:n], peer)
			}
		}
	}()
	t.Cleanup(func() {
		conn.Close()
		<-done
	})
	return conn.LocalAddr().(*net.UDPAddr), packets
}

func TestUdpPingReply(t *testing.T) {
	for _, network := range []string{"udp4", "udp6"} {
		t.Run(network, func(t *testing.T) {
			addr, packets := udpTestServer(t, network, true)
			response, err := (&WebSocketReporter{}).handleUdpPing(map[string]interface{}{
				"ip": addr.IP.String(), "port": addr.Port, "count": 2, "timeout": 500,
				"payload": "00 ff 61 62", "requestId": "udp-reply",
			})
			if err != nil {
				t.Fatal(err)
			}
			if !response.Success || response.Status != udpReachable || response.SentPackets != 2 || response.ReceivedPackets != 2 {
				t.Fatalf("unexpected response: %+v", response)
			}
			if response.AverageTime == nil || *response.AverageTime < 0 || response.PacketLoss == nil || *response.PacketLoss != 0 {
				t.Fatalf("unexpected timings: %+v", response)
			}
			if response.RequestId != "udp-reply" {
				t.Fatal("request ID was lost")
			}
			for i := 0; i < 2; i++ {
				select {
				case packet := <-packets:
					if !bytes.Equal(packet, []byte{0, 255, 97, 98}) {
						t.Fatalf("wrong probe bytes: %x", packet)
					}
				case <-time.After(time.Second):
					t.Fatal("probe packet was not received")
				}
			}
		})
	}
}

func TestUdpPingSilentServiceIsUnknown(t *testing.T) {
	addr, packets := udpTestServer(t, "udp4", false)
	response, err := (&WebSocketReporter{}).handleUdpPing(map[string]interface{}{
		"ip": addr.IP.String(), "port": addr.Port, "count": 2, "timeout": 30,
	})
	if err != nil {
		t.Fatal(err)
	}
	if response.Status != udpUnknown || response.Success || response.SentPackets != 2 || response.ReceivedPackets != 0 {
		t.Fatalf("silence must remain inconclusive: %+v", response)
	}
	if response.AverageTime != nil || response.PacketLoss != nil || response.ErrorMessage == "" {
		t.Fatalf("unknown results must not report fabricated loss or timings: %+v", response)
	}
	select {
	case packet := <-packets:
		if string(packet) != "flux-panel-udp-probe" {
			t.Fatalf("unexpected default probe: %q", packet)
		}
	case <-time.After(time.Second):
		t.Fatal("default probe was not sent")
	}
}

func TestUdpPingClosedPort(t *testing.T) {
	conn, err := net.ListenPacket("udp4", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	addr := conn.LocalAddr().(*net.UDPAddr)
	conn.Close()
	response, err := (&WebSocketReporter{}).handleUdpPing(map[string]interface{}{
		"ip": addr.IP.String(), "port": addr.Port, "count": 1, "timeout": 100,
	})
	if err != nil {
		t.Fatal(err)
	}
	// Some operating systems suppress ICMP errors; silence must stay unknown.
	if response.Success || (response.Status != udpUnreachable && response.Status != udpUnknown) || response.ErrorMessage == "" {
		t.Fatalf("closed port cannot be reported reachable: %+v", response)
	}
	t.Logf("closed UDP port status: %s", response.Status)
}

func TestUdpPingValidation(t *testing.T) {
	for name, changes := range map[string]map[string]interface{}{
		"host":             {"ip": "bad host"},
		"port_zero":        {"port": 0},
		"port_high":        {"port": 65536},
		"count":            {"count": 5},
		"negative_count":   {"count": -1},
		"timeout":          {"timeout": 3001},
		"negative_timeout": {"timeout": -1},
		"hex":              {"payload": "zz"},
		"odd_hex":          {"payload": "a"},
		"long_payload":     {"payload": strings.Repeat("aa", 1025)},
		"fractional_port":  {"port": 1.5},
	} {
		t.Run(name, func(t *testing.T) {
			data := map[string]interface{}{"ip": "127.0.0.1", "port": 9, "count": 1, "timeout": 1}
			for key, value := range changes {
				data[key] = value
			}
			if _, err := (&WebSocketReporter{}).handleUdpPing(data); err == nil {
				t.Fatal("invalid request was accepted")
			}
		})
	}
}

func TestUdpUnreachableClassification(t *testing.T) {
	for _, errno := range []syscall.Errno{syscall.ECONNREFUSED, syscall.EHOSTUNREACH, syscall.ENETUNREACH} {
		if !isUDPUnreachable(&net.OpError{Op: "read", Net: "udp", Err: fmt.Errorf("wrapped: %w", errno)}) {
			t.Fatalf("explicit network error was not recognized: %v", errno)
		}
	}
	if isUDPUnreachable(nil) || isUDPUnreachable(os.ErrDeadlineExceeded) || isUDPUnreachable(net.ErrClosed) || isUDPUnreachable(errors.New("unknown error")) {
		t.Fatal("a local error or timeout was classified as unreachable")
	}
	if isUDPUnreachable(syscall.ECONNRESET) != (runtime.GOOS == "windows") {
		t.Fatal("connection reset classification must follow Windows UDP semantics")
	}
}

func TestUdpPingCommandResponse(t *testing.T) {
	addr, _ := udpTestServer(t, "udp4", true)
	originalDir, err := os.Getwd()
	if err != nil {
		t.Fatal(err)
	}
	if err := os.Chdir(t.TempDir()); err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { os.Chdir(originalDir) })
	responses := make(chan CommandResponse, 1)
	upgrader := websocket.Upgrader{}
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		conn, err := upgrader.Upgrade(w, r, nil)
		if err != nil {
			return
		}
		defer conn.Close()
		conn.SetReadDeadline(time.Now().Add(3 * time.Second))
		var response CommandResponse
		if err := conn.ReadJSON(&response); err == nil {
			responses <- response
		}
	}))
	defer server.Close()
	conn, _, err := websocket.DefaultDialer.Dial("ws"+strings.TrimPrefix(server.URL, "http"), nil)
	if err != nil {
		t.Fatal(err)
	}
	defer conn.Close()
	reporter := &WebSocketReporter{conn: conn, connected: true, ns: "p2"}
	reporter.routeCommand(CommandMessage{
		Type: "UdpPing", RequestId: "udp-command",
		Data: map[string]interface{}{"ip": addr.IP.String(), "port": addr.Port, "count": 1, "timeout": 500},
	})
	select {
	case response := <-responses:
		if response.Type != "UdpPingResponse" || response.RequestId != "udp-command" || !response.Success || response.Message != "OK" {
			t.Fatalf("unexpected command response: %+v", response)
		}
		encoded, err := json.Marshal(response.Data)
		if err != nil {
			t.Fatal(err)
		}
		var result UdpPingResponse
		if err := json.Unmarshal(encoded, &result); err != nil {
			t.Fatal(err)
		}
		if result.Status != udpReachable || result.ReceivedPackets != 1 || result.RequestId != response.RequestId {
			t.Fatalf("probe result or correlation ID missing from command: %+v", result)
		}
	case <-time.After(3 * time.Second):
		t.Fatal("node did not send a command response")
	}
	if _, err := os.Stat("gost.json"); !os.IsNotExist(err) {
		t.Fatalf("diagnosis unexpectedly wrote the node configuration: %v", err)
	}
}
