/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.test.lib;

import android.test.productsharedlib.ProductSharedLib;
import android.test.systemextsharedlib.SystemExtSharedLib;
import android.test.systemsharedlib.SystemSharedLib;
import android.test.vendorsharedlib.VendorSharedLib;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public abstract class AppTestCommon {
    public enum AppLocation { DATA, SYSTEM, PRODUCT, VENDOR }

    public abstract AppLocation getAppLocation();

    // Loading private libs using absolute paths through shared libs should
    // normally only depend on the location of the shared lib, so these tests
    // are shared for all apps, regardless of location.

    // Returns true when system private native libs are accessible directly from
    // the app classloader namespace.
    private boolean systemPrivateLibsAccessibleFromAppNamespace() {
        // Currently it only works from system apps. It also works from product
        // apps on old versions where they were treated like system apps.
        // TODO(b/237577392): Fix this to work from system shared libs.
        return getAppLocation() == AppLocation.SYSTEM
                || (getAppLocation() == AppLocation.PRODUCT && TestUtils.productAppsAreShared());
    }

    // Detect exception when product private libs are accessible directly from
    // the app classloader namespace even when they shouldn't be.
    private boolean productPrivateLibsAccessibleFromAppNamespace() {
        // In old versions where product apps were treated like system apps, the
        // product private libs were included in the system namespace, so
        // they're accessible both from system and product apps.
        // TODO(b/237577392): Fix this to work from product shared libs.
        return (getAppLocation() == AppLocation.SYSTEM || getAppLocation() == AppLocation.PRODUCT)
                && TestUtils.productAppsAreShared();
    }

    // Detect exception where we don't switch from a shared system namespace to
    // a product or vendor "unbundled" namespace when calling into
    // ProductSharedLib and VendorSharedLib. That means they still can load
    // private system libs but not private libs in their own partition.
    // TODO(mast): Stop propagating the shared property (isBundledApp in
    // LoadedApk.java) down to public and vendor shared java libs?
    private boolean noSwitchToVendorOrProductNamespace() {
        // System apps get shared namespaces, and also product apps on old
        // versions where they were treated like system apps.
        return getAppLocation() == AppLocation.SYSTEM
                || (getAppLocation() == AppLocation.PRODUCT && TestUtils.productAppsAreShared());
    }

    @Test
    public void testLoadPrivateLibrariesViaSystemSharedLibWithAbsolutePaths() {
        if (systemPrivateLibsAccessibleFromAppNamespace()) {
            SystemSharedLib.load(TestUtils.libPath("/system", "system_private7"));
            SystemSharedLib.load(TestUtils.libPath("/system_ext", "systemext_private7"));
        } else {
            TestUtils.assertLibraryInaccessible(() -> {
                SystemSharedLib.load(TestUtils.libPath("/system", "system_private7"));
            });
            TestUtils.assertLibraryInaccessible(() -> {
                SystemSharedLib.load(TestUtils.libPath("/system_ext", "systemext_private7"));
            });
        }

        if (productPrivateLibsAccessibleFromAppNamespace()) {
            SystemSharedLib.load(TestUtils.libPath("/product", "product_private7"));
        } else {
            TestUtils.assertLibraryInaccessible(() -> {
                SystemSharedLib.load(TestUtils.libPath("/product", "product_private7"));
            });
        }

        TestUtils.assertLibraryInaccessible(
                () -> { SystemSharedLib.load(TestUtils.libPath("/vendor", "vendor_private7")); });
    }

    @Test
    public void testLoadPrivateLibrariesViaSystemExtSharedLibWithAbsolutePaths() {
        if (systemPrivateLibsAccessibleFromAppNamespace()) {
            SystemExtSharedLib.load(TestUtils.libPath("/system", "system_private8"));
            SystemExtSharedLib.load(TestUtils.libPath("/system_ext", "systemext_private8"));
        } else {
            TestUtils.assertLibraryInaccessible(() -> {
                SystemExtSharedLib.load(TestUtils.libPath("/system", "system_private8"));
            });
            TestUtils.assertLibraryInaccessible(() -> {
                SystemExtSharedLib.load(TestUtils.libPath("/system_ext", "systemext_private8"));
            });
        }

        if (productPrivateLibsAccessibleFromAppNamespace()) {
            SystemExtSharedLib.load(TestUtils.libPath("/product", "product_private8"));
        } else {
            TestUtils.assertLibraryInaccessible(() -> {
                SystemExtSharedLib.load(TestUtils.libPath("/product", "product_private8"));
            });
        }

        TestUtils.assertLibraryInaccessible(() -> {
            SystemExtSharedLib.load(TestUtils.libPath("/vendor", "vendor_private8"));
        });
    }

    @Test
    public void testLoadPrivateLibrariesViaProductSharedLibWithAbsolutePaths() {
        if (systemPrivateLibsAccessibleFromAppNamespace() || noSwitchToVendorOrProductNamespace()) {
            ProductSharedLib.load(TestUtils.libPath("/system", "system_private9"));
            ProductSharedLib.load(TestUtils.libPath("/system_ext", "systemext_private9"));
        } else {
            TestUtils.assertLibraryInaccessible(() -> {
                ProductSharedLib.load(TestUtils.libPath("/system", "system_private9"));
            });
            TestUtils.assertLibraryInaccessible(() -> {
                ProductSharedLib.load(TestUtils.libPath("/system_ext", "systemext_private9"));
            });
        }

        boolean loadPrivateProductLib;
        if (TestUtils.productAppsAreShared()) {
            // The library is accessible if the app is in either system or
            // product, because both are loaded as system apps and private product
            // libs are available for both.
            loadPrivateProductLib = getAppLocation() == AppLocation.SYSTEM
                    || getAppLocation() == AppLocation.PRODUCT;
        } else {
            loadPrivateProductLib = !noSwitchToVendorOrProductNamespace();
        }
        if (loadPrivateProductLib) {
            ProductSharedLib.load(TestUtils.libPath("/product", "product_private9"));
        } else {
            TestUtils.assertLibraryInaccessible(() -> {
                ProductSharedLib.load(TestUtils.libPath("/product", "product_private9"));
            });
        }

        TestUtils.assertLibraryInaccessible(
                () -> { ProductSharedLib.load(TestUtils.libPath("/vendor", "vendor_private9")); });
    }

    @Test
    public void testLoadPrivateLibrariesViaVendorSharedLibWithAbsolutePaths() {
        if (systemPrivateLibsAccessibleFromAppNamespace() || noSwitchToVendorOrProductNamespace()) {
            VendorSharedLib.load(TestUtils.libPath("/system", "system_private10"));
            VendorSharedLib.load(TestUtils.libPath("/system_ext", "systemext_private10"));
        } else {
            TestUtils.assertLibraryInaccessible(() -> {
                VendorSharedLib.load(TestUtils.libPath("/system", "system_private10"));
            });
            TestUtils.assertLibraryInaccessible(() -> {
                VendorSharedLib.load(TestUtils.libPath("/system_ext", "systemext_private10"));
            });
        }

        if (productPrivateLibsAccessibleFromAppNamespace()) {
            VendorSharedLib.load(TestUtils.libPath("/product", "product_private10"));
        } else {
            TestUtils.assertLibraryInaccessible(() -> {
                VendorSharedLib.load(TestUtils.libPath("/product", "product_private10"));
            });
        }

        if (!noSwitchToVendorOrProductNamespace()) {
            VendorSharedLib.load(TestUtils.libPath("/vendor", "vendor_private10"));
        } else {
            TestUtils.assertLibraryInaccessible(() -> {
                VendorSharedLib.load(TestUtils.libPath("/vendor", "vendor_private10"));
            });
        }
    }
}
