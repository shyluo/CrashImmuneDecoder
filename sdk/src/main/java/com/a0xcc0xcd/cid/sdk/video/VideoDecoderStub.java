package com.a0xcc0xcd.cid.sdk.video;

import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;

/**
 * Created by shengyang on 16-3-30.
 */
public abstract class VideoDecoderStub extends Binder implements IVideoDecoder {
    public VideoDecoderStub() {
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
            case CONFIG_TRANSACTION: {
                data.enforceInterface(DESCRIPTION);

                Bundle params = data.readBundle();
                boolean result = config(params);

                reply.writeNoException();
                reply.writeInt(result ? 1 : 0);

                return true;
            }
            case GET_INPUT_MEMORY_FILE_TRANSACTION: {
                data.enforceInterface(DESCRIPTION);

                ParcelFileDescriptor pfd = getInputMemoryFile();
                if (pfd != null) {
                    reply.writeNoException();

                    reply.writeInt(1);
                    pfd.writeToParcel(reply, 0);

                } else {
                    reply.writeInt(0);
                }

                return true;
            }
            case GET_INPUT_MEMORY_FILE_SIZE_TRANSACTION: {
                data.enforceInterface(DESCRIPTION);

                int size = getInputMemoryFileSize();

                reply.writeNoException();

                reply.writeInt(size);

                return true;
            }
            case FILL_FRAME_TRANSACTION: {
                data.enforceInterface(DESCRIPTION);

                int offset = data.readInt();
                int size = data.readInt();
                long pts = data.readLong();

                boolean result = fillFrame(offset, size, pts);

                reply.writeNoException();

                reply.writeInt(result ? 1 : 0);

                return true;
            }
            case SET_CALLBACK_TRANSACTION: {
                data.enforceInterface(DESCRIPTION);

                IVideoDecoderCallback callback = VideoDecoderCallbackProxy.asInterface(data.readStrongBinder());

                setCallback(callback);

                reply.writeNoException();

                return true;
            }
            case RELEASE_TRANSACTION: {
                data.enforceInterface(DESCRIPTION);

                release();

                reply.writeNoException();

                return true;
            }
        }

        return super.onTransact(code, data, reply, flags);
    }
}
