/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.cts.mocka11yime;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.PersistableBundle;

import androidx.annotation.AnyThread;
import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * {@link ContentProvider} to receive {@link MockA11yImeSettings} via
 * {@link ContentProvider#call(String, String, String, Bundle)}.
 */
public final class MockA11yImeContentProvider extends ContentProvider {

    private static final Object sParamsLock = new Object();

    @GuardedBy("sParamsLock")
    @Nullable
    private static MockA11yImeSettings sSettings = null;

    @GuardedBy("sParamsLock")
    @Nullable
    private static String sClientPackageName = null;

    @GuardedBy("sParamsLock")
    @Nullable
    private static String sEventCallbackIntentActionName = null;

    @Override
    public boolean onCreate() {
        return false;
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection,
            @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        return null;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return null;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection,
            @Nullable String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection,
            @Nullable String[] selectionArgs) {
        return 0;
    }

    @Override
    public Bundle call(String authority,
            @MockA11yImeConstants.ContentProviderCommand String method, String arg, Bundle extras) {
        if (!MockA11yImeConstants.SETTINGS_PROVIDER_AUTHORITY.equals(authority)) {
            return Bundle.EMPTY;
        }

        switch (method) {
            case MockA11yImeConstants.ContentProviderCommand.DELETE:
                setParams(null, null, null);
                return Bundle.EMPTY;
            case MockA11yImeConstants.ContentProviderCommand.WRITE: {
                final String callingPackageName = getCallingPackage();
                if (callingPackageName == null) {
                    throw new SecurityException("Failed to obtain the calling package name.");
                }
                setParams(callingPackageName, extras.getString(
                                MockA11yImeConstants.BundleKey.EVENT_CALLBACK_INTENT_ACTION_NAME),
                        new MockA11yImeSettings(extras.getParcelable(
                                MockA11yImeConstants.BundleKey.SETTINGS, PersistableBundle.class)));
                return Bundle.EMPTY;
            }
            default:
                return Bundle.EMPTY;
        }
    }

    @AnyThread
    private static void setParams(@Nullable String clientPackageName,
            @Nullable String eventCallbackIntentActionName,
            @Nullable MockA11yImeSettings settings) {
        synchronized (sParamsLock) {
            sClientPackageName = clientPackageName;
            sEventCallbackIntentActionName = eventCallbackIntentActionName;
            sSettings = settings;
        }
    }

    @AnyThread
    @Nullable
    static MockA11yImeSettings getSettings() {
        synchronized (sParamsLock) {
            return sSettings;
        }
    }

    @AnyThread
    @Nullable
    static String getClientPackageName() {
        synchronized (sParamsLock) {
            return sClientPackageName;
        }
    }

    @AnyThread
    @Nullable
    static String getEventCallbackActionName() {
        synchronized (sParamsLock) {
            return sEventCallbackIntentActionName;
        }
    }
}
