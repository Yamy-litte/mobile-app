"use strict";
const { before, after, beforeEach, test } = require("node:test");
const fs = require("node:fs");
const { URL } = require("node:url");
const { initializeTestEnvironment, assertSucceeds, assertFails } = require("@firebase/rules-unit-testing");
const { collection, query, where, getDocs, getCountFromServer, doc, setDoc, getDoc, updateDoc, deleteDoc, deleteField, writeBatch, serverTimestamp, Timestamp } = require("firebase/firestore");
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


test("admin dashboard counts use existing read permissions and deny reader profile enumeration", async () => {
  const assert=require("node:assert/strict");
  const admin=db("admin");
  for(const name of ["BookTitles","BookCopies"]) {
    const count=await assertSucceeds(getCountFromServer(collection(admin,name)));
    assert.equal(count.data().count,1);
  }
  const readers=await assertSucceeds(getCountFromServer(query(collection(admin,"Users"),where("role","==","Reader"))));
  assert.equal(readers.data().count,2);
  await assertFails(getCountFromServer(query(collection(db("reader"),"Users"),where("role","==","Reader"))));
});

test("retirement cannot restart history or discard repair/priority copies", async () => {
  for (const state of ["DangSuaChua","ThanhLy","DaMat"]) {
    await env.withSecurityRulesDisabled(c => updateDoc(doc(c.firestore(),"BookCopies/copy"),{copyStatus:state,isHidden:true}));
    await assertFails(updateDoc(doc(db("admin"),"BookCopies/copy"),{copyStatus:"SanSang",isHidden:false}));
  }
  await env.withSecurityRulesDisabled(c => updateDoc(doc(c.firestore(),"BookCopies/copy"),{copyStatus:"SanSang",isHidden:false,priorityUserId:"reader"}));
  await assertFails(updateDoc(doc(db("admin"),"BookCopies/copy"),{copyStatus:"ThanhLy",isHidden:true}));
});
test("hiding or retiring a ready copy must decrement parent atomically", async () => {
  const d=db("admin");
  await assertFails(updateDoc(doc(d,"BookCopies/copy"),{copyStatus:"ThanhLy",isHidden:true}));
  const b=writeBatch(d);
  b.update(doc(d,"BookCopies/copy"),{copyStatus:"ThanhLy",isHidden:true});
  b.update(doc(d,"BookTitles/book"),{availableCopies:0});
  await assertSucceeds(b.commit());
  const parent=await getDoc(doc(d,"BookTitles/book"));
  require("node:assert/strict").equal(parent.data().totalCopies,1);
});
test("hidden title can be restored by active admin without changing copy counters", async () => {
  const d = db("admin"), title = doc(d, "BookTitles/book");
  await assertSucceeds(updateDoc(title, { isHidden: true }));
  await assertSucceeds(updateDoc(title, { isHidden: false }));
  await assertFails(updateDoc(doc(db("reader"), "BookTitles/book"), { isHidden: true }));
});
test("only repair-hidden copy may be restored, with matching parent stock increment", async () => {
  const d = db("admin");
  const parent = doc(d, "BookTitles/book"), child = doc(d, "BookCopies/copy");
  const hide = writeBatch(d);
  hide.update(child, { copyStatus: "DangSuaChua", isHidden: true });
  hide.update(parent, { availableCopies: 0 });
  await assertSucceeds(hide.commit());
  await assertFails(updateDoc(child, { copyStatus: "SanSang", isHidden: false }));
  const restored = writeBatch(d);
  restored.update(child, { copyStatus: "SanSang", isHidden: false, updatedAt: serverTimestamp() });
  restored.update(parent, { availableCopies: 1 });
  await assertSucceeds(restored.commit());
  const updatedParent = await getDoc(parent);
  require("node:assert/strict").equal(updatedParent.data().availableCopies, 1);
  require("node:assert/strict").equal(updatedParent.data().totalCopies, 1);
});
test("hidden repair cannot be restored when title is hidden or priorities exist", async () => {
  const d = db("admin"), parent = doc(d, "BookTitles/book"), child = doc(d, "BookCopies/copy");
  await env.withSecurityRulesDisabled(c => {
    const b = writeBatch(c.firestore());
    b.update(doc(c.firestore(), "BookTitles/book"), { isHidden: true, availableCopies: 0 });
    b.update(doc(c.firestore(), "BookCopies/copy"), { isHidden: true, copyStatus: "DangSuaChua" });
    return b.commit();
  });
  let attempt = writeBatch(d);
  attempt.update(child, { copyStatus: "SanSang", isHidden: false });
  attempt.update(parent, { availableCopies: 1 });
  await assertFails(attempt.commit());
  await assertSucceeds(updateDoc(parent, { isHidden: false }));
  await env.withSecurityRulesDisabled(c =>
    updateDoc(doc(c.firestore(), "BookCopies/copy"), { priorityUserId: "reader" }));
  attempt = writeBatch(d);
  attempt.update(child, { copyStatus: "SanSang", isHidden: false });
  attempt.update(parent, { availableCopies: 1 });
  await assertFails(attempt.commit());
});
test("retired and lost copies remain non-restorable even with a parent counter increase", async () => {
  const d = db("admin"), parent = doc(d, "BookTitles/book"), child = doc(d, "BookCopies/copy");
  for (const status of ["ThanhLy", "DaMat"]) {
    await env.withSecurityRulesDisabled(c => {
      const b = writeBatch(c.firestore());
      b.update(doc(c.firestore(), "BookTitles/book"), { availableCopies: 0 });
      b.update(doc(c.firestore(), "BookCopies/copy"), { isHidden: true, copyStatus: status });
      return b.commit();
    });
    const attempt = writeBatch(d);
    attempt.update(child, { copyStatus: "SanSang", isHidden: false });
    attempt.update(parent, { availableCopies: 1 });
    await assertFails(attempt.commit());
  }
});
test("five-copy intake fits access budget and invalid child rolls back parent", async () => {
  const d=db("admin"), b=writeBatch(d);
  b.update(doc(d,"BookTitles/book"),{totalCopies:6,availableCopies:6});
  for(let i=0;i<5;i++)b.set(doc(d,`BookCopies/intake-${i}`),{...copy,barcode:`BOOK-${i+2}`});
  await assertSucceeds(b.commit());
  const bad=writeBatch(d);
  bad.update(doc(d,"BookTitles/book"),{totalCopies:7,availableCopies:7});
  bad.set(doc(d,"BookCopies/bad"),{...copy,copyStatus:"Invented"});
  await assertFails(bad.commit());
  require("node:assert/strict").equal((await getDoc(doc(d,"BookTitles/book"))).data().totalCopies,6);
});


function onlineRequestFixture() {
  return {
    requestId:"reader_book", userId:"reader", bookTitleId:"book",
    bookTitle:"Real inventory", loanDays:14, status:"Pending", attempt:1,
    createdAt:serverTimestamp(), updatedAt:serverTimestamp()
  };
}
function onlineNoticeFixture(status, message) {
  return {
    requestId:"reader_book", userId:"reader", status, attempt:1,
    title:"Yêu cầu mượn Online", message,
    createdAt:serverTimestamp(), readAt:null
  };
}
test("reader submits only their own active request, never a preorder or duplicate", async () => {
  const own = db("reader"), request = doc(own,"OnlineLoanRequests/reader_book");
  await assertFails(setDoc(doc(db("other"), "OnlineLoanRequests/reader_book"), onlineRequestFixture()));
  await assertFails(setDoc(request, {...onlineRequestFixture(), loanDays:0}));
  await assertFails(setDoc(request, {...onlineRequestFixture(), status:"Approved"}));
  await assertFails(setDoc(request, {...onlineRequestFixture(), bookTitleId:"other"}));
  await assertSucceeds(setDoc(request, onlineRequestFixture()));
  await assertFails(setDoc(request, onlineRequestFixture()));
  await assertSucceeds(getDoc(request));
  await assertFails(getDoc(doc(db("other"), "OnlineLoanRequests/reader_book")));
  const owned = await assertSucceeds(getDocs(query(collection(own, "OnlineLoanRequests"), where("userId","==","reader"))));
  require("node:assert/strict").equal(owned.docs.length,1);
});
test("Admin rejection atomically creates a private Reader notification; Reader can resubmit", async () => {
  const requestReader = doc(db("reader"), "OnlineLoanRequests/reader_book");
  await setDoc(requestReader, onlineRequestFixture());
  const admin = db("admin"), request = doc(admin,"OnlineLoanRequests/reader_book");
  const notification = doc(admin,"OnlineLoanNotifications/reader_book_v1_Rejected");
  const review = {
    status:"Rejected", rejectionReason:"Không thể phục vụ hôm nay",
    reviewedBy:"admin", reviewedAt:serverTimestamp(),
    notificationId:notification.id, updatedAt:serverTimestamp()
  };
  await assertFails(updateDoc(request, review));
  const batch=writeBatch(admin);
  batch.update(request,review);
  batch.set(notification, onlineNoticeFixture("Rejected", "Vui lòng thử lại sau"));
  await assertSucceeds(batch.commit());
  await assertFails(updateDoc(request,{status:"Approved"}));
  await assertFails(updateDoc(doc(db("reader"),"OnlineLoanNotifications/reader_book_v1_Rejected"),{message:"Forged"}));
  await assertSucceeds(updateDoc(doc(db("reader"),"OnlineLoanNotifications/reader_book_v1_Rejected"),{readAt:serverTimestamp()}));
  await assertFails(updateDoc(doc(db("reader"),"OnlineLoanNotifications/reader_book_v1_Rejected"),{readAt:serverTimestamp()}));
  await assertFails(getDoc(doc(db("other"),"OnlineLoanNotifications/reader_book_v1_Rejected")));
  const retry = {
    status:"Pending", attempt:2, loanDays:7,
    createdAt:serverTimestamp(), updatedAt:serverTimestamp(),
    reviewedBy:deleteField(), reviewedAt:deleteField(), notificationId:deleteField(),
    rejectionReason:deleteField()
  };
  await assertSucceeds(updateDoc(requestReader,retry));
});
test("Admin approval holds exactly one available copy and creates one Reader notification", async () => {
  const d=db("admin"), request=doc(d,"OnlineLoanRequests/reader_book");
  await assertSucceeds(setDoc(doc(db("reader"),"OnlineLoanRequests/reader_book"),onlineRequestFixture()));
  const notification=doc(d,"OnlineLoanNotifications/reader_book_v1_Approved");
  const decision={
    status:"Approved", copyId:"copy", reviewedBy:"admin",
    reviewedAt:serverTimestamp(), updatedAt:serverTimestamp(),
    notificationId:notification.id,
    holdUntil:Timestamp.fromDate(new Date(Date.now()+24*3600000))
  };
  await assertFails(updateDoc(request, decision));
  const batch=writeBatch(d);
  batch.update(request,decision);
  batch.update(doc(d,"BookCopies/copy"),{copyStatus:"ChoLay",priorityUserId:"reader",updatedAt:serverTimestamp()});
  batch.update(doc(d,"BookTitles/book"),{availableCopies:0,updatedAt:serverTimestamp()});
  batch.set(notification,onlineNoticeFixture("Approved","Đến nhận sách trong 24 giờ"));
  await assertSucceeds(batch.commit());
  require("node:assert/strict").equal((await getDoc(doc(d,"BookTitles/book"))).data().availableCopies,0);
  require("node:assert/strict").equal((await getDoc(doc(d,"BookCopies/copy"))).data().copyStatus,"ChoLay");
  await assertFails(updateDoc(request,decision));
  await assertFails(updateDoc(doc(db("reader"),"OnlineLoanRequests/reader_book"),{status:"Rejected"}));
});
test("expired online hold returns reserved copy once; unauthorized reader cannot expire it", async () => {
  const now=new Date(Date.now()-2*3600000), older=new Date(Date.now()-26*3600000);
  await env.withSecurityRulesDisabled(async c => {
    const d=c.firestore(), batch=writeBatch(d);
    batch.set(doc(d,"OnlineLoanRequests/reader_book"),{
      requestId:"reader_book", userId:"reader", bookTitleId:"book",
      bookTitle:"Real inventory", loanDays:14, attempt:1,
      status:"Approved", createdAt:Timestamp.fromDate(older),
      updatedAt:Timestamp.fromDate(older), copyId:"copy", reviewedBy:"admin",
      reviewedAt:Timestamp.fromDate(older), holdUntil:Timestamp.fromDate(now),
      notificationId:"reader_book_v1_Approved"
    });
    batch.update(doc(d,"BookCopies/copy"),{copyStatus:"ChoLay",priorityUserId:"reader"});
    batch.update(doc(d,"BookTitles/book"),{availableCopies:0});
    await batch.commit();
  });
  const d=db("admin"), request=doc(d,"OnlineLoanRequests/reader_book");
  const notification=doc(d,"OnlineLoanNotifications/reader_book_v1_Expired");
  await assertFails(updateDoc(doc(db("reader"),"OnlineLoanRequests/reader_book"),{status:"Expired"}));
  const release=writeBatch(d);
  release.update(request,{status:"Expired",notificationId:notification.id,updatedAt:serverTimestamp()});
  release.update(doc(d,"BookCopies/copy"),{copyStatus:"SanSang",priorityUserId:null,updatedAt:serverTimestamp()});
  release.update(doc(d,"BookTitles/book"),{availableCopies:1,updatedAt:serverTimestamp()});
  release.set(notification,onlineNoticeFixture("Expired","Đã hết thời gian giữ sách."));
  await assertSucceeds(release.commit());
  require("node:assert/strict").equal((await getDoc(doc(d,"BookTitles/book"))).data().availableCopies,1);
  const duplicate = writeBatch(d);
  duplicate.update(request,{status:"Expired",notificationId:notification.id,updatedAt:serverTimestamp()});
  duplicate.update(doc(d,"BookCopies/copy"),{copyStatus:"SanSang",priorityUserId:null,updatedAt:serverTimestamp()});
  duplicate.update(doc(d,"BookTitles/book"),{availableCopies:2,updatedAt:serverTimestamp()});
  await assertFails(duplicate.commit());
});

test("Online handover checks linked copy and notification; Reader cannot forge checkout", async () => {
  const now = new Date();
  await env.withSecurityRulesDisabled(async c => {
    const d=c.firestore(), batch=writeBatch(d);
    batch.set(doc(d,"OnlineLoanRequests/reader_book"),{
      requestId:"reader_book",userId:"reader",bookTitleId:"book",bookTitle:"Real inventory",
      loanDays:7,attempt:1,status:"Approved",createdAt:Timestamp.fromDate(now),
      updatedAt:Timestamp.fromDate(now),reviewedBy:"admin",reviewedAt:Timestamp.fromDate(now),
      notificationId:"reader_book_v1_Approved",copyId:"copy",
      holdUntil:Timestamp.fromDate(new Date(Date.now()+24*3600000))
    });
    batch.update(doc(d,"BookCopies/copy"),{copyStatus:"ChoLay",priorityUserId:"reader"});
    batch.update(doc(d,"BookTitles/book"),{availableCopies:0});
    await batch.commit();
  });
  const d=db("admin"), request=doc(d,"OnlineLoanRequests/reader_book");
  const due=Timestamp.fromDate(new Date(Date.now()+7*86400000));
  const notification=doc(d,"OnlineLoanNotifications/reader_book_v1_Collected");
  const issued={status:"Collected",collectedBy:"admin",collectedAt:serverTimestamp(),
      dueAt:due,updatedAt:serverTimestamp(),notificationId:notification.id};
  await assertFails(updateDoc(request,issued));
  await assertFails(updateDoc(doc(db("reader"),"OnlineLoanRequests/reader_book"),issued));
  const batch=writeBatch(d);
  batch.update(request,issued);
  batch.update(doc(d,"BookCopies/copy"),{copyStatus:"DaMuon",priorityUserId:null,
      updatedAt:serverTimestamp()});
  batch.set(notification,onlineNoticeFixture("Collected","Đã nhận sách tại quầy."));
  await assertSucceeds(batch.commit());
  const title=await getDoc(doc(d,"BookTitles/book"));
  require("node:assert/strict").equal(title.data().availableCopies,0);
  require("node:assert/strict").equal((await getDoc(request)).data().status,"Collected");
  await assertFails(updateDoc(request,{status:"Approved"}));
});

test("Online return restores availability once; Reader can request again only after return", async () => {
  const now=new Date();
  await env.withSecurityRulesDisabled(async c => {
    const d=c.firestore(), batch=writeBatch(d);
    batch.set(doc(d,"OnlineLoanRequests/reader_book"),{
      requestId:"reader_book", userId:"reader", bookTitleId:"book",
      bookTitle:"Real inventory", loanDays:7, attempt:1, status:"Collected",
      createdAt:Timestamp.fromDate(now),updatedAt:Timestamp.fromDate(now),
      reviewedBy:"admin",reviewedAt:Timestamp.fromDate(now),
      notificationId:"reader_book_v1_Collected",copyId:"copy",
      holdUntil:Timestamp.fromDate(new Date(Date.now()+24*3600000)),
      collectedAt:Timestamp.fromDate(now),collectedBy:"admin",
      dueAt:Timestamp.fromDate(new Date(Date.now()+7*86400000))
    });
    batch.update(doc(d,"BookCopies/copy"),{copyStatus:"DaMuon",priorityUserId:null});
    batch.update(doc(d,"BookTitles/book"),{availableCopies:0});
    await batch.commit();
  });
  const d=db("admin"), request=doc(d,"OnlineLoanRequests/reader_book");
  const notification=doc(d,"OnlineLoanNotifications/reader_book_v1_Returned");
  await assertFails(updateDoc(request,{status:"Returned",returnedBy:"admin",
    returnedAt:serverTimestamp(),updatedAt:serverTimestamp(),notificationId:notification.id}));
  const batch=writeBatch(d);
  batch.update(request,{status:"Returned",returnedBy:"admin",
    returnedAt:serverTimestamp(),updatedAt:serverTimestamp(),notificationId:notification.id});
  batch.update(doc(d,"BookCopies/copy"),{copyStatus:"SanSang",priorityUserId:null,
    updatedAt:serverTimestamp()});
  batch.update(doc(d,"BookTitles/book"),{availableCopies:1,updatedAt:serverTimestamp()});
  batch.set(notification,onlineNoticeFixture("Returned","Đã nhận trả sách."));
  await assertSucceeds(batch.commit());
  require("node:assert/strict").equal((await getDoc(doc(d,"BookTitles/book"))).data().availableCopies,1);
  const again=writeBatch(d);
  again.update(request,{status:"Returned",updatedAt:serverTimestamp()});
  again.update(doc(d,"BookTitles/book"),{availableCopies:2});
  await assertFails(again.commit());
  const reapply={
    status:"Pending",attempt:2,loanDays:7,
    createdAt:serverTimestamp(),updatedAt:serverTimestamp(),
    copyId:deleteField(),holdUntil:deleteField(),reviewedBy:deleteField(),
    reviewedAt:deleteField(),notificationId:deleteField(),
    collectedAt:deleteField(),collectedBy:deleteField(),dueAt:deleteField(),
    returnedAt:deleteField(),returnedBy:deleteField()
  };
  await assertSucceeds(updateDoc(doc(db("reader"),"OnlineLoanRequests/reader_book"),reapply));
  await assertFails(updateDoc(doc(db("reader"),"OnlineLoanRequests/reader_book"),{status:"Collected"}));
});
