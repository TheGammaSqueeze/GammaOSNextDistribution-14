/*
 * Copyright (C) 2018 The Android Open Source Project
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
package android.appwidget.cts.appbal;

import android.app.Activity;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

public class BalActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        requestAppWidget();
    }

    private void requestAppWidget() {
        try {
            // pinResult tries to launch a service which launches a background activity.
            PendingIntent pinResult = PendingIntent.getService(this, 0,
                    new Intent(this, BalService.class),
                    PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_MUTABLE);
            AppWidgetManager appWidgetManager = this.getSystemService(AppWidgetManager.class);
            android.content.ComponentName firstWidgetProvider =
                    new android.content.ComponentName(this, BalAppWidgetProvider.class);
            appWidgetManager.requestPinAppWidget(firstWidgetProvider, null, pinResult);
            Log.i("BalActivity", "requested pin App widget");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
