/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.helper.aoa;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Range;
import com.google.common.util.concurrent.Uninterruptibles;

import java.awt.Point;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import java.util.logging.Logger;
import javax.annotation.Nonnull;

/**
 * USB connected AOAv2-compatible Android device.
 *
 * <p>This host-side utility can be used to send commands (e.g. clicks, swipes, keystrokes, and
 * more) to a connected device without the need for ADB.
 *
 * @see <a href="https://source.android.com/devices/accessories/aoa2">Android Open Accessory
 *     Protocol 2.0</a>
 */
public class AoaDevice implements AutoCloseable {

    private static final Logger LOGGER = Logger.getLogger(AoaDevice.class.getName());

    // USB error code
    static final int DEVICE_NOT_FOUND = -4;

    // USB request types (direction and vendor type)
    static final byte INPUT = (byte) (0x80 | (0x02 << 5));
    static final byte OUTPUT = (byte) (0x00 | (0x02 << 5));

    // AOA VID and PID
    static final int GOOGLE_VID = 0x18D1;
    private static final Range<Integer> AOA_PID = Range.closed(0x2D00, 0x2D05);
    private static final Range<Integer> AUDIO_PID = Range.closed(0x2D02, 0x2D05);
    private static final ImmutableSet<Integer> ADB_PID = ImmutableSet.of(0x2D01, 0x2D03, 0x2D05);

    // Simulated accessory information
    private static final byte[] MANUFACTURER = "Android\0".getBytes(Charsets.UTF_8);
    private static final byte[] MODEL = (AoaDevice.class.getName() + "\0").getBytes(Charsets.UTF_8);
    private static final byte[] VERSION = "1.0\0".getBytes(Charsets.UTF_8);

    // AOA requests
    static final byte ACCESSORY_GET_PROTOCOL = 51;
    static final byte ACCESSORY_SEND_STRING = 52;
    static final byte ACCESSORY_START = 53;
    static final byte ACCESSORY_REGISTER_HID = 54;
    static final byte ACCESSORY_UNREGISTER_HID = 55;
    static final byte ACCESSORY_SET_HID_REPORT_DESC = 56;
    static final byte ACCESSORY_SEND_HID_EVENT = 57;
    static final byte ACCESSORY_SET_AUDIO_MODE = 58;

    // Maximum attempts at restarting in accessory mode
    static final int ACCESSORY_START_MAX_RETRIES = 5;

    // Touch types
    static final byte TOUCH_UP = 0b00;
    static final byte TOUCH_DOWN = 0b11;

    // System buttons
    static final byte SYSTEM_WAKE = 0b001;
    static final byte SYSTEM_HOME = 0b010;
    static final byte SYSTEM_BACK = 0b100;

    // Durations and steps
    private static final Duration CONNECTION_TIMEOUT = Duration.ofSeconds(10L);
    private static final Duration CONFIGURE_DELAY = Duration.ofSeconds(1L);
    private static final Duration ACTION_DELAY = Duration.ofSeconds(3L);
    private static final Duration STEP_DELAY = Duration.ofMillis(10L);
    static final Duration LONG_CLICK = Duration.ofSeconds(1L);

    private final UsbHelper mHelper;
    private UsbDevice mDelegate;
    private final String mSerialNumber;

    AoaDevice(@Nonnull UsbHelper helper, @Nonnull UsbDevice delegate) {
        mHelper = helper;
        mDelegate = delegate;
        if (!isValid()) {
            throw new UsbException("Invalid device connection");
        }
        mSerialNumber = mDelegate.getSerialNumber();
        if (mSerialNumber == null) {
            throw new UsbException("Could not determine device serial number");
        }
        initialize();
    }

    // Configure the device, switching to accessory mode if necessary and registering the HIDs
    private void initialize() {
        for (int attempt = 0; ; attempt++) {
            if (isAccessoryMode()) {
                registerHIDs();
                return;
            }
            if (attempt >= ACCESSORY_START_MAX_RETRIES) {
                LOGGER.warning(
                        String.format(
                                "Failed to start accessory mode on %s after %d attempts;"
                                        + " proceeding anyway",
                                mSerialNumber, attempt));
                registerHIDs();
                return;
            }
            // Send accessory information, restart in accessory mode, and try to initialize again
            transferOrThrow(ACCESSORY_SEND_STRING, 0, 0, MANUFACTURER);
            transferOrThrow(ACCESSORY_SEND_STRING, 0, 1, MODEL);
            transferOrThrow(ACCESSORY_SEND_STRING, 0, 3, VERSION);
            transferOrThrow(ACCESSORY_SET_AUDIO_MODE, 1, 0, new byte[0]);
            transferOrThrow(ACCESSORY_START, 0, 0, new byte[0]);
            sleep(CONFIGURE_DELAY);
            mDelegate.close();
            reconnect();
        }
    }

    // Convenience method to perform an outbound control transfer
    private int transfer(byte request, int value, int index, byte[] data) {
        return mDelegate.controlTransfer(OUTPUT, request, value, index, data);
    }

    // Convenience method to perform an outbound control transfer and throw if an error occurs
    private void transferOrThrow(byte request, int value, int index, byte[] data) {
        mHelper.checkResult(transfer(request, value, index, data));
    }

    // Reconnect to underlying USB device
    private void reconnect() {
        mDelegate = mHelper.getDevice(mSerialNumber, CONNECTION_TIMEOUT);
        if (!isValid()) {
            throw new UsbException("Timed out while reconnecting to device %s", mSerialNumber);
        }
    }

    // Register HIDs
    private void registerHIDs() {
        for (AoaHID hid : AoaHID.values()) {
            // register HID identifier and descriptor
            transferOrThrow(
                    ACCESSORY_REGISTER_HID, hid.getId(), hid.getDescriptor().length, new byte[0]);
            transferOrThrow(ACCESSORY_SET_HID_REPORT_DESC, hid.getId(), 0, hid.getDescriptor());
        }
        sleep(CONFIGURE_DELAY);
    }

    // Unregister HIDs
    private void unregisterHIDs() {
        for (AoaHID hid : AoaHID.values()) {
            transfer(ACCESSORY_UNREGISTER_HID, hid.getId(), 0, new byte[0]);
        }
    }

    /**
     * Close and re-fetch the connection. This is necessary after the USB connection has been reset,
     * e.g. when toggling accessory mode or USB debugging.
     */
    public void resetConnection() {
        close();
        reconnect();
        initialize();
    }

    /** @return true if connection is non-null, but does not check if resetting is necessary */
    public boolean isValid() {
        return mDelegate != null && mDelegate.isValid();
    }

    /** @return device's serial number */
    @Nonnull
    public String getSerialNumber() {
        return mSerialNumber;
    }

    // Checks whether the device is in accessory mode
    private boolean isAccessoryMode() {
        return GOOGLE_VID == mDelegate.getVendorId()
                && AOA_PID.contains(mDelegate.getProductId());
    }

    /** @return true if device has USB debugging enabled */
    public boolean isAdbEnabled() {
        return GOOGLE_VID == mDelegate.getVendorId()
                && ADB_PID.contains(mDelegate.getProductId());
    }

    /** @return true if device has USB audio enabled */
    public boolean isAudioEnabled() {
        return GOOGLE_VID == mDelegate.getVendorId()
                && AUDIO_PID.contains(mDelegate.getProductId());
    }

    /** Get current time. */
    @VisibleForTesting
    Instant now() {
        return Instant.now();
    }

    /** Wait for a specified duration. */
    public void sleep(@Nonnull Duration duration) {
        Uninterruptibles.sleepUninterruptibly(duration.toNanos(), TimeUnit.NANOSECONDS);
    }

    /** Perform a click. */
    public void click(@Nonnull Point point) {
        click(point, Duration.ZERO);
    }

    // Click and wait at a location.
    private void click(Point point, Duration duration) {
        touch(TOUCH_DOWN, point, duration);
        touch(TOUCH_UP, point, ACTION_DELAY);
    }

    /** Perform a long click. */
    public void longClick(@Nonnull Point point) {
        click(point, LONG_CLICK);
    }

    /**
     * Swipe from one position to another in the specified duration.
     *
     * @param from starting position
     * @param to final position
     * @param duration swipe motion duration
     */
    public void swipe(@Nonnull Point from, @Nonnull Point to, @Nonnull Duration duration) {
        Instant start = now();
        touch(TOUCH_DOWN, from, STEP_DELAY);
        while (true) {
            Duration elapsed = Duration.between(start, now());
            if (duration.compareTo(elapsed) < 0) {
                break;
            }
            double progress = (double) elapsed.toMillis() / duration.toMillis();
            Point point =
                    new Point(
                            (int) (progress * to.x + (1 - progress) * from.x),
                            (int) (progress * to.y + (1 - progress) * from.y));
            touch(TOUCH_DOWN, point, STEP_DELAY);
        }
        touch(TOUCH_UP, to, ACTION_DELAY);
    }

    // Send a touch event to the device
    private void touch(byte type, Point point, Duration pause) {
        int x = Math.min(Math.max(point.x, 0), 360);
        int y = Math.min(Math.max(point.y, 0), 640);
        byte[] data = new byte[] {type, (byte) x, (byte) (x >> 8), (byte) y, (byte) (y >> 8)};
        send(AoaHID.TOUCH_SCREEN, data, pause);
    }

    /**
     * Press a combination of keys.
     *
     * @param keys list of keys
     */
    public void pressKeys(AoaKey... keys) {
        pressKeys(Arrays.asList(keys));
    }

    /**
     * Press a combination of keys.
     *
     * @param keys list of keys
     */
    public void pressKeys(@Nonnull List<AoaKey> keys) {
        Iterator<AoaKey> it = keys.stream().filter(Objects::nonNull).iterator();
        while (it.hasNext()) {
            AoaKey key = it.next();
            send(AoaHID.KEYBOARD, key.toHidData(), STEP_DELAY);
            send(
                    AoaHID.KEYBOARD,
                    AoaKey.NOOP.toHidData(),
                    it.hasNext() ? STEP_DELAY : ACTION_DELAY);
        }
    }

    /** Wake up the device if it is sleeping. */
    public void wakeUp() {
        send(AoaHID.SYSTEM, new byte[] {SYSTEM_WAKE}, ACTION_DELAY);
    }

    /** Press the device's home button. */
    public void goHome() {
        send(AoaHID.SYSTEM, new byte[] {SYSTEM_HOME}, ACTION_DELAY);
    }

    /** Press the device's back button. */
    public void goBack() {
        send(AoaHID.SYSTEM, new byte[] {SYSTEM_BACK}, ACTION_DELAY);
    }

    // Send a HID event to the device
    private void send(AoaHID hid, byte[] data, Duration pause) {
        int result = transfer(ACCESSORY_SEND_HID_EVENT, hid.getId(), 0, data);
        if (result == DEVICE_NOT_FOUND) {
            LOGGER.warning(
                    String.format(
                            "Device %s not found while sending AOA HID event; resetting connection",
                            mSerialNumber));
            resetConnection();
            result = transfer(ACCESSORY_SEND_HID_EVENT, hid.getId(), 0, data);
        }
        mHelper.checkResult(result);
        sleep(pause);
    }

    /** Close the device connection. */
    @Override
    public void close() {
        if (isValid()) {
            if (isAccessoryMode()) {
                unregisterHIDs();
            }
            mDelegate.close();
            mDelegate = null;
        }
    }

    @Override
    public String toString() {
        return String.format("AoaDevice{%s}", mSerialNumber);
    }
}
