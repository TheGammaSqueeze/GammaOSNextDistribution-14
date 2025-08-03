/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.server.wm.backgroundactivity.appa;

import static android.server.wm.backgroundactivity.common.CommonComponents.EVENT_NOTIFIER_EXTRA;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;
import android.server.wm.backgroundactivity.common.CommonComponents;

public class StartNextMatchingActivity extends Activity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ResultReceiver receiver = getIntent().getParcelableExtra(EVENT_NOTIFIER_EXTRA,
                ResultReceiver.class);
        receiver.send(CommonComponents.Event.APP_A_LAUNCHER_MOVING_TO_BACKGROUND_ACTIVITY, null);

        moveTaskToBack(true);
        new Handler().postDelayed(() -> {
            startNextMatchingActivity(getIntent());
        }, 5000);
    }
}

