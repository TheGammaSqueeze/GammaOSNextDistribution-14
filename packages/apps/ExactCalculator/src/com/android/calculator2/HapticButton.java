/*
 * SPDX-FileCopyrightText: 2006 The Android Open Source Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.calculator2;

import android.content.Context;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import com.google.android.material.button.MaterialButton;

/**
 * A basic Button that vibrates on finger down.
 */
public class HapticButton extends MaterialButton {
    public HapticButton(Context context) {
        super(context);
        initVibration(context);
    }

    public HapticButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        initVibration(context);
    }

    public HapticButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initVibration(context);
    }

    private void initVibration(Context context) {
        setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                }

                // Passthrough
                return false;
            }
        });
    }
}
