# Luồng 2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Hoàn thiện chi tiết sách Reader và quản lý kho Admin với dữ liệu thật, ghi nguyên tử và bảo toàn lịch sử.

**Architecture:** Java/XML tái sử dụng SessionActivity, Model/DAO và BookCatalogueSyncManager. Thao tác kho đi qua InventoryRepository dùng Firestore transaction; SQLite nhận kết quả qua đường đồng bộ hiện có. Không dùng Cloud Functions.

**Tech Stack:** Android Java 11/XML, SQLite, Firebase Auth/Firestore, Glide hiện có, JUnit/Robolectric, Firestore emulator.

**Spec:** `docs/superpowers/specs/2026-10-21-flow2-catalogue-inventory-design.md` (người dùng đã duyệt).

## Global Constraints

- Không dùng Cloud Functions trong bất kỳ phần nào.
- Không commit, push, deploy hoặc migration dữ liệu cloud khi chưa được yêu cầu riêng.
- Không sửa Luồng 1, không triển khai mượn/trả, đặt trước hoặc báo mất.
- Firestore BookTitles chỉ ghi categoryId; category trong SQLite/model là tên phục vụ hiển thị.
- Không đổi tên collection, field hoặc trạng thái nghiệp vụ hiện hữu.
- Tái sử dụng library_background, library_surface, library_text, library_muted, library_border, library_blue.
- Không tự seed hoặc sửa toàn bộ dữ liệu cloud.
- Giữ lockfile/dependency hiện có; giải thích trước nếu cần thư viện mã vạch.
- Khi thay đổi Rules, thông báo người dùng cập nhật; chỉ chạy kiểm thử với emulator.

## Review Focus

1. Nhập thêm đồng thời hoặc thử lại sau mất phản hồi không được tạo trùng bản/tăng bộ đếm hai lần — Task 1 và 2.
2. Barcode dữ liệu cũ không tuần tự hoặc có bản đã thanh lý không được bị tái sử dụng — Task 1.
3. Đầu sách ẩn hoặc phiên hết hiệu lực khi đang mở chi tiết phải ngừng hiển thị/thao tác phù hợp — Task 3 và 4.
4. Sửa metadata từ màn hình cũ không được ghi đè bộ đếm mới hoặc đổi định danh — Task 1 và 2.
5. Số lượng cũ lệch với BookCopies phải báo cần đối soát, không tự sửa lịch sử — Task 1 và 3.

## Task 1: Giao dịch kho và quy tắc đầu vào

**Files:**
- Create `app/src/main/java/com/example/libraryonline/data/inventory/InventoryPolicy.java`
- Create `app/src/main/java/com/example/libraryonline/data/inventory/InventoryRepository.java`
- Create `app/src/main/java/com/example/libraryonline/data/inventory/StockIntake.java`
- Test `app/src/test/java/com/example/libraryonline/data/InventoryPolicyTest.java`
- Modify `data/repository/BookRepository.java` and `data/remote/firebase/BookCatalogueSyncManager.java` only where existing production writers overlap.

**Interfaces:** `InventoryPolicy.validateTitle(BookTitle)`, `validateIntake(int,String)`, `isAvailable(BookCopy)`, `requireRetirable(BookCopy)`; repository exposes `Task<Void> createTitle(BookTitle, StockIntake)`, `addCopies(String, StockIntake)`, `updateTitle(BookTitle)`, `updateShelf(String,String)`, `hideCopy(String)`, `retireCopy(String)`, `hideTitle(String)`. StockIntake carries immutable operation-local copy IDs/barcodes, quantity and shelf location; IDs survive transaction retries.

- [ ] Write failing tests: SanSang visible is available; hidden/DangSuaChua/ThanhLy/DaMuon/ChoLay/DaDatTruoc are not. Retire accepts only visible SanSang without priority reservation. Zero/negative/overflow quantity and blank shelf rejected. Missing categoryId rejected without deriving one from display text.
- [ ] Run `:app:testDebugUnitTest --tests '*InventoryPolicyTest'`; observe expected failure before implementation.
- [ ] Implement policy and prepared intake with a documented maximum of 5 copies per atomic intake initially; test 1/5 accepted, 0/6 rejected. This conservative limit must pass emulator Rules access limits before acceptance.
- [ ] Implement transactions: all reads precede writes; read fresh title/copy/category state; retain totalCopies when retiring; decrement availableCopies once; only patch metadata fields during edits. Compare state instead of decrementing again on repeated hide/retire.
- [ ] For add/create, discover existing barcodes then prepare unused sequential suffixes without relying on totalCopies. Transaction reads destination docs, detects collision and rejects stale preparation; never overwrites an existing copy. If all prepared docs already represent the same intake, return success without incrementing counters. Partial or mismatched existence is an error.
- [ ] Validate preflight copy inventory against fresh title version in transaction. If title changed, reject/reload preflight; never use an unchecked query result to overwrite newer counters. Report inconsistent old data without repair.
- [ ] Search callers of old repository writers; route new UI exclusively to InventoryRepository and prevent old background upload from overwriting existing cloud catalogue records. Preserve unrelated CRUD APIs where they have no production caller; document limitations.
- [ ] Run policy tests; add tests for historical barcode collision, integer overflow and already-completed transition. Do not commit.

## Task 2: Rules and transaction integration tests

**Files:** Modify `firestore.rules`, `tools/firestore-tests/rules.test.cjs`; create an integration fixture only if required by existing test structure.

**Interfaces:** Keep Categories/BookTitles/BookCopies field names and existing role resolution. Task 1 transactions must obey these Rules.

- [ ] Write emulator cases rejecting anonymous/Reader/inactive Admin writes; barcode or parent changes; deletes; retirement from DaMuon/ChoLay/DaDatTruoc; invalid counters and category references. Verify existing Rules tests remain unchanged in intent.
- [ ] Run Firestore emulator tests to record the gaps before tightening Rules.
- [ ] Tighten only catalogue checks required by these cases. Enforce relevant types, immutable identity, allowed transition and metadata fields. Do not claim Rules can calculate an arbitrary collection-wide total; atomic client behavior and concurrent tests cover that limitation.
- [ ] Test transaction rollback on an invalid copy in a multi-write intake, the maximum five-copy intake, repeated operation and two competing intakes. Do not label a JavaScript recreation as proof that the Android repository itself works: exercise its Android path when runtime permits, otherwise record the testing gap explicitly.
- [ ] Run complete existing Rules suite with `firebase.emulators.json` and demo-library-online. Capture command/exit code, remove generated untracked logs from repo only if created by this run. Do not deploy.

## Task 3: Admin inventory screens

**Files:**
- Create `ui/admin/InventoryActivity.java`, `ui/admin/InventoryViewModel.java`, `ui/admin/InventoryForm.java`
- Create XML `activity_inventory.xml`, `item_inventory_book.xml`, `dialog_inventory_title.xml`, `dialog_inventory_intake.xml`, `item_inventory_copy.xml`
- Modify `ui/admin/AdminMainActivity.java`, `res/layout/activity_admin_main.xml`, `AndroidManifest.xml`, `res/values/strings.xml`
- Test `app/src/test/java/com/example/libraryonline/data/InventoryUiTest.java`

**Interfaces:** Activity extends SessionActivity with ADMIN role; ViewModel preserves prepared intake/busy state across rotation, calls Task 1 API. No role from Intent is trusted. Forms use CategoryDao.Category.categoryId, BookTitle and BookCopy without renaming fields.

- [ ] Write Robolectric tests for form validation, immutable IDs, disabled duplicate submit, loading/empty/error presentation and blocked states; run to observe expected failure.
- [ ] Add Home entry and non-exported activity with system-bar insets. Load real catalogue through existing sync/cache; support search and seeing hidden titles without fake fallback rows.
- [ ] Add title create/edit form, detail/copy list, intake form showing 1–5 limit and shelf, location edit, explicit hide/retire confirmations. Hide unsupported actions; callbacks must verify current session/generation before displaying results.
- [ ] Preserve pending operation identity on rotation/retry, keep server errors visible, retain user input after failure and refresh from confirmed data after success. Do not optimistically report offline writes as completed.
- [ ] Display printable Code 128 for new/existing copy barcodes. Inspect available barcode support first; if absent, explain a narrowly scoped encoder dependency before adding it. Do not implement an untested custom barcode alphabet or add camera scanning.
- [ ] Test unavailable category, network failure, data mismatch, retirement twice, operation callback after logout, narrow screen and rotation; rerun tests.

## Task 4: Reader detail and catalogue consistency

**Files:**
- Create `ui/reader/BookDetailActivity.java`, `res/layout/activity_book_detail.xml`
- Modify `ui/reader/ReaderMainActivity.java`, `AndroidManifest.xml`, `res/values/strings.xml`
- Modify existing repository/sync only if required for by-ID detail observation.
- Test `app/src/test/java/com/example/libraryonline/data/BookDetailTest.java`

**Interfaces:** Detail accepts only extra `bookTitleId`, extends SessionActivity with READER role and reuses BookCoverLoader. BookRepository.getBookTitle(String) supplies cache, existing sync listener triggers refresh.

- [ ] Write failing Robolectric tests for binding real fields, absent fields, unavailable/hidden title, quantities and state transitions; run tests.
- [ ] Replace showBookDetails text dialog with navigation to the non-exported detail activity. Implement cover, author/title, metadata, description, availability, back navigation and loading/error/retry with shared colors.
- [ ] Read by ID after authorization; listen only while authorized; close listeners/executors safely. Reload on catalogue updates, and stop showing a title that becomes hidden. Never reconstruct a title from display-only Intent fields.
- [ ] Inspect category chips and search; bind real category IDs/names and remove fixed fallback category choices if found. Reader filtering keeps hidden titles out of both lists and detail.
- [ ] Verify empty title ID, cloud missing/hidden after cached success, image failure, offline cache and callback after session stop. No fake borrow button.

## Task 5: Regression, review and handover

**Files:** Create `docs/FLOW2_VERIFICATION.md`; update this plan checkboxes from actual execution.

- [ ] Run `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug` using workspace activation and existing toolchain. Record actual totals and warnings; investigate regressions instead of disabling tests.
- [ ] Run complete Rules emulator suite, plus Android repository/emulator integration when available. State clearly if device, instrumentation or barcode scan validation cannot run.
- [ ] Review screenshots/renderings for Reader detail and Admin screens at the same theme and narrow viewport; check no hardcoded new palette.
- [ ] Run `git diff --check`, inspect diff and remaining old writer call sites; verify no schema rename, seed data, Cloud Functions or unrelated auth modifications.
- [ ] Fresh whole-change review, resolve actionable findings and rerun only affected checks.
- [ ] Report every changed file, commands/results, remaining gaps, APK path and Rules update needed. Do not claim flow completion if any required action remains unimplemented. No commit/push/deploy.

## Handoff

Plan self-review: all approved scope sections map to Tasks 1–5; copy lifecycle and Reader availability share the same policy. Implementation completed in the working tree; see docs/FLOW2_VERIFICATION.md for actual results and limits. Recommended execution: Native, implement in this session with one independent review at the end, because the UI and Rules depend closely on the repository transaction interface.

## Execution record — 2026-10-21

- [x] Task 1: pure rules + transactional repository + actual SDK emulator integration, retries and concurrent intake.
- [x] Task 2: catalogue Rules, 15 emulator cases; no deploy.
- [x] Task 3: Admin inventory/forms/barcodes, durable UID-scoped journal and form drafts.
- [x] Task 4: Reader detail Activity, real categoryId filters, cache/live/error states.
- [x] Task 5: full suite, APK, lint, diff review and independent review fix pass.

Detailed step checkboxes above remain the original planned checklist; this record and FLOW2_VERIFICATION.md distinguish automated evidence from manual/device checks not run. No commit/push.

Rulings: existing cloud checkout retained; journal scratch stored outside repo; intake limit 5; ZXing Core 3.5.3 only; old Admin sync UI download-only; durable pending journal supplements saved state; reconciliation updates existing category timestamp only to fence stale intake transactions; no unhide/camera/loan workflows. Full reasons and tradeoffs in FLOW2_VERIFICATION.md.
