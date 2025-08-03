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
 * limitations under the License
 */

package com.android.server.art;

import static android.os.ParcelFileDescriptor.AutoCloseInputStream;

import static com.android.server.art.DexUseManagerLocal.CheckedSecondaryDexInfo;
import static com.android.server.art.model.DexoptResult.DexContainerFileDexoptResult;
import static com.android.server.art.model.DexoptResult.PackageDexoptResult;
import static com.android.server.art.model.DexoptStatus.DexContainerFileDexoptStatus;
import static com.android.server.art.testing.TestingUtils.deepEq;
import static com.android.server.art.testing.TestingUtils.inAnyOrder;
import static com.android.server.art.testing.TestingUtils.inAnyOrderDeepEquals;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.AdditionalMatchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.apphibernation.AppHibernationManager;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.ServiceSpecificException;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.os.storage.StorageManager;

import androidx.test.filters.SmallTest;

import com.android.modules.utils.pm.PackageStateModulesUtils;
import com.android.server.art.model.ArtFlags;
import com.android.server.art.model.ArtManagedFileStats;
import com.android.server.art.model.Config;
import com.android.server.art.model.DeleteResult;
import com.android.server.art.model.DexoptParams;
import com.android.server.art.model.DexoptResult;
import com.android.server.art.model.DexoptStatus;
import com.android.server.art.testing.StaticMockitoRule;
import com.android.server.art.testing.TestingUtils;
import com.android.server.pm.PackageManagerLocal;
import com.android.server.pm.pkg.AndroidPackage;
import com.android.server.pm.pkg.AndroidPackageSplit;
import com.android.server.pm.pkg.PackageState;
import com.android.server.pm.pkg.PackageUserState;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.mockito.Mock;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@SmallTest
@RunWith(Parameterized.class)
public class ArtManagerLocalTest {
    private static final String PKG_NAME_1 = "com.example.foo";
    private static final String PKG_NAME_2 = "com.android.bar";
    private static final String PKG_NAME_HIBERNATING = "com.example.hibernating";
    private static final int INACTIVE_DAYS = 1;
    private static final long CURRENT_TIME_MS = 10000000000l;
    private static final long RECENT_TIME_MS =
            CURRENT_TIME_MS - TimeUnit.DAYS.toMillis(INACTIVE_DAYS) + 1;
    private static final long NOT_RECENT_TIME_MS =
            CURRENT_TIME_MS - TimeUnit.DAYS.toMillis(INACTIVE_DAYS) - 1;

    @Rule
    public StaticMockitoRule mockitoRule = new StaticMockitoRule(
            SystemProperties.class, Constants.class, PackageStateModulesUtils.class);

    @Mock private ArtManagerLocal.Injector mInjector;
    @Mock private ArtFileManager.Injector mArtFileManagerInjector;
    @Mock private PackageManagerLocal mPackageManagerLocal;
    @Mock private PackageManagerLocal.FilteredSnapshot mSnapshot;
    @Mock private IArtd mArtd;
    @Mock private DexoptHelper mDexoptHelper;
    @Mock private AppHibernationManager mAppHibernationManager;
    @Mock private UserManager mUserManager;
    @Mock private DexUseManagerLocal mDexUseManager;
    @Mock private StorageManager mStorageManager;
    @Mock private ArtdRefCache.Pin mArtdPin;
    private PackageState mPkgState1;
    private AndroidPackage mPkg1;
    private CheckedSecondaryDexInfo mPkg1SecondaryDexInfo1;
    private CheckedSecondaryDexInfo mPkg1SecondaryDexInfoNotFound;
    private Config mConfig;

    // True if the artifacts should be in dalvik-cache.
    @Parameter(0) public boolean mIsInDalvikCache;

    private ArtManagerLocal mArtManagerLocal;

    @Parameters(name = "mIsInDalvikCache={0}")
    public static Iterable<Object[]> data() {
        return List.of(new Object[] {true}, new Object[] {false});
    }

    @Before
    public void setUp() throws Exception {
        mConfig = new Config();

        // Use `lenient()` to suppress `UnnecessaryStubbingException` thrown by the strict stubs.
        // These are the default test setups. They may or may not be used depending on the code path
        // that each test case examines.
        lenient().when(mInjector.getPackageManagerLocal()).thenReturn(mPackageManagerLocal);
        lenient().when(mInjector.getArtd()).thenReturn(mArtd);
        lenient().when(mInjector.createArtdPin()).thenReturn(mArtdPin);
        lenient().when(mInjector.getDexoptHelper()).thenReturn(mDexoptHelper);
        lenient().when(mInjector.getConfig()).thenReturn(mConfig);
        lenient().when(mInjector.getAppHibernationManager()).thenReturn(mAppHibernationManager);
        lenient().when(mInjector.getUserManager()).thenReturn(mUserManager);
        lenient().when(mInjector.isSystemUiPackage(any())).thenReturn(false);
        lenient().when(mInjector.isLauncherPackage(any())).thenReturn(false);
        lenient().when(mInjector.getDexUseManager()).thenReturn(mDexUseManager);
        lenient().when(mInjector.getCurrentTimeMillis()).thenReturn(CURRENT_TIME_MS);
        lenient().when(mInjector.getStorageManager()).thenReturn(mStorageManager);
        lenient()
                .when(mInjector.getArtFileManager())
                .thenReturn(new ArtFileManager(mArtFileManagerInjector));

        lenient().when(mArtFileManagerInjector.getArtd()).thenReturn(mArtd);
        lenient().when(mArtFileManagerInjector.getUserManager()).thenReturn(mUserManager);
        lenient().when(mArtFileManagerInjector.getDexUseManager()).thenReturn(mDexUseManager);
        lenient().when(mArtFileManagerInjector.isSystemOrRootOrShell()).thenReturn(true);

        Path tempDir = Files.createTempDirectory("temp");
        tempDir.toFile().deleteOnExit();
        lenient().when(mInjector.getTempDir()).thenReturn(tempDir.toString());

        lenient().when(SystemProperties.get(eq("pm.dexopt.install"))).thenReturn("speed-profile");
        lenient().when(SystemProperties.get(eq("pm.dexopt.bg-dexopt"))).thenReturn("speed-profile");
        lenient().when(SystemProperties.get(eq("pm.dexopt.first-boot"))).thenReturn("verify");
        lenient()
                .when(SystemProperties.get(eq("pm.dexopt.boot-after-mainline-update")))
                .thenReturn("verify");
        lenient().when(SystemProperties.get(eq("pm.dexopt.inactive"))).thenReturn("verify");
        lenient()
                .when(SystemProperties.getInt(matches("pm\\.dexopt\\..*\\.concurrency"), anyInt()))
                .thenReturn(3);
        lenient()
                .when(SystemProperties.getInt(
                        matches("persist\\.device_config\\.runtime\\..*_concurrency"), anyInt()))
                .thenReturn(3);
        lenient()
                .when(SystemProperties.getInt(
                        eq("pm.dexopt.downgrade_after_inactive_days"), anyInt()))
                .thenReturn(INACTIVE_DAYS);

        // No ISA translation.
        lenient()
                .when(SystemProperties.get(argThat(arg -> arg.startsWith("ro.dalvik.vm.isa."))))
                .thenReturn("");

        lenient().when(Constants.getPreferredAbi()).thenReturn("arm64-v8a");
        lenient().when(Constants.getNative64BitAbi()).thenReturn("arm64-v8a");
        lenient().when(Constants.getNative32BitAbi()).thenReturn("armeabi-v7a");
        lenient().when(Constants.isBootImageProfilingEnabled()).thenReturn(true);

        lenient().when(mAppHibernationManager.isHibernatingGlobally(any())).thenReturn(false);
        lenient().when(mAppHibernationManager.isOatArtifactDeletionEnabled()).thenReturn(true);

        lenient()
                .when(mUserManager.getUserHandles(anyBoolean()))
                .thenReturn(List.of(UserHandle.of(0), UserHandle.of(1)));

        // All packages are by default recently used.
        lenient().when(mDexUseManager.getPackageLastUsedAtMs(any())).thenReturn(RECENT_TIME_MS);
        mPkg1SecondaryDexInfo1 = createSecondaryDexInfo("/data/user/0/foo/1.apk", UserHandle.of(0));
        mPkg1SecondaryDexInfoNotFound =
                createSecondaryDexInfo("/data/user/0/foo/not_found.apk", UserHandle.of(0));
        lenient()
                .doReturn(List.of(mPkg1SecondaryDexInfo1, mPkg1SecondaryDexInfoNotFound))
                .when(mDexUseManager)
                .getSecondaryDexInfo(eq(PKG_NAME_1));
        lenient()
                .doReturn(List.of(mPkg1SecondaryDexInfo1, mPkg1SecondaryDexInfoNotFound))
                .when(mDexUseManager)
                .getCheckedSecondaryDexInfo(
                        eq(PKG_NAME_1), eq(false) /* excludeObsoleteDexesAndLoaders */);
        lenient()
                .doReturn(List.of(mPkg1SecondaryDexInfo1))
                .when(mDexUseManager)
                .getCheckedSecondaryDexInfo(
                        eq(PKG_NAME_1), eq(true) /* excludeObsoleteDexesAndLoaders */);

        simulateStorageNotLow();

        lenient().when(mPackageManagerLocal.withFilteredSnapshot()).thenReturn(mSnapshot);
        List<PackageState> pkgStates = createPackageStates();
        for (PackageState pkgState : pkgStates) {
            lenient()
                    .when(mSnapshot.getPackageState(pkgState.getPackageName()))
                    .thenReturn(pkgState);
        }
        var packageStateMap = pkgStates.stream().collect(
                Collectors.toMap(PackageState::getPackageName, it -> it));
        lenient().when(mSnapshot.getPackageStates()).thenReturn(packageStateMap);
        mPkgState1 = mSnapshot.getPackageState(PKG_NAME_1);
        mPkg1 = mPkgState1.getAndroidPackage();

        lenient().when(mArtd.isInDalvikCache(any())).thenReturn(mIsInDalvikCache);

        // By default, none of the profiles are usable.
        lenient().when(mArtd.isProfileUsable(any(), any())).thenReturn(false);
        lenient()
                .when(mArtd.copyAndRewriteProfile(any(), any(), any()))
                .thenReturn(TestingUtils.createCopyAndRewriteProfileNoProfile());
        lenient()
                .when(mArtd.copyAndRewriteEmbeddedProfile(any(), any()))
                .thenReturn(TestingUtils.createCopyAndRewriteProfileNoProfile());

        mArtManagerLocal = new ArtManagerLocal(mInjector);
    }

    @Test
    public void testDeleteDexoptArtifacts() throws Exception {
        final long DEXOPT_ARTIFACTS_FREED = 1l;
        final long RUNTIME_ARTIFACTS_FREED = 100l;

        when(mArtd.deleteArtifacts(any())).thenReturn(DEXOPT_ARTIFACTS_FREED);
        when(mArtd.deleteRuntimeArtifacts(any())).thenReturn(RUNTIME_ARTIFACTS_FREED);

        DeleteResult result = mArtManagerLocal.deleteDexoptArtifacts(mSnapshot, PKG_NAME_1);
        assertThat(result.getFreedBytes())
                .isEqualTo(6 * DEXOPT_ARTIFACTS_FREED + 4 * RUNTIME_ARTIFACTS_FREED);

        verify(mArtd).deleteArtifacts(deepEq(AidlUtils.buildArtifactsPath(
                "/somewhere/app/foo/base.apk", "arm64", mIsInDalvikCache)));
        verify(mArtd).deleteArtifacts(deepEq(AidlUtils.buildArtifactsPath(
                "/somewhere/app/foo/base.apk", "arm", mIsInDalvikCache)));
        verify(mArtd).deleteArtifacts(deepEq(AidlUtils.buildArtifactsPath(
                "/somewhere/app/foo/split_0.apk", "arm64", mIsInDalvikCache)));
        verify(mArtd).deleteArtifacts(deepEq(AidlUtils.buildArtifactsPath(
                "/somewhere/app/foo/split_0.apk", "arm", mIsInDalvikCache)));
        verify(mArtd).deleteArtifacts(deepEq(AidlUtils.buildArtifactsPath(
                "/data/user/0/foo/1.apk", "arm64", false /* isInDalvikCache */)));
        verify(mArtd).deleteArtifacts(deepEq(AidlUtils.buildArtifactsPath(
                "/data/user/0/foo/not_found.apk", "arm64", false /* isInDalvikCache */)));

        verify(mArtd).deleteRuntimeArtifacts(deepEq(AidlUtils.buildRuntimeArtifactsPath(
                PKG_NAME_1, "/somewhere/app/foo/base.apk", "arm64")));
        verify(mArtd).deleteRuntimeArtifacts(deepEq(AidlUtils.buildRuntimeArtifactsPath(
                PKG_NAME_1, "/somewhere/app/foo/base.apk", "arm")));
        verify(mArtd).deleteRuntimeArtifacts(deepEq(AidlUtils.buildRuntimeArtifactsPath(
                PKG_NAME_1, "/somewhere/app/foo/split_0.apk", "arm64")));
        verify(mArtd).deleteRuntimeArtifacts(deepEq(AidlUtils.buildRuntimeArtifactsPath(
                PKG_NAME_1, "/somewhere/app/foo/split_0.apk", "arm")));

        // Verify that there are no more calls than the ones above.
        verify(mArtd, times(6)).deleteArtifacts(any());
        verify(mArtd, times(4)).deleteRuntimeArtifacts(any());
    }

    @Test
    public void testDeleteDexoptArtifactsTranslatedIsas() throws Exception {
        final long DEXOPT_ARTIFACTS_FREED = 1l;
        final long RUNTIME_ARTIFACTS_FREED = 100l;

        lenient().when(SystemProperties.get("ro.dalvik.vm.isa.arm64")).thenReturn("x86_64");
        lenient().when(SystemProperties.get("ro.dalvik.vm.isa.arm")).thenReturn("x86");
        lenient().when(Constants.getPreferredAbi()).thenReturn("x86_64");
        lenient().when(Constants.getNative64BitAbi()).thenReturn("x86_64");
        lenient().when(Constants.getNative32BitAbi()).thenReturn("x86");
        when(mPkg1SecondaryDexInfo1.abiNames()).thenReturn(Set.of("x86_64"));
        when(mPkg1SecondaryDexInfoNotFound.abiNames()).thenReturn(Set.of("x86_64"));

        when(mArtd.deleteArtifacts(any())).thenReturn(DEXOPT_ARTIFACTS_FREED);
        when(mArtd.deleteRuntimeArtifacts(any())).thenReturn(RUNTIME_ARTIFACTS_FREED);

        DeleteResult result = mArtManagerLocal.deleteDexoptArtifacts(mSnapshot, PKG_NAME_1);
        assertThat(result.getFreedBytes())
                .isEqualTo(6 * DEXOPT_ARTIFACTS_FREED + 4 * RUNTIME_ARTIFACTS_FREED);

        verify(mArtd).deleteArtifacts(deepEq(AidlUtils.buildArtifactsPath(
                "/somewhere/app/foo/base.apk", "x86_64", mIsInDalvikCache)));
        verify(mArtd).deleteArtifacts(deepEq(AidlUtils.buildArtifactsPath(
                "/somewhere/app/foo/base.apk", "x86", mIsInDalvikCache)));
        verify(mArtd).deleteArtifacts(deepEq(AidlUtils.buildArtifactsPath(
                "/somewhere/app/foo/split_0.apk", "x86_64", mIsInDalvikCache)));
        verify(mArtd).deleteArtifacts(deepEq(AidlUtils.buildArtifactsPath(
                "/somewhere/app/foo/split_0.apk", "x86", mIsInDalvikCache)));

        verify(mArtd).deleteRuntimeArtifacts(deepEq(AidlUtils.buildRuntimeArtifactsPath(
                PKG_NAME_1, "/somewhere/app/foo/base.apk", "x86_64")));
        verify(mArtd).deleteRuntimeArtifacts(deepEq(AidlUtils.buildRuntimeArtifactsPath(
                PKG_NAME_1, "/somewhere/app/foo/base.apk", "x86")));
        verify(mArtd).deleteRuntimeArtifacts(deepEq(AidlUtils.buildRuntimeArtifactsPath(
                PKG_NAME_1, "/somewhere/app/foo/split_0.apk", "x86_64")));
        verify(mArtd).deleteRuntimeArtifacts(deepEq(AidlUtils.buildRuntimeArtifactsPath(
                PKG_NAME_1, "/somewhere/app/foo/split_0.apk", "x86")));

        // We assume that the ISA got from `DexUseManagerLocal` is already the translated one.
        verify(mArtd).deleteArtifacts(deepEq(AidlUtils.buildArtifactsPath(
                "/data/user/0/foo/1.apk", "x86_64", false /* isInDalvikCache */)));
        verify(mArtd).deleteArtifacts(deepEq(AidlUtils.buildArtifactsPath(
                "/data/user/0/foo/not_found.apk", "x86_64", false /* isInDalvikCache */)));

        // Verify that there are no more calls than the ones above.
        verify(mArtd, times(6)).deleteArtifacts(any());
        verify(mArtd, times(4)).deleteRuntimeArtifacts(any());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testdeleteDexoptArtifactsPackageNotFound() throws Exception {
        when(mSnapshot.getPackageState(anyString())).thenReturn(null);

        mArtManagerLocal.deleteDexoptArtifacts(mSnapshot, PKG_NAME_1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testdeleteDexoptArtifactsNoPackage() throws Exception {
        when(mPkgState1.getAndroidPackage()).thenReturn(null);

        mArtManagerLocal.deleteDexoptArtifacts(mSnapshot, PKG_NAME_1);
    }

    @Test
    public void testGetDexoptStatus() throws Exception {
        doReturn(createGetDexoptStatusResult("speed", "compilation-reason-0",
                         "location-debug-string-0", ArtifactsLocation.NEXT_TO_DEX))
                .when(mArtd)
                .getDexoptStatus("/somewhere/app/foo/base.apk", "arm64", "PCL[]");
        doReturn(createGetDexoptStatusResult("speed-profile", "compilation-reason-1",
                         "location-debug-string-1", ArtifactsLocation.NEXT_TO_DEX))
                .when(mArtd)
                .getDexoptStatus("/somewhere/app/foo/base.apk", "arm", "PCL[]");
        doReturn(createGetDexoptStatusResult("verify", "compilation-reason-2",
                         "location-debug-string-2", ArtifactsLocation.NEXT_TO_DEX))
                .when(mArtd)
                .getDexoptStatus("/somewhere/app/foo/split_0.apk", "arm64", "PCL[base.apk]");
        doReturn(createGetDexoptStatusResult("extract", "compilation-reason-3",
                         "location-debug-string-3", ArtifactsLocation.NEXT_TO_DEX))
                .when(mArtd)
                .getDexoptStatus("/somewhere/app/foo/split_0.apk", "arm", "PCL[base.apk]");
        doReturn(createGetDexoptStatusResult(
                         "run-from-apk", "unknown", "unknown", ArtifactsLocation.NEXT_TO_DEX))
                .when(mArtd)
                .getDexoptStatus("/data/user/0/foo/1.apk", "arm64", "CLC");
        doReturn(createGetDexoptStatusResult(
                         "unknown", "unknown", "error", ArtifactsLocation.NONE_OR_ERROR))
                .when(mArtd)
                .getDexoptStatus("/data/user/0/foo/not_found.apk", "arm64", "CLC");

        DexoptStatus result = mArtManagerLocal.getDexoptStatus(mSnapshot, PKG_NAME_1);

        assertThat(result.getDexContainerFileDexoptStatuses())
                .comparingElementsUsing(TestingUtils.<DexContainerFileDexoptStatus>deepEquality())
                .containsExactly(
                        DexContainerFileDexoptStatus.create("/somewhere/app/foo/base.apk",
                                true /* isPrimaryDex */, true /* isPrimaryAbi */, "arm64-v8a",
                                "speed", "compilation-reason-0", "location-debug-string-0"),
                        DexContainerFileDexoptStatus.create("/somewhere/app/foo/base.apk",
                                true /* isPrimaryDex */, false /* isPrimaryAbi */, "armeabi-v7a",
                                "speed-profile", "compilation-reason-1", "location-debug-string-1"),
                        DexContainerFileDexoptStatus.create("/somewhere/app/foo/split_0.apk",
                                true /* isPrimaryDex */, true /* isPrimaryAbi */, "arm64-v8a",
                                "verify", "compilation-reason-2", "location-debug-string-2"),
                        DexContainerFileDexoptStatus.create("/somewhere/app/foo/split_0.apk",
                                true /* isPrimaryDex */, false /* isPrimaryAbi */, "armeabi-v7a",
                                "extract", "compilation-reason-3", "location-debug-string-3"),
                        DexContainerFileDexoptStatus.create("/data/user/0/foo/1.apk",
                                false /* isPrimaryDex */, true /* isPrimaryAbi */, "arm64-v8a",
                                "run-from-apk", "unknown", "unknown"),
                        DexContainerFileDexoptStatus.create("/data/user/0/foo/not_found.apk",
                                false /* isPrimaryDex */, true /* isPrimaryAbi */, "arm64-v8a",
                                "unknown", "unknown", "error"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetDexoptStatusPackageNotFound() throws Exception {
        when(mSnapshot.getPackageState(anyString())).thenReturn(null);

        mArtManagerLocal.getDexoptStatus(mSnapshot, PKG_NAME_1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetDexoptStatusNoPackage() throws Exception {
        when(mPkgState1.getAndroidPackage()).thenReturn(null);

        mArtManagerLocal.getDexoptStatus(mSnapshot, PKG_NAME_1);
    }

    @Test
    public void testGetDexoptStatusNonFatalError() throws Exception {
        when(mArtd.getDexoptStatus(any(), any(), any()))
                .thenThrow(new ServiceSpecificException(1 /* errorCode */, "some error message"));

        DexoptStatus result = mArtManagerLocal.getDexoptStatus(mSnapshot, PKG_NAME_1);

        List<DexContainerFileDexoptStatus> statuses = result.getDexContainerFileDexoptStatuses();
        assertThat(statuses.size()).isEqualTo(6);

        for (DexContainerFileDexoptStatus status : statuses) {
            assertThat(status.getCompilerFilter()).isEqualTo("error");
            assertThat(status.getCompilationReason()).isEqualTo("error");
            assertThat(status.getLocationDebugString()).isEqualTo("some error message");
        }
    }

    @Test
    public void testClearAppProfiles() throws Exception {
        mArtManagerLocal.clearAppProfiles(mSnapshot, PKG_NAME_1);

        verify(mArtd).deleteProfile(
                deepEq(AidlUtils.buildProfilePathForPrimaryRef(PKG_NAME_1, "primary")));
        verify(mArtd).deleteProfile(deepEq(
                AidlUtils.buildProfilePathForPrimaryCur(0 /* userId */, PKG_NAME_1, "primary")));
        verify(mArtd).deleteProfile(deepEq(
                AidlUtils.buildProfilePathForPrimaryCur(1 /* userId */, PKG_NAME_1, "primary")));

        verify(mArtd).deleteProfile(
                deepEq(AidlUtils.buildProfilePathForSecondaryRef("/data/user/0/foo/1.apk")));
        verify(mArtd).deleteProfile(
                deepEq(AidlUtils.buildProfilePathForSecondaryCur("/data/user/0/foo/1.apk")));

        verify(mArtd).deleteProfile(deepEq(
                AidlUtils.buildProfilePathForSecondaryRef("/data/user/0/foo/not_found.apk")));
        verify(mArtd).deleteProfile(deepEq(
                AidlUtils.buildProfilePathForSecondaryCur("/data/user/0/foo/not_found.apk")));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testClearAppProfilesPackageNotFound() throws Exception {
        when(mSnapshot.getPackageState(anyString())).thenReturn(null);

        mArtManagerLocal.clearAppProfiles(mSnapshot, PKG_NAME_1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testClearAppProfilesNoPackage() throws Exception {
        when(mPkgState1.getAndroidPackage()).thenReturn(null);

        mArtManagerLocal.clearAppProfiles(mSnapshot, PKG_NAME_1);
    }

    @Test
    public void testDexoptPackage() throws Exception {
        var params = new DexoptParams.Builder("install").build();
        var result = DexoptResult.create();
        var cancellationSignal = new CancellationSignal();

        when(mDexoptHelper.dexopt(any(), deepEq(List.of(PKG_NAME_1)), same(params),
                     same(cancellationSignal), any()))
                .thenReturn(result);

        assertThat(
                mArtManagerLocal.dexoptPackage(mSnapshot, PKG_NAME_1, params, cancellationSignal))
                .isSameInstanceAs(result);
    }

    @Test
    public void testResetDexoptStatus() throws Exception {
        var result = DexoptResult.create();
        var cancellationSignal = new CancellationSignal();

        when(mDexoptHelper.dexopt(
                     any(), deepEq(List.of(PKG_NAME_1)), any(), same(cancellationSignal), any()))
                .thenReturn(result);

        assertThat(mArtManagerLocal.resetDexoptStatus(mSnapshot, PKG_NAME_1, cancellationSignal))
                .isSameInstanceAs(result);

        verify(mArtd).deleteProfile(
                deepEq(AidlUtils.buildProfilePathForPrimaryRef(PKG_NAME_1, "primary")));
        verify(mArtd).deleteProfile(deepEq(
                AidlUtils.buildProfilePathForPrimaryCur(0 /* userId */, PKG_NAME_1, "primary")));
        verify(mArtd).deleteProfile(deepEq(
                AidlUtils.buildProfilePathForPrimaryCur(1 /* userId */, PKG_NAME_1, "primary")));

        verify(mArtd).deleteArtifacts(deepEq(AidlUtils.buildArtifactsPath(
                "/somewhere/app/foo/base.apk", "arm64", mIsInDalvikCache)));
        verify(mArtd).deleteArtifacts(deepEq(AidlUtils.buildArtifactsPath(
                "/somewhere/app/foo/base.apk", "arm", mIsInDalvikCache)));
        verify(mArtd).deleteArtifacts(deepEq(AidlUtils.buildArtifactsPath(
                "/somewhere/app/foo/split_0.apk", "arm64", mIsInDalvikCache)));
        verify(mArtd).deleteArtifacts(deepEq(AidlUtils.buildArtifactsPath(
                "/somewhere/app/foo/split_0.apk", "arm", mIsInDalvikCache)));

        verify(mArtd).deleteRuntimeArtifacts(deepEq(AidlUtils.buildRuntimeArtifactsPath(
                PKG_NAME_1, "/somewhere/app/foo/base.apk", "arm64")));
        verify(mArtd).deleteRuntimeArtifacts(deepEq(AidlUtils.buildRuntimeArtifactsPath(
                PKG_NAME_1, "/somewhere/app/foo/base.apk", "arm")));
        verify(mArtd).deleteRuntimeArtifacts(deepEq(AidlUtils.buildRuntimeArtifactsPath(
                PKG_NAME_1, "/somewhere/app/foo/split_0.apk", "arm64")));
        verify(mArtd).deleteRuntimeArtifacts(deepEq(AidlUtils.buildRuntimeArtifactsPath(
                PKG_NAME_1, "/somewhere/app/foo/split_0.apk", "arm")));

        verify(mArtd).deleteProfile(
                deepEq(AidlUtils.buildProfilePathForSecondaryRef("/data/user/0/foo/1.apk")));
        verify(mArtd).deleteProfile(
                deepEq(AidlUtils.buildProfilePathForSecondaryCur("/data/user/0/foo/1.apk")));

        verify(mArtd).deleteArtifacts(deepEq(AidlUtils.buildArtifactsPath(
                "/data/user/0/foo/1.apk", "arm64", false /* isInDalvikCache */)));
    }

    @Test
    public void testDexoptPackages() throws Exception {
        var dexoptResult = DexoptResult.create();
        var cancellationSignal = new CancellationSignal();
        when(mDexUseManager.getPackageLastUsedAtMs(PKG_NAME_2)).thenReturn(CURRENT_TIME_MS);
        simulateStorageLow();

        // It should use the default package list and params. The list is sorted by last active
        // time in descending order.
        doReturn(dexoptResult)
                .when(mDexoptHelper)
                .dexopt(any(), deepEq(List.of(PKG_NAME_2, PKG_NAME_1)),
                        argThat(params -> params.getReason().equals("bg-dexopt")),
                        same(cancellationSignal), any(), any(), any());

        assertThat(mArtManagerLocal.dexoptPackages(mSnapshot, "bg-dexopt", cancellationSignal,
                           null /* processCallbackExecutor */, null /* processCallback */))
                .isEqualTo(Map.of(ArtFlags.PASS_MAIN, dexoptResult));

        // Nothing to downgrade.
        verify(mDexoptHelper, never())
                .dexopt(any(), any(), argThat(params -> params.getReason().equals("inactive")),
                        any(), any(), any(), any());
    }

    @Test
    public void testDexoptPackagesRecentlyInstalled() throws Exception {
        // The package is recently installed but hasn't been used.
        PackageUserState userState = mPkgState1.getStateForUser(UserHandle.of(1));
        when(userState.getFirstInstallTimeMillis()).thenReturn(RECENT_TIME_MS);
        when(mDexUseManager.getPackageLastUsedAtMs(PKG_NAME_1)).thenReturn(0l);
        simulateStorageLow();

        var result = DexoptResult.create();
        var cancellationSignal = new CancellationSignal();

        // PKG_NAME_1 should be dexopted.
        doReturn(result)
                .when(mDexoptHelper)
                .dexopt(any(), inAnyOrder(PKG_NAME_1, PKG_NAME_2),
                        argThat(params -> params.getReason().equals("bg-dexopt")), any(), any(),
                        any(), any());

        mArtManagerLocal.dexoptPackages(mSnapshot, "bg-dexopt", cancellationSignal,
                null /* processCallbackExecutor */, null /* processCallback */);

        // PKG_NAME_1 should not be downgraded.
        verify(mDexoptHelper, never())
                .dexopt(any(), any(), argThat(params -> params.getReason().equals("inactive")),
                        any(), any(), any(), any());
    }

    @Test
    public void testDexoptPackagesInactive() throws Exception {
        // PKG_NAME_1 is neither recently installed nor recently used.
        PackageUserState userState = mPkgState1.getStateForUser(UserHandle.of(1));
        when(userState.getFirstInstallTimeMillis()).thenReturn(NOT_RECENT_TIME_MS);
        when(mDexUseManager.getPackageLastUsedAtMs(PKG_NAME_1)).thenReturn(NOT_RECENT_TIME_MS);
        simulateStorageLow();

        var mainResult = DexoptResult.create();
        var downgradeResult = DexoptResult.create();
        var cancellationSignal = new CancellationSignal();

        // PKG_NAME_1 should not be dexopted.
        doReturn(mainResult)
                .when(mDexoptHelper)
                .dexopt(any(), deepEq(List.of(PKG_NAME_2)),
                        argThat(params -> params.getReason().equals("bg-dexopt")), any(), any(),
                        any(), any());

        // PKG_NAME_1 should be downgraded.
        doReturn(downgradeResult)
                .when(mDexoptHelper)
                .dexopt(any(), deepEq(List.of(PKG_NAME_1)),
                        argThat(params -> params.getReason().equals("inactive")), any(), any(),
                        any(), any());

        assertThat(mArtManagerLocal.dexoptPackages(mSnapshot, "bg-dexopt", cancellationSignal,
                           null /* processCallbackExecutor */, null /* processCallback */))
                .isEqualTo(Map.of(
                        ArtFlags.PASS_DOWNGRADE, downgradeResult, ArtFlags.PASS_MAIN, mainResult));
    }

    @Test
    public void testDexoptPackagesInactiveStorageNotLow() throws Exception {
        // PKG_NAME_1 is neither recently installed nor recently used.
        PackageUserState userState = mPkgState1.getStateForUser(UserHandle.of(1));
        when(userState.getFirstInstallTimeMillis()).thenReturn(NOT_RECENT_TIME_MS);
        when(mDexUseManager.getPackageLastUsedAtMs(PKG_NAME_1)).thenReturn(NOT_RECENT_TIME_MS);

        var result = DexoptResult.create();
        var cancellationSignal = new CancellationSignal();

        // PKG_NAME_1 should not be dexopted.
        doReturn(result)
                .when(mDexoptHelper)
                .dexopt(any(), deepEq(List.of(PKG_NAME_2)),
                        argThat(params -> params.getReason().equals("bg-dexopt")), any(), any(),
                        any(), any());

        mArtManagerLocal.dexoptPackages(mSnapshot, "bg-dexopt", cancellationSignal,
                null /* processCallbackExecutor */, null /* processCallback */);

        // PKG_NAME_1 should not be downgraded because the storage is not low.
        verify(mDexoptHelper, never())
                .dexopt(any(), any(), argThat(params -> params.getReason().equals("inactive")),
                        any(), any(), any(), any());
    }

    @Test
    public void testDexoptPackagesBootAfterMainlineUpdate() throws Exception {
        var result = DexoptResult.create();
        var cancellationSignal = new CancellationSignal();

        lenient().when(mInjector.isSystemUiPackage(PKG_NAME_1)).thenReturn(true);
        lenient().when(mInjector.isLauncherPackage(PKG_NAME_2)).thenReturn(true);

        // It should dexopt the system UI and the launcher.
        when(mDexoptHelper.dexopt(
                     any(), inAnyOrder(PKG_NAME_1, PKG_NAME_2), any(), any(), any(), any(), any()))
                .thenReturn(result);

        mArtManagerLocal.dexoptPackages(mSnapshot, "boot-after-mainline-update", cancellationSignal,
                null /* processCallbackExecutor */, null /* processCallback */);
    }

    @Test
    public void testDexoptPackagesBootAfterMainlineUpdatePackagesNotFound() throws Exception {
        var result = DexoptResult.create();
        var cancellationSignal = new CancellationSignal();
        // PKG_NAME_1 is neither recently installed nor recently used.
        PackageUserState userState = mPkgState1.getStateForUser(UserHandle.of(1));
        lenient().when(userState.getFirstInstallTimeMillis()).thenReturn(NOT_RECENT_TIME_MS);
        lenient()
                .when(mDexUseManager.getPackageLastUsedAtMs(PKG_NAME_1))
                .thenReturn(NOT_RECENT_TIME_MS);
        simulateStorageLow();

        // It should dexopt the system UI and the launcher, but they are not found.
        when(mDexoptHelper.dexopt(any(), deepEq(List.of()), any(), any(), any(), any(), any()))
                .thenReturn(result);

        mArtManagerLocal.dexoptPackages(mSnapshot, "boot-after-mainline-update", cancellationSignal,
                null /* processCallbackExecutor */, null /* processCallback */);

        // It should never downgrade apps, even if the storage is low.
        verify(mDexoptHelper, never())
                .dexopt(any(), any(), argThat(params -> params.getReason().equals("inactive")),
                        any(), any(), any(), any());
    }

    @Test
    public void testDexoptPackagesOverride() throws Exception {
        // PKG_NAME_1 is neither recently installed nor recently used.
        PackageUserState userState = mPkgState1.getStateForUser(UserHandle.of(1));
        when(userState.getFirstInstallTimeMillis()).thenReturn(NOT_RECENT_TIME_MS);
        when(mDexUseManager.getPackageLastUsedAtMs(PKG_NAME_1)).thenReturn(NOT_RECENT_TIME_MS);
        simulateStorageLow();

        var params = new DexoptParams.Builder("bg-dexopt").build();
        var result = DexoptResult.create();
        var cancellationSignal = new CancellationSignal();

        mArtManagerLocal.setBatchDexoptStartCallback(ForkJoinPool.commonPool(),
                (snapshot, reason, defaultPackages, builder, passedSignal) -> {
                    assertThat(reason).isEqualTo("bg-dexopt");
                    assertThat(defaultPackages).containsExactly(PKG_NAME_2);
                    assertThat(passedSignal).isSameInstanceAs(cancellationSignal);
                    builder.setPackages(List.of(PKG_NAME_1)).setDexoptParams(params);
                });

        // It should use the overridden package list and params.
        doReturn(result)
                .when(mDexoptHelper)
                .dexopt(any(), deepEq(List.of(PKG_NAME_1)), same(params), any(), any(), any(),
                        any());

        mArtManagerLocal.dexoptPackages(mSnapshot, "bg-dexopt", cancellationSignal,
                null /* processCallbackExecutor */, null /* processCallback */);

        // It should not downgrade PKG_NAME_1 because it's in the overridden package list. It should
        // not downgrade PKG_NAME_2 either because it's not an inactive package.
        verify(mDexoptHelper, never())
                .dexopt(any(), any(), argThat(params2 -> params2.getReason().equals("inactive")),
                        any(), any(), any(), any());
    }

    @Test
    public void testDexoptPackagesOverrideCleared() throws Exception {
        var params = new DexoptParams.Builder("bg-dexopt").build();
        var result = DexoptResult.create();
        var cancellationSignal = new CancellationSignal();

        mArtManagerLocal.setBatchDexoptStartCallback(ForkJoinPool.commonPool(),
                (snapshot, reason, defaultPackages, builder, passedSignal) -> {
                    builder.setPackages(List.of(PKG_NAME_1)).setDexoptParams(params);
                });
        mArtManagerLocal.clearBatchDexoptStartCallback();

        // It should use the default package list and params.
        when(mDexoptHelper.dexopt(any(), inAnyOrder(PKG_NAME_1, PKG_NAME_2), not(same(params)),
                     same(cancellationSignal), any(), any(), any()))
                .thenReturn(result);

        assertThat(mArtManagerLocal.dexoptPackages(mSnapshot, "bg-dexopt", cancellationSignal,
                           null /* processCallbackExecutor */, null /* processCallback */))
                .isEqualTo(Map.of(ArtFlags.PASS_MAIN, result));
    }

    @Test
    public void testDexoptPackagesSupplementaryPass() throws Exception {
        // The supplementary pass should only try dexopting PKG_NAME_2.
        var mainResult = DexoptResult.create("speed-profile", "bg-dexopt",
                List.of(PackageDexoptResult.create(PKG_NAME_1,
                                List.of(DexContainerFileDexoptResult.create("dex-file-1",
                                                true /* isPrimaryAbi */, "arm64", "speed-profile",
                                                DexoptResult.DEXOPT_PERFORMED),
                                        DexContainerFileDexoptResult.create("dex-file-2",
                                                true /* isPrimaryAbi */, "arm64", "speed",
                                                DexoptResult.DEXOPT_SKIPPED)),
                                null /* packageLevelStatus */),
                        PackageDexoptResult.create(PKG_NAME_2,
                                List.of(DexContainerFileDexoptResult.create("dex-file-1",
                                                true /* isPrimaryAbi */, "arm64", "speed-profile",
                                                DexoptResult.DEXOPT_PERFORMED),
                                        DexContainerFileDexoptResult.create("dex-file-2",
                                                true /* isPrimaryAbi */, "arm64", "speed-profile",
                                                DexoptResult.DEXOPT_SKIPPED)),
                                null /* packageLevelStatus */)));
        var supplementaryResult = DexoptResult.create();
        var cancellationSignal = new CancellationSignal();

        // The main pass.
        doReturn(mainResult)
                .when(mDexoptHelper)
                .dexopt(any(), inAnyOrder(PKG_NAME_1, PKG_NAME_2),
                        argThat(params
                                -> params.getReason().equals("bg-dexopt")
                                        && (params.getFlags() & ArtFlags.FLAG_FORCE_MERGE_PROFILE)
                                                == 0),
                        any(), any(), any(), any());

        // The supplementary pass.
        doReturn(supplementaryResult)
                .when(mDexoptHelper)
                .dexopt(any(), inAnyOrder(PKG_NAME_2),
                        argThat(params
                                -> params.getReason().equals("bg-dexopt")
                                        && (params.getFlags() & ArtFlags.FLAG_FORCE_MERGE_PROFILE)
                                                != 0),
                        any(), any(), any(), any());

        assertThat(mArtManagerLocal.dexoptPackages(mSnapshot, "bg-dexopt", cancellationSignal,
                           null /* processCallbackExecutor */, null /* processCallback */))
                .isEqualTo(Map.of(ArtFlags.PASS_MAIN, mainResult, ArtFlags.PASS_SUPPLEMENTARY,
                        supplementaryResult));
    }

    @Test(expected = IllegalStateException.class)
    public void testDexoptPackagesOverrideReasonChanged() throws Exception {
        var params = new DexoptParams.Builder("first-boot").build();
        var cancellationSignal = new CancellationSignal();

        mArtManagerLocal.setBatchDexoptStartCallback(ForkJoinPool.commonPool(),
                (snapshot, reason, defaultPackages, builder, passedSignal) -> {
                    builder.setDexoptParams(params);
                });

        mArtManagerLocal.dexoptPackages(mSnapshot, "bg-dexopt", cancellationSignal,
                null /* processCallbackExecutor */, null /* processCallback */);
    }

    @Test
    public void testSnapshotAppProfile() throws Exception {
        var options = new MergeProfileOptions();
        options.forceMerge = true;
        options.forBootImage = false;

        File tempFile = File.createTempFile("primary", ".prof");
        tempFile.deleteOnExit();

        ProfilePath refProfile = AidlUtils.buildProfilePathForPrimaryRef(PKG_NAME_1, "primary");
        String dexPath = "/somewhere/app/foo/base.apk";

        when(mArtd.isProfileUsable(deepEq(refProfile), eq(dexPath))).thenReturn(true);

        when(mArtd.mergeProfiles(deepEq(List.of(refProfile,
                                         AidlUtils.buildProfilePathForPrimaryCur(
                                                 0 /* userId */, PKG_NAME_1, "primary"),
                                         AidlUtils.buildProfilePathForPrimaryCur(
                                                 1 /* userId */, PKG_NAME_1, "primary"))),
                     isNull(),
                     deepEq(AidlUtils.buildOutputProfileForPrimary(PKG_NAME_1, "primary",
                             Process.SYSTEM_UID, Process.SYSTEM_UID, false /* isPublic */)),
                     deepEq(List.of(dexPath)), deepEq(options)))
                .thenAnswer(invocation -> {
                    try (var writer = new FileWriter(tempFile)) {
                        writer.write("snapshot");
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    var output = invocation.<OutputProfile>getArgument(2);
                    output.profilePath.tmpPath = tempFile.getPath();
                    return true;
                });

        ParcelFileDescriptor fd =
                mArtManagerLocal.snapshotAppProfile(mSnapshot, PKG_NAME_1, null /* splitName */);

        verify(mArtd).deleteProfile(
                argThat(profile -> profile.getTmpProfilePath().tmpPath.equals(tempFile.getPath())));

        assertThat(fd.getStatSize()).isGreaterThan(0);
        try (InputStream inputStream = new AutoCloseInputStream(fd)) {
            String contents = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(contents).isEqualTo("snapshot");
        }
    }

    @Test
    public void testSnapshotAppProfileFromDm() throws Exception {
        String tempPathForRef = "/temp/path/for/ref";
        File tempFileForSnapshot = File.createTempFile("primary", ".prof");
        tempFileForSnapshot.deleteOnExit();

        ProfilePath refProfile = AidlUtils.buildProfilePathForPrimaryRef(PKG_NAME_1, "primary");
        String dexPath = "/somewhere/app/foo/base.apk";

        // Simulate that the reference profile doesn't exist.
        when(mArtd.isProfileUsable(deepEq(refProfile), eq(dexPath))).thenReturn(false);

        // The DM file is usable.
        when(mArtd.copyAndRewriteProfile(
                     deepEq(AidlUtils.buildProfilePathForDm(dexPath)), any(), eq(dexPath)))
                .thenAnswer(invocation -> {
                    var output = invocation.<OutputProfile>getArgument(1);
                    output.profilePath.tmpPath = tempPathForRef;
                    return TestingUtils.createCopyAndRewriteProfileSuccess();
                });

        // Verify that the reference file initialized from the DM file is used.
        when(mArtd.mergeProfiles(
                     argThat(profiles
                             -> profiles.stream().anyMatch(profile
                                     -> profile.getTag() == ProfilePath.tmpProfilePath
                                             && profile.getTmpProfilePath().tmpPath.equals(
                                                     tempPathForRef))),
                     isNull(), any(), deepEq(List.of(dexPath)), any()))
                .thenAnswer(invocation -> {
                    var output = invocation.<OutputProfile>getArgument(2);
                    output.profilePath.tmpPath = tempFileForSnapshot.getPath();
                    return true;
                });

        ParcelFileDescriptor fd =
                mArtManagerLocal.snapshotAppProfile(mSnapshot, PKG_NAME_1, null /* splitName */);

        verify(mArtd).deleteProfile(argThat(profile
                -> profile.getTmpProfilePath().tmpPath.equals(tempFileForSnapshot.getPath())));
        verify(mArtd).deleteProfile(
                argThat(profile -> profile.getTmpProfilePath().tmpPath.equals(tempPathForRef)));
    }

    @Test
    public void testSnapshotAppProfileSplit() throws Exception {
        ProfilePath refProfile =
                AidlUtils.buildProfilePathForPrimaryRef(PKG_NAME_1, "split_0.split");
        String dexPath = "/somewhere/app/foo/split_0.apk";

        when(mArtd.isProfileUsable(deepEq(refProfile), eq(dexPath))).thenReturn(true);

        when(mArtd.mergeProfiles(deepEq(List.of(refProfile,
                                         AidlUtils.buildProfilePathForPrimaryCur(
                                                 0 /* userId */, PKG_NAME_1, "split_0.split"),
                                         AidlUtils.buildProfilePathForPrimaryCur(
                                                 1 /* userId */, PKG_NAME_1, "split_0.split"))),
                     isNull(),
                     deepEq(AidlUtils.buildOutputProfileForPrimary(PKG_NAME_1, "split_0.split",
                             Process.SYSTEM_UID, Process.SYSTEM_UID, false /* isPublic */)),
                     deepEq(List.of(dexPath)), any()))
                .thenReturn(false);

        mArtManagerLocal.snapshotAppProfile(mSnapshot, PKG_NAME_1, "split_0");
    }

    @Test
    public void testSnapshotAppProfileEmpty() throws Exception {
        when(mArtd.mergeProfiles(any(), any(), any(), any(), any())).thenReturn(false);

        ParcelFileDescriptor fd =
                mArtManagerLocal.snapshotAppProfile(mSnapshot, PKG_NAME_1, null /* splitName */);

        verify(mArtd, never()).deleteProfile(any());

        assertThat(fd.getStatSize()).isEqualTo(0);
        try (InputStream inputStream = new AutoCloseInputStream(fd)) {
            assertThat(inputStream.readAllBytes()).isEmpty();
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSnapshotAppProfilePackageNotFound() throws Exception {
        when(mSnapshot.getPackageState(anyString())).thenReturn(null);

        mArtManagerLocal.snapshotAppProfile(mSnapshot, PKG_NAME_1, null /* splitName */);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSnapshotAppProfileNoPackage() throws Exception {
        when(mPkgState1.getAndroidPackage()).thenReturn(null);

        mArtManagerLocal.snapshotAppProfile(mSnapshot, PKG_NAME_1, null /* splitName */);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSnapshotAppProfileSplitNotFound() throws Exception {
        mArtManagerLocal.snapshotAppProfile(mSnapshot, PKG_NAME_1, "non-existent-split");
    }

    @Test
    public void testDumpAppProfile() throws Exception {
        var options = new MergeProfileOptions();
        options.dumpOnly = true;

        when(mArtd.mergeProfiles(any(), isNull(), any(), any(), deepEq(options)))
                .thenReturn(false); // A non-empty merge is tested in `testSnapshotAppProfile`.

        ParcelFileDescriptor fd = mArtManagerLocal.dumpAppProfile(
                mSnapshot, PKG_NAME_1, null /* splitName */, false /* dumpClassesAndMethods */);
    }

    @Test
    public void testDumpAppProfileDumpClassesAndMethods() throws Exception {
        var options = new MergeProfileOptions();
        options.dumpClassesAndMethods = true;

        when(mArtd.mergeProfiles(any(), isNull(), any(), any(), deepEq(options)))
                .thenReturn(false); // A non-empty merge is tested in `testSnapshotAppProfile`.

        ParcelFileDescriptor fd = mArtManagerLocal.dumpAppProfile(
                mSnapshot, PKG_NAME_1, null /* splitName */, true /* dumpClassesAndMethods */);
    }

    @Test
    public void testSnapshotBootImageProfile() throws Exception {
        // `lenient()` is required to allow mocking the same method multiple times.
        lenient().when(Constants.getenv("BOOTCLASSPATH")).thenReturn("bcp0:bcp1");
        lenient().when(Constants.getenv("SYSTEMSERVERCLASSPATH")).thenReturn("sscp0:sscp1");
        lenient().when(Constants.getenv("STANDALONE_SYSTEMSERVER_JARS")).thenReturn("sssj0:sssj1");

        var options = new MergeProfileOptions();
        options.forceMerge = true;
        options.forBootImage = true;

        when(mArtd.mergeProfiles(
                     inAnyOrderDeepEquals(
                             AidlUtils.buildProfilePathForPrimaryRef("android", "primary"),
                             AidlUtils.buildProfilePathForPrimaryCur(
                                     0 /* userId */, "android", "primary"),
                             AidlUtils.buildProfilePathForPrimaryCur(
                                     1 /* userId */, "android", "primary"),
                             AidlUtils.buildProfilePathForPrimaryRef(PKG_NAME_1, "primary"),
                             AidlUtils.buildProfilePathForPrimaryCur(
                                     0 /* userId */, PKG_NAME_1, "primary"),
                             AidlUtils.buildProfilePathForPrimaryCur(
                                     1 /* userId */, PKG_NAME_1, "primary"),
                             AidlUtils.buildProfilePathForPrimaryRef(PKG_NAME_1, "split_0.split"),
                             AidlUtils.buildProfilePathForPrimaryCur(
                                     0 /* userId */, PKG_NAME_1, "split_0.split"),
                             AidlUtils.buildProfilePathForPrimaryCur(
                                     1 /* userId */, PKG_NAME_1, "split_0.split"),
                             AidlUtils.buildProfilePathForPrimaryRef(PKG_NAME_2, "primary"),
                             AidlUtils.buildProfilePathForPrimaryCur(
                                     0 /* userId */, PKG_NAME_2, "primary"),
                             AidlUtils.buildProfilePathForPrimaryCur(
                                     1 /* userId */, PKG_NAME_2, "primary"),
                             AidlUtils.buildProfilePathForPrimaryRef(
                                     PKG_NAME_HIBERNATING, "primary"),
                             AidlUtils.buildProfilePathForPrimaryCur(
                                     0 /* userId */, PKG_NAME_HIBERNATING, "primary"),
                             AidlUtils.buildProfilePathForPrimaryCur(
                                     1 /* userId */, PKG_NAME_HIBERNATING, "primary")),
                     isNull(),
                     deepEq(AidlUtils.buildOutputProfileForPrimary("android", "primary",
                             Process.SYSTEM_UID, Process.SYSTEM_UID, false /* isPublic */)),
                     deepEq(List.of("bcp0", "bcp1", "sscp0", "sscp1", "sssj0", "sssj1")),
                     deepEq(options)))
                .thenReturn(false); // A non-empty merge is tested in `testSnapshotAppProfile`.

        mArtManagerLocal.snapshotBootImageProfile(mSnapshot);
    }

    @Test
    public void testOnBoot() throws Exception {
        var progressCallbackExecutor = mock(Executor.class);
        var progressCallback = mock(Consumer.class);

        when(mDexoptHelper.dexopt(any(), any(),
                     argThat(params -> params.getReason().equals(ReasonMapping.REASON_FIRST_BOOT)),
                     any(), any(), same(progressCallbackExecutor), same(progressCallback)))
                .thenReturn(DexoptResult.create());

        mArtManagerLocal.onBoot(
                ReasonMapping.REASON_FIRST_BOOT, progressCallbackExecutor, progressCallback);
    }

    @Test
    public void testOnBootNoProgressCallback() throws Exception {
        when(mDexoptHelper.dexopt(any(), any(),
                     argThat(params -> params.getReason().equals(ReasonMapping.REASON_FIRST_BOOT)),
                     any(), any(), isNull(), isNull()))
                .thenReturn(DexoptResult.create());

        mArtManagerLocal.onBoot(ReasonMapping.REASON_FIRST_BOOT,
                null /* progressCallbackExecutor */, null /* progressCallback */);
    }

    @Test
    public void testCleanup() throws Exception {
        // It should keep all artifacts, but not runtime images.
        doReturn(createGetDexoptStatusResult(
                         "speed-profile", "bg-dexopt", "location", ArtifactsLocation.NEXT_TO_DEX))
                .when(mArtd)
                .getDexoptStatus(eq("/somewhere/app/foo/base.apk"), eq("arm64"), any());
        doReturn(createGetDexoptStatusResult(
                         "verify", "cmdline", "location", ArtifactsLocation.NEXT_TO_DEX))
                .when(mArtd)
                .getDexoptStatus(eq("/data/user/0/foo/1.apk"), eq("arm64"), any());

        // It should keep all artifacts and runtime images.
        doReturn(createGetDexoptStatusResult(
                         "verify", "bg-dexopt", "location", ArtifactsLocation.DALVIK_CACHE))
                .when(mArtd)
                .getDexoptStatus(eq("/somewhere/app/foo/split_0.apk"), eq("arm64"), any());

        // It should only keep VDEX files and runtime images.
        doReturn(createGetDexoptStatusResult(
                         "verify", "vdex", "location", ArtifactsLocation.NEXT_TO_DEX))
                .when(mArtd)
                .getDexoptStatus(eq("/somewhere/app/foo/split_0.apk"), eq("arm"), any());

        // It should not keep any artifacts or runtime images.
        doReturn(createGetDexoptStatusResult(
                         "run-from-apk", "unknown", "unknown", ArtifactsLocation.NONE_OR_ERROR))
                .when(mArtd)
                .getDexoptStatus(eq("/somewhere/app/foo/base.apk"), eq("arm"), any());

        when(mSnapshot.getPackageStates()).thenReturn(Map.of(PKG_NAME_1, mPkgState1));
        mArtManagerLocal.cleanup(mSnapshot);

        verify(mArtd).cleanup(
                inAnyOrderDeepEquals(AidlUtils.buildProfilePathForPrimaryRef(PKG_NAME_1, "primary"),
                        AidlUtils.buildProfilePathForPrimaryCur(
                                0 /* userId */, PKG_NAME_1, "primary"),
                        AidlUtils.buildProfilePathForPrimaryCur(
                                1 /* userId */, PKG_NAME_1, "primary"),
                        AidlUtils.buildProfilePathForPrimaryRef(PKG_NAME_1, "split_0.split"),
                        AidlUtils.buildProfilePathForPrimaryCur(
                                0 /* userId */, PKG_NAME_1, "split_0.split"),
                        AidlUtils.buildProfilePathForPrimaryCur(
                                1 /* userId */, PKG_NAME_1, "split_0.split"),
                        AidlUtils.buildProfilePathForSecondaryRef("/data/user/0/foo/1.apk"),
                        AidlUtils.buildProfilePathForSecondaryCur("/data/user/0/foo/1.apk")),
                inAnyOrderDeepEquals(AidlUtils.buildArtifactsPath("/somewhere/app/foo/base.apk",
                                             "arm64", false /* isInDalvikCache */),
                        AidlUtils.buildArtifactsPath(
                                "/data/user/0/foo/1.apk", "arm64", false /* isInDalvikCache */),
                        AidlUtils.buildArtifactsPath("/somewhere/app/foo/split_0.apk", "arm64",
                                true /* isInDalvikCache */)),
                inAnyOrderDeepEquals(VdexPath.artifactsPath(AidlUtils.buildArtifactsPath(
                        "/somewhere/app/foo/split_0.apk", "arm", false /* isInDalvikCache */))),
                inAnyOrderDeepEquals(AidlUtils.buildRuntimeArtifactsPath(
                                             PKG_NAME_1, "/somewhere/app/foo/split_0.apk", "arm64"),
                        AidlUtils.buildRuntimeArtifactsPath(
                                PKG_NAME_1, "/somewhere/app/foo/split_0.apk", "arm")));
    }

    @Test
    public void testGetArtManagedFileStatsSystem() throws Exception {
        testGetArtManagedFileStats(true /* isSystemOrRootOrShell */);
    }

    @Test
    public void testGetArtManagedFileStatsNonSystem() throws Exception {
        testGetArtManagedFileStats(false /* isSystemOrRootOrShell */);
    }

    private void testGetArtManagedFileStats(boolean isSystemOrRootOrShell) throws Exception {
        if (!isSystemOrRootOrShell) {
            lenient().when(mArtFileManagerInjector.isSystemOrRootOrShell()).thenReturn(false);
            lenient()
                    .when(mArtFileManagerInjector.getCallingUserHandle())
                    .thenReturn(UserHandle.of(0));
        }

        // The same setup as `testCleanup`, but add a secondary dex file for a different user. Its
        // artifacts and profiles should only be counted if the caller is system or root or shell.
        CheckedSecondaryDexInfo pkg1SecondaryDexInfo2 =
                createSecondaryDexInfo("/data/user/1/foo/1.apk", UserHandle.of(1));
        lenient()
                .doReturn(List.of(mPkg1SecondaryDexInfo1, pkg1SecondaryDexInfo2))
                .when(mDexUseManager)
                .getCheckedSecondaryDexInfo(
                        eq(PKG_NAME_1), eq(true) /* excludeObsoleteDexesAndLoaders */);

        // It should count all artifacts, but not runtime images.
        doReturn(createGetDexoptStatusResult(
                         "speed-profile", "bg-dexopt", "location", ArtifactsLocation.NEXT_TO_DEX))
                .when(mArtd)
                .getDexoptStatus(eq("/somewhere/app/foo/base.apk"), eq("arm64"), any());
        doReturn(createGetDexoptStatusResult(
                         "verify", "cmdline", "location", ArtifactsLocation.NEXT_TO_DEX))
                .when(mArtd)
                .getDexoptStatus(eq("/data/user/0/foo/1.apk"), eq("arm64"), any());

        // It should count all artifacts and runtime images.
        doReturn(createGetDexoptStatusResult(
                         "verify", "bg-dexopt", "location", ArtifactsLocation.DALVIK_CACHE))
                .when(mArtd)
                .getDexoptStatus(eq("/somewhere/app/foo/split_0.apk"), eq("arm64"), any());

        // It should only count VDEX files and runtime images.
        doReturn(createGetDexoptStatusResult(
                         "verify", "vdex", "location", ArtifactsLocation.NEXT_TO_DEX))
                .when(mArtd)
                .getDexoptStatus(eq("/somewhere/app/foo/split_0.apk"), eq("arm"), any());

        // It should not count any artifacts or runtime images.
        doReturn(createGetDexoptStatusResult(
                         "run-from-apk", "unknown", "unknown", ArtifactsLocation.NONE_OR_ERROR))
                .when(mArtd)
                .getDexoptStatus(eq("/somewhere/app/foo/base.apk"), eq("arm"), any());

        // These are counted as TYPE_DEXOPT_ARTIFACT.
        doReturn(1l << 0).when(mArtd).getArtifactsSize(deepEq(AidlUtils.buildArtifactsPath(
                "/somewhere/app/foo/base.apk", "arm64", false /* isInDalvikCache */)));
        doReturn(1l << 1).when(mArtd).getArtifactsSize(deepEq(AidlUtils.buildArtifactsPath(
                "/data/user/0/foo/1.apk", "arm64", false /* isInDalvikCache */)));
        doReturn(1l << 2).when(mArtd).getArtifactsSize(deepEq(AidlUtils.buildArtifactsPath(
                "/somewhere/app/foo/split_0.apk", "arm64", true /* isInDalvikCache */)));
        doReturn(1l << 3).when(mArtd).getVdexFileSize(
                deepEq(VdexPath.artifactsPath(AidlUtils.buildArtifactsPath(
                        "/somewhere/app/foo/split_0.apk", "arm", false /* isInDalvikCache */))));
        doReturn(1l << 4).when(mArtd).getRuntimeArtifactsSize(
                deepEq(AidlUtils.buildRuntimeArtifactsPath(
                        PKG_NAME_1, "/somewhere/app/foo/split_0.apk", "arm64")));
        doReturn(1l << 5).when(mArtd).getRuntimeArtifactsSize(
                deepEq(AidlUtils.buildRuntimeArtifactsPath(
                        PKG_NAME_1, "/somewhere/app/foo/split_0.apk", "arm")));
        long expectedDexoptArtifactSize =
                (1l << 0) + (1l << 1) + (1l << 2) + (1l << 3) + (1l << 4) + (1l << 5);
        int expectedGetArtifactsSizeCalls = 3;
        int expectedGetVdexFileSizeCalls = 1;
        int expectedGetRuntimeArtifactsSizeCalls = 2;

        // These are counted as TYPE_REF_PROFILE.
        doReturn(1l << 6).when(mArtd).getProfileSize(
                deepEq(AidlUtils.buildProfilePathForPrimaryRef(PKG_NAME_1, "primary")));
        doReturn(1l << 7).when(mArtd).getProfileSize(
                deepEq(AidlUtils.buildProfilePathForPrimaryRef(PKG_NAME_1, "split_0.split")));
        doReturn(1l << 8).when(mArtd).getProfileSize(
                deepEq(AidlUtils.buildProfilePathForSecondaryRef("/data/user/0/foo/1.apk")));
        long expectedRefProfileSize = (1l << 6) + (1l << 7) + (1l << 8);
        int expectedGetProfileSizeCalls = 3;

        // These are counted as TYPE_CUR_PROFILE.
        doReturn(1l << 9).when(mArtd).getProfileSize(deepEq(
                AidlUtils.buildProfilePathForPrimaryCur(0 /* userId */, PKG_NAME_1, "primary")));
        doReturn(1l << 10).when(mArtd).getProfileSize(
                deepEq(AidlUtils.buildProfilePathForPrimaryCur(
                        0 /* userId */, PKG_NAME_1, "split_0.split")));
        doReturn(1l << 11).when(mArtd).getProfileSize(
                deepEq(AidlUtils.buildProfilePathForSecondaryCur("/data/user/0/foo/1.apk")));
        long expectedCurProfileSize = (1l << 9) + (1l << 10) + (1l << 11);
        expectedGetProfileSizeCalls += 3;

        // These belong to a different user.
        if (isSystemOrRootOrShell) {
            doReturn(createGetDexoptStatusResult(
                             "verify", "cmdline", "location", ArtifactsLocation.NEXT_TO_DEX))
                    .when(mArtd)
                    .getDexoptStatus(eq("/data/user/1/foo/1.apk"), eq("arm64"), any());

            // These are counted as TYPE_DEXOPT_ARTIFACT.
            // Dexopt artifacts of secondary dex files.
            doReturn(1l << 12).when(mArtd).getArtifactsSize(deepEq(AidlUtils.buildArtifactsPath(
                    "/data/user/1/foo/1.apk", "arm64", false /* isInDalvikCache */)));
            expectedDexoptArtifactSize += (1l << 12);
            expectedGetArtifactsSizeCalls += 1;

            // These are counted as TYPE_REF_PROFILE.
            // Reference profiles of secondary dex files.
            doReturn(1l << 13).when(mArtd).getProfileSize(
                    deepEq(AidlUtils.buildProfilePathForSecondaryRef("/data/user/1/foo/1.apk")));
            expectedRefProfileSize += (1l << 13);
            expectedGetProfileSizeCalls += 1;

            // These are counted as TYPE_CUR_PROFILE.
            // Current profiles of primary dex files.
            doReturn(1l << 14).when(mArtd).getProfileSize(
                    deepEq(AidlUtils.buildProfilePathForPrimaryCur(
                            1 /* userId */, PKG_NAME_1, "primary")));
            doReturn(1l << 15).when(mArtd).getProfileSize(
                    deepEq(AidlUtils.buildProfilePathForPrimaryCur(
                            1 /* userId */, PKG_NAME_1, "split_0.split")));
            // Current profiles of secondary dex files.
            doReturn(1l << 16).when(mArtd).getProfileSize(
                    deepEq(AidlUtils.buildProfilePathForSecondaryCur("/data/user/1/foo/1.apk")));
            expectedCurProfileSize += (1l << 14) + (1l << 15) + (1l << 16);
            expectedGetProfileSizeCalls += 3;
        }

        ArtManagedFileStats stats = mArtManagerLocal.getArtManagedFileStats(mSnapshot, PKG_NAME_1);
        assertThat(stats.getTotalSizeBytesByType(ArtManagedFileStats.TYPE_DEXOPT_ARTIFACT))
                .isEqualTo(expectedDexoptArtifactSize);
        assertThat(stats.getTotalSizeBytesByType(ArtManagedFileStats.TYPE_REF_PROFILE))
                .isEqualTo(expectedRefProfileSize);
        assertThat(stats.getTotalSizeBytesByType(ArtManagedFileStats.TYPE_CUR_PROFILE))
                .isEqualTo(expectedCurProfileSize);

        verify(mArtd, times(expectedGetArtifactsSizeCalls)).getArtifactsSize(any());
        verify(mArtd, times(expectedGetVdexFileSizeCalls)).getVdexFileSize(any());
        verify(mArtd, times(expectedGetRuntimeArtifactsSizeCalls)).getRuntimeArtifactsSize(any());
        verify(mArtd, times(expectedGetProfileSizeCalls)).getProfileSize(any());
    }

    private AndroidPackage createPackage(boolean multiSplit) {
        AndroidPackage pkg = mock(AndroidPackage.class);

        var baseSplit = mock(AndroidPackageSplit.class);
        lenient().when(baseSplit.getPath()).thenReturn("/somewhere/app/foo/base.apk");
        lenient().when(baseSplit.isHasCode()).thenReturn(true);

        if (multiSplit) {
            // split_0 has code while split_1 doesn't.
            var split0 = mock(AndroidPackageSplit.class);
            lenient().when(split0.getName()).thenReturn("split_0");
            lenient().when(split0.getPath()).thenReturn("/somewhere/app/foo/split_0.apk");
            lenient().when(split0.isHasCode()).thenReturn(true);
            var split1 = mock(AndroidPackageSplit.class);
            lenient().when(split1.getName()).thenReturn("split_1");
            lenient().when(split1.getPath()).thenReturn("/somewhere/app/foo/split_1.apk");
            lenient().when(split1.isHasCode()).thenReturn(false);

            lenient().when(pkg.getSplits()).thenReturn(List.of(baseSplit, split0, split1));
        } else {
            lenient().when(pkg.getSplits()).thenReturn(List.of(baseSplit));
        }

        return pkg;
    }

    private PackageUserState createPackageUserState() {
        PackageUserState pkgUserState = mock(PackageUserState.class);
        lenient().when(pkgUserState.isInstalled()).thenReturn(true);
        // All packages are by default pre-installed.
        lenient().when(pkgUserState.getFirstInstallTimeMillis()).thenReturn(0l);
        return pkgUserState;
    }

    private PackageState createPackageState(
            String packageName, boolean isDexoptable, boolean multiSplit) {
        PackageState pkgState = mock(PackageState.class);

        lenient().when(pkgState.getPackageName()).thenReturn(packageName);
        lenient().when(pkgState.getPrimaryCpuAbi()).thenReturn("arm64-v8a");
        lenient().when(pkgState.getSecondaryCpuAbi()).thenReturn("armeabi-v7a");

        AndroidPackage pkg = createPackage(multiSplit);
        lenient().when(pkgState.getAndroidPackage()).thenReturn(pkg);

        PackageUserState pkgUserState0 = createPackageUserState();
        lenient().when(pkgState.getStateForUser(UserHandle.of(0))).thenReturn(pkgUserState0);
        PackageUserState pkgUserState1 = createPackageUserState();
        lenient().when(pkgState.getStateForUser(UserHandle.of(1))).thenReturn(pkgUserState1);

        lenient().when(PackageStateModulesUtils.isDexoptable(pkgState)).thenReturn(isDexoptable);

        return pkgState;
    }

    private List<PackageState> createPackageStates() {
        PackageState pkgState1 =
                createPackageState(PKG_NAME_1, true /* isDexoptable */, true /* multiSplit */);

        PackageState pkgState2 =
                createPackageState(PKG_NAME_2, true /* isDexoptable */, false /* multiSplit */);

        // This should not be dexopted because it's hibernating. However, it should be included
        // when snapshotting boot image profile.
        PackageState pkgHibernatingState = createPackageState(
                PKG_NAME_HIBERNATING, true /* isDexoptable */, false /* multiSplit */);
        lenient()
                .when(mAppHibernationManager.isHibernatingGlobally(PKG_NAME_HIBERNATING))
                .thenReturn(true);

        // This should not be dexopted because it's not dexoptable.
        PackageState nonDexoptablePkgState = createPackageState(
                "com.example.non-dexoptable", false /* isDexoptable */, false /* multiSplit */);

        return List.of(pkgState1, pkgState2, pkgHibernatingState, nonDexoptablePkgState);
    }

    private GetDexoptStatusResult createGetDexoptStatusResult(String compilerFilter,
            String compilationReason, String locationDebugString, @ArtifactsLocation int location) {
        var getDexoptStatusResult = new GetDexoptStatusResult();
        getDexoptStatusResult.compilerFilter = compilerFilter;
        getDexoptStatusResult.compilationReason = compilationReason;
        getDexoptStatusResult.locationDebugString = locationDebugString;
        getDexoptStatusResult.artifactsLocation = location;
        return getDexoptStatusResult;
    }

    private CheckedSecondaryDexInfo createSecondaryDexInfo(String dexPath, UserHandle userHandle)
            throws Exception {
        var dexInfo = mock(CheckedSecondaryDexInfo.class);
        lenient().when(dexInfo.dexPath()).thenReturn(dexPath);
        lenient().when(dexInfo.abiNames()).thenReturn(Set.of("arm64-v8a"));
        lenient().when(dexInfo.classLoaderContext()).thenReturn("CLC");
        lenient().when(dexInfo.userHandle()).thenReturn(userHandle);
        return dexInfo;
    }

    private void simulateStorageLow() throws Exception {
        lenient()
                .when(mStorageManager.getAllocatableBytes(any()))
                .thenReturn(ArtManagerLocal.DOWNGRADE_THRESHOLD_ABOVE_LOW_BYTES - 1);
    }

    private void simulateStorageNotLow() throws Exception {
        lenient()
                .when(mStorageManager.getAllocatableBytes(any()))
                .thenReturn(ArtManagerLocal.DOWNGRADE_THRESHOLD_ABOVE_LOW_BYTES);
    }
}
