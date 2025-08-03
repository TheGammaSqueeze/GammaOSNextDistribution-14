/*
 * Copyright (C) 2020 The Android Open Source Project
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
package android.car.apitest;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.Manifest;
import android.annotation.FloatRange;
import android.car.Car;
import android.car.CarBugreportManager;
import android.car.CarBugreportManager.CarBugreportManagerCallback;
import android.os.FileUtils;
import android.os.ParcelFileDescriptor;

import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@RunWith(AndroidJUnit4.class)
@LargeTest
public final class CarBugreportManagerTest extends CarApiTestBase {
    private static final String TAG = CarBugreportManagerTest.class.getSimpleName();

    // Note that most of the test environments have 600s time limit, and in some cases the time
    // limit is shared between all the tests.
    // Dumpstate with dry_run flag should finish within one minute, but it might work slower on
    // busy devices.
    private static final int BUGREPORT_TIMEOUT_MILLIS = 90_000;
    private static final int NO_ERROR = -1;

    // These items will be closed during tearDown().
    private final ArrayList<Closeable> mAllCloseables = new ArrayList<>();

    private CarBugreportManager mManager;
    private FakeCarBugreportCallback mFakeCallback;
    private ParcelFileDescriptor mOutput;
    private ParcelFileDescriptor mExtraOutput;

    @Before
    public void setUp() throws Exception {
        mManager = (CarBugreportManager) getCar().getCarManager(Car.CAR_BUGREPORT_SERVICE);
        mFakeCallback = new FakeCarBugreportCallback();
        mOutput = openDevNullParcelFd();
        mExtraOutput = openDevNullParcelFd();
        mAllCloseables.addAll(List.of(mOutput, mExtraOutput));
    }

    @After
    public void tearDown() throws Exception {
        getPermissions();  // For cancelBugreport()
        try {
            mManager.cancelBugreport();
        } finally {
            dropPermissions();
        }
        for (Closeable closeable : mAllCloseables) {
            try {
                closeable.close();
            } catch (IOException e) {
                // No need to handle it
            }
        }
    }

    @Test
    public void test_requestBugreport_failsWhenNoPermission() {
        dropPermissions();

        SecurityException expected =
                assertThrows(SecurityException.class,
                        () -> mManager.requestBugreportForTesting(
                            mOutput, mExtraOutput, mFakeCallback));
        assertThat(expected).hasMessageThat().contains(
                "nor current process has android.permission.DUMP.");
    }

    @Test
    public void test_requestBugreport_works() throws Exception {
        getPermissions();
        PipedTempFile output = PipedTempFile.create("bugreport-" + getTestName(), ".zip");
        PipedTempFile extraOutput = PipedTempFile.create("screenshot-" + getTestName(), ".png");
        mAllCloseables.addAll(List.of(output, extraOutput));

        mManager.requestBugreportForTesting(
                output.getWriteFd(), extraOutput.getWriteFd(), mFakeCallback);

        // The FDs must be duped and closed in requestBugreport() immediately.
        assertFdIsClosed(output.getWriteFd());
        assertFdIsClosed(extraOutput.getWriteFd());

        // Blocks the thread until bugreport is finished.
        PipedTempFile.copyAllToPersistentFiles(output, extraOutput);

        mFakeCallback.waitTillDoneOrTimeout(BUGREPORT_TIMEOUT_MILLIS);
        assertThat(mFakeCallback.isFinishedSuccessfully()).isEqualTo(true);
        assertThat(mFakeCallback.getReceivedProgress()).isTrue();
        assertContainsValidBugreport(output.getPersistentFile());
    }

    @Test
    public void test_requestBugreport_cannotRunMultipleBugreports() throws Exception {
        getPermissions();
        FakeCarBugreportCallback callback2 = new FakeCarBugreportCallback();
        ParcelFileDescriptor output2 = openDevNullParcelFd();
        ParcelFileDescriptor extraOutput2 = openDevNullParcelFd();

        // 1st bugreport.
        mManager.requestBugreportForTesting(mOutput, mExtraOutput, mFakeCallback);

        // 2nd bugreport.
        mManager.requestBugreportForTesting(output2, extraOutput2, callback2);

        callback2.waitTillDoneOrTimeout(BUGREPORT_TIMEOUT_MILLIS);
        assertThat(callback2.getErrorCode()).isEqualTo(
                CarBugreportManagerCallback.CAR_BUGREPORT_IN_PROGRESS);
        assertThat(mFakeCallback.isFinished()).isFalse();
    }

    @Test
    public void test_cancelBugreport_works() throws Exception {
        getPermissions();
        FakeCarBugreportCallback callback2 = new FakeCarBugreportCallback();
        ParcelFileDescriptor output2 = openDevNullParcelFd();
        ParcelFileDescriptor extraOutput2 = openDevNullParcelFd();

        // 1st bugreport.
        mManager.requestBugreportForTesting(mOutput, mExtraOutput, mFakeCallback);
        mManager.cancelBugreport();

        // Allow the system to finish the bugreport cancellation, 0.5 seconds is enough.
        Thread.sleep(500);

        // 2nd bugreport must work, because 1st bugreport was cancelled.
        mManager.requestBugreportForTesting(output2, extraOutput2, callback2);

        callback2.waitTillProgressOrTimeout(BUGREPORT_TIMEOUT_MILLIS);
        assertThat(callback2.getErrorCode()).isEqualTo(NO_ERROR);
        assertThat(callback2.getReceivedProgress()).isEqualTo(true);
    }

    private static void getPermissions() {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.DUMP);
    }

    private static void dropPermissions() {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .dropShellPermissionIdentity();
    }

    private static void assertFdIsClosed(ParcelFileDescriptor pfd) {
        try {
            int fd = pfd.getFd();
            fail("Expected ParcelFileDescriptor argument to be closed, but got: " + fd);
        } catch (IllegalStateException expected) {
        }
    }

    private static void assertContainsValidBugreport(File file) throws IOException {
        try (ZipFile zipFile = new ZipFile(file)) {
            for (ZipEntry entry : Collections.list(zipFile.entries())) {
                if (entry.isDirectory()) {
                    continue;
                }
                // Find "bugreport-TIMESTAMP.txt" file.
                if (!entry.getName().startsWith("bugreport-") || !entry.getName().endsWith(
                        ".txt")) {
                    continue;
                }
                try (InputStream entryStream = zipFile.getInputStream(entry)) {
                    String data = streamToText(entryStream, /* maxSizeBytes= */  51200);
                    assertThat(data).contains("== dumpstate: ");
                    assertThat(data).contains("dry_run=1");
                    assertThat(data).contains("Build fingerprint: ");
                }
                return;
            }
        }
        fail("bugreport-TIMESTAMP.txt not found in the final zip file.");
    }

    private static String streamToText(InputStream in, int maxSizeBytes) throws IOException {
        assertThat(maxSizeBytes).isGreaterThan(0);

        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] data = new byte[maxSizeBytes];
        int nRead;
        int totalRead = 0;

        while ((nRead = in.read(data, 0, data.length)) != -1 && totalRead <= maxSizeBytes) {
            result.write(data, 0, nRead);
            totalRead += maxSizeBytes;
        }

        return result.toString(StandardCharsets.UTF_8.name());
    }

    private static ParcelFileDescriptor openDevNullParcelFd() throws IOException {
        return ParcelFileDescriptor.open(
                new File("/dev/null"),
                ParcelFileDescriptor.MODE_WRITE_ONLY | ParcelFileDescriptor.MODE_APPEND);
    }

    /**
     * Creates a piped ParcelFileDescriptor that anyone can write. Clients must call
     * {@link copyToPersistentFile}, otherwise writers will be blocked when writing to the pipe.
     *
     * <p>It was created because {@code CarService} is denied to write to a test cache file
     * by SELinux.
     */
    private static class PipedTempFile implements Closeable {
        private final File mPersistentFile;
        private final ParcelFileDescriptor mReadFd;
        private final ParcelFileDescriptor mWriteFd;

        static PipedTempFile create(String prefix, String extension) throws IOException {
            ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
            File f = File.createTempFile(prefix, extension);
            f.setReadable(/* readable= */ true, /* ownerOnly= */ true);
            f.setWritable(/* readable= */ true, /* ownerOnly= */ true);
            f.deleteOnExit();
            return new PipedTempFile(pipe[0], pipe[1], f);
        }

        static void copyAllToPersistentFiles(PipedTempFile... files) throws IOException {
            for (PipedTempFile f : files) {
                f.copyToPersistentFile();
            }
        }

        private PipedTempFile(
                ParcelFileDescriptor readFd, ParcelFileDescriptor writeFd, File persistentFile) {
            mReadFd = readFd;
            mWriteFd = writeFd;
            mPersistentFile = persistentFile;
        }

        ParcelFileDescriptor getWriteFd() {
            return mWriteFd;
        }

        File getPersistentFile() {
            return mPersistentFile;
        }

        @Override
        public void close() throws IOException {
            try {
                mReadFd.close();
            } finally {
                mWriteFd.close();
            }
        }

        /** Copies data from the pipe to the persistent file. Blocks the thread. */
        void copyToPersistentFile() throws IOException {
            try (InputStream in = new ParcelFileDescriptor.AutoCloseInputStream(mReadFd);
                FileOutputStream out = new FileOutputStream(mPersistentFile)) {
                FileUtils.copy(in, out);
            }
        }
    }

    private static class FakeCarBugreportCallback extends CarBugreportManagerCallback {
        private final Object mLock = new Object();
        private final CountDownLatch mEndedLatch = new CountDownLatch(1);
        private final CountDownLatch mProgressLatch = new CountDownLatch(1);
        private boolean mReceivedProgress = false;
        private int mErrorCode = NO_ERROR;

        @Override
        public void onProgress(@FloatRange(from = 0f, to = 100f) float progress) {
            synchronized (mLock) {
                mReceivedProgress = true;
            }
            mProgressLatch.countDown();
        }

        @Override
        public void onError(
                @CarBugreportManagerCallback.CarBugreportErrorCode int errorCode) {
            synchronized (mLock) {
                mErrorCode = errorCode;
            }
            mEndedLatch.countDown();
            mProgressLatch.countDown();
        }

        @Override
        public void onFinished() {
            mEndedLatch.countDown();
            mProgressLatch.countDown();
        }

        int getErrorCode() {
            synchronized (mLock) {
                return mErrorCode;
            }
        }

        boolean getReceivedProgress() {
            synchronized (mLock) {
                return mReceivedProgress;
            }
        }

        boolean isFinishedSuccessfully() {
            return mEndedLatch.getCount() == 0 && getErrorCode() == NO_ERROR;
        }

        boolean isFinished() {
            return mEndedLatch.getCount() == 0;
        }

        void waitTillDoneOrTimeout(long timeoutMillis) throws InterruptedException {
            mEndedLatch.await(timeoutMillis, TimeUnit.MILLISECONDS);
            if (mEndedLatch.getCount() > 0) {
                fail("Time out. CarBugreportManager didn't finish.");
            }
        }

        void waitTillProgressOrTimeout(long timeoutMillis) throws InterruptedException {
            mProgressLatch.await(timeoutMillis, TimeUnit.MILLISECONDS);
            if (mProgressLatch.getCount() > 0) {
                fail("Time out. CarBugreportManager didn't send progress or finish.");
            }
        }
    }
}
