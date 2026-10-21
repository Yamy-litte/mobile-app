# Luồng 2 — Tra cứu sách và quản lý kho

## Phạm vi đã thống nhất

Hoàn thiện danh sách, tìm kiếm, thể loại, chi tiết và số lượng khả dụng cho Reader; thêm, sửa, nhập thêm, ẩn và thanh lý sách cho Admin. Chi tiết Reader hiện là hộp thoại văn bản, phải được hoàn thiện thành giao diện Java/XML theo thiết kế đã cung cấp. Không dùng Cloud Functions trong bất kỳ phần nào. Không sửa Luồng 1, không triển khai mượn/trả, đặt trước hoặc báo mất. Không commit, push, deploy hoặc migration dữ liệu cloud khi chưa được yêu cầu riêng.

## Căn cứ và hiện trạng

- Đặc tả DOCX, phân hệ 4: tạo đầu sách và bản sách, barcode Code 128, nhập bổ sung, mã bất biến, ẩn và thanh lý logic.
- Thiết kế bảng: BookTitles, BookCopies và các trạng thái hiện hữu; lịch sử phải được bảo toàn.
- Chỉ dẫn người dùng về schema được ưu tiên: Firestore BookTitles chỉ ghi categoryId; category trong SQLite/model là tên phục vụ hiển thị.
- ReaderMainActivity.showBookDetails đang dùng AlertDialog văn bản.
- BookCopyRepository chỉ thao tác SQLite; CloudLibraryRepository hiện ghi đầu sách và bản sách riêng lẻ. Không dùng các thao tác này để thực hiện một nghiệp vụ kho nhiều bản ghi.
- BookCatalogueSyncManager đã có đường Firestore về SQLite; tái sử dụng đường này làm nguồn cập nhật cache.
- Rules đã giới hạn ghi catalogue cho Admin hoạt động, cấm xóa vật lý và bảo vệ một số trạng thái. Cần thêm kiểm thử chuyển trạng thái và đường ghi mới.

## Giao diện

### Reader

Màn hình chi tiết nhận bookTitleId, đọc lại dữ liệu qua repository/cache, không truyền cả bản sao dữ liệu làm nguồn chuẩn. Có ảnh bìa, tên, tác giả, thể loại, mô tả, nhà xuất bản, năm, ISBN và số lượng khả dụng. Hiển thị trạng thái không còn công khai/không tìm thấy khi dữ liệu thay đổi. Không cung cấp nút mượn/đặt trước giả trong phạm vi này.

Giữ tìm kiếm và lọc danh sách hiện có; đối chiếu bộ lọc với Categories thật. Không thêm sách hoặc thể loại giả khi không có dữ liệu. Có trạng thái tải, rỗng, lỗi, thử lại và thông tin cache khi phù hợp. Bìa sách tái sử dụng BookCoverLoader.

### Admin

Thêm điểm vào quản lý kho từ Home. Danh sách hỗ trợ tìm kiếm và xem cả đầu sách ẩn. Chi tiết đầu sách có thông tin chung, các bản vật lý và thao tác theo trạng thái.

Form thêm/sửa chọn thể loại hiện có bằng categoryId; kiểm tra các trường theo schema, số lượng nguyên dương, vị trí kệ, URL ảnh nếu có. Không cho sửa mã đầu sách, mã bản hoặc barcode. Nhập thêm qua đầu sách tìm được là đường thao tác trong đợt này; quét camera thuộc phần mở rộng chưa triển khai.

Mỗi thao tác ghi có trạng thái đang lưu, chống gửi lặp, thông báo lỗi và chỉ báo thành công sau khi Firestore xác nhận. Ẩn/thanh lý có xác nhận ghi rõ bản sách bị tác động. Barcode Code 128 được hiển thị cho các bản mới; nếu cần thêm thư viện mã vạch, phải giải thích dependency và ảnh hưởng trước khi thêm.

Tái sử dụng library_background, library_surface, library_text, library_muted, library_border, library_blue; không hardcode hệ màu mới. Giao diện giữ theme Light hiện tại.

## Dữ liệu và tính nhất quán

Giữ Firebase Auth + Firestore trực tiếp; Rules là ranh giới phân quyền. SQLite là cache catalogue, không ghi local thành công trước rồi coi nghiệp vụ cloud đã hoàn tất.

Tạo repository chuyên trách thao tác kho, dùng Firestore transaction cho các thay đổi liên quan. Mọi lần nhập đọc lại đầu sách trên server để phát hiện cạnh tranh; sinh ID ổn định cho một lần gửi và kiểm tra bản đã tồn tại trước khi thử lại. Không tạo ID mới ở mỗi lần callback transaction được thực thi.

Không lấy totalCopies làm số thứ tự barcode vì dữ liệu cũ có thể đã thanh lý hoặc có mã khác định dạng. Việc chọn dải mã phải kiểm tra các mã hiện hữu, rồi transaction đọc các document đích trước khi tạo để tránh ghi đè. Giữ nguyên barcode cũ. Giới hạn kích thước một giao dịch theo giới hạn Firestore/Rules thực tế, thông báo giới hạn nhập cho người dùng; không âm thầm chia một lần nhập thành nhiều giao dịch có thể thành công một phần.

availableCopies phản ánh các bản phục vụ được: SanSang, không ẩn. Thay đổi số lượng dùng dữ liệu đọc trong transaction, không dùng số đang hiển thị trên UI. Nếu dữ liệu đầu sách và bản sách cũ không khớp, báo lỗi cần đối soát; không tự seed hoặc sửa toàn bộ dữ liệu cloud.

Sửa thông tin đầu sách không ghi đè các bộ đếm hoặc trạng thái từ cache. Sửa vị trí kệ chỉ cập nhật trường được phép.

Ẩn từng bản SanSang chuyển DangSuaChua và isHidden=true, giảm availableCopies, giữ tổng và lịch sử. Không tác động bản DaMuon, ChoLay hoặc DaDatTruoc. Ẩn đầu sách dùng isHidden để loại khỏi Reader; không tự đổi trạng thái các bản đang có giao dịch. Không tự bổ sung thao tác phục hồi sách khi chưa có quy tắc được xác nhận.

Thanh lý chỉ từ SanSang sang ThanhLy; giảm số khả dụng đúng một lần, giữ document và barcode. Không dùng thao tác delete. Tổng số bản được giữ là số bản đã ghi nhận, gồm bản thanh lý: tài liệu chỉ quy định giảm tồn khả dụng, chưa quy định giảm totalCopies. UI cần phân biệt bản hiện phục vụ với bản đã thanh lý; không dùng tổng lịch sử làm số lượng có thể cho mượn.

## Phân quyền và lỗi

Màn hình Admin tái sử dụng SessionActivity. Reader và người chưa đăng nhập không được ghi catalogue theo Rules dù gọi Firestore trực tiếp. Kiểm tra đầu vào ở client để báo lỗi và ở Rules để bảo vệ dữ liệu. Không mở quyền collection ngoài Luồng 2.

Các transaction kho cần mạng; khi offline, vẫn cho đọc cache theo khả năng phiên hiện tại, nhưng không nhận ghi kho vào hàng đợi rồi báo thành công. Lỗi quyền, xung đột và dữ liệu không hợp lệ phải có thông báo riêng có thể hành động.

## Kiểm thử và tiêu chí nghiệm thu

- Unit test quy tắc trạng thái, kiểm tra đầu vào, bộ đếm và nhận diện thao tác lặp.
- Firestore emulator: Admin hoạt động, Reader, chưa đăng nhập, Admin không hoạt động; ID/barcode bất biến; cấm xóa; trạng thái không được thanh lý; rollback khi một ghi bị từ chối.
- Kiểm tra nhập đồng thời và thử lại không sinh trùng bản hoặc tăng bộ đếm hai lần.
- Reader chỉ thấy đầu sách công khai; chi tiết cập nhật đúng dữ liệu, ảnh và trạng thái rỗng/lỗi.
- Kiểm tra quay lại/đăng xuất trên các màn hình mới, giữ bảo vệ phiên hiện có.
- Chạy unit test, lint, assembleDebug và emulator phù hợp; ghi nguyên nhân nếu môi trường không chạy được kiểm thử thiết bị.
- Rà soát diff, liệt kê file thay đổi, cảnh báo còn lại. Nếu sửa Rules phải thông báo người dùng cập nhật; không deploy tự động.

## Trạng thái tài liệu

Thiết kế cụ thể để người dùng rà soát trước kế hoạch triển khai. Chưa có thay đổi mã sản phẩm hoặc dependency. Các giới hạn được nêu rõ: không camera trong đợt này, không phục hồi trạng thái, không nghiệp vụ mượn/đặt trước và không tự đối soát dữ liệu cloud cũ.
