# Registration architecture in the current Android app

The app uses Firebase Authentication and direct Firestore writes (Spark workflow), not callable Functions. `RegisterActivity` writes RegistrationRequests + RegistrationEmailIndex + RegistrationPhoneIndex atomically. `AdminMainActivity` uses a named secondary Auth instance, then atomically writes Users and the registration decision. Rejection is a Firestore update followed by Android EmailSender. Approval emails a random temporary password and requires a password change; it does **not** send a password-reset email in the current implementation.

The legacy submitRegistration, approveRegistration, rejectRegistration and seedSampleBooks endpoints have been removed from source because no current Android caller uses them. firebase.json no longer advertises a Functions codebase. This source change does not delete any already-deployed endpoint. Do not deploy or delete remote functions without a separate inventory of deployed clients and explicit approval.

Categories, BookTitles and BookCopies have explicit Rules. The Rules still deny unspecified collections: LoanReceipts, LoanDetails, Reservations, Penalties, PenaltyPayments, SystemConfigs and Notifications currently have only local SQLite implementations. Do not open these collections merely because they appear in the design documents.

## Integrity and compatibility

- Public registration is intentional. The three-document batch and create-only indices prevent duplicate requests for the same normalized email/phone. Indices stay reserved after rejection. Existing legacy requests without indices are not backfilled automatically.
- Review is one-way from Pending/PendingVerification to Approved/Rejected. Approval must match the Users profile in the atomic batch. Rules do not create or validate the existence of an Auth account; Android's Auth creation and compensating cleanup are separate operations.
- Registration birthDate maps to Users.dateOfBirth; reviewedBy remains the authenticated UID used as Admins document ID in this app. No migration to a distinct adminId is performed.
- Client-side OTP and emailVerified=true are **not server-verifiable proof**. SMTP on the Android client and public request abuse remain architectural limitations, not problems fixed by retiring unused Functions.
- Admin rights currently use Admins/{uid}.adminStatus (Active/HoatDong), as do login and existing Rules. The proposed Users.role + Admins.userId model requires a separately planned compatibility migration; no bootstrap records were changed.
- Existing SQLite/cloud rows are never deleted based on demo-looking IDs. New installations/upgrades no longer insert sample books. Review old local data before manually syncing it.

## Validation without production access

See [CLOUD_FUNCTIONS_AUDIT.md](docs/CLOUD_FUNCTIONS_AUDIT.md) for the inventory, evidence, remaining gaps and exact commands. Use firebase.emulators.json with the demo-library-online project for Rules tests. No Firebase deployment or destructive migration is part of this change.
