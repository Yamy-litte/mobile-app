const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { defineSecret } = require("firebase-functions/params");
const { initializeApp } = require("firebase-admin/app");
const { getAuth } = require("firebase-admin/auth");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const nodemailer = require("nodemailer");

initializeApp();

const smtpUser = defineSecret("SMTP_USER");
const smtpPass = defineSecret("SMTP_PASS");
const db = getFirestore();

async function requireAdmin(request) {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Bạn cần đăng nhập tài khoản quản trị.");
  }
  const adminDoc = await db.collection("Admins").doc(request.auth.uid).get();
  const status = adminDoc.get("adminStatus");
  if (!adminDoc.exists || !["Active", "HoatDong"].includes(String(status))) {
    throw new HttpsError("permission-denied", "Tài khoản không có quyền quản trị đang hoạt động.");
  }
}

function normalizeEmail(value) {
  return String(value || "").trim().toLowerCase();
}

async function sendRejectionEmail(email, fullName, reason) {
  const host = process.env.SMTP_HOST;
  const port = Number(process.env.SMTP_PORT || 465);
  const from = process.env.SMTP_FROM;
  if (!host || !from) {
    console.warn("SMTP is not configured. Rejection status was saved, but email was not sent.");
    return false;
  }
  const transporter = nodemailer.createTransport({
    host,
    port,
    secure: port === 465,
    auth: { user: smtpUser.value(), pass: smtpPass.value() }
  });
  await transporter.sendMail({
    from,
    to: email,
    subject: "Kết quả đăng ký thẻ Library Online",
    text: [
      "Xin chào " + (fullName || "bạn") + ",",
      "",
      "Rất tiếc, yêu cầu đăng ký thẻ thư viện của bạn chưa được chấp thuận.",
      "Lý do: " + reason,
      "",
      "Bạn có thể liên hệ thư viện để được giải đáp hoặc gửi lại yêu cầu sau khi khắc phục thông tin.",
      "",
      "Trân trọng,",
      "Ban quản lý Library Online"
    ].join("\n")
  });
  return true;
}


exports.submitRegistration = onCall(async (request) => {
  const input = request.data || {};
  const fullName = String(input.fullName || "").trim();
  const birthDate = String(input.birthDate || "").trim();
  const email = normalizeEmail(input.email);
  const phone = String(input.phone || "").trim();
  const termsAccepted = input.termsAccepted === true;

  if (fullName.length < 2 || fullName.length > 120) {
    throw new HttpsError("invalid-argument", "Họ tên phải từ 2 đến 120 ký tự.");
  }
  if (!/^\d{4}-\d{2}-\d{2}$/.test(birthDate)) {
    throw new HttpsError("invalid-argument", "Ngày sinh không hợp lệ.");
  }
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    throw new HttpsError("invalid-argument", "Email không đúng định dạng.");
  }
  if (!/^\+?84[0-9]{9,10}$|^0[0-9]{9}$/.test(phone)) {
    throw new HttpsError("invalid-argument", "Số điện thoại không đúng định dạng.");
  }
  if (!termsAccepted) {
    throw new HttpsError("failed-precondition", "Bạn cần đồng ý điều khoản đăng ký.");
  }

  const existingUserEmail = await db.collection("Users").where("email", "==", email).limit(1).get();
  if (!existingUserEmail.empty) {
    throw new HttpsError("already-exists", "Email đã được sử dụng cho tài khoản độc giả.");
  }
  const existingUserPhone = await db.collection("Users").where("phone", "==", phone).limit(1).get();
  if (!existingUserPhone.empty) {
    throw new HttpsError("already-exists", "Số điện thoại đã được sử dụng.");
  }

  const [emailRequests, phoneRequests] = await Promise.all([
    db.collection("RegistrationRequests").where("email", "==", email).get(),
    db.collection("RegistrationRequests").where("phone", "==", phone).get()
  ]);
  const activeStatuses = ["Pending", "PendingVerification", "Approved"];
  if (emailRequests.docs.some((doc) => activeStatuses.includes(String(doc.get("status"))))) {
    throw new HttpsError("already-exists", "Email đã có yêu cầu đăng ký đang xử lý.");
  }
  if (phoneRequests.docs.some((doc) => activeStatuses.includes(String(doc.get("status"))))) {
    throw new HttpsError("already-exists", "Số điện thoại đã có yêu cầu đăng ký đang xử lý.");
  }

  const ref = db.collection("RegistrationRequests").doc();
  await ref.set({
    fullName,
    birthDate,
    email,
    phone,
    termsAccepted: true,
    termsAcceptedAt: FieldValue.serverTimestamp(),
    status: "Pending",
    emailVerified: false,
    phoneVerified: false,
    rejectionReason: "",
    createdAt: FieldValue.serverTimestamp()
  });
  return { success: true, requestId: ref.id };
});

exports.approveRegistration = onCall(async (request) => {
  await requireAdmin(request);
  const requestId = String((request.data && request.data.requestId) || "").trim();
  if (!requestId) throw new HttpsError("invalid-argument", "Thiếu mã yêu cầu đăng ký.");

  const requestRef = db.collection("RegistrationRequests").doc(requestId);
  const snap = await requestRef.get();
  if (!snap.exists) throw new HttpsError("not-found", "Không tìm thấy yêu cầu đăng ký.");

  const data = snap.data();
  if (!["Pending", "PendingVerification"].includes(String(data.status))) {
    throw new HttpsError("failed-precondition", "Yêu cầu này đã được xử lý hoặc không còn chờ duyệt.");
  }

  const email = normalizeEmail(data.email);
  const phone = String(data.phone || "").trim();
  if (!email || !phone || !data.fullName) {
    throw new HttpsError("failed-precondition", "Hồ sơ thiếu họ tên, email hoặc số điện thoại.");
  }

  const existingByEmail = await db.collection("Users").where("email", "==", email).limit(1).get();
  if (!existingByEmail.empty) {
    throw new HttpsError("already-exists", "Email đã được sử dụng.");
  }
  const existingByPhone = await db.collection("Users").where("phone", "==", phone).limit(1).get();
  if (!existingByPhone.empty) {
    throw new HttpsError("already-exists", "Số điện thoại đã được sử dụng.");
  }

  let userRecord;
  try {
    userRecord = await getAuth().createUser({
      email,
      emailVerified: false,
      disabled: false,
      displayName: String(data.fullName)
    });
  } catch (error) {
    if (error.code === "auth/email-already-exists") {
      throw new HttpsError("already-exists", "Email đã tồn tại trong Firebase Authentication.");
    }
    throw new HttpsError("internal", "Không thể tạo tài khoản Firebase Authentication.");
  }

  const barcode = "TV-" + userRecord.uid.substring(0, 8).toUpperCase();
  const userData = {
    userId: userRecord.uid,
    fullName: String(data.fullName),
    dateOfBirth: String(data.birthDate || data.dateOfBirth || ""),
    email,
    phone,
    barcode,
    role: "Reader",
    cardStatus: "HoatDong",
    createdAt: FieldValue.serverTimestamp(),
    updatedAt: FieldValue.serverTimestamp()
  };

  try {
    const batch = db.batch();
    batch.set(db.collection("Users").doc(userRecord.uid), userData);
    batch.update(requestRef, {
      status: "Approved",
      reviewedBy: request.auth.uid,
      reviewedAt: FieldValue.serverTimestamp(),
      userId: userRecord.uid,
      barcode
    });
    await batch.commit();
  } catch (error) {
    await getAuth().deleteUser(userRecord.uid).catch(() => {});
    throw new HttpsError("internal", "Không thể lưu hồ sơ đã duyệt. Tài khoản tạo dở đã được dọn nếu có thể.");
  }

  return { success: true, email, userId: userRecord.uid, barcode };
});

exports.rejectRegistration = onCall({ secrets: [smtpUser, smtpPass] }, async (request) => {
  await requireAdmin(request);
  const requestId = String((request.data && request.data.requestId) || "").trim();
  const reason = String((request.data && request.data.reason) || "").trim();
  if (!requestId) throw new HttpsError("invalid-argument", "Thiếu mã yêu cầu đăng ký.");
  if (reason.length < 3 || reason.length > 500) {
    throw new HttpsError("invalid-argument", "Lý do từ chối phải từ 3 đến 500 ký tự.");
  }

  const ref = db.collection("RegistrationRequests").doc(requestId);
  const snap = await ref.get();
  if (!snap.exists) throw new HttpsError("not-found", "Không tìm thấy yêu cầu đăng ký.");
  const data = snap.data();
  if (!["Pending", "PendingVerification"].includes(String(data.status))) {
    throw new HttpsError("failed-precondition", "Yêu cầu này đã được xử lý.");
  }

  await ref.update({
    status: "Rejected",
    rejectionReason: reason,
    reviewedBy: request.auth.uid,
    reviewedAt: FieldValue.serverTimestamp()
  });

  let emailSent = false;
  try {
    emailSent = await sendRejectionEmail(normalizeEmail(data.email), String(data.fullName || ""), reason);
  } catch (error) {
    console.error("Rejection email failed", error);
  }
  return { success: true, emailSent };
});

const SAMPLE_BOOKS = [
  {
    "bookTitleId": "BOOK001",
    "title": "Sapiens: Lược sử loài người",
    "author": "Yuval Noah Harari",
    "isbn": "9780062315007",
    "category": "Lịch sử",
    "publisher": "Harper",
    "publishYear": 2015,
    "description": "Hành trình nhìn lại lịch sử loài người từ thời đồ đá đến thế giới hiện đại.",
    "coverUrl": "https://covers.openlibrary.org/b/isbn/9780062315007-L.jpg",
    "totalCopies": 4,
    "availableCopies": 4,
    "isHidden": false
  },
  {
    "bookTitleId": "BOOK002",
    "title": "Atomic Habits: Thay đổi tí hon, hiệu quả bất ngờ",
    "author": "James Clear",
    "isbn": "9780735211292",
    "category": "Tâm lý",
    "publisher": "Avery",
    "publishYear": 2018,
    "description": "Phương pháp xây dựng thói quen tốt bằng những thay đổi nhỏ và bền vững.",
    "coverUrl": "https://covers.openlibrary.org/b/isbn/9780735211292-L.jpg",
    "totalCopies": 5,
    "availableCopies": 4,
    "isHidden": false
  },
  {
    "bookTitleId": "BOOK003",
    "title": "Đắc Nhân Tâm",
    "author": "Dale Carnegie",
    "isbn": "9780671027032",
    "category": "Tâm lý",
    "publisher": "Simon & Schuster",
    "publishYear": 1998,
    "description": "Những nguyên tắc giao tiếp và xây dựng quan hệ giữa người với người.",
    "coverUrl": "https://covers.openlibrary.org/b/isbn/9780671027032-L.jpg",
    "totalCopies": 6,
    "availableCopies": 5,
    "isHidden": false
  },
  {
    "bookTitleId": "BOOK004",
    "title": "1984",
    "author": "George Orwell",
    "isbn": "9780451524935",
    "category": "Văn học",
    "publisher": "Signet Classics",
    "publishYear": 1950,
    "description": "Tiểu thuyết phản địa đàng kinh điển về quyền lực, giám sát và tự do cá nhân.",
    "coverUrl": "https://covers.openlibrary.org/b/isbn/9780451524935-L.jpg",
    "totalCopies": 3,
    "availableCopies": 2,
    "isHidden": false
  },
  {
    "bookTitleId": "BOOK005",
    "title": "Giết con chim nhại",
    "author": "Harper Lee",
    "isbn": "9780061120084",
    "category": "Văn học",
    "publisher": "Harper Perennial",
    "publishYear": 2006,
    "description": "Tác phẩm kinh điển về công lý, lòng trắc ẩn và tuổi thơ ở miền Nam nước Mỹ.",
    "coverUrl": "https://covers.openlibrary.org/b/isbn/9780061120084-L.jpg",
    "totalCopies": 4,
    "availableCopies": 4,
    "isHidden": false
  },
  {
    "bookTitleId": "BOOK006",
    "title": "The Great Gatsby",
    "author": "F. Scott Fitzgerald",
    "isbn": "9780743273565",
    "category": "Văn học",
    "publisher": "Scribner",
    "publishYear": 2004,
    "description": "Bức chân dung nước Mỹ thập niên 1920 qua giấc mơ và bi kịch của Jay Gatsby.",
    "coverUrl": "https://covers.openlibrary.org/b/isbn/9780743273565-L.jpg",
    "totalCopies": 3,
    "availableCopies": 3,
    "isHidden": false
  },
  {
    "bookTitleId": "BOOK007",
    "title": "Pride and Prejudice",
    "author": "Jane Austen",
    "isbn": "9780141439518",
    "category": "Văn học",
    "publisher": "Penguin Classics",
    "publishYear": 2002,
    "description": "Tiểu thuyết về tình yêu, định kiến xã hội và những lựa chọn cá nhân.",
    "coverUrl": "https://covers.openlibrary.org/b/isbn/9780141439518-L.jpg",
    "totalCopies": 2,
    "availableCopies": 1,
    "isHidden": false
  },
  {
    "bookTitleId": "BOOK008",
    "title": "The Psychology of Money",
    "author": "Morgan Housel",
    "isbn": "9780857197689",
    "category": "Kinh tế",
    "publisher": "Harriman House",
    "publishYear": 2020,
    "description": "Những bài học về hành vi, rủi ro và cách con người đưa ra quyết định tài chính.",
    "coverUrl": "https://covers.openlibrary.org/b/isbn/9780857197689-L.jpg",
    "totalCopies": 4,
    "availableCopies": 4,
    "isHidden": false
  },
  {
    "bookTitleId": "BOOK009",
    "title": "Clean Code",
    "author": "Robert C. Martin",
    "isbn": "9780132350884",
    "category": "Công nghệ",
    "publisher": "Prentice Hall",
    "publishYear": 2008,
    "description": "Các nguyên tắc thực hành để viết mã nguồn dễ đọc, dễ bảo trì và có chất lượng.",
    "coverUrl": "https://covers.openlibrary.org/b/isbn/9780132350884-L.jpg",
    "totalCopies": 2,
    "availableCopies": 2,
    "isHidden": false
  },
  {
    "bookTitleId": "BOOK010",
    "title": "A Brief History of Time",
    "author": "Stephen Hawking",
    "isbn": "9780553380163",
    "category": "Khoa học",
    "publisher": "Bantam",
    "publishYear": 1998,
    "description": "Giới thiệu những câu hỏi lớn của vũ trụ học như thời gian, hố đen và nguồn gốc vũ trụ.",
    "coverUrl": "https://covers.openlibrary.org/b/isbn/9780553380163-L.jpg",
    "totalCopies": 3,
    "availableCopies": 3,
    "isHidden": false
  }
];

exports.seedSampleBooks = onCall(async (request) => {
  await requireAdmin(request);
  const batch = db.batch();
  const now = FieldValue.serverTimestamp();
  for (const book of SAMPLE_BOOKS) {
    const ref = db.collection("BookTitles").doc(book.bookTitleId);
    batch.set(ref, { ...book, createdAt: now, updatedAt: now }, { merge: true });
  }
  await batch.commit();
  return { success: true, count: SAMPLE_BOOKS.length, collection: "BookTitles" };
});
