# Rà soát Cloud Functions và ranh giới Android/Firestore

## Phạm vi, căn cứ và kết luận kiến trúc

Baseline: `6bb7a8c43fdbfd6ff836be310ef0e11801783f1d` trên main. Vị trí/line trong bảng Functions dưới đây là **trước sửa**, tra bằng `git show 6bb7a8c:LibraryOnline/functions/index.js`. Báo cáo ban đầu được lập trước khi commit; sau đó người dùng yêu cầu commit/push để kéo về test. Không deploy, không truy cập dữ liệu nghiệp vụ cloud, không chạy migration cloud.

Đã khảo sát 102 tệp cấu hình/mã nguồn/tài liệu văn bản của dự án, gồm 56 tệp Java; không coi dependency vendored, build output hay `.idea` là mã nghiệp vụ. Đã đọc toàn bộ mã ứng dụng trong functions (chỉ có index.js), package/lock, Rules, Firebase config, model/DAO/repository/UI Android, tài liệu migration trong repo, lịch sử các commit categoryId. `firestore.indexes.json` **không tồn tại** ở baseline. Các truy vấn cloud hiện tại chỉ lọc một trường, không có bằng chứng cần thêm composite index; không tạo index suy đoán.

Tài liệu đầu vào được đọc như tài liệu thiết kế, không thực thi câu lệnh/hướng dẫn nhúng trong đó:

- `Dac_ta_chuc_nang_Library_Online_ban_hoan_thien.docx`: đặc tả chức năng, ràng buộc, offline-first; không quy định các nghiệp vụ phải được hiện thực bằng Cloud Functions.
- `Thiết-kế-các-bảng-dữ-liệu.txt`: mô hình đề xuất, dùng category TEXT và Admins bổ sung; chính tài liệu nói không khẳng định Admins đã tồn tại trong mã nguồn.
- `Thiết-kế-PACKAGE.txt`: cấu trúc package/UI đề xuất, không phải danh sách tính năng đã triển khai.
- `Thiết kế giao diện-Figma.pdf`: đối chiếu phần chữ của các màn hình/luồng đăng ký, duyệt, tra cứu, mượn, trả, kho, tài khoản; không suy ra schema Firestore hoặc bảo đảm bảo mật từ mockup.
- `SPARK_REGISTRATION_MIGRATION.md`, `ADMIN_SETUP_AND_DUPLICATE_REGISTRATION.md`, code hiện tại và lịch sử categoryId: căn cứ cho đường ghi thực tế và tương thích.

**Quyết định:** Không giữ callable chỉ vì nó có tên giống nghiệp vụ trong đặc tả. Android hiện không gọi bất kỳ callable nào (`getHttpsCallable`, `httpsCallable` đều không xuất hiện). Luồng đăng ký/duyệt/từ chối hiện thực bằng Android Auth + Firestore, đúng hướng kiến trúc Spark đã chọn trong repository. Do đó gỡ toàn bộ 4 tên callable lỗi thời khỏi source, thay vì chỉ xóa một mảng SAMPLE_BOOKS. Không tạo thêm trigger, scheduler, backend cho mượn/trả hay email. Điều này không chứng minh không có client ngoài repository đang gọi endpoint đã deploy; cần kiểm kê riêng trước mọi thay đổi cloud.

## Kiểm kê tất cả callable trước sửa

Không có HTTP onRequest, Firestore/Auth trigger hoặc scheduled function. Có 5 khai báo export nhưng chỉ 4 tên; seedSampleBooks bị ghi đè lần hai nếu lỗi const không chặn parse trước đó.

| Tên / loại / vị trí baseline | Trigger hoặc nơi gọi | Mục đích được chứng minh | Đọc / ghi | Quyền, xác thực, đầu vào cũ | Quan hệ Android / function khác | Quyết định |
|---|---|---|---|---|---|---|
| submitRegistration / onCall / index.js:63 | Client callable; không có caller Android hiện tại | Gửi hồ sơ Pending | R Users.email/phone; R RegistrationRequests.email/phone/status. W RegistrationRequests: fullName,birthDate,email,phone,termsAccepted,termsAcceptedAt,status,emailVerified,phoneVerified,rejectionReason,createdAt | Cho phép chưa đăng nhập; kiểm tra tên 2–120, regex ngày/email/phone, đồng ý điều khoản. Ngày chỉ regex, ép String nhận kiểu không mong muốn; phone không cùng normalization với Android. Query-then-write không khóa unique | Android RegisterActivity tạo request + hai index bằng batch; callable không tạo index, emailVerified=false khác Android true | **Loại bỏ** đường ghi cũ; giữ đường Android, không chuyển kiến trúc |
| approveRegistration / onCall / index.js:125 | Không có caller Android | Duyệt thẻ: Auth account, Users, quyết định | R Admins/{uid}.adminStatus; R request/status/profile; R Users.email/phone; W Auth; W Users.userId,fullName,dateOfBirth,email,phone,barcode,role,cardStatus,createdAt,updatedAt; W request.status,reviewedBy,reviewedAt,userId,barcode | requireAdmin Active/HoatDong. requestId chỉ String/trim, không chặn slash. Pending/PendingVerification đọc ngoài transaction; không email/password delivery, thiếu mustChangePassword; barcode chỉ 8 ký tự UID không bảo đảm unique | Android createApprovedAccount dùng secondary Auth, mật khẩu tạm ngẫu nhiên, Users + decision batch, mustChangePassword, email. Backend Admin SDK bỏ qua Rules nên có thể đua với Android rejection | **Loại bỏ** đường song song; Rules bổ sung kiểm tra profile khớp khi batch duyệt; không tự chọn mô hình UID/barcode mới |
| rejectRegistration / onCall / index.js:202 | Không có caller Android | Từ chối và gửi email | R Admins/status, request/status/fullName/email; W request.status,rejectionReason,reviewedBy,reviewedAt; SMTP email | Admin; requestId trim, reason 3–500. Read-check-update không transaction: có thể ghi đè quyết định vừa duyệt; email nằm ngoài write, không outbox, retry sau write không gửi lại | Android showRejectDialog đã update theo Rules rồi EmailSender.send. Gọi sendRejectionEmail; SMTP_USER/PASS secrets + SMTP_HOST/PORT/FROM không liên quan BuildConfig SMTP của Android | **Loại bỏ** code và .env.example hết sử dụng; không tạo mail trigger vì sẽ gửi trùng với Android |
| seedSampleBooks / onCall / index.js:378 và :539 | Không có caller Android | Chỉ seed 10 đầu sách demo; không có nghiệp vụ production yêu cầu seed | R Admins/status. W BookTitles/BOOK001..BOOK010 title,author,isbn,**category tên**,publisher,publishYear,description,coverUrl,totalCopies,availableCopies,isHidden,createdAt,updatedAt | Admin nhưng Admin SDK vượt Rules; merge vẫn ghi đè trường đã có. Không tạo Categories, không tạo BookCopies, số bản mượn không có LoanDetails. Retry đặt lại counters/timestamps | Trùng dữ liệu demo SQLite DatabaseHelper. Lặp seed có thể làm sai tồn kho thật | **Loại bỏ cả hai khai báo và cả hai mảng**; không xóa bất kỳ document nào đã tồn tại |

### Helper nội bộ

| Helper baseline | Vai trò / dữ liệu / quyền | Quyết định |
|---|---|---|
| requireAdmin, index.js:14 | Đọc Admins/{request.auth.uid}; báo unauthenticated/permission-denied; không kiểm tra Users.role như mô hình đề xuất | Gỡ cùng callable; giữ nguyên chính sách Admin đang dùng trong Android/Rules, chưa migration profile |
| normalizeEmail, index.js:25 | Ép String, trim/lowercase, được submit/approve/reject dùng | Gỡ helper backend không còn caller; giữ InputValidator Android |
| sendRejectionEmail, index.js:29 | Đọc SMTP config/secret, gửi nodemailer; không có quyền riêng, phụ thuộc caller reject đã authorize. Thiếu host/from trả false, lỗi SMTP chỉ log | Gỡ cùng callable và config mẫu. Không copy thông tin bí mật vào source/test |

Sau sửa `functions/index.js` export object rỗng, không import SDK, không initializeApp, không có quyền ghi hay side effect khi load. ESLint no-redeclare/no-dupe-args/no-dupe-keys cùng syntax/import test bảo vệ khai báo/export. Không còn SAMPLE_BOOKS trong production source.

## Collection và nguồn chuẩn

| Dữ liệu | Nguồn chuẩn và đường dùng thực tế | Quan hệ/điểm tương thích |
|---|---|---|
| Categories | Firestore; CloudLibraryRepository.saveCategory; BookCatalogueSyncManager; CategoryDao cache | categoryId ổn định; name để hiển thị; isActive phải được bảo toàn khi sync |
| BookTitles | Firestore cho catalogue đã đồng bộ; BookRepository → uploader; snapshot → SQLite | categoryId tham chiếu Categories; title ID là document ID; không ghi category tên vào Firestore. SQLite/model giữ category tên để tìm kiếm/hiển thị. Legacy cloud category được đọc tương thích và chuyển trong thao tác sync Admin hiện có |
| BookCopies | Firestore, BookCopyRepository → uploader; SQLite cache | bookTitleId → BookTitles; barcode/parent bất biến; status SanSang/ChoLay/DaMuon/DaDatTruoc/DangSuaChua/ThanhLy/DaMat; priorityUserId phải trỏ Reader khi được gán |
| Admins | Firestore Admins/{Auth UID}; AdminLoginActivity/LoginActivity/Rules | Active/HoatDong được mã hiện tại chấp nhận; Admins.userId và Users.role trong thiết kế mới chưa phải mô hình runtime được migration |
| Users | Firestore hồ sơ sau duyệt; Auth là nguồn danh tính/mật khẩu | Reader tự đọc hồ sơ; Admin đọc danh sách. Chỉ tự cập nhật mustChangePassword true→false và updatedAt; không tự nâng role/cardStatus |
| RegistrationRequests | RegisterActivity batch; AdminMainActivity review | birthDate ở request → dateOfBirth trong Users; Pending/PendingVerification legacy → Approved/Rejected; reviewedBy là Admin Auth UID hiện dùng |
| RegistrationEmailIndex / RegistrationPhoneIndex | Hai index create-only trong cùng batch request | Không giải phóng khi reject; không tự backfill yêu cầu cũ; tránh request đồng thời trùng email/phone đã chuẩn hóa |
| PasswordResetRequests | ForgotPasswordActivity gửi, AdminMainActivity review trực tiếp | Đường này không dùng callable. Truy vấn Users trước đăng nhập hiện bị Rules từ chối: lỗi luồng đã có, không sửa bằng cách công khai hồ sơ Reader |
| LoanReceipts, LoanDetails, Reservations, Penalties, PenaltyPayments, SystemConfigs, Notifications | Model/DAO/repository SQLite | Chưa có đường gọi cloud tương ứng trong source thực tế. Catch-all Rules tiếp tục deny. Không tạo scheduler 24h/48h, FCM, tính phạt hay cloud trigger chỉ từ tên bảng |

Các fallback ảnh bìa/chữ hiển thị, đọc legacy category, default SystemConfigs (5 sách/14 ngày/24h/48h/5.000đ/2×) có căn cứ UI/đặc tả, được giữ. Chúng khác với sample catalogue BOOK001..BOOK010. Không xóa bản ghi kho thật vì ID trông giống sample.

## Những sửa đổi có căn cứ

1. Gỡ callable cũ, seed và SMTP backend chết; bỏ functions codebase trong firebase.json. Không có deployment nên không xóa endpoint cloud đã deploy.
2. Bỏ chèn demo inventory trong cả onCreate/onUpgrade SQLite. Không tăng DB version để ép migration, không DROP/DELETE, không xóa records cũ. Giữ đồng bộ inventory thật của Android; đổi tên method addSampleCatalogueSyncButton thành addCatalogueSyncButton vì method đó thực sự đồng bộ dữ liệu kho.
3. Sync tạo Categories thiếu **trước** migrate/upload BookTitles và BookCopies. Không tạo bộ thể loại mặc định không có căn cứ; không rename/reactivate category tồn tại. Nếu cloud thiếu cả ID/tên, báo lỗi thay vì bịa “Khác”. Category snapshot cập nhật cache hiển thị rồi resolve lại titles, không ghi cloud từ listener.
4. Các create-missing inventory dùng transaction và đọc lại trước ghi, giữ document do client khác vừa tạo. Chia nhóm 5 để không vượt giới hạn Rules đọc parent/priority. Legacy category migration dùng transaction nhóm 10 để không xóa field từ snapshot đã lỗi thời. Retry không ghi đè inventory tồn tại; không có trigger tự lặp.
5. Rules không cho BookTitles ghi tên category; kiểm tra category parent, kiểu các trường/counters. BookCopies cần parent thực, barcode/parent không được đổi, priority có Reader, chặn ẩn/thanh lý/sửa chữa khi đang mượn/giữ. Vẫn cho xử lý DaMat vì đặc tả cho phép báo mất sách đang mượn. Không đổi tên status hay collection.
6. Duyệt đăng ký phải thấy Users profile khớp tên/ngày sinh/email/phone/barcode/role/cardStatus trong kết quả atomic; không thể chỉ mark Approved mà thiếu profile. Quyết định đã hoàn tất không được review lại, giữ cơ chế one-way hiện có.
7. Thêm lint, syntax/import/contract tests; thêm test Rules độc lập với project thật, chạy demo project/emulator. Không thêm nghiệp vụ backend chưa được đặc tả.

## Kiểm thử và bằng chứng

Node dùng **20** theo functions/package.json khi chạy check/lint/test (host mặc định là Node 24). Trước sửa: import index.js lỗi SyntaxError SAMPLE_BOOKS lần hai; contract tests 2 đạt/3 lỗi đúng ở import, codebase cấu hình cũ, seed SQLite. Sau sửa: npm run check, npm run lint và npm test (5/5) đạt. ESLint cũng chạy trực tiếp lên test Rules; không có symbol/export trùng.

Lệnh tái chạy từ LibraryOnline:

```sh
npm --prefix functions run check
npm --prefix functions run lint
npm --prefix functions test
npm --prefix tools/firestore-tests ci
npx --yes --package=firebase-tools@14.22.0 firebase emulators:exec \
  --config firebase.emulators.json --project demo-library-online --only firestore \
  'npm --prefix tools/firestore-tests test'
bash gradlew :app:assembleDebug :app:testDebugUnitTest --no-daemon --max-workers=2
```

Máy cloud cần `source /workspace/environment/activate.sh` trước Gradle. Khi home chỉ đọc, đặt `export npm_config_cache=/workspace/.cache/npm` trước các lệnh npm/npx (bao gồm tiến trình npx con của npm run lint); lần chạy lint với cache mặc định đã lỗi quyền ghi, chạy lại với cache này đã đạt. `npm ci` chỉ chạy ở **tools/firestore-tests**, không ở functions có node_modules được track. Lint dùng ESLint 9.39.1 ghim qua npx, không sửa dependency runtime/lock cũ. Test Rules có package/lock riêng, chỉ devDependencies Firebase client và rules-unit-testing; không tác động APK/runtime cloud. Không vô hiệu hóa TLS/checksum.

### Trạng thái Rules emulator: BỊ CHẶN, chưa được xác minh

Đã thực sự chạy Firebase CLI 14.22.0 với config test/demo project. CLI dừng **trước khi chạy test** vì download cloud-firestore-emulator-v1.19.8.jar tại storage.googleapis.com bị HTTP 403 “Domain forbidden”. Không có 9/9 test đạt; **0 test Rules đã chạy**, và compilation Rules bằng emulator chưa được xác minh. Source test được syntax-check và lint thành công, không thay thế engine Rules thật.

Đã cập nhật start_skill trong draft để bỏ ghi chú lỗi SAMPLE_BOOKS đã lỗi thời và ghi đúng cách chạy các kiểm tra mới; install_script giữ nguyên. Đã lưu bổ sung duy nhất storage.googleapis.com vào custom allowlist draft (trước đó rỗng, giữ presets), không publish. Thử lại URL sau khi lưu vẫn bị CONNECT 403; saved draft chưa áp dụng runtime. Cần cho phép domain trong Environment settings, áp dụng cấu hình rồi chạy lại đúng lệnh. Không nới Rules để che lỗi, không thử trên production thay emulator.

9 test Rules đã chuẩn bị: anonymous read denied; Reader tự đọc, không đọc hồ sơ người khác; Reader/Inactive Admin không ghi kho/nâng quyền; Active Admin batch tạo category/title/copy; reject category tên/dangling parent/counter sai; copy identity bất biến/held copy không thanh lý; priority phải là Reader thật; public registration + indices/replay; profile approval atomic + final review; reject reason/admin/replay (các trường hợp được nhóm trong 9 test).

### Android

Build APK debug đã chạy thành công sau các sửa schema/sync; không chỉ syntax-check Java. JVM suite chạy đủ 5 test: 4 đạt, 1 lỗi `InputValidatorTest.emailAndPasswordValidationRejectInvalidInputs`, stack vào InputValidator.java:10 vì `android.util.Patterns.EMAIL_ADDRESS == null` trong JVM Android stub. Đây là lỗi đã tái hiện ở baseline onboarding, không phải Cloud Functions. Không sửa/bỏ qua test và không đổi assertion. Xác nhận thay thế đã làm: biên dịch Java/resource/dex/package qua assembleDebug. Xác nhận email validation runtime đầy đủ vẫn cần Android instrumentation hoặc cấu hình Robolectric phù hợp; không khẳng định build thay thế được kiểm thử hành vi.

Chưa có thiết bị/emulator Android để chạy instrumentation; chưa kiểm thử Auth creation, email delivery, OTP, offline queue đa thiết bị, FCM hay dữ liệu cloud thật. Unit tests Java không chứng minh các luồng đó hoạt động.

## Điểm còn tồn tại / cần quyết định trước production

| Vấn đề có bằng chứng | Rủi ro / việc tiếp theo, không tự ý thay kiến trúc |
|---|---|
| Rules engine chưa chạy do network | Chưa đủ cơ sở xác nhận bộ Rules mới production-ready; mở domain và chạy 9 test trước mọi deployment |
| RegisterActivity tạo/so OTP trên thiết bị; emailVerified=true do client tự khai | Không phải proof xác minh mailbox. Không dùng field này để cấp quyền. Thiết kế trusted verification/rate limit cần quyết định backend riêng; không tự thêm callable/trigger gửi email |
| EmailSender ghi rõ demo-only, lấy SMTP từ BuildConfig; approval gửi mật khẩu tạm qua email | Credential có thể bị trích từ APK nếu build có cấu hình. Không có secret được thêm/hardcode. Cần kế hoạch server mail/Auth verification thay cho client SMTP trước production |
| Public request và PasswordResetRequests chưa rate-limit/App Check enforced | Có thể spam dù shape/index đúng. Cần kiến trúc chống abuse được phê duyệt; không “fix” bằng cấm người chưa đăng nhập vì đặc tả cho phép họ đăng ký |
| ForgotPasswordActivity query Users trước login trong khi Rules cấm | Không mở read Users công khai. Cần chọn Firebase password-reset flow an toàn hoặc backend xác minh có thiết kế; UI đang có luồng admin-mediated khác với spec |
| Admins/{uid} hiện đủ quyền; thiết kế mới còn yêu cầu Users.role=Admin và Admins.userId | Cần kiểm kê/migrate hồ sơ bootstrap có phê duyệt. Siết role ngay sẽ khóa Admin đang dùng; không sửa dữ liệu hay tự đổi khóa ngoại |
| barcode “TV-” + 8 ký tự UID trong Android | Không bảo đảm unique tuyệt đối như schema đề xuất. Cần kế hoạch mã thẻ/unique index tương thích thẻ đã phát; không tự đổi barcode và invalid thẻ thật |
| Auth create + Firestore batch + email là ba hệ thống riêng | Compensation delete Auth có thể lỗi, email có thể lỗi sau commit; không có outbox/retry bền vững. Rules bảo vệ final review nhưng không làm Auth/SMTP trở thành transaction. Không khẳng định exactly-once end-to-end |
| totalCopies/availableCopies là counters do Admin ghi; chưa có luồng mượn/trả cloud đầy đủ | Rules chỉ kiểm tra bounds, không tổng hợp được toàn bộ BookCopies. Không tự tạo trigger đếm lại vì sẽ xung đột nguồn ghi và chưa có quy tắc cụ thể đếm ThanhLy/DaMat. Cần transaction nghiệp vụ thực tế khi triển khai |
| Dữ liệu mẫu đã tồn tại và legacy yêu cầu thiếu unique indices | Chưa thể phân biệt records demo đã chỉnh sửa với dữ liệu thật. Không xóa, không backfill cloud tự động. Cần export/đối soát riêng trước manual sync; bản sửa chỉ chặn phát sinh mẫu mới |
| Legacy category migration vẫn chỉ chạy khi Admin bấm sync đã có trong UI | Không chạy migration ở phiên rà soát. Cần backup/kiểm thử dữ liệu legacy với emulator trước dùng cloud; thiếu tên/parent fail đóng, không suy đoán tên |
| Offline-first yêu cầu đồng bộ mượn/trả/đặt trước/thu phạt; hiện nhiều repository chỉ SQLite | Phần đặc tả chưa triển khai; không được coi đã hoàn tất chỉ vì Functions sạch. Không mở Rules/viết backend bổ sung ngoài yêu cầu sửa luồng hiện có |
| Dependency vendor được track, runtime dependencies Functions không còn dùng | Giữ nguyên firebase-admin/firebase-functions/nodemailer và lock cũ để tránh xóa hàng nghìn tệp ngoài phạm vi. Không import chúng vào entry point. Dọn dependency/vendor cần thay đổi riêng; không cài đè functions/node_modules |
| Thiết kế đề xuất và thực tế khác category/date/Admin IDs | Dùng Firestore categoryId hiện hành, giữ category hiển thị SQLite; giữ birthDate↔dateOfBirth và reviewedBy UID. Không âm thầm chuyển schema theo tài liệu cũ |

## Tệp sửa và commit

Các thay đổi được commit/push theo yêu cầu người dùng để kéo về test. Baseline ở đầu báo cáo; dùng git log để xem SHA và diff của commit.

| Tệp | Lý do |
|---|---|
| functions/index.js | Gỡ cả bốn endpoint cũ, seed trùng và helpers không còn dùng; entry point không side effect |
| functions/package.json | Check/lint/test scripts; bỏ serve/deploy script không còn codebase |
| functions/.env.example (xóa) | SMTP backend đã nghỉ, tránh nhầm với Android config |
| functions/eslint.config.cjs (mới) | Kiểm tra biến, symbol, key và code lỗi; Node CommonJS globals đúng |
| functions/test/contracts.test.js (mới) | Import/export, cấu hình backend nghỉ, caller Android, seed SQLite, serializer categoryId |
| firebase.json | Gỡ Functions codebase chết; giữ cấu hình Rules/project |
| firebase.emulators.json (mới) | Firestore test-only localhost; không trỏ production |
| firestore.rules | Parent/schema, identity/priority/copy state, approval profile consistency |
| app/.../data/local/db/DatabaseHelper.java | Không tự chèn mẫu, không đụng records đã có |
| app/.../data/remote/firebase/BookCatalogueSyncManager.java | Parent-first category sync, bảo toàn category cloud, đọc label, transaction create-missing/migrate, nhóm nhỏ Rules |
| app/.../ui/admin/AdminMainActivity.java | Tên method sync đúng ý nghĩa, giữ nút sync kho thật |
| SPARK_REGISTRATION_MIGRATION.md | Mô tả đúng đường chạy hiện tại và caveat, bỏ chỉ dẫn deploy lỗi thời |
| tools/firestore-tests/package.json, package-lock.json, .gitignore, rules.test.cjs (mới) | Dev-only test harness/lock riêng, chặn test khi thiếu emulator host |
| docs/CLOUD_FUNCTIONS_AUDIT.md (mới) | Kiểm kê, bằng chứng, quyết định, kiểm thử, giới hạn và hành động còn lại |

Không sửa original functions/package-lock.json, dependency declarations runtime, tracked functions/node_modules, google-services.json, .firebaserc, tên collection/field/status, test Android hiện hữu. Không deploy Firebase, không xóa dữ liệu cloud và không chạy migration cloud. Chưa thể kết luận toàn bộ Library Online đúng đặc tả hay sẵn sàng production trong khi những giới hạn trên còn tồn tại.

## Cập nhật sau phản hồi Home Reader

Lần sửa tiếp theo đã tái hiện và xử lý lỗi UNIQUE Categories.name trong SQLite, nâng local cache v6 lên v7 có kiểm thử bảo toàn dữ liệu và thêm trạng thái tải rõ ràng. Thông tin không tăng version trong phần rà soát ban đầu ở trên là lịch sử của bước đó; xem [READER_HOME_FIX.md](READER_HOME_FIX.md) cho mã hiện tại, 6 test mới và kết quả suite 10/11. Auth/OTP không thay đổi, không seed hay migration cloud.
