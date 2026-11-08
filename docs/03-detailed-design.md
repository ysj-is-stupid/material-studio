# 素材制作网站详细设计

> 阶段 3 产物。依据题目 R-01 至 R-14 和已确认的技术选型；本文件描述待实现行为，不代表代码已经完成。进入工程骨架前须先完成本阶段汇报。

## 1. 目标与边界

用户从 Java 提供的模板中选择一个，上传符合模板要求的图片，提交模拟制作任务，在后台处理期间继续浏览网站，并能在历史页看到属于自己的任务状态和预览结果。Java 承担业务执行与持久化；Next.js 承担页面、交互和请求转发。模拟器不调用真实 AI。

本项目不实现支付、会员、第三方登录、复杂模板后台、真实 AI、视频处理、失败任务重试或进程重启后对运行中任务的自动续跑。参考图用于确定列表、详情/制作及历史区域的主要布局，不复刻截图里的品牌、积分、删除、账户设置等题外功能。

## 2. 子步骤及交付检查

1. **数据与身份**：确定匿名用户、模板、素材、任务和阶段记录的归属、约束及持久化方式。
2. **接口与校验**：确定浏览、上传、提交、状态、历史及图片访问接口与错误响应。
3. **后台状态机**：确定受理、排队、三个阶段、终态、失败注入和进程异常边界。
4. **前端状态**：确定上传与提交状态、多个任务的跟踪、查询异常、刷新恢复和清理规则。
5. **验收映射**：确定关键测试及 R-01 至 R-14 的验证入口。

## 3. 系统结构

```text
浏览器（Next.js 页面、React 状态）
  └─ 同源 /api/*（Next.js Route Handler，仅转发请求及 Cookie）
       └─ Java Spring Boot /api/*
            ├─ MyBatis → MySQL（模板、匿名用户、素材元数据、任务、阶段记录）
            ├─ 本地持久化目录（上传图片、生成结果）
            └─ Spring 后台定时工作线程（领取等待任务并执行三阶段）
```

前端采用 Next.js App Router、React、TypeScript；后端采用 Java、Spring Boot、MyBatis；数据库为 MySQL。版本采用阶段 1 的基线，具体补丁版本、锁文件和镜像标签在工程骨架阶段固定。Next.js 不直接执行制作步骤，也不存储业务数据。前端通过同源接口访问 Java，以免浏览器依赖容器内部地址。

## 4. 数据模型

数据库迁移脚本在工程骨架阶段提供，核心表及关键约束如下。时间统一存 UTC，界面按用户本地时区显示。

| 表 | 关键字段 | 约束与用途 |
| --- | --- | --- |
| `demo_user` | `id` UUID、`token_hash`、`created_at` | `token_hash` 唯一；只存匿名身份令牌的散列，不存明文令牌。 |
| `template` | `id`、`title`、`category`、`description`、`cover_path`、`required_image_count`、`accepted_mime_types`、`max_file_size_bytes`、`layout_key`、`enabled` | 初始化至少两种分类和不同上传数量的模板；列表与详情均由 Java 读取。 |
| `asset` | `id` UUID、`user_id`、`template_id`、`stored_path`、`mime_type`、`size_bytes`、`created_at` | 文件写入完成后才插入记录；路径由服务端生成，不能使用用户文件名作为存储路径。 |
| `production_task` | `id` UUID、`user_id`、`template_id`、`status`、`current_stage`、`result_path`、`failure_reason`、`fail_stage`、`step_delay_ms`、`created_at`、`started_at`、`finished_at` | 保存受理时的模拟参数快照；按 `(user_id, created_at, id)` 建历史查询索引。 |
| `task_asset` | `task_id`、`asset_id`、`position` | 记录提交时的图片顺序；`(task_id, position)` 唯一，素材 ID 不得重复。 |
| `task_stage_event` | `task_id`、`stage`、`state`、`started_at`、`finished_at`、`failure_reason` | 每个任务每个阶段最多一条；记录阶段开始、成功或失败，可证明真实执行边界。 |

模板种子数据在数据库初始化时写入；封面和演示素材作为项目自有静态文件随源码交付。上传文件和任务结果放在容器数据卷。数据库保存可定位文件的相对路径；客户端只接收受控的访问 URL，不接收磁盘路径。任务与素材按 `user_id` 查询，跨用户访问统一返回 404。

### 匿名身份

首次调用 `GET /api/identity` 时，Java 生成不可预测的随机令牌，写入 `HttpOnly`、`SameSite=Lax` 的 Cookie，仅在数据库保存令牌散列，并返回稳定的 `viewerId`。后续请求由 Java 校验 Cookie。生产 HTTPS 环境使用 `Secure`；本地 HTTP 演示不设置该标记。Next.js 转发 `Cookie` 和 `Set-Cookie`，不自行签发身份。若无有效身份，业务接口返回 401，前端重新建立身份后再读取数据；历史记录不会自动并入新身份。不同浏览器资料或无痕窗口可演示个人记录隔离。

R-10 补充：页面导航提供 A/B 演示账户切换。两个账户各持一枚独立身份令牌 Cookie；`material_viewer_active` Cookie 只标识当前账户，不能代替令牌。`POST /api/identity/switch` 校验目标为 A/B，首次切换到目标账户时创建身份；切回时复用原身份。身份接口返回当前 `slot` 和 `viewerId`。前端切换成功后整页进入历史页，清除先前身份的上传状态和查询缓存。切换失败保持原账户并显示错误。不同浏览器资料或无痕窗口仍可演示浏览器间隔离。该方案为本地演示身份，不提供凭据登录和跨设备恢复。

## 5. HTTP 接口草案

浏览器访问同源 `/api/*`；下表列出 Java 对应接口。所有写操作使用 JSON，图片上传除外。错误体统一为 `{ "code": "...", "message": "..." }`，字段错误可附 `fieldErrors`。错误消息供界面理解，详细异常只写服务端日志。

| 方法与路径 | 请求 | 成功响应 | 主要异常 |
| --- | --- | --- | --- |
| `GET /api/identity` | Cookie 可为空 | `{viewerId,slot}`；首次访问同时设置 Cookie | 服务暂不可用 503/500 |
| `POST /api/identity/switch` | `{slot:"A"|"B"}` | `{viewerId,slot}`；首次切换时设置对应 Cookie | 非法 slot 返回 422 |
| `GET /api/templates` | 可选 `category` | `{categories, items:[TemplateSummary]}` | 空结果返回空数组 |
| `GET /api/templates/{id}` | 路径 ID | `TemplateDetail` | 模板不存在 404 |
| `POST /api/assets` | multipart：`templateId`、`file` | 201 `{assetId, previewUrl, mimeType, sizeBytes}` | 401、404、413、415、422 |
| `POST /api/tasks` | `{templateId, assetIds:[...]}` | 202 `{taskId, status:"QUEUED"}` | 401、404、422 |
| `GET /api/tasks` | `limit`、可选分页游标 | `{items:[TaskSummary], nextCursor}`，按创建时间倒序 | 401 |
| `GET /api/tasks/{id}` | 任务 ID | `TaskDetail` | 401、404 |
| `GET /api/tasks/{id}/result` | 任务 ID | 仅成功任务返回 `image/png` | 401、404、409 |
| `GET /api/assets/{id}/preview` | 素材 ID | 返回本人上传的图片 | 401、404 |
| `GET /api/templates/{id}/cover` | 模板 ID | 返回模板封面图片 | 404 |

`TemplateDetail` 至少包含 ID、标题、分类、描述、封面 URL、要求的图片数量、允许的 MIME 类型和每张图片的最大字节数。`TaskDetail` 至少包含任务 ID、模板 ID/标题、创建时间、整体状态、当前阶段、阶段记录、失败原因及成功时的 `resultUrl`。历史摘要保留同样的可展示状态、阶段、错误和结果地址，便于刷新后恢复。

上传前端先校验数量、类型与大小，并即时显示预览；Java 仍执行权威校验：模板存在且启用、文件实际可解码为 JPEG/PNG、MIME 与模板允许类型一致、文件大小在模板限制内。上传时对请求流设置上限，先写临时文件，再原子移动到服务端生成的路径并保存元数据；失败时清理临时文件。提交时再次校验素材数量、ID 不重复、均属于当前匿名用户及指定模板、文件仍可用。提交请求中禁用重复提交；后端不承诺相同请求幂等，README 将说明这一取舍。

### 模拟参数

正式页面不暴露模拟开关。默认值来自 Java 配置：`SIM_DEFAULT_FAIL_STAGE=NONE`、`SIM_DEFAULT_STEP_DELAY_MS=1500`。为演示指定任务的失败，开发配置允许 `POST /api/tasks` 增加 `simulation:{failStage:"ASSET_CHECK|GENERATE|FINALIZE|NONE",stepDelayMs:0..15000}`；只有 `SIM_ALLOW_REQUEST_OVERRIDE=true` 时接受，否则返回 422。参数在受理时写入任务记录，任务之间不会因运行时配置变化而互相影响。README 给出无需真实凭据的演示命令，并说明此开关仅用于本地演示。

## 6. 后台任务状态机

整体状态：`QUEUED`、`RUNNING`、`SUCCEEDED`、`FAILED`。阶段：`NONE`、`ASSET_CHECK`、`GENERATE`、`FINALIZE`。阶段记录状态：`RUNNING`、`SUCCEEDED`、`FAILED`。

```text
提交校验 → 数据库事务写任务与 task_asset → 返回 202 + taskId
                         ↓
                  QUEUED / NONE
                         ↓ 后台工作线程领取
                  RUNNING / ASSET_CHECK
                         ↓ 成功
                  RUNNING / GENERATE
                         ↓ 成功
                  RUNNING / FINALIZE
                         ↓ 成功
                  SUCCEEDED / FINALIZE + resultUrl

任一阶段失败 → FAILED / 失败阶段 + failureReason；不执行后续阶段
```

使用 Spring 的单个后台定时工作线程扫描最早的 `QUEUED` 任务，并以数据库条件更新 `status = QUEUED → RUNNING` 领取。一个任务可以等待，另一个任务可以运行；题目允许排队，不要求并行。调度线程独立于 HTTP 请求，因此返回任务 ID 后关闭页面不会中断执行。读取任务的 GET 请求只读数据库，绝不推动阶段。

每个阶段开始时，在独立事务中更新 `current_stage` 并插入 `RUNNING` 阶段记录；模拟延迟在事务外执行，使阶段可被查询观察。阶段成功时更新记录为 `SUCCEEDED`，再启动下一阶段。故意失败或处理异常时，在同一事务内将阶段记录和任务标记为 `FAILED`，保存可理解的原因，停止后续执行。最终阶段成功后，在同一事务里写入结果相对路径并标记任务 `SUCCEEDED`。终态更新带状态条件，避免后续查询或旧工作线程把终态改回处理中。

`ASSET_CHECK` 再次检查素材文件是否存在且可解码；`GENERATE` 用 Java 图片处理生成确定性的 PNG 临时结果，可按模板布局使用一张或两张已上传图片；`FINALIZE` 把结果放入持久化目录并记录可预览路径。失败注入在指定阶段开始、可观察延迟结束后触发。清理无用临时文件。阶段耗时和失败配置不会依赖前端计时。

进程在任务 `RUNNING` 时退出，任务状态可能停留在该阶段；本题不要求重启自动续跑，README 必须明确说明。仍为 `QUEUED` 的任务可在服务恢复后由工作线程领取。单实例 Compose 是本方案的运行前提；多实例分布式调度不在范围内。

## 7. 前端页面与异步状态

### 模板列表

顶部导航、分类筛选和模板卡片参考 UI-01 的信息层级，使用项目自己的图片和文案。分类和卡片来自 Java。分别处理加载中、空列表、请求失败和重试。点击卡片进入详情。

### 模板详情与制作

详情区参考 UI-02：展示模板封面、描述和每个上传位；旁侧展示该用户的近期任务。每个上传位独立管理 `EMPTY → SELECTED → UPLOADING → READY`，以及 `UPLOAD_FAILED`。支持预览、替换、移除；替换时旧请求取消或忽略，旧响应不得覆盖新文件。只有所有上传位 `READY` 且提交请求未进行时可提交。提交返回 202 后立即显示新任务卡片并清空提交中状态，而不是等制作完成。

### 历史与任务跟踪

历史页参考 UI-03 的卡片式状态区，展示创建时间、等待/处理中、当前阶段、成功预览或失败原因；不加入截图里的删除、积分和重试能力。首次进入或刷新时从 Java 读取该身份的历史任务。TanStack Query 以 `viewerId` 和任务 ID 作为缓存键；每个任务的响应只更新同一 ID。仅对缓存中 `QUEUED/RUNNING` 的任务启用轮询，终态停止该任务轮询；没有活动任务时停止整体轮询。页面离开时取消请求，身份变化时清空旧身份缓存。使用请求取消和身份/任务键检查，避免晚到响应覆盖新用户或其他任务。

网络或查询接口异常只显示“状态暂时无法更新”及重试入口，并保留最近一次服务端任务状态；只有服务端明确返回 `FAILED` 才显示“制作失败”。初次加载失败、空历史、模板 404 和上传失败都有独立反馈。桌面端完成主要布局，小屏堆叠仍可上传和查看任务；按钮、输入、图片和上传位提供语义标签与键盘焦点。

## 8. 验收与测试设计

| 范围 | 可验证行为 | 计划验证方式 |
| --- | --- | --- |
| R-01～R-03 | 指定技术栈和 Java 接口；模板分类；不同图片数量；上传预览、替换、移除及前后端校验 | 构建检查、接口测试、手工页面走查 |
| R-04～R-06 | 202 快速受理；后台三阶段顺序；阶段可查询；指定阶段失败中断；耗时可调 | Java 单元/集成测试与演示脚本 |
| R-07～R-10 | 上传/提交/任务状态区分；查询异常保留状态；多任务隔离；停止终态轮询；刷新恢复；匿名用户隔离 | 前端组件/状态测试、API 测试与手工演示 |
| R-11 | 参考图对应的布局、可读状态、小屏操作和基本无障碍 | 页面走查 |
| R-12～R-14 | CI 检查前端 lint/类型/构建及 Java 测试/构建；README；Compose 启动及初始化 | CI、容器内启动和完整演示 |

后端关键测试：按顺序完成三个阶段并得到 PNG；在每个阶段分别注入失败时后续阶段没有记录；提交受理时任务先为 `QUEUED`；跨用户访问任务和素材被拒绝；不合法图片/素材/模板被拒绝。前端关键测试：两个任务的响应互不覆盖；一个任务完成后停止跟踪而另一个继续；查询错误不把任务改成 `FAILED`；刷新后从历史恢复未完成任务。测试通过后仍需在容器中实际运行一遍成功和失败流程，不能只用源码推断运行效果。

## 9. 工程骨架阶段的输入

下一阶段仅建立可启动、可连接的工程基础：`frontend/`、`backend/`、`db/`、`docker-compose.yml`、`.github/workflows/ci.yml`、配置示例和 README 初稿。须锁定具体兼容版本、提供数据库初始化与模板种子数据、挂载文件数据卷，并验证前端、Java 和 MySQL 能在 Compose 中相互连接。业务闭环留到核心链路阶段实现。
