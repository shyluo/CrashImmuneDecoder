package com.a0xcc0xcd.cid.sdk.video;

import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

/**
 * Created by shengyang on 16-9-28.
 */
public class VideoDecoderClientDeathRecipient implements IBinder.DeathRecipient {
    private static final String LOG_TAG = "0xcc0xcd.com - Remote Client Death Recipient";
    private static boolean DEBUG = true;

    private IVideoDecoder videoDecoder;

    VideoDecoderClientDeathRecipient(IVideoDecoder videoDocoder) {
        this.videoDecoder = videoDocoder;
    }

    @Override
    public void binderDied() {
        Log.w(LOG_TAG, "Video decoder client is died");

        if (videoDecoder != null) {
            try {
                videoDecoder.release();
            } catch (RemoteException e) {
                Log.e(LOG_TAG, "Failed to release video decoder");
            }

            videoDecoder = null;
        }
    }
}
