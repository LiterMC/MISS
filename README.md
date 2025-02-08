# MISS

[![Modrinth Downloads](https://img.shields.io/modrinth/dt/miss?color=4&label=Downloads&logo=modrinth)](https://modrinth.com/mod/miss)
[![GitHub](https://img.shields.io/badge/GitHub-Repo-blue?logo=github)](https://github.com/LiterMC/MISS/)
[![License](https://img.shields.io/badge/License-GPL3.0-green.svg)](LICENSE)
![Minecraft Version](https://img.shields.io/badge/Minecraft-1.19%2B-blue?logo=minecraft)

MISS 是一个 Minecraft 模组，用于通过 WebSocket 转发 Minecraft 连接。这使得你可以通过 WebSocket 协议连接到 Minecraft 服务器，提供了一种新的网络传输方式。

## 功能特性

- 通过 WebSocket 协议转发 Minecraft 连接。
- 支持 Minecraft 客户端与服务器之间的双向通信。


## 前置要求

- Minecraft Java 版 1.19+
- Nginx 1.18+ 
- 有效域名与SSL证书 (可选)
- Java 17+

## 模组安装

#### 服务端

1. 从 [Modrinth](https://modrinth.com/mod/miss/versions) 下载对应版本
2. 将文件放入服务端 `mods` 目录
3. 重启 Minecraft 服务器

#### 客户端

1. 从 [Modrinth](https://modrinth.com/mod/miss/versions) 下载对应版本
2. 将文件放入客户端 `mods` 目录
3. 启动 Minecraft 客户端

## Nginx配置

反向代理服务器配置示例：

```nginx
# 假设用户访问的域名是 mc.example.com，且已申请 SSL 证书
server {
    listen 443 ssl;
    server_name mc.example.com;

    ssl_certificate /path/to/fullchain.pem;
    ssl_certificate_key /path/to/privkey.pem;

    # 该 location 其实也可以使用不同的 path 转发到不同的服务器
    location / {
        proxy_pass http://ip:25565; # 转发 Minecraft 服务器的地址（可以是域名或IP）
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "Upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

请根据实际情况修改配置文件中的域名和证书路径。

## 如何连接服务器

1. 安装客户端 MOD 后
2. 启动游戏
3. 添加服务器并在地址栏输入：
   `wss://mc.example.com`
4. 点击"加入服务器"按钮

## 注意事项

- 确保443端口已打开，且你的服务器支持 WebSocket 协议。
- 确保你的 Minecraft 服务器配置允许通过 WebSocket 连接。
- 使用此模组时，请确保你的网络环境支持 WebSocket 协议。
