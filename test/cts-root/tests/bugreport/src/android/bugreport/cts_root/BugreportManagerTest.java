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

package android.bugreport.cts_root;

import static com.android.compatibility.common.util.SystemUtil.runShellCommand;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.fail;

import android.content.Context;
import android.os.BugreportManager;
import android.os.BugreportManager.BugreportCallback;
import android.os.BugreportParams;
import android.os.ParcelFileDescriptor;

import androidx.annotation.NonNull;
import androidx.test.InstrumentationRegistry;
import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

import java.io.File;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Device-side tests for Bugreport Manager API.
 *
 * <p>These tests require root to allowlist the test package to use the BugreportManager APIs.
 */
@RunWith(AndroidJUnit4.class)
public class BugreportManagerTest {

    private Context mContext;
    private BugreportManager mBugreportManager;

    @Rule
    public TestName name = new TestName();

    private static final long UIAUTOMATOR_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(10);

    @Before
    public void setup() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mBugreportManager = mContext.getSystemService(BugreportManager.class);
        // Kill current bugreport, so that it does not interfere with future bugreports.
        runShellCommand("setprop ctl.stop bugreportd");
    }

    @After
    public void tearDown() {
        // Kill current bugreport, so that it does not interfere with future bugreports.
        runShellCommand("setprop ctl.stop bugreportd");
    }

    @LargeTest
    @Test
    public void testRetrieveBugreportConsentGranted() throws Exception {
        File bugreportFile = createTempFile("bugreport_" + name.getMethodName(), ".zip");
        File startBugreportFile = createTempFile("startbugreport", ".zip");
        CountDownLatch latch = new CountDownLatch(1);
        BugreportCallbackImpl callback = new BugreportCallbackImpl(latch);
        mBugreportManager.startBugreport(parcelFd(startBugreportFile), null,
                new BugreportParams(
                        BugreportParams.BUGREPORT_MODE_INTERACTIVE,
                        BugreportParams.BUGREPORT_FLAG_DEFER_CONSENT),
                mContext.getMainExecutor(), callback);
        latch.await(4, TimeUnit.MINUTES);
        assertThat(callback.isSuccess()).isTrue();
        // No data should be passed to the FD used to call startBugreport.
        assertThat(startBugreportFile.length()).isEqualTo(0);
        String bugreportFileLocation = callback.getBugreportFile();
        waitForDumpstateServiceToStop();



        // Trying to retrieve an unknown bugreport should fail
        latch = new CountDownLatch(1);
        callback = new BugreportCallbackImpl(latch);
        File bugreportFile2 = createTempFile("bugreport2_" + name.getMethodName(), ".zip");
        mBugreportManager.retrieveBugreport(
                "unknown/file.zip", parcelFd(bugreportFile2),
                mContext.getMainExecutor(), callback);
        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(callback.getErrorCode()).isEqualTo(
                BugreportCallback.BUGREPORT_ERROR_NO_BUGREPORT_TO_RETRIEVE);

        // A bugreport was previously generated for this caller. When the consent dialog is invoked
        // and accepted, the bugreport files should be passed to the calling package.
        ParcelFileDescriptor bugreportFd = parcelFd(bugreportFile);
        assertThat(bugreportFd).isNotNull();
        latch = new CountDownLatch(1);
        mBugreportManager.retrieveBugreport(bugreportFileLocation, bugreportFd,
                mContext.getMainExecutor(), new BugreportCallbackImpl(latch));
        shareConsentDialog(ConsentReply.ALLOW);
        assertThat(latch.await(1, TimeUnit.MINUTES)).isTrue();
        assertThat(bugreportFile.length()).isGreaterThan(0);
    }


    @LargeTest
    @Test
    public void testRetrieveBugreportConsentDenied() throws Exception {
        File bugreportFile = createTempFile("bugreport_" + name.getMethodName(), ".zip");

        // User denies consent, therefore no data should be passed back to the bugreport file.
        CountDownLatch latch = new CountDownLatch(1);
        BugreportCallbackImpl callback = new BugreportCallbackImpl(latch);
        mBugreportManager.startBugreport(parcelFd(new File("/dev/null")),
                null, new BugreportParams(BugreportParams.BUGREPORT_MODE_INTERACTIVE,
                BugreportParams.BUGREPORT_FLAG_DEFER_CONSENT),
                mContext.getMainExecutor(), callback);
        latch.await(4, TimeUnit.MINUTES);
        assertThat(callback.isSuccess()).isTrue();
        String bugreportFileLocation = callback.getBugreportFile();
        waitForDumpstateServiceToStop();

        latch = new CountDownLatch(1);
        ParcelFileDescriptor bugreportFd = parcelFd(bugreportFile);
        assertThat(bugreportFd).isNotNull();
        mBugreportManager.retrieveBugreport(
                bugreportFileLocation,
                bugreportFd,
                mContext.getMainExecutor(),
                callback);
        shareConsentDialog(ConsentReply.DENY);
        latch.await(1, TimeUnit.MINUTES);
        assertThat(callback.getErrorCode()).isEqualTo(
                BugreportCallback.BUGREPORT_ERROR_USER_DENIED_CONSENT);
        assertThat(bugreportFile.length()).isEqualTo(0);

        // Since consent has already been denied, this call should fail because consent cannot
        // be requested twice for the same bugreport.
        latch = new CountDownLatch(1);
        callback = new BugreportCallbackImpl(latch);
        mBugreportManager.retrieveBugreport(bugreportFileLocation, parcelFd(bugreportFile),
                mContext.getMainExecutor(), callback);
        latch.await(1, TimeUnit.MINUTES);
        assertThat(callback.getErrorCode()).isEqualTo(
                BugreportCallback.BUGREPORT_ERROR_NO_BUGREPORT_TO_RETRIEVE);
    }

    private ParcelFileDescriptor parcelFd(File file) throws Exception {
        return ParcelFileDescriptor.open(file,
            ParcelFileDescriptor.MODE_WRITE_ONLY | ParcelFileDescriptor.MODE_APPEND);
    }

    private static File createTempFile(String prefix, String extension) throws Exception {
        final File f = File.createTempFile(prefix, extension);
        f.setReadable(true, true);
        f.setWritable(true, true);

        f.deleteOnExit();
        return f;
    }

    private static final class BugreportCallbackImpl extends BugreportCallback {
        private int mErrorCode = -1;
        private boolean mSuccess = false;
        private String mBugreportFile;
        private final Object mLock = new Object();

        private final CountDownLatch mLatch;

        BugreportCallbackImpl(CountDownLatch latch) {
            mLatch = latch;
        }

        @Override
        public void onError(int errorCode) {
            synchronized (mLock) {
                mErrorCode = errorCode;
                mLatch.countDown();
            }
        }

        @Override
        public void onFinished(String bugreportFile) {
            synchronized (mLock) {
                mBugreportFile = bugreportFile;
                mLatch.countDown();
                mSuccess =  true;
            }
        }

        @Override
        public void onFinished() {
            synchronized (mLock) {
                mLatch.countDown();
                mSuccess = true;
            }
        }

        public int getErrorCode() {
            synchronized (mLock) {
                return mErrorCode;
            }
        }

        public boolean isSuccess() {
            synchronized (mLock) {
                return mSuccess;
            }
        }

        public String getBugreportFile() {
            synchronized (mLock) {
                return mBugreportFile;
            }
        }
    }

    private enum ConsentReply {
        ALLOW,
        DENY,
        TIMEOUT
    }

    /*
     * Ensure the consent dialog is shown and take action according to <code>consentReply<code/>.
     * It will fail if the dialog is not shown when <code>ignoreNotFound<code/> is false.
     */
    private void shareConsentDialog(@NonNull ConsentReply consentReply) throws Exception {
        final UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

        // Unlock before finding/clicking an object.
        device.wakeUp();
        device.executeShellCommand("wm dismiss-keyguard");

        final BySelector consentTitleObj = By.res("android", "alertTitle");
        if (!device.wait(Until.hasObject(consentTitleObj), UIAUTOMATOR_TIMEOUT_MS)) {
            fail("The consent dialog is not found");
        }
        if (consentReply.equals(ConsentReply.TIMEOUT)) {
            return;
        }
        final BySelector selector;
        if (consentReply.equals(ConsentReply.ALLOW)) {
            selector = By.res("android", "button1");
        } else { // ConsentReply.DENY
            selector = By.res("android", "button2");
        }
        final UiObject2 btnObj = device.findObject(selector);
        assertThat(btnObj).isNotNull();
        btnObj.click();

        assertThat(device.wait(Until.gone(consentTitleObj), UIAUTOMATOR_TIMEOUT_MS)).isTrue();
    }


    /** Waits for the dumpstate service to stop, for up to 5 seconds. */
    private void waitForDumpstateServiceToStop() throws Exception {
        int pollingIntervalMillis = 100;
        int numPolls = 50;
        Method method = Class.forName("android.os.ServiceManager").getMethod(
                "getService", String.class);
        while (numPolls-- > 0) {
            // If getService() returns null, the service has stopped.
            if (method.invoke(null, "dumpstate") == null) {
                return;
            }
            Thread.sleep(pollingIntervalMillis);
        }
        fail("Dumpstate did not stop within 5 seconds");
    }
}
