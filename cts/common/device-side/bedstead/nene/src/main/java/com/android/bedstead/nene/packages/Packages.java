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

package com.android.bedstead.nene.packages;

import static android.Manifest.permission.INSTALL_PACKAGES;
import static android.Manifest.permission.INTERACT_ACROSS_USERS;
import static android.Manifest.permission.INTERACT_ACROSS_USERS_FULL;
import static android.content.Intent.ACTION_VIEW;
import static android.content.pm.PackageInstaller.EXTRA_PACKAGE_NAME;
import static android.content.pm.PackageInstaller.EXTRA_STATUS;
import static android.content.pm.PackageInstaller.EXTRA_STATUS_MESSAGE;
import static android.content.pm.PackageInstaller.STATUS_FAILURE;
import static android.content.pm.PackageInstaller.STATUS_SUCCESS;
import static android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL;
import static android.content.pm.PackageManager.MATCH_ALL;
import static android.content.pm.PackageManager.MATCH_DEFAULT_ONLY;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.R;

import static com.android.bedstead.nene.permissions.CommonPermissions.INSTALL_TEST_ONLY_PACKAGE;
import static com.android.bedstead.nene.permissions.CommonPermissions.USE_SYSTEM_DATA_LOADERS;
import static com.android.compatibility.common.util.FileUtils.readInputStreamFully;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.annotation.CheckResult;
import androidx.annotation.RequiresApi;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.activities.ActivityReference;
import com.android.bedstead.nene.annotations.Experimental;
import com.android.bedstead.nene.exceptions.AdbException;
import com.android.bedstead.nene.exceptions.AdbParseException;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.permissions.PermissionContext;
import com.android.bedstead.nene.users.UserReference;
import com.android.bedstead.nene.utils.BlockingIntentSender;
import com.android.bedstead.nene.utils.Poll;
import com.android.bedstead.nene.utils.ResolveInfoWrapper;
import com.android.bedstead.nene.utils.ShellCommand;
import com.android.bedstead.nene.utils.ShellCommandUtils;
import com.android.bedstead.nene.utils.UndoableContext;
import com.android.bedstead.nene.utils.Versions;
import com.android.compatibility.common.util.BlockingBroadcastReceiver;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

/**
 * Test APIs relating to packages.
 */
public final class Packages {

    private static final String LOG_TAG = "Packages";

    /** Reference to a Java resource. */
    public static final class JavaResource {
        private final String mName;

        private JavaResource(String name) {
            mName = name;
        }

        /** Reference a Java resource by name. */
        public static JavaResource javaResource(String name) {
            if (name == null) {
                throw new NullPointerException();
            }
            return new JavaResource(name);
        }

        @Override
        public String toString() {
            return "JavaResource{name=" + mName + "}";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof JavaResource)) return false;
            JavaResource that = (JavaResource) o;
            return mName.equals(that.mName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mName);
        }
    }

    /** Reference to an Android resource. */
    public static final class AndroidResource {
        private final String mName;

        private AndroidResource(String name) {
            if (name == null) {
                throw new NullPointerException();
            }
            mName = name;
        }

        /** Reference an Android resource by name. */
        public static AndroidResource androidResource(String name) {
            return new AndroidResource(name);
        }

        @Override
        public String toString() {
            return "AndroidResource{name=" + mName + "}";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof AndroidResource)) return false;
            AndroidResource that = (AndroidResource) o;
            return mName.equals(that.mName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mName);
        }
    }

    public static final Packages sInstance = new Packages();

    private static final String PACKAGE_VERIFIER_INCLUDE_ADB = "verifier_verify_adb_installs";

    private Set<String> mFeatures = null;
    private final Context mInstrumentedContext;

    private final IntentFilter mPackageAddedIntentFilter =
            new IntentFilter(Intent.ACTION_PACKAGE_ADDED);

    private static final PackageManager sPackageManager =
            TestApis.context().instrumentedContext().getPackageManager();

    static final AdbPackageParser sParser = AdbPackageParser.get(SDK_INT);


    public Packages() {
        mPackageAddedIntentFilter.addDataScheme("package");
        mInstrumentedContext = TestApis.context().instrumentedContext();
    }

    /** Get the features available on the device. */
    public Set<String> features() {
        if (mFeatures == null) {
            mFeatures = new HashSet<>();
            PackageManager pm = TestApis.context().instrumentedContext().getPackageManager();
            FeatureInfo[] features = pm.getSystemAvailableFeatures();
            if (features != null) {
                Arrays.stream(features).map(f -> f.name).forEach(mFeatures::add);
            }
        }
        return mFeatures;
    }

    /** Get packages installed for the instrumented user. */
    public Collection<Package> installedForUser() {
        return installedForUser(TestApis.users().instrumented());
    }

    /**
     * Resolve all packages installed for a given {@link UserReference}.
     */
    public Collection<Package> installedForUser(UserReference user) {
        if (user == null) {
            throw new NullPointerException();
        }

        if (!Versions.meetsMinimumSdkVersionRequirement(R)
                || TestApis.packages().instrumented().isInstantApp()) {
            AdbPackageParser.ParseResult packages = parseDumpsys();
            return packages.mPackages.values().stream()
                    .filter(p -> p.installedOnUsers().contains(user))
                    .map(p -> find(p.packageName()))
                    .collect(Collectors.toSet());
        }

        if (user.equals(TestApis.users().instrumented())) {
            return TestApis.context().instrumentedContext().getPackageManager()
                    .getInstalledPackages(/* flags= */ 0)
                    .stream()
                    .map(i -> new Package(i.packageName))
                    .collect(Collectors.toSet());
        }

        try (PermissionContext p = TestApis.permissions()
                .withPermission(INTERACT_ACROSS_USERS_FULL)) {
            return TestApis.context().androidContextAsUser(user).getPackageManager()
                    .getInstalledPackages(/* flags= */ 0)
                    .stream()
                    .map(i -> new Package(i.packageName))
                    .collect(Collectors.toSet());
        }
    }

    /** Install the {@link File} to the instrumented user. */
    public Package install(File apkFile) {
        return install(TestApis.users().instrumented(), apkFile);
    }

    /** Install a file as a byte array to the instrumented user. */
    public Package install(byte[] apkFile) {
        return install(TestApis.users().instrumented(), apkFile);
    }

    /**
     * Install an APK file to a given {@link UserReference}.
     *
     * <p>The user must be started.
     *
     * <p>If the package is already installed, this will replace it.
     *
     * <p>If the package is marked testOnly, it will still be installed.
     *
     * <p>On versions of Android prior to Q, this will return null. On other versions it will return
     * the installed package.
     */
    @Nullable
    public Package install(UserReference user, File apkFile) {
        if (user == null || apkFile == null) {
            throw new NullPointerException();
        }

        if (Versions.meetsMinimumSdkVersionRequirement(Build.VERSION_CODES.S)) {
            return install(user, loadBytes(apkFile));
        }

        if (!user.exists()) {
            throw new NeneException("Packages can not be installed in non-existing users "
                    + "(Trying to install into user " + user + ")");
        }

        if (!user.isRunning()) {
            throw new NeneException("Packages can not be installed in stopped users "
                    + "(Trying to install into user " + user + ")");
        }

        if (!user.isUnlocked()) {
            throw new NeneException("Packages can not be installed in locked users "
                    + "(Trying to install into user " + user + ")");
        }

        try (UndoableContext verification = setVerifyAdbInstalls(false)) {
            // This is not in the try because if the install fails we don't want to await the broadcast
            BlockingBroadcastReceiver broadcastReceiver =
                    registerPackageInstalledBroadcastReceiver(user);

            try {
                Collection<Package> beforePackages = TestApis.packages().installedForUser(user);

                // Expected output "Success"
                ShellCommand.builderForUser(user, "pm install")
                        .addOperand("-r") // Reinstall automatically
                        .addOperand("-t") // Allow test-only install
                        .addOperand(apkFile.getAbsolutePath())
                        .validate(ShellCommandUtils::startsWithSuccess)
                        .execute();

                Package installedPackage = Poll.forValue("newly installed packages", () -> {
                    Set<Package> packages = new HashSet<>(
                            TestApis.packages().installedForUser(user));
                    packages.removeAll(beforePackages);
                    if (packages.isEmpty()) {
                        return null;
                    }
                    return packages.iterator().next();
                }).toNotBeNull()
                        .timeout(Duration.ofSeconds(10))
                        .await();
                if (installedPackage == null) {
                    installedPackage = waitForPackageAddedBroadcast(broadcastReceiver);
                }
                return installedPackage;
            } catch (AdbException e) {
                throw new NeneException("Could not install " + apkFile + " for user " + user, e);
            } finally {
                if (broadcastReceiver != null) {
                    broadcastReceiver.unregisterQuietly();
                }
            }
        }
    }

    // TODO: Move this somewhere reusable (in utils)
    private static byte[] loadBytes(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            return readInputStreamFully(fis);
        } catch (IOException e) {
            throw new NeneException("Could not read file bytes for file " + file);
        }
    }

    /**
     * Install an APK from the given byte array to a given {@link UserReference}.
     *
     * <p>The user must be started.
     *
     * <p>If the package is already installed, this will replace it.
     *
     * <p>If the package is marked testOnly, it will still be installed.
     *
     * <p>When running as an instant app, this will return null. On other versions it will return
     * the installed package.
     */
    @Nullable
    public Package install(UserReference user, byte[] apkFile) {
        if (user == null || apkFile == null) {
            throw new NullPointerException();
        }

        if (!user.exists()) {
            throw new NeneException("Packages can not be installed in non-existing users "
                    + "(Trying to install into user " + user + ")");
        }

        if (!user.isRunning()) {
            throw new NeneException("Packages can not be installed in stopped users "
                    + "(Trying to install into user " + user + ")");
        }

        if (!user.isUnlocked()) {
            throw new NeneException("Packages can not be installed in locked users "
                    + "(Trying to install into user " + user + ")");
        }

        try (UndoableContext verification = setVerifyAdbInstalls(false)) {
            if (TestApis.packages().instrumented().isInstantApp()) {
                // We should install using stdin with the byte array
                try {
                    ShellCommand.builderForUser(user, "pm install")
                            .addOperand("-t") // Allow installing test apks
                            .addOperand("-r") // Replace existing apps
                            .addOption("-S", apkFile.length) // Install from stdin
                            .writeToStdIn(apkFile)
                            .validate(ShellCommandUtils::startsWithSuccess)
                            .execute();
                } catch (AdbException e) {
                    throw new NeneException("Error installing from instant app", e);
                }

                // Arbitrary sleep because the shell command doesn't block and we can't listen for
                // the broadcast (instant app)
                try {
                    Thread.sleep(10_000);
                } catch (InterruptedException e) {
                    throw new NeneException("Interrupted while waiting for install", e);
                }

                return null;
            }

            if (true || !Versions.meetsMinimumSdkVersionRequirement(Build.VERSION_CODES.S)) {
                // We can't make use of -r when using SessionParams
                return installUsingAdb(user, apkFile);
            }

            // This is not inside the try because if the install is unsuccessful we don't want to
            // await the broadcast
            BlockingBroadcastReceiver broadcastReceiver =
                    registerPackageInstalledBroadcastReceiver(user);

            try {
                PackageManager packageManager =
                        TestApis.context().androidContextAsUser(user).getPackageManager();
                PackageInstaller packageInstaller = packageManager.getPackageInstaller();

                int sessionId;
                try (PermissionContext p = TestApis.permissions().withPermission(
                        INTERACT_ACROSS_USERS_FULL, INTERACT_ACROSS_USERS,
                        INSTALL_TEST_ONLY_PACKAGE, USE_SYSTEM_DATA_LOADERS)) {
                    PackageInstaller.SessionParams sessionParams =
                            new PackageInstaller.SessionParams(
                                    MODE_FULL_INSTALL);
                    sessionParams.setInstallFlagAllowTest();
                    sessionId = packageInstaller.createSession(sessionParams);
                }

                PackageInstaller.Session session = packageInstaller.openSession(sessionId);
                try (OutputStream out =
                             session.openWrite("NAME", 0, apkFile.length)) {
                    out.write(apkFile);
                    session.fsync(out);
                }

                try (BlockingIntentSender intentSender = BlockingIntentSender.create()) {
                    try (PermissionContext p =
                                 TestApis.permissions().withPermission(
                                         INSTALL_PACKAGES, INSTALL_TEST_ONLY_PACKAGE)) {
                        session.commit(intentSender.intentSender());
                        session.close();

                        Intent intent = intentSender.await();

                        if (intent == null) {
                            throw new NeneException(
                                    "Did not receive intent from package installer session when"
                                            + " installing bytes on user " + user
                                            + ". Relevant logcat: "
                                            + TestApis.logcat().dump(
                                                    l -> l.contains("PackageInstaller")));
                        }

                        if (intent.getIntExtra(EXTRA_STATUS, /* defaultValue= */ STATUS_FAILURE)
                                != STATUS_SUCCESS) {
                            throw new NeneException("Not successful while installing package. "
                                    + "Got status: "
                                    + intent.getIntExtra(
                                    EXTRA_STATUS, /* defaultValue= */ STATUS_FAILURE)
                                    + " extra info: " + intent.getStringExtra(
                                    EXTRA_STATUS_MESSAGE));
                        }

                        String installedPackageName = intent.getStringExtra(EXTRA_PACKAGE_NAME);
                        return TestApis.packages().find(installedPackageName);
                    }
                }
            } catch (IOException e) {
                throw new NeneException("Could not install package", e);
            } finally {
                if (broadcastReceiver != null) {
                    broadcastReceiver.unregisterQuietly();
                }
            }
        }
    }

    @Nullable
    private Package installUsingAdb(UserReference user, byte[] apkFile) {
        // This is not in the try because if the install fails we don't want to await the broadcast
        BlockingBroadcastReceiver broadcastReceiver =
                registerPackageInstalledBroadcastReceiver(user);

        // We should install using stdin with the byte array
        try {
            Collection<Package> beforePackages = TestApis.packages().installedForUser(user);

            ShellCommand.builderForUser(user, "pm install")
                    .addOperand("-t") // Allow installing test apks
                    .addOperand("-r") // Replace existing apps
                    .addOption("-S", apkFile.length) // Install from stdin
                    .writeToStdIn(apkFile)
                    .validate(ShellCommandUtils::startsWithSuccess)
                    .execute();

            Package installedPackage = Poll.forValue("newly installed packages", () -> {
                        Set<Package> packages = new HashSet<>(
                                TestApis.packages().installedForUser(user));
                        packages.removeAll(beforePackages);
                        if (packages.isEmpty()) {
                            return null;
                        }
                        return packages.iterator().next();
                    }).toNotBeNull()
                    .timeout(Duration.ofSeconds(10))
                    .await();

            if (installedPackage == null) {
                installedPackage = waitForPackageAddedBroadcast(broadcastReceiver);
            }
            return installedPackage;
        } catch (AdbException e) {
            throw new NeneException("Error installing package", e);
        } finally {
            if (broadcastReceiver != null) {
                broadcastReceiver.unregisterQuietly();
            }
        }
    }

    @Nullable
    private Package waitForPackageAddedBroadcast(BlockingBroadcastReceiver broadcastReceiver) {
        if (broadcastReceiver == null) {
            // On Android versions prior to R we can't block on a broadcast for package installation
            try {
                Thread.sleep(20000);
            } catch (InterruptedException e) {
                Log.e(LOG_TAG, "Interrupted waiting for package installation", e);
            }

            return null;
        }

        Intent intent = broadcastReceiver.awaitForBroadcast();
        if (intent == null) {
            throw new NeneException(
                    "Did not receive ACTION_PACKAGE_ADDED broadcast after installing package.");
        }
        // TODO(scottjonathan): Could this be flaky? what if something is added elsewhere at
        //  the same time...
        String installedPackageName = intent.getDataString().split(":", 2)[1];

        return TestApis.packages().find(installedPackageName);
    }

    /**
     * Install an APK stored in Android resources to the given {@link UserReference}.
     *
     * <p>The user must be started.
     *
     * <p>If the package is already installed, this will replace it.
     *
     * <p>If the package is marked testOnly, it will still be installed.
     */
    @Experimental
    public Package install(UserReference user, AndroidResource resource) {
        int indexId = mInstrumentedContext.getResources().getIdentifier(
                resource.mName, /* defType= */ null, /* defPackage= */ null);

        try (InputStream inputStream =
                     mInstrumentedContext.getResources().openRawResource(indexId)) {
            return install(user, readInputStreamFully(inputStream));
        } catch (IOException e) {
            throw new NeneException("Error reading resource " + resource, e);
        }
    }

    /**
     * Install an APK stored in Java resources to the given {@link UserReference}.
     *
     * <p>The user must be started.
     *
     * <p>If the package is already installed, this will replace it.
     *
     * <p>If the package is marked testOnly, it will still be installed.
     */
    @Experimental
    public Package install(UserReference user, JavaResource resource) {
        try (InputStream inputStream =
                     Packages.class.getClassLoader().getResourceAsStream(resource.mName)) {
            return install(user, readInputStreamFully(inputStream));
        } catch (IOException e) {
            throw new NeneException("Error reading java resource " + resource, e);
        }
    }

    @Nullable
    private BlockingBroadcastReceiver registerPackageInstalledBroadcastReceiver(
            UserReference user) {
        BlockingBroadcastReceiver broadcastReceiver = BlockingBroadcastReceiver.create(
                TestApis.context().androidContextAsUser(user),
                mPackageAddedIntentFilter);

        if (user.equals(TestApis.users().instrumented())) {
            broadcastReceiver.register();
        } else if (Versions.meetsMinimumSdkVersionRequirement(Build.VERSION_CODES.Q)) {
            try (PermissionContext p =
                         TestApis.permissions().withPermission(INTERACT_ACROSS_USERS_FULL)) {
                broadcastReceiver.register();
            }
        } else {
            return null;
        }

        return broadcastReceiver;
    }

    /**
     * Set packages which will not be cleaned up by the system even if they are not installed on
     * any user.
     *
     * <p>This will ensure they can still be resolved and re-installed without needing the APK
     */
    @RequiresApi(Build.VERSION_CODES.S)
    @CheckResult
    public KeepUninstalledPackagesBuilder keepUninstalledPackages() {
        Versions.requireMinimumVersion(Build.VERSION_CODES.S);

        return new KeepUninstalledPackagesBuilder();
    }

    /**
     * Get a reference to a package with the given {@code packageName}.
     *
     * <p>This does not guarantee that the package exists. Call {@link Package#exists()}
     * to find if the package exists on the device, or {@link Package#installedOnUsers()}
     * to find the users it is installed for.
     */
    public Package find(String packageName) {
        if (packageName == null) {
            throw new NullPointerException();
        }
        return new Package(packageName);
    }

    /**
     * Get a reference to a given {@code componentName} activity.
     *
     * <p>This does not guarantee that the component exists - nor that it is actually an activity.
     */
    @Experimental
    public ActivityReference activity(ComponentName componentName) {
        if (componentName == null) {
            throw new NullPointerException();
        }

        return new ActivityReference(
                find(componentName.getPackageName()), componentName.getClassName());
    }

    /**
     * Get a reference to a given {@code componentName}.
     *
     * <p>This does not guarantee that the component exists.
     */
    @Experimental
    public ComponentReference component(ComponentName componentName) {
        if (componentName == null) {
            throw new NullPointerException();
        }

        return new ComponentReference(
                find(componentName.getPackageName()), componentName.getClassName());
    }

    /** Get a reference to the package being instrumented. */
    @Experimental
    public Package instrumented() {
        return find(TestApis.context().instrumentedContext().getPackageName());
    }

    static AdbPackageParser.ParseResult parseDumpsys() {
        try {
            String dumpsysOutput = ShellCommand.builder("dumpsys package").execute();
            return Packages.sParser.parse(dumpsysOutput);
        } catch (AdbException | AdbParseException e) {
            throw new NeneException("Error parsing package dumpsys", e);
        }
    }

    /**
     * System apps installed on the instrumented user.
     */
    @Experimental
    public Set<Package> systemApps() {
        return systemApps(TestApis.users().instrumented());
    }

    /**
     * System apps installed on the given user.
     */
    @Experimental
    public Set<Package> systemApps(UserReference user) {
        return installedForUser(user).stream()
                .filter(Package::hasSystemFlag)
                .collect(Collectors.toSet());
    }

    /**
     * Oem defined default dialer app.
     */
    @Experimental
    public Package oemDefaultDialerApp() {
        String defaultDialerPackage = TestApis.context().instrumentedContext().getString(
                Resources.getSystem().getIdentifier("config_defaultDialer", "string", "android"));
        return TestApis.packages().find(defaultDialerPackage);
    }

    /**
     * Oem defined default sms app.
     */
    @Experimental
    public Package oemDefaultSmsApp() {
        String defaultSmsPackage = TestApis.context().instrumentedContext().getString(
                Resources.getSystem().getIdentifier("config_defaultSms", "string", "android"));
        return TestApis.packages().find(defaultSmsPackage);
    }

    @Experimental
    public UndoableContext setVerifyAdbInstalls(boolean verify) {
        boolean originalVerifyAdbInstalls = getVerifyAdbInstalls();

        if (originalVerifyAdbInstalls == verify) {
            return UndoableContext.EMPTY;
        }

        TestApis.settings().global().putInt(PACKAGE_VERIFIER_INCLUDE_ADB, verify ? 1 : 0);

        return new UndoableContext(() -> {
            setVerifyAdbInstalls(originalVerifyAdbInstalls);
        });
    }

    @Experimental
    public boolean getVerifyAdbInstalls() {
        return TestApis.settings().global().getInt(PACKAGE_VERIFIER_INCLUDE_ADB, 1) == 1;
    }

    /**
     * Get the Launcher package.
     */
    @Experimental
    public Package launcher() {
        return find(TestApis.ui().device().getLauncherPackageName());
    }

    /**
     * Finds the browser assigned to handle browsing intents by default for selected user.
     *
     * @return the package for the default browser if there is one, null otherwise.
     */
    @SuppressWarnings("NewApi")
    @Experimental
    public Package defaultBrowserForUser(UserReference user) {
        ResolveInfo resolvedActivity;
        List<ResolveInfo> possibleActivities;
        Intent toResolve = new Intent(ACTION_VIEW, Uri.parse("http://"));

        PackageManager pm = TestApis.context()
                .instrumentationContextAsUser(user)
                .getPackageManager();

        if (Versions.meetsMinimumSdkVersionRequirement(Versions.T)) {
            possibleActivities = pm.queryIntentActivities(toResolve,
                    PackageManager.ResolveInfoFlags.of(MATCH_ALL));
            resolvedActivity = pm.resolveActivity(toResolve,
                    PackageManager.ResolveInfoFlags.of(MATCH_DEFAULT_ONLY));
        } else {
            possibleActivities = pm.queryIntentActivities(toResolve, MATCH_ALL);
            resolvedActivity = pm.resolveActivity(toResolve, MATCH_DEFAULT_ONLY);
        }

        Set<String> possibleBrowserPackageNames =  possibleActivities.stream()
                .map(Packages::extractPackageName)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Log.i(LOG_TAG, "possibleBrowserPackageNames: " + possibleBrowserPackageNames);

        String resolvedBrowserPackageName = extractPackageName(resolvedActivity);
        Log.i(LOG_TAG, "defaultBrowserPackageName: " + resolvedBrowserPackageName);
        if (resolvedBrowserPackageName == null
                || !possibleBrowserPackageNames.contains(resolvedBrowserPackageName)) {
            return null;
        }

        return find(resolvedBrowserPackageName);
    }

    private static String extractPackageName(@Nullable ResolveInfo nullableInfo) {
        return Optional.ofNullable(nullableInfo)
                .map(resolveInfo -> resolveInfo.activityInfo)
                .map(activityInfo -> activityInfo.packageName)
                .orElse(null);
    }

    /** See {@link PackageManager#queryIntentActivities(Intent, int)}.
     *
     * <p> Returns a list of {@link ResolveInfo} wrapped in {@link ResolveInfoWrapper}.*/
    @Experimental
    public List<ResolveInfoWrapper> queryIntentActivities(Intent intent, int flags) {
        return TestApis.context().instrumentedContext().getPackageManager()
                .queryIntentActivities(intent, flags)
                .stream().map(r -> new ResolveInfoWrapper(r.activityInfo, r.match))
                .collect(Collectors.toList());
    }

    /** See {@link PackageManager#queryIntentActivities(Intent, int)}.
     *
     * <p> Returns a list of {@link ResolveInfo} wrapped in {@link ResolveInfoWrapper}.*/
    @Experimental
    public List<ResolveInfoWrapper> queryIntentActivities(UserReference user, Intent intent, int flags) {
        return TestApis.context().androidContextAsUser(user).getPackageManager()
                .queryIntentActivities(intent, flags)
                .stream().map(r -> new ResolveInfoWrapper(r.activityInfo, r.match))
                .collect(Collectors.toList());
    }

 }
