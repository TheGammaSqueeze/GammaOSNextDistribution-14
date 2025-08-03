/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.cts.devicepolicy;

import static com.android.cts.devicepolicy.DeviceAdminFeaturesCheckerRule.FEATURE_MANAGED_USERS;

import static org.junit.Assert.assertTrue;

import com.android.cts.devicepolicy.DeviceAdminFeaturesCheckerRule.RequiresAdditionalFeatures;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;

import org.junit.Test;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class UserRestrictionsTest extends BaseDevicePolicyTest {
    private static final String DEVICE_ADMIN_PKG = "com.android.cts.deviceandprofileowner";
    private static final String DEVICE_ADMIN_APK = "CtsDeviceAndProfileOwnerApp.apk";
    private static final String ADMIN_RECEIVER_TEST_CLASS
            = ".BaseDeviceAdminTest$BasicAdminReceiver";

    private static final String GLOBAL_RESTRICTIONS_TEST_CLASS =
            "userrestrictions.ProfileGlobalRestrictionsTest";
    private static final String SET_GLOBAL_RESTRICTIONS_TEST =
            "testSetProfileGlobalRestrictions";
    private static final String CLEAR_GLOBAL_RESTRICTIONS_TEST =
            "testClearProfileGlobalRestrictions";
    private static final String ENSURE_GLOBAL_RESTRICTIONS_TEST =
            "testProfileGlobalRestrictionsEnforced";
    private static final String ENSURE_NO_GLOBAL_RESTRICTIONS_TEST =
            "testProfileGlobalRestrictionsNotEnforced";

    private boolean mRemoveOwnerInTearDown;
    private int mDeviceOwnerUserId;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        mRemoveOwnerInTearDown = false;
    }

    @Override
    public void tearDown() throws Exception {
        if (mRemoveOwnerInTearDown) {
            String componentName = DEVICE_ADMIN_PKG + "/" + ADMIN_RECEIVER_TEST_CLASS;
            assertTrue("Failed to clear owner", removeAdmin(componentName, mDeviceOwnerUserId));
            if (isHeadlessSystemUserMode()) {
                boolean removed = removeAdmin(componentName, mPrimaryUserId);
                if (!removed) {
                    CLog.e("Failed to remove %s on user %d", componentName, mPrimaryUserId);
                }
            }
        }

        // DO/PO might have set DISALLOW_REMOVE_USER, so it needs to be done after removing
        // them.
        removeTestUsers();
        getDevice().uninstallPackage(DEVICE_ADMIN_PKG);

        super.tearDown();
    }

    private void runTests(@Nonnull String className,
            @Nullable String method, int userId) throws DeviceNotAvailableException {
        runDeviceTestsAsUser(DEVICE_ADMIN_PKG, "." + className, method, userId);
    }

    private void runTests(@Nonnull String className, int userId)
            throws DeviceNotAvailableException {
        runTests(className, null, userId);
    }

    @Test
    public void testUserRestrictions_deviceOwnerOnly() throws Exception {
        setDo();

        try {
            runTests("userrestrictions.DeviceOwnerUserRestrictionsTest",
                    "testDefaultRestrictions", mDeviceOwnerUserId);
            runTests("userrestrictions.DeviceOwnerUserRestrictionsTest",
                    "testSetAllRestrictions", mDeviceOwnerUserId);
            runTests("userrestrictions.DeviceOwnerUserRestrictionsTest",
                    "testBroadcast", mDeviceOwnerUserId);
        } finally {
            // Clear all restrictions restrictions on user 0.
            runTests("userrestrictions.DeviceOwnerUserRestrictionsTest",
                    "testClearAllRestrictions", mDeviceOwnerUserId);
        }
    }

    @Test
    public void testUserRestrictions_primaryProfileOwnerOnly() throws Exception {
        assumeHasMainUser();
        setPoAsUser(mPrimaryUserId);

        try {
            runTests("userrestrictions.PrimaryProfileOwnerUserRestrictionsTest",
                    "testDefaultRestrictions", mPrimaryUserId);
            runTests("userrestrictions.PrimaryProfileOwnerUserRestrictionsTest",
                    "testSetAllRestrictions", mPrimaryUserId);
            runTests("userrestrictions.PrimaryProfileOwnerUserRestrictionsTest",
                    "testBroadcast", mPrimaryUserId);
        } finally {
            // Clear all restrictions restrictions on the main user.
            runTests("userrestrictions.PrimaryProfileOwnerUserRestrictionsTest",
                    "testClearAllRestrictions", mPrimaryUserId);
        }
    }

    // Checks restrictions for managed user (NOT managed profile).
    @Test
    public void testUserRestrictions_secondaryProfileOwnerOnly() throws Exception {
        assumeSupportsMultiUser();

        final int secondaryUserId = createUser();
        setPoAsUser(secondaryUserId);

        try {
            runTests("userrestrictions.SecondaryProfileOwnerUserRestrictionsTest",
                    "testDefaultRestrictions", secondaryUserId);
            runTests("userrestrictions.SecondaryProfileOwnerUserRestrictionsTest",
                    "testSetAllRestrictions", secondaryUserId);
            runTests("userrestrictions.SecondaryProfileOwnerUserRestrictionsTest",
                    "testBroadcast", secondaryUserId);
        } finally {
            // Clear all restrictions restrictions on secondary user.
            runTests("userrestrictions.SecondaryProfileOwnerUserRestrictionsTest",
                    "testClearAllRestrictions", secondaryUserId);
        }
    }

    // Checks restrictions for managed profile.
    @RequiresAdditionalFeatures({FEATURE_MANAGED_USERS})
    @Test
    public void testUserRestrictions_managedProfileOwnerOnly() throws Exception {
        assumeCanCreateOneManagedUser();

        // Create managed profile.
        final int profileUserId = createManagedProfile(mPrimaryUserId /* parentUserId */);
        // createManagedProfile doesn't start the user automatically.
        startUser(profileUserId);
        setPoAsUser(profileUserId);

        try {
            runTests("userrestrictions.ManagedProfileOwnerUserRestrictionsTest",
                    "testDefaultRestrictions", profileUserId);
            runTests("userrestrictions.ManagedProfileOwnerUserRestrictionsTest",
                    "testSetAllRestrictions", profileUserId);
            runTests("userrestrictions.ManagedProfileOwnerUserRestrictionsTest",
                    "testBroadcast", profileUserId);
        } finally {
            // Clear all restrictions restrictions on profile.
            runTests("userrestrictions.ManagedProfileOwnerUserRestrictionsTest",
                    "testClearAllRestrictions", profileUserId);
        }
    }

    /**
     * DO + PO combination.  Make sure global DO restrictions are visible on secondary users.
     */
    @Test
    public void testUserRestrictions_layering() throws Exception {
        assumeSupportsMultiUser();
        setDo();

        final int secondaryUserId = createUserAndWaitStart();
        if (!isHeadlessSystemUserMode()) {
            // In headless a PO is set by default on any new additional users so we only need to
            // set it on non-headless.
            setPoAsUser(secondaryUserId);
        }

        try {
            // Ensure that UserManager differentiates its own restrictions from DO restrictions.
            runTests("userrestrictions.DeviceOwnerUserRestrictionsTest",
                    "testHasBaseUserRestrictions", mDeviceOwnerUserId);

            // Let DO set all restrictions.
            runTests("userrestrictions.DeviceOwnerUserRestrictionsTest",
                    "testSetAllRestrictions", mDeviceOwnerUserId);

            // Make sure the global restrictions are visible to secondary users.
            runTests("userrestrictions.SecondaryProfileOwnerUserRestrictionsTest",
                    "testHasGlobalRestrictions", secondaryUserId);

            // Then let PO set all restrictions.
            runTests("userrestrictions.SecondaryProfileOwnerUserRestrictionsTest",
                    "testSetAllRestrictions", secondaryUserId);

            // Make sure both local and global restrictions are visible on secondary users.
            runTests("userrestrictions.SecondaryProfileOwnerUserRestrictionsTest",
                    "testHasBothGlobalAndLocalRestrictions", secondaryUserId);

            // Let DO clear all restrictions.
            runTests("userrestrictions.DeviceOwnerUserRestrictionsTest",
                    "testClearAllRestrictions", mDeviceOwnerUserId);

            // Now only PO restrictions should be set on the secondary user.
            runTests("userrestrictions.SecondaryProfileOwnerUserRestrictionsTest",
                    "testLocalRestrictionsOnly", secondaryUserId);
        } finally {
            // Clear all restrictions restrictions on user 0.
            runTests("userrestrictions.DeviceOwnerUserRestrictionsTest",
                    "testClearAllRestrictions", mDeviceOwnerUserId);
            // Clear all restrictions restrictions on secondary user.
            runTests("userrestrictions.SecondaryProfileOwnerUserRestrictionsTest",
                    "testClearAllRestrictions", secondaryUserId);
        }
    }

    /**
     * PO on main-user.  It can set DO restrictions too, but they shouldn't leak to other users.
     */
    @Test
    public void testUserRestrictions_layering_profileOwnerNoLeaking() throws Exception {
        assumeSupportsMultiUser();
        assumeHasMainUser();

        // Set PO on the main user.
        setPoAsUser(mPrimaryUserId);

        // Create another user and set PO.
        final int secondaryUserId = createUserAndWaitStart();
        setPoAsUser(secondaryUserId);

        try {
            // Let main-user PO set all restrictions.
            runTests("userrestrictions.PrimaryProfileOwnerUserRestrictionsTest",
                    "testSetAllRestrictions", mPrimaryUserId);

            // Secondary users shouldn't see any of them. Leaky user restrictions are excluded.
            runTests("userrestrictions.SecondaryProfileOwnerUserRestrictionsTest",
                    "testDefaultAndLeakyRestrictions", secondaryUserId);
        } finally {
            // Clear all restrictions restrictions on the main user.
            runTests("userrestrictions.PrimaryProfileOwnerUserRestrictionsTest",
                    "testClearAllRestrictions", mPrimaryUserId);
            // Clear all restrictions restrictions on secondary user.
            runTests("userrestrictions.SecondaryProfileOwnerUserRestrictionsTest",
                    "testClearAllRestrictions", secondaryUserId);
        }
    }

    /**
     * DO sets profile global restrictions (only ENSURE_VERIFY_APPS), should affect all
     * users (not a particularly special case but to be sure).
     */
    @Test
    public void testUserRestrictions_profileGlobalRestrictionsAsDo() throws Exception {
        assumeSupportsMultiUser();
        setDo();
        final int secondaryUserId;
        if (!isHeadlessSystemUserMode()) {
            // Create another user and set PO.
            secondaryUserId = createUserAndWaitStart();
            setPoAsUser(secondaryUserId);
        } else {
            // In headless system user mode, PO is set on primary user when DO is set.
            secondaryUserId = mPrimaryUserId;
        }

        final int[] usersToCheck = {mDeviceOwnerUserId, secondaryUserId};

        // Do sets the restriction.
        setAndCheckProfileGlobalRestriction(mDeviceOwnerUserId, usersToCheck);
    }

    /**
     * Managed profile owner sets profile global restrictions (only ENSURE_VERIFY_APPS), should
     * affect all users.
     */
    @RequiresAdditionalFeatures({FEATURE_MANAGED_USERS})
    @Test
    public void testUserRestrictions_ProfileGlobalRestrictionsAsPo() throws Exception {
        assumeCanCreateOneManagedUser();

        // Set PO on the main user.
        setPoAsUser(mPrimaryUserId);

        // Create another user with PO.
        final int secondaryUserId = createManagedProfile(mPrimaryUserId /* parentUserId */);
        setPoAsUser(secondaryUserId);

        final int[] usersToCheck = {mPrimaryUserId, secondaryUserId};

        // Check the case when primary user's PO sets the restriction.
        setAndCheckProfileGlobalRestriction(mPrimaryUserId, usersToCheck);

        // Check the case when managed profile owner sets the restriction.
        setAndCheckProfileGlobalRestriction(secondaryUserId, usersToCheck);
    }

    /** Installs admin package and makes it a profile owner for a given user. */
    private void setPoAsUser(int userId) throws Exception {
        installAppAsUser(DEVICE_ADMIN_APK, /* grantPermssions= */true,
                /* dontKillApp= */ true, userId);
        assertTrue("Failed to set profile owner",
                setProfileOwner(DEVICE_ADMIN_PKG + "/" + ADMIN_RECEIVER_TEST_CLASS,
                        userId, /* expectFailure */ false));
        // If PO is not in primary user, it will be removed with the user.
        if (userId == mDeviceOwnerUserId) {
            mRemoveOwnerInTearDown = true;
        }
    }

    /** Installs admin package and makes it a device owner. */
    private void setDo() throws Exception {
        installDeviceOwnerApp(DEVICE_ADMIN_APK);

        assertTrue("Failed to set device owner",
                setDeviceOwner(DEVICE_ADMIN_PKG + "/" + ADMIN_RECEIVER_TEST_CLASS,
                        mDeviceOwnerUserId, /*expectFailure*/ false));
        mRemoveOwnerInTearDown = true;

        if (isHeadlessSystemUserMode()) {
            affiliateUsers(DEVICE_ADMIN_PKG, mDeviceOwnerUserId, mPrimaryUserId);
        }
    }

    /**
     * Sets user restriction and checks that it applies to all users.
     * @param enforcingUserId user who should set/clear the restriction, should be either
     *        primary or secondary user id and should have device or profile owner active.
     * @param usersToCheck users that should have this restriction enforced.
     */
    private void setAndCheckProfileGlobalRestriction(int enforcingUserId, int usersToCheck[])
            throws Exception {
        // Always try to clear the restriction to avoid undesirable side effects.
        try {
            // Set the restriction.
            runGlobalRestrictionsTest(SET_GLOBAL_RESTRICTIONS_TEST, enforcingUserId);
            // Check that the restriction is in power.
            for (int userId : usersToCheck) {
                runGlobalRestrictionsTest(ENSURE_GLOBAL_RESTRICTIONS_TEST, userId);
            }
        } finally {
            // Clear the restriction.
            runGlobalRestrictionsTest(CLEAR_GLOBAL_RESTRICTIONS_TEST, enforcingUserId);
            // Check that the restriction is not in power anymore.
            for (int userId : usersToCheck) {
                runGlobalRestrictionsTest(ENSURE_NO_GLOBAL_RESTRICTIONS_TEST, userId);
            }
        }
    }

    /** Convenience method to run global user restrictions tests. */
    private void runGlobalRestrictionsTest(String testMethodName, int userId) throws Exception {
        runTests(GLOBAL_RESTRICTIONS_TEST_CLASS, testMethodName, userId);
    }
}
