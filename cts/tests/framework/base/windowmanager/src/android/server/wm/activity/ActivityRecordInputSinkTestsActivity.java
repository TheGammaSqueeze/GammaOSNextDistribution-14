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
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;

import java.util.concurrent.atomic.AtomicInteger;

public class ActivityRecordInputSinkTestsActivity extends Activity {

    static final String LAUNCH_ACTIVITY_ACTION = "launch";
    static final String COMPONENT_EXTRA = "component";
    static final String EXTRA_EXTRA = "extra";

    Button mTopButton;
    Button mBottomButton;

    static volatile AtomicInteger sButtonClickCount = new AtomicInteger(0);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mTopButton = new Button(this);
        mTopButton.setOnClickListener(v -> sButtonClickCount.getAndIncrement());
        mTopButton.setLayoutParams(
                new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.MATCH_PARENT));
        setContentView(mTopButton);
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerReceiver(
                mReceiver, new IntentFilter(LAUNCH_ACTIVITY_ACTION), Context.RECEIVER_NOT_EXPORTED);
    }

    @Override
    protected void onStop() {
        unregisterReceiver(mReceiver);
        super.onStop();
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case LAUNCH_ACTIVITY_ACTION:
                    Intent activityIntent = new Intent();
                    activityIntent.setComponent(intent.getParcelableExtra(COMPONENT_EXTRA,
                            ComponentName.class));
                    activityIntent.replaceExtras(intent.getBundleExtra(EXTRA_EXTRA));
                    startActivity(activityIntent);
                    break;
                default:
                    throw new AssertionError("Unknown action" + intent.getAction());
            }
        }
    };

}
