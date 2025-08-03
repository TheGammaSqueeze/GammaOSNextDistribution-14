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

package android.voiceinteraction.cts.services;

import static android.Manifest.permission.CAPTURE_AUDIO_HOTWORD;
import static android.Manifest.permission.MANAGE_HOTWORD_DETECTION;
import static android.Manifest.permission.RECEIVE_SANDBOX_TRIGGER_AUDIO;
import static android.Manifest.permission.RECORD_AUDIO;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import android.os.Handler;
import android.os.HandlerThread;
import android.service.voice.VoiceInteractionService;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * The basic {@link VoiceInteractionService} that doesn't contain a HotwordDetectionService.
 */
public class CtsMainVoiceInteractionService extends BaseVoiceInteractionService {

    private static final String TAG = "CtsMainVoiceInteractionService";

    private final Handler mHandler;

    private boolean mVoiceActivationPermissionEnabled;

    public CtsMainVoiceInteractionService() {
        HandlerThread handlerThread = new HandlerThread("CtsMainVoiceInteractionService");
        handlerThread.start();
        mHandler = Handler.createAsync(handlerThread.getLooper());
    }

    public void setVoiceActivationPermissionEnabled(boolean val) {
        mVoiceActivationPermissionEnabled = val;
    }

    /**
     * Create an AlwaysOnHotwordDetector. This is used to verify case if the VoiceInteractionService
     * doesn't define a HotwordDetectionService.
     */
    public void createAlwaysOnHotwordDetector() {
        mDetectorInitializedLatch = new CountDownLatch(1);
        List<String> requestedPermissions = new ArrayList<String>(
                Arrays.asList(MANAGE_HOTWORD_DETECTION, CAPTURE_AUDIO_HOTWORD, RECORD_AUDIO));

        // TODO(b/305787465): Remove this block and request RECEIVE_SANDBOX_TRIGGER_AUDIO directly
        //  once flag has been fully ramped up.
        if (mVoiceActivationPermissionEnabled) {
            Log.i(TAG, "Requesting voice activation permissions!");
            requestedPermissions.add(RECEIVE_SANDBOX_TRIGGER_AUDIO);
        }

        mHandler.post(() -> runWithShellPermissionIdentity(() -> {
            callCreateAlwaysOnHotwordDetector(mNoOpHotwordDetectorCallback,
                    /* useExecutor= */ false);
        }, requestedPermissions.toArray(new String[0])));
    }

    /**
     * Create a VisualQueryDetector. This is used to verify case if the VoiceInteractionService
     * doesn't define a VisualQueryDetector.
     */
    public void createVisualQueryDetector() {
        mDetectorInitializedLatch = new CountDownLatch(1);
        mHandler.post(() -> runWithShellPermissionIdentity(() -> {
            callCreateVisualQueryDetector(mNoOpVisualQueryDetectorCallback);
        }, MANAGE_HOTWORD_DETECTION));
    }
}
