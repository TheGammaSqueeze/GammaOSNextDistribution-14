/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.cts.net;

import org.junit.Test;

public class HostsideSelfDeclaredNetworkCapabilitiesCheckTest extends HostsideNetworkTestCase {

    private static final String TEST_WITH_PROPERTY_IN_CURRENT_SDK_APK =
            "CtsHostsideNetworkCapTestsAppWithProperty.apk";
    private static final String TEST_WITHOUT_PROPERTY_IN_CURRENT_SDK_APK =
            "CtsHostsideNetworkCapTestsAppWithoutProperty.apk";
    private static final String TEST_IN_SDK_33_APK =
            "CtsHostsideNetworkCapTestsAppSdk33.apk";
    private static final String TEST_APP_PKG =
            "com.android.cts.net.hostside.networkslicingtestapp";
    private static final String TEST_CLASS_NAME = ".NetworkSelfDeclaredCapabilitiesTest";
    private static final String WITH_SELF_DECLARED_CAPABILITIES_METHOD =
            "requestNetwork_withSelfDeclaredCapabilities";
    private static final String LACKING_SELF_DECLARED_CAPABILITIES_METHOD =
            "requestNetwork_lackingRequiredSelfDeclaredCapabilities";
    private static final String WITHOUT_REQUEST_CAPABILITIES_METHOD =
            "requestNetwork_withoutRequestCapabilities";


    @Test
    public void testRequestNetworkInCurrentSdkWithProperty() throws Exception {
        uninstallPackage(TEST_APP_PKG, false);
        installPackage(TEST_WITH_PROPERTY_IN_CURRENT_SDK_APK);
        // If the self-declared capabilities are defined,
        // the ConnectivityManager.requestNetwork() call should always pass.
        runDeviceTests(TEST_APP_PKG,
                TEST_APP_PKG + TEST_CLASS_NAME,
                WITH_SELF_DECLARED_CAPABILITIES_METHOD);
        runDeviceTests(TEST_APP_PKG,
                TEST_APP_PKG + TEST_CLASS_NAME,
                WITHOUT_REQUEST_CAPABILITIES_METHOD);
        uninstallPackage(TEST_APP_PKG, true);
    }

    @Test
    public void testRequestNetworkInCurrentSdkWithoutProperty() throws Exception {
        uninstallPackage(TEST_APP_PKG, false);
        installPackage(TEST_WITHOUT_PROPERTY_IN_CURRENT_SDK_APK);
        // If the self-declared capabilities are not defined,
        // the ConnectivityManager.requestNetwork() call will fail if the properly is not declared.
        runDeviceTests(TEST_APP_PKG,
                TEST_APP_PKG + TEST_CLASS_NAME,
                LACKING_SELF_DECLARED_CAPABILITIES_METHOD);
        runDeviceTests(TEST_APP_PKG,
                TEST_APP_PKG + TEST_CLASS_NAME,
                WITHOUT_REQUEST_CAPABILITIES_METHOD);
        uninstallPackage(TEST_APP_PKG, true);
    }

    @Test
    public void testRequestNetworkInSdk33() throws Exception {
        uninstallPackage(TEST_APP_PKG, false);
        installPackage(TEST_IN_SDK_33_APK);
        // In Sdk33, the ConnectivityManager.requestNetwork() call should always pass.
        runDeviceTests(TEST_APP_PKG,
                TEST_APP_PKG + TEST_CLASS_NAME,
                WITH_SELF_DECLARED_CAPABILITIES_METHOD);
        runDeviceTests(TEST_APP_PKG,
                TEST_APP_PKG + TEST_CLASS_NAME,
                WITHOUT_REQUEST_CAPABILITIES_METHOD);
        uninstallPackage(TEST_APP_PKG, true);
    }

    @Test
    public void testReinstallPackageWillUpdateProperty() throws Exception {
        uninstallPackage(TEST_APP_PKG, false);
        installPackage(TEST_WITHOUT_PROPERTY_IN_CURRENT_SDK_APK);
        runDeviceTests(TEST_APP_PKG,
                TEST_APP_PKG + TEST_CLASS_NAME,
                LACKING_SELF_DECLARED_CAPABILITIES_METHOD);
        uninstallPackage(TEST_APP_PKG, true);


        // Updates package.
        installPackage(TEST_WITH_PROPERTY_IN_CURRENT_SDK_APK);
        runDeviceTests(TEST_APP_PKG,
                TEST_APP_PKG + TEST_CLASS_NAME,
                WITH_SELF_DECLARED_CAPABILITIES_METHOD);
        uninstallPackage(TEST_APP_PKG, true);

    }
}

