package com.a0xcc0xcd.cid.sdk.utils;

import android.os.Build;
import android.os.MemoryFile;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.FileDescriptor;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Created by shengyang on 15-10-29.
 */
public class AshmemFile {
    private static String LOG_TAG = "0xcc0xcd.com - Ashmem File";

    // mmap(2) protection flags from <sys/mman.h>
    public static final int PROT_READ = 0x1;
    public static final int PROT_WRITE = 0x2;

    private ParcelFileDescriptor mPFD;
    private FileDescriptor mFD;        // ashmem file descriptor
    private int mAddressInt;   // address of ashmem memory for Build.VERSION.SDK_INT < 21
    private long mAddressLong;   // address of ashmem memory for Build.VERSION.SDK_INT >= 21
    private int mLength;    // total length of our ashmem region

    private static Class mMemoryFileClass = null;
    private static Method mMemoryFileNativeMmap = null;
    private static Method mMemoryFileNativeUnmmap = null;
    private static Method mMemoryFileNativeClose = null;
    private static Method mMemoryFileNativeGetSize = null;
    private static Method mMemoryFileNativeWrite = null;
    private static Method mMemoryFileNativeRead = null;

    static {
        mMemoryFileClass = MemoryFile.class;

        try {
            mMemoryFileNativeMmap = mMemoryFileClass.getDeclaredMethod("native_mmap", new Class[] {FileDescriptor.class, int.class, int.class});
            mMemoryFileNativeMmap.setAccessible(true);

            if (Build.VERSION.SDK_INT < 21) {
                mMemoryFileNativeUnmmap = mMemoryFileClass.getDeclaredMethod("native_munmap", new Class[] {int.class, int.class});
            } else {
                mMemoryFileNativeUnmmap = mMemoryFileClass.getDeclaredMethod("native_munmap", new Class[] {long.class, int.class});
            }
            mMemoryFileNativeUnmmap.setAccessible(true);

            mMemoryFileNativeClose = mMemoryFileClass.getDeclaredMethod("native_close", new Class[] {FileDescriptor.class});
            mMemoryFileNativeClose.setAccessible(true);

            mMemoryFileNativeGetSize = mMemoryFileClass.getDeclaredMethod("native_get_size", new Class[] {FileDescriptor.class});
            mMemoryFileNativeGetSize.setAccessible(true);

            if (Build.VERSION.SDK_INT < 21) {
                mMemoryFileNativeWrite = mMemoryFileClass.getDeclaredMethod("native_write", new Class[] {FileDescriptor.class, int.class, byte[].class, int.class, int.class, int.class, boolean.class});
            } else {
                mMemoryFileNativeWrite = mMemoryFileClass.getDeclaredMethod("native_write", new Class[] {FileDescriptor.class, long.class, byte[].class, int.class, int.class, int.class, boolean.class});
            }
            mMemoryFileNativeWrite.setAccessible(true);

            if (Build.VERSION.SDK_INT < 21) {
                mMemoryFileNativeRead = mMemoryFileClass.getDeclaredMethod("native_read", new Class[] {FileDescriptor.class, int.class, byte[].class, int.class, int.class, int.class, boolean.class});
            } else {
                mMemoryFileNativeRead = mMemoryFileClass.getDeclaredMethod("native_read", new Class[] {FileDescriptor.class, long.class, byte[].class, int.class, int.class, int.class, boolean.class});
            }
            mMemoryFileNativeRead.setAccessible(true);
        } catch (NoSuchMethodException e) {
            Log.e(LOG_TAG, "Failed to get native methods, error: " + e.getMessage());
        }
    }

    public AshmemFile(ParcelFileDescriptor pfd, int length, int mode) throws IOException, NullPointerException, IllegalArgumentException {
        FileDescriptor fd = pfd.getFileDescriptor();
        if (fd == null) {
            throw new NullPointerException("File descriptor is null.");
        }
        if (!isMemoryFile(fd)) {
            throw new IllegalArgumentException("Not a memory file.");
        }

        mLength = length;
        mFD = fd;
        mPFD = pfd;

        if (Build.VERSION.SDK_INT < 21) {
            mAddressInt = native_mmap_int(mFD, length, mode);
        } else {
            mAddressLong = native_mmap_1ong(mFD, length, mode);
        }
    }

    public void close() {
        deactivate();
        if (!isClosed()) {
            //native_close(mFD);
            try {
                mPFD.close();
            } catch (IOException e) {
                Log.e(LOG_TAG, "Failed to close file descriptor");
            }
            mFD = null;
        }
    }

    public void deactivate() {
        if (!isDeactivated()) {
            try {
                if (Build.VERSION.SDK_INT < 21) {
                    native_munmap_int(mAddressInt, mLength);
                    mAddressInt = 0;
                } else {
                    native_munmap_long(mAddressLong, mLength);
                    mAddressLong = 0;
                }
            } catch (IOException ex) {
                Log.e(LOG_TAG, "Failed to deactivate, error: " + ex.toString());
            }
        }
    }

    public FileDescriptor getFileDescriptor() throws IOException {
        return mFD;
    }

    public static boolean isMemoryFile(FileDescriptor fd) throws IOException {
        return (native_get_size(fd) >= 0);
    }

    public static int getSize(FileDescriptor fd) throws IOException {
        return native_get_size(fd);
    }

    private boolean isDeactivated() {
        return Build.VERSION.SDK_INT < 21 ? mAddressInt == 0 : mAddressLong == 0;
    }

    private boolean isClosed() {
        return mFD == null || mFD != null && !mFD.valid();
    }

    @Override
    protected void finalize() {
        if (!isClosed()) {
            Log.e(LOG_TAG, "MemoryFile.finalize() called while ashmem still open");
            close();
        }
    }

    public int length() {
        return mLength;
    }

    public int readBytes(byte[] buffer, int srcOffset, int destOffset, int count)
            throws IOException {
        if (isDeactivated()) {
            throw new IOException("Can't read from deactivated memory file.");
        }
        if (destOffset < 0 || destOffset > buffer.length || count < 0
                || count > buffer.length - destOffset
                || srcOffset < 0 || srcOffset > mLength
                || count > mLength - srcOffset) {
            throw new IndexOutOfBoundsException();
        }

        if(Build.VERSION.SDK_INT < 21) {
            return native_read_int(mFD, mAddressInt, buffer, srcOffset, destOffset, count, false);
        } else {
            return native_read_long(mFD, mAddressLong, buffer, srcOffset, destOffset, count, false);
        }
    }

    public void writeBytes(byte[] buffer, int srcOffset, int destOffset, int count)
            throws IOException {
        if (isDeactivated()) {
            throw new IOException("Can't write to deactivated memory file.");
        }
        if (srcOffset < 0 || srcOffset > buffer.length || count < 0
                || count > buffer.length - srcOffset
                || destOffset < 0 || destOffset > mLength
                || count > mLength - destOffset) {
            throw new IndexOutOfBoundsException();
        }

        if(Build.VERSION.SDK_INT < 21) {
            native_write_int(mFD, mAddressInt, buffer, srcOffset, destOffset, count, false);
        } else {
            native_write_long(mFD, mAddressLong, buffer, srcOffset, destOffset, count, false);
        }
    }

    private static int native_mmap_int(FileDescriptor fd, int length, int mode) throws IOException {
        int ret = -1;

        try {
            ret = (Integer)mMemoryFileNativeMmap.invoke(null, new Object[] {fd, length, mode});
        } catch (IllegalAccessException e) {
            Log.e(LOG_TAG, "Failed to invoke native_mmap_int, error (IllegalAccessException): " + e.getMessage());
        } catch (InvocationTargetException e) {
            Throwable throwable = e.getTargetException();
            if (throwable != null) {
                Log.e(LOG_TAG, "Failed to invoke native_mmap_int, error (InvocationTargetException): " + throwable.getMessage());
            } else {
                Log.e(LOG_TAG, "Failed to invoke native_mmap_int, error (InvocationTargetException): no target exception");
            }
        }

        return ret;
    }

    private static long native_mmap_1ong(FileDescriptor fd, int length, int mode) throws IOException {
        long ret = -1;

        try {
            ret = (Long)mMemoryFileNativeMmap.invoke(null, new Object[] {fd, length, mode});
        } catch (IllegalAccessException e) {
            Log.e(LOG_TAG, "Failed to invoke native_mmap_long, error (IllegalAccessException): " + e.getMessage());
        } catch (InvocationTargetException e) {
            Throwable throwable = e.getTargetException();
            if (throwable != null) {
                Log.e(LOG_TAG, "Failed to invoke native_mmap_long, error (InvocationTargetException): " + throwable.getMessage());
            } else {
                Log.e(LOG_TAG, "Failed to invoke native_mmap_long, error (InvocationTargetException): no target exception");
            }
        }

        return ret;
    }

    private static void native_munmap_int(int addr, int length) throws IOException {
        try {
            mMemoryFileNativeUnmmap.invoke(null, new Object[] {addr, length});
        } catch (IllegalAccessException e) {
            Log.e(LOG_TAG, "Failed to invoke native_munmap_int, error (IllegalAccessException): " + e.getMessage());
        } catch (InvocationTargetException e) {
            Throwable throwable = e.getTargetException();
            if (throwable != null) {
                Log.e(LOG_TAG, "Failed to invoke native_munmap_int, error (InvocationTargetException): " + throwable.getMessage());
            } else {
                Log.e(LOG_TAG, "Failed to invoke native_munmap_int, error (InvocationTargetException): no target exception");
            }
        }
    }

    private static void native_munmap_long(long addr, int length) throws IOException {
        try {
            mMemoryFileNativeUnmmap.invoke(null, new Object[] {addr, length});
        } catch (IllegalAccessException e) {
            Log.e(LOG_TAG, "Failed to invoke native_munmap_long, error (IllegalAccessException): " + e.getMessage());
        } catch (InvocationTargetException e) {
            Throwable throwable = e.getTargetException();
            if (throwable != null) {
                Log.e(LOG_TAG, "Failed to invoke native_munmap_long, error (InvocationTargetException): " + throwable.getMessage());
            } else {
                Log.e(LOG_TAG, "Failed to invoke native_munmap_long, error (InvocationTargetException): no target exception");
            }
        }
    }

    private static int native_get_size(FileDescriptor fd) throws IOException {
        int ret = -1;

        try {
            ret = (Integer)mMemoryFileNativeGetSize.invoke(null, new Object[] {fd});
        } catch (IllegalAccessException e) {
            Log.e(LOG_TAG, "Failed to invoke native_get_size, error (IllegalAccessException): " + e.getMessage());
        } catch (InvocationTargetException e) {
            Throwable throwable = e.getTargetException();
            if (throwable != null) {
                Log.e(LOG_TAG, "Failed to invoke native_get_size, error (InvocationTargetException): " + throwable.getMessage());
            } else {
                Log.e(LOG_TAG, "Failed to invoke native_get_size, error (InvocationTargetException): no target exception");
            }
        }

        return ret;
    }

    private static void native_close(FileDescriptor fd) {
        try {
            mMemoryFileNativeClose.invoke(null, new Object[] {fd});
        } catch (IllegalAccessException e) {
            Log.e(LOG_TAG, "Failed to invoke native_close, error (IllegalAccessException): " + e.getMessage());
        } catch (InvocationTargetException e) {
            Throwable throwable = e.getTargetException();
            if (throwable != null) {
                Log.e(LOG_TAG, "Failed to invoke native_close, error (InvocationTargetException): " + throwable.getMessage());
            } else {
                Log.e(LOG_TAG, "Failed to invoke native_close, error (InvocationTargetException): no target exception");
            }
        }
    }

    private static int native_read_int(FileDescriptor fd, int address, byte[] buffer,
                                       int srcOffset, int destOffset, int count, boolean isUnpinned) throws IOException {
        int ret = -1;

        try {
            ret = (Integer)mMemoryFileNativeRead.invoke(null, new Object[] {fd, address, buffer, srcOffset, destOffset, count, isUnpinned});
        } catch (IllegalAccessException e) {
            Log.e(LOG_TAG, "Failed to invoke native_read_int, error (IllegalAccessException): " + e.getMessage());
        } catch (InvocationTargetException e) {
            Throwable throwable = e.getTargetException();
            if (throwable != null) {
                Log.e(LOG_TAG, "Failed to invoke native_read_int, error (InvocationTargetException): " + throwable.getMessage());
            } else {
                Log.e(LOG_TAG, "Failed to invoke native_read_int, error (InvocationTargetException): no target exception");
            }
        }

        return ret;
    }

    private static int native_read_long(FileDescriptor fd, long address, byte[] buffer,
                                        int srcOffset, int destOffset, int count, boolean isUnpinned) throws IOException {
        int ret = -1;

        try {
            ret = (Integer)mMemoryFileNativeRead.invoke(null, new Object[] {fd, address, buffer, srcOffset, destOffset, count, isUnpinned});
        } catch (IllegalAccessException e) {
            Log.e(LOG_TAG, "Failed to invoke native_read_long, error (IllegalAccessException): " + e.getMessage());
        } catch (InvocationTargetException e) {
            Throwable throwable = e.getTargetException();
            if (throwable != null) {
                Log.e(LOG_TAG, "Failed to invoke native_read_long, error (InvocationTargetException): " + throwable.getMessage());
            } else {
                Log.e(LOG_TAG, "Failed to invoke native_read_long, error (InvocationTargetException): no target exception");
            }
        }

        return ret;
    }

    private static void native_write_int(FileDescriptor fd, int address, byte[] buffer,
                                         int srcOffset, int destOffset, int count, boolean isUnpinned) throws IOException {
        try {
            mMemoryFileNativeWrite.invoke(null, new Object[] {fd, address, buffer, srcOffset, destOffset, count, isUnpinned});
        } catch (IllegalAccessException e) {
            Log.e(LOG_TAG, "Failed to invoke native_write_int, error (IllegalAccessException): " + e.getMessage());
        } catch (InvocationTargetException e) {
            Throwable throwable = e.getTargetException();
            if (throwable != null) {
                Log.e(LOG_TAG, "Failed to invoke native_write_int, error (InvocationTargetException): " + throwable.getMessage());
            } else {
                Log.e(LOG_TAG, "Failed to invoke native_write_int, error (InvocationTargetException): no target exception");
            }
        }
    }

    private static void native_write_long(FileDescriptor fd, long address, byte[] buffer,
                                          int srcOffset, int destOffset, int count, boolean isUnpinned) throws IOException {
        try {
            mMemoryFileNativeWrite.invoke(null, new Object[] {fd, address, buffer, srcOffset, destOffset, count, isUnpinned});
        } catch (IllegalAccessException e) {
            Log.e(LOG_TAG, "Failed to invoke native_write_long, error (IllegalAccessException): " + e.getMessage());
        } catch (InvocationTargetException e) {
            Throwable throwable = e.getTargetException();
            if (throwable != null) {
                Log.e(LOG_TAG, "Failed to invoke native_write_long, error (InvocationTargetException): " + throwable.getMessage());
            } else {
                Log.e(LOG_TAG, "Failed to invoke native_write_long, error (InvocationTargetException): no target exception");
            }
        }
    }
}