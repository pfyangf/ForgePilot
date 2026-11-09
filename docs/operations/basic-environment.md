# ForgePilot 基础环境部署说明

**适用版本：** V1.2（`dev` 分支，提交 `276cc953` 及之后）  
**适用场景：** 单台云服务器上的功能验收，不是生产部署。  
**参考机型：** 2 核 CPU / 2 GB 内存 / 3 Mbps 公网带宽

本文说明如何在一台小规格云主机上把控制面、执行面和工作台跑起来，并完成登录、工作台、项目、成员、需求与任务等软件侧验收。真实 Codex 编码不在这台机器的能力范围内。

## 1. 这台机器够不够

| 目标 | 2 核 2G / 3 Mbps 够不够 | 说明 |
| --- | --- | --- |
| 登录、工作台、项目、成员、需求、任务、目录、规约、操作记录 | **够，但必须用精简模式** | 只起 PostgreSQL 和 Redis，关掉 RabbitMQ 与 MinIO，限制 JVM 内存，并加 2 GB swap |
| 同时拉起 Compose 里全部四个中间件 | **不够** | RabbitMQ 管理镜像和 MinIO 各自还要一百多 MB，两台 JVM 再加上它们很容易把 2 GB 吃光 |
| 在本机 `npm run dev` 做日常开发 | **勉强，不推荐** | Vite 开发服务器本身就要占不少内存 |
| 真实编码（安装 `codex`、拉仓库、起容器、跑 Agent） | **不够** | 编码容器和工作区会再占一层内存和磁盘，2 GB 会频繁 OOM |
| 带宽 | **一个人点页面够用** | 首次拉镜像、Maven 和 npm 依赖会慢，按 3 Mbps 估算，首次准备大约 30 到 50 分钟 |

结论：用这台机器做**软件功能验收**可以；不要把 RabbitMQ、MinIO、真实 Codex 和 Docker 编码沙箱也堆上去。若要验证真实编码，另准备至少 4 核 8G、磁盘 40 GB 以上的执行节点。

建议内存分配（精简模式，大约 1.3 到 1.6 GB，剩下的留给系统和 swap）：

| 进程 | 建议占用 |
| --- | --- |
| 操作系统 + Docker | 约 400 到 600 MB |
| PostgreSQL 16（Alpine） | 约 80 MB |
| Redis 7（Alpine） | 约 20 MB |
| `forgepilot-server`（`-Xmx256m`） | 约 300 MB |
| `forgepilot-execution`（`-Xmx256m`） | 约 280 MB |
| Nginx 托管前端静态文件 | 约 10 MB |

系统盘建议不少于 20 GB。首次构建会下载 Docker 镜像、Maven 依赖和 npm 依赖。

## 2. 精简模式开什么、关什么

仓库里的 `docker-compose.yml` 只提供基础设施，控制面和执行面要在宿主机用 Java 启动。2G 机器只启动其中两个服务：

```bash
docker compose up -d postgres redis
```

不要启动 `rabbitmq` 和 `minio`。控制面默认已经：

- `FORGEPILOT_RABBIT_ENABLED=false`：控制事件走内存假 broker，Outbox 仍可工作
- `FORGEPILOT_MINIO_ENABLED=false`：日志归档走内存存储，不影响登录和工作台

这两个默认值正好适合这台测试机。

公网机器上，把数据库和 Redis 绑到本机回环地址，避免 `5432`、`6379` 暴露到公网。修改 `docker-compose.yml` 的端口：

```yaml
services:
  postgres:
    ports:
      - "127.0.0.1:5432:5432"
  redis:
    ports:
      - "127.0.0.1:6379:6379"
```

安全组或防火墙只放行：

- `22`：SSH
- `80`：工作台（Nginx）

不要对公网开放 `5432`、`6379`、`8080`、`8081`、`5672`、`9000`、`9001`。`8081` 是执行面，只应被本机控制面访问。

演示账号口令是公开的 `ForgePilot!dev`。如果这台机器有公网 IP，务必用安全组限制来源 IP，或部署后立刻改口令。本文的中间件口令同样只适合单人测试，不要用于共享环境。

## 3. 系统准备

以 Ubuntu 22.04 / 24.04 为例。

### 3.1 加 2 GB swap

没有 swap 时，Maven、npm 或 Spring 启动都可能直接被系统杀掉。

```bash
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
free -h
```

### 3.2 安装依赖

```bash
sudo apt-get update
sudo apt-get install -y git curl ca-certificates openjdk-17-jdk maven nginx docker.io docker-compose-v2
sudo systemctl enable --now docker
sudo usermod -aG docker "$USER"
```

重新登录一次，使 `docker` 组生效。检查：

```bash
java -version          # 需要 17 或更高
mvn -version
docker compose version
node -v || true
```

前端构建需要 Node.js 20 或更高：

```bash
curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
sudo apt-get install -y nodejs
node -v
```

### 3.3 取代码

```bash
sudo mkdir -p /opt/forgepilot
sudo chown "$USER":"$USER" /opt/forgepilot
git clone -b dev https://github.com/pfyangf/ForgePilot.git /opt/forgepilot
cd /opt/forgepilot
```

## 4. 启动基础设施

先按第 2 节改好 Compose 端口绑定，再启动：

```bash
cd /opt/forgepilot
docker compose up -d postgres redis
docker compose ps
docker compose logs --tail=20 postgres redis
```

等到 `pg_isready` 和 `redis-cli ping` 健康后再继续。

## 5. 构建

2G 机器上构建会走 swap，请耐心等待，不要并行跑 `mvn` 和 `npm`。

```bash
cd /opt/forgepilot
export MAVEN_OPTS="-Xms128m -Xmx512m"
mvn -DskipTests package

export NODE_OPTIONS="--max-old-space-size=768"
npm ci --prefix forgepilot-web
npm run build --prefix forgepilot-web
```

产物：

- `forgepilot-server/target/forgepilot-server-0.1.0-SNAPSHOT.jar`
- `forgepilot-execution/target/forgepilot-execution-0.1.0-SNAPSHOT.jar`
- `forgepilot-web/dist/`

如果 `npm run build` 因内存被杀，可以先在内存更大的机器上构建 `dist/` 和两个 jar，再拷到这台服务器。控制面启动仍需要本机的 PostgreSQL，因为 Flyway 会在启动时执行迁移。

## 6. 环境变量

创建 `/opt/forgepilot/env/test.env`：

```bash
# 控制面
export FORGEPILOT_DB_URL=jdbc:postgresql://127.0.0.1:5432/forgepilot
export FORGEPILOT_DB_USERNAME=forgepilot
export FORGEPILOT_DB_PASSWORD=forgepilot
export FORGEPILOT_REDIS_HOST=127.0.0.1
export FORGEPILOT_REDIS_PORT=6379
export FORGEPILOT_RABBIT_ENABLED=false
export FORGEPILOT_MINIO_ENABLED=false
export FORGEPILOT_WORKERS_ENABLED=true
export FORGEPILOT_EXECUTION_URL=http://127.0.0.1:8081
export FORGEPILOT_JWT_SECRET='请换成至少 32 字节的随机字符串'
export FORGEPILOT_WORKSPACE_ROOT=/var/lib/forgepilot/workspaces

# 执行面
export FORGEPILOT_CONTROL_PLANE_URL=http://127.0.0.1:8080
export FORGEPILOT_STORAGE_ROOT=/var/lib/forgepilot/execution
export FORGEPILOT_NODE_KEY=test-exec-1
export FORGEPILOT_NODE_CAPACITY=1
export FORGEPILOT_PROCESS_REQUIRE_SANDBOX=false
```

生成 JWT 密钥：

```bash
openssl rand -base64 48
```

创建工作目录：

```bash
sudo mkdir -p /var/lib/forgepilot/workspaces /var/lib/forgepilot/execution
sudo chown -R "$USER":"$USER" /var/lib/forgepilot
```

`FORGEPILOT_PROCESS_REQUIRE_SANDBOX=false` 只适合这台没有编码负载的验收机。生产或真实编码节点应使用容器运行时，并把该值设为 `true`。

可选、此刻不必填：

- `FORGEPILOT_GITLAB_TOKEN`：没有它时，非 `file://` 远端的 Draft MR 会失败，登录和工作台不受影响
- 模型目录里的非占位密钥：没有它时不会假装编码成功
- 本机 `codex` CLI：没有它时启动编码会话会进入 `NOT_AVAILABLE` / `WAITING_USER`

## 7. 启动控制面和执行面

```bash
set -a
source /opt/forgepilot/env/test.env
set +a

cd /opt/forgepilot
nohup java -Xms128m -Xmx256m \
  -jar forgepilot-server/target/forgepilot-server-0.1.0-SNAPSHOT.jar \
  > /tmp/forgepilot-server.log 2>&1 &

nohup java -Xms128m -Xmx256m \
  -jar forgepilot-execution/target/forgepilot-execution-0.1.0-SNAPSHOT.jar \
  > /tmp/forgepilot-execution.log 2>&1 &
```

健康检查：

```bash
curl -sS http://127.0.0.1:8080/api/v1/health
curl -sS http://127.0.0.1:8081/api/v1/health
```

两者都应返回健康状态。控制面第一次启动会跑 Flyway，并写入演示账号，可能需要一两分钟。

## 8. 用 Nginx 对外提供工作台

前端请求使用相对路径 `/api/...`，必须和工作台同源。不要单独把 Vite 开发端口暴露到公网，也不要让浏览器直接打 `8080`。

```nginx
server {
    listen 80;
    server_name _;

    root /opt/forgepilot/forgepilot-web/dist;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 300s;
        proxy_buffering off;
    }
}
```

启用配置：

```bash
sudo tee /etc/nginx/sites-available/forgepilot >/dev/null <<'EOF'
# 把上面的 server { ... } 贴到这里
EOF
sudo ln -sf /etc/nginx/sites-available/forgepilot /etc/nginx/sites-enabled/forgepilot
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t && sudo systemctl reload nginx
```

浏览器访问 `http://<公网IP>/`。安全组需要放行 80 端口。

## 9. 验收清单

用演示账号登录，口令都是 `ForgePilot!dev`：

| 用户名 | 角色 | 用来验什么 |
| --- | --- | --- |
| `demo_member` | 成员 | 登录工作台，查看「支付」项目里的需求和任务 |
| `demo_admin` | 租户管理员 | 新建项目、维护成员和目录 |
| `platform_admin` | 平台管理员 | 无租户，工作台默认是空的 |
| `other_member` | 另一家公司的成员 | 确认看不到演示科技的内容 |

建议按这个顺序点一遍：

1. `demo_member` 登录，工作台能看到待处理和我的任务。
2. 打开项目、需求、任务详情；任务详情能看到会话和归档日志入口。
3. `demo_admin` 登录，能邀请或调整项目成员（角色为 `project-admin` 或 `member`）。
4. 能查看模型 Provider 和编码工具目录（演示种子是 `PLACEHOLDER_*`，响应里不会返回密钥）。
5. 能查看操作记录和日报。
6. `other_member` 登录，不能看到另一家公司的项目。
7. 若不安装 `codex`、不填真实密钥，启动编码应明确失败，而不会显示成功。

## 10. 常见问题

**启动后立刻消失，或日志里有 `OutOfMemoryError` / `Killed`**  
先确认 swap 已启用，JVM 使用 `-Xmx256m`，并且没有同时启动 RabbitMQ、MinIO 或 `npm run dev`。用 `free -h` 和 `dmesg -T | tail` 查看是否被 OOM Killer 杀掉。

**页面能打开，登录或接口 404**  
Nginx 没有把 `/api/` 反代到 `127.0.0.1:8080`，或前端不是通过 80 端口同源访问。

**能登录，但节点列表是空的**  
执行面没有起来，或 `FORGEPILOT_CONTROL_PLANE_URL` / `FORGEPILOT_EXECUTION_URL` 不是本机回环地址。先打两个 health 接口。

**首次 `docker compose` / `mvn` / `npm` 很慢**  
3 Mbps 大约是每秒 0.375 MB。只拉 PostgreSQL 和 Redis 的 Alpine 镜像、Maven 依赖和 npm 依赖，整体常要半小时以上。这是带宽限制，不是程序卡死。

**想验证真实编码怎么办**  
需要另准备更大的机器：安装 `codex` CLI、在模型目录写入非占位密钥、配置可用的 GitLab token，并让编码跑在容器里。不要在这台 2G 机器上做这一步。

## 11. 停止与更新

```bash
# 停止 Java 进程（按实际 PID 或用下面方式查找）
pgrep -af forgepilot

# 更新代码后重新构建并启动
cd /opt/forgepilot
git pull --ff-only origin dev
export MAVEN_OPTS="-Xms128m -Xmx512m"
mvn -DskipTests package
export NODE_OPTIONS="--max-old-space-size=768"
npm ci --prefix forgepilot-web
npm run build --prefix forgepilot-web
sudo systemctl reload nginx
```

中间件数据在 Docker volume 里，`git pull` 不会清库。需要重置演示数据时再单独处理 volume，默认不要删。
