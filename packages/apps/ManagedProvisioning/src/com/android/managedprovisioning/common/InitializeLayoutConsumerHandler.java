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

package com.android.managedprovisioning.common;

import androidx.annotation.LayoutRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.android.managedprovisioning.model.CustomizationParams;

/**
 * A handler that initializes the layout params.
 */
public interface InitializeLayoutConsumerHandler {
    /**
     * A callback to initialize the layout params.
     */
    void initializeLayoutParams(
            @LayoutRes int layoutRes,
            @Nullable @StringRes Integer headerRes);
}
