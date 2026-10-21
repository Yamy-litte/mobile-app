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
