# Firebase Authentication email templates - Library Online

Mở Firebase Console, chọn đúng project, vào Authentication -> Templates. Chọn ngôn ngữ Tiếng Việt nếu có. Firebase có thể hiển thị biến liên kết khác nhau theo phiên bản giao diện; hãy giữ nguyên biến action link do Firebase chèn sẵn, không thay bằng URL tự tạo.

## Password reset / Đặt lại mật khẩu

**Subject / Tiêu đề**

Đặt lại mật khẩu tài khoản Library Online

**Nội dung gợi ý**

Xin chào,

Chúng tôi nhận được yêu cầu đặt lại mật khẩu cho tài khoản Library Online liên kết với địa chỉ email này.

Để tạo mật khẩu mới, vui lòng nhấn nút hoặc mở liên kết đặt lại mật khẩu do Firebase chèn bên dưới.

Nếu bạn không yêu cầu thao tác này, hãy bỏ qua email. Mật khẩu hiện tại của bạn sẽ không thay đổi.

Vì lý do bảo mật, vui lòng không chia sẻ liên kết này với bất kỳ ai.

Trân trọng,  
Ban quản lý Library Online

## Email address verification / Xác minh địa chỉ email

**Subject / Tiêu đề**

Xác minh địa chỉ email Library Online

**Nội dung gợi ý**

Xin chào,

Cảm ơn bạn đã đăng ký tài khoản Library Online.

Vui lòng nhấn nút hoặc mở liên kết xác minh do Firebase chèn bên dưới để xác nhận bạn có quyền truy cập hộp thư này.

Nếu bạn không đăng ký tài khoản Library Online, bạn có thể bỏ qua email này.

Lưu ý: xác minh email không đồng nghĩa với việc thẻ thư viện đã được duyệt. Hồ sơ thành viên vẫn cần được thủ thư xem xét theo quy trình của thư viện.

Trân trọng,  
Ban quản lý Library Online

## Kiểm tra sau khi lưu

1. Authentication -> Sign-in method: bật Email/Password.
2. Authentication -> Templates: lưu hai mẫu.
3. Thử thao tác Quên mật khẩu trong ứng dụng với email thử nghiệm.
4. Kiểm tra Inbox và Spam.
5. Mẫu xác minh chỉ được gửi khi ứng dụng/backend thực sự gọi luồng xác minh Firebase Auth. Chỉ lưu template không tự gửi email.

## Luồng duyệt thẻ trong app

- Admin duyệt hồ sơ qua Cloud Function có kiểm tra quyền quản trị.
- Backend tạo tài khoản Firebase Authentication và hồ sơ Users.
- Android gọi chức năng gửi email đặt mật khẩu để độc giả tự tạo mật khẩu, không gửi mật khẩu mặc định dạng văn bản.
- Khi từ chối, backend lưu lý do và gửi email thông báo nếu SMTP đã được cấu hình.

## Triển khai Cloud Functions

1. Cài Firebase CLI, đăng nhập và chọn đúng Firebase project.
2. Tại thư mục LibraryOnline, chạy npm install trong thư mục functions.
3. Thiết lập secret SMTP_USER và SMTP_PASS bằng Firebase CLI.
4. Tạo file .env.<PROJECT_ID> từ .env.example ở máy phát triển, điền SMTP_HOST, SMTP_PORT và SMTP_FROM. Không commit thông tin đăng nhập thật.
5. Chạy firebase deploy --only functions.

Tài khoản Gmail gửi SMTP cần bật xác minh hai bước và dùng App Password nếu nhà cung cấp yêu cầu. Tốt hơn nữa là dùng nhà cung cấp email giao dịch cho môi trường thật.