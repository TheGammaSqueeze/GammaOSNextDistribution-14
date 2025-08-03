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

package android.server.wm.backgroundactivity.appa;

import static android.server.wm.backgroundactivity.common.CommonComponents.EVENT_NOTIFIER_EXTRA;
import static android.server.wm.backgroundactivity.common.CommonComponents.Event.APP_A_START_WIDGET_CONFIG_ACTIVITY;

import android.app.Activity;
import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ResultReceiver;

public class WidgetConfigTestActivity extends Activity {

    private static final int REQUEST_BIND_APPWIDGET = 0;
    private ResultReceiver mResultReceiver;

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        Intent widgetIntent = getIntent().getParcelableExtra(Intent.EXTRA_INTENT, Intent.class);
        mResultReceiver = getIntent().getParcelableExtra(EVENT_NOTIFIER_EXTRA,
                ResultReceiver.class);
        startActivityForResult(widgetIntent, REQUEST_BIND_APPWIDGET);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_BIND_APPWIDGET && resultCode == Activity.RESULT_OK) {
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                mResultReceiver.send(APP_A_START_WIDGET_CONFIG_ACTIVITY, null);
                final int appWidgetId = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1);
                AppWidgetHost appWidgetHost = new AppWidgetHost(getApplicationContext(), 0);
                appWidgetHost.startAppWidgetConfigureActivityForResult(this,
                        appWidgetId, 0, 0, null);
            }, 15000);
        }
    }
}