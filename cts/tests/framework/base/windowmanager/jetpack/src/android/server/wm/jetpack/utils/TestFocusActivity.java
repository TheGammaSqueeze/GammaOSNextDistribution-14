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

package android.server.wm.jetpack.utils;

import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

public class TestFocusActivity extends TestActivityWithId {

    private static final int FOCUSABLE_VIEW_COUNT = 3;

    private int mLastKeyCode = 0;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final LinearLayout contentView = new LinearLayout(this);
        contentView.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 1; i <= FOCUSABLE_VIEW_COUNT; i++) {
            final Button button = new Button(this);
            final String text = "#" + i;
            button.setText(text);

            // Makes sure the heights of all focusable views are the same.
            button.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

            // Sets the center view as the default-focused view.
            button.setFocusedByDefault(i == (FOCUSABLE_VIEW_COUNT + 1) / 2);

            // Debug only
            button.setOnFocusChangeListener((v , hasFocus) -> Log.d(getClass().getSimpleName(),
                    "onFocusChange text=" + text + " hasFocus=" + hasFocus));

            contentView.addView(button);
        }
        setContentView(contentView);

        // Debug only
        contentView.getViewTreeObserver().addOnTouchModeChangeListener(isInTouchMode -> Log.d(
                getClass().getSimpleName(), "onTouchModeChanged isInTouchMode=" + isInTouchMode));
    }

    public int getFocusableViewCount() {
        return FOCUSABLE_VIEW_COUNT;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        Log.d(getClass().getSimpleName(), "onKeyDown keyCode=" + KeyEvent.keyCodeToString(keyCode));
        mLastKeyCode = keyCode;
        return super.onKeyDown(keyCode, event);
    }

    public int getLastKeyCode() {
        return mLastKeyCode;
    }
}
