package org.birthdayadapter.service;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import org.birthdayadapter.util.AccountHelper;
import org.birthdayadapter.util.Log;

public class BirthdayWorker extends Worker {

    public static final String ACTION = "action";
    public static final String ACTION_MANUAL_COMPLETE_SYNC = "MANUAL_SYNC";
    public static final String ACTION_CHANGE_COLOR = "CHANGE_COLOR";

    public BirthdayWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        String action = getInputData().getString(ACTION);

        if (action == null) {
            Log.e("BirthdayWorker", "Action is null");
            return Result.failure();
        }

        try {
            if (ACTION_CHANGE_COLOR.equals(action)) {
                if (new AccountHelper(getApplicationContext()).isAccountActivated()) {
                    CalendarSyncAdapterService.updateCalendarColor(getApplicationContext());
                }
            } else if (ACTION_MANUAL_COMPLETE_SYNC.equals(action)) {
                CalendarSyncAdapterService.performSync(getApplicationContext());
            }
            return Result.success();
        } catch (Exception e) {
            Log.e("BirthdayWorker", "Worker failed", e);
            return Result.failure();
        }
    }
}
