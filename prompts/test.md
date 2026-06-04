请在测试目录下创建测试基类 BaseServiceTest，
放在 com.twentyzhang.BlueWhale 包下。

要求：

- 添加 @ExtendWith(MockitoExtension.class) 注解
- 创建一个辅助方法 mockAuthUser(Long userId, String role, Long storeId)，
  用于模拟 SecurityContext 中的登录用户
  将构造好的 AuthUser 写入 SecurityContextHolder
- 每个测试方法结束后清空 SecurityContextHolder



请为 UserServiceImpl 编写单元测试，
放在 com.twentyzhang.BlueWhale.service 包下，
测试类命名 UserServiceTest，继承 BaseServiceTest。

使用 @Mock 模拟以下依赖：

- UserMapper
- RedisUtil
- JwtUtil
- PasswordEncoder

需要覆盖以下测试用例：

register 方法：

- 正常注册成功
- 手机号已存在时抛出 BusinessException

login 方法：

- 正常登录成功，验证返回值包含 accessToken 和 refreshToken
- 手机号不存在时抛出 BusinessException
- 密码错误时抛出 BusinessException

getUserInfo 方法：

- 正常返回当前用户信息
- 手机号脱敏验证（138****8000 格式）

changePassword 方法：

- 正常修改密码成功
- 旧密码错误时抛出 BusinessException
- 新密码与确认密码不一致时抛出 BusinessException



请为 StoreServiceImpl 编写单元测试，
放在 com.twentyzhang.BlueWhale.service 包下，
测试类命名 StoreServiceTest，继承 BaseServiceTest。

使用 @Mock 模拟以下依赖：

- StoreMapper
- UserMapper

需要覆盖以下测试用例：

listStores 方法：

- 正常分页返回商店列表

getStoreDetail 方法：

- 正常返回商店详情
- 商店不存在时抛出 BusinessException（code 404）

createStore 方法：

- 正常创建商店成功，验证 Staff 用户的 storeId 被正确更新
- 当前用户非 Admin 时抛出 BusinessException（code 403）
- staffPhone 对应用户不存在时抛出 BusinessException
- staffPhone 对应用户角色不是 STAFF 时抛出 BusinessException

updateStore 方法：

- 正常更新商店信息
- 商店不存在时抛出 BusinessException（code 404）
- 当前用户非 Admin 时抛出 BusinessException（code 403）

listAllStoresForAdmin 方法：

- 正常分页返回（含 creditCode 字段）
- 当前用户非 Admin 时抛出 BusinessException（code 403）


请为 UserController 和 StoreController 编写 Controller 层测试，
分别命名 UserControllerTest 和 StoreControllerTest。

要求：

- 使用 @WebMvcTest + @MockBean 模拟 Service 层
- 使用 MockMvc 发送请求
- 不加载完整 Spring 上下文，排除 Security 配置

需要覆盖以下测试用例：

UserController：

- POST /api/auth/register 参数校验：手机号为空、密码长度不足时返回 400
- POST /api/auth/login 正常登录返回 200 和 token
- GET /api/users/me 未携带 token 返回 401
- PUT /api/users/me/password 新旧密码相同时返回 400

StoreController：

- GET /api/stores 正常返回分页数据
- GET /api/stores/{storeId} 商店不存在返回 404
- POST /api/stores 非 Admin 调用返回 403



请为 ProductCategoryServiceImpl 编写单元测试，
命名 ProductCategoryServiceTest，继承 BaseServiceTest。

使用 @Mock 模拟：

- ProductCategoryMapper
- ProductMapper

需要覆盖以下测试用例：

getCategoryTree：

- 正常返回树形结构，验证父子关系组装正确
- 无分类数据时返回空列表

createCategory：

- 正常创建顶级分类（parentId 为 null）
- 正常创建子分类
- parentId 对应分类不存在时抛出 BusinessException
- 同级下存在同名分类时抛出 BusinessException
- 非 Staff/Admin 调用时抛出 BusinessException（code 403）

deleteCategory：

- 正常删除叶子节点分类
- 分类不存在时抛出 BusinessException（code 404）
- 存在子分类时抛出 BusinessException
- 存在关联商品时抛出 BusinessException
- 非 Admin 调用时抛出 BusinessException（code 403）


请运行所有新增的测试用例：
mvn test -pl . -Dtest="UserServiceTest,StoreServiceTest,UserControllerTest,StoreControllerTest"

如果有失败的用例，逐一分析原因并修复，
不要修改测试用例本身，只修复业务代码或 mock 配置
