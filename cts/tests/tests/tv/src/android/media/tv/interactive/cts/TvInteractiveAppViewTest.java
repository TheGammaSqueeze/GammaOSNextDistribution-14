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

import static org.junit.Assert.assertNotNull;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.tv.interactive.TvInteractiveAppManager;
import android.media.tv.interactive.TvInteractiveAppServiceInfo;
import android.media.tv.interactive.TvInteractiveAppView;
import android.os.ConditionVariable;
import android.tv.cts.R;
import android.view.InputEvent;

import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.RequiredFeatureRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.Executor;

/**
 * Test {@link android.media.tv.interactive.TvInteractiveAppView}.
 */
@RunWith(AndroidJUnit4.class)
public class TvInteractiveAppViewTest {
    private static final long TIME_OUT_MS = 20000L;

    private Instrumentation mInstrumentation;
    private ActivityScenario<TvInteractiveAppViewStubActivity> mActivityScenario;
    private TvInteractiveAppViewStubActivity mActivity;
    private TvInteractiveAppView mTvInteractiveAppView;
    private TvInteractiveAppManager mManager;
    private TvInteractiveAppServiceInfo mStubInfo;
    private TvInteractiveAppView.OnUnhandledInputEventListener mOnUnhandledInputEventListener;

    @Rule
    public RequiredFeatureRule featureRule = new RequiredFeatureRule(
            PackageManager.FEATURE_LIVE_TV);

    private final MockCallback mCallback = new MockCallback();

    public static class MockCallback extends TvInteractiveAppView.TvInteractiveAppCallback {
        private String mInteractiveAppServiceId;
        private int mState = -1;
        private int mErr = -1;

        @Override
        public void onStateChanged(String interactiveAppServiceId, int state, int err) {
            super.onStateChanged(interactiveAppServiceId, state, err);
            mInteractiveAppServiceId = interactiveAppServiceId;
            mState = state;
            mErr = err;
        }
    }

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

        for (TvInteractiveAppServiceInfo info : mManager.getTvInteractiveAppServiceList()) {
            if (info.getServiceInfo().name.equals(StubTvInteractiveAppService.class.getName())) {
                mStubInfo = info;
            }
        }
        assertNotNull(mStubInfo);
        mTvInteractiveAppView.setCallback(getExecutor(), mCallback);
    }

    @After
    public void tearDown() throws Throwable {
        runTestOnUiThread(new Runnable() {
            public void run() {
                mTvInteractiveAppView.clearCallback();
                mTvInteractiveAppView.clearOnUnhandledInputEventListener();
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
    public void testConstructor() throws Throwable {
        runTestOnUiThread(new Runnable() {
            public void run() {
                new TvInteractiveAppView(mActivity);
                new TvInteractiveAppView(mActivity, null);
                new TvInteractiveAppView(mActivity, null, 0);
            }
        });
    }

    @Test
    public void testStartInteractiveApp() throws Throwable {
        mTvInteractiveAppView.prepareInteractiveApp(mStubInfo.getId(), 1);
        mInstrumentation.waitForIdleSync();
        new PollingCheck(TIME_OUT_MS) {
            @Override
            protected boolean check() {
                return mTvInteractiveAppView.getInteractiveAppSession() != null;
            }
        }.run();
        mTvInteractiveAppView.startInteractiveApp();
        new PollingCheck(TIME_OUT_MS) {
            @Override
            protected boolean check() {
                return mCallback.mInteractiveAppServiceId == mStubInfo.getId()
                        && mCallback.mState
                        == TvInteractiveAppManager.INTERACTIVE_APP_STATE_RUNNING
                        && mCallback.mErr == TvInteractiveAppManager.ERROR_NONE;
            }
        }.run();
    }

    @Test
    public void testGetOnUnhandledInputEventListener() {
        mOnUnhandledInputEventListener = new TvInteractiveAppView.OnUnhandledInputEventListener() {
            @Override
            public boolean onUnhandledInputEvent(InputEvent event) {
                return true;
            }
        };
        mTvInteractiveAppView.setOnUnhandledInputEventListener(getExecutor(),
                mOnUnhandledInputEventListener);
        new PollingCheck(TIME_OUT_MS) {
            @Override
            protected boolean check() {
                return mTvInteractiveAppView.getOnUnhandledInputEventListener()
                        == mOnUnhandledInputEventListener;
            }
        }.run();
    }
}
