# Debug 记录

记录开发过程中遇到的坑、IDE 报错及解决方案，供下次快速定位。

---

## 1. 单元测试 | MyBatis Plus 3.5.9 `insert` 重载歧义

**现象**

IDE 报错：
```
Ambiguous method call: both 'BaseMapper.insert(T)' and 'BaseMapper.insert(Collection<T>)' match
```

出现在 Mockito `when` 或 `verify` 中调用 `userMapper.insert(any(User.class))` 时。

**原因**

MyBatis Plus **3.5.9** 在 `BaseMapper` 上新增了 `insert(Collection<T> entityList)` 默认方法。
Mockito 的参数匹配器（如 `any(User.class)`）在编译期返回 `null`，两个重载都接受 `null`，编译器无法确定调用哪一个。

**解决方案**

用类型转型强制编译器选择 `insert(T entity)` 重载：

```java
// ❌ 有歧义
when(userMapper.insert(any(User.class))).thenReturn(1);

// ✅ 转型锁定单实体重载
when(userMapper.insert((User) any())).thenReturn(1);

// verify 同理
verify(userMapper).insert((User) captor.capture());
```

**受影响范围**

MyBatis Plus 3.5.9 同时为 `insert` 和 `updateById` 增加了集合重载，测试中凡涉及这两个方法的
`when` / `verify` 调用均需用 `anyXxx()` 辅助方法消歧（见"补充"部分）。
`selectById`、`deleteById` 目前仅有一个重载，暂不受影响。

**补充：`(T) any()` 转型在部分 IDE 版本仍报歧义**

`(Store) any()` 转型理论上应消歧（JLS §15.12.2），但 IntelliJ 对 Mockito 泛型返回值
的类型推断存在误报。最可靠的方案是写一个返回类型明确的私有辅助方法：

```java
// 测试类内部私有方法，返回类型显式为 Store，编译器能唯一确定调用 insert(Store)
private static Store anyStore() {
    return ArgumentMatchers.any(Store.class);
}

// 用法
doAnswer(inv -> { ((Store) inv.getArgument(0)).setId(10L); return 1; })
    .when(storeMapper).insert(anyStore());
```

对于"验证方法从未被调用"的场景，优先用 `verifyNoInteractions(mapper)`，
完全绕开方法名和参数类型，同时语义也更清晰。

---

## 2. 单元测试 | `argThat` lambda 中泛型参数方法不可见

**现象**

IDE 报错：
```
Cannot resolve method 'getPhone' in 'T'
Cannot resolve method 'getPassword' in 'T'
```

出现在 `verify(userMapper).insert(argThat(u -> u.getPhone()...))` 时。

**原因**

`argThat(lambda)` 的参数类型由编译器从上下文推断。当调用 `BaseMapper` 的泛型方法时，`u` 被推断为泛型占位符 `T` 而非具体实体类（如 `User`），因此实体方法不可见。

**解决方案**

用 `ArgumentCaptor<User>` 替代 `argThat` lambda，capture 后得到强类型对象再断言：

```java
// ❌ 泛型 T，方法不可见
verify(userMapper).insert(argThat(u -> "138...".equals(u.getPhone())));

// ✅ 用 ArgumentCaptor，capture 后是强类型
ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
verify(userMapper).insert((User) captor.capture()); // (User) 同时解决问题 1
User saved = captor.getValue();
assertEquals("13800138000", saved.getPhone());
```

---

## 3. 单元测试 | `ServiceImpl.baseMapper` 在 Mockito 中无法自动注入

**现象**

测试运行时 `getById` / `save` 等方法抛 `NullPointerException`，`baseMapper` 为 null。

**原因**

`XxxServiceImpl extends ServiceImpl<XxxMapper, T>` 中，`baseMapper` 是声明在父类 `ServiceImpl` 的 `protected M baseMapper` 字段。
Mockito 的 `@InjectMocks` 使用构造器注入时只注入本类声明的字段；父类的泛型字段因**类型擦除**（运行时 `M` 变为 `BaseMapper`），Mockito 找不到精确类型匹配，字段注入失败。

**解决方案**

在 `@BeforeEach` 中用 `ReflectionTestUtils.setField` 显式注入：

```java
@BeforeEach
void setUp() {
    ReflectionTestUtils.setField(userService, "baseMapper", userMapper);
}
```

此方案对所有 `ServiceImpl` 子类通用，`UserServiceImpl`、`StoreServiceImpl` 等均采用此方式。

---

## 4. 单元测试 | `@Transactional` 在纯 Mockito 测试中不生效

**现象**

`createStore` 等加了 `@Transactional` 的方法，在 Mockito 单元测试中 insert 和 update 不在同一事务内，中途异常不会回滚。

**原因**

`@Transactional` 依赖 Spring 的 AOP 代理，纯 Mockito 测试（无 `@SpringBootTest`）中 Bean 未经代理，注解不生效。

**影响**

单元测试只验证业务逻辑，不测试事务行为。
事务行为需通过集成测试（`@SpringBootTest` + 真实数据库）验证。
目前项目中此类集成测试尚未编写。
