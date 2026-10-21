# Luồng 2 — Tra cứu và quản lý kho

## Phạm vi

- Reader: mở màn hình chi tiết Java/XML từ danh sách, ảnh bìa qua Glide, metadata, mô tả, số khả dụng, trạng thái không công khai/lỗi/cache; lọc bằng categoryId thật.
- Admin: điểm vào Quản lý kho từ Home; danh sách/tìm kiếm, thêm đầu sách và 1–5 bản mỗi đợt, sửa metadata, nhập bổ sung, sửa kệ, ẩn đầu sách/cuốn và thanh lý cuốn SanSang. Barcode Code 128 có xem và in/lưu PDF.
- Firestore là nguồn chuẩn cho ghi kho; SQLite giữ cache. Không dùng Cloud Functions; không đổi schema/collection/trạng thái và không migration dữ liệu cloud.
- Tổng bản đã nhập giữ cả bản thanh lý. availableCopies giảm đúng một lần; giữ ID, barcode và lịch sử.
- Không tự tạo BookCopies để bù số lượng cũ. Khi đầu sách và các bản vật lý không khớp, thao tác báo cần đối soát.

## Những lựa chọn khi triển khai

1. Làm trực tiếp trên branch `work`; người dùng đã yêu cầu commit và push các bản cập nhật lên repo sau khi kiểm thử.
2. Giới hạn 5 cuốn mỗi lần nhập để giao dịch nhỏ phù hợp Rules; không chia âm thầm thành các đợt thành công một phần.
3. ID bản sách gắn UUID của thao tác; barcode có số thứ tự riêng. Tránh coi hai Admin cùng chọn một dải barcode là cùng một lần nhập.
4. Mỗi thay đổi kho đọc lại đầu sách và bản sách trong transaction. Các công cụ ngoài ứng dụng vẫn phải tuân thủ cùng giao thức; Rules không thể tự tính tổng tùy ý của toàn collection.
5. Đường đồng bộ cũ trên Admin Home chỉ tải về; không tiếp tục upload đầu sách và cuốn riêng rẽ qua UI, tránh bỏ qua transaction kho. API cũ không bị xóa tùy tiện.
6. Thêm duy nhất `com.google.zxing:core:3.5.3` để mã hóa Code 128. Không thêm camera/thư viện quét hoặc thay lockfile; mã tạo ra được giải mã lại trong test.
7. Lưu mã thao tác đang chờ vào SharedPreferences riêng theo UID trước khi ghi cloud. Không lưu mật khẩu/token/quyền quản trị; UID chỉ phân tách dữ liệu retry, Rules vẫn quyết định quyền. Commit local nhỏ là điều kiện để gửi mutation.
8. Khi lần nhập bị từ chối chắc chắn, đối chiếu lại các ID đã cấp trước khi bỏ trạng thái chờ. Nếu chưa có bản nào, transaction cập nhật duy nhất `Categories.updatedAt` để làm hết hiệu lực transaction nhập cũ còn đang chờ; không sửa bộ đếm/trạng thái hoặc tạo dữ liệu. Nếu chưa thể xác nhận do mạng/quyền, giữ mã cũ để thử lại. Chi phí: một lần ghi timestamp khi phục hồi lần nhập bị từ chối.
9. Khóa thực thi theo UID trong tiến trình, tránh hai màn hình cùng gửi/đối chiếu một thao tác. Không coi UID local là nguồn cấp quyền.
10. Không hỗ trợ khôi phục sách ẩn/sửa chữa, báo mất hoặc quét camera ở đợt này, đúng thiết kế đã duyệt. Nhập thêm qua tìm kiếm đầu sách.

## Cập nhật Firebase

**Có thay đổi `firestore.rules`; người dùng cần cập nhật Rules khi sử dụng APK này.** Bổ sung bảo vệ chuyển trạng thái sửa chữa/thanh lý, giữ lịch sử và yêu cầu giảm bộ đếm cùng giao dịch. Không mở quyền collection khác.

**Chưa deploy Firebase, chưa chạy migration, chưa ghi/xóa dữ liệu cloud thật.** Kiểm thử sử dụng `demo-library-online` trên loopback.

## Kiểm thử tái hiện

Từ thư mục `LibraryOnline`, với JDK/Android SDK và dependency đã cài:

```bash
bash gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
firebase emulators:exec --config firebase.emulators.json --project demo-library-online --only firestore 'npm --prefix tools/firestore-tests test'
firebase emulators:exec --config firebase.emulators.json --project demo-library-online --only firestore,auth 'bash tools/firestore-tests/run-android-inventory.sh'
```

`InventoryEmulatorTest` được chạy riêng với cờ `FLOW2_ANDROID_EMULATOR_TEST`; khi không có emulator, các ca đó được đánh dấu skipped để không gọi cloud. Lượt chạy emulator riêng phải có 0 skipped. Đây là SDK Android thật trong Robolectric gọi Auth/Firestore emulator, không chỉ bản mô phỏng JavaScript của thuật toán.

Đã chạy Rules emulator **15/15** và Android SDK emulator **6/6**, không bỏ qua ca nào trong lượt emulator. Các ca Android gồm tạo/nhập lại, nhập đồng thời 5 bản, giữ bộ đếm khi sửa metadata, ẩn/thanh lý lặp, khôi phục mã thao tác sau mất allocation, thể loại ngừng hoạt động và Rules từ chối schema legacy mà không khóa các thao tác khác.

Nhật ký đầy đủ trong workspace `/workspace/.cache/flow2/`, gồm các lượt RED, lỗi trung gian, bản sửa và kết quả cuối. Lượt rà soát độc lập phát hiện hai vấn đề quan trọng về retry/journal; đã sửa và kiểm chứng bằng các ca emulator nêu trên. Không còn finding review nào được hoãn lại.

Các lỗi trung gian đã xử lý: PdfDocument không AutoCloseable; API Java 24 khi minSdk 23; bộ nhớ SDK Auth test giữ listener của FirebaseApp đã xóa (fixture dùng API key emulator riêng); đọc lặp document chưa tồn tại trong transaction gây sai phiên bản; thông báo không tìm thấy sách chưa biến mất khi cache cập nhật. Không giảm mức kiểm tra hay vô hiệu hóa test để xử lý lỗi.

## Kết quả cuối

- `:app:testDebugUnitTest`: **76 ca chạy thành công**, 0 thất bại/lỗi. Có 6 ca emulator opt-in skipped ở lượt thường; cả **6/6** đã chạy riêng với emulator và đều thành công, 0 skipped.
- Firestore Rules emulator: **15/15 thành công**.
- `:app:assembleDebug`: thành công. APK: `app/build/outputs/apk/debug/app-debug.apk`.
- `:app:lintDebug`: **0 lỗi, 244 cảnh báo** (nền trước thay đổi: 204). Không tắt kiểm tra; cảnh báo gồm i18n, layout/style và API/dependency deprecation cần dọn tiếp.
- `git diff --check`: thành công.
- Render/kiểm tra Reader detail và Admin inventory ở 320dp, cùng theme/palette hiện có; Code 128 được giải mã lại đúng barcode gốc.
- Không đổi lockfile, phiên bản SQLite hoặc field/collection. Không sử dụng Cloud Functions. Thay đổi được đưa lên repo theo yêu cầu người dùng; chưa deploy Firebase.

## Giới hạn kiểm thử

- Chưa thao tác trực tiếp trên điện thoại/Android Emulator UI, chưa có Logcat từ thiết bị hoặc in/scan nhãn vật lý.
- Có ảnh render Robolectric 320dp để kiểm tra bố cục/palette. Ảnh trong kiểm thử dùng fixture riêng, không có dữ liệu mẫu production.
- Giữ nguyên kiểm tra phiên cần server của Luồng 1; không tuyên bố hoàn thiện offline-first toàn bộ ứng dụng.
- Không kiểm chứng trạng thái Rules đã deploy hoặc dữ liệu kho thật trong Firebase Console. Kho cũ thiếu bản vật lý/có schema legacy cần đối soát riêng, không tự sửa dữ liệu thật.

## Danh sách file thay đổi

Các thay đổi bên dưới được đóng gói trong commit Luồng 2 trên nền `9e45136`; xem lịch sử Git để lấy mã commit.

| File | Lý do |
|---|---|
| `app/build.gradle.kts` | Thêm ZXing Core 3.5.3 cho Code 128; giữ các dependency khác. |
| `app/src/main/AndroidManifest.xml` | Đăng ký hai Activity riêng tư, không exported. |
| `app/src/main/java/com/example/libraryonline/data/inventory/InventoryChanges.java` | Quy tắc kho, identity nhập và transaction Firestore có kiểm tra dữ liệu. |
| `app/src/main/java/com/example/libraryonline/data/inventory/InventoryPolicy.java` | Quy tắc kho, identity nhập và transaction Firestore có kiểm tra dữ liệu. |
| `app/src/main/java/com/example/libraryonline/data/inventory/InventoryRepository.java` | Quy tắc kho, identity nhập và transaction Firestore có kiểm tra dữ liệu. |
| `app/src/main/java/com/example/libraryonline/data/inventory/StockIntake.java` | Quy tắc kho, identity nhập và transaction Firestore có kiểm tra dữ liệu. |
| `app/src/main/java/com/example/libraryonline/ui/admin/AdminMainActivity.java` | Nối quản lý kho, đổi đồng bộ UI sang tải về để tránh ghi riêng lẻ. |
| `app/src/main/java/com/example/libraryonline/ui/admin/BarcodeLabels.java` | Giao diện/form kho, vòng đời thao tác, journal retry hoặc nhãn mã vạch. |
| `app/src/main/java/com/example/libraryonline/ui/admin/InventoryActivity.java` | Giao diện/form kho, vòng đời thao tác, journal retry hoặc nhãn mã vạch. |
| `app/src/main/java/com/example/libraryonline/ui/admin/InventoryForm.java` | Giao diện/form kho, vòng đời thao tác, journal retry hoặc nhãn mã vạch. |
| `app/src/main/java/com/example/libraryonline/ui/admin/InventoryViewModel.java` | Giao diện/form kho, vòng đời thao tác, journal retry hoặc nhãn mã vạch. |
| `app/src/main/java/com/example/libraryonline/ui/admin/PendingInventoryStore.java` | Giao diện/form kho, vòng đời thao tác, journal retry hoặc nhãn mã vạch. |
| `app/src/main/java/com/example/libraryonline/ui/reader/BookDetailActivity.java` | Chi tiết sách đầy đủ với cache, cập nhật và trạng thái lỗi/không công khai. |
| `app/src/main/java/com/example/libraryonline/ui/reader/BookDetailView.java` | Chi tiết sách đầy đủ với cache, cập nhật và trạng thái lỗi/không công khai. |
| `app/src/main/java/com/example/libraryonline/ui/reader/ReaderMainActivity.java` | Mở chi tiết mới, lọc categoryId thật, bỏ danh mục hardcode. |
| `app/src/main/res/layout/activity_admin_main.xml` | Thêm điểm vào quản lý kho. |
| `app/src/main/res/layout/activity_book_detail.xml` | Layout/style/nội dung Luồng 2 dùng palette chung. |
| `app/src/main/res/layout/activity_inventory.xml` | Layout/style/nội dung Luồng 2 dùng palette chung. |
| `app/src/main/res/layout/dialog_inventory_intake.xml` | Layout/style/nội dung Luồng 2 dùng palette chung. |
| `app/src/main/res/layout/dialog_inventory_title.xml` | Layout/style/nội dung Luồng 2 dùng palette chung. |
| `app/src/main/res/layout/item_inventory_book.xml` | Layout/style/nội dung Luồng 2 dùng palette chung. |
| `app/src/main/res/layout/item_inventory_copy.xml` | Layout/style/nội dung Luồng 2 dùng palette chung. |
| `app/src/main/res/values/admin_strings.xml` | Mô tả đúng chiều đồng bộ tải về. |
| `app/src/main/res/values/catalogue_strings.xml` | Layout/style/nội dung Luồng 2 dùng palette chung. |
| `app/src/main/res/values/catalogue_styles.xml` | Layout/style/nội dung Luồng 2 dùng palette chung. |
| `app/src/test/java/com/example/libraryonline/data/BarcodeLabelsTest.java` | Kiểm thử hồi quy logic, giao diện, session, barcode hoặc emulator thực. |
| `app/src/test/java/com/example/libraryonline/data/BookDetailTest.java` | Kiểm thử hồi quy logic, giao diện, session, barcode hoặc emulator thực. |
| `app/src/test/java/com/example/libraryonline/data/CatalogueScreensTest.java` | Kiểm thử hồi quy logic, giao diện, session, barcode hoặc emulator thực. |
| `app/src/test/java/com/example/libraryonline/data/InventoryChangesTest.java` | Kiểm thử hồi quy logic, giao diện, session, barcode hoặc emulator thực. |
| `app/src/test/java/com/example/libraryonline/data/InventoryEmulatorTest.java` | Kiểm thử hồi quy logic, giao diện, session, barcode hoặc emulator thực. |
| `app/src/test/java/com/example/libraryonline/data/InventoryPolicyTest.java` | Kiểm thử hồi quy logic, giao diện, session, barcode hoặc emulator thực. |
| `app/src/test/java/com/example/libraryonline/data/InventorySessionTest.java` | Kiểm thử hồi quy logic, giao diện, session, barcode hoặc emulator thực. |
| `app/src/test/java/com/example/libraryonline/data/InventoryUiTest.java` | Kiểm thử hồi quy logic, giao diện, session, barcode hoặc emulator thực. |
| `app/src/test/java/com/example/libraryonline/data/PendingInventoryStoreTest.java` | Kiểm thử hồi quy logic, giao diện, session, barcode hoặc emulator thực. |
| `docs/FLOW2_VERIFICATION.md` | Thiết kế/kế hoạch đã duyệt và báo cáo kết quả/phạm vi. |
| `docs/superpowers/plans/2026-10-21-flow2-catalogue-inventory.md` | Thiết kế/kế hoạch đã duyệt và báo cáo kết quả/phạm vi. |
| `docs/superpowers/specs/2026-10-21-flow2-catalogue-inventory-design.md` | Thiết kế/kế hoạch đã duyệt và báo cáo kết quả/phạm vi. |
| `firestore.rules` | Bảo vệ chuyển trạng thái và bộ đếm đồng thời; không deploy. |
| `tools/firestore-tests/rules.test.cjs` | Rules tests và runner Android SDK emulator có điều kiện local. |
| `tools/firestore-tests/run-android-inventory.sh` | Rules tests và runner Android SDK emulator có điều kiện local. |
