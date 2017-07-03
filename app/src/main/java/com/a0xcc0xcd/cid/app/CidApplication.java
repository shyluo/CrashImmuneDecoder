package com.a0xcc0xcd.cid.app;

import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.util.Log;

import com.a0xcc0xcd.cid.sdk.MediaServiceManager;

/**
 * Created by shengyang on 17-7-3.
 */

public class CidApplication extends Application {
    private static final String LOG_TAG = "0xcc0xcd.com - Cid Application";
    private static boolean DEBUG = true;

    @Override
    public void onCreate() {
        super.onCreate();

        String packageName = getPackageName();
        String currentProcessName = getCurrentProcessName(this);
        if (currentProcessName.trim().compareToIgnoreCase(packageName) == 0) {
            MediaServiceManager.getInstance().initialize(this);
        }
    }

    private String getCurrentProcessName(Context context) {
        int pid = android.os.Process.myPid();

        ActivityManager activityManager = (ActivityManager) context
                .getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningAppProcessInfo appProcess : activityManager
                .getRunningAppProcesses()) {
            if (appProcess.pid == pid) {
                return appProcess.processName;
            }
        }

        return "";
    }
}
