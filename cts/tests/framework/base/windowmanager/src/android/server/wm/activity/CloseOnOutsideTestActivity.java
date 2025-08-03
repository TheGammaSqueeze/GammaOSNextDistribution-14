/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.server.wm.activity;

import android.app.Activity;
import android.graphics.Insets;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager.LayoutParams;
import android.view.WindowMetrics;

import androidx.annotation.Nullable;

/**
 * Activity that makes its Window half width/height so that an area exists outside which can be
 * tapped to close it when {@link Activity#setFinishOnTouchOutside(boolean)} is enabled.
 */
public class CloseOnOutsideTestActivity extends Activity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    public void setupWindowSize() {
        View contentView = getWindow().getDecorView().findViewById(android.R.id.content);
        int width = contentView.getWidth();
        int height = contentView.getHeight();

        WindowMetrics windowMetrics = getWindowManager().getCurrentWindowMetrics();
        Insets insets = windowMetrics.getWindowInsets().getInsets(WindowInsets.Type.systemBars());

        LayoutParams params = getWindow().getAttributes();
        params.width = width / 2;
        params.height = height / 2;
        params.gravity = Gravity.LEFT | Gravity.TOP;
        params.x = insets.left + (width / 4);
        params.y = insets.top + (height / 4);
        getWindow().setAttributes(params);
    }
}
