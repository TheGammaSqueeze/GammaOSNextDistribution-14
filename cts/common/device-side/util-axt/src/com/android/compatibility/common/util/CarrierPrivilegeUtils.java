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

package com.android.compatibility.common.util;

import static android.telephony.TelephonyManager.CarrierPrivilegesCallback;

import static com.android.compatibility.common.util.SystemUtil.runShellCommand;
import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static org.junit.Assert.fail;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.PersistableBundle;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.security.MessageDigest;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Utility to execute a code block with carrier privileges, or as the carrier service.
 *
 * <p>The utility methods contained in this class will release carrier privileges once the specified
 * task is completed.
 *
 * <p>This utility class explicitly does not support cases where the calling application is SIM
 * privileged; in that case, the rescinding of carrier privileges will time out and fail.
 *
 * <p>Example:
 *
 * <pre>
 *   CarrierPrivilegeUtils.withCarrierPrivileges(c, subId, () -> telephonyManager.setFoo(bar));
 *   CarrierPrivilegeUtils.asCarrierService(c, subId, () -> telephonyManager.setFoo(bar));
 * </pre>
 *
 * @see TelephonyManager#hasCarrierPrivileges()
 */
@TargetApi(Build.VERSION_CODES.TIRAMISU)
public final class CarrierPrivilegeUtils {
    private static final String TAG = CarrierPrivilegeUtils.class.getSimpleName();

    private static class CarrierPrivilegeChangeMonitor implements AutoCloseable {
        private final CountDownLatch mLatch = new CountDownLatch(1);
        private final Context mContext;
        private final boolean mIsShell;
        private final TelephonyManager mTelephonyManager;
        private final CarrierPrivilegesCallback mCarrierPrivilegesCallback;

        /**
         * Construct a {@link CarrierPrivilegesCallback} to monitor carrier privileges change.
         *
         * @param c context
         * @param subId subscriptionId to listen to
         * @param gainCarrierPrivileges true if wait to grant carrier privileges, false if wait to
         *     revoke
         * @param overrideCarrierServicePackage {@code true} if this should wait for an override to
         *     take effect, {@code false} if this should wait for the override to be cleared
         * @param isShell true if the caller is Shell
         */
        CarrierPrivilegeChangeMonitor(
                Context c,
                int subId,
                boolean gainCarrierPrivileges,
                boolean overrideCarrierServicePackage,
                boolean isShell) {
            mContext = c;
            mIsShell = isShell;
            mTelephonyManager = mContext.getSystemService(
                    TelephonyManager.class).createForSubscriptionId(subId);
            Objects.requireNonNull(mTelephonyManager);

            final int slotIndex = SubscriptionManager.getSlotIndex(subId);
            mCarrierPrivilegesCallback =
                    new CarrierPrivilegesCallback() {
                        /*
                         * onCarrierServiceChanged() returns a @Nullable carrierServicePackageName,
                         * and TM#getCarrierServicePackageNameForLogicalSlot() requires
                         * using shell permission identity to get READ_PRIVILEGED_PHONE_STATE, which
                         * could clobber actual CTS package values. As such, we have to track both
                         * the carrier service package name, and that it has truly been set
                         * (including being set to null). The associated onCarrierServiceChanged
                         * callback will always be fired upon registration in the enclosing
                         * CarrierPrivilegeChangeMonitor constructor.
                         */
                        private boolean mHasReceivedCarrierServicePackageName = false;
                        private String mCarrierServicePackage = null;

                        @Override
                        public void onCarrierPrivilegesChanged(
                                Set<String> privilegedPackageNames, Set<Integer> privilegedUids) {
                            verifyStateAndFireLatch();
                        }

                        @Override
                        public void onCarrierServiceChanged(
                                String carrierServicePackageName, int carrierServiceUid) {
                            mCarrierServicePackage = carrierServicePackageName;
                            mHasReceivedCarrierServicePackageName = true;
                            verifyStateAndFireLatch();
                        }

                        private void verifyStateAndFireLatch() {
                            if (mTelephonyManager.hasCarrierPrivileges() != gainCarrierPrivileges) {
                                return;
                            }

                            boolean isCurrentApp =
                                    Objects.equals(
                                            mCarrierServicePackage, mContext.getOpPackageName());
                            if (!mHasReceivedCarrierServicePackageName
                                    || isCurrentApp != overrideCarrierServicePackage) {
                                return; // Conditions not yet satisfied; return.
                            }

                            mLatch.countDown();
                        }
                    };

            // Run with shell identify only when caller is not Shell to avoid overriding current
            // SHELL permissions
            if (mIsShell) {
                mTelephonyManager.registerCarrierPrivilegesCallback(
                        slotIndex, mContext.getMainExecutor(), mCarrierPrivilegesCallback);
            } else {
                runWithShellPermissionIdentity(() -> {
                    mTelephonyManager.registerCarrierPrivilegesCallback(
                            slotIndex,
                            mContext.getMainExecutor(),
                            mCarrierPrivilegesCallback);
                }, Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
            }
        }

        @Override
        public void close() {
            if (mTelephonyManager == null) return;

            if (mIsShell) {
                mTelephonyManager.unregisterCarrierPrivilegesCallback(mCarrierPrivilegesCallback);
            } else {
                runWithShellPermissionIdentity(
                        () -> mTelephonyManager.unregisterCarrierPrivilegesCallback(
                                mCarrierPrivilegesCallback),
                        Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
            }
        }

        public void waitForCarrierPrivilegeChanged() throws Exception {
            if (!mLatch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Failed to update carrier privileges");
            }
        }
    }

    private static TelephonyManager getTelephonyManager(Context c, int subId) {
        return c.getSystemService(TelephonyManager.class).createForSubscriptionId(subId);
    }

    private static boolean hasCarrierPrivileges(Context c, int subId) {
        // Synchronously check for carrier privileges. Checking certificates MAY be incorrect if
        // broadcasts are delayed.
        return getTelephonyManager(c, subId).hasCarrierPrivileges();
    }

    private static boolean isCarrierServicePackage(Context c, int subId, boolean isShell) {
        // Synchronously check if the calling package is the carrier service package.
        String carrierServicePackageName = null;
        if (isShell) {
            carrierServicePackageName =
                    getTelephonyManager(c, subId)
                            .getCarrierServicePackageNameForLogicalSlot(
                                    SubscriptionManager.getSlotIndex(subId));
        } else {
            carrierServicePackageName = runWithShellPermissionIdentity(() -> {
                return getTelephonyManager(c, subId)
                        .getCarrierServicePackageNameForLogicalSlot(
                                SubscriptionManager.getSlotIndex(subId));
            }, android.Manifest.permission.READ_PRIVILEGED_PHONE_STATE);
        }

        return Objects.equals(c.getOpPackageName(), carrierServicePackageName);
    }

    private static String getCertHashForThisPackage(final Context c) throws Exception {
        final PackageInfo pkgInfo = c.getPackageManager()
                .getPackageInfo(c.getOpPackageName(), PackageManager.GET_SIGNATURES);
        final MessageDigest md = MessageDigest.getInstance("SHA-256");
        final byte[] certHash = md.digest(pkgInfo.signatures[0].toByteArray());
        return UiccUtil.bytesToHexString(certHash);
    }

    private static void changeCarrierPrivileges(
            Context c,
            int subId,
            boolean gainCarrierPrivileges,
            boolean overrideCarrierServicePackage,
            boolean isShell)
            throws Exception {
        if (!ApiLevelUtil.isAtLeast(Build.VERSION_CODES.TIRAMISU)) {
            throw new IllegalStateException("CarrierPrivilegeUtils requires at least SDK 33");
        }

        if (hasCarrierPrivileges(c, subId) == gainCarrierPrivileges
                && isCarrierServicePackage(c, subId, isShell) == overrideCarrierServicePackage) {
            Log.w(
                    TAG,
                    "Carrier privileges already "
                            + (gainCarrierPrivileges ? "granted" : "revoked")
                            + "or carrier service already "
                            + (overrideCarrierServicePackage ? "overridden" : "cleared")
                            + "; bug?");
            return;
        }

        final String certHash = getCertHashForThisPackage(c);
        final PersistableBundle carrierConfigs;

        if (gainCarrierPrivileges) {
            carrierConfigs = new PersistableBundle();
            carrierConfigs.putStringArray(
                    CarrierConfigManager.KEY_CARRIER_CERTIFICATE_STRING_ARRAY,
                    new String[] {certHash});
        } else {
            carrierConfigs = null;
        }

        final CarrierConfigManager configManager = c.getSystemService(CarrierConfigManager.class);

        try (CarrierPrivilegeChangeMonitor monitor =
                new CarrierPrivilegeChangeMonitor(
                        c, subId, gainCarrierPrivileges, overrideCarrierServicePackage, isShell)) {
            // If the caller is the shell, it's dangerous to adopt shell permission identity for
            // the CarrierConfig override (as it will override the existing shell permissions).
            if (isShell) {
                configManager.overrideConfig(subId, carrierConfigs);
            } else {
                runWithShellPermissionIdentity(() -> {
                    configManager.overrideConfig(subId, carrierConfigs);
                }, android.Manifest.permission.MODIFY_PHONE_STATE);
            }

            if (overrideCarrierServicePackage) {
                runShellCommand(
                        "cmd phone set-carrier-service-package-override -s "
                                + subId
                                + " "
                                + c.getOpPackageName());
            } else {
                runShellCommand("cmd phone clear-carrier-service-package-override -s " + subId);
            }

            monitor.waitForCarrierPrivilegeChanged();
        }
    }

    /**
     * Utility class to prevent nested calls
     *
     * <p>Unless refcounted, a nested call will clear privileges on the outer call.
     */
    private static class NestedCallChecker implements AutoCloseable {
        private static final AtomicBoolean sCheckBit = new AtomicBoolean();

        private NestedCallChecker() {
            if (!sCheckBit.compareAndSet(false /* expected */, true /* update */)) {
                fail("Nested CarrierPrivilegeUtils calls are not supported");
            }
        }

        @Override
        public void close() {
            sCheckBit.set(false);
        }
    }

    /** Runs the provided action with the calling package granted carrier privileges. */
    public static void withCarrierPrivileges(Context c, int subId, ThrowingRunnable action)
            throws Exception {
        try (NestedCallChecker checker = new NestedCallChecker()) {
            changeCarrierPrivileges(
                    c,
                    subId,
                    true /* gainCarrierPrivileges */,
                    false /* overrideCarrierServicePackage */,
                    false /* isShell */);
            action.run();
        } finally {
            changeCarrierPrivileges(
                    c,
                    subId,
                    false /* gainCarrierPrivileges */,
                    false /* overrideCarrierServicePackage */,
                    false /* isShell */);
        }
    }

    /**
     * Runs the provided action with the calling package granted carrier privileges.
     *
     * <p>This variant of the method does NOT acquire shell identity to prevent overriding current
     * shell permissions. The caller is expected to hold the READ_PRIVILEGED_PHONE_STATE permission.
     */
    public static void withCarrierPrivilegesForShell(Context c, int subId, ThrowingRunnable action)
            throws Exception {
        try (NestedCallChecker checker = new NestedCallChecker()) {
            changeCarrierPrivileges(
                    c,
                    subId,
                    true /* gainCarrierPrivileges */,
                    false /* overrideCarrierServicePackage */,
                    true /* isShell */);
            action.run();
        } finally {
            changeCarrierPrivileges(
                    c,
                    subId,
                    false /* gainCarrierPrivileges */,
                    false /* overrideCarrierServicePackage */,
                    true /* isShell */);
        }
    }

    /** Runs the provided action with the calling package granted carrier privileges. */
    public static <R> R withCarrierPrivileges(Context c, int subId, ThrowingSupplier<R> action)
            throws Exception {
        try (NestedCallChecker checker = new NestedCallChecker()) {
            changeCarrierPrivileges(
                    c,
                    subId,
                    true /* gainCarrierPrivileges */,
                    false /* overrideCarrierServicePackage */,
                    false /* isShell */);
            return action.get();
        } finally {
            changeCarrierPrivileges(
                    c,
                    subId,
                    false /* gainCarrierPrivileges */,
                    false /* overrideCarrierServicePackage */,
                    false /* isShell */);
        }
    }

    /**
     * Runs the provided action with the calling package set as the Carrier Service.
     *
     * <p>This will also run the action with carrier privileges, which is a necessary condition to
     * be a carrier service.
     */
    public static void asCarrierService(Context c, int subId, ThrowingRunnable action)
            throws Exception {
        try (NestedCallChecker checker = new NestedCallChecker()) {
            changeCarrierPrivileges(
                    c,
                    subId,
                    true /* gainCarrierPrivileges */,
                    true /* overrideCarrierServicePackage */,
                    false /* isShell */);
            action.run();
        } finally {
            changeCarrierPrivileges(
                    c,
                    subId,
                    false /* gainCarrierPrivileges */,
                    false /* overrideCarrierServicePackage */,
                    false /* isShell */);
        }
    }

    /**
     * Runs the provided action with the calling package set as the Carrier Service.
     *
     * <p>This will also run the action with carrier privileges, which is a necessary condition to
     * be a carrier service.
     *
     * <p>This variant of the method does NOT acquire shell identity to prevent overriding current
     * shell permissions. The caller is expected to hold the READ_PRIVILEGED_PHONE_STATE permission.
     */
    public static void asCarrierServiceForShell(Context c, int subId, ThrowingRunnable action)
            throws Exception {
        try (NestedCallChecker checker = new NestedCallChecker()) {
            changeCarrierPrivileges(
                    c,
                    subId,
                    true /* gainCarrierPrivileges */,
                    true /* overrideCarrierServicePackage */,
                    true /* isShell */);
            action.run();
        } finally {
            changeCarrierPrivileges(
                    c,
                    subId,
                    false /* gainCarrierPrivileges */,
                    false /* overrideCarrierServicePackage */,
                    true /* isShell */);
        }
    }

    /**
     * Runs the provided action with the calling package set as the Carrier Service.
     *
     * <p>This will also run the action with carrier privileges, which is a necessary condition to
     * be a carrier service.
     */
    public static <R> R asCarrierService(Context c, int subId, ThrowingSupplier<R> action)
            throws Exception {
        try (NestedCallChecker checker = new NestedCallChecker()) {
            changeCarrierPrivileges(
                    c,
                    subId,
                    true /* gainCarrierPrivileges */,
                    true /* overrideCarrierServicePackage */,
                    false /* isShell */);
            return action.get();
        } finally {
            changeCarrierPrivileges(
                    c,
                    subId,
                    false /* gainCarrierPrivileges */,
                    false /* overrideCarrierServicePackage */,
                    false /* isShell */);
        }
    }
}
