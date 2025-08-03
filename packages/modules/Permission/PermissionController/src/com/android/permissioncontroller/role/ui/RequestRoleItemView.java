/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.permissioncontroller.role.ui;

import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

/**
 * Item view for qualifying applications in role requests.
 */
public interface RequestRoleItemView {

    /**
     * Get the {@link ImageView} for item icon.
     */
    @NonNull
    ImageView getIconImageView();

    /**
     * Get the {@link TextView} for item title.
     */
    @NonNull
    TextView getTitleTextView();

    /**
     * Get the {@link TextView} for item subtitle.
     */
    @NonNull
    TextView getSubtitleTextView();
}
