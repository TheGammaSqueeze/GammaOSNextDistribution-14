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

package android.car.cts.builtin.util;

import static android.car.cts.builtin.util.LogcatHelper.Buffer.EVENTS;
import static android.car.cts.builtin.util.LogcatHelper.Level.INFO;
import static android.car.cts.builtin.util.LogcatHelper.assertLogcatMessage;
import static android.car.cts.builtin.util.LogcatHelper.clearLog;

import android.car.builtin.util.EventLogHelper;

import org.junit.Before;
import org.junit.Test;

public final class EventLogHelperTest {

    private static final int TIMEOUT_MS = 10_000;

    @Before
    public void setup() {
        clearLog();
    }

    @Test
    public void testWriteCarHelperStart() {
        EventLogHelper.writeCarHelperStart();

        assertLogMessage("car_helper_start");
    }

    @Test
    public void testWriteCarHelperBootPhase() {
        EventLogHelper.writeCarHelperBootPhase(1);

        assertLogMessage("car_helper_boot_phase", "1");
    }

    @Test
    public void testWriteCarHelperUserStarting() {
        EventLogHelper.writeCarHelperUserStarting(100);

        assertLogMessage("car_helper_user_starting", "100");
    }

    @Test
    public void testWriteCarHelperUserSwitching() {
        EventLogHelper.writeCarHelperUserSwitching(100, 101);

        assertLogMessage("car_helper_user_switching", "[100,101]");
    }

    @Test
    public void testWriteCarHelperUserUnlocking() {
        EventLogHelper.writeCarHelperUserUnlocking(100);

        assertLogMessage("car_helper_user_unlocking", "100");
    }

    @Test
    public void testWriteCarHelperUserUnlocked() {
        EventLogHelper.writeCarHelperUserUnlocked(100);

        assertLogMessage("car_helper_user_unlocked", "100");
    }

    @Test
    public void testWriteCarHelperUserStopping() {
        EventLogHelper.writeCarHelperUserStopping(100);

        assertLogMessage("car_helper_user_stopping", "100");
    }

    @Test
    public void testWriteCarHelperUserStopped() {
        EventLogHelper.writeCarHelperUserStopped(100);

        assertLogMessage("car_helper_user_stopped", "100");
    }

    @Test
    public void testWriteCarHelperServiceConnected() {
        EventLogHelper.writeCarHelperServiceConnected();

        assertLogMessage("car_helper_svc_connected");
    }

    @Test
    public void testWriteCarServiceInit() {
        EventLogHelper.writeCarServiceInit(101);

        assertLogMessage("car_service_init", "101");
    }

    @Test
    public void testWriteCarServiceVhalReconnected() {
        EventLogHelper.writeCarServiceVhalReconnected(101);

        assertLogMessage("car_service_vhal_reconnected", "101");
    }

    @Test
    public void testWriteCarServiceSetCarServiceHelper() {
        EventLogHelper.writeCarServiceSetCarServiceHelper(101);

        assertLogMessage("car_service_set_car_service_helper", "101");
    }

    @Test
    public void testWriteCarServiceOnUserLifecycle() {
        EventLogHelper.writeCarServiceOnUserLifecycle(1, 2, 3);

        assertLogMessage("car_service_on_user_lifecycle", "[1,2,3]");
    }

    @Test
    public void testWriteCarServiceCreate() {
        EventLogHelper.writeCarServiceCreate(true);

        assertLogMessage("car_service_create", "1");
    }

    @Test
    public void testWriteCarServiceConnected() {
        EventLogHelper.writeCarServiceConnected("testString");

        assertLogMessage("car_service_connected", "testString");
    }

    @Test
    public void testWriteCarServiceDestroy() {
        EventLogHelper.writeCarServiceDestroy(true);

        assertLogMessage("car_service_destroy", "1");
    }

    @Test
    public void testWriteCarServiceVhalDied() {
        EventLogHelper.writeCarServiceVhalDied(101);

        assertLogMessage("car_service_vhal_died", "101");
    }

    @Test
    public void testWriteCarServiceInitBootUser() {
        EventLogHelper.writeCarServiceInitBootUser();

        assertLogMessage("car_service_init_boot_user");
    }

    @Test
    public void testWriteCarServiceOnUserRemoved() {
        EventLogHelper.writeCarServiceOnUserRemoved(101);

        assertLogMessage("car_service_on_user_removed", "101");
    }

    @Test
    public void testWriteCarUserServiceInitialUserInfoReq() {
        EventLogHelper.writeCarUserServiceInitialUserInfoReq(1, 2, 3, 4, 5);

        assertLogMessage("car_user_svc_initial_user_info_req", "[1,2,3,4,5]");
    }

    @Test
    public void testWriteCarUserServiceInitialUserInfoResp() {
        EventLogHelper.writeCarUserServiceInitialUserInfoResp(1, 2, 3, 4, "string1", "string2");

        assertLogMessage("car_user_svc_initial_user_info_resp", "[1,2,3,4,string1,string2]");
    }

    @Test
    public void testWriteCarUserServiceSetInitialUser() {
        EventLogHelper.writeCarUserServiceSetInitialUser(101);

        assertLogMessage("car_user_svc_set_initial_user", "101");
    }

    @Test
    public void testWriteCarUserServiceSetLifecycleListener() {
        EventLogHelper.writeCarUserServiceSetLifecycleListener(101, "string1");

        assertLogMessage("car_user_svc_set_lifecycle_listener", "[101,string1]");
    }

    @Test
    public void testWriteCarUserServiceResetLifecycleListener() {
        EventLogHelper.writeCarUserServiceResetLifecycleListener(101, "string1");

        assertLogMessage("car_user_svc_reset_lifecycle_listener", "[101,string1]");
    }

    @Test
    public void testWriteCarUserServiceSwitchUserReq() {
        EventLogHelper.writeCarUserServiceSwitchUserReq(101, 102);

        assertLogMessage("car_user_svc_switch_user_req", "[101,102]");
    }

    @Test
    public void testWriteCarUserServiceSwitchUserResp() {
        EventLogHelper.writeCarUserServiceSwitchUserResp(101, 102, "string");

        assertLogMessage("car_user_svc_switch_user_resp", "[101,102,string]");
    }

    @Test
    public void testWriteCarUserServicePostSwitchUserReq() {
        EventLogHelper.writeCarUserServicePostSwitchUserReq(101, 102);

        assertLogMessage("car_user_svc_post_switch_user_req", "[101,102]");
    }

    @Test
    public void testWriteCarUserServiceGetUserAuthReq() {
        EventLogHelper.writeCarUserServiceGetUserAuthReq(101, 102, 103);

        assertLogMessage("car_user_svc_get_user_auth_req", "[101,102,103]");
    }

    @Test
    public void testWriteCarUserServiceLogoutUserReq() {
        EventLogHelper.writeCarUserServiceLogoutUserReq(101, 102);

        assertLogMessage("car_user_svc_logout_user_req", "[101,102]");
    }

    @Test
    public void testWriteCarUserServiceLogoutUserResp() {
        EventLogHelper.writeCarUserServiceLogoutUserResp(101, 102, "string");

        assertLogMessage("car_user_svc_logout_user_resp", "[101,102,string]");
    }

    @Test
    public void testWriteCarUserServiceGetUserAuthResp() {
        EventLogHelper.writeCarUserServiceGetUserAuthResp(101);

        assertLogMessage("car_user_svc_get_user_auth_resp", "101");
    }

    @Test
    public void testWriteCarUserServiceSwitchUserUiReq() {
        EventLogHelper.writeCarUserServiceSwitchUserUiReq(101);

        assertLogMessage("car_user_svc_switch_user_ui_req", "101");
    }

    @Test
    public void testWriteCarUserServiceSwitchUserFromHalReq() {
        EventLogHelper.writeCarUserServiceSwitchUserFromHalReq(101, 102);

        assertLogMessage("car_user_svc_switch_user_from_hal_req", "[101,102]");
    }

    @Test
    public void testWriteCarUserServiceSetUserAuthReq() {
        EventLogHelper.writeCarUserServiceSetUserAuthReq(101, 102, 103);

        assertLogMessage("car_user_svc_set_user_auth_req", "[101,102,103]");
    }

    @Test
    public void testWriteCarUserServiceSetUserAuthResp() {
        EventLogHelper.writeCarUserServiceSetUserAuthResp(101, "string");

        assertLogMessage("car_user_svc_set_user_auth_resp", "[101,string]");
    }

    @Test
    public void testWriteCarUserServiceCreateUserReq() {
        EventLogHelper.writeCarUserServiceCreateUserReq("string1", "string2", 101, 102, 103);

        assertLogMessage("car_user_svc_create_user_req", "[string1,string2,101,102,103]");
    }

    @Test
    public void testWriteCarUserServiceCreateUserResp() {
        EventLogHelper.writeCarUserServiceCreateUserResp(101, 102, "string");

        assertLogMessage("car_user_svc_create_user_resp", "[101,102,string]");
    }

    @Test
    public void testWriteCarUserServiceCreateUserUserCreated() {
        EventLogHelper.writeCarUserServiceCreateUserUserCreated(101, "string1", "string2", 102);

        assertLogMessage("car_user_svc_create_user_user_created", "[101,string1,string2,102]");
    }

    @Test
    public void testWriteCarUserServiceCreateUserUserRemoved() {
        EventLogHelper.writeCarUserServiceCreateUserUserRemoved(101, "string");

        assertLogMessage("car_user_svc_create_user_user_removed", "[101,string]");
    }

    @Test
    public void testWriteCarUserServiceRemoveUserReq() {
        EventLogHelper.writeCarUserServiceRemoveUserReq(101, 102);

        assertLogMessage("car_user_svc_remove_user_req", "[101,102]");
    }

    @Test
    public void testWriteCarUserServiceRemoveUserResp() {
        EventLogHelper.writeCarUserServiceRemoveUserResp(101, 102);

        assertLogMessage("car_user_svc_remove_user_resp", "[101,102]");
    }

    @Test
    public void testWriteCarUserServiceNotifyAppLifecycleListener() {
        EventLogHelper.writeCarUserServiceNotifyAppLifecycleListener(101, "string", 102, 103, 104);

        assertLogMessage("car_user_svc_notify_app_lifecycle_listener", "[101,string,102,103,104]");
    }

    @Test
    public void testWriteCarUserServiceNotifyInternalLifecycleListener() {
        EventLogHelper.writeCarUserServiceNotifyInternalLifecycleListener("string", 102, 103, 104);

        assertLogMessage("car_user_svc_notify_internal_lifecycle_listener", "[string,102,103,104]");
    }

    @Test
    public void testWriteCarUserServicePreCreationRequested() {
        EventLogHelper.writeCarUserServicePreCreationRequested(101, 102);

        assertLogMessage("car_user_svc_pre_creation_requested", "[101,102]");
    }

    @Test
    public void testWriteCarUserServicePreCreationStatus() {
        EventLogHelper.writeCarUserServicePreCreationStatus(101, 102, 103, 104, 105, 106, 107);

        assertLogMessage("car_user_svc_pre_creation_status", "[101,102,103,104,105,106,107]");
    }

    @Test
    public void testWriteCarUserServiceStartUserInBackgroundReq() {
        EventLogHelper.writeCarUserServiceStartUserInBackgroundReq(101);

        assertLogMessage("car_user_svc_start_user_in_background_req", "101");
    }

    @Test
    public void testWriteCarUserServiceStartUserInBackgroundResp() {
        EventLogHelper.writeCarUserServiceStartUserInBackgroundResp(101, 102);

        assertLogMessage("car_user_svc_start_user_in_background_resp", "[101,102]");
    }

    @Test
    public void testWriteCarUserServiceStopUserReq() {
        EventLogHelper.writeCarUserServiceStopUserReq(101);

        assertLogMessage("car_user_svc_stop_user_req", "101");
    }

    @Test
    public void testWriteCarUserServiceStopUserResp() {
        EventLogHelper.writeCarUserServiceStopUserResp(101, 102);

        assertLogMessage("car_user_svc_stop_user_resp", "[101,102]");
    }

    @Test
    public void testWriteCarUserServiceInitialUserInfoReqComplete() {
        EventLogHelper.writeCarUserServiceInitialUserInfoReqComplete(101);

        assertLogMessage("car_user_svc_initial_user_info_req_complete", "101");
    }

    @Test
    public void testWriteCarUserHalInitialUserInfoReq() {
        EventLogHelper.writeCarUserHalInitialUserInfoReq(101, 102, 103);

        assertLogMessage("car_user_hal_initial_user_info_req", "[101,102,103]");
    }

    @Test
    public void testWriteCarUserHalInitialUserInfoResp() {
        EventLogHelper.writeCarUserHalInitialUserInfoResp(101, 102, 103, 104, 105, "string1",
                "string2");

        assertLogMessage("car_user_hal_initial_user_info_resp",
                "[101,102,103,104,105,string1,string2]");
    }

    @Test
    public void testWriteCarInitialUserStartFgUser() {
        EventLogHelper.writeCarInitialUserStartFgUser(101);

        assertLogMessage("car_initial_user_start_fg_user", "101");
    }

    @Test
    public void testWriteCarInitialUserInfo() {
        EventLogHelper.writeCarInitialUserInfo(101, true, 102, "string1", 103, true, "string2");

        assertLogMessage("car_initial_user_info", "[101,1,102,string1,103,1,string2]");
    }

    @Test
    public void testWriteCarInitialUserFallbackDefaultBehavior() {
        EventLogHelper.writeCarInitialUserFallbackDefaultBehavior("string1");

        assertLogMessage("car_initial_user_fallback_default_behavior", "string1");
    }

    @Test
    public void testWriteCarInitialUserReplaceGuest() {
        EventLogHelper.writeCarInitialUserReplaceGuest(101);

        assertLogMessage("car_initial_user_replace_guest", "101");
    }

    @Test
    public void testWriteCarInitialUserUnlockSystemUser() {
        EventLogHelper.writeCarInitialUserUnlockSystemUser();

        assertLogMessage("car_initial_user_unlock_system_user");
    }

    @Test
    public void testWriteCarInitialUserSetLastActive() {
        EventLogHelper.writeCarInitialUserSetLastActive(101);

        assertLogMessage("car_initial_user_set_last_active", "101");
    }

    @Test
    public void testWriteCarInitialUserResetGlobalProperty() {
        EventLogHelper.writeCarInitialUserResetGlobalProperty("string1");

        assertLogMessage("car_initial_user_reset_global_property", "string1");
    }

    @Test
    public void testWriteCarUserHalSwitchUserReq() {
        EventLogHelper.writeCarUserHalSwitchUserReq(101, 102, 103, 104);

        assertLogMessage("car_user_hal_switch_user_req", "[101,102,103,104]");
    }

    @Test
    public void testWriteCarUserHalSwitchUserResp() {
        EventLogHelper.writeCarUserHalSwitchUserResp(101, 102, 103, "string");

        assertLogMessage("car_user_hal_switch_user_resp", "[101,102,103,string]");
    }

    @Test
    public void testWriteCarUserHalPostSwitchUserReq() {
        EventLogHelper.writeCarUserHalPostSwitchUserReq(101, 102, 103);

        assertLogMessage("car_user_hal_post_switch_user_req", "[101,102,103]");
    }

    @Test
    public void writeCarUserHalGetUserAuthReq() {
        Object[] objectArray = new Object[] {
                101, 102, 103, "string", 104
        };
        EventLogHelper.writeCarUserHalGetUserAuthReq(objectArray);

        assertLogMessage("car_user_hal_get_user_auth_req", "[101,102,103,string,104]");
    }

    @Test
    public void testWriteCarUserHalGetUserAuthResp() {
        Object[] objectArray = new Object[] {
                101, 102, 103, "string", 104
        };
        EventLogHelper.writeCarUserHalGetUserAuthResp(objectArray);

        assertLogMessage("car_user_hal_get_user_auth_resp", "[101,102,103,string,104]");
    }

    @Test
    public void testWriteCarUserHalLegacySwitchUserReq() {
        EventLogHelper.writeCarUserHalLegacySwitchUserReq(101, 102, 103);

        assertLogMessage("car_user_hal_legacy_switch_user_req", "[101,102,103]");
    }

    @Test
    public void testWriteCarUserHalSetUserAuthReq() {
        Object[] objectArray = new Object[] {
                101, 102, 103, "string", 104
        };
        EventLogHelper.writeCarUserHalSetUserAuthReq(objectArray);

        assertLogMessage("car_user_hal_set_user_auth_req", "[101,102,103,string,104]");
    }

    @Test
    public void testWriteCarUserHalSetUserAuthResp() {
        Object[] objectArray = new Object[] {
                101, 102, 103, "string", 104
        };
        EventLogHelper.writeCarUserHalSetUserAuthResp(objectArray);

        assertLogMessage("car_user_hal_set_user_auth_resp", "[101,102,103,string,104]");
    }

    @Test
    public void testWriteCarUserHalOemSwitchUserReq() {
        EventLogHelper.writeCarUserHalOemSwitchUserReq(101, 102);

        assertLogMessage("car_user_hal_oem_switch_user_req", "[101,102]");
    }

    @Test
    public void testWriteCarUserHalCreateUserReq() {
        EventLogHelper.writeCarUserHalCreateUserReq(101, "string", 102, 103);

        assertLogMessage("car_user_hal_create_user_req", "[101,string,102,103]");
    }

    @Test
    public void testWriteCarUserHalCreateUserResp() {
        EventLogHelper.writeCarUserHalCreateUserResp(101, 102, 103, "string");

        assertLogMessage("car_user_hal_create_user_resp", "[101,102,103,string]");
    }

    @Test
    public void testWriteCarUserHalRemoveUserReq() {
        EventLogHelper.writeCarUserHalRemoveUserReq(101, 102);

        assertLogMessage("car_user_hal_remove_user_req", "[101,102]");
    }

    @Test
    public void testWriteCarUserManagerAddListener() {
        EventLogHelper.writeCarUserManagerAddListener(101, "string", true);

        assertLogMessage("car_user_mgr_add_listener", "[101,string,1]");
    }

    @Test
    public void testWriteCarUserManagerRemoveListener() {
        EventLogHelper.writeCarUserManagerRemoveListener(101, "string");

        assertLogMessage("car_user_mgr_remove_listener", "[101,string]");
    }

    @Test
    public void testWriteCarUserManagerDisconnected() {
        EventLogHelper.writeCarUserManagerDisconnected(101);

        assertLogMessage("car_user_mgr_disconnected", "101");
    }

    @Test
    public void testWriteCarUserManagerSwitchUserReq() {
        EventLogHelper.writeCarUserManagerSwitchUserReq(101, 102);

        assertLogMessage("car_user_mgr_switch_user_req", "[101,102]");
    }

    @Test
    public void testWriteCarUserManagerSwitchUserResp() {
        EventLogHelper.writeCarUserManagerSwitchUserResp(101, 102, "string");

        assertLogMessage("car_user_mgr_switch_user_resp", "[101,102,string]");
    }

    @Test
    public void testWriteCarUserManagerLogoutUserReq() {
        EventLogHelper.writeCarUserManagerLogoutUserReq(42108);

        assertLogMessage("car_user_mgr_logout_user_req", "42108");
    }

    @Test
    public void testWriteCarUserManagerLogoutUserResp() {
        EventLogHelper.writeCarUserManagerLogoutUserResp(42108, 1, "D'OH!");

        assertLogMessage("car_user_mgr_logout_user_resp", "[42108,1,D'OH!]");
    }

    @Test
    public void testWriteCarUserManagerGetUserAuthReq() {
        Object[] objectArray = new Object[] {
                101, 102, 103, "string", 104
        };
        EventLogHelper.writeCarUserManagerGetUserAuthReq(objectArray);

        assertLogMessage("car_user_mgr_get_user_auth_req", "[101,102,103,string,104]");
    }

    @Test
    public void testWriteCarUserManagerGetUserAuthResp() {
        Object[] objectArray = new Object[] {
                101, 102, 103, "string", 104
        };
        EventLogHelper.writeCarUserManagerGetUserAuthResp(objectArray);

        assertLogMessage("car_user_mgr_get_user_auth_resp", "[101,102,103,string,104]");
    }

    @Test
    public void testWriteCarUserManagerSetUserAuthReq() {
        Object[] objectArray = new Object[] {
                101, 102, 103, "string", 104
        };
        EventLogHelper.writeCarUserManagerSetUserAuthReq(objectArray);

        assertLogMessage("car_user_mgr_set_user_auth_req", "[101,102,103,string,104]");
    }

    @Test
    public void testWriteCarUserManagerSetUserAuthResp() {
        Object[] objectArray = new Object[] {
                101, 102, 103, "string", 104
        };
        EventLogHelper.writeCarUserManagerSetUserAuthResp(objectArray);

        assertLogMessage("car_user_mgr_set_user_auth_resp", "[101,102,103,string,104]");
    }

    @Test
    public void testWriteCarUserManagerCreateUserReq() {
        EventLogHelper.writeCarUserManagerCreateUserReq(101, "string1", "string2", 102);

        assertLogMessage("car_user_mgr_create_user_req", "[101,string1,string2,102]");
    }

    @Test
    public void testWriteCarUserManagerCreateUserResp() {
        EventLogHelper.writeCarUserManagerCreateUserResp(101, 102, "string");

        assertLogMessage("car_user_mgr_create_user_resp", "[101,102,string]");
    }

    @Test
    public void testWriteCarUserManagerRemoveUserReq() {
        EventLogHelper.writeCarUserManagerRemoveUserReq(101, 102);

        assertLogMessage("car_user_mgr_remove_user_req", "[101,102]");
    }

    @Test
    public void testWriteCarUserManagerRemoveUserResp() {
        EventLogHelper.writeCarUserManagerRemoveUserResp(101, 102);

        assertLogMessage("car_user_mgr_remove_user_resp", "[101,102]");
    }

    @Test
    public void testWriteCarUserManagerStartUserReq() {
        EventLogHelper.writeCarUserManagerStartUserReq(101, 102, -1);

        assertLogMessage("car_user_mgr_start_user_req", "[101,102,-1]");
    }

    @Test
    public void testWriteCarUserManagerStartUserResp() {
        EventLogHelper.writeCarUserManagerStartUserResp(101, 102, 3, 14);

        assertLogMessage("car_user_mgr_start_user_resp", "[101,102,3,14]");
    }

    @Test
    public void testWriteCarUserManagerStopUserReq() {
        EventLogHelper.writeCarUserManagerStopUserReq(101, 102);

        assertLogMessage("car_user_mgr_stop_user_req", "[101,102]");
    }

    @Test
    public void testWriteCarUserManagerStopUserResp() {
        EventLogHelper.writeCarUserManagerStopUserResp(101, 102, 3);

        assertLogMessage("car_user_mgr_stop_user_resp", "[101,102,3]");
    }

    @Test
    public void testWriteCarUserManagerNotifyLifecycleListener() {
        EventLogHelper.writeCarUserManagerNotifyLifecycleListener(101, 102, 103, 104);

        assertLogMessage("car_user_mgr_notify_lifecycle_listener", "[101,102,103,104]");
    }

    @Test
    public void testWriteCarUserManagerPreCreateUserReq() {
        EventLogHelper.writeCarUserManagerPreCreateUserReq(101);

        assertLogMessage("car_user_mgr_pre_create_user_req", "101");
    }

    @Test
    public void testWriteCarDevicePolicyManagerRemoveUserReq() {
        EventLogHelper.writeCarDevicePolicyManagerRemoveUserReq(101, 102);

        assertLogMessage("car_dp_mgr_remove_user_req", "[101,102]");
    }

    @Test
    public void testWriteCarDevicePolicyManagerRemoveUserResp() {
        EventLogHelper.writeCarDevicePolicyManagerRemoveUserResp(101, 102);

        assertLogMessage("car_dp_mgr_remove_user_resp", "[101,102]");
    }

    @Test
    public void testWriteCarDevicePolicyManagerCreateUserReq() {
        EventLogHelper.writeCarDevicePolicyManagerCreateUserReq(101, "string", 102);

        assertLogMessage("car_dp_mgr_create_user_req", "[101,string,102]");
    }

    @Test
    public void testWriteCarDevicePolicyManagerCreateUserResp() {
        EventLogHelper.writeCarDevicePolicyManagerCreateUserResp(101, 102);

        assertLogMessage("car_dp_mgr_create_user_resp", "[101,102]");
    }

    @Test
    public void testWriteCarDevicePolicyManagerStartUserInBackgroundReq() {
        EventLogHelper.writeCarDevicePolicyManagerStartUserInBackgroundReq(101, 102);

        assertLogMessage("car_dp_mgr_start_user_in_background_req", "[101,102]");
    }

    @Test
    public void testWriteCarDevicePolicyManagerStartUserInBackgroundResp() {
        EventLogHelper.writeCarDevicePolicyManagerStartUserInBackgroundResp(101, 102);

        assertLogMessage("car_dp_mgr_start_user_in_background_resp", "[101,102]");
    }

    @Test
    public void testWriteCarDevicePolicyManagerStopUserReq() {
        EventLogHelper.writeCarDevicePolicyManagerStopUserReq(101, 102);

        assertLogMessage("car_dp_mgr_stop_user_req", "[101,102]");
    }

    @Test
    public void testWriteCarDevicePolicyManagerStopUserResp() {
        EventLogHelper.writeCarDevicePolicyManagerStopUserResp(101, 102);

        assertLogMessage("car_dp_mgr_stop_user_resp", "[101,102]");
    }

    @Test
    public void testWritePowerPolicyChange() {
        EventLogHelper.writePowerPolicyChange("string");

        assertLogMessage("car_pwr_mgr_pwr_policy_change", "string");
    }

    @Test
    public void testWriteCarPowerManagerStateChange() {
        EventLogHelper.writeCarPowerManagerStateChange(101);

        assertLogMessage("car_pwr_mgr_state_change", "101");
    }

    @Test
    public void testWriteCarPowerManagerStateRequest() {
        EventLogHelper.writeCarPowerManagerStateRequest(101, 102);

        assertLogMessage("car_pwr_mgr_state_req", "[101,102]");
    }

    @Test
    public void testWriteGarageModeEvent() {
        EventLogHelper.writeGarageModeEvent(101);

        assertLogMessage("car_pwr_mgr_garage_mode", "101");
    }

    private void assertLogMessage(String event, String values) {
        assertLogcatMessage(EVENTS, INFO, event, values, TIMEOUT_MS);
    }

    private void assertLogMessage(String event) {
        assertLogMessage(event, /* values=*/ "");
    }
}
