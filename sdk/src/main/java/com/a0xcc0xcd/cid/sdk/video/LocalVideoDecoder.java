package com.a0xcc0xcd.cid.sdk.video;

import android.graphics.Rect;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.opengl.Matrix;
import android.os.Build;
import android.os.Bundle;
import android.view.Surface;

import java.nio.ByteBuffer;

/**
 * Created by shengyang on 16-5-27.
 */
public class LocalVideoDecoder {
    public static final String KEY_WIDTH = "key.width";
    public static final String KEY_HEIGHT = "key.height";
    public static final String KEY_SURFACE = "key.surface";

    public static final int HARDWARE_DECODER_ERROR = 1;

    public interface VideoDecoderClient {
        public void onError(int code);
        public void onSlow(boolean restart);
        public void onDecodeHardwareFrame(long pts);
    }

    public static boolean supportsHardwareDecoder(String mimeType) {
        if (Build.VERSION.SDK_INT < 16) {
            return false;
        }

        int numCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);

            if(codecInfo.isEncoder()) {
                continue;
            }

            String[] types = codecInfo.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                if (types[j].equalsIgnoreCase(mimeType)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean config(Bundle params) {
        int width = params.getInt(KEY_WIDTH, 0);
        int height = params.getInt(KEY_HEIGHT, 0);
        Surface surface = (Surface)params.getParcelable(KEY_SURFACE);

        if (surface == null) {
            return config(width, height);
        }

        return config(width, height, surface);
    }

    public boolean fillFrame(ByteBuffer frame, int offset, int size, long pts) {
        return false;
    }

    public void release() {

    }

    protected boolean config(int width, int height) {
        return false;
    }

    protected boolean config(int width, int height, Surface surface) {
        return false;
    }
}