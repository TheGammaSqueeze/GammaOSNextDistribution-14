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

package com.android.car.qc.provider;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.StrictMode;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.car.qc.QCItem;

import java.util.Set;

/**
 * Base Quick Controls provider implementation.
 */
public abstract class BaseQCProvider extends ContentProvider {
    public static final String METHOD_BIND = "QC_METHOD_BIND";
    public static final String METHOD_SUBSCRIBE = "QC_METHOD_SUBSCRIBE";
    public static final String METHOD_UNSUBSCRIBE = "QC_METHOD_UNSUBSCRIBE";
    public static final String METHOD_DESTROY = "QC_METHOD_DESTROY";
    public static final String EXTRA_URI = "QC_EXTRA_URI";
    public static final String EXTRA_ITEM = "QC_EXTRA_ITEM";

    private static final String TAG = "BaseQCProvider";
    private static final long QC_ANR_TIMEOUT = 3000L;
    private static final Handler MAIN_THREAD_HANDLER = new Handler(Looper.getMainLooper());
    private String mCallbackMethod;
    private final Runnable mAnr = () -> {
        Process.sendSignal(Process.myPid(), Process.SIGNAL_QUIT);
        Log.e(TAG, "Timed out while handling QC method " + mCallbackMethod);
    };

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        enforceCallingPermissions();

        Uri uri = getUriWithoutUserId(validateIncomingUriOrNull(
                extras.getParcelable(EXTRA_URI)));
        switch(method) {
            case METHOD_BIND:
                QCItem item = handleBind(uri);
                Bundle b = new Bundle();
                b.putParcelable(EXTRA_ITEM, item);
                return b;
            case METHOD_SUBSCRIBE:
                handleSubscribe(uri);
                break;
            case METHOD_UNSUBSCRIBE:
                handleUnsubscribe(uri);
                break;
            case METHOD_DESTROY:
                handleDestroy(uri);
                break;
        }
        return super.call(method, arg, extras);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }

    /**
     * Method to create and return a {@link QCItem}.
     *
     * onBind is expected to return as quickly as possible. Therefore, no network or other IO
     * will be allowed. Any loading that needs to be done should happen in the background and
     * should then notify the content resolver of the change when ready to provide the
     * complete data in onBind.
     */
    @Nullable
    protected QCItem onBind(@NonNull Uri uri) {
        return null;
    }

    /**
     * Called to inform an app that an item has been subscribed to.
     *
     * Subscribing is a way that a host can notify apps of which QCItems they would like to
     * receive updates for. The providing apps are expected to keep the content up to date
     * and notify of change via the content resolver.
     */
    protected void onSubscribed(@NonNull Uri uri) {
    }

    /**
     * Called to inform an app that an item has been unsubscribed from.
     *
     * This is used to notify providing apps that a host is no longer listening
     * to updates, so any background processes and/or listeners should be removed.
     */
    protected void onUnsubscribed(@NonNull Uri uri) {
    }

    /**
     * Called to inform an app that an item is being destroyed.
     *
     * This is used to notify providing apps that a host is no longer going to use this QCItem
     * instance, so the relevant elements should be cleaned up.
     */
    protected void onDestroy(@NonNull Uri uri) {
    }

    /**
     * Returns a Set of packages that are allowed to call this provider.
     */
    @NonNull
    protected abstract Set<String> getAllowlistedPackages();

    private QCItem handleBind(Uri uri) {
        mCallbackMethod = "handleBind";
        MAIN_THREAD_HANDLER.postDelayed(mAnr, QC_ANR_TIMEOUT);
        try {
            return onBindStrict(uri);
        } finally {
            MAIN_THREAD_HANDLER.removeCallbacks(mAnr);
        }
    }

    private QCItem onBindStrict(@NonNull Uri uri) {
        StrictMode.ThreadPolicy oldPolicy = StrictMode.getThreadPolicy();
        try {
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                    .detectAll()

                    // TODO(268275789): Revert back to penaltyDeath and ensure it works in
                    //                  presubmit
                    .penaltyLog()

                    .build());
            return onBind(uri);
        } finally {
            StrictMode.setThreadPolicy(oldPolicy);
        }
    }

    private void handleSubscribe(@NonNull Uri uri) {
        mCallbackMethod = "handleSubscribe";
        MAIN_THREAD_HANDLER.postDelayed(mAnr, QC_ANR_TIMEOUT);
        try {
            onSubscribed(uri);
        } finally {
            MAIN_THREAD_HANDLER.removeCallbacks(mAnr);
        }
    }

    private void handleUnsubscribe(@NonNull Uri uri) {
        mCallbackMethod = "handleUnsubscribe";
        MAIN_THREAD_HANDLER.postDelayed(mAnr, QC_ANR_TIMEOUT);
        try {
            onUnsubscribed(uri);
        } finally {
            MAIN_THREAD_HANDLER.removeCallbacks(mAnr);
        }
    }

    private void handleDestroy(@NonNull Uri uri) {
        mCallbackMethod = "handleDestroy";
        MAIN_THREAD_HANDLER.postDelayed(mAnr, QC_ANR_TIMEOUT);
        try {
            onDestroy(uri);
        } finally {
            MAIN_THREAD_HANDLER.removeCallbacks(mAnr);
        }
    }

    private Uri validateIncomingUriOrNull(Uri uri) {
        if (uri == null) {
            throw new IllegalArgumentException("Uri cannot be null");
        }
        return validateIncomingUri(uri);
    }

    private void enforceCallingPermissions() {
        String callingPackage = getCallingPackage();
        if (callingPackage == null) {
            throw new IllegalArgumentException("Calling package cannot be null");
        }
        if (!getAllowlistedPackages().contains(callingPackage)) {
            throw new SecurityException(
                    String.format("%s is not permitted to access provider: %s", callingPackage,
                            getClass().getName()));
        }
    }
}
