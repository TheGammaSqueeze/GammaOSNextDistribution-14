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

package com.android.services.telephony.rcs;

import android.telephony.ims.SipDialogState;

import java.util.List;

/**
 * The listener interface for notifying the state of sip dialogs to SipDialogsStateHandle.
 * refer to {@link SipTransportController}
 */
public interface SipDialogsStateListener {
    /**
     * To map dialog state information of available delegates
     * @param key This is an ID of SipSessionTracker for distinguishing whose delegate is
     *               during dialog mapping.
     * @param dialogStates This is dialog state information of delegate
     */
    void reMappingSipDelegateState(String key, List<SipDialogState> dialogStates);

    /**
     * Notify SipDialogState information with
     * {@link com.android.internal.telephony.ISipDialogStateCallback}
     */
    void notifySipDialogState();
}
