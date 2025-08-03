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

package com.android.managedprovisioning.common;

import static java.util.Objects.requireNonNull;

import android.content.Intent;

/**
 * Utilities related to error dialogs, such as constructing a resulting {@link Intent} that
 * describes a dialog.
 */
public final class ErrorDialogUtils {
    public static final String EXTRA_DIALOG_TITLE_ID = "dialog_title_id";
    public static final String EXTRA_ERROR_MESSAGE_RES_ID =
            "dialog_error_message_res_id";
    public static final String EXTRA_FACTORY_RESET_REQUIRED =
            "factory_reset_required";

    private ErrorDialogUtils() {}

    /**
     * Creates a resulting intent to be returned as a result that describes an error.
     */
    public static Intent createResultIntent(ErrorWrapper error) {
        requireNonNull(error);
        Intent intent = new Intent();
        if (error.dialogTitleId != 0) {
            intent.putExtra(EXTRA_DIALOG_TITLE_ID, error.dialogTitleId);
        }
        if (error.errorMessageResId != 0) {
            intent.putExtra(EXTRA_ERROR_MESSAGE_RES_ID, error.errorMessageResId);
        }
        intent.putExtra(EXTRA_FACTORY_RESET_REQUIRED, error.factoryResetRequired);
        return intent;
    }
}
