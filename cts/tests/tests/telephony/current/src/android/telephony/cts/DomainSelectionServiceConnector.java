/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.telephony.cts;

import static android.telephony.cts.TestDomainSelectionService.LATCH_ON_BIND;
import static android.telephony.cts.TestDomainSelectionService.LATCH_ON_UNBIND;

import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.telephony.cts.util.TelephonyUtils;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Connects the CTS test DomainSelectionService to the telephony framework.
 */
class DomainSelectionServiceConnector {

    private static final String TAG = "CtsDomainSelectionServiceConnector";

    private static final String PACKAGE_NAME =
            InstrumentationRegistry.getInstrumentation().getTargetContext().getPackageName();
    private static final String CLASS_NAME = TestDomainSelectionService.class.getName();

    private static final String COMMAND_BASE = "cmd phone ";
    private static final String COMMAND_SET_SERVICE_OVERRIDE =
            "domainselection set-dss-override ";
    private static final String COMMAND_CLEAR_SERVICE_OVERRIDE =
            "domainselection clear-dss-override";

    private class TestServiceConnection implements ServiceConnection {

        private final CountDownLatch mLatch;

        TestServiceConnection(CountDownLatch latch) {
            mLatch = latch;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mTestService = ((TestDomainSelectionService.LocalBinder) service).getService();
            mLatch.countDown();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mTestService = null;
        }
    }

    public class Controller {

        private boolean mIsServiceOverridden = false;

        void clearPackage() throws Exception {
            mIsServiceOverridden = true;
            boolean unbindSent = setTestDomainSelectionService("none");
            if (unbindSent) waitForPackageUnbind();
        }

        void waitForPackageUnbind() {
            TestDomainSelectionService testService = getTestService();
            if (testService == null) return;
            // First unbind the local services
            removeLocalServiceConnection();
            // Then wait for telephony framework to unbind if there is still an active binding.
            boolean isBound = testService.isTelephonyBound();
            Log.i(TAG, "waitForPackageUnbind: isBound=" + isBound);
            if (isBound) {
                // Wait for telephony to unbind to local service
                testService.waitForLatchCountdown(LATCH_ON_UNBIND);
            }
        }

        boolean overrideService() throws Exception {
            mIsServiceOverridden = true;
            return setTestDomainSelectionService(PACKAGE_NAME)
                    && getTestService().waitForLatchCountdown(LATCH_ON_BIND);
        }

        void restoreOriginalPackage() throws Exception {
            if (mIsServiceOverridden) {
                mIsServiceOverridden = false;
                clearTestDomainSelectionServiceOverride();
            }
        }

        private boolean setTestDomainSelectionService(String packageName) throws Exception {
            String result = TelephonyUtils.executeShellCommand(mInstrumentation,
                    constructSetDomainSelectionServiceOverrideCommand(packageName, CLASS_NAME));
            Log.i(TAG, "setTestDomainSelectionService result: " + result);
            return "true".equals(result);
        }

        private boolean clearTestDomainSelectionServiceOverride() throws Exception {
            String result = TelephonyUtils.executeShellCommand(mInstrumentation,
                    constructClearDomainSelectionServiceOverrideCommand());
            Log.i(TAG, "clearTestDomainSelectionServiceOverride result: " + result);
            return "true".equals(result);
        }

        private String constructSetDomainSelectionServiceOverrideCommand(String packageName,
                String className) {
            ComponentName componentName = ComponentName.createRelative(packageName, className);
            return COMMAND_BASE + COMMAND_SET_SERVICE_OVERRIDE + componentName.flattenToString();
        }

        private String constructClearDomainSelectionServiceOverrideCommand() {
            return COMMAND_BASE + COMMAND_CLEAR_SERVICE_OVERRIDE;
        }
    }

    private Instrumentation mInstrumentation;

    private TestDomainSelectionService mTestService;
    private TestServiceConnection mTestServiceConn;

    private Controller mController;

    DomainSelectionServiceConnector(Instrumentation instrumentation) {
        mInstrumentation = instrumentation;
    }

    void clearActiveDomainSelectionService() throws Exception {
        mController = new Controller();
        mController.clearPackage();
    }

    private boolean setupLocalTestDomainSelectionService() {
        if (mTestService != null) {
            return true;
        }
        CountDownLatch latch = new CountDownLatch(1);
        mTestServiceConn = new TestServiceConnection(latch);
        mInstrumentation.getContext().bindService(new Intent(mInstrumentation.getContext(),
                TestDomainSelectionService.class), mTestServiceConn, Context.BIND_AUTO_CREATE);
        try {
            return latch.await(5000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            return false;
        }
    }

    /**
     * Binds to the local implementation of DomainSelectionService but does not trigger
     * binding from telephony to allow additional configuration steps.
     * @return true if this request succeeded, false otherwise.
     */
    boolean connectTestDomainSelectionServiceLocally() {
        if (!setupLocalTestDomainSelectionService()) {
            Log.w(TAG, "connectTestDomainSelectionService: couldn't set up service.");
            return false;
        }
        mTestService.resetState();
        return true;
    }

    /**
     * Trigger the telephony framework to bind to the local service implementation.
     * @return true if this request succeeded, false otherwise.
     */
    boolean triggerFrameworkConnectionToTestDomainSelectionService() throws Exception {
        return mController.overrideService();
    }

    boolean connectTestDomainSelectionService() throws Exception {
        if (!connectTestDomainSelectionServiceLocally()) return false;
        return triggerFrameworkConnectionToTestDomainSelectionService();
    }

    private void removeLocalServiceConnection() {
        if (mTestServiceConn != null) {
            mInstrumentation.getContext().unbindService(mTestServiceConn);
            mTestServiceConn = null;
            mTestService = null;
        }
    }

    // Detect and disconnect active service.
    void disconnectService() throws Exception {
        // Remove local connections
        removeLocalServiceConnection();
        mController.restoreOriginalPackage();
    }

    TestDomainSelectionService getTestService() {
        return mTestService;
    }
}
