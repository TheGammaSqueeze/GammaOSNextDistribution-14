/*
 * Copyright 2021 The Android Open Source Project
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

package android.server.wm.backgroundactivity.common;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.os.ResultReceiver;

interface ITestService {
    PendingIntent generatePendingIntent(in ComponentName componentName, in int flags, in Bundle createOptions, in ResultReceiver resultReceiver);
    PendingIntent generatePendingIntentBroadcast(in ComponentName componentName, in ResultReceiver resultReceiver);
    void startManageSpaceActivity();
    void sendByTextClassification(in TextClassification classification);
    void sendPendingIntent(in PendingIntent pendingIntent, in Bundle sendOptions);
    void sendPendingIntentWithActivity(in PendingIntent pendingIntent, in Bundle sendOptions);
    void sendPendingIntentWithActivityForResult(in PendingIntent pendingIntent, in Bundle sendOptions);
    void startActivityIntent(in Intent intent);
}