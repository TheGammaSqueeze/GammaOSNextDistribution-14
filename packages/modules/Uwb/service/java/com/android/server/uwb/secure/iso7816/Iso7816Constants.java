/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.server.uwb.secure.iso7816;

/** A sampling of constants defined by ISO7816. */
public abstract class Iso7816Constants {
    public static final byte CLA_BASE = 0x00;

    public static final byte CLA_PROPRIETARY = (byte) 0x80;

    // ISO7816-4 CLA mask indicating that command chaining is being used
    public static final byte CLA_COMMAND_CHAINING_MASK = (byte) 0x10;

    public static final byte INS_SELECT = (byte) 0xA4;

    public static final byte INS_READ_RECORD = (byte) 0xB2;

    public static final byte INS_GET_DATA = (byte) 0xCA;

    public static final byte INS_GET_PROCSESSING_OPTIONS = (byte) 0xA8;

    public static final byte OFFSET_CLA = 0;

    public static final byte OFFSET_INS = 1;

    public static final byte OFFSET_P1 = 2;

    public static final byte OFFSET_P2 = 3;

    public static final byte OFFSET_LC = 4;

    public static final byte OFFSET_CDATA = 5;

    /** Used with {@link #INS_SELECT} to select an application by application DF (aka AID). */
    public static final byte P1_SELECT_BY_DEDICATED_FILE_NAME = (byte) 0x04;

    public static final byte TAG_LIST = (byte) 0x5C;

    public static final byte EXTENDED_HEAD_LIST = (byte) 0x4D;

    private Iso7816Constants() {
    }
}
