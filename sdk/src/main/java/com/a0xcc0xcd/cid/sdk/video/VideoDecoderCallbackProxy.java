package com.a0xcc0xcd.cid.sdk.video;

import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;

/**
 * Created by shengyang on 16-3-30.
 */
public class VideoDecoderCallbackProxy implements IVideoDecoderCallback {
    private static final String LOG_TAG = "0xcc0xcd.com - Video Decoder Callback Proxy";

    private IBinder remote;

    public static IVideoDecoderCallback asInterface(IBinder obj) {
        if(obj == null) {
            return null;
        }

        return new VideoDecoderCallbackProxy(obj);
    }

    VideoDecoderCallbackProxy(IBinder r) {
        remote = r;
    }

    public IBinder asBinder() {
        return remote;
    }

    public String getInterfaceDescriptor() {
        return DESCRIPTION;
    }

    public void onError(int code) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();

        try {
            data.writeInterfaceToken(DESCRIPTION);

            data.writeInt(code);

            remote.transact(ON_ERROR_TRANSACTION, data, reply, IBinder.FLAG_ONEWAY);

            reply.readException();
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    public void onSlow(boolean restart) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();

        try {
            data.writeInterfaceToken(DESCRIPTION);

            data.writeInt(restart ? 1 : 0);

            remote.transact(ON_SLOW_TRANSACTION, data, reply, IBinder.FLAG_ONEWAY);

            reply.readException();
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    public void onDecodeHardwareFrame(long pts) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();

        try {
            data.writeInterfaceToken(DESCRIPTION);

            data.writeLong(pts);

            remote.transact(ON_DECODE_HARDWARE_FRAME_TRANSACTION, data, reply, IBinder.FLAG_ONEWAY);

            reply.readException();
        } finally {
            reply.recycle();
            data.recycle();
        }
    }
}
