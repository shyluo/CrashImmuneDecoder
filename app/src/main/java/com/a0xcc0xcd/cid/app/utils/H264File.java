package com.a0xcc0xcd.cid.app.utils;

import android.util.Log;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Created by shengyang on 16-2-22.
 */
public class H264File {
    private static final String LOG_TAG = "0xcc0xcd.com - H264 File";
    private static boolean DEBUG = true;

    private OutputStream outFile = null;
    private InputStream inFile = null;

    public H264File(String filePath, boolean read) {
        try {
            if (read) {
                inFile = new FileInputStream(filePath);
            } else {
                outFile = new FileOutputStream(filePath);
            }
        } catch (FileNotFoundException e) {
            Log.e(LOG_TAG, "Failed to open h264 file");
        }
    }

    public H264File(InputStream inputStream) {
        inFile = inputStream;
    }

    public H264File(OutputStream outputStream) {
        outFile = outputStream;
    }

    public void writeHeader(byte[] header, int offset, int size) {
        try {
            writeInt(size);
            outFile.write(header, offset, size);
        } catch (IOException e) {
            Log.e(LOG_TAG, "Failed to write h264 header");
        }
    }

    public void writeFrame(byte[] frame, int offset, int size) {
        try {
            writeInt(size);
            outFile.write(frame, offset, size);
        } catch (IOException e) {
            Log.e(LOG_TAG, "Failed to write h264 frame");
        }
    }

    public int readFrame(byte[] buffer, int offset, int capacity) {
        int size = 0;

        try {
            size = readInt();
            if (size > capacity || size <= 0) {
                return 0;
            }

            inFile.read(buffer, offset, size);
        } catch (IOException e) {
            Log.e(LOG_TAG, "Failed to read h264 frame");
        }

        return size;
    }

    public void close() {
        if(outFile != null) {
            try {
                outFile.close();
            } catch (IOException e) {
                Log.e(LOG_TAG, "Failed to close file");
            }
        }

        if(inFile != null) {
            try {
                inFile.close();
            } catch (IOException e) {
                Log.e(LOG_TAG, "Failed to close file");
            }
        }
    }

    private void writeInt(int i) {
        byte a = (byte)(i & 0xf);
        byte b = (byte)((i >> 8) & 0xf);
        byte c = (byte)((i >> 16) & 0xf);
        byte d = (byte)((i >> 24) & 0xf);

        try {
            outFile.write(d);
            outFile.write(c);
            outFile.write(b);
            outFile.write(a);
        } catch (IOException e) {
            Log.e(LOG_TAG, "Failed to write integer to file");
        }
    }

    private int readInt() {
        int i = 0;

        try {
            int a = inFile.read();
            int b = inFile.read();
            int c = inFile.read();
            int d = inFile.read();

            i = a << 24 | b << 16 | c << 8 | d;
        } catch (IOException e) {
            Log.e(LOG_TAG, "Failed to read integer from file");
        }

        return i;
    }
}