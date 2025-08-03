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

package android.server.wm.overlay;

import static android.server.wm.overlay.UntrustedTouchTestService.BACKGROUND_COLOR;

import android.app.Activity;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Toast;

public class ToastActivity extends Activity {
    private static final String TAG = "ToastActivity";

    @Override
    protected void onResume() {
        super.onResume();
        // For toast matters, foreground means having an activity resumed on screen, so doing this
        // on onResume()
        Toast toast = new Toast(this);
        View view = new View(this);
        view.setBackgroundColor(BACKGROUND_COLOR);
        toast.setView(view);
        toast.setGravity(Gravity.FILL, 0, 0);
        toast.setDuration(Toast.LENGTH_LONG);
        Log.d(TAG, "Posting custom toast");
        toast.show();
        finish();
    }
}
