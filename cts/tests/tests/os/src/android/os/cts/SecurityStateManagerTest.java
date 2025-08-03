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

package android.os.cts;

import static android.os.SecurityStateManager.KEY_KERNEL_VERSION;
import static android.os.SecurityStateManager.KEY_SYSTEM_SPL;
import static android.os.SecurityStateManager.KEY_VENDOR_SPL;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static org.testng.AssertJUnit.assertEquals;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.os.Flags;
import android.os.SecurityStateManager;
import android.os.SystemProperties;
import android.os.VintfRuntimeInfo;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.webkit.WebViewUpdateService;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RunWith(AndroidJUnit4.class)
@RequiresFlagsEnabled(Flags.FLAG_SECURITY_STATE_SERVICE)
public class SecurityStateManagerTest {

    private Context mContext;
    private Resources mResources;
    private PackageManager mPackageManager;
    private SecurityStateManager mSecurityStateManager;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setUp() {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.INTERACT_ACROSS_USERS_FULL);
        mContext = getApplicationContext();
        mResources = mContext.getResources();
        mPackageManager = mContext.getPackageManager();
        mSecurityStateManager = mContext.getSystemService(SecurityStateManager.class);
    }

    @Test
    public void testGetGlobalSecurityState() throws Exception {
        Pattern pattern = Pattern.compile("(\\d+\\.\\d+\\.\\d+)(.*)");
        Matcher matcher = pattern.matcher(VintfRuntimeInfo.getKernelVersion());
        String kernelVersion = "";
        if (matcher.matches()) {
            kernelVersion = matcher.group(1);
        }
        String defaultModuleMetadata = mContext.getString(
                mResources.getIdentifier("config_defaultModuleMetadataProvider",
                        "string", "android"));
        List<String> webViewPackages = Arrays.stream(WebViewUpdateService.getAllWebViewPackages())
                .map(info -> info.packageName).toList();
        List<String> securityStatePackages = Arrays.stream(mContext.getResources().getStringArray(
                mResources.getIdentifier("config_securityStatePackages",
                        "array", "android"))).toList();
        Bundle bundle = mSecurityStateManager.getGlobalSecurityState();

        assertEquals(bundle.getString(KEY_SYSTEM_SPL), Build.VERSION.SECURITY_PATCH);
        assertEquals(bundle.getString(KEY_VENDOR_SPL),
                SystemProperties.get("ro.vendor.build.security_patch", ""));
        assertEquals(bundle.getString(KEY_KERNEL_VERSION), kernelVersion);
        packageVersionNameCheck(bundle, defaultModuleMetadata);
        webViewPackages.forEach(p -> packageVersionNameCheck(bundle, p));
        securityStatePackages.forEach(p -> packageVersionNameCheck(bundle, p));
    }

    private void packageVersionNameCheck(Bundle bundle, String packageName) {
        if (bundle.containsKey(packageName)) {
            try {
                assertEquals(bundle.getString(packageName),
                        mPackageManager.getPackageInfo(packageName, 0 /* flags */).versionName);
            } catch (PackageManager.NameNotFoundException ignored) {
                assertEquals(bundle.getString(packageName), "");
            }
        }
    }
}
