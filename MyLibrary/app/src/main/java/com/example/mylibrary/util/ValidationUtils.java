package com.example.mylibrary.util;

public class ValidationUtils {
    public static boolean isValidEmail(String email) {
        return email != null && email.matches("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}");
    }

    public static boolean isValidPassword(String password) {
        return password != null && password.length() >= 6;
    }

    public static boolean isValidPhoneNumber(String phone) {
        return phone != null && phone.matches("[0-9]{10,}$");
    }

    public static boolean isValidLibraryCardNumber(String cardNumber) {
        return cardNumber != null && cardNumber.length() > 0;
    }
}
