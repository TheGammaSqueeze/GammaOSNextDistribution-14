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
package com.android.wallpaper.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;

import com.android.wallpaper.R;

/**
 * Custom layout for the download button.
 */
public final class WallpaperDownloadButton extends FrameLayout {

    ToggleButton mDownloadButton;
    View mDownloadActionProgressBar;

    /**
     * Constructor
     */
    public WallpaperDownloadButton(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        LayoutInflater.from(context).inflate(R.layout.button_download_wallpaper, this, true);
        mDownloadButton = findViewById(R.id.download_button);
        mDownloadActionProgressBar = findViewById(R.id.action_download_progress);
    }

    /**
     * Set {@link CompoundButton.OnCheckedChangeListener }
     */
    public void setOnCheckedChangeListener(CompoundButton.OnCheckedChangeListener listener) {
        mDownloadButton.setOnCheckedChangeListener(listener);
    }

    /**
     * Show the progress bar for the download button
     */
    public void showDownloadActionProgress() {
        mDownloadButton.setVisibility(GONE);
        mDownloadActionProgressBar.setVisibility(VISIBLE);
    }

    /**
     * Hide the progress bar for the download button
     */
    public void hideDownloadActionProgress() {
        mDownloadButton.setVisibility(VISIBLE);
        mDownloadActionProgressBar.setVisibility(GONE);
    }

    /**
     * Update the color in case the context theme has changed.
     */
    public void updateColor() {
        Context context = getContext();
        if (context == null) {
            return;
        }
        mDownloadButton.setForeground(null);
        mDownloadButton.setForeground(AppCompatResources.getDrawable(context,
                R.drawable.wallpaper_control_button_download));
    }
}
