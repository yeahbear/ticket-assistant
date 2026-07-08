# 智能铁路票务助手

这是按项目实战章节完成的 Spring Boot 项目，包含会话管理、聊天记忆隔离、聊天记忆持久化、流式输出、RAG 知识库、购票、退票、天气工具调用和前端演示页面。

## 技术栈

- Spring Boot 3.4.0
- Java 17
- MyBatis-Plus
- MySQL
- LangChain4j 工具注解
- DeepSeek OpenAI 兼容流式接口
- Reactor `Flux` 流式输出
- 静态 HTML/CSS/JavaScript 前端
- 接口盒子 12306 余票/票价接口

## 数据库

创建数据库：

```sql
CREATE DATABASE IF NOT EXISTS ticket_assistant DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

然后执行：

```sql
source src/main/resources/db/schema.sql;
```

默认连接配置：

```text
DB_HOST=localhost
DB_PORT=3306
DB_NAME=ticket_assistant
DB_USERNAME=root
DB_PASSWORD=123456
```

## DeepSeek 配置

如果你有 DeepSeek Key，设置环境变量：

```powershell
$env:DEEPSEEK_API_KEY="你的key"
```

没有 Key 也能演示：项目会自动使用本地演示助手，购票、退票、天气、会话隔离和历史恢复都可以跑通。

## 真实车票接口配置

如果要启用真实余票和票价查询，设置接口盒子环境变量：

```powershell
$env:APIHZ_DEVELOPER_ID="你的开发者ID"
$env:APIHZ_DEVELOPER_KEY="你的开发者KEY"
$env:APIHZ_12306_BASE_URL="http://101.35.2.25/api/12306"
```

启用后可以在聊天里输入：

```text
查询广州到上海 2026-07-15 车票
```

购票时如果同时提供出发地和目的地，例如“广州到上海”，系统会优先校验真实余票并使用真实票价；接口不可用时会回退到本地模拟购票，避免演示中断。

## 启动

如果本机没有全局 Maven，直接运行：

```powershell
powershell -ExecutionPolicy Bypass -File .\run-dev.ps1 run
```

访问：

```text
http://127.0.0.1:19999/index.html
```

## 验收对应

- 项目结构：Spring Boot 3.4.0，端口 `19999`。
- MyBatis-Plus：会话、消息、订单、退票记录都通过 Mapper 访问数据库。
- 流式输出：`GET /api/chat/stream` 使用 `text/event-stream`。
- 聊天记忆分离：每个会话 `sessionId` 独立保存消息。
- 聊天记忆持久化：聊天消息保存到 MySQL，重启后可查询。
- 天气工具调用：询问“广州天气”等内容会调用天气工具。
- 真实余票查询：询问“广州到上海 2026-07-15 车票”等内容会调用接口盒子 12306 余票接口。
- 我要购票：收集姓名、身份证、车次、日期、座位类型后写入订单。
- 我要退票：按订单号或姓名+身份证退票，计算手续费并保存退票记录。
- 扩展创新：订单列表、退款记录、无 Key 本地演示助手。
