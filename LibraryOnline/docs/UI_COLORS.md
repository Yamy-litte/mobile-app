# Quy chuẩn màu Library Online

Trước khi tạo/chỉnh giao diện, đọc `app/src/main/res/values/colors.xml`, `themes.xml`, các style và layout đã hoàn thành. Hiện không có `styles.xml` chung; `admin_styles.xml` chỉ chứa kiểu bố cục/chữ và tham chiếu màu chung.

Bảng màu gốc đã được định nghĩa và đang dùng trên Login/Reader, vì vậy ưu tiên bảng này thay cho màu tạm thời:

| Vai trò | Resource chuẩn | Giá trị |
|---|---|---|
| Nền màn hình | `library_background` | `#F5F7FC` |
| Thẻ, dialog, khu vực nổi | `library_surface` | `#FFFFFF` |
| Nền ô nhập | `library_surface` | `#FFFFFF` |
| Viền | `library_border` | `#DCE3EF` |
| Chữ chính | `library_text` | `#182230` |
| Chữ phụ | `library_muted` | `#68758A` |
| Thương hiệu, nút chính, icon hành động | `library_blue` | `#315EFB` |
| Navy đã có | `library_navy` | `#172B4D` |
| Nền nhấn nhẹ đã có ở avatar/cover Reader | `library_accent_background` | `#E8EEF9` |

Các màu trạng thái hiện có được giữ nguyên: `library_success`, `library_warning`, `library_danger`; màu đăng xuất hiện có là `library_logout`. `reader_cover_placeholder` tham chiếu màu nhấn nhẹ chung để tương thích mã cũ.

- Định nghĩa màu trong `colors.xml`, tham chiếu resource ở XML và `ContextCompat.getColor` ở Java. Không tạo palette riêng theo màn hình hoặc hardcode màu trong layout/drawable/Java mới.
- Dùng background cho toàn màn hình, surface cho thẻ/dialog/ô nhập. Không thêm nền tối, gradient hay một sắc nền riêng cho màn hình mới.
- Đối chiếu Figma và các màn hình đã hoàn thành trước khi sửa. PDF Admin có nền kem, nhưng yêu cầu đồng bộ màu hiện tại ưu tiên bảng màu chung đã dùng trong Login/Reader; bố cục Admin vẫn theo PDF.
- Giữ màu thương hiệu/nút/icon và trạng thái đã thống nhất. Không thay số liệu hoặc nghiệp vụ trong một lần chỉnh màu.
- Dùng cùng `Theme.LibraryOnline` sáng cả khi thiết bị bật chế độ tối; không bổ sung override DayNight riêng làm nền thẻ/dialog lệch màu.
- Kiểm tra Admin/Login/Reader cùng theme, bao gồm loading, thẻ nội dung, ô nhập và cấu hình night/notnight. Ảnh Robolectric là kiểm tra layout/theme; không thay thế smoke test trên thiết bị thật.

Nếu một dự án/giao diện chưa có màu gốc, bộ dự phòng được người dùng đưa ra là background `#F7F8FA`, surface/input `#FFFFFF`, border `#E5E7EB`, text_primary `#1F2937`, text_secondary `#6B7280`. Không áp dụng bộ dự phòng để ghi đè bảng màu gốc hiện có của Library Online.

## Đồng bộ resource ngày 23/10/2026

- `colors.xml` chứa các tên màu ngữ nghĩa `background`, `surface`, `input_background`, `border`, `text_primary`, `text_secondary`, đều tham chiếu **bảng màu gốc** `library_*` nêu ở trên; không thay bảng màu gốc bằng bộ dự phòng.
- Nền `activity_admin_main.xml`, `activity_admin_management.xml`, `activity_reader_online_borrow.xml` và `view_auth_session_gate.xml` đều dùng `@color/background`. Các layout dùng `@color/library_background` từ trước vẫn đồng nhất vì hai resource trỏ tới cùng màu.
- Các alias `admin_screen_bg`, `admin_screen_ink`, `admin_screen_muted`, `admin_screen_border`, `admin_screen_readonly` và `online_canvas` trỏ đến resource ngữ nghĩa chung để giữ tương thích nơi còn sử dụng tên cũ.
- Di chuyển bảng màu Admin/Online vào `colors.xml` thay vì định nghĩa màu ở nhiều file `values`. Từ chuẩn Login, `admin_screen_primary` và `online_forest` là alias của `library_blue`; vẫn giữ các màu trạng thái chuyên biệt.
- `Theme.LibraryOnline` ánh xạ nền, chữ, viền và các surface-container Material3 về palette chung cho cả chế độ sáng/tối, không dùng nền kem Figma khác với bảng màu đã chuẩn hóa cho Login/Reader.
- Có kiểm thử hồi quy `SharedPaletteTest` cho alias màu và nền các màn hình Auth/Reader/Admin/Online ở chế độ `notnight` và `night`. Cần chạy `./gradlew :app:testDebugUnitTest :app:assembleDebug` và kiểm tra thực tế trên thiết bị sau khi áp dụng.

## Quyết định giao diện: lấy Login làm chuẩn (23/10/2026)

Bản Figma chỉ dùng tham khảo **bố cục form, thứ tự thông tin, khoảng cách và luồng thao tác**, không phải nguồn màu thương hiệu. Mọi màn hình Reader, Admin, xác thực và mượn Online phải dùng cùng bảng màu Login:

- Nền `#F5F7FC`: `@color/background` / `@color/library_background`.
- Thẻ, dialog, input `#FFFFFF`: `@color/surface` / `@color/input_background`.
- Viền `#DCE3EF`: `@color/border`.
- Chữ chính `#182230`: `@color/text_primary`.
- Chữ phụ `#68758A`: `@color/text_secondary`.
- Nút, biểu tượng hành động, lựa chọn hiện hành `#315EFB`: `@color/library_blue`.

Màu lỗi/thành công/cảnh báo là **màu trạng thái**, không dùng thay màu nhận diện. `admin_screen_primary`, `online_forest` nay tham chiếu `library_blue`, các nền nhấn dùng `library_accent_background` đã có từ Login. Không thêm bảng màu theo từng trang.

### Quy ước mã nguồn

- **`res/layout/`**: toàn bộ bố cục tĩnh; khai báo `Button`, `TextView`, `EditText`, `LinearLayout` cho màn hình và thẻ danh sách ở XML. Ví dụ: `view_admin_reader_list.xml`, `view_admin_config_panel.xml`, `view_admin_reports_panel.xml`, `item_admin_online_request.xml`, `item_reader_online_request.xml`, `item_reader_local_notification.xml`.
- **`res/drawable/`**, **`res/color/`**, **`res/values/`**: hình dạng viền, state-list của filter, typography/màu dùng chung; tránh dùng `setTextColor`, `setPadding`, `setBackgroundTint` để định dạng các view cố định trong Java.
- **`ui/` Java**: chỉ gọi `setContentView` / `inflate(R.layout...)`, `findViewById` rồi xử lý dữ liệu, sự kiện, trạng thái và điều hướng. Danh sách động được phép thêm/bớt **các layout item đã inflate** theo kết quả Firestore/SQLite; không tạo widget mới bằng `new TextView/Button/EditText/LinearLayout`.
- **View đồ họa đặc thù** (đồ thị và mã vạch sinh từ dữ liệu) vẫn cần logic vẽ; `AdminWeeklyTrendView` đặt tại package `view/` và được khai báo bằng custom-view tag trong layout thay vì tạo trực tiếp trong Activity.
- Chế độ sáng/tối cùng dùng `Theme.LibraryOnline` nền sáng. Kiểm tra `XmlPresentationTest`, `SharedPaletteTest` và các luồng điều hướng, cập nhật, duyệt, gửi Online trước khi merge.

### Cách kiểm tra

Kiểm tra biên giới XML/UI và màu chuẩn bằng Python không cần thư viện ngoài:

```sh
python tools/check_ui_boundaries.py
```

Chạy trong thư mục `LibraryOnline`:

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

Và smoke test trên thiết bị: Login, Reader Home/Loans/Notifications/Online, Admin Home/Reader Management/Config/Reports/Online requests; xác nhận theme sáng cả khi điện thoại ở dark mode.
