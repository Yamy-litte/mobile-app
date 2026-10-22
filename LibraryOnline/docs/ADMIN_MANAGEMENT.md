# Admin management implementation

This branch adds an Admin-only management screen to the Android app.

## Included
- Reader search by name or phone, card lock/unlock and one-year renewal, plus local-history summary.
- System configuration stored at `SystemConfigs/default` and cached in SQLite.
- Fine-payment dashboard using the local `PenaltyPayments` and `Penalties` tables.
- Admin profile contact/email editing and a dedicated password-change screen.
- Manual-barcode counter workflow for borrowing, returning and collecting outstanding fines. Transactions are atomic in SQLite.
- Firestore Rules restrict profile, reader-card and system-configuration updates to validated fields and bounded values.

## Data boundary
Reader card/profile information and system configuration are cloud-backed. Circulation receipts, loan details, penalties, payments, and report totals remain SQLite-local in this implementation. The screen labels those reports accordingly; it does not claim cloud synchronization. The circulation screen accepts manually entered barcodes and does not yet open the device camera. Book/catalogue data must be synchronized to the Admin device before circulation.

## Validation status
The repository connector was used to inspect and modify source. An Android Gradle build, emulator run, Firebase Rules emulator test, and end-to-end Firestore test have not been run in this environment.
