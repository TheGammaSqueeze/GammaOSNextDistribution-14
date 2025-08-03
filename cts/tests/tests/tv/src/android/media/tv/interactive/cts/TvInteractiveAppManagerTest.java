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
 * limitations under the License.
 */

package android.media.tv.interactive.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertNotNull;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.tv.interactive.AppLinkInfo;
import android.media.tv.interactive.TvInteractiveAppManager;
import android.media.tv.interactive.TvInteractiveAppServiceInfo;
import android.media.tv.interactive.TvInteractiveAppView;
import android.os.Bundle;
import android.os.ConditionVariable;
import android.tv.cts.R;

import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.RequiredFeatureRule;

import junit.framework.AssertionFailedError;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * Test {@link android.media.tv.interactive.TvInteractiveAppManager}.
 */
@RunWith(AndroidJUnit4.class)
public class TvInteractiveAppManagerTest {
    private static final long TIME_OUT_MS = 20000L;

    private Instrumentation mInstrumentation;
    private ActivityScenario<TvInteractiveAppViewStubActivity> mActivityScenario;
    private TvInteractiveAppViewStubActivity mActivity;
    private TvInteractiveAppView mTvInteractiveAppView;
    private TvInteractiveAppManager mManager;

    private final MockCallback mCallback = new MockCallback();

    public static class MockCallback extends TvInteractiveAppManager.TvInteractiveAppCallback {
        private String mIAppServiceId;
        private int mType;
        private int mState;
        private int mErr;

        @Override
        public void onInteractiveAppServiceAdded(String iAppServiceId) {
            super.onInteractiveAppServiceAdded(iAppServiceId);
        }

        @Override
        public void onInteractiveAppServiceRemoved(String iAppServiceId) {
            super.onInteractiveAppServiceRemoved(iAppServiceId);
        }

        @Override
        public void onInteractiveAppServiceUpdated(String iAppServiceId) {
            super.onInteractiveAppServiceUpdated(iAppServiceId);
        }

        @Override
        public void onTvInteractiveAppServiceInfoUpdated(TvInteractiveAppServiceInfo iAppInfo) {
            super.onTvInteractiveAppServiceInfoUpdated(iAppInfo);
        }

        @Override
        public void onTvInteractiveAppServiceStateChanged(
                String iAppServiceId, int type, int state, int err) {
            super.onTvInteractiveAppServiceStateChanged(iAppServiceId, type, state, err);
            mIAppServiceId = iAppServiceId;
            mType = type;
            mState = state;
            mErr = err;
        }
    }

    @Rule
    public RequiredFeatureRule featureRule = new RequiredFeatureRule(
            PackageManager.FEATURE_LIVE_TV);


    private TvInteractiveAppView findTvInteractiveAppViewById(int id) {
        return (TvInteractiveAppView) mActivity.findViewById(id);
    }

    private void runTestOnUiThread(final Runnable r) throws Throwable {
        final Throwable[] exceptions = new Throwable[1];
        mInstrumentation.runOnMainSync(new Runnable() {
            public void run() {
                try {
                    r.run();
                } catch (Throwable throwable) {
                    exceptions[0] = throwable;
                }
            }
        });
        if (exceptions[0] != null) {
            throw exceptions[0];
        }
    }

    private Executor getExecutor() {
        return Runnable::run;
    }

    @Before
    public void setUp() throws Throwable {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClass(
                mInstrumentation.getTargetContext(), TvInteractiveAppViewStubActivity.class);

        // DO NOT use ActivityScenario.launch(Class), which can cause ActivityNotFoundException
        // related to BootstrapActivity.
        mActivityScenario = ActivityScenario.launch(intent);
        ConditionVariable activityReferenceObtained = new ConditionVariable();
        mActivityScenario.onActivity(activity -> {
            mActivity = activity;
            activityReferenceObtained.open();
        });
        activityReferenceObtained.block(TIME_OUT_MS);

        assertNotNull("Failed to acquire activity reference.", mActivity);
        mTvInteractiveAppView = findTvInteractiveAppViewById(R.id.tviappview);
        assertNotNull("Failed to find TvInteractiveAppView.", mTvInteractiveAppView);

        mManager = (TvInteractiveAppManager) mActivity.getSystemService(
                Context.TV_INTERACTIVE_APP_SERVICE);
        assertNotNull("Failed to get TvInteractiveAppManager.", mManager);
        runTestOnUiThread(new Runnable() {
            @Override
            public void run() {
                mManager.registerCallback(getExecutor(), mCallback);
            }
        });
    }

    @After
    public void tearDown() throws Throwable {
        runTestOnUiThread(new Runnable() {
            public void run() {
                mManager.unregisterCallback(mCallback);
                mTvInteractiveAppView.reset();
            }
        });
        mInstrumentation.waitForIdleSync();
        mActivity = null;
        if (mActivityScenario != null) {
            mActivityScenario.close();
        }
    }

    @Test
    public void testGetTvInteractiveAppServiceInfoList() throws Exception {
        List<TvInteractiveAppServiceInfo> list = mManager.getTvInteractiveAppServiceList();

        for (TvInteractiveAppServiceInfo info : list) {
            if (info.getServiceInfo().name.equals(StubTvInteractiveAppService.class.getName())
                    && info.getSupportedTypes()
                    == (TvInteractiveAppServiceInfo.INTERACTIVE_APP_TYPE_HBBTV
                            | TvInteractiveAppServiceInfo.INTERACTIVE_APP_TYPE_GINGA
                            | TvInteractiveAppServiceInfo.INTERACTIVE_APP_TYPE_OTHER)) {
                assertThat(info.getCustomSupportedTypes())
                        .containsExactly("custom_type", "another");
                return;
            }
        }
        throw new AssertionFailedError(
                "getTvInteractiveAppServiceList() doesn't contain valid "
                        + "TvInteractiveAppServiceInfo: "
                        + StubTvInteractiveAppService.class.getName());
    }

    @Test
    public void testGetAppLinkInfoList() {
        List<AppLinkInfo> infos = mManager.getAppLinkInfoList();
        String packageName = "android.tv.cts";
        for (AppLinkInfo info : infos) {
            if (info.getComponentName().getPackageName().equals(packageName)
                    && info.getComponentName().getClassName().equals(
                            "android.media.tv.cts.TvInputSetupActivityStub")
                    && info.getUri().toString().equals("https://example.test")) {
                return;
            }
        }
        throw new AssertionFailedError(
                "getAppLinkInfoList() doesn't contain expected AppLinkInfo of " + packageName);
    }

    @Test
    public void testAppLinkCommand() throws Exception {
        List<TvInteractiveAppServiceInfo> list = mManager.getTvInteractiveAppServiceList();

        TvInteractiveAppServiceInfo stubInfo = null;
        for (TvInteractiveAppServiceInfo info : list) {
            if (info.getServiceInfo().name.equals(StubTvInteractiveAppService.class.getName())) {
                stubInfo = info;
                break;
            }
        }
        assertNotNull(stubInfo);

        Bundle bundle = new Bundle();
        bundle.putString(TvInteractiveAppManager.APP_LINK_KEY_PACKAGE_NAME, "pkg_name");
        bundle.putString(TvInteractiveAppManager.APP_LINK_KEY_CLASS_NAME, "clazz_name");

        mManager.sendAppLinkCommand(stubInfo.getId(), bundle);
        PollingCheck.waitFor(
                TIME_OUT_MS, () -> StubTvInteractiveAppService.sAppLinkCommand != null);

        assertBundlesAreEqual(StubTvInteractiveAppService.sAppLinkCommand, bundle);
    }

    @Test
    public void testAppLinkInfo() throws Exception {
        List<TvInteractiveAppServiceInfo> list = mManager.getTvInteractiveAppServiceList();

        TvInteractiveAppServiceInfo stubInfo = null;
        for (TvInteractiveAppServiceInfo info : list) {
            if (info.getServiceInfo().name.equals(StubTvInteractiveAppService.class.getName())) {
                stubInfo = info;
                break;
            }
        }
        assertNotNull(stubInfo);

        AppLinkInfo info = new AppLinkInfo("pkg_name", "clazz_name", "https://uri.test");

        mManager.registerAppLinkInfo(stubInfo.getId(), info);
        PollingCheck.waitFor(
                TIME_OUT_MS, () -> StubTvInteractiveAppService.sAppLinkInfo != null);
        assertThat(StubTvInteractiveAppService.sAppLinkInfo.getComponentName().getPackageName())
                .isEqualTo("pkg_name");
        assertThat(StubTvInteractiveAppService.sAppLinkInfo.getComponentName().getClassName())
                .isEqualTo("clazz_name");
        assertThat(StubTvInteractiveAppService.sAppLinkInfo.getUri().toString())
                .isEqualTo("https://uri.test");

        mManager.unregisterAppLinkInfo(stubInfo.getId(), info);
        PollingCheck.waitFor(
                TIME_OUT_MS, () -> StubTvInteractiveAppService.sAppLinkInfo == null);

        info = new AppLinkInfo("pkg2", "class2", "http://applinkinfo.com");

        mManager.registerAppLinkInfo(stubInfo.getId(), info);
        PollingCheck.waitFor(
                TIME_OUT_MS, () -> StubTvInteractiveAppService.sAppLinkInfo != null);
        assertThat(StubTvInteractiveAppService.sAppLinkInfo.getComponentName().getPackageName())
                .isEqualTo("pkg2");
        assertThat(StubTvInteractiveAppService.sAppLinkInfo.getComponentName().getClassName())
                .isEqualTo("class2");
        assertThat(StubTvInteractiveAppService.sAppLinkInfo.getUri().toString())
                .isEqualTo("http://applinkinfo.com");
    }

    private static void assertBundlesAreEqual(Bundle actual, Bundle expected) {
        if (expected != null && actual != null) {
            assertThat(actual.keySet()).isEqualTo(expected.keySet());
            for (String key : expected.keySet()) {
                assertThat(actual.get(key)).isEqualTo(expected.get(key));
            }
        } else {
            assertThat(actual).isEqualTo(expected);
        }
    }
}
