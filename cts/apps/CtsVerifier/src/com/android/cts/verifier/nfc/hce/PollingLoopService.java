/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.cts.verifier.nfc.hce;

import android.content.ComponentName;
import android.content.Intent;
import android.nfc.cardemulation.PollingFrame;

import java.util.ArrayList;
import java.util.List;

public class PollingLoopService extends HceService {
    static final String TAG = "PollingLoopService";

    public static final String POLLING_FRAME_ACTION =
            "com.android.cts.verifier.nfc.hce.POLLING_FRAME_ACTION";
    public static final String POLLING_FRAME_EXTRA = "POLLING_FRAME_EXTRA";

    static final ComponentName COMPONENT = new ComponentName("com.android.cts.verifier",
            PollingLoopService.class.getName());

    public static final CommandApdu[] APDU_COMMAND_SEQUENCE = {
            HceUtils.buildSelectApdu(HceUtils.ACCESS_AID, true),
            HceUtils.buildCommandApdu("80CA01F000", true)
    };

    public static final String[] APDU_RESPOND_SEQUENCE = {
            "123456789000",
            "1481148114819000"
    };

    public PollingLoopService() {
        initialize(APDU_COMMAND_SEQUENCE, APDU_RESPOND_SEQUENCE);
    }

    @Override
    public ComponentName getComponent() {
        return COMPONENT;
    }

    @Override
    public void processPollingFrames(List<PollingFrame> frames) {
        Intent pollingFrameIntent = new Intent(POLLING_FRAME_ACTION);
        pollingFrameIntent.putExtra(HceUtils.EXTRA_COMPONENT, getComponent());
        pollingFrameIntent.putExtra(HceUtils.EXTRA_DURATION,
                System.currentTimeMillis() - mStartTime);
        pollingFrameIntent.putExtra(POLLING_FRAME_EXTRA, new ArrayList<PollingFrame>(frames));
        sendBroadcast(pollingFrameIntent);
    }
}
