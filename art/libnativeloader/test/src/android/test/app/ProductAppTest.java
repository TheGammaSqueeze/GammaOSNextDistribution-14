/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.test.app;

import android.test.lib.AppTestCommon;
import android.test.lib.TestUtils;
import android.test.productsharedlib.ProductSharedLib;
import android.test.systemextsharedlib.SystemExtSharedLib;
import android.test.systemsharedlib.SystemSharedLib;
import android.test.vendorsharedlib.VendorSharedLib;

import androidx.test.filters.MediumTest;

import org.junit.Test;

@MediumTest
public class ProductAppTest extends AppTestCommon {
    @Override
    public AppLocation getAppLocation() {
        return AppLocation.PRODUCT;
    }

    @Test
    public void testPrivateLibsExist() {
        TestUtils.testPrivateLibsExist("/product", "product_private");
    }

    @Test
    public void testLoadExtendedPublicLibraries() {
        System.loadLibrary("system_extpub.oem1");
        System.loadLibrary("system_extpub.oem2");
        System.loadLibrary("system_extpub1.oem1");
        if (!TestUtils.productAppsAreShared()) {
            TestUtils.assertLibraryInaccessible( // Missing <uses-native-library>.
                    () -> System.loadLibrary("system_extpub_nouses.oem2"));
        }
        System.loadLibrary("product_extpub.product1");
        System.loadLibrary("product_extpub1.product1");
    }

    @Test
    public void testLoadPrivateLibraries() {
        if (!TestUtils.productAppsAreShared()) {
            TestUtils.assertLibraryInaccessible(() -> System.loadLibrary("system_private1"));
            TestUtils.assertLibraryInaccessible(() -> System.loadLibrary("systemext_private1"));
        }
        System.loadLibrary("product_private1");
        TestUtils.assertLibraryInaccessible(() -> System.loadLibrary("vendor_private1"));
    }

    @Test
    public void testLoadExtendedPublicLibrariesViaSystemSharedLib() {
        SystemSharedLib.loadLibrary("system_extpub2.oem1");
        if (!TestUtils.skipPublicProductLibTests()) {
            SystemSharedLib.loadLibrary("product_extpub2.product1");
        }
    }

    @Test
    public void testLoadPrivateLibrariesViaSystemSharedLib() {
        if (!TestUtils.productAppsAreShared()) {
            // TODO(b/237577392): Loading a private native system library via a shared system
            // library ought to work.
            TestUtils.assertLibraryInaccessible(
                    () -> SystemSharedLib.loadLibrary("system_private2"));
            TestUtils.assertLibraryInaccessible(
                    () -> SystemSharedLib.loadLibrary("systemext_private2"));

            TestUtils.assertLibraryInaccessible(
                    () -> SystemSharedLib.loadLibrary("product_private2"));
        }

        TestUtils.assertLibraryInaccessible(() -> SystemSharedLib.loadLibrary("vendor_private2"));
    }

    @Test
    public void testLoadPrivateLibrariesViaSystemExtSharedLib() {
        if (!TestUtils.productAppsAreShared()) {
            // TODO(b/237577392): Loading a private native system library via a shared system
            // library ought to work.
            TestUtils.assertLibraryInaccessible(
                    () -> SystemExtSharedLib.loadLibrary("system_private3"));
            TestUtils.assertLibraryInaccessible(
                    () -> SystemExtSharedLib.loadLibrary("systemext_private3"));

            TestUtils.assertLibraryInaccessible(
                    () -> SystemExtSharedLib.loadLibrary("product_private3"));
        }

        TestUtils.assertLibraryInaccessible(
                () -> SystemExtSharedLib.loadLibrary("vendor_private3"));
    }

    @Test
    public void testLoadPrivateLibrariesViaProductSharedLib() {
        if (!TestUtils.productAppsAreShared()) {
            TestUtils.assertLibraryInaccessible(
                    () -> ProductSharedLib.loadLibrary("system_private4"));
            TestUtils.assertLibraryInaccessible(
                    () -> ProductSharedLib.loadLibrary("systemext_private4"));
        }

        // Can load product_private4 by name only through the app classloader namespace.
        ProductSharedLib.loadLibrary("product_private4");

        TestUtils.assertLibraryInaccessible(() -> ProductSharedLib.loadLibrary("vendor_private4"));
    }

    @Test
    public void testLoadPrivateLibrariesViaVendorSharedLib() {
        if (!TestUtils.productAppsAreShared()) {
            TestUtils.assertLibraryInaccessible(
                    () -> VendorSharedLib.loadLibrary("system_private5"));
            TestUtils.assertLibraryInaccessible(
                    () -> VendorSharedLib.loadLibrary("systemext_private5"));

            TestUtils.assertLibraryInaccessible(
                    () -> VendorSharedLib.loadLibrary("product_private5"));

            // When the app has a shared namespace, its libraries get loaded
            // with shared namespaces as well, inheriting the same paths. So
            // since the app wouldn't have access to /vendor/${LIB},
            // VendorSharedLib here wouldn't either, and this would fail.
            VendorSharedLib.loadLibrary("vendor_private5");
        }
    }

    @Test
    public void testLoadExtendedPublicLibrariesWithAbsolutePaths() {
        System.load(TestUtils.libPath("/system", "system_extpub3.oem1"));
        System.load(TestUtils.libPath("/product", "product_extpub3.product1"));
    }

    @Test
    public void testLoadPrivateLibrariesWithAbsolutePaths() {
        if (!TestUtils.productAppsAreShared()) {
            TestUtils.assertLibraryInaccessible(
                    () -> System.load(TestUtils.libPath("/system", "system_private6")));
            TestUtils.assertLibraryInaccessible(
                    () -> System.load(TestUtils.libPath("/system_ext", "systemext_private6")));
        }
        System.load(TestUtils.libPath("/product", "product_private6"));
        TestUtils.assertLibraryInaccessible(
                () -> System.load(TestUtils.libPath("/vendor", "vendor_private6")));
    }
}
