/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.jdwptunnel.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.util.AbiUtils;
import com.android.tradefed.util.RunUtil;

import com.sun.jdi.Bootstrap;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.VirtualMachineManager;
import com.sun.jdi.connect.AttachingConnector;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.event.ClassPrepareEvent;
import com.sun.jdi.request.BreakpointRequest;
import com.sun.jdi.request.ClassPrepareRequest;
import com.sun.jdi.request.EventRequest;
import com.sun.jdi.request.EventRequestManager;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.time.Instant;
import java.util.Map;


/**
 * Host-side tests for setting up a JDWP connection to an app.
 *
 * <p>This test ensures that it is possible to attach a debugger to an app using 'adb' and perform
 * at least some basic debugging actions.
 *
 * <p>The {@link DebuggableSampleDeviceActivity} is the activity we are debugging.
 *
 * <p>We will start that activity with 'wait-for-debugger', set a breakpoint on the first line of
 * the {@code onCreate} method and wait for the breakpoint to be hit.
 *
 * <p>Run with: atest CtsJdwpTunnelHostTestCases
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class JdwpTunnelTest extends BaseHostJUnit4Test {
    private static final String DEBUGGABLE_TEST_APP_PACKAGE_NAME =
      "android.jdwptunnel.sampleapp.debuggable";
    private static final String DEBUGGABLE_TEST_APP_ACTIVITY_CLASS_NAME =
      "DebuggableSampleDeviceActivity";
    private static final String PROFILEABLE_TEST_APP_PACKAGE_NAME =
      "android.jdwptunnel.sampleapp.profileable";
    private static final String PROFILEABLE_TEST_APP_ACTIVITY_CLASS_NAME =
      "ProfileableSampleDeviceActivity";
    private static final String DDMS_TEST_APP_PACKAGE_NAME = "android.jdwptunnel.sampleapp.ddms";
    private static final String DDMS_TEST_APP_ACTIVITY_CLASS_NAME = "DdmsSampleDeviceActivity";

    private ITestDevice mDevice;

    @Before
    public void setUp() throws Exception {
        installPackage("CtsJdwpTunnelDebuggableSampleApp.apk");
        installPackage("CtsJdwpTunnelProfileableSampleApp.apk");
        installPackage("CtsJdwpTunnelDdmsSampleApp.apk");
        mDevice = getDevice();
    }

    private void moveToHomeScreen() throws Exception {
        // Wakeup the device if it is on the lockscreen and move it to the home screen.
        mDevice.executeShellCommand("input keyevent KEYCODE_WAKEUP");
        mDevice.executeShellCommand("wm dismiss-keyguard");
        mDevice.executeShellCommand("input keyevent KEYCODE_HOME");
    }

    private VirtualMachine getDebuggerConnection(String port) throws Exception {
        VirtualMachineManager vmm = Bootstrap.virtualMachineManager();
        AttachingConnector conn =
                vmm.attachingConnectors().stream()
                        .filter((x) -> x.transport().name().equals("dt_socket"))
                        .findFirst()
                        .orElseThrow(
                                () -> new Error("Could not find dt_socket connector!"));
        Map<String, Connector.Argument> params = conn.defaultArguments();
        params.get("port").setValue(port);
        params.get("hostname").setValue("localhost");
        // Timeout after 1 minute
        params.get("timeout").setValue("60000");
        return conn.attach(params);
    }

    private String forwardJdwp(String pid) throws Exception {
        // Try to have adb figure out the port number.
        String result = mDevice.executeAdbCommand("forward", "tcp:0", "jdwp:" + pid);
        if (result != null) {
            return result.trim();
        }
        // We might be using an ancient adb. Try using a static port number instead. Number chosen
        // arbitrarially. '15002' does not appear in any file as anything resembling a port number
        // as far as I can tell.
        final String port = "15002";
        result = mDevice.executeAdbCommand("forward", "tcp:" + port, "jdwp:" + pid);
        assertTrue(result != null);
        return port;
    }

    private String startupForwarding(String packageName, String shortClassName, boolean debug)
          throws Exception {
        moveToHomeScreen();
        new Thread(() -> {
            try {
                mDevice.executeShellCommand(
                        "cmd activity start-activity "
                        + (debug ? "-D" : "")
                        + " -W -n "
                        + packageName
                        + "/."
                        + shortClassName);
            } catch (DeviceNotAvailableException e) {
                CLog.i("Failed to start activity for package: " + packageName, e);
            }
        }).start();

        // Don't keep trying after a minute.
        final Instant deadline = Instant.now().plusSeconds(60);
        String pid = "";
        while ((pid = mDevice.executeShellCommand(
                    "pidof " + packageName).trim()).equals("")) {
            if (Instant.now().isAfter(deadline)) {
                fail("Unable to find PID of " + packageName + " process!");
            }
            // Wait 1 second and try again.
            RunUtil.getDefault().sleep(1000);
        }
        String port = forwardJdwp(pid);
        assertTrue(!"".equals(port));
        return port;
    }

    private VirtualMachine startupTest(String packageName, String shortClassName) throws Exception {
      return getDebuggerConnection(startupForwarding(packageName, shortClassName, true));
    }

    /**
     * Tests that we can attach a debugger and perform basic debugging functions.
     *
     * We start the app with Wait-for-debugger. Wait for the ClassPrepare of the activity class and
     * put and wait for a breakpoint on the onCreate function.
     *
     * TODO: We should expand this to more functions.
     */
    private void testAttachDebugger(String packageName, String shortClassName)
      throws DeviceNotAvailableException, Exception {
        String fullClassName = packageName + "." + shortClassName;

        VirtualMachine vm = startupTest(packageName, shortClassName);
        EventRequestManager erm = vm.eventRequestManager();
        try {
            // Just pause the runtime so it won't get ahead of us while we setup everything.
            vm.suspend();
            // Overall timeout for this whole test. 2-minutes
            final Instant deadline = Instant.now().plusSeconds(120);
            // Check the test-activity class is not already loaded.
            assertTrue(shortClassName + " is not yet loaded!",
                    vm.allClasses().stream()
                            .noneMatch(x -> x.name().equals(fullClassName)));

            // Wait for the class to load.
            ClassPrepareRequest cpr = erm.createClassPrepareRequest();
            cpr.addClassFilter(fullClassName);
            cpr.setSuspendPolicy(EventRequest.SUSPEND_ALL);
            cpr.enable();
            vm.resume();
            ReferenceType activityType = null;
            while (activityType == null) {
                if (Instant.now().isAfter(deadline)) {
                    fail(fullClassName + " did not load within timeout!");
                }
                activityType = vm.eventQueue().remove().stream()
                        .filter(e -> cpr == e.request())
                        .findFirst()
                        .map(e -> ((ClassPrepareEvent) e).referenceType())
                        .orElse(null);
            }
            cpr.disable();
            // Set a breakpoint on the onCreate method at the first line.
            BreakpointRequest bpr = erm.createBreakpointRequest(
                    activityType.methodsByName("onCreate").get(0).allLineLocations().get(0));
            bpr.setSuspendPolicy(EventRequest.SUSPEND_ALL);
            bpr.enable();
            vm.resume();

            // Wait for the event.
            while (!vm.eventQueue().remove().stream().anyMatch(e -> e.request() == bpr)) {
                if (Instant.now().isAfter(deadline)) {
                    fail(fullClassName + " did hit onCreate breakpoint within timeout!");
                }
            }
            bpr.disable();
            vm.resume();
        } finally {
            // Always cleanup.
            vm.dispose();
        }
    }

    /**
     * Tests that we can attach a debugger and perform basic debugging functions to a
     * debuggable app.
     *
     * We start the app with Wait-for-debugger. Wait for the ClassPrepare of the activity
     * class and put and wait for a breakpoint on the onCreate function.
     */
    @Test
    public void testAttachDebuggerToDebuggableApp() throws DeviceNotAvailableException, Exception {
        testAttachDebugger(DEBUGGABLE_TEST_APP_PACKAGE_NAME,
                           DEBUGGABLE_TEST_APP_ACTIVITY_CLASS_NAME);
    }

    /**
     * Tests that we CANNOT attach a debugger to a non-debuggable-but-profileable app.
     *
     * We test the attempt to attach the debuggable should fail on a user build device at the
     * expected API call.
     */
    @Test
    public void testAttachDebuggerToProfileableApp() throws DeviceNotAvailableException, Exception {
        java.io.IOException thrownException = null;
        try {
            testAttachDebugger(PROFILEABLE_TEST_APP_PACKAGE_NAME,
                               PROFILEABLE_TEST_APP_ACTIVITY_CLASS_NAME);
        } catch (java.io.IOException e) {
            thrownException = e;
        }
        // Jdwp is only enabled on eng builds or when persist.debug.dalvik.vm.jdwp.enabled is set.
        // Check that we are able to attach a debugger in these cases.
        String buildType = mDevice.getProperty("ro.build.type");
        String enableJdwp = mDevice.getProperty("persist.debug.dalvik.vm.jdwp.enabled");
        if (buildType.equals("eng") || (enableJdwp != null && enableJdwp.equals("1"))) {
            assertNull(thrownException);
            return;
        }
        // We are on a device that doesn't enable jdwp.
        assertNotNull(thrownException);
        if (thrownException != null) {
            // Verify the exception is thrown from the "getDebuggerConnection" method in this test
            // when it calls the "attach" method from class AttachingConnector or its subclass.
            // In other words, the callstack is expected to look like
            //
            // at jdk.jdi/com.sun.tools.jdi.SocketAttachingConnector.attach(SocketAttachingConnector.java:83)
            // at android.jdwptunnel.cts.JdwpTunnelTest.getDebuggerConnection(JdwpTunnelTest.java:96)
            boolean thrownByGetDebuggerConnection = false;
            StackTraceElement[] stack = thrownException.getStackTrace();
            for (int i = 0; i < stack.length; i++) {
                if (stack[i].getClassName().equals("android.jdwptunnel.cts.JdwpTunnelTest") &&
                    stack[i].getMethodName().equals("getDebuggerConnection")) {
                    thrownByGetDebuggerConnection = true;
                    assertTrue(i > 0);
                    assertEquals("attach", stack[i - 1].getMethodName());
                    break;
                }
            }
            assertTrue(thrownByGetDebuggerConnection);
        }
    }

    private String getDeviceBaseArch() throws Exception {
        String abi = mDevice.executeShellCommand("getprop ro.product.cpu.abi").replace("\n", "");
        return AbiUtils.getBaseArchForAbi(abi);
    }

    /**
     * Tests that we don't get any DDMS messages before the handshake.
     *
     * <p>Since DDMS can send asynchronous replies it could race with the JDWP handshake. This could
     * confuse clients. See bug: 178655046
     */
    @Test
    public void testDdmsWaitsForHandshake() throws DeviceNotAvailableException, Exception {
        // Skip this test if not running on the device's native abi.
        String testingArch = AbiUtils.getBaseArchForAbi(getAbi().getName());
        String deviceArch = getDeviceBaseArch();
        Assume.assumeTrue(testingArch.equals(deviceArch));

        String port =
            startupForwarding(DDMS_TEST_APP_PACKAGE_NAME, DDMS_TEST_APP_ACTIVITY_CLASS_NAME, false);
        Socket sock = new Socket("localhost", Integer.decode(port).intValue());
        OutputStream os = sock.getOutputStream();
        // Let the test spin a bit. Try to lose any race with the app.
        RunUtil.getDefault().sleep(1000);
        String handshake = "JDWP-Handshake";
        byte[] handshake_bytes = handshake.getBytes("US-ASCII");
        os.write(handshake_bytes);
        os.flush();
        InputStream is = sock.getInputStream();
        // Make sure we get the handshake first.
        for (byte b : handshake_bytes) {
            assertEquals(b, is.read());
        }

        // Don't require anything in particular next since lots of things can send
        // DDMS packets. Since there is no debugger connection we can assert that
        // it is a DDMS packet at least by looking for a negative id.
        // Skip the length
        is.skip(4);
        // Data sent big-endian so first byte has sign bit.
        assertTrue((is.read() & 0x80) == 0x80);
    }
}
