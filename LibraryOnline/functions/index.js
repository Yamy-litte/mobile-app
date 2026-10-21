"use strict";

// The Android Spark workflow owns registration/review via Firebase Auth + Firestore.
// No callable/trigger is needed by the current client. Do not reintroduce parallel
// registration writers or production seed endpoints. See ../docs/CLOUD_FUNCTIONS_AUDIT.md.
// Keeping an inert entry point allows import/syntax regression checks without any
// Admin SDK initialization, credentials, network requests, or database writes.
module.exports = Object.freeze({});
