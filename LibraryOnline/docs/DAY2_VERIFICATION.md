# Ngày 2 — đăng nhập, hồ sơ và phiên

Phạm vi: checklist Ngày 2 của người dùng; đối chiếu DOCX đặc tả, thiết kế bảng TXT, Figma PDF và kiến trúc Spark đang có. Không tạo nghiệp vụ mới dựa vào tên hàm/dữ liệu mẫu.

## Các lỗi có căn cứ và cách sửa

| Bằng chứng trước sửa | Xử lý |
|---|---|
| Login/AdminLogin tự đọc hồ sơ với logic khác nhau, dùng cache mặc định, không khôi phục phiên | Dùng SessionGateway/SessionPolicy chung, refresh Auth token và đọc Source.SERVER, điều hướng theo quyền đã xác minh |
| Home Admin khởi động listener ngay khi onCreate; Home Reader chỉ kiểm tra Auth khác null | SessionActivity che nội dung và chưa chạy listener/dữ liệu riêng tư cho đến khi xác minh role, trạng thái và UID |
| Quay lại màn hình không kiểm tra lại quyền/khóa thẻ | Kiểm tra trên mỗi foreground và theo dõi Auth/Admins/Users; lỗi mạng có retry/logout |
| Task listener gắn Activity bị gỡ lúc onStop nhưng busy vẫn true | Vô hiệu kết quả cũ bằng generation, reset khi dừng và khôi phục lại khi quay về; có regression test |
| Hộp thoại là cửa sổ riêng, có thể nằm trên lớp chặn phiên | Theo dõi và đóng hộp thoại khi khóa/dừng/đăng xuất; xác nhận Admin kiểm tra phiên sẵn sàng |
| Tasks.whenAll che loại lỗi Firestore bên trong ExecutionException | Trả nguyên exception của task con để phân biệt mất mạng và hết phiên |
| Users SQLite chưa được đồng bộ từ hồ sơ đăng nhập | Upsert theo UID sau kiểm tra server, transaction khi lưu quan hệ Admin; không REPLACE/xóa lịch sử |
| Admin không có thao tác đăng xuất hiển thị | Thêm thông tin phiên và nút Đăng xuất trong layout thật (theme không có ActionBar) |
| Register chỉ khóa nút gửi; tên một ký tự có thể qua UI nhưng bị Rules từ chối | Kiểm tra tên 2–120, email tối đa 254, ngày ISO thật/không tương lai; chặn gửi lặp/OTP đang gửi và khóa trường khi ghi |
| Đổi mật khẩu override onBackPressed cũ không còn bảo đảm nhận gesture với target SDK mới | OnBackPressedDispatcher: Back đăng xuất; kết quả hoàn tất phải qua kiểm tra phiên lại |
| Callback đồng bộ Admin có thể đến sau khi executor bị đóng, nút sync bị kẹt sau khi dừng | Submission đồng bộ kiểm tra closed dưới cùng lock; đóng SQLite sau các tác vụ đang chạy, reset nút khi dừng; regression test callback muộn |
| InputValidatorTest chạy Android Patterns trên JVM stub | Chạy cùng assertions bằng Robolectric; không bỏ test, không mock kết quả đúng |

## Đối chiếu schema và luồng

- Đăng ký **không tạo ngay tài khoản Reader hoạt động**. Đặc tả yêu cầu RegistrationRequests/Pending và Admin duyệt mới tạo Auth + Users. Giữ nguyên batch ba document gồm hai index email/phone. Form không có role/UID do người dùng chọn; Rules từ chối field thêm và nâng quyền.
- Auth UID = đường dẫn Users/{uid} và userId. SessionPolicy yêu cầu Reader, HoatDong, không isDeleted, hồ sơ bắt buộc có dữ liệu và email khớp Auth. mustChangePassword chuyển đến đổi mật khẩu trước Home.
- Nguồn Admin là Admins/{uid}.adminStatus Active/HoatDong được Rules bảo vệ. Không tin Users.role=Admin riêng lẻ, Intent hoặc role trong SQLite. Tương thích Admin khởi tạo ban đầu chỉ có Admins document; không tự sinh employeeCode/hồ sơ giả để thỏa SQLite.
- Firestore giữ dữ liệu hồ sơ chuẩn; SQLite là cache, không cấp quyền. Trùng email giữa hai UID gây lỗi thay vì xóa người dùng cũ. Không cache password/OTP vào SQLite hay Intent; ô password không lưu view state.
- Home Reader tiếp tục đọc sách từ SQLite, catalogue sync nhận dữ liệu Firestore; không đưa seed/fallback trở lại và không đổi categoryId/category hay trạng thái BookCopies.
- Login đúng quyền thay toàn bộ task; logout xóa private back stack. Trang Admin/Reader và đổi mật khẩu không exported.
- Checkbox ghi nhớ chỉ lưu email, không lưu password. Firebase SDK quản lý phiên xác thực.

## Checklist thực hiện và giới hạn

| Nhóm | Đã kiểm tra bằng mã/test local | Cần kiểm tra trên máy/Console |
|---|---|---|
| Firebase/init | google-services.json có cấu hình, package trùng applicationId; gateway kiểm tra khởi tạo, token/server profile | Provider Email/Password và tình trạng tài khoản production chưa truy cập Console |
| Login Java/XML | Giữ layout thiết kế hiện có, mắt hiện/ẩn mật khẩu, busy controls, lỗi credential/network/disabled/rate-limit, khôi phục và retry phiên | Kiểm tra bố cục bằng mắt trên thiết bị và mạng thật |
| Đăng ký | Form/OTP hiện có, giới hạn đầu vào, Pending-only, unique indices, không tự cấp quyền | Gửi SMTP/nhận OTP và email duyệt thật chưa chạy trong cloud này |
| Hồ sơ/cache | UID/role/status; upsert lặp giữ FK LoanReceipts; xung đột email không thay thế dữ liệu | Hồ sơ production thiếu trường cần Admin kiểm tra, không tự bổ sung dữ liệu giả |
| Quyền | Reader không mở nội dung Admin, Admin hợp lệ mới chạy private work; khóa/thiếu/sai UID bị từ chối; Rules chống nâng quyền | Dùng tài khoản Admin/Reader thật để smoke test |
| Phiên/Home | Background/foreground, kết quả muộn sau logout, retry offline, đổi mật khẩu bắt buộc, đóng private dialogs, clear task | Back gesture API 37, process death và Logcat trên thiết bị thật |

## Phần còn tồn tại được ghi rõ

- Không có thiết bị/Android emulator gắn adb: chưa chạy Logcat hay thao tác UI thực tế. Robolectric và Firebase service emulator không thay thế kiểm thử toàn bộ app trên máy.
- ForgotPasswordActivity hiện query Users trước đăng nhập, bị Rules bảo mật từ chối. Không mở public Users để chữa lỗi. Luồng khôi phục mật khẩu và hồ sơ cá nhân đầy đủ thuộc bước cần thống nhất riêng, không đánh dấu đã hoàn thiện trong Ngày 2.
- Home Admin hiện là màn hình duyệt thành viên có thật trong repository, chưa phải toàn bộ dashboard/kho/mượn/trả trong thiết kế. Không tự dựng các nghiệp vụ ngoài checklist tài khoản/phiên.
- OTP SMTP do client thực hiện không phải bằng chứng xác minh phía server; quyền Reader vẫn phụ thuộc Admin duyệt. Kiến trúc Spark tạo Auth và Firestore không thể thành một transaction chung; lỗi rollback Auth cần theo dõi như audit trước. Không tuyên bố đã giải quyết giới hạn kiến trúc này bằng sửa UI.
- Admin bootstrap thiếu Users/employeeCode chỉ được xác minh quyền và hiển thị thông tin sẵn có; không tự tạo quan hệ SQL thiếu dữ liệu. Hồ sơ Admin đầy đủ mới cache được.
- Không đổi dependency production hoặc lockfile. Các cảnh báo lint còn lại được ghi trong kết quả lệnh; không tắt lint hay nới Rules.
- **Không thay đổi firestore.rules trong đợt Ngày 2; không deploy Firebase, không migration/xóa cloud. Người dùng không cần cập nhật Rules cho commit này.** Khi có thay đổi Rules ở các đợt sau sẽ thông báo rõ.

## Kết quả lệnh

Lượt cuối 2026-10-21, chạy thật và đọc kết quả:

| Lệnh | Kết quả |
|---|---|
| `bash gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --no-configuration-cache --no-daemon --max-workers=2` | BUILD SUCCESSFUL, 45 test, 0 fail/skip; APK debug tạo được; lint 0 lỗi, 210 cảnh báo (chủ yếu chuỗi hardcode, bố cục/RTL, cảnh báo hiện có) |
| `npm --prefix functions run check` | Đạt cú pháp Node |
| `npm --prefix functions test` | 5/5 contract tests đạt |
| ESLint 9.39.1 với `functions/eslint.config.cjs`, index.js và test/*.js | Exit 0 |
| Firebase CLI `emulators:exec --config firebase.emulators.json --project demo-library-online --only firestore 'npm --prefix tools/firestore-tests test'` | 11/11 Rules tests đạt, local emulator |
| Firebase CLI `emulators:exec --config firebase.emulators.json --project demo-library-online --only auth 'npm --prefix tools/firestore-tests run test:auth'` | 1 integration test đạt: tạo/sign-in cùng UID, sai mật khẩu, không có tài khoản, disabled; REST Auth emulator, không phải login trên thiết bị Android |
| `git diff --check` | Đạt |
| `adb devices` / Logcat | Không thực hiện được: adb không khởi tạo vì mặc định cố tạo `/home/agent/.android` trên filesystem read-only; biến Android user/config không đổi được đường dẫn này của binary hiện tại. Không có kết quả Logcat hay smoke test thiết bị để báo đạt. |

Test phiên/UI dùng fake gateway trên Robolectric, test cache dùng SQLite thật trong runtime Robolectric. Không coi các test này là đã kiểm tra provider/account production hay thao tác OTP thật. Lượt đầu AuthErrorsTest cũng gặp `TextUtils.isEmpty not mocked`; đã chuyển sang Robolectric và chạy lại thành công. Không sửa lỗi bằng bật `returnDefaultValues` hay loại test khỏi suite.

Emulator config thêm **Auth local 127.0.0.1:9099**; không thay Firebase production config. Không đổi runtime/exports Cloud Functions, dependency hoặc lockfile.


## File thay đổi trong commit triển khai

| File | Lý do |
|---|---|
| `app/build.gradle.kts` | Robolectric đọc resource layout thật |
| `app/src/main/java/com/example/libraryonline/data/local/dao/UserDao.java` | Upsert theo UID, không thay thế parent row |
| `app/src/main/java/com/example/libraryonline/data/remote/firebase/BookCatalogueSyncManager.java` | Chặn callback sau close; đóng DB sau tác vụ đang chạy |
| `app/src/main/java/com/example/libraryonline/data/repository/UserRepository.java` | API upsert hồ sơ local |
| `app/src/main/java/com/example/libraryonline/ui/admin/AdminMainActivity.java` | Gate Admin, dừng listener, profile/logout thật và reset sync UI |
| `app/src/main/java/com/example/libraryonline/ui/auth/AdminLoginActivity.java` | Khôi phục/retry phiên, policy chung, busy/lifecycle, nhớ email |
| `app/src/main/java/com/example/libraryonline/ui/auth/ChangePasswordActivity.java` | Gate bắt buộc, kiểm tra UID, dispatcher Back và xác minh lại |
| `app/src/main/java/com/example/libraryonline/ui/auth/LoginActivity.java` | Khôi phục/retry phiên, phân quyền chung, busy và callback lifecycle |
| `app/src/main/java/com/example/libraryonline/ui/auth/RegisterActivity.java` | Validation/busy chống gửi lặp, đóng băng đầu vào khi gửi |
| `app/src/main/java/com/example/libraryonline/ui/reader/ReaderMainActivity.java` | Gate Reader, lifecycle catalogue, private dialogs/logout |
| `app/src/main/java/com/example/libraryonline/utils/InputValidator.java` | Giới hạn tên/email và ngày sinh hợp lệ |
| `app/src/main/res/layout/activity_admin_login.xml` | Không lưu password vào view state |
| `app/src/main/res/layout/activity_admin_main.xml` | Thông tin phiên và nút đăng xuất hiển thị |
| `app/src/main/res/layout/activity_change_password.xml` | Không lưu password vào view state |
| `app/src/main/res/layout/activity_login.xml` | Không lưu password vào view state |
| `app/src/test/java/com/example/libraryonline/utils/InputValidatorTest.java` | Giữ assertions cũ, dùng Robolectric và thêm boundary tests |
| `firebase.emulators.json` | Thêm Auth emulator loopback để kiểm thử local |
| `tools/firestore-tests/package.json` | Script test Auth emulator riêng, không thêm dependency |
| `tools/firestore-tests/rules.test.cjs` | Negative tests truy cập/role giả/đăng ký giả |
| `app/src/main/java/com/example/libraryonline/data/session/FirebaseSessionGateway.java` | Refresh token/server profile, watch và cache UID |
| `app/src/main/java/com/example/libraryonline/data/session/SessionException.java` | Phân loại lý do từ chối phiên |
| `app/src/main/java/com/example/libraryonline/data/session/SessionGateway.java` | Boundary xác minh phiên có thể kiểm thử |
| `app/src/main/java/com/example/libraryonline/data/session/SessionPolicy.java` | Nguồn quy tắc quyền/UID/hồ sơ chung |
| `app/src/main/java/com/example/libraryonline/data/session/SessionProfile.java` | Kết quả hồ sơ/quyền chuẩn |
| `app/src/main/java/com/example/libraryonline/ui/auth/AuthErrors.java` | Thông báo lỗi theo loại Auth/Firestore |
| `app/src/main/java/com/example/libraryonline/ui/auth/SessionActivity.java` | Gate màn hình, lifecycle, retry/logout, đóng dialog |
| `app/src/main/java/com/example/libraryonline/ui/auth/SessionNavigation.java` | Điều hướng role/password gate và clear task |
| `app/src/main/res/layout/view_session_gate.xml` | UI loading/error/retry/logout trước khi mở nội dung riêng tư |
| `app/src/test/java/com/example/libraryonline/session/AuthErrorsTest.java` | Regression test quyền/cache/phiên/lifecycle |
| `app/src/test/java/com/example/libraryonline/session/CatalogueSyncLifecycleTest.java` | Regression test quyền/cache/phiên/lifecycle |
| `app/src/test/java/com/example/libraryonline/session/LoginLifecycleTest.java` | Regression test quyền/cache/phiên/lifecycle |
| `app/src/test/java/com/example/libraryonline/session/SessionGateTest.java` | Regression test quyền/cache/phiên/lifecycle |
| `app/src/test/java/com/example/libraryonline/session/SessionPolicyTest.java` | Regression test quyền/cache/phiên/lifecycle |
| `app/src/test/java/com/example/libraryonline/session/UserProfileCacheTest.java` | Regression test quyền/cache/phiên/lifecycle |
| `docs/DAY2_REQUIREMENTS.md` | Hỗ trợ kiểm thử/triển khai Ngày 2 |
| `docs/DAY2_VERIFICATION.md` | Hỗ trợ kiểm thử/triển khai Ngày 2 |
| `docs/superpowers/plans/2026-10-21-day2-auth-session.md` | Hỗ trợ kiểm thử/triển khai Ngày 2 |
| `tools/firestore-tests/auth.test.cjs` | Integration Auth emulator với UID/credential/disabled |
