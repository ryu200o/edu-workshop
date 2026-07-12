application/
├── port/
│   ├── in/
│   │   ├── command/     <-- Chứa DTO Lệnh Ghi (Records) và Bus Interface
│   │   └── query/       <-- Chứa DTO Lệnh Đọc (Records) và Bus Interface
│   └── out/             <-- Chứa ALL Outbound Ports (UserStateGateway, UserQueryGateway,...)
├── handler/             <-- PHẲNG HÓA hoàn toàn (Không chia package con)
├── event/               <-- Chứa Application Events
└── mapper/              <-- Chứa các bộ chuyển đổi DTO <-> Domain

Code gợi ý:
Dưới đây là mã nguồn cụ thể, hoàn chỉnh cho CreateUserCommandHandler và các thành phần liên quan theo đúng cấu trúc Cách 3 (phẳng hóa tầng handler, giấu kín bằng package-private) kết hợp với CommandBus bằng Spring Context.
Tôi sẽ viết code theo chuẩn Java hiện đại (sử dụng Java Records) để bạn áp dụng trực tiếp vào dự án thực tế.
------------------------------
## 1. Thành phần dùng chung (Shared Interfaces)
Hai interface này bạn định nghĩa một lần duy nhất ở package core/shared của hệ thống để làm khung.
```java
// Khung định nghĩa một Lệnh có kiểu dữ liệu trả về là R
public interface Command<R> {}
// Khung định nghĩa một bộ xử lý Lệnh tương ứng
public interface CommandHandler<C extends Command<R>, R> {
    R handle(C command);
}
```
------------------------------
## 2. Tầng port/in/command/ (Bề mặt tiếp xúc của Module)
Đây là phần duy nhất mà Controller (Driving Adapter) nhìn thấy và có quyền truy cập.
```java
package com.example.module.application.port.in.command;
import com.example.module.application.core.Command;
import java.util.UUID;
// DTO đầu vào, dùng Record để tự động có getter, equals, hashCode, toString
public record CreateUserCommand(
    String email,
    String password,
    String fullName
) implements Command<UUID> {} // Trả về UUID của User sau khi tạo thành công
```
------------------------------
## 3. Tầng port/out/ (SPI - Outgoing Port)
Interface định nghĩa cách thức lưu trữ dữ liệu. Tầng Application chỉ gọi Interface này. Việc lưu vào MySQL, PostgreSQL hay MongoDB do tầng Adapter bên ngoài tự triển khai.
```java
package com.example.module.application.port.out;
import com.example.module.domain.User; // Domain Model thuần túy của bạn

public interface UserStateGateway {
    User save(User user);
    boolean existsByEmail(String email);
}
```
------------------------------
## 4. Tầng handler/ (Logic xử lý đóng gói kín)
Lưu ý quan trọng: Class CreateUserCommandHandler KHÔNG có từ khóa public. Nó ở dạng package-private để không một module nào khác bên ngoài có thể can thiệp hoặc import trực tiếp.
```java
package com.example.module.application.handler;
import com.example.module.application.core.CommandHandler;import com.example.module.application.port.in.command.CreateUserCommand;import com.example.module.application.port.out.UserStateGateway;import com.example.module.domain.User;import org.springframework.stereotype.Component;import org.springframework.transaction.annotation.Transactional;import java.util.UUID;

@Component // Đăng ký với Spring Context để CommandBus tự động tìm thấy
class CreateUserCommandHandler implements CommandHandler<CreateUserCommand, UUID> {

    private final UserStateGateway userStateGateway; // Gọi Output Port
    // private final ApplicationEventPublisher eventPublisher; // Nếu bạn dùng Spring Modulith Events

    // Spring tự động Inject dependency qua Constructor class
    CreateUserCommandHandler(UserStateGateway userStateGateway) {
        this.userStateGateway = userStateGateway;
    }

    @Override
    @Transactional // Quản lý Transaction được đặt chính xác tại UseCase/Handler này
    public UUID handle(CreateUserCommand command) {
        
        // 1. Kiểm tra quy tắc nghiệp vụ (Business Rule Validation)
        if (userStateGateway.existsByEmail(command.email())) {
            throw new IllegalArgumentException("Email already registered!");
        }

        // 2. Gọi Domain Model để thực hiện nghiệp vụ (Hoặc map từ DTO sang Domain)
        // Giả sử class User là Rich Domain Model chứa logic mã hóa password...
        User newUser = User.createNew(
            command.email(), 
            command.password(), 
            command.fullName()
        );

        // 3. Lưu xuống Database thông qua Output Port
        User savedUser = userStateGateway.save(newUser);

        // 4. (Tùy chọn) Phát đi một Event cho Spring Modulith nếu cần các module khác lắng nghe
        // eventPublisher.publishEvent(new UserCreatedEvent(savedUser.getId()));

        // 5. Trả về kết quả cho CommandBus -> trả về cho Controller
        return savedUser.getId();
    }
}
```
------------------------------
## 5. Mã nguồn cho SimpleCommandBus chạy bằng Spring Context
Để đoạn code trên hoạt động tự động thông qua CommandBus, đây là cách viết lớp triển khai của Bus sử dụng ResolvableType (rất an toàn về kiểu dữ liệu trong Spring):
```java
package com.example.module.application.handler;
import com.example.module.application.core.Command;import com.example.module.application.core.CommandHandler;import com.example.module.application.port.in.CommandBus;import org.springframework.context.ApplicationContext;import org.springframework.core.ResolvableType;import org.springframework.stereotype.Component;

@Component
class SimpleCommandBus implements CommandBus {

    private final ApplicationContext context;

    public SimpleCommandBus(ApplicationContext context) {
        this.context = context;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R, C extends Command<R>> R execute(C command) {
        // Tự động tìm bean phù hợp với CommandHandler<TênCommand, TênKếtQuả>
        ResolvableType type = ResolvableType.forClassWithGenerics(CommandHandler.class, command.getClass(), Object.class);
        String[] beanNames = context.getBeanNamesForType(type);

        if (beanNames.length == 0) {
            throw new IllegalStateException("Không tìm thấy Handler nào cho lệnh: " + command.getClass().getSimpleName());
        }

        CommandHandler<C, R> handler = (CommandHandler<C, R>) context.getBean(beanNames[0]);
        return handler.handle(command);
    }
}
```
Dưới đây là mã nguồn hoàn chỉnh cho phần Query (Đọc dữ liệu) của thực thể User.
Điểm khác biệt cốt lõi của Query so với Command trong mô hình CQS kết hợp Hexagonal là: Query không đi qua Domain Model. Nó đi "tắt" (Bypass) từ Output Port thẳng ra DTO để tối ưu tốc độ đọc dữ liệu, sử dụng @Transactional(readOnly = true).
------------------------------
## 1. Thành phần dùng chung (Shared Interfaces)
Định nghĩa một lần duy nhất tại package core/shared.
```java
// Khung định nghĩa một Truy vấn có kiểu dữ liệu trả về là R
public interface Query<R> {}
// Khung định nghĩa một bộ xử lý Truy vấn tương ứng
public interface QueryHandler<Q extends Query<R>, R> {
    R handle(Q query);
}
```
------------------------------
## 2. Tầng port/in/query/ (Bề mặt tiếp xúc phần Đọc)
Đây là DTO đầu vào của truy vấn và DTO kết quả trả ra cho Controller.
```java
package com.example.module.application.port.in.query;
import com.example.module.application.core.Query;import java.util.UUID;
// 1. DTO Truy vấn: Chứa các tiêu chí tìm kiếm (Dùng Record)
public record GetUserQuery(UUID userId) implements Query<UserResponse> {}
// 2. DTO Kết quả: Chỉ chứa các trường cần hiển thị ra giao diện (Projection)
public record UserResponse(
    UUID id,
    String email,
    String fullName,
    String status
) {}
```
------------------------------
## 3. Tầng port/out/ (SPI - Outgoing Port cho Query)
Trong CQS chuẩn, bạn nên tách biệt Output Port của Lệnh Đọc (UserQueryGateway) ra khỏi Lệnh Ghi (UserStateGateway).

* UserQueryGateway sẽ trả thẳng về UserResponse (DTO) chứ không trả về User (Domain Model). Điều này giúp Spring Data JPA / JDBC / jOOQ chạy projection cực nhanh, giảm tải cho bộ nhớ.
```java
package com.example.module.application.port.out;
import com.example.module.application.port.in.query.UserResponse;import java.util.Optional;import java.util.UUID;
public interface UserQueryGateway {
    Optional<UserResponse> findById(UUID userId);
}
```
------------------------------
## 4. Tầng handler/ (Logic truy vấn xếp phẳng, đóng gói kín)
Class này cũng để ở dạng package-private (không có chữ public) để giấu kín bên trong module.
```java
package com.example.module.application.handler;
import com.example.module.application.core.QueryHandler;import com.example.module.application.port.in.query.GetUserQuery;import com.example.module.application.port.in.query.UserResponse;import com.example.module.application.port.out.UserQueryGateway;import org.springframework.stereotype.Component;import org.springframework.transaction.annotation.Transactional;

@Component // Đăng ký để QueryBus tự động tìm thấy
class GetUserQueryHandler implements QueryHandler<GetUserQuery, UserResponse> {

    private final UserQueryGateway userQueryGateway; // Gọi Output Port chuyên Đọc

    // Inject qua Constructor
    class GetUserQueryHandler(UserQueryGateway userQueryGateway) {
        this.userQueryGateway = userQueryGateway;
    }

    @Override
    @Transactional(readOnly = true) // Tối ưu hóa hiệu năng Database (Bỏ Dirty Checking của Hibernate)
    public UserResponse handle(GetUserQuery query) {
        
        // Gọi thẳng Output Port và trả về DTO, không qua xử lý nghiệp vụ Domain
        return userQueryGateway.findById(query.userId())
            .orElseThrow(() -> new IllegalArgumentException("User not found with ID: " + query.userId()));
    }
}
```
------------------------------
## 5. Bổ sung QueryBus vào tầng handler/
Tương tự như CommandBus, bạn tạo thêm một bộ điều phối dành riêng cho Query để Controller sử dụng.
```java
// Interface đặt tại port/in/
package com.example.module.application.port.in;
public interface QueryBus {
    <R, Q extends Query<R>> R execute(Q query);
}
```
```java
// Lớp hiện thực đặt phẳng tại handler/ (package-private)
package com.example.module.application.handler;
import com.example.module.application.core.Query;import com.example.module.application.core.QueryHandler;import com.example.module.application.port.in.QueryBus;import org.springframework.context.ApplicationContext;import org.springframework.core.ResolvableType;import org.springframework.stereotype.Component;

@Component
class SimpleQueryBus implements QueryBus {

    private final ApplicationContext context;

    public SimpleQueryBus(ApplicationContext context) {
        this.context = context;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <R, Q extends Query<R>> R execute(Q query) {
        ResolvableType type = ResolvableType.forClassWithGenerics(QueryHandler.class, query.getClass(), Object.class);
        String[] beanNames = context.getBeanNamesForType(type);

        if (beanNames.length == 0) {
            throw new IllegalStateException("Không tìm thấy Handler nào cho truy vấn: " + query.getClass().getSimpleName());
        }

        QueryHandler<Q, R> handler = (QueryHandler<Q, R>) context.getBean(beanNames);
        return handler.handle(query);
    }
}
```
------------------------------
## Kết quả cuối cùng tại Controller (Driving Adapter)
Sau khi có cả Command và Query Bus, lớp Controller của bạn sẽ đạt đến độ tối giản và hoàn hảo về mặt kiến trúc:
```java
package com.example.module.adapter.in.rest;
import com.example.module.application.port.in.CommandBus;import com.example.module.application.port.in.QueryBus;import com.example.module.application.port.in.command.CreateUserCommand;import com.example.module.application.port.in.query.GetUserQuery;import com.example.module.application.port.in.query.UserResponse;import org.springframework.web.bind.annotation.*;import java.util.UUID;

@RestController
@RequestMapping("/users")
class UserController {

    private final CommandBus commandBus;
    private final QueryBus queryBus;

    // Inject cả 2 Bus thông qua Port Interface công khai
    public UserController(CommandBus commandBus, QueryBus queryBus) {
        this.commandBus = commandBus;
        this.queryBus = queryBus;
    }

    // LỆNH GHI (COMMAND)
    @PostMapping
    public UUID createUser(@RequestBody CreateUserCommand command) {
        return commandBus.execute(command);
    }

    // LỆNH ĐỌC (QUERY)
    @GetMapping("/{id}")
    public UserResponse getUserById(@PathVariable UUID id) {
        return queryBus.execute(new GetUserQuery(id));
    }
}
```
## Điểm cộng lớn của thiết kế này cho dự án thực tế:

* Tách biệt Database (Read/Write Splitting): Vì ở tầng handler, CreateUserCommandHandler dùng UserStateGateway còn GetUserQueryHandler dùng UserQueryGateway. Sau này nếu dự án phình to, bạn có thể dễ dàng cấu hình cho UserQueryGateway đọc từ một Database Replica (Read-only), còn UserStateGateway ghi vào Master Database mà không cần sửa một dòng code nào trong tầng Application.


