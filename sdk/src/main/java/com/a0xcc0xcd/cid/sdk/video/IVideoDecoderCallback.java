package com.a0xcc0xcd.cid.sdk.video;

import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;

/**
 * Created by shengyang on 16-3-30.
 */
public interface IVideoDecoderCallback extends IInterface {
    public void onError(int code) throws RemoteException;
    public void onSlow(boolean restart) throws RemoteException;
    public void onDecodeHardwareFrame(long pts) throws RemoteException;

    String DESCRIPTION = "com.a0xcc0xcd.cid.sdk.video.IVideoDecoderCallback";

    int ON_ERROR_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION;
    int ON_SLOW_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION + 1;
    int ON_DECODE_HARDWARE_FRAME_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION + 2;
}
