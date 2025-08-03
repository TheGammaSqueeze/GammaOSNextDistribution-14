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

package com.android.bedstead.nene.ui;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

import android.app.UiAutomation;
import android.content.Intent;

import androidx.test.uiautomator.UiDevice;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.utils.ShellCommandUtils;

/**
 * Test APIs related to UI.
 */
public final class Ui {

    public static final Ui sInstance = new Ui();

    private Ui() {

    }

    /**
     * Get a {@link UiDevice}.
     */
    public UiDevice device() {
        return UiDevice.getInstance(ShellCommandUtils.instrumentation());
    }

    /**
     * Open the home screen.
     */
    public void goHome() {
        Intent homeIntent = new Intent(Intent.ACTION_MAIN);
        homeIntent.addCategory(Intent.CATEGORY_HOME);
        homeIntent.addFlags(FLAG_ACTIVITY_NEW_TASK);
        TestApis.context().instrumentedContext().startActivity(homeIntent);
    }
}
