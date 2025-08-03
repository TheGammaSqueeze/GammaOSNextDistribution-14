/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.devicepolicy.cts.utils;

import static com.android.bedstead.nene.permissions.CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS;

import static org.junit.Assert.fail;

import android.app.admin.DevicePolicyManager;
import android.app.admin.DevicePolicyState;
import android.app.admin.LockTaskPolicy;
import android.app.admin.MostRecent;
import android.app.admin.MostRestrictive;
import android.app.admin.PolicyKey;
import android.app.admin.PolicyState;
import android.app.admin.TopPriority;
import android.content.ComponentName;
import android.os.UserHandle;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.permissions.PermissionContext;

import java.util.List;
import java.util.Set;

public final class PolicyEngineUtils {

    private PolicyEngineUtils() {}

    public static final List<Boolean> TRUE_MORE_RESTRICTIVE = List.of(true, false);

    public static final List<Boolean> FALSE_MORE_RESTRICTIVE = List.of(false, true);

    public static String FINANCED_DEVICE_CONTROLLER_ROLE =
            "android.app.role.SYSTEM_FINANCED_DEVICE_CONTROLLER";

    public static PolicyState<Boolean> getBooleanPolicyState(PolicyKey policyKey, UserHandle user) {
        try (PermissionContext p = TestApis.permissions().withPermission(
                MANAGE_PROFILE_AND_DEVICE_OWNERS)) {
            DevicePolicyManager dpm = TestApis.context().instrumentedContext().getSystemService(
                    DevicePolicyManager.class);
            DevicePolicyState state = dpm.getDevicePolicyState();
            if (state.getPoliciesForUser(user).get(policyKey) != null) {
                return (PolicyState<Boolean>) state.getPoliciesForUser(user).get(policyKey);
            } else {
                return (PolicyState<Boolean>) state.getPoliciesForUser(UserHandle.ALL)
                                .get(policyKey);
            }
        } catch (ClassCastException e) {
            fail("Returned policy is not of type Boolean: " + e);
            return null;
        }
    }

    public static PolicyState<Set<String>> getStringSetPolicyState(
            PolicyKey policyKey, UserHandle user) {
        try (PermissionContext p = TestApis.permissions().withPermission(
                MANAGE_PROFILE_AND_DEVICE_OWNERS)) {
            DevicePolicyManager dpm = TestApis.context().instrumentedContext().getSystemService(
                    DevicePolicyManager.class);
            DevicePolicyState state = dpm.getDevicePolicyState();
            if (state.getPoliciesForUser(user).get(policyKey) != null) {
                return (PolicyState<Set<String>>) state.getPoliciesForUser(user).get(policyKey);
            } else {
                return (PolicyState<Set<String>>) state.getPoliciesForUser(UserHandle.ALL)
                                .get(policyKey);
            }
        } catch (ClassCastException e) {
            fail("Returned policy is not of type Set<String>: " + e);
            return null;
        }
    }

    public static PolicyState<LockTaskPolicy> getLockTaskPolicyState(
            PolicyKey policyKey, UserHandle user) {
        try (PermissionContext p = TestApis.permissions().withPermission(
                MANAGE_PROFILE_AND_DEVICE_OWNERS)) {
            DevicePolicyManager dpm = TestApis.context().instrumentedContext().getSystemService(
                    DevicePolicyManager.class);
            DevicePolicyState state = dpm.getDevicePolicyState();
            return (PolicyState<LockTaskPolicy>) state.getPoliciesForUser(user).get(policyKey);
        } catch (ClassCastException e) {
            fail("Returned policy is not of type LockTaskPolicy: " + e);
            return null;
        }
    }

    public static PolicyState<ComponentName> getComponentNamePolicyState(
            PolicyKey policyKey, UserHandle user) {
        try (PermissionContext p = TestApis.permissions().withPermission(
                MANAGE_PROFILE_AND_DEVICE_OWNERS)) {
            DevicePolicyManager dpm = TestApis.context().instrumentedContext().getSystemService(
                    DevicePolicyManager.class);
            DevicePolicyState state = dpm.getDevicePolicyState();
            if (state.getPoliciesForUser(user).get(policyKey) != null) {
                return (PolicyState<ComponentName>) state.getPoliciesForUser(user).get(policyKey);
            } else {
                return (PolicyState<ComponentName>) state.getPoliciesForUser(UserHandle.ALL)
                        .get(policyKey);
            }
        } catch (ClassCastException e) {
            fail("Returned policy is not of type ComponentName: " + e);
            return null;
        }
    }

    public static MostRestrictive<Boolean> getMostRestrictiveBooleanMechanism(
            PolicyState<Boolean> policyState) {
        try {
            return (MostRestrictive<Boolean>) policyState.getResolutionMechanism();
        } catch (ClassCastException e) {
            fail("Returned resolution mechanism is not of type MostRestrictive<Boolean>: " + e);
            return null;
        }
    }

    public static TopPriority<?> getTopPriorityMechanism(PolicyState<?> policyState) {
        try {
            return (TopPriority<?>) policyState.getResolutionMechanism();
        } catch (ClassCastException e) {
            fail("Returned resolution mechanism is not of type TopPriority<>: " + e);
            return null;
        }
    }

    public static MostRecent<Set<String>> getMostRecentStringSetMechanism(
            PolicyState<Set<String>> policyState) {
        try {
            return (MostRecent<Set<String>>) policyState.getResolutionMechanism();
        } catch (ClassCastException e) {
            fail("Returned resolution mechanism is not of type MostRecent<Set<String>>: " + e);
            return null;
        }
    }
}
