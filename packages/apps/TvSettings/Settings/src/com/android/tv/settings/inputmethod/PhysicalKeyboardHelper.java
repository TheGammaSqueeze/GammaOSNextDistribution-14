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

package com.android.tv.settings.inputmethod;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.hardware.input.InputDeviceIdentifier;
import android.hardware.input.InputManager;
import android.hardware.input.KeyboardLayout;
import android.text.TextUtils;
import android.view.InputDevice;

import com.android.settingslib.R;

import java.text.Collator;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Helper methods to retrieve information about physical keyboard devices.
 */
public class PhysicalKeyboardHelper {

    /**
     * Queries the input manager for a list of physical keyboards.
     */
    @NonNull
    public static List<DeviceInfo> getPhysicalKeyboards(
            @NonNull Context context) {
        final List<DeviceInfo> keyboards = new ArrayList<>();
        final InputManager im = context.getSystemService(InputManager.class);
        if (im == null) {
            return new ArrayList<>();
        }
        for (int deviceId : InputDevice.getDeviceIds()) {
            final InputDevice device = InputDevice.getDevice(deviceId);
            if (device == null || device.isVirtual() || !device.isFullKeyboard()) {
                continue;
            }
            final String currentLayoutDesc =
                    im.getCurrentKeyboardLayoutForInputDevice(device.getIdentifier());
            keyboards.add(new DeviceInfo(device.getName(), device.getId(),
                    device.getIdentifier(), currentLayoutDesc,
                    getLayoutLabel(currentLayoutDesc, context, im)));
        }

        // We intentionally don't reuse Comparator because Collator may not be thread-safe.
        final Collator collator = Collator.getInstance();
        keyboards.sort((a, b) -> {
            int result = collator.compare(a.mDeviceName, b.mDeviceName);
            if (result != 0) {
                return result;
            }
            result = a.mDeviceIdentifier.getDescriptor().compareTo(
                    b.mDeviceIdentifier.getDescriptor());
            if (result != 0) {
                return result;
            }
            return collator.compare(a.mCurrentLayoutLabel, b.mCurrentLayoutLabel);
        });
        return keyboards;
    }

    private static String getLayoutLabel(@Nullable String currentLayoutDescriptor,
            @NonNull Context context, @NonNull InputManager im) {
        if (currentLayoutDescriptor == null) {
            return context.getString(R.string.keyboard_layout_default_label);
        }
        final KeyboardLayout currentLayout = im.getKeyboardLayout(currentLayoutDescriptor);
        if (currentLayout == null) {
            return context.getString(R.string.keyboard_layout_default_label);
        }
        // If current layout is specified but the layout is null, just return an empty string
        // instead of falling back to R.string.keyboard_layout_default_label.
        return TextUtils.emptyIfNull(currentLayout.getLabel());
    }

    /**
     * Contains information about a physical keyboard.
     */
    public static final class DeviceInfo {
        @NonNull
        public final String mDeviceName;
        public final int mDeviceId;
        @NonNull
        public final InputDeviceIdentifier mDeviceIdentifier;
        @NonNull
        public final String mCurrentLayoutLabel;
        @Nullable
        public final String mCurrentLayoutDescriptor;

        public DeviceInfo(
                @Nullable String deviceName,
                int deviceId,
                @NonNull InputDeviceIdentifier deviceIdentifier,
                @Nullable String layoutDescriptor,
                @NonNull String layoutLabel) {
            mDeviceName = TextUtils.emptyIfNull(deviceName);
            mDeviceId = deviceId;
            mDeviceIdentifier = deviceIdentifier;
            mCurrentLayoutDescriptor = layoutDescriptor;
            mCurrentLayoutLabel = layoutLabel;
        }

        public String getSummary() {
            return mDeviceName + ": " + mCurrentLayoutLabel;
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) return true;
            if (o == null) return false;

            if (!(o instanceof DeviceInfo)) return false;

            final DeviceInfo that = (DeviceInfo) o;
            if (!TextUtils.equals(mDeviceName, that.mDeviceName)) {
                return false;
            }
            if (!(mDeviceId == that.mDeviceId)) {
                return false;
            }
            if (!Objects.equals(mDeviceIdentifier, that.mDeviceIdentifier)) {
                return false;
            }
            if (!TextUtils.equals(mCurrentLayoutDescriptor, that.mCurrentLayoutDescriptor)) {
                return false;
            }
            if (!TextUtils.equals(mCurrentLayoutLabel, that.mCurrentLayoutLabel)) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            return Objects.hash(mDeviceName, mDeviceId, mDeviceIdentifier, mCurrentLayoutLabel,
                    mCurrentLayoutDescriptor);
        }

        @Override
        public String toString() {
            return "DeviceInfo: name=" + mDeviceName + ", id=" + mDeviceId
                    + ", descriptor=" + mDeviceIdentifier.getDescriptor()
                    + ", currentLayoutDescriptor=" + mCurrentLayoutDescriptor
                    + ", currentLayoutLabel=" + mCurrentLayoutLabel;
        }
    }
}
