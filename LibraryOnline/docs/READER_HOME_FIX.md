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
