package fr.heinisch.birthdayadapter.util;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

public class SyncStatusManager {

    private static SyncStatusManager sInstance;
    private final MutableLiveData<Boolean> mIsSyncing = new MutableLiveData<>();

    private SyncStatusManager() {
        // private constructor for singleton
    }

    public static synchronized SyncStatusManager getInstance() {
        if (sInstance == null) {
            sInstance = new SyncStatusManager();
        }
        return sInstance;
    }

    public LiveData<Boolean> isSyncing() {
        return mIsSyncing;
    }

    public void setSyncing(boolean isSyncing) {
        mIsSyncing.postValue(isSyncing);
    }
}
