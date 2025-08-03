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

package android.telecom.cts;

import android.content.Context;
import android.util.Log;

/**
 * The CTS test class InCallServiceFlagChecker is meant to check flags passed into InCallController.
 * Flags passed in InCallController can cause unwanted behavior.
 */
public class InCallServiceFlagChecker extends BaseTelecomTestWithMockServices {

    public static final String LOG_TAG = InCallServiceFlagChecker.class.getName();
    public static final String TARGET_SERVICE = ".MockInCallService:system";
    public static final String DUMPSYS_COMMAND = "dumpsys activity services android.telecom.cts";
    public static final String FLAGS_TEXT_MATCHER = "flags=0x";
    public static final int FLAGS_OFFSET = FLAGS_TEXT_MATCHER.length();
    public static final char SPACE_CHAR = ' ';

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        NewOutgoingCallBroadcastReceiver.reset();
        if (mShouldTestTelecom) {
            setupConnectionService(null, FLAG_REGISTER | FLAG_ENABLE);
        }
    }

    @Override
    protected void tearDown() throws Exception {
        if (mShouldTestTelecom) {
            TestUtils.clearSystemDialerOverride(getInstrumentation());
            TestUtils.removeTestEmergencyNumber(getInstrumentation(), TEST_EMERGENCY_NUMBER);
        }
        super.tearDown();
    }

    /**
     * CTS test to ensure InCallService bindings DO NOT have BIND_ABOVE_CLIENT flag set on binding.
     */
    public void testIsBindAboveClientFlagSet() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }

        // Trigger InCallService so flags can be examined. Otherwise, the service will not show
        // up when the dumpsys command is executed.
        placeAndVerifyCall();
        verifyConnectionForOutgoingCall();

        // Dump the testing package, android.telecom.cts, service information. Doing so will expose
        // if the BIND_ABOVE_CLIENT flag is set or not on our target InCallService.
        String dumpOutput = TestUtils.executeShellCommand(getInstrumentation(), DUMPSYS_COMMAND);
        assertNotNull(dumpOutput);
        assertTrue(dumpOutput.length() > 0);
        Log.i(LOG_TAG, dumpOutput);

        // Take off chunk of unwanted pretext.
        String targetSection = dumpOutput.substring(dumpOutput.indexOf(TARGET_SERVICE));
        assertNotNull(targetSection);
        assertTrue(targetSection.length() > 0);
        Log.i(LOG_TAG, targetSection);

        // extract the TARGET_SERVICE flags
        int flagsValue = extractFlagsValue(targetSection);

        // assert the Context.BIND_ABOVE_CLIENT flag is not set!
        assertTrue((flagsValue & Context.BIND_ABOVE_CLIENT) != Context.BIND_ABOVE_CLIENT);
    }


    /**
     * Find the first flag section of the given string which contains ConnectionRecordObject's
     *
     * @param s ConnectionRecordObject in string format.
     * @return flags decimal value in the ConnectionRecordObject.
     */
    public int extractFlagsValue(String s) {
        // builder to pull flag digits from dumped text
        StringBuilder sb = new StringBuilder();

        // find the flags=0x text to begin pulling flag digits
        int i = s.indexOf(FLAGS_TEXT_MATCHER) + FLAGS_OFFSET;
        int n = s.length();

        // If (i == -1) then this means the flag information was never found.
        assertTrue(i != -1); // assert we found flag info

        // stop when either there is no more text or a space is encountered
        while (i < n && s.charAt(i) != SPACE_CHAR) {
            sb.append(s.charAt(i));
            i++;
        }

        // convert string builder into string
        String extractedFlagAsString = sb.toString();
        assertNotNull(extractedFlagAsString);
        assertTrue(extractedFlagAsString.length() > 0);
        Log.i(LOG_TAG, extractedFlagAsString);

        // convert the string into and integer and return value
        return Integer.parseInt(extractedFlagAsString);
    }
}
