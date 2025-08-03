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

package com.android.tv.settings.library.util;

import android.content.Context;

import com.android.tv.settings.library.PreferenceCompat;
import com.android.tv.settings.library.UIUpdateCallback;
import com.android.tv.settings.library.data.PreferenceCompatManager;


/**
 * A controller that manages event for preference.
 */
public abstract class AbstractPreferenceController {

    private static final String TAG = "AbstractPrefController";

    protected final Context mContext;
    protected final UIUpdateCallback mUIUpdateCallback;
    protected final int mStateIdentifier;
    protected final PreferenceCompatManager mPreferenceCompatManager;
    protected final String[] mKey;
    protected final PreferenceCompat mPreferenceCompat;

    public AbstractPreferenceController(Context context, UIUpdateCallback callback,
            int stateIdentifier, PreferenceCompatManager preferenceCompatManager) {
        mUIUpdateCallback = callback;
        mContext = context;
        mStateIdentifier = stateIdentifier;
        mPreferenceCompatManager = preferenceCompatManager;
        mKey = getPreferenceKey();
        mPreferenceCompat = mPreferenceCompatManager.getOrCreatePrefCompat(mKey);
    }

    public AbstractPreferenceController(Context context, UIUpdateCallback callback,
            int stateIdentifier, PreferenceCompatManager preferenceCompatManager, String[] key) {
        mUIUpdateCallback = callback;
        mContext = context;
        mStateIdentifier = stateIdentifier;
        mPreferenceCompatManager = preferenceCompatManager;
        mKey = key;
        mPreferenceCompat = mPreferenceCompatManager.getOrCreatePrefCompat(mKey);
    }

    /**
     * @return whether the setting controlled by the controller is available.
     */
    public abstract boolean isAvailable();

    /**
     * Initialize preference compat.
     */
    protected abstract void init();

    /**
     * Initialize preference compat and notify to update UI.
     */
    public void initAndNotify() {
        init();
        notifyChange();
    }

    /**
     * Updates the current status of preference compat.
     */
    public void updateAndNotify() {
        update();
        notifyChange();
    }

    public void notifyChange() {
        if (mPreferenceCompat != null) {
            mUIUpdateCallback.notifyUpdate(mStateIdentifier, mPreferenceCompat);
        }
    }

    /**
     * Update preference compat.
     */
    protected abstract void update();


    /**
     * Handle preference click.
     *
     * @param status status of new state
     * @return whether the click is handled
     */
    public boolean handlePreferenceTreeClick(boolean status) {
        return false;
    }

    /**
     * Handle preference change.
     *
     * @param newValue new value of state
     * @return whether the click is handled
     */
    public boolean handlePreferenceChange(Object newValue) {
        return false;
    }

    public PreferenceCompat getPrefCompat() {
        return mPreferenceCompat;
    }

    public String[] getPreferenceKey() {
        return mKey;
    }
}
