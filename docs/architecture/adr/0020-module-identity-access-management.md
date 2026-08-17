# ADR 0020: Kiến trúc Module Identity & Access Management (IAM) — Finalized

* **Trạng thái:** ACCEPTED
* **Ngày ban hành:** 2026-08-17
* **Baseline Codebase:** Commit `5e500ea` (Merge PR #64 — Epic 3C), 578/578 tests PASS.
* **Bối cảnh:** Thay thế toàn bộ mock headers (`X-Actor-Id`, `X-User-Id`, `X-Actor-Role`) và `PermitAllSecurity` bằng hệ thống xác thực và phân quyền chính quy mà không phá vỡ tính phân rã của 5 business modules hiện tại.

---

## 1. Quyết định Kiến trúc (Decisions)

* **Aggregate Boundary & Single Identity Source:**
* Toàn bộ dữ liệu xác thực (Credentials) và hồ sơ cá nhân (Profile) được gom chung vào một Aggregate Root duy nhất `User` (bảng `iam_users`).
* Các module khác chỉ tham chiếu qua `userId` (Opaque UUID).
* Email được chuẩn hóa `LOWER(email)` tại tầng Domain và khóa Unique Index dạng `LOWER(email)` tại Database.


* **Mô hình Phân quyền 2 Tầng (Hybrid Authorization):**
* *Global RBAC (IAM quản lý):* Danh sách vai trò toàn cục gồm `USER`, `ADMIN`, `PLANNER`, `AUDITOR`, `VERIFIER`. Mọi tài khoản **bắt buộc sở hữu role nền tảng `USER**`.
* *Contextual Authority (Domain Module tự quản lý):* Vai trò `TRAINER` và `STUDENT` **không lưu trong IAM** và không có trong Token. Quyền được đánh giá động tại Command Handlers của `Workshop`, `Registration`, `Attendance` dựa trên so khớp `principal.userId` với khóa ngoại nội bộ (`assigned_trainer_id`, `registration.user_id`).


* **Authentication & Token Claims:**
* *Transport:* `Authorization: Bearer <access_token>` (Stateless JWT, TTL 15 phút).
* *Danh sách Claims tối thiểu trong Token:*
* `sub`: `UUID` (chính là `userId`).
* `email`: `String` (lowercase).
* `roles`: `List<String>` (Tập hợp Global Roles).
* `status`: `String` (`ACTIVE`).
* `mcp`: `Boolean` (`must_change_password`). Nếu `mcp == true`, Security Filter chặn toàn bộ API nghiệp vụ, chỉ cho phép truy cập `POST /api/v1/iam/me/change-password` và `POST /api/v1/iam/auth/logout`.




* **Quản lý Phiên & Thu hồi (Session & Revocation):**
* Refresh Token (Opaque string, TTL 7 ngày) lưu hash trong DB bảng `iam_refresh_tokens`.
* Áp dụng bắt buộc **Refresh Token Rotation (RTR)**.
* Thu hồi toàn bộ refresh token khi: Đổi mật khẩu, Reset mật khẩu, hoặc tài khoản chuyển sang `LOCKED`/`DISABLED`.


* **Chính sách Khóa tài khoản Lũy tiến (Escalated Lockout):**
* Lưu trữ `failed_login_attempts`, `lockout_count`, `locked_until`, `last_locked_at`.
* Sai 5 lần liên tiếp lần đầu (`lockout_count = 1`): Khóa 15 phút.
* Tái phạm tiếp tục sai 5 lần sau khi mở khóa (`lockout_count >= 2`): Khóa 60 phút.
* Đăng nhập thành công: Reset toàn bộ `failed_login_attempts = 0`, `lockout_count = 0`, `locked_until = null`.


* **Giao tiếp Liên Module & Fallback:**
* Cung cấp Facade `IamExposeAPI` trả về `UserSummarySnapshot`.
* Chuẩn hóa Helper dùng chung `UserSummarySnapshot.fallback(UUID id)`: Khi tra cứu ID lịch sử không tồn tại trong IAM, hệ thống trả về `fullName = "Học viên/Nhân sự cũ"`, `email = "N/A"`, `status = "UNKNOWN"`.
* Sự kiện Outbox (`UserRegisteredEvent`, `PasswordResetRequestedEvent`): Chỉ mang `userId`, `email`, và `tokenId` (định danh token) thay vì lộ raw token bí mật trên Event Bus.



---

## 2. Phân loại API & Endpoint Boundaries

| Phân nhóm | Endpoints | Scope & Phân quyền |
| --- | --- | --- |
| **Auth Public** (6 APIs) | `register`, `verify-email`, `login`, `refresh`, `forgot-password`, `reset-password` | `PermitAll` |
| **Self-Service** (5 APIs) | `GET /me`, `PUT /me/profile`, `POST /me/change-password`, `POST /logout`, `POST /logout-all` | Authenticated User |
| **Admin-Only** (7 APIs) | `create-user`, `list-users`, `get-user-detail`, `update-roles`, `lock/unlock`, `disable/enable`, `admin-reset-password` | `ADMIN` |

---