package com.a0xcc0xcd.cid.sdk;

import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;

import com.a0xcc0xcd.cid.sdk.video.IVideoDecoder;
import com.a0xcc0xcd.cid.sdk.video.VideoDecoderProxy;

/**
 * Created by shengyang on 16-3-28.
 */
public class MediaServiceProxy implements IMediaService {
    private static final String LOG_TAG = "0xcc0xcd.com - Media Service Proxy";

    private IBinder remote;

    public static IMediaService asInterface(IBinder obj) {
        if(obj == null) {
            return null;
        }

        return new MediaServiceProxy(obj);
    }

    MediaServiceProxy(IBinder r) {
        remote = r;
    }

    public IBinder asBinder() {
        return remote;
    }

    public String getInterfaceDescriptor() {
        return DESCRIPTION;
    }

    public IVideoDecoder createH264HardwareDecoder() throws RemoteException {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();

        IVideoDecoder decoder = null;

        try {
            data.writeInterfaceToken(DESCRIPTION);

            remote.transact(CREATE_H264_HARDWARE_DECODER_TRANSACTION, data, reply, 0);

            reply.readException();

            if (reply.readInt() == 1) {
                decoder = VideoDecoderProxy.asInterface(reply.readStrongBinder());
            }
        } finally {
            reply.recycle();
            data.recycle();
        }

        return decoder;
    }
}
