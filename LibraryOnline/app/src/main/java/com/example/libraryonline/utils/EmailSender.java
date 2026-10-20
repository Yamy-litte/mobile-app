package com.example.libraryonline.utils;

import android.text.TextUtils;
import android.util.Log;

import com.example.libraryonline.BuildConfig;

import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

/**
 * Demo-only SMTP sender. Credentials are injected at build time and are still
 * recoverable from the APK; do not use this design for a production release.
 */
public final class EmailSender {
    private static final String TAG = "EmailSender";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private EmailSender() {}

    public interface Callback {
        void onResult(boolean success, String message);
    }

    public static void send(String recipient, String subject, String body, Callback callback) {
        EXECUTOR.execute(() -> {
            try {
                String sender = BuildConfig.SMTP_EMAIL.trim();
                String appPassword = BuildConfig.SMTP_APP_PASSWORD.replaceAll("\\s+", "");
                if (TextUtils.isEmpty(sender) || TextUtils.isEmpty(appPassword)) {
                    throw new IllegalStateException(
                            "Chưa cấu hình SMTP_EMAIL và SMTP_APP_PASSWORD trong Gradle properties hoặc tham số -P.");
                }
                Properties props = new Properties();
                props.put("mail.smtp.auth", "true");
                props.put("mail.smtp.starttls.enable", "true");
                props.put("mail.smtp.host", "smtp.gmail.com");
                props.put("mail.smtp.port", "587");
                props.put("mail.smtp.connectiontimeout", "15000");
                props.put("mail.smtp.timeout", "15000");
                props.put("mail.smtp.writetimeout", "15000");

                Session session = Session.getInstance(props, new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(sender, appPassword);
                    }
                });
                MimeMessage message = new MimeMessage(session);
                message.setFrom(new InternetAddress(sender));
                message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipient, false));
                message.setSubject(subject, "UTF-8");
                message.setText(body, "UTF-8");
                Transport.send(message);
                post(callback, true, "Đã gửi email.");
            } catch (Exception e) {
                Log.e(TAG, "Không gửi được email", e);
                post(callback, false, e.getMessage() == null ? "Không gửi được email." : e.getMessage());
            }
        });
    }

    private static void post(Callback callback, boolean success, String message) {
        if (callback != null) {
            new android.os.Handler(android.os.Looper.getMainLooper())
                    .post(() -> callback.onResult(success, message));
        }
    }
}
