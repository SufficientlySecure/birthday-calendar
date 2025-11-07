package org.birthdayadapter.service;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.birthdayadapter.util.Log;

public class BirthdaySyncWorker extends Worker {

    public BirthdaySyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            // Directly call the static method that performs the sync logic
            CalendarSyncAdapterService.performSync(getApplicationContext());
            return Result.success();
        } catch (Exception e) {
            Log.e("BirthdaySyncWorker", "Sync failed", e);
            return Result.failure();
        }
    }
}
