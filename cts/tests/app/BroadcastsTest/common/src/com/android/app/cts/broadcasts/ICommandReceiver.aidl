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
package com.android.app.cts.broadcasts;

import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.RemoteCallback;

import com.android.app.cts.broadcasts.BroadcastReceipt;

import java.util.List;

interface ICommandReceiver {
    void sendBroadcast(in Intent intent, in Bundle options);
    void monitorBroadcasts(in IntentFilter filter, in String cookie);
    List<BroadcastReceipt> getReceivedBroadcasts(in String cookie);
    void clearCookie(in String cookie);
    int getPid();
    void tearDown();
}