# Admin management implementation

This branch adds an Admin-only management screen to the Android app.

## Included
- Reader search by name or phone, card lock/unlock and one-year renewal, plus full read-only per-reader borrowing history and late-return fine/payment details.
- System configuration stored at `SystemConfigs/default` and cached in SQLite.
- Fine-payment dashboard using the local `PenaltyPayments` and `Penalties` tables.
- Admin profile contact/email editing and a dedicated password-change screen.
- Manual-barcode counter workflow for borrowing, returning and collecting outstanding fines. Transactions are atomic in SQLite.
- Firestore Rules restrict profile, reader-card and system-configuration updates to validated fields and bounded values.

## Data boundary
Reader card/profile information and system configuration are cloud-backed. Circulation receipts, loan details, penalties, payments, and report totals remain SQLite-local in this implementation. The screen labels those reports accordingly; it does not claim cloud synchronization. The circulation screen accepts manually entered barcodes and does not yet open the device camera. Book/catalogue data must be synchronized to the Admin device before circulation.

## Validation status
The repository connector was used to inspect and modify source. An Android Gradle build, emulator run, Firebase Rules emulator test, and end-to-end Firestore test have not been run in this environment.

## Reader profile → borrowing history / fines (updated)

The former three-counter dialog has been replaced by a dedicated detail screen:
- Firestore `OnlineLoanReceipts` (server read, scoped to selected Reader UID):
  real pickup / return receipts, copy ID, loan period, return status and renewal count.
- SQLite `LoanReceipts` + `LoanDetails` (THIS Admin device):
  all existing per-reader local loan details, book title, barcode, dates and renewal count.
- SQLite `Penalties` + `PenaltyPayments` (THIS Admin device):
  only `LateReturn` fine records, due/paid/outstanding amounts, late days,
  and itemized confirmed/cancelled payment history.

The view keeps results from the successful source even when the other fails.
Unknown debt must display `—`, not falsely report 0 VND. Refresh is manual,
and stale asynchronous results are discarded.

The screen **does not** reconcile duplicate legacy local Online rows with cloud
receipts, claim to be a cross-device loan ledger, or fabricate cloud penalties.
Cloud Online late-return fine creation/collection is still a separate unfinished
system-level feature. No database migration, data deletion, or Firestore Rule
change is part of this Reader profile update.
