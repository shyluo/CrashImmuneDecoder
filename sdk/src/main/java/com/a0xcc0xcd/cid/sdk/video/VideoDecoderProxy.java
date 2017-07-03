package com.a0xcc0xcd.cid.sdk.video;

import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.util.Log;
import android.view.Surface;

import java.io.FileDescriptor;

/**
 * Created by shengyang on 16-3-30.
 */
public class VideoDecoderProxy implements IVideoDecoder {
    private static final String LOG_TAG = "0xcc0xcd.com - Video Decoder Proxy";

    private IBinder remote;

    public static IVideoDecoder asInterface(IBinder obj) {
        if(obj == null) {
            return null;
        }

        return new VideoDecoderProxy(obj);
    }

    VideoDecoderProxy(IBinder r) {
        remote = r;
    }

    public IBinder asBinder() {
        return remote;
    }

    public String getInterfaceDescriptor() {
        return DESCRIPTION;
    }

    public boolean config(Bundle params) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();

        int result = 0;

        try {
            data.writeInterfaceToken(DESCRIPTION);

            params.writeToParcel(data, 0);

            remote.transact(CONFIG_TRANSACTION, data, reply, 0);

            reply.readException();

            result = reply.readInt();
        } finally {
            reply.recycle();
            data.recycle();
        }

        return result != 0;
    }

    public ParcelFileDescriptor getInputMemoryFile() throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();

        ParcelFileDescriptor pfd = null;

        try {
            data.writeInterfaceToken(DESCRIPTION);

            remote.transact(GET_INPUT_MEMORY_FILE_TRANSACTION, data, reply, 0);

            reply.readException();

            if(0 != reply.readInt()) {
                pfd = ParcelFileDescriptor.CREATOR.createFromParcel(reply);
            }
        } finally {
            reply.recycle();
            data.recycle();
        }

        return pfd;
    }

    public int getInputMemoryFileSize() throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();

        int size = 0;

        try {
            data.writeInterfaceToken(DESCRIPTION);

            remote.transact(GET_INPUT_MEMORY_FILE_SIZE_TRANSACTION, data, reply, 0);

            reply.readException();

            size = reply.readInt();
        } finally {
            reply.recycle();
            data.recycle();
        }

        return size;
    }

    public boolean fillFrame(int offset, int size, long pts) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();

        int result = -1;

        try {
            data.writeInterfaceToken(DESCRIPTION);

            data.writeInt(offset);
            data.writeInt(size);
            data.writeLong(pts);

            remote.transact(FILL_FRAME_TRANSACTION, data, reply, 0);

            reply.readException();

            result = reply.readInt();
        } finally {
            reply.recycle();
            data.recycle();
        }

        return result == 1;
    }

    public void setCallback(IVideoDecoderCallback callback) throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();

        try {
            data.writeInterfaceToken(DESCRIPTION);

            data.writeStrongBinder(callback.asBinder());

            remote.transact(SET_CALLBACK_TRANSACTION, data, reply, 0);

            reply.readException();
        } finally {
            reply.recycle();
            data.recycle();
        }
    }

    public void release() throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();

        try {
            data.writeInterfaceToken(DESCRIPTION);

            remote.transact(RELEASE_TRANSACTION, data, reply, 0);

            reply.readException();
        } finally {
            reply.recycle();
            data.recycle();
        }
    }
}