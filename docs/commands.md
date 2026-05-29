# 常用开发指令

> 所有指令均在**项目根目录**（`pom.xml` 所在目录）执行。

---

## 1. 依赖管理

| 指令 | 用途 |
|---|---|
| `mvn dependency:resolve` | 下载 pom.xml 中所有依赖到本地 Maven 仓库 |
| `mvn dependency:resolve -U` | 强制检查远程仓库，更新 SNAPSHOT 依赖至最新版本 |

---

## 2. 编译

| 指令 | 用途 |
|---|---|
| `mvn compile` | 编译主代码（`src/main`），输出到 `target/classes` |
| `mvn clean compile` | 清除上次编译产物后重新编译 |
| `mvn clean package -DskipTests` | 编译并打包，跳过测试（适合快速验证能否编译通过） |

---

## 3. 运行项目

| 指令 | 用途 |
|---|---|
| `mvn spring-boot:run` | 本地启动，使用 `application.yml` 默认配置 |
| `mvn spring-boot:run -Dspring-boot.run.profiles=dev` | 激活 `dev` Profile，加载 `application-dev.yml` |
| `mvn spring-boot:run -Dspring-boot.run.profiles=prod` | 激活 `prod` Profile，加载 `application-prod.yml` |

注入环境变量示例（覆盖 yml 中的占位符）：

```bash
# Linux / macOS
JWT_SECRET=xxx REDIS_PASSWORD=yyy mvn spring-boot:run

# Windows PowerShell
$env:JWT_SECRET="xxx"; $env:REDIS_PASSWORD="yyy"; mvn spring-boot:run

# Windows CMD
set JWT_SECRET=xxx && set REDIS_PASSWORD=yyy && mvn spring-boot:run
```

---

## 4. 测试

| 指令 | 用途 |
|---|---|
| `mvn test` | 运行 `src/test` 下所有测试类 |
| `mvn test -Dtest=ClassName` | 只运行指定测试类，例如 `-Dtest=RedisTest` |
| `mvn test -Dtest=ClassName#methodName` | 只运行指定测试类中的某个方法 |

---

## 5. 打包

| 指令 | 用途 |
|---|---|
| `mvn clean package -DskipTests` | 清理后打包，跳过测试，生成 `target/bluewhale-0.0.1-SNAPSHOT.jar` |

运行打好的 jar：

```bash
java -jar target/bluewhale-0.0.1-SNAPSHOT.jar
# 指定 Profile
java -jar target/bluewhale-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
# 注入环境变量
java -DJWT_SECRET=xxx -jar target/bluewhale-0.0.1-SNAPSHOT.jar
```
