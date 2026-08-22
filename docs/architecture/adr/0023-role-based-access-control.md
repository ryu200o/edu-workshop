# ADR 0023: Role-Based Access Control and Declarative Method Security Policy

* **Trạng thái:** PROPOSED (Đã đồng thuận giữa SA, BA và Đội thi công)
* **Ngày:** 2026-08-22
* **Người quyết định:** Software Architect (SA), Business Analyst (BA), Core Engineering Team
* **Tài liệu liên quan:**
* `ADR 0020: IAM Architecture & Security Ingress`

* `ADR 0021: Strict Command-Query Separation (Strict CQS)`
* `ADR 0022: Declarative Idempotency Framework`



---

## 1. Bối cảnh & Vấn đề (Context & Problem Statement)

Sau khi hoàn tất hạ tầng xác thực (IAM - ADR 0020), hệ thống đã cấp phát và nhận diện được danh tính `AuthenticatedPrincipal` mang danh sách quyền hạn toàn cục (`GlobalRole`). Tuy nhiên, qua quá trình rà soát bảo mật toàn diện, hệ thống phát hiện các lỗ hổng và sự bất nhất trong cơ chế phân quyền (Authorization):

1. **Thiếu sót Vai trò Quản lý Hạ tầng Vật lý:** Chưa có vai trò chuyên trách để quản lý phòng ốc, trang thiết bị, lịch bảo trì và vận hành cơ sở vật chất (Facility Operations).


2. **Thiếu Bảo vệ Phân quyền tại các Nghiệp vụ Trọng yếu:** Toàn bộ các thao tác ghi (Write Commands) của Module `room`, `facility-ops` và `workshop` hiện chỉ dừng ở mức yêu cầu đăng nhập chung (`authenticated`), chưa có rào cản phân quyền theo vai trò. Bất kỳ người dùng hợp lệ nào cũng có thể tạo, chỉnh sửa hoặc hủy tài nguyên.


3. **Sự Phân mảnh trong Cơ chế Thực thi (Inconsistent Enforcement):** Hệ thống đang tồn tại đồng thời 3 cách tiếp cận bảo mật: quy tắc URL trong `SecurityFilterChain` (IAM), kiểm tra thủ công `hasRole(...)` rải rác trong Controller/Handler (Attendance, Registration), và không kiểm tra (Workshop, Room).


4. **Trộn lẫn Giữa RBAC và Ranh giới Nghiệp vụ:** Việc kiểm tra vai trò thủ công trong Application Layer làm ô nhiễm Domain/Application Core bởi các ngoại lệ hạ tầng, gây khó khăn cho việc kiểm thử độc lập và duy trì tính đóng gói của kiến trúc Hexagonal.



---

## 2. Quyết định Kiến trúc (Decision)

### 2.1 Bổ sung Vai trò Toàn cục `FACILITY_MANAGER`

* Bổ sung giá trị `FACILITY_MANAGER` vào danh mục `GlobalRole`.


* **Phạm vi quyền hạn (Scope):** Quản lý toàn bộ vòng đời của phòng vật lý (`room`), thiết lập lịch bảo trì (`maintenance-schedules`), và truy xuất dữ liệu tác động vận hành cơ sở vật chất (`facility-ops`).


* Khái niệm "Facility" đại diện cho toàn bộ hạ tầng cơ sở vật chất; do đó không tách nhỏ thành `ROOM_MANAGER` nhằm tránh phân mảnh vai trò khi hệ thống mở rộng thiết bị và cơ sở sau này.

### 2.2 Phân tầng Kiến trúc Bảo mật (Security Layering Architecture)

Hệ thống chuẩn hóa cơ chế bảo vệ theo mô hình 2 tầng rạch ròi:

* **Tầng Mạng & Xác thực Chung (`SecurityFilterChain`):**
* Chỉ chịu trách nhiệm về Ingress Skeleton, CORS, CSRF, giải mã JWT.


* Chỉ duy trì quy tắc định tuyến URL cho module IAM (`/api/v1/iam/auth/**` là `permitAll`, `/api/v1/iam/admin/**` là `hasRole('ADMIN')`, `/api/v1/iam/me/**` là `authenticated`).


* Không mở rộng cấu hình URL-based rules sang các module nghiệp vụ nhằm tránh lỗi lệch cấu hình (*Configuration Drift*) khi thay đổi đường dẫn.




* **Tầng Cửa ngõ Nghiệp vụ (Declarative Method Security tại Inbound Controllers):**
* Kích hoạt Method Security tập trung trên toàn hệ thống.


* Áp dụng kiểm tra phân quyền RBAC trực tiếp tại Inbound Controller methods trước khi ủy quyền xử lý cho `CommandBus` hoặc Query Handlers.


* Tự động phản hồi chuẩn RFC 7807 `403 Forbidden` khi người dùng không đủ quyền hạn.





### 2.3 Chính sách Phân quyền Khai báo theo Miền (Domain-Driven Policy Annotations)

Thay vì sử dụng các biểu thức SpEL thô (`hasAnyRole(...)`) rải rác trong mã nguồn, hệ thống chuẩn hóa thành các **Policy Meta-Annotations** mang ngôn ngữ miền nghiệp vụ đặt tại Shared Kernel:

* `@CanManageRooms`: Áp dụng cho các tác vụ quản lý phòng và lịch bảo trì cơ sở vật chất.


* `@CanManageWorkshops`: Áp dụng cho toàn bộ các lệnh khởi tạo, điều phối, lên lịch và xuất bản Workshop.


* `@CanMarkAttendance`: Áp dụng cho tác vụ chốt và ghi nhận sổ điểm danh sự kiện.


* `@CanAuditAttendance`: Áp dụng cho tác vụ kiểm toán và điều chỉnh dữ liệu điểm danh.


* `@CanVerifyRegistrations`: Áp dụng cho tác vụ soát vé và xác thực lượt đăng ký của học viên.



### 2.4 Phân định Ranh giới: RBAC Tĩnh vs. Resource Ownership

* **RBAC Tĩnh (Role-Based Access Control):** Được bảo vệ tuyệt đối ở biên tiếp nhận (Inbound Controller) thông qua Policy Meta-Annotations.
* **Resource Ownership Đơn giản (Chính chủ cấp Ingress):** Controller trích xuất trực tiếp `principal.userId()` từ ngữ cảnh bảo mật và gán vào Command/Query, ngăn chặn hành vi giả mạo ID người khác qua Request Payload.


* **Data-driven Ownership (Chính chủ phụ thuộc Dữ liệu):** Các điều kiện kiểm tra sở hữu phức tạp dựa trên trạng thái thực thể trong cơ sở dữ liệu sẽ được thẩm định an toàn bên trong Application Layer dựa trên `actorId`, không kéo Spring Security Framework vào sâu trong Domain Model.



### 2.5 Ma trận Phân quyền Chuẩn hóa Toàn Hệ Thống (Authorization Matrix)

| Phân vùng Nghiệp vụ | Tuyến Endpoint / Hành động | Chính sách Quyền hạn Áp dụng | Đối tượng Được phép |
| --- | --- | --- | --- |
| **IAM Authentication** | `POST /api/v1/iam/auth/**`<br> | Public Ingress (`permitAll`)

| Mọi người dùng / Khách vãng lai

|
| **IAM Administration** | `/api/v1/iam/admin/**`<br> | System Administration (`ADMIN`)

| Quản trị viên hệ thống

|
| **IAM User Profile** | `/api/v1/iam/me/**`<br> | User Self (`USER` + `principal.userId()`)

| Chính chủ tài khoản

|
| **Room Management** | `POST /api/v1/rooms/**`<br><br>

<br>`POST /api/v1/rooms/{id}/maintenance-schedules`<br> | `@CanManageRooms` | `FACILITY_MANAGER`, `ADMIN`<br> |
| **Room Tra cứu** | `GET /api/v1/rooms/**`<br> | General Authenticated Read | Toàn bộ người dùng đã đăng nhập (`USER`)

|
| **Facility Operations** | `GET /api/v1/facility-ops/**`<br> | `@CanManageRooms` | `FACILITY_MANAGER`, `ADMIN`<br> |
| **Workshop Management** | `POST /api/v1/workshops/**` (Toàn bộ 13 write commands)

| `@CanManageWorkshops` | `PLANNER`, `ADMIN`<br> |
| **Workshop Tra cứu** | `GET /api/v1/workshops/**`<br> | General Authenticated Read | Toàn bộ người dùng đã đăng nhập (`USER`)

|
| **Registration Ingress** | `POST /api/v1/registrations` (Tạo / Hủy đăng ký)

| User Self (`USER` + `principal.userId()`)

| Chính chủ học viên

|
|  | `POST /api/v1/registrations/verify`<br> | `@CanVerifyRegistrations` | `VERIFIER`, `ADMIN`<br> |
|  | `GET /api/v1/registrations/my`<br> | User Self (`USER` + `principal.userId()`)

| Chính chủ học viên

|
| **Attendance Ingress** | `POST /api/v1/.../mark`<br> | `@CanMarkAttendance` | `PLANNER`, `ADMIN`<br> |
|  | `POST /api/v1/.../adjust`<br> | `@CanAuditAttendance` | `AUDITOR`, `ADMIN`<br> |
|  | `POST /api/v1/.../check-in`<br>

<br>`POST /api/v1/.../appeal`<br> | User Self (`USER` + `principal.userId()`)

| Chính chủ học viên tham gia

|
|  | `GET /api/v1/workshops/{id}/attendance`<br> | Event & Audit Access | `PLANNER`, `AUDITOR`, `ADMIN`<br> |
|  | `GET /api/v1/attendance-records/{id}`<br> | Fine-grained Record Access | Chính chủ bản ghi HOẶC `AUDITOR`, `ADMIN`<br> |

---

## 3. Hậu quả & Đánh đổi (Consequences)

### Ưu điểm

* **Bảo vệ Toàn diện:** Xóa bỏ hoàn toàn các lỗ hổng "bỏ ngỏ quyền" tại các module `room`, `facility-ops`, và `workshop`.


* **Khai báo Tường minh (Declarative & Clean):** Sử dụng Policy Meta-Annotations giúp Controller thể hiện rõ ràng ý đồ nghiệp vụ, loại bỏ các chuỗi SpEL phân mảnh và kiểm tra logic thủ công rải rác.


* **Chuẩn hóa Giao diện Lỗi:** Toàn bộ vi phạm phân quyền được quy về mã trạng thái chuẩn HTTP 403 Forbidden thông qua bộ xử lý lỗi tập trung của nền tảng.


* **Giữ sạch Lõi Ứng dụng:** Application Handlers và Domain Aggregates hoàn toàn không bị ràng buộc bởi các interface hay framework của Spring Security.



### Ràng buộc & Đánh đổi

* **Cập nhật Bộ Kiểm thử Tự động:** Các bài kiểm thử tích hợp và E2E hiện tại gọi vào các endpoint được bảo vệ bắt buộc phải khai báo ngữ cảnh xác thực với vai trò tương ứng (`@WithMockCustomUser(roles = ...)`).
* **Quản trị Phân quyền Nghiêm ngặt:** Tài khoản người dùng khi tạo mới hoặc cấp quyền quản trị phải được gán đúng danh mục vai trò toàn cục để thực thi các tác vụ chuyên biệt tương ứng.



---