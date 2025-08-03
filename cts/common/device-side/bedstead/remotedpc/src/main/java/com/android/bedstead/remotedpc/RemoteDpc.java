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

package com.android.bedstead.remotedpc;

import static android.os.UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES;

import static com.android.bedstead.nene.permissions.CommonPermissions.MANAGE_PROFILE_AND_DEVICE_OWNERS;
import static com.android.bedstead.nene.users.UserType.MANAGED_PROFILE_TYPE_NAME;

import android.app.admin.DevicePolicyManager;
import android.app.admin.ManagedProfileProvisioningParams;
import android.app.admin.ProvisioningException;
import android.content.ComponentName;
import android.os.Build;
import android.os.UserHandle;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.annotations.Experimental;
import com.android.bedstead.nene.devicepolicy.DeviceOwner;
import com.android.bedstead.nene.devicepolicy.DevicePolicyController;
import com.android.bedstead.nene.devicepolicy.ProfileOwner;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.permissions.PermissionContext;
import com.android.bedstead.nene.users.UserReference;
import com.android.bedstead.nene.utils.Versions;
import com.android.bedstead.testapp.TestApp;
import com.android.bedstead.testapp.TestAppProvider;
import com.android.bedstead.testapp.TestAppQueryBuilder;

/** Entry point to RemoteDPC. */
public class RemoteDpc extends RemotePolicyManager {

    public static final String REMOTE_DPC_APP_PACKAGE_NAME_OR_PREFIX = "com.android.cts.RemoteDPC";
    private static final String TEST_APP_CLASS_NAME =
            "com.android.bedstead.testapp.BaseTestAppDeviceAdminReceiver";
    private static final String LOG_TAG = "RemoteDpc";

    private static final DevicePolicyManager sDevicePolicyManager =
            TestApis.context().instrumentedContext().getSystemService(DevicePolicyManager.class);
    private static final TestAppProvider sTestAppProvider = new TestAppProvider();

    private boolean mShouldRemoveUserWhenRemoved = false;

    /**
     * Get the {@link RemoteDpc} instance for the Device Owner.
     *
     * <p>This will return {@code null} if there is no Device Owner or it is not a RemoteDPC app.
     */
    @Nullable
    public static RemoteDpc deviceOwner() {
        DeviceOwner deviceOwner = TestApis.devicePolicy().getDeviceOwner();
        if (!isRemoteDpc(deviceOwner)) {
            return null;
        }

        TestApp remoteDpcTestApp = new TestAppProvider().query().wherePackageName()
                .isEqualTo(deviceOwner.componentName().getPackageName())
                .get();
        return new RemoteDpc(remoteDpcTestApp, deviceOwner);
    }

    /**
     * Get the {@link RemoteDpc} instance for the Profile Owner of the current user.
     *
     * <p>This will return null if there is no Profile Owner or it is not a RemoteDPC app.
     */
    @Nullable
    public static RemoteDpc profileOwner() {
        return profileOwner(TestApis.users().instrumented());
    }

    /**
     * Get the {@link RemoteDpc} instance for the Profile Owner of the given {@code profile}.
     *
     * <p>This will return null if there is no Profile Owner or it is not a RemoteDPC app.
     */
    @Nullable
    public static RemoteDpc profileOwner(UserHandle profile) {
        if (profile == null) {
            throw new NullPointerException();
        }

        return profileOwner(TestApis.users().find(profile));
    }

    /**
     * Get the {@link RemoteDpc} instance for the Profile Owner of the given {@code profile}.
     *
     * <p>This will return null if there is no Profile Owner or it is not a RemoteDPC app.
     */
    @Nullable
    public static RemoteDpc profileOwner(UserReference profile) {
        if (profile == null) {
            throw new NullPointerException();
        }

        ProfileOwner profileOwner = TestApis.devicePolicy().getProfileOwner(profile);
        if (!isRemoteDpc(profileOwner)) {
            return null;
        }

        TestApp remoteDpcTestApp = new TestAppProvider().query().wherePackageName()
                .isEqualTo(profileOwner.componentName().getPackageName())
                .get();
        return new RemoteDpc(remoteDpcTestApp, profileOwner);
    }

    /**
     * Get the most specific {@link RemoteDpc} instance for the current user.
     *
     * <p>If the user has a RemoteDPC Profile Owner, this will refer to that. If it does not but
     * has a RemoteDPC Device Owner it will refer to that. Otherwise it will return null.
     */
    @Nullable
    public static RemoteDpc any() {
        return any(TestApis.users().instrumented());
    }

    /**
     * Get the most specific {@link RemoteDpc} instance for the current user.
     *
     * <p>If the user has a RemoteDPC Profile Owner, this will refer to that. If it does not but
     * has a RemoteDPC Device Owner it will refer to that. Otherwise it will return null.
     */
    @Nullable
    public static RemoteDpc any(UserHandle user) {
        if (user == null) {
            throw new NullPointerException();
        }

        return any(TestApis.users().find(user));
    }

    /**
     * Get the most specific {@link RemoteDpc} instance for the current user.
     *
     * <p>If the user has a RemoteDPC Profile Owner, this will refer to that. If it does not but
     * has a RemoteDPC Device Owner it will refer to that. Otherwise it will return null.
     */
    @Nullable
    public static RemoteDpc any(UserReference user) {
        RemoteDpc remoteDPC = profileOwner(user);
        if (remoteDPC != null) {
            return remoteDPC;
        }
        return deviceOwner();
    }

    /**
     * Get the {@link RemoteDpc} controller for the given {@link DevicePolicyController}.
     */
    public static RemoteDpc forDevicePolicyController(DevicePolicyController controller) {
        if (controller == null) {
            throw new NullPointerException();
        }

        if (isRemoteDpc(controller)) {
            TestApp remoteDpcTestApp = new TestAppProvider().query().wherePackageName()
                    .isEqualTo(controller.componentName().getPackageName())
                    .get();

            return new RemoteDpc(remoteDpcTestApp, controller);
        }

        throw new IllegalStateException("DevicePolicyController is not a RemoteDPC: "
                + controller);
    }

    /**
     * Set RemoteDPC as the Device Owner.
     */
    public static RemoteDpc setAsDeviceOwner() {
        return setAsDeviceOwner(new TestAppProvider().query().wherePackageName()
                .isEqualTo(REMOTE_DPC_APP_PACKAGE_NAME_OR_PREFIX));
    }

    /**
     * Sets RemoteDPC as the Device Owner on the system user based on TestAppQuery
     */
    public static RemoteDpc setAsDeviceOwner(TestAppQueryBuilder dpcQuery) {
        return setAsDeviceOwner(dpcQuery, TestApis.users().system());
    }

    /**
     * Sets RemoteDPC as the Device Owner on the given user based on TestAppQuery
     */
    public static RemoteDpc setAsDeviceOwner(TestAppQueryBuilder dpcQuery, UserReference user) {
        // We make sure that the query has RemoteDpc filter specified,
        // this is useful for the case where the user calls the method directly
        // and does not specify the RemoteDpc filter.
        dpcQuery = enforceRemoteDpcPackageFilter(dpcQuery);

        DeviceOwner currentDeviceOwner = TestApis.devicePolicy().getDeviceOwner();
        if (matchesRemoteDpcQuery(currentDeviceOwner, dpcQuery)) {
            return RemoteDpc.forDevicePolicyController(currentDeviceOwner);
        }

        if (currentDeviceOwner != null) {
            currentDeviceOwner.remove();
        }

        TestApp testApp = dpcQuery.get();
        testApp.install(user);
        Log.i(LOG_TAG, "Installing RemoteDPC app: " + testApp.packageName());
        ComponentName componentName =
                new ComponentName(testApp.packageName(), TEST_APP_CLASS_NAME);
        DeviceOwner deviceOwner = TestApis.devicePolicy().setDeviceOwner(componentName, user);
        return new RemoteDpc(testApp, deviceOwner);
    }

    /**
     * Set any RemoteDPC as the Profile Owner of the instrumented user.
     */
    public static RemoteDpc setAsProfileOwner() {
        return setAsProfileOwner(TestApis.users().instrumented());
    }

    /**
     * Set RemoteDPC that matches the query as the Profile Owner of the instrumented user.
     */
    public static RemoteDpc setAsProfileOwner(TestAppQueryBuilder dpcQuery) {
        return setAsProfileOwner(TestApis.users().instrumented(), dpcQuery);
    }

    /**
     * Set any RemoteDPC as the Profile Owner.
     */
    public static RemoteDpc setAsProfileOwner(UserHandle user) {
        if (user == null) {
            throw new NullPointerException();
        }

        TestAppQueryBuilder anyRemoteDpcQuery = new TestAppProvider().query()
                .wherePackageName().startsWith(REMOTE_DPC_APP_PACKAGE_NAME_OR_PREFIX);
        return setAsProfileOwner(TestApis.users().find(user), anyRemoteDpcQuery);
    }

    /**
     * Set RemoteDPC that matches the query as the Profile Owner.
     */
    public static RemoteDpc setAsProfileOwner(
            UserHandle user, TestAppQueryBuilder dpcQuery) {
        if (user == null) {
            throw new NullPointerException();
        }
        return setAsProfileOwner(TestApis.users().find(user), dpcQuery);
    }

    /**
     * Set RemoteDPC as the Profile Owner.
     *
     * <p>If called for Android versions prior to Q, an exception will be thrown if the user is not
     * the instrumented user.
     */
    public static RemoteDpc setAsProfileOwner(UserReference user) {
        if (user == null) {
            throw new NullPointerException();
        }

        TestAppQueryBuilder anyRemoteDpcQuery = new TestAppProvider().query()
                .wherePackageName().startsWith(REMOTE_DPC_APP_PACKAGE_NAME_OR_PREFIX);
        return setAsProfileOwner(user, anyRemoteDpcQuery);
    }

    /**
     * Set RemoteDPC that matches the query as the Profile Owner.
     *
     * <p>If called for Android versions prior to Q, an exception will be thrown if the user is not
     * the instrumented user.
     */
    public static RemoteDpc setAsProfileOwner(
            UserReference user, TestAppQueryBuilder dpcQuery) {
        // We make sure that the query has RemoteDpc filter specified,
        // this is useful for the case where the user calls the method directly
        // and does not specify the RemoteDpc filter.
        dpcQuery = enforceRemoteDpcPackageFilter(dpcQuery);

        if (user == null) {
            throw new NullPointerException();
        }

        if (!user.equals(TestApis.users().instrumented())) {
            if (!Versions.meetsMinimumSdkVersionRequirement(Build.VERSION_CODES.Q)) {
                throw new NeneException("Cannot use RemoteDPC across users prior to Q");
            }
        }

        ProfileOwner currentProfileOwner = TestApis.devicePolicy().getProfileOwner(user);
        if (matchesRemoteDpcQuery(currentProfileOwner, dpcQuery)) {
            return RemoteDpc.forDevicePolicyController(currentProfileOwner);
        }

        return setAsProfileOwner(user, dpcQuery.get());
    }

    /**
     * Set specific RemoteDPC {@link TestApp} as the Profile Owner.
     *
     * <p>If called for Android versions prior to Q, an exception will be thrown if the user is not
     * the instrumented user.
     */
    public static RemoteDpc setAsProfileOwner(
            UserReference user, TestApp dpcTestApp) {
        if (!dpcTestApp.pkg().packageName().startsWith(REMOTE_DPC_APP_PACKAGE_NAME_OR_PREFIX)) {
            throw new IllegalArgumentException("setAsProfileOwner test app must be a RemoteDPC");
        }

        if (user == null) {
            throw new NullPointerException();
        }

        if (!user.equals(TestApis.users().instrumented())) {
            if (!Versions.meetsMinimumSdkVersionRequirement(Build.VERSION_CODES.Q)) {
                throw new NeneException("Cannot use RemoteDPC across users prior to Q");
            }
        }

        ProfileOwner currentProfileOwner = TestApis.devicePolicy().getProfileOwner(user);

        if (currentProfileOwner != null) {
            currentProfileOwner.remove();
        }

        // TODO(274125850): Figure out the core reason these users are stopped
        if (!user.isRunning()) {
            user.start();
        }

        if (!dpcTestApp.installedOnUser(user)) {
            Log.i(LOG_TAG, "Installing RemoteDPC app: " + dpcTestApp.packageName());
            dpcTestApp.install(user);
        }

        ComponentName componentName =
                new ComponentName(dpcTestApp.packageName(), TEST_APP_CLASS_NAME);
        RemoteDpc remoteDpc = new RemoteDpc(
                dpcTestApp,
                TestApis.devicePolicy().setProfileOwner(user, componentName));

        // DISALLOW_INSTALL_UNKNOWN_SOURCES causes verification failures in work profiles
        remoteDpc.devicePolicyManager()
                .clearUserRestriction(remoteDpc.componentName(), DISALLOW_INSTALL_UNKNOWN_SOURCES);

        return remoteDpc;
    }

    /**
     * Create a work profile of the instrumented user with RemoteDpc as the profile owner.
     *
     * <p>If autoclosed, the user will be removed along with the dpc.
     *
     * <p>If called for Android versions prior to Q an exception will be thrown
     */
    @Experimental
    public static RemoteDpc createWorkProfile() {
        return createWorkProfile(TestApis.users().instrumented());
    }

    /**
     * Create a work profile of the instrumented user with RemoteDpc as the profile owner.
     *
     * <p>If autoclosed, the user will be removed along with the dpc.
     *
     * <p>If called for Android versions prior to Q an exception will be thrown
     */
    @Experimental
    public static RemoteDpc createWorkProfile(TestAppQueryBuilder dpcQuery) {
        return createWorkProfile(TestApis.users().instrumented(), dpcQuery);
    }

    /**
     * Create a work profile with RemoteDpc as the profile owner.
     *
     * <p>If autoclosed, the user will be removed along with the dpc.
     *
     * <p>If called for Android versions prior to Q an exception will be thrown
     */
    @Experimental
    public static RemoteDpc createWorkProfile(UserReference parent) {
        return createWorkProfile(parent, new TestAppProvider().query().wherePackageName()
                .isEqualTo(REMOTE_DPC_APP_PACKAGE_NAME_OR_PREFIX));
    }

    /**
     * Create a work profile with RemoteDpc as the profile owner.
     *
     * <p>If autoclosed, the user will be removed along with the dpc.
     *
     * <p>If called for Android versions prior to Q an exception will be thrown
     */
    @Experimental
    public static RemoteDpc createWorkProfile(UserReference parent, TestAppQueryBuilder dpcQuery) {
        // It'd be ideal if this method could be in TestApis.devicePolicy() but the dependency
        // direction wouldn't allow it
        if (parent == null) {
            throw new NullPointerException();
        }

        if (!Versions.meetsMinimumSdkVersionRequirement(Build.VERSION_CODES.Q)) {
            throw new NeneException("Cannot use RemoteDPC across users prior to Q");
        }

        if (!Versions.meetsMinimumSdkVersionRequirement(Build.VERSION_CODES.S)) {
            UserReference profile = TestApis.users().createUser()
                .type(TestApis.users().supportedType(MANAGED_PROFILE_TYPE_NAME))
                .parent(parent)
                .createAndStart();

            return setAsProfileOwner(profile, dpcQuery);
        }

        boolean removeFromParent = false;
        TestApp testApp = dpcQuery.get();
        if (!testApp.installedOnUser(parent)) {
            Log.i(LOG_TAG, "Installing RemoteDPC app: " + testApp.packageName());
            testApp.install(parent);
        }

        try (PermissionContext p =
                     TestApis.permissions().withPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)) {
            RemoteDpc dpc = forDevicePolicyController(TestApis.devicePolicy().getProfileOwner(
                    sDevicePolicyManager.createAndProvisionManagedProfile(
                            new ManagedProfileProvisioningParams.Builder(
                                    new ComponentName(testApp.packageName(), TEST_APP_CLASS_NAME),
                                    "RemoteDPC").build())));

            dpc.devicePolicyManager().setProfileEnabled(dpc.componentName());

            dpc.mShouldRemoveUserWhenRemoved = true;
            return dpc;

        } catch (ProvisioningException e) {
            throw new NeneException("Error provisioning work profile", e);
        } finally {
            if (removeFromParent) {
                testApp.uninstall(parent);
            }
        }
    }

    /**
     * Check if the RemoteDpc matches the query
     */
    public static boolean matchesRemoteDpcQuery(
            DevicePolicyController devicePolicyController,
            TestAppQueryBuilder dpcQuery) {
        if (isRemoteDpc(devicePolicyController)) {
            RemoteDpc remoteDpc = RemoteDpc.forDevicePolicyController(devicePolicyController);
            return dpcQuery.matches(remoteDpc.testApp());
        }
        return false;
    }

    /**
     * Check if dpc is a RemoteDpc
     */
    public static boolean isRemoteDpc(DevicePolicyController controller) {
        return controller != null
                && controller.componentName().getPackageName()
                .startsWith(REMOTE_DPC_APP_PACKAGE_NAME_OR_PREFIX)
                && controller.componentName().getClassName().equals(TEST_APP_CLASS_NAME);
    }

    private static TestAppQueryBuilder enforceRemoteDpcPackageFilter(
            TestAppQueryBuilder dpcQuery) {
        return dpcQuery.wherePackageName()
                .startsWith(REMOTE_DPC_APP_PACKAGE_NAME_OR_PREFIX)
                .allowInternalBedsteadTestApps();
    }

    private final DevicePolicyController mDevicePolicyController;

    RemoteDpc(TestApp remoteDpcTestApp, DevicePolicyController devicePolicyController) {
        super(remoteDpcTestApp, devicePolicyController == null ? null
                : devicePolicyController.user());
        mDevicePolicyController = devicePolicyController;
    }

    /**
     * Get the {@link DevicePolicyController} for this instance of RemoteDPC.
     */
    public DevicePolicyController devicePolicyController() {
        return mDevicePolicyController;
    }

    /**
     * Remove RemoteDPC as Device Owner or Profile Owner and uninstall the APK from the user.
     */
    public void remove() {
        if (mShouldRemoveUserWhenRemoved) {
            mDevicePolicyController.user().remove();
        } else {
            mDevicePolicyController.remove();
            TestApis.packages().find(mDevicePolicyController.componentName().getPackageName())
                    .uninstall(mDevicePolicyController.user());
        }
    }

    @Override
    public void close() {
        remove();
    }

    /**
     * Get the {@link ComponentName} of the DPC.
     */
    @Override
    public ComponentName componentName() {
        return mDevicePolicyController.componentName();
    }

    @Override
    public int hashCode() {
        return mDevicePolicyController.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof RemoteDpc)) {
            return false;
        }

        RemoteDpc other = (RemoteDpc) obj;
        return other.mDevicePolicyController.equals(mDevicePolicyController);
    }

    @Override
    public String toString() {
        return "RemoteDpc{"
                + "devicePolicyController=" + mDevicePolicyController
                + ", testApp=" + super.toString()
                + '}';
    }
}
