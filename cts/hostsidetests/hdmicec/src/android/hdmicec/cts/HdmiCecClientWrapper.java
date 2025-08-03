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

package android.hdmicec.cts;

import android.hdmicec.cts.error.CecClientWrapperException;
import android.hdmicec.cts.error.ErrorCodes;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.RunUtil;

import org.junit.rules.ExternalResource;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/** Class that helps communicate with the cec-client */
public final class HdmiCecClientWrapper extends ExternalResource {

    private static final int MILLISECONDS_TO_READY = 10000;
    private static final int DEFAULT_TIMEOUT = 20000;
    private static final int BUFFER_SIZE = 1024;

    private Process mCecClient;
    private BufferedWriter mOutputConsole;
    private BufferedReader mInputConsole;
    private boolean mCecClientInitialised = false;

    private LogicalAddress selfDevice = LogicalAddress.RECORDER_1;
    private LogicalAddress targetDevice = LogicalAddress.UNKNOWN;
    private String clientParams[];
    private StringBuilder sendVendorCommand = new StringBuilder("cmd hdmi_control vendorcommand ");
    private int physicalAddress = 0xFFFF;

    private CecOperand featureAbortOperand = CecOperand.FEATURE_ABORT;
    private List<Integer> featureAbortReasons =
            new ArrayList<>(HdmiCecConstants.ABORT_INVALID_OPERAND);
    private boolean isFeatureAbortExpected = false;

    private static final String CEC_PORT_BUSY = "unable to open the device on port";

    public HdmiCecClientWrapper(String ...clientParams) {
        this.clientParams = clientParams;
    }

    @Override
    protected void after() {
        this.killCecProcess();
    }

    void setTargetLogicalAddress(LogicalAddress dutLogicalAddress) {
        targetDevice = dutLogicalAddress;
    }

    public List<String> getValidCecClientPorts() throws CecClientWrapperException {

        List<String> listPortsCommand = new ArrayList();
        Process cecClient;

        listPortsCommand.add("cec-client");
        listPortsCommand.add("-l");

        List<String> comPorts = new ArrayList();
        try {
            cecClient = RunUtil.getDefault().runCmdInBackground(listPortsCommand);
        } catch (IOException ioe) {
            throw new CecClientWrapperException(
                    ErrorCodes.CecClientStart,
                    "as cec-client may not be installed. Please refer to README for"
                        + " setup/installation instructions.");
        }
        try {
            BufferedReader inputConsole =
                    new BufferedReader(new InputStreamReader(cecClient.getInputStream()));
            while (cecClient.isAlive()) {
                if (inputConsole.ready()) {
                    String line = inputConsole.readLine();
                    if (line.toLowerCase().contains("com port")) {
                        String port = line.split(":")[1].trim();
                        comPorts.add(port);
                    }
                }
            }
            inputConsole.close();
            cecClient.waitFor();
        } catch (IOException | InterruptedException ioe) {
            throw new CecClientWrapperException(ErrorCodes.ReadConsole, ioe);
        }

        return comPorts;
    }

    boolean initValidCecClient(ITestDevice device, List<String> clientCommands)
            throws CecClientWrapperException {

        String serialNo;
        List<String> launchCommand = new ArrayList(clientCommands);
        try {
            serialNo = device.getProperty("ro.serialno");
        } catch (DeviceNotAvailableException de) {
            throw new CecClientWrapperException(ErrorCodes.DeviceNotAvailable, de);
        }
        File mDeviceEntry = new File(HdmiCecConstants.CEC_MAP_FOLDER, serialNo);

        try (BufferedReader reader = new BufferedReader(new FileReader(mDeviceEntry))) {
            String port = reader.readLine();
            launchCommand.add(port);
            mCecClient = RunUtil.getDefault().runCmdInBackground(launchCommand);
            mInputConsole = new BufferedReader(new InputStreamReader(mCecClient.getInputStream()));

            /* Wait for the client to become ready */
            if (checkConsoleOutput(
                    CecClientMessage.CLIENT_CONSOLE_READY + "", MILLISECONDS_TO_READY)) {
                        mOutputConsole =
                                new BufferedWriter(
                                        new OutputStreamWriter(mCecClient.getOutputStream()),
                                        BUFFER_SIZE);
                        return true;
            } else {
                CLog.e("Console did not get ready!");
                /* Kill the unwanted cec-client process. */
                Process killProcess = mCecClient.destroyForcibly();
                killProcess.waitFor();
            }
        } catch (IOException | InterruptedException ioe) {
            throw new CecClientWrapperException(
                    ErrorCodes.ReadConsole, ioe, "Could not open port mapping file");
        }
        return false;
    }

    /** Initialise the client */
    void init(boolean startAsTv, ITestDevice device) throws CecClientWrapperException {
        if (targetDevice == LogicalAddress.UNKNOWN) {
            throw new CecClientWrapperException(
                    ErrorCodes.CecClientStart, "Missing logical address of the target device.");
        }

        List<String> commands = new ArrayList();

        commands.add("cec-client");

        /* "-p 2" starts the client as if it is connected to HDMI port 2, taking the physical
         * address 2.0.0.0 */
        commands.add("-p");
        commands.add("2");
        physicalAddress = 0x2000;
        if (startAsTv) {
            commands.add("-t");
            commands.add("x");
            selfDevice = LogicalAddress.TV;
        }
        /* "-d 15" set the log level to ERROR|WARNING|NOTICE|TRAFFIC */
        commands.add("-d");
        commands.add("15");
        commands.addAll(Arrays.asList(clientParams));
        if (Arrays.asList(clientParams).contains("a")) {
            selfDevice = LogicalAddress.AUDIO_SYSTEM;
        }

        mCecClientInitialised = true;
        if (!initValidCecClient(device, commands)) {
            mCecClientInitialised = false;

            throw new CecClientWrapperException(ErrorCodes.CecClientStart);
        }
    }

    private void checkCecClient() throws CecClientWrapperException {
        if (!mCecClientInitialised) {
            throw new CecClientWrapperException(ErrorCodes.CecClientStart);
        }
        if (!mCecClient.isAlive()) {
            throw new CecClientWrapperException(ErrorCodes.CecClientNotRunning);
        }
    }

    /**
     * Sends a CEC message with source marked as broadcast to the device passed in the constructor
     * through the output console of the cec-communication channel.
     */
    public void sendCecMessage(CecOperand message) throws CecClientWrapperException {
        sendCecMessage(message, "");
    }

    /**
     * Sends a CEC message with source marked as broadcast to the device passed in the constructor
     * through the output console of the cec-communication channel.
     */
    public void sendCecMessage(CecOperand message, String params) throws CecClientWrapperException {
        sendCecMessage(LogicalAddress.BROADCAST, targetDevice, message, params);
    }

    /**
     * Sends a CEC message from source device to the device passed in the constructor through the
     * output console of the cec-communication channel.
     */
    public void sendCecMessage(LogicalAddress source, CecOperand message)
            throws CecClientWrapperException {
        sendCecMessage(source, targetDevice, message, "");
    }

    /**
     * Sends a CEC message from source device to the device passed in the constructor through the
     * output console of the cec-communication channel with the appended params.
     */
    public void sendCecMessage(LogicalAddress source, CecOperand message, String params)
            throws Exception {
        sendCecMessage(source, targetDevice, message, params);
    }

    /**
     * Sends a CEC message from source device to a destination device through the output console of
     * the cec-communication channel.
     */
    public void sendCecMessage(
            LogicalAddress source, LogicalAddress destination, CecOperand message)
            throws CecClientWrapperException {
        sendCecMessage(source, destination, message, "");
    }

    /**
     * Broadcasts a CEC ACTIVE_SOURCE message from client device source through the output console
     * of the cec-communication channel.
     */
    public void broadcastActiveSource(LogicalAddress source) throws CecClientWrapperException {
        int sourcePa = (source == selfDevice) ? physicalAddress : 0xFFFF;
        sendCecMessage(
                source,
                LogicalAddress.BROADCAST,
                CecOperand.ACTIVE_SOURCE,
                CecMessage.formatParams(sourcePa, HdmiCecConstants.PHYSICAL_ADDRESS_LENGTH));
    }

    /**
     * Broadcasts a CEC ACTIVE_SOURCE message with physicalAddressOfActiveDevice from client device
     * source through the output console of the cec-communication channel.
     */
    public void broadcastActiveSource(LogicalAddress source, int physicalAddressOfActiveDevice)
            throws CecClientWrapperException {
        sendCecMessage(
                source,
                LogicalAddress.BROADCAST,
                CecOperand.ACTIVE_SOURCE,
                CecMessage.formatParams(
                        physicalAddressOfActiveDevice, HdmiCecConstants.PHYSICAL_ADDRESS_LENGTH));
    }

    /**
     * Broadcasts a CEC REPORT_PHYSICAL_ADDRESS message from client device source through the output
     * console of the cec-communication channel.
     */
    public void broadcastReportPhysicalAddress(LogicalAddress source)
            throws CecClientWrapperException {
        String deviceType = CecMessage.formatParams(source.getDeviceType());
        int sourcePa = (source == selfDevice) ? physicalAddress : 0xFFFF;
        String physicalAddress =
                CecMessage.formatParams(sourcePa, HdmiCecConstants.PHYSICAL_ADDRESS_LENGTH);
        sendCecMessage(
                source,
                LogicalAddress.BROADCAST,
                CecOperand.REPORT_PHYSICAL_ADDRESS,
                physicalAddress + deviceType);
    }

    /**
     * Broadcasts a CEC REPORT_PHYSICAL_ADDRESS message with physicalAddressToReport from client
     * device source through the output console of the cec-communication channel.
     */
    public void broadcastReportPhysicalAddress(LogicalAddress source, int physicalAddressToReport)
            throws CecClientWrapperException {
        String deviceType = CecMessage.formatParams(source.getDeviceType());
        String physicalAddress =
                CecMessage.formatParams(
                        physicalAddressToReport, HdmiCecConstants.PHYSICAL_ADDRESS_LENGTH);
        sendCecMessage(
                source,
                LogicalAddress.BROADCAST,
                CecOperand.REPORT_PHYSICAL_ADDRESS,
                physicalAddress + deviceType);
    }

    /**
     * Sends a CEC message from source device to a destination device through the output console of
     * the cec-communication channel with the appended params.
     */
    public void sendCecMessage(
            LogicalAddress source, LogicalAddress destination, CecOperand message, String params)
            throws CecClientWrapperException {
        checkCecClient();
        String sendMessageString = "tx " + source + destination + ":" + message + params;
        try {
            CLog.v("Sending CEC message: " + sendMessageString);
            mOutputConsole.write(sendMessageString);
            mOutputConsole.newLine();
            mOutputConsole.flush();
        } catch (IOException ioe) {
            throw new CecClientWrapperException(ErrorCodes.WriteConsole, ioe);
        }
    }

    public void sendMultipleUserControlPressAndRelease(
            LogicalAddress source, List<Integer> keycodes) throws CecClientWrapperException {
        try {
            for (int keycode : keycodes) {
                String key = String.format("%02x", keycode);
                mOutputConsole.write(
                        "tx "
                                + source
                                + targetDevice
                                + ":"
                                + CecOperand.USER_CONTROL_PRESSED
                                + ":"
                                + key);
                mOutputConsole.newLine();
                mOutputConsole.write(
                        "tx " + source + targetDevice + ":" + CecOperand.USER_CONTROL_RELEASED);
                mOutputConsole.newLine();
                mOutputConsole.flush();
                TimeUnit.MILLISECONDS.sleep(200);
            }
        } catch (InterruptedException | IOException ioe) {
            throw new CecClientWrapperException(ErrorCodes.WriteConsole, ioe);
        }
    }

    /**
     * Sends a <USER_CONTROL_PRESSED> and <USER_CONTROL_RELEASED> from source to device through the
     * output console of the cec-communication channel with the mentioned keycode.
     */
    public void sendUserControlPressAndRelease(LogicalAddress source, int keycode, boolean holdKey)
            throws CecClientWrapperException {
        sendUserControlPressAndRelease(source, targetDevice, keycode, holdKey);
    }

    /**
     * Sends a <USER_CONTROL_PRESSED> and <USER_CONTROL_RELEASED> from source to destination
     * through the output console of the cec-communication channel with the mentioned keycode.
     */
    public void sendUserControlPressAndRelease(
            LogicalAddress source, LogicalAddress destination, int keycode, boolean holdKey)
            throws CecClientWrapperException {
        sendUserControlPress(source, destination, keycode, holdKey);
        try {
            /* Sleep less than 200ms between press and release */
            TimeUnit.MILLISECONDS.sleep(100);
            mOutputConsole.write(
                    "tx " + source + destination + ":" + CecOperand.USER_CONTROL_RELEASED);
            mOutputConsole.flush();
        } catch (IOException | InterruptedException ioe) {
            throw new CecClientWrapperException(ErrorCodes.WriteConsole, ioe);
        }
    }

    /**
     * Sends a {@code <UCP>} with and additional param. This is used to check that the DUT ignores
     * additional params in an otherwise correct message.
     */
    public void sendUserControlPressAndReleaseWithAdditionalParams(
            LogicalAddress source, LogicalAddress destination, int keyCode, int additionalParam)
            throws CecClientWrapperException {
        String key = String.format("%02x", keyCode);
        String command =
                "tx "
                        + source
                        + destination
                        + ":"
                        + CecOperand.USER_CONTROL_PRESSED
                        + ":"
                        + key
                        + ":"
                        + additionalParam;

        try {
            mOutputConsole.write(command);
            mOutputConsole.newLine();
            mOutputConsole.write(
                    "tx " + source + destination + ":" + CecOperand.USER_CONTROL_RELEASED);
            mOutputConsole.newLine();
            mOutputConsole.flush();
        } catch (IOException ioe) {
            throw new CecClientWrapperException(ErrorCodes.WriteConsole, ioe);
        }
    }

    /**
     * Sends a <UCP> message from source to destination through the output console of the
     * cec-communication channel with the mentioned keycode. If holdKey is true, the method will
     * send multiple <UCP> messages to simulate a long press. No <UCR> will be sent.
     */
    public void sendUserControlPress(
            LogicalAddress source, LogicalAddress destination, int keycode, boolean holdKey)
            throws CecClientWrapperException {
        String key = String.format("%02x", keycode);
        String command = "tx " + source + destination + ":" +
                CecOperand.USER_CONTROL_PRESSED + ":" + key;

        try {
            if (holdKey) {
                /* Repeat once every 450ms for at least 5 seconds. Send 11 times in loop every
                 * 450ms. The message is sent once after the loop as well.
                 * ((11 + 1) * 0.45 = 5.4s total) */
                int repeat = 11;
                for (int i = 0; i < repeat; i++) {
                    mOutputConsole.write(command);
                    mOutputConsole.newLine();
                    mOutputConsole.flush();
                    TimeUnit.MILLISECONDS.sleep(450);
                }
            }

            mOutputConsole.write(command);
            mOutputConsole.newLine();
            mOutputConsole.flush();
        } catch (IOException | InterruptedException ioe) {
            throw new CecClientWrapperException(ErrorCodes.WriteConsole, ioe);
        }
    }

    /**
     * Sends a series of <UCP> [firstKeycode] from source to destination through the output console
     * of the cec-communication channel immediately followed by <UCP> [secondKeycode]. No <UCR>
     * message is sent.
     */
    public void sendUserControlInterruptedPressAndHold(
            LogicalAddress source,
            LogicalAddress destination,
            int firstKeycode,
            int secondKeycode,
            boolean holdKey)
            throws CecClientWrapperException {
        sendUserControlPress(source, destination, firstKeycode, holdKey);
        try {
            /* Sleep less than 200ms between press and release */
            TimeUnit.MILLISECONDS.sleep(100);
        } catch (InterruptedException ie) {
            throw new CecClientWrapperException(ErrorCodes.WriteConsole, ie);
        }
        sendUserControlPress(source, destination, secondKeycode, false);
    }

    /** Sends a poll message to the device */
    public void sendPoll() throws Exception {
        sendPoll(targetDevice);
    }

    /** Sends a poll message to the destination */
    public void sendPoll(LogicalAddress destination) throws Exception {
        String command = CecClientMessage.POLL + " " + destination;
        sendConsoleMessage(command);
    }


    /** Sends a message to the output console of the cec-client */
    public void sendConsoleMessage(String message) throws CecClientWrapperException {
        sendConsoleMessage(message, mOutputConsole);
    }

    /** Sends a message to the output console of the cec-client */
    public void sendConsoleMessage(String message, BufferedWriter outputConsole)
            throws CecClientWrapperException {
        CLog.v("Sending console message:: " + message);
        try {
            outputConsole.write(message);
            outputConsole.flush();
        } catch (IOException ioe) {
            throw new CecClientWrapperException(ErrorCodes.WriteConsole, ioe);
        }
    }

    /** Check for any string on the input console of the cec-client, uses default timeout */
    public boolean checkConsoleOutput(String expectedMessage) throws CecClientWrapperException {
        return checkConsoleOutput(expectedMessage, DEFAULT_TIMEOUT);
    }

    /** Check for any string on the input console of the cec-client */
    public boolean checkConsoleOutput(String expectedMessage, long timeoutMillis)
            throws CecClientWrapperException {
        checkCecClient();
        return checkConsoleOutput(expectedMessage, timeoutMillis, mInputConsole);
    }

    /** Check for any string on the specified input console */
    public boolean checkConsoleOutput(
            String expectedMessage, long timeoutMillis, BufferedReader inputConsole)
            throws CecClientWrapperException {
        long startTime = System.currentTimeMillis();
        long endTime = startTime;

        while ((endTime - startTime <= timeoutMillis)) {
            try {
                if (inputConsole.ready()) {
                    String line = inputConsole.readLine();
                    if (line != null
                            && line.toLowerCase().contains(expectedMessage.toLowerCase())) {
                        CLog.v("Found " + expectedMessage + " in " + line);
                        return true;
                    } else if (line.toLowerCase().contains(CEC_PORT_BUSY.toLowerCase())) {
                        throw new CecClientWrapperException(ErrorCodes.CecPortBusy);
                    }
                }
            } catch (IOException ioe) {
                throw new CecClientWrapperException(ErrorCodes.ReadConsole, ioe);
            }
            endTime = System.currentTimeMillis();
        }
        return false;
    }

    /** Gets all the messages received from the given list of source devices during a period of
     * duration seconds.
     */
    public List<CecOperand> getAllMessages(List<LogicalAddress> sourceList, int duration)
            throws CecClientWrapperException {
        List<CecOperand> receivedOperands = new ArrayList<>();
        long startTime = System.currentTimeMillis();
        long endTime = startTime;

        String source = sourceList.toString().replace(",", "").replace(" ", "");

        Pattern pattern = Pattern.compile("(.*>>)(.*?)" +
                "(" + source + "\\p{XDigit}):(.*)",
            Pattern.CASE_INSENSITIVE);

        while ((endTime - startTime <= (duration * 1000))) {
            try {
                if (mInputConsole.ready()) {
                    String line = mInputConsole.readLine();
                    if (pattern.matcher(line).matches()) {
                        CecOperand operand = CecMessage.getOperand(line);
                        if (!receivedOperands.contains(operand)) {
                            receivedOperands.add(operand);
                        }
                    }
                }
            } catch (IOException ioe) {
                throw new CecClientWrapperException(ErrorCodes.ReadConsole, ioe);
            }
            endTime = System.currentTimeMillis();
        }
        return receivedOperands;
    }

    /**
     * Gets the list of logical addresses which receives messages with operand expectedMessage
     * during a period of duration seconds.
     */
    public List<LogicalAddress> getAllDestLogicalAddresses(CecOperand expectedMessage, int duration)
            throws CecClientWrapperException {
        return getAllDestLogicalAddresses(expectedMessage, "", duration);
    }

    /**
     * Gets the list of logical addresses which receives messages with operand expectedMessage and
     * params during a period of duration seconds.
     */
    public List<LogicalAddress> getAllDestLogicalAddresses(
            CecOperand expectedMessage, String params, int duration)
            throws CecClientWrapperException {
        List<LogicalAddress> destinationAddresses = new ArrayList<>();
        long startTime = System.currentTimeMillis();
        long endTime = startTime;
        Pattern pattern =
                Pattern.compile(
                        "(.*>>)(.*?)" + ":(" + expectedMessage + params + ")(.*)",
                        Pattern.CASE_INSENSITIVE);

        while ((endTime - startTime <= (duration * 1000))) {
            try {
                if (mInputConsole.ready()) {
                    String line = mInputConsole.readLine();
                    if (pattern.matcher(line).matches()) {
                        LogicalAddress destination = CecMessage.getDestination(line);
                        if (!destinationAddresses.contains(destination)) {
                            destinationAddresses.add(destination);
                        }
                    }
                }
            } catch (IOException ioe) {
                throw new CecClientWrapperException(ErrorCodes.ReadConsole, ioe);
            }
            endTime = System.currentTimeMillis();
        }
        return destinationAddresses;
    }

    /**
     * The next checkExpectedOutput calls will also permit a feature abort as an alternate to the
     * expected operand. The feature abort will be permissible if it has
     *
     * @param abortForOperand The operand for which the feature abort could be an allowed response
     * @param reasons List of allowed reasons that the feature abort message could have
     */
    private void setExpectFeatureAbortFor(CecOperand abortOperand, Integer... abortReasons) {
        isFeatureAbortExpected = true;
        featureAbortOperand = abortOperand;
        featureAbortReasons = Arrays.asList(abortReasons);
    }

    /** Removes feature abort as a permissible alternate response for {@link checkExpectedOutput} */
    private void unsetExpectFeatureAbort() {
        isFeatureAbortExpected = false;
        CecOperand featureAbortOperand = CecOperand.FEATURE_ABORT;
        List<Integer> featureAbortReasons = new ArrayList<>(HdmiCecConstants.ABORT_INVALID_OPERAND);
    }

    /**
     * Looks for the CEC expectedMessage broadcast on the cec-client communication channel and
     * returns the first line that contains that message within default timeout. If the CEC message
     * is not found within the timeout, an CecClientWrapperException is thrown.
     */
    public String checkExpectedOutput(CecOperand expectedMessage) throws CecClientWrapperException {
        return checkExpectedOutput(
                targetDevice, LogicalAddress.BROADCAST, expectedMessage, DEFAULT_TIMEOUT, false);
    }

    /**
     * Looks for the CEC expectedMessage sent to CEC device toDevice on the cec-client communication
     * channel and returns the first line that contains that message within default timeout. If the
     * CEC message is not found within the timeout, an CecClientWrapperException is thrown.
     */
    public String checkExpectedOutput(LogicalAddress toDevice, CecOperand expectedMessage)
            throws CecClientWrapperException {
        return checkExpectedOutput(targetDevice, toDevice, expectedMessage, DEFAULT_TIMEOUT, false);
    }

    /**
     * Looks for the broadcasted CEC expectedMessage sent from cec-client device fromDevice on the
     * cec-client communication channel and returns the first line that contains that message within
     * default timeout. If the CEC message is not found within the timeout, an
     * CecClientWrapperException is thrown.
     */
    public String checkExpectedMessageFromClient(
            LogicalAddress fromDevice, CecOperand expectedMessage)
            throws CecClientWrapperException {
        return checkExpectedMessageFromClient(
                fromDevice, LogicalAddress.BROADCAST, expectedMessage);
    }

    /**
     * Looks for the CEC expectedMessage sent from cec-client device fromDevice to CEC device
     * toDevice on the cec-client communication channel and returns the first line that contains
     * that message within default timeout. If the CEC message is not found within the timeout, an
     * CecClientWrapperException is thrown.
     */
    public String checkExpectedMessageFromClient(
            LogicalAddress fromDevice, LogicalAddress toDevice, CecOperand expectedMessage)
            throws CecClientWrapperException {
        return checkExpectedOutput(fromDevice, toDevice, expectedMessage, DEFAULT_TIMEOUT, true);
    }

    /**
     * Looks for the CEC expectedMessage or a {@code <Feature Abort>} for {@code
     * featureAbortOperand} with one of the abort reasons in {@code abortReason} is sent from
     * cec-client device fromDevice to the DUT on the cec-client communication channel and returns
     * the first line that contains that message within default timeout. If the CEC message is not
     * found within the timeout, a CecClientWrapperException is thrown.
     */
    public String checkExpectedOutputOrFeatureAbort(
            LogicalAddress fromDevice,
            CecOperand expectedMessage,
            CecOperand featureAbortOperand,
            Integer... featureAbortReasons)
            throws CecClientWrapperException {
        setExpectFeatureAbortFor(featureAbortOperand, featureAbortReasons);
        String message =
                checkExpectedOutput(
                        targetDevice, fromDevice, expectedMessage, DEFAULT_TIMEOUT, false);
        unsetExpectFeatureAbort();
        return message;
    }

    /**
     * Looks for the CEC expectedMessage broadcast on the cec-client communication channel and
     * returns the first line that contains that message within timeoutMillis. If the CEC message is
     * not found within the timeout, an CecClientWrapperException is thrown.
     */
    public String checkExpectedOutput(CecOperand expectedMessage, long timeoutMillis)
            throws CecClientWrapperException {
        return checkExpectedOutput(
                targetDevice, LogicalAddress.BROADCAST, expectedMessage, timeoutMillis, false);
    }

    /**
     * Looks for the CEC expectedMessage sent to CEC device toDevice on the cec-client communication
     * channel and returns the first line that contains that message within timeoutMillis. If the
     * CEC message is not found within the timeout, an CecClientWrapperException is thrown.
     */
    public String checkExpectedOutput(
            LogicalAddress toDevice, CecOperand expectedMessage, long timeoutMillis)
            throws CecClientWrapperException {
        return checkExpectedOutput(targetDevice, toDevice, expectedMessage, timeoutMillis, false);
    }

    /**
     * Looks for the CEC expectedMessage sent from CEC device fromDevice to CEC device toDevice on
     * the cec-client communication channel and returns the first line that contains that message
     * within default timeout. If the CEC message is not found within the timeout, a
     * CecClientWrapperException is thrown.
     */
    public String checkExpectedOutput(
            LogicalAddress fromDevice, LogicalAddress toDevice, CecOperand expectedMessage)
            throws CecClientWrapperException {
        return checkExpectedOutput(fromDevice, toDevice, expectedMessage, DEFAULT_TIMEOUT, false);
    }

    /**
     * Looks for the CEC expectedMessage sent from CEC device fromDevice to CEC device toDevice on
     * the cec-client communication channel and returns the first line that contains that message
     * within timeoutMillis. If the CEC message is not found within the timeout, an
     * CecClientWrapperException is thrown. This method looks for the CEC messages coming from
     * Cec-client if fromCecClient is true.
     */
    public String checkExpectedOutput(
            LogicalAddress fromDevice,
            LogicalAddress toDevice,
            CecOperand expectedMessage,
            long timeoutMillis,
            boolean fromCecClient)
            throws CecClientWrapperException {
        checkCecClient();
        long startTime = System.currentTimeMillis();
        long endTime = startTime;
        String direction = fromCecClient ? "<<" : ">>";
        Pattern pattern;
        if (expectedMessage == CecOperand.POLL) {
            pattern =
                    Pattern.compile(
                            "(.*"
                                    + direction
                                    + ")(.*?)"
                                    + "("
                                    + fromDevice
                                    + toDevice
                                    + ")(.*)",
                            Pattern.CASE_INSENSITIVE);
        } else {
            String expectedOperands = expectedMessage.toString();
            if (isFeatureAbortExpected) {
                expectedOperands += "|" + CecOperand.FEATURE_ABORT;
            }
            pattern =
                    Pattern.compile(
                            "(.*"
                                    + direction
                                    + ")(.*?)"
                                    + "("
                                    + fromDevice
                                    + toDevice
                                    + "):"
                                    + "("
                                    + expectedOperands
                                    + ")(.*)",
                            Pattern.CASE_INSENSITIVE);
        }
        while ((endTime - startTime <= timeoutMillis)) {
            try {
                if (mInputConsole.ready()) {
                    String line = mInputConsole.readLine();
                    if (pattern.matcher(line).matches()) {
                        if (isFeatureAbortExpected
                                && CecMessage.getOperand(line) == CecOperand.FEATURE_ABORT) {
                            CecOperand featureAbortedFor =
                                    CecOperand.getOperand(CecMessage.getParams(line, 0, 2));
                            int reason = CecMessage.getParams(line, 2, 4);
                            if (featureAbortedFor == featureAbortOperand
                                    && featureAbortReasons.contains(reason)) {
                                return line;
                            } else {
                                continue;
                            }
                        }
                        CLog.v("Found " + expectedMessage.name() + " in " + line);
                        return line;
                    }
                }
            } catch (IOException ioe) {
                throw new CecClientWrapperException(ErrorCodes.ReadConsole, ioe);
            }
            endTime = System.currentTimeMillis();
        }
        throw new CecClientWrapperException(ErrorCodes.CecMessageNotFound, expectedMessage.name());
    }

    public void checkNoMessagesSentFromDevice(int timeoutMillis, List<CecOperand> excludeOperands)
            throws CecClientWrapperException {
        checkCecClient();
        long startTime = System.currentTimeMillis();
        long endTime = startTime;
        Pattern pattern =
                Pattern.compile("(.*>>)(.*?)("
                                + targetDevice
                                + "\\p{XDigit}):(.*)",
                        Pattern.CASE_INSENSITIVE);
        while ((endTime - startTime <= timeoutMillis)) {
            try {
                if (mInputConsole.ready()) {
                    String line = mInputConsole.readLine();
                    if (pattern.matcher(line).matches()) {
                        CecOperand operand = CecMessage.getOperand(line);
                        if(excludeOperands.contains(operand)){
                            continue;
                        }
                        CLog.v("Found unexpected message in " + line);
                        throw new CecClientWrapperException(
                                ErrorCodes.CecMessageFound,
                                CecMessage.getOperand(line)
                                        + " from "
                                        + targetDevice
                                        + " with params "
                                        + CecMessage.getParamsAsString(line));
                    }
                }
            } catch (IOException ioe) {
                throw new CecClientWrapperException(ErrorCodes.ReadConsole, ioe);
            }
            endTime = System.currentTimeMillis();
        }
    }

    public void checkNoMessagesSentFromDevice(int timeoutMillis)
            throws CecClientWrapperException {
        List<CecOperand> excludeOperands = new ArrayList<>();
        checkNoMessagesSentFromDevice(timeoutMillis, excludeOperands);
    }

    /**
     * Looks for the CEC message incorrectMessage sent to CEC device toDevice on the cec-client
     * communication channel and throws an CecClientWrapperException if it finds the line that
     * contains the message within the default timeout. If the CEC message is not found within the
     * timeout, function returns without error.
     */
    public void checkOutputDoesNotContainMessage(
            LogicalAddress toDevice, CecOperand incorrectMessage) throws CecClientWrapperException {
        checkOutputDoesNotContainMessage(toDevice, incorrectMessage, "", DEFAULT_TIMEOUT);
     }

    /**
     * Looks for the CEC message incorrectMessage along with the params sent to CEC device toDevice
     * on the cec-client communication channel and throws an CecClientWrapperException if it finds
     * the line that contains the message with its params within the default timeout. If the CEC
     * message is not found within the timeout, function returns without error.
     */
    public void checkOutputDoesNotContainMessage(
            LogicalAddress toDevice, CecOperand incorrectMessage, String params)
            throws CecClientWrapperException {
        checkOutputDoesNotContainMessage(toDevice, incorrectMessage, params, DEFAULT_TIMEOUT);
    }

    /**
     * Looks for the CEC message incorrectMessage sent to CEC device toDevice on the cec-client
     * communication channel and throws an CecClientWrapperException if it finds the line that
     * contains the message within timeoutMillis. If the CEC message is not found within the
     * timeout, function returns without error.
     */
    public void checkOutputDoesNotContainMessage(
            LogicalAddress toDevice, CecOperand incorrectMessage, long timeoutMillis)
            throws CecClientWrapperException {
        checkOutputDoesNotContainMessage(toDevice, incorrectMessage, "", timeoutMillis);
    }

    /**
     * Looks for the CEC message incorrectMessage along with the params sent to CEC device toDevice
     * on the cec-client communication channel and throws an CecClientWrapperException if it finds
     * the line that contains the message and params within timeoutMillis. If the CEC message is not
     * found within the timeout, function returns without error.
     */
    public void checkOutputDoesNotContainMessage(
            LogicalAddress toDevice, CecOperand incorrectMessage, String params, long timeoutMillis)
            throws CecClientWrapperException {
        checkCecClient();
        long startTime = System.currentTimeMillis();
        long endTime = startTime;
        Pattern pattern =
                Pattern.compile(
                        "(.*>>)(.*?)"
                                + "("
                                + targetDevice
                                + toDevice
                                + "):"
                                + "("
                                + incorrectMessage
                                + params
                                + ")(.*)",
                        Pattern.CASE_INSENSITIVE);

        while ((endTime - startTime <= timeoutMillis)) {
            try {
                if (mInputConsole.ready()) {
                    String line = mInputConsole.readLine();
                    if (pattern.matcher(line).matches()) {
                        CLog.v("Found " + incorrectMessage.name() + " in " + line);
                        throw new CecClientWrapperException(
                                ErrorCodes.CecMessageFound,
                                incorrectMessage.name()
                                        + " to "
                                        + toDevice
                                        + " with params "
                                        + CecMessage.getParamsAsString(line));
                    }
                }
            } catch (IOException ioe) {
                throw new CecClientWrapperException(ErrorCodes.ReadConsole, ioe);
            }
            endTime = System.currentTimeMillis();
        }
     }

    /**
     * Checks that one of the message from the {@code primaryMessages} is broadcasted from target
     * device before sending any of the messages from the {@code secondaryMessages} on the
     * cec-client communication channel within default time.
     *
     * @param primaryMessages   list of CEC messages out of which at least one is expected from the
     *                          target device.
     * @param secondaryMessages list of CEC messages that are not expected before primary messages
     *                          to be sent from the target device.
     * @return the first line that contains any of the primaryMessages.
     * If none of the {@code primaryMessages} are found or if any of the {@code secondaryMessages}
     * are found, exception is thrown.
     */
    public String checkMessagesInOrder(
            List<CecOperand> primaryMessages,
            List<String> secondaryMessages)
            throws CecClientWrapperException {
        return checkMessagesInOrder(LogicalAddress.BROADCAST, primaryMessages, secondaryMessages);
    }

    /**
     * Checks that one of the message from the {@code primaryMessages} is sent from target
     * device to destination before sending any of the messages from the {@code secondaryMessages}
     * on the cec-client communication channel within default time.
     *
     * @param destination       logical address of the destination device.
     * @param primaryMessages   list of CEC messages out of which at least one is expected from the
     *                          target device.
     * @param secondaryMessages list of CEC messages that are not expected before primary messages
     *                          to be sent from the target device.
     * @return the first line that contains any of the primaryMessages.
     * If none of the {@code primaryMessages} are found or if any of the {@code secondaryMessages}
     * are found, exception is thrown.
     */
    public String checkMessagesInOrder(
            LogicalAddress destination,
            List<CecOperand> primaryMessages,
            List<String> secondaryMessages)
            throws CecClientWrapperException {
        return checkMessagesInOrder(
                destination, primaryMessages, secondaryMessages, DEFAULT_TIMEOUT);
    }

    /**
     * Checks that one of the message from the {@code primaryMessages} is sent from target
     * device to destination before sending any of the messages from the {@code secondaryMessages}
     * on the cec-client communication channel within give time.
     *
     * @param destination       logical address of the destination device.
     * @param primaryMessages   list of CEC messages out of which at least one is expected from the
     *                          target device.
     * @param secondaryMessages list of CEC messages that are not expected before primary messages
     *                          to be sent from the target device.
     * @param timeoutMillis     timeout to monitor CEC messages from source device.
     * @return the first line that contains any of the primaryMessages.
     * If none of the {@code primaryMessages} are found or if any of the {@code secondaryMessages}
     * are found, exception is thrown.
     */
    public String checkMessagesInOrder(
            LogicalAddress destination,
            List<CecOperand> primaryMessages,
            List<String> secondaryMessages,
            long timeoutMillis)
            throws CecClientWrapperException {
        return checkMessagesInOrder(
                targetDevice, destination, primaryMessages, secondaryMessages, timeoutMillis);
    }

    /**
     * Checks that one of the message from the {@code primaryMessages} is sent from source device to
     * destination before sending any of the messages from the {@code secondaryMessages}
     * on the cec-client communication channel within give time.
     *
     * @param source            logical address of the source device.
     * @param destination       logical address of the destination device.
     * @param primaryMessages   list of CEC messages out of which at least one is expected from the
     *                          target device.
     * @param secondaryMessages list of CEC messages that are not expected before primary messages
     *                          to be sent from the target device.
     * @param timeoutMillis     timeout to monitor CEC messages from source device.
     * @return the first line that contains any of the primaryMessages.
     * If none of the {@code primaryMessages} are found or if any of the {@code secondaryMessages}
     * are found, exception is thrown.
     */
    public String checkMessagesInOrder(
            LogicalAddress source,
            LogicalAddress destination,
            List<CecOperand> primaryMessages,
            List<String> secondaryMessages,
            long timeoutMillis)
            throws CecClientWrapperException {
        checkCecClient();
        long startTime = System.currentTimeMillis();
        long endTime = startTime;
        Pattern pattern = Pattern.compile("(.*>>)(.*?)"
                        + "(" + source + destination + "):"
                        + "(.*)",
                Pattern.CASE_INSENSITIVE);

        while ((endTime - startTime <= timeoutMillis)) {
            try {
                if (mInputConsole.ready()) {
                    String line = mInputConsole.readLine();
                    if (pattern.matcher(line).matches()) {
                        CecOperand operand = CecMessage.getOperand(line);
                        String params = CecMessage.getParamsAsString(line);
                        // Check for secondary messages. If found, throw an exception.
                        for (String secondaryMessage : secondaryMessages) {
                            if (line.contains(secondaryMessage)) {
                                throw new CecClientWrapperException(ErrorCodes.CecMessageFound,
                                        operand.name() + " to " + destination + " with params "
                                                + CecMessage.getParamsAsString(line));
                            }
                        }
                        // Check for the primary messages.
                        if (primaryMessages.contains(operand)) {
                            CLog.v("Found " + operand.name() + " in " + line);
                            return line;
                        }
                    }
                }
            } catch (IOException ioe) {
                throw new CecClientWrapperException(ErrorCodes.ReadConsole, ioe);
            }
            endTime = System.currentTimeMillis();
        }
        throw new CecClientWrapperException(
                ErrorCodes.CecMessageNotFound, primaryMessages.toString());
    }

    /** Returns the device type that the cec-client has started as. */
    public LogicalAddress getSelfDevice() {
        return selfDevice;
    }

    /** Set the physical address of the cec-client instance */
    public void setPhysicalAddress(int newPhysicalAddress) throws CecClientWrapperException {
        String command =
                String.format(
                        "pa %02d %02d",
                        (newPhysicalAddress & 0xFF00) >> 8, newPhysicalAddress & 0xFF);
        sendConsoleMessage(command);
        physicalAddress = newPhysicalAddress;
    }

    /** Get the physical address of the cec-client instance, will return 0xFFFF if uninitialised */
    public int getPhysicalAddress() {
        return physicalAddress;
    }

    public void clearClientOutput() {
        mInputConsole = new BufferedReader(new InputStreamReader(mCecClient.getInputStream()));
    }

    /**
     * Kills the cec-client process that was created in init().
     */
    private void killCecProcess() {
        try {
            boolean processQuit = false;
            checkCecClient();
            sendConsoleMessage(CecClientMessage.QUIT_CLIENT.toString());
            if (checkConsoleOutput(
                    CecClientMessage.CLIENT_CONSOLE_END.toString(), MILLISECONDS_TO_READY)) {
                mOutputConsole.close();
                mInputConsole.close();
                if (mCecClient.waitFor(MILLISECONDS_TO_READY, TimeUnit.MILLISECONDS)) {
                    /* The cec-client process is quit */
                    processQuit = true;
                }
            }
            mCecClientInitialised = false;
            if (!processQuit) {
                /* Use a pkill cec-client if the cec-client process is not dead in spite of the
                 * quit above.
                 */
                List<String> commands = new ArrayList<>();
                Process killProcess;
                commands.add("pkill");
                commands.add("cec-client");
                killProcess = RunUtil.getDefault().runCmdInBackground(commands);
                killProcess.waitFor();
            }
        } catch (IOException | InterruptedException | CecClientWrapperException e) {
            /*
             * If cec-client is not running, do not throw a CecClientWrapperException, just return.
             */
            CLog.w(new CecClientWrapperException(ErrorCodes.CecClientStop, e));
        }
    }
}
