/*
 * Copyright 2020 The Android Open Source Project
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

package android.hdmicec.cts;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import java.util.HashMap;
import java.util.Map;

/** Helper class to call tests in the HdmiCecControlManagerHelper app */
public class HdmiControlManagerUtility {
    /** The package name of the APK. */
    private static final String TEST_PKG = "android.hdmicec.app";

    /** The class name of the main activity in the APK. */
    private static final String TEST_CLS = "android.hdmicec.app.HdmiControlManagerHelper";

    /** The method name of the set active source case. */
    private static final String SELECT_DEVICE = "deviceSelect";

    /** The method name of the set active source case. */
    private static final String SEND_INTERRUPTED_LONG_PRESS = "interruptedLongPress";

    /** The method name of the set active source case. */
    private static final String VENDOR_CMD_LISTENER_WITHOUT_ID = "vendorCmdListenerWithoutId";

    /** The method name of the set active source case. */
    private static final String VENDOR_CMD_LISTENER_WITH_ID = "vendorCmdListenerWithId";

    /** The key of the set active source case arguments. */
    private static final String LOGICAL_ADDR = "ARG_LOGICAL_ADDR";

    /** The timeout of the test. */
    private static final long TEST_TIMEOUT_MS = 10 * 60 * 1000L;

    /**
     * Method to make a device the active source. Will only work if the DUT is TV.
     *
     * @param host Reference to the JUnit4 host test class
     * @param device Reference to the DUT
     * @param logicalAddress The logical address of the device that should be made the active source
     */
    public static void selectDevice(BaseHostJUnit4Test host, ITestDevice device,
            String logicalAddress) throws DeviceNotAvailableException {
        Map<String, String> args = new HashMap<>();
        args.put(LOGICAL_ADDR, logicalAddress);
        host.runDeviceTests(device, null, TEST_PKG, TEST_CLS, SELECT_DEVICE, null,
                TEST_TIMEOUT_MS, TEST_TIMEOUT_MS, 0L, true, false, args);
    }

    /**
     * Sends a long press keyevent (KEYCODE_UP) followed by a short press of another keyevent
     * (KEYCODE_DOWN).
     */
    public static void sendLongPressKeyevent(BaseHostJUnit4Test host) throws DeviceNotAvailableException {
        host.runDeviceTests(TEST_PKG, TEST_CLS, SEND_INTERRUPTED_LONG_PRESS);
    }

    /** Registers a vendor command listener without a vendor ID. */
    public static void registerVendorCmdListenerWithoutId(BaseHostJUnit4Test host)
            throws DeviceNotAvailableException {
        host.runDeviceTests(TEST_PKG, TEST_CLS, VENDOR_CMD_LISTENER_WITHOUT_ID);
    }

    /** Registers a vendor command listener with vendor ID. */
    public static void registerVendorCmdListenerWithId(BaseHostJUnit4Test host)
            throws DeviceNotAvailableException {
        host.runDeviceTests(TEST_PKG, TEST_CLS, VENDOR_CMD_LISTENER_WITH_ID);
    }
}
