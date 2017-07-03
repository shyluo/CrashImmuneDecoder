package com.a0xcc0xcd.cid.app;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.a0xcc0xcd.cid.app.utils.H264File;
import com.a0xcc0xcd.cid.sdk.IMediaService;
import com.a0xcc0xcd.cid.sdk.MediaServiceManager;
import com.a0xcc0xcd.cid.sdk.utils.AshmemFile;
import com.a0xcc0xcd.cid.sdk.video.IVideoDecoder;
import com.a0xcc0xcd.cid.sdk.video.VideoDecoderCallbackStub;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;

public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback {
    private static final String LOG_TAG = "0xcc0xcd.com - Main Activity";
    private static boolean DEBUG = true;

    private SurfaceView surfaceView;
    private static SurfaceHolder sSurfaceHolder = null;

    private String h264File = "test.h264";
    private int width = 720;
    private int height = 1280;

    private HardwareDecoderWrapper decoderWrapper = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        surfaceView = (SurfaceView) findViewById(R.id.video_surface_view);
        SurfaceHolder sh = surfaceView.getHolder();
        sh.addCallback(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (decoderWrapper != null) {
            decoderWrapper.stop();
            decoderWrapper = null;
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (sSurfaceHolder != null) {
            throw new RuntimeException("sSurfaceHolder is already set");
        }

        sSurfaceHolder = holder;

        if (decoderWrapper == null) {
            decoderWrapper = new HardwareDecoderWrapper(getApplicationContext(), h264File, width, height);
        }

        decoderWrapper.start();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        sSurfaceHolder = null;

        if (decoderWrapper != null) {
            decoderWrapper.stop();
            decoderWrapper = null;
        }
    }

    static private class VideoDecoderCallback extends VideoDecoderCallbackStub {

        public VideoDecoderCallback() {

        }

        @Override
        public void onError(int code) throws RemoteException {

        }

        @Override
        public void onSlow(boolean restart) {

        }

        @Override
        public void onDecodeHardwareFrame(long pts) {

        }
    }

    static private class HardwareDecoderWrapper {
        private IVideoDecoder decoder = null;
        private VideoDecoderCallback videoDecoderCallback;

        private AshmemFile inputMemoryFile;
        private int inputMemoryFileSize;

        private HandlerThread thread;
        private H264FrameHandler handler;

        public HardwareDecoderWrapper(Context context, String file, int width, int height) {
            thread = new HandlerThread("H264 Frame Decoder");
            thread.start();

            handler = new H264FrameHandler(context, thread.getLooper(), this, file, width, height);

            try {
                IMediaService mediaService = MediaServiceManager.getInstance().getMediaService();
                decoder = mediaService.createH264HardwareDecoder();
            } catch (RemoteException e) {
                Log.e(LOG_TAG, "Failed to create remote hardware decoder");
                return;
            }

            try {
                videoDecoderCallback = new VideoDecoderCallback();
                decoder.setCallback(videoDecoderCallback);
            } catch (RemoteException e) {
                Log.e(LOG_TAG, "Failed to set callback for remote hardware decoder");
            }
        }

        public void start() {
            handler.sendStart();
        }

        public void stop() {
            handler.sendStop();

            try {
                decoder.release();
            } catch (RemoteException e) {
                Log.e(LOG_TAG, "Failed to release decoder");
            }

            decoder = null;
        }

        public boolean config(int width, int height, Surface surface) {
            try {
                Bundle params = new Bundle();
                params.putInt(IVideoDecoder.KEY_WIDTH, width);
                params.putInt(IVideoDecoder.KEY_HEIGHT, height);
                params.putParcelable(IVideoDecoder.KEY_SURFACE, surface);

                if (!decoder.config(params)) {
                    Log.e(LOG_TAG, "Failed to config decoder");
                    return false;
                }

                ParcelFileDescriptor pfd = decoder.getInputMemoryFile();
                inputMemoryFileSize = decoder.getInputMemoryFileSize();

                try {
                    inputMemoryFile = new AshmemFile(pfd, inputMemoryFileSize, AshmemFile.PROT_WRITE);
                } catch (IOException e) {
                    Log.e(LOG_TAG, "Failed to create decoder's input memory file");
                }

                return true;
            } catch (RemoteException e) {
                Log.e(LOG_TAG, "Failed to create x264 encoder");
            }

            return false;
        }

        public boolean fillFrame(byte[] data, int offset, int size, long pts) {
            try {
                inputMemoryFile.writeBytes(data, offset, 0, size);
                return decoder.fillFrame(0, size, pts);
            } catch (IOException e) {
                Log.e(LOG_TAG, "Failed to write bytes");
            } catch (RemoteException e) {
                Log.e(LOG_TAG, "Failed to fill frame");
            }

            return false;
        }
    }

    private static class H264FrameHandler extends Handler {
        private static final int MSG_START_DECODING = 1;
        private static final int MSG_STOP_DECODING = 2;
        private static final int MSG_READ_FRAME = 3;

        private WeakReference<HardwareDecoderWrapper> h264DecoderWrapper;

        private Context context = null;
        private H264File h264File = null;

        private String file = "";
        private int width = 0;
        private int height = 0;

        private int bufferCapacity;
        private byte buffer[];

        private boolean starting = false;

        private Object stopLock = new Object();
        private boolean stop = false;

        public H264FrameHandler(Context context, Looper looper, HardwareDecoderWrapper wrapper, String file, int width, int height) {
            super(looper);

            h264DecoderWrapper = new WeakReference<HardwareDecoderWrapper>(wrapper);

            this.context = context;
            this.file = file;
            this.width = width;
            this.height = height;

            bufferCapacity = 100 * 1024;
            buffer = new byte[bufferCapacity];
        }

        public void sendStart() {
            sendEmptyMessage(MSG_START_DECODING);
        }

        public void sendStop() {
            sendEmptyMessage(MSG_STOP_DECODING);

            synchronized (stopLock) {
                while (!stop) {
                    try {
                        stopLock.wait();
                    } catch (InterruptedException ie) { /* not expected */ }
                }
            }
        }

        @Override
        public void handleMessage(Message msg) {
            HardwareDecoderWrapper wrapper = h264DecoderWrapper.get();
            if (wrapper == null) {
                return;
            }

            switch (msg.what) {
                case MSG_START_DECODING: {
                    if (!starting) {
                        if (!openFile()) {
                            return;
                        }

                        if (wrapper.config(width, height, sSurfaceHolder.getSurface())) {
                            starting = true;
                            sendEmptyMessage(MSG_READ_FRAME);
                        } else {
                            Log.e(LOG_TAG, "Failed to config decoder");
                        }
                    }
                }
                break;
                case MSG_READ_FRAME: {
                    if (starting) {
                        int ret = h264File.readFrame(buffer, 0, bufferCapacity);

                        if (ret > 0) {
                            if (wrapper.fillFrame(buffer, 0, ret, 0)) {
                                sendEmptyMessageDelayed(MSG_READ_FRAME, 60);
                            }
                        } else if (openFile()){
                            sendEmptyMessage(MSG_READ_FRAME);
                        }
                    }
                }
                break;
                case MSG_STOP_DECODING: {
                    if (starting) {
                        starting = false;
                        h264File.close();

                        synchronized (stopLock) {
                            stop = true;
                            stopLock.notify();
                        }
                    }
                }
                break;
            }
        }

        private boolean openFile() {
            InputStream inputStream = null;

            try {
                AssetManager assetManager = context.getAssets();
                inputStream = assetManager.open(file);
            } catch (IOException e) {
                Log.e(LOG_TAG, "Failed to open file: " + file);
                return false;
            }

            h264File = new H264File(inputStream);
            return true;
        }
    }
}