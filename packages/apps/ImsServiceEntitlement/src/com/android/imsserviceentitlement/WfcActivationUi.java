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

package com.android.imsserviceentitlement;

import android.app.Activity;

import androidx.annotation.StringRes;

/** The interface for UI manipulation. */
public interface WfcActivationUi {
    /** Custom result code, indicating activation flow failed. */
    int RESULT_FAILURE = Activity.RESULT_FIRST_USER;

    /** Shows the basic SuW style UI and returns {@code true} on success */
    boolean showActivationUi(
            @StringRes int title,
            @StringRes int text,
            boolean isInProgress,
            @StringRes int primaryButtonText,
            int primaryButtonResult,
            @StringRes int secondaryButtonText);

    /** Shows the full screen webview */
    boolean showWebview(String url, String postData);

    /**
     * Finishes the activity with {@code result}:
     *
     * <ul>
     *   <li>{@link Activity#RESULT_OK}: WFC should be turned on.
     *   <li>{@link Activity#RESULT_CANCELED}: WFC should be OFF because user cancelled.
     *   <li>{@link #RESULT_FAILURE}: WFC can be OFF because of failure.
     * </ul>
     */
    void setResultAndFinish(int result);

    /** Returns the WfcActivationController associated with the UI. */
    WfcActivationController getController();
}
