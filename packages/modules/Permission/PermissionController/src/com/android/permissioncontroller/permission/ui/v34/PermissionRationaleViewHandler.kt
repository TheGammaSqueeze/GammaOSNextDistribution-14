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

package com.android.permissioncontroller.permission.ui.v34

import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.Window
import androidx.annotation.IntDef
import androidx.annotation.RequiresApi

/**
 * Class for managing the presentation and user interaction of the "permission rationale" user
 * interface.
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
interface PermissionRationaleViewHandler {
    @Retention(AnnotationRetention.SOURCE)
    @IntDef(Result.CANCELLED)
    annotation class Result {
        companion object {
            const val CANCELLED = -1
        }
    }

    /**
     * Listener interface for getting notified when the user responds to a permission rationale user
     * action.
     */
    interface ResultListener {
        fun onPermissionRationaleResult(groupName: String?, @Result result: Int)
    }

    /**
     * Creates and returns the view hierarchy that is managed by this view handler. This must be
     * called before [.updateUi].
     */
    fun createView(): View

    /**
     * Updates the view hierarchy to reflect the specified state.
     *
     * Note that this must be called at least once before showing the UI to the user to properly
     * initialize the UI.
     *
     * @param groupName the name of the permission group
     * @param title the title for the dialog
     * @param dataSharingSourceMessage the data sharing source data usage comes from message to
     *   display the user
     * @param purposeTitle the data usage purposes title to display the user
     * @param purposeMessage the data usage purposes message to display the user
     * @param learnMoreMessage the more info about safety labels message to display the user
     * @param settingsMessage the settings link message to display the user
     */
    fun updateUi(
        groupName: String,
        title: CharSequence,
        dataSharingSourceMessage: CharSequence,
        purposeTitle: CharSequence,
        purposeMessage: CharSequence,
        learnMoreMessage: CharSequence,
        settingsMessage: CharSequence
    )

    /**
     * Called by [PermissionRationaleActivity] to save the state of this view handler to the
     * specified bundle.
     */
    fun saveInstanceState(outState: Bundle)

    /**
     * Called by [PermissionRationaleActivity] to load the state of this view handler from the
     * specified bundle.
     */
    fun loadInstanceState(savedInstanceState: Bundle)

    /** Gives a chance for handling the back key. */
    fun onBackPressed()

    /** Handles cancel event for the permission rationale dialog. */
    fun onCancelled() {}

    /**
     * Called by [PermissionRationaleActivity] to allow the handler to update the ui when blur is
     * enabled/disabled.
     */
    fun onBlurEnabledChanged(window: Window?, enabled: Boolean) {}
}
