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

package com.android.tv.settings.system.locale;

import android.app.ActivityManager;
import android.content.Context;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.ViewModel;

import com.android.internal.app.LocaleStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * ViewModel to provide data for locale selection.
 */
public class LocaleDataViewModel extends ViewModel {
    static final boolean TRANSLATED_ONLY = false;
    private static final String TAG = "LocaleDataViewModel";
    @VisibleForTesting
    final Map<LocaleStore.LocaleInfo, List<LocaleStore.LocaleInfo>> mLocaleMap =
            new HashMap<>();
    Set<LocaleStore.LocaleInfo> mLocaleInfos;

    public static Locale getCurrentLocale() {
        try {
            return ActivityManager.getService().getConfiguration()
                    .getLocales().get(0);
        } catch (RemoteException e) {
            Log.e(TAG, "Could not retrieve locale", e);
            return null;
        }
    }

    public synchronized Set<LocaleStore.LocaleInfo> getLocaleInfos(
            Context context) {
        if (mLocaleInfos == null) {
            mLocaleInfos = LocaleStore.getLevelLocales(context,
                    /* ignorables= */ Collections.emptySet(),
                    /* parent= */ null,
                    TRANSLATED_ONLY);
        }
        return mLocaleInfos;
    }

    public synchronized void addLocaleInfoList(LocaleStore.LocaleInfo localeInfo, Context context) {
        if (mLocaleMap.containsKey(localeInfo)) {
            return;
        }
        ArrayList<LocaleStore.LocaleInfo> localeInfoWithCountryList = new ArrayList<>(
                LocaleStore.getLevelLocales(
                        context, Collections.emptySet(), localeInfo, TRANSLATED_ONLY));
        mLocaleMap.put(localeInfo, localeInfoWithCountryList);
    }

    public synchronized List<LocaleStore.LocaleInfo>
    getLocaleInfoList(LocaleStore.LocaleInfo localeInfo) {
        return mLocaleMap.get(localeInfo);
    }
}