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

package android.app.sdksandbox;

import static com.google.common.truth.Truth.assertThat;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Process;
import android.os.StrictMode;
import android.provider.DeviceConfig;
import android.test.mock.MockContext;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.test.InstrumentationRegistry;

import com.android.compatibility.common.util.DeviceConfigStateManager;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Tests {@link SandboxedSdkContext} APIs.
 */
@RunWith(JUnit4.class)
public class SandboxedSdkContextUnitTest {

    private SandboxedSdkContext mSandboxedSdkContext;
    private static final String CLIENT_PACKAGE_NAME = "com.android.client";
    private static final String SDK_NAME = "com.android.codeproviderresources";
    private static final String RESOURCES_PACKAGE = "com.android.codeproviderresources_1";
    private static final String TEST_INTEGER_KEY = "test_integer";
    private static final int TEST_INTEGER_VALUE = 1234;
    private static final String TEST_STRING_KEY = "test_string";
    private static final String TEST_STRING_VALUE = "Test String";
    private static final String TEST_ASSET_FILE = "test-asset.txt";
    private static final String TEST_ASSET_VALUE = "This is a test asset";
    private static final String SDK_CE_DATA_DIR = "/data/misc_ce/0/sdksandbox/com.foo/sdk@123";
    private static final String SDK_DE_DATA_DIR = "/data/misc_de/0/sdksandbox/com.foo/sdk@123";

    private static boolean sCustomizedSdkContextEnabled = SdkLevel.isAtLeastU();

    private MockitoSession mStaticMockSession;

    @BeforeClass
    public static void setUpClass() {
        DeviceConfigStateManager stateManager =
                new DeviceConfigStateManager(
                        InstrumentationRegistry.getInstrumentation().getContext(),
                        DeviceConfig.NAMESPACE_ADSERVICES,
                        "sdksandbox_customized_sdk_context_enabled");
        sCustomizedSdkContextEnabled &= Boolean.parseBoolean(stateManager.get());
    }

    @Before
    public void setUp() throws Exception {
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .strictness(Strictness.LENIENT)
                        .mockStatic(Process.class)
                        .mockStatic(StrictMode.class)
                        .startMocking();
        ExtendedMockito.doReturn(true).when(() -> Process.isSdkSandbox());
        ExtendedMockito.doNothing().when(() -> StrictMode.setVmPolicy(Mockito.any()));

        Context context = Mockito.spy(InstrumentationRegistry.getContext());
        ApplicationInfo info =
                context.getPackageManager()
                        .getApplicationInfo(
                                RESOURCES_PACKAGE,
                                PackageManager.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES);

        Context baseContext = context;
        ClassLoader loader = getClass().getClassLoader();
        if (sCustomizedSdkContextEnabled) {
            info.dataDir = SDK_CE_DATA_DIR;
            info.credentialProtectedDataDir = SDK_CE_DATA_DIR;
            info.deviceProtectedDataDir = SDK_DE_DATA_DIR;

            baseContext = context.createContextForSdkInSandbox(info, 0);
            loader = baseContext.getClassLoader();
        }

        mSandboxedSdkContext =
                new SandboxedSdkContext(
                        baseContext,
                        loader,
                        CLIENT_PACKAGE_NAME,
                        info,
                        SDK_NAME,
                        SDK_CE_DATA_DIR,
                        SDK_DE_DATA_DIR,
                        sCustomizedSdkContextEnabled);
    }

    @After
    public void tearDown() throws Exception {
        if (mStaticMockSession != null) {
            mStaticMockSession.finishMocking();
        }
    }

    @Test
    public void testResources() {
        Resources resources = mSandboxedSdkContext.getResources();
        assertThat(resources).isNotNull();
        int integerId = resources.getIdentifier(TEST_INTEGER_KEY, "integer", RESOURCES_PACKAGE);
        assertThat(resources.getInteger(integerId)).isEqualTo(TEST_INTEGER_VALUE);
        int stringId = resources.getIdentifier(TEST_STRING_KEY, "string", RESOURCES_PACKAGE);
        assertThat(resources.getString(stringId)).isEqualTo(TEST_STRING_VALUE);
    }

    @Test
    public void testSdkName() {
        assertThat(mSandboxedSdkContext.getSdkName()).isEqualTo(SDK_NAME);
    }

    @Test
    public void testSdkPackageName() {
        assertThat(mSandboxedSdkContext.getSdkPackageName()).isEqualTo(RESOURCES_PACKAGE);
    }

    @Test
    public void testClientPackageName() {
        assertThat(mSandboxedSdkContext.getClientPackageName()).isEqualTo(CLIENT_PACKAGE_NAME);
    }

    @Test
    public void testAssets() throws Exception {
        AssetManager assets = mSandboxedSdkContext.getAssets();
        assertThat(assets).isNotNull();
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                assets.open(TEST_ASSET_FILE)));
        String readAsset = reader.readLine();
        assertThat(readAsset).isEqualTo(TEST_ASSET_VALUE);
    }

    @Test
    public void testExposureThroughSandboxedSdkProvider() throws Exception {
        TestSdkProvider sdkProvider = new TestSdkProvider();
        sdkProvider.attachContext(mSandboxedSdkContext);
        assertThat(sdkProvider.getContext()).isSameInstanceAs(mSandboxedSdkContext);
    }

    @Test
    public void testGetDataDir_CredentialEncrypted() throws Exception {
        assertThat(mSandboxedSdkContext.getDataDir().toString()).isEqualTo(SDK_CE_DATA_DIR);

        Context ceContext = mSandboxedSdkContext.createCredentialProtectedStorageContext();
        assertThat(ceContext.isCredentialProtectedStorage()).isTrue();
        assertThat(ceContext.getDataDir().toString()).isEqualTo(SDK_CE_DATA_DIR);
    }

    @Test
    public void testGetDataDir_DeviceEncrypted() throws Exception {
        Context deContext = mSandboxedSdkContext.createDeviceProtectedStorageContext();
        assertThat(deContext.isDeviceProtectedStorage()).isTrue();
        assertThat(deContext.getDataDir().toString()).isEqualTo(SDK_DE_DATA_DIR);
    }

    @Test
    public void testGetSystemService_notRegistered_delegatesToBaseContext() throws Exception {
        TestService testService = new TestService(InstrumentationRegistry.getContext());
        TestContext testContext = new TestContext(testService);

        ApplicationInfo info =
                InstrumentationRegistry.getContext()
                        .getPackageManager()
                        .getApplicationInfo(
                                RESOURCES_PACKAGE,
                                PackageManager.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES);
        SdkSandboxSystemServiceRegistry registry = new SdkSandboxSystemServiceRegistry();
        SandboxedSdkContext sandboxedSdkContext =
                new SandboxedSdkContext(
                        testContext,
                        getClass().getClassLoader(),
                        CLIENT_PACKAGE_NAME,
                        info,
                        SDK_NAME,
                        SDK_CE_DATA_DIR,
                        SDK_DE_DATA_DIR,
                        sCustomizedSdkContextEnabled,
                        registry);
        assertThat(sandboxedSdkContext.getSystemService("ignored")).isSameInstanceAs(testService);
    }

    @Test
    public void testGetSystemService_registered_mutatesService() throws Exception {
        TestService testService = new TestService(InstrumentationRegistry.getContext());
        TestContext testContext = new TestContext(testService);

        ApplicationInfo info =
                InstrumentationRegistry.getContext()
                        .getPackageManager()
                        .getApplicationInfo(
                                RESOURCES_PACKAGE,
                                PackageManager.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES);
        SdkSandboxSystemServiceRegistry registry = new SdkSandboxSystemServiceRegistry();
        registry.registerServiceMutator(
                "service", (service, ctx) -> (((TestService) service)).initialize(ctx));
        SandboxedSdkContext sandboxedSdkContext =
                new SandboxedSdkContext(
                        testContext,
                        getClass().getClassLoader(),
                        CLIENT_PACKAGE_NAME,
                        info,
                        SDK_NAME,
                        SDK_CE_DATA_DIR,
                        SDK_DE_DATA_DIR,
                        sCustomizedSdkContextEnabled,
                        registry);
        TestService service = (TestService) sandboxedSdkContext.getSystemService("service");
        assertThat(service.mInitialized).isTrue();
        assertThat(service.mCtx).isSameInstanceAs(sandboxedSdkContext);
    }

    @Test
    public void testCachingPerContextIntance() throws Exception {
        // TODO(b/242889021): simplify this test after refactoring the c-tor of SandboxedSdkContext

        ApplicationInfo info =
                InstrumentationRegistry.getContext()
                        .getPackageManager()
                        .getApplicationInfo(
                                RESOURCES_PACKAGE,
                                PackageManager.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES);
        SandboxedSdkContext ctx1 =
                new SandboxedSdkContext(
                        InstrumentationRegistry.getContext()
                                .createCredentialProtectedStorageContext(),
                        getClass().getClassLoader(),
                        CLIENT_PACKAGE_NAME,
                        info,
                        SDK_NAME,
                        SDK_CE_DATA_DIR,
                        SDK_DE_DATA_DIR,
                        sCustomizedSdkContextEnabled);
        SandboxedSdkContext ctx2 =
                new SandboxedSdkContext(
                        InstrumentationRegistry.getContext()
                                .createCredentialProtectedStorageContext(),
                        getClass().getClassLoader(),
                        CLIENT_PACKAGE_NAME,
                        info,
                        SDK_NAME,
                        SDK_CE_DATA_DIR,
                        SDK_DE_DATA_DIR,
                        sCustomizedSdkContextEnabled);

        ActivityManager am1 = ctx1.getSystemService(ActivityManager.class);
        ActivityManager am2 = ctx2.getSystemService(ActivityManager.class);
        assertThat(am1).isNotSameInstanceAs(am2);
    }

    /**
     * Test the getter of customizedSdkContextEnabled flag
     *
     * @throws Exception for errors to retrieve the ApplicationInfo from PackageManager
     */
    @Test
    public void testisCustomizedSdkContextEnabled() throws Exception {
        ApplicationInfo info =
                InstrumentationRegistry.getContext()
                        .getPackageManager()
                        .getApplicationInfo(
                                RESOURCES_PACKAGE,
                                PackageManager.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES);

        boolean isCustomizedSdkContextEnabled = true;
        SandboxedSdkContext context =
                new SandboxedSdkContext(
                        InstrumentationRegistry.getContext()
                                .createCredentialProtectedStorageContext(),
                        getClass().getClassLoader(),
                        CLIENT_PACKAGE_NAME,
                        info,
                        SDK_NAME,
                        SDK_CE_DATA_DIR,
                        SDK_DE_DATA_DIR,
                        isCustomizedSdkContextEnabled);
        assertThat(context.isCustomizedSdkContextEnabled())
                .isEqualTo(isCustomizedSdkContextEnabled);

        isCustomizedSdkContextEnabled = false;
        context =
                new SandboxedSdkContext(
                        InstrumentationRegistry.getContext()
                                .createCredentialProtectedStorageContext(),
                        getClass().getClassLoader(),
                        CLIENT_PACKAGE_NAME,
                        info,
                        SDK_NAME,
                        SDK_CE_DATA_DIR,
                        SDK_DE_DATA_DIR,
                        isCustomizedSdkContextEnabled);
        assertThat(context.isCustomizedSdkContextEnabled())
                .isEqualTo(isCustomizedSdkContextEnabled);
    }

    /**
     * Ensure that SandboxedSdkContext objects can create new instances from the same class with new
     * Context as base.
     *
     * @throws Exception for errors to retrieve the ApplicationInfo from PackageManager
     */
    @Test
    public void testCreateContextWithNewBase() throws Exception {
        ApplicationInfo info =
                InstrumentationRegistry.getContext()
                        .getPackageManager()
                        .getApplicationInfo(
                                RESOURCES_PACKAGE,
                                PackageManager.MATCH_STATIC_SHARED_AND_SDK_LIBRARIES);
        TestService testService = new TestService(InstrumentationRegistry.getContext());
        TestContext baseContext = new TestContext(testService);
        SandboxedSdkContext sdkContext =
                new SandboxedSdkContext(
                        baseContext,
                        getClass().getClassLoader(),
                        CLIENT_PACKAGE_NAME,
                        info,
                        SDK_NAME,
                        SDK_CE_DATA_DIR,
                        SDK_DE_DATA_DIR,
                        sCustomizedSdkContextEnabled);
        assertThat(sdkContext.getBaseContext()).isEqualTo(baseContext);

        Context newBaseContext = new TestContext(testService);
        SandboxedSdkContext contextWithNewBase =
                sdkContext.createContextWithNewBase(newBaseContext);

        assertThat(contextWithNewBase.getBaseContext()).isEqualTo(newBaseContext);
    }

    private static class TestContext extends MockContext {

        private final TestService mMockService;

        private TestContext(TestService mockService) {
            mMockService = mockService;
        }

        @Override
        public Object getSystemService(String serviceName) {
            return mMockService;
        }
    }

    private static class TestService {

        private boolean mInitialized = false;
        private Context mCtx;

        private TestService(Context ctx) {
            mCtx = ctx;
        }

        public TestService initialize(Context ctx) {
            mInitialized = true;
            mCtx = ctx;
            return this;
        }
    }

    private static class TestSdkProvider extends SandboxedSdkProvider {
        @NonNull
        @Override
        public SandboxedSdk onLoadSdk(@NonNull Bundle params) throws LoadSdkException {
            return null;
        }

        @NonNull
        @Override
        public View getView(
                @NonNull Context windowContext, @NonNull Bundle params, int width, int height) {
            return null;
        }
    }
}
