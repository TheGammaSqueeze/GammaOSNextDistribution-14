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
package com.android.tradefed.invoker.shard.token;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.StubDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.RunUtil;

import com.google.common.annotations.VisibleForTesting;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Token provider for Consumer Electronics Control (CEC) related tokens. */
public class CecControllerTokenProvider implements ITokenProvider {

    private static final int TIMEOUT_MILLIS = 10000;
    private List<String> devicesWithToken = new ArrayList<>();

    @Override
    public boolean hasToken(ITestDevice device, TokenProperty token) {
        if (device.getIDevice() instanceof StubDevice) {
            return false;
        }
        switch (token) {
            case CEC_TEST_CONTROLLER:
                try {
                    if (!device.hasFeature("feature:android.hardware.hdmi.cec")
                            || !device.hasFeature("feature:android.software.leanback")) {
                        // We are testing non-HDMI devices, so don't check for adapter availability
                        return true;
                    }
                    /* Check if the device and adapter are connected */
                    return isCecAdapterConnected(device);
                } catch (DeviceNotAvailableException dnae) {
                    CLog.e("Device not available. Not providing token.");
                    return false;
                }
            default:
                CLog.e("Token '%s' doesn't match any CecControllerTokenProvider tokens.", token);
                return false;
        }
    }

    /** Check for any string on the input console of the cec-client */
    @VisibleForTesting
    boolean checkConsoleOutput(
            String expectedMessage, long timeoutMillis, BufferedReader inputConsole)
            throws IOException {
        long startTime = System.currentTimeMillis();
        long endTime = startTime;
        do {
            if (inputConsole.ready()) {
                String line = inputConsole.readLine();
                if (line != null && line.toLowerCase().contains(expectedMessage)) {
                    CLog.v("Found " + expectedMessage + " in " + line);
                    return true;
                }
            }
            endTime = System.currentTimeMillis();
        } while ((endTime - startTime <= timeoutMillis));
        return false;
    }

    List<String> getValidCecClientPorts() throws IOException, InterruptedException {
        List<String> listPortsCommand = new ArrayList<>();

        listPortsCommand.add("cec-client");
        listPortsCommand.add("-l");

        List<String> comPorts = new ArrayList<>();
        Process cecClient = RunUtil.getDefault().runCmdInBackground(listPortsCommand);
        try (BufferedReader inputConsole =
                new BufferedReader(new InputStreamReader(cecClient.getInputStream()))) {
            while (cecClient.isAlive()) {
                if (inputConsole.ready()) {
                    String line = inputConsole.readLine();
                    if (line.toLowerCase().contains("com port")) {
                        String port = line.split(":")[1].trim();
                        comPorts.add(port);
                    }
                }
            }
        }
        cecClient.waitFor();

        return comPorts;
    }

    /**
     * Converts ascii characters to hexadecimal numbers that can be appended to a CEC message as
     * params. For example, "spa" will be converted to "73:70:61"
     */
    @VisibleForTesting
    String convertStringToHexParams(String rawParams) {
        StringBuilder params = new StringBuilder("");
        for (int i = 0; i < rawParams.length(); i++) {
            params.append(String.format(":%02x", (int) rawParams.charAt(i)));
        }
        return params.toString().substring(1);
    }

    /** Gets the logical address of the DUT by parsing the dumpsys hdmi_control. */
    int getDumpsysLogicalAddress(ITestDevice device)
            throws IOException, DeviceNotAvailableException {
        String line;
        String pattern = "(.*?)" + "(mAddress: )" + "(?<address>\\d+)" + "(.*?)";
        Pattern p = Pattern.compile(pattern);
        Matcher m;
        String dumpsys = device.executeShellCommand("dumpsys hdmi_control");
        BufferedReader reader = new BufferedReader(new StringReader(dumpsys));
        while ((line = reader.readLine()) != null) {
            m = p.matcher(line);
            if (m.matches()) {
                int address = Integer.decode(m.group("address"));
                return address;
            }
        }
        throw new IOException("Could not parse logical address from dumpsys.");
    }

    @VisibleForTesting
    boolean isCecAdapterConnected(ITestDevice device) throws DeviceNotAvailableException {
        List<String> launchCommand = new ArrayList<>();
        Process mCecClient;
        String serialNo = device.getProperty("ro.serialno");

        if (devicesWithToken.contains(serialNo)) {
            /* This device has been checked and issued a token, don't check again. */
            return true;
        }

        launchCommand.add("cec-client");

        try {
            List<String> comPorts = getValidCecClientPorts();

            if (comPorts.size() == 0) {
                /* No adapter is connected. No token to be given. */
                return false;
            }

            int targetDevice = getDumpsysLogicalAddress(device);
            int toDevice;
            launchCommand.add("-t");
            if (targetDevice == 0) {
                toDevice = 4;
                launchCommand.add("p");
            } else {
                toDevice = 0;
                launchCommand.add("x");
            }

            String serialNoParam = convertStringToHexParams(serialNo);
            StringBuilder sendVendorCommand = new StringBuilder("cmd hdmi_control vendorcommand ");
            sendVendorCommand.append(" -t " + targetDevice);
            sendVendorCommand.append(" -d " + toDevice);
            sendVendorCommand.append(" -a " + serialNoParam);

            for (String port : comPorts) {
                launchCommand.add(port);
                mCecClient = RunUtil.getDefault().runCmdInBackground(launchCommand);
                try (BufferedReader inputConsole =
                        new BufferedReader(new InputStreamReader(mCecClient.getInputStream()))) {

                    /* Wait for the client to become ready */
                    if (checkConsoleOutput("waiting for input", TIMEOUT_MILLIS, inputConsole)) {

                        device.executeShellCommand(sendVendorCommand.toString());
                        if (checkConsoleOutput(serialNoParam, TIMEOUT_MILLIS, inputConsole)) {
                            /* Add to list of devices with token, it need not be checked again */
                            devicesWithToken.add(serialNo);
                            return true;
                        }
                    } else {
                        CLog.e("Console did not get ready!");
                    }
                } finally {

                    /* Kill the unwanted cec-client process. */
                    Process killProcess = mCecClient.destroyForcibly();
                    killProcess.waitFor();
                    launchCommand.remove(port);
                }
            }
        } catch (IOException | InterruptedException e) {
            CLog.e(
                    "Caught "
                            + e.getClass().getSimpleName()
                            + ". "
                            + "Could not launch the cec-client process.");
            return false;
        }
        return false;
    }
}
