/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.chassis.car.ui.plugin.toolbar;

import androidx.annotation.NonNull;

import com.android.car.ui.plugin.oemapis.toolbar.ProgressBarControllerOEMV1;
import com.android.car.ui.toolbar.ProgressBarController;

/**
 * Wrapper class that passes the data to car-ui via ProgressBarControllerOEMV1 interface
 */
public final class ProgressBarAdapterProxy implements ProgressBarControllerOEMV1 {
    private final ProgressBarController mProgressBarController;

    public ProgressBarAdapterProxy(@NonNull ProgressBarController progressBarController) {
        mProgressBarController = progressBarController;
    }

    @Override
    public void setVisible(boolean b) {
        mProgressBarController.setVisible(b);
    }

    @Override
    public void setIndeterminate(boolean b) {
        mProgressBarController.setIndeterminate(b);
    }

    @Override
    public void setMax(int i) {
        mProgressBarController.setMax(i);
    }

    @Override
    public void setMin(int i) {
        mProgressBarController.setMin(i);
    }

    @Override
    public void setProgress(int i) {
        mProgressBarController.setProgress(i);
    }
}
