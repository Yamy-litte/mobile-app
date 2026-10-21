package com.example.libraryonline.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk=28,manifest=org.robolectric.annotation.Config.NONE)
public class InputValidatorTest {

    @Test
    public void normalizeEmailTrimsAndLowercases() {
        assertEquals("reader@example.com",
                InputValidator.normalizeEmail("  Reader@Example.com "));
    }

    @Test
    public void vietnamPhoneAcceptsCommonPrefixesAndFormatting() {
        assertTrue(InputValidator.isValidVietnamPhone("0912345678"));
        assertTrue(InputValidator.isValidVietnamPhone("+84 912 345 678"));
        assertTrue(InputValidator.isValidVietnamPhone("84-912-345-678"));
        assertEquals("0912345678", InputValidator.normalizeVietnamPhone("+84 912 345 678"));
    }

    @Test
    public void vietnamPhoneRejectsInvalidLengthAndLandlineLikeNumbers() {
        assertFalse(InputValidator.isValidVietnamPhone("091234567"));
        assertFalse(InputValidator.isValidVietnamPhone("0212345678"));
        assertFalse(InputValidator.isValidVietnamPhone(null));
    }

    @Test
    public void emailAndPasswordValidationRejectInvalidInputs() {
        assertTrue(InputValidator.isValidEmail("reader@example.com"));
        assertFalse(InputValidator.isValidEmail("reader@"));
        assertTrue(InputValidator.isValidPassword("12345678"));
        assertFalse(InputValidator.isValidPassword("1234567"));
        assertFalse(InputValidator.isNonEmpty("   "));
    }

    @Test public void registrationNameMatchesSchemaBounds() {
        assertFalse(InputValidator.isValidFullName(null));assertFalse(InputValidator.isValidFullName(" A "));
        assertTrue(InputValidator.isValidFullName("  Lan  "));
        assertTrue(InputValidator.isValidFullName(new String(new char[120]).replace('\0','A')));
        assertFalse(InputValidator.isValidFullName(new String(new char[121]).replace('\0','A')));
    }
    @Test public void birthDateMustBeRealIsoDateAndNotInTheFuture() {
        assertTrue(InputValidator.isValidBirthDate("2000-02-29"));
        assertFalse(InputValidator.isValidBirthDate("2001-02-29"));
        assertFalse(InputValidator.isValidBirthDate("2000-02-30"));
        assertFalse(InputValidator.isValidBirthDate("01/01/2000"));
        assertFalse(InputValidator.isValidBirthDate("2999-01-01"));
        assertFalse(InputValidator.isValidBirthDate(null));
        assertFalse(InputValidator.isValidEmail(new String(new char[254]).replace('\0','a')+"@x.com"));
    }
}
