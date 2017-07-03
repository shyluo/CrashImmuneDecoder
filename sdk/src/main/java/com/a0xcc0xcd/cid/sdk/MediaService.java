package com.a0xcc0xcd.cid.sdk;

import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import com.a0xcc0xcd.cid.sdk.video.IVideoDecoder;
import com.a0xcc0xcd.cid.sdk.video.RemoteHardwareDecoder;


/**
 * Created by shengyang on 16-3-28.
 */
public class MediaService extends Service {
    private static final String LOG_TAG = "0xcc0xcd.com - Media Service";
    private static boolean DEBUG = true;

    private final MediaServiceStub binder = new MediaServiceStub() {
        @Override
        public IVideoDecoder createH264HardwareDecoder() throws RemoteException {
            IVideoDecoder decoder = null;

            try {
                decoder = new RemoteHardwareDecoder("video/avc");
            } catch (Exception e) {
                if (Build.VERSION.SDK_INT >= 15) {
                    throw new RemoteException(e.getMessage());
                } else {
                    throw new RemoteException();
                }
            }

            return decoder;
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(LOG_TAG, "Media Service Created");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(LOG_TAG, "Media Service Destroyed");
    }

    @Override
    public IBinder onBind(Intent intent) {
        // We call stopSelf() to request that this service be stopped as soon as the client
        // unbinds. Otherwise the system may keep it around and available for a reconnect. The
        // child processes do not currently support reconnect; they must be initialized from
        // scratch every time.
        stopSelf();

        return binder;
    }
}