# 南鲸商城 — 前端对接文档

> 本文档面向前端开发，描述后端已实现接口的调用方式、鉴权流程、错误处理、枚举值与本地启动方法。
> 接口的完整字段定义以 [`api-spec.md`](./api-spec.md) 为准，本文档侧重「前端怎么用」。
> 最后更新：2026-06-09（任务 7 实时客服 WebSocket + STOMP）。

---

## 0. 最近接口变更（前端需重点调整）

> 本次更新（任务 7 实时客服）含 **0 处破坏性变更** 和 **1 个新模块**。下表为速览，细节见模块十一。

### 🆕 新增能力（任务 7）

| # | 新增 | 接口/协议 | 说明 |
|---|---|---|---|
| 6 | **实时客服 REST** | `GET/POST /api/chat/**` | 会话列表（角色感知）、历史游标分页、接入、释放 |
| 7 | **实时客服 WebSocket** | `ws://host:8080/ws`（STOMP） | 买家/客服实时发消息；客服订阅店铺主题获取新消息和接入/释放通知；JWT 在 CONNECT 帧鉴权 |

---

> 历史变更（任务 4 报表导出、任务 5 多优惠券叠加）含 **2 处破坏性变更** 和 **3 项新增**。下表为速览，细节见对应模块（订单=模块五、优惠券=模块八、报表=模块十）。

### ⚠️ 破坏性变更（必须改，否则功能异常）

| # | 变更 | 影响接口 | 前端要做什么 |
|---|---|---|---|
| 1 | **优惠券类型枚举调整**：原 `AMOUNT_OFF` **拆分为** `FULL_REDUCTION`（满减，有门槛）和 `DIRECT_OFF`（直减，无门槛） | 所有读写券 `type` 的接口：可领券列表、我的优惠券、发布店铺/全局券 | ① 更新 type→文案映射，**删除 `AMOUNT_OFF`**，新增 `FULL_REDUCTION`/`DIRECT_OFF` 两个展示；② 发布券表单的「类型」选项由 2 项改 3 项；③ 满减必须带 `minOrderAmount>0`，直减必须 `minOrderAmount=0`（前端做对应表单校验，否则后端 400） |
| 2 | **下单改为多券**：`POST /api/orders` 请求体 `couponId`（单个 `long`）→ `couponIds`（`long[]` 数组） | 创建订单 | 改字段名与类型；不用券时传 `[]` 或不传。选券 UI 支持多选（受下方叠加规则约束） |

### 🆕 新增能力

| # | 新增 | 接口 | 说明 |
|---|---|---|---|
| 3 | **优惠券试算** | `POST /api/orders/coupon-preview` | 下单前预览「最优能省多少」，结算页选券时实时调用；不创建订单、不核销券 |
| 4 | **多券叠加规则** | 下单 / 试算 | **同类型最多 1 张、不同类型可叠加**（折扣 + 满减 + 直减 各 1 张，最多 3 张）。后台自动按最优顺序计价取**最大折扣**，前端只需把选中的券 ID 全部放进 `couponIds`，无需关心计算顺序 |
| 5 | **报表导出 Excel** | `GET /api/stores/{storeId}/reports/orders/export`、`GET /api/admin/reports/orders/export` | 返回 **xlsx 二进制流（非 JSON）**，需用 blob 方式下载，**不要走统一 JSON 拦截器**（见模块十说明） |

---

## 1. 项目基本信息

| 项 | 值 |
|---|---|
| 后端地址 | `http://localhost:8080` |
| Base URL | `http://localhost:8080/api` |
| 协议 | HTTP / JSON |
| 字符集 | UTF-8（请求体务必以 UTF-8 编码，含中文时尤其注意） |

### 统一响应格式

所有接口（无论成功失败）都返回如下结构：

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| code | int | 业务状态码，见第 3 节。`200` 为成功 |
| message | string | 成功为 `"success"`；失败为错误原因（可直接展示给用户） |
| data | any | 业务数据；失败时为 `null` |

**成功示例：**
```json
{ "code": 200, "message": "success", "data": { "id": 101 } }
```

**错误示例：**
```json
{ "code": 400, "message": "手机号或密码不正确", "data": null }
```

> ⚠️ 注意：HTTP 状态码与业务 `code` 不一定一致。除少数被 Spring Security 拦截的请求（见第 2 节）外，业务层错误大多以 **HTTP 200** 返回、靠响应体里的 `code` 区分。**前端应以响应体的 `code` 字段为准判断成败**，而非 HTTP 状态码。

### 分页响应格式

凡返回列表且分页的接口，`data` 统一为：

```json
{
  "records": [],
  "total": 100,
  "size": 10,
  "current": 1,
  "pages": 10
}
```

| 字段 | 说明 |
|---|---|
| records | 当前页数据数组 |
| total | 总记录数 |
| size | 每页数量 |
| current | 当前页码 |
| pages | 总页数 |

通用分页查询参数：`page`（页码，默认 1）、`size`（每页数量，默认 10）。

> 部分返回**全量列表**的接口（地址列表、分类树、我的优惠券）`data` 直接是数组，不分页，见各接口说明。

---

## 2. 认证说明

### 2.1 登录流程

1. 调用 `POST /api/auth/login`，提交 `{ phone, password }`。
2. 成功返回：

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "bea4241b3def45a7aa2ed7cd5a74f10b",
  "userId": 1,
  "nickname": "张三",
  "role": "CUSTOMER"
}
```

3. 前端保存 `token`、`refreshToken`、`userId`、`role`（建议 localStorage 或内存 + 持久化）。

### 2.2 请求头格式

需要登录的接口，在请求头携带 accessToken：

```
Authorization: Bearer <token>
```

- token 有效期 **24 小时**。
- 无需登录的接口（见 2.4）可不携带；携带了也不影响。

### 2.3 Token 过期处理（refreshToken 换新）

accessToken 过期后，用 refreshToken 换取新 token，无需重新登录：

1. 调用 `POST /api/auth/refresh`，提交 `{ userId, refreshToken }`。
2. 成功返回新的 `token` 和**轮换后的新 `refreshToken`**：

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...（新）",
  "refreshToken": "73e5e2007f8b44058472e391b472b379（新）"
}
```

3. **前端必须用响应里的新 `refreshToken` 覆盖本地旧值** —— 每次刷新都会轮换，旧 refreshToken 立即失效。

**推荐的前端拦截器逻辑：**

```
请求返回 code=401（或 token 解析失败）
  → 若本地有 refreshToken，调用 /api/auth/refresh
      → 成功：更新 token + refreshToken，重放原请求
      → 失败（401）：清除本地登录态，跳转登录页
  → 若无 refreshToken：直接跳转登录页
```

- refreshToken 有效期 **7 天**（存于服务端 Redis）。超过 7 天或刷新失败（返回 `401`）→ 必须重新登录。

### 2.4 无需携带 token 的接口（游客可访问）

| 方法 | 路径 |
|---|---|
| POST | `/api/auth/register` |
| POST | `/api/auth/login` |
| POST | `/api/auth/refresh` |
| GET | `/api/categories` |
| GET | `/api/stores` |
| GET | `/api/stores/{storeId}` |
| GET | `/api/stores/{storeId}/products` |
| GET | `/api/products` |
| GET | `/api/products/{productId}` |
| GET | `/api/products/{productId}/reviews` |
| GET | `/api/coupon-groups` |

> 其余所有接口都需要登录态。

---

## 3. 错误码说明

| code | 含义 | 典型场景 | 前端建议处理 |
|---|---|---|---|
| 200 | 成功 | 正常返回 | 取 `data` 使用 |
| 400 | 参数错误 / 业务校验失败 | 字段格式不对、库存不足、状态不允许操作等 | 弹出 `message` 提示用户 |
| 401 | 未登录 / Token 无效或过期 | 未带 token、token 过期、refreshToken 失效 | 触发刷新或跳转登录 |
| 403 | 无权限 | 角色不符、Staff 操作他店资源、访问他人数据 | 提示「无权限」，不可重试 |
| 404 | 资源不存在 | 商品 / 订单 / 地址 / 分类等不存在 | 提示资源不存在 |
| 500 | 服务器内部错误 | 未预期异常、请求体非法（如非 UTF-8 / JSON 解析失败） | 提示「服务繁忙，请稍后重试」 |

> **关于 HTTP 状态码：** 未登录访问受保护接口时，请求被 Spring Security 在进入业务层前拦截，此时返回 **HTTP 403**（空响应体）。带了无效/过期 token 同理。前端拦截器应把「HTTP 401/403 且响应体为空」也并入「需要重新登录/刷新」分支处理。

### 建议的统一错误处理

```js
// 伪代码：axios 响应拦截器
api.interceptors.response.use(
  (resp) => {
    const { code, message, data } = resp.data;
    if (code === 200) return data;
    if (code === 401) return tryRefreshThenRetry(resp.config); // 见 2.3
    if (code === 403) toast('无权限执行该操作');
    else toast(message || '请求失败');
    return Promise.reject(resp.data);
  },
  (err) => {
    // HTTP 403/401（被 Security 拦截，无响应体）→ 当作未登录
    if (err.response && [401, 403].includes(err.response.status) && !err.response.data) {
      return tryRefreshThenRetry(err.config);
    }
    toast('网络或服务异常');
    return Promise.reject(err);
  }
);
```

---

## 4. 各模块接口清单

> 「角色」列：无需登录 = 游客；需要登录 = 任意已登录用户；其余为特定角色。
> Staff 操作店铺资源时，路径里的 `storeId` 必须是其所属店铺，否则 `403`。
> 请求/响应字段的完整定义见 [`api-spec.md`](./api-spec.md)。

### 模块一：用户与认证

| 接口 | 方法 + 路径 | 角色 | 关键参数 | 响应 data | 常见错误 |
|---|---|---|---|---|---|
| 注册 | POST `/api/auth/register` | 游客 | body: `phone`(11位,必), `password`(6-20,必), `nickname`(2-20,必), `role`(CUSTOMER/STAFF,必) | `null` | 手机号已注册→400；格式错→400 |
| 登录 | POST `/api/auth/login` | 游客 | body: `phone`(必), `password`(必) | `{token, refreshToken, userId, nickname, role}` | 手机号或密码不正确→400 |
| 刷新 Token | POST `/api/auth/refresh` | 游客（凭 refreshToken） | body: `userId`(必), `refreshToken`(必) | `{token, refreshToken}` | refreshToken 无效/过期→401 |
| 当前用户信息 | GET `/api/users/me` | 需要登录 | — | `{id, phone(脱敏), nickname, role, storeId}` | 未登录→401/403 |
| 修改昵称 | PUT `/api/users/me` | 需要登录 | body: `nickname` | `null` | — |
| 修改密码 | PUT `/api/users/me/password` | 需要登录 | body: `oldPassword`, `newPassword`, `confirmPassword` | `null` | 两次新密码不一致→400；旧密码错→400 |

> `GET /api/users/me` 的 `phone` 已脱敏为 `138****8000`；`storeId` 仅 STAFF 有值。

### 模块二：商店与商品浏览（游客可访问）

| 接口 | 方法 + 路径 | 角色 | 关键参数 | 响应 data | 常见错误 |
|---|---|---|---|---|---|
| 商店列表 | GET `/api/stores` | 游客 | query: `page`,`size` | 分页：`{id,name,logo,productCount}` | — |
| 商店详情 | GET `/api/stores/{storeId}` | 游客 | path: `storeId` | `{id,name,creditCode,logo}` | 商店不存在→404 |
| 店内商品列表 | GET `/api/stores/{storeId}/products` | 游客 | path: `storeId`；query: `page,size,categoryId,keyword,minPrice,maxPrice`(均可选) | 分页：`{id,name,price,stock,imageUrl,categoryName}` | 商店不存在→404 |
| 全局商品搜索 | GET `/api/products` | 游客 | query: `keyword,categoryId,minPrice,maxPrice,page,size`(均可选) | 同上（分页） | — |
| 商品详情 | GET `/api/products/{productId}` | 游客 | path: `productId` | `{id,name,price,stock,imageUrl,category{id,name,parentId,parentName},storeId,storeName,averageRating,reviewCount}` | 商品不存在→404 |
| 分类树 | GET `/api/categories` | 游客 | — | 数组（嵌套）：`[{id,name,parentId,children[]}]` | — |

> `averageRating` 无评论时为 `null`（不是 0）；`category` 可能为 `null`（商品未设分类）。

### 模块三：商品管理（Staff / Admin）

| 接口 | 方法 + 路径 | 角色 | 关键参数 | 响应 data | 常见错误 |
|---|---|---|---|---|---|
| 新建商品 | POST `/api/stores/{storeId}/products` | Staff（本店） | body: `name`,`price`,`stock`,`categoryId`,`imageUrl`(可选) | `{id}` | 非本店→403；分类不存在→404 |
| 编辑商品 | PUT `/api/stores/{storeId}/products/{productId}` | Staff（本店） | body: 字段均可选 | `null` | 商品不存在/越权→404；分类不存在→404 |
| 删除商品 | DELETE `/api/stores/{storeId}/products/{productId}` | Staff（本店） | path | `null` | 有未完成订单→400；商品不存在→404 |
| 更新库存 | PUT `/api/stores/{storeId}/products/{productId}/stock` | Staff（本店） | body: `delta`(正整数), `type`(IN/OUT) | `{currentStock}` | 出库超库存→400 |
| 新建分类 | POST `/api/categories` | Staff 或 Admin | body: `name`, `parentId`(可选,null 为顶级) | `{id}` | 父分类不存在→404；同级重名→400 |
| 删除分类 | DELETE `/api/categories/{categoryId}` | Admin | path | `null` | 存在子分类→400；存在商品→400；不存在→404 |

### 模块四：购物车（Customer）

| 接口 | 方法 + 路径 | 角色 | 关键参数 | 响应 data | 常见错误 |
|---|---|---|---|---|---|
| 查看购物车 | GET `/api/cart` | Customer | — | `{items:[{id,productId,productName,imageUrl,price,quantity,stock,subtotal}], total}` | — |
| 加入购物车 | POST `/api/cart/items` | Customer | body: `productId`, `quantity` | `null` | 商品不存在→404；超库存→400 |
| 修改数量 | PUT `/api/cart/items/{itemId}` | Customer | body: `quantity`（0 表示删除该条目） | `null` | 条目不存在→404；非本人→403；超库存→400 |
| 删除条目 | DELETE `/api/cart/items/{itemId}` | Customer | path | `null` | 条目不存在→404；非本人→403 |

> 已下架商品在购物车中显示为 `name="（商品已下架）"`、`price=0`、`subtotal=0`，不计入 `total`。

### 模块五：订单

| 接口 | 方法 + 路径 | 角色 | 关键参数 | 响应 data | 常见错误 |
|---|---|---|---|---|---|
| 创建订单 | POST `/api/orders` | Customer | body: `cartItemIds`(long[],必), `addressId`(必), **`couponIds`(long[],可选)** | `{orderId,totalAmount,discountAmount,payableAmount}` | 购物车条目/地址/商品不存在→404/400；库存不足→400；券不可用/未达门槛/同类型多张/不适用本店→400 |
| **优惠券试算** | POST `/api/orders/coupon-preview` | Customer | body: `cartItemIds`(long[],必), `couponIds`(long[],可选) | `{totalAmount,discountAmount,payableAmount,appliedCouponIds}` | 同下单的券校验错误→400 |
| 我的订单 | GET `/api/orders` | Customer | query: `status`(可选), `page`,`size` | 分页：`{id,status,payableAmount,createdAt,storeName,itemCount}` | — |
| 订单详情 | GET `/api/orders/{orderId}` | Customer（本人）/ Staff（本店） | path | `{id,status,totalAmount,discountAmount,payableAmount,createdAt,paidAt,address{...},items[...]}` | 不存在/越权→404/403 |
| 支付 | POST `/api/orders/{orderId}/pay` | Customer（本人） | body `{}` | `{status:"PAID", paidAt}` | 状态非 PENDING_PAYMENT→400 |
| 取消 | POST `/api/orders/{orderId}/cancel` | Customer（本人） | body `{}` | `{status:"CANCELLED", refunded}` | 状态非 PENDING_PAYMENT/PAID→400 |
| 确认收货 | POST `/api/orders/{orderId}/confirm` | Customer（本人） | body `{}` | `{status:"COMPLETED"}` | 状态非 SHIPPED→400 |
| 申请退款 | POST `/api/orders/{orderId}/refund` | Customer（本人） | body: `reason` | `{status:"CANCELLED", refundedAt}` | 状态非 COMPLETED→400 |
| 门店订单列表 | GET `/api/stores/{storeId}/orders` | Staff（本店） | query: `status`,`page`,`size` | 分页（同我的订单） | 非本店→403 |
| 发货 | POST `/api/stores/{storeId}/orders/{orderId}/ship` | Staff（本店） | body: `trackingNumber`, `carrier` | `{status:"SHIPPED", shippedAt}` | 状态非 PAID→400；非本店→403 |

> 取消/退款响应里 `refunded` / `refundedAt`：已支付订单取消时 `refunded=true`，未支付时 `false`。取消和退款都会恢复库存并把已用优惠券（**含多张**）退回 UNUSED。
>
> **多优惠券叠加（重点）：**
> - 一笔订单可同时使用 **折扣(DISCOUNT) + 满减(FULL_REDUCTION) + 直减(DIRECT_OFF) 各一张**，最多 3 张；**同类型只能选 1 张**（选了两张折扣券会 400）。
> - 前端把所有选中券 ID 放进 `couponIds` 即可，**顺序无所谓**——后端会穷举所有先后组合，返回**折扣最大**的方案。
> - **门槛（满减券 `minOrderAmount`）按订单原始总额判定**：达标与否在选券时即可确定，不会因为先用了折扣把金额压低而失效。任一选中券不达门槛/不适用本店，整单报 400（`message` 会指明是哪张券）。
> - **店铺券只能用于该店订单**（全局券 `storeId=null` 不限）。当前订单为单店订单。
> - **建议流程**：结算页用户勾选券 → 调 `POST /api/orders/coupon-preview` 实时展示「预计优惠 `discountAmount` / 实付 `payableAmount`」→ 用户确认后调 `POST /api/orders` 下单。下单时后端会**重新计算**，不依赖试算结果（试算的 `appliedCouponIds` 仅供展示最优顺序）。

### 模块六：收货地址（Customer）

| 接口 | 方法 + 路径 | 角色 | 关键参数 | 响应 data | 常见错误 |
|---|---|---|---|---|---|
| 地址列表 | GET `/api/addresses` | Customer | — | **数组**（不分页）：`[{id,receiverName,phone,province,city,district,detail,isDefault}]` | — |
| 新增地址 | POST `/api/addresses` | Customer | body: `receiverName,phone,province,city,district,detail,isDefault` | `{id}` | — |
| 修改地址 | PUT `/api/addresses/{addressId}` | Customer（本人） | body: 字段均可选 | `null` | 不存在→404；非本人→403 |
| 删除地址 | DELETE `/api/addresses/{addressId}` | Customer（本人） | path | `null` | 不存在→404；非本人→403 |

> 首条地址自动成为默认；设某地址为默认会自动取消其他默认；删除默认地址后自动把最近创建的地址设为默认。地址列表按「默认在前、创建时间倒序」排序。

### 模块七：评论

| 接口 | 方法 + 路径 | 角色 | 关键参数 | 响应 data | 常见错误 |
|---|---|---|---|---|---|
| 评论列表 | GET `/api/products/{productId}/reviews` | 游客 | path；query: `page,size` | 分页：`{id,userId,userNickname,rating,content,createdAt,parentId,replies[{id,userId,userNickname,content,createdAt}]}` | 商品不存在→404 |
| 发表评论 | POST `/api/products/{productId}/reviews` | Customer | body: `orderId`, `rating`(1-5), `content` | `{id}` | 无对应已完成订单→400；订单不含该商品→400；重复评论→400 |
| 回复评论 | POST `/api/reviews/{reviewId}/replies` | Customer | body: `content` | `{id}` | 评论不存在→404；对回复再回复→400（不支持多级） |

> 评论列表里 `parentId=null` 为一级评价（含 `rating`），`replies` 为其回复列表（不分页、按时间升序）。回复无购买限制。

### 模块八：优惠券

| 接口 | 方法 + 路径 | 角色 | 关键参数 | 响应 data | 常见错误 |
|---|---|---|---|---|---|
| 可领券列表 | GET `/api/coupon-groups` | 游客（领取需登录） | query: `page,size` | 分页：`{id,name,type,value,minOrderAmount,expireAt,totalCount,remainCount,storeId,storeName}` | — |
| 领取优惠券 | POST `/api/coupon-groups/{groupId}/claim` | Customer | path | `{couponId}` | 券组不存在→404；已抢光/已领过/已过期→400 |
| 我的优惠券 | GET `/api/coupons/mine` | Customer | query: `status`(UNUSED/USED/EXPIRED,可选) | **数组**：`[{id,groupName,type,value,minOrderAmount,expireAt,status,storeId}]` | — |
| 发布店铺券 | POST `/api/stores/{storeId}/coupon-groups` | Staff（本店） | body: `name,type,value,minOrderAmount,totalCount,startAt,expireAt` | `{id}` | 非本店→403；type/value/门槛不合法→400 |
| 发布全局券 | POST `/api/coupon-groups` | Admin | body: 同上（无 storeId） | `{id}` | 非 Admin→403；type/value/门槛不合法→400 |
| 删除券组 | DELETE `/api/coupon-groups/{groupId}` | Staff（本店券）/ Admin（全局券） | path | `null` | 越权→403；不存在→404 |

> **券类型 `type`（已由 2 类拆为 3 类）：**
> - `DISCOUNT`（折扣券）：`value` 为折扣率（0.5=五折，须 0<value<1）；`minOrderAmount` 任意。
> - `FULL_REDUCTION`（满减券）：`value` 为减免金额（元，>0）；**`minOrderAmount` 必须 >0**（即门槛）。
> - `DIRECT_OFF`（直减券）：`value` 为减免金额（元，>0）；**`minOrderAmount` 必须 =0**（无门槛）。
>
> `storeId=null` 为全局券。发布券表单请按上述约束做前端校验，否则后端返回 400。

### 模块九：商店管理（Admin）

| 接口 | 方法 + 路径 | 角色 | 关键参数 | 响应 data | 常见错误 |
|---|---|---|---|---|---|
| 创建商店 | POST `/api/stores` | Admin | body: `name,creditCode,logo,staffPhone` | `{storeId}` | staffPhone 未注册/非 STAFF→400 |
| 修改商店 | PUT `/api/stores/{storeId}` | Admin | body: `name,logo`（均可选） | `null` | 商店不存在→404 |
| 所有商店 | GET `/api/admin/stores` | Admin | query: `page,size` | 分页（同商店列表 + `creditCode`） | 非 Admin→403 |

> 创建商店时 `staffPhone` 必须是**已注册且角色为 STAFF**的账号；创建后该 Staff 的 `storeId` 被绑定到新店。

### 模块十：报表

| 接口 | 方法 + 路径 | 角色 | 关键参数 | 响应 data | 常见错误 |
|---|---|---|---|---|---|
| 门店订单报表 | GET `/api/stores/{storeId}/reports/orders` | Staff（本店） | query: `startDate,endDate`(yyyy-MM-dd,可选) | `{totalOrders,totalRevenue,averageOrderAmount,statusBreakdown{...},dailyRevenue[{date,revenue,orders}]}` | 非本店→403；起始晚于截止→400 |
| 全局汇总报表 | GET `/api/admin/reports/orders` | Admin | query: 同上 | `{totalOrders,totalRevenue,storeBreakdown[{storeId,storeName,orders,revenue}],dailyRevenue[...]}` | 非 Admin→403 |
| **门店报表导出 xlsx** | GET `/api/stores/{storeId}/reports/orders/export` | Staff（本店） | query: 同上 | **xlsx 二进制流**（非 JSON） | 非本店→403 |
| **全局报表导出 xlsx** | GET `/api/admin/reports/orders/export` | Admin | query: 同上 | **xlsx 二进制流**（非 JSON） | 非 Admin→403 |

> 日期缺省：`endDate` 默认今天，`startDate` 默认截止前 30 天。`totalOrders` 含所有状态，`totalRevenue` 仅统计 COMPLETED 订单。
>
> **导出接口（前端特殊处理）：** 返回的是 Excel 文件流，不是统一 JSON 结构，**不能走第 3 节的 JSON 响应拦截器**，否则会解析失败。请单独发起请求（仍需带 `Authorization` 头）并以 blob 接收下载：
> - 工作簿含 3 个 Sheet：汇总 / 状态分布(门店)·门店分布(全局) / 每日收入。
> - 响应头 `Content-Disposition` 自带文件名（如 `store-1-order-report.xlsx`）。
> - 鉴权失败（非本店/非 Admin/未登录）时仍返回 **JSON 错误体**（此时不是文件流），前端可据 `Content-Type` 判断：是 `application/vnd.openxmlformats-...` 即文件、否则按 JSON 解析错误。
>
> ```js
> // 示例：axios blob 下载（绕过 JSON 拦截器，单独实例）
> const resp = await axios.get(`/api/stores/${storeId}/reports/orders/export`, {
>   params: { startDate, endDate },
>   headers: { Authorization: `Bearer ${token}` },
>   responseType: 'blob',
> });
> const url = URL.createObjectURL(resp.data);
> const a = document.createElement('a');
> a.href = url; a.download = 'order-report.xlsx'; a.click();
> URL.revokeObjectURL(url);
> ```

### 模块十一：实时客服

#### 11.1 REST 接口

| 接口 | 方法 + 路径 | 角色 | 关键参数 | 响应 data | 常见错误 |
|---|---|---|---|---|---|
| 会话列表 | GET `/api/chat/sessions` | 需要登录 | — | 数组（角色感知，见下） | — |
| 历史消息 | GET `/api/chat/sessions/{id}/messages` | 需要登录 | query: `before`(游标，可选), `size`(默认20) | 数组（按 id 倒序） | 不存在/越权→404/403 |
| 接入会话 | POST `/api/chat/sessions/{id}/claim` | Staff（本店） | body `{}` | `null` | 已被他人接待→400；非本店→403 |
| 释放会话 | POST `/api/chat/sessions/{id}/release` | Staff（本店，assignee） | body `{}` | `null` | 非 assignee→403 |

**会话列表响应字段（Customer 视角）：**

```json
[{
  "sessionId": 1, "storeId": 1, "storeName": "南鲸旗舰店",
  "lastMessage": "你好", "lastMessageAt": "2026-06-09T10:00:00",
  "staffOnline": true
}]
```

**会话列表响应字段（Staff 视角）：**

```json
[{
  "sessionId": 1, "customerId": 3, "customerNickname": "测试顾客",
  "customerOnline": true, "assigneeStaffId": null, "assigneeNickname": null,
  "lastMessage": "你好", "lastMessageAt": "2026-06-09T10:00:00"
}]
```

> `assigneeStaffId=null`=未接入；等于当前 staffId=本人接待；其他值=他人接待。

**历史消息响应字段：**

```json
[{ "id": 42, "sessionId": 1, "senderRole": "CUSTOMER", "senderId": 3,
   "content": "你好", "createdAt": "2026-06-09T10:00:00" }]
```

> 游标翻页：用本次返回的最小 `id` 作为下次请求的 `before` 值，可持续向上翻历史。

#### 11.2 WebSocket 连接与订阅

**连接（STOMP over WebSocket）：**

```js
import { Client } from '@stomp/stompjs';

const client = new Client({
  brokerURL: 'ws://localhost:8080/ws',
  connectHeaders: {
    Authorization: `Bearer ${token}`,   // JWT，必须
  },
  onConnect: () => {
    // 连接成功，开始订阅
  },
  onStompError: (frame) => {
    // 连接被拒（token 无效）
  },
});
client.activate();
```

**订阅（买家）：**
```js
// 收取客服消息
client.subscribe('/user/queue/messages', (msg) => {
  const data = JSON.parse(msg.body); // ChatMessageResponse
});
// 错误回执
client.subscribe('/user/queue/errors', (msg) => {
  const err = JSON.parse(msg.body); // {code, message}
});
```

**订阅（客服）：**
```js
// 收取分配给自己的买家消息
client.subscribe('/user/queue/messages', (msg) => { ... });
// 店铺主题：未接入新消息 + 接入/释放事件
client.subscribe(`/topic/store.${storeId}`, (msg) => {
  const event = JSON.parse(msg.body); // StoreTopicEvent
  // event.type: "MESSAGE" | "CLAIMED" | "RELEASED"
});
// 错误回执
client.subscribe('/user/queue/errors', (msg) => { ... });
```

#### 11.3 发送消息

**买家发消息：**
```js
client.publish({
  destination: '/app/chat.customer.send',
  body: JSON.stringify({ storeId: 1, content: '你好，请问有货吗？' }),
});
```

**客服发消息：**
```js
client.publish({
  destination: '/app/chat.staff.send',
  body: JSON.stringify({ sessionId: 1, content: '您好，有货的！' }),
});
```

#### 11.4 店铺主题事件（StoreTopicEvent）

| `type` | 含义 | 关键字段 | 客服端建议处理 |
|---|---|---|---|
| `MESSAGE` | 未接入会话有新消息 | `sessionId`, `message`(ChatMessageResponse) | 刷新待接入会话列表，展示新消息提醒 |
| `CLAIMED` | 会话已被某客服接入 | `sessionId`, `staffId` | 从待接入列表移除；更新该会话的 `assigneeStaffId` |
| `RELEASED` | 会话已释放回公共池 | `sessionId` | 重新显示在待接入列表 |

#### 11.5 注意事项

- **token 续期**：WebSocket 连接建立后 token 过期不会自动断开，但建议在页面加载时先确保 accessToken 有效（调 `/api/auth/refresh`），再建立 WS 连接；或在 onStompError 时重连。
- **当前实现不发 STOMP ERROR 帧**：token 无效时后端拒绝 CONNECT，WS 连接关闭，客户端收不到结构化 STOMP ERROR 帧（后续版本会补），可监听 `onDisconnect` / `onWebSocketClose` 作为失败信号。
- **消息顺序**：同一会话内消息按 `id`（自增）有序；`id` 可直接用于游标分页。
- **建议联调顺序**：① ADMIN 建商店，STAFF 绑定；② CUSTOMER 登录，连接 WS，发第一条消息建会话；③ STAFF 登录，连接 WS，订阅店铺主题，收到 `StoreTopicEvent{type:MESSAGE}`；④ STAFF 调 `/claim` 接入；⑤ 双向收发消息；⑥ STAFF 调 `/release` 释放。

---

## 5. 枚举值说明

| 枚举 | 取值 | 含义 |
|---|---|---|
| 用户角色 `role` | `CUSTOMER` | 顾客 |
| | `STAFF` | 门店工作人员（绑定一个 storeId） |
| | `ADMIN` | 商场管理员 |
| 订单状态 `status` | `PENDING_PAYMENT` | 待付款 |
| | `PAID` | 已付款（待发货） |
| | `SHIPPED` | 已发货（待收货） |
| | `COMPLETED` | 已完成（已收货） |
| | `CANCELLED` | 已取消 / 已退款 |
| 优惠券类型 `type` | `DISCOUNT` | 折扣券，`value` 为折扣率（0.5=五折，0<value<1） |
| | `FULL_REDUCTION` | 满减券（有门槛），`value` 为减免金额（元），`minOrderAmount`>0 |
| | `DIRECT_OFF` | 直减券（无门槛），`value` 为减免金额（元），`minOrderAmount`=0 |
| 优惠券状态 `status` | `UNUSED` | 未使用 |
| | `USED` | 已使用 |
| | `EXPIRED` | 已过期 |
| 库存操作 `type` | `IN` | 入库（增加库存） |
| | `OUT` | 出库（减少库存） |

**订单状态流转：**
```
PENDING_PAYMENT --pay--> PAID --ship(Staff)--> SHIPPED --confirm--> COMPLETED --refund--> CANCELLED
       └──────────cancel──────────┘ (PENDING_PAYMENT / PAID 可取消 → CANCELLED)
```

---

## 6. 本地开发说明

### 6.1 环境依赖

| 依赖 | 版本 / 说明 |
|---|---|
| JDK | 17 |
| 构建工具 | Maven |
| MySQL | 8.x，监听 **3305** 端口，数据库名 `bluewhale` |
| Redis | 6.x+，监听 **6379** 端口（用于存 refreshToken） |

默认连接配置（见 `src/main/resources/application.yml`，可用环境变量覆盖）：

| 配置 | 默认值 | 环境变量 |
|---|---|---|
| MySQL URL | `jdbc:mysql://localhost:3305/bluewhale` | — |
| MySQL 用户/密码 | `root` / `123456` | — |
| Redis | `localhost:6379`，无密码，db 0 | `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` / `REDIS_DATABASE` |
| JWT 密钥 | 内置开发默认值 | `JWT_SECRET`（Base64，≥256 位） |
| JWT 有效期(ms) | `86400000`（24h） | `JWT_EXPIRATION` |

### 6.2 数据库初始化

数据库结构与种子数据由 **Flyway 自动迁移**管理，无需再手动执行 SQL：

1. 创建一个**空库**：`CREATE DATABASE bluewhale DEFAULT CHARSET utf8mb4;`
2. 确保 `application.yml` 的数据源指向该库（默认端口 3305）。
3. 启动应用——Flyway 会自动执行 `src/main/resources/db/migration/` 下的脚本：
   - `V1__init_schema.sql`：建 12 张表；
   - `V2__add_product_version.sql`：商品加乐观锁 `version` 列；
   - `V3__coupon_stacking.sql`：券类型拆分（满减/直减）+ 建 `order_coupon` 关联表（多券）；
   - `V4__chat.sql`：建 `chat_session` + `chat_message` 两张表（实时客服，任务 7）；
   - `R__seed_data.sql`：灌入测试商店 / 账号 / 分类 / 商品。

> 迁移脚本是单一可执行来源，原 `docs/schema.sql` / `docs/data.sql` 已迁入上述目录。
> 表结构使用逻辑删除（`deleted` 字段，0 正常 / 1 删除），MyBatis Plus 自动过滤。
> 详见 [数据库迁移指南.md](./数据库迁移指南.md)。

### 6.3 启动后端服务

```bash
# 编译（跳过测试）
mvn clean package -DskipTests

# 运行
mvn spring-boot:run
```

启动成功后控制台出现 `Started BluewhaleApplication`，服务监听 `http://localhost:8080`。

```bash
# 运行测试
mvn test
```

### 6.4 测试账号说明

#### 预置种子账号（Flyway 启动时自动灌入，可直接登录）

迁移脚本 `R__seed_data.sql` 会写入下列账号（密码为真实 BCrypt 哈希，可直接登录）及一个测试商店「南鲸旗舰店」、两级商品分类和若干测试商品：

| 角色     | 手机号        | 明文密码          | 昵称       | 备注                 |
| -------- | ------------- | ----------------- | ---------- | -------------------- |
| ADMIN    | `13000000000` | `Admin@123456`    | 超级管理员 | —                    |
| STAFF    | `13100000001` | `Staff@123456`    | 测试店员   | 已绑定「南鲸旗舰店」 |
| CUSTOMER | `13200000002` | `Customer@123456` | 测试顾客   | —                    |

> 种子数据放在 Flyway 可重复迁移 `R__seed_data.sql` 中，脚本开头会先清空相关表再插入，启动时自动应用；需要改种子时直接改该文件并重启即可，无需手动跑 SQL。

如需手动获取额外账号，可按下面三种方式：

注册接口**只允许 `CUSTOMER` / `STAFF`**（ADMIN 不开放注册）。三种角色的获取方式：

**① CUSTOMER（顾客）** — 直接注册即可：
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"phone":"13800138001","password":"Abc123456","nickname":"测试顾客","role":"CUSTOMER"}'
```

**② STAFF（店员）** — 先注册为 STAFF，再由 ADMIN 通过「创建商店」把它绑定到某店：
```bash
# 1) 注册 staff 账号
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"phone":"13900139001","password":"Abc123456","nickname":"测试店员","role":"STAFF"}'
# 2) 用 ADMIN 登录后调用 POST /api/stores，body 里 staffPhone 填上面的手机号，
#    创建成功后该 staff 的 storeId 即被绑定。
```

**③ ADMIN（管理员）** — 不能注册，需直接在数据库创建。最简单的做法：先注册一个普通账号，再用 SQL 改其角色为 ADMIN（密码沿用注册时的 BCrypt 哈希）：
```sql
-- 先用接口注册 13700137001 / Abc123456，再执行：
UPDATE `user` SET role = 'ADMIN', store_id = NULL WHERE phone = '13700137001';
```
> 预置 ADMIN（手机号 `13000000000`）由 `R__seed_data.sql` 写入，密码为真实哈希 `Admin@123456`，可直接登录，无需再手动改库。
>
> 注：ADMIN 手机号特意用 `13000000000`（而非早期占位的 `10000000000`），因为登录/注册接口对 `phone` 强制 `^1[3-9]\d{9}$` 格式校验，`10` 开头的号码会被 `@Valid` 直接拒绝、根本进不到登录逻辑。

### 6.5 联调建议顺序

1. 注册 + 登录，拿到 token，验证刷新流程。
2. 用 ADMIN 建分类、建商店（绑定 staff）。
3. 用 STAFF 上架商品、设库存。
4. 用 CUSTOMER 浏览 → 加购 → 建地址 → 领券 → 下单 → 支付。
5. STAFF 发货 → CUSTOMER 确认收货 → 评论 → 退款。
6. STAFF / ADMIN 查看报表。
