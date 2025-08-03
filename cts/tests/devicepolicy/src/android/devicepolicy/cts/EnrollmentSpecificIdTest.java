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

import static android.Manifest.permission.LOCAL_MAC_ADDRESS;
import static android.Manifest.permission.NETWORK_SETTINGS;
import static android.Manifest.permission.READ_PRIVILEGED_PHONE_STATE;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.annotation.NonNull;
import android.app.admin.flags.Flags;
import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.telephony.TelephonyManager;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureHasPermission;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasDevicePolicyManagerRoleHolder;
import com.android.bedstead.harrier.annotations.enterprise.PolicyAppliesTest;
import com.android.bedstead.harrier.policies.EnrollmentSpecificId;
import com.android.bedstead.nene.TestApis;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;

import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@RunWith(BedsteadJUnit4.class)
public final class EnrollmentSpecificIdTest {

    private static final String ORGANIZATION_ID = "abcxyz123";
    private static final String DIFFERENT_ORGANIZATION_ID = "xyz";

    @ClassRule
    public static final DeviceState sDeviceState = new DeviceState();

    @Rule
    public final TestRule mCheckFlagsRule = RuleChain
            .outerRule(DeviceFlagsValueProvider.createCheckFlagsRule())
            .around(sDeviceState);
    private static final Context sContext = TestApis.context().instrumentedContext();

    @Postsubmit(reason = "New test")
    @PolicyAppliesTest(policy = EnrollmentSpecificId.class)
    public void emptyOrganizationId_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> sDeviceState.dpc().devicePolicyManager().setOrganizationId(""));
    }

    @Postsubmit(reason = "New test")
    @PolicyAppliesTest(policy = EnrollmentSpecificId.class)
    public void reSetOrganizationId_throws() {
        try {
            sDeviceState.dpc().devicePolicyManager().setOrganizationId(ORGANIZATION_ID);

            assertThrows(IllegalStateException.class,
                    () -> sDeviceState.dpc().devicePolicyManager()
                            .setOrganizationId(DIFFERENT_ORGANIZATION_ID));
        } finally {
            TestApis.devicePolicy().clearOrganizationId(sDeviceState.dpc().user());
        }
    }

    /**
     * This test tests that the platform calculates the ESID according to the specification and
     * does not, for example, return the same ESID regardless of the managing package.
     */
    @Postsubmit(reason = "New test")
    @PolicyAppliesTest(policy = EnrollmentSpecificId.class)
    @EnsureHasPermission({READ_PRIVILEGED_PHONE_STATE, NETWORK_SETTINGS, LOCAL_MAC_ADDRESS})
    public void enrollmentSpecificId_CorrectlyCalculated() {
        try {
            sDeviceState.dpc().devicePolicyManager().setOrganizationId(ORGANIZATION_ID);
            final String esidFromDpm = sDeviceState.dpc().devicePolicyManager()
                    .getEnrollmentSpecificId();
            final String calculatedEsid = calculateEsid(
                    sDeviceState.dpc().componentName().getPackageName(),
                    ORGANIZATION_ID);

            assertThat(esidFromDpm).isEqualTo(calculatedEsid);
        } finally {
            TestApis.devicePolicy().clearOrganizationId(sDeviceState.dpc().user());
        }
    }

    /**
     * New role holder test
     */
    @Postsubmit(reason = "New test")
    @PolicyAppliesTest(policy = EnrollmentSpecificId.class)
    @EnsureHasPermission({READ_PRIVILEGED_PHONE_STATE, NETWORK_SETTINGS, LOCAL_MAC_ADDRESS})
    @EnsureHasDevicePolicyManagerRoleHolder
    @RequiresFlagsEnabled(Flags.FLAG_PERMISSION_MIGRATION_FOR_ZERO_TRUST_IMPL_ENABLED)
    public void enrollmentSpecificId_RoleHolderCanAccess() {
        try {
            sDeviceState.dpc().devicePolicyManager().setOrganizationId(ORGANIZATION_ID);
            String esidFromDpm = sDeviceState.dpc().devicePolicyManager()
                    .getEnrollmentSpecificId();
            String esidFromRoleHolder = sDeviceState.dpmRoleHolder().devicePolicyManager()
                    .getEnrollmentSpecificId();

            assertThat(esidFromDpm).isEqualTo(esidFromRoleHolder);
        } finally {
            TestApis.devicePolicy().clearOrganizationId(sDeviceState.dpc().user());
        }
    }

    private String calculateEsid(String profileOwnerPackage, String enterpriseIdString) {
        TelephonyManager telephonyService = sContext.getSystemService(TelephonyManager.class);
        assertThat(telephonyService).isNotNull();

        WifiManager wifiManager = sContext.getSystemService(WifiManager.class);
        assertThat(wifiManager).isNotNull();

        final byte[] serialNumber = getPaddedHardwareIdentifier(Build.getSerial()).getBytes();
        final byte[] imei = getPaddedHardwareIdentifier(telephonyService.getImei(0)).getBytes();
        final byte[] meid = getPaddedHardwareIdentifier(telephonyService.getMeid(0)).getBytes();

        final byte[] macAddress;
        final String[] macAddresses = wifiManager.getFactoryMacAddresses();
        if (macAddresses == null || macAddresses.length == 0) {
            macAddress = "".getBytes();
        } else {
            macAddress = macAddresses[0].getBytes();
        }

        final int totalIdentifiersLength = serialNumber.length + imei.length + meid.length
                + macAddress.length;
        final ByteBuffer fixedIdentifiers = ByteBuffer.allocate(totalIdentifiersLength);
        fixedIdentifiers.put(serialNumber);
        fixedIdentifiers.put(imei);
        fixedIdentifiers.put(meid);
        fixedIdentifiers.put(macAddress);

        final byte[] dpcPackage = getPaddedProfileOwnerName(profileOwnerPackage).getBytes();
        final byte[] enterpriseId = getPaddedEnterpriseId(enterpriseIdString).getBytes();
        final ByteBuffer info = ByteBuffer.allocate(dpcPackage.length + enterpriseId.length);
        info.put(dpcPackage);
        info.put(enterpriseId);
        final byte[] esidBytes = computeHkdf("HMACSHA256", fixedIdentifiers.array(), null,
                info.array(), 16);
        ByteBuffer esidByteBuffer = ByteBuffer.wrap(esidBytes);

        return encodeBase32(esidByteBuffer.getLong()) + encodeBase32(esidByteBuffer.getLong());
    }

    private static String getPaddedHardwareIdentifier(String hardwareIdentifier) {
        if (hardwareIdentifier == null) {
            hardwareIdentifier = "";
        }
        String hwIdentifier = String.format("%16s", hardwareIdentifier);
        return hwIdentifier.substring(0, 16);
    }

    private static String getPaddedProfileOwnerName(String profileOwnerPackage) {
        return String.format("%64s", profileOwnerPackage);
    }

    private static String getPaddedEnterpriseId(String enterpriseId) {
        return String.format("%64s", enterpriseId);
    }

    // Copied from android.security.identity.Util, here to make sure Enterprise-Specific ID is
    // calculated according to spec.
    @NonNull
    private static byte[] computeHkdf(
            @NonNull String macAlgorithm, @NonNull final byte[] ikm, @NonNull final byte[] salt,
            @NonNull final byte[] info, int size) {
        Mac mac = null;
        try {
            mac = Mac.getInstance(macAlgorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("No such algorithm: " + macAlgorithm, e);
        }
        if (size > 255 * mac.getMacLength()) {
            throw new RuntimeException("size too large");
        }
        try {
            if (salt == null || salt.length == 0) {
                // According to RFC 5869, Section 2.2 the salt is optional. If no salt is provided
                // then HKDF uses a salt that is an array of zeros of the same length as the hash
                // digest.
                mac.init(new SecretKeySpec(new byte[mac.getMacLength()], macAlgorithm));
            } else {
                mac.init(new SecretKeySpec(salt, macAlgorithm));
            }
            byte[] prk = mac.doFinal(ikm);
            byte[] result = new byte[size];
            int ctr = 1;
            int pos = 0;
            mac.init(new SecretKeySpec(prk, macAlgorithm));
            byte[] digest = new byte[0];
            while (true) {
                mac.update(digest);
                mac.update(info);
                mac.update((byte) ctr);
                digest = mac.doFinal();
                if (pos + digest.length < size) {
                    System.arraycopy(digest, 0, result, pos, digest.length);
                    pos += digest.length;
                    ctr++;
                } else {
                    System.arraycopy(digest, 0, result, pos, size - pos);
                    break;
                }
            }
            return result;
        } catch (InvalidKeyException e) {
            throw new RuntimeException("Error MACing", e);
        }
    }

    private static final char[] ENCODE = {
            'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H',
            'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P',
            'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X',
            'Y', 'Z', '2', '3', '4', '5', '6', '7',
    };

    private static final char SEPARATOR = '-';
    private static final int LONG_SIZE = 13;
    private static final int GROUP_SIZE = 4;

    private static String encodeBase32(long input) {
        final char[] alphabet = ENCODE;

        /*
         * Make a character array with room for the separators between each
         * group.
         */
        final char[] encoded = new char[LONG_SIZE + (LONG_SIZE / GROUP_SIZE)];

        int index = encoded.length;
        for (int i = 0; i < LONG_SIZE; i++) {
            /*
             * Make sure we don't put a separator at the beginning. Since we're
             * building from the rear of the array, we use (LONG_SIZE %
             * GROUP_SIZE) to make the odd-size group appear at the end instead
             * of the beginning.
             */
            if (i > 0 && (i % GROUP_SIZE) == (LONG_SIZE % GROUP_SIZE)) {
                encoded[--index] = SEPARATOR;
            }

            /*
             * Extract 5 bits of data, then shift it out.
             */
            final int group = (int) (input & 0x1F);
            input >>>= 5;

            encoded[--index] = alphabet[group];
        }

        return String.valueOf(encoded);
    }
}
