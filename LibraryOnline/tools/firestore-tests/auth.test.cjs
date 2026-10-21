"use strict";
const { test }=require("node:test");
const assert=require("node:assert/strict");
const host=process.env.FIREBASE_AUTH_EMULATOR_HOST;
if (!host || !/^(127\.0\.0\.1|localhost):[0-9]+$/.test(host)) throw new Error("Only a local Auth emulator is permitted");
const base=`http://${host}/identitytoolkit.googleapis.com/v1`;
async function call(action,body) {
  const response=await fetch(`${base}/${action}?key=demo-key`,{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify(body)});
  return {ok:response.ok,body:await response.json()};
}
test("Email/password Auth preserves UID, rejects wrong credentials and disabled accounts",async () => {
  const email=`reader-${Date.now()}@example.com`,password="Test-only-password-930!";
  const created=await call("accounts:signUp",{email,password,returnSecureToken:true});
  assert.equal(created.ok,true);const uid=created.body.localId;
  const login=await call("accounts:signInWithPassword",{email,password,returnSecureToken:true});
  assert.equal(login.ok,true);assert.equal(login.body.localId,uid);
  const wrong=await call("accounts:signInWithPassword",{email,password:"wrong-password",returnSecureToken:true});
  assert.equal(wrong.ok,false);
  const missing=await call("accounts:signInWithPassword",{email:`missing-${Date.now()}@example.com`,password,returnSecureToken:true});
  assert.equal(missing.ok,false);
  const disabled=await fetch(`${base}/projects/demo-library-online/accounts:update`,{method:"POST",headers:{"Content-Type":"application/json",Authorization:"Bearer owner"},body:JSON.stringify({localId:uid,disableUser:true})});
  assert.equal(disabled.ok,true);
  const blocked=await call("accounts:signInWithPassword",{email,password,returnSecureToken:true});
  assert.equal(blocked.ok,false);assert.equal(blocked.body.error.message,"USER_DISABLED");
});
