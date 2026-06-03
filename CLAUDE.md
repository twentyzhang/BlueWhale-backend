# BlueWhale Project

## 新会话必读

每次新会话开始时先读 **`docs/上手指南.md`**，其中说明了：

- 需要读哪些文件才能快速了解项目当前进度
- 实现代码或做出技术决策后必须更新哪些文档

---

## Tech Stack

| Layer      | Technology                                               |
| ---------- | -------------------------------------------------------- |
| Language   | Java 17                                                  |
| Framework  | Spring Boot 3.4.0                                        |
| Build Tool | Maven                                                    |
| ORM        | MyBatis Plus 3.5.9                                       |
| Security   | Spring Security (stateless / JWT-ready)                  |
| Database   | MySQL 8.x                                                |
| Validation | Jakarta Bean Validation (spring-boot-starter-validation) |
| Utility    | Lombok, Spring Boot DevTools                             |

## Project Info

- **Group**: com.twentyzhang
- **Artifact**: bluewhale
- **Root Package**: com.twentyzhang.bluewhale

## Directory Structure

```
src/
└── main/
    ├── java/com/twentyzhang/bluewhale/
    │   ├── BluewhaleApplication.java   # Entry point
    │   ├── controller/                 # REST controllers (@RestController)
    │   ├── service/                    # Business logic interfaces and impls
    │   ├── repository/                 # MyBatis Plus mapper interfaces
    │   ├── entity/                     # Database entity classes (@TableName)
    │   ├── dto/                        # Request/Response data transfer objects
    │   ├── config/                     # Spring configuration classes
    │   │   ├── MybatisPlusConfig.java  # Pagination interceptor
    │   │   └── SecurityConfig.java     # Spring Security filter chain
    │   └── exception/                  # Exception handling
    │       ├── BusinessException.java  # Custom runtime exception
    │       └── GlobalExceptionHandler.java  # @RestControllerAdvice
    └── resources/
        └── application.yml             # App configuration (DB, MyBatis Plus, logging)
```

## Key Configuration Notes

- **Security**: Stateless session (STATELESS). `/api/auth/**` is open; all other endpoints require authentication.
- **MyBatis Plus**: Pagination interceptor enabled for MySQL. Underscore-to-camelCase mapping on. Logical delete via `deleted` field (1=deleted, 0=active).
- **Database**: Configure `spring.datasource.url/username/password` in `application.yml` before running.

## Common Commands

```bash
# Build
mvn clean package -DskipTests

# Run
mvn spring-boot:run

# Test
mvn test
```
