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
package com.android.bedstead.testapp

import com.android.eventlib.events.activities.ActivityCreatedEvent
import com.android.eventlib.events.activities.ActivityCreatedEvent.ActivityCreatedEventQuery
import com.android.eventlib.events.activities.ActivityDestroyedEvent
import com.android.eventlib.events.activities.ActivityDestroyedEvent.ActivityDestroyedEventQuery
import com.android.eventlib.events.activities.ActivityEvents
import com.android.eventlib.events.activities.ActivityPausedEvent
import com.android.eventlib.events.activities.ActivityPausedEvent.ActivityPausedEventQuery
import com.android.eventlib.events.activities.ActivityRestartedEvent
import com.android.eventlib.events.activities.ActivityRestartedEvent.ActivityRestartedEventQuery
import com.android.eventlib.events.activities.ActivityResumedEvent
import com.android.eventlib.events.activities.ActivityResumedEvent.ActivityResumedEventQuery
import com.android.eventlib.events.activities.ActivityStartedEvent
import com.android.eventlib.events.activities.ActivityStartedEvent.ActivityStartedEventQuery
import com.android.eventlib.events.activities.ActivityStoppedEvent
import com.android.eventlib.events.activities.ActivityStoppedEvent.ActivityStoppedEventQuery
import com.android.eventlib.events.broadcastreceivers.BroadcastReceivedEvent
import com.android.eventlib.events.broadcastreceivers.BroadcastReceivedEvent.BroadcastReceivedEventQuery
import com.android.eventlib.events.broadcastreceivers.BroadcastReceiverEvents
import com.android.eventlib.events.delegatedadminreceivers.DelegatedAdminChoosePrivateKeyAliasEvent
import com.android.eventlib.events.delegatedadminreceivers.DelegatedAdminChoosePrivateKeyAliasEvent.DelegatedAdminChoosePrivateKeyAliasEventQuery
import com.android.eventlib.events.delegatedadminreceivers.DelegatedAdminReceiverEvents
import com.android.eventlib.events.delegatedadminreceivers.DelegatedAdminSecurityLogsAvailableEvent
import com.android.eventlib.events.delegatedadminreceivers.DelegatedAdminSecurityLogsAvailableEvent.DelegatedAdminSecurityLogsAvailableEventQuery
import com.android.eventlib.events.deviceadminreceivers.DelegatedAdminNetworkLogsAvailableEvent
import com.android.eventlib.events.deviceadminreceivers.DelegatedAdminNetworkLogsAvailableEvent.DelegatedAdminNetworkLogsAvailableEventQuery
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminBugreportFailedEvent
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminBugreportFailedEvent.DeviceAdminBugreportFailedEventQuery
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminBugreportSharedEvent
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminBugreportSharedEvent.DeviceAdminBugreportSharedEventQuery
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminBugreportSharingDeclinedEvent
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminBugreportSharingDeclinedEvent.DeviceAdminBugreportSharingDeclinedEventQuery
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminChoosePrivateKeyAliasEvent
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminChoosePrivateKeyAliasEvent.DeviceAdminChoosePrivateKeyAliasEventQuery
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminDisableRequestedEvent
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminDisableRequestedEvent.DeviceAdminDisableRequestedEventQuery
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminDisabledEvent
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminDisabledEvent.DeviceAdminDisabledEventQuery
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminEnabledEvent
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminEnabledEvent.DeviceAdminEnabledEventQuery
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminLockTaskModeEnteringEvent
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminLockTaskModeEnteringEvent.DeviceAdminLockTaskModeEnteringEventQuery
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminLockTaskModeExitingEvent
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminLockTaskModeExitingEvent.DeviceAdminLockTaskModeExitingEventQuery
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminNetworkLogsAvailableEvent
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminNetworkLogsAvailableEvent.DeviceAdminNetworkLogsAvailableEventQuery
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminOperationSafetyStateChangedEvent
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminOperationSafetyStateChangedEvent.DeviceAdminOperationSafetyStateChangedEventQuery
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminPasswordChangedEvent
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminPasswordChangedEvent.DeviceAdminPasswordChangedEventQuery
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminPasswordExpiringEvent
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminPasswordExpiringEvent.DeviceAdminPasswordExpiringEventQuery
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminPasswordFailedEvent
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminPasswordFailedEvent.DeviceAdminPasswordFailedEventQuery
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminPasswordSucceededEvent
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminPasswordSucceededEvent.DeviceAdminPasswordSucceededEventQuery
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminProfileProvisioningCompleteEvent
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminProfileProvisioningCompleteEvent.DeviceAdminProfileProvisioningCompleteEventQuery
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminReadyForUserInitializationEvent
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminReadyForUserInitializationEvent.DeviceAdminReadyForUserInitializationEventQuery
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminReceiverEvents
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminSecurityLogsAvailableEvent
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminSecurityLogsAvailableEvent.DeviceAdminSecurityLogsAvailableEventQuery
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminSystemUpdatePendingEvent
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminSystemUpdatePendingEvent.DeviceAdminSystemUpdatePendingEventQuery
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminTransferAffiliatedProfileOwnershipCompleteEvent
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminTransferAffiliatedProfileOwnershipCompleteEvent.DeviceAdminTransferAffiliatedProfileOwnershipCompleteEventQuery
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminTransferOwnershipCompleteEvent
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminTransferOwnershipCompleteEvent.DeviceAdminTransferOwnershipCompleteEventQuery
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminUserAddedEvent
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminUserAddedEvent.DeviceAdminUserAddedEventQuery
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminUserRemovedEvent
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminUserRemovedEvent.DeviceAdminUserRemovedEventQuery
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminUserStartedEvent
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminUserStartedEvent.DeviceAdminUserStartedEventQuery
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminUserStoppedEvent
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminUserStoppedEvent.DeviceAdminUserStoppedEventQuery
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminUserSwitchedEvent
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminUserSwitchedEvent.DeviceAdminUserSwitchedEventQuery
import com.android.eventlib.events.services.ServiceBoundEvent
import com.android.eventlib.events.services.ServiceBoundEvent.ServiceBoundEventQuery
import com.android.eventlib.events.services.ServiceConfigurationChangedEvent
import com.android.eventlib.events.services.ServiceConfigurationChangedEvent.ServiceConfigurationChangedEventQuery
import com.android.eventlib.events.services.ServiceCreatedEvent
import com.android.eventlib.events.services.ServiceCreatedEvent.ServiceCreatedEventQuery
import com.android.eventlib.events.services.ServiceDestroyedEvent
import com.android.eventlib.events.services.ServiceDestroyedEvent.ServiceDestroyedEventQuery
import com.android.eventlib.events.services.ServiceEvents
import com.android.eventlib.events.services.ServiceLowMemoryEvent
import com.android.eventlib.events.services.ServiceLowMemoryEvent.ServiceLowMemoryEventQuery
import com.android.eventlib.events.services.ServiceMemoryTrimmedEvent
import com.android.eventlib.events.services.ServiceMemoryTrimmedEvent.ServiceMemoryTrimmedEventQuery
import com.android.eventlib.events.services.ServiceReboundEvent
import com.android.eventlib.events.services.ServiceReboundEvent.ServiceReboundEventQuery
import com.android.eventlib.events.services.ServiceStartedEvent
import com.android.eventlib.events.services.ServiceStartedEvent.ServiceStartedEventQuery
import com.android.eventlib.events.services.ServiceTaskRemovedEvent
import com.android.eventlib.events.services.ServiceTaskRemovedEvent.ServiceTaskRemovedEventQuery
import com.android.eventlib.events.services.ServiceUnboundEvent
import com.android.eventlib.events.services.ServiceUnboundEvent.ServiceUnboundEventQuery

/**
 * Quick access to events on this test app.
 *
 *
 * Additional filters can be added to the returned object.
 *
 *
 * `#poll` can be used to fetch results, and the result can be asserted on.
 */
class TestAppEvents internal constructor(private val mTestApp: TestAppInstance) : ActivityEvents,
    BroadcastReceiverEvents, DeviceAdminReceiverEvents, DelegatedAdminReceiverEvents,
    ServiceEvents {

    override fun activityCreated(): ActivityCreatedEventQuery =
        ActivityCreatedEvent.queryPackage(mTestApp.packageName()).onUser(mTestApp.user())

    override fun activityDestroyed(): ActivityDestroyedEventQuery =
        ActivityDestroyedEvent.queryPackage(mTestApp.packageName()).onUser(mTestApp.user())

    override fun activityPaused(): ActivityPausedEventQuery =
        ActivityPausedEvent.queryPackage(mTestApp.packageName()).onUser(mTestApp.user())

    override fun activityRestarted(): ActivityRestartedEventQuery =
        ActivityRestartedEvent.queryPackage(mTestApp.packageName()).onUser(mTestApp.user())

    override fun activityResumed(): ActivityResumedEventQuery =
        ActivityResumedEvent.queryPackage(mTestApp.packageName()).onUser(mTestApp.user())

    override fun activityStarted(): ActivityStartedEventQuery =
        ActivityStartedEvent.queryPackage(mTestApp.packageName()).onUser(mTestApp.user())

    override fun activityStopped(): ActivityStoppedEventQuery =
        ActivityStoppedEvent.queryPackage(mTestApp.packageName()).onUser(mTestApp.user())

    override fun broadcastReceived(): BroadcastReceivedEventQuery =
        BroadcastReceivedEvent.queryPackage(mTestApp.packageName()).onUser(mTestApp.user())

    override fun bugReportFailed(): DeviceAdminBugreportFailedEventQuery =
        DeviceAdminBugreportFailedEvent.queryPackage(mTestApp.packageName()).onUser(mTestApp.user())

    override fun bugReportShared(): DeviceAdminBugreportSharedEventQuery =
        DeviceAdminBugreportSharedEvent.queryPackage(mTestApp.packageName()).onUser(mTestApp.user())

    override fun bugReportSharingDeclined(): DeviceAdminBugreportSharingDeclinedEventQuery =
        DeviceAdminBugreportSharingDeclinedEvent.queryPackage(mTestApp.packageName()).onUser(mTestApp.user())

    override fun choosePrivateKeyAlias(): DeviceAdminChoosePrivateKeyAliasEventQuery =
        DeviceAdminChoosePrivateKeyAliasEvent.queryPackage(mTestApp.packageName()).onUser(mTestApp.user())

    override fun deviceAdminDisabled(): DeviceAdminDisabledEventQuery =
        DeviceAdminDisabledEvent.queryPackage(mTestApp.packageName()).onUser(mTestApp.user())

    override fun deviceAdminDisableRequested(): DeviceAdminDisableRequestedEventQuery =
        DeviceAdminDisableRequestedEvent.queryPackage(mTestApp.packageName()).onUser(mTestApp.user())

    override fun deviceAdminEnabled(): DeviceAdminEnabledEventQuery =
        DeviceAdminEnabledEvent.queryPackage(mTestApp.packageName()).onUser(mTestApp.user())

    override fun lockTaskModeEntering(): DeviceAdminLockTaskModeEnteringEventQuery =
        DeviceAdminLockTaskModeEnteringEvent.queryPackage(mTestApp.packageName()).onUser(mTestApp.user())

    override fun lockTaskModeExiting(): DeviceAdminLockTaskModeExitingEventQuery =
        DeviceAdminLockTaskModeExitingEvent.queryPackage(mTestApp.packageName()).onUser(mTestApp.user())

    override fun networkLogsAvailable(): DeviceAdminNetworkLogsAvailableEventQuery =
        DeviceAdminNetworkLogsAvailableEvent.queryPackage(mTestApp.packageName()).onUser(mTestApp.user())

    override fun operationSafetyStateChanged(): DeviceAdminOperationSafetyStateChangedEventQuery =
        DeviceAdminOperationSafetyStateChangedEvent.queryPackage(mTestApp.packageName()).onUser(mTestApp.user())

    override fun passwordChanged(): DeviceAdminPasswordChangedEventQuery =
        DeviceAdminPasswordChangedEvent.queryPackage(mTestApp.packageName()).onUser(mTestApp.user())

    override fun passwordExpiring(): DeviceAdminPasswordExpiringEventQuery =
        DeviceAdminPasswordExpiringEvent.queryPackage(mTestApp.packageName()).onUser(mTestApp.user())

    override fun passwordFailed(): DeviceAdminPasswordFailedEventQuery =
        DeviceAdminPasswordFailedEvent.queryPackage(mTestApp.packageName()).onUser(mTestApp.user())

    override fun passwordSucceeded(): DeviceAdminPasswordSucceededEventQuery =
        DeviceAdminPasswordSucceededEvent.queryPackage(mTestApp.packageName()).onUser(mTestApp.user())

    override fun profileProvisioningComplete(): DeviceAdminProfileProvisioningCompleteEventQuery =
        DeviceAdminProfileProvisioningCompleteEvent.queryPackage(mTestApp.packageName()).onUser(mTestApp.user())

    override fun readyForUserInitialization(): DeviceAdminReadyForUserInitializationEventQuery =
        DeviceAdminReadyForUserInitializationEvent.queryPackage(mTestApp.packageName()).onUser(mTestApp.user())

    override fun securityLogsAvailable(): DeviceAdminSecurityLogsAvailableEventQuery =
        DeviceAdminSecurityLogsAvailableEvent.queryPackage(mTestApp.packageName()).onUser(mTestApp.user())

    override fun systemUpdatePending(): DeviceAdminSystemUpdatePendingEventQuery =
        DeviceAdminSystemUpdatePendingEvent.queryPackage(mTestApp.packageName()).onUser(mTestApp.user())

    override fun transferAffiliatedProfileOwnershipComplete(): DeviceAdminTransferAffiliatedProfileOwnershipCompleteEventQuery =
        DeviceAdminTransferAffiliatedProfileOwnershipCompleteEvent.queryPackage(mTestApp.packageName()).onUser(mTestApp.user())

    override fun transferOwnershipComplete(): DeviceAdminTransferOwnershipCompleteEventQuery =
        DeviceAdminTransferOwnershipCompleteEvent.queryPackage(mTestApp.packageName()).onUser(mTestApp.user())

    override fun userAdded(): DeviceAdminUserAddedEventQuery =
        DeviceAdminUserAddedEvent.queryPackage(mTestApp.packageName()).onUser(mTestApp.user())

    override fun userRemoved(): DeviceAdminUserRemovedEventQuery =
        DeviceAdminUserRemovedEvent.queryPackage(mTestApp.packageName()).onUser(mTestApp.user())

    override fun userStarted(): DeviceAdminUserStartedEventQuery =
        DeviceAdminUserStartedEvent.queryPackage(mTestApp.packageName()).onUser(mTestApp.user())

    override fun userStopped(): DeviceAdminUserStoppedEventQuery =
        DeviceAdminUserStoppedEvent.queryPackage(mTestApp.packageName()).onUser(mTestApp.user())

    override fun userSwitched(): DeviceAdminUserSwitchedEventQuery =
        DeviceAdminUserSwitchedEvent.queryPackage(mTestApp.packageName()).onUser(mTestApp.user())

    override fun serviceCreated(): ServiceCreatedEventQuery =
        ServiceCreatedEvent.queryPackage(mTestApp.testApp().packageName()).onUser(mTestApp.user())

    override fun serviceStarted(): ServiceStartedEventQuery =
        ServiceStartedEvent.queryPackage(mTestApp.testApp().packageName()).onUser(mTestApp.user())

    override fun serviceDestroyed(): ServiceDestroyedEventQuery =
        ServiceDestroyedEvent.queryPackage(mTestApp.testApp().packageName()).onUser(mTestApp.user())

    override fun serviceConfigurationChanged(): ServiceConfigurationChangedEventQuery =
        ServiceConfigurationChangedEvent.queryPackage(mTestApp.testApp().packageName()).onUser(mTestApp.user())

    override fun serviceLowMemory(): ServiceLowMemoryEventQuery =
        ServiceLowMemoryEvent.queryPackage(mTestApp.testApp().packageName()).onUser(mTestApp.user())

    override fun serviceMemoryTrimmed(): ServiceMemoryTrimmedEventQuery =
        ServiceMemoryTrimmedEvent.queryPackage(mTestApp.testApp().packageName()).onUser(mTestApp.user())

    override fun serviceBound(): ServiceBoundEventQuery =
        ServiceBoundEvent.queryPackage(mTestApp.testApp().packageName()).onUser(mTestApp.user())

    override fun serviceUnbound(): ServiceUnboundEventQuery =
        ServiceUnboundEvent.queryPackage(mTestApp.testApp().packageName()).onUser(mTestApp.user())

    override fun serviceRebound(): ServiceReboundEventQuery =
        ServiceReboundEvent.queryPackage(mTestApp.testApp().packageName()).onUser(mTestApp.user())

    override fun serviceTaskRemoved(): ServiceTaskRemovedEventQuery =
        ServiceTaskRemovedEvent.queryPackage(mTestApp.testApp().packageName()).onUser(mTestApp.user())

    override fun delegateChoosePrivateKeyAlias(): DelegatedAdminChoosePrivateKeyAliasEventQuery =
        DelegatedAdminChoosePrivateKeyAliasEvent.queryPackage(mTestApp.testApp().packageName()).onUser(mTestApp.user())

    override fun delegateNetworkLogsAvailable(): DelegatedAdminNetworkLogsAvailableEventQuery =
        DelegatedAdminNetworkLogsAvailableEvent.queryPackage(mTestApp.testApp().packageName()).onUser(mTestApp.user())

    override fun delegateSecurityLogsAvailable(): DelegatedAdminSecurityLogsAvailableEventQuery =
        DelegatedAdminSecurityLogsAvailableEvent.queryPackage(mTestApp.testApp().packageName()).onUser(mTestApp.user())
}
