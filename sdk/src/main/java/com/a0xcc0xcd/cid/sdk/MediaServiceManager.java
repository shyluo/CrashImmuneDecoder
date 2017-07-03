package com.a0xcc0xcd.cid.sdk;

import android.content.Context;
import android.util.Log;

/**
 * Created by shengyang on 16-7-27.
 */
public class MediaServiceManager {
    private static  final  String LOG_TAG = "0xcc0xcd.com - Media Service Manager";
    private static boolean DEBUG = true;

    private static MediaServiceManager sInstance = new MediaServiceManager();

    private Object lock = new Object();

    private Context context;

    private IMediaService mediaService;
    private MediaServiceConnectionCallback connectionCallbak;
    private MediaServiceDeathCallback deathCallbak;
    private MediaServiceConnection connection;

    private boolean disableHardwareDecocder = false;

    static public MediaServiceManager getInstance() {
        return sInstance;
    }

    static public class MediaServiceConnectionCallback implements MediaServiceConnection.ConnectionCallback {
        private MediaServiceManager manager;

        public MediaServiceConnectionCallback(MediaServiceManager manager) {
            this.manager = manager;
        }

        @Override
        public void onConnected(IMediaService service) {
            manager.setup(service);
        }
    }

    static public class MediaServiceDeathCallback implements MediaServiceConnection.DeathCallback {
        private MediaServiceManager manager;

        public MediaServiceDeathCallback(MediaServiceManager manager) {
            this.manager = manager;
        }

        @Override
        public void onMediaServiceDied(MediaServiceConnection connection) {
            Log.e(LOG_TAG, "onMediaServiceDied");

            manager.disableHardwareDecocder();
            manager.stop();

            manager.initialize(null);
        }
    }

    public void initialize(Context ctx) {
        synchronized (lock) {
            if (context == null) {
                context = ctx;
            }

            if (connectionCallbak == null) {
                connectionCallbak = new MediaServiceConnectionCallback(this);
            }

            if (deathCallbak == null) {
                deathCallbak = new MediaServiceDeathCallback(this);
            }

            if (connection == null) {
                connection = new MediaServiceConnection(context, deathCallbak, connectionCallbak, AppMediaService.class);
            }

            if (mediaService == null) {
                connection.bind();
            }
        }
    }

    private void setup(IMediaService service) {
        synchronized (lock) {
            mediaService = service;
        }
    }

    private void stop() {
        synchronized (lock) {
            mediaService = null;
        }
    }

    public IMediaService getMediaService() {
        IMediaService service = null;
        synchronized (lock) {
            while (mediaService == null) {
                try {
                    lock.wait(50);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            service = mediaService;
        }

        return service;
    }

    public void disableHardwareDecocder() {
        disableHardwareDecocder = true;
    }

    public boolean canUseHardwareDecoder() {
        return !disableHardwareDecocder;
    }
}
