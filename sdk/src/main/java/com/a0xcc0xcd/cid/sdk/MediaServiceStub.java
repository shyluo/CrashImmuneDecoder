package com.a0xcc0xcd.cid.sdk;

import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;

import com.a0xcc0xcd.cid.sdk.video.IVideoDecoder;

/**
 * Created by shengyang on 16-3-28.
 */
public abstract class MediaServiceStub extends Binder implements IMediaService {
    public MediaServiceStub() {
        attachInterface(this, DESCRIPTION);
    }

    public IBinder asBinder() {
        return this;
    }

    @Override
    public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
        switch (code) {
            case CREATE_H264_HARDWARE_DECODER_TRANSACTION: {
                data.enforceInterface(DESCRIPTION);

                IVideoDecoder decoder = createH264HardwareDecoder();

                reply.writeNoException();
                reply.writeInt(1);
                reply.writeStrongBinder(decoder.asBinder());

                return true;
            }
        }

        return super.onTransact(code, data, reply, flags);
    }
}
