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

import android.app.Application;
import android.util.ArrayMap;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;

import java.util.Map;

public class SettingsViewModel extends AndroidViewModel {
    private final Map<String, PreferenceContentProviderLiveData> mPreferenceVisibilityLiveDataMap =
            new ArrayMap<>();

    public SettingsViewModel(@NonNull Application application) {
        super(application);
    }

    public PreferenceContentProviderLiveData getVisibilityLiveData(String compoundKey) {
        if (!mPreferenceVisibilityLiveDataMap.containsKey(compoundKey)) {
            init(compoundKey);
        }
        return mPreferenceVisibilityLiveDataMap.get(compoundKey);
    }

    public void init(String compoundKey) {
        mPreferenceVisibilityLiveDataMap.put(compoundKey,
                new PreferenceContentProviderLiveData(compoundKey, getApplication()));
    }
}
