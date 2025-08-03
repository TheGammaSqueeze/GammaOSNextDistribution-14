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

package android.server.wm.app;

import static android.server.wm.app.Components.UnresponsiveActivity.EXTRA_ON_CREATE_DELAY_MS;
import static android.server.wm.app.Components.UnresponsiveActivity.EXTRA_ON_KEYDOWN_DELAY_MS;
import static android.server.wm.app.Components.UnresponsiveActivity.EXTRA_ON_MOTIONEVENT_DELAY_MS;

import android.app.Activity;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.MotionEvent;

public class UnresponsiveActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final int delay = getIntent().getIntExtra(EXTRA_ON_CREATE_DELAY_MS, 0);
        SystemClock.sleep(delay);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        final int delay = getIntent().getIntExtra(EXTRA_ON_KEYDOWN_DELAY_MS, 0);
        SystemClock.sleep(delay);
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final int delay = getIntent().getIntExtra(EXTRA_ON_MOTIONEVENT_DELAY_MS, 0);
        SystemClock.sleep(delay);
        return super.onGenericMotionEvent(event);
    }
}
