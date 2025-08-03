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

package android.virtualdevice.cts.applaunch;

import static android.content.Context.DEVICE_ID_DEFAULT;
import static android.content.Context.DEVICE_ID_INVALID;
import static android.view.Display.DEFAULT_DISPLAY;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.Instrumentation.ActivityMonitor;
import android.app.Service;
import android.companion.virtual.VirtualDeviceManager.VirtualDevice;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.platform.test.annotations.AppModeFull;
import android.virtualdevice.cts.applaunch.AppComponents.EmptyActivity;
import android.virtualdevice.cts.applaunch.AppComponents.SecondActivity;
import android.virtualdevice.cts.applaunch.AppComponents.TestService;
import android.virtualdevice.cts.common.VirtualDeviceRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.IntConsumer;

@RunWith(AndroidJUnit4.class)
@ApiTest(apis = {"android.content.Context#updateDeviceId",
        "android.content.Context#unregisterDeviceIdChangeListener",
        "android.content.Context#registerDeviceIdChangeListener"}
)
@AppModeFull(reason = "VirtualDeviceManager cannot be accessed by instant apps")
public class DeviceAssociationTest {

    private static final long TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(3);

    @Rule
    public VirtualDeviceRule mRule = VirtualDeviceRule.createDefault();

    private final Context mContext = getInstrumentation().getContext();
    private Executor mTestExecutor;
    private VirtualDisplay mVirtualDisplay;
    private VirtualDevice mVirtualDevice;

    @Mock
    private IntConsumer mDeviceChangeListener;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mTestExecutor = mContext.getMainExecutor();
        mVirtualDevice = mRule.createManagedVirtualDevice();
        mVirtualDisplay = mRule.createManagedVirtualDisplayWithFlags(mVirtualDevice,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                        | DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED
                        | DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY);
    }

    @Test
    @ApiTest(apis = {"android.content.Context#registerDeviceIdChangeListener"})
    public void registerDeviceIdChangeListener_nullListener_throwsException() {
        assertThrows(
                NullPointerException.class,
                () -> mContext.registerDeviceIdChangeListener(mTestExecutor, null));
    }

    @Test
    @ApiTest(apis = {"android.content.Context#registerDeviceIdChangeListener"})
    public void registerDeviceIdChangeListener_nullExecutor_throwsException() {
        assertThrows(
                NullPointerException.class,
                () -> mContext.registerDeviceIdChangeListener(null, mDeviceChangeListener));
    }

    @Test
    @ApiTest(apis = {"android.content.Context#unregisterDeviceIdChangeListener"})
    public void registerDeviceIdChangeListener_registerTwice_throwsException() {
        mContext.registerDeviceIdChangeListener(mTestExecutor, mDeviceChangeListener);
        assertThrows(
                IllegalArgumentException.class,
                () -> mContext.registerDeviceIdChangeListener(
                        mTestExecutor, mDeviceChangeListener));
    }

    @Test
    @ApiTest(apis = {"android.content.Context#unregisterDeviceIdChangeListener"})
    public void unregisterDeviceChangedListener_nullListener_throwsException() {
        assertThrows(
                NullPointerException.class,
                () -> mContext.unregisterDeviceIdChangeListener(null));
    }

    @Test
    @ApiTest(apis = {"android.content.Context#unregisterDeviceIdChangeListener"})
    public void unregisterDeviceChangedListener_succeeds() {
        mContext.registerDeviceIdChangeListener(mTestExecutor, mDeviceChangeListener);
        mContext.unregisterDeviceIdChangeListener(mDeviceChangeListener);
        mContext.updateDeviceId(mVirtualDevice.getDeviceId());

        assertThat(mContext.getDeviceId()).isEqualTo(mVirtualDevice.getDeviceId());
        verifyZeroInteractions(mDeviceChangeListener);
    }

    @Test
    @ApiTest(apis = {"android.content.Context#updateDeviceId"})
    public void updateDeviceId_invalidDeviceId_shouldThrowIllegalArgumentException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> mContext.updateDeviceId(DEVICE_ID_INVALID));
    }

    @Test
    @ApiTest(apis = {"android.content.Context#updateDeviceId"})
    public void updateDeviceId_missingDeviceId_shouldThrowIllegalArgumentException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> mContext.updateDeviceId(mVirtualDevice.getDeviceId() + 1));
    }

    @Test
    @ApiTest(apis = {"android.content.Context#updateDeviceId"})
    public void updateDeviceId_defaultDeviceId() {
        mContext.updateDeviceId(DEVICE_ID_DEFAULT);
        assertThat(mContext.getDeviceId()).isEqualTo(DEVICE_ID_DEFAULT);
    }

    @Test
    @ApiTest(apis = {"android.content.Context#updateDeviceId"})
    public void updateDeviceId_validVirtualDeviceId() {
        mContext.updateDeviceId(mVirtualDevice.getDeviceId());
        assertThat(mContext.getDeviceId()).isEqualTo(mVirtualDevice.getDeviceId());
    }

    @Test
    @ApiTest(apis = {"android.content.Context#updateDeviceId"})
    public void updateDeviceId_convertToDeviceContext_overridesValue() {
        mContext.updateDeviceId(mVirtualDevice.getDeviceId());
        assertThat(mContext.getDeviceId()).isEqualTo(mVirtualDevice.getDeviceId());

        Context defaultDeviceContext = mContext.createDeviceContext(DEVICE_ID_DEFAULT);

        assertThat(defaultDeviceContext.getDeviceId()).isEqualTo(DEVICE_ID_DEFAULT);
    }

    @Test
    @ApiTest(apis = {"android.content.Context#updateDeviceId"})
    public void updateDeviceId_onDeviceContext_throwsException() {
        Context deviceContext = mContext.createDeviceContext(mVirtualDevice.getDeviceId());
        deviceContext.registerDeviceIdChangeListener(mTestExecutor, mDeviceChangeListener);

        assertThrows(
                UnsupportedOperationException.class,
                () -> deviceContext.updateDeviceId(DEVICE_ID_DEFAULT));
    }

    @Test
    @ApiTest(apis = {"android.content.Context#updateDeviceId"})
    public void updateDeviceId_notifiesListeners() {
        mContext.registerDeviceIdChangeListener(mTestExecutor, mDeviceChangeListener);
        mContext.updateDeviceId(mVirtualDevice.getDeviceId());

        assertThat(mContext.getDeviceId()).isEqualTo(mVirtualDevice.getDeviceId());
        verify(mDeviceChangeListener, timeout(TIMEOUT_MILLIS)).accept(mVirtualDevice.getDeviceId());
    }

    @Test
    @ApiTest(apis = {"android.content.Context#updateDeviceId"})
    public void updateDeviceId_sameId_doesNotNotifyListeners() {
        mContext.registerDeviceIdChangeListener(mTestExecutor, mDeviceChangeListener);
        mContext.updateDeviceId(mVirtualDevice.getDeviceId());
        verify(mDeviceChangeListener, timeout(TIMEOUT_MILLIS)).accept(mVirtualDevice.getDeviceId());

        mContext.updateDeviceId(mVirtualDevice.getDeviceId());
        verifyNoMoreInteractions(mDeviceChangeListener);
    }

    @Test
    public void activityContext_startActivityOnVirtualDevice_returnsVirtualDeviceId() {
        Activity activity = mRule.startActivityOnDisplaySync(mVirtualDisplay, EmptyActivity.class);
        assertThat(activity.getDeviceId()).isEqualTo(mVirtualDevice.getDeviceId());
    }

    @Test
    public void activityContext_startActivityOnDefaultDevice_returnsDefaultDeviceId() {
        Activity activity = mRule.startActivityOnDisplaySync(DEFAULT_DISPLAY, EmptyActivity.class);
        assertThat(activity.getDeviceId()).isEqualTo(DEVICE_ID_DEFAULT);
    }

    @Test
    public void activityContext_activityMovesDisplay_returnsDeviceIdAssociatedWithDisplay() {
        Activity activity = mRule.startActivityOnDisplaySync(mVirtualDisplay, EmptyActivity.class);
        VirtualDevice virtualDevice2 = mRule.createManagedVirtualDevice();
        VirtualDisplay virtualDisplay2 = mRule.createManagedVirtualDisplayWithFlags(virtualDevice2,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                        | DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED
                        | DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY);

        assertThat(activity.getDeviceId()).isEqualTo(mVirtualDevice.getDeviceId());

        activity.updateDisplay(virtualDisplay2.getDisplay().getDisplayId());

        assertThat(activity.getDeviceId()).isEqualTo(virtualDevice2.getDeviceId());
    }

    @Test
    public void applicationContext_lastActivityOnVirtualDevice_returnsVirtualDeviceId() {
        mRule.startActivityOnDisplaySync(DEFAULT_DISPLAY, EmptyActivity.class);
        mRule.startActivityOnDisplaySync(mVirtualDisplay, SecondActivity.class);

        assertThat(getApplicationContext().getDeviceId()).isEqualTo(mVirtualDevice.getDeviceId());
    }

    @Test
    public void applicationContext_recreateActivity_returnsDeviceIdOfActivity() {
        Activity activity = mRule.startActivityOnDisplaySync(mVirtualDisplay, EmptyActivity.class);
        ActivityMonitor monitor = new ActivityMonitor(EmptyActivity.class.getName(),
                new Instrumentation.ActivityResult(0, new Intent()), false);
        getInstrumentation().addMonitor(monitor);

        getInstrumentation().runOnMainSync(() -> activity.recreate());
        monitor.waitForActivity();
        getInstrumentation().removeMonitor(monitor);

        assertThat(getApplicationContext().getDeviceId()).isEqualTo(mVirtualDevice.getDeviceId());
    }

    @Test
    public void applicationContext_recreateFirstActivity_returnsDeviceOfLastCreatedActivity() {
        Activity activity = mRule.startActivityOnDisplaySync(DEFAULT_DISPLAY, EmptyActivity.class);
        mRule.startActivityOnDisplaySync(mVirtualDisplay, SecondActivity.class);
        ActivityMonitor monitor = new ActivityMonitor(EmptyActivity.class.getName(),
                new Instrumentation.ActivityResult(0, new Intent()), false);
        getInstrumentation().addMonitor(monitor);

        getInstrumentation().runOnMainSync(() -> activity.recreate());
        monitor.waitForActivity();
        getInstrumentation().removeMonitor(monitor);

        assertThat(getApplicationContext().getDeviceId()).isEqualTo(mVirtualDevice.getDeviceId());
    }

    @Test
    public void applicationContext_activityOnVirtualDeviceDestroyed_returnsDefault() {
        Activity activity = mRule.startActivityOnDisplaySync(mVirtualDisplay, EmptyActivity.class);
        activity.finish();

        mRule.waitAndAssertActivityRemoved(activity.getComponentName());
        assertThat(getApplicationContext().getDeviceId()).isEqualTo(DEVICE_ID_DEFAULT);
    }

    @Test
    public void applicationContext_activityOnDefaultDeviceDestroyed_returnsVirtual() {
        mRule.startActivityOnDisplaySync(mVirtualDisplay, EmptyActivity.class);
        Activity activity = mRule.startActivityOnDisplaySync(DEFAULT_DISPLAY, SecondActivity.class);

        activity.finish();

        mRule.waitAndAssertActivityRemoved(activity.getComponentName());
        assertThat(getApplicationContext().getDeviceId()).isEqualTo(mVirtualDevice.getDeviceId());
    }

    @Test
    public void applicationContext_lastActivityOnDefaultDevice_returnsDefault() {
        mRule.startActivityOnDisplaySync(mVirtualDisplay, EmptyActivity.class);
        mRule.startActivityOnDisplaySync(DEFAULT_DISPLAY, SecondActivity.class);

        assertThat(getApplicationContext().getDeviceId()).isEqualTo(DEVICE_ID_DEFAULT);
    }

    @Test
    public void application_context_noActivities_returnsDefault() {
        assertThat(getApplicationContext().getDeviceId()).isEqualTo(DEVICE_ID_DEFAULT);
    }

    @Test
    public void applicationContext_twoVirtualDevices_returnsIdOfLatest() {
        mRule.startActivityOnDisplaySync(mVirtualDisplay, EmptyActivity.class);
        Context context = getApplicationContext();
        VirtualDevice virtualDevice2 = mRule.createManagedVirtualDevice();
        VirtualDisplay virtualDisplay2 = mRule.createManagedVirtualDisplayWithFlags(virtualDevice2,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                        | DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED
                        | DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY);

        assertThat(context.getDeviceId()).isEqualTo(mVirtualDevice.getDeviceId());

        mRule.startActivityOnDisplaySync(virtualDisplay2, SecondActivity.class);
        assertThat(context.getDeviceId()).isEqualTo(virtualDevice2.getDeviceId());

        mRule.startActivityOnDisplaySync(DEFAULT_DISPLAY, SecondActivity.class);
        assertThat(context.getDeviceId()).isEqualTo(DEVICE_ID_DEFAULT);
    }

    @Test
    public void serviceContext_lastActivityOnVirtualDevice_returnsVirtualDeviceId()
            throws TimeoutException {
        Service service = TestService.startService(mContext);
        mRule.startActivityOnDisplaySync(DEFAULT_DISPLAY, EmptyActivity.class);
        mRule.startActivityOnDisplaySync(mVirtualDisplay, SecondActivity.class);

        assertThat(service.getDeviceId()).isEqualTo(mVirtualDevice.getDeviceId());
    }

    @Test
    public void serviceContext_lastActivityOnDefaultDevice_returnsDefault()
            throws TimeoutException {
        Service service = TestService.startService(mContext);
        mRule.startActivityOnDisplaySync(mVirtualDisplay, EmptyActivity.class);
        mRule.startActivityOnDisplaySync(DEFAULT_DISPLAY, SecondActivity.class);

        assertThat(service.getDeviceId()).isEqualTo(DEVICE_ID_DEFAULT);
    }

    @Test
    public void serviceContext_startServiceAfterActivity_hasDeviceIdOfTopActivity()
            throws TimeoutException {
        mRule.startActivityOnDisplaySync(DEFAULT_DISPLAY, EmptyActivity.class);
        mRule.startActivityOnDisplaySync(mVirtualDisplay, SecondActivity.class);
        Service service = TestService.startService(mContext);

        assertThat(service.getDeviceId()).isEqualTo(mVirtualDevice.getDeviceId());
    }

    @Test
    public void serviceContext_startServiceAfterActivityDeviceIsClosed_returnsDefault()
            throws TimeoutException {
        mRule.startActivityOnDisplaySync(DEFAULT_DISPLAY, EmptyActivity.class);
        mRule.startActivityOnDisplaySync(mVirtualDisplay, SecondActivity.class);
        mVirtualDevice.close();
        mRule.assertDisplayDoesNotExist(mVirtualDisplay.getDisplay().getDisplayId());
        Service service = TestService.startService(mContext);

        assertThat(service.getDeviceId()).isEqualTo(DEVICE_ID_DEFAULT);
    }

    @Test
    public void serviceContext_noActivities_hasDefaultId() throws TimeoutException {
        Service service = TestService.startService(mContext);
        assertThat(service.getDeviceId()).isEqualTo(DEVICE_ID_DEFAULT);
    }
}
