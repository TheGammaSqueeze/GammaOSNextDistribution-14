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

package com.android.tv.settings.widget;

import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;

import androidx.lifecycle.MutableLiveData;

import com.android.settingslib.utils.ThreadUtils;

public class PreferenceContentProviderLiveData extends MutableLiveData<Boolean> {
    private static final String AUTHORITY = "com.google.android.apps.tv.settings.contentprovider";
    private static final String PATH_PREFERENCE = "preference";
    private static final String PREFERENCE_DATA_PROVIDER_PATH =
            "content://" + AUTHORITY + "/" + PATH_PREFERENCE;

    private final ContentObserver mContentObserver = new ContentObserver(null) {
        @Override
        public void onChange(boolean selfChange) {
            postGetVisibilityStatus();
        }
    };

    private final Context mContext;
    private final Uri mUri;
    private final String mKey;
    private boolean mRegistered = false;
    private static final String VISIBLE = "visible";

    public PreferenceContentProviderLiveData(String key, Context context) {
        mKey = key;
        mUri = Uri.parse(PREFERENCE_DATA_PROVIDER_PATH + "/" + key);
        mContext = context;
    }

    @Override
    protected void onActive() {
        postGetVisibilityStatus();
        if (!mRegistered) {
            mContext.getContentResolver().registerContentObserver(mUri, false,
                    mContentObserver);
            mRegistered = true;
        }
    }

    @Override
    protected void onInactive() {
        mContext.getContentResolver().unregisterContentObserver(mContentObserver);
        mRegistered = false;
    }

    public String getKey() {
        return mKey;
    }

    private void postGetVisibilityStatus() {
        ThreadUtils.postOnBackgroundThread(() -> {
            postValue(Boolean.valueOf(getVisibilityStatus()));
        });
    }

    private boolean getVisibilityStatus() {
        try {
            Cursor cursor = mContext.getContentResolver().query(mUri, null, null, null);
            if (cursor != null && cursor.getCount() != 0) {
                cursor.moveToFirst();
                int visible = cursor.getInt(cursor.getColumnIndex(VISIBLE));
                return visible == 1;
            }
        } catch (IllegalArgumentException | NullPointerException e) {
            return true;
        }
        return true;
    }
}
