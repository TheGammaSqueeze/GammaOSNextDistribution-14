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
package com.android.eventlib.premade

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PersistableBundle
import android.os.UserHandle
import com.android.eventlib.events.broadcastreceivers.BroadcastReceivedEvent
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminBugreportFailedEvent
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminBugreportSharedEvent
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminBugreportSharingDeclinedEvent
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminChoosePrivateKeyAliasEvent
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminDisableRequestedEvent
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminDisabledEvent
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminEnabledEvent
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminLockTaskModeEnteringEvent
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminLockTaskModeExitingEvent
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminNetworkLogsAvailableEvent
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminOperationSafetyStateChangedEvent
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminPasswordChangedEvent
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminPasswordExpiringEvent
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminPasswordFailedEvent
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminPasswordSucceededEvent
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminProfileProvisioningCompleteEvent
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminReadyForUserInitializationEvent
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminSecurityLogsAvailableEvent
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminSystemUpdatePendingEvent
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminTransferAffiliatedProfileOwnershipCompleteEvent
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminTransferOwnershipCompleteEvent
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminUserAddedEvent
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminUserRemovedEvent
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminUserStartedEvent
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminUserStoppedEvent
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminUserSwitchedEvent

/** Implementation of [DeviceAdminReceiver] which logs events in response to callbacks.  */
open class EventLibDeviceAdminReceiver : DeviceAdminReceiver() {
    private var mOverrideDeviceAdminReceiverClassName: String? = null
    fun setOverrideDeviceAdminReceiverClassName(overrideDeviceAdminReceiverClassName: String) {
        mOverrideDeviceAdminReceiverClassName = overrideDeviceAdminReceiverClassName
    }

    /**
     * Get the class name for this [DeviceAdminReceiver].
     *
     *
     * This will account for the name being overridden.
     */
    fun className(): String =
        mOverrideDeviceAdminReceiverClassName ?: EventLibDeviceAdminReceiver::class.java.name

    override fun onEnabled(context: Context, intent: Intent) {
        val logger = DeviceAdminEnabledEvent.logger(this, context, intent)
        mOverrideDeviceAdminReceiverClassName?.let { logger.setDeviceAdminReceiver(it) }
        logger.log()
        super.onEnabled(context, intent)
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence? {
        val logger = DeviceAdminDisableRequestedEvent.logger(this, context, intent)
        mOverrideDeviceAdminReceiverClassName?.let { logger.setDeviceAdminReceiver(it) }
        logger.log()
        return super.onDisableRequested(context, intent)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        val logger = DeviceAdminDisabledEvent.logger(this, context, intent)
        mOverrideDeviceAdminReceiverClassName?.let { logger.setDeviceAdminReceiver(it) }
        logger.log()
        super.onDisabled(context, intent)
    }

    override fun onPasswordChanged(context: Context, intent: Intent) {
        val logger = DeviceAdminPasswordChangedEvent.logger(this, context, intent)
        mOverrideDeviceAdminReceiverClassName?.let { logger.setDeviceAdminReceiver(it) }
        logger.log()
        super.onPasswordChanged(context, intent)
    }

    override fun onPasswordChanged(context: Context, intent: Intent, user: UserHandle) {
        val logger = DeviceAdminPasswordChangedEvent.logger(this, context, intent)
        logger.setUserHandle(user)
        mOverrideDeviceAdminReceiverClassName?.let { logger.setDeviceAdminReceiver(it) }
        logger.log()
    }

    override fun onPasswordFailed(context: Context, intent: Intent) {
        val logger = DeviceAdminPasswordFailedEvent.logger(this, context, intent)
        mOverrideDeviceAdminReceiverClassName?.let { logger.setDeviceAdminReceiver(it) }
        logger.log()
        super.onPasswordFailed(context, intent)
    }

    override fun onPasswordFailed(context: Context, intent: Intent, user: UserHandle) {
        val logger = DeviceAdminPasswordFailedEvent.logger(this, context, intent)
        logger.setUserHandle(user)
        mOverrideDeviceAdminReceiverClassName?.let { logger.setDeviceAdminReceiver(it) }
        logger.log()
    }

    override fun onPasswordSucceeded(context: Context, intent: Intent) {
        val logger = DeviceAdminPasswordSucceededEvent.logger(this, context, intent)
        mOverrideDeviceAdminReceiverClassName?.let { logger.setDeviceAdminReceiver(it) }
        logger.log()
        super.onPasswordSucceeded(context, intent)
    }

    override fun onPasswordSucceeded(context: Context, intent: Intent, user: UserHandle) {
        val logger = DeviceAdminPasswordSucceededEvent.logger(this, context, intent)
        logger.setUserHandle(user)
        mOverrideDeviceAdminReceiverClassName?.let { logger.setDeviceAdminReceiver(it) }
        logger.log()
    }

    override fun onPasswordExpiring(context: Context, intent: Intent) {
        val logger = DeviceAdminPasswordExpiringEvent.logger(this, context, intent)
        mOverrideDeviceAdminReceiverClassName?.let { logger.setDeviceAdminReceiver(it) }
        logger.log()
        super.onPasswordExpiring(context, intent)
    }

    override fun onPasswordExpiring(context: Context, intent: Intent, user: UserHandle) {
        val logger = DeviceAdminPasswordExpiringEvent.logger(this, context, intent)
        logger.setUserHandle(user)
        mOverrideDeviceAdminReceiverClassName?.let { logger.setDeviceAdminReceiver(it) }
        logger.log()
    }

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        val logger = DeviceAdminProfileProvisioningCompleteEvent.logger(this, context, intent)
        mOverrideDeviceAdminReceiverClassName?.let { logger.setDeviceAdminReceiver(it) }
        logger.log()
        super.onProfileProvisioningComplete(context, intent)
    }

    override fun onReadyForUserInitialization(context: Context, intent: Intent) {
        val logger = DeviceAdminReadyForUserInitializationEvent.logger(this, context, intent)
        mOverrideDeviceAdminReceiverClassName?.let { logger.setDeviceAdminReceiver(it) }
        logger.log()
        super.onReadyForUserInitialization(context, intent)
    }

    override fun onLockTaskModeEntering(context: Context, intent: Intent, pkg: String) {
        val logger = DeviceAdminLockTaskModeEnteringEvent.logger(this, context, intent, pkg)
        mOverrideDeviceAdminReceiverClassName?.let { logger.setDeviceAdminReceiver(it) }
        logger.log()
        super.onLockTaskModeEntering(context, intent, pkg)
    }

    override fun onLockTaskModeExiting(context: Context, intent: Intent) {
        val logger = DeviceAdminLockTaskModeExitingEvent.logger(this, context, intent)
        mOverrideDeviceAdminReceiverClassName?.let { logger.setDeviceAdminReceiver(it) }
        logger.log()
        super.onLockTaskModeExiting(context, intent)
    }

    override fun onChoosePrivateKeyAlias(
        context: Context, intent: Intent, uid: Int, uri: Uri?,
        alias: String?
    ): String? {
        val logger = DeviceAdminChoosePrivateKeyAliasEvent
            .logger(this, context, intent, uid, uri, alias)
        mOverrideDeviceAdminReceiverClassName?.let { logger.setDeviceAdminReceiver(it) }
        logger.log()

        // TODO(b/198280332) Allow TestApp to return values for methods.
        super.onChoosePrivateKeyAlias(context, intent, uid, uri, alias)
        return uri?.getQueryParameter("alias")
    }

    override fun onSystemUpdatePending(context: Context, intent: Intent, receivedTime: Long) {
        val logger = DeviceAdminSystemUpdatePendingEvent.logger(this, context, intent, receivedTime)
        mOverrideDeviceAdminReceiverClassName?.let { logger.setDeviceAdminReceiver(it) }
        logger.log()
        super.onSystemUpdatePending(context, intent, receivedTime)
    }

    override fun onBugreportSharingDeclined(context: Context, intent: Intent) {
        val logger = DeviceAdminBugreportSharingDeclinedEvent.logger(this, context, intent)
        mOverrideDeviceAdminReceiverClassName?.let { logger.setDeviceAdminReceiver(it) }
        logger.log()
        super.onBugreportSharingDeclined(context, intent)
    }

    override fun onBugreportShared(context: Context, intent: Intent, bugreportHash: String) {
        val logger = DeviceAdminBugreportSharedEvent.logger(this, context, intent, bugreportHash)
        mOverrideDeviceAdminReceiverClassName?.let { logger.setDeviceAdminReceiver(it) }
        logger.log()
        super.onBugreportSharingDeclined(context, intent)
    }

    override fun onBugreportFailed(context: Context, intent: Intent, failureCode: Int) {
        val logger = DeviceAdminBugreportFailedEvent.logger(this, context, intent, failureCode)
        mOverrideDeviceAdminReceiverClassName?.let { logger.setDeviceAdminReceiver(it) }
        logger.log()
        super.onBugreportFailed(context, intent, failureCode)
    }

    override fun onSecurityLogsAvailable(context: Context, intent: Intent) {
        val logger = DeviceAdminSecurityLogsAvailableEvent.logger(this, context, intent)
        mOverrideDeviceAdminReceiverClassName?.let { logger.setDeviceAdminReceiver(it) }
        logger.log()
        super.onSecurityLogsAvailable(context, intent)
    }

    override fun onNetworkLogsAvailable(
        context: Context, intent: Intent, batchToken: Long,
        networkLogsCount: Int
    ) {
        val logger = DeviceAdminNetworkLogsAvailableEvent
            .logger(this, context, intent, batchToken, networkLogsCount)
        mOverrideDeviceAdminReceiverClassName?.let { logger.setDeviceAdminReceiver(it) }
        logger.log()
        super.onNetworkLogsAvailable(context, intent, batchToken, networkLogsCount)
    }

    override fun onUserAdded(context: Context, intent: Intent, addedUser: UserHandle) {
        val logger = DeviceAdminUserAddedEvent.logger(this, context, intent, addedUser)
        mOverrideDeviceAdminReceiverClassName?.let { logger.setDeviceAdminReceiver(it) }
        logger.log()
        super.onUserAdded(context, intent, addedUser)
    }

    override fun onUserRemoved(context: Context, intent: Intent, removedUser: UserHandle) {
        val logger = DeviceAdminUserRemovedEvent.logger(this, context, intent, removedUser)
        mOverrideDeviceAdminReceiverClassName?.let { logger.setDeviceAdminReceiver(it) }
        logger.log()
        super.onUserRemoved(context, intent, removedUser)
    }

    override fun onUserStarted(context: Context, intent: Intent, startedUser: UserHandle) {
        val logger = DeviceAdminUserStartedEvent.logger(this, context, intent, startedUser)
        mOverrideDeviceAdminReceiverClassName?.let { logger.setDeviceAdminReceiver(it) }
        logger.log()
        super.onUserStarted(context, intent, startedUser)
    }

    override fun onUserStopped(context: Context, intent: Intent, stoppedUser: UserHandle) {
        val logger = DeviceAdminUserStoppedEvent.logger(this, context, intent, stoppedUser)
        mOverrideDeviceAdminReceiverClassName?.let { logger.setDeviceAdminReceiver(it) }
        logger.log()
        super.onUserStopped(context, intent, stoppedUser)
    }

    override fun onUserSwitched(context: Context, intent: Intent, switchedUser: UserHandle) {
        val logger = DeviceAdminUserSwitchedEvent.logger(this, context, intent, switchedUser)
        mOverrideDeviceAdminReceiverClassName?.let { logger.setDeviceAdminReceiver(it) }
        logger.log()
        super.onUserRemoved(context, intent, switchedUser)
    }

    override fun onTransferOwnershipComplete(context: Context, bundle: PersistableBundle?) {
        val logger = DeviceAdminTransferOwnershipCompleteEvent.logger(this, context, bundle)
        mOverrideDeviceAdminReceiverClassName?.let { logger.setDeviceAdminReceiver(it) }
        logger.log()
        super.onTransferOwnershipComplete(context, bundle)
    }

    override fun onTransferAffiliatedProfileOwnershipComplete(context: Context, user: UserHandle) {
        val logger = DeviceAdminTransferAffiliatedProfileOwnershipCompleteEvent
            .logger(this, context, user)
        mOverrideDeviceAdminReceiverClassName?.let { logger.setDeviceAdminReceiver(it) }
        logger.log()
        super.onTransferAffiliatedProfileOwnershipComplete(context, user)
    }

    override fun onOperationSafetyStateChanged(context: Context, reason: Int, isSafe: Boolean) {
        val logger = DeviceAdminOperationSafetyStateChangedEvent
            .logger(this, context, reason, isSafe)
        mOverrideDeviceAdminReceiverClassName?.let { logger.setDeviceAdminReceiver(it) }
        logger.log()
        super.onOperationSafetyStateChanged(context, reason, isSafe)
    }

    override fun onReceive(context: Context, intent: Intent) {
        val logger = BroadcastReceivedEvent.logger(this, context, intent, resultCode)
        mOverrideDeviceAdminReceiverClassName?.let { logger.setBroadcastReceiver(it) }
        logger.log()
        super.onReceive(context, intent)
    }
}
