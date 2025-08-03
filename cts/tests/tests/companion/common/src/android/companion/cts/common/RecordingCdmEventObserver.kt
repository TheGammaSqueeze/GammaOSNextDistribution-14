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

package android.companion.cts.common

import android.companion.AssociationInfo
import android.companion.CompanionDeviceManager
import android.companion.cts.common.RecordingCallback.CallbackInvocation
import android.companion.cts.common.RecordingCallback.OnAssociationCreated
import android.companion.cts.common.RecordingCallback.OnAssociationPending
import android.companion.cts.common.RecordingCallback.OnDeviceFound
import android.companion.cts.common.RecordingCallback.OnFailure
import android.content.IntentSender

/**
 * This class is a combination of a
 * [android.companion.CompanionDeviceManager.OnAssociationsChangedListener] and a
 * [android.companion.CompanionDeviceManager.Callback].
 *
 * It can simultaneously serve as both an association change listener and a CDM callback,
 * enabling it to make assertions on the order of all externally observable CDM events.
 */
class RecordingCdmEventObserver
private constructor(container: InvocationContainer<CdmEvent>) :
    CompanionDeviceManager.Callback(),
    CompanionDeviceManager.OnAssociationsChangedListener,
    InvocationTracker<RecordingCdmEventObserver.CdmEvent> by container {

    constructor() : this(InvocationContainer())

    // association change listener behavior

    override fun onAssociationsChanged(associations: List<AssociationInfo>) =
        recordInvocation(AssociationChange(associations))

    // CDM callback behavior

    override fun onDeviceFound(intentSender: IntentSender) =
        recordInvocation(CdmCallback(OnDeviceFound(intentSender)))

    override fun onAssociationPending(intentSender: IntentSender) =
        recordInvocation(CdmCallback(OnAssociationPending(intentSender)))

    override fun onAssociationCreated(associationInfo: AssociationInfo) =
        recordInvocation(CdmCallback(OnAssociationCreated(associationInfo)))

    override fun onFailure(error: CharSequence?) =
        recordInvocation(CdmCallback(OnFailure(error)))

    sealed interface CdmEvent
    data class AssociationChange(val associations: List<AssociationInfo>) : CdmEvent
    data class CdmCallback(val invocation: CallbackInvocation) : CdmEvent
}
