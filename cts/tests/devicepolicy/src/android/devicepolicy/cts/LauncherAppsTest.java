/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static com.android.bedstead.nene.permissions.CommonPermissions.CHANGE_COMPONENT_ENABLED_STATE;
import static com.android.queryable.queries.ActivityQuery.activity;
import static com.android.queryable.queries.IntentFilterQuery.intentFilter;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.os.Process;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureDoesNotHavePermission;
import com.android.bedstead.harrier.annotations.EnsureHasPermission;
import com.android.bedstead.harrier.annotations.RequireNotHeadlessSystemUserMode;
import com.android.bedstead.harrier.annotations.parameterized.IncludeRunOnParentOfProfileOwnerWithNoDeviceOwner;
import com.android.bedstead.harrier.annotations.parameterized.IncludeRunOnPrimaryUserWithNoDpc;
import com.android.bedstead.harrier.annotations.parameterized.IncludeRunOnProfileOwnerProfileWithNoDeviceOwner;
import com.android.bedstead.harrier.annotations.parameterized.IncludeRunOnSystemDeviceOwnerUser;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.testapp.TestApp;
import com.android.bedstead.testapp.TestAppActivityReference;
import com.android.bedstead.testapp.TestAppInstance;
import com.android.queryable.info.ActivityInfo;
import com.android.queryable.queries.Query;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(BedsteadJUnit4.class)
public final class LauncherAppsTest {
    private static final Context sContext = TestApis.context().instrumentedContext();
    private static final LauncherApps sLauncherApps = sContext.getSystemService(LauncherApps.class);
    private static final String SYNTHETIC_APP_DETAILS_ACTIVITY = "android.app.AppDetailsActivity";
    private static final PackageManager sPackageManager = sContext.getPackageManager();


    @ClassRule @Rule
    public static DeviceState sDeviceState = new DeviceState();


    private static final Query<ActivityInfo> MAIN_ACTIVITY_QUERY =
            activity()
                    .where().exported().isTrue()
                    .where().intentFilters().contains(
                            intentFilter()
                                    .where().actions().contains(Intent.ACTION_MAIN)
                                    .where().categories().contains(Intent.CATEGORY_LAUNCHER)
                    );

    private static final TestApp sTestApp = sDeviceState.testApps().query()
            .whereActivities().contains(MAIN_ACTIVITY_QUERY)
            .whereTestOnly().isTrue().get();

    // TODO(257215214): Unrelated to device policy - move to PackageManager module
    @Test
    public void resolveActivity_invalid_doesNotCrash() {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName("invalidPackage", "invalidClass"));

        // Test that resolving invalid intent does not crash launcher
        assertThat(sLauncherApps.resolveActivity(intent, Process.myUserHandle())).isNull();
    }

    @Test
    @IncludeRunOnSystemDeviceOwnerUser
    // Device Owner devices should not show hidden apps
    @IncludeRunOnProfileOwnerProfileWithNoDeviceOwner // Work profiles should not show hidden apps
    public void getActivityList_activityIsDisabled_isNotIncludedInList() {
        // We install on the DPC user so that this installs in the work profile when there is one
        try (TestAppInstance app = sTestApp.install(sDeviceState.dpc().user())) {
            disableMainActivity(app);

            List<LauncherActivityInfo> launcherActivities = sLauncherApps.getActivityList(
                    app.packageName(), app.user().userHandle());

            assertThat(launcherActivities).isEmpty();
        }
    }


    @Test
    @IncludeRunOnPrimaryUserWithNoDpc // Synthetic activities should be injected in general
    @IncludeRunOnParentOfProfileOwnerWithNoDeviceOwner // Should be injected for personal users with a work profile
    @RequireNotHeadlessSystemUserMode(reason = "b/257217938 functionality is broken on headless")
    public void getActivityList_launcherActivityIsDisabled_syntheticActivityIsIncluded() {
        try (TestAppInstance app = sTestApp.install()) {
            disableMainActivity(app);

            List<LauncherActivityInfo> activities = sLauncherApps.getActivityList(
                    app.packageName(), app.user().userHandle());

            assertSyntheticActivityIsIncluded(activities);
        }
    }

    // TODO(257215214): Unrelated to device policy - move to PackageManager module
    @Test
    @EnsureDoesNotHavePermission(CHANGE_COMPONENT_ENABLED_STATE)
    public void setSyntheticAppDetailsActivityEnabled_noPermission_throwsException() {
        try (TestAppInstance app = sTestApp.install()) {
            assertThrows(SecurityException.class, () ->
                    sPackageManager.setSyntheticAppDetailsActivityEnabled(
                            app.packageName(), /* enabled= */ false));
        }
    }

    // TODO(257215214): Unrelated to device policy - move to PackageManager module
    @Test
    @EnsureHasPermission(CHANGE_COMPONENT_ENABLED_STATE)
    public void setSyntheticAppDetailsActivityEnabled_true_isEnabled() {
        try (TestAppInstance app = sTestApp.install()) {
            disableMainActivity(app);

            sPackageManager.setSyntheticAppDetailsActivityEnabled(
                    app.packageName(), /* enabled= */ true);

            assertThat(app.testApp().pkg().syntheticAppDetailsActivityEnabled()).isTrue();
        }
    }

    // TODO(257215214): Unrelated to device policy - move to PackageManager module
    @Test
    @EnsureHasPermission(CHANGE_COMPONENT_ENABLED_STATE)
    @RequireNotHeadlessSystemUserMode(reason = "b/257217938 functionality is broken on headless")
    public void setSyntheticAppDetailsActivityEnabled_true_activityListIncludesSyntheticActivity() {
        try (TestAppInstance app = sTestApp.install()) {
            disableMainActivity(app);

            sPackageManager.setSyntheticAppDetailsActivityEnabled(
                    app.packageName(), /* enabled= */ true);

            List<LauncherActivityInfo> activities = sLauncherApps.getActivityList(
                    app.packageName(), app.user().userHandle());
            assertSyntheticActivityIsIncluded(activities);
        }
    }

    // TODO(257215214): Unrelated to device policy - move to PackageManager module
    @Test
    @EnsureHasPermission(CHANGE_COMPONENT_ENABLED_STATE)
    public void setSyntheticAppDetailsActivityEnabled_false_isNotEnabled() {
        try (TestAppInstance app = sTestApp.install()) {
            try {
                disableMainActivity(app);

                sPackageManager.setSyntheticAppDetailsActivityEnabled(
                        app.packageName(), /* enabled= */ false);

                assertThat(app.testApp().pkg().syntheticAppDetailsActivityEnabled()).isFalse();
            } finally {
                app.testApp().pkg().setSyntheticAppDetailsActivityEnabled(true);
            }
        }
    }

    // TODO(257215214): Unrelated to device policy - move to PackageManager module
    @Test
    @EnsureHasPermission(CHANGE_COMPONENT_ENABLED_STATE)
    public void setSyntheticAppDetailsActivityEnabled_false_activityListDoesNotIncludeSyntheticActivity() {
        try (TestAppInstance app = sTestApp.install()) {
            try {
                disableMainActivity(app);

                sPackageManager.setSyntheticAppDetailsActivityEnabled(
                        app.packageName(), /* enabled= */ false);

                List<LauncherActivityInfo> activities = sLauncherApps.getActivityList(
                        app.packageName(), app.user().userHandle());
                assertThat(activities).isEmpty();
            } finally {
                app.testApp().pkg().setSyntheticAppDetailsActivityEnabled(true);
            }
        }
    }

    private void disableMainActivity(TestAppInstance testApp) {
        TestAppActivityReference activity = testApp.activities().query()
                .whereActivity().exported().isTrue()
                .whereActivity().intentFilters().contains(
                        intentFilter()
                                .where().actions().contains(Intent.ACTION_MAIN)
                                .where().categories().contains(Intent.CATEGORY_LAUNCHER)
                ).get();
        activity.component().disable();
    }

    private void assertSyntheticActivityIsIncluded(List<LauncherActivityInfo> activities) {
        assertThat(activities).isNotEmpty();
        for (LauncherActivityInfo info : activities) {
            assertThat(info.getName()).isEqualTo(SYNTHETIC_APP_DETAILS_ACTIVITY);
            assertThat(TestApis.packages().activity(info.getComponentName()).isEnabled())
                    .isTrue();
            assertThat(TestApis.packages().activity(info.getComponentName()).isExported())
                    .isTrue();
        }
    }
}
