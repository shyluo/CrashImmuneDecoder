package com.a0xcc0xcd.cid.sdk.video;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.*;
import android.util.Log;
import android.view.Surface;

import com.a0xcc0xcd.cid.sdk.WatchDog;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.LinkedList;

/**
 * Created by shengyang on 16-3-31.
 */
public class LocalHardwareDecoder extends LocalVideoDecoder implements WatchDog.Monitor {
    private static final String LOG_TAG = "0xcc0xcd.com - Local Hardware Decoder";
    private static boolean DEBUG = true;

    private static final int MAX_FRAME_BUFFER_COUNT = 10;// 4;
    private static final int MAX_WAIT_FREE_FRAME_BUFFER_TIME = 200;

    private static final int MAX_EXCEPTION_COUNT = 100;

    private static final int DRAIN_RESULT_FIRST_FRAME = 0;
    private static final int DRAIN_RESULT_SUCCEEDED = 1;
    private static final int DRAIN_RESULT_FAILED = 2;
    private static final int DRAIN_RESULT_TRY_AGAIN = 3;

    private String decoderType;
    private MediaCodec codec = null;

    private MediaCodec.BufferInfo bufferInfo = null;
    private ByteBuffer[] inputBuffers = null;
    private ByteBuffer[] outputBuffers = null;

    private boolean decoderOwnsSurface = false;

    private Surface surface = null;
    private int width = 0;
    private int height = 0;

    private int dropFrames = 0;

    private WeakReference<VideoDecoderClient> weakClient;

    private int allocatedBufferCount = 0;
    private LinkedList<QueuedBuffer> freeBufferQueue = null;

    private MainThread mainThread;

    private int dequeueInputBufferExceptionCount = 0;
    private int dequeueOutputBufferExceptionCount = 0;

    private Object waitingLock = new Object();
    private boolean isWaiting = false;
    private long waitingTime = 0;

    private boolean isSlow = false;

    private int monitorKey;

    private static class QueuedBuffer {
        public ByteBuffer buffer = null;
        public int size = 0;
        public int capacity = 0;
        public long pts = 0;

        public QueuedBuffer(int c) {
            capacity = c;
            //buffer = new byte[capacity];
            buffer = ByteBuffer.allocateDirect(c);

            size = 0;
            pts = 0;
        }

        public void resize(int c) {
            capacity = c;
            //buffer = new byte[c];
            buffer = ByteBuffer.allocateDirect(c);
        }
    }

    public LocalHardwareDecoder(VideoDecoderClient client, String decoderType) throws Exception {
        if (!supportsHardwareDecoder(decoderType)) {
            throw new Exception("Unsupport hardware decoder for " + decoderType + " on api level: " + Build.VERSION.SDK_INT);
        }

        weakClient = new WeakReference<VideoDecoderClient>(client);
        this.decoderType = decoderType;

        freeBufferQueue = new LinkedList<QueuedBuffer>();
    }

    public boolean config(int width, int height, Surface surface) {
        if (!surface.isValid()) {
            Log.e(LOG_TAG, "Surface is not valid");
            return false;
        }

        release();

        int frameSize = 100 * 1024; // 100KB

        allocatedBufferCount = 2;
        freeBufferQueue.addLast(new QueuedBuffer(frameSize));
        freeBufferQueue.addLast(new QueuedBuffer(frameSize));

        this.width = width;
        this.height = height;
        this.surface = surface;

        mainThread = new MainThread(this);
        mainThread.setName("Decoder Main Thread");
        mainThread.start();
        mainThread.waitUntilStartup();

        monitorKey = WatchDog.getInstance().addTask("Hardware Decoder", mainThread.getHandler(), 1000, 1000, 800, this);

        mainThread.waitUntilSetup();

        if(codec == null) {
            Log.e(LOG_TAG, "Failed to setup main thread");
            mainThread = null;
            return false;
        }

        return true;
    }

    @Override
    public void onTimeout() {
        Log.w(LOG_TAG, "Decoder main thread is not reachable, kill self");
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    public boolean fillFrame(ByteBuffer buffer, int offset, int size, long pts) {
        if (mainThread == null) {
            Log.e(LOG_TAG, "Hardware Decoder is not configed");
            return false;
        }

        if (size < 5) {
            Log.e(LOG_TAG, "Frame size is too small: " + size);
            return false;
        }

        MainHandler mainHandler = mainThread.getHandler();

        QueuedBuffer frame;

        synchronized (freeBufferQueue) {
            frame = freeBufferQueue.pollFirst();

            if (frame == null && allocatedBufferCount >= MAX_FRAME_BUFFER_COUNT) {
                try {
                    freeBufferQueue.wait(MAX_WAIT_FREE_FRAME_BUFFER_TIME);
                } catch (InterruptedException e) {
                    if (mainHandler != null) {
                        mainHandler.sendDrainBuffer();
                    }
                    return false;
                }

                frame = freeBufferQueue.pollFirst();
            }
        }

        if (frame == null && allocatedBufferCount < MAX_FRAME_BUFFER_COUNT) {
            ++allocatedBufferCount;
            int frameSize = 100 * 1024; // 100KB
            frame = new QueuedBuffer(frameSize);
            //frame = new QueuedBuffer(width * height * 3 / 2);
        }

        if (frame == null) {
            if (mainHandler != null) {
                mainHandler.sendDrainBuffer();
            }
            Log.i(LOG_TAG, "Drop Frames: " + (++dropFrames));
            return false;
        }

        if (frame.capacity < size) {
            frame.resize(size);
        }

        frame.size = size;
        frame.pts = pts;

        buffer.position(offset);
        buffer.get(frame.buffer.array(), frame.buffer.arrayOffset(), size);


        synchronized (waitingLock) {
            if (isWaiting) {
                long now = System.currentTimeMillis();
                if (now - waitingTime > 5) {
                    isSlow = true;
                }
            }
        }

        if (isSlow) {
            if (decoderType.equalsIgnoreCase("video/avc")) {
                //int info = H264NALUParser.getInfo(frame.buffer, 0, frame.size);
                //boolean isRefed = H264NALUParser.isRefed(info);
                //
                //if (!isRefed) {
                //    isSlow = false;
                //
                //    recycleBuffer(frame);
                //    Log.w(LOG_TAG, "Drop buffer in advance");
                //
                //    if (mainHandler != null) {
                //        mainHandler.sendDrainBuffer();
                //    }
                //
                //    return true;
                //}
            }
        }

        if (mainHandler != null) {
            mainHandler.sendFrameAvailable(frame);
        } else {
            recycleBuffer(frame);
            Log.e(LOG_TAG, "Main handler is null while filling frame");
            return false;
        }

        return true;
    }

    public void release() {
        if (mainThread != null) {
            MainHandler mh = mainThread.getHandler();
            if (mh != null) {
                mh.sendShutdown();

                try {
                    mainThread.join();
                } catch (InterruptedException ie) {
                    // not expected
                    throw new RuntimeException("join main thread was interrupted: ", ie);
                }

                WatchDog.getInstance().removeTask(monitorKey);
                monitorKey = 0;

                mainThread = null;
            } else {
                Log.e(LOG_TAG, "Main handler is null while releasing");
            }
        }

        if (freeBufferQueue != null) {
            while (!freeBufferQueue.isEmpty()) {
                freeBufferQueue.pollFirst();
            }
        }

        allocatedBufferCount = 0;
    }

    void setOwnsSurface(boolean own) {
        decoderOwnsSurface = own;
    }

    private boolean initCodec() {
        try {
            codec = MediaCodec.createDecoderByType(decoderType);

            MediaFormat format = MediaFormat.createVideoFormat(decoderType, width, height);
            codec.configure(format, surface, null, 0);

            bufferInfo = new MediaCodec.BufferInfo();

            codec.start();

            inputBuffers = codec.getInputBuffers();
            outputBuffers = codec.getOutputBuffers();

            return true;
        } catch (Exception e) {
            Log.i(LOG_TAG, "Failed to create hardware decoder, type: " + decoderType +
                    ", width: " + width + ", height: " + height +
                    "error msg: " + e.getMessage());

            destroyCodec();
        }

        return false;
    }

    private void destroyCodec() {
        if (codec != null) {
            try {
                codec.stop();
            } catch (Exception e) {
                Log.e(LOG_TAG, "Failed to stop hardware decoder, error: " + e.getMessage());
            }

            codec.release();

            codec = null;
        }

        if (surface != null) {
            if (decoderOwnsSurface) {
                surface.release();
            }

            surface = null;
        }
    }

    private boolean dropBuffer(QueuedBuffer buffer) {
        if (decoderType.equalsIgnoreCase("video/avc")) {
            //int info = H264NALUParser.getInfo(buffer.buffer, 0, buffer.size);
            //int type = H264NALUParser.getType(info);
            //boolean isRefed = H264NALUParser.isRefed(info);
            //
            //Log.i(LOG_TAG, "Overrun frame, type: " + type + ", refed: " + isRefed);
            //
            //return !isRefed;
        }

        return false;
    }

    private void decoderSlow(boolean restart) {
        Log.w(LOG_TAG, "Decoder is slow");
        VideoDecoderClient client = weakClient.get();
        if (client != null) {
            client.onSlow(restart);
        }
    }

    private boolean fillBuffer(QueuedBuffer buffer) {
        if (codec == null) {
            return false;
        }

        try {
            final int TIMEOUT_USEC = 500000;

            long begin = System.currentTimeMillis();

            boolean result = true;
            long end = 0;
            long usingTime = 0;

            synchronized (waitingLock) {
                isWaiting = true;
                waitingTime = begin;
            }

            int index = codec.dequeueInputBuffer(TIMEOUT_USEC);
            if (index >= 0) {
                dequeueInputBufferExceptionCount = 0;

                ByteBuffer inputBuffer = inputBuffers[index];
                inputBuffer.clear();
                inputBuffer.put(buffer.buffer.array(), buffer.buffer.arrayOffset(), buffer.size);

                codec.queueInputBuffer(index, 0, buffer.size, buffer.pts, 0);

                end = System.currentTimeMillis();
                usingTime = end - begin;

                Log.d(LOG_TAG, "Succeeded to dequeue input buffer, using time: " + usingTime + ", pts: " + buffer.pts);
            } else {
                end = System.currentTimeMillis();
                usingTime = end - begin;

                Log.e(LOG_TAG, "Failed to dequeue input buffer, using time: " + usingTime);

                result = false;
            }

            synchronized (waitingLock) {
                isWaiting = false;
            }

            drainBuffer(0);

            return result;
        } catch (Exception e) {
            Log.e(LOG_TAG, "Failed to dequeue or queue input buffer, err: " + e.getMessage());

            dequeueInputBufferExceptionCount++;

            if (dequeueInputBufferExceptionCount > MAX_EXCEPTION_COUNT) {
                VideoDecoderClient client = weakClient.get();
                if (client != null) {
                    client.onError(LocalVideoDecoder.HARDWARE_DECODER_ERROR);
                }
            }
        }

        drainBuffer(0);

        return false;
    }

    private int drainBuffer(long timeout) {
        if(codec == null) {
            return DRAIN_RESULT_FAILED;
        }

        try {
            int status = codec.dequeueOutputBuffer(bufferInfo, 10000);

            if (status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat format = codec.getOutputFormat();
                Log.d(LOG_TAG, "Output format changed: " + format);
                return DRAIN_RESULT_FIRST_FRAME;
            } else if (status == MediaCodec.INFO_TRY_AGAIN_LATER) {
                //Log.i(LOG_TAG, "Try again to dequeue output buffer");
                return DRAIN_RESULT_TRY_AGAIN;
            } else if (status == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                //Log.i(LOG_TAG, "Output buffer changed");
                outputBuffers = codec.getOutputBuffers();
                return DRAIN_RESULT_TRY_AGAIN;
            } else if (status < 0) {
                //Log.i(LOG_TAG, "Failled to dequeue output buffer");
                return DRAIN_RESULT_FAILED;
            } else {
                ByteBuffer outBuffer = outputBuffers[status];
                codec.releaseOutputBuffer(status, true);

                VideoDecoderClient client = weakClient.get();
                if (client != null) {
                    client.onDecodeHardwareFrame(bufferInfo.presentationTimeUs);
                }

                dequeueOutputBufferExceptionCount = 0;

                long end = System.currentTimeMillis();

                return DRAIN_RESULT_SUCCEEDED;
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "Failed to dequeue or queue output buffer, err: " + e.getMessage());

            dequeueOutputBufferExceptionCount++;

            if (dequeueOutputBufferExceptionCount > MAX_EXCEPTION_COUNT) {
                VideoDecoderClient client = weakClient.get();
                if (client != null) {
                    client.onError(LocalVideoDecoder.HARDWARE_DECODER_ERROR);
                }
            }
        }

        return DRAIN_RESULT_FAILED;
    }

    private void recycleBuffer(QueuedBuffer buffer) {
        synchronized (freeBufferQueue) {
            freeBufferQueue.addLast(buffer);
            freeBufferQueue.notify();
        }
    }

    private static class MainThread extends Thread {
        private volatile MainHandler mainHandler;
        private WeakReference<LocalHardwareDecoder> weakHardwareDecoder;

        // Used to wait for the thread to start.
        private Object startLock = new Object();
        private boolean startup = false;

        private Object setupLock = new Object();
        private boolean setup = false;

        public MainThread(LocalHardwareDecoder decoder) {
            weakHardwareDecoder = new WeakReference<LocalHardwareDecoder>(decoder);
        }

        /**
         * Thread entry point.
         */
        @Override
        public void run() {
            Looper.prepare();

            // We need to create the Handler before reporting ready.
            mainHandler = new MainHandler(this);

            synchronized (startLock) {
                startup = true;
                startLock.notify();    // signal waitUntilReady()
            }

            boolean suc = openCodec();

            synchronized (setupLock) {
                setup = true;
                setupLock.notify();    // signal waitUntilSetup()
            }

            if (!suc) {
                Log.e(LOG_TAG, "Failed to open codec");
                return;
            }

            Looper.loop();

            Log.i(LOG_TAG, "looper quit");

            releaseCodec();

            synchronized (startLock) {
                startup = false;
            }

            synchronized (setupLock) {
                setup = false;
            }
        }

        public void waitUntilStartup() {
            synchronized (startLock) {
                while (!startup) {
                    try {
                        startLock.wait();
                    } catch (InterruptedException ie) { /* not expected */ }
                }
            }
        }

        public void waitUntilSetup() {
            synchronized (setupLock) {
                while (!setup) {
                    try {
                        setupLock.wait();
                    } catch (InterruptedException ie) { /* not expected */ }
                }
            }
        }

        public MainHandler getHandler() {
            return mainHandler;
        }

        private void shutdown() {
            Log.d(LOG_TAG, "shutdown");
            Looper.myLooper().quit();
        }

        private boolean openCodec() {
            LocalHardwareDecoder decoder = weakHardwareDecoder.get();
            if (decoder != null) {
                if (!decoder.initCodec()) {
                    Log.e(LOG_TAG, "Failed to init codec");
                    return false;
                }
            }
            return true;
        }

        private void releaseCodec() {
            LocalHardwareDecoder decoder = weakHardwareDecoder.get();
            if (decoder != null) {
                decoder.destroyCodec();
            }
        }

        private void frameAvailable(QueuedBuffer buffer, boolean overrun) {
            LocalHardwareDecoder decoder = weakHardwareDecoder.get();
            if (decoder!= null) {
                if (!overrun || !decoder.dropBuffer(buffer)) {
                    if (overrun) {
                        decoder.decoderSlow(false);
                    }

                    if (!decoder.fillBuffer(buffer)) {
                        Log.e(LOG_TAG, "Failed to fill buffer");
                    }
                } else {
                    Log.w(LOG_TAG, "Drop buffer");

                    decoder.drainBuffer(0);
                }
            }
        }

        private void drainBuffer() {
            LocalHardwareDecoder decoder = weakHardwareDecoder.get();
            if (decoder!= null) {
                decoder.drainBuffer(0);
            }
        }

        private void recycleBuffer(QueuedBuffer buffer) {
            LocalHardwareDecoder decoder = weakHardwareDecoder.get();
            if (decoder!= null) {
                decoder.recycleBuffer(buffer);
            }
        }
    }

    private static class MainHandler extends Handler {
        public static final int MSG_FRAME_AVAILABLE = 1;
        public static final int MSG_SHUTDOWN = 2;
        public static final int MSG_OVERRUN_FRAME_AVAILABLE = 3;
        public static final int MSG_DRAIN_BUFFER = 4;

        private WeakReference<MainThread> weakMainThread;

        public MainHandler(MainThread thread) {
            weakMainThread = new WeakReference<MainThread>(thread);
        }

        public void sendFrameAvailable(QueuedBuffer frame) {
            boolean overrun = hasMessages(MSG_FRAME_AVAILABLE) || hasMessages(MSG_OVERRUN_FRAME_AVAILABLE);

            sendMessage(obtainMessage(overrun ? MSG_OVERRUN_FRAME_AVAILABLE : MSG_FRAME_AVAILABLE, frame));
        }

        public void sendDrainBuffer() {
            sendMessage(obtainMessage(MSG_DRAIN_BUFFER));
        }

        public void sendShutdown() {
            sendMessage(obtainMessage(MSG_SHUTDOWN));
        }

        @Override
        public void handleMessage(Message msg) {
            int what = msg.what;
            //Log.d(TAG, "RenderHandler [" + this + "]: what=" + what);

            MainThread mainThread = weakMainThread.get();
            if (mainThread == null) {
                Log.w(LOG_TAG, "MainHandler.handleMessage: weak ref is null");
                return;
            }

            switch (what) {
                case MSG_FRAME_AVAILABLE: {
                    QueuedBuffer buffer = (QueuedBuffer)msg.obj;
                    mainThread.frameAvailable(buffer, false);
                    mainThread.recycleBuffer(buffer);
                } break;
                case MSG_OVERRUN_FRAME_AVAILABLE: {
                    QueuedBuffer buffer = (QueuedBuffer)msg.obj;
                    mainThread.frameAvailable(buffer, true);
                    mainThread.recycleBuffer(buffer);
                } break;
                case MSG_DRAIN_BUFFER: {
                    mainThread.drainBuffer();
                } break;
                case MSG_SHUTDOWN: {
                    mainThread.shutdown();
                } break;
            }
        }
    }
}
