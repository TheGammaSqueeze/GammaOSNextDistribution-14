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

package com.android.car.settings.profiles;

import android.content.Context;
import android.content.Intent;

import com.android.car.settings.common.ConfirmationDialogFragment;
import com.android.car.settings.common.FragmentController;
import com.android.internal.annotations.VisibleForTesting;

/**
 * Consolidates demo profile dialog logic into one handler so we can have
 * consistent logic across various parts of the Settings app.
 */
public class DemoProfileDialogHandler {

    private static final String FACTORY_RESET_PACKAGE_NAME = "android";
    private static final String FACTORY_RESET_REASON = "ExitRetailModeConfirmed";
    @VisibleForTesting
    static final String CONFIRM_EXIT_RETAIL_MODE_DIALOG_TAG =
            "com.android.car.settings.profiles.ConfirmExitRetailModeDialog";

    private final Context mContext;
    private final FragmentController mFragmentController;

    /**
     * Will perform a factory reset. Copied from
     * {@link com.android.settings.MainClearConfirm#doMainClear()}
     */
    private final ConfirmationDialogFragment.ConfirmListener mConfirmExitRetailModeListener;

    public DemoProfileDialogHandler(Context context, FragmentController fragmentController) {
        mContext = context;
        mFragmentController = fragmentController;
        mConfirmExitRetailModeListener =
                arguments -> {
                    Intent intent = new Intent(Intent.ACTION_FACTORY_RESET);
                    intent.setPackage(FACTORY_RESET_PACKAGE_NAME);
                    intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
                    intent.putExtra(Intent.EXTRA_REASON, FACTORY_RESET_REASON);
                    intent.putExtra(Intent.EXTRA_WIPE_EXTERNAL_STORAGE, true);
                    intent.putExtra(Intent.EXTRA_WIPE_ESIMS, true);
                    mContext.sendBroadcast(intent);
                    // Intent handling is asynchronous -- assume it will happen soon.
                };
    }

    /**
     * Handles operations that should happen in host's onCreateInternal().
     * Resets listeners as they can get unregistered with certain configuration changes.
     */
    public void onCreateInternal() {
        ConfirmationDialogFragment.resetListeners(
                (ConfirmationDialogFragment) mFragmentController.findDialogByTag(
                        CONFIRM_EXIT_RETAIL_MODE_DIALOG_TAG),
                mConfirmExitRetailModeListener,
                /* rejectListener= */ null,
                /* neutralListener= */ null);
    }

    /**
     * Display dialog to exit retail mode
     */
    public void showExitRetailDialog() {
        ConfirmationDialogFragment dialogFragment =
                ProfilesDialogProvider.getConfirmExitRetailModeDialogFragment(mContext,
                        mConfirmExitRetailModeListener, null);

        mFragmentController.showDialog(dialogFragment, CONFIRM_EXIT_RETAIL_MODE_DIALOG_TAG);
    }
}
