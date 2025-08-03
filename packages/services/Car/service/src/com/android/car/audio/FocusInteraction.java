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

import static android.media.AudioManager.AUDIOFOCUS_REQUEST_DELAYED;
import static android.media.AudioManager.AUDIOFOCUS_REQUEST_FAILED;
import static android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED;

import static com.android.car.audio.CarAudioContext.ALARM;
import static com.android.car.audio.CarAudioContext.ANNOUNCEMENT;
import static com.android.car.audio.CarAudioContext.CALL;
import static com.android.car.audio.CarAudioContext.CALL_RING;
import static com.android.car.audio.CarAudioContext.EMERGENCY;
import static com.android.car.audio.CarAudioContext.MUSIC;
import static com.android.car.audio.CarAudioContext.NAVIGATION;
import static com.android.car.audio.CarAudioContext.NOTIFICATION;
import static com.android.car.audio.CarAudioContext.SAFETY;
import static com.android.car.audio.CarAudioContext.SYSTEM_SOUND;
import static com.android.car.audio.CarAudioContext.VEHICLE_STATUS;
import static com.android.car.audio.CarAudioContext.VOICE_COMMAND;
import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;

import android.annotation.UserIdInt;
import android.car.builtin.os.UserManagerHelper;
import android.car.builtin.util.Slogf;
import android.car.settings.CarSettings;
import android.database.ContentObserver;
import android.media.AudioAttributes;
import android.net.Uri;
import android.provider.Settings;
import android.util.SparseArray;
import android.util.proto.ProtoOutputStream;

import com.android.car.CarLog;
import com.android.car.audio.CarAudioContext.AudioContext;
import com.android.car.audio.CarAudioDumpProto.CarAudioZoneFocusProto.CarAudioFocusProto;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import java.util.List;
import java.util.Objects;

/**
 * FocusInteraction is responsible for evaluating how incoming focus requests should be handled
 * based on pre-defined interaction behaviors for each incoming {@link AudioContext} in relation to
 * a {@link AudioContext} that is currently holding focus.
 */
final class FocusInteraction {

    private static final String TAG = CarLog.tagFor(FocusInteraction.class);

    static final Uri AUDIO_FOCUS_NAVIGATION_REJECTED_DURING_CALL_URI =
            Settings.Secure.getUriFor(
                    CarSettings.Secure.KEY_AUDIO_FOCUS_NAVIGATION_REJECTED_DURING_CALL);

    // Values for the internal interaction matrix we use to make focus decisions
    @VisibleForTesting
    static final int INTERACTION_REJECT = 0; // Focus not granted
    @VisibleForTesting
    static final int INTERACTION_EXCLUSIVE = 1; // Focus granted, others loose focus
    @VisibleForTesting
    static final int INTERACTION_CONCURRENT = 2; // Focus granted, others keep focus

    private static final SparseArray<SparseArray<Integer>> INTERACTION_MATRIX =
            new SparseArray<>(/* initialCapacity= */ 13);

    static {
        // Each Row represents CarAudioContext of current focus holder
        // Each Column represents CarAudioContext of incoming request (labels along the right)
        // Cell value is one of INTERACTION_REJECT, INTERACTION_EXCLUSIVE,
        // or INTERACTION_CONCURRENT

        // Focus holder: INVALID
        INTERACTION_MATRIX.append(/* INVALID= */ 0, new SparseArray() {
            {
                append(/* INVALID= */ 0, INTERACTION_REJECT); // INVALID
                append(MUSIC, INTERACTION_REJECT); // MUSIC
                append(NAVIGATION, INTERACTION_REJECT); // NAVIGATION
                append(VOICE_COMMAND, INTERACTION_REJECT); // VOICE_COMMAND
                append(CALL_RING, INTERACTION_REJECT); // CALL_RING
                append(CALL, INTERACTION_REJECT); // CALL
                append(ALARM, INTERACTION_REJECT); // ALARM
                append(NOTIFICATION, INTERACTION_REJECT); // NOTIFICATION
                append(SYSTEM_SOUND, INTERACTION_REJECT); // SYSTEM_SOUND,
                append(EMERGENCY, INTERACTION_EXCLUSIVE); // EMERGENCY
                append(SAFETY, INTERACTION_EXCLUSIVE); // SAFETY
                append(VEHICLE_STATUS, INTERACTION_REJECT); // VEHICLE_STATUS
                append(ANNOUNCEMENT, INTERACTION_REJECT); // ANNOUNCEMENT
            }
        });
        // Focus holder: MUSIC
        INTERACTION_MATRIX.append(MUSIC, new SparseArray() {
            {
                append(/* INVALID= */ 0, INTERACTION_REJECT); // INVALID
                append(MUSIC, INTERACTION_EXCLUSIVE); // MUSIC
                append(NAVIGATION, INTERACTION_CONCURRENT); // NAVIGATION
                append(VOICE_COMMAND, INTERACTION_EXCLUSIVE); // VOICE_COMMAND
                append(CALL_RING, INTERACTION_EXCLUSIVE); // CALL_RING
                append(CALL, INTERACTION_EXCLUSIVE); // CALL
                append(ALARM, INTERACTION_EXCLUSIVE); // ALARM
                append(NOTIFICATION, INTERACTION_CONCURRENT); // NOTIFICATION
                append(SYSTEM_SOUND, INTERACTION_CONCURRENT); // SYSTEM_SOUND,
                append(EMERGENCY, INTERACTION_EXCLUSIVE); // EMERGENCY
                append(SAFETY, INTERACTION_CONCURRENT); // SAFETY
                append(VEHICLE_STATUS, INTERACTION_CONCURRENT); // VEHICLE_STATUS
                append(ANNOUNCEMENT, INTERACTION_EXCLUSIVE); // ANNOUNCEMENT
            }
        });
        // Focus holder: NAVIGATION
        INTERACTION_MATRIX.append(NAVIGATION, new SparseArray() {
            {
                append(/* INVALID= */ 0, INTERACTION_REJECT); // INVALID
                append(MUSIC, INTERACTION_CONCURRENT); // MUSIC
                append(NAVIGATION, INTERACTION_CONCURRENT); // NAVIGATION
                append(VOICE_COMMAND, INTERACTION_EXCLUSIVE); // VOICE_COMMAND
                append(CALL_RING, INTERACTION_CONCURRENT); // CALL_RING
                append(CALL, INTERACTION_EXCLUSIVE); // CALL
                append(ALARM, INTERACTION_CONCURRENT); // ALARM
                append(NOTIFICATION, INTERACTION_CONCURRENT); // NOTIFICATION
                append(SYSTEM_SOUND, INTERACTION_CONCURRENT); // SYSTEM_SOUND,
                append(EMERGENCY, INTERACTION_EXCLUSIVE); // EMERGENCY
                append(SAFETY, INTERACTION_CONCURRENT); // SAFETY
                append(VEHICLE_STATUS, INTERACTION_CONCURRENT); // VEHICLE_STATUS
                append(ANNOUNCEMENT, INTERACTION_CONCURRENT); // ANNOUNCEMENT
            }
        });
        // Focus holder: VOICE_COMMAND
        INTERACTION_MATRIX.append(VOICE_COMMAND, new SparseArray() {
            {
                append(/* INVALID= */ 0, INTERACTION_REJECT); // INVALID
                append(MUSIC, INTERACTION_CONCURRENT); // MUSIC
                append(NAVIGATION, INTERACTION_REJECT); // NAVIGATION
                append(VOICE_COMMAND, INTERACTION_CONCURRENT); // VOICE_COMMAND
                append(CALL_RING, INTERACTION_EXCLUSIVE); // CALL_RING
                append(CALL, INTERACTION_EXCLUSIVE); // CALL
                append(ALARM, INTERACTION_REJECT); // ALARM
                append(NOTIFICATION, INTERACTION_REJECT); // NOTIFICATION
                append(SYSTEM_SOUND, INTERACTION_REJECT); // SYSTEM_SOUND,
                append(EMERGENCY, INTERACTION_EXCLUSIVE); // EMERGENCY
                append(SAFETY, INTERACTION_CONCURRENT); // SAFETY
                append(VEHICLE_STATUS, INTERACTION_CONCURRENT); // VEHICLE_STATUS
                append(ANNOUNCEMENT, INTERACTION_REJECT); // ANNOUNCEMENT
            }
        });
        // Focus holder: CALL_RING
        INTERACTION_MATRIX.append(CALL_RING, new SparseArray() {
            {
                append(/* INVALID= */ 0, INTERACTION_REJECT); // INVALID
                append(MUSIC, INTERACTION_REJECT); // MUSIC
                append(NAVIGATION, INTERACTION_CONCURRENT); // NAVIGATION
                append(VOICE_COMMAND, INTERACTION_CONCURRENT); // VOICE_COMMAND
                append(CALL_RING, INTERACTION_CONCURRENT); // CALL_RING
                append(CALL, INTERACTION_CONCURRENT); // CALL
                append(ALARM, INTERACTION_REJECT); // ALARM
                append(NOTIFICATION, INTERACTION_REJECT); // NOTIFICATION
                append(SYSTEM_SOUND, INTERACTION_CONCURRENT); // SYSTEM_SOUND,
                append(EMERGENCY, INTERACTION_EXCLUSIVE); // EMERGENCY
                append(SAFETY, INTERACTION_CONCURRENT); // SAFETY
                append(VEHICLE_STATUS, INTERACTION_CONCURRENT); // VEHICLE_STATUS
                append(ANNOUNCEMENT, INTERACTION_REJECT); // ANNOUNCEMENT
            }
        });
        // Focus holder: CALL
        INTERACTION_MATRIX.append(CALL, new SparseArray() {
            {
                append(/* INVALID= */ 0, INTERACTION_REJECT); // INVALID
                append(MUSIC, INTERACTION_REJECT); // MUSIC
                append(NAVIGATION, INTERACTION_CONCURRENT); // NAVIGATION
                append(VOICE_COMMAND, INTERACTION_REJECT); // VOICE_COMMAND
                append(CALL_RING, INTERACTION_CONCURRENT); // CALL_RING
                append(CALL, INTERACTION_CONCURRENT); // CALL
                append(ALARM, INTERACTION_CONCURRENT); // ALARM
                append(NOTIFICATION, INTERACTION_CONCURRENT); // NOTIFICATION
                append(SYSTEM_SOUND, INTERACTION_REJECT); // SYSTEM_SOUND,
                append(EMERGENCY, INTERACTION_CONCURRENT); // EMERGENCY
                append(SAFETY, INTERACTION_CONCURRENT); // SAFETY
                append(VEHICLE_STATUS, INTERACTION_CONCURRENT); // VEHICLE_STATUS
                append(ANNOUNCEMENT, INTERACTION_REJECT); // ANNOUNCEMENT
            }
        });
        // Focus holder: ALARM
        INTERACTION_MATRIX.append(ALARM, new SparseArray() {
            {
                append(/* INVALID= */ 0, INTERACTION_REJECT); // INVALID
                append(MUSIC, INTERACTION_CONCURRENT); // MUSIC
                append(NAVIGATION, INTERACTION_CONCURRENT); // NAVIGATION
                append(VOICE_COMMAND, INTERACTION_EXCLUSIVE); // VOICE_COMMAND
                append(CALL_RING, INTERACTION_EXCLUSIVE); // CALL_RING
                append(CALL, INTERACTION_EXCLUSIVE); // CALL
                append(ALARM, INTERACTION_CONCURRENT); // ALARM
                append(NOTIFICATION, INTERACTION_CONCURRENT); // NOTIFICATION
                append(SYSTEM_SOUND, INTERACTION_CONCURRENT); // SYSTEM_SOUND,
                append(EMERGENCY, INTERACTION_EXCLUSIVE); // EMERGENCY
                append(SAFETY, INTERACTION_CONCURRENT); // SAFETY
                append(VEHICLE_STATUS, INTERACTION_CONCURRENT); // VEHICLE_STATUS
                append(ANNOUNCEMENT, INTERACTION_REJECT); // ANNOUNCEMENT
            }
        });
        // Focus holder: NOTIFICATION
        INTERACTION_MATRIX.append(NOTIFICATION, new SparseArray() {
            {
                append(/* INVALID= */ 0, INTERACTION_REJECT); // INVALID
                append(MUSIC, INTERACTION_CONCURRENT); // MUSIC
                append(NAVIGATION, INTERACTION_CONCURRENT); // NAVIGATION
                append(VOICE_COMMAND, INTERACTION_EXCLUSIVE); // VOICE_COMMAND
                append(CALL_RING, INTERACTION_EXCLUSIVE); // CALL_RING
                append(CALL, INTERACTION_EXCLUSIVE); // CALL
                append(ALARM, INTERACTION_CONCURRENT); // ALARM
                append(NOTIFICATION, INTERACTION_CONCURRENT); // NOTIFICATION
                append(SYSTEM_SOUND, INTERACTION_CONCURRENT); // SYSTEM_SOUND,
                append(EMERGENCY, INTERACTION_EXCLUSIVE); // EMERGENCY
                append(SAFETY, INTERACTION_CONCURRENT); // SAFETY
                append(VEHICLE_STATUS, INTERACTION_CONCURRENT); // VEHICLE_STATUS
                append(ANNOUNCEMENT, INTERACTION_CONCURRENT); // ANNOUNCEMENT
            }
        });
        // Focus holder: SYSTEM_SOUND
        INTERACTION_MATRIX.append(SYSTEM_SOUND, new SparseArray() {
            {
                append(/* INVALID= */ 0, INTERACTION_REJECT); // INVALID
                append(MUSIC, INTERACTION_CONCURRENT); // MUSIC
                append(NAVIGATION, INTERACTION_CONCURRENT); // NAVIGATION
                append(VOICE_COMMAND, INTERACTION_EXCLUSIVE); // VOICE_COMMAND
                append(CALL_RING, INTERACTION_EXCLUSIVE); // CALL_RING
                append(CALL, INTERACTION_EXCLUSIVE); // CALL
                append(ALARM, INTERACTION_CONCURRENT); // ALARM
                append(NOTIFICATION, INTERACTION_CONCURRENT); // NOTIFICATION
                append(SYSTEM_SOUND, INTERACTION_CONCURRENT); // SYSTEM_SOUND,
                append(EMERGENCY, INTERACTION_EXCLUSIVE); // EMERGENCY
                append(SAFETY, INTERACTION_CONCURRENT); // SAFETY
                append(VEHICLE_STATUS, INTERACTION_CONCURRENT); // VEHICLE_STATUS
                append(ANNOUNCEMENT, INTERACTION_CONCURRENT); // ANNOUNCEMENT
            }
        });
        // Focus holder: EMERGENCY
        INTERACTION_MATRIX.append(EMERGENCY, new SparseArray() {
            {
                append(/* INVALID= */ 0, INTERACTION_REJECT); // INVALID
                append(MUSIC, INTERACTION_REJECT); // MUSIC
                append(NAVIGATION, INTERACTION_REJECT); // NAVIGATION
                append(VOICE_COMMAND, INTERACTION_REJECT); // VOICE_COMMAND
                append(CALL_RING, INTERACTION_REJECT); // CALL_RING
                append(CALL, INTERACTION_CONCURRENT); // CALL
                append(ALARM, INTERACTION_REJECT); // ALARM
                append(NOTIFICATION, INTERACTION_REJECT); // NOTIFICATION
                append(SYSTEM_SOUND, INTERACTION_REJECT); // SYSTEM_SOUND,
                append(EMERGENCY, INTERACTION_CONCURRENT); // EMERGENCY
                append(SAFETY, INTERACTION_CONCURRENT); // SAFETY
                append(VEHICLE_STATUS, INTERACTION_REJECT); // VEHICLE_STATUS
                append(ANNOUNCEMENT, INTERACTION_REJECT); // ANNOUNCEMENT
            }
        });
        // Focus holder: SAFETY
        INTERACTION_MATRIX.append(SAFETY, new SparseArray() {
            {
                append(/* INVALID= */ 0, INTERACTION_REJECT); // INVALID
                append(MUSIC, INTERACTION_CONCURRENT); // MUSIC
                append(NAVIGATION, INTERACTION_CONCURRENT); // NAVIGATION
                append(VOICE_COMMAND, INTERACTION_CONCURRENT); // VOICE_COMMAND
                append(CALL_RING, INTERACTION_CONCURRENT); // CALL_RING
                append(CALL, INTERACTION_CONCURRENT); // CALL
                append(ALARM, INTERACTION_CONCURRENT); // ALARM
                append(NOTIFICATION, INTERACTION_CONCURRENT); // NOTIFICATION
                append(SYSTEM_SOUND, INTERACTION_CONCURRENT); // SYSTEM_SOUND,
                append(EMERGENCY, INTERACTION_CONCURRENT); // EMERGENCY
                append(SAFETY, INTERACTION_CONCURRENT); // SAFETY
                append(VEHICLE_STATUS, INTERACTION_CONCURRENT); // VEHICLE_STATUS
                append(ANNOUNCEMENT, INTERACTION_CONCURRENT); // ANNOUNCEMENT
            }
        });
        // Focus holder: VEHICLE_STATUS
        INTERACTION_MATRIX.append(VEHICLE_STATUS, new SparseArray() {
            {
                append(/* INVALID= */ 0, INTERACTION_REJECT); // INVALID
                append(MUSIC, INTERACTION_CONCURRENT); // MUSIC
                append(NAVIGATION, INTERACTION_CONCURRENT); // NAVIGATION
                append(VOICE_COMMAND, INTERACTION_CONCURRENT); // VOICE_COMMAND
                append(CALL_RING, INTERACTION_CONCURRENT); // CALL_RING
                append(CALL, INTERACTION_CONCURRENT); // CALL
                append(ALARM, INTERACTION_CONCURRENT); // ALARM
                append(NOTIFICATION, INTERACTION_CONCURRENT); // NOTIFICATION
                append(SYSTEM_SOUND, INTERACTION_CONCURRENT); // SYSTEM_SOUND,
                append(EMERGENCY, INTERACTION_EXCLUSIVE); // EMERGENCY
                append(SAFETY, INTERACTION_CONCURRENT); // SAFETY
                append(VEHICLE_STATUS, INTERACTION_CONCURRENT); // VEHICLE_STATUS
                append(ANNOUNCEMENT, INTERACTION_CONCURRENT); // ANNOUNCEMENT
            }
        });
        // Focus holder: ANNOUNCEMENT
        INTERACTION_MATRIX.append(ANNOUNCEMENT, new SparseArray() {
            {
                append(/* INVALID= */ 0, INTERACTION_REJECT); // INVALID
                append(MUSIC, INTERACTION_EXCLUSIVE); // MUSIC
                append(NAVIGATION, INTERACTION_CONCURRENT); // NAVIGATION
                append(VOICE_COMMAND, INTERACTION_EXCLUSIVE); // VOICE_COMMAND
                append(CALL_RING, INTERACTION_EXCLUSIVE); // CALL_RING
                append(CALL, INTERACTION_EXCLUSIVE); // CALL
                append(ALARM, INTERACTION_EXCLUSIVE); // ALARM
                append(NOTIFICATION, INTERACTION_CONCURRENT); // NOTIFICATION
                append(SYSTEM_SOUND, INTERACTION_CONCURRENT); // SYSTEM_SOUND,
                append(EMERGENCY, INTERACTION_EXCLUSIVE); // EMERGENCY
                append(SAFETY, INTERACTION_CONCURRENT); // SAFETY
                append(VEHICLE_STATUS, INTERACTION_CONCURRENT); // VEHICLE_STATUS
                append(ANNOUNCEMENT, INTERACTION_EXCLUSIVE); // ANNOUNCEMENT
            }
        });
    }

    private final Object mLock = new Object();

    @GuardedBy("mLock")
    private final SparseArray<SparseArray<Integer>> mInteractionMatrix;

    private ContentObserver mContentObserver;

    private final CarAudioSettings mCarAudioFocusSettings;

    private final ContentObserverFactory mContentObserverFactory;
    private int mUserId;

    /**
     * Constructs a focus interaction instance.
     */
    FocusInteraction(CarAudioSettings carAudioSettings,
            ContentObserverFactory contentObserverFactory) {
        mCarAudioFocusSettings = Objects.requireNonNull(carAudioSettings,
                "Car Audio Settings can not be null.");
        mContentObserverFactory = Objects.requireNonNull(contentObserverFactory,
                "Content Observer Factory can not be null.");
        mInteractionMatrix = INTERACTION_MATRIX.clone();
    }

    private void navigationOnCallSettingChanged() {
        synchronized (mLock) {
            if (mUserId != UserManagerHelper.USER_NULL) {
                setRejectNavigationOnCallLocked(isRejectNavigationOnCallEnabledInSettings(mUserId));
            }
        }
    }

    @GuardedBy("mLock")
    public void setRejectNavigationOnCallLocked(boolean navigationRejectedWithCall) {
        int callContext = CarAudioContext.getLegacyContextForUsage(
                        AudioAttributes.USAGE_VOICE_COMMUNICATION);
        int navContext = CarAudioContext.getLegacyContextForUsage(
                        AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE);
        mInteractionMatrix.get(callContext).put(navContext,
                navigationRejectedWithCall ? INTERACTION_REJECT : INTERACTION_CONCURRENT);
    }

    public boolean isRejectNavigationOnCallEnabled() {
        int callContext = CarAudioContext.getLegacyContextForUsage(
                AudioAttributes.USAGE_VOICE_COMMUNICATION);
        int navContext = CarAudioContext.getLegacyContextForUsage(
                AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE);
        synchronized (mLock) {
            return mInteractionMatrix.get(callContext).get(navContext) == INTERACTION_REJECT;
        }
    }

    /**
     * Evaluates interaction between incoming focus audio attribute usage and the current focus
     * request based on interaction matrix.
     *
     * <p><b>Note</b> In addition to returning the request results
     * for the incoming request based on this interaction, this method also adds the current {@code
     * focusHolder} to the {@code focusLosers} list when appropriate.
     *
     * @param requestedUsage        Audio attribute usage of the incoming request
     * @param focusHolder           {@link FocusEntry} for current focus holder
     * @param allowDucking          Whether ducking is allowed
     * @param allowsDelayedFocus    Whether delayed focus is allowed
     * @param focusLosers           Mutable array to add focusHolder to if it should lose focus
     * @return result of focus interaction, can be any of
     *      {@link android.media.AudioManager#AUDIOFOCUS_REQUEST_DELAYED},
     *      {@link android.media.AudioManager#AUDIOFOCUS_REQUEST_FAILED}, or
     *      {@link android.media.AudioManager#AUDIOFOCUS_REQUEST_GRANTED}
     */
    int evaluateRequest(int requestedUsage, FocusEntry focusHolder, boolean allowDucking,
            boolean allowsDelayedFocus, List<FocusEntry> focusLosers) {
        int holderUsage = focusHolder.getAudioFocusInfo().getAttributes().getSystemUsage();

        synchronized (mLock) {
            int focusDecision = getFocusInteractionLocked(requestedUsage, holderUsage);

            switch (focusDecision) {
                case INTERACTION_REJECT:
                    if (allowsDelayedFocus) {
                        return AUDIOFOCUS_REQUEST_DELAYED;
                    }
                    return AUDIOFOCUS_REQUEST_FAILED;
                case INTERACTION_EXCLUSIVE:
                    focusLosers.add(focusHolder);
                    return AUDIOFOCUS_REQUEST_GRANTED;
                case INTERACTION_CONCURRENT:
                    // If ducking isn't allowed by the focus requester, then everybody else
                    // must get a LOSS.
                    // If a focus holder has set the AUDIOFOCUS_FLAG_PAUSES_ON_DUCKABLE_LOSS flag,
                    // they must get a LOSS message even if ducking would otherwise be allowed.
                    // If a focus holder holds the RECEIVE_CAR_AUDIO_DUCKING_EVENTS permission,
                    // they must receive all audio focus losses.
                    if (!allowDucking
                            || focusHolder.wantsPauseInsteadOfDucking()
                            || focusHolder.receivesDuckEvents()) {
                        focusLosers.add(focusHolder);
                    }
                    return AUDIOFOCUS_REQUEST_GRANTED;
                default:
                    Slogf.e(TAG, "Unsupported CarAudioContext %d - rejecting request",
                            focusDecision);
                    return AUDIOFOCUS_REQUEST_FAILED;
            }
        }
    }

    @GuardedBy("mLock")
    private int getFocusInteractionLocked(int requestedUsage, int holderUsage) {
        int requestedContext = CarAudioContext.getLegacyContextForUsage(requestedUsage);
        int holderContext = CarAudioContext.getLegacyContextForUsage(holderUsage);
        SparseArray<Integer> holderRow = mInteractionMatrix.get(holderContext);
        return holderRow.get(requestedContext);
    }

    /**
     * Sets userId for interaction focus settings
     */
    void setUserIdForSettings(@UserIdInt int userId) {
        synchronized (mLock) {
            if (mContentObserver != null) {
                mCarAudioFocusSettings.getContentResolverForUser(mUserId)
                        .unregisterContentObserver(mContentObserver);
                mContentObserver = null;
            }
            mUserId = userId;
            if (mUserId == UserManagerHelper.USER_NULL) {
                setRejectNavigationOnCallLocked(false);
                return;
            }
            mContentObserver = mContentObserverFactory.createObserver(
                    () -> navigationOnCallSettingChanged());
            mCarAudioFocusSettings.getContentResolverForUser(mUserId)
                    .registerContentObserver(AUDIO_FOCUS_NAVIGATION_REJECTED_DURING_CALL_URI,
                            /* notifyForDescendants= */false, mContentObserver);
            setRejectNavigationOnCallLocked(isRejectNavigationOnCallEnabledInSettings(mUserId));
        }
    }

    private boolean isRejectNavigationOnCallEnabledInSettings(@UserIdInt int userId) {
        return mCarAudioFocusSettings.isRejectNavigationOnCallEnabledInSettings(userId);
    }

    @VisibleForTesting
    SparseArray<SparseArray<Integer>> getInteractionMatrix() {
        synchronized (mLock) {
            return mInteractionMatrix.clone();
        }
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dump(IndentingPrintWriter writer) {
        boolean rejectNavigationOnCall = isRejectNavigationOnCallEnabled();
        writer.printf("Reject Navigation on Call: %b\n", rejectNavigationOnCall);
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dumpProto(ProtoOutputStream proto) {
        long focusInteractionToken = proto.start(CarAudioFocusProto.FOCUS_INTERACTION);
        boolean rejectNavigationOnCall = isRejectNavigationOnCallEnabled();
        proto.write(CarAudioFocusProto.FocusInteractionProto.REJECT_NAVIGATION_ON_CALL,
                rejectNavigationOnCall);
        proto.end(focusInteractionToken);
    }
}
