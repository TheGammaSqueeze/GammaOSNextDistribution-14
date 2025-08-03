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

package com.android.app.cts.broadcasts;

public class Common {
    public static final String TAG = "BroadcastTest";

    public static final String ORDERED_BROADCAST_ACTION =
            "com.android.app.cts.broadcasts.helper.ORDERED";

    public static final String ORDERED_BROADCAST_INITIAL_DATA =
            "com.android.app.cts.broadcasts.helper.INITIAL_DATA";

    public static final String ORDERED_BROADCAST_RESULT_DATA =
            "com.android.app.cts.broadcasts.helper.RESULT_DATA";
}
