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

package com.android.server;

import com.android.net.module.util.HexDump;

public class IpSecXfrmControllerTestHex {
    private static final String XFRM_NEW_SA_HEX_STRING =
            "2003000010000000000000003FE1D4B6"
                    + "00000000000000000000000000000000"
                    + "00000000000000000000000000000000"
                    + "00000000000000000A00000000000000"
                    + "000000000000000020010DB800000000"
                    + "0000000000000111AABBCCDD32000000"
                    + "20010DB8000000000000000000000222"
                    + "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF"
                    + "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF"
                    + "00000000000000000000000000000000"
                    + "00000000000000000000000000000000"
                    + "00000000000000000000000000000000"
                    + "FD464C65000000000000000000000000"
                    + "00000000000000000000000000000000"
                    + "024000000A0000000000000000000000"
                    + "5C000100686D61632873686131290000"
                    + "00000000000000000000000000000000"
                    + "00000000000000000000000000000000"
                    + "00000000000000000000000000000000"
                    + "00000000A000000055F01AC07E15E437"
                    + "115DDE0AEDD18A822BA9F81E60001400"
                    + "686D6163287368613129000000000000"
                    + "00000000000000000000000000000000"
                    + "00000000000000000000000000000000"
                    + "00000000000000000000000000000000"
                    + "A00000006000000055F01AC07E15E437"
                    + "115DDE0AEDD18A822BA9F81E58000200"
                    + "63626328616573290000000000000000"
                    + "00000000000000000000000000000000"
                    + "00000000000000000000000000000000"
                    + "00000000000000000000000000000000"
                    + "800000006AED4975ADF006D65C76F639"
                    + "23A6265B1C0117004000000000000000"
                    + "00000000000000000000000000080000"
                    + "00000000000000000000000000000000"
                    + "00000000000000000000000000000000"
                    + "00000000000000000000000000000000"
                    + "00000000000000000000000000000000"
                    + "00000000000000000000000000000000"
                    + "00000000000000000000000000000000"
                    + "00000000000000000000000000000000"
                    + "00000000000000000000000000000000"
                    + "00000000000000000000000000000000"
                    + "00000000000000000000000000000000"
                    + "00000000000000000000000000000000"
                    + "00000000000000000000000000000000"
                    + "00000000000000000000000000000000"
                    + "00000000000000000000000000000000"
                    + "00000000000000000000000000000000"
                    + "00000000000000000000000000000000"
                    + "00000000000000000000000000000000";
    public static final byte[] XFRM_NEW_SA_HEX =
            HexDump.hexStringToByteArray(XFRM_NEW_SA_HEX_STRING);

    private static final String XFRM_ESRCH_HEX_STRING =
            "3C0000000200000000000000A5060000"
                    + "FDFFFFFF280000001200010000000000"
                    + "0000000020010DB80000000000000000"
                    + "00000111AABBCCDD0A003200";
    public static final byte[] XFRM_ESRCH_HEX = HexDump.hexStringToByteArray(XFRM_ESRCH_HEX_STRING);
}
