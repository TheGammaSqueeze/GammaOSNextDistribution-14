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
package com.android.server.uwb.secure.omapi;

import static com.android.server.uwb.secure.iso7816.StatusWord.SW_NO_ERROR;
import static com.android.server.uwb.secure.iso7816.StatusWord.SW_NO_SPECIFIC_DIAGNOSTIC;
import static com.android.server.uwb.util.Constants.FIRA_APPLET_AID;

import static com.google.common.base.Preconditions.checkNotNull;

import android.content.Context;
import android.se.omapi.Channel;
import android.se.omapi.Reader;
import android.se.omapi.SEService;
import android.se.omapi.Session;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import com.android.server.uwb.secure.iso7816.CommandApdu;
import com.android.server.uwb.secure.iso7816.ResponseApdu;
import com.android.server.uwb.util.DataTypeConversionUtil;

import java.io.IOException;
import java.util.NoSuchElementException;
import java.util.concurrent.Executor;

/** Object for creating an actual connection to a secure element through the OMAPI layer. */
@WorkerThread
public class OmapiConnectionImpl implements OmapiConnection {
    private static final String LOG_TAG = "OmapiConnectionImpl";

    private final Context mContext;
    private final Executor mSyncExecutor = (runnable) -> runnable.run();

    @VisibleForTesting
    @Nullable SEService mSeService;
    @Nullable private Session mSession;
    @Nullable private Reader mReader;
    @Nullable private Channel mChannel;

    public OmapiConnectionImpl(
            Context context) {
        this.mContext = context;
    }

    /**
     * Initializes the connection to SeService.
     */
    @Override
    public void init(OmapiConnection.InitCompletionCallback callback) {
        if (this.mSeService == null) {
            this.mSeService =
                    new SEService(
                            mContext,
                            mSyncExecutor,
                            () -> {
                                callback.onInitCompletion();
                            });
        }
    }

    /**
     * Transmits the command APDU to FiRa applet.
     */
    @NonNull
    @Override
    public ResponseApdu transmit(CommandApdu command) throws IOException {
        byte[] response = transmit(command.getEncoded());
        ResponseApdu responseApdu = ResponseApdu.fromResponse(response);
        return responseApdu;
    }

    /**
     * Transmits the given bytes to the SE
     *
     * @param command bytes to transmit, must consist a valid command as a response will be received
     *     from the SE and returned to the caller
     * @return the APDU response received from the SE
     */
    private byte[] transmit(byte[] command) throws IOException {
        if (mChannel == null) {
            throw new IOException("No active channel found.");
        }

        return mChannel.transmit(command);
    }

    /**
     * Closes the logical channel to the FiRa applet.
     * @throws IOException
     */
    @Override
    public void closeChannel() throws IOException {
        Session session = getSession();
        if (session == null) {
            logw("Cannot close channel without a Session.");
        } else {
            session.closeChannels();
            mChannel = null;
        }
    }

    /**
     * Gets tthe debug information for the current OMAPI connection.
     */
    public String getDebugInfo() {
        StringBuilder res = new StringBuilder();
        SEService seService = getSecureElementService();
        if (seService == null) {
            res.append("Could not get SEService");
        } else {
            res.append("Readers: \n");
            for (Reader reader : seService.getReaders()) {
                logi("Found reader: " + reader.getName());
                res.append("\tName: ")
                        .append(reader.getName())
                        .append(" isSecureElementPresent: ")
                        .append(reader.isSecureElementPresent());
            }
        }

        return res.toString();
    }

    @Nullable
    private SEService getSecureElementService() {
        if (mContext == null) {
            logd("The SEService is not initialized.");
            return null;
        }

        if (mSeService == null || !mSeService.isConnected()) {
            logd("OMAPI SEService is not connected.");
            return null;
        }

        return mSeService;
    }

    /**
     * Opens the logical channel to the FiRa applet, called once for each secure session.
     * @throws IOException
     */
    @NonNull
    @Override
    public ResponseApdu openChannel() throws IOException {
        if (mChannel != null) {
            // Repeated SELECT operations are not supported and indicative of leaky code.
            logw("Repeated SELECT operations are not supported.");
            return ResponseApdu.fromStatusWord(SW_NO_SPECIFIC_DIAGNOSTIC);
        }

        byte[] response = null;
        Session session = getSession();
        if (session == null) {
            logw("Cannot open a Channel without a Session.");
        } else {
            try {
                mChannel = session.openLogicalChannel(FIRA_APPLET_AID);
            } catch (SecurityException | NoSuchElementException | UnsupportedOperationException e) {
                logw("Exception trying to talk to DCK Applet");
                throw new IOException(e);
            }
            logi("Logical channel opened for AID: "
                    + DataTypeConversionUtil.byteArrayToHexString(FIRA_APPLET_AID));
            checkNotNull(mChannel);
            response = mChannel.getSelectResponse();
            logi("Channel open response: "
                    + DataTypeConversionUtil.byteArrayToHexString(response));
        }

        if (response == null || response.length == 0) {
            throw new IOException("Null response received from channel open.");
        }

        ResponseApdu responseApdu = ResponseApdu.fromResponse(response);
        return responseApdu;
    }

    @Nullable
    private Session getSession() throws IOException {
        if (mSession == null) {
            Reader reader = getReader();
            if (reader == null) {
                logw("Cannot get Session without Reader.");
            } else {
                logi("Opening session with reader: " + reader.getName());
                mSession = reader.openSession();
            }
        }
        return mSession;
    }

    @Nullable
    private Reader getReader() throws IOException {
        if (mReader == null) {
            SEService seService = getSecureElementService();
            if (seService == null) {
                logw("SEService not connected. Cannot get Reader without SEService.");
            } else {
                for (Reader r : seService.getReaders()) {
                    if (r.getName().startsWith("eSE")) {
                        if (checkFiRaAppletPresence(r)) {
                            mReader = r;
                            break;
                        }
                    }
                }
                if (mReader == null) {
                    logw("Unable to find or select applet.");
                    throw new IOException("FiRa applet not found");
                }
            }
        }
        return mReader;
    }

    private boolean checkFiRaAppletPresence(Reader reader) {
        this.mReader = reader;
        try {
            ResponseApdu selectResponse = openChannel();
            closeChannel();
            if (selectResponse.getStatusWord() == SW_NO_ERROR.toInt()) {
                logi("FiRa applet found with reader: " +  reader.getName());
                return true;
            } else {
                logw("Unable to select applet or applet not found with reader: "
                        + reader.getName()
                        + "Received response to"
                        + " SELECT: "
                        + selectResponse);
            }
        } catch (IOException e) {
            logw("IOException happened with reader: " + reader.getName());
        }

        logw("Error selecting FiRa applet (or applet not present) on reader: "
                + reader.getName());

        this.mReader = null;
        if (mSession != null) {
            mSession.close();
        }
        this.mSession = null;
        return false;
    }

    private void logd(String log) {
        Log.d(LOG_TAG, log);
    }

    private void logw(String log) {
        Log.w(LOG_TAG, log);
    }

    private void logi(String log) {
        Log.i(LOG_TAG, log);
    }
}
