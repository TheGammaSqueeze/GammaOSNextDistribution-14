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

package android.cts.install;

import static android.cts.install.InstallRule.VERSION_CODE_INVALID;

import static com.android.cts.install.lib.InstallUtils.getPackageInstaller;
import static com.android.cts.install.lib.PackageInstallerSessionInfoSubject.assertThat;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInstaller;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.cts.install.lib.Install;
import com.android.cts.install.lib.InstallUtils;
import com.android.cts.install.lib.TestApp;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@RunWith(Parameterized.class)
public final class InstallTest {
    @Parameter(0)
    public INSTALL_TYPE mInstallType;

    @Parameter(1)
    public boolean mStaged;

    @Parameter(2)
    public boolean mEnableRollback;

    @Parameters(name = "{0}_Staged{1}_Rollback{2}")
    public static Collection<Object[]> combinations() {
        boolean[] booleanValues = new boolean[]{true, false};
        List<Object[]> temp = new ArrayList<>();
        for (INSTALL_TYPE installType : INSTALL_TYPE.values()) {
            for (boolean staged : booleanValues) {
                for (boolean enableRollback : booleanValues) {
                    temp.add(new Object[]{installType, staged, enableRollback});
                }
            }
        }
        return temp;
    }

    @Rule
    public InstallRule mInstallRule = new InstallRule();

    @Rule
    public SessionRule mSessionRule = new SessionRule();

    private static final int VERSION_CODE_TARGET = 2;

    /**
     * Cleans up test environment.
     *
     * This is marked as @Test to take advantage of @Before/@After methods of hostside test cases.
     * Actual purpose of this method to be called before and after each test case of
     * {@link android.cts.install.host.InstallTest} to reduce tests flakiness.
     */
    @Test
    public void cleanUp_phase() throws Exception {
        mInstallRule.cleanUp();
        mSessionRule.cleanUp();
    }

    @Test
    public void action_phase() throws Exception {
        Install install = getParameterizedInstall(VERSION_CODE_TARGET);
        int sessionId = install.commit();
        mSessionRule.recordSessionId(sessionId);
    }

    @Test
    public void assert_commitFailure_phase() {
        Install install = getParameterizedInstall(VERSION_CODE_TARGET);
        if (mInstallType.equals(INSTALL_TYPE.SINGLE_APEX)) {
            InstallUtils.commitExpectingFailure(AssertionError.class,
                "does not support non-staged update", install);
        } else {
            InstallUtils.commitExpectingFailure(IllegalStateException.class,
                "Mix of APK and APEX is not supported for non-staged multi-package session",
                install);
        }
    }

    @Test
    public void assert_phase() {
        mInstallRule.assertPackageVersion(mInstallType, VERSION_CODE_TARGET);
    }

    @Test
    public void assert_preReboot_phase() throws Exception {
        assertNoSessionCommitBroadcastSent();
        assertThat(mSessionRule.retrieveSessionInfo()).isStagedSessionReady();
        mInstallRule.assertPackageVersion(mInstallType, VERSION_CODE_INVALID);
    }

    @Test
    public void assert_postReboot_phase() throws Exception {
        assertThat(mSessionRule.retrieveSessionInfo()).isStagedSessionApplied();
        mInstallRule.assertPackageVersion(mInstallType, VERSION_CODE_TARGET);
        assertNoSessionCommitBroadcastSent();
    }

    @Test
    public void action_abandonSession_phase() throws Exception {
        getPackageInstaller().abandonSession(mSessionRule.retrieveSessionId());
    }

    @Test
    public void assert_abandonSession_phase() {
        mInstallRule.assertPackageVersion(mInstallType, VERSION_CODE_INVALID);
    }

    /** Gets parameterized {@link Install} of test packages with specific version. */
    private Install getParameterizedInstall(int versionCode) {
        List<TestApp> testApps = mInstallRule.getTestApps(mInstallType, versionCode);
        Install install = testApps.size() == 1
                ? Install.single(testApps.get(0))
                : Install.multi(testApps.toArray(new TestApp[testApps.size()]));
        if (mStaged) {
            install.setStaged();
        }
        if (mEnableRollback) {
            install.setEnableRollback();
        }
        return install;
    }

    private static void assertNoSessionCommitBroadcastSent() throws InterruptedException {
        BlockingQueue<PackageInstaller.SessionInfo> committedSessions = new LinkedBlockingQueue<>();
        BroadcastReceiver sessionCommittedReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                try {
                    PackageInstaller.SessionInfo info =
                            intent.getParcelableExtra(PackageInstaller.EXTRA_SESSION);
                    committedSessions.put(info);
                } catch (InterruptedException e) {
                    throw new AssertionError(e);
                }
            }
        };

        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        context.registerReceiver(sessionCommittedReceiver,
                new IntentFilter(PackageInstaller.ACTION_SESSION_COMMITTED));

        PackageInstaller.SessionInfo info = committedSessions.poll(10, TimeUnit.SECONDS);
        context.unregisterReceiver(sessionCommittedReceiver);

        assertThat(info).isNull();
    }
}
