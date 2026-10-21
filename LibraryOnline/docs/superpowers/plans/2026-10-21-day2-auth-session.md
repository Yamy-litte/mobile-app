# Day 2 Auth and Session Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Complete existing login/registration, trusted role routing, profile caching and protected-session lifecycle, with reproducible checks.

**Architecture:** Retain Android Spark registration/review and existing bootstrap Admin profiles. Separate pure access decisions/profile parsing from the Firebase adapter and a shared Activity session gate. The server decides access; local SQLite only caches identity-linked profiles.

**Tech Stack:** Java/XML, Firebase Auth/Firestore, SQLite, JUnit/Robolectric, Firestore demo emulator.

**Spec:** ../../DAY2_REQUIREMENTS.md

## Global Constraints

- Preserve existing OTP behavior, book catalogue and Glide loader.
- Admin compatibility: Admins/{uid}, status Active/HoatDong; Reader role exactly Reader, cardStatus HoatDong.
- No cloud deployments, data resets or invented expiry/employee identifiers.
- No passwords/OTP in SQLite or saved state. No permissions from Intent extras or cached SQLite role.
- Push reviewed stable code to main; disclose all unrun live checks and required Rules updates.

## Review Focus

- Pending/locked/deleted/missing/UID-mismatched profiles must fail closed (Task 1 policy tests).
- A Reader opening Admin directly must never start Admin listeners (Task 2 guard tests).
- A delayed callback after logout/account change must not reopen a private screen (Task 2 generation/UID tests).
- Network failure during restore must offer retry without treating it as a wrong password (Tasks 1/2 error/state tests).
- Repeated profile sync and signup taps must not duplicate identities or overwrite local loan history (Tasks 1/3 SQLite/busy validation tests).

### Task 1: Trusted profile policy and UID cache

Files: create data/session/{SessionProfile,SessionPolicy,SessionException,SessionGateway,FirebaseSessionGateway}.java; modify UserDao/UserRepository, add data/session tests.

Interfaces: SessionPolicy.resolve(String uid, String authEmail, Map<String,Object> admin, Map<String,Object> user) -> SessionProfile or SessionException. SessionGateway exposes currentUid(), resolve() -> Task<SessionProfile>, watch(Runnable) -> AutoCloseable, signOut(). SessionProfile contains uid, trusted role, display profile and password-change requirement.

- [x] Add policy tests: Reader HoatDong passes; blocked/pending/deleted/missing/UID mismatch fails; Active Admin works without inventing Users bootstrap; inactive Admin cannot fall through to Reader; required password change retained.
- [x] Add Robolectric SQLite tests: repeated UID upsert updates same row and preserves related records; conflicting email on different UID fails without replacing another user.
- [x] Implement policy, server-only adapter, profile upsert; cache after successful server authorization, not before.
- [x] Run targeted tests and review async UID checks.

### Task 2: Session gate, routing and protected lifecycle

Files: create ui/auth/{SessionActivity,SessionNavigation,AuthErrors}.java and res/layout/view_session_gate.xml; modify LoginActivity, AdminLoginActivity, ChangePasswordActivity, AdminMainActivity, ReaderMainActivity; add guard/navigation tests.

Interfaces: SessionActivity.expectedRole(), allowPasswordChange(), onSessionAuthorized(SessionProfile), onSessionStopped(), createSessionGateway(), isSessionReady(). Login activities share safe routing/error helpers; private targets use NEW_TASK|CLEAR_TASK on auth transitions.

- [x] Add fake-gateway Robolectric lifecycle tests for signed out, wrong role, password change, network retry, logout/account-change stale callback and clear task flags.
- [x] Implement loading/error shield before private content/listeners, server checks on foreground and live role/auth changes.
- [x] Integrate trusted profile on both Home screens and add Admin logout/profile UI; stop private listeners when session leaves foreground.
- [x] Implement restored SDK session and busy login controls with typed errors, without logging credentials or distinguishing protected account-existence details.
- [x] Run guard/navigation tests and inspect role routing/back-stack behavior.

### Task 3: Registration validation and profile/Rules consistency

Files: modify InputValidator, RegisterActivity, Admin approval payload as needed, auth XML; update firestore.rules and tools/firestore-tests/rules.test.cjs only for proven schema gaps.

- [x] Add validation tests for name 2–120, valid non-future ISO date, normalized phone/email; retain all existing assertions and run Android Patterns tests with Robolectric.
- [x] Add busy-state checks and freeze relevant inputs during writes; retain OTP 6 digits/60 seconds and Pending-only request fields.
- [x] Ensure profile UID/role/status/payload required fields match schema and local cache; never accept self-selected Admin role.
- [x] Add Rules negative cases for public role injection/Reader privilege escalation and required fields. Do not loosen public Users reads to make forgot-password queries work.
- [x] Run available auth tests and attempt emulator only if network permits. Record configuration/live-provider limitations explicitly.

### Task 4: Verification, checklist, commit/push

- [x] Run full JVM suite, Node lint/check/contracts, Rules syntax/test runner when emulator available, standalone assembleDebug and diff checks.
- [x] Record each Day 2 item as verified locally, needs device/Firebase verification, or still blocked; include exact failing/unrun outcomes and Rules deployment notice.
- [x] Commit stable code and push HEAD to main after confirming origin is current. No Firebase deployment.


Kết quả thực tế và các bước cần máy/Console được phân biệt trong [DAY2_VERIFICATION.md](../../DAY2_VERIFICATION.md). Không dùng dấu hoàn tất trong kế hoạch để tuyên bố các smoke test thiết bị chưa chạy.
