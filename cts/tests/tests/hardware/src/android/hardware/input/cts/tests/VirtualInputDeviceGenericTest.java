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

package android.hardware.input.cts.tests;

import static android.Manifest.permission.CREATE_VIRTUAL_DEVICE;
import static android.Manifest.permission.INJECT_EVENTS;
import static android.view.Display.DEFAULT_DISPLAY;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.companion.virtual.VirtualDeviceManager.VirtualDevice;
import android.companion.virtual.flags.Flags;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.hardware.input.InputManager;
import android.hardware.input.cts.virtualcreators.VirtualInputDeviceCreator;
import android.hardware.input.cts.virtualcreators.VirtualInputDeviceCreator.InputDeviceHolder;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import android.virtualdevice.cts.common.VirtualDeviceRule;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@SmallTest
@RunWith(JUnitParamsRunner.class)
public class VirtualInputDeviceGenericTest {

    private static final String DEVICE_NAME = "CtsVirtualGenericTestDevice";

    @Rule
    public final VirtualDeviceRule mRule = VirtualDeviceRule.createDefault();

    private VirtualDevice mVirtualDevice;
    private DisplayManager mDisplayManager;
    private InputManager mInputManager;

    public interface VirtualInputDeviceFactory<T extends Closeable> {
        InputDeviceHolder<T> create(
                VirtualDevice virtualDevice, String name, Display display);
    }

    @Before
    public void setUp() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mDisplayManager = context.getSystemService(DisplayManager.class);
        mInputManager = context.getSystemService(InputManager.class);
        mVirtualDevice = mRule.createManagedVirtualDevice();
    }

    private List<VirtualInputDeviceFactory> allInputDevices() {
        List<VirtualInputDeviceFactory> deviceFactories = new ArrayList<>(Arrays.asList(
                VirtualInputDeviceCreator::createAndPrepareDpad,
                VirtualInputDeviceCreator::createAndPrepareKeyboard,
                VirtualInputDeviceCreator::createAndPrepareMouse,
                VirtualInputDeviceCreator::createAndPrepareTouchscreen,
                VirtualInputDeviceCreator::createAndPrepareNavigationTouchpad
        ));
        if (Flags.virtualStylus()) {
            deviceFactories.add(VirtualInputDeviceCreator::createAndPrepareStylus);
        }
        return deviceFactories;
    }

    @Parameters(method = "allInputDevices")
    @Test
    public void close_multipleCallsSucceed(VirtualInputDeviceFactory factory) throws Exception {
        VirtualDisplay display = mRule.createManagedVirtualDisplayWithFlags(mVirtualDevice,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                        | DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
                        | DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED);
        Closeable inputDevice =
                factory.create(mVirtualDevice, DEVICE_NAME, display.getDisplay()).getDevice();
        inputDevice.close();
        inputDevice.close();
        inputDevice.close();
    }

    @Parameters(method = "allInputDevices")
    @Test
    public void close_removesInputDevice(VirtualInputDeviceFactory factory) throws Exception {
        VirtualDisplay display = mRule.createManagedVirtualDisplayWithFlags(mVirtualDevice,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                        | DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
                        | DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED);
        var deviceHolder = factory.create(mVirtualDevice, DEVICE_NAME, display.getDisplay());
        try (InputDeviceRemovedWaiter waiter =
                     new InputDeviceRemovedWaiter(mInputManager, deviceHolder.getDeviceId())) {
            deviceHolder.close();
            assertThat(waiter.awaitDeviceRemoval()).isTrue();
        }
    }

    @Parameters(method = "allInputDevices")
    @Test
    public void closeVirtualDevice_removesInputDevice(VirtualInputDeviceFactory factory)
            throws Exception {
        VirtualDisplay display = mRule.createManagedVirtualDisplayWithFlags(mVirtualDevice,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                        | DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
                        | DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED);
        var deviceHolder = factory.create(mVirtualDevice, DEVICE_NAME, display.getDisplay());
        try (InputDeviceRemovedWaiter waiter =
                     new InputDeviceRemovedWaiter(mInputManager, deviceHolder.getDeviceId())) {
            mVirtualDevice.close();
            assertThat(waiter.awaitDeviceRemoval()).isTrue();
        }
    }

    @Parameters(method = "allInputDevices")
    @Test
    public void createVirtualInputDevice_duplicateName_throwsException(
            VirtualInputDeviceFactory factory) {
        VirtualDisplay display = mRule.createManagedVirtualDisplayWithFlags(mVirtualDevice,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                        | DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
                        | DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED);
        factory.create(mVirtualDevice, DEVICE_NAME, display.getDisplay());
        assertThrows(IllegalArgumentException.class,
                () -> factory.create(mVirtualDevice, DEVICE_NAME, display.getDisplay()));
    }

    @Parameters(method = "allInputDevices")
    @Test
    public void createVirtualInputDevice_defaultDisplay_throwsException(
            VirtualInputDeviceFactory factory) {
        Display display = mDisplayManager.getDisplay(DEFAULT_DISPLAY);
        assertThrows(SecurityException.class,
                () -> factory.create(mVirtualDevice, DEVICE_NAME, display));
    }

    @Parameters(method = "allInputDevices")
    @Test
    public void createVirtualInputDevice_unownedDisplay_throwsException(
            VirtualInputDeviceFactory factory) {
        VirtualDisplay unownedDisplay = mRule.createManagedUnownedVirtualDisplayWithFlags(
                DisplayManager.VIRTUAL_DISPLAY_FLAG_SUPPORTS_TOUCH);
        assertThrows(SecurityException.class,
                () -> factory.create(mVirtualDevice, DEVICE_NAME, unownedDisplay.getDisplay()));
    }

    @Parameters(method = "allInputDevices")
    @Test
    public void createVirtualInputDevice_defaultDisplay_injectEvents_succeeds(
            VirtualInputDeviceFactory factory) {
        Display display = mDisplayManager.getDisplay(DEFAULT_DISPLAY);
        assertThat(mRule.runWithTemporaryPermission(
                () -> factory.create(mVirtualDevice, DEVICE_NAME, display),
                INJECT_EVENTS, CREATE_VIRTUAL_DEVICE))
                .isNotNull();
    }

    @Parameters(method = "allInputDevices")
    @Test
    public void createVirtualInputDevice_unownedVirtualDisplay_injectEvents_succeeds(
            VirtualInputDeviceFactory factory) {
        VirtualDisplay unownedDisplay = mRule.createManagedUnownedVirtualDisplayWithFlags(
                DisplayManager.VIRTUAL_DISPLAY_FLAG_SUPPORTS_TOUCH);
        assertThat(mRule.runWithTemporaryPermission(
                () -> factory.create(mVirtualDevice, DEVICE_NAME, unownedDisplay.getDisplay()),
                INJECT_EVENTS, CREATE_VIRTUAL_DEVICE))
                .isNotNull();
    }


    /** Utility to verify that an input device with a given ID has been removed. */
    private static class InputDeviceRemovedWaiter implements InputManager.InputDeviceListener,
            AutoCloseable {
        private final InputManager mInputManager;
        private final CountDownLatch mLatch = new CountDownLatch(1);
        private final int mDeviceId;

        InputDeviceRemovedWaiter(InputManager inputManager, int deviceId) {
            mDeviceId = deviceId;
            mInputManager = inputManager;
            mInputManager.registerInputDeviceListener(this, new Handler(Looper.getMainLooper()));
        }

        @Override
        public void onInputDeviceAdded(int deviceId) {
        }

        @Override
        public void onInputDeviceRemoved(int deviceId) {
            if (deviceId == mDeviceId) {
                mLatch.countDown();
            }
        }

        @Override
        public void onInputDeviceChanged(int deviceId) {
        }

        @Override
        public void close() {
            mInputManager.unregisterInputDeviceListener(this);
        }

        public boolean awaitDeviceRemoval() throws InterruptedException {
            return mLatch.await(3, TimeUnit.SECONDS);
        }
    }
}
