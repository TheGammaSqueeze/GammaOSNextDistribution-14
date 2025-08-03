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

package android.devicepolicy.cts;


import static com.android.bedstead.nene.packages.CommonPackages.FEATURE_BACKUP;
import static com.android.bedstead.nene.permissions.CommonPermissions.BACKUP;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeFalse;
import static org.testng.Assert.assertThrows;

import android.app.admin.SecurityLog;
import android.app.admin.flags.Flags;
import android.app.backup.BackupManager;
import android.content.ComponentName;
import android.content.Context;

import com.android.bedstead.flags.annotations.RequireFlagsEnabled;
import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureHasPermission;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.RequireFeature;
import com.android.bedstead.harrier.annotations.enterprise.CanSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyAppliesTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyDoesNotApplyTest;
import com.android.bedstead.harrier.policies.Backup;
import com.android.bedstead.harrier.policies.BackupAndSecurityLogging;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.utils.Poll;
import com.android.compatibility.common.util.ApiTest;

import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(BedsteadJUnit4.class)
@RequireFeature(FEATURE_BACKUP)
public final class BackupTest {
    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final Context sContext = TestApis.context().instrumentedContext();
    private static final BackupManager sLocalBackupManager = new BackupManager(sContext);

    @PolicyAppliesTest(policy = Backup.class)
    @EnsureHasPermission(BACKUP)
    @Postsubmit(reason = "new test")
    public void isBackupEnabled_default_returnsFalse() {
        assertThat(sLocalBackupManager.isBackupEnabled()).isFalse();
    }

    @PolicyAppliesTest(policy = Backup.class)
    @Postsubmit(reason = "new test")
    public void isBackupServiceEnabled_default_returnsFalse() {
        assertThat(sDeviceState.dpc().devicePolicyManager().isBackupServiceEnabled(
                sDeviceState.dpc().componentName())).isFalse();
    }

    @PolicyAppliesTest(policy = Backup.class)
    @Postsubmit(reason = "new test")
    @EnsureHasPermission(BACKUP)
    public void setBackupServiceEnabled_true_setsBackupServiceEnabled() {
        assumeFalse("Logic is special cased on headless system user",
                TestApis.users().instrumented().type()
                        .name().equals("android.os.usertype.system.HEADLESS"));

        try {
            sDeviceState.dpc().devicePolicyManager().setBackupServiceEnabled(
                    sDeviceState.dpc().componentName(), true);

            Poll.forValue("DPC isBackupServiceEnabled",
                    () -> sDeviceState.dpc().devicePolicyManager().isBackupServiceEnabled(
                            sDeviceState.dpc().componentName()))
                    .toBeEqualTo(true)
                    .errorOnFail()
                    .await();
            assertThat(sLocalBackupManager
                    .isBackupServiceActive(TestApis.users().instrumented().userHandle())).isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setBackupServiceEnabled(
                    sDeviceState.dpc().componentName(), false);
        }
    }

    @PolicyAppliesTest(policy = Backup.class)
    @Postsubmit(reason = "new test")
    @EnsureHasPermission(BACKUP)
    public void setBackupServiceEnabled_false_setsBackupServiceNotEnabled() {
        try {
            sDeviceState.dpc().devicePolicyManager().setBackupServiceEnabled(
                    sDeviceState.dpc().componentName(), false);


            Poll.forValue("DPC isBackupServiceEnabled",
                            () -> sDeviceState.dpc().devicePolicyManager().isBackupServiceEnabled(
                                    sDeviceState.dpc().componentName()))
                    .toBeEqualTo(false)
                    .errorOnFail()
                    .await();
            assertThat(sLocalBackupManager
                    .isBackupServiceActive(TestApis.users().instrumented().userHandle())).isFalse();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setBackupServiceEnabled(
                    sDeviceState.dpc().componentName(), false);
        }
    }

    @PolicyDoesNotApplyTest(policy = Backup.class)
    @Postsubmit(reason = "new test")
    @EnsureHasPermission(BACKUP)
    @Ignore("b/221087493 weird behavior regarding if it applies to a parent of a profile owner")
    public void setBackupServiceEnabled_doesNotApply_doesNotSetBackupServiceEnabled() {
        try {
            sDeviceState.dpc().devicePolicyManager().setBackupServiceEnabled(
                    sDeviceState.dpc().componentName(), true);

            assertThat(sLocalBackupManager
                    .isBackupServiceActive(TestApis.users().instrumented().userHandle())).isFalse();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setBackupServiceEnabled(
                    sDeviceState.dpc().componentName(), false);
        }
    }

    @CannotSetPolicyTest(policy = Backup.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "new test")
    public void setBackupServiceEnabled_cannotSetPolicy_throwsException() {
        assertThrows(SecurityException.class, () -> {
            sDeviceState.dpc().devicePolicyManager().setBackupServiceEnabled(
                    sDeviceState.dpc().componentName(), true);
        });
    }

    @CannotSetPolicyTest(policy = Backup.class, includeNonDeviceAdminStates = false)
    @Postsubmit(reason = "new test")
    public void isBackupServiceEnabled_cannotSetPolicy_throwsException() {
        assertThrows(SecurityException.class, () -> {
            sDeviceState.dpc().devicePolicyManager().isBackupServiceEnabled(
                    sDeviceState.dpc().componentName());
        });
    }

    @CanSetPolicyTest(policy = Backup.class)
    @Postsubmit(reason = "new test")
    public void isBackupServiceEnabled_canSetPolicy_doesNotThrow() {
        sDeviceState.dpc().devicePolicyManager().isBackupServiceEnabled(
                sDeviceState.dpc().componentName());
    }

    /** Positive test for SecurityLog#TAG_BACKUP_SERVICE_TOGGLED */
    @CanSetPolicyTest(policy = {BackupAndSecurityLogging.class})
    @RequireFlagsEnabled(Flags.FLAG_BACKUP_SERVICE_SECURITY_LOG_EVENT_ENABLED)
    @ApiTest(apis = {"android.app.admin.SecurityLog#TAG_BACKUP_SERVICE_TOGGLED"})
    @Postsubmit(reason = "new test")
    public void setBackupServiceEnabled_enableBackup_SecurityLogEventsEmitted()
            throws Exception {
        ensureNoAdditionalFullUsers();
        ComponentName admin = sDeviceState.dpc().componentName();
        boolean backupState = sDeviceState.dpc().devicePolicyManager()
                .isBackupServiceEnabled(admin);

        try {
            // Start with backup disabled
            sDeviceState.dpc().devicePolicyManager().setBackupServiceEnabled(admin, false);
            // Flush any existing security logs
            sDeviceState.dpc().devicePolicyManager().setSecurityLoggingEnabled(admin, false);
            sDeviceState.dpc().devicePolicyManager().setSecurityLoggingEnabled(admin, true);

            // Enabling backup service and check security log
            sDeviceState.dpc().devicePolicyManager().setBackupServiceEnabled(admin, true);
            TestApis.devicePolicy().forceSecurityLogs();
            List<SecurityLog.SecurityEvent> logs = sDeviceState.dpc().devicePolicyManager()
                    .retrieveSecurityLogs(admin).stream()
                    .filter(e -> e.getTag() == SecurityLog.TAG_BACKUP_SERVICE_TOGGLED).toList();
            assertWithMessage("Incorrect number of log events returned after enabling backup")
                    .that(logs).hasSize(1);
            assertThat(logs.get(0).getStringData(0)).isEqualTo(admin.getPackageName());
            assertThat(logs.get(0).getIntegerData(1)).isEqualTo(sDeviceState.dpc().user().id());
            assertThat(logs.get(0).getIntegerData(2)).isEqualTo(/* enabled */ 1);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setSecurityLoggingEnabled(admin, false);
            sDeviceState.dpc().devicePolicyManager().setBackupServiceEnabled(admin, backupState);
        }
    }

    /** Positive test for SecurityLog#TAG_BACKUP_SERVICE_TOGGLED */
    @CanSetPolicyTest(policy = {BackupAndSecurityLogging.class})
    @RequireFlagsEnabled(Flags.FLAG_BACKUP_SERVICE_SECURITY_LOG_EVENT_ENABLED)
    @ApiTest(apis = {"android.app.admin.SecurityLog#TAG_BACKUP_SERVICE_TOGGLED"})
    @Postsubmit(reason = "new test")
    public void setBackupServiceEnabled_disableBackup_SecurityLogEventsEmitted()
            throws Exception {
        ensureNoAdditionalFullUsers();
        ComponentName admin = sDeviceState.dpc().componentName();
        boolean backupState = sDeviceState.dpc().devicePolicyManager()
                .isBackupServiceEnabled(admin);

        try {
            // Start with backup enabled
            sDeviceState.dpc().devicePolicyManager().setBackupServiceEnabled(admin, true);
            // Flush any existing security logs
            sDeviceState.dpc().devicePolicyManager().setSecurityLoggingEnabled(admin, false);
            sDeviceState.dpc().devicePolicyManager().setSecurityLoggingEnabled(admin, true);

            // Disabling backup service and check security log
            sDeviceState.dpc().devicePolicyManager().setBackupServiceEnabled(admin, false);
            TestApis.devicePolicy().forceSecurityLogs();
            List<SecurityLog.SecurityEvent> logs = sDeviceState.dpc().devicePolicyManager()
                    .retrieveSecurityLogs(admin).stream()
                    .filter(e -> e.getTag() == SecurityLog.TAG_BACKUP_SERVICE_TOGGLED).toList();
            assertWithMessage("Incorrect number of log events returned after disabling backup")
                    .that(logs).hasSize(1);
            assertThat(logs.get(0).getStringData(0)).isEqualTo(admin.getPackageName());
            assertThat(logs.get(0).getIntegerData(1)).isEqualTo(sDeviceState.dpc().user().id());
            assertThat(logs.get(0).getIntegerData(2)).isEqualTo(/* enabled */ 0);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setSecurityLoggingEnabled(admin, false);
            sDeviceState.dpc().devicePolicyManager().setBackupServiceEnabled(admin, backupState);
        }
    }
    private void ensureNoAdditionalFullUsers() {
        // TODO(273474964): Move into infra
        try {
            TestApis.users().all().stream().filter(u -> (u != TestApis.users().instrumented()
                    && u != TestApis.users().system()
                    && u != TestApis.users().current() // We can't remove the profile of
                    // the instrumented user for the run on parent profile tests. But the profiles
                    // of other users will be removed when the full-user is removed anyway.
                    && !u.isProfile())).forEach(u -> u.remove());
        } catch (NeneException e) {
            // Happens when we can't remove a user
            throw new NeneException(
                    "Error when removing user. Instrumented user is "
                            + TestApis.users().instrumented() + ", current user is "
                            + TestApis.users().current() + ", system user is "
                            + TestApis.users().system(), e
            );
        }
    }
}
