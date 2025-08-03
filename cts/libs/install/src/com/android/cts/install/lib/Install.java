/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.cts.install.lib;

import static com.google.common.truth.Truth.assertThat;

import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.text.TextUtils;

import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.compatibility.common.util.SystemUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

/**
 * Builder class for installing test apps and creating install sessions.
 */
public class Install {
    // The collection of apps to be installed with parameters inherited from parent Install object.
    private final TestApp[] mTestApps;
    // The collection of apps to be installed with parameters independent of parent Install object.
    private final Install[] mChildInstalls;
    // Indicates whether Install represents a multiPackage install.
    private final boolean mIsMultiPackage;
    // PackageInstaller.Session parameters.
    private String mPackageName = null;
    private boolean mIsStaged = false;
    private boolean mIsDowngrade = false;
    private boolean mEnableRollback = false;
    private int mRollbackDataPolicy = 0;
    private long mLifetimeMillis = 0;
    private int mRollbackImpactLevel = 0;
    private int mSessionMode = PackageInstaller.SessionParams.MODE_FULL_INSTALL;
    private int mInstallFlags = 0;
    private boolean mBypassAllowedApexUpdateCheck = true;
    private boolean mBypassStagedInstallerCheck = true;
    private long mTimeoutMillis = TimeUnit.MINUTES.toMillis(5);

    private boolean mDisableVerifier = true;

    private Install(boolean isMultiPackage, TestApp... testApps) {
        mIsMultiPackage = isMultiPackage;
        mTestApps = testApps;
        mChildInstalls = new Install[0];
    }

    private Install(boolean isMultiPackage, Install... installs) {
        mIsMultiPackage = isMultiPackage;
        mTestApps = new TestApp[0];
        mChildInstalls = installs;
    }

    /**
     * Creates an Install builder to install a single package.
     */
    public static Install single(TestApp testApp) {
        return new Install(false, testApp);
    }

    /**
     * Creates an Install builder to install using multiPackage.
     */
    public static Install multi(TestApp... testApps) {
        return new Install(true, testApps);
    }

    /**
     * Creates an Install builder from separate Install builders. The newly created builder
     * will be responsible for building the parent session, while each one of the other builders
     * will be responsible for building one of the child sessions.
     *
     * <p>Modifications to the parent install are not propagated to the child installs,
     * and vice versa. This gives more control over a multi install session,
     * e.g. can setStaged on a subset of the child sessions or setStaged on a child session but
     * not on the parent session.
     *
     * <p>It's encouraged to use {@link #multi} that receives {@link TestApp}s
     * instead of {@link Install}s. This variation of {@link #multi} should be used only if it's
     * necessary to modify parameters in a subset of the installed sessions.
     */
    public static Install multi(Install... installs) {
        for (Install childInstall : installs) {
            assertThat(childInstall.isMultiPackage()).isFalse();
        }
        Install install = new Install(true, installs);
        return install;
    }

    /**
     * Sets package name to the session params.
     */
    public Install setPackageName(String packageName) {
        mPackageName = packageName;
        return this;
    }

    /**
     * Makes the install a staged install.
     */
    public Install setStaged() {
        mIsStaged = true;
        return this;
    }

    /**
     * Marks the install as a downgrade.
     */
    public Install setRequestDowngrade() {
        mIsDowngrade = true;
        return this;
    }

    /**
     * Enables rollback for the install.
     */
    public Install setEnableRollback() {
        mEnableRollback = true;
        return this;
    }

    /**
     * Enables rollback for the install with specified rollback data policy.
     */
    public Install setEnableRollback(int dataPolicy) {
        mEnableRollback = true;
        mRollbackDataPolicy = dataPolicy;
        return this;
    }

    /**
     * Sets lifetimeMillis for rollback expiration.
     */
    public Install setRollbackLifetimeMillis(long lifetimeMillis) {
        mLifetimeMillis = lifetimeMillis;
        return this;
    }

    /**
     * Sets rollbackImpactLevel for the install.
     */
    public Install setRollbackImpactLevel(int impactLevel) {
        mRollbackImpactLevel = impactLevel;
        return this;
    }

    /**
     * Sets the session mode {@link PackageInstaller.SessionParams#MODE_INHERIT_EXISTING}.
     * If it's not set, then the default session mode is
     * {@link PackageInstaller.SessionParams#MODE_FULL_INSTALL}
     */
    public Install setSessionMode(int sessionMode) {
        mSessionMode = sessionMode;
        return this;
    }

    /**
     * Sets the session params.
     */
    public Install addInstallFlags(int installFlags) {
        mInstallFlags |= installFlags;
        return this;
    }

    /**
     * Sets whether to call {@code pm bypass-allowed-apex-update-check true} when creating install
     * session.
     */
    public Install setBypassAllowedApexUpdateCheck(boolean bypassAllowedApexUpdateCheck) {
        mBypassAllowedApexUpdateCheck = bypassAllowedApexUpdateCheck;
        return this;
    }

    /**
     * Sets whether to call {@code pm bypass-staged-installer-check true} when creating install
     * session.
     */
    public Install setBypassStangedInstallerCheck(boolean bypassStagedInstallerCheck) {
        mBypassStagedInstallerCheck = bypassStagedInstallerCheck;
        return this;
    }

    /**
     * Sets the installation timeout. {@link #commit()} will fail if install doesn't
     * complete within the timeout. The default is 5 minutes.
     */
    public Install setTimeout(long timeoutMillis) {
        mTimeoutMillis = timeoutMillis;
        return this;
    }

    /**
     * Enable verifier for testing purpose. The default is to disable the verifier.
     */
    public Install enableVerifier() {
        mDisableVerifier = false;
        return this;
    }

    /**
     * Commits the install.
     *
     * @return the session id of the install session, if the session is successful.
     * @throws AssertionError if the install doesn't succeed.
     */
    public int commit() throws IOException, InterruptedException {
        int sessionId = createSession();
        try (PackageInstaller.Session session =
                     InstallUtils.openPackageInstallerSession(sessionId)) {
            LocalIntentSender sender = new LocalIntentSender();
            session.commit(sender.getIntentSender());
            Intent result = sender.pollResult(mTimeoutMillis, TimeUnit.MILLISECONDS);
            if (result == null) {
                throw new AssertionError("Install timeout, sessionId=" + sessionId);
            }
            InstallUtils.assertStatusSuccess(result);
            return sessionId;
        }
    }

    /**
     * Kicks off an install flow by creating an install session
     * and, in the case of a multiPackage install, child install sessions.
     *
     * @return the session id of the install session, if the session is successful.
     */
    public int createSession() throws IOException {
        int sessionId;
        if (isMultiPackage()) {
            sessionId = createEmptyInstallSession(/*multiPackage*/ true, /*isApex*/false);
            try (PackageInstaller.Session session =
                         InstallUtils.openPackageInstallerSession(sessionId)) {
                for (Install subInstall : mChildInstalls) {
                    session.addChildSessionId(subInstall.createSession());
                }
                for (TestApp testApp : mTestApps) {
                    session.addChildSessionId(createSingleInstallSession(testApp));
                }
            }
        } else {
            assert mTestApps.length == 1;
            sessionId = createSingleInstallSession(mTestApps[0]);
        }
        return sessionId;
    }

    /**
     * Creates an empty install session with appropriate install params set.
     *
     * @return the session id of the newly created session
     */
    private int createEmptyInstallSession(boolean multiPackage, boolean isApex)
            throws IOException {
        if ((mIsStaged || isApex) && mBypassStagedInstallerCheck) {
            SystemUtil.runShellCommandForNoOutput("pm bypass-staged-installer-check true");
        }
        if (isApex && mBypassAllowedApexUpdateCheck) {
            SystemUtil.runShellCommandForNoOutput("pm bypass-allowed-apex-update-check true");
        }
        if (mDisableVerifier && ApiLevelUtil.isAfter(Build.VERSION_CODES.TIRAMISU)) {
            // This command is only available in U and later
            SystemUtil.runShellCommandForNoOutput("pm disable-verification-for-uid "
                    + android.os.Process.myUid());
        }
        try {
            PackageInstaller.SessionParams params =
                    new PackageInstaller.SessionParams(mSessionMode);
            if (!TextUtils.isEmpty(mPackageName)) {
                params.setAppPackageName(mPackageName);
            }
            if (multiPackage) {
                params.setMultiPackage();
            }
            if (isApex) {
                params.setInstallAsApex();
            }
            if (mIsStaged) {
                params.setStaged();
            }
            params.setRequestDowngrade(mIsDowngrade);
            params.setEnableRollback(mEnableRollback, mRollbackDataPolicy);
            if (mEnableRollback && mLifetimeMillis > 0) {
                params.setRollbackLifetimeMillis(mLifetimeMillis);
            }
            if (mEnableRollback && mRollbackImpactLevel >= 0) {
                params.setRollbackImpactLevel(mRollbackImpactLevel);
            }
            if (mInstallFlags != 0) {
                InstallUtils.mutateInstallFlags(params, mInstallFlags);
            }
            PackageInstaller installer = InstallUtils.getPackageInstaller();
            if (installer == null) {
                // installer may be null, eg. instant app
                throw new IllegalStateException("PackageInstaller not found");
            }
            return installer.createSession(params);
        } finally {
            if ((mIsStaged || isApex) && mBypassStagedInstallerCheck) {
                SystemUtil.runShellCommandForNoOutput("pm bypass-staged-installer-check false");
            }
            if (isApex && mBypassAllowedApexUpdateCheck) {
                SystemUtil.runShellCommandForNoOutput("pm bypass-allowed-apex-update-check false");
            }
        }
    }

    /**
     * Creates an install session for the given test app.
     *
     * @return the session id of the newly created session.
     */
    private int createSingleInstallSession(TestApp app) throws IOException {
        int sessionId = createEmptyInstallSession(/*multiPackage*/false, app.isApex());
        try (PackageInstaller.Session session =
                     InstallUtils.getPackageInstaller().openSession(sessionId)) {
            for (String resourceName : app.getResourceNames()) {
                try (OutputStream os = session.openWrite(resourceName, 0, -1);
                     InputStream is = app.getResourceStream(resourceName);) {
                    if (is == null) {
                        throw new IOException("Resource " + resourceName + " not found");
                    }
                    byte[] buffer = new byte[4096];
                    int n;
                    while ((n = is.read(buffer)) >= 0) {
                        os.write(buffer, 0, n);
                    }
                }
            }
            return sessionId;
        }
    }

    private boolean isMultiPackage() {
        return mIsMultiPackage;
    }

}
