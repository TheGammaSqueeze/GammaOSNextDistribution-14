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

package android.voiceinteraction.cts.testcore;

import android.content.Context;
import android.provider.Settings;
import android.voiceinteraction.cts.services.BaseVoiceInteractionService;

import com.android.compatibility.common.util.UserSettings;

import org.junit.rules.ExternalResource;

/**
 * A customized test rule that control the VoiceInteractionService connect and disconnect. The rule
 * will init a CountDownLatch before switching to test VoiceInteractionService. Waiting the latch
 * to make sure service is connected before testing.
 *
 * NOTE: the test VoiceInteractionService should extends {@link BaseVoiceInteractionService}, the
 * connection is controlled in this base class.
 */
public class VoiceInteractionServiceConnectedRule extends ExternalResource {

    private final Context mContext;
    private final String mTestVoiceInteractionService;
    private String mOriginalVoiceInteractionService;
    private final UserSettings mUserSettings;

    public VoiceInteractionServiceConnectedRule(Context context,
            String testVoiceInteractionService) {
        mContext = context;
        mTestVoiceInteractionService = testVoiceInteractionService;
        mUserSettings = new UserSettings(mContext);
    }

    @Override
    protected void before() throws Throwable {
        // To avoid onReady() is called before init connect latch, we should set the service
        // after init connect latch
        BaseVoiceInteractionService.initServiceConnectionLatches();
        mOriginalVoiceInteractionService = mUserSettings
                .get(Settings.Secure.VOICE_INTERACTION_SERVICE);
        setVoiceInteractionService(mTestVoiceInteractionService);
        BaseVoiceInteractionService.waitServiceConnect();
    }

    @Override
    protected void after() {
        // Restore to original VoiceInteractionService
        setVoiceInteractionService(mOriginalVoiceInteractionService);
        // Wait service onShutdown() called
        try {
            BaseVoiceInteractionService.waitServiceDisconnect();
            BaseVoiceInteractionService.resetStaticValues();
        } catch (InterruptedException e) {
            throw new AssertionError("waitServiceDisconnect error " + e);
        }
    }

    private void setVoiceInteractionService(String service) {
        mUserSettings.syncSet(Settings.Secure.VOICE_INTERACTION_SERVICE, service);
    }
}
