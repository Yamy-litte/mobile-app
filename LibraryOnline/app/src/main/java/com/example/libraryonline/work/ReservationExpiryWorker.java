package com.example.libraryonline.work;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.libraryonline.data.repository.CirculationRepository;

/** Periodically advances reservation queues after their 48-hour priority hold expires. */
public final class ReservationExpiryWorker extends Worker {
    public ReservationExpiryWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        try (CirculationRepository repository = new CirculationRepository(getApplicationContext())) {
            repository.processExpiredReservationHolds();
            return Result.success();
        } catch (RuntimeException error) {
            return Result.retry();
        }
    }
}
