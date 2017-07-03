package com.a0xcc0xcd.cid.sdk.video;

import android.os.Bundle;
import android.os.MemoryFile;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.util.Log;
import android.view.Surface;

import java.io.FileDescriptor;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;

/**
 * Created by shengyang on 16-3-31.
 */
public class RemoteHardwareDecoder extends VideoDecoderStub implements LocalVideoDecoder.VideoDecoderClient {
    private static final String LOG_TAG = "0xcc0xcd.com - Remote Hardware Decoder Wrapper";
    private static boolean DEBUG = true;

    private MemoryFile inputMemoryFile = null;
    private FileDescriptor inputMemoryFileDiscriptor = null;
    private int inputMemoryFileSize = 0;
    private ByteBuffer inputBuffer = null;

    private IVideoDecoderCallback callback;
    private VideoDecoderClientDeathRecipient deathRecipient;

    private LocalHardwareDecoder decoder;

    public RemoteHardwareDecoder(String decoderType) throws Exception {
        deathRecipient = new VideoDecoderClientDeathRecipient(this);

        decoder = new LocalHardwareDecoder(this, decoderType);
        decoder.setOwnsSurface(true);

        inputMemoryFileSize = 1024 * 1024;
        try {
            inputMemoryFile = new MemoryFile("hardware_decoder_input_memory_file", inputMemoryFileSize);
            inputMemoryFileDiscriptor = getMemoryFileDiscriptor(inputMemoryFile);

            inputBuffer = ByteBuffer.allocateDirect(inputMemoryFileSize);
        } catch (IOException e) {
            Log.e(LOG_TAG, "Failed to create memory file for input");
        }
    }

    public void onError(int code) {
        if (callback != null) {
            try {
                callback.onError(code);
            } catch (RemoteException e) {
                Log.e(LOG_TAG, "Faile to notify error code: " + code + ", error: " + e.getMessage());
            }
        }
    }

    public void onSlow(boolean restart) {
        if (callback != null) {
            try {
                callback.onSlow(restart);
            } catch (RemoteException e) {
                Log.e(LOG_TAG, "Faile to notify decoder slow, error: " + e.getMessage());
            }
        }
    }

    public void onDecodeHardwareFrame(long pts) {
        if (callback != null) {
            try {
                callback.onDecodeHardwareFrame(pts);
            } catch (RemoteException e) {
                Log.e(LOG_TAG, "Faile to notify hardware decode event, error: " + e.getMessage());
            }
        }
    }

    public boolean config(Bundle params) throws RemoteException {
        int width = params.getInt(KEY_WIDTH, 0);
        int height = params.getInt(KEY_HEIGHT, 0);
        Surface surface = (Surface)params.getParcelable(KEY_SURFACE);

        return decoder.config(width, height, surface);
    }

    public ParcelFileDescriptor getInputMemoryFile() throws RemoteException {
        ParcelFileDescriptor pfd = null;

        try {
            pfd = ParcelFileDescriptor.dup(inputMemoryFileDiscriptor);
        } catch (IOException e) {
            Log.e(LOG_TAG, "Failed to dup input memory file descriptor");
        }

        return pfd;
    }

    public int getInputMemoryFileSize() throws RemoteException {
        return inputMemoryFileSize;
    }

    public boolean fillFrame(int offset, int size, long pts) throws RemoteException {
        try {
            if ((offset + size) > 0 && (offset + size) <= inputMemoryFileSize) {
                inputMemoryFile.readBytes(inputBuffer.array(), offset, inputBuffer.arrayOffset(), size);
            } else {
                Log.e(LOG_TAG, "Frame size is too large, offset: " + offset + "size: " + size + ", input memory file size: " + inputMemoryFileSize);
            }
        } catch (IOException e) {
            Log.e(LOG_TAG, "Failed to read frame from input memory file");
            return false;
        }

        return decoder.fillFrame(inputBuffer, 0, size, pts);
    }

    public void setCallback(IVideoDecoderCallback callback) throws RemoteException {
        this.callback = callback;

        if (callback != null) {
            callback.asBinder().linkToDeath(deathRecipient, 0);
        }
    }

    public void release() throws RemoteException {
        if (decoder != null) {
            decoder.release();
            decoder = null;
        }

        if (callback != null) {
            callback.asBinder().unlinkToDeath(deathRecipient, 0);
            callback = null;
        }

        if (inputMemoryFile != null) {
            inputMemoryFile.close();
            inputMemoryFileSize = 0;
            inputMemoryFileDiscriptor = null;
            inputMemoryFile = null;
            inputBuffer = null;
        }
    }


    private FileDescriptor getMemoryFileDiscriptor(MemoryFile file) {
        Class<?> clazz = file.getClass();
        FileDescriptor fd = null;

        try {
            Method method = clazz.getMethod("getFileDescriptor");
            fd = (FileDescriptor) method.invoke(file);
        } catch (NoSuchMethodException ex) {
            Log.e(LOG_TAG, "Cann't find getFileDescriptor method from class MemoryFile");
        } catch (IllegalArgumentException e) {
            Log.e(LOG_TAG, "Illegal Argument while calling getFileDescriptor method of MemoryFile");
        } catch (IllegalAccessException e) {
            Log.e(LOG_TAG, "Illegal Access while calling getFileDescriptor method of MemoryFile");
        } catch (InvocationTargetException e) {
            Log.e(LOG_TAG, "Invocation Error while calling getFileDescriptor method of MemoryFile");
        }

        return fd;
    }
}
