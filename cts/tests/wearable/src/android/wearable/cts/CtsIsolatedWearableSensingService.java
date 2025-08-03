/*
 * Copyright (C) 2024 The Android Open Source Project
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
package android.wearable.cts;

import android.app.ambientcontext.AmbientContextEventRequest;
import android.app.wearable.WearableSensingManager;
import android.os.ParcelFileDescriptor;
import android.os.PersistableBundle;
import android.os.SharedMemory;
import android.service.ambientcontext.AmbientContextDetectionResult;
import android.service.ambientcontext.AmbientContextDetectionServiceStatus;
import android.service.wearable.WearableSensingService;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.function.Consumer;

/**
 * An implementation of {@link WearableSensingService} for CTS testing in an isolated process.
 *
 * <p>This service allows us to test APIs that will kill the {@link WearableSensingService} process,
 * which is not possible for {@link CtsWearableSensingService} because it will kill the test runner
 * too. The downside of this service is that the test runner cannot use static methods for setup and
 * verification. Instead, We use {@link #onDataProvided(PersistableBundle, SharedMemory, Consumer)}
 * with special keys in the PersistableBundle to perform setup and verification.
 */
public class CtsIsolatedWearableSensingService extends WearableSensingService {
    private static final String TAG = "CtsIsolatedWSS";

    /** PersistableBundle key that represents an action, such as setup and verify. */
    public static final String BUNDLE_ACTION_KEY = "ACTION";

    /** PersistableBundle value that represents a request to reset the service. */
    public static final String ACTION_RESET = "RESET";

    /**
     * PersistableBundle value that represents a request to verify the data received from the
     * wearable.
     */
    public static final String ACTION_VERIFY_DATA_RECEIVED_FROM_WEARABLE =
            "VERIFY_DATA_RECEIVED_FROM_WEARABLE";

    /**
     * PersistableBundle key that represents the expected string to be received from the wearable.
     */
    public static final String EXPECTED_STRING_FROM_WEARABLE_KEY =
            "EXPECTED_STRING_FROM_WEARABLE_KEY";

    /**
     * PersistableBundle value that represents a request to send data to the secure wearable
     * connection.
     */
    public static final String ACTION_SEND_DATA_TO_WEARABLE = "SEND_DATA_TO_WEARABLE";

    /** PersistableBundle key that represents the string to send to the wearable. */
    public static final String STRING_TO_SEND_KEY = "STRING_TO_SEND_KEY";

    /**
     * PersistableBundle value that represents a request to close the secure wearable connection.
     */
    public static final String ACTION_CLOSE_WEARABLE_CONNECTION = "CLOSE_WEARABLE_CONNECTION";

    /** PersistableBundle value that represents a request to set a boolean state to true. */
    public static final String ACTION_SET_BOOLEAN_STATE = "SET_BOOLEAN_STATE";

    /**
     * PersistableBundle value that represents a request to verify the boolean state has been set to
     * true. This is used to check whether the process has been restarted since the state is set.
     */
    public static final String ACTION_VERIFY_BOOLEAN_STATE = "VERIFY_BOOLEAN_STATE";

    private volatile ParcelFileDescriptor mSecureWearableConnection;
    private volatile boolean mBooleanState = false;

    @Override
    public void onSecureConnectionProvided(
            ParcelFileDescriptor secureWearableConnection, Consumer<Integer> statusConsumer) {
        Log.w(TAG, "onSecureConnectionProvided");
        mSecureWearableConnection = secureWearableConnection;
        statusConsumer.accept(WearableSensingManager.STATUS_SUCCESS);
    }

    @Override
    public void onDataProvided(
            PersistableBundle data, SharedMemory sharedMemory, Consumer<Integer> statusConsumer) {
        String action = data.getString(BUNDLE_ACTION_KEY);
        Log.i(TAG, "#onDataProvided, action: " + action);
        try {
            switch (action) {
                case ACTION_RESET:
                    reset();
                    statusConsumer.accept(WearableSensingManager.STATUS_SUCCESS);
                    return;
                case ACTION_VERIFY_DATA_RECEIVED_FROM_WEARABLE:
                    String expectedString = data.getString(EXPECTED_STRING_FROM_WEARABLE_KEY);
                    verifyDataReceivedFromWearable(expectedString, statusConsumer);
                    return;
                case ACTION_SEND_DATA_TO_WEARABLE:
                    String stringToSend = data.getString(STRING_TO_SEND_KEY);
                    sendDataToWearable(stringToSend, statusConsumer);
                    return;
                case ACTION_CLOSE_WEARABLE_CONNECTION:
                    closeWearableConnection(statusConsumer);
                    return;
                case ACTION_SET_BOOLEAN_STATE:
                    mBooleanState = true;
                    statusConsumer.accept(WearableSensingManager.STATUS_SUCCESS);
                    return;
                case ACTION_VERIFY_BOOLEAN_STATE:
                    statusConsumer.accept(
                            mBooleanState
                                    ? WearableSensingManager.STATUS_SUCCESS
                                    : WearableSensingManager.STATUS_UNKNOWN);
                    return;
                default:
                    Log.w(TAG, "Unknown action: " + action);
                    statusConsumer.accept(WearableSensingManager.STATUS_UNKNOWN);
                    return;
            }
        } catch (Exception ex) {
            // Exception in this process will not show up in the test runner, so just Log it and
            // return an unknown status code.
            Log.e(TAG, "Unexpected exception in onDataProvided.", ex);
            statusConsumer.accept(WearableSensingManager.STATUS_UNKNOWN);
        }
    }

    private void reset() {
        Log.i(TAG, "#reset");
        mSecureWearableConnection = null;
        mBooleanState = false;
    }

    private void verifyDataReceivedFromWearable(
            String expectedString, Consumer<Integer> statusConsumer) throws IOException {
        if (mSecureWearableConnection == null) {
            Log.e(
                    TAG,
                    "#verifyDataReceivedFromWearable called but mSecureWearableConnection is null");
            statusConsumer.accept(WearableSensingManager.STATUS_UNKNOWN);
            return;
        }
        if (expectedString == null) {
            Log.e(TAG, "#verifyDataReceivedFromWearable called but no expected string is provided");
            statusConsumer.accept(WearableSensingManager.STATUS_UNKNOWN);
            return;
        }
        byte[] expectedBytes = expectedString.getBytes(StandardCharsets.UTF_8);
        byte[] dataFromWearable = readData(mSecureWearableConnection, expectedBytes.length);
        if (Arrays.equals(expectedBytes, dataFromWearable)) {
            statusConsumer.accept(WearableSensingManager.STATUS_SUCCESS);
        } else {
            Log.e(
                    TAG,
                    String.format(
                            "Data bytes received from wearable are different from expected."
                                + " Received length: %s, expected length: %s",
                            dataFromWearable.length, expectedBytes.length));
            statusConsumer.accept(WearableSensingManager.STATUS_UNKNOWN);
        }
    }

    private byte[] readData(ParcelFileDescriptor pfd, int length) throws IOException {
        InputStream is = new ParcelFileDescriptor.AutoCloseInputStream(pfd);
        byte[] dataRead = new byte[length];
        is.read(dataRead, 0, length);
        is.close();
        return dataRead;
    }

    private void sendDataToWearable(String stringToSend, Consumer<Integer> statusConsumer)
            throws Exception {
        if (mSecureWearableConnection == null) {
            Log.e(TAG, "#sendDataToWearable called but mSecureWearableConnection is null");
            statusConsumer.accept(WearableSensingManager.STATUS_UNKNOWN);
            return;
        }
        if (stringToSend == null) {
            Log.e(TAG, "#sendDataToWearable called but no stringToSend is provided");
            statusConsumer.accept(WearableSensingManager.STATUS_UNKNOWN);
            return;
        }
        byte[] bytesToSend = stringToSend.getBytes(StandardCharsets.UTF_8);
        writeData(mSecureWearableConnection, bytesToSend);
        statusConsumer.accept(WearableSensingManager.STATUS_SUCCESS);
    }

    private void writeData(ParcelFileDescriptor pfd, byte[] data) throws IOException {
        OutputStream os = new ParcelFileDescriptor.AutoCloseOutputStream(pfd);
        os.write(data);
    }

    private void closeWearableConnection(Consumer<Integer> statusConsumer) throws Exception {
        if (mSecureWearableConnection == null) {
            Log.e(TAG, "#sendDataToWearable called but mSecureWearableConnection is null");
            statusConsumer.accept(WearableSensingManager.STATUS_UNKNOWN);
            return;
        }
        mSecureWearableConnection.close();
        statusConsumer.accept(WearableSensingManager.STATUS_SUCCESS);
    }

    // The methods below are not used. They are tested in CtsWearableSensingService and only
    // implemented here because they are abstact.

    @Override
    public void onDataStreamProvided(
            ParcelFileDescriptor parcelFileDescriptor, Consumer<Integer> statusConsumer) {
        Log.w(TAG, "onDataStreamProvided");
    }

    @Override
    public void onStartDetection(
            AmbientContextEventRequest request,
            String packageName,
            Consumer<AmbientContextDetectionServiceStatus> statusConsumer,
            Consumer<AmbientContextDetectionResult> detectionResultConsumer) {
        Log.w(TAG, "onStartDetection");
    }

    @Override
    public void onStopDetection(String packageName) {
        Log.w(TAG, "onStopDetection");
    }

    @Override
    public void onQueryServiceStatus(
            Set<Integer> eventTypes,
            String packageName,
            Consumer<AmbientContextDetectionServiceStatus> consumer) {
        Log.w(TAG, "onQueryServiceStatus");
    }
}
