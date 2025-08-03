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

package com.android.eventlib.premade;

import static com.google.common.truth.Truth.assertThat;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.PersistableBundle;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.RequireRunOnSystemUser;
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasNoDeviceOwner;
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasNoProfileOwner;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.devicepolicy.DeviceOwner;
import com.android.bedstead.nene.devicepolicy.ProfileOwner;
import com.android.bedstead.nene.users.UserReference;
import com.android.eventlib.EventLogs;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminBugreportFailedEvent;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminBugreportSharedEvent;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminBugreportSharingDeclinedEvent;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminChoosePrivateKeyAliasEvent;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminDisableRequestedEvent;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminDisabledEvent;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminEnabledEvent;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminLockTaskModeEnteringEvent;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminLockTaskModeExitingEvent;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminNetworkLogsAvailableEvent;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminOperationSafetyStateChangedEvent;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminPasswordChangedEvent;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminPasswordExpiringEvent;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminPasswordFailedEvent;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminPasswordSucceededEvent;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminProfileProvisioningCompleteEvent;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminReadyForUserInitializationEvent;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminSecurityLogsAvailableEvent;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminSystemUpdatePendingEvent;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminTransferAffiliatedProfileOwnershipCompleteEvent;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminTransferOwnershipCompleteEvent;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminUserAddedEvent;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminUserRemovedEvent;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminUserStartedEvent;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminUserStoppedEvent;
import com.android.eventlib.events.deviceadminreceivers.DeviceAdminUserSwitchedEvent;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BedsteadJUnit4.class)
public class EventLibDeviceAdminReceiverTest {

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final Context sContext = TestApis.context().instrumentedContext();
    private static final ComponentName DEVICE_ADMIN_COMPONENT =
            new ComponentName(
                    sContext.getPackageName(), EventLibDeviceAdminReceiver.class.getName());
    private static final UserReference sUser = TestApis.users().instrumented();
    private static final Intent sIntent = new Intent();
    private static final String PKG = "package";
    private static final int UID = 1;
    private static final Uri URI = Uri.parse("http://uri");
    private static final String ALIAS = "alias";
    private static final String BUGREPORT_HASH = "A";
    private static final int FAILURE_CODE = 1;
    private static final long BATCH_TOKEN = 1;
    private static final int NETWORK_LOGS_COUNT = 1;
    private static final PersistableBundle sPersistableBundle = new PersistableBundle();
    private static final int REASON = 1;
    private static final boolean IS_SAFE = true;

    @Before
    public void setUp() {
        EventLogs.resetLogs();
    }

    @Test
    @RequireRunOnSystemUser // Device owner can only be set on system user
    @EnsureHasNoDeviceOwner
    public void enableDeviceOwner_logsEnabledEvent() {
        DeviceOwner deviceOwner =
                TestApis.devicePolicy().setDeviceOwner(DEVICE_ADMIN_COMPONENT);

        try {
            EventLogs<DeviceAdminEnabledEvent> eventLogs =
                    DeviceAdminEnabledEvent.queryPackage(sContext.getPackageName());

            assertThat(eventLogs.poll()).isNotNull();
        } finally {
            deviceOwner.remove();
        }
    }

    @Test
    @EnsureHasNoProfileOwner
    public void enableProfileOwner_logsEnabledEvent() {
        ProfileOwner profileOwner =
                TestApis.devicePolicy().setProfileOwner(sUser, DEVICE_ADMIN_COMPONENT);

        try {
            EventLogs<DeviceAdminEnabledEvent> eventLogs =
                    DeviceAdminEnabledEvent.queryPackage(sContext.getPackageName());

            assertThat(eventLogs.poll()).isNotNull();
        } finally {
            profileOwner.remove();
        }
    }

    @Test
    public void disableProfileOwner_logsDisableRequestedEvent() {
        EventLibDeviceAdminReceiver receiver = new EventLibDeviceAdminReceiver();

        receiver.onDisableRequested(sContext, sIntent);

        EventLogs<DeviceAdminDisableRequestedEvent> eventLogs =
                DeviceAdminDisableRequestedEvent.queryPackage(sContext.getPackageName());
        assertThat(eventLogs.poll().intent()).isEqualTo(sIntent);
    }

    @Test
    public void disableProfileOwner_logsDisabledEvent() {
        EventLibDeviceAdminReceiver receiver = new EventLibDeviceAdminReceiver();

        receiver.onDisabled(sContext, sIntent);

        EventLogs<DeviceAdminDisabledEvent> eventLogs =
                DeviceAdminDisabledEvent.queryPackage(sContext.getPackageName());
        assertThat(eventLogs.poll().intent()).isEqualTo(sIntent);
    }

    @Test
    public void changePassword_logsPasswordChangedEvent() {
        EventLibDeviceAdminReceiver receiver = new EventLibDeviceAdminReceiver();

        receiver.onPasswordChanged(sContext, sIntent);

        EventLogs<DeviceAdminPasswordChangedEvent> eventLogs =
                DeviceAdminPasswordChangedEvent.queryPackage(sContext.getPackageName());
        assertThat(eventLogs.poll().intent()).isEqualTo(sIntent);
    }

    @Test
    public void changePasswordWithUserHandle_logsPasswordChangedEvent() {
        EventLibDeviceAdminReceiver receiver = new EventLibDeviceAdminReceiver();

        receiver.onPasswordChanged(sContext, sIntent, sUser.userHandle());

        EventLogs<DeviceAdminPasswordChangedEvent> eventLogs =
                DeviceAdminPasswordChangedEvent.queryPackage(sContext.getPackageName());
        DeviceAdminPasswordChangedEvent event = eventLogs.poll();
        assertThat(event.intent()).isEqualTo(sIntent);
        assertThat(event.userHandle()).isEqualTo(sUser.userHandle());
    }

    @Test
    public void failPassword_logsPasswordFailedEvent() {
        EventLibDeviceAdminReceiver receiver = new EventLibDeviceAdminReceiver();

        receiver.onPasswordFailed(sContext, sIntent);

        EventLogs<DeviceAdminPasswordFailedEvent> eventLogs =
                DeviceAdminPasswordFailedEvent.queryPackage(sContext.getPackageName());
        assertThat(eventLogs.poll().intent()).isEqualTo(sIntent);
    }

    @Test
    public void failPasswordWithUserHandle_logsPasswordFailedEvent() {
        EventLibDeviceAdminReceiver receiver = new EventLibDeviceAdminReceiver();

        receiver.onPasswordFailed(sContext, sIntent, sUser.userHandle());

        EventLogs<DeviceAdminPasswordFailedEvent> eventLogs =
                DeviceAdminPasswordFailedEvent.queryPackage(sContext.getPackageName());
        DeviceAdminPasswordFailedEvent event = eventLogs.poll();
        assertThat(event.intent()).isEqualTo(sIntent);
        assertThat(event.userHandle()).isEqualTo(sUser.userHandle());
    }

    @Test
    public void succeedPassword_logsPasswordSucceededEvent() {
        EventLibDeviceAdminReceiver receiver = new EventLibDeviceAdminReceiver();

        receiver.onPasswordSucceeded(sContext, sIntent);

        EventLogs<DeviceAdminPasswordSucceededEvent> eventLogs =
                DeviceAdminPasswordSucceededEvent.queryPackage(sContext.getPackageName());
        assertThat(eventLogs.poll().intent()).isEqualTo(sIntent);
    }

    @Test
    public void succeedPasswordWithUserHandle_logsPasswordSucceededEvent() {
        EventLibDeviceAdminReceiver receiver = new EventLibDeviceAdminReceiver();

        receiver.onPasswordSucceeded(sContext, sIntent, sUser.userHandle());

        EventLogs<DeviceAdminPasswordSucceededEvent> eventLogs =
                DeviceAdminPasswordSucceededEvent.queryPackage(sContext.getPackageName());
        DeviceAdminPasswordSucceededEvent event = eventLogs.poll();
        assertThat(event.intent()).isEqualTo(sIntent);
        assertThat(event.userHandle()).isEqualTo(sUser.userHandle());
    }

    @Test
    public void passwordExpiring_logsPasswordExpiringEvent() {
        EventLibDeviceAdminReceiver receiver = new EventLibDeviceAdminReceiver();

        receiver.onPasswordExpiring(sContext, sIntent);

        EventLogs<DeviceAdminPasswordExpiringEvent> eventLogs =
                DeviceAdminPasswordExpiringEvent.queryPackage(sContext.getPackageName());
        assertThat(eventLogs.poll().intent()).isEqualTo(sIntent);
    }

    @Test
    public void passwordExpiringWithUserHandle_logsPasswordExpiringEvent() {
        EventLibDeviceAdminReceiver receiver = new EventLibDeviceAdminReceiver();

        receiver.onPasswordExpiring(sContext, sIntent, sUser.userHandle());

        EventLogs<DeviceAdminPasswordExpiringEvent> eventLogs =
                DeviceAdminPasswordExpiringEvent.queryPackage(sContext.getPackageName());
        DeviceAdminPasswordExpiringEvent event = eventLogs.poll();
        assertThat(event.intent()).isEqualTo(sIntent);
        assertThat(event.user()).isEqualTo(sUser.userHandle());
    }

    @Test
    public void profileProvisioningComplete_logsProfileProvisioningCompleteEvent() {
        EventLibDeviceAdminReceiver receiver = new EventLibDeviceAdminReceiver();

        receiver.onProfileProvisioningComplete(sContext, sIntent);

        EventLogs<DeviceAdminProfileProvisioningCompleteEvent> eventLogs =
                DeviceAdminProfileProvisioningCompleteEvent.queryPackage(sContext.getPackageName());
        assertThat(eventLogs.poll().intent()).isEqualTo(sIntent);
    }

    @Test
    public void readyForUserInitialization_logsReadyForUserInitializationEvent() {
        EventLibDeviceAdminReceiver receiver = new EventLibDeviceAdminReceiver();

        receiver.onReadyForUserInitialization(sContext, sIntent);

        EventLogs<DeviceAdminReadyForUserInitializationEvent> eventLogs =
                DeviceAdminReadyForUserInitializationEvent.queryPackage(sContext.getPackageName());
        assertThat(eventLogs.poll().intent()).isEqualTo(sIntent);
    }

    @Test
    public void lockTaskModeEntering_logsLockTaskModeEnteringEvent() {
        EventLibDeviceAdminReceiver receiver = new EventLibDeviceAdminReceiver();

        receiver.onLockTaskModeEntering(sContext, sIntent, PKG);

        EventLogs<DeviceAdminLockTaskModeEnteringEvent> eventLogs =
                DeviceAdminLockTaskModeEnteringEvent.queryPackage(sContext.getPackageName());
        assertThat(eventLogs.poll().intent()).isEqualTo(sIntent);
    }

    @Test
    public void lockTaskModeExiting_logsLockTaskModeExitingEvent() {
        EventLibDeviceAdminReceiver receiver = new EventLibDeviceAdminReceiver();

        receiver.onLockTaskModeExiting(sContext, sIntent);

        EventLogs<DeviceAdminLockTaskModeExitingEvent> eventLogs =
                DeviceAdminLockTaskModeExitingEvent.queryPackage(sContext.getPackageName());
        assertThat(eventLogs.poll().intent()).isEqualTo(sIntent);
    }

    @Test
    public void choosePrivateKeyAlias_logsChoosePrivateKeyAliasEvent() {
        EventLibDeviceAdminReceiver receiver = new EventLibDeviceAdminReceiver();

        receiver.onChoosePrivateKeyAlias(sContext, sIntent, UID, URI, ALIAS);

        EventLogs<DeviceAdminChoosePrivateKeyAliasEvent> eventLogs =
                DeviceAdminChoosePrivateKeyAliasEvent.queryPackage(sContext.getPackageName());
        assertThat(eventLogs.poll().intent()).isEqualTo(sIntent);
    }

    @Test
    public void systemUpdatePending_logsSystemUpdatePendingEvent() {
        EventLibDeviceAdminReceiver receiver = new EventLibDeviceAdminReceiver();
        long receivedTime = System.currentTimeMillis();

        receiver.onSystemUpdatePending(sContext, sIntent, receivedTime);

        EventLogs<DeviceAdminSystemUpdatePendingEvent> eventLogs =
                DeviceAdminSystemUpdatePendingEvent.queryPackage(sContext.getPackageName());
        assertThat(eventLogs.poll().receivedTime()).isEqualTo(receivedTime);
    }

    @Test
    public void bugreportSharingDeclined_logsBugReportSharingDeclinedEvent() {
        EventLibDeviceAdminReceiver receiver = new EventLibDeviceAdminReceiver();

        receiver.onBugreportSharingDeclined(sContext, sIntent);

        EventLogs<DeviceAdminBugreportSharingDeclinedEvent> eventLogs =
                DeviceAdminBugreportSharingDeclinedEvent.queryPackage(sContext.getPackageName());
        assertThat(eventLogs.poll().intent()).isEqualTo(sIntent);
    }

    @Test
    public void bugreportShared_logsBugReportSharedEvent() {
        EventLibDeviceAdminReceiver receiver = new EventLibDeviceAdminReceiver();

        receiver.onBugreportShared(sContext, sIntent, BUGREPORT_HASH);

        EventLogs<DeviceAdminBugreportSharedEvent> eventLogs =
                DeviceAdminBugreportSharedEvent.queryPackage(sContext.getPackageName());
        assertThat(eventLogs.poll().intent()).isEqualTo(sIntent);
    }

    @Test
    public void bugreportFailed_logsBugReportFailedEvent() {
        EventLibDeviceAdminReceiver receiver = new EventLibDeviceAdminReceiver();

        receiver.onBugreportFailed(sContext, sIntent, FAILURE_CODE);

        EventLogs<DeviceAdminBugreportFailedEvent> eventLogs =
                DeviceAdminBugreportFailedEvent.queryPackage(sContext.getPackageName());
        assertThat(eventLogs.poll().intent()).isEqualTo(sIntent);
    }

    @Test
    public void securityLogsAvailable_logsSecurityLogsAvailableEvent() {
        EventLibDeviceAdminReceiver receiver = new EventLibDeviceAdminReceiver();

        receiver.onSecurityLogsAvailable(sContext, sIntent);

        EventLogs<DeviceAdminSecurityLogsAvailableEvent> eventLogs =
                DeviceAdminSecurityLogsAvailableEvent.queryPackage(sContext.getPackageName());
        assertThat(eventLogs.poll().intent()).isEqualTo(sIntent);
    }

    @Test
    public void networkLogsAvailable_logsNetworksLogsAvailableEvent() {
        EventLibDeviceAdminReceiver receiver = new EventLibDeviceAdminReceiver();

        receiver.onNetworkLogsAvailable(sContext, sIntent, BATCH_TOKEN, NETWORK_LOGS_COUNT);

        EventLogs<DeviceAdminNetworkLogsAvailableEvent> eventLogs =
                DeviceAdminNetworkLogsAvailableEvent.queryPackage(sContext.getPackageName());
        assertThat(eventLogs.poll().intent()).isEqualTo(sIntent);
    }

    @Test
    public void userAdded_logsUserAddedEvent() {
        EventLibDeviceAdminReceiver receiver = new EventLibDeviceAdminReceiver();

        receiver.onUserAdded(sContext, sIntent, sUser.userHandle());

        EventLogs<DeviceAdminUserAddedEvent> eventLogs =
                DeviceAdminUserAddedEvent.queryPackage(sContext.getPackageName());
        assertThat(eventLogs.poll().intent()).isEqualTo(sIntent);
    }

    @Test
    public void userRemoved_logsUserRemovedEvent() {
        EventLibDeviceAdminReceiver receiver = new EventLibDeviceAdminReceiver();

        receiver.onUserRemoved(sContext, sIntent, sUser.userHandle());

        EventLogs<DeviceAdminUserRemovedEvent> eventLogs =
                DeviceAdminUserRemovedEvent.queryPackage(sContext.getPackageName());
        assertThat(eventLogs.poll().intent()).isEqualTo(sIntent);
    }

    @Test
    public void userStarted_logsUserStartedEvent() {
        EventLibDeviceAdminReceiver receiver = new EventLibDeviceAdminReceiver();

        receiver.onUserStarted(sContext, sIntent, sUser.userHandle());

        EventLogs<DeviceAdminUserStartedEvent> eventLogs =
                DeviceAdminUserStartedEvent.queryPackage(sContext.getPackageName());
        assertThat(eventLogs.poll().intent()).isEqualTo(sIntent);
    }

    @Test
    public void userStopped_logsUserStoppedEvent() {
        EventLibDeviceAdminReceiver receiver = new EventLibDeviceAdminReceiver();

        receiver.onUserStopped(sContext, sIntent, sUser.userHandle());

        EventLogs<DeviceAdminUserStoppedEvent> eventLogs =
                DeviceAdminUserStoppedEvent.queryPackage(sContext.getPackageName());
        assertThat(eventLogs.poll().intent()).isEqualTo(sIntent);
    }

    @Test
    public void userSwitched_logsUserSwitchedEvent() {
        EventLibDeviceAdminReceiver receiver = new EventLibDeviceAdminReceiver();

        receiver.onUserSwitched(sContext, sIntent, sUser.userHandle());

        EventLogs<DeviceAdminUserSwitchedEvent> eventLogs =
                DeviceAdminUserSwitchedEvent.queryPackage(sContext.getPackageName());
        assertThat(eventLogs.poll().intent()).isEqualTo(sIntent);
    }

    @Test
    public void transferOwnershipComplete_logsTransferOwnershipCompleteEvent() {
        EventLibDeviceAdminReceiver receiver = new EventLibDeviceAdminReceiver();

        receiver.onTransferOwnershipComplete(sContext, sPersistableBundle);

        EventLogs<DeviceAdminTransferOwnershipCompleteEvent> eventLogs =
                DeviceAdminTransferOwnershipCompleteEvent.queryPackage(sContext.getPackageName());
        assertThat(eventLogs.poll().bundle()).isEqualTo(sPersistableBundle);
    }

    @Test
    public void transferAffiliatedProfileOwnershipComplete_logsTransferAffiliatedProfileOwnershipCompleteEvent() {
        EventLibDeviceAdminReceiver receiver = new EventLibDeviceAdminReceiver();

        receiver.onTransferAffiliatedProfileOwnershipComplete(sContext, sUser.userHandle());

        EventLogs<DeviceAdminTransferAffiliatedProfileOwnershipCompleteEvent> eventLogs =
                DeviceAdminTransferAffiliatedProfileOwnershipCompleteEvent.queryPackage(
                        sContext.getPackageName());
        assertThat(eventLogs.poll().user()).isEqualTo(sUser.userHandle());
    }

    @Test
    public void operationSafetyStateChanged_logsOperationSafetyStateChangedEvent() {
        EventLibDeviceAdminReceiver receiver = new EventLibDeviceAdminReceiver();

        receiver.onOperationSafetyStateChanged(sContext, REASON, IS_SAFE);

        EventLogs<DeviceAdminOperationSafetyStateChangedEvent> eventLogs =
                DeviceAdminOperationSafetyStateChangedEvent.queryPackage(sContext.getPackageName());
        assertThat(eventLogs.poll().reason()).isEqualTo(REASON);
    }

}
