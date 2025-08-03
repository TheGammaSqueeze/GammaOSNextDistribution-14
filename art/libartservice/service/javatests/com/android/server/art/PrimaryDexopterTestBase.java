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

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;

import android.os.CancellationSignal;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.os.storage.StorageManager;

import com.android.modules.utils.pm.PackageStateModulesUtils;
import com.android.server.art.model.Config;
import com.android.server.art.testing.StaticMockitoRule;
import com.android.server.pm.pkg.AndroidPackage;
import com.android.server.pm.pkg.AndroidPackageSplit;
import com.android.server.pm.pkg.PackageState;
import com.android.server.pm.pkg.PackageUserState;

import dalvik.system.PathClassLoader;

import org.junit.Before;
import org.junit.Rule;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.List;

public class PrimaryDexopterTestBase {
    protected static final String PKG_NAME = "com.example.foo";
    protected static final int UID = 12345;
    protected static final int SHARED_GID = UserHandle.getSharedAppGid(UID);
    protected static final long ART_VERSION = 331413030l;
    protected static final String APP_VERSION_NAME = "12.34.56";
    protected static final long APP_VERSION_CODE = 1536036288l;

    @Rule
    public StaticMockitoRule mockitoRule = new StaticMockitoRule(
            SystemProperties.class, Constants.class, PackageStateModulesUtils.class);

    @Mock protected PrimaryDexopter.Injector mInjector;
    @Mock protected IArtd mArtd;
    @Mock protected UserManager mUserManager;
    @Mock protected DexUseManagerLocal mDexUseManager;
    @Mock protected StorageManager mStorageManager;
    protected PackageState mPkgState;
    protected AndroidPackage mPkg;
    protected PackageUserState mPkgUserStateNotInstalled;
    protected PackageUserState mPkgUserStateInstalled;
    protected CancellationSignal mCancellationSignal;
    protected Config mConfig;

    @Before
    public void setUp() throws Exception {
        mPkgUserStateNotInstalled = createPackageUserState(false /* installed */);
        mPkgUserStateInstalled = createPackageUserState(true /* installed */);
        mPkgState = createPackageState();
        mPkg = mPkgState.getAndroidPackage();
        mCancellationSignal = new CancellationSignal();
        mConfig = new Config();

        lenient().when(mInjector.getArtd()).thenReturn(mArtd);
        lenient().when(mInjector.isSystemUiPackage(any())).thenReturn(false);
        lenient().when(mInjector.isLauncherPackage(any())).thenReturn(false);
        lenient().when(mInjector.getUserManager()).thenReturn(mUserManager);
        lenient().when(mInjector.getDexUseManager()).thenReturn(mDexUseManager);
        lenient().when(mInjector.getStorageManager()).thenReturn(mStorageManager);
        lenient().when(mInjector.getArtVersion()).thenReturn(ART_VERSION);
        lenient().when(mInjector.getConfig()).thenReturn(mConfig);

        lenient()
                .when(SystemProperties.get("dalvik.vm.systemuicompilerfilter"))
                .thenReturn("speed");
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

        lenient()
                .when(mUserManager.getUserHandles(anyBoolean()))
                .thenReturn(List.of(UserHandle.of(0), UserHandle.of(1), UserHandle.of(2)));

        lenient().when(mDexUseManager.isPrimaryDexUsedByOtherApps(any(), any())).thenReturn(false);

        lenient().when(mStorageManager.getAllocatableBytes(any())).thenReturn(1l);
    }

    private AndroidPackage createPackage() {
        // This package has the base APK and one split APK that has code.
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

        var splits = List.of(baseSplit, split0, split1);
        lenient().when(pkg.getSplits()).thenReturn(splits);

        lenient().when(pkg.isVmSafeMode()).thenReturn(false);
        lenient().when(pkg.isDebuggable()).thenReturn(false);
        lenient().when(pkg.getTargetSdkVersion()).thenReturn(123);
        lenient().when(pkg.isSignedWithPlatformKey()).thenReturn(false);
        lenient().when(pkg.isNonSdkApiRequested()).thenReturn(false);
        lenient().when(pkg.getVersionName()).thenReturn(APP_VERSION_NAME);
        lenient().when(pkg.getLongVersionCode()).thenReturn(APP_VERSION_CODE);
        return pkg;
    }

    private PackageState createPackageState() {
        PackageState pkgState = mock(PackageState.class);
        lenient().when(pkgState.getPackageName()).thenReturn(PKG_NAME);
        lenient().when(pkgState.getPrimaryCpuAbi()).thenReturn("arm64-v8a");
        lenient().when(pkgState.getSecondaryCpuAbi()).thenReturn("armeabi-v7a");
        lenient().when(pkgState.getAppId()).thenReturn(UID);
        lenient().when(pkgState.getSharedLibraryDependencies()).thenReturn(new ArrayList<>());
        lenient().when(pkgState.getStateForUser(any())).thenReturn(mPkgUserStateNotInstalled);
        AndroidPackage pkg = createPackage();
        lenient().when(pkgState.getAndroidPackage()).thenReturn(pkg);
        lenient()
                .when(PackageStateModulesUtils.isLoadableInOtherProcesses(
                        same(pkgState), anyBoolean()))
                .thenReturn(false);
        return pkgState;
    }

    private PackageUserState createPackageUserState(boolean isInstalled) {
        PackageUserState pkgUserState = mock(PackageUserState.class);
        lenient().when(pkgUserState.isInstalled()).thenReturn(isInstalled);
        return pkgUserState;
    }

    protected GetDexoptNeededResult dexoptIsNotNeeded() {
        return dexoptIsNotNeeded(true /* hasDexCode */);
    }

    protected GetDexoptNeededResult dexoptIsNotNeeded(boolean hasDexCode) {
        var result = new GetDexoptNeededResult();
        result.isDexoptNeeded = false;
        result.hasDexCode = hasDexCode;
        return result;
    }

    protected GetDexoptNeededResult dexoptIsNeeded() {
        return dexoptIsNeeded(ArtifactsLocation.NONE_OR_ERROR);
    }

    protected GetDexoptNeededResult dexoptIsNeeded(@ArtifactsLocation int location) {
        var result = new GetDexoptNeededResult();
        result.isDexoptNeeded = true;
        result.artifactsLocation = location;
        if (location != ArtifactsLocation.NONE_OR_ERROR) {
            result.isVdexUsable = true;
        }
        result.hasDexCode = true;
        return result;
    }

    protected ArtdDexoptResult createArtdDexoptResult(boolean cancelled, long wallTimeMs,
            long cpuTimeMs, long sizeBytes, long sizeBeforeBytes) {
        var result = new ArtdDexoptResult();
        result.cancelled = cancelled;
        result.wallTimeMs = wallTimeMs;
        result.cpuTimeMs = cpuTimeMs;
        result.sizeBytes = sizeBytes;
        result.sizeBeforeBytes = sizeBeforeBytes;
        return result;
    }

    protected ArtdDexoptResult createArtdDexoptResult(boolean cancelled) {
        return createArtdDexoptResult(cancelled, 0 /* wallTimeMs */, 0 /* cpuTimeMs */,
                0 /* sizeBytes */, 0 /* sizeBeforeBytes */);
    }
}
