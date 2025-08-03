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

package android.devicepolicy.cts;

import static android.app.admin.DevicePolicyManager.ACTION_DATA_SHARING_RESTRICTION_APPLIED;
import static android.app.admin.DevicePolicyManager.EXTRA_RESTRICTION;
import static android.app.admin.DevicePolicyManager.FLAG_MANAGED_CAN_ACCESS_PARENT;
import static android.app.admin.DevicePolicyManager.FLAG_PARENT_CAN_ACCESS_MANAGED;
import static android.content.Intent.ACTION_SEND;
import static android.content.Intent.CATEGORY_DEFAULT;
import static android.content.Intent.EXTRA_TEXT;
import static android.content.pm.PackageManager.MATCH_DEFAULT_ONLY;
import static android.os.UserManager.DISALLOW_SHARE_INTO_MANAGED_PROFILE;

import static com.android.bedstead.harrier.UserType.WORK_PROFILE;
import static com.android.bedstead.nene.permissions.CommonPermissions.INTERACT_ACROSS_USERS_FULL;
import static com.android.bedstead.nene.userrestrictions.CommonUserRestrictions.DISALLOW_CROSS_PROFILE_COPY_PASTE;
import static com.android.queryable.queries.ActivityQuery.activity;
import static com.android.queryable.queries.IntentFilterQuery.intentFilter;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureDoesNotHaveUserRestriction;
import com.android.bedstead.harrier.annotations.EnsureHasUserRestriction;
import com.android.bedstead.harrier.annotations.EnsureHasWorkProfile;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.RequireRunOnWorkProfile;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.permissions.PermissionContext;
import com.android.bedstead.nene.users.UserReference;
import com.android.bedstead.nene.utils.Poll;
import com.android.bedstead.nene.utils.ResolveInfoWrapper;
import com.android.bedstead.remotedpc.RemoteDpc;
import com.android.bedstead.testapp.TestApp;
import com.android.bedstead.testapp.TestAppInstance;
import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.BlockingBroadcastReceiver;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.stream.Collectors;

@RunWith(BedsteadJUnit4.class)
public final class CrossProfileSharingTest {
    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final TestApp sTestApp = sDeviceState.testApps().query()
            .whereActivities().contains(
                    activity().where().intentFilters().contains(
                            intentFilter().where().actions().contains("com.android.testapp.SOME_ACTION"),
                            intentFilter().where().actions().contains("android.intent.action.PICK"),
                            intentFilter().where().actions().contains("android.intent.action.SEND_MULTIPLE")
                    )).get();

    // Known action that is handled in the opposite profile, used to query forwarder activity.
    private static final String CROSS_PROFILE_ACTION = "com.android.testapp.SOME_ACTION";

    // TODO(b/191640667): use parametrization instead of looping once available.
    // These are the data sharing intents which can be forwarded to the primary profile.
    private static final Intent[] OPENING_INTENTS = {
            new Intent(Intent.ACTION_GET_CONTENT).setType("*/*").addCategory(
                    Intent.CATEGORY_OPENABLE),
            new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(
                    Intent.CATEGORY_OPENABLE),
            new Intent(Intent.ACTION_PICK).setType("*/*").addCategory(
                    Intent.CATEGORY_DEFAULT),
            new Intent(Intent.ACTION_PICK).addCategory(Intent.CATEGORY_DEFAULT)
    };

    // These are the data sharing intents which can be forwarded to the managed profile.
    private static final Intent[] SHARING_INTENTS = {
            new Intent(ACTION_SEND).setType("*/*"),
            new Intent(Intent.ACTION_SEND_MULTIPLE).setType("*/*")
    };

    private static final IntentFilter SEND_INTENT_FILTER =
            IntentFilter.create(ACTION_SEND, /* dataType= */ "*/*");
    static {
        SEND_INTENT_FILTER.addCategory(CATEGORY_DEFAULT);
    }

    private static final Intent SEND_INTENT =
            new Intent(ACTION_SEND).setType("text/plain").addCategory(CATEGORY_DEFAULT)
                    .putExtra(EXTRA_TEXT, "example");
    private static final Intent CHOOSER_INTENT =
            Intent.createChooser(SEND_INTENT, /* title= */ null);

    /**
     * Test sharing initiated from the profile side i.e. user tries to pick up personal data within
     * a work app when DISALLOW_SHARE_INTO_MANAGED_PROFILE is enforced.
     */
    @Test
    @Postsubmit(reason = "new test")
    @RequireRunOnWorkProfile
    public void openingPersonalFromProfile_disallowShareIntoProfile_restrictionApplied() {
        ResolveInfo toPersonalForwarderInfo = getWorkToPersonalForwarder();

        // Enforce the restriction and wait for it to be applied.
        setSharingIntoProfileEnabled(false);
        // Test app handles android.intent.action.PICK just in case no other app does.
        try (TestAppInstance testAppParent =
                     sTestApp.install(sDeviceState.primaryUser())) {
            // Verify that the intents don't resolve into cross-profile forwarder.
            assertCrossProfileIntentsResolvability(OPENING_INTENTS,
                    toPersonalForwarderInfo, /* expectForwardable */ false);
        } finally {
            // Restore default state.
            setSharingIntoProfileEnabled(true);
        }
    }

    /**
     * Test sharing initiated from the profile side i.e. user tries to pick up personal data within
     * a work app when DISALLOW_SHARE_INTO_MANAGED_PROFILE is NOT enforced.
     */
    @Test
    @Postsubmit(reason = "new test")
    @RequireRunOnWorkProfile
    public void openingPersonalFromProfile_disallowShareIntoProfile_restrictionRemoved() {
        ResolveInfo workToPersonalForwarder = getWorkToPersonalForwarder();

        // Enforce the restriction and wait for it to be applied, then remove it and wait again.
        setSharingIntoProfileEnabled(false);
        setSharingIntoProfileEnabled(true);

        // Test app handles android.intent.action.PICK just in case no other app does.
        try (TestAppInstance testAppParent =
                     sTestApp.install(sDeviceState.primaryUser())) {
            // Verify that the intents resolve into cross-profile forwarder.
            assertCrossProfileIntentsResolvability(
                    OPENING_INTENTS, workToPersonalForwarder, /* expectForwardable */ true);
        }
    }

    @Test
    @Postsubmit(reason = "new test")
    @EnsureHasWorkProfile
    public void sharingFromPersonalToWork_disallowShareIntoProfile_restrictionApplied() {
        ResolveInfo personalToWorkForwarder = getPersonalToWorkForwarder();

        // Enforce the restriction and wait for it to be applied.
        setSharingIntoProfileEnabled(false);

        try {
            // Verify that sharing intent doesn't get resolve into profile forwarder.
            assertCrossProfileIntentsResolvability(
                    SHARING_INTENTS, personalToWorkForwarder, /* expectForwardable */ false);
        } finally {
            setSharingIntoProfileEnabled(true);
        }

    }

    @Test
    @Postsubmit(reason = "new test")
    @EnsureHasWorkProfile
    public void sharingFromPersonalToWork_disallowShareIntoProfile_restrictionRemoved() {
        try (TestAppInstance testApp = sTestApp.install(sDeviceState.workProfile())) {
            ResolveInfo personalToWorkForwarder = getPersonalToWorkForwarder();

            // Enforce the restriction and wait for it to be applied, then remove it and wait again.
            setSharingIntoProfileEnabled(false);
            setSharingIntoProfileEnabled(true);

            // Verify that sharing intent gets resolved into profile forwarder successfully.
            assertCrossProfileIntentsResolvability(
                    SHARING_INTENTS, personalToWorkForwarder, /* expectForwardable */ true);
        }
    }

    @ApiTest(apis = "android.app.admin.DevicePolicyManager#addCrossProfileIntentFilter")
    @Postsubmit(reason = "new test")
    @Test
    @EnsureHasWorkProfile(dpcIsPrimary = true)
    public void addCrossProfileIntentFilter_switchToOtherProfile_chooserActivityLaunched() {
        try {
            IntentFilter sendIntentFilter = IntentFilter.create(ACTION_SEND, /* dataType= */ "*/*");
            sendIntentFilter.addCategory(CATEGORY_DEFAULT);
            sDeviceState.dpc().devicePolicyManager().addCrossProfileIntentFilter(
                    sDeviceState.dpc().componentName(), sendIntentFilter,
                    FLAG_MANAGED_CAN_ACCESS_PARENT | FLAG_PARENT_CAN_ACCESS_MANAGED);
            Intent switchToOtherProfileIntent = getSwitchToOtherProfileIntent();

            TestApis.context().instrumentedContext().startActivity(switchToOtherProfileIntent);

            ComponentName chooserActivityComponent = TestApis.activities()
                    .getTargetActivityOfIntent(CHOOSER_INTENT,
                            PackageManager.MATCH_DEFAULT_ONLY).componentName();

            Poll.forValue("Chooser activity launched",
                            () -> TestApis.activities().foregroundActivity().componentName())
                    .toBeEqualTo(chooserActivityComponent)
                    .errorOnFail()
                    .await();
        } finally {
            sDeviceState.dpc().devicePolicyManager().clearCrossProfileIntentFilters(
                    sDeviceState.dpc().componentName());
        }
    }

    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#createAdminSupportIntent",
            "android.os.UserManager#DISALLOW_CROSS_PROFILE_COPY_PASTE"})
    @Postsubmit(reason = "new test")
    @EnsureHasUserRestriction(DISALLOW_CROSS_PROFILE_COPY_PASTE)
    @Test
    public void createAdminSupportIntent_disallowCrossProfileCopyPaste_createsIntent() {
        Intent intent = TestApis.devicePolicy().createAdminSupportIntent(
                DISALLOW_CROSS_PROFILE_COPY_PASTE);

        assertThat(intent.getStringExtra(EXTRA_RESTRICTION))
                .isEqualTo(DISALLOW_CROSS_PROFILE_COPY_PASTE);
    }

    @ApiTest(apis = {"android.app.admin.DevicePolicyManager#createAdminSupportIntent",
            "android.os.UserManager#DISALLOW_CROSS_PROFILE_COPY_PASTE"})
    @Postsubmit(reason = "new test")
    @EnsureDoesNotHaveUserRestriction(DISALLOW_CROSS_PROFILE_COPY_PASTE)
    @Test
    public void createAdminSupportIntent_allowCrossProfileCopyPaste_doesNotCreate() {
        Intent intent = TestApis.devicePolicy().createAdminSupportIntent(
                DISALLOW_CROSS_PROFILE_COPY_PASTE);

        assertThat(intent).isNull();
    }

    private Intent getSwitchToOtherProfileIntent() {
        ResolveInfoWrapper switchToOtherProfileResolveInfo = getSwitchToOtherProfileResolveInfo(
        );
        assertWithMessage("Could not retrieve the switch to other profile resolve info.")
                .that(switchToOtherProfileResolveInfo)
                .isNotNull();

        Intent switchToOtherProfileIntent = new Intent(CHOOSER_INTENT);
        ActivityInfo activityInfo = switchToOtherProfileResolveInfo.activityInfo();
        switchToOtherProfileIntent.setComponent(
                new ComponentName(activityInfo.packageName, activityInfo.name));
        switchToOtherProfileIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return switchToOtherProfileIntent;
    }

    /**
     * Returns the ResolveInfo of the "Switch Profiles" dialog if one exists for this intent
     * (otherwise null).
     */
    private ResolveInfoWrapper getSwitchToOtherProfileResolveInfo() {
        // match == 0 means that this intent actually doesn't match to any activity on this profile,
        // that means it should be on the other profile.

        return TestApis.packages().queryIntentActivities(CrossProfileSharingTest.SEND_INTENT,
                        android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
                .stream()
                .filter(r -> r.match() == 0)
                .findFirst()
                .orElse(null);
    }

    private ResolveInfo getPersonalToWorkForwarder() {
        return getResolveInfo(sDeviceState.workProfile(), FLAG_MANAGED_CAN_ACCESS_PARENT);
    }

    private ResolveInfo getWorkToPersonalForwarder() {
        return getResolveInfo(sDeviceState.primaryUser(), FLAG_PARENT_CAN_ACCESS_MANAGED);
    }

    /**
     * Finds ResolveInfo for a system activity that forwards cross-profile intent by resolving an
     * intent that it handled in one profile from the other profile.
     * TODO(b/198759180): replace it with a @TestApi
     */
    private ResolveInfo getResolveInfo(UserReference targetProfile, int direction) {
        ResolveInfo forwarderInfo;
        try (TestAppInstance testApp = sTestApp.install(targetProfile)) {
            // Set up cross profile intent filters so we can resolve these to find out framework's
            // intent forwarder activity as ground truth
            sDeviceState.profileOwner(WORK_PROFILE).devicePolicyManager()
                    .addCrossProfileIntentFilter(sDeviceState.profileOwner(WORK_PROFILE)
                                    .componentName(),
                            new IntentFilter(CROSS_PROFILE_ACTION), direction);
            try {
                forwarderInfo = getCrossProfileIntentForwarder(new Intent(CROSS_PROFILE_ACTION));
            } finally {
                sDeviceState.profileOwner(WORK_PROFILE).devicePolicyManager()
                        .clearCrossProfileIntentFilters(
                                sDeviceState.profileOwner(WORK_PROFILE).componentName());
            }
        }
        return forwarderInfo;
    }

    private ResolveInfo getCrossProfileIntentForwarder(Intent intent) {
        List<ResolveInfo> result = TestApis.context().instrumentedContext().getPackageManager()
                .queryIntentActivities(intent, MATCH_DEFAULT_ONLY)
                .stream().filter(ResolveInfo::isCrossProfileIntentForwarderActivity)
                .collect(Collectors.toList());

        assertWithMessage("Failed to get intent forwarder component")
                .that(result.size()).isEqualTo(1);

        return result.get(0);
    }

    private void setSharingIntoProfileEnabled(boolean enabled) {
        RemoteDpc remoteDpc = sDeviceState.profileOwner(WORK_PROFILE);
        IntentFilter filter = new IntentFilter(ACTION_DATA_SHARING_RESTRICTION_APPLIED);
        Context remoteCtx = TestApis.context().androidContextAsUser(remoteDpc.user());
        try (PermissionContext permissionContext =
                     TestApis.permissions().withPermission(INTERACT_ACROSS_USERS_FULL);
             BlockingBroadcastReceiver receiver =
                     BlockingBroadcastReceiver.create(remoteCtx, filter).register()) {
            if (enabled) {
                remoteDpc.devicePolicyManager().clearUserRestriction(
                        remoteDpc.componentName(), DISALLOW_SHARE_INTO_MANAGED_PROFILE);
            } else {
                remoteDpc.devicePolicyManager().addUserRestriction(
                        remoteDpc.componentName(), DISALLOW_SHARE_INTO_MANAGED_PROFILE);
            }
        }
    }

    private void assertCrossProfileIntentsResolvability(
            Intent[] intents, ResolveInfo expectedForwarder, boolean expectForwardable) {
        for (Intent intent : intents) {
            List<ResolveInfo> resolveInfoList = TestApis.context().instrumentedContext()
                    .getPackageManager().queryIntentActivities(intent, MATCH_DEFAULT_ONLY);
            if (expectForwardable) {
                assertWithMessage(String.format(
                        "Expect %s to be forwardable, but resolve list does not contain expected "
                                + "intent forwarder %s", intent, expectedForwarder))
                        .that(containsResolveInfo(resolveInfoList, expectedForwarder)).isTrue();
            } else {
                assertWithMessage(String.format(
                        "Expect %s not to be forwardable, but resolve list contains intent "
                                + "forwarder %s", intent, expectedForwarder))
                        .that(containsResolveInfo(resolveInfoList, expectedForwarder)).isFalse();
            }
        }
    }

    private boolean containsResolveInfo(List<ResolveInfo> list, ResolveInfo info) {
        for (ResolveInfo entry : list) {
            if (entry.activityInfo.packageName.equals(info.activityInfo.packageName)
                    && entry.activityInfo.name.equals(info.activityInfo.name)) {
                return true;
            }
        }
        return false;
    }
}
