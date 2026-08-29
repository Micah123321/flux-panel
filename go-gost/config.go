package main

import (
	"encoding/json"
	"fmt"
	"os"
)

// ServerConfig 单个主控的接入配置。
// Ns 为该主控的命名空间前缀（形如 p2、p3），首个主控为空字符串（服务名不加前缀）。
type ServerConfig struct {
	Addr   string `json:"addr"`
	Secret string `json:"secret"`
	Ns     string `json:"ns,omitempty"`
}

// Config 节点配置。
// 新格式使用 Servers 数组支持多主控；顶层 Addr/Secret/Http/Tls/Socks 保留：
// Http/Tls/Socks 为协议屏蔽全局开关；顶层 Addr/Secret 为首主控镜像，保证旧版二进制可读。
type Config struct {
	Addr    string         `json:"addr"`
	Secret  string         `json:"secret"`
	Http    int            `json:"http"`
	Tls     int            `json:"tls"`
	Socks   int            `json:"socks"`
	Servers []ServerConfig `json:"servers,omitempty"`
}

// LoadConfig 加载配置文件，旧版单主控格式自动迁移为 Servers 数组。
func LoadConfig(configPath string) (*Config, error) {
	if _, err := os.Stat(configPath); os.IsNotExist(err) {
		return nil, fmt.Errorf("配置文件不存在: %s", configPath)
	}

	data, err := os.ReadFile(configPath)
	if err != nil {
		return nil, fmt.Errorf("读取配置文件失败: %v", err)
	}

	var config Config
	if err := json.Unmarshal(data, &config); err != nil {
		return nil, fmt.Errorf("解析配置文件失败: %v", err)
	}

	if err := config.normalize(); err != nil {
		return nil, err
	}
	return &config, nil
}

// normalize 将旧格式迁移为多主控格式并校验。
func (c *Config) normalize() error {
	if len(c.Servers) == 0 {
		if c.Addr == "" {
			return fmt.Errorf("服务器地址不能为空")
		}
		c.Servers = []ServerConfig{{Addr: c.Addr, Secret: c.Secret}}
	}
	seen := make(map[string]bool, len(c.Servers))
	for i := range c.Servers {
		s := &c.Servers[i]
		if s.Addr == "" {
			return fmt.Errorf("主控[%d] 服务器地址不能为空", i+1)
		}
		if s.Secret == "" {
			return fmt.Errorf("主控[%d] %s 密钥不能为空", i+1, s.Addr)
		}
		if s.Ns != "" && seen[s.Ns] {
			return fmt.Errorf("主控命名空间 %s 重复", s.Ns)
		}
		seen[s.Ns] = true
	}
	// 首主控镜像到顶层，保证旧版 gost 二进制仍可读取
	c.Addr = c.Servers[0].Addr
	c.Secret = c.Servers[0].Secret
	return nil
}