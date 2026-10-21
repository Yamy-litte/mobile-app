# Local Firestore Rules tests

From LibraryOnline, use Node 20 and Java 21+:

```sh
npm --prefix tools/firestore-tests ci
npx --yes --package=firebase-tools@14.22.0 firebase emulators:exec --config firebase.emulators.json --project demo-library-online --only firestore 'npm --prefix tools/firestore-tests test'
```

The test requires FIRESTORE_EMULATOR_HOST injected by emulators:exec and uses only demo-library-online. It refuses a missing emulator configuration. Do not substitute the real Firebase project. A network failure before the runner starts is not a passing or failing Rules suite. The CLI downloads the verified emulator from storage.googleapis.com; permit that destination without disabling TLS or integrity verification.

Dependencies and lock are isolated here because functions/node_modules is tracked. See ../../docs/CLOUD_FUNCTIONS_AUDIT.md for the audit and remaining limitations.
