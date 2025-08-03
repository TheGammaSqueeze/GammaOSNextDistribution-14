/*
 * Copyright 2020 The Android Open Source Project
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

package android.app.stubs.shared;

import android.os.ResultReceiver;

interface ICloseSystemDialogsTestsService {
    void sendCloseSystemDialogsBroadcast();
    void closeSystemDialogsViaWindowManager(String reason);
    void closeSystemDialogsViaActivityManager(String reason);
    boolean waitForAccessibilityServiceWindow(long timeoutMs);

    const int RESULT_OK = 0;
    const int RESULT_SECURITY_EXCEPTION = 1;
    const int RESULT_ERROR = 2;

    /**
     * Posts a notification with id {@code notificationId} with a broadcast pending intent, then in
     * that pending intent sends {@link android.content.Intent#ACTION_CLOSE_SYSTEM_DIALOGS} either
     * via a regular Context.sendBroadcast() call if usePendingIntent is false or via a
     * PendingIntent.getBroadcast(...).send() if usePendingIntent is true.
     *
     * The caller is responsible for trigerring the notification. The passed in {@code receiver}
     * will be called once the intent has been sent.
     */
    void postNotification(int notificationId, in ResultReceiver receiver, boolean usePendingIntent);

    /** Checks that it can call @hide methods. */
    boolean waitUntilReady(long timeoutMs);
}
