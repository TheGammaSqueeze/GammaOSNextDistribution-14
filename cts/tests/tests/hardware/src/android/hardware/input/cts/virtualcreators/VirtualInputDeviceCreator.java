/*
 * Copyright 2023 The Android Open Source Project
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

package android.hardware.input.cts.virtualcreators;

import static com.google.common.truth.Truth.assertThat;

import android.companion.virtual.VirtualDeviceManager.VirtualDevice;
import android.hardware.input.InputManager;
import android.hardware.input.VirtualDpad;
import android.hardware.input.VirtualDpadConfig;
import android.hardware.input.VirtualKeyboard;
import android.hardware.input.VirtualKeyboardConfig;
import android.hardware.input.VirtualMouse;
import android.hardware.input.VirtualMouseConfig;
import android.hardware.input.VirtualNavigationTouchpad;
import android.hardware.input.VirtualNavigationTouchpadConfig;
import android.hardware.input.VirtualStylus;
import android.hardware.input.VirtualStylusConfig;
import android.hardware.input.VirtualTouchscreen;
import android.hardware.input.VirtualTouchscreenConfig;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import android.view.InputDevice;

import androidx.test.platform.app.InstrumentationRegistry;

import java.io.Closeable;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Static utilities for creating virtual input devices.
 */
public final class VirtualInputDeviceCreator {

    public static final int PRODUCT_ID = 1;
    public static final int VENDOR_ID = 1;

    private static <T extends Closeable> InputDeviceHolder<T> prepareInputDevice(
            Supplier<T> deviceCreator) {
        return prepareInputDevice(deviceCreator, /* languageTag= */ null, /* layoutType= */ null);
    }

    private static <T extends Closeable> InputDeviceHolder<T> prepareInputDevice(
            Supplier<T> deviceCreator, String languageTag, String layoutType) {
        InputManager inputManager = InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getSystemService(InputManager.class);
        try (InputDeviceAddedWaiter waiter =
                     new InputDeviceAddedWaiter(inputManager, languageTag, layoutType)) {
            return new InputDeviceHolder<T>(deviceCreator.get(), waiter.await());
        } catch (InterruptedException e) {
            throw new AssertionError("Virtual input device setup was interrupted", e);
        }
    }

    public static InputDeviceHolder<VirtualTouchscreen> createAndPrepareTouchscreen(
            VirtualDevice virtualDevice, String name, Display display) {
        return prepareInputDevice(() -> virtualDevice.createVirtualTouchscreen(
                new VirtualTouchscreenConfig.Builder(
                        display.getMode().getPhysicalWidth(),
                        display.getMode().getPhysicalHeight())
                        .setVendorId(VENDOR_ID)
                        .setProductId(PRODUCT_ID)
                        .setInputDeviceName(name)
                        .setAssociatedDisplayId(display.getDisplayId())
                        .build()));
    }

    public static InputDeviceHolder<VirtualStylus> createAndPrepareStylus(
            VirtualDevice virtualDevice, String name, Display display) {
        return prepareInputDevice(() -> virtualDevice.createVirtualStylus(
                new VirtualStylusConfig.Builder(
                        display.getMode().getPhysicalWidth(),
                        display.getMode().getPhysicalHeight())
                        .setVendorId(VENDOR_ID)
                        .setProductId(PRODUCT_ID)
                        .setInputDeviceName(name)
                        .setAssociatedDisplayId(display.getDisplayId())
                        .build()
        ));
    }

    public static InputDeviceHolder<VirtualMouse> createAndPrepareMouse(
            VirtualDevice virtualDevice, String name, Display display) {
        return prepareInputDevice(() -> virtualDevice.createVirtualMouse(
                new VirtualMouseConfig.Builder()
                        .setVendorId(VENDOR_ID)
                        .setProductId(PRODUCT_ID)
                        .setInputDeviceName(name)
                        .setAssociatedDisplayId(display.getDisplayId())
                        .build()));
    }

    public static InputDeviceHolder<VirtualKeyboard> createAndPrepareKeyboard(
            VirtualDevice virtualDevice, String name, Display display) {
        return createAndPrepareKeyboard(virtualDevice, name, display,
                VirtualKeyboardConfig.DEFAULT_LANGUAGE_TAG,
                VirtualKeyboardConfig.DEFAULT_LAYOUT_TYPE);
    }

    public static InputDeviceHolder<VirtualKeyboard> createAndPrepareKeyboard(
            VirtualDevice virtualDevice, String name, Display display, String languageTag,
            String layoutType) {
        return prepareInputDevice(() -> virtualDevice.createVirtualKeyboard(
                new VirtualKeyboardConfig.Builder()
                        .setVendorId(VENDOR_ID)
                        .setProductId(PRODUCT_ID)
                        .setInputDeviceName(name)
                        .setAssociatedDisplayId(display.getDisplayId())
                        .setLanguageTag(languageTag)
                        .setLayoutType(layoutType)
                        .build()), languageTag, layoutType);
    }

    public static InputDeviceHolder<VirtualDpad> createAndPrepareDpad(
            VirtualDevice virtualDevice, String name, Display display) {
        return prepareInputDevice(() -> virtualDevice.createVirtualDpad(
                new VirtualDpadConfig.Builder()
                        .setVendorId(VENDOR_ID)
                        .setProductId(PRODUCT_ID)
                        .setInputDeviceName(name)
                        .setAssociatedDisplayId(display.getDisplayId())
                        .build()));
    }

    public static InputDeviceHolder<VirtualNavigationTouchpad> createAndPrepareNavigationTouchpad(
            VirtualDevice virtualDevice, String name, Display display) {
        return createAndPrepareNavigationTouchpad(virtualDevice, name, display,
                display.getMode().getPhysicalWidth(), display.getMode().getPhysicalHeight());
    }

    public static InputDeviceHolder<VirtualNavigationTouchpad> createAndPrepareNavigationTouchpad(
            VirtualDevice virtualDevice, String name, Display display, int touchpadWidth,
            int touchpadHeight) {
        return prepareInputDevice(() -> virtualDevice.createVirtualNavigationTouchpad(
                new VirtualNavigationTouchpadConfig.Builder(touchpadWidth, touchpadHeight)
                        .setVendorId(VENDOR_ID)
                        .setProductId(PRODUCT_ID)
                        .setInputDeviceName(name)
                        .setAssociatedDisplayId(display.getDisplayId())
                        .build()));
    }

    private VirtualInputDeviceCreator() {
    }

    /** Holds a virtual input device along with its input device ID. */
    public static class InputDeviceHolder<T extends Closeable> implements Closeable {
        private final T mDevice;
        private final int mDeviceId;

        public InputDeviceHolder(T device, int deviceId) {
            mDevice = device;
            mDeviceId = deviceId;
        }

        public T getDevice() {
            return mDevice;
        }

        public int getDeviceId() {
            return mDeviceId;
        }

        @Override
        public void close() throws IOException {
            mDevice.close();
        }
    }

    /** Utility to verify that an input device with a given parameters has been created. */
    private static class InputDeviceAddedWaiter implements InputManager.InputDeviceListener,
            AutoCloseable {

        private final InputManager mInputManager;
        private final CountDownLatch mLatch = new CountDownLatch(1);
        private final String mLanguageTag;
        private final String mLayoutType;
        private int mDeviceId;

        InputDeviceAddedWaiter(InputManager inputManager, String languageTag, String layoutType) {
            mLanguageTag = languageTag;
            mLayoutType = layoutType;
            mInputManager = inputManager;
            mInputManager.registerInputDeviceListener(this, new Handler(Looper.getMainLooper()));
        }

        @Override
        public void onInputDeviceAdded(int deviceId) {
            onInputDeviceChanged(deviceId);
        }

        @Override
        public void onInputDeviceRemoved(int deviceId) {
        }

        @Override
        public void onInputDeviceChanged(int deviceId) {
            InputDevice device = mInputManager.getInputDevice(deviceId);
            if (device != null && device.getProductId() == PRODUCT_ID
                    && device.getVendorId() == VENDOR_ID
                    && Objects.equals(mLanguageTag, device.getKeyboardLanguageTag())
                    && Objects.equals(mLayoutType, device.getKeyboardLayoutType())) {
                mDeviceId = deviceId;
                mLatch.countDown();
            }
        }

        @Override
        public void close() {
            mInputManager.unregisterInputDeviceListener(this);
        }

        /** Returns the device ID of the newly added input device. */
        public int await() throws InterruptedException {
            assertThat(mLatch.await(3, TimeUnit.SECONDS)).isTrue();
            return mDeviceId;
        }
    }
}
