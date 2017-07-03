package com.a0xcc0xcd.cid.sdk.video;

import android.os.Bundle;
import android.os.IBinder;
import android.os.IInterface;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;

/**
 * Created by shengyang on 16-3-30.
 */
public interface IVideoDecoder extends IInterface {
    public static final String KEY_WIDTH = "com.0xcc0xcd.width";
    public static final String KEY_HEIGHT = "com.0xcc0xcd.height";
    public static final String KEY_SURFACE = "com.0xcc0xcd.surface";

    public boolean config(Bundle params) throws RemoteException;

    public ParcelFileDescriptor getInputMemoryFile() throws RemoteException;
    public int getInputMemoryFileSize() throws RemoteException;

    public boolean fillFrame(int offset, int size, long pts) throws RemoteException;

    public void setCallback(IVideoDecoderCallback callback) throws RemoteException;

    public void release() throws RemoteException;

    String DESCRIPTION = "com.a0xcc0xcd.cid.sdk.video.IVideoDecoder";

    int CONFIG_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION;
    int GET_INPUT_MEMORY_FILE_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION + 1;
    int GET_INPUT_MEMORY_FILE_SIZE_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION + 2;
    int FILL_FRAME_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION + 3;
    int SET_CALLBACK_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION + 4;
    int RELEASE_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION + 5;
}