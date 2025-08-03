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

package com.android.systemui.car.systembar;

import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.util.AttributeSet;

import com.android.systemui.statusbar.AlphaOptimizedImageView;

/** The button used to show the app grid and Recents in the system bar. */
public class CarUiPortraitAppGridButton extends CarUiPortraitSystemBarButton {
    private RecentsButtonStateProvider mRecentsButtonStateProvider;
    private boolean mIsAppGridActive;

    public CarUiPortraitAppGridButton(Context context,
            AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void init() {
        mRecentsButtonStateProvider = new CarUiRecentsButtonStateProvider(getContext(), this);
    }

    /**
     * To set if the AppGrid activity is in foreground.
     * The button selected state depends on AppGrid or Recents being in foreground.
     */
    public void setAppGridSelected(boolean selected) {
        mIsAppGridActive = selected;
        super.setSelected(mRecentsButtonStateProvider.getIsRecentsActive() || mIsAppGridActive);
    }

    /**
     * To set if the Recents activity is in foreground.
     * The button selected state depends on AppGrid or Recents being in foreground.
     */
    public void setRecentsSelected(boolean selected) {
        mRecentsButtonStateProvider.setIsRecentsActive(selected);

        super.setSelected(mRecentsButtonStateProvider.getIsRecentsActive() || mIsAppGridActive);
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
