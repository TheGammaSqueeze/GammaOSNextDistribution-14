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

package com.android.tv.feedbackconsent;

import static com.android.tv.feedbackconsent.TvFeedbackConstants.RESULT_CODE_OK;

import android.content.Context;
import android.os.BugreportManager;
import android.os.BugreportParams;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.os.BugreportManager.BugreportCallback;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

final class TvFeedbackBugreportHelper {

    private static final String TAG = TvFeedbackBugreportHelper.class.getSimpleName();

    public static final String BUGREPORT_FILENAME = "bugreport.zip";
    private File mBugreportFile;
    private final File cacheDir;
    private final Context mContext;

    TvFeedbackBugreportHelper(Context context) {
        mContext = context;
        cacheDir = mContext.getCacheDir();
    }

    void startBugreport(boolean bugreportConsented,
                        ITvDiagnosticInformationManagerCallback callback, Uri bugreportUri) {
        BugreportCallbackImpl bugreportCallback = new BugreportCallbackImpl(callback, bugreportUri);

        if (!bugreportConsented) {
            Log.d(TAG, "User denied consent to share bugreport");
            bugreportCallback.onError(
                BugreportCallback.BUGREPORT_ERROR_USER_DENIED_CONSENT);
            return;
        }

        BugreportManager bugreportManager = mContext.getSystemService(BugreportManager.class);
        if (bugreportManager == null) {
            Log.e(TAG, "BugreportManager is not available");
            bugreportCallback.onError(
                BugreportCallback.BUGREPORT_ERROR_RUNTIME);
            return;
        }

        ParcelFileDescriptor bugreportFd = createBugreportFile();
        if (bugreportFd == null) {
            Log.e(TAG, "Bugreport file descriptor could not be created.");
            bugreportCallback.onError(
                BugreportCallback.BUGREPORT_ERROR_RUNTIME);
            return;
        }

        bugreportManager.startBugreport(bugreportFd,
            /* screenshotFd= */ null,
            new BugreportParams(BugreportParams.BUGREPORT_MODE_FULL),
            runnable -> new Handler(Looper.getMainLooper()).post(runnable),
            bugreportCallback);
    }

    @Nullable
    private ParcelFileDescriptor createBugreportFile() {
        try {
            mBugreportFile = File.createTempFile(BUGREPORT_FILENAME, null, cacheDir);
            return ParcelFileDescriptor.open(
                mBugreportFile,
                ParcelFileDescriptor.MODE_WRITE_ONLY | ParcelFileDescriptor.MODE_APPEND);
        } catch (IOException e) {
            Log.e(TAG, "Error creating file " + BUGREPORT_FILENAME, e);
        }
        return null;
    }

    private final class BugreportCallbackImpl extends BugreportCallback {
        private final ITvDiagnosticInformationManagerCallback mCallback;
        private final Uri mBugreportUri;

        BugreportCallbackImpl(ITvDiagnosticInformationManagerCallback callback, Uri bugreportUri) {
            mCallback = callback;
            mBugreportUri = bugreportUri;
        }

        @Override
        public void onError(@BugreportErrorCode int errorCode) {
            Log.e(TAG, "Error generating bugreport: " + errorCode);
            mBugreportFile.delete();
            try {
                mCallback.onBugreportError(errorCode);
            } catch (RemoteException ex) {
                throw new RuntimeException(ex);
            }
        }

        @Override
        public void onFinished() {
            int bugreportResultCode = copyBugreportToUri(mBugreportUri);
            mBugreportFile.delete();
            try {
                if (bugreportResultCode == RESULT_CODE_OK) {
                    mCallback.onBugreportFinished();
                    Log.d(TAG, "Bugreport generated and returned successfully.");
                } else {
                    mCallback.onBugreportError(bugreportResultCode);
                }

            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
        }

        private int copyBugreportToUri(Uri bugreportUri) {
            try (OutputStream out = mContext.getContentResolver()
                .openOutputStream(bugreportUri, "w")) {
                Files.copy(Path.of(mBugreportFile.getPath()), out);
            } catch (FileNotFoundException e) {
                Log.e(TAG, "Uri file not found", e);
                return BugreportCallback.BUGREPORT_ERROR_INVALID_INPUT;
            } catch (IOException e) {
                Log.e(TAG, "Error copying bugreport", e);
                return BugreportCallback.BUGREPORT_ERROR_RUNTIME;
            }

            return RESULT_CODE_OK;
        }
    }
}