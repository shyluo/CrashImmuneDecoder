package com.a0xcc0xcd.cid.sdk;

import android.os.Handler;
import android.os.HandlerThread;

import android.util.Log;

import java.util.HashMap;

/**
 * Created by shengyang on 16-11-25.
 */
public class WatchDog {
    private static final String LOG_TAG = "0xcc0xcd.com - Watch Dog";
    private static boolean DEBUG = true;

    private static WatchDog sInstance = new WatchDog();

    private int key = 0;
    private HashMap<Integer, MonitorTask> monitorTasks;

    private HandlerThread monitorThread;
    private Handler monitorHandler;

    private WatchDog() {
        monitorTasks = new HashMap<>();
        monitorThread = new HandlerThread("Monitor Thread");
        monitorThread.start();
        monitorHandler = new Handler(monitorThread.getLooper());
    }

    static public WatchDog getInstance() {
        return sInstance;
    }

    public interface Monitor {
        public void onTimeout();
    }

    synchronized public int addTask(String name, Handler handler, int interval, int firstTimeout, int timeout, Monitor monitor) {
        int taskKey = key++;

        MonitorTask task = new MonitorTask(taskKey, name, handler, interval, firstTimeout, timeout, monitor);
        monitorTasks.put(taskKey, task);

        task.start();

        return taskKey;
    }

    synchronized public void removeTask(int taskKey) {
        MonitorTask task = monitorTasks.remove(taskKey);
        task.stop();
    }

    synchronized public boolean hasTask(int key) {
        return monitorTasks.containsKey(key);
    }

    public Handler getMonitorHandler() {
        return monitorHandler;
    }

    private static class MonitorTask implements Runnable {
        private String name;
        private long lastDetectTime;
        private int interval;
        private int timeout;
        private int firstTimeout;
        private Handler handler;
        private int key;
        private Monitor monitor;

        public MonitorTask(int key, String name, Handler handler, int interval, int firstTmeout, int timeout, Monitor monitor) {
            this.key = key;
            this.name = name;
            this.handler = handler;
            this.interval = interval;
            this.firstTimeout = firstTmeout;
            this.timeout = timeout;
            this.monitor = monitor;
            this.lastDetectTime = 0;
        }

        public void start() {
            lastDetectTime = System.currentTimeMillis();
            handler.post(this);

            Handler monitorHandler = WatchDog.getInstance().getMonitorHandler();
            monitorHandler.postDelayed(detector, firstTimeout);
        }

        public void stop() {
            Handler monitorHandler = WatchDog.getInstance().getMonitorHandler();
            monitorHandler.removeCallbacks(detector);
        }

        @Override
        public void run() {
            //Log.d(LOG_TAG, "Detect " + name);

            Handler monitorHandler = WatchDog.getInstance().getMonitorHandler();
            monitorHandler.removeCallbacks(detector);

            if (WatchDog.getInstance().hasTask(key)) {
                long now = System.currentTimeMillis();
                long next = lastDetectTime + interval;

                long delay = next > now ? next - now : 0;
                handler.postDelayed(this, delay);

                monitorHandler.postDelayed(detector, delay + timeout);

                lastDetectTime = now + delay;
            }
        }

        private Runnable detector = new Runnable() {
            @Override
            public void run() {
                if (!WatchDog.getInstance().hasTask(key)) {
                    return;
                }

                Log.w(LOG_TAG, name + " has stopped, last detect time: " + lastDetectTime + ", interval: " + interval + ", now: " + System.currentTimeMillis());

                monitor.onTimeout();
            }
        };
    }
}