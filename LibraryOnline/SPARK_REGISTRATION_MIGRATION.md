# Spark registration flow migration

The Android registration flow no longer calls the `submitRegistration` callable. It writes a pending document directly to `RegistrationRequests`. The admin screen uses a named secondary Firebase Auth instance to create a reader account without signing out the administrator, then writes the reader profile and marks the request approved in a Firestore batch.

## Important deployment warning

The updated `firestore.rules` permits only the registration request shape described in the rules, active-admin review, and admin-created reader profiles. It intentionally denies all other collections. **Do not deploy these rules to a live database until explicit least-privilege rules have been added for every other collection used by the library app** (books, loans, fines, notifications, and any others actually used in the project). Deploying the catch-all deny rule without those rules can break existing app features.

After reviewing and completing rules for the whole app, deploy only Firestore rules with:

```powershell
firebase use libraryonline-dd3a0
firebase deploy --only firestore:rules
```

The Android source changes are on `main`; pull the latest commit and rebuild the app before testing.

## Spark limitations and remaining caveats

- This client-only approach cannot provide the same trusted server-side duplicate checks or abuse/rate limiting as Cloud Functions. The admin screen checks duplicate email/phone values in `Users` before approval, and Firebase Authentication rejects an already-registered email. It does not guarantee global uniqueness under concurrent requests.
- Approval sends a Firebase password-reset email so the reader can choose a password. This is not proof that the applicant's mailbox was verified before approval.
- Rejection reasons are saved, but automatic rejection emails are not sent in this Spark-only flow. Notify the applicant manually.
- Test the full flow with a test Firebase project first. Verify anonymous request creation, admin-only reads/review, reader profile creation, reset email delivery, and reader/admin login before using real user data.
