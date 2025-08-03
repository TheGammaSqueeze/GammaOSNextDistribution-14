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
package com.android.customization.model.themedicon;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import com.android.customization.module.CustomizationPreferences;
import com.android.themepicker.R;
import com.android.wallpaper.module.InjectorProvider;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Retrieves the themed icon switch by {@link ContentResolver} from the current launcher
 */
public class ThemedIconSwitchProvider {

    private static ThemedIconSwitchProvider sThemedIconSwitchProvider;

    private static final String ICON_THEMED = "icon_themed";
    private static final String COL_ICON_THEMED_VALUE = "boolean_value";
    private static final int ENABLED = 1;
    private static final int RESULT_SUCCESS = 1;

    private final ExecutorService mExecutorService = Executors.newSingleThreadExecutor();
    private final ContentResolver mContentResolver;
    private final ThemedIconUtils mThemedIconUtils;
    private final CustomizationPreferences mCustomizationPreferences;

    /** Callback for the themed icon enabled state fetching result. */
    public interface FetchThemedIconEnabledCallback {
        /** Gets called when the result is available. */
        void onResult(boolean isEnabled);
    }

    /** Returns the {@link ThemedIconSwitchProvider} instance. */
    public static ThemedIconSwitchProvider getInstance(Context context) {
        if (sThemedIconSwitchProvider == null) {
            Context appContext = context.getApplicationContext();
            sThemedIconSwitchProvider = new ThemedIconSwitchProvider(
                    appContext.getContentResolver(),
                    new ThemedIconUtils(appContext,
                            appContext.getString(R.string.themed_icon_metadata_key)),
                    (CustomizationPreferences) InjectorProvider.getInjector()
                            .getPreferences(appContext));
        }
        return sThemedIconSwitchProvider;
    }

    private ThemedIconSwitchProvider(ContentResolver contentResolver,
            ThemedIconUtils themedIconUtils, CustomizationPreferences customizationPreferences) {
        mContentResolver = contentResolver;
        mThemedIconUtils = themedIconUtils;
        mCustomizationPreferences = customizationPreferences;
    }

    /** Returns {@code true} if themed icon feature is available. */
    public boolean isThemedIconAvailable() {
        return mThemedIconUtils.isThemedIconAvailable();
    }

    /** Gets the themed icon feature enabled state from SharedPreferences. */
    public boolean isThemedIconEnabled() {
        return mCustomizationPreferences.getThemedIconEnabled();
    }

    /**
     * Fetches the themed icon feature enabled state and stores in SharedPreferences, or returns the
     * SharedPreferences result if the fetching failed.
     */
    public void fetchThemedIconEnabled(@Nullable FetchThemedIconEnabledCallback callback) {
        mExecutorService.submit(() -> {
            try (Cursor cursor = mContentResolver.query(
                    mThemedIconUtils.getUriForPath(ICON_THEMED), /* projection= */ null,
                    /* selection= */ null, /* selectionArgs= */ null, /* sortOrder= */ null)) {
                if (cursor != null && cursor.moveToNext()) {
                    boolean isEnabled = cursor.getInt(cursor.getColumnIndex(COL_ICON_THEMED_VALUE))
                            == ENABLED;
                    if (mCustomizationPreferences.getThemedIconEnabled() != isEnabled) {
                        mCustomizationPreferences.setThemedIconEnabled(isEnabled);
                    }
                    if (callback != null) {
                        postMainThread(() -> callback.onResult(isEnabled));
                    }
                    return;
                }
            }
            if (callback != null) {
                postMainThread(
                        () -> callback.onResult(mCustomizationPreferences.getThemedIconEnabled()));
            }
        });
    }

    /**
     * Enables themed icon feature or not.
     *
     * <p>The value would also be stored in SharedPreferences.
     */
    public void setThemedIconEnabled(boolean enabled) {
        mExecutorService.submit(() -> {
            ContentValues values = new ContentValues();
            values.put(COL_ICON_THEMED_VALUE, enabled);
            int result = mContentResolver.update(mThemedIconUtils.getUriForPath(ICON_THEMED),
                    values, /* where= */ null, /* selectionArgs= */ null);
            if (result == RESULT_SUCCESS) {
                mCustomizationPreferences.setThemedIconEnabled(enabled);
            }
        });
    }

    private void postMainThread(Runnable runnable) {
        new Handler(Looper.getMainLooper()).post(runnable);
    }
}
