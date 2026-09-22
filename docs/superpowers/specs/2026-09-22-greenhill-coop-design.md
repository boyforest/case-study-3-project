# Greenhill Food Co-op 订货系统 — 设计文档

- 课程:ISYS3001 Managing Software Development · Case Study 2
- 项目路径:`~/greenhill-coop`
- 日期:2026-09-22
- 状态:已确认(待实施)
- 交付窗口:1 周

---

## 1. 背景与目标

Greenhill Food Co-op 是布里斯班 Moorooka 的一家志愿运营社区食品合作社(186 户会员,每周约 90–110 户下单)。当前流程靠纸质订单表 + Ngaire 手工录入电子表格,周日晚 3–4 小时的录入是单点故障。

本系统目标(来自案例第 3 节):

1. 会员自己录入订单,消除周日晚上的重复录入;
2. 拣货单与轮次汇总基于真实数据生成,不再手工加总;
3. 为财务(Doug)留下可追溯的"谁订了什么、多少钱"的记录。

**本系统替换的是纸质订单表和协调员的电子表格,不替换银行账户、会计和拣货台本身。**

---

## 2. 范围

### 2.1 Sprint 内(12 个故事)

| # | 分支 | 故事 | 关键验收 |
|---|------|------|---------|
| 1 | `story/01-foundation` | 项目骨架与统一基础 | Spring Boot + H2 可启动;`Result<T>` 统一返回;全局异常处理;schema 建表;种子数据加载 |
| 2 | `story/02-auth` | 登录与角色鉴权 | 会员号+密码登录返回 JWT;无 token 401;会员接口与协调员接口按角色隔离 |
| 3 | `story/03-members` | 协调员维护会员 | 新增、查询(关键字/状态/分页)、编辑、停用、启用、重置密码;停用不删除 |
| 4 | `story/04-products` | 协调员维护商品 | 新增、编辑、撤回;每个商品有名称、售价、**销售方式(按件/按公斤)**、货架位 |
| 5 | `story/05-rounds` | 协调员管理轮次 | 创建(编号/开闭时间/取货日);开放→关闭→打包状态流转;同一时间仅一个 OPEN |
| 6 | `story/06-catalog` | 会员查看可订商品 | 列出 OPEN 轮次中 ACTIVE 商品(名称、单价、销售方式);已撤回不显示;无 OPEN 轮次给出提示而非空列表 |
| 7 | `story/07-place-order` | 会员下单与计价 | 下单/改单;按件必须整数、按公斤可小数;行金额与订单总额正确;**下单时快照单价** |
| 8 | `story/08-manage-order` | 会员改单/取消/只读 | OPEN 期间可改可取消;关闭后仍可查看不可修改;不影响他人订单 |
| 9 | `story/09-round-orders` | 协调员查看轮次全部订单 | 按轮次列出所有订单(含会员、行明细、金额);空轮次返回空结果不报错 |
| 10 | `story/10-round-totals` | 轮次按商品汇总 | 每个商品的总订购量(按公斤/按件)+ 总金额;供下批发采购单使用 |
| 11 | `story/11-order-for-member` | 协调员代会员下单 | 协调员可为任意 ACTIVE 会员在 OPEN 轮次下单/改单(服务 Jan Buckley 这类电话下单会员) |
| 12 | `story/12-delivery` | 交付 | 种子数据完整;README 干净 checkout 可跑;`mvn test` 全绿;`docs/handover.md`、`docs/jira-import.csv` 就绪 |

### 2.2 明确不做(进产品 Backlog,交接文档写明原因)

在线支付/收款、银行对账、会员对账单与余额、信用/挂账、拣货实际重量记录与短供分配、拣货单按货架/箱号排序打印、短信/邮件通知、Harvest Belt 门户对接、Two Creeks 对接、配送跑腿、手机 App、审计日志(谁改了什么)、历史价目表与调价、会计软件导出、直接扣款、电子秤/硬件、会员注册自助、季度排班与 $30 代班费、会费收取。

---

## 3. 技术选型与决策记录

沿用团队既有项目(`com.jinlin24th.www`)的技术栈与代码模式,以降低团队后续开发与调优成本;但剥离全部与作业无关的重依赖,确保"干净 checkout 即可运行"。

| 决策点 | 选择 | 理由 |
|--------|------|------|
| 语言/框架 | Java 17 + Spring Boot 3.2.5 | 团队最熟;案例允许任意 web 应用 |
| 持久层 | MyBatis-Plus 3.5.5(BaseMapper) | 团队最熟;简单 CRUD 无需 XML |
| 数据库 | **H2 文件模式**(`./data/greenhill.mv.db`) | 零安装;重启不丢数据;教师无需 MySQL/Redis |
| 认证 | JWT(jjwt 0.12.5)+ BCrypt(`spring-security-crypto`) | 沿用旧项目模式;`jwt.secret` 在 yml 有默认值,不会因缺环境变量启动失败 |
| 参数校验 | spring-boot-starter-validation | 标准做法 |
| API 文档 | springdoc-openapi 2.5.0(Swagger UI) | 演示与验收方便,依赖小 |
| 前端 | React 19 + Vite 7 + antd 6 + axios + react-router 7(JSX) | 沿用旧项目 `admin-web` 模式;`CrudTable`、`request.js` 直接复用 |
| 前端打包 | **预构建产物入库**,默认 `mvn package` 不依赖 Node;`-Pfrontend` profile 重新构建 | 教师只需 JDK;慢网/墙内不下载 Node |
| 构建工具 | Maven Wrapper(`./mvnw`)入库 | 教师无需预装 Maven |
| UI 语言 | 英文 | 课程与验收人为英文语境 |

**已从旧项目剥离、本项目不引入:** MySQL、Redis、RocketMQ、微信小程序 SDK、微信支付 SDK、Docker、文件上传、操作日志切面、限流切面、多角色后台账号体系。

---

## 4. 架构与项目结构

```
greenhill-coop/
├── mvnw / mvnw.cmd / .mvn/            # Maven Wrapper
├── pom.xml                             # 默认 profile:不含前端构建
├── README.md                           # 唯一运行说明(含演示账号)
├── .gitignore                          # 忽略 data/、target/、node_modules/
├── docs/
│   ├── superpowers/specs/              # 本设计文档
│   ├── handover.md                     # 交接文档(六节,对应案例第 9 节)
│   └── jira-import.csv                 # Jira 导入用 backlog(Sprint + Product)
├── frontend/                           # React 源码(开发时用)
│   ├── package.json / vite.config.js / index.html
│   └── src/{main.jsx, App.jsx, router/, api/, utils/, components/, layouts/, pages/}
├── src/main/java/com/greenhill/coop/
│   ├── CoopApplication.java
│   ├── common/        # Result, BizCode, BizException, GlobalExceptionHandler, PageResult
│   ├── auth/          # JwtUtil, JwtInterceptor, @RequireCoordinator, @CurrentUser, UserContext, ArgumentResolver
│   ├── config/        # WebMvcConfig(CORS/静态资源/拦截器), DataSeeder
│   ├── entity/        # Member, Product, Round, Order, OrderLine
│   ├── mapper/        # 5 个 BaseMapper + 汇总查询
│   ├── dto/           # 请求/响应对象
│   ├── service/       # MemberService, ProductService, RoundService, OrderService, PricingService
│   └── controller/    # AuthController, MemberController, ProductController, RoundController, OrderController
├── src/main/resources/
│   ├── application.yml
│   ├── schema.sql                      # CREATE TABLE IF NOT EXISTS
│   └── static/                         # 前端构建产物(入库)
└── src/test/java/com/greenhill/coop/   # 测试见第 11 节
```

运行时形态:**单个 Spring Boot JAR**,同时提供 REST API 和前端静态页面(SPA 由前端路由接管,后端对非 `/api/**` 路径回退到 `index.html`)。

---

## 5. 数据模型

H2 使用 `MODE=MySQL;DATABASE_TO_LOWER=TRUE` 以便 DDL 与团队 MySQL 经验一致。所有表含 `created_at`、`updated_at`。

### member
| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | |
| member_no | VARCHAR(10) | NOT NULL, UNIQUE | 如 `M-094` |
| name | VARCHAR(100) | NOT NULL | |
| phone | VARCHAR(20) | | |
| email | VARCHAR(100) | NULL | Jan Buckley 无 email |
| address | VARCHAR(200) | NULL | |
| role | VARCHAR(20) | NOT NULL | `MEMBER` / `COORDINATOR` |
| status | VARCHAR(20) | NOT NULL | `ACTIVE` / `INACTIVE` |
| password_hash | VARCHAR(100) | NOT NULL | BCrypt |

### product
| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK | |
| name | VARCHAR(100) | NOT NULL | |
| unit_type | VARCHAR(20) | NOT NULL | `PER_UNIT` / `PER_KG` |
| price | DECIMAL(10,2) | NOT NULL | 按件单价或每公斤单价(已含 12% 加价) |
| bay | VARCHAR(10) | NULL | 货架位(B1/B3/A2/VEG/COOL…),拣货用 |
| status | VARCHAR(20) | NOT NULL | `ACTIVE` / `WITHDRAWN` |

### round
| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK | |
| round_no | INT | NOT NULL, UNIQUE | 轮次号,如 33 |
| orders_open_at | TIMESTAMP | NOT NULL | |
| orders_close_at | TIMESTAMP | NOT NULL | 周日 20:00 |
| pickup_date | DATE | NOT NULL | 周四 |
| status | VARCHAR(20) | NOT NULL | `OPEN` / `CLOSED` / `PACKED` |

业务约束:同一时间至多一个 `OPEN` 轮次;状态只能 `OPEN → CLOSED → PACKED`,不可回退。

### orders
| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK | |
| member_id | BIGINT | NOT NULL, FK | |
| round_id | BIGINT | NOT NULL, FK | |
| status | VARCHAR(20) | NOT NULL | `ACTIVE` / `CANCELLED` |
| UNIQUE | | (member_id, round_id) | 每会员每轮次一条订单 |

取消订单保留记录(不物理删除);重新下单时复用同一行,重置为 `ACTIVE` 并替换行明细。

### order_line
| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK | |
| order_id | BIGINT | NOT NULL, FK | |
| product_id | BIGINT | NOT NULL, FK | |
| quantity | DECIMAL(10,3) | NOT NULL | 按件为整数;按公斤最多 3 位小数 |
| unit_type_snapshot | VARCHAR(20) | NOT NULL | 下单时的销售方式 |
| unit_price | DECIMAL(10,2) | NOT NULL | **下单时的单价快照** |
| line_total | DECIMAL(10,2) | NOT NULL | 计算后存储,四舍五入 2 位 |
| UNIQUE | | (order_id, product_id) | 同一订单同一商品一行 |

---

## 6. 计价规则(唯一领域逻辑)

`PricingService`:

```
lineTotal(unitType, quantity, unitPrice):
  PER_UNIT:  校验 quantity 为整数(scale ≤ 0),返回 quantity × unitPrice
  PER_KG:    校验 quantity 小数位 ≤ 3,返回 quantity × unitPrice
  结果统一 setScale(2, HALF_UP)

orderTotal(lines) = Σ line_total   # 各行已是 2 位,不再二次舍入
```

校验规则(下单时,任一不满足返回 400):
- `quantity > 0`
- `PER_UNIT` 必须为整数(如 2、1;`1.5` 拒绝)
- `PER_KG` 最多 3 位小数(如 `1.5`、`0.25`、`1.58`)

**已确认的决策:**
- **不做 Ngaire 的 10 分凑整。** 按 Doug 要求记录实际金额(2 位四舍五入)。
- 售价已含 12% 加价,系统不计算毛利,`price` 即最终售价。
- 单价在**下单时快照**到 `order_line.unit_price`,之后商品调价不影响既有订单(Doug 的历史价格需求;完整价目历史表属 backlog)。

---

## 7. API 设计

统一返回 `Result<T>`:`{ code, message, data }`,`code=200` 成功;业务失败 HTTP 200 + `code=400/404/409`;未登录 HTTP 401;越权 HTTP 403。与旧项目 `request.js` 拦截器约定一致。

| 方法 | 路径 | 角色 | 说明 |
|------|------|------|------|
| POST | `/api/auth/login` | 公开 | `{memberNo, password}` → `{token, member}` |
| GET | `/api/auth/me` | 登录 | 当前用户信息 |
| GET | `/api/members` | 协调员 | 分页 + `keyword` + `status` |
| POST | `/api/members` | 协调员 | 新增会员(含初始密码) |
| PUT | `/api/members/{id}` | 协调员 | 编辑 |
| POST | `/api/members/{id}/deactivate` | 协调员 | 停用 |
| POST | `/api/members/{id}/activate` | 协调员 | 启用 |
| POST | `/api/members/{id}/reset-password` | 协调员 | 重置密码 |
| GET | `/api/products` | 协调员 | 分页 + `keyword` + `status` |
| POST | `/api/products` | 协调员 | 新增(名称/售价/销售方式/货架位) |
| PUT | `/api/products/{id}` | 协调员 | 编辑 |
| POST | `/api/products/{id}/withdraw` | 协调员 | 撤回(不破坏既有订单) |
| GET | `/api/products/available` | 会员 | 返回 `{round: {...}\|null, products: [...]}`;无 OPEN 轮次时 `round=null`、`products=[]`,前端据此显示提示 |
| GET | `/api/rounds/current` | 会员 | 当前轮次或 null |
| GET | `/api/rounds` | 协调员 | 轮次列表(分页) |
| POST | `/api/rounds` | 协调员 | 创建并开放 |
| POST | `/api/rounds/{id}/close` | 协调员 | 关闭 |
| POST | `/api/rounds/{id}/pack` | 协调员 | 标记打包完成 |
| GET | `/api/orders/mine` | 会员 | 我的订单列表(含历史轮次,带行明细) |
| PUT | `/api/orders/mine` | 会员 | 在 OPEN 轮次下单/改单(整体替换行明细) |
| DELETE | `/api/orders/mine` | 会员 | 取消当前订单(仅 OPEN) |
| GET | `/api/orders` | 协调员 | `roundId` 查询轮次全部订单(含会员、行、金额) |
| POST | `/api/orders/for-member` | 协调员 | `{memberId, lines[]}` 代下单/改单 |
| GET | `/api/rounds/{id}/totals` | 协调员 | 按商品汇总:总数量 + 总金额 |

鉴权实现:`JwtInterceptor` 拦截 `/api/**`(放行 `/api/auth/login`),解析 token 写入 `UserContext`;检查 `@RequireCoordinator` 注解做角色控制;`@CurrentUser` 参数解析器注入当前用户。

---

## 8. 前端设计

复用旧项目模式:`utils/request.js`(axios 拦截器 + 401 跳登录)、`utils/auth.js`(token 存取)、`components/CrudTable.jsx`(去掉图片上传)、`layouts/AppLayout.jsx`(侧边菜单按角色渲染)。

| 路由 | 页面 | 角色 | 内容 |
|------|------|------|------|
| `/login` | LoginPage | 公开 | 会员号 + 密码 |
| `/shop` | ShopPage | 会员 | 当前轮次商品表(名称/销售方式/单价/货架位)+ 数量输入(按件整数、按公斤 3 位小数)+ 订单篮侧栏(行金额、总额)+ 保存/取消;无 OPEN 轮次显示 Alert |
| `/my-order` | MyOrderPage | 会员 | 我的订单列表(跨轮次,可展开行明细,含金额);已关闭轮次只读 |
| `/admin/members` | MembersPage | 协调员 | CrudTable + 停用/启用/重置密码 |
| `/admin/products` | ProductsPage | 协调员 | CrudTable + 撤回 |
| `/admin/rounds` | RoundsPage | 协调员 | 轮次表格 + 创建 + 关闭/打包 |
| `/admin/orders` | OrdersPage | 协调员 | 轮次下拉 + 全部订单(展开行明细)+ 代会员下单 |
| `/admin/totals` | TotalsPage | 协调员 | 轮次下拉 + 按商品汇总表 + 合计行 |

菜单:`COORDINATOR` 看到"会员/商品/轮次/订单/汇总",`MEMBER` 看到"下单/我的订单";协调员同时具备会员菜单(其本身也是会员户)。登录后按角色跳转。

---

## 9. 认证与权限

- 登录:`member_no` + 密码(BCrypt 校验),签发 JWT(claims: `memberId`, `role`, 7 天有效)。
- 前端:token 存 localStorage,`Authorization: Bearer <token>`;401 清 token 跳 `/login`。
- 权限规则:
  - 会员只能读写**自己**的订单(`/api/orders/mine` 从 token 取 memberId,不接收 memberId 参数);
  - 协调员接口(会员/商品/轮次管理、轮次订单、汇总、代下单)一律 `@RequireCoordinator`;
  - 商品/轮次的公开读接口仅返回 OPEN 轮次数据。
- 已知限制(写入交接文档):无刷新/吊销机制;`jwt.secret` 默认值在 yml 中(课程范围,生产需环境变量注入)。

---

## 10. 种子数据

由 `DataSeeder` 在**表为空时**写入(避免覆盖协调员在演示前的修改),密码统一 BCrypt(`coop1234`,README 注明为演示值)。

**会员(10 户):**
| member_no | name | role | 备注 |
|-----------|------|------|------|
| M-001 | Ngaire Fletcher | COORDINATOR | 协调员 |
| M-041 | Doug Halvorsen | MEMBER | 财务 |
| M-052 | Bao Nguyen | MEMBER | 拣货志愿协调 |
| M-063 | Jan Buckley | MEMBER | 81 岁,无 email,电话下单 |
| M-077 | Ruth Callaghan | MEMBER | status=INACTIVE,演示停用 |
| M-094 | Ky Tran | MEMBER | 文档 A 的订单 |
| M-118 | Ada Okonkwo | MEMBER | 文档 B 中注意到短供 |
| M-152 | Sepideh Rahimi | MEMBER | 文档 B 未取货 |
| M-102 | Priya Raman | MEMBER | 补充数据 |
| M-127 | Tom Whitfield | MEMBER | 补充数据 |

**商品(12 个,价格取自文档 A / C):**
| 名称 | 销售方式 | 售价 | 货架位 |
|------|---------|------|--------|
| Rolled oats, organic | PER_KG | 3.40 | B1 |
| Brown rice, medium | PER_KG | 4.10 | B3 |
| Red lentils, split | PER_KG | 4.85 | B4 |
| Coffee beans, whole | PER_KG | 32.00 | C2 |
| Tahini, 375g jar | PER_UNIT | 9.80 | A2 |
| Eggs, free range, dozen | PER_UNIT | 7.50 | COOL |
| Pumpkin (Two Creeks) | PER_KG | 2.60 | VEG |
| Olive oil, 1L tin | PER_UNIT | 19.60 | A1 |
| Peanut butter, 500g jar | PER_UNIT | 8.40 | A2 |
| Raw almonds | PER_KG | 18.90 | C1 |
| Carrots (Two Creeks) | PER_KG | 3.20 | VEG |
| Olive oil soap bar | PER_UNIT | 4.20 | A4 |

**轮次与订单:**
- Round 33:`PACKED`,取货 2026-08-13;含 5 笔订单(Halvorsen、Buckley、Tran、Okonkwo、Rahimi),其中 Ky Tran 订单与文档 A 完全一致(7 行,合计 **$54.85**),用于验收计价引擎。
- Round 34:`OPEN`,开/关/取货日期**由 DataSeeder 按运行时刻动态计算**(下一个周五 09:00 开、下一个周日 20:00 关、再下一个周四取货),保证任何时间演示都是"当前轮次",不会过期。

---

## 11. 测试策略

后端 JUnit 5 + Spring Boot Test + MockMvc;`@SpringBootTest` 使用独立 H2 内存库(测试 profile),不污染演示数据。前端不做自动化测试(写入交接文档"已知限制")。

| 测试类 | 覆盖故事 | 要点 |
|--------|---------|------|
| `CoopApplicationTests` | 1 | 上下文启动;`/` 返回前端页面 |
| `AuthApiTest` | 2 | 登录成功/密码错/无 token 401;`/me` |
| `MemberApiTest` | 3 | 增改查、停用后不能登录、会员访问 403 |
| `ProductApiTest` | 4 | 增改、撤回;撤回后不出现在 available;会员写操作 403 |
| `RoundApiTest` | 5 | 创建;第二个 OPEN 被拒;OPEN→CLOSED→PACKED;回退被拒 |
| `CatalogApiTest` | 6 | 只返回 OPEN 轮次 ACTIVE 商品;无 OPEN 轮次返回空+提示 |
| `PricingServiceTest` | 7 | **按件 vs 按公斤**;整数校验;小数位校验;四舍五入;Ky Tran 合计 $54.85;混合订单总额 |
| `OrderApiTest` | 7,8 | 下单/改单/取消;关闭后 PUT/DELETE 被拒;改单不影响他人;每轮一单;价格快照(下单后调价,订单金额不变) |
| `RoundOrdersApiTest` | 9 | 协调员按轮次看全部订单;会员访问 403;空轮次返回空数组 |
| `RoundTotalsApiTest` | 10 | 按商品汇总数量与金额;按公斤与按件混合;空轮次合计为 0 |
| `OrderForMemberApiTest` | 11 | 协调员代下单;会员调用 403;目标会员不存在 404 |

`mvn test` 为 DoD 验收命令;README 写明。

---

## 12. 构建、交付与 Git 流程

### 构建与运行(README 内容)
- 前置:JDK 17(唯一要求;无需 Node/MySQL/Redis)
- 运行:`./mvnw clean package && java -jar target/greenhill-coop-1.0.0.jar` → `http://localhost:8080`
- 测试:`./mvnw test`
- 数据库文件:`./data/greenhill.mv.db`(删除即重置);H2 控制台 `/h2-console`
- 演示账号:M-001(协调员)、M-094 等,密码 `coop1234`
- 开发者重建前端:`./mvnw clean package -Pfrontend`(需 Node 20+,`frontend-maven-plugin` 构建到 `src/main/resources/static`)或 `cd frontend && npm install && npm run build`
- Swagger UI:`/swagger-ui.html`

### Git 流程(满足 DoD 的"每故事一分支 + PR review")
- 每个故事一个分支,分支**基于上一个故事分支**(堆叠):`story/01-foundation` → `story/02-auth` → … → `story/12-delivery`。
- 推送到 GitHub 后,团队按 01→12 顺序创建 PR(base=main)、互相 review、合并;合并完成后 main 即完整应用。
- 本地开发以最后分支为完整应用;`main` 保持初始提交直至 PR 合并。
- 每个故事至少 1 个 commit,message 使用 `feat(story-NN): ...` 格式,便于在 Jira/Confluence 中引用。

### DoD 映射
| DoD 要求 | 本设计对应 |
|----------|-----------|
| 代码提交到 story 分支 | 第 12 节 Git 流程 |
| 验收标准满足并演示 | 第 2.1 节故事表 + 种子数据可现场演示 |
| PR review | 堆叠分支 + GitHub PR |
| 自动化测试(含计价规则) | 第 11 节,`PricingServiceTest` |
| 干净 checkout 可跑 | 第 12 节构建说明,零外部依赖 |
| 无关键缺陷 | 测试覆盖下单/计价/启动 |
| Jira/Confluence 记录 | `docs/jira-import.csv`、`docs/handover.md` 供粘贴 |

---

## 13. 假设与决策(同步到 Confluence)

1. 商品售价为**已含 12% 加价**的最终价,系统不计算毛利。
2. **不做 10 分凑整**,金额按行 2 位四舍五入(HALF_UP)。
3. 每会员每轮次一单;取消后可重新下单(复用订单行,保留取消历史仅到状态层)。
4. 轮次状态单向流转 `OPEN → CLOSED → PACKED`;同一时间至多一个 OPEN。
5. 协调员可为任意 ACTIVE 会员代下单(Jan Buckley 场景)。
6. 会员登录由协调员开户/重置密码;无自助注册。
7. 单价快照存于订单行;商品调价不影响历史订单。
8. 已撤回商品保留在历史订单中,但不可再订购。
9. 界面英文;金额币种为 AUD,存 DECIMAL 不存浮点。
10. 时间以服务器本地时区处理(课程范围,不做时区国际化)。
11. **订购窗口由 `round.status` 判定,不由钟表时间判定**:到点自动关闭/自动开放属 backlog,本冲刺由协调员点击"关闭"(Ngaire 的"截止时间真正生效"通过状态控制实现)。

---

## 14. 已知限制(写入交接文档)

1. JWT 无刷新/吊销;`jwt.secret` 有默认值(生产应环境变量注入)。
2. 无审计日志(谁在何时改了什么)。
3. 无拣货实际重量与短供分配(Bao 的核心诉求之一,已进 backlog)。
4. 无支付、对账、对账单(Doug 的完整诉求,已进 backlog)。
5. 前端无自动化测试;后端通过 MockMvc 覆盖行为。
6. H2 单文件库适合演示与小规模使用,不适合多用户生产环境。
7. 无并发写保护(同一订单并发修改可能后写覆盖,课程范围可接受)。
8. 汇总视图在请求时实时计算,未做缓存。
9. 无定时任务:轮次不会到点自动开放/关闭,需协调员手动操作(已进 backlog)。

---

## 15. 附:故事与验收标准明细

每个故事的完整用户故事 + 验收标准将在实施计划(writing-plans 产出)与 `docs/jira-import.csv` 中给出,与第 2.1 节一一对应,不另设范围。
