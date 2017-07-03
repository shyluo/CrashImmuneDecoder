package com.a0xcc0xcd.cid.sdk;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

/**
 * Created by shengyang on 16-3-28.
 */
public class MediaServiceConnection implements ServiceConnection {
    private static final String LOG_TAG = "0xcc0xcd.com - Media Service Connection";
    private static boolean DEBUG = true;

    private final Object lock = new Object();

    private IMediaService service = null;
    private boolean serviceConnectComplete = false;
    private boolean serviceDisconnected = false;

    private Context context;
    private DeathCallback deathCallback;
    private ConnectionCallback connectionCallback;

    private Class<? extends MediaService> serviceClass;

    private boolean bound = false;

    public MediaServiceConnection(Context context, DeathCallback deathCallback,
                           ConnectionCallback connectionCallback,
                           Class<? extends MediaService> serviceClass) {
        this.context = context;
        this.deathCallback = deathCallback;
        this.connectionCallback = connectionCallback;
        this.serviceClass = serviceClass;
    }

    public interface DeathCallback {
        void onMediaServiceDied(MediaServiceConnection connection);
    }

    public interface ConnectionCallback {
        void onConnected(IMediaService service);
    }

    public boolean bind() {
        if (!bound) {
            final Intent intent = createServiceBindIntent();
            bound = context.bindService(intent, this, Context.BIND_AUTO_CREATE);

            Log.i(LOG_TAG, "bind Service");
        }

        return bound;
    }

    private Intent createServiceBindIntent() {
        Intent intent = new Intent();
        intent.setClassName(context, serviceClass.getName());
        intent.setPackage(context.getPackageName());
        return intent;
    }

    public void unbind() {
        if (bound) {
            context.unbindService(this);
            bound = false;
        }
    }

    boolean isBound() {
        return bound;
    }

    @Override
    public void onServiceConnected(ComponentName className, IBinder binder) {
        synchronized (lock) {
            if (serviceConnectComplete) {
                return;
            }

            serviceConnectComplete = true;
            service = MediaServiceProxy.asInterface(binder);

            if (connectionCallback != null) {
                connectionCallback.onConnected(service);
            }
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName className) {
        synchronized (lock) {
            if (serviceDisconnected) {
                return;
            }
            serviceDisconnected = true;

            unbind();// We don't want to auto-restart on crash. Let the app do that.

            if (deathCallback != null) {
                deathCallback.onMediaServiceDied(this);
            }
        }
    }

    public IMediaService getService() {
        synchronized (lock) {
            return service;
        }
    }
}
