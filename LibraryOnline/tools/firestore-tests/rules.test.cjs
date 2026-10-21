"use strict";
const { before, after, beforeEach, test } = require("node:test");
const fs = require("node:fs");
const { URL } = require("node:url");
const { initializeTestEnvironment, assertSucceeds, assertFails } = require("@firebase/rules-unit-testing");
const { doc, setDoc, getDoc, updateDoc, deleteDoc, writeBatch, serverTimestamp } = require("firebase/firestore");
let env;
const projectId = "demo-library-online";
const category = { categoryId: "cat", name: "Văn học", isActive: true };
const book = { title: "Real inventory", author: "Author", isbn: "", categoryId: "cat", publisher: "", publishYear: 2026, description: "", coverUrl: "", totalCopies: 1, availableCopies: 1, isHidden: false };
const copy = { bookTitleId: "book", barcode: "barcode", shelfLocation: "A", copyStatus: "SanSang", priorityUserId: null, isHidden: false };
const db = (uid) => uid ? env.authenticatedContext(uid).firestore() : env.unauthenticatedContext().firestore();
before(async () => {
  if (!process.env.FIRESTORE_EMULATOR_HOST) throw new Error("Use the local Firestore emulator; cloud testing is forbidden");
  const host = new URL(`http://${process.env.FIRESTORE_EMULATOR_HOST}`).hostname;
  if (!["127.0.0.1", "localhost", "[::1]"].includes(host)) throw new Error("Only a loopback emulator is permitted");
  env = await initializeTestEnvironment({ projectId, firestore: { rules: fs.readFileSync(require("node:path").join(__dirname, "../../firestore.rules"), "utf8") } });
});
after(async () => { if (env) await env.cleanup(); });
beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (c) => {
    const d = c.firestore();
    for (const [p, value] of Object.entries({
      "Admins/admin": { adminStatus: "Active" }, "Admins/inactive": { adminStatus: "Inactive" },
      "Users/reader": { userId: "reader", role: "Reader", cardStatus: "HoatDong", mustChangePassword: true },
      "Users/other": { userId: "other", role: "Reader" },
      "Categories/cat": category, "BookTitles/book": book, "BookCopies/copy": copy,
    })) await setDoc(doc(d, p), value);
  });
});
test("anonymous cannot read inventory or users; reader sees own profile only", async () => {
  await assertFails(getDoc(doc(db(), "BookTitles/book")));
  await assertFails(getDoc(doc(db(), "Users/reader")));
  await assertSucceeds(getDoc(doc(db("reader"), "Users/reader")));
  await assertFails(getDoc(doc(db("reader"), "Users/other")));
  await assertSucceeds(getDoc(doc(db("reader"), "BookTitles/book")));
});
test("reader and inactive admin cannot mutate inventory or promote themselves", async () => {
  for (const uid of ["reader", "inactive"]) {
    await assertFails(updateDoc(doc(db(uid), "BookTitles/book"), { availableCopies: 0 }));
    await assertFails(setDoc(doc(db(uid), "Admins/reader"), { adminStatus: "Active" }));
  }
  await assertFails(updateDoc(doc(db("reader"), "Users/reader"), { role: "Admin" }));
  await assertSucceeds(updateDoc(doc(db("reader"), "Users/reader"), { mustChangePassword: false, updatedAt: serverTimestamp() }));
});
test("active admin can create category/title/copy atomically in dependency order", async () => {
  const d = db("admin"), b = writeBatch(d);
  b.set(doc(d, "Categories/new"), { ...category, categoryId: "new" });
  b.set(doc(d, "BookTitles/new"), { ...book, categoryId: "new" });
  b.set(doc(d, "BookCopies/new"), { ...copy, bookTitleId: "new", barcode: "new" });
  await assertSucceeds(b.commit());
});
test("catalogue rejects legacy category names, dangling categories and invalid counters", async () => {
  const ref = doc(db("admin"), "BookTitles/book");
  await assertFails(updateDoc(ref, { category: "Văn học" }));
  await assertFails(updateDoc(ref, { categoryId: "missing" }));
  await assertFails(updateDoc(ref, { availableCopies: 2 }));
  await assertFails(updateDoc(ref, { totalCopies: -1 }));
  await assertFails(deleteDoc(ref));
});
test("copy identity/parent cannot change, and borrowed/held copies cannot be retired", async () => {
  const ref = doc(db("admin"), "BookCopies/copy");
  await assertFails(setDoc(doc(db("admin"), "BookCopies/orphan"), { ...copy, bookTitleId: "missing" }));
  await assertFails(updateDoc(ref, { barcode: "replacement" }));
  await assertFails(updateDoc(ref, { bookTitleId: "replacement" }));
  await assertSucceeds(updateDoc(ref, { copyStatus: "DaMuon" }));
  await assertFails(updateDoc(ref, { copyStatus: "ThanhLy" }));
  await assertFails(updateDoc(ref, { isHidden: true }));
  await assertFails(deleteDoc(ref));
});
test("reserved copy must identify a real priority reader", async () => {
  const ref = doc(db("admin"), "BookCopies/copy");
  await assertFails(updateDoc(ref, { copyStatus: "DaDatTruoc", priorityUserId: null }));
  await assertFails(updateDoc(ref, { copyStatus: "DaDatTruoc", priorityUserId: "missing" }));
  await assertSucceeds(updateDoc(ref, { copyStatus: "DaDatTruoc", priorityUserId: "reader" }));
});
function registration(d, id, email = "reader@example.com", phone = "0912345678") {
  const b = writeBatch(d);
  b.set(doc(d, `RegistrationRequests/${id}`), { fullName: "New Reader", birthDate: "2000-01-01", email, phone, termsAccepted: true, termsAcceptedAt: serverTimestamp(), status: "Pending", emailVerified: true, phoneVerified: false, rejectionReason: "", createdAt: serverTimestamp() });
  b.set(doc(d, `RegistrationEmailIndex/${email}`), { value: email, requestId: id, createdAt: serverTimestamp() });
  b.set(doc(d, `RegistrationPhoneIndex/${phone}`), { value: phone, requestId: id, createdAt: serverTimestamp() });
  return b;
}
test("public registration requires atomic unique indices; replay cannot duplicate a request", async () => {
  await assertSucceeds(registration(db(), "r1").commit());
  await assertFails(registration(db(), "r2").commit());
  await assertFails(getDoc(doc(db(), "RegistrationRequests/r1")));
  await assertFails(updateDoc(doc(db("reader"), "RegistrationRequests/r1"), { status: "Approved" }));
});
test("approval must match a reader profile in the same atomic result; review is final", async () => {
  await registration(db(), "r1").commit();
  const d = db("admin"), ref = doc(d, "RegistrationRequests/r1");
  const changes = { status: "Approved", reviewedBy: "admin", reviewedAt: serverTimestamp(), userId: "new-reader", barcode: "TV-new" };
  await assertFails(updateDoc(ref, changes));
  const b = writeBatch(d);
  b.set(doc(d, "Users/new-reader"), { userId: "new-reader", fullName: "New Reader", dateOfBirth: "2000-01-01", email: "reader@example.com", phone: "0912345678", role: "Reader", cardStatus: "HoatDong", barcode: "TV-new", mustChangePassword: true });
  b.update(ref, changes);
  await assertSucceeds(b.commit());
  await assertFails(updateDoc(ref, { status: "Rejected", rejectionReason: "Too late", reviewedBy: "admin", reviewedAt: serverTimestamp() }));
});
test("rejection needs active admin and bounded reason; replay cannot review twice", async () => {
  await registration(db(), "r1").commit();
  const changes = { status: "Rejected", rejectionReason: "Invalid request", reviewedBy: "admin", reviewedAt: serverTimestamp() };
  await assertFails(updateDoc(doc(db("inactive"), "RegistrationRequests/r1"), changes));
  await assertFails(updateDoc(doc(db("admin"), "RegistrationRequests/r1"), { ...changes, rejectionReason: "x" }));
  await assertSucceeds(updateDoc(doc(db("admin"), "RegistrationRequests/r1"), changes));
  await assertFails(updateDoc(doc(db("admin"), "RegistrationRequests/r1"), changes));
});

test("session checks can read own missing admin marker but cannot grant admin rights", async () => {
  const own = await assertSucceeds(getDoc(doc(db("reader"), "Admins/reader")));
  if (own.exists()) throw new Error("Fixture must not grant Reader an Admin record");
  await assertFails(getDoc(doc(db("reader"), "Admins/admin")));
  await assertFails(setDoc(doc(db("reader"), "Users/forged"), { userId:"forged", role:"Admin", cardStatus:"HoatDong", email:"forged@example.com", phone:"0912345678" }));
  await assertFails(updateDoc(doc(db("reader"), "Users/reader"), { cardStatus:"HoatDong", mustChangePassword:false, role:"Admin" }));
  await assertFails(setDoc(doc(db(), "Users/public"), { userId:"public", role:"Reader", cardStatus:"HoatDong" }));
});
test("registration cannot smuggle role, uid or a pre-approved profile", async () => {
  const d=db();
  for (const extra of [{role:"Admin"},{userId:"admin"},{status:"Approved"}]) {
    const batch=registration(d,"forged");
    batch.update(doc(d,"RegistrationRequests/forged"),extra);
    await assertFails(batch.commit());
  }
});
