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

import static android.voiceinteraction.cts.testcore.Helper.MANAGE_VOICE_KEYPHRASES;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import android.media.voice.KeyphraseModelManager;
import android.service.voice.VoiceInteractionService;

import org.junit.rules.ExternalResource;

/** Rule which manages overriding the model enrollment database with a in-mem fake. */
public class VoiceInteractionServiceOverrideEnrollmentRule extends ExternalResource {

    /**
     * Create the rule.
     *
     * @param service - Service which the rule will override
     */
    public VoiceInteractionServiceOverrideEnrollmentRule(VoiceInteractionService service) {
        mService = service;
    }

    /**
     * Get the {@link KeyphraseModelManager} associated with the VIService, with the additional
     * guarantee it will enroll to a in-mem fake db.
     *
     * @return - model manager to enroll models into the fake db with.
     */
    public KeyphraseModelManager getModelManager() {
        return mKeyphraseModelManager;
    }

    @Override
    protected void before() throws Throwable {
        runWithShellPermissionIdentity(
                () -> {
                    mKeyphraseModelManager = mService.createKeyphraseModelManager();
                    mKeyphraseModelManager.setModelDatabaseForTestEnabled(true);
                }, MANAGE_VOICE_KEYPHRASES);
    }

    @Override
    protected void after() {
        runWithShellPermissionIdentity(
                () -> mKeyphraseModelManager.setModelDatabaseForTestEnabled(false),
                MANAGE_VOICE_KEYPHRASES);
        mKeyphraseModelManager = null;
    }

    private VoiceInteractionService mService = null;
    private KeyphraseModelManager mKeyphraseModelManager = null;
}
