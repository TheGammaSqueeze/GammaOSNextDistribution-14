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
import static com.android.server.art.OutputArtifacts.PermissionSettings;
import static com.android.server.art.model.DexoptResult.DexContainerFileDexoptResult;
import static com.android.server.art.testing.TestingUtils.deepEq;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.os.CancellationSignal;
import android.os.SystemProperties;
import android.os.UserHandle;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.server.art.model.ArtFlags;
import com.android.server.art.model.Config;
import com.android.server.art.model.DexoptParams;
import com.android.server.art.model.DexoptResult;
import com.android.server.art.testing.StaticMockitoRule;
import com.android.server.art.testing.TestingUtils;
import com.android.server.pm.PackageSetting;
import com.android.server.pm.pkg.AndroidPackage;
import com.android.server.pm.pkg.PackageState;
import com.android.server.pm.pkg.PackageStateUnserialized;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class SecondaryDexopterTest {
    private static final String PKG_NAME = "com.example.foo";
    private static final int APP_ID = 12345;
    private static final UserHandle USER_HANDLE = UserHandle.of(2);
    private static final int UID = USER_HANDLE.getUid(APP_ID);
    private static final String APP_DATA_DIR = "/data/user/2/" + PKG_NAME;
    private static final String DEX_1 = APP_DATA_DIR + "/1.apk";
    private static final String DEX_2 = APP_DATA_DIR + "/2.apk";
    private static final String DEX_3 = APP_DATA_DIR + "/3.apk";

    private final DexoptParams mDexoptParams =
            new DexoptParams.Builder("bg-dexopt")
                    .setCompilerFilter("speed-profile")
                    .setFlags(ArtFlags.FLAG_FOR_PRIMARY_DEX | ArtFlags.FLAG_FOR_SECONDARY_DEX)
                    .build();

    private final ProfilePath mDex1RefProfile = AidlUtils.buildProfilePathForSecondaryRef(DEX_1);
    private final ProfilePath mDex1CurProfile = AidlUtils.buildProfilePathForSecondaryCur(DEX_1);
    private final ProfilePath mDex2RefProfile = AidlUtils.buildProfilePathForSecondaryRef(DEX_2);
    private final ProfilePath mDex3RefProfile = AidlUtils.buildProfilePathForSecondaryRef(DEX_3);
    private final OutputProfile mDex1PrivateOutputProfile =
            AidlUtils.buildOutputProfileForSecondary(DEX_1, UID, UID, false /* isOtherReadable */);

    private final int mDefaultDexoptTrigger = DexoptTrigger.COMPILER_FILTER_IS_BETTER
            | DexoptTrigger.PRIMARY_BOOT_IMAGE_BECOMES_USABLE | DexoptTrigger.NEED_EXTRACTION;
    private final int mBetterOrSameDexoptTrigger = DexoptTrigger.COMPILER_FILTER_IS_BETTER
            | DexoptTrigger.COMPILER_FILTER_IS_SAME
            | DexoptTrigger.PRIMARY_BOOT_IMAGE_BECOMES_USABLE | DexoptTrigger.NEED_EXTRACTION;

    private final MergeProfileOptions mMergeProfileOptions = new MergeProfileOptions();

    @Rule
    public StaticMockitoRule mockitoRule =
            new StaticMockitoRule(SystemProperties.class, Constants.class);

    @Mock private SecondaryDexopter.Injector mInjector;
    @Mock private IArtd mArtd;
    @Mock private DexUseManagerLocal mDexUseManager;
    private PackageState mPkgState;
    private AndroidPackage mPkg;
    private CancellationSignal mCancellationSignal;
    protected Config mConfig;

    private SecondaryDexopter mSecondaryDexopter;

    @Before
    public void setUp() throws Exception {
        mPkgState = createPackageState();
        mPkg = mPkgState.getAndroidPackage();
        mCancellationSignal = new CancellationSignal();
        mConfig = new Config();

        lenient()
                .when(SystemProperties.getBoolean(eq("dalvik.vm.always_debuggable"), anyBoolean()))
                .thenReturn(false);
        lenient().when(SystemProperties.get("dalvik.vm.appimageformat")).thenReturn("lz4");
        lenient().when(SystemProperties.get("pm.dexopt.shared")).thenReturn("speed");

        // No ISA translation.
        lenient()
                .when(SystemProperties.get(argThat(arg -> arg.startsWith("ro.dalvik.vm.isa."))))
                .thenReturn("");

        lenient().when(Constants.getPreferredAbi()).thenReturn("arm64-v8a");
        lenient().when(Constants.getNative64BitAbi()).thenReturn("arm64-v8a");
        lenient().when(Constants.getNative32BitAbi()).thenReturn("armeabi-v7a");

        lenient().when(mInjector.getArtd()).thenReturn(mArtd);
        lenient().when(mInjector.isSystemUiPackage(any())).thenReturn(false);
        lenient().when(mInjector.isLauncherPackage(any())).thenReturn(false);
        lenient().when(mInjector.getDexUseManager()).thenReturn(mDexUseManager);
        lenient().when(mInjector.getConfig()).thenReturn(mConfig);

        List<CheckedSecondaryDexInfo> secondaryDexInfo = createSecondaryDexInfo();
        lenient()
                .when(mDexUseManager.getCheckedSecondaryDexInfo(
                        eq(PKG_NAME), eq(true) /* excludeObsoleteDexesAndLoaders */))
                .thenReturn(secondaryDexInfo);

        prepareProfiles();

        // Dexopt is always needed and successful.
        lenient()
                .when(mArtd.getDexoptNeeded(any(), any(), any(), any(), anyInt()))
                .thenReturn(dexoptIsNeeded());
        lenient()
                .when(mArtd.dexopt(any(), any(), any(), any(), any(), any(), any(), any(), anyInt(),
                        any(), any()))
                .thenReturn(createArtdDexoptResult());

        lenient()
                .when(mArtd.createCancellationSignal())
                .thenReturn(mock(IArtdCancellationSignal.class));

        mSecondaryDexopter = new SecondaryDexopter(
                mInjector, mPkgState, mPkg, mDexoptParams, mCancellationSignal);
    }

    @Test
    public void testDexopt() throws Exception {
        assertThat(mSecondaryDexopter.dexopt())
                .comparingElementsUsing(TestingUtils.<DexContainerFileDexoptResult>deepEquality())
                .containsExactly(
                        DexContainerFileDexoptResult.create(DEX_1, true /* isPrimaryAbi */,
                                "arm64-v8a", "speed-profile", DexoptResult.DEXOPT_PERFORMED),
                        DexContainerFileDexoptResult.create(DEX_2, true /* isPrimaryAbi */,
                                "arm64-v8a", "speed", DexoptResult.DEXOPT_PERFORMED),
                        DexContainerFileDexoptResult.create(DEX_2, false /* isPrimaryAbi */,
                                "armeabi-v7a", "speed", DexoptResult.DEXOPT_PERFORMED),
                        DexContainerFileDexoptResult.create(DEX_3, true /* isPrimaryAbi */,
                                "arm64-v8a", "verify", DexoptResult.DEXOPT_PERFORMED));

        // It should use profile for dex 1.

        verify(mArtd).mergeProfiles(deepEq(List.of(mDex1CurProfile)), deepEq(mDex1RefProfile),
                deepEq(mDex1PrivateOutputProfile), deepEq(List.of(DEX_1)),
                deepEq(mMergeProfileOptions));

        verify(mArtd).getDexoptNeeded(
                eq(DEX_1), eq("arm64"), any(), eq("speed-profile"), eq(mBetterOrSameDexoptTrigger));
        checkDexoptWithPrivateProfile(verify(mArtd), DEX_1, "arm64",
                ProfilePath.tmpProfilePath(mDex1PrivateOutputProfile.profilePath), "CLC_FOR_DEX_1");

        verify(mArtd).commitTmpProfile(deepEq(mDex1PrivateOutputProfile.profilePath));

        verify(mArtd).deleteProfile(deepEq(mDex1CurProfile));

        // It should use "speed" for dex 2 for both ISAs and make the artifacts public.

        verify(mArtd, never()).isProfileUsable(deepEq(mDex2RefProfile), any());
        verify(mArtd, never()).mergeProfiles(any(), deepEq(mDex2RefProfile), any(), any(), any());

        verify(mArtd).getDexoptNeeded(
                eq(DEX_2), eq("arm64"), any(), eq("speed"), eq(mDefaultDexoptTrigger));
        checkDexoptWithNoProfile(
                verify(mArtd), DEX_2, "arm64", "speed", "CLC_FOR_DEX_2", true /* isPublic */);

        verify(mArtd).getDexoptNeeded(
                eq(DEX_2), eq("arm"), any(), eq("speed"), eq(mDefaultDexoptTrigger));
        checkDexoptWithNoProfile(
                verify(mArtd), DEX_2, "arm", "speed", "CLC_FOR_DEX_2", true /* isPublic */);

        // It should use "verify" for dex 3 and make the artifacts private.

        verify(mArtd, never()).isProfileUsable(deepEq(mDex3RefProfile), any());
        verify(mArtd, never()).mergeProfiles(any(), deepEq(mDex3RefProfile), any(), any(), any());

        verify(mArtd).getDexoptNeeded(
                eq(DEX_3), eq("arm64"), isNull(), eq("verify"), eq(mDefaultDexoptTrigger));
        checkDexoptWithNoProfile(verify(mArtd), DEX_3, "arm64", "verify",
                null /* classLoaderContext */, false /* isPublic */);
    }

    private AndroidPackage createPackage() {
        var pkg = mock(AndroidPackage.class);
        lenient().when(pkg.isVmSafeMode()).thenReturn(false);
        lenient().when(pkg.isDebuggable()).thenReturn(false);
        lenient().when(pkg.getTargetSdkVersion()).thenReturn(123);
        lenient().when(pkg.isSignedWithPlatformKey()).thenReturn(false);
        lenient().when(pkg.isNonSdkApiRequested()).thenReturn(false);
        return pkg;
    }

    private PackageState createPackageState() {
        var pkgState = mock(PackageState.class);
        lenient().when(pkgState.getPackageName()).thenReturn(PKG_NAME);
        lenient().when(pkgState.getPrimaryCpuAbi()).thenReturn("arm64-v8a");
        lenient().when(pkgState.getSecondaryCpuAbi()).thenReturn("armeabi-v7a");
        lenient().when(pkgState.getAppId()).thenReturn(APP_ID);
        lenient().when(pkgState.getSeInfo()).thenReturn("se-info");
        AndroidPackage pkg = createPackage();
        lenient().when(pkgState.getAndroidPackage()).thenReturn(pkg);
        return pkgState;
    }

    private List<CheckedSecondaryDexInfo> createSecondaryDexInfo() throws Exception {
        // This should be compiled with profile.
        var dex1Info = mock(CheckedSecondaryDexInfo.class);
        lenient().when(dex1Info.dexPath()).thenReturn(DEX_1);
        lenient().when(dex1Info.userHandle()).thenReturn(USER_HANDLE);
        lenient().when(dex1Info.classLoaderContext()).thenReturn("CLC_FOR_DEX_1");
        lenient().when(dex1Info.abiNames()).thenReturn(Set.of("arm64-v8a"));
        lenient().when(dex1Info.isUsedByOtherApps()).thenReturn(false);
        lenient().when(dex1Info.fileVisibility()).thenReturn(FileVisibility.OTHER_READABLE);

        // This should be compiled without profile because it's used by other apps.
        var dex2Info = mock(CheckedSecondaryDexInfo.class);
        lenient().when(dex2Info.dexPath()).thenReturn(DEX_2);
        lenient().when(dex2Info.userHandle()).thenReturn(USER_HANDLE);
        lenient().when(dex2Info.classLoaderContext()).thenReturn("CLC_FOR_DEX_2");
        lenient().when(dex2Info.abiNames()).thenReturn(Set.of("arm64-v8a", "armeabi-v7a"));
        lenient().when(dex2Info.isUsedByOtherApps()).thenReturn(true);
        lenient().when(dex2Info.fileVisibility()).thenReturn(FileVisibility.OTHER_READABLE);

        // This should be compiled with verify because the class loader context is invalid.
        var dex3Info = mock(CheckedSecondaryDexInfo.class);
        lenient().when(dex3Info.dexPath()).thenReturn(DEX_3);
        lenient().when(dex3Info.userHandle()).thenReturn(USER_HANDLE);
        lenient().when(dex3Info.classLoaderContext()).thenReturn(null);
        lenient().when(dex3Info.abiNames()).thenReturn(Set.of("arm64-v8a"));
        lenient().when(dex3Info.isUsedByOtherApps()).thenReturn(false);
        lenient().when(dex3Info.fileVisibility()).thenReturn(FileVisibility.NOT_OTHER_READABLE);

        return List.of(dex1Info, dex2Info, dex3Info);
    }

    private void prepareProfiles() throws Exception {
        // Profile for dex file 1 is usable.
        lenient().when(mArtd.isProfileUsable(deepEq(mDex1RefProfile), any())).thenReturn(true);
        lenient()
                .when(mArtd.getProfileVisibility(deepEq(mDex1RefProfile)))
                .thenReturn(FileVisibility.NOT_OTHER_READABLE);

        // Profiles for dex file 2 and 3 are also usable, but shouldn't be used.
        lenient().when(mArtd.isProfileUsable(deepEq(mDex2RefProfile), any())).thenReturn(true);
        lenient()
                .when(mArtd.getProfileVisibility(deepEq(mDex2RefProfile)))
                .thenReturn(FileVisibility.NOT_OTHER_READABLE);
        lenient().when(mArtd.isProfileUsable(deepEq(mDex3RefProfile), any())).thenReturn(true);
        lenient()
                .when(mArtd.getProfileVisibility(deepEq(mDex3RefProfile)))
                .thenReturn(FileVisibility.NOT_OTHER_READABLE);

        lenient().when(mArtd.mergeProfiles(any(), any(), any(), any(), any())).thenReturn(true);

        // By default, none of the embedded profiles are usable.
        lenient()
                .when(mArtd.copyAndRewriteEmbeddedProfile(any(), any()))
                .thenReturn(TestingUtils.createCopyAndRewriteProfileNoProfile());
    }

    private GetDexoptNeededResult dexoptIsNeeded() {
        var result = new GetDexoptNeededResult();
        result.isDexoptNeeded = true;
        result.artifactsLocation = ArtifactsLocation.NONE_OR_ERROR;
        result.isVdexUsable = false;
        result.hasDexCode = true;
        return result;
    }

    private ArtdDexoptResult createArtdDexoptResult() {
        var result = new ArtdDexoptResult();
        result.cancelled = false;
        result.wallTimeMs = 0;
        result.cpuTimeMs = 0;
        result.sizeBytes = 0;
        result.sizeBeforeBytes = 0;
        return result;
    }

    private void checkDexoptWithPrivateProfile(IArtd artd, String dexPath, String isa,
            ProfilePath profile, String classLoaderContext) throws Exception {
        PermissionSettings permissionSettings = buildPermissionSettings(false /* isPublic */);
        OutputArtifacts outputArtifacts = AidlUtils.buildOutputArtifacts(
                dexPath, isa, false /* isInDalvikCache */, permissionSettings);
        artd.dexopt(deepEq(outputArtifacts), eq(dexPath), eq(isa), eq(classLoaderContext),
                eq("speed-profile"), deepEq(profile), any(), isNull() /* dmFile */, anyInt(),
                argThat(dexoptOptions -> dexoptOptions.generateAppImage == true), any());
    }

    private void checkDexoptWithNoProfile(IArtd artd, String dexPath, String isa,
            String compilerFilter, String classLoaderContext, boolean isPublic) throws Exception {
        PermissionSettings permissionSettings = buildPermissionSettings(isPublic);
        OutputArtifacts outputArtifacts = AidlUtils.buildOutputArtifacts(
                dexPath, isa, false /* isInDalvikCache */, permissionSettings);
        artd.dexopt(deepEq(outputArtifacts), eq(dexPath), eq(isa), eq(classLoaderContext),
                eq(compilerFilter), isNull(), any(), isNull() /* dmFile */, anyInt(),
                argThat(dexoptOptions -> dexoptOptions.generateAppImage == false), any());
    }

    private PermissionSettings buildPermissionSettings(boolean isPublic) {
        FsPermission dirFsPermission = AidlUtils.buildFsPermission(UID /* uid */, UID /* gid */,
                false /* isOtherReadable */, true /* isOtherExecutable */);
        FsPermission fileFsPermission =
                AidlUtils.buildFsPermission(UID /* uid */, UID /* gid */, isPublic);
        return AidlUtils.buildPermissionSettings(
                dirFsPermission, fileFsPermission, AidlUtils.buildSeContext("se-info", UID));
    }
}
