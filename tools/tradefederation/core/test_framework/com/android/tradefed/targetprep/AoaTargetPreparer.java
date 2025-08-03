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
package com.android.tradefed.targetprep;

import com.android.helper.aoa.AoaDevice;
import com.android.helper.aoa.AoaKey;
import com.android.helper.aoa.UsbHelper;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.OptionClass;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.RegexTrie;

import com.google.common.annotations.VisibleForTesting;

import java.awt.Point;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * {@link ITargetPreparer} that executes a series of actions (e.g. clicks and swipes) using the
 * Android Open Accessory (AOAv2) protocol. This allows controlling an Android device without
 * enabling USB debugging.
 *
 * <p>Accepts a list of strings which correspond to {@link AoaDevice} methods:
 *
 * <ul>
 *   <li>Click using x and y coordinates, e.g. "click 0 0" or "longClick 360 640".
 *   <li>Swipe between two sets of coordinates in a specified number of milliseconds, e.g. "swipe 0
 *       0 100 360 640" to swipe from (0, 0) to (360, 640) in 100 milliseconds.
 *   <li>Write a string of alphanumeric text, e.g. "write hello world".
 *   <li>Press a combination of keys, e.g. "key RIGHT 2*TAB ENTER".
 *   <li>Wake up the device with "wake".
 *   <li>Press the home button with "home".
 *   <li>Press the back button with "back".
 *   <li>Wait for specified number of milliseconds, e.g. "sleep 1000" to wait for 1000 milliseconds.
 * </ul>
 */
@OptionClass(alias = "aoa-preparer")
public class AoaTargetPreparer extends BaseTargetPreparer {

    private static final String POINT = "(\\d{1,3}) (\\d{1,3})";
    private static final Pattern KEY = Pattern.compile("\\s+(?:(\\d+)\\*)?([a-zA-Z0-9@\\-_+.]+)");

    @FunctionalInterface
    private interface Action extends BiConsumer<AoaDevice, List<String>> {}

    // Trie of possible actions, parses the input string and determines the operation to execute
    private static final RegexTrie<Action> ACTIONS = new RegexTrie<>();

    static {
        // clicks
        ACTIONS.put(
                (device, args) -> device.click(parsePoint(args.get(0), args.get(1))),
                String.format("click %s", POINT));
        ACTIONS.put(
                (device, args) -> device.longClick(parsePoint(args.get(0), args.get(1))),
                String.format("longClick %s", POINT));

        // swipes
        ACTIONS.put(
                (device, args) -> {
                    Point from = parsePoint(args.get(0), args.get(1));
                    Duration duration = parseMillis(args.get(2));
                    Point to = parsePoint(args.get(3), args.get(4));
                    device.swipe(from, to, duration);
                },
                String.format("swipe %s (\\d+) %s", POINT, POINT));

        // keyboard
        ACTIONS.put(
                (device, args) -> {
                    List<AoaKey> keys =
                            Stream.of(args.get(0).split(""))
                                    .map(AoaTargetPreparer::parseKey)
                                    .collect(Collectors.toList());
                    device.pressKeys(keys);
                },
                "write ([a-zA-Z0-9@\\-_+.\\s]+)");
        ACTIONS.put(
                (device, args) -> {
                    List<AoaKey> keys = new ArrayList<>();
                    Matcher matcher = KEY.matcher(args.get(0));
                    while (matcher.find()) {
                        int count = matcher.group(1) == null ? 1 : Integer.decode(matcher.group(1));
                        AoaKey key = parseKey(matcher.group(2));
                        keys.addAll(Collections.nCopies(count, key));
                    }
                    device.pressKeys(keys);
                },
                "key((?: (?:\\d+\\*)?[a-zA-Z0-9@\\-_+.]+)+)");

        // other
        ACTIONS.put((device, args) -> device.wakeUp(), "wake");
        ACTIONS.put((device, args) -> device.goHome(), "home");
        ACTIONS.put((device, args) -> device.goBack(), "back");
        ACTIONS.put(
                (device, args) -> {
                    Duration duration = parseMillis(args.get(0));
                    device.sleep(duration);
                },
                "sleep (\\d+)");
    }

    @Option(name = "device-timeout", description = "Maximum time to wait for device")
    private Duration mDeviceTimeout = Duration.ofMinutes(1L);

    @Option(
        name = "wait-for-device-online",
        description = "Checks whether the device is online after preparation."
    )
    private boolean mWaitForDeviceOnline = true;

    @Option(name = "action", description = "AOAv2 action to perform. Can be repeated.")
    private List<String> mActions = new ArrayList<>();

    @Override
    public void setUp(TestInformation testInfo)
            throws TargetSetupError, BuildError, DeviceNotAvailableException {
        if (mActions.isEmpty()) {
            return;
        }
        ITestDevice device = testInfo.getDevice();
        try {
            configure(device.getSerialNumber());
        } catch (RuntimeException e) {
            throw new TargetSetupError(e.getMessage(), e, device.getDeviceDescriptor());
        }

        if (mWaitForDeviceOnline) {
            CLog.i("Checking that %s is online after preparation", device.getSerialNumber());
            device.waitForDeviceOnline();
        }
    }

    // Connect to device using its serial number and perform actions
    private void configure(String serialNumber) throws DeviceNotAvailableException {
        try (UsbHelper usb = getUsbHelper();
                AoaDevice device = usb.getAoaDevice(serialNumber, mDeviceTimeout)) {
            if (device == null) {
                throw new DeviceNotAvailableException(
                        "AOAv2-compatible device not found", serialNumber);
            }
            CLog.i("Executing %d actions on %s", mActions.size(), serialNumber);
            mActions.forEach(action -> execute(device, action));
        }
    }

    @VisibleForTesting
    UsbHelper getUsbHelper() {
        return new UsbHelper();
    }

    // Parse and execute an action
    @VisibleForTesting
    void execute(AoaDevice device, String input) {
        CLog.d("Executing '%s' on %s", input, device.getSerialNumber());
        List<List<String>> args = new ArrayList<>();
        Action action = ACTIONS.retrieve(args, input);
        if (action == null) {
            throw new IllegalArgumentException(String.format("Invalid action %s", input));
        }
        action.accept(device, args.get(0));
    }

    // Construct point from string coordinates
    private static Point parsePoint(String x, String y) {
        return new Point(Integer.decode(x), Integer.decode(y));
    }

    // Construct duration from string milliseconds
    private static Duration parseMillis(String millis) {
        return Duration.ofMillis(Long.parseLong(millis));
    }

    // Convert a string value into an AOA key
    private static AoaKey parseKey(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        if (value.matches("\\s+")) {
            return new AoaKey(0x2C); // Convert whitespace to the space character
        }
        // Lookup key (case sensitive and insensitive) or try to parse into an integer
        AoaKey key = KEYS.get(value);
        if (key == null) {
            key = KEYS.get(value.toLowerCase());
        }
        return key != null ? key : new AoaKey(Integer.decode(value));
    }

    // Map of characters to HID keycodes
    private static final Map<String, AoaKey> KEYS = new HashMap<>();

    static {
        // Letters (case-sensitive)
        for (int usage = 0x04, letter = 'a'; letter <= 'z'; usage++, letter++) {
            String lowerCase = Character.toString((char) letter);
            KEYS.put(lowerCase, new AoaKey(usage));
            KEYS.put(lowerCase.toUpperCase(), new AoaKey(usage, AoaKey.Modifier.SHIFT));
        }

        // Numbers
        KEYS.put("1", new AoaKey(0x1E));
        KEYS.put("2", new AoaKey(0x1F));
        KEYS.put("3", new AoaKey(0x20));
        KEYS.put("4", new AoaKey(0x21));
        KEYS.put("5", new AoaKey(0x22));
        KEYS.put("6", new AoaKey(0x23));
        KEYS.put("7", new AoaKey(0x24));
        KEYS.put("8", new AoaKey(0x25));
        KEYS.put("9", new AoaKey(0x26));
        KEYS.put("0", new AoaKey(0x27));

        // Additional keys
        KEYS.put("enter", new AoaKey(0x28));
        KEYS.put("tab", new AoaKey(0x2B));
        KEYS.put("space", new AoaKey(0x2C));
        KEYS.put("right", new AoaKey(0x4F));
        KEYS.put("left", new AoaKey(0x50));
        KEYS.put("down", new AoaKey(0x51));
        KEYS.put("up", new AoaKey(0x52));
        KEYS.put("@", new AoaKey(0x1F, AoaKey.Modifier.SHIFT));
        KEYS.put("-", new AoaKey(0x2D));
        KEYS.put("_", new AoaKey(0x2D, AoaKey.Modifier.SHIFT));
        KEYS.put("+", new AoaKey(0x2E, AoaKey.Modifier.SHIFT));
        KEYS.put(".", new AoaKey(0x37));
    }
}
