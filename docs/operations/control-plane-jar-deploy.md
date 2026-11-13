# 控制面 JAR 部署注意

`forgepilot-server` 使用 Spring Boot 可执行 fat jar。运行中的 JVM 会打开该文件；若用 `scp`/`cp` **原地覆盖**正在使用的 jar，常见后果是：

- 嵌套依赖（如 `BOOT-INF/lib/logback-*.jar`）读取失败
- 请求出错时日志链路抛出 `NoClassDefFoundError: ch/qos/logback/classic/spi/ThrowableProxy`
- 客户端表现为接口「测试 / 删除」一直转圈或空响应，真实业务异常被吞掉

## 正确步骤

1. 先停止控制面进程（只停 ForgePilot 自己的 server，不要动同机其他应用）
2. 将新 jar 上传到临时路径（例如 `forgepilot-server-0.1.0-SNAPSHOT.jar.new`）
3. `mv` 原子替换到 `forgepilot-server/target/forgepilot-server-0.1.0-SNAPSHOT.jar`
4. 校验：`jar tf … | wc -l` 应有数百条以上，且能从 fat jar 抽出 `logback-classic` 并看到 `ThrowableProxy.class`
5. 再 `source` 环境变量后启动

不要在进程仍持有旧 inode 时原地截断写入同一路径。


## 强制使用 IPv4

若主机没有可用的全局 IPv6 路由，JVM 仍可能优先解析并连接 IPv6，表现为模型调用 `ConnectException`。控制面（以及同样要访问公网模型服务的执行面）应同时保留下面两处标志，避免下一次只改 jar 路径的 unit 重写把参数丢掉：

1. 环境文件（例如 `env/test.env`）中的 `JAVA_TOOL_OPTIONS=-Djava.net.preferIPv4Stack=true`
2. 启动命令或 systemd `ExecStart` 中的 `-Djava.net.preferIPv4Stack=true`

systemd 示例（路径按实际部署调整，不要在仓库里写入公网 IP）：

```
[Service]
EnvironmentFile=/opt/forgepilot/env/test.env
Environment=JAVA_TOOL_OPTIONS=-Djava.net.preferIPv4Stack=true
ExecStart=/usr/bin/java -Djava.net.preferIPv4Stack=true -Xms128m -Xmx256m -jar /opt/forgepilot/forgepilot-server/target/forgepilot-server-0.1.0-SNAPSHOT.jar
```

更换 jar 时先停进程，再原子替换文件，并确认 `ExecStart` 与 `JAVA_TOOL_OPTIONS` 仍然带有 IPv4 标志后再启动。
