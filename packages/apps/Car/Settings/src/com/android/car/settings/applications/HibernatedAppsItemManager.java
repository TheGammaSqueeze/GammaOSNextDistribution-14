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

package com.android.car.settings.applications;

import android.content.Context;
import android.permission.PermissionControllerManager;

import androidx.annotation.NonNull;

/**
 * Class for fetching and returning the number of hibernated apps. Largely derived from
 * {@link com.android.settings.applications.HibernatedAppsPreferenceController}.
 */
public class HibernatedAppsItemManager {
    private final Context mContext;
    private HibernatedAppsCountListener mListener;

    public HibernatedAppsItemManager(Context context) {
        mContext = context;
    }

    /**
     * Starts fetching recently used apps
     */
    public void startLoading() {
        PermissionControllerManager permController =
                mContext.getSystemService(PermissionControllerManager.class);
        if (mListener != null && permController != null) {
            // This executor is only used for returning the value
            // The main logic happens on a background thread
            permController.getUnusedAppCount(mContext.getMainExecutor(),
                    mListener::onHibernatedAppsCountLoaded);
        }
    }

    /**
     * Registers a listener that will be notified once the data is loaded.
     */
    public void setListener(@NonNull HibernatedAppsCountListener listener) {
        mListener = listener;
    }

    /**
     * Callback that is called once the count of hibernated apps has been fetched.
     */
    public interface HibernatedAppsCountListener {
        /**
         * Called when the count of hibernated apps has loaded.
         */
        void onHibernatedAppsCountLoaded(int hibernatedAppsCount);
    }
}
