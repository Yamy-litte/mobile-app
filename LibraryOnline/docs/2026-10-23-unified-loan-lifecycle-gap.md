# LibraryOnline — unified borrowing lifecycle assessment (2026-10-23)

## Requirement

Once a physical copy has been handed over, Online and direct loans must follow the
**same** loan lifecycle. Only Online has an initial Reader request and Admin approval.
There is no preorder queue or lost-book settlement in the current scope.

Functional specification calls for:
- Up to five books and maximum configured borrowing days.
- One extension of **seven days**, initiated by Reader, only before/on the due date.
- Identical actual pickup, return, overdue and fine semantics for both origins.
- Preserve legacy history and offline-first counter operations.

## Implemented on main in this change

1. New pure `LoanExtensionPolicy` shared by local and cloud paths.
2. Local `CirculationRepository.extendLoan`: atomic update of
   `LoanDetails.dueDate`, `originalDueDate`, `extensionCount`, `extended`,
   after verifying the loan belongs to the Reader, remains open, has not been
   returned, and has never been extended.
3. Firestore `OnlineLoanRequestRepository.extendLoan`: updates **both**
   `OnlineLoanRequests.dueAt` and `OnlineLoanReceipts.dueAt` within a
   transaction, with `originalDueAt`, `extensionCount=1`, `extended=true`.
   Rules permit only the owning Reader to perform this exact seven-day
   change while the receipt remains Open; attempts to edit only one document
   are denied.
4. Reader can extend eligible local and Online loans directly from loan cards.
5. When re-submitting a previously returned Online request, prior extension
   metadata is removed on the request; the old receipt remains immutable
   historical evidence.
6. Unit tests and a Firestore emulator test case added (emulator tests must
   be executed before deploying Rules).

## IMPORTANT: unified ledger is NOT complete

Before this change the system had two distinct ledgers:
- **Local**: SQLite `LoanReceipts`, `LoanDetails`, `Penalties`,
  `PenaltyPayments`, managed per Admin device.
- **Cloud**: Firestore `OnlineLoanRequests` and `OnlineLoanReceipts`
  after actual pickup.

This update aligns extension rules, but **does not** merge those stores,
replicate direct loans to all Reader devices, or make late return penalties
for Online loans work like local penalties.

**Do not describe the system as a single cross-device ledger yet.**
Merely copying Online receipts into SQLite or direct receipts into Firestore
would create duplicate or conflicting records without atomic, idempotent
reconciliation. No automatic data migration has been executed.

## Required next architecture stage

- Design a canonical shared post-checkout receipt/detail record, including
  `loanType`, Reader UID, copy ID, actual collected time, due date,
  extension history, returned time, overdue penalty ID, and stable operation ID.
- Online approval creates a *request*, not a loan. Online pickup and
  direct checkout both create the same canonical loan record.
- Local SQLite remains an offline outbox/cache with durable operation IDs,
  conflict detection, replay-safe server reconciliation, and no silent
  overwriting of Firestore copy availability.
- After pickup, both origins must use the same return and penalty/payment
  service. Reader history, active-loan counts, overdue notifications and
  Admin dashboard must query the canonical ledger.
- Backfill legacy Online and direct receipts through audited, idempotent
  migrations; retain original IDs and reject duplicate active copy loans.
- Test network loss, simultaneous scan by two Admin devices, old data upgrades,
  double return, renewed due dates, partial payments, and authority boundaries.

## Release checks

From `LibraryOnline/`:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
.\gradlew.bat :app:connectedDebugAndroidTest
npm --prefix tools/firestore-tests ci
# Run tools/firestore-tests under Firestore Emulator via the project script.
```

Firestore Rules are modified in git but **not deployed automatically** by a
GitHub commit. Run security tests locally first, then deploy rules through
the normal reviewed Firebase workflow. This work was performed using GitHub
source edits without an Android emulator or Firestore emulator in session.
