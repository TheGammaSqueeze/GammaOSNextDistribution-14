/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.cts.verifier.usb.accessory;

import static com.android.cts.verifier.usb.Util.runAndAssertException;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;
import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Guide the user to run test for the USB accessory interface.
 */
public class UsbAccessoryTestActivity extends PassFailButtons.Activity implements
        AccessoryAttachmentHandler.AccessoryAttachmentObserver {
    private static final String LOG_TAG = UsbAccessoryTestActivity.class.getSimpleName();
    private static final int MAX_BUFFER_SIZE = 16384;

    private static final int TEST_DATA_SIZE_THRESHOLD = 100 * 1024 * 1024; // 100MB

    private TextView mStatus;
    private ProgressBar mProgress;

    private BroadcastReceiver mUsbAccessoryHandshakeReceiver;

    private Boolean mAccessoryStart = false;
    private boolean mHasSendStringCount = false;
    private boolean mHasAccessoryConnectionStartTime = false;
    private CompletableFuture<Void> mAccessoryHandshakeIntent = new CompletableFuture<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.usb_main);
        setInfoResources(
                R.string.usb_accessory_test, R.string.usb_accessory_test_info, -1);

        setPassFailButtonClickListeners();
        mStatus = (TextView) findViewById(R.id.status);
        mProgress = (ProgressBar) findViewById(R.id.progress_bar);
        mStatus.setText(R.string.usb_accessory_test_step1);
        getPassButton().setEnabled(false);

        AccessoryAttachmentHandler.addObserver(this);

        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_ACCESSORY_HANDSHAKE);

        mUsbAccessoryHandshakeReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                synchronized (UsbAccessoryTestActivity.this) {
                    mAccessoryStart = intent.getBooleanExtra(
                            UsbManager.EXTRA_ACCESSORY_START, false);

                    // check SENDSTRING event
                    if (intent.hasExtra(UsbManager.EXTRA_ACCESSORY_STRING_COUNT)) {
                        mHasSendStringCount = true;
                    }

                    // check GETPROTOCOL event
                    if (intent.hasExtra(UsbManager.EXTRA_ACCESSORY_UEVENT_TIME)) {
                        mHasAccessoryConnectionStartTime = true;
                    }
                    mAccessoryHandshakeIntent.complete(null);
                }
            }
        };

        registerReceiver(mUsbAccessoryHandshakeReceiver, filter, Context.RECEIVER_EXPORTED);
    }

    @Override
    public boolean requiresReportLog() {
        return true;
    }

    @Override
    public void onAttached(UsbAccessory accessory) {
        mStatus.setText(R.string.usb_accessory_test_step2);
        mProgress.setVisibility(View.VISIBLE);

        final long accessroyStarTime = 3 * 1000;

        AccessoryAttachmentHandler.removeObserver(this);

        UsbManager usbManager = getSystemService(UsbManager.class);

        (new AsyncTask<Void, Void, Throwable>() {
            @Override
            protected Throwable doInBackground(Void... params) {
                try {
                    assertEquals("Android CTS", accessory.getManufacturer());
                    assertEquals("Android CTS test companion device", accessory.getModel());
                    assertEquals("Android device running CTS verifier", accessory.getDescription());
                    assertEquals("2", accessory.getVersion());
                    assertEquals("https://source.android.com/compatibility/cts/verifier.html",
                            accessory.getUri());
                    assertEquals("0", accessory.getSerial());

                    assertTrue(Arrays.asList(usbManager.getAccessoryList()).contains(accessory));

                    runAndAssertException(() -> usbManager.openAccessory(null),
                            NullPointerException.class);

                    ParcelFileDescriptor accessoryFd = usbManager.openAccessory(accessory);
                    assertNotNull(accessoryFd);

                    try (InputStream is = new ParcelFileDescriptor.AutoCloseInputStream(
                            accessoryFd)) {
                        try (OutputStream os = new ParcelFileDescriptor.AutoCloseOutputStream(
                                accessoryFd)) {
                            byte[] origBuffer32 = new byte[32];
                            (new Random()).nextBytes(origBuffer32);

                            byte[] origBufferMax = new byte[MAX_BUFFER_SIZE];
                            (new Random()).nextBytes(origBufferMax);

                            byte[] bufferMax = new byte[MAX_BUFFER_SIZE];
                            byte[] buffer32 = new byte[32];
                            byte[] buffer16 = new byte[16];

                            // Echo a transfer
                            nextTest(is, os, "echo 32 bytes");

                            os.write(origBuffer32);

                            int numRead = is.read(buffer32);
                            assertEquals(32, numRead);
                            assertArrayEquals(origBuffer32, buffer32);

                            // Receive less data than available
                            nextTest(is, os, "echo 32 bytes");

                            os.write(origBuffer32);

                            numRead = is.read(buffer16);
                            assertEquals(16, numRead);
                            assertArrayEquals(Arrays.copyOf(origBuffer32, 16), buffer16);

                            // If a transfer was only partially read, the rest of the transfer is
                            // lost. We cannot read the second part, hence proceed to the next test.

                            // Send two transfers in a row
                            nextTest(is, os, "echo two 16 byte transfers as one");

                            os.write(Arrays.copyOf(origBuffer32, 16));
                            os.write(Arrays.copyOfRange(origBuffer32, 16, 32));

                            numRead = is.read(buffer32);
                            assertEquals(32, numRead);
                            assertArrayEquals(origBuffer32, buffer32);

                            // Receive two transfers in a row into a buffer that is bigger than the
                            // transfer
                            nextTest(is, os, "echo 32 bytes as two 16 byte transfers");

                            os.write(origBuffer32);

                            // Even though the buffer would hold 32 bytes the input stream will read
                            // the transfers individually
                            numRead = is.read(buffer32);
                            assertEquals(16, numRead);
                            assertArrayEquals(Arrays.copyOf(origBuffer32, 16),
                                    Arrays.copyOf(buffer32, 16));

                            numRead = is.read(buffer32);
                            assertEquals(16, numRead);
                            assertArrayEquals(Arrays.copyOfRange(origBuffer32, 16, 32),
                                    Arrays.copyOf(buffer32, 16));

                            // Echo a buffer with the maximum size
                            nextTest(is, os, "echo max bytes");

                            os.write(origBufferMax);

                            numRead = is.read(bufferMax);
                            assertEquals(MAX_BUFFER_SIZE, numRead);
                            assertArrayEquals(origBufferMax, bufferMax);

                            // Echo a buffer with twice the maximum size
                            nextTest(is, os, "echo max*2 bytes");

                            byte[] oversizeBuffer = new byte[MAX_BUFFER_SIZE * 2];
                            System.arraycopy(origBufferMax, 0, oversizeBuffer, 0, MAX_BUFFER_SIZE);
                            System.arraycopy(origBufferMax, 0, oversizeBuffer, MAX_BUFFER_SIZE,
                                    MAX_BUFFER_SIZE);
                            os.write(oversizeBuffer);

                            // The other side can not write more than the maximum size at once,
                            // hence we get two transfers in return
                            numRead = is.read(bufferMax);
                            assertEquals(MAX_BUFFER_SIZE, numRead);
                            assertArrayEquals(origBufferMax, bufferMax);

                            numRead = is.read(bufferMax);
                            assertEquals(MAX_BUFFER_SIZE, numRead);
                            assertArrayEquals(origBufferMax, bufferMax);

                            nextTest(is, os, "measure out transfer speed");

                            byte[] result = new byte[1];
                            long bytesSent = 0;
                            long timeStart = SystemClock.elapsedRealtime();
                            while (bytesSent < TEST_DATA_SIZE_THRESHOLD) {
                                os.write(origBufferMax);
                                bytesSent += MAX_BUFFER_SIZE;
                            }
                            numRead = is.read(result);
                            double speedKBPS = (bytesSent * 8 * 1000. / 1024.)
                                    / (SystemClock.elapsedRealtime() - timeStart);
                            assertEquals(1, numRead);
                            assertEquals(1, result[0]);
                            // We don't mandate min speed for now, let's collect data on what it is.
                            getReportLog().setSummary(
                                    "Output USB accesory transfer speed",
                                    speedKBPS,
                                    ResultType.HIGHER_BETTER,
                                    ResultUnit.KBPS);
                            Log.i(LOG_TAG, "Write data transfer speed is " + speedKBPS + "KBPS");

                            nextTest(is, os, "measure in transfer speed");

                            long bytesRead = 0;
                            timeStart = SystemClock.elapsedRealtime();
                            while (bytesRead < TEST_DATA_SIZE_THRESHOLD) {
                                numRead = is.read(bufferMax);
                                bytesRead += numRead;
                            }
                            numRead = is.read(result);
                            speedKBPS = (bytesRead * 8 * 1000. / 1024.)
                                    / (SystemClock.elapsedRealtime() - timeStart);
                            assertEquals(1, numRead);
                            assertEquals(1, result[0]);
                            // We don't mandate min speed for now, let's collect data on what it is.
                            getReportLog().setSummary(
                                    "Input USB accesory transfer speed",
                                    speedKBPS,
                                    ResultType.HIGHER_BETTER,
                                    ResultUnit.KBPS);
                            Log.i(LOG_TAG, "Read data transfer speed is " + speedKBPS + "KBPS");

                            nextTest(is, os, "Receive USB_ACCESSORY_HANDSHAKE intent");

                            mAccessoryHandshakeIntent.get(accessroyStarTime,
                                    TimeUnit.MILLISECONDS);
                            assertTrue(mAccessoryStart);
                            assertTrue(mHasSendStringCount);
                            assertTrue(mHasAccessoryConnectionStartTime);

                            unregisterReceiver(mUsbAccessoryHandshakeReceiver);
                            mUsbAccessoryHandshakeReceiver = null;

                            nextTest(is, os, "done");
                        }
                    }

                    accessoryFd.close();

                    return null;
                } catch (Throwable t) {
                    return  t;
                }
            }

            @Override
            protected void onPostExecute(Throwable t) {
                if (t == null) {
                    setTestResultAndFinish(true);
                } else {
                    fail(null, t);
                }
            }
        }).execute();
    }

    /**
     * Signal to the companion device that we want to switch to the next test.
     *
     * @param is       The input stream from the companion device
     * @param os       The output stream from the companion device
     * @param testName The name of the new test
     */
    private boolean nextTest(@NonNull InputStream is, @NonNull OutputStream os,
            @NonNull String testName) throws IOException {
        Log.i(LOG_TAG, "Init new test " + testName);

        ByteBuffer nameBuffer = Charset.forName("UTF-8").encode(CharBuffer.wrap(testName));
        byte[] sizeBuffer = {(byte) nameBuffer.limit()};

        os.write(sizeBuffer);
        os.write(Arrays.copyOf(nameBuffer.array(), nameBuffer.limit()));

        int ret = is.read();
        if (ret <= 0) {
            Log.i(LOG_TAG, "Last test failed " + ret);
            return false;
        }

        os.write(0);

        Log.i(LOG_TAG, "Running " + testName);

        return true;
    }

    @Override
    protected void onDestroy() {
        AccessoryAttachmentHandler.removeObserver(this);

        if (mUsbAccessoryHandshakeReceiver != null) {
            unregisterReceiver(mUsbAccessoryHandshakeReceiver);
        }

        super.onDestroy();
    }

    /**
     * Indicate that the test failed.
     */
    private void fail(@Nullable String s, @Nullable Throwable e) {
        Log.e(LOG_TAG, s, e);
        setTestResultAndFinish(false);
    }
}
