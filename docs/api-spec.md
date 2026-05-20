# 南鲸商城 — API 设计文档

## 约定

### Base URL
```
/api
```

### 认证方式
需要登录的接口在请求头中携带 JWT Token：
```
Authorization: Bearer <token>
```

### 权限标记说明

| 标记 | 含义 |
|---|---|
| 无需登录 | 游客可访问 |
| 需要登录 | 任意已登录用户 |
| 仅 Customer | 角色为顾客 |
| 仅 Staff | 角色为门店工作人员（且只能操作本店资源） |
| 仅 Admin | 角色为商场管理员 |

### 统一响应格式

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

**错误响应示例：**
```json
{
  "code": 400,
  "message": "手机号格式不正确",
  "data": null
}
```

### 分页响应格式（凡返回列表的接口统一使用）

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "records": [],
    "total": 100,
    "size": 10,
    "current": 1,
    "pages": 10
  }
}
```

### 通用分页查询参数

| 参数 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `page` | int | 1 | 当前页码 |
| `size` | int | 10 | 每页数量 |

---

## 模块一：用户模块

### POST /api/auth/register — 用户注册

**权限：** 无需登录

**请求体：**
```json
{
  "phone": "13800138000",
  "password": "Abc123456",
  "nickname": "张三",
  "role": "CUSTOMER"
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| phone | string | 是 | 手机号，11位，唯一 |
| password | string | 是 | 密码，6-20位 |
| nickname | string | 是 | 用户昵称 |
| role | string | 是 | 枚举：`CUSTOMER` / `STAFF`（Admin 不开放注册） |

**响应 data：**
```json
null
```

---

### POST /api/auth/login — 用户登录

**权限：** 无需登录

**请求体：**
```json
{
  "phone": "13800138000",
  "password": "Abc123456"
}
```

**响应 data：**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "userId": 1,
  "nickname": "张三",
  "role": "CUSTOMER"
}
```

---

### GET /api/users/me — 获取当前用户信息

**权限：** 需要登录

**请求参数：** 无

**响应 data：**
```json
{
  "id": 1,
  "phone": "138****8000",
  "nickname": "张三",
  "role": "CUSTOMER",
  "storeId": null
}
```

> Staff 用户的 `storeId` 为其所属商店 ID，Customer/Admin 为 null。

---

### PUT /api/users/me — 修改个人信息

**权限：** 需要登录

**请求体：**
```json
{
  "nickname": "新昵称"
}
```

**响应 data：** `null`

---

### PUT /api/users/me/password — 修改密码

**权限：** 需要登录

**请求体：**
```json
{
  "oldPassword": "Abc123456",
  "newPassword": "NewPass789",
  "confirmPassword": "NewPass789"
}
```

**响应 data：** `null`

---

## 模块二：商店与商品浏览

### GET /api/stores — 商店列表

**权限：** 无需登录

**Query 参数：** `page`, `size`

**响应 data（分页）：**
```json
{
  "records": [
    {
      "id": 1,
      "name": "南鲸旗舰店",
      "logo": "https://cdn.example.com/logo.png",
      "productCount": 42
    }
  ],
  "total": 5,
  "size": 10,
  "current": 1,
  "pages": 1
}
```

---

### GET /api/stores/{storeId} — 商店详情

**权限：** 无需登录

**路径参数：** `storeId`

**响应 data：**
```json
{
  "id": 1,
  "name": "南鲸旗舰店",
  "creditCode": "91320100...",
  "logo": "https://cdn.example.com/logo.png"
}
```

---

### GET /api/stores/{storeId}/products — 商店商品列表

**权限：** 无需登录

**路径参数：** `storeId`

**Query 参数：**

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| page | int | 否 | 默认 1 |
| size | int | 否 | 默认 10 |
| categoryId | long | 否 | 按分类筛选 |
| keyword | string | 否 | 商品名称模糊搜索 |
| minPrice | decimal | 否 | 最低价格 |
| maxPrice | decimal | 否 | 最高价格 |

**响应 data（分页）：**
```json
{
  "records": [
    {
      "id": 101,
      "name": "国产老陈醋",
      "price": 12.80,
      "stock": 200,
      "imageUrl": "https://cdn.example.com/product.jpg",
      "categoryName": "调味品"
    }
  ],
  "total": 42,
  "size": 10,
  "current": 1,
  "pages": 5
}
```

---

### GET /api/products/{productId} — 商品详情

**权限：** 无需登录

**路径参数：** `productId`

**响应 data：**
```json
{
  "id": 101,
  "name": "国产老陈醋",
  "price": 12.80,
  "stock": 200,
  "imageUrl": "https://cdn.example.com/product.jpg",
  "category": {
    "id": 5,
    "name": "调味品",
    "parentId": 2,
    "parentName": "食品"
  },
  "storeId": 1,
  "storeName": "南鲸旗舰店",
  "averageRating": 4.5,
  "reviewCount": 18
}
```

---

### GET /api/products — 全局商品搜索

**权限：** 无需登录

**Query 参数：**

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| keyword | string | 否 | 商品名称关键词 |
| categoryId | long | 否 | 分类 ID |
| minPrice | decimal | 否 | 最低价格 |
| maxPrice | decimal | 否 | 最高价格 |
| page | int | 否 | 默认 1 |
| size | int | 否 | 默认 10 |

**响应 data：** 同商店商品列表（分页格式）

---

### GET /api/categories — 商品分类树

**权限：** 无需登录

**响应 data：**
```json
[
  {
    "id": 1,
    "name": "食品",
    "parentId": null,
    "children": [
      {
        "id": 3,
        "name": "饮料",
        "parentId": 1,
        "children": []
      },
      {
        "id": 4,
        "name": "零食",
        "parentId": 1,
        "children": []
      }
    ]
  }
]
```

---

## 模块三：商品管理（Staff）

### POST /api/stores/{storeId}/products — 新建商品

**权限：** 仅 Staff（本店）

**请求体：**
```json
{
  "name": "国产老陈醋",
  "price": 12.80,
  "stock": 200,
  "categoryId": 5,
  "imageUrl": "https://cdn.example.com/product.jpg"
}
```

**响应 data：**
```json
{ "id": 101 }
```

---

### PUT /api/stores/{storeId}/products/{productId} — 编辑商品

**权限：** 仅 Staff（本店）

**请求体（字段均为可选，仅传需修改的字段）：**
```json
{
  "name": "山西老陈醋",
  "price": 14.50,
  "categoryId": 5,
  "imageUrl": "https://cdn.example.com/new.jpg"
}
```

**响应 data：** `null`

---

### DELETE /api/stores/{storeId}/products/{productId} — 删除商品

**权限：** 仅 Staff（本店）

**响应 data：** `null`

---

### PUT /api/stores/{storeId}/products/{productId}/stock — 更新库存

**权限：** 仅 Staff（本店）

**请求体：**
```json
{
  "delta": 50,
  "type": "IN"
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| delta | int | 变更数量，正整数 |
| type | string | `IN`（入库）/ `OUT`（出库） |

**响应 data：**
```json
{ "currentStock": 250 }
```

---

### POST /api/categories — 新建分类

**权限：** 仅 Staff 或 Admin

**请求体：**
```json
{
  "name": "饮料",
  "parentId": 1
}
```

> `parentId` 为 null 则创建顶级分类。

**响应 data：**
```json
{ "id": 6 }
```

---

### DELETE /api/categories/{categoryId} — 删除分类

**权限：** 仅 Admin

**响应 data：** `null`

---

## 模块四：购物车

### GET /api/cart — 查看购物车

**权限：** 仅 Customer

**响应 data：**
```json
{
  "items": [
    {
      "id": 1,
      "productId": 101,
      "productName": "国产老陈醋",
      "imageUrl": "https://cdn.example.com/product.jpg",
      "price": 12.80,
      "quantity": 2,
      "stock": 200,
      "subtotal": 25.60
    }
  ],
  "total": 25.60
}
```

---

### POST /api/cart/items — 加入购物车

**权限：** 仅 Customer

**请求体：**
```json
{
  "productId": 101,
  "quantity": 2
}
```

> 如果该商品已在购物车中，数量累加。

**响应 data：** `null`

---

### PUT /api/cart/items/{itemId} — 修改购物车商品数量

**权限：** 仅 Customer

**请求体：**
```json
{
  "quantity": 3
}
```

**响应 data：** `null`

---

### DELETE /api/cart/items/{itemId} — 删除购物车条目

**权限：** 仅 Customer

**响应 data：** `null`

---

## 模块五：订单

### POST /api/orders — 创建订单

**权限：** 仅 Customer

**请求体：**
```json
{
  "cartItemIds": [1, 2],
  "addressId": 3,
  "couponId": 5
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| cartItemIds | long[] | 是 | 要结算的购物车条目 ID 列表 |
| addressId | long | 是 | 选择的收货地址 ID |
| couponId | long | 否 | 使用的优惠券 ID（不使用则不传） |

**响应 data：**
```json
{
  "orderId": 1001,
  "totalAmount": 23.04,
  "discountAmount": 2.56,
  "payableAmount": 20.48
}
```

---

### GET /api/orders — 我的订单列表

**权限：** 仅 Customer

**Query 参数：**

| 参数 | 类型 | 说明 |
|---|---|---|
| status | string | 可选筛选：`PENDING_PAYMENT` / `PAID` / `SHIPPED` / `COMPLETED` / `CANCELLED` |
| page | int | 默认 1 |
| size | int | 默认 10 |

**响应 data（分页）：**
```json
{
  "records": [
    {
      "id": 1001,
      "status": "PAID",
      "payableAmount": 20.48,
      "createdAt": "2026-05-20T10:00:00",
      "storeName": "南鲸旗舰店",
      "itemCount": 2
    }
  ],
  "total": 5,
  "size": 10,
  "current": 1,
  "pages": 1
}
```

---

### GET /api/orders/{orderId} — 订单详情

**权限：** 需要登录（Customer 只能查自己的订单，Staff 只能查本店订单）

**响应 data：**
```json
{
  "id": 1001,
  "status": "PAID",
  "totalAmount": 23.04,
  "discountAmount": 2.56,
  "payableAmount": 20.48,
  "createdAt": "2026-05-20T10:00:00",
  "paidAt": "2026-05-20T10:05:00",
  "address": {
    "receiverName": "张三",
    "phone": "13800138000",
    "detail": "南京市鼓楼区汉口路22号"
  },
  "items": [
    {
      "productId": 101,
      "productName": "国产老陈醋",
      "price": 12.80,
      "quantity": 2,
      "subtotal": 25.60
    }
  ]
}
```

---

### POST /api/orders/{orderId}/pay — 支付订单

**权限：** 仅 Customer（本人订单）

**请求体：** `{}` （模拟支付，无需真实支付参数）

**响应 data：**
```json
{ "status": "PAID", "paidAt": "2026-05-20T10:05:00" }
```

---

### POST /api/orders/{orderId}/cancel — 取消订单

**权限：** 仅 Customer（本人订单，状态为 `PENDING_PAYMENT` 或 `PAID`）

**请求体：** `{}`

**响应 data：**
```json
{ "status": "CANCELLED", "refunded": true }
```

> 已支付订单取消时 `refunded: true`，未支付时 `refunded: false`。

---

### POST /api/orders/{orderId}/confirm — 确认收货

**权限：** 仅 Customer（本人订单，状态为 `SHIPPED`）

**请求体：** `{}`

**响应 data：**
```json
{ "status": "COMPLETED" }
```

---

### POST /api/orders/{orderId}/refund — 申请退款

**权限：** 仅 Customer（本人订单，状态为 `COMPLETED`）

**请求体：**
```json
{ "reason": "商品质量问题" }
```

**响应 data：**
```json
{ "status": "CANCELLED", "refundedAt": "2026-05-20T12:00:00" }
```

---

### GET /api/stores/{storeId}/orders — 门店订单列表

**权限：** 仅 Staff（本店）

**Query 参数：**

| 参数 | 类型 | 说明 |
|---|---|---|
| status | string | 可选筛选（同上） |
| page | int | 默认 1 |
| size | int | 默认 10 |

**响应 data：** 同我的订单列表（分页格式）

---

### POST /api/stores/{storeId}/orders/{orderId}/ship — 订单发货

**权限：** 仅 Staff（本店，订单状态为 `PAID`）

**请求体：**
```json
{
  "trackingNumber": "SF1234567890",
  "carrier": "顺丰速运"
}
```

**响应 data：**
```json
{ "status": "SHIPPED", "shippedAt": "2026-05-21T09:00:00" }
```

---

## 模块六：收货地址

### GET /api/addresses — 我的地址列表

**权限：** 仅 Customer

**响应 data：**
```json
[
  {
    "id": 3,
    "receiverName": "张三",
    "phone": "13800138000",
    "province": "江苏省",
    "city": "南京市",
    "district": "鼓楼区",
    "detail": "汉口路22号",
    "isDefault": true
  }
]
```

---

### POST /api/addresses — 新增地址

**权限：** 仅 Customer

**请求体：**
```json
{
  "receiverName": "张三",
  "phone": "13800138000",
  "province": "江苏省",
  "city": "南京市",
  "district": "鼓楼区",
  "detail": "汉口路22号",
  "isDefault": false
}
```

**响应 data：**
```json
{ "id": 4 }
```

---

### PUT /api/addresses/{addressId} — 修改地址

**权限：** 仅 Customer（本人地址）

**请求体：** 同新增地址（字段均可选）

**响应 data：** `null`

---

### DELETE /api/addresses/{addressId} — 删除地址

**权限：** 仅 Customer（本人地址）

**响应 data：** `null`

---

## 模块七：评论

### GET /api/products/{productId}/reviews — 商品评论列表

**权限：** 无需登录

**Query 参数：** `page`, `size`

**响应 data（分页）：**
```json
{
  "records": [
    {
      "id": 201,
      "userId": 1,
      "userNickname": "张三",
      "rating": 5,
      "content": "醋味纯正，物超所值！",
      "createdAt": "2026-05-18T14:00:00",
      "parentId": null,
      "replies": [
        {
          "id": 205,
          "userId": 2,
          "userNickname": "李四",
          "content": "同感，一直回购",
          "createdAt": "2026-05-19T10:00:00"
        }
      ]
    }
  ],
  "total": 18,
  "size": 10,
  "current": 1,
  "pages": 2
}
```

---

### POST /api/products/{productId}/reviews — 发表评论

**权限：** 仅 Customer（需有该商品对应的已完成订单）

**请求体：**
```json
{
  "orderId": 1001,
  "rating": 5,
  "content": "醋味纯正，物超所值！"
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| orderId | long | 是 | 关联订单 ID，用于验证购买资格 |
| rating | int | 是 | 评分 1-5 |
| content | string | 是 | 评论内容 |

**响应 data：**
```json
{ "id": 201 }
```

---

### POST /api/reviews/{reviewId}/replies — 回复评论

**权限：** 仅 Customer（无需购买限制）

**请求体：**
```json
{
  "content": "同感，一直回购"
}
```

**响应 data：**
```json
{ "id": 205 }
```

---

## 模块八：优惠券

### GET /api/coupon-groups — 可领取优惠券列表

**权限：** 无需登录（领取需登录）

**Query 参数：** `page`, `size`

**响应 data（分页）：**
```json
{
  "records": [
    {
      "id": 10,
      "name": "新人5折券",
      "type": "DISCOUNT",
      "value": 0.5,
      "minOrderAmount": 20.00,
      "expireAt": "2026-06-30T23:59:59",
      "totalCount": 100,
      "remainCount": 63,
      "storeId": null,
      "storeName": null
    }
  ],
  "total": 5,
  "size": 10,
  "current": 1,
  "pages": 1
}
```

> `type` 枚举：`DISCOUNT`（折扣券，value 为折扣率）/ `AMOUNT_OFF`（满减券，value 为减免金额）  
> `storeId` 为 null 表示全局优惠券，否则为店铺优惠券。

---

### POST /api/coupon-groups/{groupId}/claim — 领取优惠券

**权限：** 仅 Customer

**请求体：** `{}`

**响应 data：**
```json
{ "couponId": 501 }
```

---

### GET /api/coupons/mine — 我的优惠券列表

**权限：** 仅 Customer

**Query 参数：**

| 参数 | 类型 | 说明 |
|---|---|---|
| status | string | `UNUSED` / `USED` / `EXPIRED`，不传返回全部 |

**响应 data：**
```json
[
  {
    "id": 501,
    "groupName": "新人5折券",
    "type": "DISCOUNT",
    "value": 0.5,
    "minOrderAmount": 20.00,
    "expireAt": "2026-06-30T23:59:59",
    "status": "UNUSED",
    "storeId": null
  }
]
```

---

### POST /api/stores/{storeId}/coupon-groups — 发布店铺优惠券

**权限：** 仅 Staff（本店）

**请求体：**
```json
{
  "name": "夏日满减券",
  "type": "AMOUNT_OFF",
  "value": 5.00,
  "minOrderAmount": 30.00,
  "totalCount": 200,
  "startAt": "2026-06-01T00:00:00",
  "expireAt": "2026-08-31T23:59:59"
}
```

**响应 data：**
```json
{ "id": 11 }
```

---

### POST /api/coupon-groups — 发布全局优惠券

**权限：** 仅 Admin

**请求体：** 同发布店铺优惠券（无需 storeId）

**响应 data：**
```json
{ "id": 12 }
```

---

### DELETE /api/coupon-groups/{groupId} — 删除优惠券组

**权限：** Staff（本店券） 或 Admin（全局券）

**响应 data：** `null`

---

## 模块九：商店管理（Admin）

### POST /api/stores — 创建商店

**权限：** 仅 Admin

**请求体：**
```json
{
  "name": "南鲸旗舰店",
  "creditCode": "91320100MA1XXXXX",
  "logo": "https://cdn.example.com/logo.png",
  "staffPhone": "13900139000"
}
```

> `staffPhone` 为该商店初始 Staff 账号的手机号（账号须已注册）。

**响应 data：**
```json
{ "storeId": 2 }
```

---

### PUT /api/stores/{storeId} — 修改商店信息

**权限：** 仅 Admin

**请求体（字段均可选）：**
```json
{
  "name": "南鲸官方旗舰店",
  "logo": "https://cdn.example.com/new-logo.png"
}
```

**响应 data：** `null`

---

### GET /api/admin/stores — 管理员查看所有商店

**权限：** 仅 Admin

**Query 参数：** `page`, `size`

**响应 data（分页）：** 同商店列表，额外包含 `creditCode` 字段

---

## 模块十：报表

### GET /api/stores/{storeId}/reports/orders — 门店订单报表

**权限：** 仅 Staff（本店）

**Query 参数：**

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| startDate | string | 否 | 起始日期，格式 `yyyy-MM-dd` |
| endDate | string | 否 | 截止日期，格式 `yyyy-MM-dd` |

**响应 data：**
```json
{
  "totalOrders": 128,
  "totalRevenue": 3840.50,
  "averageOrderAmount": 30.00,
  "statusBreakdown": {
    "COMPLETED": 100,
    "CANCELLED": 15,
    "PENDING_PAYMENT": 5,
    "SHIPPED": 8
  },
  "dailyRevenue": [
    { "date": "2026-05-19", "revenue": 560.00, "orders": 18 },
    { "date": "2026-05-20", "revenue": 480.00, "orders": 15 }
  ]
}
```

---

### GET /api/admin/reports/orders — 全局汇总报表

**权限：** 仅 Admin

**Query 参数：** 同门店订单报表

**响应 data：**
```json
{
  "totalOrders": 860,
  "totalRevenue": 28600.00,
  "storeBreakdown": [
    {
      "storeId": 1,
      "storeName": "南鲸旗舰店",
      "orders": 128,
      "revenue": 3840.50
    }
  ],
  "dailyRevenue": [
    { "date": "2026-05-20", "revenue": 1200.00, "orders": 40 }
  ]
}
```

---

## 接口汇总

| 模块 | 方法 | 路径 | 权限 |
|---|---|---|---|
| **用户** | POST | `/api/auth/register` | 无需登录 |
| | POST | `/api/auth/login` | 无需登录 |
| | GET | `/api/users/me` | 需要登录 |
| | PUT | `/api/users/me` | 需要登录 |
| | PUT | `/api/users/me/password` | 需要登录 |
| **浏览** | GET | `/api/stores` | 无需登录 |
| | GET | `/api/stores/{storeId}` | 无需登录 |
| | GET | `/api/stores/{storeId}/products` | 无需登录 |
| | GET | `/api/products/{productId}` | 无需登录 |
| | GET | `/api/products` | 无需登录 |
| | GET | `/api/categories` | 无需登录 |
| **商品管理** | POST | `/api/stores/{storeId}/products` | Staff |
| | PUT | `/api/stores/{storeId}/products/{productId}` | Staff |
| | DELETE | `/api/stores/{storeId}/products/{productId}` | Staff |
| | PUT | `/api/stores/{storeId}/products/{productId}/stock` | Staff |
| | POST | `/api/categories` | Staff/Admin |
| | DELETE | `/api/categories/{categoryId}` | Admin |
| **购物车** | GET | `/api/cart` | Customer |
| | POST | `/api/cart/items` | Customer |
| | PUT | `/api/cart/items/{itemId}` | Customer |
| | DELETE | `/api/cart/items/{itemId}` | Customer |
| **订单** | POST | `/api/orders` | Customer |
| | GET | `/api/orders` | Customer |
| | GET | `/api/orders/{orderId}` | Customer/Staff |
| | POST | `/api/orders/{orderId}/pay` | Customer |
| | POST | `/api/orders/{orderId}/cancel` | Customer |
| | POST | `/api/orders/{orderId}/confirm` | Customer |
| | POST | `/api/orders/{orderId}/refund` | Customer |
| | GET | `/api/stores/{storeId}/orders` | Staff |
| | POST | `/api/stores/{storeId}/orders/{orderId}/ship` | Staff |
| **地址** | GET | `/api/addresses` | Customer |
| | POST | `/api/addresses` | Customer |
| | PUT | `/api/addresses/{addressId}` | Customer |
| | DELETE | `/api/addresses/{addressId}` | Customer |
| **评论** | GET | `/api/products/{productId}/reviews` | 无需登录 |
| | POST | `/api/products/{productId}/reviews` | Customer |
| | POST | `/api/reviews/{reviewId}/replies` | Customer |
| **优惠券** | GET | `/api/coupon-groups` | 无需登录 |
| | POST | `/api/coupon-groups/{groupId}/claim` | Customer |
| | GET | `/api/coupons/mine` | Customer |
| | POST | `/api/stores/{storeId}/coupon-groups` | Staff |
| | POST | `/api/coupon-groups` | Admin |
| | DELETE | `/api/coupon-groups/{groupId}` | Staff/Admin |
| **商店管理** | POST | `/api/stores` | Admin |
| | PUT | `/api/stores/{storeId}` | Admin |
| | GET | `/api/admin/stores` | Admin |
| **报表** | GET | `/api/stores/{storeId}/reports/orders` | Staff |
| | GET | `/api/admin/reports/orders` | Admin |
