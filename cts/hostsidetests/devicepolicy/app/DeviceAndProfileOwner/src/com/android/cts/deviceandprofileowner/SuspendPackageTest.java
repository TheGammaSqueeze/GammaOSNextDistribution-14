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

package com.android.cts.deviceandprofileowner;

import static com.android.server.pm.shortcutmanagertest.ShortcutManagerTestUtils.getDefaultLauncher;

import static com.google.common.truth.Truth.assertWithMessage;

import android.content.pm.SuspendDialogInfo;
import android.util.Log;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

public class SuspendPackageTest extends BaseDeviceAdminTest {

    private static final String TAG = SuspendPackageTest.class.getSimpleName();
    private static final String INTENT_RECEIVER_PKG = "com.android.cts.intent.receiver";

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        Log.d(TAG, "Running test on user " + mContext.getUserId());
    }

    public void testSetPackagesSuspended() throws Exception {
        String[] notHandled = setSuspendedPackages(/* suspend= */ true, INTENT_RECEIVER_PKG);
        // all packages should be handled.
        assertWithMessage("packages not suspended").that(notHandled).isEmpty();

        assertPackageSuspended(INTENT_RECEIVER_PKG);
    }

    public void testSetPackagesSuspendedWithPackageManager() throws Exception {
        SuspendDialogInfo dialogInfo = new SuspendDialogInfo.Builder()
                .setMessage("Test message")
                .build();

        String[] notHandledPackages =
                mContext.getPackageManager().setPackagesSuspended(
                        new String[] {INTENT_RECEIVER_PKG}, true, null, null, dialogInfo);
        // all packages should be handled.
        assertWithMessage("notHandlePackages").that(notHandledPackages).isEmpty();

        assertPackageSuspended(INTENT_RECEIVER_PKG);
    }

    public void testSetPackagesNotSuspendedWithPackageManager() throws Exception {
        String[] notHandled = mContext.getPackageManager().setPackagesSuspended(
                new String[] {INTENT_RECEIVER_PKG}, false, null, null, (SuspendDialogInfo) null);
        // all packages should be handled.
        assertWithMessage("packages not handled").that(notHandled).isEmpty();

        // test isPackageSuspended
        assertPackageNotSuspended(INTENT_RECEIVER_PKG);
    }

    public void testSetPackagesNotSuspended() throws Exception {

        String[] notHandled = setSuspendedPackages(/* suspend= */ false, INTENT_RECEIVER_PKG);
        // all packages should be handled.
        assertWithMessage("packages not suspended").that(notHandled).isEmpty();

        // test isPackageSuspended
        assertPackageNotSuspended(INTENT_RECEIVER_PKG);
    }

    /**
     * Verify that we cannot suspend launcher and dpc app.
     */
    public void testSuspendNotSuspendablePackages() throws Exception {
        String launcherPackage = getDefaultLauncher(getInstrumentation());
        String dpcPackage = ADMIN_RECEIVER_COMPONENT.getPackageName();
        String[] notHandledPackages = setSuspendedPackages(/* suspend= */ true,
                launcherPackage, dpcPackage);
        // no package should be handled.
        assertWithMessage("not handled packages").that(notHandledPackages).asList()
                .containsExactly(launcherPackage, dpcPackage);

        Set<String> exemptApps = mDevicePolicyManager.getPolicyExemptApps();
        if (exemptApps.isEmpty()) {
            Log.v(TAG, "testSuspendNotSuspendablePackages(): no exempt apps");
            return;
        }

        Log.v(TAG, "testSuspendNotSuspendablePackages(): testing exempt apps: " + exemptApps);
        notHandledPackages = setSuspendedPackages(/* suspend= */ true, exemptApps);
        assertWithMessage("exempt apps not suspended").that(notHandledPackages).asList()
            .containsExactlyElementsIn(exemptApps);
    }

    private String[] setSuspendedPackages(boolean suspend, Collection<String> pkgs) {
        String[] pkgsArray = new String[pkgs.size()];
        pkgs.toArray(pkgsArray);
        return setSuspendedPackages(suspend, pkgsArray);
    }

    private String[] setSuspendedPackages(boolean suspend, String... pkgs) {
        Log.d(TAG, "Calling setPackagesSuspended(" + suspend + ", " + Arrays.toString(pkgs));
        String[] notHandled =
                mDevicePolicyManager.setPackagesSuspended(ADMIN_RECEIVER_COMPONENT, pkgs, suspend);
        Log.d(TAG, "Returning " + Arrays.toString(notHandled));
        return notHandled;
    }

    private void assertPackageSuspended(String pkg) throws Exception {
        assertPackageSuspension(pkg, /* expected= */ true);
    }

    private void assertPackageNotSuspended(String pkg) throws Exception {
        assertPackageSuspension(pkg, /* expected= */ false);
    }

    private void assertPackageSuspension(String pkg, boolean expected) throws Exception {
        boolean actual =
                mDevicePolicyManager.isPackageSuspended(ADMIN_RECEIVER_COMPONENT, pkg);
        Log.d(TAG, "isPackageSuspended(" + pkg + "): " + actual);
        assertWithMessage("package %s suspension", pkg).that(actual).isEqualTo(expected);
    }
}
