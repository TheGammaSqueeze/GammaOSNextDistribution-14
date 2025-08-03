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

package com.android.sts.common;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeNoException;
import static org.junit.Assume.assumeTrue;
import static com.android.sts.common.CommandUtil.runAndCheck;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.IOException;
import java.io.StringReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import javax.xml.xpath.XPathExpressionException;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.junit.rules.TestWatcher;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;
import com.android.tradefed.util.RunUtil;

/** TestWatcher that sets up a virtual bluetooth HAL and reboots the device once done. */
public class RootcanalUtils extends TestWatcher {
    private static final String LOCK_FILENAME = "/data/local/tmp/sts_rootcanal.lck";

    private BaseHostJUnit4Test test;
    private OverlayFsUtils overlayFsUtils;

    public RootcanalUtils(BaseHostJUnit4Test test) {
        assertNotNull(test);
        this.test = test;
        this.overlayFsUtils = new OverlayFsUtils(test);
    }

    @Override
    public void finished(Description d) {
        ITestDevice device = test.getDevice();
        assertNotNull("Device not set", device);
        try {
            device.enableAdbRoot();
            ProcessUtil.killAll(
                    device, "android\\.hardware\\.bluetooth@1\\.1-service\\.sim", 10_000, false);
            runAndCheck(device, String.format("rm -rf '%s'", LOCK_FILENAME));
            device.disableAdbRoot();
            // OverlayFsUtils' finished() will restart the device.
            overlayFsUtils.finished(d);
            device.waitForDeviceAvailable();
            CommandResult res = device.executeShellV2Command("svc bluetooth enable");
            if (res.getStatus() != CommandStatus.SUCCESS) {
                CLog.e("Could not reenable Bluetooth during cleanup!");
            }
        } catch (DeviceNotAvailableException e) {
            throw new AssertionError("Device unavailable when cleaning up", e);
        } catch (TimeoutException e) {
            CLog.w("Could not kill rootcanal HAL during cleanup");
        } catch (ProcessUtil.KillException e) {
            if (e.getReason() != ProcessUtil.KillException.Reason.NO_SUCH_PROCESS) {
                CLog.w("Could not kill rootcanal HAL during cleanup: " + e.getMessage());
            }
        }
    }

    /**
     * Replace existing HAL with RootCanal HAL on current device.
     *
     * @return an instance of RootcanalController
     */
    public RootcanalController enableRootcanal()
            throws DeviceNotAvailableException, IOException, InterruptedException {
        ITestDevice device = test.getDevice();
        assertNotNull("Device not set", device);
        assumeTrue(
                "Device does not seem to have Bluetooth",
                device.hasFeature("android.hardware.bluetooth")
                        || device.hasFeature("android.hardware.bluetooth_le"));
        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(test.getBuild());

        // Check and made sure we're not calling this more than once for a device
        assertFalse("rootcanal set up called more than once", device.doesFileExist(LOCK_FILENAME));
        device.pushString("", LOCK_FILENAME);

        // Make sure that /vendor is writable
        try {
            overlayFsUtils.makeWritable("/vendor", 100);
        } catch (IllegalStateException e) {
            CLog.w(e);
        }

        // Remove existing HAL files and push new virtual HAL files.
        runAndCheck(device, "svc bluetooth disable");
        runAndCheck(
                device,
                "rm -f /vendor/lib64/hw/android.hardware.bluetooth@* "
                        + "/vendor/lib/hw/android.hardware.bluetooth@* "
                        + "/vendor/bin/hw/android.hardware.bluetooth@* "
                        + "/vendor/etc/init/android.hardware.bluetooth@*");

        device.pushFile(
                buildHelper.getTestFile("android.hardware.bluetooth@1.1-service.sim"),
                "/vendor/bin/hw/android.hardware.bluetooth@1.1-service.sim");

        // Pushing the same lib to both 32 and 64bit lib dirs because (a) it works and
        // (b) FileUtil does not yet support "arm/lib" and "arm64/lib64" layout.
        device.pushFile(
                buildHelper.getTestFile("android.hardware.bluetooth@1.1-impl-sim.so"),
                "/vendor/lib/hw/android.hardware.bluetooth@1.1-impl-sim.so");
        device.pushFile(
                buildHelper.getTestFile("android.hardware.bluetooth@1.1-impl-sim.so"),
                "/vendor/lib64/hw/android.hardware.bluetooth@1.1-impl-sim.so");
        device.pushFile(
                buildHelper.getTestFile("android.hardware.bluetooth@1.1-service.sim.rc"),
                "/vendor/etc/init/android.hardware.bluetooth@1.1-service.sim.rc");

        // Download and patch the VINTF manifest if needed.
        tryUpdateVintfManifest(device);

        // Rootcanal expects certain libraries to be in /vendor and not /system so copy them over
        copySystemLibToVendorIfMissing("libchrome.so");
        copySystemLibToVendorIfMissing("android.hardware.bluetooth@1.1.so");
        copySystemLibToVendorIfMissing("android.hardware.bluetooth@1.0.so");

        // Fix up permissions and SELinux contexts of files pushed over
        runAndCheck(device, "chmod 755 /vendor/bin/hw/android.hardware.bluetooth@1.1-service.sim");
        runAndCheck(
                device,
                "chcon u:object_r:hal_bluetooth_default_exec:s0 "
                        + "/vendor/bin/hw/android.hardware.bluetooth@1.1-service.sim");
        runAndCheck(
                device,
                "chmod 644 "
                        + "/vendor/etc/vintf/manifest.xml "
                        + "/vendor/lib/hw/android.hardware.bluetooth@1.1-impl-sim.so "
                        + "/vendor/lib64/hw/android.hardware.bluetooth@1.1-impl-sim.so");
        runAndCheck(
                device, "chcon u:object_r:vendor_configs_file:s0 /vendor/etc/vintf/manifest.xml");
        runAndCheck(
                device,
                "chcon u:object_r:vendor_file:s0 "
                        + "/vendor/lib/hw/android.hardware.bluetooth@1.1-impl-sim.so "
                        + "/vendor/lib64/hw/android.hardware.bluetooth@1.1-impl-sim.so");

        try {
            // Kill currently running BT HAL.
            if (ProcessUtil.killAll(device, "android\\.hardware\\.bluetooth@.*", 10_000, false)) {
                CLog.d("Killed existing BT HAL");
            } else {
                CLog.w("No existing BT HAL was found running");
            }

            // Kill hwservicemanager, wait for it to come back up on its own, and wait for it
            // to finish initializing. This is needed to reload the VINTF and HAL rc information.
            // Note that a userspace reboot would not work here because hwservicemanager starts
            // before userdata is mounted.
            device.setProperty("hwservicemanager.ready", "false");
            ProcessUtil.killAll(device, "hwservicemanager$", 10_000);
            waitPropertyValue(device, "hwservicemanager.ready", "true", 10_000);
            TimeUnit.SECONDS.sleep(30);

            // Launch the new HAL
            List<String> cmd =
                    List.of(
                            "adb",
                            "-s",
                            device.getSerialNumber(),
                            "shell",
                            "/vendor/bin/hw/android.hardware.bluetooth@1.1-service.sim");
            RunUtil.getDefault().runCmdInBackground(cmd);
            ProcessUtil.waitProcessRunning(
                    device, "android\\.hardware\\.bluetooth@1\\.1-service\\.sim", 10_000);
        } catch (TimeoutException e) {
            assumeNoException("Could not start virtual BT HAL", e);
        } catch (ProcessUtil.KillException e) {
            assumeNoException("Failed to kill process", e);
        }

        // Reenable Bluetooth and enable RootCanal control channel
        String checkCmd = "netstat -l -t -n -W | grep '0\\.0\\.0\\.0:6111'";
        while (true) {
            runAndCheck(device, "svc bluetooth enable");
            runAndCheck(device, "setprop vendor.bt.rootcanal_test_console true");
            CommandResult res = device.executeShellV2Command(checkCmd);
            if (res.getStatus() == CommandStatus.SUCCESS) {
                break;
            }
        }

        // Forward root canal control ports on the device to the host
        int testPort = findOpenPort();
        device.executeAdbCommand("forward", String.format("tcp:%d", testPort), "tcp:6111");

        int hciPort = findOpenPort();
        device.executeAdbCommand("forward", String.format("tcp:%d", hciPort), "tcp:6211");

        return new RootcanalController(testPort, hciPort);
    }

    private void copySystemLibToVendorIfMissing(String filename)
            throws DeviceNotAvailableException {
        runAndCheck(
                test.getDevice(),
                String.format(
                        "(test -f /vendor/lib64/%1$s || cp /system/lib64/%1$s /vendor/lib64/%1$s)"
                                + " || (test -f /vendor/lib/%1$s || cp /system/lib/%1$s"
                                + " /vendor/lib/%1$s)",
                        filename));
    }

    private void tryUpdateVintfManifest(ITestDevice device)
            throws DeviceNotAvailableException, IOException {
        try {
            String vintfManifest = device.pullFileContents("/vendor/etc/vintf/manifest.xml");
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(vintfManifest)));
            String XPATH = "/manifest/hal[name=\"android.hardware.bluetooth\"][version!=\"1.1\"]";
            Node node =
                    (Node)
                            XPathFactory.newInstance()
                                    .newXPath()
                                    .evaluate(XPATH, doc, XPathConstants.NODE);
            if (node != null) {
                Node versionNode =
                        (Node)
                                XPathFactory.newInstance()
                                        .newXPath()
                                        .evaluate("version", node, XPathConstants.NODE);
                versionNode.setTextContent("1.1");

                Node fqnameNode =
                        (Node)
                                XPathFactory.newInstance()
                                        .newXPath()
                                        .evaluate("fqname", node, XPathConstants.NODE);
                String newFqname =
                        fqnameNode.getTextContent().replaceAll("@[0-9]+\\.[0-9]+(::.*)", "@1.1$1");
                fqnameNode.setTextContent(newFqname);

                File outFile = File.createTempFile("stsrootcanal", null);
                outFile.deleteOnExit();

                Transformer transformer = TransformerFactory.newInstance().newTransformer();
                DOMSource source = new DOMSource(doc);
                StreamResult result = new StreamResult(new FileWriter(outFile));
                transformer.transform(source, result);
                device.pushFile(outFile, "/vendor/etc/vintf/manifest.xml");
                CLog.d("Updated VINTF manifest");
            } else {
                CLog.d("Not updating VINTF manifest");
            }
        } catch (ParserConfigurationException
                | SAXException
                | XPathExpressionException
                | TransformerException e) {
            CLog.e("Could not parse vintf manifest: %s", e);
        }
    }

    /** Spin wait until given property has given value. */
    private void waitPropertyValue(ITestDevice device, String name, String value, long timeoutMs)
            throws TimeoutException, DeviceNotAvailableException, InterruptedException {
        long endTime = System.currentTimeMillis() + timeoutMs;
        while (true) {
            if (value.equals(device.getProperty(name))) {
                return;
            }
            if (System.currentTimeMillis() > endTime) {
                throw new TimeoutException();
            }
            TimeUnit.MILLISECONDS.sleep(250);
        }
    }

    /** Find an open TCP port on the host */
    private static int findOpenPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        }
    }

    /** Class that encapsulates a virtual HCI device that can be controlled by HCI commands. */
    public static class HciDevice implements AutoCloseable {
        private static final String READ_FAIL_MSG = "Failed to read HCI packet";
        private final Socket hciSocket;

        private HciDevice(Socket hciSocket) {
            this.hciSocket = hciSocket;
        }

        @Override
        public void close() throws IOException {
            hciSocket.close();
        }

        /**
         * Convenient wrapper around sendHciPacket to send a HCI command packet to device.
         *
         * @param ogf Opcode group field
         * @param ocf Opcode command field
         * @param params the rest of the command parameters
         */
        public void sendHciCmd(int ogf, int ocf, byte[] params) throws IOException {
            assertTrue("params length must be less than 256 bytes", params.length < 256);
            ByteBuffer cmd = ByteBuffer.allocate(4 + params.length).order(ByteOrder.LITTLE_ENDIAN);
            int opcode = (ogf << 10) | ocf;
            cmd.put((byte) 0x01).putShort((short) opcode).put((byte) params.length).put(params);
            sendHciPacket(cmd.array());
        }

        /**
         * Send raw HCI packet to device.
         *
         * @param packet raw packet data to send to device
         */
        public void sendHciPacket(byte[] packet) throws IOException {
            CLog.d("sending HCI: %s", Arrays.toString(packet));
            hciSocket.getOutputStream().write(packet);
        }

        /** Read one HCI packet from device, blocking until data is available. */
        public byte[] readHciPacket() throws IOException {
            ByteArrayOutputStream ret = new ByteArrayOutputStream();
            InputStream in = hciSocket.getInputStream();

            // Read the packet type
            byte[] typeBuf = new byte[1];
            assertEquals(READ_FAIL_MSG, 1, in.read(typeBuf, 0, 1));
            ret.write(typeBuf);

            // Read the header and figure out how much data to read
            // according to BT core spec 5.2 vol 4 part A section 2 & part E section 5.4
            byte[] hdrBuf = new byte[4];
            int dataLength;

            switch (typeBuf[0]) {
                case 0x01: // Command packet
                case 0x03: // Synch data packet
                    assertEquals(READ_FAIL_MSG, 3, in.read(hdrBuf, 0, 3));
                    ret.write(hdrBuf, 0, 3);
                    dataLength = hdrBuf[2];
                    break;

                case 0x02: // Async data packet
                    assertEquals(READ_FAIL_MSG, 4, in.read(hdrBuf, 0, 4));
                    ret.write(hdrBuf, 0, 4);
                    dataLength = (((int) hdrBuf[2]) & 0xFF) | ((((int) hdrBuf[3]) & 0xFF) << 8);
                    break;

                case 0x04: // Event
                    assertEquals(READ_FAIL_MSG, 2, in.read(hdrBuf, 0, 2));
                    ret.write(hdrBuf, 0, 2);
                    dataLength = hdrBuf[1];
                    break;

                case 0x05: // ISO synch data packet
                    assertEquals(READ_FAIL_MSG, 4, in.read(hdrBuf, 0, 4));
                    ret.write(hdrBuf, 0, 4);
                    dataLength = (((int) hdrBuf[2]) & 0xFF) | ((((int) hdrBuf[3]) & 0xFC) << 6);
                    break;

                default:
                    throw new IOException("Unexpected packet type: " + String.valueOf(typeBuf[0]));
            }

            // Read the data payload
            byte[] data = new byte[dataLength];
            assertEquals(READ_FAIL_MSG, dataLength, in.read(data, 0, dataLength));
            ret.write(data, 0, dataLength);

            return ret.toByteArray();
        }
    }

    public static class RootcanalController implements AutoCloseable {
        private final int testPort;
        private final int hciPort;
        private Socket rootcanalTestChannel = null;
        private List<HciDevice> hciDevices = new ArrayList<>();

        private RootcanalController(int testPort, int hciPort)
                throws IOException, InterruptedException {
            this.testPort = testPort;
            this.hciPort = hciPort;
            CLog.d("Rootcanal controller initialized; testPort=%d, hciPort=%d", testPort, hciPort);
        }

        @Override
        public void close() throws IOException {
            rootcanalTestChannel.close();
            for (HciDevice dev : hciDevices) {
                dev.close();
            }
        }

        /**
         * Create a new HCI device by connecting to rootcanal's HCI socket.
         *
         * @return HciDevice object that allows sending/receiving from the HCI port
         */
        public HciDevice createHciDevice()
                throws DeviceNotAvailableException, IOException, InterruptedException {
            HciDevice dev = new HciDevice(new Socket("localhost", hciPort));
            hciDevices.add(dev);
            return dev;
        }

        /**
         * Send one command to rootcanal test channel.
         *
         * <p>Send `help` command for list of accepted commands from Rootcanal.
         *
         * @param cmd command to send
         * @param args arguments for the command
         * @return Response string from rootcanal
         */
        public String sendTestChannelCommand(String cmd, String... args)
                throws IOException, InterruptedException {
            if (rootcanalTestChannel == null) {
                rootcanalTestChannel = new Socket("localhost", testPort);
                CLog.d(
                        "RootCanal test channel init: "
                                + readTestChannel(rootcanalTestChannel.getInputStream()));
            }

            // Translated from system/bt/vendor_libs/test_vendor_lib/scripts/test_channel.py
            ByteArrayOutputStream msg = new ByteArrayOutputStream();
            msg.write(cmd.length());
            msg.write(cmd.getBytes("ASCII"));
            msg.write(args.length);
            for (String arg : args) {
                msg.write(arg.length());
                msg.write(arg.getBytes("ASCII"));
            }

            rootcanalTestChannel.getOutputStream().write(msg.toByteArray());
            return readTestChannel(rootcanalTestChannel.getInputStream());
        }

        /** Read one message from rootcanal test channel. */
        private String readTestChannel(InputStream in) throws IOException {
            // Translated from system/bt/vendor_libs/test_vendor_lib/scripts/test_channel.py
            ByteBuffer sizeBuf = ByteBuffer.allocate(Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN);
            in.read(sizeBuf.array(), 0, Integer.BYTES);
            int size = sizeBuf.getInt();

            byte[] buf = new byte[size];
            in.read(buf, 0, size);
            return new String(buf);
        }
    }
}
