package com.example.libraryonline.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
}
