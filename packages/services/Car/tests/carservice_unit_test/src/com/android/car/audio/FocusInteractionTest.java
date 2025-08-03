/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.car.audio;

import static android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE;
import static android.media.AudioAttributes.USAGE_MEDIA;
import static android.media.AudioAttributes.USAGE_VIRTUAL_SOURCE;
import static android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION;
import static android.media.AudioManager.AUDIOFOCUS_GAIN;

import static com.android.car.audio.CarAudioTestUtils.getInfo;
import static com.android.car.audio.ContentObserverFactory.ContentChangeCallback;
import static com.android.car.audio.FocusInteraction.AUDIO_FOCUS_NAVIGATION_REJECTED_DURING_CALL_URI;
import static com.android.car.audio.FocusInteraction.INTERACTION_CONCURRENT;
import static com.android.car.audio.FocusInteraction.INTERACTION_EXCLUSIVE;
import static com.android.car.audio.FocusInteraction.INTERACTION_REJECT;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.car.media.CarAudioManager;
import android.car.test.AbstractExpectableTestCase;
import android.content.ContentResolver;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.media.AudioAttributes;
import android.media.AudioFocusInfo;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.SparseArray;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public final class FocusInteractionTest extends AbstractExpectableTestCase {
    private static final int UNDEFINED_USAGE_VALUE = -10;
    private static final int TEST_USER_ID = 100;

    private static final CarAudioContext TEST_CAR_AUDIO_CONTEXT =
            new CarAudioContext(CarAudioContext.getAllContextsInfo(),
                    /* useCoreAudioRouting= */ false);

    @Mock
    private CarAudioSettings mMockCarAudioSettings;
    @Mock
    private ContentResolver mMockContentResolver;
    @Mock
    private ContentObserverFactory mMockContentObserverFactory;
    @Mock
    private Handler mMockHandler;
    @Rule
    public MockitoRule mMockitoRule = MockitoJUnit.rule();

    private ContentObserver mContentObserver;

    private final List<FocusEntry> mLosers = new ArrayList<>();

    private FocusInteraction mFocusInteraction;
    private PackageManager mMockPackageManager;

    @Before
    public void setUp() {
        when(mMockCarAudioSettings.getContentResolverForUser(TEST_USER_ID))
                .thenReturn(mMockContentResolver);
        doAnswer(invocation -> {
            ContentChangeCallback wrapper = (ContentChangeCallback) invocation.getArguments()[0];
            mContentObserver = new ContentObserver(mMockHandler) {
                @Override
                public void onChange(boolean selfChange, Uri uri) {
                    if (AUDIO_FOCUS_NAVIGATION_REJECTED_DURING_CALL_URI.equals(uri)) {
                        wrapper.onChange();
                    }
                }
            };
            return mContentObserver;
        }).when(mMockContentObserverFactory).createObserver(any());
        mFocusInteraction = new FocusInteraction(mMockCarAudioSettings,
                mMockContentObserverFactory);
        mMockPackageManager = mock(PackageManager.class);
        when(mMockPackageManager.checkPermission(anyString(), anyString()))
                .thenReturn(PackageManager.PERMISSION_GRANTED);
    }

    @Test
    public void constructor_withNullSettings_fails() {
        NullPointerException thrown =
                assertThrows(NullPointerException.class,
                        () -> new FocusInteraction(/* carAudioSettings= */ null,
                                mMockContentObserverFactory));

        expectWithMessage("Null settings exception")
                .that(thrown).hasMessageThat().contains("Settings");
    }

    @Test
    public void constructor_withNullObserverFactory_fails() {
        NullPointerException thrown =
                assertThrows(NullPointerException.class,
                        () -> new FocusInteraction(mMockCarAudioSettings,
                                /*  contentObserverFactory= */ null));

        expectWithMessage("Null observer factory exception")
                .that(thrown).hasMessageThat().contains("Content Observer Factory");
    }

    @Test
    public void getInteractionMatrix_returnsNByNMatrix() {
        // One extra for CarAudioContext.getInvalidContext()
        CarAudioContext carAudioContext =
                new CarAudioContext(CarAudioContext.getAllContextsInfo(),
                        /* useCoreAudioRouting= */ false);
        int n = carAudioContext.getAllContextsIds().size() + 1;

        SparseArray<SparseArray<Integer>> interactionMatrix =
                mFocusInteraction.getInteractionMatrix();

        expectWithMessage("Interaction matrix").that(interactionMatrix.size()).isEqualTo(n);
        for (int i = 0; i < n; i++) {
            expectWithMessage("Interaction matrix row %s", i)
                    .that(interactionMatrix.get(i).size()).isEqualTo(n);
        }
    }

    @Test
    public void getInteractionMatrix_hasValidInteractionValues() {
        List<Integer> supportedInteractions = Arrays.asList(INTERACTION_REJECT,
                INTERACTION_EXCLUSIVE, INTERACTION_CONCURRENT);

        SparseArray<SparseArray<Integer>> interactionMatrix =
                mFocusInteraction.getInteractionMatrix();
        for (int i = 0; i < interactionMatrix.size(); i++) {
            for (int j = 0; j < interactionMatrix.get(i).size(); j++) {
                expectWithMessage("Row %s column %s value %s", i, j,
                        interactionMatrix.get(i).get(j)).that(
                        interactionMatrix.get(i).get(j)).isIn(supportedInteractions);
            }
        }
    }

    @Test
    public void evaluateResult_forRejectPair_returnsFailed() {
        FocusEntry focusEntry = newMockFocusEntryWithUsage(USAGE_VIRTUAL_SOURCE);

        int result = mFocusInteraction.evaluateRequest(USAGE_VIRTUAL_SOURCE,
                focusEntry, /* allowDucking= */ false, /* allowsDelayedFocus= */ false, mLosers);

        expectWithMessage("Focus evaluation result for reject pair").that(result)
                .isEqualTo(AudioManager.AUDIOFOCUS_REQUEST_FAILED);
    }

    @Test
    public void evaluateResult_forCallAndNavigation_withNavigationNotRejected_returnsConcurrent() {
        when(mMockCarAudioSettings.isRejectNavigationOnCallEnabledInSettings(TEST_USER_ID))
                .thenReturn(false);

        mFocusInteraction.setUserIdForSettings(TEST_USER_ID);
        FocusEntry focusEntry = newMockFocusEntryWithUsage(USAGE_VOICE_COMMUNICATION);

        int result = mFocusInteraction.evaluateRequest(USAGE_ASSISTANCE_NAVIGATION_GUIDANCE,
                focusEntry, /* allowDucking= */ false, /* allowsDelayedFocus= */ false, mLosers);

        expectWithMessage("Focus interaction for call and navigation"
                + " while reject navigation is inactive")
                .that(result).isEqualTo(AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
    }

    @Test
    public void evaluateResult_forCallAndNavigation_withNavigationRejected_returnsConcurrent() {
        when(mMockCarAudioSettings.isRejectNavigationOnCallEnabledInSettings(TEST_USER_ID))
                .thenReturn(true);
        mFocusInteraction.setUserIdForSettings(TEST_USER_ID);
        FocusEntry focusEntry = newMockFocusEntryWithUsage(USAGE_VOICE_COMMUNICATION);

        int result = mFocusInteraction.evaluateRequest(USAGE_ASSISTANCE_NAVIGATION_GUIDANCE,
                focusEntry, /* allowDucking= */ false, /* allowsDelayedFocus= */ false, mLosers);

        expectWithMessage("Focus interaction for call and navigation"
                + " while reject navigation is active")
                .that(result).isEqualTo(AudioManager.AUDIOFOCUS_REQUEST_FAILED);
    }

    @Test
    public void evaluateResult_forRejectPair_doesNotAddToLosers() {
        FocusEntry focusEntry = newMockFocusEntryWithUsage(USAGE_VIRTUAL_SOURCE);

        mFocusInteraction.evaluateRequest(USAGE_VIRTUAL_SOURCE, focusEntry,
                        /* allowDucking= */ false, /* allowsDelayedFocus= */ false, mLosers);

        expectWithMessage("Audio focus loser for reject pair").that(mLosers).isEmpty();
    }

    @Test
    public void evaluateRequest_forExclusivePair_returnsGranted() {
        FocusEntry focusEntry = newMockFocusEntryWithUsage(USAGE_MEDIA);

        int result = mFocusInteraction.evaluateRequest(USAGE_MEDIA, focusEntry,
                /* allowDucking= */ false, /* allowsDelayedFocus= */ false, mLosers);

        expectWithMessage("Focus interaction for exclusive pair").that(result)
                .isEqualTo(AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
    }

    @Test
    public void evaluateRequest_forExclusivePair_addsEntryToLosers() {
        FocusEntry focusEntry = newMockFocusEntryWithUsage(USAGE_MEDIA);

        mFocusInteraction.evaluateRequest(USAGE_MEDIA, focusEntry, /* allowDucking= */ false,
                        /* allowsDelayedFocus= */ false, mLosers);

        expectWithMessage("Audio focus loser for exclusive pair").that(mLosers)
                .containsExactly(focusEntry);
    }

    @Test
    public void evaluateResult_forConcurrentPair_returnsGranted() {
        FocusEntry focusEntry = newMockFocusEntryWithUsage(USAGE_ASSISTANCE_NAVIGATION_GUIDANCE);

        int result = mFocusInteraction.evaluateRequest(USAGE_MEDIA, focusEntry,
                /* allowDucking= */ false, /* allowsDelayedFocus= */ false, mLosers);

        expectWithMessage("Audio focus interaction for concurrent pair").that(result)
                .isEqualTo(AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
    }

    @Test
    public void evaluateResult_forConcurrentPair_andNoDucking_addsToLosers() {
        FocusEntry focusEntry =
                newMockFocusEntryWithDuckingBehavior(/* pauseInsteadOfDucking= */ false,
                        /* receivesDuckingEvents= */ false);

        mFocusInteraction.evaluateRequest(USAGE_MEDIA, focusEntry, /* allowDucking= */ false,
                /* allowsDelayedFocus= */ false, mLosers);

        expectWithMessage("Audio focus loser for concurrent pair and no audio ducking")
                .that(mLosers).containsExactly(focusEntry);
    }

    @Test
    public void evaluateResult_forConcurrentPair_andWantsPauseInsteadOfDucking_addsToLosers() {
        FocusEntry focusEntry = newMockFocusEntryWithDuckingBehavior(
                /* pauseInsteadOfDucking= */ true, /* receivesDuckingEvents= */ false);

        mFocusInteraction.evaluateRequest(USAGE_MEDIA, focusEntry, /* allowDucking= */ true,
                /* allowsDelayedFocus= */ false, mLosers);

        expectWithMessage("Audio focus loser for concurrent pair and allows pause")
                .that(mLosers).containsExactly(focusEntry);
    }

    @Test
    public void evaluateResult_forConcurrentPair_andReceivesDuckEvents_addsToLosers() {
        FocusEntry focusEntry = newMockFocusEntryWithDuckingBehavior(
                /* pauseInsteadOfDucking= */ false, /* receivesDuckingEvents= */ true);

        mFocusInteraction.evaluateRequest(USAGE_MEDIA, focusEntry, /* allowDucking= */ true,
                /* allowsDelayedFocus= */ false, mLosers);

        expectWithMessage("Focus losers for concurrent pair and receives ducking event")
                .that(mLosers).containsExactly(focusEntry);
    }

    @Test
    public void evaluateResult_forUndefinedUsage_throws() {
        FocusEntry focusEntry = newMockFocusEntryWithUsage(USAGE_ASSISTANCE_NAVIGATION_GUIDANCE);

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> mFocusInteraction.evaluateRequest(UNDEFINED_USAGE_VALUE, focusEntry,
                        /* allowDucking= */ false, /* allowsDelayedFocus= */ false,
                        mLosers));

        expectWithMessage("Invalid usage exception").that(thrown).hasMessageThat()
                .contains("usage");
    }

    @Test
    public void evaluateRequest_forExclusivePair_withDelayedFocus_returnsGranted() {
        FocusEntry focusEntry = newMockFocusEntryWithUsage(USAGE_MEDIA);

        int result = mFocusInteraction.evaluateRequest(USAGE_MEDIA, focusEntry,
                /* allowDucking= */ false, /* allowsDelayedFocus= */ true, mLosers);

        expectWithMessage("Focus interaction for exclusive pair with delayed focus")
                .that(result).isEqualTo(AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
    }

    @Test
    public void evaluateRequest_forRejectPair_withDelayedFocus_returnsDelayed() {
        FocusEntry focusEntry = newMockFocusEntryWithUsage(USAGE_VOICE_COMMUNICATION);

        int result = mFocusInteraction.evaluateRequest(USAGE_MEDIA, focusEntry,
                /* allowDucking= */ false, /* allowsDelayedFocus= */ true, mLosers);

        expectWithMessage("Audio focus interaction for reject pair with delayed focus")
                .that(result).isEqualTo(AudioManager.AUDIOFOCUS_REQUEST_DELAYED);
    }

    @Test
    public void evaluateRequest_forRejectPair_withoutDelayedFocus_returnsReject() {
        FocusEntry focusEntry = newMockFocusEntryWithUsage(USAGE_VOICE_COMMUNICATION);

        int result = mFocusInteraction.evaluateRequest(USAGE_MEDIA, focusEntry,
                /* allowDucking= */ false, /* allowsDelayedFocus= */ false, mLosers);

        expectWithMessage("Audio focus interaction for reject pair without delayed focus")
                .that(result).isEqualTo(AudioManager.AUDIOFOCUS_REQUEST_FAILED);
    }

    @Test
    public void isRejectNavigationOnCallEnabled_isRejected() {
        when(mMockCarAudioSettings.isRejectNavigationOnCallEnabledInSettings(TEST_USER_ID))
                .thenReturn(true, false);

        mFocusInteraction.setUserIdForSettings(TEST_USER_ID);

        expectWithMessage("Initial Reject Navigation on Call Status")
                .that(mFocusInteraction.isRejectNavigationOnCallEnabled()).isTrue();
    }

    @Test
    public void onChange_forContentObserver_rejectedOnCallDisabled() {
        when(mMockCarAudioSettings.isRejectNavigationOnCallEnabledInSettings(TEST_USER_ID))
                .thenReturn(true, false);
        mFocusInteraction.setUserIdForSettings(TEST_USER_ID);

        mContentObserver.onChange(true, AUDIO_FOCUS_NAVIGATION_REJECTED_DURING_CALL_URI);

        expectWithMessage("Reject Navigation on Call Status after Update")
                .that(mFocusInteraction.isRejectNavigationOnCallEnabled()).isFalse();
    }

    private FocusEntry newMockFocusEntryWithUsage(int usage) {
        return newMockFocusEntryWithUsage(usage, /* pauseInsteadOfDucking= */ false,
                /* receivesDuckingEvents= */ false);
    }

    private FocusEntry newMockFocusEntryWithUsage(int usage, boolean pauseInsteadOfDucking,
            boolean receivesDuckingEvents) {
        AudioAttributes audioAttributes = getAudioAttributeFromUsage(usage, receivesDuckingEvents);
        AudioFocusInfo info = getInfo(audioAttributes, "client", AUDIOFOCUS_GAIN,
                /* acceptsDelayedFocus= */ false, pauseInsteadOfDucking, /* uid= */ 867530);
        return new FocusEntry(info, TEST_CAR_AUDIO_CONTEXT
                .getContextForAudioAttribute(audioAttributes),
                mMockPackageManager);
    }

    private static AudioAttributes getAudioAttributeFromUsage(int usage,
            boolean receivesDuckingEvents) {
        AudioAttributes.Builder builder = new AudioAttributes.Builder();
        if (AudioAttributes.isSystemUsage(usage)) {
            builder.setSystemUsage(usage);
        } else {
            builder.setUsage(usage);
        }
        Bundle bundle = new Bundle();
        bundle.putBoolean(CarAudioManager.AUDIOFOCUS_EXTRA_RECEIVE_DUCKING_EVENTS,
                receivesDuckingEvents);
        builder.addBundle(bundle);
        return builder.build();
    }

    private FocusEntry newMockFocusEntryWithDuckingBehavior(boolean pauseInsteadOfDucking,
            boolean receivesDuckingEvents) {
        return newMockFocusEntryWithUsage(USAGE_ASSISTANCE_NAVIGATION_GUIDANCE,
                pauseInsteadOfDucking, receivesDuckingEvents);
    }
}
