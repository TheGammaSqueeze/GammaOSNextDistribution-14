/*
 * Copyright (C) 2022 The Android Open Source Project
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

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.LinearLayout;

public class TranslucentFloatingActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout tv = new LinearLayout(this);
        tv.setBackgroundColor(Color.GREEN);
        tv.setPadding(50, 50, 50, 50);
        tv.setGravity(Gravity.CENTER);
        setContentView(tv);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);

        registerReceiver(mReceiver,
                new IntentFilter(Components.TranslucentFloatingActivity.ACTION_FINISH),
                Context.RECEIVER_EXPORTED);
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Components.TranslucentFloatingActivity.ACTION_FINISH.equals(intent.getAction())) {
                unregisterReceiver(this);
                finish();
                if (intent.getBooleanExtra(
                        Components.TranslucentFloatingActivity.EXTRA_FADE_EXIT, false)) {
                    overridePendingTransition(0, R.anim.fade);
                }
            }
        }
    };

}
