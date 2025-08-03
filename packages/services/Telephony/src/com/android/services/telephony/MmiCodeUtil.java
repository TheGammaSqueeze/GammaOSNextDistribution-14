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

package com.android.services.telephony;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MmiCodeUtil {
    //***** Constants

    // Supp Service codes from TS 22.030 Annex B

    //Called line presentation
    static final String SC_CLIP    = "30";
    static final String SC_CLIR    = "31";

    // Call Forwarding
    static final String SC_CFU     = "21";
    static final String SC_CFB     = "67";
    static final String SC_CFNRy   = "61";
    static final String SC_CFNR    = "62";

    static final String SC_CF_All = "002";
    static final String SC_CF_All_Conditional = "004";

    // Call Waiting
    static final String SC_WAIT     = "43";

    // Call Barring
    static final String SC_BAOC         = "33";
    static final String SC_BAOIC        = "331";
    static final String SC_BAOICxH      = "332";
    static final String SC_BAIC         = "35";
    static final String SC_BAICr        = "351";

    static final String SC_BA_ALL       = "330";
    static final String SC_BA_MO        = "333";
    static final String SC_BA_MT        = "353";

    // Supp Service Password registration
    static final String SC_PWD          = "03";

    // PIN/PIN2/PUK/PUK2
    static final String SC_PIN          = "04";
    static final String SC_PIN2         = "042";
    static final String SC_PUK          = "05";
    static final String SC_PUK2         = "052";

    // See TS 22.030 6.5.2 "Structure of the MMI"

    static Pattern sPatternSuppService = Pattern.compile(
        "((\\*|#|\\*#|\\*\\*|##)(\\d{2,3})(\\*([^*#]*)(\\*([^*#]*)(\\*([^*#]*)(\\*([^*#]*))?)?)?)?#)(.*)");
/*       1  2                    3          4  5       6   7         8    9     10  11             12

         1 = Full string up to and including #
         2 = action (activation/interrogation/registration/erasure)
         3 = service code
         5 = SIA
         7 = SIB
         9 = SIC
         10 = dialing number
*/

    static final int MATCH_GROUP_SERVICE_CODE = 3;

    public static final String BUTTON_CLIR_KEY  = "button_clir_key";
    public static final String BUTTON_CW_KEY    = "button_cw_key";
    public static final String CALL_FORWARDING_KEY = "call_forwarding_key";
    public static final String CALL_BARRING_KEY = "call_barring_key";

    //***** Public Class methods
    public static String getMmiServiceCode(String dialString) {
        Matcher m;
        String ret = null;

        m = sPatternSuppService.matcher(dialString);

        if (m.matches()) {
            ret = makeEmptyNull(m.group(MATCH_GROUP_SERVICE_CODE));
        }

        return ret;
    }

    private static String makeEmptyNull(String s) {
        if (s != null && s.length() == 0) return null;

        return s;
    }

    static boolean isServiceCodeCallForwarding(String sc) {
        return sc != null &&
                (sc.equals(SC_CFU)
                || sc.equals(SC_CFB) || sc.equals(SC_CFNRy)
                || sc.equals(SC_CFNR) || sc.equals(SC_CF_All)
                || sc.equals(SC_CF_All_Conditional));
    }

    static boolean isServiceCodeCallBarring(String sc) {
        return sc != null &&
            (sc.equals(SC_BAOC)
             || sc.equals(SC_BAOIC) || sc.equals(SC_BAOICxH)
             || sc.equals(SC_BAIC) || sc.equals(SC_BAICr)
             || sc.equals(SC_BA_ALL) || sc.equals(SC_BA_MO)
             || sc.equals(SC_BA_MT));
    }

    static boolean isPinPukCommand(String sc) {
        return sc != null && (sc.equals(SC_PIN) || sc.equals(SC_PIN2)
                              || sc.equals(SC_PUK) || sc.equals(SC_PUK2));
     }

    public static String getSuppServiceKey(String dialString) {
        String sc = getMmiServiceCode(dialString);
        if (sc != null && sc.equals(SC_CLIP)) {
            return "";
        } else if (sc != null && sc.equals(SC_CLIR)) {
            return BUTTON_CLIR_KEY;
        } else if (isServiceCodeCallForwarding(sc)) {
            return CALL_FORWARDING_KEY;
        } else if (isServiceCodeCallBarring(sc)) {
            return CALL_BARRING_KEY;
        } else if (sc != null && sc.equals(SC_PWD)) {
            return "";
        } else if (sc != null && sc.equals(SC_WAIT)) {
            return BUTTON_CW_KEY;
        } else if (isPinPukCommand(sc)) {
            return "";
        } else {
            return null;
        }
    }
}
