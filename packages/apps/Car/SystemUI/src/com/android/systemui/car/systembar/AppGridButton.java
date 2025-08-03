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

package com.android.systemui.car.systembar;

import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.util.AttributeSet;

import com.android.systemui.statusbar.AlphaOptimizedImageView;

/**
 * AppGridButton is used to display the app grid and toggle recents.
 */
public class AppGridButton extends CarSystemBarButton {
    private RecentsButtonStateProvider mRecentsButtonStateProvider;

    public AppGridButton(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void init() {
        mRecentsButtonStateProvider = new RecentsButtonStateProvider(getContext(), this);
    }

    @Override
    protected void setUpIntents(TypedArray typedArray) {
        mRecentsButtonStateProvider.setUpIntents(typedArray, super::setUpIntents);
    }

    @Override
    protected OnClickListener getButtonClickListener(Intent toSend) {
        return mRecentsButtonStateProvider.getButtonClickListener(toSend,
                super::getButtonClickListener);
    }

    @Override
    protected void updateImage(AlphaOptimizedImageView icon) {
        mRecentsButtonStateProvider.updateImage(icon, super::updateImage);
    }

    @Override
    protected void refreshIconAlpha(AlphaOptimizedImageView icon) {
        mRecentsButtonStateProvider.refreshIconAlpha(icon, super::refreshIconAlpha);
    }
}
