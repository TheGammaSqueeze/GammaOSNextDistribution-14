/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.hdmicec.cts.targetprep;

import android.hdmicec.cts.BaseHdmiCecCtsTest;
import android.hdmicec.cts.CecClientMessage;
import android.hdmicec.cts.CecMessage;
import android.hdmicec.cts.HdmiCecClientWrapper;
import android.hdmicec.cts.HdmiCecConstants;
import android.hdmicec.cts.LogicalAddress;
import android.hdmicec.cts.error.CecClientWrapperException;
import android.hdmicec.cts.error.ErrorCodes;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.targetprep.BaseTargetPreparer;
import com.android.tradefed.targetprep.TargetSetupError;
import com.android.tradefed.util.RunUtil;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/* Sets up the CEC tests by discovering which port the CEC adapter connected to */
public class CecPortDiscoverer extends BaseTargetPreparer {

    private static final int TIMEOUT_MILLIS = 15000;
    private static final int MAX_RETRY_COUNT = 3;

    private File mCecMapDir = HdmiCecConstants.CEC_MAP_FOLDER;
    private File mDeviceEntry = null;
    private File mPortEntry = null;

    private String instructionsOnError =
            "\nIn case the setup is valid according to the README and "
                    + "the test should have run, please verify that\n"
                    + "1. cec-client is not running already on the port the DUT is connected to\n"
                    + "2. "
                    + HdmiCecConstants.CEC_MAP_FOLDER
                    + " has been cleared of stale mappings (in case a"
                    + " test was interrupted)\n";

    /** {@inheritDoc} */
    @Override
    public void setUp(TestInformation testInfo)
            throws TargetSetupError, DeviceNotAvailableException {
        ITestDevice device = testInfo.getDevice();
        if (!device.hasFeature("feature:android.hardware.hdmi.cec")
                || !device.hasFeature("feature:android.software.leanback")
                || isTvEmulator(device)) {
            // We are testing non-HDMI devices, so don't check for adapter availability
            return;
        }
        synchronized (CecPortDiscoverer.class) {
            if (!mCecMapDir.exists()) {
                mCecMapDir.mkdirs();
            }
            initValidClient(device);
        }
    }

    /**
     * Check if the DUT is an emulator.
     * @param device The DUT.
     * @return true If the DUT is an emulator.
     * @throws DeviceNotAvailableException
     */
    public static boolean isTvEmulator(ITestDevice device) throws DeviceNotAvailableException {
        return !device.executeShellCommand("getprop " + HdmiCecConstants.PROPERTY_BUILD_FINGERPRINT
                        + " | grep \"cf_x86\"")
                .isEmpty();
    }

    /** {@inheritDoc} */
    @Override
    public void tearDown(TestInformation testInfo, Throwable e) {
        if (mDeviceEntry != null) {
            mDeviceEntry.delete();
        }
        if (mPortEntry != null) {
            mPortEntry.delete();
        }
    }

    private void initValidClient(ITestDevice device)
            throws TargetSetupError, DeviceNotAvailableException {

        List<String> launchCommand = new ArrayList();
        Process mCecClient;
        /* This is a semi-functional object only, the methods that we can use are limited. */
        HdmiCecClientWrapper cecClientWrapper = new HdmiCecClientWrapper();

        launchCommand.add("cec-client");
        String serialNo = "";

        try {
            List<String> comPorts = cecClientWrapper.getValidCecClientPorts();

            if (comPorts.size() == 0) {
                throw new TargetSetupError("No adapters connected to host.");
            }

            int targetDeviceType =
                    BaseHdmiCecCtsTest.getTargetLogicalAddress(device).getDeviceType();
            int toDevice;
            launchCommand.add("-t");
            launchCommand.add("r");
            launchCommand.add("-t");
            if (targetDeviceType == HdmiCecConstants.CEC_DEVICE_TYPE_TV) {
                toDevice = LogicalAddress.PLAYBACK_1.getLogicalAddressAsInt();
                launchCommand.add("p");
            } else {
                toDevice = LogicalAddress.TV.getLogicalAddressAsInt();
                launchCommand.add("x");
            }

            serialNo = device.getProperty("ro.serialno");
            String serialNoHashCode = String.valueOf(serialNo.hashCode());
            String serialNoParam = CecMessage.convertStringToHexParams(serialNoHashCode);
            /*
             * formatParams prefixes with a ':' that we do not want in the vendorcommand
             * command line utility.
             */
            serialNoParam = serialNoParam.substring(1);
            StringBuilder sendVendorCommand = new StringBuilder("cmd hdmi_control vendorcommand ");
            sendVendorCommand.append(" -t " + targetDeviceType);
            sendVendorCommand.append(" -d " + toDevice);
            sendVendorCommand.append(" -a " + serialNoParam);

            for (String port : comPorts) {
                launchCommand.add(port);
                boolean portBeingRetried = true;
                int retryCount = 0;
                do {
                    File adapterMapping = new File(mCecMapDir, getPortFilename(port));
                    /*
                     * Check for the mapping before each iteration. It is possible that another DUT
                     * got mapped to this port while this DUT is still trying to discover if this is
                     * the right port.
                     */
                    if (adapterMapping.exists()) {
                        /* Exit the current port's retry loop */
                        launchCommand.remove(port);
                        break;
                    }
                    mCecClient = RunUtil.getDefault().runCmdInBackground(launchCommand);
                    BufferedReader inputConsole =
                            new BufferedReader(
                                    new InputStreamReader(mCecClient.getInputStream()));
                    BufferedWriter outputConsole =
                            new BufferedWriter(
                                    new OutputStreamWriter(mCecClient.getOutputStream()));
                    try {
                        /* Wait for the client to become ready */
                        if (cecClientWrapper.checkConsoleOutput(
                                CecClientMessage.CLIENT_CONSOLE_READY.toString(),
                                TIMEOUT_MILLIS, inputConsole)) {

                            device.executeShellCommand(sendVendorCommand.toString());
                            if (cecClientWrapper.checkConsoleOutput(
                                    serialNoParam, TIMEOUT_MILLIS, inputConsole)) {
                                if (targetDeviceType != HdmiCecConstants.CEC_DEVICE_TYPE_TV) {
                                    // Timeout in milliseconds
                                    long getVersionTimeout = 3000;

                                    String getVersionMessage = "tx 10:9f";
                                    cecClientWrapper.sendConsoleMessage(
                                            getVersionMessage, outputConsole);
                                    String getVersionResponse = "01:9e";
                                    if (cecClientWrapper.checkConsoleOutput(
                                            getVersionResponse, getVersionTimeout, inputConsole)) {
                                        throw new Exception(
                                                "Setup error! The sink device (TV) in the test setup"
                                                    + " seems to have CEC enabled. Please disable"
                                                    + " and retry tests.");
                                    }
                                }

                                writeMapping(port, serialNo);
                                return;
                            }
                            /* Since it did not find the required message. Check another port */
                            portBeingRetried = false;
                        } else {
                            CLog.e("Console did not get ready!");
                            throw new CecClientWrapperException(ErrorCodes.CecPortBusy);
                        }
                    } catch (CecClientWrapperException cwe) {
                        if (cwe.getErrorCode() != ErrorCodes.CecPortBusy) {
                            retryCount = MAX_RETRY_COUNT;
                        } else {
                            retryCount++;
                        }
                        if (retryCount >= MAX_RETRY_COUNT) {
                            /* We have retried enough number of times. Check another port */
                            portBeingRetried = false;
                        } else {
                            /* Give a break before checking the port again. */
                            TimeUnit.MILLISECONDS.sleep(TIMEOUT_MILLIS);
                        }
                    } finally {
                        /* Kill the unwanted cec-client process. */
                        boolean processQuit = false;
                        cecClientWrapper.sendConsoleMessage(
                                CecClientMessage.QUIT_CLIENT.toString(), outputConsole);
                        if (cecClientWrapper.checkConsoleOutput(
                                CecClientMessage.CLIENT_CONSOLE_END.toString(),
                                TIMEOUT_MILLIS, inputConsole)) {
                            outputConsole.close();
                            inputConsole.close();
                            if (mCecClient.waitFor(10, TimeUnit.SECONDS)) {
                                /* The cec-client process is quit */
                                processQuit = true;
                            }
                        }
                        if (!processQuit) {
                            /* Use destryForcibly if the cec-client process is not dead
                             * in spite of the quit above.
                             */
                            Process killProcess = mCecClient.destroyForcibly();
                            killProcess.waitFor(60, TimeUnit.SECONDS);
                        }
                    }
                } while (portBeingRetried);
                launchCommand.remove(port);
            }
        } catch (IOException | InterruptedException e) {
            throw new TargetSetupError(
                    "Caught "
                            + e.getClass().getSimpleName()
                            + ". "
                            + "Could not get adapter mapping for device"
                            + serialNo
                            + "."
                            + instructionsOnError,
                    e);
        } catch (Exception generic) {
            throw new TargetSetupError(
                    "Caught an exception with message '"
                            + generic.getMessage()
                            + "'. "
                            + "Could not get adapter mapping for device"
                            + serialNo
                            + "."
                            + instructionsOnError,
                    generic);
        }
        throw new TargetSetupError(
                "Device " + serialNo + " not connected to any adapter!" + instructionsOnError);
    }

    private String getPortFilename(String port) {
        /* Returns only the name of the port, ignoring the path */
        return new File(port).getName();
    }

    private void writeMapping(String port, String serialNo) throws TargetSetupError {
        mDeviceEntry = new File(mCecMapDir, serialNo);
        mPortEntry = new File(mCecMapDir, getPortFilename(port));
        try (BufferedWriter device = new BufferedWriter(new FileWriter(mDeviceEntry));
                BufferedWriter adapter = new BufferedWriter(new FileWriter(mPortEntry))) {
            mDeviceEntry.createNewFile();
            device.write(port);
            device.flush();
            adapter.write(serialNo);
            adapter.flush();
        } catch (IOException ioe) {
            throw new TargetSetupError(
                    "Could not create mapping file " + mCecMapDir + "/" + mDeviceEntry.getName());
        }
    }
}
