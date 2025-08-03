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

package com.android.cts.voiceinteraction.host;

import android.compat.cts.CompatChangeGatingTestCase;

import com.android.tradefed.device.DeviceNotAvailableException;

import com.google.common.collect.ImmutableSet;

import java.util.Set;

public class VoiceInteractionCompatTests extends CompatChangeGatingTestCase {

    private static final long ENFORCE_HOTWORD_PHRASE_ID = 215066299L;
    private static final String TEST_APP_PACKAGE_NAME =
            "com.android.cts.voiceinteraction";
    private static final String VOICE_INTERACTION_SERVICES_PACKAGE_NAME =
            "android.voiceinteraction.service";
    private static final Set<Long> ENFORCE_HOTWORD_PHRASE_ID_CHANGES_SET = ImmutableSet.of(
            ENFORCE_HOTWORD_PHRASE_ID);

    public void testEnforceHotwordPhraseIdChangeEnabled() throws Exception {
        setCompatConfig(
                /* enabledChanges= */ ENFORCE_HOTWORD_PHRASE_ID_CHANGES_SET,
                /* disabledChanges= */ ImmutableSet.of(),
                VOICE_INTERACTION_SERVICES_PACKAGE_NAME);

        runDeviceCompatTestReported(TEST_APP_PACKAGE_NAME,
                ".VoiceInteractionCompatTests",
                "enforceHotwordPhraseIdEnabled",
                /* enabledChanges= */ ENFORCE_HOTWORD_PHRASE_ID_CHANGES_SET,
                /* disabledChanges= */ ImmutableSet.of(),
                /* reportedEnabledChanges= */ ImmutableSet.of(),
                /* reportedDisabledChanges= */ ImmutableSet.of());

        runDeviceCompatTestReported(TEST_APP_PACKAGE_NAME,
                ".VoiceInteractionCompatTests",
                "enforceHotwordPhraseIdEnabled_rejectNotMatchingPhraseId",
                /* enabledChanges= */ ENFORCE_HOTWORD_PHRASE_ID_CHANGES_SET,
                /* disabledChanges= */ ImmutableSet.of(),
                /* reportedEnabledChanges= */ ImmutableSet.of(),
                /* reportedDisabledChanges= */ ImmutableSet.of());

        runDeviceCompatTestReported(TEST_APP_PACKAGE_NAME,
                ".VoiceInteractionCompatTests",
                "enforceHotwordPhraseIdEnabled_rejectPhraseIdNotSet",
                /* enabledChanges= */ ENFORCE_HOTWORD_PHRASE_ID_CHANGES_SET,
                /* disabledChanges= */ ImmutableSet.of(),
                /* reportedEnabledChanges= */ ImmutableSet.of(),
                /* reportedDisabledChanges= */ ImmutableSet.of());

        runDeviceCompatTestReported(TEST_APP_PACKAGE_NAME,
                ".VoiceInteractionCompatTests",
                "enforceHotwordPhraseIdEnabled_acceptMatchingPhraseId",
                /* enabledChanges= */ ENFORCE_HOTWORD_PHRASE_ID_CHANGES_SET,
                /* disabledChanges= */ ImmutableSet.of(),
                /* reportedEnabledChanges= */ ImmutableSet.of(),
                /* reportedDisabledChanges= */ ImmutableSet.of());
    }

    public void testEnforceHotwordPhraseIdChangeDisabled() throws Exception {
        setCompatConfig(
                /*enabledChanges*/ ImmutableSet.of(),
                /*disabledChanges*/ ENFORCE_HOTWORD_PHRASE_ID_CHANGES_SET,
                VOICE_INTERACTION_SERVICES_PACKAGE_NAME);

        runDeviceCompatTestReported(TEST_APP_PACKAGE_NAME,
                ".VoiceInteractionCompatTests",
                "enforceHotwordPhraseIdDisabled",
                /* enabledChanges= */ ImmutableSet.of(),
                /* disabledChanges= */ ENFORCE_HOTWORD_PHRASE_ID_CHANGES_SET,
                /* reportedEnabledChanges= */ ImmutableSet.of(),
                /* reportedDisabledChanges= */ ImmutableSet.of());

        runDeviceCompatTestReported(TEST_APP_PACKAGE_NAME,
                ".VoiceInteractionCompatTests",
                "enforceHotwordPhraseIdDisabled_acceptNotMatchingPhraseId",
                /* enabledChanges= */ ImmutableSet.of(),
                /* disabledChanges= */ ENFORCE_HOTWORD_PHRASE_ID_CHANGES_SET,
                /* reportedEnabledChanges= */ ImmutableSet.of(),
                /* reportedDisabledChanges= */ ImmutableSet.of());

        runDeviceCompatTestReported(TEST_APP_PACKAGE_NAME,
                ".VoiceInteractionCompatTests",
                "enforceHotwordPhraseIdDisabled_acceptPhraseIdNotSet",
                /* enabledChanges= */ ImmutableSet.of(),
                /* disabledChanges= */ ENFORCE_HOTWORD_PHRASE_ID_CHANGES_SET,
                /* reportedEnabledChanges= */ ImmutableSet.of(),
                /* reportedDisabledChanges= */ ImmutableSet.of());

        runDeviceCompatTestReported(TEST_APP_PACKAGE_NAME,
                ".VoiceInteractionCompatTests",
                "enforceHotwordPhraseIdDisabled_acceptMatchingPhraseId",
                /* enabledChanges= */ ImmutableSet.of(),
                /* disabledChanges= */ ENFORCE_HOTWORD_PHRASE_ID_CHANGES_SET,
                /* reportedEnabledChanges= */ ImmutableSet.of(),
                /* reportedDisabledChanges= */ ImmutableSet.of());
    }

    /**
     * This test assumes that ENFORCE_HOTWORD_PHRASE_ID is disabled
     */
    // TODO(nambur): move default tests to device side only
    public void testDefaultPhraseIdEnforcementBehavior() throws DeviceNotAvailableException {
        runDeviceCompatTest(TEST_APP_PACKAGE_NAME,
                ".VoiceInteractionCompatTests",
                "enforceHotwordPhraseIdDisabled",
                /*enabledChanges*/ ImmutableSet.of(),
                /*disabledChanges*/ ImmutableSet.of());

        runDeviceCompatTest(TEST_APP_PACKAGE_NAME,
                ".VoiceInteractionCompatTests",
                "enforceHotwordPhraseIdDisabled_acceptNotMatchingPhraseId",
                /*enabledChanges*/ ImmutableSet.of(),
                /*disabledChanges*/ ImmutableSet.of());

        runDeviceCompatTest(TEST_APP_PACKAGE_NAME,
                ".VoiceInteractionCompatTests",
                "enforceHotwordPhraseIdDisabled_acceptPhraseIdNotSet",
                /*enabledChanges*/ ImmutableSet.of(),
                /*disabledChanges*/ ImmutableSet.of());

        runDeviceCompatTest(TEST_APP_PACKAGE_NAME,
                ".VoiceInteractionCompatTests",
                "enforceHotwordPhraseIdDisabled_acceptMatchingPhraseId",
                /*enabledChanges*/ ImmutableSet.of(),
                /*disabledChanges*/ ImmutableSet.of());
    }
}
