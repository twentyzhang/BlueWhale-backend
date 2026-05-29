帮我在项目中引入 Redis，要求：

1. 在 pom.xml 中添加依赖：

   - spring-boot-starter-data-redis
   - commons-pool2（连接池）
2. 在 application.yml 中添加 Redis 配置：

   - host、port、password、database 用占位符即可
   - 连接池配置：最大连接数8、最大空闲数8、最小空闲数0
3. 在 com.twentyzhang.BlueWhale.config 包下创建 RedisConfig 配置类：

   - 使用 StringRedisTemplate
   - key 和 value 的序列化方式都使用 StringRedisSerializer
   - 创建 RedisUtil 工具类放在 com.twentyzhang.BlueWhale.util 包下
     封装常用操作：set、get、delete、setWithExpire、hasKey

---


请在 pom.xml 中添加 JWT 相关依赖：

- jjwt-api
- jjwt-impl
- jjwt-jackson

使用 io.jsonwebtoken 的 jjwt 库，版本使用目前最新的稳定版本
