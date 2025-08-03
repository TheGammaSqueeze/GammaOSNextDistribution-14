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

package com.android.systemui.car.keyguard;

import android.annotation.ColorInt;
import android.content.Context;
import android.util.AttributeSet;

import com.android.keyguard.KeyguardPINView;
import com.android.keyguard.NumPadButton;
import com.android.systemui.R;

/**
 * Car-specific {@link KeyguardPINView} to bypass unwanted view logic.
 *
 * TODO(b/204220809): Instead of extending {@link KeyguardPINView} separate shared logic into
 * interface.
 */
public class CarKeyguardPINView extends KeyguardPINView {
    private final @ColorInt int mButtonImageColor;

    private NumPadButton mOkButton;
    private NumPadButton mDeleteButton;

    public CarKeyguardPINView(Context context) {
        this(context, /* attrs= */ null);
    }

    public CarKeyguardPINView(Context context, AttributeSet attrs) {
        super(context, attrs);

        mButtonImageColor = context.getColor(R.color.keyguard_keypad_image_color);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mOkButton = findViewById(R.id.key_enter);
        mDeleteButton = findViewById(R.id.delete_button);
    }

    @Override
    public void startAppearAnimation() {
        super.startAppearAnimation();

        mOkButton.setColorFilter(mButtonImageColor);
        mDeleteButton.setColorFilter(mButtonImageColor);
    }
}
