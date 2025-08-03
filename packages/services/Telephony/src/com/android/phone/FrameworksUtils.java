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

package com.android.phone;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.Configuration;

/**
 * This class provides utility functions over framework APIs
 */
public class FrameworksUtils {
    /**
     * Create a new instance of {@link AlertDialog.Builder}.
     * @param context reference to a Context
     * @return an instance of AlertDialog.Builder
     */
    public static AlertDialog.Builder makeAlertDialogBuilder(Context context) {
        boolean isDarkTheme = (context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        return new AlertDialog.Builder(context, isDarkTheme
                ? android.R.style.Theme_DeviceDefault_Dialog_Alert : 0);
    }
}
