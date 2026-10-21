# Ngày 2: tài khoản, hồ sơ và phiên đăng nhập

Căn cứ: checklist người dùng trong phiên làm việc, đặc tả Library Online DOCX, thiết kế bảng dữ liệu TXT, Figma PDF đã cung cấp; kiến trúc Spark hiện có trong SPARK_REGISTRATION_MIGRATION.md.

- Java/XML; giữ giao diện/luồng OTP email đã chạy được; đăng ký vẫn tạo RegistrationRequests Pending + hai index, không tự tạo Reader hoạt động hoặc Admin. Admin duyệt mới tạo Auth + Users.
- Firebase Auth UID là khóa hồ sơ. Không lưu password/OTP vào SQLite hoặc Intent. Cloud là nguồn quyền; SQLite là cache hồ sơ, không được dùng để tự cấp quyền.
- Tương thích Admins/{uid}.adminStatus Active/HoatDong đang dùng. Không tự migration sang Users.role=Admin và adminId riêng khi chưa có kế hoạch dữ liệu.
- Reader phải có hồ sơ Reader, thẻ HoatDong, không isDeleted; từ chối thiếu hồ sơ/UID sai/khóa/chờ duyệt/hết hạn. mustChangePassword phải hoàn tất trước Home.
- Kiểm tra phiên từ server khi vào/quay lại màn hình riêng tư; lỗi mạng cho retry, không âm thầm lấy role từ Intent/cache. Admin không hoạt động và Reader không được thấy nội dung Admin.
- Login khôi phục phiên SDK có sẵn, phân biệt network/disabled/rate-limit/config với credential sai; có busy gate chống nhấn lặp. Đăng xuất xóa private back stack.
- Hồ sơ server được upsert theo UID về SQLite, không INSERT OR REPLACE làm mất lịch sử; không xóa cache nghiệp vụ để sửa xung đột.
- Chỉ hoàn thiện phần Home theo vai trò/thông tin người dùng/phiên trong phạm vi Ngày 2; không tự xây thêm các phân hệ kho/mượn/trả chưa triển khai.
- Kiểm tra cấu hình ứng dụng và SDK khởi tạo được tại máy hiện tại. Không có quyền Firebase Console thì ghi rõ provider/live account/Logcat chưa xác minh, không giả kết quả.
- Không deploy Firebase, không ghi/xóa/migrate dữ liệu cloud để thử. Nếu Rules thay đổi phải thông báo người dùng cập nhật. Test Rules chỉ dùng emulator/demo project.
- Chạy unit tests (bao gồm Android Patterns trên runtime Robolectric phù hợp), build debug, kiểm tra diff và push main theo ủy quyền đã có.
