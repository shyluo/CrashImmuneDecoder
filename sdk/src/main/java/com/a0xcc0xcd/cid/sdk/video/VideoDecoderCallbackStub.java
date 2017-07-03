package com.a0xcc0xcd.cid.sdk.video;

import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;

/**
 * Created by shengyang on 16-3-30.
 */
public abstract class VideoDecoderCallbackStub extends Binder implements IVideoDecoderCallback {
    public VideoDecoderCallbackStub() {
        attachInterface(this, DESCRIPTION);
    }

    public IBinder asBinder() {
        return this;
    }

    @Override
    public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
        switch (code) {
            case INTERFACE_TRANSACTION: {
                reply.writeString(DESCRIPTION);
                return true;
            }
            case ON_ERROR_TRANSACTION: {
                data.enforceInterface(DESCRIPTION);

                int err = data.readInt();

                onError(err);

                reply.writeNoException();

                return true;
            }
            case ON_SLOW_TRANSACTION: {
                data.enforceInterface(DESCRIPTION);

                int restart = data.readInt();

                onSlow(restart != 0);

                reply.writeNoException();

                return true;
            }
            case ON_DECODE_HARDWARE_FRAME_TRANSACTION: {
                data.enforceInterface(DESCRIPTION);

                long pts = data.readLong();
                onDecodeHardwareFrame(pts);

                reply.writeNoException();

                return true;
            }
        }

        return super.onTransact(code, data, reply, flags);
    }
}
