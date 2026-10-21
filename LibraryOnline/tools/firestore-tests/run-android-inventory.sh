#!/usr/bin/env bash
# Run from LibraryOnline via firebase emulators:exec --only firestore,auth.
set -eu
: "${FIRESTORE_EMULATOR_HOST:?Start the local Firestore emulator first}"
: "${FIREBASE_AUTH_EMULATOR_HOST:?Start the local Auth emulator first}"
export FLOW2_ANDROID_EMULATOR_TEST=1
bash gradlew :app:testDebugUnitTest --tests '*InventoryEmulatorTest' --no-configuration-cache --no-daemon --max-workers=2
