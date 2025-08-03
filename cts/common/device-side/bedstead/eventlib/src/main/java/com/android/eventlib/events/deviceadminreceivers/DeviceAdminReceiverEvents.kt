/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.eventlib.events.deviceadminreceivers

import com.android.eventlib.events.deviceadminreceivers.DeviceAdminBugreportFailedEvent.DeviceAdminBugreportFailedEventQuery
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminBugreportSharedEvent.DeviceAdminBugreportSharedEventQuery
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminBugreportSharingDeclinedEvent.DeviceAdminBugreportSharingDeclinedEventQuery
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminChoosePrivateKeyAliasEvent.DeviceAdminChoosePrivateKeyAliasEventQuery
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminDisableRequestedEvent.DeviceAdminDisableRequestedEventQuery
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminDisabledEvent.DeviceAdminDisabledEventQuery
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminEnabledEvent.DeviceAdminEnabledEventQuery
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminLockTaskModeEnteringEvent.DeviceAdminLockTaskModeEnteringEventQuery
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminLockTaskModeExitingEvent.DeviceAdminLockTaskModeExitingEventQuery
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminNetworkLogsAvailableEvent.DeviceAdminNetworkLogsAvailableEventQuery
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminOperationSafetyStateChangedEvent.DeviceAdminOperationSafetyStateChangedEventQuery
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminPasswordChangedEvent.DeviceAdminPasswordChangedEventQuery
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminPasswordExpiringEvent.DeviceAdminPasswordExpiringEventQuery
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminPasswordFailedEvent.DeviceAdminPasswordFailedEventQuery
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminPasswordSucceededEvent.DeviceAdminPasswordSucceededEventQuery
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminProfileProvisioningCompleteEvent.DeviceAdminProfileProvisioningCompleteEventQuery
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminReadyForUserInitializationEvent.DeviceAdminReadyForUserInitializationEventQuery
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminSecurityLogsAvailableEvent.DeviceAdminSecurityLogsAvailableEventQuery
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminSystemUpdatePendingEvent.DeviceAdminSystemUpdatePendingEventQuery
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminTransferAffiliatedProfileOwnershipCompleteEvent.DeviceAdminTransferAffiliatedProfileOwnershipCompleteEventQuery
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminTransferOwnershipCompleteEvent.DeviceAdminTransferOwnershipCompleteEventQuery
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminUserAddedEvent.DeviceAdminUserAddedEventQuery
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminUserRemovedEvent.DeviceAdminUserRemovedEventQuery
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminUserStartedEvent.DeviceAdminUserStartedEventQuery
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminUserStoppedEvent.DeviceAdminUserStoppedEventQuery
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminUserSwitchedEvent.DeviceAdminUserSwitchedEventQuery

/**
 * Quick access to event queries about device admin receivers.
 */
interface DeviceAdminReceiverEvents {
    /**
     * Query for when [DeviceAdminReceiver.onBugreportFailed] is called
     * on a device admin receiver.
     *
     *
     * Additional filters can be added to the returned object.
     *
     *
     * `#poll` can be used to fetch results, and the result can be asserted on.
     */
    fun bugReportFailed(): DeviceAdminBugreportFailedEventQuery

    /**
     * Query for when [DeviceAdminReceiver.onBugreportShared]  is
     * called on a device admin receiver.
     *
     *
     * Additional filters can be added to the returned object.
     *
     *
     * `#poll` can be used to fetch results, and the result can be asserted on.
     */
    fun bugReportShared(): DeviceAdminBugreportSharedEventQuery

    /**
     * Query for when [DeviceAdminReceiver.onBugreportSharingDeclined]  is
     * called on a device admin receiver.
     *
     *
     * Additional filters can be added to the returned object.
     *
     *
     * `#poll` can be used to fetch results, and the result can be asserted on.
     */
    fun bugReportSharingDeclined(): DeviceAdminBugreportSharingDeclinedEventQuery

    /**
     * Query for when [DeviceAdminReceiver.onChoosePrivateKeyAlias]
     * is called on a device admin receiver.
     *
     *
     * Additional filters can be added to the returned object.
     *
     *
     * `#poll` can be used to fetch results, and the result can be asserted on.
     */
    fun choosePrivateKeyAlias(): DeviceAdminChoosePrivateKeyAliasEventQuery

    /**
     * Query for when [DeviceAdminReceiver.onDisabled] is called on a
     * device admin receiver.
     *
     *
     * Additional filters can be added to the returned object.
     *
     *
     * `#poll` can be used to fetch results, and the result can be asserted on.
     */
    fun deviceAdminDisabled(): DeviceAdminDisabledEventQuery

    /**
     * Query for when [DeviceAdminReceiver.onDisableRequested] is called on
     * a device admin receiver.
     *
     *
     * Additional filters can be added to the returned object.
     *
     *
     * `#poll` can be used to fetch results, and the result can be asserted on.
     */
    fun deviceAdminDisableRequested(): DeviceAdminDisableRequestedEventQuery

    /**
     * Query for when [DeviceAdminReceiver.onEnabled] is called on a device
     * admin receiver.
     *
     *
     * Additional filters can be added to the returned object.
     *
     *
     * `#poll` can be used to fetch results, and the result can be asserted on.
     */
    fun deviceAdminEnabled(): DeviceAdminEnabledEventQuery

    /**
     * Query for when [DeviceAdminReceiver.onLockTaskModeEntering]
     * is called on a device admin receiver.
     *
     *
     * Additional filters can be added to the returned object.
     *
     *
     * `#poll` can be used to fetch results, and the result can be asserted on.
     */
    fun lockTaskModeEntering(): DeviceAdminLockTaskModeEnteringEventQuery

    /**
     * Query for when [DeviceAdminReceiver.onLockTaskModeExiting] is called
     * on a device admin receiver.
     *
     *
     * Additional filters can be added to the returned object.
     *
     *
     * `#poll` can be used to fetch results, and the result can be asserted on.
     */
    fun lockTaskModeExiting(): DeviceAdminLockTaskModeExitingEventQuery

    /**
     * Query for when [DeviceAdminReceiver.onNetworkLogsAvailable]
     * is called on a device admin receiver.
     *
     *
     * Additional filters can be added to the returned object.
     *
     *
     * `#poll` can be used to fetch results, and the result can be asserted on.
     */
    fun networkLogsAvailable(): DeviceAdminNetworkLogsAvailableEventQuery

    /**
     * Query for when [DeviceAdminReceiver.onOperationSafetyStateChanged] is
     * called on a device admin receiver.
     *
     *
     * Additional filters can be added to the returned object.
     *
     *
     * `#poll` can be used to fetch results, and the result can be asserted on.
     */
    fun operationSafetyStateChanged(): DeviceAdminOperationSafetyStateChangedEventQuery

    /**
     * Query for when [DeviceAdminReceiver.onPasswordChanged] is
     * called on a device admin receiver.
     *
     *
     * Additional filters can be added to the returned object.
     *
     *
     * `#poll` can be used to fetch results, and the result can be asserted on.
     */
    fun passwordChanged(): DeviceAdminPasswordChangedEventQuery

    /**
     * Query for when [DeviceAdminReceiver.onPasswordExpiring]
     * is called on a device admin receiver.
     *
     *
     * Additional filters can be added to the returned object.
     *
     *
     * `#poll` can be used to fetch results, and the result can be asserted on.
     */
    fun passwordExpiring(): DeviceAdminPasswordExpiringEventQuery

    /**
     * Query for when [DeviceAdminReceiver.onPasswordFailed] is
     * called on a device admin receiver.
     *
     *
     * Additional filters can be added to the returned object.
     *
     *
     * `#poll` can be used to fetch results, and the result can be asserted on.
     */
    fun passwordFailed(): DeviceAdminPasswordFailedEventQuery

    /**
     * Query for when [DeviceAdminReceiver.onPasswordSucceeded]
     * is called on a device admin receiver.
     *
     *
     * Additional filters can be added to the returned object.
     *
     *
     * `#poll` can be used to fetch results, and the result can be asserted on.
     */
    fun passwordSucceeded(): DeviceAdminPasswordSucceededEventQuery

    /**
     * Query for when [DeviceAdminReceiver.onProfileProvisioningComplete] is
     * called on a device admin receiver.
     *
     *
     * Additional filters can be added to the returned object.
     *
     *
     * `#poll` can be used to fetch results, and the result can be asserted on.
     */
    fun profileProvisioningComplete(): DeviceAdminProfileProvisioningCompleteEventQuery

    /**
     * Query for when [DeviceAdminReceiver.onReadyForUserInitialization] is
     * called on a device admin receiver.
     *
     *
     * Additional filters can be added to the returned object.
     *
     *
     * `#poll` can be used to fetch results, and the result can be asserted on.
     */
    fun readyForUserInitialization(): DeviceAdminReadyForUserInitializationEventQuery

    /**
     * Query for when [DeviceAdminReceiver.onSecurityLogsAvailable] is called
     * on a device admin receiver.
     *
     *
     * Additional filters can be added to the returned object.
     *
     *
     * `#poll` can be used to fetch results, and the result can be asserted on.
     */
    fun securityLogsAvailable(): DeviceAdminSecurityLogsAvailableEventQuery

    /**
     * Query for when [DeviceAdminReceiver.onSystemUpdatePending] is
     * called on a device admin receiver.
     *
     *
     * Additional filters can be added to the returned object.
     *
     *
     * `#poll` can be used to fetch results, and the result can be asserted on.
     */
    fun systemUpdatePending(): DeviceAdminSystemUpdatePendingEventQuery

    /**
     * Query for when [DeviceAdminReceiver.onTransferAffiliatedProfileOwnershipComplete]
     * is called on a device admin receiver.
     *
     *
     * Additional filters can be added to the returned object.
     *
     *
     * `#poll` can be used to fetch results, and the result can be asserted on.
     */
    fun transferAffiliatedProfileOwnershipComplete(): DeviceAdminTransferAffiliatedProfileOwnershipCompleteEventQuery

    /**
     * Query for when [DeviceAdminReceiver.onTransferOwnershipComplete]
     * is called on a device admin receiver.
     *
     *
     * Additional filters can be added to the returned object.
     *
     *
     * `#poll` can be used to fetch results, and the result can be asserted on.
     */
    fun transferOwnershipComplete(): DeviceAdminTransferOwnershipCompleteEventQuery

    /**
     * Query for when [DeviceAdminReceiver.onUserAdded] is called
     * on a device admin receiver.
     *
     *
     * Additional filters can be added to the returned object.
     *
     *
     * `#poll` can be used to fetch results, and the result can be asserted on.
     */
    fun userAdded(): DeviceAdminUserAddedEventQuery

    /**
     * Query for when [DeviceAdminReceiver.onUserRemoved] is
     * called on a device admin receiver.
     *
     *
     * Additional filters can be added to the returned object.
     *
     *
     * `#poll` can be used to fetch results, and the result can be asserted on.
     */
    fun userRemoved(): DeviceAdminUserRemovedEventQuery

    /**
     * Query for when [DeviceAdminReceiver.onUserStarted] is
     * called on a device admin receiver.
     *
     *
     * Additional filters can be added to the returned object.
     *
     *
     * `#poll` can be used to fetch results, and the result can be asserted on.
     */
    fun userStarted(): DeviceAdminUserStartedEventQuery

    /**
     * Query for when [DeviceAdminReceiver.onUserStopped] is
     * called on a device admin receiver.
     *
     *
     * Additional filters can be added to the returned object.
     *
     *
     * `#poll` can be used to fetch results, and the result can be asserted on.
     */
    fun userStopped(): DeviceAdminUserStoppedEventQuery

    /**
     * Query for when [DeviceAdminReceiver.onUserSwitched] is
     * called on a device admin receiver.
     *
     *
     * Additional filters can be added to the returned object.
     *
     *
     * `#poll` can be used to fetch results, and the result can be asserted on.
     */
    fun userSwitched(): DeviceAdminUserSwitchedEventQuery
}
