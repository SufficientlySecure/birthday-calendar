package org.birthdayadapter.service;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import org.birthdayadapter.util.AccountHelper;

public class SyncWorker extends Worker {

    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        // Trigger the manual sync from the AccountHelper
        new AccountHelper(getApplicationContext()).manualSync();
        return Result.success();
    }
}
