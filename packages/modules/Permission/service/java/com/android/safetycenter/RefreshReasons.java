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

package com.android.safetycenter;

import static android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE;
import static android.safetycenter.SafetyCenterManager.EXTRA_REFRESH_REQUEST_TYPE_FETCH_FRESH_DATA;
import static android.safetycenter.SafetyCenterManager.EXTRA_REFRESH_REQUEST_TYPE_GET_DATA;
import static android.safetycenter.SafetyCenterManager.REFRESH_REASON_DEVICE_LOCALE_CHANGE;
import static android.safetycenter.SafetyCenterManager.REFRESH_REASON_DEVICE_REBOOT;
import static android.safetycenter.SafetyCenterManager.REFRESH_REASON_OTHER;
import static android.safetycenter.SafetyCenterManager.REFRESH_REASON_PAGE_OPEN;
import static android.safetycenter.SafetyCenterManager.REFRESH_REASON_PERIODIC;
import static android.safetycenter.SafetyCenterManager.REFRESH_REASON_RESCAN_BUTTON_CLICK;
import static android.safetycenter.SafetyCenterManager.REFRESH_REASON_SAFETY_CENTER_ENABLED;

import android.annotation.TargetApi;
import android.safetycenter.SafetyCenterManager.RefreshReason;
import android.safetycenter.SafetyCenterManager.RefreshRequestType;
import android.util.Log;

/** Helpers to do with {@link RefreshReason}. */
final class RefreshReasons {

    private static final String TAG = "RefreshReasons";

    private RefreshReasons() {}

    /**
     * Validates the given {@link RefreshReason}, and throws an {@link IllegalArgumentException} in
     * case of unexpected value.
     */
    @TargetApi(UPSIDE_DOWN_CAKE)
    static void validate(@RefreshReason int refreshReason) {
        switch (refreshReason) {
            case REFRESH_REASON_RESCAN_BUTTON_CLICK:
            case REFRESH_REASON_PAGE_OPEN:
            case REFRESH_REASON_DEVICE_REBOOT:
            case REFRESH_REASON_DEVICE_LOCALE_CHANGE:
            case REFRESH_REASON_SAFETY_CENTER_ENABLED:
            case REFRESH_REASON_OTHER:
            case REFRESH_REASON_PERIODIC:
                return;
        }
        throw new IllegalArgumentException("Unexpected refresh reason: " + refreshReason);
    }

    /** Converts the given {@link RefreshReason} to a {@link RefreshRequestType}. */
    @TargetApi(UPSIDE_DOWN_CAKE)
    @RefreshRequestType
    static int toRefreshRequestType(@RefreshReason int refreshReason) {
        switch (refreshReason) {
            case REFRESH_REASON_RESCAN_BUTTON_CLICK:
                return EXTRA_REFRESH_REQUEST_TYPE_FETCH_FRESH_DATA;
            case REFRESH_REASON_PAGE_OPEN:
            case REFRESH_REASON_DEVICE_REBOOT:
            case REFRESH_REASON_DEVICE_LOCALE_CHANGE:
            case REFRESH_REASON_SAFETY_CENTER_ENABLED:
            case REFRESH_REASON_OTHER:
            case REFRESH_REASON_PERIODIC:
                return EXTRA_REFRESH_REQUEST_TYPE_GET_DATA;
        }
        Log.w(TAG, "Unexpected refresh reason: " + refreshReason);
        return EXTRA_REFRESH_REQUEST_TYPE_GET_DATA;
    }

    /**
     * Returns {@code true} if the given {@link RefreshReason} corresponds to a background refresh.
     */
    @TargetApi(UPSIDE_DOWN_CAKE)
    static boolean isBackgroundRefresh(@RefreshReason int refreshReason) {
        switch (refreshReason) {
            case REFRESH_REASON_DEVICE_REBOOT:
            case REFRESH_REASON_DEVICE_LOCALE_CHANGE:
            case REFRESH_REASON_SAFETY_CENTER_ENABLED:
            case REFRESH_REASON_OTHER:
            case REFRESH_REASON_PERIODIC:
                return true;
            case REFRESH_REASON_PAGE_OPEN:
            case REFRESH_REASON_RESCAN_BUTTON_CLICK:
                return false;
        }
        Log.w(TAG, "Unexpected refresh reason: " + refreshReason);
        return false;
    }
}
