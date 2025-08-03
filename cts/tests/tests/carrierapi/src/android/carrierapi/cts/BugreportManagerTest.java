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

package android.carrierapi.cts;

import static com.android.compatibility.common.util.SystemUtil.runShellCommand;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.fail;

import android.content.pm.PackageManager;
import android.os.BugreportManager;
import android.os.BugreportManager.BugreportCallback;
import android.os.BugreportParams;
import android.os.FileUtils;
import android.os.ParcelFileDescriptor;
import android.platform.test.annotations.SystemUserOnly;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.Direction;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.PollingCheck;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.concurrent.TimeUnit;

/**
 * Unit tests for {@link BugreportManager}'s carrier functionality, specifically "connectivity"
 * bugreports.
 *
 * <p>Structure is largely adapted from
 * frameworks/base/core/tests/bugreports/.../BugreportManagerTest.java.
 *
 * <p>Test using `atest CtsCarrierApiTestCases:BugreportManagerTest` or `make cts -j64 &&
 * cts-tradefed run cts -m CtsCarrierApiTestCases --test
 * android.carrierapi.cts.BugreportManagerTest`
 *
 * <p>TODO(b/211774553) consider enforcing BR content. Will likely have to be a host-side test for
 * performance reasons.
 */
@SystemUserOnly(reason = "BugreportManager requires calls to originate from the primary user")
@RunWith(AndroidJUnit4.class)
public class BugreportManagerTest extends BaseCarrierApiTest {
    private static final String TAG = "BugreportManagerTest";

    // See BugreportManagerServiceImpl#BUGREPORT_SERVICE.
    private static final String BUGREPORT_SERVICE = "bugreportd";

    private static final long BUGREPORT_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(10);
    private static final long UIAUTOMATOR_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(10);
    private static final long ONEWAY_CALLBACK_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(5);
    // This value is defined in dumpstate.cpp:TELEPHONY_REPORT_USER_CONSENT_TIMEOUT_MS. Because the
    // consent dialog is so large and important, the user *must* be given at least 2 minutes to read
    // it before it times out.
    private static final long MINIMUM_CONSENT_TIMEOUT_MILLIS = TimeUnit.MINUTES.toMillis(2);

    private static final BySelector CONSENT_DIALOG_TITLE_SELECTOR = By.res("android", "alertTitle");

    @Rule public TestName name = new TestName();

    private BugreportManager mBugreportManager;
    private File mBugreportFile;
    private ParcelFileDescriptor mBugreportFd;
    private File mScreenshotFile;
    private ParcelFileDescriptor mScreenshotFd;

    @Before
    public void setUp() throws Exception {
        mBugreportManager = getContext().getSystemService(BugreportManager.class);

        killCurrentBugreportIfRunning();
        mBugreportFile = createTempFile("bugreport_" + name.getMethodName(), ".zip");
        mBugreportFd = parcelFd(mBugreportFile);
        // Should never be written for anything a carrier app can trigger; several tests assert that
        // this file has no content.
        mScreenshotFile = createTempFile("screenshot_" + name.getMethodName(), ".png");
        mScreenshotFd = parcelFd(mScreenshotFile);
    }

    @After
    public void tearDown() throws Exception {
        if (!werePreconditionsSatisfied()) return;

        FileUtils.closeQuietly(mBugreportFd);
        FileUtils.closeQuietly(mScreenshotFd);
        killCurrentBugreportIfRunning();
    }

    @Test
    @CddTest(requirement = "9.8.10/C-1-1")
    public void startConnectivityBugreport() throws Exception {
        BugreportCallbackImpl callback = new BugreportCallbackImpl();

        assertThat(callback.hasEarlyReportFinished()).isFalse();
        mBugreportManager.startConnectivityBugreport(mBugreportFd, Runnable::run, callback);
        setConsentDialogReply(ConsentReply.ALLOW);
        waitUntilDoneOrTimeout(callback);

        assertThat(callback.isSuccess()).isTrue();
        assertThat(callback.hasEarlyReportFinished()).isTrue();
        assertThat(callback.hasReceivedProgress()).isTrue();
        assertThat(mBugreportFile.length()).isGreaterThan(0L);
        assertFdIsClosed(mBugreportFd);
    }

    @Test
    @CddTest(requirement = "9.8.10/C-1-3")
    public void startConnectivityBugreport_consentDenied() throws Exception {
        BugreportCallbackImpl callback = new BugreportCallbackImpl();

        mBugreportManager.startConnectivityBugreport(mBugreportFd, Runnable::run, callback);
        setConsentDialogReply(ConsentReply.DENY);
        waitUntilDoneOrTimeout(callback);

        assertThat(callback.getErrorCode())
                .isEqualTo(BugreportCallback.BUGREPORT_ERROR_USER_DENIED_CONSENT);
        assertThat(callback.hasReceivedProgress()).isTrue();
        assertThat(mBugreportFile.length()).isEqualTo(0L);
        assertFdIsClosed(mBugreportFd);
    }

    @Test
    @CddTest(requirement = "9.8.10/C-1-3")
    public void startConnectivityBugreport_consentTimeout() throws Exception {
        BugreportCallbackImpl callback = new BugreportCallbackImpl();
        long startTimeMillis = System.currentTimeMillis();

        mBugreportManager.startConnectivityBugreport(mBugreportFd, Runnable::run, callback);
        setConsentDialogReply(ConsentReply.NONE_TIMEOUT);
        waitUntilDoneOrTimeout(callback);

        assertThat(callback.getErrorCode())
                .isEqualTo(BugreportCallback.BUGREPORT_ERROR_USER_CONSENT_TIMED_OUT);
        assertThat(callback.hasReceivedProgress()).isTrue();
        assertThat(mBugreportFile.length()).isEqualTo(0L);
        assertFdIsClosed(mBugreportFd);
        // Ensure the dialog was displaying long enough.
        assertThat(System.currentTimeMillis() - startTimeMillis)
                .isAtLeast(MINIMUM_CONSENT_TIMEOUT_MILLIS);
        // The dialog may still be displaying, dismiss it if so.
        dismissConsentDialogIfPresent();
    }

    @Test
    public void simultaneousBugreportsNotAllowed() throws Exception {
        BugreportCallbackImpl callback1 = new BugreportCallbackImpl();
        BugreportCallbackImpl callback2 = new BugreportCallbackImpl();
        File bugreportFile2 = createTempFile("bugreport_2_" + name.getMethodName(), ".zip");
        ParcelFileDescriptor bugreportFd2 = parcelFd(bugreportFile2);

        assertThat(callback1.hasEarlyReportFinished()).isFalse();
        // Start the first report, but don't accept the consent dialog or wait for the callback to
        // complete yet.
        mBugreportManager.startConnectivityBugreport(mBugreportFd, Runnable::run, callback1);

        // Attempting to start a second report immediately gets us a concurrency error.
        mBugreportManager.startConnectivityBugreport(bugreportFd2, Runnable::run, callback2);
        // Since IDumpstateListener#onError is oneway, it's not guaranteed that binder has delivered
        // the callback to us yet, even though BugreportManagerServiceImpl sends it before returning
        // from #startBugreport.
        PollingCheck.check(
                "No terminal callback received for the second bugreport",
                ONEWAY_CALLBACK_TIMEOUT_MILLIS,
                callback2::isDone);
        assertThat(callback2.getErrorCode())
                .isEqualTo(BugreportCallback.BUGREPORT_ERROR_ANOTHER_REPORT_IN_PROGRESS);

        // Now wait for the first report to complete normally.
        setConsentDialogReply(ConsentReply.ALLOW);
        waitUntilDoneOrTimeout(callback1);

        assertThat(callback1.isSuccess()).isTrue();
        assertThat(callback1.hasEarlyReportFinished()).isTrue();
        assertThat(callback1.hasReceivedProgress()).isTrue();
        assertThat(mBugreportFile.length()).isGreaterThan(0L);
        assertFdIsClosed(mBugreportFd);
        // The second report never got any details filled in.
        assertThat(callback2.hasReceivedProgress()).isFalse();
        assertThat(bugreportFile2.length()).isEqualTo(0L);
        assertFdIsClosed(bugreportFd2);
    }

    @Test
    @CddTest(requirement = "9.8.10/C-1-3")
    public void cancelBugreport() throws Exception {
        BugreportCallbackImpl callback = new BugreportCallbackImpl();

        // Start the report, but don't accept the consent dialog or wait for the callback to
        // complete yet.
        mBugreportManager.startConnectivityBugreport(mBugreportFd, Runnable::run, callback);

        assertThat(callback.isDone()).isFalse();

        // Cancel and wait for the final result.
        mBugreportManager.cancelBugreport();
        waitUntilDoneOrTimeout(callback);

        assertThat(callback.getErrorCode()).isEqualTo(BugreportCallback.BUGREPORT_ERROR_RUNTIME);
        assertThat(mBugreportFile.length()).isEqualTo(0L);
        assertFdIsClosed(mBugreportFd);
    }

    @Test
    @CddTest(requirement = "9.8.10/C-1-1")
    public void startBugreport_connectivityBugreport() throws Exception {
        BugreportCallbackImpl callback = new BugreportCallbackImpl();

        assertThat(callback.hasEarlyReportFinished()).isFalse();
        // Carrier apps that compile with the system SDK have visibility to use this API, so we need
        // to enforce that the additional parameters can't be abused to e.g. surreptitiously capture
        // screenshots.
        mBugreportManager.startBugreport(
                mBugreportFd,
                mScreenshotFd,
                new BugreportParams(BugreportParams.BUGREPORT_MODE_TELEPHONY),
                Runnable::run,
                callback);
        setConsentDialogReply(ConsentReply.ALLOW);
        waitUntilDoneOrTimeout(callback);

        assertThat(callback.isSuccess()).isTrue();
        assertThat(callback.hasEarlyReportFinished()).isTrue();
        assertThat(callback.hasReceivedProgress()).isTrue();
        assertThat(mBugreportFile.length()).isGreaterThan(0L);
        assertFdIsClosed(mBugreportFd);
        // Screenshots are never captured for connectivity bugreports, even if an FD is passed in.
        assertThat(mScreenshotFile.length()).isEqualTo(0L);
        assertFdIsClosed(mScreenshotFd);
    }

    @Test
    public void startBugreport_fullBugreport() throws Exception {
        assertSecurityExceptionThrownForMode(BugreportParams.BUGREPORT_MODE_FULL);
    }

    @Test
    public void startBugreport_interactiveBugreport() throws Exception {
        assertSecurityExceptionThrownForMode(BugreportParams.BUGREPORT_MODE_INTERACTIVE);
    }

    @Test
    public void startBugreport_remoteBugreport() throws Exception {
        assertSecurityExceptionThrownForMode(BugreportParams.BUGREPORT_MODE_REMOTE);
    }

    @Test
    public void startBugreport_wearBugreport() throws Exception {
        assertSecurityExceptionThrownForMode(BugreportParams.BUGREPORT_MODE_WEAR);
    }

    @Test
    public void startBugreport_wifiBugreport() throws Exception {
        assertSecurityExceptionThrownForMode(BugreportParams.BUGREPORT_MODE_WIFI);
    }

    @Test
    public void startBugreport_defaultBugreport() throws Exception {
        // BUGREPORT_MODE_DEFAULT (6) is defined by the AIDL, but isn't accepted by
        // BugreportManagerServiceImpl or exposed in BugreportParams.
        assertExceptionThrownForMode(6, IllegalArgumentException.class);
    }

    @Test
    public void startBugreport_negativeMode() throws Exception {
        assertExceptionThrownForMode(-1, IllegalArgumentException.class);
    }

    @Test
    public void startBugreport_invalidMode() throws Exception {
        assertExceptionThrownForMode(BugreportParams.BUGREPORT_MODE_MAX_VALUE + 1,
                IllegalArgumentException.class);
    }

    /* Implementatiion of {@link BugreportCallback} that offers wrappers around execution result */
    private static final class BugreportCallbackImpl extends BugreportCallback {
        private int mErrorCode = -1;
        private boolean mSuccess = false;
        private boolean mReceivedProgress = false;
        private boolean mEarlyReportFinished = false;
        private final Object mLock = new Object();

        @Override
        public synchronized void onProgress(float progress) {
            mReceivedProgress = true;
        }

        @Override
        public synchronized void onError(int errorCode) {
            Log.d(TAG, "Bugreport errored");
            mErrorCode = errorCode;
        }

        @Override
        public synchronized void onFinished() {
            Log.d(TAG, "Bugreport finished");
            mSuccess = true;
        }

        @Override
        public synchronized void onEarlyReportFinished() {
            mEarlyReportFinished = true;
        }

        /* Indicates completion; and ended up with a success or error. */
        public synchronized boolean isDone() {
            return (mErrorCode != -1) || mSuccess;
        }

        public synchronized int getErrorCode() {
            return mErrorCode;
        }

        public synchronized boolean isSuccess() {
            return mSuccess;
        }

        public synchronized boolean hasReceivedProgress() {
            return mReceivedProgress;
        }

        public synchronized boolean hasEarlyReportFinished() {
            return mEarlyReportFinished;
        }
    }

    /**
     * Kills the current bugreport if one is in progress to prevent failing test cases from
     * cascading into other cases and causing flakes.
     */
    private static void killCurrentBugreportIfRunning() throws Exception {
        runShellCommand("setprop ctl.stop " + BUGREPORT_SERVICE);
    }

    /** Allow/deny the consent dialog to sharing bugreport data, or just check existence. */
    private enum ConsentReply {
        // Touch the positive button.
        ALLOW,
        // Touch the negative button.
        DENY,
        // Just verify that the dialog has appeared, but make no touches.
        NONE_TIMEOUT,
    }

    private boolean isWear() {
        return getContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH);
    }

    private BySelector getSelectorForConsentDialog() {
        if (isWear()) {
            return By.pkg(getContext().getPackageManager().getPermissionControllerPackageName());
        }
        return CONSENT_DIALOG_TITLE_SELECTOR;
    }

    private void setConsentDialogReply(ConsentReply consentReply) throws Exception {
        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

        // No need to wake + dismiss keyguard here; CTS respects our DISABLE_KEYGUARD permission.
        if (!device.wait(
                Until.hasObject(getSelectorForConsentDialog()), UIAUTOMATOR_TIMEOUT_MILLIS)) {
            fail("The consent dialog can't be found");
        }

        final BySelector replySelector;
        switch (consentReply) {
            case ALLOW:
                Log.d(TAG, "Allow the consent dialog");
                replySelector = By.res("android", "button1");
                break;
            case DENY:
                Log.d(TAG, "Deny the consent dialog");
                replySelector = By.res("android", "button2");
                break;
            case NONE_TIMEOUT:
            default:
                // Not making a choice, just leave the dialog up now that we know it exists. It will
                // eventually time out, but we don't wait for that here.
                return;
        }

        UiObject2 replyButton;
        UiObject2 scrollable =
                device.findObject(By.res("android:id/scrollView").scrollable(true));
        while ((replyButton = device.findObject(replySelector)) == null) {
            // Need to scroll the screen to get to the buttons on some form factors
            // (e.g. on a watch).
            scrollable.scroll(Direction.DOWN, 100);
        }

        assertWithMessage("The button of consent dialog is not found")
                .that(replyButton)
                .isNotNull();
        replyButton.click();

        assertThat(
                        device.wait(
                                Until.gone(getSelectorForConsentDialog()),
                                UIAUTOMATOR_TIMEOUT_MILLIS))
                .isTrue();
    }

    private void dismissConsentDialogIfPresent() throws Exception {
        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

        if (!device.hasObject(getSelectorForConsentDialog())) {
            return;
        }

        Log.d(
                TAG,
                "Consent dialog still present on the screen even though report finished,"
                        + " dismissing it");
        device.pressBack();
        assertThat(
                        device.wait(
                                Until.gone(getSelectorForConsentDialog()),
                                UIAUTOMATOR_TIMEOUT_MILLIS))
                .isTrue();
    }

    private static void waitUntilDoneOrTimeout(BugreportCallbackImpl callback) throws Exception {
        long startTimeMillis = System.currentTimeMillis();
        while (!callback.isDone()) {
            Thread.sleep(1000);
            if (System.currentTimeMillis() - startTimeMillis >= BUGREPORT_TIMEOUT_MILLIS) {
                Log.w(TAG, "Timed out waiting for bugreport completion");
                break;
            }
            Log.d(TAG, "Waited " + (System.currentTimeMillis() - startTimeMillis + "ms"));
        }
    }

    private void assertSecurityExceptionThrownForMode(int mode) {
        assertExceptionThrownForMode(mode, SecurityException.class);
    }

    private <T extends Throwable> void assertExceptionThrownForMode(
            int mode, Class<T> exceptionType) {
        BugreportCallbackImpl callback = new BugreportCallbackImpl();
        try {
            mBugreportManager.startBugreport(
                    mBugreportFd,
                    mScreenshotFd,
                    new BugreportParams(mode),
                    Runnable::run,
                    callback);
            fail("BugreportMode " + mode + " should cause " + exceptionType.getSimpleName());
        } catch (Throwable thrown) {
            if (!exceptionType.isInstance(thrown)) {
                throw thrown;
            }
        }

        assertThat(callback.isDone()).isFalse();
        assertThat(callback.hasReceivedProgress()).isFalse();
        assertThat(mBugreportFile.length()).isEqualTo(0L);
        assertFdIsClosed(mBugreportFd);
        assertThat(mScreenshotFile.length()).isEqualTo(0L);
        assertFdIsClosed(mScreenshotFd);
    }

    private static File createTempFile(String prefix, String extension) throws Exception {
        File f = File.createTempFile(prefix, extension);
        f.setReadable(true, true);
        f.setWritable(true, true);
        f.deleteOnExit();
        return f;
    }

    private static ParcelFileDescriptor parcelFd(File file) throws Exception {
        return ParcelFileDescriptor.open(
                file, ParcelFileDescriptor.MODE_WRITE_ONLY | ParcelFileDescriptor.MODE_APPEND);
    }

    private static void assertFdIsClosed(ParcelFileDescriptor pfd) {
        try {
            int fd = pfd.getFd();
            fail("Expected ParcelFileDescriptor argument to be closed, but got: " + fd);
        } catch (IllegalStateException expected) {
        }
    }
}
