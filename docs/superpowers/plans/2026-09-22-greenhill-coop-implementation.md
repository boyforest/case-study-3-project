# Greenhill Food Co-op 订货系统 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付一个可运行的合作社订货系统(Spring Boot + React/antd),支持会员下单、协调员管理会员/商品/轮次、按件与按公斤计价、轮次订单与商品汇总,并满足课程 DoD(干净 checkout 可跑、每故事一分支、自动化测试)。

**Architecture:** 单体 Spring Boot 3.2.5 应用,MyBatis-Plus 访问 H2 文件库,JWT 拦截器 + `@RequireCoordinator` 做两级权限;React SPA 由 Vite 构建为静态产物提交进 `src/main/resources/static`,默认 Maven 构建不依赖 Node。业务按故事纵向切片,12 个堆叠分支对应 12 个故事。

**Tech Stack:** Java 17、Spring Boot 3.2.5、MyBatis-Plus 3.5.5、H2(MODE=MySQL,文件模式)、jjwt 0.12.5、spring-security-crypto(BCrypt)、Lombok、springdoc-openapi 2.5.0;React 19 + Vite 7 + antd 6 + axios + react-router 7;JUnit 5 + MockMvc。

**Spec:** `docs/superpowers/specs/2026-09-22-greenhill-coop-design.md`

---

## 0. 执行约定

**环境(已在本机验证):** JDK 17.0.18、Maven 3.9.14、Node v22.22.2、npm 10.9.7。

**分支策略(堆叠):** 每个故事一个分支,基于上一个故事分支创建;`main` 只保留设计文档提交,等团队在 GitHub 上按 01→12 顺序开 PR 合并。

```bash
# Task 1 起:
git checkout -b story/01-foundation main
# Task 2 起:
git checkout -b story/02-auth story/01-foundation
# ...以此类推
```

**提交规范:** 每个故事 1-3 个 commit,格式 `feat(story-NN): ...` / `test(story-NN): ...` / `docs(story-NN): ...`。

**常用命令:**

```bash
# 后端测试(在任意 story 分支上)
./mvnw test
# 全量构建(默认不构建前端)
./mvnw clean package
# 启动
java -jar target/greenhill-coop-1.0.0.jar
# 前端开发/构建(改动前端后必须重建并提交 static 产物)
cd frontend && npm install && npm run build
```

**演示账号:** 所有种子会员密码 `coop1234`;协调员 `M-001`。

**约定:**
- 所有金额 `BigDecimal`,行金额 2 位 `HALF_UP`;不做 10 分凑整。
- 业务失败返回 HTTP 200 + `Result.code=400/404/409`;未登录 HTTP 401;越权 HTTP 403;系统异常 HTTP 500;未知路径 HTTP 404。
- 前端无自动化测试;每完成一个前端页面,执行 `npm run build` 并确认产物更新;视觉验收由用户在浏览器完成。
- 计划中所有相对路径均相对仓库根 `~/greenhill-coop`。

---

## 文件地图

| 文件 | 职责 |
|------|------|
| `pom.xml` | Spring Boot 父 POM、依赖、`frontend` profile |
| `src/main/resources/application.yml` | 端口 8080、H2 文件库、MyBatis-Plus、JWT 默认密钥 |
| `src/main/resources/schema.sql` | 5 张表 DDL(`IF NOT EXISTS`) |
| `src/main/java/com/greenhill/coop/CoopApplication.java` | 启动类 |
| `common/BizCode.java` | 业务码枚举 |
| `common/Result.java` | 统一返回 |
| `common/BizException.java` | 业务异常 |
| `common/GlobalExceptionHandler.java` | 全局异常 → HTTP/Result 映射 |
| `common/PageResult.java` | 分页返回 |
| `common/enums/*.java` | MemberRole/MemberStatus/UnitType/ProductStatus/RoundStatus/OrderStatus |
| `auth/JwtUtil.java` | 签发/解析 JWT(仅 memberId) |
| `auth/JwtInterceptor.java` | 解析 token → 加载会员 → 校验状态 → 角色检查 |
| `auth/UserContext.java` | ThreadLocal 当前用户 |
| `auth/MemberContext.java` | 当前用户记录(id/memberNo/name/role) |
| `auth/RequireCoordinator.java` | 协调员接口注解 |
| `auth/CurrentUser.java` + `CurrentUserArgumentResolver.java` | 注入当前用户 |
| `config/AppConfig.java` | `PasswordEncoder` Bean |
| `config/WebMvcConfig.java` | 拦截器注册、`@CurrentUser` 解析器、SPA 转发 |
| `config/AutoFillHandler.java` | created_at/updated_at 自动填充 |
| `config/DataSeeder.java` | 空库种子数据(`@Profile("!test")`) |
| `entity/*.java` | Member/Product/Round/Order/OrderLine |
| `mapper/*.java` | 5 个 BaseMapper + `OrderLineMapper.selectRoundTotals` |
| `dto/*.java` | 请求/响应 record 与投影类 |
| `service/PricingService.java` | 按件/按公斤计价(核心领域逻辑) |
| `service/AuthService.java` | 登录 |
| `service/MemberService.java` | 会员 CRUD/停用/重置密码 |
| `service/ProductService.java` | 商品 CRUD/撤回/可订列表 |
| `service/RoundService.java` | 轮次 CRUD/状态流转/当前轮次 |
| `service/OrderService.java` | 下单/改单/取消/轮次订单/汇总/代下单 |
| `controller/*.java` | Auth/Member/Product/Round/Order 5 个控制器 |
| `frontend/**` | React 源码(见各 Task) |
| `src/main/resources/static/**` | 前端构建产物(入库) |
| `src/test/java/com/greenhill/coop/**` | 测试(见各 Task) |
| `src/test/resources/application-test.yml` | 测试用内存 H2 |
| `README.md` / `docs/handover.md` / `docs/jira-import.csv` | Task 12 交付物 |

---

## Task 1: 项目骨架与统一基础(`story/01-foundation`)

**Files:**
- Create: `pom.xml`, `.mvn/wrapper/*`(用命令生成), `src/main/java/com/greenhill/coop/CoopApplication.java`
- Create: `src/main/resources/application.yml`, `src/main/resources/schema.sql`
- Create: `src/main/java/com/greenhill/coop/common/{BizCode,Result,BizException,GlobalExceptionHandler,PageResult}.java`
- Create: `src/main/java/com/greenhill/coop/common/enums/{MemberRole,MemberStatus,UnitType,ProductStatus,RoundStatus,OrderStatus}.java`
- Create: `src/main/java/com/greenhill/coop/config/AutoFillHandler.java`, `src/main/java/com/greenhill/coop/config/MybatisPlusConfig.java`
- Create: `src/main/java/com/greenhill/coop/entity/{Member,Product,Round,Order,OrderLine}.java`
- Create: `src/main/java/com/greenhill/coop/mapper/{Member,Product,Round,Order,OrderLine}Mapper.java`
- Create: `src/test/java/com/greenhill/coop/CoopApplicationTests.java`, `src/test/resources/application-test.yml`

- [ ] **Step 1: 生成 Maven Wrapper 与目录**

```bash
mkdir -p src/main/java/com/greenhill/coop/{common/enums,auth,config,entity,mapper,dto,service,controller}
mkdir -p src/main/resources src/test/java/com/greenhill/coop src/test/resources
mvn -N wrapper:wrapper -Dmaven=3.9.14
```

Expected: 生成 `mvnw`、`mvnw.cmd`、`.mvn/wrapper/maven-wrapper.properties`。

- [ ] **Step 2: 写 `pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.5</version>
        <relativePath/>
    </parent>
    <groupId>com.greenhill</groupId>
    <artifactId>greenhill-coop</artifactId>
    <version>1.0.0</version>
    <name>greenhill-coop</name>
    <description>Greenhill Food Co-op weekly ordering system</description>
    <properties>
        <java.version>17</java.version>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-crypto</artifactId>
        </dependency>
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
            <version>3.5.5</version>
        </dependency>
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-api</artifactId>
            <version>0.12.5</version>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-impl</artifactId>
            <version>0.12.5</version>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-jackson</artifactId>
            <version>0.12.5</version>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <version>1.18.30</version>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
            <version>2.5.0</version>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <configuration>
                    <annotationProcessorPaths>
                        <path>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                            <version>1.18.30</version>
                        </path>
                    </annotationProcessorPaths>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>
    <profiles>
        <profile>
            <id>frontend</id>
            <build>
                <plugins>
                    <plugin>
                        <groupId>com.github.eirslett</groupId>
                        <artifactId>frontend-maven-plugin</artifactId>
                        <version>1.15.0</version>
                        <configuration>
                            <workingDirectory>frontend</workingDirectory>
                            <nodeVersion>v22.12.0</nodeVersion>
                        </configuration>
                        <executions>
                            <execution>
                                <id>npm-install</id>
                                <goals><goal>npm</goal></goals>
                                <configuration><arguments>install</arguments></configuration>
                            </execution>
                            <execution>
                                <id>npm-build</id>
                                <goals><goal>npm</goal></goals>
                                <configuration><arguments>run build</arguments></configuration>
                            </execution>
                        </executions>
                    </plugin>
                </plugins>
            </build>
        </profile>
    </profiles>
</project>
```

- [ ] **Step 3: 写 `src/main/resources/application.yml`**

```yaml
server:
  port: 8080

spring:
  datasource:
    driver-class-name: org.h2.Driver
    url: jdbc:h2:file:./data/greenhill;MODE=MySQL;DATABASE_TO_LOWER=TRUE;AUTO_SERVER=TRUE
    username: sa
    password: ""
  sql:
    init:
      mode: always
  h2:
    console:
      enabled: true
      path: /h2-console

mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true

jwt:
  secret: Z3JlZW5oaWxsLWNvb3AtZGV2LXNlY3JldC1rZXktMzJieXRlcy1taW4tMDAwMQ==
  expiration: 604800000

springdoc:
  swagger-ui:
    path: /swagger-ui.html
```

- [ ] **Step 4: 写 `src/main/resources/schema.sql`**

```sql
CREATE TABLE IF NOT EXISTS member (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_no VARCHAR(10) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    phone VARCHAR(20),
    email VARCHAR(100),
    address VARCHAR(200),
    role VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS product (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    unit_type VARCHAR(20) NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    bay VARCHAR(10),
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS round (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    round_no INT NOT NULL UNIQUE,
    orders_open_at TIMESTAMP NOT NULL,
    orders_close_at TIMESTAMP NOT NULL,
    pickup_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id BIGINT NOT NULL,
    round_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_order_member_round UNIQUE (member_id, round_id)
);

CREATE TABLE IF NOT EXISTS order_line (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity DECIMAL(10,3) NOT NULL,
    unit_type_snapshot VARCHAR(20) NOT NULL,
    unit_price DECIMAL(10,2) NOT NULL,
    line_total DECIMAL(10,2) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_line_order_product UNIQUE (order_id, product_id)
);

CREATE INDEX IF NOT EXISTS idx_orders_round ON orders(round_id);
```

- [ ] **Step 5: 写 6 个枚举**

`src/main/java/com/greenhill/coop/common/enums/MemberRole.java`:

```java
package com.greenhill.coop.common.enums;

public enum MemberRole {
    MEMBER,
    COORDINATOR
}
```

`MemberStatus.java` / `ProductStatus.java` / `RoundStatus.java` / `OrderStatus.java` / `UnitType.java` 同构,枚举值如下:

```java
public enum MemberStatus { ACTIVE, INACTIVE }
public enum ProductStatus { ACTIVE, WITHDRAWN }
public enum RoundStatus { OPEN, CLOSED, PACKED }
public enum OrderStatus { ACTIVE, CANCELLED }
public enum UnitType { PER_UNIT, PER_KG }
```

(每个文件单独一个枚举,package 同上。)

- [ ] **Step 6: 写 `common/BizCode.java`、`Result.java`、`BizException.java`、`PageResult.java`**

```java
package com.greenhill.coop.common;

import lombok.Getter;

@Getter
public enum BizCode {
    SUCCESS(200, "success"),
    PARAM_ERROR(400, "Invalid request"),
    UNAUTHORIZED(401, "Not logged in"),
    FORBIDDEN(403, "No permission"),
    NOT_FOUND(404, "Not found"),
    CONFLICT(409, "Conflict"),
    METHOD_NOT_ALLOWED(405, "Method not allowed"),
    SYSTEM_ERROR(500, "System error");

    private final int code;
    private final String message;

    BizCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
```

```java
package com.greenhill.coop.common;

import lombok.Data;

@Data
public class Result<T> {
    private int code;
    private String message;
    private T data;

    public static <T> Result<T> success(T data) {
        Result<T> r = new Result<>();
        r.code = BizCode.SUCCESS.getCode();
        r.message = BizCode.SUCCESS.getMessage();
        r.data = data;
        return r;
    }

    public static <T> Result<T> error(int code, String message) {
        Result<T> r = new Result<>();
        r.code = code;
        r.message = message;
        return r;
    }
}
```

```java
package com.greenhill.coop.common;

import lombok.Getter;

@Getter
public class BizException extends RuntimeException {
    private final int code;

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public static BizException badRequest(String message) {
        return new BizException(BizCode.PARAM_ERROR.getCode(), message);
    }

    public static BizException notFound(String message) {
        return new BizException(BizCode.NOT_FOUND.getCode(), message);
    }

    public static BizException conflict(String message) {
        return new BizException(BizCode.CONFLICT.getCode(), message);
    }

    public static BizException unauthorized(String message) {
        return new BizException(BizCode.UNAUTHORIZED.getCode(), message);
    }

    public static BizException forbidden(String message) {
        return new BizException(BizCode.FORBIDDEN.getCode(), message);
    }
}
```

```java
package com.greenhill.coop.common;

import lombok.Data;

import java.util.List;

@Data
public class PageResult<T> {
    private List<T> records;
    private long total;
    private long current;
    private long size;

    public static <T> PageResult<T> of(List<T> records, long total, long current, long size) {
        PageResult<T> p = new PageResult<>();
        p.records = records;
        p.total = total;
        p.current = current;
        p.size = size;
        return p;
    }
}
```

- [ ] **Step 7: 写 `common/GlobalExceptionHandler.java`**

```java
package com.greenhill.coop.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ResponseEntity<Result<Void>> handleBiz(BizException e) {
        HttpStatus status = switch (e.getCode()) {
            case 401 -> HttpStatus.UNAUTHORIZED;
            case 403 -> HttpStatus.FORBIDDEN;
            default -> HttpStatus.OK;
        };
        return ResponseEntity.status(status).body(Result.error(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidation(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = fieldError == null ? "Invalid request" : fieldError.getField() + ": " + fieldError.getDefaultMessage();
        return Result.error(BizCode.PARAM_ERROR.getCode(), message);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleNoResource(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.error(BizCode.NOT_FOUND.getCode(), "Not found"));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
        MissingServletRequestParameterException.class})
    public Result<Void> handleBadRequest(Exception e) {
        return Result.error(BizCode.PARAM_ERROR.getCode(), "Invalid request");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(Result.error(BizCode.METHOD_NOT_ALLOWED.getCode(), "Method not allowed"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public Result<Void> handleIntegrity(DataIntegrityViolationException e) {
        log.warn("Data integrity violation", e);
        return Result.error(BizCode.CONFLICT.getCode(), "This change conflicts with existing data");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleOther(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error(BizCode.SYSTEM_ERROR.getCode(), "System error"));
    }
}
```

- [ ] **Step 8: 写 `CoopApplication.java`、`config/AutoFillHandler.java`、`config/MybatisPlusConfig.java`**

```java
package com.greenhill.coop;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.greenhill.coop.mapper")
public class CoopApplication {
    public static void main(String[] args) {
        SpringApplication.run(CoopApplication.class, args);
    }
}
```

```java
package com.greenhill.coop.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class AutoFillHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
    }
}
```

```java
package com.greenhill.coop.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.H2);
        pagination.setMaxLimit(500L);
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }
}
```

- [ ] **Step 9: 写 5 个实体**

```java
package com.greenhill.coop.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.greenhill.coop.common.enums.MemberRole;
import com.greenhill.coop.common.enums.MemberStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("member")
public class Member {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String memberNo;
    private String name;
    private String phone;
    private String email;
    private String address;
    private MemberRole role;
    private MemberStatus status;
    private String passwordHash;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
```

```java
package com.greenhill.coop.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.greenhill.coop.common.enums.ProductStatus;
import com.greenhill.coop.common.enums.UnitType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("product")
public class Product {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private UnitType unitType;
    private BigDecimal price;
    private String bay;
    private ProductStatus status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
```

```java
package com.greenhill.coop.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.greenhill.coop.common.enums.RoundStatus;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("round")
public class Round {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer roundNo;
    private LocalDateTime ordersOpenAt;
    private LocalDateTime ordersCloseAt;
    private LocalDate pickupDate;
    private RoundStatus status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
```

```java
package com.greenhill.coop.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.greenhill.coop.common.enums.OrderStatus;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("orders")
public class Order {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long memberId;
    private Long roundId;
    private OrderStatus status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
```

```java
package com.greenhill.coop.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.greenhill.coop.common.enums.UnitType;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("order_line")
public class OrderLine {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Long productId;
    private BigDecimal quantity;
    private UnitType unitTypeSnapshot;
    private BigDecimal unitPrice;
    private BigDecimal lineTotal;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 10: 写 5 个 Mapper**

`MemberMapper.java` / `ProductMapper.java` / `RoundMapper.java` / `OrderMapper.java`(同构,`OrderLineMapper` 多加汇总查询):

```java
package com.greenhill.coop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.greenhill.coop.entity.Member;

public interface MemberMapper extends BaseMapper<Member> {
}
```

```java
package com.greenhill.coop.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.greenhill.coop.entity.OrderLine;
import com.greenhill.coop.dto.RoundTotalRow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface OrderLineMapper extends BaseMapper<OrderLine> {

    @Select("""
        SELECT p.id AS productId,
               p.name AS productName,
               ol.unit_type_snapshot AS unitType,
               SUM(ol.quantity) AS totalQuantity,
               SUM(ol.line_total) AS totalAmount
        FROM order_line ol
        JOIN orders o ON o.id = ol.order_id
        JOIN product p ON p.id = ol.product_id
        WHERE o.round_id = #{roundId} AND o.status = 'ACTIVE'
        GROUP BY p.id, p.name, ol.unit_type_snapshot
        ORDER BY p.name
        """)
    List<RoundTotalRow> selectRoundTotals(@Param("roundId") Long roundId);
}
```

- [ ] **Step 11: 写 `dto/RoundTotalRow.java`(投影类,Task 10 使用)**

```java
package com.greenhill.coop.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class RoundTotalRow {
    private Long productId;
    private String productName;
    private String unitType;
    private BigDecimal totalQuantity;
    private BigDecimal totalAmount;
}
```

- [ ] **Step 12: 写测试配置与骨架测试**

`src/test/resources/application-test.yml`:

```yaml
spring:
  datasource:
    driver-class-name: org.h2.Driver
    url: jdbc:h2:mem:testdb;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1
    username: sa
    password: ""
  sql:
    init:
      mode: always
```

`src/test/java/com/greenhill/coop/CoopApplicationTests.java`:

```java
package com.greenhill.coop;

import com.greenhill.coop.common.enums.MemberRole;
import com.greenhill.coop.common.enums.MemberStatus;
import com.greenhill.coop.entity.Member;
import com.greenhill.coop.mapper.MemberMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CoopApplicationTests {

    @Autowired
    private MemberMapper memberMapper;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void contextLoadsAndSchemaIsCreated() {
        assertThat(memberMapper.selectCount(null)).isZero();
    }

    @Test
    void entityRoundTripWithEnumsAndAutoFillTimestamps() {
        Member member = new Member();
        member.setMemberNo("M-900");
        member.setName("Test Member");
        member.setRole(MemberRole.MEMBER);
        member.setStatus(MemberStatus.ACTIVE);
        member.setPasswordHash("x");
        memberMapper.insert(member);

        Member loaded = memberMapper.selectById(member.getId());
        assertThat(loaded.getRole()).isEqualTo(MemberRole.MEMBER);
        assertThat(loaded.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getUpdatedAt()).isNotNull();
    }

    @Test
    void unknownPathReturns404() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/no-such-page"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound());
    }
}
```

- [ ] **Step 13: 运行测试(应通过)**

```bash
./mvnw test
```

Expected: `BUILD SUCCESS`,3 个测试通过(上下文/建表、实体往返、未知路径 404)。

- [ ] **Step 14: 提交**

```bash
git add -A
git commit -m "feat(story-01): project skeleton, common result/exception layer, schema and entities"
```

---

## Task 2: 登录与角色鉴权 + 前端脚手架(`story/02-auth`)

**Files:**
- Create: `src/main/java/com/greenhill/coop/auth/{JwtUtil,JwtInterceptor,UserContext,MemberContext,RequireCoordinator,CurrentUser,CurrentUserArgumentResolver}.java`
- Create: `src/main/java/com/greenhill/coop/config/{AppConfig,WebMvcConfig}.java`
- Create: `src/main/java/com/greenhill/coop/dto/{LoginRequest,LoginResponse,MemberView}.java`
- Create: `src/main/java/com/greenhill/coop/service/AuthService.java`
- Create: `src/main/java/com/greenhill/coop/controller/AuthController.java`
- Create: `src/test/java/com/greenhill/coop/ApiTestBase.java`, `src/test/java/com/greenhill/coop/AuthApiTest.java`, `src/test/java/com/greenhill/coop/TestCoordinatorController.java`
- Create: `frontend/{package.json,vite.config.js,index.html}` 与 `frontend/src/**`(见 Step 10-14)

- [ ] **Step 1: 建分支**

```bash
git checkout -b story/02-auth story/01-foundation
```

- [ ] **Step 2: 写失败测试 `AuthApiTest.java`**

```java
package com.greenhill.coop;

import com.greenhill.coop.common.enums.MemberRole;
import com.greenhill.coop.entity.Member;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthApiTest extends ApiTestBase {

    @BeforeEach
    void setUp() {
        createMember("M-001", "Ngaire Fletcher", MemberRole.COORDINATOR, "coop1234");
        createMember("M-094", "Ky Tran", MemberRole.MEMBER, "coop1234");
    }

    @Test
    void loginWithCorrectCredentialsReturnsToken() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberNo\":\"M-094\",\"password\":\"coop1234\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.token").isNotEmpty())
            .andExpect(jsonPath("$.data.member.memberNo").value("M-094"));
    }

    @Test
    void loginWithWrongPasswordIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberNo\":\"M-094\",\"password\":\"wrong\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void inactiveMemberCannotLogIn() throws Exception {
        Member m = createMember("M-077", "Ruth Callaghan", MemberRole.MEMBER, "coop1234");
        m.setStatus(com.greenhill.coop.common.enums.MemberStatus.INACTIVE);
        memberMapper.updateById(m);

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberNo\":\"M-077\",\"password\":\"coop1234\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void meWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void meWithTokenReturnsCurrentMember() throws Exception {
        String token = tokenFor("M-094", "coop1234");
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.memberNo").value("M-094"));
    }

    @Test
    void coordinatorEndpointRejectsMembersAndClearsContext() throws Exception {
        mockMvc.perform(get("/api/test/coordinator-only")
                .header("Authorization", "Bearer " + tokenFor("M-094", "coop1234")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(403));

        org.assertj.core.api.Assertions.assertThat(com.greenhill.coop.auth.UserContext.get()).isNull();
    }
}
```

`TestCoordinatorController.java`:

```java
package com.greenhill.coop;

import com.greenhill.coop.auth.RequireCoordinator;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequireCoordinator
public class TestCoordinatorController {

    @GetMapping("/api/test/coordinator-only")
    public String coordinatorOnly() {
        return "ok";
    }
}
```

`ApiTestBase.java`:

```java
package com.greenhill.coop;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.greenhill.coop.common.enums.MemberRole;
import com.greenhill.coop.common.enums.MemberStatus;
import com.greenhill.coop.common.enums.ProductStatus;
import com.greenhill.coop.common.enums.RoundStatus;
import com.greenhill.coop.common.enums.UnitType;
import com.greenhill.coop.entity.Member;
import com.greenhill.coop.entity.Product;
import com.greenhill.coop.entity.Round;
import com.greenhill.coop.mapper.MemberMapper;
import com.greenhill.coop.mapper.OrderLineMapper;
import com.greenhill.coop.mapper.OrderMapper;
import com.greenhill.coop.mapper.ProductMapper;
import com.greenhill.coop.mapper.RoundMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public abstract class ApiTestBase {

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected PasswordEncoder passwordEncoder;
    @Autowired protected MemberMapper memberMapper;
    @Autowired protected ProductMapper productMapper;
    @Autowired protected RoundMapper roundMapper;
    @Autowired protected OrderMapper orderMapper;
    @Autowired protected OrderLineMapper orderLineMapper;

    protected Member createMember(String memberNo, String name, MemberRole role, String password) {
        Member m = new Member();
        m.setMemberNo(memberNo);
        m.setName(name);
        m.setRole(role);
        m.setStatus(MemberStatus.ACTIVE);
        m.setPasswordHash(passwordEncoder.encode(password));
        memberMapper.insert(m);
        return m;
    }

    protected Product createProduct(String name, UnitType unitType, String price, String bay) {
        Product p = new Product();
        p.setName(name);
        p.setUnitType(unitType);
        p.setPrice(new BigDecimal(price));
        p.setBay(bay);
        p.setStatus(ProductStatus.ACTIVE);
        productMapper.insert(p);
        return p;
    }

    protected Round createRound(int roundNo, RoundStatus status) {
        Round r = new Round();
        r.setRoundNo(roundNo);
        r.setOrdersOpenAt(LocalDateTime.now().minusDays(2));
        r.setOrdersCloseAt(LocalDateTime.now().plusDays(2));
        r.setPickupDate(LocalDate.now().plusDays(5));
        r.setStatus(status);
        roundMapper.insert(r);
        return r;
    }

    protected String tokenFor(String memberNo, String password) throws Exception {
        MvcResult result = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("memberNo", memberNo, "password", password))))
            .andReturn();
        String responseBody = result.getResponse().getContentAsString();
        JsonNode root = objectMapper.readTree(responseBody);
        String token = root.path("data").path("token").asText();
        if (root.path("code").asInt() != 200 || token.isBlank()) {
            throw new IllegalStateException("Login failed for " + memberNo + ": " + responseBody);
        }
        return token;
    }
}
```

- [ ] **Step 3: 运行测试确认失败**

```bash
./mvnw test -Dtest=AuthApiTest
```

Expected: 编译失败或 401/404 —— `JwtInterceptor`、`AuthController` 尚不存在。

- [ ] **Step 4: 写 `auth/` 包(7 个文件)**

`MemberContext.java`:

```java
package com.greenhill.coop.auth;

import com.greenhill.coop.common.enums.MemberRole;

public record MemberContext(Long id, String memberNo, String name, MemberRole role) {
}
```

`UserContext.java`:

```java
package com.greenhill.coop.auth;

public final class UserContext {
    private static final ThreadLocal<MemberContext> CURRENT = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(MemberContext context) {
        CURRENT.set(context);
    }

    public static MemberContext get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
```

`JwtUtil.java`:

```java
package com.greenhill.coop.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    private final SecretKey signingKey;
    private final long expirationMillis;

    public JwtUtil(@Value("${jwt.secret}") String secret, @Value("${jwt.expiration}") long expirationMillis) {
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(secret);
        } catch (Exception e) {
            keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMillis = expirationMillis;
    }

    public String generateToken(Long memberId) {
        Date now = new Date();
        return Jwts.builder()
            .subject(memberId.toString())
            .issuedAt(now)
            .expiration(new Date(now.getTime() + expirationMillis))
            .signWith(signingKey)
            .compact();
    }

    public Long getMemberId(String token) {
        Claims claims = Jwts.parser().verifyWith(signingKey).build()
            .parseSignedClaims(token).getPayload();
        return Long.parseLong(claims.getSubject());
    }
}
```

`RequireCoordinator.java`:

```java
package com.greenhill.coop.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireCoordinator {
}
```

`CurrentUser.java`:

```java
package com.greenhill.coop.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {
}
```

`CurrentUserArgumentResolver.java`:

```java
package com.greenhill.coop.auth;

import com.greenhill.coop.common.BizException;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class)
            && MemberContext.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        MemberContext context = UserContext.get();
        if (context == null) {
            throw BizException.unauthorized("Not logged in");
        }
        return context;
    }
}
```

`JwtInterceptor.java`:

```java
package com.greenhill.coop.auth;

import com.greenhill.coop.common.BizException;
import com.greenhill.coop.common.enums.MemberRole;
import com.greenhill.coop.common.enums.MemberStatus;
import com.greenhill.coop.entity.Member;
import com.greenhill.coop.mapper.MemberMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final MemberMapper memberMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw BizException.unauthorized("Not logged in");
        }
        Long memberId;
        try {
            memberId = jwtUtil.getMemberId(header.substring(7));
        } catch (Exception e) {
            throw BizException.unauthorized("Invalid or expired token");
        }
        Member member = memberMapper.selectById(memberId);
        if (member == null) {
            throw BizException.unauthorized("Member not found");
        }
        if (member.getStatus() != MemberStatus.ACTIVE) {
            throw BizException.unauthorized("Account is inactive");
        }
        boolean needsCoordinator = handlerMethod.hasMethodAnnotation(RequireCoordinator.class)
            || handlerMethod.getBeanType().isAnnotationPresent(RequireCoordinator.class);
        if (needsCoordinator && member.getRole() != MemberRole.COORDINATOR) {
            throw BizException.forbidden("Coordinator permission required");
        }
        UserContext.set(new MemberContext(member.getId(), member.getMemberNo(), member.getName(), member.getRole()));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }
}
```

- [ ] **Step 5: 写 `config/AppConfig.java`、`config/WebMvcConfig.java`**

```java
package com.greenhill.coop.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class AppConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

```java
package com.greenhill.coop.config;

import com.greenhill.coop.auth.CurrentUserArgumentResolver;
import com.greenhill.coop.auth.JwtInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtInterceptor jwtInterceptor;
    private final CurrentUserArgumentResolver currentUserArgumentResolver;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
            .addPathPatterns("/api/**")
            .excludePathPatterns("/api/auth/login");
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserArgumentResolver);
    }
}
```

- [ ] **Step 6: 写 `dto/LoginRequest.java`、`dto/MemberView.java`、`dto/LoginResponse.java`**

```java
package com.greenhill.coop.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(@NotBlank String memberNo, @NotBlank String password) {
}
```

```java
package com.greenhill.coop.dto;

import com.greenhill.coop.common.enums.MemberRole;
import com.greenhill.coop.common.enums.MemberStatus;
import com.greenhill.coop.entity.Member;

public record MemberView(Long id, String memberNo, String name, String phone, String email,
                         String address, MemberRole role, MemberStatus status) {

    public static MemberView from(Member m) {
        return new MemberView(m.getId(), m.getMemberNo(), m.getName(), m.getPhone(),
            m.getEmail(), m.getAddress(), m.getRole(), m.getStatus());
    }
}
```

```java
package com.greenhill.coop.dto;

public record LoginResponse(String token, MemberView member) {
}
```

- [ ] **Step 7: 写 `service/AuthService.java` 与 `controller/AuthController.java`**

```java
package com.greenhill.coop.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.greenhill.coop.auth.JwtUtil;
import com.greenhill.coop.common.BizException;
import com.greenhill.coop.common.enums.MemberStatus;
import com.greenhill.coop.dto.LoginRequest;
import com.greenhill.coop.dto.LoginResponse;
import com.greenhill.coop.dto.MemberView;
import com.greenhill.coop.entity.Member;
import com.greenhill.coop.mapper.MemberMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final MemberMapper memberMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public LoginResponse login(LoginRequest request) {
        Member member = memberMapper.selectOne(new LambdaQueryWrapper<Member>()
            .eq(Member::getMemberNo, request.memberNo()));
        if (member == null || !passwordEncoder.matches(request.password(), member.getPasswordHash())) {
            throw BizException.badRequest("Invalid member number or password");
        }
        if (member.getStatus() != MemberStatus.ACTIVE) {
            throw BizException.badRequest("This account is inactive");
        }
        return new LoginResponse(jwtUtil.generateToken(member.getId()), MemberView.from(member));
    }
}
```

```java
package com.greenhill.coop.controller;

import com.greenhill.coop.auth.CurrentUser;
import com.greenhill.coop.auth.MemberContext;
import com.greenhill.coop.common.Result;
import com.greenhill.coop.dto.LoginRequest;
import com.greenhill.coop.dto.LoginResponse;
import com.greenhill.coop.dto.MemberView;
import com.greenhill.coop.service.AuthService;
import com.greenhill.coop.service.MemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final MemberService memberService;

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }

    @GetMapping("/me")
    public Result<MemberView> me(@CurrentUser MemberContext context) {
        return Result.success(memberService.getView(context.id()));
    }
}
```

`MemberService` 本任务只需 `getView(Long id)`(Task 3 再扩展):

```java
package com.greenhill.coop.service;

import com.greenhill.coop.common.BizException;
import com.greenhill.coop.dto.MemberView;
import com.greenhill.coop.entity.Member;
import com.greenhill.coop.mapper.MemberMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberMapper memberMapper;

    public MemberView getView(Long id) {
        Member member = memberMapper.selectById(id);
        if (member == null) {
            throw BizException.notFound("Member not found");
        }
        return MemberView.from(member);
    }
}
```

- [ ] **Step 8: 运行测试(应通过)**

```bash
./mvnw test -Dtest=AuthApiTest,CoopApplicationTests
```

Expected: `BUILD SUCCESS`,全部通过。

- [ ] **Step 9: 提交后端**

```bash
git add -A
git commit -m "feat(story-02): JWT login, role guard, auth endpoints and test base"
```

- [ ] **Step 10: 写前端脚手架文件**

`frontend/package.json`:

```json
{
  "name": "greenhill-coop-frontend",
  "version": "1.0.0",
  "private": true,
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "vite build"
  },
  "dependencies": {
    "@ant-design/icons": "^6.2.3",
    "antd": "^6.3.7",
    "axios": "^1.16.0",
    "dayjs": "^1.11.13",
    "react": "^19.1.1",
    "react-dom": "^19.1.1",
    "react-router-dom": "^7.15.0"
  },
  "devDependencies": {
    "@vitejs/plugin-react": "^5.0.4",
    "vite": "^7.1.12"
  }
}
```

`frontend/vite.config.js`:

```js
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080'
    }
  },
  build: {
    outDir: '../src/main/resources/static',
    emptyOutDir: true,
    rollupOptions: {
      output: {
        entryFileNames: 'assets/app.js',
        chunkFileNames: 'assets/[name].js',
        assetFileNames: 'assets/[name][extname]'
      }
    }
  }
})
```

`frontend/index.html`:

```html
<!doctype html>
<html lang="en">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>Greenhill Food Co-op</title>
  </head>
  <body>
    <div id="root"></div>
    <script type="module" src="/src/main.jsx"></script>
  </body>
</html>
```

`frontend/src/utils/auth.js`:

```js
const TOKEN_KEY = 'greenhill_token'
const USER_KEY = 'greenhill_user'

export function getToken() {
  return localStorage.getItem(TOKEN_KEY)
}

export function setAuth(token, user) {
  localStorage.setItem(TOKEN_KEY, token)
  localStorage.setItem(USER_KEY, JSON.stringify(user))
}

export function getUser() {
  try {
    return JSON.parse(localStorage.getItem(USER_KEY) || 'null')
  } catch {
    return null
  }
}

export function clearAuth() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}
```

`frontend/src/utils/request.js`(改写自旧项目,去掉 antd message 桥接,改为直接提示):

```js
import axios from 'axios'
import { clearAuth, getToken } from './auth'

const request = axios.create({ baseURL: '', timeout: 15000 })

request.interceptors.request.use(config => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

request.interceptors.response.use(
  response => {
    const data = response.data
    if (data && data.code !== 0 && data.code !== 200) {
      const error = new Error(data.message || `Error ${data.code}`)
      error.code = data.code
      return Promise.reject(error)
    }
    return data?.data
  },
  error => {
    const status = error.response?.status
    const message = error.response?.data?.message || error.message || 'Request failed'
    if (status === 401) {
      clearAuth()
      window.location.href = '/login'
    }
    return Promise.reject(new Error(message))
  }
)

export default request
```

`frontend/src/api/auth.js`:

```js
import request from '../utils/request'

export function login(data) {
  return request.post('/api/auth/login', data)
}

export function me() {
  return request.get('/api/auth/me')
}
```

`frontend/src/layouts/AppLayout.jsx`:

```jsx
import { Layout, Menu, Button, Space, Typography, App as AntApp } from 'antd'
import {
  ShopOutlined, ProfileOutlined, TeamOutlined, TagsOutlined,
  CalendarOutlined, UnorderedListOutlined, PieChartOutlined, LogoutOutlined
} from '@ant-design/icons'
import { useLocation, useNavigate, Outlet } from 'react-router-dom'
import { clearAuth, getUser } from '../utils/auth'

const memberItems = [
  { key: '/shop', icon: <ShopOutlined />, label: 'Place order' },
  { key: '/my-order', icon: <ProfileOutlined />, label: 'My orders' }
]

const adminItems = [
  { key: '/admin/members', icon: <TeamOutlined />, label: 'Members' },
  { key: '/admin/products', icon: <TagsOutlined />, label: 'Products' },
  { key: '/admin/rounds', icon: <CalendarOutlined />, label: 'Rounds' },
  { key: '/admin/orders', icon: <UnorderedListOutlined />, label: 'Round orders' },
  { key: '/admin/totals', icon: <PieChartOutlined />, label: 'Round totals' }
]

export default function AppLayout() {
  const navigate = useNavigate()
  const location = useLocation()
  const user = getUser()
  const items = user?.role === 'COORDINATOR' ? [...memberItems, ...adminItems] : memberItems

  return (
    <AntApp>
      <Layout style={{ minHeight: '100vh' }}>
        <Layout.Sider theme="light" width={230}>
          <div style={{ padding: 16 }}>
            <Typography.Title level={5} style={{ margin: 0 }}>Greenhill Food Co-op</Typography.Title>
            <Typography.Text type="secondary">{user?.name} ({user?.memberNo})</Typography.Text>
          </div>
          <Menu
            mode="inline"
            selectedKeys={[location.pathname]}
            items={items}
            onClick={({ key }) => navigate(key)}
          />
        </Layout.Sider>
        <Layout>
          <Layout.Header style={{ background: '#fff', display: 'flex', justifyContent: 'flex-end', alignItems: 'center' }}>
            <Space>
              <Typography.Text>{user?.role === 'COORDINATOR' ? 'Coordinator' : 'Member'}</Typography.Text>
              <Button icon={<LogoutOutlined />} onClick={() => { clearAuth(); navigate('/login') }}>
                Sign out
              </Button>
            </Space>
          </Layout.Header>
          <Layout.Content style={{ padding: 24 }}>
            <Outlet />
          </Layout.Content>
        </Layout>
      </Layout>
    </AntApp>
  )
}
```

`frontend/src/pages/LoginPage.jsx`:

```jsx
import { useState } from 'react'
import { Button, Card, Form, Input, Typography, message } from 'antd'
import { useNavigate } from 'react-router-dom'
import { login } from '../api/auth'
import { setAuth } from '../utils/auth'

export default function LoginPage() {
  const navigate = useNavigate()
  const [loading, setLoading] = useState(false)

  async function onFinish(values) {
    setLoading(true)
    try {
      const data = await login(values)
      setAuth(data.token, data.member)
      navigate(data.member.role === 'COORDINATOR' ? '/admin/orders' : '/shop')
    } catch (error) {
      message.error(error.message)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '100vh', background: '#f5f5f5' }}>
      <Card style={{ width: 380 }}>
        <Typography.Title level={4} style={{ textAlign: 'center' }}>Greenhill Food Co-op</Typography.Title>
        <Typography.Paragraph type="secondary" style={{ textAlign: 'center' }}>
          Weekly grocery ordering
        </Typography.Paragraph>
        <Form layout="vertical" onFinish={onFinish}>
          <Form.Item name="memberNo" label="Member number" rules={[{ required: true }]}>
            <Input placeholder="M-094" />
          </Form.Item>
          <Form.Item name="password" label="Password" rules={[{ required: true }]}>
            <Input.Password />
          </Form.Item>
          <Button type="primary" htmlType="submit" block loading={loading}>Sign in</Button>
        </Form>
      </Card>
    </div>
  )
}
```

`frontend/src/App.jsx`:

```jsx
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import AppLayout from './layouts/AppLayout'
import LoginPage from './pages/LoginPage'
import { getUser } from './utils/auth'

function RequireAuth({ roles, children }) {
  const user = getUser()
  if (!user) {
    return <Navigate to="/login" replace />
  }
  if (roles && !roles.includes(user.role)) {
    return <Navigate to="/shop" replace />
  }
  return children
}

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route element={<RequireAuth><AppLayout /></RequireAuth>}>
          <Route path="/shop" element={<div>Shop page (story 06)</div>} />
          <Route path="/my-order" element={<div>My orders (story 08)</div>} />
          <Route path="/admin/members" element={<RequireAuth roles={['COORDINATOR']}><div>Members (story 03)</div></RequireAuth>} />
          <Route path="/admin/products" element={<RequireAuth roles={['COORDINATOR']}><div>Products (story 04)</div></RequireAuth>} />
          <Route path="/admin/rounds" element={<RequireAuth roles={['COORDINATOR']}><div>Rounds (story 05)</div></RequireAuth>} />
          <Route path="/admin/orders" element={<RequireAuth roles={['COORDINATOR']}><div>Round orders (story 09)</div></RequireAuth>} />
          <Route path="/admin/totals" element={<RequireAuth roles={['COORDINATOR']}><div>Round totals (story 10)</div></RequireAuth>} />
        </Route>
        <Route path="*" element={<Navigate to="/shop" replace />} />
      </Routes>
    </BrowserRouter>
  )
}
```

`frontend/src/main.jsx`:

```jsx
import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './App'
import './assets/global.css'

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
)
```

`frontend/src/assets/global.css`:

```css
body {
  margin: 0;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
}
```

- [ ] **Step 11: 添加 SPA 转发(后端返回前端页面)**

`src/main/java/com/greenhill/coop/config/SpaForwardController.java`:

```java
package com.greenhill.coop.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaForwardController {

    @GetMapping(value = {"/", "/login", "/shop", "/my-order", "/admin/members", "/admin/products",
        "/admin/rounds", "/admin/orders", "/admin/totals"})
    public String index() {
        return "forward:/index.html";
    }
}
```

- [ ] **Step 12: 构建前端并验证产物入库**

```bash
cd frontend && npm install && npm run build && cd ..
ls src/main/resources/static src/main/resources/static/assets
```

Expected: `index.html` 与 `assets/app.js`、`assets/index.css` 生成。

- [ ] **Step 13: 全量测试 + 端到端手工验证**

```bash
./mvnw clean package -DskipTests && ./mvnw test
```

Expected: 测试全绿。

```bash
java -jar target/greenhill-coop-1.0.0.jar &
sleep 8
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/
curl -s -X POST http://localhost:8080/api/auth/login -H 'Content-Type: application/json' -d '{"memberNo":"M-001","password":"coop1234"}'
```

Expected: `/` 返回 `200`;登录返回 `{"code":200,...}`(种子数据在 Task 3 才完整,若 `M-001` 尚未种入则用 Task 3 后复验;本步若返回 400 属预期,记录即可)。

`kill %1` 关闭应用。

- [ ] **Step 14: 提交前端**

```bash
git add -A
git commit -m "feat(story-02): React scaffold, login page, layout, SPA forwarding and built assets"
```

---

## Task 3: 协调员维护会员(`story/03-members`)

**Files:**
- Modify: `src/main/java/com/greenhill/coop/service/MemberService.java`(扩展)
- Create: `src/main/java/com/greenhill/coop/dto/{MemberCreateRequest,MemberUpdateRequest,ResetPasswordRequest}.java`
- Create: `src/main/java/com/greenhill/coop/controller/MemberController.java`
- Create: `src/test/java/com/greenhill/coop/MemberApiTest.java`
- Create: `frontend/src/components/CrudTable.jsx`、`frontend/src/api/member.js`、`frontend/src/pages/admin/MembersPage.jsx`
- Modify: `frontend/src/App.jsx`(接入 MembersPage)

- [ ] **Step 1: 建分支**

```bash
git checkout -b story/03-members story/02-auth
```

- [ ] **Step 2: 写失败测试 `MemberApiTest.java`**

```java
package com.greenhill.coop;

import com.greenhill.coop.common.enums.MemberRole;
import com.greenhill.coop.common.enums.MemberStatus;
import com.greenhill.coop.entity.Member;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MemberApiTest extends ApiTestBase {

    private String coordinatorToken;

    @BeforeEach
    void setUp() throws Exception {
        createMember("M-001", "Ngaire Fletcher", MemberRole.COORDINATOR, "coop1234");
        createMember("M-094", "Ky Tran", MemberRole.MEMBER, "coop1234");
        coordinatorToken = tokenFor("M-001", "coop1234");
    }

    @Test
    void coordinatorCanCreateAndListMembers() throws Exception {
        mockMvc.perform(post("/api/members")
                .header("Authorization", "Bearer " + coordinatorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"memberNo":"M-063","name":"Jan Buckley","phone":"0412 000 000","password":"coop1234"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.memberNo").value("M-063"));

        mockMvc.perform(get("/api/members?keyword=Jan")
                .header("Authorization", "Bearer " + coordinatorToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.records[0].name").value("Jan Buckley"));
    }

    @Test
    void duplicateMemberNumberIsRejected() throws Exception {
        mockMvc.perform(post("/api/members")
                .header("Authorization", "Bearer " + coordinatorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"memberNo":"M-094","name":"Someone Else","password":"coop1234"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(409));
    }

    @Test
    void coordinatorCanUpdateMember() throws Exception {
        Member ky = memberMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Member>()
                .eq(Member::getMemberNo, "M-094"));
        mockMvc.perform(put("/api/members/" + ky.getId())
                .header("Authorization", "Bearer " + coordinatorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Ky Tran","phone":"0438 601 772","email":"ky@example.com","address":"Moorooka"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.phone").value("0438 601 772"));
    }

    @Test
    void deactivatedMemberCannotLogInAndCanBeReactivated() throws Exception {
        Member ky = memberMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Member>()
                .eq(Member::getMemberNo, "M-094"));

        mockMvc.perform(post("/api/members/" + ky.getId() + "/deactivate")
                .header("Authorization", "Bearer " + coordinatorToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("INACTIVE"));

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberNo\":\"M-094\",\"password\":\"coop1234\"}"))
            .andExpect(jsonPath("$.code").value(400));

        mockMvc.perform(post("/api/members/" + ky.getId() + "/activate")
                .header("Authorization", "Bearer " + coordinatorToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberNo\":\"M-094\",\"password\":\"coop1234\"}"))
            .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void coordinatorCannotDeactivateSelf() throws Exception {
        Member ngaire = memberMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Member>()
                .eq(Member::getMemberNo, "M-001"));
        mockMvc.perform(post("/api/members/" + ngaire.getId() + "/deactivate")
                .header("Authorization", "Bearer " + coordinatorToken))
            .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void resetPasswordChangesLogin() throws Exception {
        Member ky = memberMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Member>()
                .eq(Member::getMemberNo, "M-094"));
        mockMvc.perform(post("/api/members/" + ky.getId() + "/reset-password")
                .header("Authorization", "Bearer " + coordinatorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"newpass123\"}"))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberNo\":\"M-094\",\"password\":\"coop1234\"}"))
            .andExpect(jsonPath("$.code").value(400));

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberNo\":\"M-094\",\"password\":\"newpass123\"}"))
            .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void memberCannotAccessMemberManagement() throws Exception {
        String memberToken = tokenFor("M-094", "coop1234");
        mockMvc.perform(get("/api/members").header("Authorization", "Bearer " + memberToken))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value(403));
        mockMvc.perform(post("/api/members")
                .header("Authorization", "Bearer " + memberToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberNo\":\"M-999\",\"name\":\"X\",\"password\":\"coop1234\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void statusFilterWorks() throws Exception {
        Member ky = memberMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Member>()
                .eq(Member::getMemberNo, "M-094"));
        ky.setStatus(MemberStatus.INACTIVE);
        memberMapper.updateById(ky);

        mockMvc.perform(get("/api/members?status=INACTIVE")
                .header("Authorization", "Bearer " + coordinatorToken))
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.records[0].memberNo").value("M-094"));
    }

    @Test
    void keywordAndStatusFilterCombine() throws Exception {
        createMember("M-050", "Jan Active", MemberRole.MEMBER, "coop1234");
        Member inactive = createMember("M-051", "Jan Inactive", MemberRole.MEMBER, "coop1234");
        inactive.setStatus(MemberStatus.INACTIVE);
        memberMapper.updateById(inactive);

        mockMvc.perform(get("/api/members?keyword=Jan&status=ACTIVE")
                .header("Authorization", "Bearer " + coordinatorToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.records[0].memberNo").value("M-050"));
    }

    @Test
    void updateMissingMemberReturns404() throws Exception {
        mockMvc.perform(put("/api/members/999999")
                .header("Authorization", "Bearer " + coordinatorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Nobody\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void resetPasswordTooShortIsRejected() throws Exception {
        Member ky = memberMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Member>()
                .eq(Member::getMemberNo, "M-094"));
        mockMvc.perform(post("/api/members/" + ky.getId() + "/reset-password")
                .header("Authorization", "Bearer " + coordinatorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"123\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void overLengthMemberNumberIsRejected() throws Exception {
        mockMvc.perform(post("/api/members")
                .header("Authorization", "Bearer " + coordinatorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"memberNo":"M-123456789","name":"Too Long","password":"coop1234"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(400));
    }
}
```

- [ ] **Step 3: 运行确认失败**

```bash
./mvnw test -Dtest=MemberApiTest
```

Expected: 404/编译失败(接口不存在)。

- [ ] **Step 4: 写 3 个 DTO**

```java
package com.greenhill.coop.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MemberCreateRequest(@NotBlank @Size(max = 10) String memberNo,
                                  @NotBlank @Size(max = 100) String name,
                                  @Size(max = 20) String phone,
                                  @Size(max = 100) String email,
                                  @Size(max = 200) String address,
                                  @NotBlank @Size(min = 6, max = 100, message = "password must be between 6 and 100 characters") String password) {
}
```

```java
package com.greenhill.coop.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MemberUpdateRequest(@NotBlank @Size(max = 100) String name,
                                  @Size(max = 20) String phone,
                                  @Size(max = 100) String email,
                                  @Size(max = 200) String address) {
}
```

```java
package com.greenhill.coop.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
    @NotBlank @Size(min = 6, max = 100, message = "password must be between 6 and 100 characters") String password) {
}
```

- [ ] **Step 5: 扩展 `MemberService`(整文件替换)**

```java
package com.greenhill.coop.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.greenhill.coop.common.BizException;
import com.greenhill.coop.common.PageResult;
import com.greenhill.coop.common.enums.MemberRole;
import com.greenhill.coop.common.enums.MemberStatus;
import com.greenhill.coop.dto.MemberCreateRequest;
import com.greenhill.coop.dto.MemberUpdateRequest;
import com.greenhill.coop.dto.MemberView;
import com.greenhill.coop.entity.Member;
import com.greenhill.coop.mapper.MemberMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final MemberMapper memberMapper;
    private final PasswordEncoder passwordEncoder;

    public PageResult<MemberView> page(String keyword, MemberStatus status, long page, long size) {
        LambdaQueryWrapper<Member> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(Member::getName, keyword).or().like(Member::getMemberNo, keyword));
        }
        if (status != null) {
            qw.eq(Member::getStatus, status);
        }
        qw.orderByAsc(Member::getMemberNo);
        Page<Member> result = memberMapper.selectPage(new Page<>(page, size), qw);
        List<MemberView> views = result.getRecords().stream().map(MemberView::from).toList();
        return PageResult.of(views, result.getTotal(), result.getCurrent(), result.getSize());
    }

    public MemberView getView(Long id) {
        return MemberView.from(find(id));
    }

    public MemberView create(MemberCreateRequest request) {
        Long existing = memberMapper.selectCount(new LambdaQueryWrapper<Member>()
            .eq(Member::getMemberNo, request.memberNo()));
        if (existing > 0) {
            throw BizException.conflict("Member number already exists");
        }
        Member member = new Member();
        member.setMemberNo(request.memberNo());
        member.setName(request.name());
        member.setPhone(request.phone());
        member.setEmail(request.email());
        member.setAddress(request.address());
        member.setRole(MemberRole.MEMBER);
        member.setStatus(MemberStatus.ACTIVE);
        member.setPasswordHash(passwordEncoder.encode(request.password()));
        memberMapper.insert(member);
        return MemberView.from(member);
    }

    public MemberView update(Long id, MemberUpdateRequest request) {
        Member member = find(id);
        member.setName(request.name());
        member.setPhone(request.phone());
        member.setEmail(request.email());
        member.setAddress(request.address());
        memberMapper.updateById(member);
        return MemberView.from(member);
    }

    public MemberView deactivate(Long id, Long currentUserId) {
        if (id.equals(currentUserId)) {
            throw BizException.badRequest("You cannot deactivate your own account");
        }
        return setStatus(id, MemberStatus.INACTIVE);
    }

    public MemberView activate(Long id) {
        return setStatus(id, MemberStatus.ACTIVE);
    }

    public void resetPassword(Long id, String password) {
        Member member = find(id);
        member.setPasswordHash(passwordEncoder.encode(password));
        memberMapper.updateById(member);
    }

    private MemberView setStatus(Long id, MemberStatus status) {
        Member member = find(id);
        member.setStatus(status);
        memberMapper.updateById(member);
        return MemberView.from(member);
    }

    private Member find(Long id) {
        Member member = memberMapper.selectById(id);
        if (member == null) {
            throw BizException.notFound("Member not found");
        }
        return member;
    }
}
```

- [ ] **Step 6: 写 `MemberController.java`**

```java
package com.greenhill.coop.controller;

import com.greenhill.coop.auth.CurrentUser;
import com.greenhill.coop.auth.MemberContext;
import com.greenhill.coop.auth.RequireCoordinator;
import com.greenhill.coop.common.PageResult;
import com.greenhill.coop.common.Result;
import com.greenhill.coop.common.enums.MemberStatus;
import com.greenhill.coop.dto.MemberCreateRequest;
import com.greenhill.coop.dto.MemberUpdateRequest;
import com.greenhill.coop.dto.MemberView;
import com.greenhill.coop.dto.ResetPasswordRequest;
import com.greenhill.coop.service.MemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
@RequireCoordinator
public class MemberController {

    private final MemberService memberService;

    @GetMapping
    public Result<PageResult<MemberView>> page(@RequestParam(required = false) String keyword,
                                               @RequestParam(required = false) MemberStatus status,
                                               @RequestParam(defaultValue = "1") long page,
                                               @RequestParam(defaultValue = "10") long size) {
        return Result.success(memberService.page(keyword, status, page, size));
    }

    @PostMapping
    public Result<MemberView> create(@Valid @RequestBody MemberCreateRequest request) {
        return Result.success(memberService.create(request));
    }

    @PutMapping("/{id}")
    public Result<MemberView> update(@PathVariable Long id, @Valid @RequestBody MemberUpdateRequest request) {
        return Result.success(memberService.update(id, request));
    }

    @PostMapping("/{id}/deactivate")
    public Result<MemberView> deactivate(@PathVariable Long id, @CurrentUser MemberContext context) {
        return Result.success(memberService.deactivate(id, context.id()));
    }

    @PostMapping("/{id}/activate")
    public Result<MemberView> activate(@PathVariable Long id) {
        return Result.success(memberService.activate(id));
    }

    @PostMapping("/{id}/reset-password")
    public Result<Void> resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPasswordRequest request) {
        memberService.resetPassword(id, request.password());
        return Result.success(null);
    }
}
```

- [ ] **Step 7: 运行测试(应通过)**

```bash
./mvnw test -Dtest=MemberApiTest
```

Expected: 12 个测试全部通过。

- [ ] **Step 8: 提交后端**

```bash
git add -A && git commit -m "feat(story-03): coordinator member management endpoints and tests"
```

- [ ] **Step 9: 写 `frontend/src/components/CrudTable.jsx`(新写,保持旧项目 props 风格)**

```jsx
import { useEffect, useState } from 'react'
import { App, Button, DatePicker, Form, Input, InputNumber, Modal, Select, Space, Table, Typography } from 'antd'
import { DeleteOutlined, EditOutlined, PlusOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'

export default function CrudTable({
  title, filters = [], columns = [], formFields = [], rowKey = 'id',
  listApi, createApi, updateApi, deleteApi, extraActions, toolbarExtra
}) {
  const { message, modal } = App.useApp()
  const [filterForm] = Form.useForm()
  const [editForm] = Form.useForm()
  const [records, setRecords] = useState([])
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 })
  const [loading, setLoading] = useState(false)
  const [editing, setEditing] = useState(null)
  const [saving, setSaving] = useState(false)

  const visibleFields = editing?.[rowKey] ? formFields.filter(f => !f.createOnly) : formFields

  const tableColumns = [
    ...columns,
    {
      title: 'Actions', key: 'actions', fixed: 'right', width: 240,
      render: (_, record) => (
        <Space size="small">
          {updateApi && <Button size="small" icon={<EditOutlined />} onClick={() => openEdit(record)}>Edit</Button>}
          {deleteApi && <Button size="small" danger icon={<DeleteOutlined />} onClick={() => confirmDelete(record)}>Delete</Button>}
          {extraActions?.(record, reload)}
        </Space>
      )
    }
  ]

  useEffect(() => { loadData(1, pagination.pageSize) }, [])

  async function loadData(page = pagination.current, size = pagination.pageSize) {
    setLoading(true)
    try {
      const params = { page, size, ...clean(filterForm.getFieldsValue()) }
      const data = await listApi(params)
      const list = Array.isArray(data) ? data : data?.records || []
      setRecords(list)
      setPagination({
        current: Number(data?.current || page),
        pageSize: Number(data?.size || size),
        total: Number(data?.total || list.length)
      })
    } catch (error) {
      message.error(error.message)
    } finally {
      setLoading(false)
    }
  }

  function reload() { loadData(pagination.current, pagination.pageSize) }

  function openCreate() { setEditing({}); editForm.resetFields() }

  function openEdit(record) {
    setEditing(record)
    editForm.setFieldsValue(toFormValues(record, formFields))
  }

  async function save() {
    setSaving(true)
    try {
      const values = fromFormValues(await editForm.validateFields(), formFields)
      if (editing?.[rowKey]) {
        await updateApi(editing[rowKey], clean(values))
        message.success('Updated')
      } else {
        await createApi(clean(values))
        message.success('Created')
      }
      setEditing(null)
      reload()
    } catch (error) {
      if (error?.message) message.error(error.message)
    } finally {
      setSaving(false)
    }
  }

  function confirmDelete(record) {
    modal.confirm({
      title: `Delete ${record[rowKey]}?`,
      okText: 'Delete', okButtonProps: { danger: true }, cancelText: 'Cancel',
      onOk: async () => {
        try {
          await deleteApi(record[rowKey])
          message.success('Deleted')
          reload()
        } catch (error) {
          message.error(error.message)
        }
      }
    })
  }

  return (
    <div>
      <Typography.Title level={4} style={{ marginTop: 0 }}>{title}</Typography.Title>
      <Space style={{ marginBottom: 16, width: '100%', justifyContent: 'space-between' }} wrap>
        <Form form={filterForm} layout="inline" onFinish={() => loadData(1, pagination.pageSize)}>
          {filters.map(field => (
            <Form.Item key={field.name} name={field.name} label={field.label}>
              <FieldControl field={field} filter />
            </Form.Item>
          ))}
          <Form.Item>
            <Space>
              <Button type="primary" htmlType="submit" icon={<SearchOutlined />}>Search</Button>
              <Button onClick={() => { filterForm.resetFields(); loadData(1, pagination.pageSize) }}>Reset</Button>
              <Button icon={<ReloadOutlined />} onClick={reload} />
            </Space>
          </Form.Item>
        </Form>
        <Space>
          {toolbarExtra?.({ reload })}
          {createApi && <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>Create</Button>}
        </Space>
      </Space>
      <Table
        rowKey={rowKey} loading={loading} columns={tableColumns} dataSource={records}
        scroll={{ x: 'max-content' }}
        pagination={{ ...pagination, showSizeChanger: true, showTotal: total => `${total} items` }}
        onChange={next => loadData(next.current, next.pageSize)}
      />
      <Modal
        title={`${editing?.[rowKey] ? 'Edit' : 'Create'} ${title}`}
        open={Boolean(editing)} okText="Save" cancelText="Cancel"
        confirmLoading={saving} onOk={save} onCancel={() => setEditing(null)} destroyOnHidden
      >
        <Form form={editForm} layout="vertical">
          {visibleFields.map(field => (
            <Form.Item key={field.name} name={field.name} label={field.label} rules={field.rules || []}>
              <FieldControl field={field} />
            </Form.Item>
          ))}
        </Form>
      </Modal>
    </div>
  )
}

function FieldControl({ field, filter }) {
  if (field.type === 'select') {
    return <Select allowClear={filter} placeholder={field.placeholder || 'Select'} options={field.options || []} style={{ minWidth: 160 }} />
  }
  if (field.type === 'datetime') {
    return <DatePicker showTime placeholder={field.placeholder || 'Select date/time'} style={{ minWidth: 200 }} />
  }
  if (field.type === 'date') {
    return <DatePicker placeholder={field.placeholder || 'Select date'} style={{ minWidth: 160 }} />
  }
  if (field.type === 'number') {
    return <InputNumber min={0} precision={field.precision} placeholder={field.placeholder} style={{ minWidth: 160 }} />
  }
  return <Input maxLength={field.maxLength} placeholder={field.placeholder} style={{ minWidth: 180 }} />
}

function clean(object) {
  return Object.fromEntries(Object.entries(object || {}).filter(([, v]) => v !== undefined && v !== null && v !== ''))
}

function toFormValues(record, fields) {
  const values = { ...record }
  fields.forEach(field => {
    if ((field.type === 'datetime' || field.type === 'date') && record?.[field.name]) {
      values[field.name] = dayjs(record[field.name])
    }
  })
  return values
}

function fromFormValues(values, fields) {
  const next = { ...values }
  fields.forEach(field => {
    if ((field.type === 'datetime' || field.type === 'date') && values?.[field.name]) {
      next[field.name] = values[field.name].format(field.type === 'date' ? 'YYYY-MM-DD' : 'YYYY-MM-DDTHH:mm:ss')
    }
  })
  return next
}
```

- [ ] **Step 10: 写 `frontend/src/api/member.js` 与 `frontend/src/pages/admin/MembersPage.jsx`**

```js
import request from '../utils/request'

export function listMembers(params) {
  return request.get('/api/members', { params })
}

export function createMember(data) {
  return request.post('/api/members', data)
}

export function updateMember(id, data) {
  return request.put(`/api/members/${id}`, data)
}

export function deactivateMember(id) {
  return request.post(`/api/members/${id}/deactivate`)
}

export function activateMember(id) {
  return request.post(`/api/members/${id}/activate`)
}

export function resetMemberPassword(id, data) {
  return request.post(`/api/members/${id}/reset-password`, data)
}
```

```jsx
import { useState } from 'react'
import { App, Button, Form, Input, Modal, Space, Tag } from 'antd'
import CrudTable from '../../components/CrudTable'
import { activateMember, createMember, deactivateMember, listMembers, resetMemberPassword, updateMember } from '../../api/member'
import { getUser } from '../../utils/auth'

const filters = [
  { name: 'keyword', label: 'Keyword', placeholder: 'Name or member no.' },
  {
    name: 'status', label: 'Status', type: 'select',
    options: [{ value: 'ACTIVE', label: 'Active' }, { value: 'INACTIVE', label: 'Inactive' }]
  }
]

const columns = [
  { title: 'Member no.', dataIndex: 'memberNo' },
  { title: 'Name', dataIndex: 'name' },
  { title: 'Phone', dataIndex: 'phone' },
  { title: 'Email', dataIndex: 'email' },
  { title: 'Role', dataIndex: 'role' },
  {
    title: 'Status', dataIndex: 'status',
    render: value => <Tag color={value === 'ACTIVE' ? 'green' : 'red'}>{value}</Tag>
  }
]

const formFields = [
  { name: 'memberNo', label: 'Member number', createOnly: true, maxLength: 10, rules: [{ required: true }] },
  { name: 'name', label: 'Name', maxLength: 100, rules: [{ required: true }] },
  { name: 'phone', label: 'Phone', maxLength: 20 },
  { name: 'email', label: 'Email', maxLength: 100 },
  { name: 'address', label: 'Address', maxLength: 200 },
  { name: 'password', label: 'Initial password', createOnly: true, maxLength: 100, rules: [{ required: true }, { min: 6 }] }
]

export default function MembersPage() {
  const { message } = App.useApp()
  const [resetTarget, setResetTarget] = useState(null)
  const [form] = Form.useForm()

  async function toggleStatus(record, reload) {
    try {
      if (record.status === 'ACTIVE') {
        await deactivateMember(record.id)
        message.success('Member deactivated')
      } else {
        await activateMember(record.id)
        message.success('Member activated')
      }
      reload()
    } catch (error) {
      message.error(error.message)
    }
  }

  async function submitReset() {
    try {
      const values = await form.validateFields()
      await resetMemberPassword(resetTarget.id, values)
      message.success('Password reset')
      setResetTarget(null)
      form.resetFields()
    } catch (error) {
      if (error?.message) message.error(error.message)
    }
  }

  return (
    <>
      <CrudTable
        title="Members"
        filters={filters}
        columns={columns}
        formFields={formFields}
        listApi={listMembers}
        createApi={createMember}
        updateApi={updateMember}
        extraActions={(record, reload) => (
          <Space size="small">
            <Button size="small" disabled={record.id === getUser()?.id} onClick={() => toggleStatus(record, reload)}>
              {record.status === 'ACTIVE' ? 'Deactivate' : 'Activate'}
            </Button>
            <Button size="small" onClick={() => setResetTarget(record)}>Reset password</Button>
          </Space>
        )}
      />
      <Modal
        title={`Reset password for ${resetTarget?.name || ''}`}
        open={Boolean(resetTarget)}
        okText="Reset"
        onOk={submitReset}
        onCancel={() => { setResetTarget(null); form.resetFields() }}
        destroyOnHidden
      >
        <Form form={form} layout="vertical">
          <Form.Item name="password" label="New password" rules={[{ required: true }, { min: 6 }]}>
            <Input.Password />
          </Form.Item>
        </Form>
      </Modal>
    </>
  )
}
```

- [ ] **Step 11: 接入路由并构建**

修改 `frontend/src/App.jsx`:把 `/admin/members` 的占位元素替换为 `<MembersPage />`,并添加 `import MembersPage from './pages/admin/MembersPage'`。

```bash
cd frontend && npm run build && cd ..
git status --short src/main/resources/static
```

Expected: `static/assets/app.js` 与 `static/index.html` 更新。

- [ ] **Step 12: 提交前端**

```bash
git add -A && git commit -m "feat(story-03): members admin page and shared CrudTable component"
```

---

## Task 4: 协调员维护商品(`story/04-products`)

**Files:**
- Create: `src/main/java/com/greenhill/coop/dto/{ProductRequest,ProductView}.java`
- Create: `src/main/java/com/greenhill/coop/service/ProductService.java`
- Create: `src/main/java/com/greenhill/coop/controller/ProductController.java`
- Create: `src/test/java/com/greenhill/coop/ProductApiTest.java`
- Create: `frontend/src/api/product.js`、`frontend/src/pages/admin/ProductsPage.jsx`
- Modify: `frontend/src/App.jsx`

- [ ] **Step 1: 建分支**

```bash
git checkout -b story/04-products story/03-members
```

- [ ] **Step 2: 写失败测试 `ProductApiTest.java`**

```java
package com.greenhill.coop;

import com.greenhill.coop.common.enums.MemberRole;
import com.greenhill.coop.common.enums.UnitType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ProductApiTest extends ApiTestBase {

    private String coordinatorToken;

    @BeforeEach
    void setUp() throws Exception {
        createMember("M-001", "Ngaire Fletcher", MemberRole.COORDINATOR, "coop1234");
        createMember("M-094", "Ky Tran", MemberRole.MEMBER, "coop1234");
        coordinatorToken = tokenFor("M-001", "coop1234");
    }

    @Test
    void coordinatorCanCreateBothUnitTypes() throws Exception {
        mockMvc.perform(post("/api/products")
                .header("Authorization", "Bearer " + coordinatorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Rolled oats, organic","unitType":"PER_KG","price":3.40,"bay":"B1"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.unitType").value("PER_KG"))
            .andExpect(jsonPath("$.data.price").value(3.40));

        mockMvc.perform(post("/api/products")
                .header("Authorization", "Bearer " + coordinatorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Tahini, 375g jar","unitType":"PER_UNIT","price":9.80,"bay":"A2"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.unitType").value("PER_UNIT"));

        mockMvc.perform(get("/api/products?keyword=oats")
                .header("Authorization", "Bearer " + coordinatorToken))
            .andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.records[0].bay").value("B1"));
    }

    @Test
    void priceAndNameCanBeUpdated() throws Exception {
        Long id = createProduct("Coffee beans, whole", UnitType.PER_KG, "32.00", "C2").getId();
        mockMvc.perform(put("/api/products/" + id)
                .header("Authorization", "Bearer " + coordinatorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Coffee beans, ground","unitType":"PER_KG","price":34.00,"bay":"C2"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.name").value("Coffee beans, ground"))
            .andExpect(jsonPath("$.data.price").value(34.00));
    }

    @Test
    void withdrawHidesProductFromActiveList() throws Exception {
        Long id = createProduct("Raw almonds", UnitType.PER_KG, "18.90", "C1").getId();
        mockMvc.perform(post("/api/products/" + id + "/withdraw")
                .header("Authorization", "Bearer " + coordinatorToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("WITHDRAWN"));

        mockMvc.perform(get("/api/products?status=ACTIVE")
                .header("Authorization", "Bearer " + coordinatorToken))
            .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void updateMissingProductReturns404() throws Exception {
        mockMvc.perform(put("/api/products/99999")
                .header("Authorization", "Bearer " + coordinatorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Ghost","unitType":"PER_UNIT","price":1.00,"bay":"Z9"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void withdrawMissingProductReturns404() throws Exception {
        mockMvc.perform(post("/api/products/99999/withdraw")
                .header("Authorization", "Bearer " + coordinatorToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void memberCannotManageProducts() throws Exception {
        String memberToken = tokenFor("M-094", "coop1234");
        mockMvc.perform(get("/api/products").header("Authorization", "Bearer " + memberToken))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/products")
                .header("Authorization", "Bearer " + memberToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"X","unitType":"PER_UNIT","price":1.00}
                    """))
            .andExpect(status().isForbidden());
    }

    @Test
    void memberCannotUpdateOrWithdrawProducts() throws Exception {
        Long id = createProduct("Honey", UnitType.PER_UNIT, "12.00", "A1").getId();
        String memberToken = tokenFor("M-094", "coop1234");
        mockMvc.perform(put("/api/products/" + id)
                .header("Authorization", "Bearer " + memberToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Honey","unitType":"PER_UNIT","price":13.00,"bay":"A1"}
                    """))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/products/" + id + "/withdraw")
                .header("Authorization", "Bearer " + memberToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void invalidPriceIsRejected() throws Exception {
        mockMvc.perform(post("/api/products")
                .header("Authorization", "Bearer " + coordinatorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Bad","unitType":"PER_UNIT","price":0}
                    """))
            .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void overLengthNameOrBayIsRejected() throws Exception {
        mockMvc.perform(post("/api/products")
                .header("Authorization", "Bearer " + coordinatorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"%s","unitType":"PER_UNIT","price":1.00}
                    """.formatted("x".repeat(101))))
            .andExpect(jsonPath("$.code").value(400));

        mockMvc.perform(post("/api/products")
                .header("Authorization", "Bearer " + coordinatorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Valid name","unitType":"PER_UNIT","price":1.00,"bay":"%s"}
                    """.formatted("B".repeat(11))))
            .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void threeDecimalPriceIsRejected() throws Exception {
        mockMvc.perform(post("/api/products")
                .header("Authorization", "Bearer " + coordinatorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"Bad price","unitType":"PER_KG","price":3.456}
                    """))
            .andExpect(jsonPath("$.code").value(400));
    }
}
```

- [ ] **Step 3: 运行确认失败**

```bash
./mvnw test -Dtest=ProductApiTest
```

Expected: 404/编译失败。

- [ ] **Step 4: 写 DTO、Service、Controller**

```java
package com.greenhill.coop.dto;

import com.greenhill.coop.common.enums.UnitType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductRequest(@NotBlank @Size(max = 100) String name, @NotNull UnitType unitType,
                             @NotNull @DecimalMin(value = "0.01") @Digits(integer = 8, fraction = 2) BigDecimal price,
                             @Size(max = 10) String bay) {
}
```

```java
package com.greenhill.coop.dto;

import com.greenhill.coop.common.enums.ProductStatus;
import com.greenhill.coop.common.enums.UnitType;
import com.greenhill.coop.entity.Product;

import java.math.BigDecimal;

public record ProductView(Long id, String name, UnitType unitType, BigDecimal price, String bay, ProductStatus status) {

    public static ProductView from(Product p) {
        return new ProductView(p.getId(), p.getName(), p.getUnitType(), p.getPrice(), p.getBay(), p.getStatus());
    }
}
```

```java
package com.greenhill.coop.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.greenhill.coop.common.BizException;
import com.greenhill.coop.common.PageResult;
import com.greenhill.coop.common.enums.ProductStatus;
import com.greenhill.coop.dto.ProductRequest;
import com.greenhill.coop.dto.ProductView;
import com.greenhill.coop.entity.Product;
import com.greenhill.coop.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductMapper productMapper;

    public PageResult<ProductView> page(String keyword, ProductStatus status, long page, long size) {
        LambdaQueryWrapper<Product> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            qw.like(Product::getName, keyword);
        }
        if (status != null) {
            qw.eq(Product::getStatus, status);
        }
        qw.orderByAsc(Product::getName);
        Page<Product> result = productMapper.selectPage(new Page<>(page, size), qw);
        List<ProductView> views = result.getRecords().stream().map(ProductView::from).toList();
        return PageResult.of(views, result.getTotal(), result.getCurrent(), result.getSize());
    }

    public ProductView create(ProductRequest request) {
        Product product = new Product();
        apply(product, request);
        product.setStatus(ProductStatus.ACTIVE);
        productMapper.insert(product);
        return ProductView.from(product);
    }

    public ProductView update(Long id, ProductRequest request) {
        Product product = find(id);
        apply(product, request);
        productMapper.updateById(product);
        return ProductView.from(product);
    }

    public ProductView withdraw(Long id) {
        Product product = find(id);
        product.setStatus(ProductStatus.WITHDRAWN);
        productMapper.updateById(product);
        return ProductView.from(product);
    }

    private void apply(Product product, ProductRequest request) {
        product.setName(request.name());
        product.setUnitType(request.unitType());
        product.setPrice(request.price());
        product.setBay(request.bay());
    }

    private Product find(Long id) {
        Product product = productMapper.selectById(id);
        if (product == null) {
            throw BizException.notFound("Product not found");
        }
        return product;
    }
}
```

```java
package com.greenhill.coop.controller;

import com.greenhill.coop.auth.RequireCoordinator;
import com.greenhill.coop.common.PageResult;
import com.greenhill.coop.common.Result;
import com.greenhill.coop.common.enums.ProductStatus;
import com.greenhill.coop.dto.ProductRequest;
import com.greenhill.coop.dto.ProductView;
import com.greenhill.coop.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@RequireCoordinator
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public Result<PageResult<ProductView>> page(@RequestParam(required = false) String keyword,
                                                @RequestParam(required = false) ProductStatus status,
                                                @RequestParam(defaultValue = "1") long page,
                                                @RequestParam(defaultValue = "10") long size) {
        return Result.success(productService.page(keyword, status, page, size));
    }

    @PostMapping
    public Result<ProductView> create(@Valid @RequestBody ProductRequest request) {
        return Result.success(productService.create(request));
    }

    @PutMapping("/{id}")
    public Result<ProductView> update(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        return Result.success(productService.update(id, request));
    }

    @PostMapping("/{id}/withdraw")
    public Result<ProductView> withdraw(@PathVariable Long id) {
        return Result.success(productService.withdraw(id));
    }
}
```

- [ ] **Step 5: 运行测试(应通过)**

```bash
./mvnw test -Dtest=ProductApiTest
```

Expected: 10 个测试通过。

- [ ] **Step 6: 提交后端**

```bash
git add -A && git commit -m "feat(story-04): coordinator product management with per-unit/per-kg pricing type"
```

- [ ] **Step 7: 写前端 `api/product.js` 与 `ProductsPage.jsx`,接入路由并构建**

```js
import request from '../utils/request'

export function listProducts(params) {
  return request.get('/api/products', { params })
}

export function createProduct(data) {
  return request.post('/api/products', data)
}

export function updateProduct(id, data) {
  return request.put(`/api/products/${id}`, data)
}

export function withdrawProduct(id) {
  return request.post(`/api/products/${id}/withdraw`)
}

export function availableProducts() {
  return request.get('/api/products/available')
}
```

```jsx
import { App, Button, Tag } from 'antd'
import CrudTable from '../../components/CrudTable'
import { createProduct, listProducts, updateProduct, withdrawProduct } from '../../api/product'

const unitTypeLabels = { PER_UNIT: 'each', PER_KG: 'per kg' }

const filters = [
  { name: 'keyword', label: 'Keyword', placeholder: 'Product name' },
  {
    name: 'status', label: 'Status', type: 'select',
    options: [{ value: 'ACTIVE', label: 'Active' }, { value: 'WITHDRAWN', label: 'Withdrawn' }]
  }
]

const columns = [
  { title: 'Name', dataIndex: 'name' },
  { title: 'Sold', dataIndex: 'unitType', render: v => unitTypeLabels[v] || v },
  { title: 'Price (AUD)', dataIndex: 'price', render: v => Number(v).toFixed(2) },
  { title: 'Bay', dataIndex: 'bay' },
  {
    title: 'Status', dataIndex: 'status',
    render: value => <Tag color={value === 'ACTIVE' ? 'green' : 'orange'}>{value}</Tag>
  }
]

const formFields = [
  { name: 'name', label: 'Name', maxLength: 100, rules: [{ required: true }] },
  {
    name: 'unitType', label: 'Sold as', type: 'select', rules: [{ required: true }],
    options: [{ value: 'PER_UNIT', label: 'Per unit (each)' }, { value: 'PER_KG', label: 'Per kilogram' }]
  },
  { name: 'price', label: 'Price (AUD)', type: 'number', precision: 2, rules: [{ required: true }] },
  { name: 'bay', label: 'Bay (e.g. B1, VEG)', maxLength: 10 }
]

export default function ProductsPage() {
  const { message, modal } = App.useApp()

  function confirmWithdraw(record, reload) {
    modal.confirm({
      title: `Withdraw "${record.name}"?`,
      content: 'It will no longer be orderable. Existing orders are not affected.',
      okText: 'Withdraw',
      onOk: async () => {
        try {
          await withdrawProduct(record.id)
          message.success('Product withdrawn')
          reload()
        } catch (error) {
          message.error(error.message)
        }
      }
    })
  }

  return (
    <CrudTable
      title="Products"
      filters={filters}
      columns={columns}
      formFields={formFields}
      listApi={listProducts}
      createApi={createProduct}
      updateApi={updateProduct}
      extraActions={(record, reload) => record.status === 'ACTIVE' && (
        <Button size="small" onClick={() => confirmWithdraw(record, reload)}>Withdraw</Button>
      )}
    />
  )
}
```

修改 `frontend/src/App.jsx` 接入 `<ProductsPage />`,然后:

```bash
cd frontend && npm run build && cd ..
```

- [ ] **Step 8: 提交前端**

```bash
git add -A && git commit -m "feat(story-04): products admin page"
```

---

## Task 5: 协调员管理轮次(`story/05-rounds`)

**Files:**
- Create: `src/main/java/com/greenhill/coop/dto/{RoundCreateRequest,RoundView}.java`
- Create: `src/main/java/com/greenhill/coop/service/RoundService.java`
- Create: `src/main/java/com/greenhill/coop/controller/RoundController.java`
- Create: `src/test/java/com/greenhill/coop/RoundApiTest.java`
- Create: `frontend/src/api/round.js`、`frontend/src/pages/admin/RoundsPage.jsx`
- Modify: `frontend/src/App.jsx`

- [ ] **Step 1: 建分支**

```bash
git checkout -b story/05-rounds story/04-products
```

- [ ] **Step 2: 写失败测试 `RoundApiTest.java`**

```java
package com.greenhill.coop;

import com.greenhill.coop.common.enums.MemberRole;
import com.greenhill.coop.common.enums.RoundStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RoundApiTest extends ApiTestBase {

    private String coordinatorToken;

    @BeforeEach
    void setUp() throws Exception {
        createMember("M-001", "Ngaire Fletcher", MemberRole.COORDINATOR, "coop1234");
        createMember("M-094", "Ky Tran", MemberRole.MEMBER, "coop1234");
        coordinatorToken = tokenFor("M-001", "coop1234");
    }

    private String roundJson(int roundNo) {
        return """
            {"roundNo":%d,"ordersOpenAt":"2026-09-25T09:00:00","ordersCloseAt":"2026-09-27T20:00:00","pickupDate":"2026-10-01"}
            """.formatted(roundNo);
    }

    @Test
    void coordinatorCreatesOpenRound() throws Exception {
        mockMvc.perform(post("/api/rounds")
                .header("Authorization", "Bearer " + coordinatorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(roundJson(34)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("OPEN"))
            .andExpect(jsonPath("$.data.roundNo").value(34));
    }

    @Test
    void invalidRoundNumberOrMissingDatesAreRejected() throws Exception {
        mockMvc.perform(post("/api/rounds")
                .header("Authorization", "Bearer " + coordinatorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"roundNo":0,"ordersOpenAt":"2026-09-25T09:00:00","ordersCloseAt":"2026-09-27T20:00:00","pickupDate":"2026-10-01"}
                    """))
            .andExpect(jsonPath("$.code").value(400));

        mockMvc.perform(post("/api/rounds")
                .header("Authorization", "Bearer " + coordinatorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roundNo\":34}"))
            .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void onlyOneOpenRoundAllowed() throws Exception {
        createRound(33, RoundStatus.OPEN);
        mockMvc.perform(post("/api/rounds")
                .header("Authorization", "Bearer " + coordinatorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(roundJson(34)))
            .andExpect(jsonPath("$.code").value(409));
    }

    @Test
    void duplicateRoundNumberRejected() throws Exception {
        createRound(33, RoundStatus.PACKED);
        mockMvc.perform(post("/api/rounds")
                .header("Authorization", "Bearer " + coordinatorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(roundJson(33)))
            .andExpect(jsonPath("$.code").value(409));
    }

    @Test
    void roundListIsPagedAndOrderedByRoundNumberDescending() throws Exception {
        createRound(33, RoundStatus.PACKED);
        createRound(34, RoundStatus.OPEN);

        mockMvc.perform(get("/api/rounds")
                .header("Authorization", "Bearer " + coordinatorToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.total").value(2))
            .andExpect(jsonPath("$.data.records[0].roundNo").value(34))
            .andExpect(jsonPath("$.data.records[1].roundNo").value(33));
    }

    @Test
    void closeThenPackTransitionsStatus() throws Exception {
        Long id = createRound(33, RoundStatus.OPEN).getId();

        mockMvc.perform(post("/api/rounds/" + id + "/close")
                .header("Authorization", "Bearer " + coordinatorToken))
            .andExpect(jsonPath("$.data.status").value("CLOSED"));

        mockMvc.perform(post("/api/rounds/" + id + "/pack")
                .header("Authorization", "Bearer " + coordinatorToken))
            .andExpect(jsonPath("$.data.status").value("PACKED"));
    }

    @Test
    void invalidTransitionsRejected() throws Exception {
        Long openId = createRound(33, RoundStatus.OPEN).getId();
        mockMvc.perform(post("/api/rounds/" + openId + "/pack")
                .header("Authorization", "Bearer " + coordinatorToken))
            .andExpect(jsonPath("$.code").value(409));

        Long packedId = createRound(32, RoundStatus.PACKED).getId();
        mockMvc.perform(post("/api/rounds/" + packedId + "/close")
                .header("Authorization", "Bearer " + coordinatorToken))
            .andExpect(jsonPath("$.code").value(409));
    }

    @Test
    void transitionsOnUnknownRoundReturn404() throws Exception {
        mockMvc.perform(post("/api/rounds/99999/close")
                .header("Authorization", "Bearer " + coordinatorToken))
            .andExpect(jsonPath("$.code").value(404));
        mockMvc.perform(post("/api/rounds/99999/pack")
                .header("Authorization", "Bearer " + coordinatorToken))
            .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void currentReturnsOpenRoundOrNull() throws Exception {
        createRound(33, RoundStatus.PACKED);
        String memberToken = tokenFor("M-094", "coop1234");
        mockMvc.perform(get("/api/rounds/current").header("Authorization", "Bearer " + memberToken))
            .andExpect(jsonPath("$.data", org.hamcrest.Matchers.nullValue()));

        createRound(34, RoundStatus.OPEN);
        mockMvc.perform(get("/api/rounds/current").header("Authorization", "Bearer " + memberToken))
            .andExpect(jsonPath("$.data.roundNo").value(34));
    }

    @Test
    void memberCannotManageRounds() throws Exception {
        String memberToken = tokenFor("M-094", "coop1234");
        mockMvc.perform(post("/api/rounds")
                .header("Authorization", "Bearer " + memberToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(roundJson(34)))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/rounds").header("Authorization", "Bearer " + memberToken))
            .andExpect(status().isForbidden());
    }
}
```

- [ ] **Step 3: 运行确认失败**

```bash
./mvnw test -Dtest=RoundApiTest
```

Expected: 404/编译失败。

- [ ] **Step 4: 写 DTO、Service、Controller**

```java
package com.greenhill.coop.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record RoundCreateRequest(@NotNull @Positive Integer roundNo, @NotNull LocalDateTime ordersOpenAt,
                                 @NotNull LocalDateTime ordersCloseAt, @NotNull LocalDate pickupDate) {
}
```

```java
package com.greenhill.coop.dto;

import com.greenhill.coop.common.enums.RoundStatus;
import com.greenhill.coop.entity.Round;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record RoundView(Long id, Integer roundNo, LocalDateTime ordersOpenAt, LocalDateTime ordersCloseAt,
                        LocalDate pickupDate, RoundStatus status) {

    public static RoundView from(Round r) {
        return new RoundView(r.getId(), r.getRoundNo(), r.getOrdersOpenAt(), r.getOrdersCloseAt(),
            r.getPickupDate(), r.getStatus());
    }
}
```

```java
package com.greenhill.coop.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.greenhill.coop.common.BizException;
import com.greenhill.coop.common.PageResult;
import com.greenhill.coop.common.enums.RoundStatus;
import com.greenhill.coop.dto.RoundCreateRequest;
import com.greenhill.coop.dto.RoundView;
import com.greenhill.coop.entity.Round;
import com.greenhill.coop.mapper.RoundMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RoundService {

    private final RoundMapper roundMapper;

    public PageResult<RoundView> page(long page, long size) {
        Page<Round> result = roundMapper.selectPage(new Page<>(page, size),
            new LambdaQueryWrapper<Round>().orderByDesc(Round::getRoundNo));
        List<RoundView> views = result.getRecords().stream().map(RoundView::from).toList();
        return PageResult.of(views, result.getTotal(), result.getCurrent(), result.getSize());
    }

    public synchronized RoundView create(RoundCreateRequest request) {
        if (roundMapper.selectCount(new LambdaQueryWrapper<Round>().eq(Round::getRoundNo, request.roundNo())) > 0) {
            throw BizException.conflict("Round number already exists");
        }
        if (currentOpen() != null) {
            throw BizException.conflict("Another round is still open");
        }
        Round round = new Round();
        round.setRoundNo(request.roundNo());
        round.setOrdersOpenAt(request.ordersOpenAt());
        round.setOrdersCloseAt(request.ordersCloseAt());
        round.setPickupDate(request.pickupDate());
        round.setStatus(RoundStatus.OPEN);
        roundMapper.insert(round);
        return RoundView.from(round);
    }

    public RoundView close(Long id) {
        return transition(id, RoundStatus.OPEN, RoundStatus.CLOSED, "Only an open round can be closed");
    }

    public RoundView pack(Long id) {
        return transition(id, RoundStatus.CLOSED, RoundStatus.PACKED, "Only a closed round can be packed");
    }

    public Round currentOpen() {
        return roundMapper.selectList(new LambdaQueryWrapper<Round>()
                .eq(Round::getStatus, RoundStatus.OPEN)
                .orderByDesc(Round::getRoundNo)
                .last("LIMIT 1"))
            .stream().findFirst().orElse(null);
    }

    private RoundView transition(Long id, RoundStatus from, RoundStatus to, String errorMessage) {
        Round round = find(id);
        if (round.getStatus() != from) {
            throw BizException.conflict(errorMessage);
        }
        round.setStatus(to);
        roundMapper.updateById(round);
        return RoundView.from(round);
    }

    private Round find(Long id) {
        Round round = roundMapper.selectById(id);
        if (round == null) {
            throw BizException.notFound("Round not found");
        }
        return round;
    }
}
```

```java
package com.greenhill.coop.controller;

import com.greenhill.coop.auth.RequireCoordinator;
import com.greenhill.coop.common.PageResult;
import com.greenhill.coop.common.Result;
import com.greenhill.coop.dto.RoundCreateRequest;
import com.greenhill.coop.dto.RoundView;
import com.greenhill.coop.service.RoundService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rounds")
@RequiredArgsConstructor
public class RoundController {

    private final RoundService roundService;

    @GetMapping("/current")
    public Result<RoundView> current() {
        var round = roundService.currentOpen();
        return Result.success(round == null ? null : RoundView.from(round));
    }

    @GetMapping
    @RequireCoordinator
    public Result<PageResult<RoundView>> page(@RequestParam(defaultValue = "1") long page,
                                              @RequestParam(defaultValue = "10") long size) {
        return Result.success(roundService.page(page, size));
    }

    @PostMapping
    @RequireCoordinator
    public Result<RoundView> create(@Valid @RequestBody RoundCreateRequest request) {
        return Result.success(roundService.create(request));
    }

    @PostMapping("/{id}/close")
    @RequireCoordinator
    public Result<RoundView> close(@PathVariable Long id) {
        return Result.success(roundService.close(id));
    }

    @PostMapping("/{id}/pack")
    @RequireCoordinator
    public Result<RoundView> pack(@PathVariable Long id) {
        return Result.success(roundService.pack(id));
    }
}
```

- [ ] **Step 5: 运行测试(应通过)**

```bash
./mvnw test -Dtest=RoundApiTest
```

Expected: 10 个测试通过。

- [ ] **Step 6: 提交后端**

```bash
git add -A && git commit -m "feat(story-05): round lifecycle with single open round enforcement"
```

- [ ] **Step 7: 写前端 `api/round.js` 与 `RoundsPage.jsx`,接入路由并构建**

```js
import request from '../utils/request'

export function currentRound() {
  return request.get('/api/rounds/current')
}

export function listRounds(params) {
  return request.get('/api/rounds', { params })
}

export function createRound(data) {
  return request.post('/api/rounds', data)
}

export function closeRound(id) {
  return request.post(`/api/rounds/${id}/close`)
}

export function packRound(id) {
  return request.post(`/api/rounds/${id}/pack`)
}

export function roundTotals(id) {
  return request.get(`/api/rounds/${id}/totals`)
}
```

```jsx
import { App, Button, Form, InputNumber, Modal, Space, Tag, Table, Typography, DatePicker } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import { useEffect, useState } from 'react'
import { closeRound, createRound, listRounds, packRound } from '../../api/round'

const statusColors = { OPEN: 'green', CLOSED: 'blue', PACKED: 'default' }

export default function RoundsPage() {
  const { message, modal } = App.useApp()
  const [records, setRecords] = useState([])
  const [loading, setLoading] = useState(false)
  const [creating, setCreating] = useState(false)
  const [form] = Form.useForm()

  useEffect(() => { load() }, [])

  async function load() {
    setLoading(true)
    try {
      const data = await listRounds({ page: 1, size: 50 })
      setRecords(data?.records || [])
    } catch (error) {
      message.error(error.message)
    } finally {
      setLoading(false)
    }
  }

  async function submitCreate() {
    const values = await form.validateFields()
    await createRound({
      roundNo: values.roundNo,
      ordersOpenAt: values.ordersOpenAt.format('YYYY-MM-DDTHH:mm:ss'),
      ordersCloseAt: values.ordersCloseAt.format('YYYY-MM-DDTHH:mm:ss'),
      pickupDate: values.pickupDate.format('YYYY-MM-DD')
    })
    message.success('Round opened')
    setCreating(false)
    form.resetFields()
    load()
  }

  async function doTransition(id, action, label) {
    modal.confirm({
      title: `${label} this round?`,
      okText: label,
      onOk: async () => {
        await action(id)
        message.success(`Round ${label.toLowerCase()}ed`)
        load()
      }
    })
  }

  const columns = [
    { title: 'Round', dataIndex: 'roundNo' },
    { title: 'Opens', dataIndex: 'ordersOpenAt', render: v => v?.replace('T', ' ') },
    { title: 'Closes', dataIndex: 'ordersCloseAt', render: v => v?.replace('T', ' ') },
    { title: 'Pickup', dataIndex: 'pickupDate' },
    { title: 'Status', dataIndex: 'status', render: v => <Tag color={statusColors[v]}>{v}</Tag> },
    {
      title: 'Actions', key: 'actions',
      render: (_, record) => (
        <Space>
          {record.status === 'OPEN' && (
            <Button size="small" onClick={() => doTransition(record.id, closeRound, 'Close')}>Close</Button>
          )}
          {record.status === 'CLOSED' && (
            <Button size="small" onClick={() => doTransition(record.id, packRound, 'Pack')}>Mark packed</Button>
          )}
        </Space>
      )
    }
  ]

  return (
    <div>
      <Space style={{ width: '100%', justifyContent: 'space-between', marginBottom: 16 }}>
        <Typography.Title level={4} style={{ margin: 0 }}>Rounds</Typography.Title>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreating(true)}>Open new round</Button>
      </Space>
      <Table rowKey="id" loading={loading} columns={columns} dataSource={records} pagination={false} />
      <Modal
        title="Open new round"
        open={creating} okText="Open" onOk={submitCreate}
        onCancel={() => setCreating(false)} destroyOnHidden
      >
        <Form form={form} layout="vertical">
          <Form.Item name="roundNo" label="Round number" rules={[{ required: true }]}>
            <InputNumber min={1} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="ordersOpenAt" label="Orders open" rules={[{ required: true }]}>
            <DatePicker showTime style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="ordersCloseAt" label="Orders close" rules={[{ required: true }]}>
            <DatePicker showTime style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="pickupDate" label="Pickup date" rules={[{ required: true }]}>
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}
```

修改 `frontend/src/App.jsx` 接入 `<RoundsPage />`,然后:

```bash
cd frontend && npm run build && cd ..
```

- [ ] **Step 8: 提交前端**

```bash
git add -A && git commit -m "feat(story-05): rounds admin page"
```

---

## Task 6: 会员查看可订商品(`story/06-catalog`)

**Files:**
- Create: `src/main/java/com/greenhill/coop/dto/AvailableProductsView.java`
- Modify: `src/main/java/com/greenhill/coop/service/ProductService.java`(加 `available()`)
- Create: `src/main/java/com/greenhill/coop/controller/CatalogController.java`
- Create: `src/test/java/com/greenhill/coop/CatalogApiTest.java`
- Create: `frontend/src/pages/ShopPage.jsx`
- Modify: `frontend/src/App.jsx`

- [ ] **Step 1: 建分支**

```bash
git checkout -b story/06-catalog story/05-rounds
```

- [ ] **Step 2: 写失败测试 `CatalogApiTest.java`**

```java
package com.greenhill.coop;

import com.greenhill.coop.common.enums.MemberRole;
import com.greenhill.coop.common.enums.RoundStatus;
import com.greenhill.coop.common.enums.UnitType;
import com.greenhill.coop.entity.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CatalogApiTest extends ApiTestBase {

    private String memberToken;

    @BeforeEach
    void setUp() throws Exception {
        createMember("M-094", "Ky Tran", MemberRole.MEMBER, "coop1234");
        memberToken = tokenFor("M-094", "coop1234");
    }

    @Test
    void withoutOpenRoundReturnsNullRoundAndEmptyList() throws Exception {
        createRound(33, RoundStatus.PACKED);
        createProduct("Rolled oats", UnitType.PER_KG, "3.40", "B1");
        mockMvc.perform(get("/api/products/available").header("Authorization", "Bearer " + memberToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.round", org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.data.products").isEmpty());
    }

    @Test
    void returnsOnlyActiveProductsWithPricingInfo() throws Exception {
        createRound(34, RoundStatus.OPEN);
        createProduct("Rolled oats, organic", UnitType.PER_KG, "3.40", "B1");
        createProduct("Tahini, 375g jar", UnitType.PER_UNIT, "9.80", "A2");
        Product withdrawn = createProduct("Raw almonds", UnitType.PER_KG, "18.90", "C1");
        withdrawn.setStatus(com.greenhill.coop.common.enums.ProductStatus.WITHDRAWN);
        productMapper.updateById(withdrawn);

        mockMvc.perform(get("/api/products/available").header("Authorization", "Bearer " + memberToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.round.roundNo").value(34))
            .andExpect(jsonPath("$.data.products.length()").value(2))
            .andExpect(jsonPath("$.data.products[0].name").value("Rolled oats, organic"))
            .andExpect(jsonPath("$.data.products[0].unitType").value("PER_KG"))
            .andExpect(jsonPath("$.data.products[0].price").value(3.40))
            .andExpect(jsonPath("$.data.products[1].name").value("Tahini, 375g jar"));
    }

    @Test
    void requiresLogin() throws Exception {
        mockMvc.perform(get("/api/products/available"))
            .andExpect(status().isUnauthorized());
    }
}
```

- [ ] **Step 3: 运行确认失败**

```bash
./mvnw test -Dtest=CatalogApiTest
```

Expected: 404/编译失败。

- [ ] **Step 4: 写实现**

```java
package com.greenhill.coop.dto;

import java.util.List;

public record AvailableProductsView(RoundView round, List<ProductView> products) {
}
```

在 `ProductService` 中新增(注入 `RoundService`;若循环依赖则改为注入 `RoundMapper`——本设计 `RoundService` 不依赖 `ProductService`,直接注入即可):

```java
    public AvailableProductsView available() {
        Round openRound = roundService.currentOpen();
        if (openRound == null) {
            return new AvailableProductsView(null, List.of());
        }
        List<ProductView> products = productMapper.selectList(new LambdaQueryWrapper<Product>()
                .eq(Product::getStatus, ProductStatus.ACTIVE)
                .orderByAsc(Product::getName))
            .stream().map(ProductView::from).toList();
        return new AvailableProductsView(RoundView.from(openRound), products);
    }
```

同时给 `ProductService` 增加字段与 import:

```java
import com.greenhill.coop.dto.AvailableProductsView;
import com.greenhill.coop.dto.RoundView;
import com.greenhill.coop.entity.Round;
// ...
    private final RoundService roundService;
```

```java
package com.greenhill.coop.controller;

import com.greenhill.coop.common.Result;
import com.greenhill.coop.dto.AvailableProductsView;
import com.greenhill.coop.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class CatalogController {

    private final ProductService productService;

    @GetMapping("/available")
    public Result<AvailableProductsView> available() {
        return Result.success(productService.available());
    }
}
```

- [ ] **Step 5: 运行测试(应通过)**

```bash
./mvnw test -Dtest=CatalogApiTest
```

Expected: 3 个测试通过。

- [ ] **Step 6: 提交后端**

```bash
git add -A && git commit -m "feat(story-06): member-facing available products endpoint"
```

- [ ] **Step 7: 写 `ShopPage.jsx`(本任务只展示商品列表;下单在 Task 7)**

```jsx
import { useEffect, useState } from 'react'
import { Alert, Card, Space, Spin, Table, Tag, Typography, message } from 'antd'
import { availableProducts } from '../api/product'

const unitTypeLabels = { PER_UNIT: 'each', PER_KG: 'per kg' }

export default function ShopPage() {
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    setLoading(true)
    availableProducts()
      .then(setData)
      .catch(error => message.error(error.message))
      .finally(() => setLoading(false))
  }, [])

  if (!data) {
    return <Spin style={{ display: 'block', marginTop: 80 }} />
  }

  if (!data.round) {
    return (
      <Alert
        type="info"
        showIcon
        message="No round is open for ordering"
        description="Ordering opens on Friday morning. Please check back later."
      />
    )
  }

  const columns = [
    { title: 'Product', dataIndex: 'name' },
    { title: 'Sold', dataIndex: 'unitType', render: v => unitTypeLabels[v] || v },
    { title: 'Price (AUD)', dataIndex: 'price', render: v => Number(v).toFixed(2) },
    { title: 'Bay', dataIndex: 'bay' }
  ]

  return (
    <Space direction="vertical" style={{ width: '100%' }} size="large">
      <Card>
        <Typography.Title level={4} style={{ margin: 0 }}>
          Round {data?.round?.roundNo} — available products
        </Typography.Title>
        <Typography.Text type="secondary">
          Orders close {data?.round?.ordersCloseAt?.replace('T', ' ')} · Pickup {data?.round?.pickupDate}
        </Typography.Text>
        <Tag style={{ marginLeft: 12 }} color="green">OPEN</Tag>
      </Card>
      <Table rowKey="id" loading={loading} columns={columns} dataSource={data?.products || []} pagination={false} />
    </Space>
  )
}
```

修改 `frontend/src/App.jsx` 接入 `<ShopPage />`,然后:

```bash
cd frontend && npm run build && cd ..
```

- [ ] **Step 8: 提交前端**

```bash
git add -A && git commit -m "feat(story-06): shop page product list with no-open-round notice"
```

---

## Task 7: 会员下单与计价引擎(`story/07-place-order`)

**Files:**
- Create: `src/main/java/com/greenhill/coop/service/PricingService.java`
- Create: `src/main/java/com/greenhill/coop/dto/{OrderLineRequest,PlaceOrderRequest,OrderLineView,OrderView}.java`
- Create: `src/main/java/com/greenhill/coop/service/OrderService.java`
- Create: `src/main/java/com/greenhill/coop/controller/OrderController.java`
- Create: `src/test/java/com/greenhill/coop/PricingServiceTest.java`, `src/test/java/com/greenhill/coop/OrderApiTest.java`, `src/test/java/com/greenhill/coop/OrderRollbackTest.java`
- Create: `frontend/src/api/order.js`
- Modify: `frontend/src/pages/ShopPage.jsx`(加数量输入、订单篮、保存)

- [ ] **Step 1: 建分支**

```bash
git checkout -b story/07-place-order story/06-catalog
```

- [ ] **Step 2: 写失败测试 `PricingServiceTest.java`(纯单元测试)**

```java
package com.greenhill.coop;

import com.greenhill.coop.common.BizException;
import com.greenhill.coop.common.enums.UnitType;
import com.greenhill.coop.entity.OrderLine;
import com.greenhill.coop.service.PricingService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PricingServiceTest {

    private final PricingService pricingService = new PricingService();

    private OrderLine line(UnitType unitType, String quantity, String unitPrice) {
        OrderLine line = new OrderLine();
        BigDecimal qty = new BigDecimal(quantity);
        BigDecimal price = new BigDecimal(unitPrice);
        line.setQuantity(qty);
        line.setUnitPrice(price);
        line.setLineTotal(pricingService.lineTotal(unitType, qty, price));
        return line;
    }

    @Test
    void perUnitLineIsCountTimesPrice() {
        assertThat(pricingService.lineTotal(UnitType.PER_UNIT, new BigDecimal("2"), new BigDecimal("7.50")))
            .isEqualByComparingTo("15.00");
    }

    @Test
    void perKgLineIsWeightTimesPricePerKg() {
        assertThat(pricingService.lineTotal(UnitType.PER_KG, new BigDecimal("1.5"), new BigDecimal("3.40")))
            .isEqualByComparingTo("5.10");
    }

    @Test
    void perKgAcceptsQuarterKilogram() {
        assertThat(pricingService.lineTotal(UnitType.PER_KG, new BigDecimal("0.25"), new BigDecimal("32.00")))
            .isEqualByComparingTo("8.00");
    }

    @Test
    void perKgRoundsHalfUp() {
        assertThat(pricingService.lineTotal(UnitType.PER_KG, new BigDecimal("0.5"), new BigDecimal("4.85")))
            .isEqualByComparingTo("2.43");
    }

    @Test
    void perKgUsesActualPackedWeightStyleDecimals() {
        assertThat(pricingService.lineTotal(UnitType.PER_KG, new BigDecimal("1.58"), new BigDecimal("3.40")))
            .isEqualByComparingTo("5.37");
    }

    @Test
    void perKgAcceptsTrailingZeroDecimals() {
        assertThat(pricingService.lineTotal(UnitType.PER_KG, new BigDecimal("1.5000"), new BigDecimal("3.40")))
            .isEqualByComparingTo("5.10");
    }

    @Test
    void perKgAcceptsExactlyThreeDecimals() {
        assertThat(pricingService.lineTotal(UnitType.PER_KG, new BigDecimal("1.234"), new BigDecimal("3.40")))
            .isEqualByComparingTo("4.20");
    }

    @Test
    void perUnitAcceptsTrailingZeroDecimals() {
        assertThat(pricingService.lineTotal(UnitType.PER_UNIT, new BigDecimal("2.0"), new BigDecimal("7.50")))
            .isEqualByComparingTo("15.00");
    }

    @Test
    void perUnitRejectsFractionalQuantity() {
        assertThatThrownBy(() -> pricingService.lineTotal(UnitType.PER_UNIT, new BigDecimal("1.5"), new BigDecimal("9.80")))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("whole number");
    }

    @Test
    void perKgRejectsMoreThanThreeDecimals() {
        assertThatThrownBy(() -> pricingService.lineTotal(UnitType.PER_KG, new BigDecimal("0.1234"), new BigDecimal("3.40")))
            .isInstanceOf(BizException.class)
            .hasMessageContaining("3 decimal");
    }

    @Test
    void rejectsZeroAndNegativeQuantity() {
        assertThatThrownBy(() -> pricingService.lineTotal(UnitType.PER_KG, BigDecimal.ZERO, new BigDecimal("3.40")))
            .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> pricingService.lineTotal(UnitType.PER_UNIT, new BigDecimal("-1"), new BigDecimal("7.50")))
            .isInstanceOf(BizException.class);
    }

    @Test
    void kyTranRound33OrderTotalsTo54Dollars85() {
        List<OrderLine> lines = List.of(
            line(UnitType.PER_KG, "1.5", "3.40"),
            line(UnitType.PER_KG, "2", "4.10"),
            line(UnitType.PER_KG, "1", "4.85"),
            line(UnitType.PER_KG, "0.25", "32.00"),
            line(UnitType.PER_UNIT, "1", "9.80"),
            line(UnitType.PER_UNIT, "2", "7.50"),
            line(UnitType.PER_KG, "1.5", "2.60")
        );
        assertThat(pricingService.orderTotal(lines)).isEqualByComparingTo("54.85");
    }
}
```

- [ ] **Step 3: 写失败测试 `OrderApiTest.java` 与 `OrderRollbackTest.java`(故事 7 部分)**

```java
package com.greenhill.coop;

import com.greenhill.coop.common.enums.MemberRole;
import com.greenhill.coop.common.enums.RoundStatus;
import com.greenhill.coop.common.enums.UnitType;
import com.greenhill.coop.entity.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderApiTest extends ApiTestBase {

    private String memberToken;
    private Long oatsId;
    private Long eggsId;
    private Long roundId;

    @BeforeEach
    void setUp() throws Exception {
        createMember("M-001", "Ngaire Fletcher", MemberRole.COORDINATOR, "coop1234");
        createMember("M-094", "Ky Tran", MemberRole.MEMBER, "coop1234");
        createMember("M-041", "Doug Halvorsen", MemberRole.MEMBER, "coop1234");
        memberToken = tokenFor("M-094", "coop1234");
        roundId = createRound(34, RoundStatus.OPEN).getId();
        oatsId = createProduct("Rolled oats, organic", UnitType.PER_KG, "3.40", "B1").getId();
        eggsId = createProduct("Eggs, free range, dozen", UnitType.PER_UNIT, "7.50", "COOL").getId();
    }

    private String linesJson() {
        return """
            {"lines":[{"productId":%d,"quantity":1.5},{"productId":%d,"quantity":2}]}
            """.formatted(oatsId, eggsId);
    }

    @Test
    void memberPlacesOrderWithPerKgAndPerUnitLines() throws Exception {
        mockMvc.perform(put("/api/orders/mine")
                .header("Authorization", "Bearer " + memberToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(linesJson()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.roundNo").value(34))
            .andExpect(jsonPath("$.data.total").value(20.10))
            .andExpect(jsonPath("$.data.lines.length()").value(2));
    }

    @Test
    void orderIsSavedAndRetrievable() throws Exception {
        mockMvc.perform(put("/api/orders/mine")
            .header("Authorization", "Bearer " + memberToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(linesJson()));

        mockMvc.perform(get("/api/orders/mine").header("Authorization", "Bearer " + memberToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].lines.length()").value(2));
    }

    @Test
    void placingAgainReplacesLines() throws Exception {
        mockMvc.perform(put("/api/orders/mine")
            .header("Authorization", "Bearer " + memberToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(linesJson()));

        mockMvc.perform(put("/api/orders/mine")
                .header("Authorization", "Bearer " + memberToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"lines\":[{\"productId\":%d,\"quantity\":3}]}".formatted(eggsId)))
            .andExpect(jsonPath("$.data.total").value(22.50))
            .andExpect(jsonPath("$.data.lines.length()").value(1));
    }

    @Test
    void perUnitFractionalQuantityRejected() throws Exception {
        mockMvc.perform(put("/api/orders/mine")
                .header("Authorization", "Bearer " + memberToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"lines\":[{\"productId\":%d,\"quantity\":1.5}]}".formatted(eggsId)))
            .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void duplicateProductRejected() throws Exception {
        mockMvc.perform(put("/api/orders/mine")
                .header("Authorization", "Bearer " + memberToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"lines\":[{\"productId\":%d,\"quantity\":1},{\"productId\":%d,\"quantity\":2}]}"
                    .formatted(oatsId, oatsId)))
            .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void emptyOrderRejected() throws Exception {
        mockMvc.perform(put("/api/orders/mine")
                .header("Authorization", "Bearer " + memberToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"lines\":[]}"))
            .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void cannotOrderWhenNoRoundIsOpen() throws Exception {
        mockMvc.perform(put("/api/orders/mine")
                .header("Authorization", "Bearer " + tokenFor("M-041", "coop1234"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(linesJson()));

        roundMapper.deleteById(roundId);

        mockMvc.perform(put("/api/orders/mine")
                .header("Authorization", "Bearer " + memberToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(linesJson()))
            .andExpect(jsonPath("$.code").value(409));
    }

    @Test
    void priceIsSnapshottedAtOrderTime() throws Exception {
        mockMvc.perform(put("/api/orders/mine")
            .header("Authorization", "Bearer " + memberToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(linesJson()));

        Product oats = productMapper.selectById(oatsId);
        oats.setPrice(new java.math.BigDecimal("4.00"));
        productMapper.updateById(oats);

        mockMvc.perform(get("/api/orders/mine").header("Authorization", "Bearer " + memberToken))
            .andExpect(jsonPath("$.data[0].total").value(20.10))
            .andExpect(jsonPath("$.data[0].lines[0].unitPrice").value(3.40));
    }

    @Test
    void withdrawnProductCannotBeOrdered() throws Exception {
        Product oats = productMapper.selectById(oatsId);
        oats.setStatus(com.greenhill.coop.common.enums.ProductStatus.WITHDRAWN);
        productMapper.updateById(oats);

        mockMvc.perform(put("/api/orders/mine")
                .header("Authorization", "Bearer " + memberToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(linesJson()))
            .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void membersOnlySeeTheirOwnOrders() throws Exception {
        mockMvc.perform(put("/api/orders/mine")
            .header("Authorization", "Bearer " + memberToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(linesJson()));

        mockMvc.perform(get("/api/orders/mine")
                .header("Authorization", "Bearer " + tokenFor("M-041", "coop1234")))
            .andExpect(jsonPath("$.data.length()").value(0));
    }
}
```

- [ ] **Step 4: 运行确认失败**

```bash
./mvnw test -Dtest=PricingServiceTest,OrderApiTest
```

Expected: 编译失败(`PricingService`、`OrderService` 等不存在)。

- [ ] **Step 5: 写 `PricingService.java`**

```java
package com.greenhill.coop.service;

import com.greenhill.coop.common.BizException;
import com.greenhill.coop.common.enums.UnitType;
import com.greenhill.coop.entity.OrderLine;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class PricingService {

    public BigDecimal lineTotal(UnitType unitType, BigDecimal quantity, BigDecimal unitPrice) {
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw BizException.badRequest("Quantity must be greater than zero");
        }
        if (unitType == UnitType.PER_UNIT && quantity.stripTrailingZeros().scale() > 0) {
            throw BizException.badRequest("This product is sold by the unit; quantity must be a whole number");
        }
        if (unitType == UnitType.PER_KG && quantity.stripTrailingZeros().scale() > 3) {
            throw BizException.badRequest("This product is sold by weight; use at most 3 decimal places");
        }
        return quantity.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal orderTotal(List<OrderLine> lines) {
        return lines.stream()
            .map(OrderLine::getLineTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
    }
}
```

- [ ] **Step 6: 写 4 个 DTO**

```java
package com.greenhill.coop.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record OrderLineRequest(@NotNull Long productId, @NotNull BigDecimal quantity) {
}
```

```java
package com.greenhill.coop.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record PlaceOrderRequest(@NotEmpty List<@Valid OrderLineRequest> lines) {
}
```

```java
package com.greenhill.coop.dto;

import com.greenhill.coop.common.enums.UnitType;

import java.math.BigDecimal;

public record OrderLineView(Long id, Long productId, String productName, UnitType unitType,
                            BigDecimal quantity, BigDecimal unitPrice, BigDecimal lineTotal) {
}
```

```java
package com.greenhill.coop.dto;

import com.greenhill.coop.common.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderView(Long id, Long roundId, Integer roundNo, Long memberId, String memberNo, String memberName,
                        OrderStatus status, BigDecimal total, List<OrderLineView> lines, LocalDateTime createdAt) {
}
```

- [ ] **Step 7: 写 `OrderService.java`**

```java
package com.greenhill.coop.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.greenhill.coop.common.BizException;
import com.greenhill.coop.common.enums.MemberStatus;
import com.greenhill.coop.common.enums.OrderStatus;
import com.greenhill.coop.common.enums.ProductStatus;
import com.greenhill.coop.dto.OrderLineRequest;
import com.greenhill.coop.dto.OrderLineView;
import com.greenhill.coop.dto.OrderView;
import com.greenhill.coop.dto.PlaceOrderRequest;
import com.greenhill.coop.entity.Member;
import com.greenhill.coop.entity.Order;
import com.greenhill.coop.entity.OrderLine;
import com.greenhill.coop.entity.Product;
import com.greenhill.coop.entity.Round;
import com.greenhill.coop.mapper.MemberMapper;
import com.greenhill.coop.mapper.OrderLineMapper;
import com.greenhill.coop.mapper.OrderMapper;
import com.greenhill.coop.mapper.ProductMapper;
import com.greenhill.coop.mapper.RoundMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderMapper orderMapper;
    private final OrderLineMapper orderLineMapper;
    private final ProductMapper productMapper;
    private final MemberMapper memberMapper;
    private final RoundMapper roundMapper;
    private final RoundService roundService;
    private final PricingService pricingService;

    @Transactional
    public OrderView placeMyOrder(Long memberId, PlaceOrderRequest request) {
        return place(memberId, request);
    }

    @Transactional
    public OrderView placeForMember(Long memberId, PlaceOrderRequest request) {
        Member member = memberMapper.selectById(memberId);
        if (member == null) {
            throw BizException.notFound("Member not found");
        }
        if (member.getStatus() != MemberStatus.ACTIVE) {
            throw BizException.badRequest("Member is inactive");
        }
        return place(memberId, request);
    }

    public List<OrderView> myOrders(Long memberId) {
        List<Order> orders = orderMapper.selectList(new LambdaQueryWrapper<Order>()
            .eq(Order::getMemberId, memberId)
            .orderByDesc(Order::getCreatedAt));
        return toViews(orders);
    }

    public List<OrderView> roundOrders(Long roundId) {
        List<Order> orders = orderMapper.selectList(new LambdaQueryWrapper<Order>()
            .eq(Order::getRoundId, roundId)
            .eq(Order::getStatus, OrderStatus.ACTIVE)
            .orderByAsc(Order::getId));
        return toViews(orders);
    }

    private OrderView place(Long memberId, PlaceOrderRequest request) {
        Round round = roundService.currentOpen();
        if (round == null) {
            throw BizException.conflict("No round is open for ordering");
        }
        if (request.lines() == null || request.lines().isEmpty()) {
            throw BizException.badRequest("Order must contain at least one line");
        }

        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
            .eq(Order::getMemberId, memberId)
            .eq(Order::getRoundId, round.getId()));
        if (order == null) {
            order = new Order();
            order.setMemberId(memberId);
            order.setRoundId(round.getId());
            order.setStatus(OrderStatus.ACTIVE);
            orderMapper.insert(order);
        } else {
            order.setStatus(OrderStatus.ACTIVE);
            orderMapper.updateById(order);
            orderLineMapper.delete(new LambdaQueryWrapper<OrderLine>().eq(OrderLine::getOrderId, order.getId()));
        }

        Set<Long> seen = new HashSet<>();
        for (OrderLineRequest lineRequest : request.lines()) {
            if (!seen.add(lineRequest.productId())) {
                throw BizException.badRequest("Duplicate product in order");
            }
            Product product = productMapper.selectById(lineRequest.productId());
            if (product == null) {
                throw BizException.notFound("Product not found");
            }
            if (product.getStatus() != ProductStatus.ACTIVE) {
                throw BizException.badRequest(product.getName() + " is not available");
            }
            OrderLine line = new OrderLine();
            line.setOrderId(order.getId());
            line.setProductId(product.getId());
            line.setQuantity(lineRequest.quantity());
            line.setUnitTypeSnapshot(product.getUnitType());
            line.setUnitPrice(product.getPrice());
            line.setLineTotal(pricingService.lineTotal(product.getUnitType(), lineRequest.quantity(), product.getPrice()));
            orderLineMapper.insert(line);
        }
        return toViews(List.of(order)).get(0);
    }

    private List<OrderView> toViews(List<Order> orders) {
        if (orders.isEmpty()) {
            return List.of();
        }
        Map<Long, Round> rounds = roundMapper.selectBatchIds(
                orders.stream().map(Order::getRoundId).distinct().toList())
            .stream().collect(Collectors.toMap(Round::getId, Function.identity()));
        Map<Long, Member> members = memberMapper.selectBatchIds(
                orders.stream().map(Order::getMemberId).distinct().toList())
            .stream().collect(Collectors.toMap(Member::getId, Function.identity()));

        List<OrderLine> allLines = orderLineMapper.selectList(new LambdaQueryWrapper<OrderLine>()
            .in(OrderLine::getOrderId, orders.stream().map(Order::getId).toList())
            .orderByAsc(OrderLine::getId));
        Map<Long, List<OrderLine>> linesByOrder = allLines.stream()
            .collect(Collectors.groupingBy(OrderLine::getOrderId));
        Map<Long, Product> products = allLines.isEmpty() ? Map.of()
            : productMapper.selectBatchIds(allLines.stream().map(OrderLine::getProductId).distinct().toList())
                .stream().collect(Collectors.toMap(Product::getId, Function.identity()));

        List<OrderView> views = new ArrayList<>();
        for (Order order : orders) {
            List<OrderLine> lines = linesByOrder.getOrDefault(order.getId(), List.of());
            BigDecimal total = pricingService.orderTotal(lines);
            List<OrderLineView> lineViews = lines.stream()
                .map(l -> new OrderLineView(l.getId(), l.getProductId(),
                    products.containsKey(l.getProductId()) ? products.get(l.getProductId()).getName() : "(removed)",
                    l.getUnitTypeSnapshot(), l.getQuantity(), l.getUnitPrice(), l.getLineTotal()))
                .toList();
            Round round = rounds.get(order.getRoundId());
            Member member = members.get(order.getMemberId());
            views.add(new OrderView(order.getId(), order.getRoundId(), round == null ? null : round.getRoundNo(),
                order.getMemberId(), member == null ? null : member.getMemberNo(),
                member == null ? null : member.getName(), order.getStatus(), total, lineViews, order.getCreatedAt()));
        }
        return views;
    }
}
```

- [ ] **Step 8: 写 `OrderController.java`(故事 7 部分)**

```java
package com.greenhill.coop.controller;

import com.greenhill.coop.auth.CurrentUser;
import com.greenhill.coop.auth.MemberContext;
import com.greenhill.coop.common.Result;
import com.greenhill.coop.dto.OrderView;
import com.greenhill.coop.dto.PlaceOrderRequest;
import com.greenhill.coop.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @GetMapping("/mine")
    public Result<List<OrderView>> myOrders(@CurrentUser MemberContext context) {
        return Result.success(orderService.myOrders(context.id()));
    }

    @PutMapping("/mine")
    public Result<OrderView> placeMine(@CurrentUser MemberContext context,
                                       @Valid @RequestBody PlaceOrderRequest request) {
        return Result.success(orderService.placeMyOrder(context.id(), request));
    }
}
```

- [ ] **Step 9: 运行测试(应通过)**

```bash
./mvnw test -Dtest=PricingServiceTest,OrderApiTest
```

Expected: `PricingServiceTest` 12 个 + `OrderApiTest` 10 个 + `OrderRollbackTest` 1 个全部通过。

- [ ] **Step 10: 提交后端**

```bash
git add -A && git commit -m "feat(story-07): pricing engine, order placement with price snapshot and tests"
```

- [ ] **Step 11: 写 `frontend/src/api/order.js`,扩展 `ShopPage.jsx`**

```js
import request from '../utils/request'

export function myOrders() {
  return request.get('/api/orders/mine')
}

export function placeOrder(data) {
  return request.put('/api/orders/mine', data)
}

export function cancelOrder() {
  return request.delete('/api/orders/mine')
}

export function ordersForRound(roundId) {
  return request.get('/api/orders', { params: { roundId } })
}

export function placeOrderForMember(data) {
  return request.post('/api/orders/for-member', data)
}
```

`ShopPage.jsx` 整文件替换为(含数量输入、订单篮、保存):

```jsx
import { useEffect, useMemo, useState } from 'react'
import { Alert, Button, Card, Col, InputNumber, Row, Space, Spin, Table, Tag, Typography, message } from 'antd'
import { availableProducts } from '../api/product'
import { myOrders, placeOrder } from '../api/order'

const unitTypeLabels = { PER_UNIT: 'each', PER_KG: 'per kg' }

function lineTotalCents(quantity, price) {
  const qMilli = Math.round(quantity * 1000)
  const pCents = Math.round(price * 100)
  return Math.round((qMilli * pCents) / 1000)
}

export default function ShopPage() {
  const [data, setData] = useState(null)
  const [quantities, setQuantities] = useState({})
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [loadError, setLoadError] = useState(null)

  useEffect(() => { load() }, [])

  async function load() {
    setLoading(true)
    setLoadError(null)
    try {
      const [available, orders] = await Promise.all([availableProducts(), myOrders()])
      setData(available)
      const current = (orders || []).find(o => o.status === 'ACTIVE' && o.roundId === available?.round?.id)
      setQuantities(current
        ? Object.fromEntries(current.lines.map(l => [l.productId, Number(l.quantity)]))
        : {})
      const currentLines = current?.lines || []
      const missing = currentLines.filter(l => !(available?.products || []).some(p => p.id === l.productId))
      if (missing.length > 0) {
        message.warning(`No longer available and removed from your basket: ${missing.map(l => l.productName).join(', ')}`)
      }
    } catch (error) {
      setLoadError(error.message)
      message.error(error.message)
    } finally {
      setLoading(false)
    }
  }

  const lines = useMemo(() => {
    if (!data?.products) return []
    return data.products
      .filter(p => quantities[p.id] > 0)
      .map(p => ({
        productId: p.id,
        name: p.name,
        unitType: p.unitType,
        quantity: quantities[p.id],
        unitPrice: Number(p.price),
        lineTotal: lineTotalCents(quantities[p.id], Number(p.price)) / 100
      }))
  }, [data, quantities])

  const total = lines.reduce((sum, l) => sum + Math.round(l.lineTotal * 100), 0) / 100

  async function save() {
    if (lines.length === 0) {
      message.warning('Add at least one product to your order')
      return
    }
    setSaving(true)
    try {
      await placeOrder({ lines: lines.map(l => ({ productId: l.productId, quantity: l.quantity })) })
      message.success('Order saved')
      load()
    } catch (error) {
      message.error(error.message)
    } finally {
      setSaving(false)
    }
  }

  if (!data) {
    if (loadError) {
      return <Alert type="error" showIcon message="Could not load the shop" description={loadError} />
    }
    return <Spin style={{ display: 'block', marginTop: 80 }} />
  }

  if (!data.round) {
    return (
      <Alert
        type="info"
        showIcon
        message="No round is open for ordering"
        description="Ordering opens on Friday morning. Please check back later."
      />
    )
  }

  const productColumns = [
    { title: 'Product', dataIndex: 'name' },
    { title: 'Sold', dataIndex: 'unitType', render: v => unitTypeLabels[v] || v },
    { title: 'Price (AUD)', dataIndex: 'price', render: v => Number(v).toFixed(2) },
    { title: 'Bay', dataIndex: 'bay' },
    {
      title: 'Quantity', key: 'quantity', width: 140,
      render: (_, record) => (
        <InputNumber
          min={0}
          step={record.unitType === 'PER_UNIT' ? 1 : 0.25}
          precision={record.unitType === 'PER_UNIT' ? 0 : 3}
          value={quantities[record.id]}
          onChange={value => setQuantities(prev => ({ ...prev, [record.id]: value }))}
        />
      )
    },
    {
      title: 'Line total', key: 'lineTotal', width: 110,
      render: (_, record) => quantities[record.id] > 0
        ? (lineTotalCents(quantities[record.id], Number(record.price)) / 100).toFixed(2)
        : '—'
    }
  ]

  const basketColumns = [
    { title: 'Product', dataIndex: 'name' },
    { title: 'Qty', dataIndex: 'quantity' },
    { title: 'Total', dataIndex: 'lineTotal', render: v => v.toFixed(2) }
  ]

  return (
    <Row gutter={16}>
      <Col span={16}>
        <Card
          title={`Round ${data?.round?.roundNo} — available products`}
          extra={<Tag color="green">OPEN</Tag>}
        >
          <Typography.Paragraph type="secondary">
            Orders close {data?.round?.ordersCloseAt?.replace('T', ' ')} · Pickup {data?.round?.pickupDate}
          </Typography.Paragraph>
          <Table rowKey="id" loading={loading} columns={productColumns}
                 dataSource={data?.products || []} pagination={false} />
        </Card>
      </Col>
      <Col span={8}>
        <Card
          title="Your order"
          extra={<Button type="primary" loading={saving} onClick={save}>Save order</Button>}
        >
          <Table rowKey="productId" columns={basketColumns} dataSource={lines}
                 pagination={false} size="small" locale={{ emptyText: 'Add products on the left' }} />
          <Typography.Title level={5} style={{ textAlign: 'right', marginTop: 16 }}>
            Total: ${total.toFixed(2)}
          </Typography.Title>
        </Card>
      </Col>
    </Row>
  )
}
```

- [ ] **Step 12: 构建并提交前端**

```bash
cd frontend && npm run build && cd ..
git add -A && git commit -m "feat(story-07): shop page order basket with quantity inputs and save"
```

---

## Task 8: 改单/取消/关闭后只读(`story/08-manage-order`)

**Files:**
- Modify: `src/main/java/com/greenhill/coop/service/OrderService.java`(加 `cancelMyOrder`)
- Modify: `src/main/java/com/greenhill/coop/controller/OrderController.java`(加 `DELETE /mine`)
- Modify: `src/test/java/com/greenhill/coop/OrderApiTest.java`(加故事 8 测试)
- Create: `frontend/src/pages/MyOrderPage.jsx`
- Modify: `frontend/src/pages/ShopPage.jsx`(加取消按钮)、`frontend/src/App.jsx`

- [ ] **Step 1: 建分支**

```bash
git checkout -b story/08-manage-order story/07-place-order
```

- [ ] **Step 2: 在 `OrderApiTest.java` 追加失败测试**

在类中追加(需要 import `delete`):

```java
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
```

```java
    @Test
    void memberCanCancelOrderWhileRoundIsOpen() throws Exception {
        mockMvc.perform(put("/api/orders/mine")
            .header("Authorization", "Bearer " + memberToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(linesJson()));

        mockMvc.perform(delete("/api/orders/mine").header("Authorization", "Bearer " + memberToken))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/orders/mine").header("Authorization", "Bearer " + memberToken))
            .andExpect(jsonPath("$.data[0].status").value("CANCELLED"))
            .andExpect(jsonPath("$.data[0].lines.length()").value(2))
            .andExpect(jsonPath("$.data[0].total").value(20.10));
    }

    @Test
    void memberCanPlaceANewOrderAfterCancelling() throws Exception {
        mockMvc.perform(put("/api/orders/mine")
            .header("Authorization", "Bearer " + memberToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(linesJson()));
        mockMvc.perform(delete("/api/orders/mine").header("Authorization", "Bearer " + memberToken));

        mockMvc.perform(put("/api/orders/mine")
                .header("Authorization", "Bearer " + memberToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"lines\":[{\"productId\":%d,\"quantity\":2}]}".formatted(eggsId)))
            .andExpect(jsonPath("$.data.status").value("ACTIVE"))
            .andExpect(jsonPath("$.data.total").value(15.00));
    }

    @Test
    void cancelWithoutOrderIsRejected() throws Exception {
        mockMvc.perform(delete("/api/orders/mine").header("Authorization", "Bearer " + memberToken))
            .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void closedRoundBlocksChangesButNotReading() throws Exception {
        mockMvc.perform(put("/api/orders/mine")
            .header("Authorization", "Bearer " + memberToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(linesJson()));

        com.greenhill.coop.entity.Round round = roundMapper.selectById(roundId);
        round.setStatus(RoundStatus.CLOSED);
        roundMapper.updateById(round);

        mockMvc.perform(put("/api/orders/mine")
                .header("Authorization", "Bearer " + memberToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(linesJson()))
            .andExpect(jsonPath("$.code").value(409));

        mockMvc.perform(delete("/api/orders/mine").header("Authorization", "Bearer " + memberToken))
            .andExpect(jsonPath("$.code").value(409));

        mockMvc.perform(get("/api/orders/mine").header("Authorization", "Bearer " + memberToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].total").value(20.10));
    }

    @Test
    void changingOrderDoesNotAffectOtherMembers() throws Exception {
        mockMvc.perform(put("/api/orders/mine")
            .header("Authorization", "Bearer " + memberToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(linesJson()));

        String otherToken = tokenFor("M-041", "coop1234");
        mockMvc.perform(put("/api/orders/mine")
            .header("Authorization", "Bearer " + otherToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"lines\":[{\"productId\":%d,\"quantity\":1}]}".formatted(eggsId)));

        mockMvc.perform(delete("/api/orders/mine").header("Authorization", "Bearer " + memberToken));

        mockMvc.perform(get("/api/orders/mine").header("Authorization", "Bearer " + otherToken))
            .andExpect(jsonPath("$.data[0].status").value("ACTIVE"))
            .andExpect(jsonPath("$.data[0].total").value(7.50));
    }

    @Test
    void cancellingTwiceReturnsNotFound() throws Exception {
        mockMvc.perform(put("/api/orders/mine")
            .header("Authorization", "Bearer " + memberToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(linesJson()));

        mockMvc.perform(delete("/api/orders/mine").header("Authorization", "Bearer " + memberToken))
            .andExpect(status().isOk());

        mockMvc.perform(delete("/api/orders/mine").header("Authorization", "Bearer " + memberToken))
            .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void cancelOnlyAffectsTheCurrentRound() throws Exception {
        mockMvc.perform(put("/api/orders/mine")
            .header("Authorization", "Bearer " + memberToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(linesJson()));

        com.greenhill.coop.entity.Round first = roundMapper.selectById(roundId);
        first.setStatus(RoundStatus.CLOSED);
        roundMapper.updateById(first);
        createRound(35, RoundStatus.OPEN);

        mockMvc.perform(put("/api/orders/mine")
            .header("Authorization", "Bearer " + memberToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(linesJson()));

        mockMvc.perform(delete("/api/orders/mine").header("Authorization", "Bearer " + memberToken))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/orders/mine").header("Authorization", "Bearer " + memberToken))
            .andExpect(jsonPath("$.data.length()").value(2))
            .andExpect(jsonPath("$.data[0].roundNo").value(35))
            .andExpect(jsonPath("$.data[0].status").value("CANCELLED"))
            .andExpect(jsonPath("$.data[1].roundNo").value(34))
            .andExpect(jsonPath("$.data[1].status").value("ACTIVE"));
    }
```

- [ ] **Step 3: 运行确认失败**

```bash
./mvnw test -Dtest=OrderApiTest
```

Expected: 新增的 7 个测试失败(DELETE 接口不存在)。

- [ ] **Step 4: 在 `OrderService` 中加取消逻辑**

```java
    @Transactional
    public void cancelMyOrder(Long memberId) {
        Round round = roundService.currentOpen();
        if (round == null) {
            throw BizException.conflict("No round is open for ordering");
        }
        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
            .eq(Order::getMemberId, memberId)
            .eq(Order::getRoundId, round.getId()));
        if (order == null || order.getStatus() != OrderStatus.ACTIVE) {
            throw BizException.notFound("No active order for the current round");
        }
        order.setStatus(OrderStatus.CANCELLED);
        orderMapper.updateById(order);
    }
```

- [ ] **Step 5: 在 `OrderController` 中加 `DELETE /mine`**

```java
import org.springframework.web.bind.annotation.DeleteMapping;

    @DeleteMapping("/mine")
    public Result<Void> cancelMine(@CurrentUser MemberContext context) {
        orderService.cancelMyOrder(context.id());
        return Result.success(null);
    }
```

- [ ] **Step 6: 运行测试(应通过)**

```bash
./mvnw test -Dtest=OrderApiTest,PricingServiceTest
```

Expected: `OrderApiTest` 17 个 + `PricingServiceTest` 12 个全部通过。

- [ ] **Step 7: 提交后端**

```bash
git add -A && git commit -m "feat(story-08): cancel order and read-only behaviour for closed rounds"
```

- [ ] **Step 8: 写 `MyOrderPage.jsx`,ShopPage 加取消按钮,接入路由并构建**

`frontend/src/pages/MyOrderPage.jsx`:

```jsx
import { useEffect, useState } from 'react'
import { App, Card, Table, Tag, Typography } from 'antd'
import { myOrders } from '../api/order'

const unitTypeLabels = { PER_UNIT: 'each', PER_KG: 'per kg' }

export default function MyOrderPage() {
  const { message } = App.useApp()
  const [orders, setOrders] = useState([])
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    setLoading(true)
    myOrders()
      .then(setOrders)
      .catch(error => message.error(error.message))
      .finally(() => setLoading(false))
  }, [])

  const columns = [
    { title: 'Round', dataIndex: 'roundNo' },
    {
      title: 'Status', dataIndex: 'status',
      render: value => <Tag color={value === 'ACTIVE' ? 'green' : 'red'}>{value}</Tag>
    },
    { title: 'Total (AUD)', dataIndex: 'total', render: v => Number(v).toFixed(2) },
    { title: 'Placed', dataIndex: 'createdAt', render: v => v?.replace('T', ' ') }
  ]

  const lineColumns = [
    { title: 'Product', dataIndex: 'productName' },
    { title: 'Sold', dataIndex: 'unitType', render: v => unitTypeLabels[v] || v },
    { title: 'Quantity', dataIndex: 'quantity', render: v => Number(v) },
    { title: 'Unit price', dataIndex: 'unitPrice', render: v => Number(v).toFixed(2) },
    { title: 'Line total', dataIndex: 'lineTotal', render: v => Number(v).toFixed(2) }
  ]

  return (
    <Card title="My orders">
      <Typography.Paragraph type="secondary">
        Orders can only be changed while the round is open for ordering.
      </Typography.Paragraph>
      <Table
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={orders}
        pagination={false}
        expandable={{
          expandedRowRender: order => (
            <Table rowKey="id" columns={lineColumns} dataSource={order.lines}
                   pagination={false} size="small" />
          )
        }}
      />
    </Card>
  )
}
```

在 `ShopPage.jsx` 的 "Your order" Card 中,把 `extra` 改为:

```jsx
          extra={(
            <Space>
              <Button danger disabled={!hasActiveOrder} onClick={cancel}>Cancel order</Button>
              <Button type="primary" loading={saving} onClick={save}>Save order</Button>
            </Space>
          )}
```

并在组件内加入 `hasActiveOrder` 状态、取消函数(需要 `App.useApp()` 的 `modal` 与 `cancelOrder`):

```jsx
  const [hasActiveOrder, setHasActiveOrder] = useState(false)

  // load() 中算出 current 之后:
  setHasActiveOrder(Boolean(current))

  function cancel() {
    modal.confirm({
      title: 'Cancel your order for this round?',
      okText: 'Cancel order',
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await cancelOrder()
          message.success('Order cancelled')
          setQuantities({})
          load()
        } catch (error) {
          message.error(error.message)
        }
      }
    })
  }
```

同时完成两处 import/初始化改动:
1. 把 `ShopPage.jsx` 顶部 antd import 中的 `message` 换成 `App`(即 `... Typography, App } from 'antd'`),并在组件函数第一行加 `const { message, modal } = App.useApp()`;
2. 从 `../api/order` 的 import 中加上 `cancelOrder`(Task 7 只导入了 `myOrders, placeOrder`)。

修改 `frontend/src/App.jsx` 接入 `<MyOrderPage />`,然后:

```bash
cd frontend && npm run build && cd ..
```

- [ ] **Step 9: 提交前端**

```bash
git add -A && git commit -m "feat(story-08): my orders page and cancel-order action"
```

---

## Task 9: 协调员查看轮次全部订单(`story/09-round-orders`)

**Files:**
- Modify: `src/main/java/com/greenhill/coop/controller/OrderController.java`(加 `GET /api/orders?roundId=`)
- Create: `src/test/java/com/greenhill/coop/RoundOrdersApiTest.java`
- Create: `frontend/src/pages/admin/OrdersPage.jsx`
- Modify: `frontend/src/App.jsx`

- [ ] **Step 1: 建分支**

```bash
git checkout -b story/09-round-orders story/08-manage-order
```

- [ ] **Step 2: 写失败测试 `RoundOrdersApiTest.java`**

```java
package com.greenhill.coop;

import com.greenhill.coop.common.enums.MemberRole;
import com.greenhill.coop.common.enums.RoundStatus;
import com.greenhill.coop.common.enums.UnitType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RoundOrdersApiTest extends ApiTestBase {

    private String coordinatorToken;
    private Long roundId;
    private Long oatsId;

    @BeforeEach
    void setUp() throws Exception {
        createMember("M-001", "Ngaire Fletcher", MemberRole.COORDINATOR, "coop1234");
        createMember("M-094", "Ky Tran", MemberRole.MEMBER, "coop1234");
        createMember("M-041", "Doug Halvorsen", MemberRole.MEMBER, "coop1234");
        coordinatorToken = tokenFor("M-001", "coop1234");
        roundId = createRound(34, RoundStatus.OPEN).getId();
        oatsId = createProduct("Rolled oats, organic", UnitType.PER_KG, "3.40", "B1").getId();
    }

    private void place(String memberNo, String quantity) throws Exception {
        mockMvc.perform(put("/api/orders/mine")
            .header("Authorization", "Bearer " + tokenFor(memberNo, "coop1234"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"lines\":[{\"productId\":%d,\"quantity\":%s}]}".formatted(oatsId, quantity)))
            .andExpect(status().isOk());
    }

    @Test
    void coordinatorSeesAllOrdersWithMemberAndLines() throws Exception {
        place("M-094", "1.5");
        place("M-041", "2");

        mockMvc.perform(get("/api/orders?roundId=" + roundId)
                .header("Authorization", "Bearer " + coordinatorToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(2))
            .andExpect(jsonPath("$.data[0].memberNo").value("M-094"))
            .andExpect(jsonPath("$.data[0].lines.length()").value(1))
            .andExpect(jsonPath("$.data[0].total").value(5.10))
            .andExpect(jsonPath("$.data[1].total").value(6.80));
    }

    @Test
    void emptyRoundReturnsEmptyArray() throws Exception {
        mockMvc.perform(get("/api/orders?roundId=" + roundId)
                .header("Authorization", "Bearer " + coordinatorToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isArray())
            .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void closedRoundOrdersAreStillVisible() throws Exception {
        place("M-094", "1.5");
        com.greenhill.coop.entity.Round round = roundMapper.selectById(roundId);
        round.setStatus(RoundStatus.CLOSED);
        roundMapper.updateById(round);

        mockMvc.perform(get("/api/orders?roundId=" + roundId)
                .header("Authorization", "Bearer " + coordinatorToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void ordersAreScopedToTheRequestedRound() throws Exception {
        place("M-094", "1.5");
        createRound(35, RoundStatus.OPEN);
        mockMvc.perform(put("/api/orders/mine")
                .header("Authorization", "Bearer " + tokenFor("M-094", "coop1234"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"lines\":[{\"productId\":%d,\"quantity\":2}]}".formatted(oatsId)))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/orders?roundId=" + roundId)
                .header("Authorization", "Bearer " + coordinatorToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].roundId").value(roundId));
    }

    @Test
    void unknownRoundReturnsEmptyArray() throws Exception {
        mockMvc.perform(get("/api/orders?roundId=99999")
                .header("Authorization", "Bearer " + coordinatorToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void missingRoundIdIsRejected() throws Exception {
        mockMvc.perform(get("/api/orders")
                .header("Authorization", "Bearer " + coordinatorToken))
            .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void memberCannotSeeRoundOrders() throws Exception {
        mockMvc.perform(get("/api/orders?roundId=" + roundId)
                .header("Authorization", "Bearer " + tokenFor("M-094", "coop1234")))
            .andExpect(status().isForbidden());
    }
}
```

- [ ] **Step 3: 运行确认失败**

```bash
./mvnw test -Dtest=RoundOrdersApiTest
```

Expected: 403/404(接口不存在或未授权)。

- [ ] **Step 4: 在 `OrderController` 加协调员查询**

```java
    @org.springframework.web.bind.annotation.GetMapping
    @com.greenhill.coop.auth.RequireCoordinator
    public Result<List<OrderView>> roundOrders(@org.springframework.web.bind.annotation.RequestParam Long roundId) {
        return Result.success(orderService.roundOrders(roundId));
    }
```

- [ ] **Step 5: 运行测试(应通过)**

```bash
./mvnw test -Dtest=RoundOrdersApiTest
```

Expected: 7 个测试通过。

- [ ] **Step 6: 提交后端**

```bash
git add -A && git commit -m "feat(story-09): coordinator round orders endpoint"
```

- [ ] **Step 7: 写 `frontend/src/pages/admin/OrdersPage.jsx`,接入路由并构建**

```jsx
import { useEffect, useState } from 'react'
import { App, Button, Card, Select, Space, Table, Tag, Typography } from 'antd'
import { ReloadOutlined } from '@ant-design/icons'
import { listRounds } from '../../api/round'
import { ordersForRound } from '../../api/order'

const unitTypeLabels = { PER_UNIT: 'each', PER_KG: 'per kg' }

export default function OrdersPage() {
  const { message } = App.useApp()
  const [rounds, setRounds] = useState([])
  const [roundId, setRoundId] = useState(null)
  const [orders, setOrders] = useState([])
  const [loading, setLoading] = useState(false)
  const [reloadKey, setReloadKey] = useState(0)

  useEffect(() => {
    listRounds({ page: 1, size: 50 })
      .then(data => {
        const records = data?.records || []
        setRounds(records)
        if (records.length > 0) setRoundId(records[0].id)
      })
      .catch(error => message.error(error.message))
  }, [])

  useEffect(() => {
    if (!roundId) return
    let ignore = false
    setLoading(true)
    setOrders([])
    ordersForRound(roundId)
      .then(data => { if (!ignore) setOrders(data) })
      .catch(error => { if (!ignore) message.error(error.message) })
      .finally(() => { if (!ignore) setLoading(false) })
    return () => { ignore = true }
  }, [roundId, reloadKey])

  const orderColumns = [
    { title: 'Member', key: 'member', render: (_, r) => `${r.memberNo} — ${r.memberName}` },
    { title: 'Status', dataIndex: 'status', render: v => <Tag color="green">{v}</Tag> },
    { title: 'Lines', key: 'lines', render: (_, r) => r.lines.length },
    { title: 'Total (AUD)', dataIndex: 'total', render: v => Number(v).toFixed(2) }
  ]

  const lineColumns = [
    { title: 'Product', dataIndex: 'productName' },
    { title: 'Sold', dataIndex: 'unitType', render: v => unitTypeLabels[v] || v },
    { title: 'Quantity', dataIndex: 'quantity', render: v => Number(v) },
    { title: 'Unit price', dataIndex: 'unitPrice', render: v => Number(v).toFixed(2) },
    { title: 'Line total', dataIndex: 'lineTotal', render: v => Number(v).toFixed(2) }
  ]

  const selectedRound = rounds.find(r => r.id === roundId)

  return (
    <Card
      title="Round orders"
      extra={(
        <Space>
          <Typography.Text type="secondary">
            {selectedRound ? `${selectedRound.status} · pickup ${selectedRound.pickupDate}` : ''}
          </Typography.Text>
          <Select
            style={{ minWidth: 200 }}
            placeholder="Select a round"
            value={roundId}
            onChange={setRoundId}
            options={rounds.map(r => ({ value: r.id, label: `Round ${r.roundNo} (${r.status})` }))}
          />
          <Button icon={<ReloadOutlined />} onClick={() => setReloadKey(k => k + 1)} />
        </Space>
      )}
    >
      <Table
        rowKey="id"
        loading={loading}
        columns={orderColumns}
        dataSource={orders}
        pagination={false}
        locale={{ emptyText: 'No orders in this round' }}
        expandable={{
          expandedRowRender: order => (
            <Table rowKey="id" columns={lineColumns} dataSource={order.lines}
                   pagination={false} size="small" />
          )
        }}
      />
    </Card>
  )
}
```

修改 `frontend/src/App.jsx` 接入 `<OrdersPage />`,然后:

```bash
cd frontend && npm run build && cd ..
```

- [ ] **Step 8: 提交前端**

```bash
git add -A && git commit -m "feat(story-09): round orders admin page"
```

---

## Task 10: 轮次按商品汇总(`story/10-round-totals`)

**Files:**
- Create: `src/main/java/com/greenhill/coop/dto/RoundTotalsView.java`
- Modify: `src/main/java/com/greenhill/coop/service/RoundService.java`(加 `roundTotals`)
- Modify: `src/main/java/com/greenhill/coop/controller/RoundController.java`(加 `GET /{id}/totals`)
- Create: `src/test/java/com/greenhill/coop/RoundTotalsApiTest.java`
- Create: `frontend/src/pages/admin/TotalsPage.jsx`
- Modify: `frontend/src/App.jsx`

- [ ] **Step 1: 建分支**

```bash
git checkout -b story/10-round-totals story/09-round-orders
```

- [ ] **Step 2: 写失败测试 `RoundTotalsApiTest.java`**

```java
package com.greenhill.coop;

import com.greenhill.coop.common.enums.MemberRole;
import com.greenhill.coop.common.enums.RoundStatus;
import com.greenhill.coop.common.enums.UnitType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RoundTotalsApiTest extends ApiTestBase {

    private String coordinatorToken;
    private Long roundId;
    private Long oatsId;
    private Long eggsId;

    @BeforeEach
    void setUp() throws Exception {
        createMember("M-001", "Ngaire Fletcher", MemberRole.COORDINATOR, "coop1234");
        createMember("M-094", "Ky Tran", MemberRole.MEMBER, "coop1234");
        createMember("M-041", "Doug Halvorsen", MemberRole.MEMBER, "coop1234");
        coordinatorToken = tokenFor("M-001", "coop1234");
        roundId = createRound(34, RoundStatus.OPEN).getId();
        oatsId = createProduct("Rolled oats, organic", UnitType.PER_KG, "3.40", "B1").getId();
        eggsId = createProduct("Eggs, free range, dozen", UnitType.PER_UNIT, "7.50", "COOL").getId();
    }

    private void order(String memberNo, String oats, String eggs) throws Exception {
        mockMvc.perform(put("/api/orders/mine")
            .header("Authorization", "Bearer " + tokenFor(memberNo, "coop1234"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"lines":[{"productId":%d,"quantity":%s},{"productId":%d,"quantity":%s}]}
                """.formatted(oatsId, oats, eggsId, eggs)));
    }

    @Test
    void totalsAggregateQuantityAndAmountPerProduct() throws Exception {
        order("M-094", "1.5", "2");
        order("M-041", "2", "1");

        mockMvc.perform(get("/api/rounds/" + roundId + "/totals")
                .header("Authorization", "Bearer " + coordinatorToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.rows.length()").value(2))
            .andExpect(jsonPath("$.data.totalAmount").value(34.40))
            .andExpect(jsonPath("$.data.rows[0].productName").value("Eggs, free range, dozen"))
            .andExpect(jsonPath("$.data.rows[0].totalQuantity").value(3))
            .andExpect(jsonPath("$.data.rows[0].totalAmount").value(22.50))
            .andExpect(jsonPath("$.data.rows[1].totalQuantity").value(3.5))
            .andExpect(jsonPath("$.data.rows[1].totalAmount").value(11.90));
    }

    @Test
    void cancelledOrdersAreExcludedFromTotals() throws Exception {
        order("M-094", "1.5", "2");
        order("M-041", "2", "1");

        mockMvc.perform(delete("/api/orders/mine")
            .header("Authorization", "Bearer " + tokenFor("M-041", "coop1234")));

        mockMvc.perform(get("/api/rounds/" + roundId + "/totals")
                .header("Authorization", "Bearer " + coordinatorToken))
            .andExpect(jsonPath("$.data.totalAmount").value(20.10))
            .andExpect(jsonPath("$.data.rows[1].totalQuantity").value(1.5));
    }

    @Test
    void emptyRoundHasZeroTotals() throws Exception {
        mockMvc.perform(get("/api/rounds/" + roundId + "/totals")
                .header("Authorization", "Bearer " + coordinatorToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.rows").isEmpty())
            .andExpect(jsonPath("$.data.totalAmount").value(0.0));
    }

    @Test
    void unknownRoundReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/rounds/99999/totals")
                .header("Authorization", "Bearer " + coordinatorToken))
            .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void memberCannotSeeTotals() throws Exception {
        mockMvc.perform(get("/api/rounds/" + roundId + "/totals")
                .header("Authorization", "Bearer " + tokenFor("M-094", "coop1234")))
            .andExpect(status().isForbidden());
    }
}
```

- [ ] **Step 3: 运行确认失败**

```bash
./mvnw test -Dtest=RoundTotalsApiTest
```

Expected: 404/403(接口不存在)。

- [ ] **Step 4: 写 `RoundTotalsView.java`**

```java
package com.greenhill.coop.dto;

import java.math.BigDecimal;
import java.util.List;

public record RoundTotalsView(Long roundId, Integer roundNo, List<RoundTotalRow> rows, BigDecimal totalAmount) {
}
```

- [ ] **Step 5: 在 `RoundService` 加汇总方法**

给 `RoundService` 增加字段与 import:

```java
import com.greenhill.coop.dto.RoundTotalRow;
import com.greenhill.coop.dto.RoundTotalsView;
import com.greenhill.coop.mapper.OrderLineMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
// ...
    private final OrderLineMapper orderLineMapper;
```

```java
    public RoundTotalsView roundTotals(Long roundId) {
        Round round = find(roundId);
        List<RoundTotalRow> rows = orderLineMapper.selectRoundTotals(roundId);
        BigDecimal total = rows.stream()
            .map(RoundTotalRow::getTotalAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
        return new RoundTotalsView(round.getId(), round.getRoundNo(), rows, total);
    }
```

- [ ] **Step 6: 在 `RoundController` 加接口**

```java
    @GetMapping("/{id}/totals")
    @RequireCoordinator
    public Result<com.greenhill.coop.dto.RoundTotalsView> totals(@PathVariable Long id) {
        return Result.success(roundService.roundTotals(id));
    }
```

- [ ] **Step 7: 运行测试(应通过)**

```bash
./mvnw test -Dtest=RoundTotalsApiTest
```

Expected: 5 个测试通过。

- [ ] **Step 8: 提交后端**

```bash
git add -A && git commit -m "feat(story-10): round totals by product with quantity and amount aggregation"
```

- [ ] **Step 9: 写 `frontend/src/pages/admin/TotalsPage.jsx`,接入路由并构建**

```jsx
import { useEffect, useState } from 'react'
import { Card, Select, Space, Table, Typography, message } from 'antd'
import { listRounds, roundTotals } from '../../api/round'

const unitTypeLabels = { PER_UNIT: 'each', PER_KG: 'per kg' }

export default function TotalsPage() {
  const [rounds, setRounds] = useState([])
  const [roundId, setRoundId] = useState(null)
  const [totals, setTotals] = useState(null)
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    listRounds({ page: 1, size: 50 })
      .then(data => {
        const records = data?.records || []
        setRounds(records)
        if (records.length > 0) setRoundId(records[0].id)
      })
      .catch(error => message.error(error.message))
  }, [])

  useEffect(() => {
    if (!roundId) return
    setLoading(true)
    roundTotals(roundId)
      .then(setTotals)
      .catch(error => message.error(error.message))
      .finally(() => setLoading(false))
  }, [roundId])

  const columns = [
    { title: 'Product', dataIndex: 'productName' },
    { title: 'Sold', dataIndex: 'unitType', render: v => unitTypeLabels[v] || v },
    { title: 'Total quantity', dataIndex: 'totalQuantity', render: v => Number(v) },
    { title: 'Total amount (AUD)', dataIndex: 'totalAmount', render: v => Number(v).toFixed(2) }
  ]

  return (
    <Card
      title="Round totals by product"
      extra={(
        <Space>
          <Typography.Text type="secondary">Use this to place the wholesale order</Typography.Text>
          <Select
            style={{ minWidth: 200 }}
            value={roundId}
            onChange={setRoundId}
            options={rounds.map(r => ({ value: r.id, label: `Round ${r.roundNo} (${r.status})` }))}
          />
        </Space>
      )}
    >
      <Table
        rowKey="productId"
        loading={loading}
        columns={columns}
        dataSource={totals?.rows || []}
        pagination={false}
        locale={{ emptyText: 'No orders in this round' }}
        summary={() => (
          <Table.Summary.Row>
            <Table.Summary.Cell index={0}><strong>Total</strong></Table.Summary.Cell>
            <Table.Summary.Cell index={1} />
            <Table.Summary.Cell index={2} />
            <Table.Summary.Cell index={3}>
              <strong>{Number(totals?.totalAmount || 0).toFixed(2)}</strong>
            </Table.Summary.Cell>
          </Table.Summary.Row>
        )}
      />
    </Card>
  )
}
```

修改 `frontend/src/App.jsx` 接入 `<TotalsPage />`,然后:

```bash
cd frontend && npm run build && cd ..
```

- [ ] **Step 10: 提交前端**

```bash
git add -A && git commit -m "feat(story-10): round totals admin page"
```

---

## Task 11: 协调员代会员下单(`story/11-order-for-member`)

**Files:**
- Modify: `src/main/java/com/greenhill/coop/controller/OrderController.java`(加 `POST /api/orders/for-member`)
- Create: `src/test/java/com/greenhill/coop/OrderForMemberApiTest.java`
- Modify: `frontend/src/pages/admin/OrdersPage.jsx`(加代下单入口)

- [ ] **Step 1: 建分支**

```bash
git checkout -b story/11-order-for-member story/10-round-totals
```

- [ ] **Step 2: 写失败测试 `OrderForMemberApiTest.java`**

```java
package com.greenhill.coop;

import com.greenhill.coop.common.enums.MemberRole;
import com.greenhill.coop.common.enums.MemberStatus;
import com.greenhill.coop.common.enums.RoundStatus;
import com.greenhill.coop.common.enums.UnitType;
import com.greenhill.coop.entity.Member;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderForMemberApiTest extends ApiTestBase {

    private String coordinatorToken;
    private Long janId;
    private Long eggsId;
    private Long roundId;

    @BeforeEach
    void setUp() throws Exception {
        createMember("M-001", "Ngaire Fletcher", MemberRole.COORDINATOR, "coop1234");
        janId = createMember("M-063", "Jan Buckley", MemberRole.MEMBER, "coop1234").getId();
        coordinatorToken = tokenFor("M-001", "coop1234");
        roundId = createRound(34, RoundStatus.OPEN).getId();
        eggsId = createProduct("Eggs, free range, dozen", UnitType.PER_UNIT, "7.50", "COOL").getId();
    }

    @Test
    void coordinatorPlacesOrderOnBehalfOfMember() throws Exception {
        mockMvc.perform(post("/api/orders/for-member")
                .header("Authorization", "Bearer " + coordinatorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberId\":%d,\"lines\":[{\"productId\":%d,\"quantity\":2}]}".formatted(janId, eggsId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.memberNo").value("M-063"))
            .andExpect(jsonPath("$.data.total").value(15.00));

        mockMvc.perform(get("/api/orders/mine")
                .header("Authorization", "Bearer " + tokenFor("M-063", "coop1234")))
            .andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].total").value(15.00));
    }

    @Test
    void coordinatorCanUpdateMemberOrderOnBehalf() throws Exception {
        mockMvc.perform(post("/api/orders/for-member")
            .header("Authorization", "Bearer " + coordinatorToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"memberId\":%d,\"lines\":[{\"productId\":%d,\"quantity\":2}]}".formatted(janId, eggsId)));

        mockMvc.perform(post("/api/orders/for-member")
                .header("Authorization", "Bearer " + coordinatorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberId\":%d,\"lines\":[{\"productId\":%d,\"quantity\":3}]}".formatted(janId, eggsId)))
            .andExpect(jsonPath("$.data.total").value(22.50))
            .andExpect(jsonPath("$.data.lines.length()").value(1));
    }

    @Test
    void unknownMemberReturnsNotFound() throws Exception {
        mockMvc.perform(post("/api/orders/for-member")
                .header("Authorization", "Bearer " + coordinatorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberId\":99999,\"lines\":[{\"productId\":%d,\"quantity\":1}]}".formatted(eggsId)))
            .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void inactiveMemberCannotReceiveOrder() throws Exception {
        Member jan = memberMapper.selectById(janId);
        jan.setStatus(MemberStatus.INACTIVE);
        memberMapper.updateById(jan);

        mockMvc.perform(post("/api/orders/for-member")
                .header("Authorization", "Bearer " + coordinatorToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberId\":%d,\"lines\":[{\"productId\":%d,\"quantity\":1}]}".formatted(janId, eggsId)))
            .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void memberCannotPlaceOrderForSomeoneElse() throws Exception {
        mockMvc.perform(post("/api/orders/for-member")
                .header("Authorization", "Bearer " + tokenFor("M-063", "coop1234"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberId\":%d,\"lines\":[{\"productId\":%d,\"quantity\":1}]}".formatted(janId, eggsId)))
            .andExpect(status().isForbidden());
    }
}
```

- [ ] **Step 3: 运行确认失败**

```bash
./mvnw test -Dtest=OrderForMemberApiTest
```

Expected: 403/404(接口不存在)。

- [ ] **Step 4: 写 `dto/OrderForMemberRequest.java` 并在 `OrderController` 加接口**

```java
package com.greenhill.coop.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record OrderForMemberRequest(@NotNull Long memberId, @NotEmpty List<@Valid OrderLineRequest> lines) {
}
```

```java
    @org.springframework.web.bind.annotation.PostMapping("/for-member")
    @com.greenhill.coop.auth.RequireCoordinator
    public Result<OrderView> placeForMember(
        @jakarta.validation.Valid @RequestBody com.greenhill.coop.dto.OrderForMemberRequest request) {
        return Result.success(orderService.placeForMember(request.memberId(),
            new PlaceOrderRequest(request.lines())));
    }
```

- [ ] **Step 5: 运行测试(应通过)**

```bash
./mvnw test -Dtest=OrderForMemberApiTest
```

Expected: 5 个测试通过。

- [ ] **Step 6: 提交后端**

```bash
git add -A && git commit -m "feat(story-11): coordinator can place orders on behalf of members"
```

- [ ] **Step 7: 在 `OrdersPage.jsx` 加代下单入口并构建**

在 `OrdersPage.jsx` 增加(需要 import `Button`、`Modal`、`Form`、`InputNumber`、`Select`、`App` 与 `listMembers`、`placeOrderForMember`):

```jsx
  const [ordering, setOrdering] = useState(false)
  const [members, setMembers] = useState([])
  const [form] = Form.useForm()
  const [products, setProducts] = useState([])

  async function openOrderForMember() {
    const [memberData, productData] = await Promise.all([
      listMembers({ page: 1, size: 500, status: 'ACTIVE' }),
      availableProducts()
    ])
    setMembers(memberData?.records || [])
    setProducts(productData?.products || [])
    setOrdering(true)
  }

  async function submitOrderForMember() {
    const values = await form.validateFields()
    const lines = (values.lines || [])
      .filter(line => line && line.productId && line.quantity > 0)
      .map(line => ({ productId: line.productId, quantity: line.quantity }))
    await placeOrderForMember({ memberId: values.memberId, lines })
    message.success('Order saved for member')
    setOrdering(false)
    form.resetFields()
    if (roundId) ordersForRound(roundId).then(setOrders)
  }
```

在 `Card` 的 `extra` 中加按钮:

```jsx
          <Button type="primary" onClick={openOrderForMember}>Order for member</Button>
```

在页面底部加 Modal:

```jsx
      <Modal
        title="Order for member"
        open={ordering}
        okText="Save order"
        onOk={submitOrderForMember}
        onCancel={() => setOrdering(false)}
        destroyOnHidden
        width={640}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="memberId" label="Member" rules={[{ required: true }]}>
            <Select
              showSearch
              optionFilterProp="label"
              options={members.map(m => ({ value: m.id, label: `${m.memberNo} — ${m.name}` }))}
            />
          </Form.Item>
          <Form.List name="lines">
            {fields => (
              <Space direction="vertical" style={{ width: '100%' }}>
                {fields.map(field => (
                  <Space key={field.key} align="baseline">
                    <Form.Item name={[field.name, 'productId']} rules={[{ required: true }]}>
                      <Select
                        showSearch
                        optionFilterProp="label"
                        style={{ width: 320 }}
                        placeholder="Product"
                        options={products.map(p => ({ value: p.id, label: p.name }))}
                      />
                    </Form.Item>
                    <Form.Item name={[field.name, 'quantity']} rules={[{ required: true }]}>
                      <InputNumber min={0} precision={3} placeholder="Qty" />
                    </Form.Item>
                    <Button danger onClick={() => fields.remove(field.name)}>Remove</Button>
                  </Space>
                ))}
                <Button onClick={() => fields.add({})}>Add line</Button>
              </Space>
            )}
          </Form.List>
        </Form>
      </Modal>
```

注意:`availableProducts()` 只返回当前 OPEN 轮次商品;若所选轮次不是 OPEN 轮次,应在前端禁用该按钮(在按钮上加 `disabled={selectedRound?.status !== 'OPEN'}`)。

```bash
cd frontend && npm run build && cd ..
```

- [ ] **Step 8: 提交前端**

```bash
git add -A && git commit -m "feat(story-11): order-for-member modal on round orders page"
```

---

## Task 12: 种子数据、README 与交接文档(`story/12-delivery`)

**Files:**
- Create: `src/main/java/com/greenhill/coop/config/DataSeeder.java`
- Create: `README.md`, `docs/handover.md`, `docs/jira-import.csv`
- Modify: `src/test/java/com/greenhill/coop/CoopApplicationTests.java`(增加种子可重复执行断言)

- [ ] **Step 1: 建分支**

```bash
git checkout -b story/12-delivery story/11-order-for-member
```

- [ ] **Step 2: 写 `DataSeeder.java`**

```java
package com.greenhill.coop.config;

import com.greenhill.coop.common.enums.MemberRole;
import com.greenhill.coop.common.enums.MemberStatus;
import com.greenhill.coop.common.enums.OrderStatus;
import com.greenhill.coop.common.enums.ProductStatus;
import com.greenhill.coop.common.enums.RoundStatus;
import com.greenhill.coop.common.enums.UnitType;
import com.greenhill.coop.entity.Member;
import com.greenhill.coop.entity.Order;
import com.greenhill.coop.entity.OrderLine;
import com.greenhill.coop.entity.Product;
import com.greenhill.coop.entity.Round;
import com.greenhill.coop.mapper.MemberMapper;
import com.greenhill.coop.mapper.OrderLineMapper;
import com.greenhill.coop.mapper.OrderMapper;
import com.greenhill.coop.mapper.ProductMapper;
import com.greenhill.coop.mapper.RoundMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@Profile("!test")
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private static final String DEMO_PASSWORD = "coop1234";

    private final MemberMapper memberMapper;
    private final ProductMapper productMapper;
    private final RoundMapper roundMapper;
    private final OrderMapper orderMapper;
    private final OrderLineMapper orderLineMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (memberMapper.selectCount(null) > 0) {
            return;
        }
        Map<String, Member> members = seedMembers();
        Map<String, Product> products = seedProducts();
        Round round33 = seedRound33();
        seedRound33Orders(members, products, round33);
        seedRound34();
    }

    private Map<String, Member> seedMembers() {
        Map<String, Member> members = new LinkedHashMap<>();
        members.put("M-001", member("M-001", "Ngaire Fletcher", MemberRole.COORDINATOR, "0400 000 001", "ngaire@greenhillcoop.org.au", "Moorooka"));
        members.put("M-041", member("M-041", "Doug Halvorsen", MemberRole.MEMBER, "0400 000 041", "doug@example.com", "Tarragindi"));
        members.put("M-052", member("M-052", "Bao Nguyen", MemberRole.MEMBER, "0400 000 052", "bao@example.com", "Moorooka"));
        members.put("M-063", member("M-063", "Jan Buckley", MemberRole.MEMBER, "0400 000 063", null, "Moorooka"));
        members.put("M-077", member("M-077", "Ruth Callaghan", MemberRole.MEMBER, "0400 000 077", null, "Salisbury"));
        members.put("M-094", member("M-094", "Ky Tran", MemberRole.MEMBER, "0438 601 772", "ky@example.com", "Moorooka"));
        members.put("M-102", member("M-102", "Priya Raman", MemberRole.MEMBER, "0400 000 102", "priya@example.com", "Rocklea"));
        members.put("M-118", member("M-118", "Ada Okonkwo", MemberRole.MEMBER, "0400 000 118", "ada@example.com", "Moorooka"));
        members.put("M-127", member("M-127", "Tom Whitfield", MemberRole.MEMBER, "0400 000 127", "tom@example.com", "Annerley"));
        members.put("M-152", member("M-152", "Sepideh Rahimi", MemberRole.MEMBER, "0400 000 152", "sepideh@example.com", "Moorooka"));
        Member inactive = members.get("M-077");
        inactive.setStatus(MemberStatus.INACTIVE);
        memberMapper.updateById(inactive);
        return members;
    }

    private Member member(String memberNo, String name, MemberRole role, String phone, String email, String address) {
        Member member = new Member();
        member.setMemberNo(memberNo);
        member.setName(name);
        member.setRole(role);
        member.setStatus(MemberStatus.ACTIVE);
        member.setPhone(phone);
        member.setEmail(email);
        member.setAddress(address);
        member.setPasswordHash(passwordEncoder.encode(DEMO_PASSWORD));
        memberMapper.insert(member);
        return member;
    }

    private Map<String, Product> seedProducts() {
        Map<String, Product> products = new LinkedHashMap<>();
        products.put("oats", product("Rolled oats, organic", UnitType.PER_KG, "3.40", "B1"));
        products.put("rice", product("Brown rice, medium", UnitType.PER_KG, "4.10", "B3"));
        products.put("lentils", product("Red lentils, split", UnitType.PER_KG, "4.85", "B4"));
        products.put("coffee", product("Coffee beans, whole", UnitType.PER_KG, "32.00", "C2"));
        products.put("tahini", product("Tahini, 375g jar", UnitType.PER_UNIT, "9.80", "A2"));
        products.put("eggs", product("Eggs, free range, dozen", UnitType.PER_UNIT, "7.50", "COOL"));
        products.put("pumpkin", product("Pumpkin (Two Creeks)", UnitType.PER_KG, "2.60", "VEG"));
        products.put("oliveOil", product("Olive oil, 1L tin", UnitType.PER_UNIT, "19.60", "A1"));
        products.put("peanutButter", product("Peanut butter, 500g jar", UnitType.PER_UNIT, "8.40", "A2"));
        products.put("almonds", product("Raw almonds", UnitType.PER_KG, "18.90", "C1"));
        products.put("carrots", product("Carrots (Two Creeks)", UnitType.PER_KG, "3.20", "VEG"));
        products.put("soap", product("Olive oil soap bar", UnitType.PER_UNIT, "4.20", "A4"));
        return products;
    }

    private Product product(String name, UnitType unitType, String price, String bay) {
        Product product = new Product();
        product.setName(name);
        product.setUnitType(unitType);
        product.setPrice(new BigDecimal(price));
        product.setBay(bay);
        product.setStatus(ProductStatus.ACTIVE);
        productMapper.insert(product);
        return product;
    }

    private Round seedRound33() {
        Round round = new Round();
        round.setRoundNo(33);
        round.setOrdersOpenAt(LocalDateTime.of(2026, 8, 7, 9, 0));
        round.setOrdersCloseAt(LocalDateTime.of(2026, 8, 9, 20, 0));
        round.setPickupDate(LocalDate.of(2026, 8, 13));
        round.setStatus(RoundStatus.PACKED);
        roundMapper.insert(round);
        return round;
    }

    private void seedRound33Orders(Map<String, Member> members, Map<String, Product> products, Round round) {
        seedOrder(members.get("M-094"), round, products, List.of(
            line("oats", "1.5"), line("rice", "2"), line("lentils", "1"), line("coffee", "0.25"),
            line("tahini", "1"), line("eggs", "2"), line("pumpkin", "1.5")));
        seedOrder(members.get("M-041"), round, products, List.of(
            line("rice", "3"), line("oliveOil", "1")));
        seedOrder(members.get("M-063"), round, products, List.of(
            line("oats", "2"), line("eggs", "1"), line("carrots", "1")));
        seedOrder(members.get("M-118"), round, products, List.of(
            line("almonds", "1"), line("peanutButter", "2")));
        seedOrder(members.get("M-152"), round, products, List.of(
            line("lentils", "2"), line("soap", "3")));
    }

    private void seedRound34() {
        LocalDate openDate = LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.FRIDAY));
        LocalDate closeDate = openDate.with(TemporalAdjusters.next(DayOfWeek.SUNDAY));
        Round round = new Round();
        round.setRoundNo(34);
        round.setOrdersOpenAt(LocalDateTime.of(openDate, LocalTime.of(9, 0)));
        round.setOrdersCloseAt(LocalDateTime.of(closeDate, LocalTime.of(20, 0)));
        round.setPickupDate(openDate.plusDays(6));
        round.setStatus(RoundStatus.OPEN);
        roundMapper.insert(round);
    }

    private record SeedLine(String productKey, String quantity) {
    }

    private SeedLine line(String productKey, String quantity) {
        return new SeedLine(productKey, quantity);
    }

    private void seedOrder(Member member, Round round, Map<String, Product> products, List<SeedLine> lines) {
        Order order = new Order();
        order.setMemberId(member.getId());
        order.setRoundId(round.getId());
        order.setStatus(OrderStatus.ACTIVE);
        orderMapper.insert(order);
        for (SeedLine seedLine : lines) {
            Product product = products.get(seedLine.productKey());
            BigDecimal quantity = new BigDecimal(seedLine.quantity());
            OrderLine orderLine = new OrderLine();
            orderLine.setOrderId(order.getId());
            orderLine.setProductId(product.getId());
            orderLine.setQuantity(quantity);
            orderLine.setUnitTypeSnapshot(product.getUnitType());
            orderLine.setUnitPrice(product.getPrice());
            orderLine.setLineTotal(quantity.multiply(product.getPrice()).setScale(2, RoundingMode.HALF_UP));
            orderLineMapper.insert(orderLine);
        }
    }
}
```

- [ ] **Step 3: 在 `CoopApplicationTests` 增加种子幂等断言**

在测试类中追加(测试 profile 下 seeder 不运行,断言空库即可;种子幂等由 `memberMapper.selectCount(null) > 0` 保证,手工验证在 Step 6):

```java
    @Test
    void allCoreTablesExist() {
        assertThat(memberMapper.selectCount(null)).isZero();
        assertThat(roundMapper.selectCount(null)).isZero();
        assertThat(orderMapper.selectCount(null)).isZero();
    }
```

并给测试类增加注入与 import:

```java
import com.greenhill.coop.mapper.OrderMapper;
import com.greenhill.coop.mapper.RoundMapper;
// ...
    @Autowired private OrderMapper orderMapper;
    @Autowired private RoundMapper roundMapper;
```

- [ ] **Step 4: 写 `README.md`**

```markdown
# Greenhill Food Co-op — Weekly Ordering System

ISYS3001 Managing Software Development · Case Study 2.

A small web application for a volunteer-run community food co-op's weekly grocery round.
Members place their own orders; the coordinator maintains members, products and rounds, and
sees the round totalled by product for the wholesale order.

## Prerequisites

- **Java 17** (the only requirement to run the application)
- Node 20+ / npm — only needed if you want to rebuild the React UI from source

## Quick start

```bash
./mvnw clean package
java -jar target/greenhill-coop-1.0.0.jar
```

Then open <http://localhost:8080>.

The database is an embedded H2 file at `./data/greenhill.mv.db`, created and seeded on first
start. There is nothing else to install — no MySQL, no Redis, no Docker.

## Demo accounts

Password for every demo account: `coop1234` (illustrative values only).

| Member no. | Name             | Role        |
|------------|------------------|-------------|
| M-001      | Ngaire Fletcher  | Coordinator |
| M-041      | Doug Halvorsen   | Member      |
| M-063      | Jan Buckley      | Member      |
| M-094      | Ky Tran          | Member      |

Round 33 is seeded as PACKED with the round-33 orders from the case study (Ky Tran's order
totals $54.85). Round 34 is seeded OPEN with dates calculated from the current date, so you
can place an order immediately.

## Running the tests

```bash
./mvnw test
```

## Resetting the demo data

```bash
rm -rf data/
```

The next start recreates and reseeds the database.

## Development

Backend only:

```bash
./mvnw spring-boot:run
```

Frontend dev server (hot reload, proxies `/api` to port 8080):

```bash
cd frontend
npm install
npm run dev
```

Rebuilding the UI into the JAR (the built assets are committed under
`src/main/resources/static`, so end users never need Node):

```bash
cd frontend && npm run build
# or, without a local Node install:
./mvnw clean package -Pfrontend
```

## Project layout

```
src/main/java/com/greenhill/coop/
  common/     unified Result/BizCode, exception handling, enums
  auth/       JWT, interceptor, @CurrentUser, @RequireCoordinator
  config/     MVC config, MyBatis-Plus config, data seeder, SPA forwarding
  entity/     Member, Product, Round, Order, OrderLine
  mapper/     MyBatis-Plus mappers
  dto/        request/response records
  service/    PricingService (per-unit vs per-kg), Auth/Member/Product/Round/Order services
  controller/ REST endpoints
frontend/     React + Vite + Ant Design source
docs/         design spec, handover document, Jira backlog import
```

## More documentation

- Design spec: `docs/superpowers/specs/2026-09-22-greenhill-coop-design.md`
- Handover document: `docs/handover.md`
- Backlog for Jira import: `docs/jira-import.csv`
- API docs (Swagger UI): <http://localhost:8080/swagger-ui.html>
- H2 console: <http://localhost:8080/h2-console> (JDBC URL `jdbc:h2:file:./data/greenhill`, user `sa`, empty password)
```

- [ ] **Step 5: 写 `docs/handover.md`(案例第 9 节要求的六节)**

```markdown
# Greenhill Food Co-op — Sprint Handover

## 1. What was delivered against scope

| Capability | Jira story | Notes |
|------------|-----------|-------|
| Login and role separation (member / coordinator) | GH-02 | JWT, BCrypt |
| Coordinator maintains members | GH-03 | create, search, edit, deactivate/activate, reset password |
| Coordinator maintains products | GH-04 | per-unit and per-kg products, withdraw |
| Coordinator manages rounds | GH-05 | open / close / packed; one open round at a time |
| Member sees products for the current round | GH-06 | withdrawn products hidden; clear message when no round is open |
| Member places an order | GH-07 | per-unit quantities must be whole; per-kg may be fractional; unit price snapshotted at order time |
| Member changes or cancels an order | GH-08 | only while the round is open; closed rounds are read-only |
| Coordinator sees all orders in a round | GH-09 | with member, lines and total |
| Coordinator sees the round totalled by product | GH-10 | total quantity and total amount per product |
| Coordinator places an order for a member | GH-11 | supports members who order by phone (Jan Buckley) |
| Seed data, README, clean-checkout build | GH-12 | `./mvnw clean package && java -jar ...` |

All acceptance criteria are covered by automated tests (`./mvnw test`); see the mapping in the
design spec, section 11.

## 2. What was not delivered, and why

Everything below was requested by Ngaire, Bao or Doug (or appears in the paper documents) but is
out of scope for this sprint. All items are in the product backlog (`docs/jira-import.csv`).

| Requested | Source | Why parked |
|-----------|--------|-----------|
| Online payments / card payments | Ngaire | Explicitly out of scope in the brief; needs a payment provider and PCI considerations |
| Bank reconciliation, member statements, running balances/credits | Doug | Explicitly out of scope; depends on payments and a ledger design |
| Packing actual weights and short-supply allocation | Bao | A second domain (fulfilment) with its own workflow; the sheet today records "what was ordered", not "what was packed" |
| Packing sheet ordered by crate number and bay, printed | Bao | Needs a print layout and crate allocation; deferred |
| SMS / email notifications ("your crate is ready") | Ngaire | Explicitly out of scope (no sending of email/SMS) |
| Harvest Belt portal integration / electronic price list | Ngaire | Explicitly out of scope |
| Delivery run for the two housebound members | Ngaire | Explicitly out of scope |
| Mobile app | Ngaire | Explicitly out of scope |
| Audit trail (who changed what) | Doug | Valuable but not required by the sprint stories; needs a cross-cutting log |
| Historical price lists | Doug | Order lines snapshot the unit price, which covers the immediate need |
| Accounting software export | Doug | Explicitly out of scope |
| Direct debit | Doug | Out of scope with payments |
| Membership fees, volunteer shift roster and $30 levy | Background | Outside the ordering problem |
| Compost scheme, seed library, sourdough swap, sausage sizzle | Ngaire | Not software problems |
| Automatic open/close of rounds at the published times | Brief | Rounds are controlled by the coordinator's explicit open/close actions |

## 3. Setup and run instructions from GitHub

See `README.md`. In short: install Java 17, then

```bash
./mvnw clean package
java -jar target/greenhill-coop-1.0.0.jar
```

Open <http://localhost:8080>. Tests: `./mvnw test`. No database, Node or Docker install is
required to run the application.

## 4. Known issues and limitations

1. Authentication is deliberately simple: JWT with a default development secret in
   `application.yml`; no refresh or revocation; passwords are BCrypt-hashed.
2. No audit log of who changed what.
3. No concurrency protection on simultaneous edits of the same order (last write wins).
4. Ordering windows are controlled by round status, not by the clock — a round does not close
   itself at the published time.
5. Round totals are calculated on request (no caching); fine at co-op scale.
6. The frontend has no automated tests; behaviour is covered by backend API tests.
7. H2 file database suits the co-op's scale and a course demonstration, not a multi-user
   production deployment.
8. The product catalogue is global (not per round). If the co-op later wants different products
   or prices per round, that is a new backlog item.
9. If the co-op changes how it buys (for example prices negotiated per round rather than a
   catalogue price), the stored-price-snapshot assumption would need revisiting.
10. Withdrawing a product is one-way; there is no reactivate endpoint (a mistaken withdrawal
    needs a new product record).
11. Round creation is guarded by a JVM-level lock; a multi-instance deployment would need a
    database-level constraint instead.

## 5. Credentials, configuration and environment

- Demo logins: see README table; password `coop1234` (illustrative only).
- Configuration lives in `src/main/resources/application.yml`:
  - `server.port` (default 8080)
  - `spring.datasource.url` — H2 file under `./data/`
  - `jwt.secret` / `jwt.expiration` — development defaults; a real deployment must inject these
    via environment variables.
- Sample `.env`-style overrides (illustrative):

```bash
SERVER_PORT=8080
JWT_SECRET=<base64-encoded 32-byte secret>
SPRING_DATASOURCE_URL=jdbc:h2:file:./data/greenhill;MODE=MySQL;DATABASE_TO_LOWER=TRUE
```

- H2 console at `/h2-console`; Swagger UI at `/swagger-ui.html`.

## 6. Recommended next-sprint backlog

In priority order:

1. **Packing list (Bao)** — print a sheet ordered by crate with bays, and record actual packed
   weights; this is the other half of the weekly round and the biggest remaining pain.
2. **Short-supply allocation** — split short deliveries fairly and record who received what.
3. **Member statements and balances (Doug)** — ordered, charged, paid, balance; depends on
   payments being decided.
4. **Payments and reconciliation** — the largest single request from both Ngaire and Doug.
5. **Automatic round open/close** — close the round at Sunday 20:00 without manual action.
6. **Audit trail** — who changed what, for treasurer-grade defensibility.
7. **Notifications** — SMS or email when a crate is ready.
8. **Per-round product lists and pricing** — if the co-op wants a different catalogue each round.
9. **Credit notes for short supply** — link credits to the next round's order.
10. **Accounting export** — CSV or ledger export for the annual return.
```

- [ ] **Step 6: 写 `docs/jira-import.csv`**

```csv
Issue Type,Summary,Epic,Priority,Story Points,Sprint,Description
Story,"Project skeleton, unified response and database schema",Foundation,Highest,3,Sprint 1,"Spring Boot app starts with H2; Result<T> and global exception handling; schema for members, products, rounds, orders, order lines."
Story,"Log in and separate member and coordinator roles",Foundation,Highest,5,Sprint 1,"Member number + password login returns a JWT; unauthenticated requests are rejected; coordinator-only endpoints enforce the role."
Story,"Coordinator maintains members",Members,Highest,5,Sprint 1,"Create, search, edit, deactivate and reactivate members, and reset passwords. Deactivated members cannot log in."
Story,"Coordinator maintains products with per-unit and per-kg pricing",Products,Highest,5,Sprint 1,"Create, edit and withdraw products. Each product is sold by the unit or by weight, with a price and a bay."
Story,"Coordinator manages the weekly round",Rounds,Highest,5,Sprint 1,"Create a round and move it from open to closed to packed. Only one round may be open at a time."
Story,"Member views products available in the current round",Ordering,High,3,Sprint 1,"Available products show name, price and how they are sold. Withdrawn products are hidden. If no round is open the member is told so."
Story,"Member places an order with per-unit and per-kg lines",Ordering,Highest,8,Sprint 1,"Quantities are validated by pricing type, line totals and the order total are calculated, and the unit price is snapshotted at order time."
Story,"Member changes or cancels an order while the round is open",Ordering,High,5,Sprint 1,"Lines can be replaced or the order cancelled while the round is open. Closed rounds are read-only and other members are unaffected."
Story,"Coordinator views all orders in a round",Coordinator views,High,3,Sprint 1,"Every order in the round is listed with member, lines and total; an empty round returns an empty result."
Story,"Coordinator views the round totalled by product",Coordinator views,Highest,5,Sprint 1,"Each product shows the total quantity ordered and the total amount, for placing the wholesale order."
Story,"Coordinator places an order on behalf of a member",Coordinator views,Medium,3,Sprint 1,"Supports members who order by phone, such as Jan Buckley."
Story,"Seed data, README and clean-checkout delivery",Delivery,Highest,5,Sprint 1,"Seeded demo data from the case study, README that runs from a clean checkout, handover document and Jira backlog."
Story,"Packing sheet by crate with actual packed weights",Backlog,Highest,8,Backlog,"Print a sheet ordered by crate number and bay and record what actually went into each crate (Bao's core request)."
Story,"Short-supply allocation",Backlog,High,5,Backlog,"Split short deliveries fairly and record who missed out (the almonds problem)."
Story,"Member statements and running balances",Backlog,High,8,Backlog,"One page per member: ordered, charged, paid, balance (Doug's request)."
Story,"Online payments and bank reconciliation",Backlog,High,13,Backlog,"Out of scope for the brief; needs a payment provider and a ledger."
Story,"Automatic round open and close",Backlog,Medium,3,Backlog,"Close ordering at the published time without manual action."
Story,"Audit trail of changes",Backlog,Medium,5,Backlog,"Who changed what and when, for treasurer-grade defensibility."
Story,"Notifications when a crate is ready",Backlog,Medium,5,Backlog,"SMS or email; explicitly out of scope in the brief."
Story,"Per-round product lists and pricing",Backlog,Medium,8,Backlog,"Different catalogue or prices per round."
Story,"Credit notes carried into the next round",Backlog,Medium,5,Backlog,"Record credits from short supply against the member's next order."
Story,"Accounting software export",Backlog,Low,3,Backlog,"Export figures for the annual return and the accountant."
Story,"Delivery run for housebound members",Backlog,Low,5,Backlog,"Two members in Salisbury cannot collect from the hall."
Story,"Harvest Belt portal integration",Backlog,Low,8,Backlog,"Electronic ordering and price lists; explicitly out of scope in the brief."
Story,"Mobile app",Backlog,Low,13,Backlog,"Explicitly out of scope in the brief."
```

- [ ] **Step 7: 全量验证**

```bash
./mvnw clean package
```

Expected: `BUILD SUCCESS`,JAR 生成。

```bash
./mvnw test
```

Expected: 全部测试通过(约 60 个)。

```bash
java -jar target/greenhill-coop-1.0.0.jar &
sleep 12
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login -H 'Content-Type: application/json' -d '{"memberNo":"M-001","password":"coop1234"}' | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['token'])")
curl -s http://localhost:8080/api/rounds/current -H "Authorization: Bearer $TOKEN"
curl -s http://localhost:8080/api/orders/mine -H "Authorization: Bearer $TOKEN"
curl -s "http://localhost:8080/api/rounds/1/totals" -H "Authorization: Bearer $TOKEN"
kill %1
```

Expected: `/` 返回 `200`;登录返回 token;当前轮次为 Round 34 OPEN;totals 返回按商品汇总(种子数据 Round 33 的汇总)。

- [ ] **Step 8: 提交**

```bash
git add -A
git commit -m "feat(story-12): seed data, README, handover document and Jira backlog import"
```

- [ ] **Step 9: 推送所有分支(交给团队开 PR)**

```bash
git push -u origin main
git push -u origin story/01-foundation story/02-auth story/03-members story/04-products \
  story/05-rounds story/06-catalog story/07-place-order story/08-manage-order \
  story/09-round-orders story/10-round-totals story/11-order-for-member story/12-delivery
```

Expected: 若尚未配置远端,先由团队创建 GitHub 仓库并 `git remote add origin <url>`;PR 按 01→12 顺序合并。

---

## Self-Review 记录(计划自查)

**1. Spec 覆盖检查**

| Spec 章节 | 对应 Task |
|-----------|----------|
| 第 2 节范围(12 故事) | Task 1-12 一一对应 |
| 第 3 节技术选型(预构建前端入库) | Task 2 Step 10-14、Task 12 README |
| 第 4 节结构与 SPA 转发 | Task 2 Step 11 |
| 第 5 节数据模型 | Task 1 Step 4/9/10 |
| 第 6 节计价规则 | Task 7 `PricingService` + `PricingServiceTest` |
| 第 7 节 API | Task 2-11 各 Controller |
| 第 8 节前端页面 | Task 2/3/4/5/6/7/8/9/10/11 各页面 |
| 第 9 节认证权限 | Task 2 `JwtInterceptor`/`@RequireCoordinator` |
| 第 10 节种子数据 | Task 12 `DataSeeder` |
| 第 11 节测试策略 | Task 1-11 各测试类 |
| 第 12 节构建与 Git 流程 | Task 1 `pom.xml`、各 Task Step 建分支、Task 12 Step 9 |
| 第 13 节假设 | 体现在 `PricingService`(不凑整)、`OrderService`(状态判定) |
| 第 14 节已知限制 | Task 12 `docs/handover.md` 第 4 节 |

**2. 占位符扫描:** 无 TBD/TODO;所有代码步骤含完整代码;重复性文件(枚举、DTO、页面)均已给出完整内容或逐字段清单。

**3. 类型一致性检查:** `RoundView`/`ProductView`/`OrderView`/`PageResult` 字段在前后端一致;`placeForMember` 复用 `PlaceOrderRequest`;`RoundService.roundTotals` 使用已定义的私有 `find`;`ProductService` 注入 `RoundService` 无循环依赖。




