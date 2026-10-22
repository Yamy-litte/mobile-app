"use strict";
const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const root = path.resolve(__dirname, "../..");
const read = (p) => fs.readFileSync(path.join(root, p), "utf8");

test("admin management cloud writes are restricted to approved fields and bounded values", () => {
  const rules = read("firestore.rules");
  const adminRules = rules.slice(rules.indexOf("match /Admins/{adminId}"), rules.indexOf("match /Users/{userId}"));
  assert.match(adminRules, /affectedKeys\(\)\.hasOnly\(\['fullName', 'phone', 'email', 'updatedAt'\]\)/);
  assert.match(adminRules, /request\.resource\.data\.updatedAt == request\.time/);
  const userRules = rules.slice(rules.indexOf("match /Users/{userId}"), rules.indexOf("match /RegistrationEmailIndex/{email}"));
  assert.match(userRules, /affectedKeys\(\)\.hasOnly\(\['cardStatus', 'cardExpiryDate', 'updatedAt'\]\)/);
  assert.match(userRules, /request\.resource\.data\.cardStatus in \['HoatDong', 'BiKhoa', 'HetHan'\]/);
  const configRules = rules.slice(rules.indexOf("match /SystemConfigs/{configId}"), rules.indexOf("match /{document=**}"));
  assert.match(configRules, /request\.resource\.data\.maxBooksPerUser <= 20/);
  assert.match(configRules, /request\.resource\.data\.updatedBy == request\.auth\.uid/);
});
