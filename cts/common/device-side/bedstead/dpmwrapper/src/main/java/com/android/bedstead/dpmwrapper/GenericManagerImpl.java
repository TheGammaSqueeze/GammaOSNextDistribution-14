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
package com.android.bedstead.dpmwrapper;

import android.content.ContentResolver;
import android.content.Context;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.util.Log;

final class GenericManagerImpl implements GenericManager {

    private static final String TAG = GenericManagerImpl.class.getSimpleName();

    private String mUser;
    private final ContentResolver mContentResolver;

    GenericManagerImpl(Context context) {
        try  {
            mUser = String.valueOf(context.getUser().getIdentifier());
        } catch (Throwable e) {
            Log.w(TAG, "Error while extracting User data from " + context + " : " + e);
            mUser = "N/A";
        }
        mContentResolver = context.getContentResolver();
    }

    @Override
    public int getSecureIntSettings(String setting) throws SettingNotFoundException {
        int value = Settings.Secure.getInt(mContentResolver, setting);
        Log.d(TAG, "getSecureIntSettings(" + setting + ") for user " + mUser + ": " + value);
        return value;
    }
}
