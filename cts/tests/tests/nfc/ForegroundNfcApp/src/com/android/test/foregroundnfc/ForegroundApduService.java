/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.test.foregroundnfc;

import android.content.ComponentName;
import android.content.Intent;
import android.nfc.cardemulation.HostApduService;
import android.nfc.cardemulation.PollingFrame;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.List;

public class ForegroundApduService extends HostApduService {

    @Override
    public void processPollingFrames(List<PollingFrame> frames) {
        ArrayList<PollingFrame> framesArrayList = new ArrayList<>(frames);
        final Intent intent = new Intent();
        intent.setAction("com.cts.PollingLoopFired");
        intent.putExtra("class_name", this.getClass().getName());
        intent.putParcelableArrayListExtra("frames", framesArrayList);
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        intent.setComponent(
                new ComponentName("android.nfc.cts",
                        "android.nfc.cts.PollingLoopBroadcastReceiver"));
        sendBroadcast(intent);
    }

    @Override
    public byte[] processCommandApdu(byte[] commandApdu, Bundle extras) {
        return new byte[0];
    }

    @Override
    public void onDeactivated(int reason) {
        return;
    }
}
