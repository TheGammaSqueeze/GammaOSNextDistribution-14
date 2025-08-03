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

package android.voiceinteraction.cts.testcore;

import android.content.Context;
import android.service.voice.VoiceInteractionService;
import android.util.Log;
import android.voiceinteraction.cts.services.BaseVoiceInteractionService;

/**
 * Same as {@link VoiceInteractionServiceConnectedRule}, but suppress throwables so it can form a
 * valid ClassRule.
 */
public class VoiceInteractionServiceConnectedClassRule
        extends VoiceInteractionServiceConnectedRule {

    public VoiceInteractionServiceConnectedClassRule(
            Context context, String testVoiceInteractionService) {
        super(context, testVoiceInteractionService);
    }

    public VoiceInteractionService getService() {
        return mService;
    }

    @Override
    protected void before() throws Throwable {
        try {
            super.before();
            mService = BaseVoiceInteractionService.getService();
        } catch (Throwable e) {
            // To make this compatible with @ClassRule, we must not throw
            Log.e("VoiceInteractionServiceConnectedRule", "Suppressed before Exception", e);
        }
    }

    @Override
    protected void after() {
        try {
            mService = null;
            super.after();
        } catch (Throwable e) {
            Log.e("VoiceInteractionServiceConnectedRule", "Suppressed after Exception", e);
        }
    }

    private VoiceInteractionService mService = null;
}
