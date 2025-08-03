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

package com.android.server.art;

import static com.android.server.art.DexUseManagerLocal.CheckedSecondaryDexInfo;
import static com.android.server.art.DexUseManagerLocal.DexLoader;
import static com.android.server.art.DexUseManagerLocal.SecondaryDexInfo;
import static com.android.server.art.model.DexoptStatus.DexContainerFileDexoptStatus;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import android.annotation.NonNull;
import android.os.SystemProperties;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.server.art.model.DexoptStatus;
import com.android.server.art.testing.StaticMockitoRule;
import com.android.server.pm.PackageManagerLocal;
import com.android.server.pm.pkg.AndroidPackage;
import com.android.server.pm.pkg.PackageState;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class DumpHelperTest {
    private static final String PKG_NAME_FOO = "com.example1.foo";
    private static final String PKG_NAME_BAR = "com.example2.bar";

    @Rule
    public StaticMockitoRule mockitoRule =
            new StaticMockitoRule(SystemProperties.class, Constants.class, ArtJni.class);

    @Mock private DumpHelper.Injector mInjector;
    @Mock private ArtManagerLocal mArtManagerLocal;
    @Mock private DexUseManagerLocal mDexUseManagerLocal;
    @Mock private PackageManagerLocal.FilteredSnapshot mSnapshot;

    private DumpHelper mDumpHelper;

    @Before
    public void setUp() throws Exception {
        lenient().when(Constants.getPreferredAbi()).thenReturn("arm64-v8a");
        lenient().when(Constants.getNative64BitAbi()).thenReturn("arm64-v8a");
        lenient().when(Constants.getNative32BitAbi()).thenReturn("armeabi-v7a");

        // No ISA translation.
        lenient()
                .when(SystemProperties.get(argThat(arg -> arg.startsWith("ro.dalvik.vm.isa."))))
                .thenReturn("");

        lenient().when(ArtJni.getGarbageCollector()).thenReturn("CollectorTypeCMC");

        lenient().when(mInjector.getArtManagerLocal()).thenReturn(mArtManagerLocal);
        lenient().when(mInjector.getDexUseManager()).thenReturn(mDexUseManagerLocal);

        Map<String, PackageState> pkgStates = createPackageStates();
        lenient().when(mSnapshot.getPackageStates()).thenReturn(pkgStates);
        for (var entry : pkgStates.entrySet()) {
            lenient().when(mSnapshot.getPackageState(entry.getKey())).thenReturn(entry.getValue());
        }

        setUpForFoo();
        setUpForBar();

        mDumpHelper = new DumpHelper(mInjector);
    }

    @Test
    public void testDump() throws Exception {
        String expected = "[com.example1.foo]\n"
                + "  path: /somewhere/app/foo/base.apk\n"
                + "    arm64: [status=speed-profile] [reason=bg-dexopt] [primary-abi]\n"
                + "      [location is /somewhere/app/foo/oat/arm64/base.odex]\n"
                + "    arm: [status=verify] [reason=install]\n"
                + "      [location is /somewhere/app/foo/oat/arm/base.odex]\n"
                + "  path: /somewhere/app/foo/split_0.apk\n"
                + "    arm64: [status=verify] [reason=vdex] [primary-abi]\n"
                + "      [location is primary.vdex in /somewhere/app/foo/split_0.dm]\n"
                + "    arm: [status=verify] [reason=vdex]\n"
                + "      [location is primary.vdex in /somewhere/app/foo/split_0.dm]\n"
                + "    used by other apps: [com.example2.bar (isa=arm)]\n"
                + "  known secondary dex files:\n"
                + "    /data/user_de/0/foo/1.apk (removed)\n"
                + "      arm: [status=run-from-apk] [reason=unknown]\n"
                + "        [location is unknown]\n"
                + "      class loader context: =VaryingClassLoaderContexts=\n"
                + "        com.example1.foo (isolated): CLC1\n"
                + "        com.example3.baz: CLC2\n"
                + "      used by other apps: [com.example1.foo (isolated) (isa=arm64), com.example3.baz (removed)]\n"
                + "    /data/user_de/0/foo/2.apk (public)\n"
                + "      arm64: [status=speed-profile] [reason=bg-dexopt] [primary-abi]\n"
                + "        [location is /data/user_de/0/foo/oat/arm64/2.odex]\n"
                + "      arm: [status=verify] [reason=vdex]\n"
                + "        [location is /data/user_de/0/foo/oat/arm/2.vdex]\n"
                + "      class loader context: PCL[]\n"
                + "[com.example2.bar]\n"
                + "  path: /somewhere/app/bar/base.apk\n"
                + "    arm: [status=verify] [reason=install] [primary-abi]\n"
                + "      [location is /somewhere/app/bar/oat/arm/base.odex]\n"
                + "    arm64: [status=verify] [reason=install]\n"
                + "      [location is /somewhere/app/bar/oat/arm64/base.odex]\n"
                + "\n"
                + "Current GC: CollectorTypeCMC\n";

        var stringWriter = new StringWriter();
        mDumpHelper.dump(new PrintWriter(stringWriter), mSnapshot);
        assertThat(stringWriter.toString()).isEqualTo(expected);
    }

    private PackageState createPackageState(@NonNull String packageName, int appId,
            boolean hasPackage, @NonNull String primaryAbi, @NonNull String secondaryAbi) {
        var pkgState = mock(PackageState.class);
        lenient().when(pkgState.getPackageName()).thenReturn(packageName);
        lenient().when(pkgState.getAppId()).thenReturn(appId);
        lenient()
                .when(pkgState.getAndroidPackage())
                .thenReturn(hasPackage ? mock(AndroidPackage.class) : null);
        lenient().when(pkgState.getPrimaryCpuAbi()).thenReturn(primaryAbi);
        lenient().when(pkgState.getSecondaryCpuAbi()).thenReturn(secondaryAbi);
        return pkgState;
    }

    private Map<String, PackageState> createPackageStates() {
        var pkgStates = new HashMap<String, PackageState>();
        pkgStates.put(PKG_NAME_FOO,
                createPackageState(PKG_NAME_FOO, 10001 /* appId */, true /* hasPackage */,
                        "arm64-v8a", "armeabi-v7a"));
        pkgStates.put(PKG_NAME_BAR,
                createPackageState(PKG_NAME_BAR, 10003 /* appId */, true /* hasPackage */,
                        "armeabi-v7a", "arm64-v8a"));
        // This should not be included in the output because it has a negative app id.
        pkgStates.put("com.android.art",
                createPackageState("com.android.art", -1 /* appId */, true /* hasPackage */,
                        "arm64-v8a", "armeabi-v7a"));
        // This should not be included in the output because it does't have AndroidPackage.
        pkgStates.put("com.example.null",
                createPackageState("com.example.null", 10010 /* appId */, false /* hasPackage */,
                        "arm64-v8a", "armeabi-v7a"));
        return pkgStates;
    }

    private void setUpForFoo() throws Exception {
        // The order of the primary dex files and the ABIs should be kept in the output. Secondary
        // dex files should be reordered in lexicographical order.
        var status = DexoptStatus.create(List.of(
                DexContainerFileDexoptStatus.create("/somewhere/app/foo/base.apk",
                        true /* isPrimaryDex */, true /* isPrimaryAbi */, "arm64-v8a",
                        "speed-profile", "bg-dexopt", "/somewhere/app/foo/oat/arm64/base.odex"),
                DexContainerFileDexoptStatus.create("/somewhere/app/foo/base.apk",
                        true /* isPrimaryDex */, false /* isPrimaryAbi */, "armeabi-v7a", "verify",
                        "install", "/somewhere/app/foo/oat/arm/base.odex"),
                DexContainerFileDexoptStatus.create("/somewhere/app/foo/split_0.apk",
                        true /* isPrimaryDex */, true /* isPrimaryAbi */, "arm64-v8a", "verify",
                        "vdex", "primary.vdex in /somewhere/app/foo/split_0.dm"),
                DexContainerFileDexoptStatus.create("/somewhere/app/foo/split_0.apk",
                        true /* isPrimaryDex */, false /* isPrimaryAbi */, "armeabi-v7a", "verify",
                        "vdex", "primary.vdex in /somewhere/app/foo/split_0.dm"),
                DexContainerFileDexoptStatus.create("/data/user_de/0/foo/2.apk",
                        false /* isPrimaryDex */, true /* isPrimaryAbi */, "arm64-v8a",
                        "speed-profile", "bg-dexopt", "/data/user_de/0/foo/oat/arm64/2.odex"),
                DexContainerFileDexoptStatus.create("/data/user_de/0/foo/2.apk",
                        false /* isPrimaryDex */, false /* isPrimaryAbi */, "armeabi-v7a", "verify",
                        "vdex", "/data/user_de/0/foo/oat/arm/2.vdex"),
                DexContainerFileDexoptStatus.create("/data/user_de/0/foo/1.apk",
                        false /* isPrimaryDex */, false /* isPrimaryAbi */, "armeabi-v7a",
                        "run-from-apk", "unknown", "unknown")));

        lenient()
                .when(mArtManagerLocal.getDexoptStatus(any(), eq(PKG_NAME_FOO)))
                .thenReturn(status);

        // The output should not show "used by other apps:".
        lenient()
                .when(mDexUseManagerLocal.getPrimaryDexLoaders(
                        PKG_NAME_FOO, "/somewhere/app/foo/base.apk"))
                .thenReturn(Set.of());

        // The output should not show "foo" in "used by other apps:".
        lenient()
                .when(mDexUseManagerLocal.getPrimaryDexLoaders(
                        PKG_NAME_FOO, "/somewhere/app/foo/split_0.apk"))
                .thenReturn(Set.of(DexLoader.create(PKG_NAME_FOO, false /* isolatedProcess */),
                        DexLoader.create(PKG_NAME_BAR, false /* isolatedProcess */)));

        var info1 = mock(CheckedSecondaryDexInfo.class);
        lenient().when(info1.dexPath()).thenReturn("/data/user_de/0/foo/1.apk");
        lenient()
                .when(info1.displayClassLoaderContext())
                .thenReturn(SecondaryDexInfo.VARYING_CLASS_LOADER_CONTEXTS);

        lenient()
                .when(mDexUseManagerLocal.getSecondaryClassLoaderContext(PKG_NAME_FOO,
                        "/data/user_de/0/foo/1.apk",
                        DexLoader.create(PKG_NAME_FOO, true /* isolatedProcess */)))
                .thenReturn("CLC1");
        lenient()
                .when(mDexUseManagerLocal.getSecondaryClassLoaderContext(PKG_NAME_FOO,
                        "/data/user_de/0/foo/1.apk",
                        DexLoader.create("com.example3.baz", false /* isolatedProcess */)))
                .thenReturn("CLC2");

        var loaders = new HashSet<DexLoader>();
        // The output should show "foo" with "(isolated)" in "used by other apps:".
        loaders.add(DexLoader.create(PKG_NAME_FOO, true /* isolatedProcess */));
        // The output should show "baz" with "(removed)" in "used by other apps:".
        loaders.add(DexLoader.create("com.example3.baz", false /* isolatedProcess */));
        lenient().when(info1.loaders()).thenReturn(loaders);

        // The output should show the dex path with "(removed)".
        lenient().when(info1.fileVisibility()).thenReturn(FileVisibility.NOT_FOUND);

        var info2 = mock(CheckedSecondaryDexInfo.class);
        lenient().when(info2.dexPath()).thenReturn("/data/user_de/0/foo/2.apk");
        lenient().when(info2.displayClassLoaderContext()).thenReturn("PCL[]");
        lenient()
                .when(mDexUseManagerLocal.getSecondaryClassLoaderContext(PKG_NAME_FOO,
                        "/data/user_de/0/foo/2.apk",
                        DexLoader.create(PKG_NAME_FOO, false /* isolatedProcess */)))
                .thenReturn("PCL[]");
        // The output should not show "used by other apps:".
        lenient()
                .when(info2.loaders())
                .thenReturn(Set.of(DexLoader.create(PKG_NAME_FOO, false /* isolatedProcess */)));
        lenient().when(info2.fileVisibility()).thenReturn(FileVisibility.OTHER_READABLE);

        lenient()
                .doReturn(List.of(info1, info2))
                .when(mDexUseManagerLocal)
                .getCheckedSecondaryDexInfo(
                        PKG_NAME_FOO, false /* excludeObsoleteDexesAndLoaders */);
    }

    private void setUpForBar() {
        // The order of the ABI should be kept in the output, despite that it's different from the
        // order for package "foo".
        // The output should not show "known secondary dex files:".
        var status = DexoptStatus.create(
                List.of(DexContainerFileDexoptStatus.create("/somewhere/app/bar/base.apk",
                                true /* isPrimaryDex */, true /* isPrimaryAbi */, "armeabi-v7a",
                                "verify", "install", "/somewhere/app/bar/oat/arm/base.odex"),
                        DexContainerFileDexoptStatus.create("/somewhere/app/bar/base.apk",
                                true /* isPrimaryDex */, false /* isPrimaryAbi */, "arm64-v8a",
                                "verify", "install", "/somewhere/app/bar/oat/arm64/base.odex")));

        lenient()
                .when(mArtManagerLocal.getDexoptStatus(any(), eq(PKG_NAME_BAR)))
                .thenReturn(status);

        lenient()
                .when(mDexUseManagerLocal.getPrimaryDexLoaders(
                        PKG_NAME_BAR, "/somewhere/app/bar/base.apk"))
                .thenReturn(Set.of());
    }
}
