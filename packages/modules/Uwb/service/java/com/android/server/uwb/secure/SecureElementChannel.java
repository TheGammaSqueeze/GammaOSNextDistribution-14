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

package com.android.server.uwb.secure;

import static com.android.server.uwb.secure.iso7816.StatusWord.SW_NO_ERROR;
import static com.android.server.uwb.secure.iso7816.StatusWord.SW_NO_SPECIFIC_DIAGNOSTIC;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import com.android.server.uwb.secure.csml.FiRaCommand;
import com.android.server.uwb.secure.iso7816.CommandApdu;
import com.android.server.uwb.secure.iso7816.ResponseApdu;
import com.android.server.uwb.secure.iso7816.StatusWord;
import com.android.server.uwb.secure.omapi.OmapiConnection;
import com.android.server.uwb.secure.omapi.OmapiConnection.InitCompletionCallback;

import java.io.IOException;
import java.util.Arrays;

/** Manages the Secure Element and allows communications with the FiRa applet. */
@WorkerThread
public class SecureElementChannel {
    private static final String LOG_TAG = "SecureElementChannel";
    private static final int MAX_SE_OPERATION_RETRIES = 3;
    private static final int DELAY_BETWEEN_SE_RETRY_ATTEMPTS_MILLIS = 10;

    private static final StatusWord SW_TEMPORARILY_UNAVAILABLE =
            StatusWord.SW_CONDITIONS_NOT_SATISFIED;

    private final OmapiConnection mOmapiConnection;
    private final boolean mRemoveDelayBetweenRetriesForTest;

    private boolean mIsOpened = false;

    /**
     * The constructor of the SecureElementChannel.
     */
    public SecureElementChannel(@NonNull OmapiConnection omapiConnection) {
        this(omapiConnection, /* removeDelayBetweenRetries= */ false);
    }

    // This constructor is made visible because we need to remove the delay between SE operations
    // during tests. Calling Thread.sleep in tests actually causes the thread running the test to
    // sleep and leads to the test timing out.
    @VisibleForTesting
    SecureElementChannel(
            @NonNull OmapiConnection omapiConnection, boolean removeDelayBetweenRetriesForTest) {
        this.mOmapiConnection = omapiConnection;
        this.mRemoveDelayBetweenRetriesForTest = removeDelayBetweenRetriesForTest;
    }

    /**
     * Initializes the SecureElementChannel.
     */
    public void init(@NonNull InitCompletionCallback callback) {
        mOmapiConnection.init(callback::onInitCompletion);
    }

    /**
     * Opens the channel to the FiRa applet, true if success.
     */
    public boolean openChannel() {
        try {
            ResponseApdu responseApdu = openChannelWithResponse();
            if (responseApdu.getStatusWord() != SW_NO_ERROR.toInt()) {
                logw("Received error [" + responseApdu + "] while opening channel");
                return false;
            }
        } catch (IOException e) {
            loge("Encountered exception while opening channel" + e);
            return false;
        }
        return true;
    }

    /**
     * Opens the channel to the FiRa applet, returns the Response APDU.
     */
    @NonNull
    public ResponseApdu openChannelWithResponse() throws IOException {
        ResponseApdu responseApdu = ResponseApdu.fromStatusWord(SW_TEMPORARILY_UNAVAILABLE);
        for (int i = 0; i < MAX_SE_OPERATION_RETRIES; i++) {
            responseApdu = mOmapiConnection.openChannel();

            if (!shouldRetryOpenChannel(responseApdu)) {
                break;
            }

            logw(
                    "Open channel failed because SE is temporarily unavailable. "
                            + "Total attempts so far: "
                            + (i + 1));

            threadSleep(DELAY_BETWEEN_SE_RETRY_ATTEMPTS_MILLIS);
        }

        if (responseApdu.getStatusWord() == StatusWord.SW_NO_ERROR.toInt()) {
            mIsOpened = true;
        } else {
            logw("All open channel attempts failed!");
        }
        return responseApdu;
    }

    /**
     * Checks if current channel is opened or not.
     */
    public boolean isOpened() {
        return mIsOpened;
    }

    private boolean shouldRetryOpenChannel(ResponseApdu responseApdu) {
        return Arrays.asList(SW_TEMPORARILY_UNAVAILABLE, SW_NO_SPECIFIC_DIAGNOSTIC)
                .contains(StatusWord.fromInt(responseApdu.getStatusWord()));
    }

    /**
     * Closes the channel to the FiRa applet.
     * @return
     */
    public boolean closeChannel() {
        try {
            mOmapiConnection.closeChannel();
        } catch (IOException e) {
            logw("Encountered exception while closing channel" + e);
            return false;
        }
        mIsOpened = false;
        return true;
    }

    /**
     * Transmits a Command APDU defined by the FiRa to the FiRa applet.
     */
    @NonNull
    public ResponseApdu transmit(@NonNull FiRaCommand fiRaCommand) throws IOException {
        return transmit(fiRaCommand.getCommandApdu());
    }

    /**
     * Transmits a Command APDU to FiRa applet.
     */
    @NonNull
    public ResponseApdu transmit(@NonNull CommandApdu command) throws IOException {
        ResponseApdu responseApdu = ResponseApdu.fromStatusWord(SW_TEMPORARILY_UNAVAILABLE);

        if (!mIsOpened) {
            return responseApdu;
        }
        for (int i = 0; i < MAX_SE_OPERATION_RETRIES; i++) {
            responseApdu = mOmapiConnection.transmit(command);
            if (responseApdu.getStatusWord() != SW_TEMPORARILY_UNAVAILABLE.toInt()) {
                return responseApdu;
            }
            logw(
                    "Transmit failed because SE is temporarily unavailable. "
                            + "Total attempts so far: "
                            + (i + 1));
            threadSleep(DELAY_BETWEEN_SE_RETRY_ATTEMPTS_MILLIS);
        }
        logw("All transmit attempts for SE failed!");
        return responseApdu;
    }

    private void threadSleep(long millis) {
        if (!mRemoveDelayBetweenRetriesForTest) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                logw("Thread sleep interrupted.");
            }
        }
    }

    private void logw(String dbgMsg) {
        Log.w(LOG_TAG, dbgMsg);
    }

    private void loge(String dbgMsg) {
        Log.e(LOG_TAG, dbgMsg);
    }
}
