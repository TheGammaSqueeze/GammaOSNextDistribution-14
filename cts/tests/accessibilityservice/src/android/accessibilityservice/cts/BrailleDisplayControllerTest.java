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

package android.accessibilityservice.cts;

import static android.Manifest.permission.MANAGE_ACCESSIBILITY;
import static android.accessibilityservice.BrailleDisplayController.BrailleDisplayCallback.FLAG_ERROR_BRAILLE_DISPLAY_NOT_FOUND;
import static android.accessibilityservice.BrailleDisplayController.BrailleDisplayCallback.FLAG_ERROR_CANNOT_ACCESS;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.accessibility.cts.common.AccessibilityDumpOnFailureRule;
import android.accessibility.cts.common.InstrumentedAccessibilityService;
import android.accessibility.cts.common.InstrumentedAccessibilityServiceTestRule;
import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.BrailleDisplayController;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.hardware.input.InputManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.SystemProperties;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.view.accessibility.Flags;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.TestUtils;

import junit.framework.AssertionFailedError;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Tests for {@link BrailleDisplayController} APIs.
 */
@RunWith(AndroidJUnit4.class)
@CddTest(requirements = {"3.10/C-1-1,C-1-2"})
@Presubmit
@AppModeFull
@RequiresFlagsEnabled(Flags.FLAG_BRAILLE_DISPLAY_HID)
public class BrailleDisplayControllerTest {
    private static final long CALLBACK_TIMEOUT_MS = 5000;
    private static final byte[] DESCRIPTOR1 = {0x05, 0x41, 0x01, 0x0A};
    private static final byte[] DESCRIPTOR2 = {0x05, 0x41, 0x01, 0x0B};
    private static final String BT_ADDRESS1 = "00:11:22:33:AA:BB";
    private static final String BT_ADDRESS2 = "22:33:44:55:AA:BB";
    // "Real" HIDRAW node files are used for the majority of tests, created
    // by the 'hid' command line tool.
    private static final String HIDRAW_NODE_0 = "/dev/hidraw0";
    private static final String HIDRAW_NODE_1 = "/dev/hidraw1";
    // Fake test files are used to test input and writing, which are not supported
    // by the 'hid' command line tool.
    // These files are in /data/system so that system_server can read/write from them.
    // Note: this also requires userdebug/eng builds and using shell commands to
    // create/read/write from files in this directory, because this test app only has
    // normal app privileges but userdebug-shell can act as the system user.
    private static final String FAKE_HIDRAW_DIR =
            "/data/system/" + BrailleDisplayControllerTest.class.getSimpleName();

    private static Instrumentation sInstrumentation;
    private static UiAutomation sUiAutomation;

    private final InstrumentedAccessibilityServiceTestRule<StubBrailleDisplayAccessibilityService>
            mServiceRule = new InstrumentedAccessibilityServiceTestRule<>(
            StubBrailleDisplayAccessibilityService.class);
    private final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule(sUiAutomation);
    private final AccessibilityDumpOnFailureRule mDumpOnFailureRule =
            new AccessibilityDumpOnFailureRule();

    private BluetoothDevice mBluetoothDevice1;
    private BluetoothDevice mBluetoothDevice2;
    private StubBrailleDisplayAccessibilityService mService;
    private BrailleDisplayController mController;
    private Executor mExecutor;
    private boolean mIsUserdebugOrEng;

    // Tracks the added and removed HIDRAW device nodes.
    private int mDeviceCount;
    private final Object mDeviceWaitObject = new Object();

    // Default implementation of BrailleDisplayCallback
    private static class TestBrailleDisplayCallback implements
            BrailleDisplayController.BrailleDisplayCallback {

        @Override
        public void onConnected(@NonNull byte[] hidDescriptor) {

        }

        @Override
        public void onConnectionFailed(int error) {

        }

        @Override
        public void onInput(@NonNull byte[] input) {

        }

        @Override
        public void onDisconnected() {

        }
    }

    @Rule
    public RuleChain mRuleChain = RuleChain
            .outerRule(mServiceRule)
            .around(mCheckFlagsRule)
            .around(mDumpOnFailureRule);

    @BeforeClass
    public static void oneTimeSetup() throws Exception {
        sInstrumentation = InstrumentationRegistry.getInstrumentation();
        sUiAutomation = sInstrumentation.getUiAutomation(
                UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
    }

    @Before
    public void setup() throws Exception {
        assumeTrue(SystemProperties.getBoolean("ro.accessibility.support_hidraw", true));
        mService = mServiceRule.getService();
        assertThat(mService).isNotNull();
        mController = mService.getBrailleDisplayController();
        mExecutor = mService.getMainExecutor();
        BluetoothManager bluetoothManager =
                sInstrumentation.getContext().getSystemService(BluetoothManager.class);
        assumeTrue(bluetoothManager != null);
        mBluetoothDevice1 = bluetoothManager.getAdapter().getRemoteDevice(BT_ADDRESS1);
        mBluetoothDevice2 = bluetoothManager.getAdapter().getRemoteDevice(BT_ADDRESS2);
        mIsUserdebugOrEng = !"user".equals(Build.TYPE);
        if (mIsUserdebugOrEng) {
            executeSystemShellCommand("mkdir " + FAKE_HIDRAW_DIR);
            TestUtils.waitUntil(FAKE_HIDRAW_DIR + " should exist", () ->
                    !executeSystemShellCommand("ls -l " + FAKE_HIDRAW_DIR).isEmpty());
        }
        mDeviceCount = 0;
        InputManager inputManager = sInstrumentation.getContext().getSystemService(
                InputManager.class);
        assertThat(inputManager).isNotNull();
        inputManager.registerInputDeviceListener(new InputManager.InputDeviceListener() {
            @Override
            public void onInputDeviceAdded(int deviceId) {
                synchronized (mDeviceWaitObject) {
                    mDeviceCount++;
                    mDeviceWaitObject.notifyAll();
                }
            }

            @Override
            public void onInputDeviceRemoved(int deviceId) {
                synchronized (mDeviceWaitObject) {
                    mDeviceCount--;
                    mDeviceWaitObject.notifyAll();
                }
            }

            @Override
            public void onInputDeviceChanged(int deviceId) {
            }
        }, new Handler(mService.getMainLooper()));
    }

    @After
    public void cleanup() throws Exception {
        if (mController != null) {
            mController.disconnect();
        }
        if (mIsUserdebugOrEng) {
            executeSystemShellCommand("rm -rf " + FAKE_HIDRAW_DIR);
        }
        // Individual tests should clean up their own test HIDRAW nodes,
        // but this can sometimes take a moment to propagate.
        TestUtils.waitOn(mDeviceWaitObject, () -> {
            synchronized (mDeviceWaitObject) {
                return mDeviceCount == 0;
            }
        }, CALLBACK_TIMEOUT_MS, "Expected all HIDRAW devices removed");
    }

    private void setTestData(List<Bundle> testData) {
        setTestData(mService, testData);
    }

    private static void setTestData(AccessibilityService service, List<Bundle> testData) {
        sUiAutomation.adoptShellPermissionIdentity(MANAGE_ACCESSIBILITY);
        BrailleDisplayController.setTestBrailleDisplayData(service, testData);
        sUiAutomation.dropShellPermissionIdentity();
    }

    private static Bundle getTestBrailleDisplay(String path, byte[] descriptor, String uniq,
            boolean isBluetooth) {
        Bundle bundle = new Bundle();
        bundle.putString(BrailleDisplayController.TEST_BRAILLE_DISPLAY_HIDRAW_PATH, path);
        bundle.putByteArray(BrailleDisplayController.TEST_BRAILLE_DISPLAY_DESCRIPTOR, descriptor);
        bundle.putString(BrailleDisplayController.TEST_BRAILLE_DISPLAY_UNIQUE_ID, uniq);
        bundle.putBoolean(BrailleDisplayController.TEST_BRAILLE_DISPLAY_BUS_BLUETOOTH, isBluetooth);
        return bundle;
    }

    /**
     * Creates a test /dev/hidraw* node using the 'hid' command line tool.
     *
     * @return An OutputStream that keeps the 'hid' command alive. Closing this stream will
     * stop the command and delete the HIDRAW node.
     */
    private OutputStream createTestHidrawNode(String expectedPath) throws Exception {
        assertThat(new File(expectedPath).exists()).isFalse();

        // See frameworks/base/cmds/hid/README.md for the expected format.
        // These values are all valid defaults copied from cts/tests/tests/hardware/res/raw,
        // but none are actually read in the tests because the tests use the data provided by
        // BrailleDisplayController.setTestBrailleDisplayData.
        String registerCommand = """
                {
                  "id": ID,
                  "command": "register",
                  "name": "fake device",
                  "bus": "bluetooth",
                  "vid": 0x0001,
                  "pid": 0x0001,
                  "source": "GAMEPAD",
                  "descriptor": [
                    0x05, 0x41, 0x09, 0x05, 0xa1, 0x01, 0x05, 0x01, 0x09, 0x01, 0xa1, 0x00, 0x09,
                    0x30, 0x09, 0x31, 0x15, 0x00, 0x26, 0xff, 0x00, 0x75, 0x08, 0x95, 0x02, 0x81,
                    0x02, 0xc0, 0x05, 0x09, 0x19, 0x01, 0x29, 0x0a, 0x15, 0x00, 0x25, 0x01, 0x75,
                    0x01, 0x95, 0x0a, 0x81, 0x02, 0x95, 0x01, 0x75, 0x06, 0x81, 0x01, 0xc0
                  ]
                }
                """.replace("ID", expectedPath.equals(HIDRAW_NODE_0) ? "0" : "1");

        final int expectedDeviceCount;
        synchronized (mDeviceWaitObject) {
            expectedDeviceCount = mDeviceCount + 1;
        }
        ParcelFileDescriptor hidCommandInput = sUiAutomation.executeShellCommandRw("hid -")[1];
        OutputStream hidCommand =
                new ParcelFileDescriptor.AutoCloseOutputStream(hidCommandInput);
        hidCommand.write(registerCommand.getBytes());
        hidCommand.flush();
        TestUtils.waitOn(mDeviceWaitObject, () -> {
            synchronized (mDeviceWaitObject) {
                return mDeviceCount == expectedDeviceCount;
            }
        }, CALLBACK_TIMEOUT_MS, "Expected HIDRAW device to be created");
        return hidCommand;
    }

    /**
     * Runs a shell command as the {@code system} user.
     *
     * <p>Supports redirection (e.g. {@code echo hello > file}) and returns the output as a String.
     */
    private static String executeSystemShellCommand(String command) throws Exception {
        // The standard UiAutomation#executeShellCommand is implemented by Runtime#exec
        // which doesn't support using redirection.
        // This implementation works around this by executing `sh` directly, and then
        // providing the requested shell command as stdin for `sh`.
        ParcelFileDescriptor[] stdoutStdin = sUiAutomation.executeShellCommandRw("su system sh");
        ParcelFileDescriptor stdout = stdoutStdin[0];
        ParcelFileDescriptor stdin = stdoutStdin[1];
        try (OutputStream stream = new ParcelFileDescriptor.AutoCloseOutputStream(stdin)) {
            stream.write(command.getBytes());
        }
        try (InputStream stream = new ParcelFileDescriptor.AutoCloseInputStream(stdout)) {
            return new String(stream.readAllBytes());
        }
    }

    private static String createFakeHidrawNode(String name) throws Exception {
        String path = Path.of(FAKE_HIDRAW_DIR, name).toString();
        executeSystemShellCommand("touch " + path);
        TestUtils.waitUntil(path + " should exist", () ->
                !executeSystemShellCommand("ls " + path).isEmpty());
        return path;
    }

    private static byte[] expectConnectionSuccess(BrailleDisplayController controller,
            Executor executor, BluetoothDevice device, Consumer<byte[]> onInput,
            Runnable onDisconnected) throws Exception {
        final AtomicReference<byte[]> connectedDeviceDescriptor = new AtomicReference<>();
        BrailleDisplayController.BrailleDisplayCallback callback =
                new TestBrailleDisplayCallback() {
                    @Override
                    public void onConnected(@NonNull byte[] hidDescriptor) {
                        connectedDeviceDescriptor.set(hidDescriptor);
                    }

                    @Override
                    public void onInput(@NonNull byte[] input) {
                        if (onInput != null) {
                            onInput.accept(input);
                        }
                    }

                    @Override
                    public void onDisconnected() {
                        if (onDisconnected != null) {
                            onDisconnected.run();
                        }
                    }
                };
        if (executor == null) {
            controller.connect(device, callback);
        } else {
            controller.connect(device, executor, callback);
        }


        TestUtils.waitUntil("Expected connection success", (int) CALLBACK_TIMEOUT_MS / 1000,
                () -> connectedDeviceDescriptor.get() != null);
        return connectedDeviceDescriptor.get();
    }

    private static byte[] expectConnectionSuccess(BrailleDisplayController controller,
            Executor executor, BluetoothDevice device) throws Exception {
        return expectConnectionSuccess(controller, executor, device, bytes -> {
        }, () -> {
        });
    }

    private static int expectConnectionFailed(BrailleDisplayController controller,
            Executor executor, BluetoothDevice device) throws Exception {
        final AtomicInteger errorCode = new AtomicInteger(0);
        BrailleDisplayController.BrailleDisplayCallback callback =
                new TestBrailleDisplayCallback() {
                    @Override
                    public void onConnectionFailed(int error) {
                        errorCode.set(error);
                    }
                };
        controller.connect(device, executor, callback);

        TestUtils.waitUntil("Expected connection failed", (int) CALLBACK_TIMEOUT_MS / 1000,
                () -> errorCode.get() != 0);
        return errorCode.get();
    }

    private static void expectFileContents(String filePath, String expectedFileContents)
            throws Exception {
        AtomicReference<String> fileContents = new AtomicReference<>();
        try {
            TestUtils.waitUntil("",
                    (int) (CALLBACK_TIMEOUT_MS / 1000),
                    () -> {
                        fileContents.set(executeSystemShellCommand("cat " + filePath));
                        return expectedFileContents.equals(fileContents.get());
                    });
        } catch (AssertionFailedError e) {
            // TestUtils.waitUntil(String, ...) requires a constant error message before failure
            // even occurs, so use a try-catch to append a more useful error message built from
            // the last known file contents after failure.
            throw new AssertionFailedError("Expected output '" + expectedFileContents
                    + "', received '" + fileContents.get() + "'\n" + e.getMessage());
        }
    }

    @Test
    @ApiTest(apis = {
            "android.accessibilityservice.BrailleDisplayController#connect",
            "android.accessibilityservice.BrailleDisplayController"
                    + ".BrailleDisplayCallback#onConnected",
            "android.accessibilityservice.BrailleDisplayController#isConnected",
    })
    public void connect() throws Exception {
        try (OutputStream testHidrawNode = createTestHidrawNode(HIDRAW_NODE_0)) {
            Bundle testBD = getTestBrailleDisplay(HIDRAW_NODE_0, DESCRIPTOR1, BT_ADDRESS1, true);
            setTestData(List.of(testBD));

            byte[] connectedDeviceDescriptor = expectConnectionSuccess(mController, mExecutor,
                    mBluetoothDevice1);

            assertThat(connectedDeviceDescriptor).isEqualTo(DESCRIPTOR1);
            assertThat(mController.isConnected()).isTrue();
        }
    }

    @Test
    @ApiTest(apis = {
            "android.accessibilityservice.BrailleDisplayController#connect",
            "android.accessibilityservice.BrailleDisplayController"
                    + ".BrailleDisplayCallback#onConnected",
            "android.accessibilityservice.BrailleDisplayController#isConnected",
    })
    public void connect_defaultExecutor_isSuccessful() throws Exception {
        try (OutputStream testHidrawNode = createTestHidrawNode(HIDRAW_NODE_0)) {
            Bundle testBD = getTestBrailleDisplay(HIDRAW_NODE_0, DESCRIPTOR1, BT_ADDRESS1, true);
            setTestData(List.of(testBD));

            byte[] connectedDeviceDescriptor = expectConnectionSuccess(mController, /*executor=*/
                    null,
                    mBluetoothDevice1);

            assertThat(connectedDeviceDescriptor).isEqualTo(DESCRIPTOR1);
            assertThat(mController.isConnected()).isTrue();
        }
    }

    @Test
    @ApiTest(apis = {
            "android.accessibilityservice.BrailleDisplayController#connect",
            "android.accessibilityservice.BrailleDisplayController"
                    + ".BrailleDisplayCallback#onConnected",
            "android.accessibilityservice.BrailleDisplayController"
                    + ".BrailleDisplayCallback#onConnectionFailed",
    })
    public void connect_alreadyConnected_throwsIllegalStateException()
            throws Exception {
        try (OutputStream testHidrawNode = createTestHidrawNode(HIDRAW_NODE_0)) {
            Bundle testBD = getTestBrailleDisplay(HIDRAW_NODE_0, DESCRIPTOR1, BT_ADDRESS1, true);
            setTestData(List.of(testBD));
            expectConnectionSuccess(mController, mExecutor, mBluetoothDevice1);

            assertThrows(IllegalStateException.class,
                    () -> mController.connect(mBluetoothDevice1, mExecutor,
                            new TestBrailleDisplayCallback()));
        }
    }

    @Test
    @ApiTest(apis = {
            "android.accessibilityservice.BrailleDisplayController#connect",
            "android.accessibilityservice.BrailleDisplayController"
                    + ".BrailleDisplayCallback#onConnectionFailed",
            "android.accessibilityservice.BrailleDisplayController"
                    + ".BrailleDisplayCallback#FLAG_ERROR_BRAILLE_DISPLAY_NOT_FOUND",
    })
    public void connect_wrongBusType_returnsNotFoundError() throws Exception {
        try (OutputStream testHidrawNode = createTestHidrawNode(HIDRAW_NODE_0)) {
            Bundle testBD = getTestBrailleDisplay(HIDRAW_NODE_0, DESCRIPTOR1, BT_ADDRESS1,
                    /*isBluetooth=*/false);
            setTestData(List.of(testBD));

            int errorCode = expectConnectionFailed(mController, mExecutor, mBluetoothDevice1);

            assertThat(errorCode).isEqualTo(FLAG_ERROR_BRAILLE_DISPLAY_NOT_FOUND);
        }
    }

    @Test
    @ApiTest(apis = {
            "android.accessibilityservice.BrailleDisplayController#connect",
            "android.accessibilityservice.BrailleDisplayController"
                    + ".BrailleDisplayCallback#onConnectionFailed",
            "android.accessibilityservice.BrailleDisplayController"
                    + ".BrailleDisplayCallback#FLAG_ERROR_BRAILLE_DISPLAY_NOT_FOUND",
    })
    public void connect_wrongUniq_returnsNotFoundError() throws Exception {
        try (OutputStream testHidrawNode = createTestHidrawNode(HIDRAW_NODE_0)) {
            String wrongUniq = mBluetoothDevice1.getAddress() + "_extra";
            Bundle testBD = getTestBrailleDisplay(HIDRAW_NODE_0, DESCRIPTOR1, wrongUniq, true);
            setTestData(List.of(testBD));

            int errorCode = expectConnectionFailed(mController, mExecutor, mBluetoothDevice1);

            assertThat(errorCode).isEqualTo(FLAG_ERROR_BRAILLE_DISPLAY_NOT_FOUND);
        }
    }

    @Test
    @ApiTest(apis = {
            "android.accessibilityservice.BrailleDisplayController#connect",
            "android.accessibilityservice.BrailleDisplayController"
                    + ".BrailleDisplayCallback#onConnectionFailed",
            "android.accessibilityservice.BrailleDisplayController"
                    + ".BrailleDisplayCallback#FLAG_ERROR_BRAILLE_DISPLAY_NOT_FOUND",
    })
    public void connect_nonBrailleDisplayDescriptor_returnsNotFoundError()
            throws Exception {
        try (OutputStream testHidrawNode = createTestHidrawNode(HIDRAW_NODE_0)) {
            final byte[] nonBrailleDisplayDescriptor = {0x05, 0x01 /* != 0x41 */};
            Bundle testBD = getTestBrailleDisplay(
                    HIDRAW_NODE_0, nonBrailleDisplayDescriptor, BT_ADDRESS1, true);
            setTestData(List.of(testBD));

            int errorCode = expectConnectionFailed(mController, mExecutor, mBluetoothDevice1);

            assertThat(errorCode).isEqualTo(FLAG_ERROR_BRAILLE_DISPLAY_NOT_FOUND);
        }
    }

    @Test
    @ApiTest(apis = {
            "android.accessibilityservice.BrailleDisplayController#connect",
            "android.accessibilityservice.BrailleDisplayController"
                    + ".BrailleDisplayCallback#onConnected",
    })
    public void connect_multipleDevices_returnsCorrectDevice() throws Exception {
        try (
                OutputStream testHidrawNode0 = createTestHidrawNode(HIDRAW_NODE_0);
                OutputStream testHidrawNode1 = createTestHidrawNode(HIDRAW_NODE_1)
        ) {
            Bundle testBD1 = getTestBrailleDisplay(HIDRAW_NODE_0, DESCRIPTOR1, BT_ADDRESS1, true);
            Bundle testBD2 = getTestBrailleDisplay(HIDRAW_NODE_1, DESCRIPTOR2, BT_ADDRESS2, true);
            setTestData(List.of(testBD1, testBD2));

            byte[] connectedDeviceDescriptor = expectConnectionSuccess(mController, mExecutor,
                    mBluetoothDevice2);

            assertThat(connectedDeviceDescriptor).isEqualTo(DESCRIPTOR2);
            assertThat(mController.isConnected()).isTrue();
        }
    }

    @Test
    @ApiTest(apis = {
            "android.accessibilityservice.BrailleDisplayController#connect",
            "android.accessibilityservice.BrailleDisplayController"
                    + ".BrailleDisplayCallback#onConnectionFailed",
            "android.accessibilityservice.BrailleDisplayController"
                    + ".BrailleDisplayCallback#FLAG_ERROR_BRAILLE_DISPLAY_NOT_FOUND",
    })
    public void connect_multipleIdenticalDevices_returnsNotFoundError() throws Exception {
        try (
                OutputStream testHidrawNode0 = createTestHidrawNode(HIDRAW_NODE_0);
                OutputStream testHidrawNode1 = createTestHidrawNode(HIDRAW_NODE_1)
        ) {
            Bundle testBD1 = getTestBrailleDisplay(HIDRAW_NODE_0, DESCRIPTOR1, BT_ADDRESS1, true);
            // BD2 copies all BD1 device properties, but is exposed as a different HIDRAW node path.
            Bundle testBD2 = testBD1.deepCopy();
            testBD2.putString(BrailleDisplayController.TEST_BRAILLE_DISPLAY_HIDRAW_PATH,
                    HIDRAW_NODE_1);
            setTestData(List.of(testBD1, testBD2));

            int errorCode = expectConnectionFailed(mController, mExecutor, mBluetoothDevice1);

            assertThat(errorCode).isEqualTo(FLAG_ERROR_BRAILLE_DISPLAY_NOT_FOUND);
        }
    }

    @Test
    @ApiTest(apis = {
            "android.accessibilityservice.BrailleDisplayController#connect",
            "android.accessibilityservice.BrailleDisplayController"
                    + ".BrailleDisplayCallback#onConnectionFailed",
            "android.accessibilityservice.BrailleDisplayController"
                    + ".BrailleDisplayCallback#FLAG_ERROR_BRAILLE_DISPLAY_NOT_FOUND",
    })
    public void connect_multipleServicesSameBrailleDisplay_returnsNotFoundError() throws Exception {
        InstrumentedAccessibilityService anotherService =
                InstrumentedAccessibilityService.enableService(
                        InstrumentedAccessibilityService.class);
        try {
            try (OutputStream testHidrawNode = createTestHidrawNode(HIDRAW_NODE_0)) {
                Bundle testBD = getTestBrailleDisplay(HIDRAW_NODE_0, DESCRIPTOR1, BT_ADDRESS1,
                        true);
                setTestData(mService, List.of(testBD));
                setTestData(anotherService, List.of(testBD));

                // Connect another service to the Braille display first.
                expectConnectionSuccess(anotherService.getBrailleDisplayController(),
                        anotherService.getMainExecutor(), mBluetoothDevice1);
                // Attempt to connect a different service to the same Braille display.
                int errorCode = expectConnectionFailed(mController, mExecutor, mBluetoothDevice1);

                assertThat(anotherService.getBrailleDisplayController().isConnected()).isTrue();
                assertThat(mController.isConnected()).isFalse();
                assertThat(errorCode).isEqualTo(FLAG_ERROR_BRAILLE_DISPLAY_NOT_FOUND);
            }
        } finally {
            anotherService.getBrailleDisplayController().disconnect();
            anotherService.disableSelfAndRemove();
        }
    }

    @Test
    @ApiTest(apis = {
            "android.accessibilityservice.BrailleDisplayController#connect",
            "android.accessibilityservice.BrailleDisplayController"
                    + ".BrailleDisplayCallback#onConnected",
            "android.accessibilityservice.BrailleDisplayController"
                    + ".BrailleDisplayCallback#onConnectionFailed",
    })
    public void connect_canConnectAfterFailedConnection() throws Exception {
        try (OutputStream testHidrawNode = createTestHidrawNode(HIDRAW_NODE_0)) {
            Bundle testBD = getTestBrailleDisplay(HIDRAW_NODE_0, DESCRIPTOR1, BT_ADDRESS1, true);
            setTestData(List.of(testBD));

            expectConnectionFailed(mController, mExecutor, mBluetoothDevice2);
            expectConnectionSuccess(mController, mExecutor, mBluetoothDevice1);
        }
    }

    @Test
    @ApiTest(apis = {
            "android.accessibilityservice.BrailleDisplayController#connect",
            "android.accessibilityservice.BrailleDisplayController"
                    + ".BrailleDisplayCallback#onConnectionFailed",
            "android.accessibilityservice.BrailleDisplayController"
                    + ".BrailleDisplayCallback#FLAG_ERROR_CANNOT_ACCESS",
    })
    public void connect_unableToGetHidrawNodePaths_returnsCannotAccessError() throws Exception {
        // BrailleDisplayScanner#getHidrawNodePaths returns null when test data is empty.
        setTestData(List.of());

        int errorCode = expectConnectionFailed(mController, mExecutor, mBluetoothDevice1);

        assertThat(errorCode).isEqualTo(FLAG_ERROR_CANNOT_ACCESS);
    }

    @Test
    @ApiTest(apis = {
            "android.accessibilityservice.BrailleDisplayController#connect",
            "android.accessibilityservice.BrailleDisplayController"
                    + ".BrailleDisplayCallback#onConnectionFailed",
            "android.accessibilityservice.BrailleDisplayController"
                    + ".BrailleDisplayCallback#FLAG_ERROR_CANNOT_ACCESS",
            "android.accessibilityservice.BrailleDisplayController"
                    + ".BrailleDisplayCallback#FLAG_ERROR_BRAILLE_DISPLAY_NOT_FOUND",
    })
    public void connect_unableToGetReportDescriptor_returnsErrors() throws Exception {
        try (OutputStream testHidrawNode = createTestHidrawNode(HIDRAW_NODE_0)) {
            Bundle testBD = getTestBrailleDisplay(HIDRAW_NODE_0, /*descriptor=*/null, BT_ADDRESS1,
                    true);
            setTestData(List.of(testBD));

            int errorCode = expectConnectionFailed(mController, mExecutor, mBluetoothDevice1);

            assertThat(errorCode).isEqualTo(
                    FLAG_ERROR_CANNOT_ACCESS | FLAG_ERROR_BRAILLE_DISPLAY_NOT_FOUND);
        }
    }

    // TODO: b/316035785 - Change this to a CTS-Verifier test, requiring >=1 connected USB device
    @Test
    @ApiTest(apis = {
            "android.accessibilityservice.BrailleDisplayController#connect",
    })
    public void connect_realUsbDevice_noPermission_throwsSecurityException() {
        // Unlike BluetoothDevice used throughout other tests, UsbDevice does not have a test
        // constructor, so we can only act on real USB devices in a test.
        //
        // It is unlikely that a CTS test environment will have a real Braille display available,
        // but we can at least test the security behavior of connect(UsbDevice) by
        // attempting to connect to any USB device that is not approved for this test app.
        //
        // All logic after the initial security check is shared between USB and Bluetooth devices.
        UsbManager usbManager = sInstrumentation.getContext().getSystemService(UsbManager.class);
        assumeTrue(usbManager != null);
        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        assumeFalse(deviceList.isEmpty());
        boolean foundUnapprovedDevice = false;
        for (UsbDevice usbDevice : deviceList.values()) {
            if (!usbManager.hasPermission(usbDevice)) {
                foundUnapprovedDevice = true;
                assertThrows(SecurityException.class,
                        () -> mController.connect(usbDevice, mExecutor,
                                new TestBrailleDisplayCallback()));
            }
        }
        assertThat(foundUnapprovedDevice).isTrue();
    }

    @Test
    @ApiTest(apis = {
            "android.accessibilityservice.BrailleDisplayController#connect",
            "android.accessibilityservice.BrailleDisplayController#disconnect",
            "android.accessibilityservice.BrailleDisplayController#isConnected",
    })
    public void connect_allowsReconnectionAfterDisconnect() throws Exception {
        // This test checks that we can reconnect after disconnection, so prepare the
        // test state by calling another test that already connects & disconnects.
        disconnect_disconnectsExistingConnection();
        TestUtils.waitOn(mDeviceWaitObject, () -> {
            synchronized (mDeviceWaitObject) {
                return mDeviceCount == 0;
            }
        }, CALLBACK_TIMEOUT_MS, "Expected all HIDRAW devices removed");

        try (OutputStream testHidrawNode = createTestHidrawNode(HIDRAW_NODE_0)) {
            expectConnectionSuccess(mController, mExecutor, mBluetoothDevice1);
            assertThat(mController.isConnected()).isTrue();
        }
    }

    @Test
    @ApiTest(apis = {
            "android.accessibilityservice.BrailleDisplayController#disconnect",
            "android.accessibilityservice.BrailleDisplayController"
                    + ".BrailleDisplayCallback#onDisconnected",
    })
    public void disconnect_disconnectsExistingConnection() throws Exception {
        try (OutputStream testHidrawNode = createTestHidrawNode(HIDRAW_NODE_0)) {
            AtomicBoolean calledDisconnected = new AtomicBoolean();
            Bundle testBD = getTestBrailleDisplay(HIDRAW_NODE_0, DESCRIPTOR1, BT_ADDRESS1, true);
            setTestData(List.of(testBD));
            expectConnectionSuccess(mController, mExecutor, mBluetoothDevice1, null,
                    () -> calledDisconnected.set(true));

            mController.disconnect();

            TestUtils.waitUntil("Expected disconnection", (int) CALLBACK_TIMEOUT_MS / 1000,
                    calledDisconnected::get);
            assertThat(mController.isConnected()).isFalse();
        }
    }

    @Test
    @ApiTest(apis = {
            "android.accessibilityservice.BrailleDisplayController#disconnect",
    })
    public void disconnect_notConnected_nothingHappens() {
        mController.disconnect();

        assertThat(mController.isConnected()).isFalse();
    }

    @Test
    @ApiTest(apis = {
            "android.accessibilityservice.BrailleDisplayController"
                    + ".BrailleDisplayCallback#onDisconnected",
    })
    public void deviceIsRemoved_callsOnBrailleDisplayDisconnected() throws Exception {
        AtomicBoolean calledDisconnected = new AtomicBoolean();
        try (OutputStream testHidrawNode = createTestHidrawNode(HIDRAW_NODE_0)) {
            Bundle testBD = getTestBrailleDisplay(HIDRAW_NODE_0, DESCRIPTOR1, BT_ADDRESS1, true);
            setTestData(List.of(testBD));
            expectConnectionSuccess(mController, mExecutor, mBluetoothDevice1, null,
                    () -> calledDisconnected.set(true));

            // Closing the OutputStream stops the `hid` command, and stopping the
            // `hid` command causes the HIDRAW node it created to be removed.
            testHidrawNode.close();

            TestUtils.waitUntil("Expected disconnection", (int) CALLBACK_TIMEOUT_MS / 1000,
                    calledDisconnected::get);
        }
    }

    @Test
    @ApiTest(apis = {
            "android.accessibilityservice.BrailleDisplayController.BrailleDisplayCallback#onInput",
    })
    public void onInput() throws Exception {
        assumeTrue("Test requires debug build", mIsUserdebugOrEng);
        String input1 = "hello", input2 = "world";
        Object waitObject = new Object();
        List<byte[]> receivedInputBytes = new ArrayList<>();
        String hidraw1 = createFakeHidrawNode("hidraw1");
        Bundle testBD = getTestBrailleDisplay(hidraw1, DESCRIPTOR1, BT_ADDRESS1, true);
        setTestData(List.of(testBD));
        expectConnectionSuccess(mController, mExecutor, mBluetoothDevice1, bytes -> {
            synchronized (waitObject) {
                receivedInputBytes.add(bytes);
                waitObject.notifyAll();
            }
        }, null);

        // Fill the Braille display "device" (test file) with two input messages
        // that arrive one second apart.
        executeSystemShellCommand("echo -n " + input1 + " >> " + hidraw1
                + " && sleep 1 && "
                + "echo -n " + input2 + " >> " + hidraw1);

        // Expect that both are individually received.
        TestUtils.waitOn(waitObject, () -> receivedInputBytes.size() == 2,
                CALLBACK_TIMEOUT_MS,
                "Expected to receive 2 calls to onInput");
        assertThat(receivedInputBytes.get(0)).isEqualTo(input1.getBytes());
        assertThat(receivedInputBytes.get(1)).isEqualTo(input2.getBytes());
    }

    @Test
    @ApiTest(apis = {
            "android.accessibilityservice.BrailleDisplayController#write",
    })
    public void write() throws Exception {
        assumeTrue("Test requires debug build", mIsUserdebugOrEng);
        String output1 = "hello", output2 = "world";
        String hidraw1 = createFakeHidrawNode("hidraw1");
        Bundle testBD = getTestBrailleDisplay(hidraw1, DESCRIPTOR1, BT_ADDRESS1, true);
        setTestData(List.of(testBD));
        expectConnectionSuccess(mController, mExecutor, mBluetoothDevice1);

        mController.write(output1.getBytes());
        mController.write(output2.getBytes());

        expectFileContents(hidraw1, output1 + output2);
    }

    @Test
    @ApiTest(apis = {
            "android.accessibilityservice.BrailleDisplayController#write",
    })
    public void write_largeOutput_throwsForLargeOutput() throws Exception {
        assumeTrue("Test requires debug build", mIsUserdebugOrEng);
        AtomicBoolean calledDisconnected = new AtomicBoolean();
        String regularOutput = "ABC";
        String largeOutput = "A".repeat(IBinder.getSuggestedMaxIpcSizeBytes() * 2);
        String hidraw1 = createFakeHidrawNode("hidraw1");
        Bundle testBD = getTestBrailleDisplay(hidraw1, DESCRIPTOR1, BT_ADDRESS1, true);
        setTestData(List.of(testBD));
        expectConnectionSuccess(mController, mExecutor, mBluetoothDevice1, null,
                () -> calledDisconnected.set(true));

        assertThrows(IllegalArgumentException.class,
                () -> mController.write(largeOutput.getBytes()));
        mController.write(regularOutput.getBytes());

        expectFileContents(hidraw1, regularOutput);
    }

    @Test
    @ApiTest(apis = {
            "android.accessibilityservice.BrailleDisplayController#write",
    })
    public void write_notConnected_throwsIfNotConnected() {
        assertThrows(IOException.class, () -> mController.write("hello".getBytes()));
        // No connected HIDRAW device file, so nothing to assert is empty.
    }
}
