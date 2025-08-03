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

package com.android.car.settings.security;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.android.car.settings.R;
import com.android.internal.widget.LockPatternView;

/** A LockPatternView that shows a toast when focused. */
public class CarLockPatternView extends LockPatternView {

    public CarLockPatternView(Context context) {
        super(context);
    }

    public CarLockPatternView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, @FocusDirection int direction,
            @android.annotation.Nullable Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        if (gainFocus) {
            Toast.makeText(getContext(),
                    getContext().getString(R.string.lockpattern_does_not_support_rotary),
                    Toast.LENGTH_LONG)
                    .show();
        }
    }
}
