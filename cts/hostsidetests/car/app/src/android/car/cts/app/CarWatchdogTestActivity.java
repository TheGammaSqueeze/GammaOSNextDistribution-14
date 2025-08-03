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
 */

package android.car.cts.app;

import android.app.Activity;
import android.car.Car;
import android.car.watchdog.CarWatchdogManager;
import android.car.watchdog.IoOveruseStats;
import android.car.watchdog.ResourceOveruseStats;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.GuardedBy;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class CarWatchdogTestActivity extends Activity {
    private static final String TAG = CarWatchdogTestActivity.class.getSimpleName();
    private static final String BYTES_TO_KILL = "bytes_to_kill";
    private static final long TEN_MEGABYTES = 1024 * 1024 * 10;
    private static final long TWO_HUNDRED_MEGABYTES = 1024 * 1024 * 200;
    private static final int DISK_DELAY_MS = 4000;
    private static final double WARN_THRESHOLD_PERCENT = 0.8;
    private static final double EXCEED_WARN_THRESHOLD_PERCENT = 0.9;

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final Object mLock = new Object();

    @GuardedBy("mLock")
    private CarWatchdogManager mCarWatchdogManager;

    private String mDumpMessage = "";
    private Car mCar;
    private File mTestDir;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        initCarApi();
        try {
            mTestDir =
                    Files.createTempDirectory(getFilesDir().toPath(), "testDir").toFile();
        } catch (IOException e) {
            setDumpMessage("ERROR: " + e.getMessage());
            finish();
            return;
        }
        mExecutor.execute(
                () -> {
                    synchronized (mLock) {
                        if (mCarWatchdogManager == null) {
                            Log.e(TAG, "CarWatchdogManager is null.");
                            finish();
                            return;
                        }
                    }
                    IoOveruseListener listener = addResourceOveruseListener();
                    try {
                        if (!writeToDisk(TEN_MEGABYTES)) {
                            finish();
                            return;
                        }

                        long remainingBytes = fetchRemainingBytes(TEN_MEGABYTES);
                        if (remainingBytes == 0) {
                            Log.d(TAG, "Remaining bytes is 0 after writing " + TEN_MEGABYTES
                                    + " bytes to disk.");
                            finish();
                            return;
                        }

                        /*
                         * Warning notification is received as soon as exceeding
                         * |WARN_THRESHOLD_PERCENT|. So, set expected minimum written bytes to
                         * |WARN_THRESHOLD_PERCENT| of the overuse threshold.
                         */
                        long bytesToWarnThreshold =
                                (long) (TWO_HUNDRED_MEGABYTES * WARN_THRESHOLD_PERCENT);

                        listener.setExpectedMinWrittenBytes(bytesToWarnThreshold);

                        long bytesToExceedWarnThreshold =
                                (long) Math.ceil(remainingBytes
                                        * EXCEED_WARN_THRESHOLD_PERCENT);

                        if (!writeToDisk(bytesToExceedWarnThreshold)) {
                            finish();
                            return;
                        }

                        listener.checkIsNotified();
                    } finally {
                        synchronized (mLock) {
                            mCarWatchdogManager.removeResourceOveruseListener(listener);
                        }
                        /* Foreground mode bytes dumped after removing listener to ensure hostside
                         * receives dump message after test is finished.
                         */
                        listener.dumpForegroundModeBytes();
                    }
                });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        setDumpMessage("");
        Bundle extras = intent.getExtras();
        if (extras == null) {
            Log.w(TAG, "onNewIntent: empty extras");
            return;
        }
        long remainingBytes = extras.getLong(BYTES_TO_KILL);
        Log.d(TAG, "Bytes to kill: " + remainingBytes);
        if (remainingBytes == 0) {
            Log.w(TAG, "onNewIntent: remaining bytes is 0");
            return;
        }
        mExecutor.execute(() -> {
            synchronized (mLock) {
                if (mCarWatchdogManager == null) {
                    Log.e(TAG, "onNewIntent: CarWatchdogManager is null.");
                    finish();
                    return;
                }
            }
            IoOveruseListener listener = addResourceOveruseListener();
            try {
                listener.setExpectedMinWrittenBytes(TWO_HUNDRED_MEGABYTES);

                writeToDisk(remainingBytes);

                listener.checkIsNotified();
            } finally {
                synchronized (mLock) {
                    mCarWatchdogManager.removeResourceOveruseListener(listener);
                }
                /* Foreground mode bytes dumped after removing listener to ensure hostside
                 * receives dump message after test is finished.
                 */
                listener.dumpForegroundModeBytes();
            }
        });
    }

    @Override
    public void dump(String prefix, FileDescriptor fd, PrintWriter writer, String[] args) {
        if (mDumpMessage.isEmpty()) {
            return;
        }
        writer.printf("%s: %s\n", TAG, mDumpMessage);
        Log.i(TAG, "Dumping message: '" + mDumpMessage + "'");
    }

    @Override
    protected void onDestroy() {
        if (mCar != null) {
            mCar.disconnect();
        }
        if (mTestDir.delete()) {
            Log.i(TAG, "Deleted directory '" + mTestDir.getAbsolutePath() + "' successfully");
        } else {
            Log.e(TAG, "Failed to delete directory '" + mTestDir.getAbsolutePath() + "'");
        }
        super.onDestroy();
    }

    private void initCarApi() {
        if (mCar != null && mCar.isConnected()) {
            mCar.disconnect();
            mCar = null;
        }
        mCar = Car.createCar(this, null, Car.CAR_WAIT_TIMEOUT_WAIT_FOREVER,
                this::initManagers);
    }

    private void initManagers(Car car, boolean ready) {
        synchronized (mLock) {
            if (ready) {
                mCarWatchdogManager = (CarWatchdogManager) car.getCarManager(
                        Car.CAR_WATCHDOG_SERVICE);
                Log.d(TAG, "initManagers() completed");
            } else {
                mCarWatchdogManager = null;
                Log.wtf(TAG, "mCarWatchdogManager set to be null");
            }
        }
    }

    private IoOveruseListener addResourceOveruseListener() {
        IoOveruseListener listener = new IoOveruseListener();
        synchronized (mLock) {
            mCarWatchdogManager.addResourceOveruseListener(getMainExecutor(),
                    CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO, listener);
        }
        return listener;
    }

    private boolean writeToDisk(long bytes) {
        File uniqueFile = new File(mTestDir, Long.toString(System.nanoTime()));
        boolean result = writeToFile(uniqueFile, bytes);
        if (uniqueFile.delete()) {
            Log.i(TAG, "Deleted file: " + uniqueFile.getAbsolutePath());
        } else {
            Log.e(TAG, "Failed to delete file: " + uniqueFile.getAbsolutePath());
        }
        return result;
    }

    private boolean writeToFile(File uniqueFile, long bytes) {
        long writtenBytes = 0;
        try (FileOutputStream fos = new FileOutputStream(uniqueFile)) {
            Log.d(TAG, "Attempting to write " + bytes + " bytes");
            writtenBytes = writeToFos(fos, bytes);
            if (writtenBytes < bytes) {
                setDumpMessage("ERROR: Failed to write '" + bytes
                        + "' bytes to disk. '" + writtenBytes
                        + "' bytes were successfully written, while '" + (bytes - writtenBytes)
                        + "' bytes were pending at the moment the exception occurred.");
                return false;
            }
            fos.getFD().sync();
            // Wait for the IO event to propagate to the system
            Thread.sleep(DISK_DELAY_MS);
            return true;
        } catch (IOException | InterruptedException e) {
            String message;
            if (e instanceof IOException) {
                message = "I/O exception";
            } else {
                message = "Thread interrupted";
                Thread.currentThread().interrupt();
            }
            if (writtenBytes > 0) {
                message += " after successfully writing to disk.";
            }
            Log.e(TAG, message, e);
            setDumpMessage("ERROR: " + message);
            return false;
        }
    }

    private long writeToFos(FileOutputStream fos, long remainingBytes) {
        long totalBytesWritten = 0;
        while (remainingBytes != 0) {
            int writeBytes =
                    (int) Math.min(Integer.MAX_VALUE,
                            Math.min(Runtime.getRuntime().freeMemory(), remainingBytes));
            try {
                fos.write(new byte[writeBytes]);
            }  catch (InterruptedIOException e) {
                Thread.currentThread().interrupt();
                continue;
            } catch (IOException e) {
                Log.e(TAG, "I/O exception while writing " + writeBytes + " to disk", e);
                return totalBytesWritten;
            }
            totalBytesWritten += writeBytes;
            remainingBytes -= writeBytes;
            if (writeBytes > 0 && remainingBytes > 0) {
                Log.i(TAG, "Total bytes written: " + totalBytesWritten + "/"
                        + (totalBytesWritten + remainingBytes));
            }
        }
        Log.d(TAG, "Write completed.");
        return totalBytesWritten;
    }

    private long fetchRemainingBytes(long minWrittenBytes) {
        ResourceOveruseStats stats;
        synchronized (mLock) {
            stats = mCarWatchdogManager.getResourceOveruseStats(
                    CarWatchdogManager.FLAG_RESOURCE_OVERUSE_IO,
                    CarWatchdogManager.STATS_PERIOD_CURRENT_DAY);
        }
        Log.d(TAG, "Fetched resource overuse stats: " + stats);
        IoOveruseStats ioOveruseStats = stats.getIoOveruseStats();
        if (ioOveruseStats == null) {
            setDumpMessage(
                    "ERROR: No I/O overuse stats available for the application after writing "
                    + minWrittenBytes + " bytes.");
            return 0;
        }
        if (ioOveruseStats.getTotalBytesWritten() < minWrittenBytes) {
            setDumpMessage("ERROR: Actual written bytes to disk '" + minWrittenBytes
                    + "' don't match written bytes '" + ioOveruseStats.getTotalBytesWritten()
                    + "' returned by get request");
            return 0;
        }
        /*
         * Check for foreground mode bytes given CtsCarApp is running in the foreground
         * during testing.
         */
        return ioOveruseStats.getRemainingWriteBytes().getForegroundModeBytes();
    }

    private void setDumpMessage(String message) {
        if (mDumpMessage.startsWith("ERROR:")) {
            mDumpMessage += ", " + message;
        } else {
            mDumpMessage = message;
        }
    }

    private final class IoOveruseListener
            implements CarWatchdogManager.ResourceOveruseListener {
        private static final int NOTIFICATION_DELAY_MS = 5000;

        private final Object mLock = new Object();
        @GuardedBy("mLock")
        private boolean mNotificationReceived;
        @GuardedBy("mLock")
        private long mForegroundModeBytes;

        private long mExpectedMinWrittenBytes;

        @Override
        public void onOveruse(ResourceOveruseStats resourceOveruseStats) {
            synchronized (mLock) {
                Log.d(TAG, "onOveruse callback received: " + resourceOveruseStats);
                mForegroundModeBytes = -1;
                mNotificationReceived = true;
                mLock.notifyAll();
                if (resourceOveruseStats.getIoOveruseStats() == null) {
                    setDumpMessage(
                            "ERROR: No I/O overuse stats reported for the application in the "
                            + "overuse notification.");
                    return;
                }
                long reportedWrittenBytes =
                        resourceOveruseStats.getIoOveruseStats().getTotalBytesWritten();
                if (reportedWrittenBytes < mExpectedMinWrittenBytes) {
                    setDumpMessage("ERROR: Actual written bytes to disk '"
                            + mExpectedMinWrittenBytes + "' don't match written bytes '"
                            + reportedWrittenBytes + "' reported in overuse notification");
                    return;
                }
                mForegroundModeBytes =
                        resourceOveruseStats.getIoOveruseStats().getRemainingWriteBytes()
                                .getForegroundModeBytes();
            }
        }

        public void dumpForegroundModeBytes() {
            synchronized (mLock) {
                setDumpMessage(
                        "INFO: --Notification-- foregroundModeBytes = " + mForegroundModeBytes);
            }
        }

        public void setExpectedMinWrittenBytes(long expectedMinWrittenBytes) {
            mExpectedMinWrittenBytes = expectedMinWrittenBytes;
        }

        public void checkIsNotified() {
            synchronized (mLock) {
                long now = SystemClock.uptimeMillis();
                long deadline = now + NOTIFICATION_DELAY_MS;
                while (!mNotificationReceived && now < deadline) {
                    try {
                        mLock.wait(deadline - now);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        continue;
                    } finally {
                        now = SystemClock.uptimeMillis();
                    }
                    break;
                }
                if (!mNotificationReceived) {
                    setDumpMessage("ERROR: I/O Overuse notification not received.");
                }
            }
        }
    }
}
