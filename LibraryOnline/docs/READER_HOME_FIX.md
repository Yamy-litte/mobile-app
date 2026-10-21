# Home Reader hiện 0 đầu sách

Người dùng xác nhận auth/OTP vẫn hoạt động, Home hiện 0 đầu sách. Không có log thiết bị hay quyền đọc dữ liệu Firebase thật, nên chưa thể kết luận kho cloud của thiết bị có dữ liệu hay không. Không sửa auth, OTP, EmailSender hay dữ liệu cloud trong lần xử lý này.

## Lỗi đã tái hiện và sửa

- Categories.name trước đây UNIQUE trong SQLite, còn categoryId mới là khóa của Firestore. Một category cloud CAT001 có tên Văn học đụng category mặc định van-hoc và ném SQLiteConstraintException. Transaction lưu catalogue có thể rollback, để cache trống. Test chạy DAO/SQLite qua Robolectric đã tái hiện đúng exception này.
- BookTitleDao.ensureCategory trước đây upsert tên/trạng thái active mỗi khi nhận một đầu sách. Một snapshot chỉ biết categoryId có thể đổi tên thể loại thành ID và bật lại thể loại đã tắt. Test đã tái hiện và được sửa bằng cách giữ metadata category đã biết.
- Lỗi đọc SQLite trên worker không được bắt; truy vấn cache dùng chung hàng đợi với tải ảnh. Tách executor đọc catalogue và hiển thị lỗi thay vì để worker dừng im lặng.
- renderBooks ghi đè thông báo lỗi đồng bộ bằng số kết quả. CatalogueLoadStatus giữ riêng lỗi, trạng thái chờ, snapshot cache và snapshot server. Chỉ snapshot **server** rỗng mới có thể báo Firebase chưa có đầu sách; snapshot offline rỗng không được xem là bằng chứng cloud trống.

## Nâng cấp local cache

SQLite tăng từ version 6 lên 7. Categories bỏ UNIQUE ở **tên hiển thị**, giữ primary key categoryId. Upgrade sao chép toàn bộ category ID, tên, trạng thái, timestamps, thay cấu trúc cache table trong upgrade transaction, kiểm tra PRAGMA foreign_key_check rồi bật lại enforcement khi mở DB. BookTitles và các bảng nghiệp vụ không bị xóa/reset. Không có migration cloud. Không yêu cầu người dùng xóa dữ liệu ứng dụng hay gỡ app.

Test v6→v7 kiểm tra sách cũ còn tồn tại, thêm category ID khác có cùng tên thành công, sách mới đọc lại được, không có FK hỏng và FK enforcement được bật. Node contract test được điều chỉnh để cho phép rebuild cache có kiểm thử bảo toàn dữ liệu, vẫn cấm DROP/DELETE các bảng sách/tài khoản/mượn; không bỏ test hay vô hiệu hóa assertion để che lỗi.

## Tệp thay đổi của riêng lần sửa Home

- app/build.gradle.kts: thêm **testImplementation** Robolectric 4.16.1 để thực thi SQLite/Android API trong JVM; không thêm dependency vào APK/runtime.
- data/local/db/DatabaseHelper.java: schema/cache upgrade v7.
- data/local/dao/CategoryDao.java: đọc category theo ID, bao gồm inactive.
- data/local/dao/BookTitleDao.java: đầu sách không rename/reactivate category.
- data/remote/firebase/BookCatalogueSyncManager.java: đưa lỗi lưu category lên UI; callback riêng cho nguồn snapshot danh mục.
- ui/reader/ReaderMainActivity.java: đọc SQLite bằng executor riêng, bắt lỗi, giữ thông báo lỗi khi render/filter, dừng tải khi không còn user.
- ui/reader/CatalogueLoadStatus.java: phân biệt lỗi local/remote, đang chờ, cache và server.
- app/src/test/java/com/example/libraryonline/data/CatalogueCacheTest.java: 3 test SQLite.
- app/src/test/java/com/example/libraryonline/data/CatalogueLoadStatusTest.java: 3 test trạng thái.
- functions/test/contracts.test.js: bảo vệ dữ liệu nghiệp vụ, tương thích upgrade local cache có kiểm thử.

Các thay đổi Functions/Rules khác trong working tree thuộc tác vụ rà soát trước, không phải thay đổi auth trong lần sửa này. Các sửa đổi được commit/push theo yêu cầu người dùng để kéo về test; chưa deploy Firebase.

## Kiểm tra thực tế

- Trước sửa cache: 3/3 CatalogueCacheTest thất bại đúng các lỗi mô tả.
- Sau sửa: 6/6 test mới đạt.
- Toàn bộ JVM suite chạy 11 test: 10 đạt, 1 thất bại đã có từ trước — InputValidatorTest.emailAndPasswordValidationRejectInvalidInputs, android.util.Patterns.EMAIL_ADDRESS là null trong Android stub. Không tắt/bỏ test này.
- assembleDebug thành công, gồm Java/resource/dex/package.
- Node syntax/lint/contract checks chạy lại; không sửa original functions lockfile hay vendor.
- Chưa chạy trực tiếp trên điện thoại, chưa xác minh số document trong Firebase thật. Không seed thêm sách để làm màn hình có dữ liệu giả.

Trong cloud, activate Java/SDK như trước. Robolectric dùng Java user.home cache riêng vì home thật chỉ đọc (không thay biến shell HOME) và Maven Central có TLS/checksum:

```sh
source /workspace/environment/activate.sh
mkdir -p /workspace/.cache/robolectric
export JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -Duser.home=/workspace/.cache/robolectric -Drobolectric.dependency.repo.url=https://repo.maven.apache.org/maven2"
bash gradlew :app:assembleDebug :app:testDebugUnitTest --no-daemon --max-workers=2
```

Nếu bản cài mới vẫn chưa có sách, đọc dòng trạng thái Home: lỗi PERMISSION_DENIED/UNAVAILABLE cần xử lý theo lỗi thực tế; Firebase server thực sự không có BookTitles thì cần bổ sung/đồng bộ **kho thật** bằng luồng Admin đã có. Bỏ seed trong tác vụ trước làm DB mới không còn sẵn 10 đầu sách demo; đó khác với mất dữ liệu thật.

## Xác nhận tiếp theo từ dữ liệu thực tế

Người dùng tiếp tục thấy 0 sách và cung cấp document Sapiens: `isHidden: true`, `availableCopies: 4`, `categoryId: lich-su`. Với document này, Reader lọc khỏi danh sách là đúng; có bản sẵn sàng không tự động làm đầu sách đã ẩn trở thành công khai. Bản sửa cache trước đó xử lý lỗi riêng đã tái hiện, không phải bằng chứng rằng document này phải hiển thị.

Muốn công khai đầu sách này, Admin chủ động đổi `BookTitles/{id}.isHidden` sang `false` trong Firebase Console. Chỉ đổi đầu sách muốn công khai. Phiên xử lý này không ghi Firebase, không tự bật lại sách, không sửa OTP/auth.

Home giờ báo rõ số đầu sách đang ẩn; trường hợp Firebase trả N document nhưng SQLite đọc 0 dòng có thông báo riêng, thay vì báo đã nhận danh mục chung chung. Số dòng cache được thống kê trước khi lọc sách ẩn, số kết quả tìm kiếm sau khi lọc được giữ riêng.

Kiểm tra thực tế sau cập nhật: 15 test JVM chạy, 14 đạt; 1 lỗi Android stub của InputValidator đã có. CatalogueCacheTest đạt 4/4, CatalogueLoadStatusTest đạt 6/6. Các test mới xác nhận sách ẩn vẫn ẩn sau đồng bộ lặp và vẫn giữ availableCopies=4; trường hợp tìm kiếm không khớp không được báo nhầm là toàn bộ sách bị ẩn. APK debug đã biên dịch trong lần chạy này; build độc lập được chạy trước khi commit/push.

## Sửa tải ảnh bìa trên Home Reader

Người dùng xác nhận đầu sách đã hiển thị nhưng ảnh chưa tải; URL coverUrl mở được trên trình duyệt. Phần tải ảnh cũ dùng HttpURLConnection với timeout 5 giây, decode trực tiếp không cache, tải lại URL trong từng thẻ mỗi lần render, bỏ qua exception và ẩn ảnh thay thế ngay khi bắt đầu. Đây là những điểm có thể gây ảnh trắng/tải chậm và không có thông tin lỗi.

Thay bằng BookCoverLoader dùng Glide 4.16.0: timeout 20 giây; cache bộ nhớ/đĩa và gộp request cùng tài nguyên do Glide quản lý; request theo Activity/view; giữ ảnh thay thế trong lúc tải và khi lỗi; xóa request/ảnh cũ khi bind lại URL. Thêm ACCESS_NETWORK_STATE (quyền normal) để loader theo dõi việc mạng kết nối lại. Không nới TLS, không cho phép cleartext trong app, không đổi coverUrl, auth/OTP hay dữ liệu Firebase. Glide là dependency runtime mới nhằm thay cơ chế tải/cache/vòng đời tự viết; version ghim cố định, không sửa dependency/lockfile Functions.

Các tệp sửa: app/build.gradle.kts, app/src/main/AndroidManifest.xml, ReaderMainActivity.java, BookCoverLoader.java mới, BookCoverLoaderTest.java mới và tài liệu này. Xóa downloader/imageExecutor cũ khỏi Activity.

Đã thực thi 4/4 BookCoverLoaderTest qua Robolectric: ảnh thật được decode/hiển thị; file ảnh lỗi giữ placeholder; URL rỗng không giữ ảnh sách cũ; HTTP server loopback trả PNG thật và bind lại URL không tạo request mạng thứ hai. Không mock kết quả bitmap hay đánh dấu test thành công khi chưa chạy. Toàn suite hiện 19 test, 18 đạt; lỗi duy nhất vẫn là Android stub InputValidator đã ghi nhận. APK debug được build lại.

Máy cloud bị proxy policy chặn covers.openlibrary.org (CONNECT 403), nên không coi đó là HTTP lỗi của máy chủ ảnh và không tuyên bố đã kiểm thử URL ngoài Internet trên điện thoại người dùng. Xác nhận URL truy cập được đến từ người dùng; test HTTP/cache/UI trong máy cloud dùng server loopback. Nếu thiết bị vẫn gặp lỗi, Logcat tag BookCoverLoader ghi loại lỗi/HTTP status mà không ghi query token của URL. Không deploy Firebase.
