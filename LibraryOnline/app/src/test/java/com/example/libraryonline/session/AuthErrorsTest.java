package com.example.libraryonline.session;

import com.example.libraryonline.ui.auth.AuthErrors;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.firestore.FirebaseFirestoreException;
import org.junit.Test;
import static org.junit.Assert.*;

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk=28,manifest=org.robolectric.annotation.Config.NONE)
public class AuthErrorsTest {
    @Test public void networkFailureKeepsSessionAndGivesConnectionInstructions() {
        for(Exception error:new Exception[]{new FirebaseNetworkException("offline"),new FirebaseFirestoreException("offline",FirebaseFirestoreException.Code.UNAVAILABLE)}) {
            assertFalse(AuthErrors.rejectsSession(error));assertTrue(AuthErrors.message(error).contains("kết nối"));
        }
    }
    @Test public void disabledAndUnauthenticatedSessionsMustSignInAgain() {
        assertTrue(AuthErrors.rejectsSession(new FirebaseAuthInvalidUserException("ERROR_USER_DISABLED","disabled")));
        assertTrue(AuthErrors.rejectsSession(new FirebaseFirestoreException("expired",FirebaseFirestoreException.Code.UNAUTHENTICATED)));
    }
    @Test public void wrongPasswordAndUnknownAccountUseTheSameSafeMessage() {
        assertEquals(AuthErrors.message(new FirebaseAuthException("ERROR_WRONG_PASSWORD","wrong")),AuthErrors.message(new FirebaseAuthException("ERROR_USER_NOT_FOUND","missing")));
    }
}
