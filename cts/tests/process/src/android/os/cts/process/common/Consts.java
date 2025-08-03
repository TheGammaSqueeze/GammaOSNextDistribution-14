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
package android.os.cts.process.common;

import android.content.ComponentName;
import android.os.cts.process.helper.MyReceiver0;
import android.os.cts.process.helper.MyReceiver1;
import android.os.cts.process.helper.MyReceiver2;
import android.os.cts.process.helper.MyReceiver3;

public class Consts {
    private Consts() {
    }

    public static final String TAG = "CtsProcessTest";

    public static final String PACKAGE_HELPER1 = "android.os.cts.process.helper1";
    public static final String PACKAGE_HELPER2 = "android.os.cts.process.helper2";
    public static final String PACKAGE_HELPER3 = "android.os.cts.process.helper3";
    public static final String PACKAGE_HELPER4 = "android.os.cts.process.helper4";

    public static final String HELPER_SHARED_PROCESS_NAME =
            "android.os.cts.process.helper.shared_process";

    private static ComponentName buildReceiver(String packageName, int receiverId) {
        switch (receiverId) {
            case 0:
                return new ComponentName(packageName, MyReceiver0.class.getName());
            case 1:
                return new ComponentName(packageName, MyReceiver1.class.getName());
            case 2:
                return new ComponentName(packageName, MyReceiver2.class.getName());
            case 3:
                return new ComponentName(packageName, MyReceiver3.class.getName());
            default:
                throw new RuntimeException("Unsupported ID detected: " + receiverId);
        }
    }

    public static final ComponentName HELPER1_RECEIVER0 = buildReceiver(PACKAGE_HELPER1, 0);
    public static final ComponentName HELPER1_RECEIVER1 = buildReceiver(PACKAGE_HELPER1, 1);
    public static final ComponentName HELPER1_RECEIVER2 = buildReceiver(PACKAGE_HELPER1, 2);

    public static final ComponentName HELPER2_RECEIVER0 = buildReceiver(PACKAGE_HELPER2, 0);
    public static final ComponentName HELPER2_RECEIVER1 = buildReceiver(PACKAGE_HELPER2, 1);
    public static final ComponentName HELPER2_RECEIVER2 = buildReceiver(PACKAGE_HELPER2, 2);

    public static final ComponentName HELPER3_RECEIVER0 = buildReceiver(PACKAGE_HELPER3, 0);
    public static final ComponentName HELPER3_RECEIVER1 = buildReceiver(PACKAGE_HELPER3, 1);
    public static final ComponentName HELPER3_RECEIVER2 = buildReceiver(PACKAGE_HELPER3, 2);
    public static final ComponentName HELPER3_RECEIVER3 = buildReceiver(PACKAGE_HELPER3, 3);

    public static final ComponentName HELPER4_RECEIVER0 = buildReceiver(PACKAGE_HELPER4, 0);
    public static final ComponentName HELPER4_RECEIVER1 = buildReceiver(PACKAGE_HELPER4, 1);
    public static final ComponentName HELPER4_RECEIVER2 = buildReceiver(PACKAGE_HELPER4, 2);
    public static final ComponentName HELPER4_RECEIVER3 = buildReceiver(PACKAGE_HELPER4, 3);

    public static final String ACTION_SEND_BACK_START_TIME = "ACTION_SEND_BACK_START_TIME";
}
