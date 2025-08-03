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

package android.telephony.cts;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.OutcomeReceiver;
import android.os.ParcelFileDescriptor;
import android.os.ParcelUuid;
import android.telephony.ims.cts.ImsUtils;
import android.os.UserHandle;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.cts.util.TelephonyUtils;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.test.InstrumentationRegistry;

import com.android.compatibility.common.util.RequiredFeatureRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class CallComposerTest {
    private static final String TEST_FILE_NAME = "red_velvet_cupcake.png";
    private static final String TEST_FILE_CONTENT_TYPE = "image/png";
    private static final long TEST_TIMEOUT_MILLIS = 5000;

    private String mPreviousDefaultDialer;
    private Context mContext;
    private boolean mPreviousTestMode;

    @Rule
    public final RequiredFeatureRule mTelephonyRequiredRule =
            new RequiredFeatureRule(PackageManager.FEATURE_TELEPHONY);

    @Before
    public void setUp() throws Exception {
        if (!ImsUtils.shouldTestImsCall()) {
            return;
        }
        mContext = InstrumentationRegistry.getContext();
        overrideDefaultDialer();
        mPreviousTestMode = Boolean.parseBoolean(
                TelephonyUtils.executeShellCommand(InstrumentationRegistry.getInstrumentation(),
                        "cmd phone callcomposer test-mode query"));
        TelephonyUtils.executeShellCommand(InstrumentationRegistry.getInstrumentation(),
                "cmd phone callcomposer test-mode enable");
    }

    @After
    public void tearDown() throws Exception {
        if (!ImsUtils.shouldTestImsCall()) {
            return;
        }
        restoreDefaultDialer();
        TelephonyUtils.executeShellCommand(InstrumentationRegistry.getInstrumentation(),
                "cmd phone callcomposer test-mode "
                        + (mPreviousTestMode ? "enable" : "disable"));
        Files.deleteIfExists(mContext.getFilesDir().toPath().resolve(TEST_FILE_NAME));
    }

    @Test
    public void testUploadPictureWithFile() throws Exception {
        if (!ImsUtils.shouldTestImsCall()) {
            return;
        }
        Path testFile = mContext.getFilesDir().toPath().resolve(TEST_FILE_NAME);
        byte[] imageData = getSamplePictureAsBytes();
        Files.write(testFile, imageData);

        UUID handle = pictureUploadHelper(testFile, null, -1);
        checkStoredData(handle, imageData);
    }

    @Test
    public void testUploadPictureAsStream() throws Exception {
        if (!ImsUtils.shouldTestImsCall()) {
            return;
        }
        byte[] imageData = getSamplePictureAsBytes();
        ByteArrayInputStream inputStream = new ByteArrayInputStream(imageData);

        UUID handle = pictureUploadHelper(null, inputStream, -1);
        checkStoredData(handle, imageData);
    }

    @Test
    public void testExcessivelyLargePictureAsFile() throws Exception {
        if (!ImsUtils.shouldTestImsCall()) {
            return;
        }
        int targetSize = (int) TelephonyManager.getMaximumCallComposerPictureSize() + 1;
        byte[] imageData = getSamplePictureAsBytes();
        byte[] paddedData = new byte[targetSize];
        System.arraycopy(imageData, 0, paddedData, 0, imageData.length);
        Path testFile = mContext.getFilesDir().toPath().resolve(TEST_FILE_NAME);
        Files.write(testFile, paddedData);

        pictureUploadHelper(testFile, null,
                TelephonyManager.CallComposerException.ERROR_FILE_TOO_LARGE);
    }

    @Test
    public void testExcessivelyLargePictureAsStream() throws Exception {
        if (!ImsUtils.shouldTestImsCall()) {
            return;
        }
        int targetSize = (int) TelephonyManager.getMaximumCallComposerPictureSize() + 1;
        byte[] imageData = getSamplePictureAsBytes();
        byte[] paddedData = new byte[targetSize];
        System.arraycopy(imageData, 0, paddedData, 0, imageData.length);
        ByteArrayInputStream inputStream = new ByteArrayInputStream(paddedData);

        pictureUploadHelper(null, inputStream,
                TelephonyManager.CallComposerException.ERROR_FILE_TOO_LARGE);
    }

    private UUID pictureUploadHelper(Path inputFile, InputStream inputStream,
            int expectedErrorCode) throws Exception {
        TelephonyManager tm = mContext.getSystemService(TelephonyManager.class);
        CompletableFuture<Pair<ParcelUuid, TelephonyManager.CallComposerException>> resultFuture =
                new CompletableFuture<>();
        OutcomeReceiver<ParcelUuid, TelephonyManager.CallComposerException> callback =
                new OutcomeReceiver<ParcelUuid, TelephonyManager.CallComposerException>() {
                    @Override
                    public void onResult(@NonNull ParcelUuid result) {
                        resultFuture.complete(Pair.create(result, null));
                    }

                    @Override
                    public void onError(TelephonyManager.CallComposerException error) {
                        resultFuture.complete(Pair.create(null, error));
                    }
        };

        if (inputFile != null) {
            tm.uploadCallComposerPicture(inputFile, TEST_FILE_CONTENT_TYPE,
                    Executors.newSingleThreadExecutor(), callback);
        } else {
            tm.uploadCallComposerPicture(inputStream, TEST_FILE_CONTENT_TYPE,
                    Executors.newSingleThreadExecutor(), callback);
        }

        Pair<ParcelUuid, TelephonyManager.CallComposerException> result;
        try {
            result = resultFuture.get(TEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            fail("Timed out waiting for response from TelephonyManager");
            return null;
        }

        if (result.second != null && expectedErrorCode < 0) {
            String error = TelephonyUtils.parseErrorCodeToString(result.second.getErrorCode(),
                    TelephonyManager.CallComposerException.class, "ERROR_");
            fail("Upload failed with " + error
                    + "\nIOException: " + result.second.getIOException());
        } else if (expectedErrorCode >= 0) {
            String expectedError = TelephonyUtils.parseErrorCodeToString(expectedErrorCode,
                    TelephonyManager.CallComposerException.class, "ERROR_");
            if (result.second == null) {
                fail("Did not get the expected error: " + expectedError);
            } else if (result.first != null) {
                fail("Got a UUID from Telephony when we expected " + expectedError);
            } else if (result.second.getErrorCode() != expectedErrorCode) {
                String observedError =
                        TelephonyUtils.parseErrorCodeToString(result.second.getErrorCode(),
                                TelephonyManager.CallComposerException.class, "ERROR_");
                fail("Expected " + expectedError + ", got " + observedError);
            }
            // If we expected an error, the test ends here
            return null;
        }

        assertNotNull(result.first);

        // Make sure that any file descriptors opened to the test file have been closed.
        if (inputFile != null) {
            try {
                Files.newOutputStream(inputFile, StandardOpenOption.WRITE,
                        StandardOpenOption.APPEND).close();
            } catch (IOException e) {
                fail("Couldn't open+close the file after upload -- leaked fd? " + e);
            }
        }
        return result.first.getUuid();
    }

    private void checkStoredData(UUID handle, byte[] expectedData) throws Exception {
        String storageUri =
                TelephonyUtils.executeShellCommand(InstrumentationRegistry.getInstrumentation(),
                        "cmd phone callcomposer simulate-outgoing-call "
                                + SubscriptionManager.getDefaultSubscriptionId() + " "
                                + handle.toString());
        ParcelFileDescriptor pfd =
                mContext.getContentResolver().openFile(Uri.parse(storageUri), "r", null);

        byte[] readBytes;
        try (InputStream is = new ParcelFileDescriptor.AutoCloseInputStream(pfd)) {
            readBytes = readBytes(is);
        }
        assertArrayEquals(expectedData, readBytes);
    }

    private byte[] getSamplePictureAsBytes() throws Exception {
        InputStream resourceInput = mContext.getResources().openRawResource(R.drawable.cupcake);
        return readBytes(resourceInput);
    }

    private static byte[] readBytes(InputStream inputStream) throws Exception {
        byte[] buffer = new byte[1024];
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int numRead;
        do {
            numRead = inputStream.read(buffer);
            if (numRead > 0) output.write(buffer, 0, numRead);
        } while (numRead > 0);
        return output.toByteArray();
    }

    private void overrideDefaultDialer() throws Exception {
        mPreviousDefaultDialer = TelephonyUtils.executeShellCommand(
                InstrumentationRegistry.getInstrumentation(), "telecom get-default-dialer");
        TelephonyUtils.executeShellCommand(InstrumentationRegistry.getInstrumentation(),
                "cmd role add-role-holder --user " + UserHandle.myUserId()
                        + " android.app.role.DIALER " + mContext.getPackageName());
    }

    private void restoreDefaultDialer() throws Exception {
        TelephonyUtils.executeShellCommand(InstrumentationRegistry.getInstrumentation(),
                "cmd role add-role-holder --user " + UserHandle.myUserId()
                        + " android.app.role.DIALER " + mPreviousDefaultDialer);
    }
}
