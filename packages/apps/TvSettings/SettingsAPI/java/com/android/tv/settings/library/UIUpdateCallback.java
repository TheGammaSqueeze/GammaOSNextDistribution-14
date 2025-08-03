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

package com.android.tv.settings.library;

import android.annotation.SystemApi;

import java.util.List;

/**
 * @hide
 * Callback for updating UI.
 */
@SystemApi
public interface UIUpdateCallback {
    /**
     *  @hide
     *  Notify to update preferenceCompat in a fragment.
     * @param state state identifier of the fragment
     * @param preferenceCompat the updated preferenceCompat
     */
    @SystemApi
    void notifyUpdate(int state, PreferenceCompat preferenceCompat);

    /**
     * @hide
     * Notify to update a list of preferenceCompats.
     * @param state state identifier of the fragment
     * @param preferences the updated list of preferenceCompats
     */
    @SystemApi
    void notifyUpdateAll(int state, List<PreferenceCompat> preferences);

    /**
     * @hide
     * Notify to update title of a fragment.
     * @param state state identifier of the fragment
     * @param title the updated title
     */
    @SystemApi
    void notifyUpdateScreenTitle(int state, String title);

    /**
     * @hide
     * Notify to navigate backward
     */
    @SystemApi
    void notifyNavigateBackward(int state);


    /**
     * @hide
     * Notify to navigate forward
     */
    @SystemApi
    void notifyNavigateForward(int state);
}
