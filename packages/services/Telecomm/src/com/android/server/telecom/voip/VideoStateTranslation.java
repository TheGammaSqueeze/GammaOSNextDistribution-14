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

package com.android.server.telecom.voip;

import android.telecom.CallAttributes;
import android.telecom.Log;
import android.telecom.VideoProfile;

/**
 * This remapping class is needed because {@link VideoProfile} has more fine grain levels of video
 * states as apposed to Transactional video states (defined in  {@link CallAttributes.CallType}.
 * To be more specific, there are 3 video states (rx, tx, and bi-directional).
 * {@link CallAttributes.CallType} only has 2 states (audio and video).
 *
 * The reason why Transactional calls have fewer states is due to the fact that the framework is
 * only used by VoIP apps and Telecom only cares to know if the call is audio or video.
 *
 * Calls that are backed by a {@link android.telecom.ConnectionService} have the ability to be
 * managed calls (non-VoIP) and Dialer needs more fine grain video states to update the UI. Thus,
 * {@link VideoProfile} is used for {@link android.telecom.ConnectionService} backed calls.
 */
public class VideoStateTranslation {
    private static final String TAG = VideoStateTranslation.class.getSimpleName();

    /**
     * Client --> Telecom
     * This should be used when the client application is signaling they are changing the video
     * state.
     */
    public static int TransactionalVideoStateToVideoProfileState(int transactionalVideo) {
        if (transactionalVideo == CallAttributes.AUDIO_CALL) {
            Log.i(TAG, "%s --> VideoProfile.STATE_AUDIO_ONLY",
                    TransactionalVideoState_toString(transactionalVideo));
            return VideoProfile.STATE_AUDIO_ONLY;
        } else {
            Log.i(TAG, "%s --> VideoProfile.STATE_BIDIRECTIONAL",
                    TransactionalVideoState_toString(transactionalVideo));
            return VideoProfile.STATE_BIDIRECTIONAL;
        }
    }

    /**
     * Telecom --> Client
     * This should be used when Telecom is informing the client of a video state change.
     */
    public static int VideoProfileStateToTransactionalVideoState(int videoProfileState) {
        if (videoProfileState == VideoProfile.STATE_AUDIO_ONLY) {
            Log.i(TAG, "%s --> CallAttributes.AUDIO_CALL",
                    VideoProfileState_toString(videoProfileState));
            return CallAttributes.AUDIO_CALL;
        } else {
            Log.i(TAG, "%s --> CallAttributes.VIDEO_CALL",
                    VideoProfileState_toString(videoProfileState));
            return CallAttributes.VIDEO_CALL;
        }
    }

    private static String TransactionalVideoState_toString(int transactionalVideoState) {
        if (transactionalVideoState == CallAttributes.AUDIO_CALL) {
            return "CallAttributes.AUDIO_CALL";
        } else {
            return "CallAttributes.VIDEO_CALL";
        }
    }

    private static String VideoProfileState_toString(int videoProfileState) {
        switch (videoProfileState) {
            case VideoProfile.STATE_BIDIRECTIONAL -> {
                return "VideoProfile.STATE_BIDIRECTIONAL";
            }
            case VideoProfile.STATE_RX_ENABLED -> {
                return "VideoProfile.STATE_RX_ENABLED";
            }
            case VideoProfile.STATE_TX_ENABLED -> {
                return "VideoProfile.STATE_TX_ENABLED";
            }
        }
        return "VideoProfile.STATE_AUDIO_ONLY";
    }
}
