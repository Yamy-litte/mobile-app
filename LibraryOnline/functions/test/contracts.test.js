"use strict";
const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const root = path.resolve(__dirname, "../..");
const read = (p) => fs.readFileSync(path.join(root, p), "utf8");
function javaFiles(dir) {
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap((e) => {
    const p = path.join(dir, e.name);
    return e.isDirectory() ? javaFiles(p) : p.endsWith(".java") ? [p] : [];
  });
}
test("retired backend loads without registering callable, trigger or seed writes", () => {
  assert.deepEqual(Object.keys(require("../index.js")), []);
});
test("Firebase configuration does not advertise a retired Functions codebase", () => {
  assert.equal(JSON.parse(read("firebase.json")).functions, undefined);
});
test("Android has no caller requiring retired endpoints", () => {
  for (const p of javaFiles(path.join(root, "app/src/main/java"))) {
    assert.doesNotMatch(fs.readFileSync(p, "utf8"), /getHttpsCallable\s*\(|httpsCallable\s*\(/, p);
  }
});
test("SQLite creation and upgrades do not inject demo inventory", () => {
  const src = read("app/src/main/java/com/example/libraryonline/data/local/db/DatabaseHelper.java");
  assert.doesNotMatch(src, /seedSampleBooks|BOOK001|BC-BOOK/);
  // A versioned cache-table rebuild is allowed only with data-preserving migration
  // coverage in CatalogueCacheTest. Never drop or clear inventory/account tables.
  assert.doesNotMatch(src, /(?:DELETE FROM|DROP TABLE) (?:BookTitles|BookCopies|Users|Admins|LoanReceipts|LoanDetails)\b/i);
});
test("Android serializer keeps category display names out of Firestore", () => {
  const src = read("app/src/main/java/com/example/libraryonline/data/remote/firebase/CloudLibraryRepository.java");
  assert.match(src, /data\.put\("categoryId", categoryId\)/);
  assert.doesNotMatch(src, /data\.put\("category",/);
});
