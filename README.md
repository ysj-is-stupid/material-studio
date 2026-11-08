# 素材制作网站

这是一个模拟素材制作网站：浏览模板、上传图片、提交后台制作任务、继续使用页面，并从个人历史查看进度与结果。制作不调用真实 AI。**阶段 7 已完成本地容器构建、运行和核心链路验收**；逐项结果见 [验收场景](docs/06-acceptance.md)，设计见 [详细设计](docs/03-detailed-design.md)。

## 版本与结构

- 前端：Node.js 22、Next.js 15.5.9、React 19.2.0、TypeScript 5.9.3、TanStack Query 5.107.1；使用 npm 锁文件。
- 后端：Java 21、Spring Boot 3.5.7、MyBatis Spring Boot Starter 3.0.5、Maven 3.9。
- 数据库：MySQL 8.4。
- `frontend/`：App Router 页面、同源 API 转发和前端测试；`backend/`：Java API、MyBatis、后台工作线程和测试；`db/init/`：建表与模板初始化；`examples/assets/`：可直接上传的本地示例图片；`.github/workflows/`：CI。

## 容器启动与当前验证入口

安装 Docker Desktop 或 Docker Engine + Compose 后，在项目根目录执行：

```sh
docker compose up --build -d
docker compose ps
```

打开 `http://localhost:3000` 可进入模板列表；访问 `http://localhost:3000/api/health` 应得到 `{"status":"UP","database":"UP"}`。此请求经 Next.js 转发至 Java，再由 MyBatis 执行数据库探测。后端直接检查入口为 `http://localhost:8080/api/health`。首次启动时 MySQL 自动执行 `db/init/` 中的建表和种子脚本；已有数据库卷不会重复执行初始化脚本。

关闭服务使用 `docker compose down`，会保留数据库和文件卷。不要使用 `down -v`，除非明确要清空本地演示数据。修改 schema 后需要提供迁移脚本，不能假设旧数据卷自动重建。

可选：复制 `.env.example` 为 `.env` 修改本地演示配置。示例密码只供本地使用，勿放入生产环境。数据库由容器内部的 `db:3306` 提供，Java 使用 `DB_URL`、`DB_USER`、`DB_PASSWORD` 连接；Next.js 使用 `JAVA_API_BASE_URL` 连接 Java。上传与结果文件保存在 `app_files` 数据卷的 `/data` 下。MySQL 时间统一按 UTC 配置，界面按浏览器本地时区显示。

## 构建与测试

容器构建：`docker compose build`。CI 在 push 与 pull request 时运行前端 `npm ci`、`npm run lint`、`npm run typecheck`、`npm run test`、`npm run build`，以及 Java `mvn -B verify`。Java 有 9 项测试，验证三阶段顺序、三个失败点中断、双图结果、无效图片拒绝、身份切换和基础健康接口；前端有 11 项测试，验证多任务独立呈现、请求异常与任务失败区分、轮询的启动和停止、身份入口、上传替换与移除、迟到响应隔离、离页取消、空列表和错误模板反馈。前端和 Java 的 Dockerfile 构建阶段也执行这些检查；`docker compose build`、`docker compose up -d` 已在本地成功完成。容器中的 MySQL 初始化、健康接口、成功任务、三个指定失败阶段、可调 3 秒延迟、错误素材拒绝、PNG 预览和 A/B 个人历史隔离已实际验证。逐项结果见 [验收场景](docs/06-acceptance.md)。

后端容器构建使用 `backend/maven-settings.xml` 指定 Maven Central 的阿里云镜像，以改善本地依赖下载稳定性；依赖版本仍由 `pom.xml` 锁定。基础镜像首次获取仍取决于运行环境的镜像仓库网络。

验收方需要运行项目时，按题目要求在容器隔离环境中安装、构建、测试和启动，不必在宿主机直接执行项目脚本。可使用 Node 22 与 Maven 3.9 / Java 21 镜像执行上述 CI 命令。

## 核心业务接口与边界

`GET /api/identity` 建立匿名 Cookie 身份；`GET /api/templates`、`GET /api/templates/{id}`、`GET /api/templates/{id}/cover` 提供模板；`POST /api/assets` 接收 multipart 的 `templateId` 与 `file`；`POST /api/tasks` 接收 `templateId` 和有序 `assetIds`，返回 202 与 `taskId`；`GET /api/tasks` 和 `GET /api/tasks/{id}` 返回本人历史与状态；`GET /api/tasks/{id}/result` 返回本人成功任务的 PNG。浏览器通过 Next.js 同源转发层访问，业务校验和持久化都在 Java。任务状态为 `QUEUED`、`RUNNING`、`SUCCEEDED`、`FAILED`；阶段为 `ASSET_CHECK`、`GENERATE`、`FINALIZE`。

模拟器依次执行素材检查、模拟生成、结果整理；成功生成可预览 PNG，指定阶段失败会保存阶段和原因。`SIM_DEFAULT_FAIL_STAGE` 可设为 `NONE` 或任一阶段，`SIM_DEFAULT_STEP_DELAY_MS` 设置每阶段延迟。`SIM_ALLOW_REQUEST_OVERRIDE=true` 时，提交 JSON 可额外包含 `simulation: {"failStage":"GENERATE","stepDelayMs":1500}`，只用于本地演示，正式页面不提供这个选项。容器运行后才能实际演示这些流程。

匿名身份使用 HttpOnly Cookie；不同浏览器资料可演示记录隔离。清除 Cookie 后无法从新身份找回原记录。历史接口当前返回最新 100 条，尚未实现翻页；未使用的上传文件也尚未自动清理。进程重启后对运行中任务的自动续跑不在题目要求内。

参考界面只用于布局和信息层级；项目内 SVG 封面为本项目自行绘制。未引入其他品牌素材、付费资源或第三方业务代码。

## R-10：用户身份与个人任务记录

本题允许自行设计身份管理。本项目提供无需注册的 A/B 演示账户：首次打开网站自动建立 A，页面右上角“演示账户 A”可切换到 B，再切回 A。Java 为每个账户生成独立的随机令牌，浏览器用各自的 `HttpOnly`、`SameSite=Lax` Cookie 保存；数据库只保存令牌 SHA-256 散列及对应用户 ID。另一个 Cookie 记录当前选中的 A/B。Next.js 只转发 Cookie，Java 根据当前身份校验素材、任务和结果访问；任务和上传素材按用户 ID 归属，跨账户读取返回 404。切换后整页打开“我的记录”，清除上一账户的页面状态。A/B 只为本机验收提供可切换身份，不是密码登录或跨设备账户系统；清除 Cookie 后不能找回原身份。

手动演示：打开 `http://localhost:3000`，确认右上角显示“演示账户 A”；进入“旅行明信片”，上传 `examples/assets/demo-coast.png`、`demo-sunset.png`，点击“开始制作”，在“我的记录”看到任务。点击右上角“演示账户 A”→“用户 B”，页面应进入 B 的“我的记录”，此时不显示 A 的任务。可在 B 再制作一项任务，确认只显示 B 的记录。再点“演示账户 B”→“用户 A”，A 原有任务应重新出现，B 的任务不出现；刷新页面后仍保持当前账户和对应记录。另开无痕窗口会建立新的 A 身份，不会看到普通窗口里的 A/B 记录。

## 配置和初始化

Compose 会创建 MySQL 8.4 数据库，首次创建 `mysql_data` 卷时执行 `db/init/01-schema.sql` 和 `02-seed.sql`。种子数据有“旅行明信片”（2 张）、“宠物画像”（1 张）、“回忆拼贴”（2 张），用于演示分类切换和不同素材数量。项目自带 `examples/assets/demo-coast.png` 和 `demo-sunset.png`，无需外部素材服务；可运行 `examples/generate_demo_assets.py` 重新生成。演示图片和封面均为项目内自行绘制。

配置项：`DB_NAME`、`DB_USER`、`DB_PASSWORD`、`DB_ROOT_PASSWORD` 控制本地数据库；Java 容器使用 `DB_URL` 和 `STORAGE_ROOT`，前端容器使用 `JAVA_API_BASE_URL`。`SIM_DEFAULT_FAIL_STAGE` 可设为 `NONE`、`ASSET_CHECK`、`GENERATE`、`FINALIZE`；`SIM_DEFAULT_STEP_DELAY_MS` 默认为 1500，每阶段按毫秒计，可在请求覆盖时设为 0 到 15000；`SIM_ALLOW_REQUEST_OVERRIDE` 在 Compose 演示配置默认为 `true`，Java 应用自身默认 `false`。线上环境应使用真实密钥、HTTPS，并关闭请求覆盖模拟参数。

## 主要接口与字段

所有浏览器请求走同源 `/api`；Next.js 只转发请求及 Cookie，校验、任务执行和 MySQL 持久化由 Java 完成。

| 接口 | 请求 | 响应重点 |
| --- | --- | --- |
| `GET /api/identity` | 无需参数 | `viewerId`、`slot`；首次请求设置 A 身份 Cookie |
| `POST /api/identity/switch` | JSON：`{"slot":"A"}` 或 `{"slot":"B"}` | `viewerId`、`slot`；首次切到 B 时建立 B 身份 |
| `GET /api/templates` | 可选 `category` | `categories`、`items`，含标题、分类、封面、素材要求 |
| `GET /api/templates/{id}` | 模板 ID | `requiredImageCount`、`acceptedMimeTypes`、`maxFileSizeBytes` 等 |
| `GET /api/templates/{id}/cover` | 模板 ID | SVG 示例封面 |
| `POST /api/assets` | multipart：`templateId`、`file` | 201：`assetId`、`previewUrl`、`mimeType`、`sizeBytes` |
| `GET /api/assets/{id}/preview` | 本人素材 ID | 图片内容 |
| `POST /api/tasks` | JSON：`templateId`、有序 `assetIds`，可选 `simulation` | 202：`taskId`、`status=QUEUED` |
| `GET /api/tasks` | 匿名身份 Cookie | `items`、`nextCursor`；当前仅返回最新 100 条，`nextCursor=null` |
| `GET /api/tasks/{id}` | 本人任务 ID | 当前状态、阶段、阶段记录、错误或结果地址 |
| `GET /api/tasks/{id}/result` | 本人成功任务 ID | 可预览的 PNG |

任务详情的 `status` 为 `QUEUED`、`RUNNING`、`SUCCEEDED`、`FAILED`；`currentStage` 为 `NONE`、`ASSET_CHECK`、`GENERATE`、`FINALIZE`。`stageHistory` 的每项包含 `stage`、`state`、`startedAt`、`finishedAt` 和可选 `failureReason`。终态成功时有 `resultUrl`，失败时有 `failureReason`。上传类型、大小、数量、素材所有权和模板匹配由 Java 再次校验；业务校验错误响应包含 `code` 和可读的 `message`。状态查询只读 MySQL，不推动制作阶段。

后台由单个 Spring 定时工作线程从 MySQL 领取等待任务，因此多个任务可以排队。每一步开始、成功或失败均分别写入数据库；页面关闭不影响已受理的任务。模拟生成器读取上传图片，制作可预览 PNG，不调用 AI。前端只轮询未完成任务，任务结束后停止；查询接口暂时不可用时保留最近的任务状态并提供重试，不把它显示成制作失败。

## 演示成功、失败和多任务

启动容器后，在浏览器打开模板列表，选择“旅行明信片”，上传 `examples/assets/` 下的两张 PNG。上传就绪后提交，右侧近期任务和“我的记录”会显示等待、三个处理阶段及结果。提交后立刻返回列表，或刷新“我的记录”，仍能从 Java 恢复任务并继续跟踪。A/B 账户的演示步骤见 R-10 小节。

下面的 `curl` 示例用于精确指定失败阶段。Windows PowerShell 中可把 `curl` 换成 `curl.exe`。先在项目根目录执行，上传响应中的两个 `assetId` 需填入提交 JSON：

```sh
curl -c demo-cookies.txt -b demo-cookies.txt http://localhost:3000/api/identity
curl -c demo-cookies.txt -b demo-cookies.txt -F 'templateId=travel-postcard' -F 'file=@examples/assets/demo-coast.png;type=image/png' http://localhost:3000/api/assets
curl -c demo-cookies.txt -b demo-cookies.txt -F 'templateId=travel-postcard' -F 'file=@examples/assets/demo-sunset.png;type=image/png' http://localhost:3000/api/assets
```

正常成功请求：

```sh
curl -b demo-cookies.txt -H 'Content-Type: application/json' -d '{"templateId":"travel-postcard","assetIds":["第一个assetId","第二个assetId"]}' http://localhost:3000/api/tasks
```

指定“模拟生成”失败并把每阶段延迟设为 3 秒（可将 `GENERATE` 替换为 `ASSET_CHECK` 或 `FINALIZE`）：

```sh
curl -b demo-cookies.txt -H 'Content-Type: application/json' -d '{"templateId":"travel-postcard","assetIds":["第一个assetId","第二个assetId"],"simulation":{"failStage":"GENERATE","stepDelayMs":3000}}' http://localhost:3000/api/tasks
```

提交响应中的 `taskId` 可用于 `GET /api/tasks/{id}`；`GET /api/tasks` 查看同一身份的全部近期任务。连续提交两次（一个成功、一个指定失败）可观察任务分别排队和独立更新。演示查询异常时，可在浏览器开发者工具中临时阻断 `/api/tasks` 请求，再恢复请求：页面应保留旧任务状态并显示查询错误，后台任务仍继续执行。不要通过停止 Java 容器制造此场景，因为进程重启后运行中任务不会自动续跑。

## 已完成、未完成与已知限制

已完成：题目指定技术栈、模板和素材交互、匿名个人记录、三步异步模拟、状态与历史查询、多任务前端轮询、CI、关键自动化测试、Compose 构建与本地容器运行验收。未完成：生产环境部署、历史翻页、孤立上传文件清理、运行中任务的重启恢复、受理幂等和自动重试；这些均非题目强制项。源码已准备推送至 GitHub 私有仓库；远端流水线结果以推送后的 Actions 运行记录为准。

本次协作从需求阅读到阶段 7 的操作记录跨约 5 小时，包含依赖下载、Docker 排障和验证；未单独记录纯开发工时，因此不把该跨度作为精确人工工时。

## 部署与回滚

本地或单机容器部署：准备非演示密码的 `.env`，备份 MySQL 和上传/结果卷，执行 `docker compose up --build -d`，确认 `docker compose ps` 和 `/api/health`，再演示一次上传到结果预览。回滚时先停止服务，恢复上一版本源码或镜像；若数据库结构已变更，需先恢复对应版本的数据库备份，再启动旧版本。当前项目只有首次初始化 SQL，已有卷不会自动迁移；不要直接删除数据库卷作为回滚手段。多实例调度和复杂发布平台不在本题范围。

## AI 使用与素材来源

本项目使用 Codex 辅助开发。工作流是先逐条阅读题目并分阶段确认，再把上下文整理为详细设计，接着生成/修改前后端代码，用编译、类型检查和行为测试修正问题。例如，模拟失败最初只有实现路径，随后增加三个阶段的失败中断测试，确认不会进入后续阶段。未使用 AI 图片服务；SVG 封面由项目内代码绘制，两张 PNG 由 `examples/generate_demo_assets.py` 生成。Next.js、React、Spring Boot、MyBatis、MySQL 等为项目依赖，没有复制第三方业务源码或题目参考图里的品牌素材。
