/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.graphics.gpuprofiling.app;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.LinearLayout;
import android.widget.TextView;

public class GpuRenderStagesDeviceActivity extends Activity {

    static {
        System.loadLibrary("ctsgraphicsgpuprofiling_jni");
    }

    private static final String TAG = GpuRenderStagesDeviceActivity.class.getSimpleName();
    private static final int ANIM_LOOP_DURATION = 1000;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        int result = nativeInitVulkan();
        Log.i(TAG, "nativeInitVulkan returned: " + result);

        TextView textView = new TextView(this);
        textView.setLayoutParams(
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        textView.setText("Initiating GPU work");

        Animation fade = new AlphaAnimation(0, 1);
        fade.setDuration(ANIM_LOOP_DURATION);
        fade.setRepeatCount(Animation.INFINITE);
        fade.setRepeatMode(Animation.REVERSE);
        fade.setInterpolator(new AccelerateDecelerateInterpolator());

        setContentView(textView);
        textView.startAnimation(fade);
    }

    private static native int nativeInitVulkan();
}
