/*
 * Copyright 2024 The Android Open Source Project
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
package com.android.ondevicepersonalization.cts.e2e;

import static android.view.Display.DEFAULT_DISPLAY;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

import android.adservices.ondevicepersonalization.OnDevicePersonalizationManager;
import android.adservices.ondevicepersonalization.OnDevicePersonalizationManager.ExecuteResult;
import android.adservices.ondevicepersonalization.SurfacePackageToken;
import android.content.ComponentName;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.Looper;
import android.os.PersistableBundle;
import android.view.Display;
import android.view.SurfaceControlViewHost.SurfacePackage;
import android.view.SurfaceView;
import android.view.View;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.rules.ActivityScenarioRule;

import com.android.compatibility.common.util.ShellUtils;
import com.android.ondevicepersonalization.testing.sampleserviceapi.SampleServiceApi;
import com.android.ondevicepersonalization.testing.utils.DeviceSupportHelper;
import com.android.ondevicepersonalization.testing.utils.ResultReceiver;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;


import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

/**
 * CTS Test cases for OnDevicePersonalizationManager#requestSurfacePackage.
 */
@RunWith(Parameterized.class)
public class RequestSurfacePackageTests {

    @Parameterized.Parameter(0)
    public boolean mIsSipFeatureEnabled;

    private static final String SERVICE_PACKAGE =
            "com.android.ondevicepersonalization.testing.sampleservice";
    private static final String SERVICE_CLASS =
            "com.android.ondevicepersonalization.testing.sampleservice.SampleService";

    private final Context mContext = ApplicationProvider.getApplicationContext();

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(
                new Object[][] {
                        {true}, {false}
                }
        );
    }

    @Before
    public void setUp() {
        // Skip the test if it runs on unsupported platforms.
        Assume.assumeTrue(DeviceSupportHelper.isDeviceSupported());

        ShellUtils.runShellCommand(
                "device_config put on_device_personalization "
                        + "shared_isolated_process_feature_enabled "
                        + mIsSipFeatureEnabled);
        ShellUtils.runShellCommand(
                "device_config put on_device_personalization "
                        + "debug.validate_rendering_config_keys "
                        + false);
        ShellUtils.runShellCommand(
                "device_config put on_device_personalization "
                        + "isolated_service_allow_list "
                        + "com.android.ondevicepersonalization.testing.sampleservice,"
                        + "com.example.odptargetingapp2");
    }

    @After
    public void reset() {
        ShellUtils.runShellCommand(
                "device_config put on_device_personalization "
                        + "isolated_service_allow_list "
                        + "null");

        ShellUtils.runShellCommand(
                "am force-stop com.google.android.ondevicepersonalization.services");
        ShellUtils.runShellCommand(
                "am force-stop com.android.ondevicepersonalization.services");

    }

    @Rule
    public final ActivityScenarioRule<TestActivity> mActivityScenarioRule =
            new ActivityScenarioRule<>(TestActivity.class);

    @Test
    public void testRequestSurfacePackage() throws InterruptedException {
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        SurfacePackageToken token = runExecute(manager);
        var receiver = new ResultReceiver<SurfacePackage>();
        SurfaceView surfaceView = createSurfaceView();
        manager.requestSurfacePackage(
                token,
                surfaceView.getHostToken(),
                getDisplayId(),
                surfaceView.getWidth(),
                surfaceView.getHeight(),
                Executors.newSingleThreadExecutor(),
                receiver);
        SurfacePackage surfacePackage = receiver.getResult();
        assertNotNull(surfacePackage);
        CountDownLatch latch = new CountDownLatch(1);
        new Handler(Looper.getMainLooper()).post(
                () -> {
                    surfaceView.setChildSurfacePackage(surfacePackage);
                    surfaceView.setZOrderOnTop(true);
                    surfaceView.setVisibility(View.VISIBLE);
                    latch.countDown();
                });
        latch.await();
    }

    @Test
    public void testRequestSurfacePackageThrowsIfSurfacePackageTokenMissing()
            throws InterruptedException {
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        SurfaceView surfaceView = createSurfaceView();
        assertThrows(
                NullPointerException.class,
                () -> manager.requestSurfacePackage(
                        null,
                        surfaceView.getHostToken(),
                        getDisplayId(),
                        surfaceView.getWidth(),
                        surfaceView.getHeight(),
                        Executors.newSingleThreadExecutor(),
                        new ResultReceiver<SurfacePackage>()));
    }

    @Test
    public void testRequestSurfacePackageThrowsIfSurfaceViewHostTokenMissing()
            throws InterruptedException {
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        SurfacePackageToken token = runExecute(manager);
        SurfaceView surfaceView = createSurfaceView();
        assertThrows(
                NullPointerException.class,
                () -> manager.requestSurfacePackage(
                        token,
                        null,
                        getDisplayId(),
                        surfaceView.getWidth(),
                        surfaceView.getHeight(),
                        Executors.newSingleThreadExecutor(),
                        new ResultReceiver<SurfacePackage>()));
    }

    @Test
    public void testRequestSurfacePackageThrowsIfInvalidDisplayId()
            throws InterruptedException {
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        SurfacePackageToken token = runExecute(manager);
        SurfaceView surfaceView = createSurfaceView();
        assertThrows(
                IllegalArgumentException.class,
                () -> manager.requestSurfacePackage(
                        token,
                        surfaceView.getHostToken(),
                        -1,
                        surfaceView.getWidth(),
                        surfaceView.getHeight(),
                        Executors.newSingleThreadExecutor(),
                        new ResultReceiver<SurfacePackage>()));
    }

    @Test
    public void testRequestSurfacePackageThrowsIfInvalidWidth()
            throws InterruptedException {
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        SurfacePackageToken token = runExecute(manager);
        SurfaceView surfaceView = createSurfaceView();
        assertThrows(
                IllegalArgumentException.class,
                () -> manager.requestSurfacePackage(
                        token,
                        surfaceView.getHostToken(),
                        getDisplayId(),
                        0,
                        surfaceView.getHeight(),
                        Executors.newSingleThreadExecutor(),
                        new ResultReceiver<SurfacePackage>()));
    }

    @Test
    public void testRequestSurfacePackageThrowsIfInvalidHeight()
            throws InterruptedException {
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        SurfacePackageToken token = runExecute(manager);
        SurfaceView surfaceView = createSurfaceView();
        assertThrows(
                IllegalArgumentException.class,
                () -> manager.requestSurfacePackage(
                        token,
                        surfaceView.getHostToken(),
                        getDisplayId(),
                        surfaceView.getWidth(),
                        0,
                        Executors.newSingleThreadExecutor(),
                        new ResultReceiver<SurfacePackage>()));
    }

    @Test
    public void testRequestSurfacePackageThrowsIfExecutorMissing()
            throws InterruptedException {
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        SurfacePackageToken token = runExecute(manager);
        SurfaceView surfaceView = createSurfaceView();
        assertThrows(
                NullPointerException.class,
                () -> manager.requestSurfacePackage(
                        token,
                        surfaceView.getHostToken(),
                        getDisplayId(),
                        surfaceView.getWidth(),
                        surfaceView.getHeight(),
                        null,
                        new ResultReceiver<SurfacePackage>()));
    }

    @Test
    public void testRequestSurfacePackageThrowsIfOutcomeReceiverMissing()
            throws InterruptedException {
        OnDevicePersonalizationManager manager =
                mContext.getSystemService(OnDevicePersonalizationManager.class);
        SurfacePackageToken token = runExecute(manager);
        SurfaceView surfaceView = createSurfaceView();
        assertThrows(
                NullPointerException.class,
                () -> manager.requestSurfacePackage(
                        token,
                        surfaceView.getHostToken(),
                        getDisplayId(),
                        surfaceView.getWidth(),
                        surfaceView.getHeight(),
                        Executors.newSingleThreadExecutor(),
                        null));
    }

    int getDisplayId() {
        final DisplayManager dm = mContext.getSystemService(DisplayManager.class);
        final Display primaryDisplay = dm.getDisplay(DEFAULT_DISPLAY);
        final Context windowContext = mContext.createDisplayContext(primaryDisplay);
        return windowContext.getDisplay().getDisplayId();
    }

    SurfaceView createSurfaceView() throws InterruptedException {
        ArrayBlockingQueue<SurfaceView> viewQueue = new ArrayBlockingQueue<>(1);
        mActivityScenarioRule.getScenario().onActivity(
                a -> viewQueue.add(a.findViewById(R.id.test_surface_view)));
        return viewQueue.take();
    }

    private SurfacePackageToken runExecute(
            OnDevicePersonalizationManager manager)
            throws InterruptedException {
        PersistableBundle params = new PersistableBundle();
        params.putString(SampleServiceApi.KEY_OPCODE, SampleServiceApi.OPCODE_RENDER_AND_LOG);
        params.putString(SampleServiceApi.KEY_RENDERING_CONFIG_IDS, "id1");
        var receiver = new ResultReceiver<ExecuteResult>();
        manager.execute(
                new ComponentName(SERVICE_PACKAGE, SERVICE_CLASS),
                params,
                Executors.newSingleThreadExecutor(),
                receiver);
        return receiver.getResult().getSurfacePackageToken();
    }
}
