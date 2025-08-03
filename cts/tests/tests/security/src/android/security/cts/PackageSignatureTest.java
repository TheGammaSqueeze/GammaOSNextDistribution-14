/*
 * Copyright (C) 2011 The Android Open Source Project
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

package android.security.cts;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.Signature;
import android.content.res.Resources.NotFoundException;
import android.platform.test.annotations.RestrictedBuildTest;
import android.test.AndroidTestCase;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PackageSignatureTest extends AndroidTestCase {

    private static final String TAG = PackageSignatureTest.class.getSimpleName();
    private static final Pattern TEST_PACKAGE_PATTERN = Pattern.compile("android\\.[^\\.]+\\.cts");

    @RestrictedBuildTest
    public void testPackageSignatures() throws Exception {
        Set<String> badPackages = new TreeSet<>();
        Set<Signature> wellKnownSignatures = getWellKnownSignatures();

        PackageManager packageManager = mContext.getPackageManager();
        List<PackageInfo> allPackageInfos = packageManager.getInstalledPackages(
                PackageManager.GET_UNINSTALLED_PACKAGES |
                PackageManager.GET_SIGNATURES |
                PackageManager.MATCH_APEX);
        for (PackageInfo packageInfo : allPackageInfos) {
            String packageName = packageInfo.packageName;
            Log.v(TAG, "Scanning " + packageName);
            if (packageName != null && !isWhitelistedPackage(packageName)) {
                for (Signature signature : packageInfo.signatures) {
                    if (wellKnownSignatures.contains(signature)) {
                        badPackages.add(packageInfo.packageName);
                    }
                }
            }
        }

        assertTrue("These packages should not be signed with a well known key: " + badPackages,
                badPackages.isEmpty());
    }

    /**
     * Returns the well-known dev-key signatures, e.g. to detect cases where devices under test are
     * using modules that have been signed using dev keys; Google will supply modules that have been
     * signed with production keys in all cases.
     *
     * <p>See {@link #writeSignature(String, String)} for instructions for how to create the raw
     * .bin files when adding entries to this list.
     */
    private Set<Signature> getWellKnownSignatures() throws NotFoundException, IOException {
        Set<Signature> wellKnownSignatures = new HashSet<>();
        wellKnownSignatures.add(getSignature(R.raw.sig_media));
        wellKnownSignatures.add(getSignature(R.raw.sig_platform));
        wellKnownSignatures.add(getSignature(R.raw.sig_shared));
        wellKnownSignatures.add(getSignature(R.raw.sig_testkey));
        wellKnownSignatures.add(getSignature(R.raw.sig_debug));
        wellKnownSignatures.add(getSignature(R.raw.sig_devkeys));
        wellKnownSignatures.add(getSignature(R.raw.sig_networkstack));
        wellKnownSignatures.add(getSignature(R.raw.sig_devkeys_media));
        wellKnownSignatures.add(getSignature(R.raw.sig_devkeys_platform));
        wellKnownSignatures.add(getSignature(R.raw.sig_devkeys_shared));
        wellKnownSignatures.add(getSignature(R.raw.sig_devkeys_networkstack));
        wellKnownSignatures.add(getSignature(R.raw.sig_com_android_conscrypt));
        wellKnownSignatures.add(getSignature(R.raw.sig_com_android_media));
        wellKnownSignatures.add(getSignature(R.raw.sig_com_android_media_swcodec));
        wellKnownSignatures.add(getSignature(R.raw.sig_com_android_resolv));
        wellKnownSignatures.add(getSignature(R.raw.sig_com_android_runtime_debug));
        wellKnownSignatures.add(getSignature(R.raw.sig_com_android_runtime_release));
        wellKnownSignatures.add(getSignature(R.raw.sig_com_android_tzdata));
        wellKnownSignatures.add(getSignature(R.raw.sig_com_google_android_conscrypt));
        wellKnownSignatures.add(getSignature(R.raw.sig_com_google_android_media));
        wellKnownSignatures.add(getSignature(R.raw.sig_com_google_android_media_swcodec));
        wellKnownSignatures.add(getSignature(R.raw.sig_com_google_android_resolv));
        wellKnownSignatures.add(getSignature(R.raw.sig_com_google_android_runtime_debug));
        wellKnownSignatures.add(getSignature(R.raw.sig_com_google_android_runtime_release));
        wellKnownSignatures.add(getSignature(R.raw.sig_com_google_android_tzdata3));
        // The following keys are not used by modules on the latest Android release, but it
        // won't negatively affect tests to include their signatures here too.
        wellKnownSignatures.add(getSignature(R.raw.sig_com_google_android_tzdata));
        wellKnownSignatures.add(getSignature(R.raw.sig_com_google_android_tzdata2));
        wellKnownSignatures.add(getSignature(R.raw.sig_android_telephony_cts_testkey));
        wellKnownSignatures.add(getSignature(R.raw.sig_build_bazel_examples_apex_minimal));
        wellKnownSignatures.add(getSignature(R.raw.sig_cert));
        wellKnownSignatures.add(getSignature(R.raw.sig_com_android_adbd));
        wellKnownSignatures.add(getSignature(R.raw.sig_com_android_adservices));
        wellKnownSignatures.add(getSignature(R.raw.sig_com_android_adservices_api));
        wellKnownSignatures.add(getSignature(R.raw.sig_com_android_appsearch));
        wellKnownSignatures.add(getSignature(R.raw.sig_com_android_art));
        wellKnownSignatures.add(getSignature(R.raw.sig_com_android_bluetooth));
        wellKnownSignatures.add(getSignature(R.raw.sig_com_android_btservices));
        wellKnownSignatures.add(getSignature(R.raw.sig_com_android_car_framework));
        wellKnownSignatures.add(getSignature(R.raw.sig_com_android_cellbroadcast));
        wellKnownSignatures.add(getSignature(R.raw.sig_com_android_compos));
        wellKnownSignatures.add(getSignature(R.raw.sig_com_android_connectivity_resources));
        wellKnownSignatures.add(getSignature(R.raw.sig_com_android_extservices));
        wellKnownSignatures.add(getSignature(R.raw.sig_com_android_geotz));
        wellKnownSignatures.add(getSignature(R.raw.sig_com_android_hardware_core_permissions));
        wellKnownSignatures.add(getSignature(R.raw.sig_com_android_hardware_power));
        wellKnownSignatures.add(getSignature(R.raw.sig_com_android_hardware_sensors));
        wellKnownSignatures.add(getSignature(R.raw.sig_com_android_hardware_thermal));
        wellKnownSignatures.add(getSignature(R.raw.sig_com_android_hardware_usb));
        wellKnownSignatures.add(getSignature(R.raw.sig_com_android_hardware_vibrator));
        wellKnownSignatures.add(getSignature(R.raw.sig_com_android_hardware_wifi));
        wellKnownSignatures.add(getSignature(R.raw.sig_com_android_hotspot2_osulogin));
        wellKnownSignatures.add(getSignature(R.raw.sig_com_android_i18n));
        wellKnownSignatures.add(getSignature(R.raw.sig_com_android_ipsec));
        wellKnownSignatures.add(getSignature(R.raw.sig_com_android_mediaprovider));
        wellKnownSignatures.add(getSignature(R.raw.sig_com_android_neuralnetworks));
        wellKnownSignatures.add(getSignature(R.raw.sig_com_android_os_statsd));
        wellKnownSignatures.add(getSignature(R.raw.sig_com_android_permission));
        wellKnownSignatures.add(getSignature(R.raw.sig_com_android_rkpd));
        wellKnownSignatures.add(getSignature(R.raw.sig_com_android_runtime));
        wellKnownSignatures.add(getSignature(R.raw.sig_com_android_safetycenter_resources));
        wellKnownSignatures.add(getSignature(R.raw.sig_com_android_sdkext));
        wellKnownSignatures.add(getSignature(R.raw.sig_com_android_uwb));
        wellKnownSignatures.add(getSignature(R.raw.sig_com_android_uwb_resources));
        wellKnownSignatures.add(getSignature(R.raw.sig_com_android_vibrator_drv2624));
        wellKnownSignatures.add(getSignature(R.raw.sig_com_android_vibrator_sunfish));
        wellKnownSignatures.add(getSignature(R.raw.sig_com_android_virt));
        wellKnownSignatures.add(getSignature(R.raw.sig_com_android_vndk));
        wellKnownSignatures.add(getSignature(R.raw.sig_com_android_wifi));
        wellKnownSignatures.add(getSignature(R.raw.sig_com_android_wifi_dialog));
        wellKnownSignatures.add(getSignature(R.raw.sig_com_android_wifi_resources));
        wellKnownSignatures.add(getSignature(R.raw.sig_com_google_cf_apex));
        wellKnownSignatures.add(getSignature(R.raw.sig_com_google_emulated_camera_provider_hal));
        wellKnownSignatures.add(getSignature(R.raw.sig_cts_appsearch_helper_cert_a));
        wellKnownSignatures.add(getSignature(R.raw.sig_cts_appsearch_helper_cert_b));
        wellKnownSignatures.add(getSignature(R.raw.sig_cts_appsearch_hosttest_helper_cert_a));
        wellKnownSignatures.add(getSignature(R.raw.sig_cts_appsearch_hosttest_helper_cert_b));
        wellKnownSignatures.add(getSignature(R.raw.sig_cts_blob_helper_cert));
        wellKnownSignatures.add(getSignature(R.raw.sig_cts_blob_helper_cert2));
        wellKnownSignatures.add(getSignature(R.raw.sig_cts_keyset_test_a));
        wellKnownSignatures.add(getSignature(R.raw.sig_cts_keyset_test_b));
        wellKnownSignatures.add(getSignature(R.raw.sig_cts_keyset_test_c));
        wellKnownSignatures.add(getSignature(R.raw.sig_cts_keyset_test_ec_a));
        wellKnownSignatures.add(getSignature(R.raw.sig_cts_net_app));
        wellKnownSignatures.add(getSignature(R.raw.sig_cts_testkey1));
        wellKnownSignatures.add(getSignature(R.raw.sig_cts_testkey2));
        wellKnownSignatures.add(getSignature(R.raw.sig_cts_uicc_2021));
        wellKnownSignatures.add(getSignature(R.raw.sig_ec_p256));
        wellKnownSignatures.add(getSignature(R.raw.sig_ec_p256_2));
        wellKnownSignatures.add(getSignature(R.raw.sig_ec_p256_3));
        wellKnownSignatures.add(getSignature(R.raw.sig_ec_p256_4));
        wellKnownSignatures.add(getSignature(R.raw.sig_ec_p256_5));
        wellKnownSignatures.add(getSignature(R.raw.sig_keyset_A));
        wellKnownSignatures.add(getSignature(R.raw.sig_keyset_B));
        wellKnownSignatures.add(getSignature(R.raw.sig_rro_remounted_test_a));
        wellKnownSignatures.add(getSignature(R.raw.sig_rsa_2048));
        wellKnownSignatures.add(getSignature(R.raw.sig_sdk_sandbox));
        wellKnownSignatures.add(getSignature(R.raw.sig_security_cts_test_cert));
        wellKnownSignatures.add(getSignature(R.raw.sig_shell_as_test_app_key));
        wellKnownSignatures.add(getSignature(R.raw.sig_test_cert_1));
        wellKnownSignatures.add(getSignature(R.raw.sig_test_cert_2));
        wellKnownSignatures.add(getSignature(R.raw.sig_test_cert_3));
        wellKnownSignatures.add(getSignature(R.raw.sig_test_cert_4));
        wellKnownSignatures.add(getSignature(R.raw.sig_testcert));
        wellKnownSignatures.add(getSignature(R.raw.sig_unit_test));
        return wellKnownSignatures;
    }

    private static final Set<String> WHITELISTED_PACKAGES = new HashSet<String>(Arrays.asList(
            // APKS are installed before beigning test
            "android.netsecpolicy.usescleartext_false.cts",
            "android.netsecpolicy.usescleartext_unspecified.cts",
            "android.netsecpolicy.usescleartext_true.cts",

            // The accessibility APK required to be installed while running CTS
            "android.accessibilityservice.delegate",

            // The device management APK required to be installed while running CTS
            "android.deviceadmin.cts",

            // APK for an activity that collects information printed in the CTS report header
            "android.tests.devicesetup",

            // Test utilities used by Tradefed harness
            "com.android.tradefed.utils.wifi",
            "android.tradefed.contentprovider",
            "androidx.test.services",

            // Game used for CTS testing...
            "com.replica.replicaisland",

            // CTS test
            "android.core.tests.libcore.package.com",
            "android.core.tests.libcore.package.conscrypt",
            "android.core.tests.libcore.package.dalvik",
            "android.core.tests.libcore.package.libcore",
            "android.core.tests.libcore.package.org",
            "android.core.tests.libcore.package.sun",
            "android.core.tests.libcore.package.tests",
            "com.android.cts.RemoteDPC",
            "com.android.testutils.connectivitychecker",

            // Test package to verify upgrades to privileged applications
            "com.android.cts.priv.ctsshim",
            "com.android.cts.ctsshim",
            // Test APEX used in CTS tests.
            "com.android.apex.cts.shim",

            // Oom Catcher package to prevent tests from ooming device.
            "com.android.cts.oomcatcher",

            // Collects device info at the start of the test
            "com.android.compatibility.common.deviceinfo"

            ));

    private boolean isWhitelistedPackage(String packageName) {
        // Don't check the signatures of CTS test packages on the device.
        // devicesetup is the APK CTS loads to collect information needed in the final report
        final Matcher matcher = TEST_PACKAGE_PATTERN.matcher(packageName);
        return matcher.matches() || WHITELISTED_PACKAGES.contains(packageName);
    }

    private static final int DEFAULT_BUFFER_BYTES = 1024 * 4;

    private Signature getSignature(int resId) throws NotFoundException, IOException {
        InputStream input = mContext.getResources().openRawResource(resId);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try {
            byte[] buffer = new byte[DEFAULT_BUFFER_BYTES];
            int numBytes = 0;
            while ((numBytes = input.read(buffer)) != -1) {
                output.write(buffer, 0, numBytes);
            }
            return new Signature(output.toByteArray());
        } finally {
            input.close();
            output.close();
        }
    }

    /**
     * Writes a package's signature to a file on the device's external storage.
     * This method was used to generate the well known signatures used by
     * this test.
     *
     * As an alternative, you can use openssl to create the
     * DER encoded certificate file.
     *
     * openssl x509 -in $ANDROID_HOME/build/target/product/security/media.x509.pem \
     *     -out sig_media.bin -outform DER
     */
    @SuppressWarnings("unused")
    private void writeSignature(String packageName, String fileName)
            throws NameNotFoundException, IOException {
        PackageManager packageManager = mContext.getPackageManager();
        PackageInfo packageInfo = packageManager.getPackageInfo(packageName,
                PackageManager.GET_SIGNATURES);
        File directory = mContext.getExternalFilesDir(null);
        int numSignatures = packageInfo.signatures.length;
        Log.i(TAG, "Will dump " + numSignatures + " signatures to " + directory);
        for (int i = 0; i < numSignatures; i++) {
            Signature signature = packageInfo.signatures[i];
            byte[] signatureBytes = signature.toByteArray();
            File signatureFile = new File(directory, fileName + "." + i);
            FileOutputStream output = null;
            try {
                output = new FileOutputStream(signatureFile);
                output.write(signatureBytes);
            } finally {
                if (output != null) {
                    output.close();
                }
            }
        }
    }
}
