/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.server.telecom;

import android.net.Uri;
import android.telecom.PhoneAccount;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MmiUtils {
    // See TS 22.030 6.5.2 "Structure of the MMI"

    private static Pattern sPatternSuppService = Pattern.compile(
            "((\\*|#|\\*#|\\*\\*|##)(\\d{2,3})(\\*([^*#]*)(\\*([^*#]*)(\\*([^*#]*)(\\*([^*#]*))?)?)?)?#)(.*)");
        /*       1  2                    3          4  5       6   7         8    9     10  11
              12

                 1 = Full string up to and including #
                 2 = action (activation/interrogation/registration/erasure)
                 3 = service code
                 5 = SIA
                 7 = SIB
                 9 = SIC
                 10 = dialing number
        */
    //regex groups
    static final int MATCH_GROUP_POUND_STRING = 1;
    static final int MATCH_GROUP_ACTION = 2; //(activation/interrogation/registration/erasure)
    static final int MATCH_GROUP_SERVICE_CODE = 3;
    static final int MATCH_GROUP_SIA = 5;
    static final int MATCH_GROUP_SIB = 7;
    static final int MATCH_GROUP_SIC = 9;
    static final int MATCH_GROUP_PWD_CONFIRM = 11;
    static final int MATCH_GROUP_DIALING_NUMBER = 12;
    // Call Forwarding service codes
    static final String SC_CFU = "21";
    static final String SC_CFB = "67";
    static final String SC_CFNRy = "61";
    static final String SC_CFNR = "62";
    static final String SC_CF_All = "002";
    static final String SC_CF_All_Conditional = "004";

    //see: https://nationalnanpa.com/number_resource_info/vsc_assignments.html
    @SuppressWarnings("DoubleBraceInitialization")
    private static Set<String> sDangerousVerticalServiceCodes = new HashSet<String>()
    {{
        add("*09"); //Selective Call Blocking/Reporting
        add("*42"); //Change Forward-To Number for Cust Programmable Call Forwarding Don't Answer
        add("*56"); //Change Forward-To Number for ISDN Call Forwarding
        add("*60"); //Selective Call Rejection Activation
        add("*63"); //Selective Call Forwarding Activation
        add("*64"); //Selective Call Acceptance Activation
        add("*68"); //Call Forwarding Busy Line/Don't Answer Activation
        add("*72"); //Call Forwarding Activation
        add("*77"); //Anonymous Call Rejection Activation
        add("*78"); //Do Not Disturb Activation
    }};
    private final int mMinLenInDangerousSet;
    private final int mMaxLenInDangerousSet;

    public MmiUtils() {
        mMinLenInDangerousSet = sDangerousVerticalServiceCodes.stream()
                .mapToInt(String::length)
                .min()
                .getAsInt();
        mMaxLenInDangerousSet = sDangerousVerticalServiceCodes.stream()
                .mapToInt(String::length)
                .max()
                .getAsInt();
    }

    /**
     * Determines if the Uri represents a call forwarding related mmi code
     *
     * @param handle The URI to call.
     * @return {@code True} if the URI represents a call forwarding related MMI
     */
    private static boolean isCallForwardingMmiCode(Uri handle) {
        Matcher m;
        String dialString = handle.getSchemeSpecificPart();
        m = sPatternSuppService.matcher(dialString);

        if (m.matches()) {
            String sc = m.group(MATCH_GROUP_SERVICE_CODE);
            return sc != null &&
                    (sc.equals(SC_CFU)
                            || sc.equals(SC_CFB) || sc.equals(SC_CFNRy)
                            || sc.equals(SC_CFNR) || sc.equals(SC_CF_All)
                            || sc.equals(SC_CF_All_Conditional));
        }

        return false;

    }

    private static boolean isTelScheme(Uri handle) {
        return (handle != null && handle.getSchemeSpecificPart() != null &&
                handle.getScheme() != null &&
                handle.getScheme().equals(PhoneAccount.SCHEME_TEL));
    }

    private boolean isDangerousVerticalServiceCode(Uri handle) {
        if (isTelScheme(handle)) {
            String dialedNumber = handle.getSchemeSpecificPart();
            if (dialedNumber.length() >= mMinLenInDangerousSet && dialedNumber.charAt(0) == '*') {
                //we only check vertical codes defined by The North American Numbering Plan Admin
                //see: https://nationalnanpa.com/number_resource_info/vsc_assignments.html
                //only two or 3-digit codes are valid as of today, but the code is generic enough.
                for (int prefixLen = mMaxLenInDangerousSet; prefixLen <= mMaxLenInDangerousSet;
                        prefixLen++) {
                    String prefix = dialedNumber.substring(0, prefixLen);
                    if (sDangerousVerticalServiceCodes.contains(prefix)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Determines if a dialed number is potentially an In-Call MMI code.  In-Call MMI codes are
     * MMI codes which can be dialed when one or more calls are in progress.
     * <P>
     * Checks for numbers formatted similar to the MMI codes defined in:
     * {@link com.android.internal.telephony.Phone#handleInCallMmiCommands(String)}
     *
     * @param handle The URI to call.
     * @return {@code True} if the URI represents a number which could be an in-call MMI code.
     */
    public boolean isPotentialInCallMMICode(Uri handle) {
        if (isTelScheme(handle)) {
            String dialedNumber = handle.getSchemeSpecificPart();
            return (dialedNumber.equals("0") ||
                    (dialedNumber.startsWith("1") && dialedNumber.length() <= 2) ||
                    (dialedNumber.startsWith("2") && dialedNumber.length() <= 2) ||
                    dialedNumber.equals("3") ||
                    dialedNumber.equals("4") ||
                    dialedNumber.equals("5"));
        }
        return false;
    }

    public boolean isPotentialMMICode(Uri handle) {
        return (handle != null && handle.getSchemeSpecificPart() != null
                && handle.getSchemeSpecificPart().contains("#"));
    }

    /**
     * Determines if the Uri represents a dangerous MMI code or Vertical Service code. Dangerous
     * codes are ones, for which,
     * we normally expect the user to be aware that an application has dialed them
     *
     * @param handle The URI to call.
     * @return {@code True} if the URI represents a dangerous code
     */
    public boolean isDangerousMmiOrVerticalCode(Uri handle) {
        if (isPotentialMMICode(handle)) {
            return isCallForwardingMmiCode(handle);
            //since some dangerous mmi codes could be carrier specific, in the future,
            //we can add a carrier config item which can list carrier specific dangerous mmi codes
        } else if (isDangerousVerticalServiceCode(handle)) {
            return true;
        }
        return false;
    }
}
