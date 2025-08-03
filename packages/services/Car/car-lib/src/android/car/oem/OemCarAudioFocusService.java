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
package android.car.oem;

import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.media.AudioFocusInfo;

import java.util.List;

/*
 * OemCarAudioFocusServiceInterface would expose all the method from IOemCarAudioFocusService. It
 * should always be in sync with IOemCarAudioFocusService. Oem will implement
 * OemCarAudioFocusServiceInterface which would be used by OemCarAudioFocusService.
 */
/**
 * Interface for Audio focus for OEM Service.
 *
 * @hide
 */
@SystemApi
public interface OemCarAudioFocusService extends OemCarServiceComponent {
    /**
     * Notifies of audio focus changes in car focus stack. It is one way call for OEM Service.
     */
    void notifyAudioFocusChange(@NonNull List<AudioFocusInfo> currentFocusHolders,
            @NonNull List<AudioFocusInfo> currentFocusLosers, int zoneId);

    /**
     * Call to evaluate a focus request, the request contains the information to make a decision.
     *
     * @param request current request containing the focus entry that triggered the current focus
     * evaluation, the current focus holders, and current focus losers (focus requests that have
     * transiently lost focus but can gain it again).
     *
     * @return the result of the focus request
     * The result can be granted, delayed, or failed. In the case of granted the car audio stack
     * will be changed according to the entries returned in newly loss and newly blocked.
     * For delayed results the entry will be added as the current delayed request and it will be
     * re-evaluated when any of the current focus holders abandons focus. For failed request,
     * the car audio focus stack will not change and the current request will not gain focus.
     *
     * <p>Note: For the new focus losers and new blocked focus entries the focus loss can be
     * permanent or transient. In the case of permanent loss the entry will receive permanent
     * focus loss and it will be removed from the car audio focus stack. For transient losses,
     * the new current request will become a blocker but will only receive transient focus loss.
     * Everytime there is focus change the blocked entries will be re-evaluated to determine
     * which can regain, lose, or continue with block focus.
     **/
    @NonNull
    OemCarAudioFocusResult evaluateAudioFocusRequest(
            @NonNull OemCarAudioFocusEvaluationRequest request);
}
