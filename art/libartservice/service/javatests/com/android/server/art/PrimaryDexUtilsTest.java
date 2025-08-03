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

import static com.android.server.art.PrimaryDexUtils.DetailedPrimaryDexInfo;
import static com.android.server.art.PrimaryDexUtils.PrimaryDexInfo;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import android.util.Pair;

import androidx.test.filters.SmallTest;

import com.android.server.pm.pkg.AndroidPackage;
import com.android.server.pm.pkg.AndroidPackageSplit;
import com.android.server.pm.pkg.PackageState;
import com.android.server.pm.pkg.SharedLibrary;

import dalvik.system.DelegateLastClassLoader;
import dalvik.system.DexClassLoader;
import dalvik.system.PathClassLoader;

import com.google.common.truth.Correspondence;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.List;

@SmallTest
@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class PrimaryDexUtilsTest {
    @Before
    public void setUp() {}

    @Test
    public void testGetDexInfo() {
        List<PrimaryDexInfo> infos =
                PrimaryDexUtils.getDexInfo(createPackage(false /* isIsolatedSplitLoading */));
        checkBasicInfo(infos);
    }

    @Test
    public void testGetDetailedDexInfo() {
        List<DetailedPrimaryDexInfo> infos = PrimaryDexUtils.getDetailedDexInfo(
                createPackageState(), createPackage(false /* isIsolatedSplitLoading */));
        checkBasicInfo(infos);

        String sharedLibrariesContext = "{"
                + "PCL[library_2.jar]{PCL[library_1_dex_1.jar:library_1_dex_2.jar]}#"
                + "PCL[library_3.jar]#"
                + "PCL[library_4.jar]{PCL[library_1_dex_1.jar:library_1_dex_2.jar]}"
                + "}";

        assertThat(infos)
                .comparingElementsUsing(Correspondence.transforming(
                        (DetailedPrimaryDexInfo info)
                                -> Pair.create(info.splitName(), info.classLoaderContext()),
                        "has split name and CLC of"))
                .containsExactly(Pair.create(null, "PCL[]" + sharedLibrariesContext),
                        Pair.create("split_0", "PCL[base.apk]" + sharedLibrariesContext),
                        Pair.create("split_2",
                                "PCL[base.apk:split_0.apk:split_1.apk]" + sharedLibrariesContext),
                        Pair.create("split_3",
                                "PCL[base.apk:split_0.apk:split_1.apk:split_2.apk]"
                                        + sharedLibrariesContext),
                        Pair.create("split_4",
                                "PCL[base.apk:split_0.apk:split_1.apk:split_2.apk:split_3.apk]"
                                        + sharedLibrariesContext));
    }

    @Test
    public void testGetDetailedDexInfoIsolated() {
        List<DetailedPrimaryDexInfo> infos = PrimaryDexUtils.getDetailedDexInfo(
                createPackageState(), createPackage(true /* isIsolatedSplitLoading */));
        checkBasicInfo(infos);

        String sharedLibrariesContext = "{"
                + "PCL[library_2.jar]{PCL[library_1_dex_1.jar:library_1_dex_2.jar]}#"
                + "PCL[library_3.jar]#"
                + "PCL[library_4.jar]{PCL[library_1_dex_1.jar:library_1_dex_2.jar]}"
                + "}";

        assertThat(infos)
                .comparingElementsUsing(Correspondence.transforming(
                        (DetailedPrimaryDexInfo info)
                                -> Pair.create(info.splitName(), info.classLoaderContext()),
                        "has split name and CLC of"))
                .containsExactly(Pair.create(null, "PCL[]" + sharedLibrariesContext),
                        Pair.create("split_0",
                                "PCL[];DLC[split_2.apk];PCL[base.apk]" + sharedLibrariesContext),
                        Pair.create("split_2", "DLC[];PCL[base.apk]" + sharedLibrariesContext),
                        Pair.create("split_3", "PCL[]"),
                        Pair.create("split_4", "PCL[];PCL[split_3.apk]"));
    }

    private <T extends PrimaryDexInfo> void checkBasicInfo(List<T> infos) {
        assertThat(infos.get(0).dexPath()).isEqualTo("/somewhere/app/foo/base.apk");
        assertThat(infos.get(0).hasCode()).isTrue();
        assertThat(infos.get(0).splitName()).isNull();

        assertThat(infos.get(1).dexPath()).isEqualTo("/somewhere/app/foo/split_0.apk");
        assertThat(infos.get(1).hasCode()).isTrue();
        assertThat(infos.get(1).splitName()).isEqualTo("split_0");

        // split_1 is skipped because it has no code.

        assertThat(infos.get(2).dexPath()).isEqualTo("/somewhere/app/foo/split_2.apk");
        assertThat(infos.get(2).hasCode()).isTrue();
        assertThat(infos.get(2).splitName()).isEqualTo("split_2");

        assertThat(infos.get(3).dexPath()).isEqualTo("/somewhere/app/foo/split_3.apk");
        assertThat(infos.get(3).hasCode()).isTrue();
        assertThat(infos.get(3).splitName()).isEqualTo("split_3");

        assertThat(infos.get(4).dexPath()).isEqualTo("/somewhere/app/foo/split_4.apk");
        assertThat(infos.get(4).hasCode()).isTrue();
        assertThat(infos.get(4).splitName()).isEqualTo("split_4");
    }

    private AndroidPackage createPackage(boolean isIsolatedSplitLoading) {
        AndroidPackage pkg = mock(AndroidPackage.class);

        var baseSplit = mock(AndroidPackageSplit.class);
        lenient().when(baseSplit.getPath()).thenReturn("/somewhere/app/foo/base.apk");
        lenient().when(baseSplit.isHasCode()).thenReturn(true);
        lenient().when(baseSplit.getClassLoaderName()).thenReturn(PathClassLoader.class.getName());

        var split0 = mock(AndroidPackageSplit.class);
        lenient().when(split0.getName()).thenReturn("split_0");
        lenient().when(split0.getPath()).thenReturn("/somewhere/app/foo/split_0.apk");
        lenient().when(split0.isHasCode()).thenReturn(true);

        var split1 = mock(AndroidPackageSplit.class);
        lenient().when(split1.getName()).thenReturn("split_1");
        lenient().when(split1.getPath()).thenReturn("/somewhere/app/foo/split_1.apk");
        lenient().when(split1.isHasCode()).thenReturn(false);

        var split2 = mock(AndroidPackageSplit.class);
        lenient().when(split2.getName()).thenReturn("split_2");
        lenient().when(split2.getPath()).thenReturn("/somewhere/app/foo/split_2.apk");
        lenient().when(split2.isHasCode()).thenReturn(true);

        var split3 = mock(AndroidPackageSplit.class);
        lenient().when(split3.getName()).thenReturn("split_3");
        lenient().when(split3.getPath()).thenReturn("/somewhere/app/foo/split_3.apk");
        lenient().when(split3.isHasCode()).thenReturn(true);

        var split4 = mock(AndroidPackageSplit.class);
        lenient().when(split4.getName()).thenReturn("split_4");
        lenient().when(split4.getPath()).thenReturn("/somewhere/app/foo/split_4.apk");
        lenient().when(split4.isHasCode()).thenReturn(true);

        var splits = List.of(baseSplit, split0, split1, split2, split3, split4);
        lenient().when(pkg.getSplits()).thenReturn(splits);

        if (isIsolatedSplitLoading) {
            // split_0: PCL(PathClassLoader), depends on split_2.
            // split_1: no code.
            // split_2: DLC(DelegateLastClassLoader), depends on base.
            // split_3: PCL(DexClassLoader), no dependency.
            // split_4: PCL(null), depends on split_3.
            lenient().when(split0.getClassLoaderName()).thenReturn(PathClassLoader.class.getName());
            lenient().when(split1.getClassLoaderName()).thenReturn(null);
            lenient()
                    .when(split2.getClassLoaderName())
                    .thenReturn(DelegateLastClassLoader.class.getName());
            lenient().when(split3.getClassLoaderName()).thenReturn(DexClassLoader.class.getName());
            lenient().when(split4.getClassLoaderName()).thenReturn(null);

            lenient().when(split0.getDependencies()).thenReturn(List.of(split2));
            lenient().when(split2.getDependencies()).thenReturn(List.of(baseSplit));
            lenient().when(split4.getDependencies()).thenReturn(List.of(split3));
            lenient().when(pkg.isIsolatedSplitLoading()).thenReturn(true);
        } else {
            lenient().when(pkg.isIsolatedSplitLoading()).thenReturn(false);
        }

        return pkg;
    }

    private PackageState createPackageState() {
        PackageState pkgState = mock(PackageState.class);

        lenient().when(pkgState.getPackageName()).thenReturn("com.example.foo");

        // Base depends on library 2, 3, 4.
        // Library 2, 4 depends on library 1.
        List<SharedLibrary> usesLibraryInfos = new ArrayList<>();

        // The native library should not be added to the CLC.
        SharedLibrary libraryNative = mock(SharedLibrary.class);
        lenient().when(libraryNative.getAllCodePaths()).thenReturn(List.of("library_native.so"));
        lenient().when(libraryNative.getDependencies()).thenReturn(null);
        lenient().when(libraryNative.isNative()).thenReturn(true);
        usesLibraryInfos.add(libraryNative);

        SharedLibrary library1 = mock(SharedLibrary.class);
        lenient()
                .when(library1.getAllCodePaths())
                .thenReturn(List.of("library_1_dex_1.jar", "library_1_dex_2.jar"));
        lenient().when(library1.getDependencies()).thenReturn(null);
        lenient().when(library1.isNative()).thenReturn(false);

        SharedLibrary library2 = mock(SharedLibrary.class);
        lenient().when(library2.getAllCodePaths()).thenReturn(List.of("library_2.jar"));
        lenient().when(library2.getDependencies()).thenReturn(List.of(library1, libraryNative));
        lenient().when(library2.isNative()).thenReturn(false);
        usesLibraryInfos.add(library2);

        SharedLibrary library3 = mock(SharedLibrary.class);
        lenient().when(library3.getAllCodePaths()).thenReturn(List.of("library_3.jar"));
        lenient().when(library3.getDependencies()).thenReturn(null);
        lenient().when(library3.isNative()).thenReturn(false);
        usesLibraryInfos.add(library3);

        SharedLibrary library4 = mock(SharedLibrary.class);
        lenient().when(library4.getAllCodePaths()).thenReturn(List.of("library_4.jar"));
        lenient().when(library4.getDependencies()).thenReturn(List.of(library1));
        lenient().when(library4.isNative()).thenReturn(false);
        usesLibraryInfos.add(library4);

        lenient().when(pkgState.getSharedLibraryDependencies()).thenReturn(usesLibraryInfos);

        return pkgState;
    }
}
