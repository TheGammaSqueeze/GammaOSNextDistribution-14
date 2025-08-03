/*
 * Copyright 2021 HIMSA II K/S - www.himsa.com.
 * Represented by EHIMA - www.ehima.com.
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

package com.android.bluetooth.mcp;

import static java.util.Map.entry;

import java.util.Map;

/**
 * Media control request, from client to Media Player
 */
public final class Request {
    private final int mOpcode;
    private final Integer mIntArg;

    /**
     * Media control request constructor
     *
     * @param opcode Control request opcode
     * @param arg    Control request argument
     */
    public Request(int opcode, int arg) {
        this.mOpcode = opcode;
        this.mIntArg = arg;
    }

    /**
     * Media control results opcode getter
     *
     * @return Control request opcode
     */
    public int getOpcode() {
        return mOpcode;
    }

    /**
     * Media control results argument getter
     *
     * @return Control request argument
     */
    public int getIntArg() {
        return mIntArg;
    }

    /**
     * Media control request results definition
     */
    public enum Results {
        SUCCESS(0x01),
        OPCODE_NOT_SUPPORTED(0x02),
        MEDIA_PLAYER_INACTIVE(0x03),
        COMMAND_CANNOT_BE_COMPLETED(0x04);

        private final int mValue;

        Results(int value) {
            mValue = value;
        }

        public int getValue() {
            return mValue;
        }
    }

    /**
     * Media control request supported opcodes definition
     */
    public final static class SupportedOpcodes {
        public static final int NONE = 0x00;
        public static final int PLAY = 0x01;
        public static final int PAUSE = 0x02;
        public static final int FAST_REWIND = 0x04;
        public static final int FAST_FORWARD = 0x08;
        public static final int STOP = 0x10;
        public static final int MOVE_RELATIVE = 0x20;
        public static final int PREVIOUS_SEGMENT = 0x40;
        public static final int NEXT_SEGMENT = 0x80;
        public static final int FIRST_SEGMENT = 0x0100;
        public static final int LAST_SEGMENT = 0x0200;
        public static final int GOTO_SEGMENT = 0x0400;
        public static final int PREVIOUS_TRACK = 0x0800;
        public static final int NEXT_TRACK = 0x1000;
        public static final int FIRST_TRACK = 0x2000;
        public static final int LAST_TRACK = 0x4000;
        public static final int GOTO_TRACK = 0x8000;
        public static final int PREVIOUS_GROUP = 0x010000;
        public static final int NEXT_GROUP = 0x020000;
        public static final int FIRST_GROUP = 0x040000;
        public static final int LAST_GROUP = 0x080000;
        public static final int GOTO_GROUP = 0x100000;

        static String toString(int opcodes) {
            StringBuilder sb = new StringBuilder();
            boolean is_complex = false;

            sb.append("0x" + Integer.toHexString(opcodes) + " (");
            for (int i = 1; i <= opcodes; i <<= 1) {
                int opcode = opcodes & i;
                if (opcode != 0) {
                    if (is_complex) {
                        sb.append(" | ");
                    } else {
                        is_complex = true;
                    }
                    sb.append(singleOpcodeToString(opcode));
                }
            }
            sb.append(")");
            return sb.toString();
        }

        private static String singleOpcodeToString(int opcode) {
            switch (opcode) {
                case 0x01:
                    return "PLAY";
                case 0x02:
                    return "PAUSE";
                case 0x04:
                    return "FAST_REWIND";
                case 0x08:
                    return "FAST_FORWARD";
                case 0x10:
                    return "STOP";
                case 0x20:
                    return "MOVE_RELATIVE";
                case 0x40:
                    return "PREVIOUS_SEGMENT";
                case 0x80:
                    return "NEXT_SEGMENT";
                case 0x0100:
                    return "FIRST_SEGMENT";
                case 0x0200:
                    return "LAST_SEGMENT";
                case 0x0400:
                    return "GOTO_SEGMENT";
                case 0x0800:
                    return "PREVIOUS_TRACK";
                case 0x1000:
                    return "NEXT_TRACK";
                case 0x2000:
                    return "FIRST_TRACK";
                case 0x4000:
                    return "LAST_TRACK";
                case 0x8000:
                    return "GOTO_TRACK";
                case 0x010000:
                    return "PREVIOUS_GROUP";
                case 0x020000:
                    return "NEXT_GROUP";
                case 0x040000:
                    return "FIRST_GROUP";
                case 0x080000:
                    return "LAST_GROUP";
                case 0x100000:
                    return "GOTO_GROUP";
                default:
                    return "0x" + Integer.toHexString(opcode);
            }
        }
    }

    /**
     * Media control request opcodes definition
     */
    public final static class Opcodes {
        public static final int PLAY = 0x01;
        public static final int PAUSE = 0x02;
        public static final int FAST_REWIND = 0x03;
        public static final int FAST_FORWARD = 0x04;
        public static final int STOP = 0x05;
        public static final int MOVE_RELATIVE = 0x10;
        public static final int PREVIOUS_SEGMENT = 0x20;
        public static final int NEXT_SEGMENT = 0x21;
        public static final int FIRST_SEGMENT = 0x22;
        public static final int LAST_SEGMENT = 0x23;
        public static final int GOTO_SEGMENT = 0x24;
        public static final int PREVIOUS_TRACK = 0x30;
        public static final int NEXT_TRACK = 0x31;
        public static final int FIRST_TRACK = 0x32;
        public static final int LAST_TRACK = 0x33;
        public static final int GOTO_TRACK = 0x34;
        public static final int PREVIOUS_GROUP = 0x40;
        public static final int NEXT_GROUP = 0x41;
        public static final int FIRST_GROUP = 0x42;
        public static final int LAST_GROUP = 0x43;
        public static final int GOTO_GROUP = 0x44;

        static String toString(int opcode) {
            switch(opcode) {
                case 0x01:
                    return "PLAY(0x01)";
                case 0x02:
                    return "PAUSE(0x02)";
                case 0x03:
                    return "FAST_REWIND(0x03)";
                case 0x04:
                    return "FAST_FORWARD(0x04)";
                case 0x05:
                    return "STOP(0x05)";
                case 0x10:
                    return "MOVE_RELATIVE(0x10)";
                case 0x20:
                    return "PREVIOUS_SEGMENT(0x20)";
                case 0x21:
                    return "NEXT_SEGMENT(0x21)";
                case 0x22:
                    return "FIRST_SEGMENT(0x22)";
                case 0x23:
                    return "LAST_SEGMENT(0x23)";
                case 0x24:
                    return "GOTO_SEGMENT(0x24)";
                case 0x30:
                    return "PREVIOUS_TRACK(0x30)";
                case 0x31:
                    return "NEXT_TRACK(0x31)";
                case 0x32:
                    return "FIRST_TRACK(0x32)";
                case 0x33:
                    return "LAST_TRACK(0x33)";
                case 0x34:
                    return "GOTO_TRACK(0x34)";
                case 0x40:
                    return "PREVIOUS_GROUP(0x40)";
                case 0x41:
                    return "NEXT_GROUP(0x41)";
                case 0x42:
                    return "FIRST_GROUP(0x42)";
                case 0x43:
                    return "LAST_GROUP(0x43)";
                case 0x44:
                    return "GOTO_GROUP(0x44)";
                default:
                    return "UNKNOWN(0x" + Integer.toHexString(opcode) + ")";
            }
        }
    }

    /* Map opcodes which are written to 'Media Control Point' characteristics to their corresponding
     * feature bit masks used in 'Media Control Point Opcodes Supported' characteristic.
     */
    public final static Map<Integer, Integer> OpcodeToOpcodeSupport = Map.ofEntries(
            entry(Opcodes.PLAY, SupportedOpcodes.PLAY),
            entry(Opcodes.PAUSE, SupportedOpcodes.PAUSE),
            entry(Opcodes.FAST_REWIND, SupportedOpcodes.FAST_REWIND),
            entry(Opcodes.FAST_FORWARD, SupportedOpcodes.FAST_FORWARD),
            entry(Opcodes.STOP, SupportedOpcodes.STOP),
            entry(Opcodes.MOVE_RELATIVE, SupportedOpcodes.MOVE_RELATIVE),
            entry(Opcodes.PREVIOUS_SEGMENT, SupportedOpcodes.PREVIOUS_SEGMENT),
            entry(Opcodes.NEXT_SEGMENT, SupportedOpcodes.NEXT_SEGMENT),
            entry(Opcodes.FIRST_SEGMENT, SupportedOpcodes.FIRST_SEGMENT),
            entry(Opcodes.LAST_SEGMENT, SupportedOpcodes.LAST_SEGMENT),
            entry(Opcodes.GOTO_SEGMENT, SupportedOpcodes.GOTO_SEGMENT),
            entry(Opcodes.PREVIOUS_TRACK, SupportedOpcodes.PREVIOUS_TRACK),
            entry(Opcodes.NEXT_TRACK, SupportedOpcodes.NEXT_TRACK),
            entry(Opcodes.FIRST_TRACK, SupportedOpcodes.FIRST_TRACK),
            entry(Opcodes.LAST_TRACK, SupportedOpcodes.LAST_TRACK),
            entry(Opcodes.GOTO_TRACK, SupportedOpcodes.GOTO_TRACK),
            entry(Opcodes.PREVIOUS_GROUP, SupportedOpcodes.PREVIOUS_GROUP),
            entry(Opcodes.NEXT_GROUP, SupportedOpcodes.NEXT_GROUP),
            entry(Opcodes.FIRST_GROUP, SupportedOpcodes.FIRST_GROUP),
            entry(Opcodes.LAST_GROUP, SupportedOpcodes.LAST_GROUP),
            entry(Opcodes.GOTO_GROUP, SupportedOpcodes.GOTO_GROUP));

}
