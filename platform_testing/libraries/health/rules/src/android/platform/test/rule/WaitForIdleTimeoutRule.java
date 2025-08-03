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

package android.platform.test.rule;

import android.util.Log;

import androidx.annotation.VisibleForTesting;
import androidx.test.uiautomator.Configurator;

import org.junit.runner.Description;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * This rule allows overriding of timeout in waitForIdle() for entire CUJs.
 *
 * <p>It is useful for CUJs where the app can't reach an idle state and should only be used as
 * temporary fixes.
 *
 * <p>Since this is meant to be a hotfix, it is disabled by default and must be turned on explicitly
 * in tests.
 */
public class WaitForIdleTimeoutRule extends TestWatcher {
    private static final String LOG_TAG = WaitForIdleTimeoutRule.class.getSimpleName();

    @VisibleForTesting static final String DISABLE_OPTION = "wait-for-idle-timeout-disable";
    private boolean mDisabled = true;

    @VisibleForTesting static final String TIMEOUT_OPTION = "wait-for-idle-timeout";
    private long mWaitForIdleTimeout = 500L;

    private List<TimeoutSetter> mTimeoutSetters = new ArrayList();

    @Override
    protected void starting(Description description) {
        mDisabled =
                Boolean.parseBoolean(
                        getArguments().getString(DISABLE_OPTION, String.valueOf(mDisabled)));
        if (mDisabled) {
            return;
        }

        mWaitForIdleTimeout =
                Long.parseLong(
                        getArguments()
                                .getString(TIMEOUT_OPTION, String.valueOf(mWaitForIdleTimeout)));

        mTimeoutSetters.addAll(getTimeoutSetters());

        for (TimeoutSetter setter : mTimeoutSetters) {
            setter.storeInitialAndSetNewTimeout(mWaitForIdleTimeout);
        }
    }

    @Override
    protected void finished(Description description) {
        if (mDisabled) {
            return;
        }

        for (TimeoutSetter setter : mTimeoutSetters) {
            setter.restoreTimeout();
        }
    }

    @VisibleForTesting
    protected List<TimeoutSetter> getTimeoutSetters() {
        return Arrays.asList(new AndroidXTimeoutSetter(), new AndroidSupportTimeoutSetter());
    }

    // Prevents code duplication when dealing with both android.support.test and androidx.test.
    @VisibleForTesting
    abstract static class TimeoutSetter {
        protected abstract String getDescription();

        protected abstract void setTimeout(long ms);

        protected abstract long getTimeout();

        private long mInitialTimeout = 0L;

        public final void storeInitialAndSetNewTimeout(long ms) {
            mInitialTimeout = getTimeout();
            Log.i(
                    LOG_TAG,
                    String.format(
                            "waitForIdle() timeout in %s was initially %d ms.",
                            getDescription(), mInitialTimeout));

            Log.i(
                    LOG_TAG,
                    String.format(
                            "Trying to set waitForIdle() timeout in %s to %d ms.",
                            getDescription(), ms));
            setTimeout(ms);
            if (getTimeout() != ms) {
                Log.w(
                        LOG_TAG,
                        String.format(
                                "Failed to set waitForIdle() timeout in %s to %d ms.",
                                getDescription(), ms));
            }

            logCurrentTimeout();
        }

        public final void restoreTimeout() {
            Log.i(
                    LOG_TAG,
                    String.format(
                            "Trying to restore waitForIdle() timeout in %s back to %d ms.",
                            getDescription(), mInitialTimeout));
            setTimeout(mInitialTimeout);
            if (getTimeout() != mInitialTimeout) {
                Log.w(
                        LOG_TAG,
                        String.format(
                                "Failed to restore waitForIdle() timeout in %s back to %d ms.",
                                getDescription(), mInitialTimeout));
            }

            logCurrentTimeout();
        }

        private void logCurrentTimeout() {
            Log.i(
                    LOG_TAG,
                    String.format(
                            "waitForIdle() timeout in %s is now %d ms.",
                            getDescription(), getTimeout()));
        }
    }

    private static class AndroidXTimeoutSetter extends TimeoutSetter {
        @Override
        protected String getDescription() {
            return "androidx.test.uiautomator";
        }

        @Override
        protected void setTimeout(long ms) {
            Configurator.getInstance().setWaitForIdleTimeout(ms);
            return;
        }

        @Override
        protected long getTimeout() {
            return Configurator.getInstance().getWaitForIdleTimeout();
        }
    }

    private static class AndroidSupportTimeoutSetter extends TimeoutSetter {
        @Override
        protected String getDescription() {
            return "android.support.test.uiautomator";
        }

        @Override
        protected void setTimeout(long ms) {
            android.support.test.uiautomator.Configurator.getInstance().setWaitForIdleTimeout(ms);
            return;
        }

        @Override
        protected long getTimeout() {
            return android.support.test.uiautomator.Configurator.getInstance()
                    .getWaitForIdleTimeout();
        }
    }
}
