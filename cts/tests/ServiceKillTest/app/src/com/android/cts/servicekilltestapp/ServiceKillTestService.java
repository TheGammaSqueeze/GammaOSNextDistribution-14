/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.android.cts.servicekilltestapp;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;
import android.os.SystemClock;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ServiceKillTestService extends Service {

    /**
     * Execution times for each measure
     */
    public static final long HOUR_IN_MS = TimeUnit.HOURS.toMillis(1);
    public static final long ALARM_REPEAT_MS = TimeUnit.MINUTES.toMillis(10);
    public static final long ALARM_REPEAT_MARGIN_MS = TimeUnit.SECONDS.toMillis(30);
    public static final long PERSIST_BENCHMARK_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(30);
    public static final long WORK_REPEAT_MS = TimeUnit.SECONDS.toMillis(10);
    public static final long MAIN_REPEAT_MS = TimeUnit.SECONDS.toMillis(10);

    /**
     * Actions and extras
     */
    public static final String TEST_CASE_PACKAGE_NAME = "com.android.cts.servicekilltest";
    public static final String TEST_APP_PACKAGE_NAME = TEST_CASE_PACKAGE_NAME + "app";
    public static final String ACTION_START = TEST_CASE_PACKAGE_NAME + ".ACTION_START";
    public static final String ACTION_STOP = TEST_CASE_PACKAGE_NAME + ".ACTION_STOP";
    public static final String ACTION_RESULT = TEST_CASE_PACKAGE_NAME + ".ACTION_RESULT";
    private static final String ACTION_ALARM = TEST_CASE_PACKAGE_NAME + ".ACTION_ALARM";
    public static final String EXTRA_TEST_ID = "test_id";


    public static final String APP = "CTSServiceKillTest";
    public static final String TAG = "ServiceKillTest";

    public static String NOTIFICATION_CHANNEL_FOREGROUND = "foreground";

    private PowerManager.WakeLock mWakeLock;
    private Handler mHandler;
    private ScheduledExecutorService mScheduledExecutor;
    private ExecutorService mExecutor;


    private Benchmark mCurrentBenchmark;

    private boolean mStarted = false;

    private ScheduledFuture<?> mScheduledFuture;

    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private BroadcastReceiver mAlarmReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null && ACTION_ALARM.equals(intent.getAction())) {
                logDebug("Alarm");
                mCurrentBenchmark.addEvent(Benchmark.Measure.ALARM, SystemClock.elapsedRealtime());
                scheduleAlarm();
                saveBenchmarkIfRequired(mCurrentBenchmark);
            }

        }
    };

    private Runnable mMainRunnable = new Runnable() {
        @Override
        public void run() {
            logDebug("Main");
            if (mWakeLock.isHeld()) {
                mCurrentBenchmark.addEvent(Benchmark.Measure.MAIN, SystemClock.elapsedRealtime());
                saveBenchmarkIfRequired(mCurrentBenchmark);
            } else {
                Log.w(TAG, "Wake lock broken");
            }
            mHandler.postDelayed(this, MAIN_REPEAT_MS);
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


    @Override
    public void onCreate() {
        super.onCreate();
        logDebug("onCreate()");
        PowerManager powerManager = getSystemService(PowerManager.class);
        mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, APP + "::" + TAG);
        mWakeLock.acquire();
        mExecutor = Executors.newSingleThreadExecutor();
        mHandler = new Handler();
        startForeground();
        loadBenchmarkAsync(benchmark -> {
            logDebug("Loading Benchmark " + benchmark);

            if (benchmark == null || !benchmark.isRunning()) {
                mCurrentBenchmark = new Benchmark();
                logDebug("New Benchmark " + mCurrentBenchmark);
            } else {
                mCurrentBenchmark = benchmark;
            }
            startBenchmark();
        });

    }

    private String getTestId(Intent i) {
        return i == null ? null : i.getStringExtra(EXTRA_TEST_ID);
    }

    private boolean isAction(Intent i, String action) {
        if (i != null && action != null) {
            return action.equals(i.getAction());
        }
        return i == null && action == null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground();

        final String id = getTestId(intent);
        if (id != null) {
            logDebug("onStartCommand TEST " + id + " action " + intent.getAction());
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (mCurrentBenchmark != null) {
                        if (isAction(intent, ACTION_START)) {
                            logDebug("Starting TEST " + id);
                            mCurrentBenchmark.startTest(id);
                        } else if (isAction(intent, ACTION_STOP)) {
                            logDebug("Stopping TEST " + id);
                            sendResult(id, mCurrentBenchmark.finishTest(id));

                            if (!mCurrentBenchmark.isRunning()) {
                                logDebug("No TEST running, stopping benchmark");
                                saveBenchmarkAsync(mCurrentBenchmark, () -> {
                                            logDebug("No TEST running, stopping service");
                                            stopSelf();
                                        });
                            }
                        } else if (isAction(intent, ACTION_RESULT)) {
                            logDebug("Getting results for TEST " + id);
                            sendResult(id, mCurrentBenchmark.getAllResults(id));
                        }
                    } else {
                        mHandler.postDelayed(this, 1000);
                    }
                }
            });
        } else {
            Log.w(TAG, "Ignoring start request without test ID");
        }
        return START_STICKY;
    }

    private void sendResult(String testId, Map<Benchmark.Measure, Float> result) {
        logDebug("Sending result");
        Intent intent = new Intent(ACTION_RESULT);
        intent.putExtra(EXTRA_TEST_ID, testId);
        intent.setPackage(TEST_CASE_PACKAGE_NAME);
        if (result != null) {
            for (Benchmark.Measure measure : result.keySet()) {
                intent.putExtra(measure.name(), result.get(measure));
                logDebug("Result " + measure.name() + "=" + result.get(measure));
            }
        }
        sendBroadcast(intent);
    }

    private void startForeground() {
        NotificationManager notificationManager = getSystemService(NotificationManager.class);

        NotificationChannel notificationChannel =
                new NotificationChannel(NOTIFICATION_CHANNEL_FOREGROUND, TAG,
                        NotificationManager.IMPORTANCE_LOW);
        notificationManager.createNotificationChannel(notificationChannel);
        startForeground(12, new Notification.Builder(this, NOTIFICATION_CHANNEL_FOREGROUND)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setChannelId(NOTIFICATION_CHANNEL_FOREGROUND)
                .setContentText("Foreground Service Kill Test Running").build());
    }

    private PendingIntent getAlarmIntent() {
        Intent i = new Intent(ACTION_ALARM);
        i.setPackage(getPackageName());
        return PendingIntent.getBroadcast(this, 0, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void scheduleAlarm() {
        long alarmTime = SystemClock.elapsedRealtime() + ALARM_REPEAT_MS;
        logDebug(String.format("Scheduling alarm at %d", alarmTime));
        AlarmManager alarmManager = getSystemService(AlarmManager.class);
        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    alarmTime,
                    getAlarmIntent()
            );
        } else {
            Log.w(TAG, "Cannot schedule exact alarms");
        }
    }

    private void cancelAlarm() {
        logDebug("Cancel alarm ");
        AlarmManager alarmManager = getSystemService(AlarmManager.class);
        alarmManager.cancel(getAlarmIntent());
    }


    private void startBenchmark() {
        mHandler.post(mMainRunnable);
        scheduleAlarm();
        registerReceiver(mAlarmReceiver, new IntentFilter(ACTION_ALARM));
        mScheduledExecutor = Executors.newScheduledThreadPool(1);
        mScheduledFuture = mScheduledExecutor.scheduleAtFixedRate(() -> {
            try {
                logDebug("Work");
                long now = SystemClock.elapsedRealtime();
                mHandler.post(() -> {
                    if (mWakeLock.isHeld()) {
                        mCurrentBenchmark.addEvent(Benchmark.Measure.WORK, now);
                        saveBenchmarkIfRequired(mCurrentBenchmark);
                    } else {
                        Log.w(TAG, "Wake lock broken");
                    }
                });
            } catch (Throwable t) {
                Log.e(TAG, "Error in scheduled execution ", t);
            }
        }, WORK_REPEAT_MS, WORK_REPEAT_MS, TimeUnit.MILLISECONDS);

        mStarted = true;
    }

    private void stopBenchmark() {
        try {
            unregisterReceiver(mAlarmReceiver);
        } catch (Exception e) {
            Log.w(TAG, "Receiver not registered", e);
        }
        cancelAlarm();
        mHandler.removeCallbacks(mMainRunnable);
        if (mScheduledExecutor != null) {
            mScheduledExecutor.shutdown();
            if (mScheduledFuture.isDone()) {
                try {
                    mScheduledFuture.get();
                } catch (CancellationException e) {
                } catch (Exception e) {
                    Log.e(TAG, "Error in scheduled execution ", e);
                }
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        logDebug("onDestroy()");
        if (mStarted) {
            stopBenchmark();
        }
        mExecutor.shutdown();
        mWakeLock.release();
    }

    private static Intent getServiceIntent(Context context) {
        return new Intent(context, ServiceKillTestService.class);
    }

    private void loadBenchmarkAsync(Consumer<Benchmark> consumer) {
        mExecutor.execute(() -> {
            final Benchmark benchmark = loadBenchmark();
            mHandler.post(() -> {
                consumer.accept(benchmark);
            });
        });
    }

    public interface Consumer<T> {
        void accept(T consumable);
    }

    private synchronized Benchmark loadBenchmark() {
        ObjectInputStream in = null;
        try {
            in = new ObjectInputStream(openFileInput(TAG));
            return (Benchmark) in.readObject();
        } catch (FileNotFoundException e) {
            logDebug("File not found");
        } catch (ClassNotFoundException e) {
            Log.e(TAG, "Class no found", e);
        } catch (IOException e) {
            Log.e(TAG, "I/O error", e);
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "Cannot close benchmark file", e);
            }
        }
        return null;
    }


    private synchronized void clearBenchmark() {
        deleteFile(TAG);
    }

    private void saveBenchmarkIfRequired(Benchmark benchmark) {
        if (SystemClock.elapsedRealtime() - benchmark.lastPersisted > PERSIST_BENCHMARK_TIMEOUT_MS) {
            saveBenchmarkAsync(benchmark, null);
        }
    }


    private void saveBenchmarkAsync(Benchmark benchmark, Runnable runnable) {
        final byte[] bytes = benchmark.toBytes();

        mExecutor.execute(() -> {
            save(bytes);
            mHandler.post(() -> {
                logDebug("SAVED " + benchmark);
                benchmark.setPersisted();
                if (runnable != null) {
                    runnable.run();
                }
            });
        });
    }

    private synchronized void save(byte[] bytes) {
        FileOutputStream fileOut = null;
        try {
            fileOut = openFileOutput(TAG, MODE_PRIVATE);
            fileOut.write(bytes);
            fileOut.flush();
        } catch (FileNotFoundException e) {
            Log.e(TAG, "File not found", e);
        } catch (IOException e) {
            Log.e(TAG, "I/O error", e);
        } finally {
            try {
                if (fileOut != null) {
                    fileOut.close();
                }
            } catch (IOException e) {
                Log.e(TAG, "Cannot close benchmark file", e);
            }
        }
    }

    private static class Range {
        public final long from;
        public final long to;

        public Range(long from, long to) {
            if (to < from || to < 0 || from < 0) {
                throw new IllegalArgumentException("FROM: " + from + " before TO: " + to);
            }
            this.from = from;
            this.to = to;
        }

        public boolean inRange(long timestamp) {
            return timestamp >= from && timestamp <= to;
        }

        public long getDuration() {
            return to - from + 1;
        }

        @Override
        public String toString() {
            return String.format("[%d-%d]", from, to);
        }
    }

    public static class Benchmark implements Serializable {

        public enum Measure {
            TOTAL,
            WORK(WORK_REPEAT_MS),
            MAIN(MAIN_REPEAT_MS),
            ALARM(ALARM_REPEAT_MS + ALARM_REPEAT_MARGIN_MS);

            private final long interval;

            Measure() {
                interval = -1;
            }

            Measure(long interval) {
                this.interval = interval;
            }
        }

        private static final long serialVersionUID = -2939643983335136263L;

        private long lastPersisted = -1;

        private long startTime;

        public Benchmark() {
            startTime = SystemClock.elapsedRealtime();
        }

        private final Map<Measure, List<Long>> eventMap = new HashMap<>();
        private final Map<String, Long> tests = new HashMap<>();

        public boolean isRunning() {
            return tests.size() > 0;
        }

        public void startTest(String id) {
            if (!tests.containsKey(id)) {
                tests.put(id, SystemClock.elapsedRealtime());
            }
        }

        public Map<Measure, Float> finishTest(String id) {
            if (tests.containsKey(id)) {
                Long startTime = tests.remove(id);
                return getAllResults(new Range(startTime, SystemClock.elapsedRealtime()));
            }
            Log.w(TAG, "Missing results for test " + id);
            return null;
        }

        public Map<Measure, Float> getAllResults(String id) {
            if (tests.containsKey(id)) {
                Long startTime = tests.get(id);
                return getAllResults(new Range(startTime, SystemClock.elapsedRealtime()));
            }
            return null;
        }

        private Map<Measure, Float> getAllResults(Range range) {
            Map<Measure, Float> results = new HashMap<>();
            for (Measure measure : Measure.values()) {
                results.put(measure, getResult(measure, range));
            }
            return results;
        }

        public long getLastPersisted() {
            return lastPersisted;
        }

        public void setPersisted() {
            this.lastPersisted = SystemClock.elapsedRealtime();
        }

        private List<Long> filter(List<Long> source, Range range) {
            List<Long> result = new ArrayList<>(source);

            if (range == null) {
                return source;
            }

            Iterator<Long> i = result.iterator();
            while (i.hasNext()) {
                if (!range.inRange(i.next())) {
                    i.remove();
                }
            }
            return result;
        }

        public void addEvent(Measure measure, long timestamp) {
            List<Long> events = getEvents(measure);
            events.add(timestamp);
            if (!eventMap.containsKey(measure)) {
                eventMap.put(measure, events);
            }
        }

        public void addEvent(Measure measure) {
            addEvent(measure, SystemClock.elapsedRealtime());
        }

        private List<Long> getEvents(Measure measure) {
            List<Long> events = eventMap.get(measure);
            return events == null ? new ArrayList<>() : events;
        }

        public float getResult(Measure measure) {
            return getResult(measure, null);
        }

        public float getResult(Measure measure, Range range) {

            if (measure == Measure.TOTAL) {
                return (getResult(Measure.WORK, range) + (2 * getResult(Measure.ALARM, range)) +
                        getResult(Measure.MAIN, range)) / 4f;
            }

            List<Long> events = filter(getEvents(measure), range);

            return Math
                    .min(1, events.size() / (getDuration(range) / (float) measure.interval));
        }

        private long getDuration() {
            return SystemClock.elapsedRealtime() - startTime;
        }

        private long getDuration(Range range) {
            if (range == null) {
                return getDuration();
            }
            return range.getDuration();
        }

        private byte[] toBytes() {
            try {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ObjectOutputStream out = new ObjectOutputStream(bos);
                out.writeObject(this);
                out.flush();
                out.close();
                bos.close();
                return bos.toByteArray();
            } catch (IOException e) {
                Log.e(TAG, "Cannot serialize benchmark: " + this, e);
                return null;
            }
        }

        @SuppressLint("DefaultLocale")
        @Override
        public String toString() {
            return toReportString().replaceAll("\n", "");
        }

        @SuppressLint("DefaultLocale")
        public String toReportString() {
            return String
                    .format("Benchmark TIME: %tT TESTS: %d \n\nMAIN:\n%.1f%% %d \n\nWORK:\n%.1f%%" +
                                    " %d \n\nALARM:\n%.1f%% %d \n\n%s",
                            getDuration() - TimeZone.getDefault().getOffset(0),
                            tests.size(), getResult(Measure.MAIN) * 100,
                            getEvents(Measure.MAIN).size(), getResult(Measure.WORK) * 100,
                            getEvents(Measure.WORK).size(), getResult(Measure.ALARM) * 100,
                            getEvents(Measure.ALARM).size(), isRunning() ? "RUNNING..." :
                                    getResult(Measure.TOTAL) >= 0.9f ? "TEST PASSED!" :
                                            "TEST FAILED!");
        }
    }

    public static void logDebug(String s) {
        if (DEBUG) {
            Log.d(TAG, s);
        }
    }
}