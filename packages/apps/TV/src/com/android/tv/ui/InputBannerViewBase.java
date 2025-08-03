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

package com.android.tv.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.LinearLayout;

import com.android.tv.MainActivity;
import com.android.tv.R;
import com.android.tv.data.StreamInfo;

public abstract class InputBannerViewBase extends LinearLayout
        implements TvTransitionManager.TransitionLayout {
    protected final long mShowDurationMillis;
    protected final Runnable mHideRunnable =
            () ->
                    ((MainActivity) getContext())
                            .getOverlayManager()
                            .hideOverlays(
                                    TvOverlayManager.FLAG_HIDE_OVERLAYS_KEEP_DIALOG
                                            | TvOverlayManager.FLAG_HIDE_OVERLAYS_KEEP_SIDE_PANELS
                                            | TvOverlayManager.FLAG_HIDE_OVERLAYS_KEEP_PROGRAM_GUIDE
                                            | TvOverlayManager.FLAG_HIDE_OVERLAYS_KEEP_MENU
                                            | TvOverlayManager.FLAG_HIDE_OVERLAYS_KEEP_FRAGMENT);
    public InputBannerViewBase(Context context) {
        this(context, null, 0);
    }

    public InputBannerViewBase(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public InputBannerViewBase(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mShowDurationMillis =
                context.getResources().getInteger(R.integer.select_input_show_duration);

    }

    public abstract void updateLabel();

    @Override
    public void onEnterAction(boolean fromEmptyScene) {
        removeCallbacks(mHideRunnable);
        postDelayed(mHideRunnable, mShowDurationMillis);
    }

    @Override
    public void onExitAction() {
        removeCallbacks(mHideRunnable);
    }

    public void onStreamInfoUpdated(StreamInfo info) {}
}
