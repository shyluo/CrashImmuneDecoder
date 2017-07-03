package com.a0xcc0xcd.cid.sdk;

import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;

import com.a0xcc0xcd.cid.sdk.video.IVideoDecoder;

/**
 * Created by shengyang on 16-3-28.
 */
public interface IMediaService extends IInterface {
    public IVideoDecoder createH264HardwareDecoder() throws RemoteException;

    String DESCRIPTION = "com.a0xcc0xcd.cid.sdk.IMediaService";

    int CREATE_H264_HARDWARE_DECODER_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION;
}
