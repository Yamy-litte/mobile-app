package com.example.libraryonline;

import android.app.Application;

import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.example.libraryonline.work.ReservationExpiryWorker;

import java.util.concurrent.TimeUnit;

/** Schedules durable background maintenance for reservation queue holds. */
public final class LibraryOnlineApplication extends Application {
    private static final String RESERVATION_EXPIRY_WORK = "reservation-expiry-periodic";

    @Override
    public void onCreate() {
        super.onCreate();
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                ReservationExpiryWorker.class, 15, TimeUnit.MINUTES).build();
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                RESERVATION_EXPIRY_WORK, ExistingPeriodicWorkPolicy.KEEP, request);
    }
}
