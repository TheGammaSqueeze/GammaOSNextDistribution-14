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

package com.android.cts.verifier.audio.midilib;

import android.media.midi.MidiDevice;
import android.media.midi.MidiInputPort;
import android.media.midi.MidiManager;
import android.media.midi.MidiReceiver;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * A test module that tests Java MIDI
 */
public abstract class JavaMidiTestModule extends MidiTestModule {
    private static final String TAG = "JavaMidiTestModule";
    private static final boolean DEBUG = true;

    //
    // MIDI Messages
    //
    // channel-oriented message (Commands)
    public static final byte MIDICMD_NOTEON = 9;
    public static final byte MIDICMD_NOTEOFF = 8;
    public static final byte MIDICMD_POLYPRESS = 10;
    public static final byte MIDICMD_CONTROL = 11;
    public static final byte MIDICMD_PROGRAMCHANGE = 12;
    public static final byte MIDICMD_CHANNELPRESS = 13;
    public static final byte MIDICMD_PITCHWHEEL = 14;

    public static final byte MIDICMD_SYSEX = (byte) 0xF0;
    public static final byte MIDICMD_EOSYSEX = (byte) 0xF7; // (byte)0b11110111;    // 0xF7

    // Active Sensing messages should be ignored.
    public static final byte MIDICMD_SYSACTIVESENSING = (byte) 0xFE;

    // In some instances, Bluetooth MIDI in particular, it is possible to overrun
    // the bandwidth, resulting in lost data. In this case, slow the data stream
    // down.
    private static final int THROTTLE_MAX_PACKET_SIZE = 15;
    private static final int THROTTLE_PERIOD_MS = 20;

    private static final int MESSAGE_MAX_BYTES = 4096;

    protected boolean mTestMismatched;

    // Test Data
    // - The set of messages to send
    private ArrayList<TestMessage> mTestMessages = new ArrayList<TestMessage>();

    // - The stream of message data to walk through when MIDI data is received.
    // NOTE: To work on USB Audio Peripherals that drop the first message
    // (AudioBoxUSB), have 2 streams to match against, one with the "warm-up"
    // message in tact ("Nominal") and one where it is absent.
    private ArrayList<Byte> mMatchStream = new ArrayList<Byte>();

    private int mReceiveStreamPos;

    // Some MIDI interfaces have been know to consistently drop the first message
    // Send one to throw away. If it shows up, ignore it. If it doesn't then
    // there is nothing there to ignore and the remainder should be legitimate.
    // Use the MIDI CONTROL command to identify this "warm-up" message
    private byte[] mWarmUpMsg = {makeMIDICmd(MIDICMD_CONTROL, 0), 0, 0};

    public JavaMidiTestModule(int deviceType) {
        super(deviceType);
        setupTestMessages();
    }

    protected abstract void openMidiDevice();

    @Override
    public void startLoopbackTest(int testId) {
        synchronized (mTestLock) {
            mTestCounter++;
            mTestRunning = true;
            enableTestButtonsAbstract(false);
        }

        if (DEBUG) {
            Log.i(TAG, "---- startLoopbackTest()");
        }

        mTestStatus = TESTSTATUS_NOTRUN;
        mTestMismatched = false;
        mReceiveStreamPos = 0;

        // These might be left open due to a failing, previous test
        // so just to be sure...
        closePorts();

        if (mIODevice.mSendDevInfo != null) {
            if (DEBUG) {
                Log.i(TAG, "---- mMidiManager.openDevice() mSendDevice: "
                        + mIODevice.mSendDevInfo);
            }
            openMidiDevice();
        }

        startTimeoutHandler();
    }

    protected void openPorts(MidiDevice device) {
        if (DEBUG) {
            Log.i(TAG, "---- openPorts()");
        }
        mIODevice.openPorts(device, new MidiMatchingReceiver());
    }

    protected void closePorts() {
        mIODevice.closePorts();
    }

    @Override
    public boolean hasTestPassed() {
        return mTestStatus == TESTSTATUS_PASSED;
    }

    // A little explanation here... It seems reasonable to send complete MIDI messages, i.e.
    // as a set of discrete packages.
    // However the looped-back data may not be delivered in message-size packets, so it makes more
    // sense to look at that as a stream of bytes.
    // So we build a set of messages to send, and then create the equivalent stream of bytes
    // from that to match against when received back in from the looped-back device.
    private void setupTestMessages() {
        if (DEBUG) {
            Log.i(TAG, "setupTestMessages()");
        }

        //TODO - Investigate using ByteArrayOutputStream for these data streams.

        //
        // Set up any set of messages you want
        // Except for the command IDs, the data values are purely arbitrary and meaningless
        // outside of being matched.
        // KeyDown
        byte[] noteOnBytes = new byte[]{ makeMIDICmd(MIDICMD_NOTEON, 0), 64, 21 };
        mTestMessages.add(new TestMessage(noteOnBytes));

        // KeyUp
        byte[] noteOffBytes = new byte[]{ makeMIDICmd(MIDICMD_NOTEOFF, 0), 73, 65 };
        mTestMessages.add(new TestMessage(noteOffBytes));

        // SysEx
        mTestMessages.add(new TestMessage(generateSysExMessage(32)));

        // Smaller SysEx
        mTestMessages.add(new TestMessage(generateSysExMessage(5)));

        // Larger SysEx
        mTestMessages.add(new TestMessage(generateSysExMessage(90)));

        // Two SysEx in a row
        byte[] sysEx1 = generateSysExMessage(7);
        byte[] sysEx2 = generateSysExMessage(23);
        mTestMessages.add(new TestMessage(concatByteArrays(sysEx1, sysEx2)));

        // Large SysEx
        mTestMessages.add(new TestMessage(generateSysExMessage(320)));

        //
        // Now build the stream to match against
        //
        mMatchStream.clear();
        for (int byteIndex = 0; byteIndex < mWarmUpMsg.length; byteIndex++) {
            mMatchStream.add(mWarmUpMsg[byteIndex]);
        }
        for (int msgIndex = 0; msgIndex < mTestMessages.size(); msgIndex++) {
            for (int byteIndex = 0;
                    byteIndex < mTestMessages.get(msgIndex).mMsgBytes.length; byteIndex++) {
                mMatchStream.add(mTestMessages.get(msgIndex).mMsgBytes[byteIndex]);
            }
        }

        mReceiveStreamPos = 0;

        if (DEBUG) {
            logByteArray("mMatchStream: ", mMatchStream, 0);
        }
    }

    private byte[] generateSysExMessage(int sysExSize) {
        byte[] sysExMsg = new byte[sysExSize];
        sysExMsg[0] = MIDICMD_SYSEX;
        for (int index = 1; index < sysExSize - 1; index++) {
            sysExMsg[index] = (byte) (index % 100);
        }
        sysExMsg[sysExSize - 1] = (byte) MIDICMD_EOSYSEX;
        return sysExMsg;
    }

    private byte[] concatByteArrays(byte[] array1, byte[] array2) {
        byte[] result = Arrays.copyOf(array1, array1.length + array2.length);
        System.arraycopy(array2, 0, result, array1.length, array2.length);
        return result;
    }

    /**
     * Compares the supplied bytes against the sent message stream at the current position
     * and advances the stream position.
     */
    private boolean matchStream(byte[] bytes, int offset, int count) {
        if (DEBUG) {
            Log.i(TAG, "---- matchStream() offset:" + offset + " count:" + count);
        }
        // a little bit of checking here...
        if (count < 0) {
            Log.e(TAG, "Negative Byte Count in MidiActivity::matchStream()");
            return false;
        }

        if (count > MESSAGE_MAX_BYTES) {
            Log.e(TAG, "Too Large Byte Count (" + count + ") in MidiActivity::matchStream()");
            return false;
        }

        boolean matches = true;

        for (int index = 0; index < count; index++) {
            // Avoid a buffer overrun. Still don't understand why it happens
            if (mReceiveStreamPos >= mMatchStream.size()) {
                // report an error here
                Log.d(TAG, "matchStream buffer overrun @" + index
                        + " of " + mMatchStream.size());
                // Dump the bufer here
                logByteArray("Expected: ", mMatchStream, 0);
                matches = false;
                break;  // bail
            }

            if (bytes[offset + index] == MIDICMD_SYSACTIVESENSING) {
                if (bytes[offset + index] == mMatchStream.get(mReceiveStreamPos)) {
                    Log.d(TAG, "matched active sensing message");
                    mReceiveStreamPos++;
                } else {
                    Log.d(TAG, "skipping active sensing message");
                }
            } else {
                // Check for "Warm Up" message
                if ((mReceiveStreamPos == 0)
                        && (bytes[offset + index] != makeMIDICmd(MIDICMD_CONTROL, 0))) {
                    // advance the match stream past the "warm-up" message
                    mReceiveStreamPos += mWarmUpMsg.length;
                    if (DEBUG) {
                        Log.d(TAG, "skipping warm-up message");
                    }
                }

                if (bytes[offset + index] != mMatchStream.get(mReceiveStreamPos)) {
                    matches = false;
                    if (DEBUG) {
                        int gotValue = bytes[offset + index] & 0x000000FF;
                        int expectedValue = mMatchStream.get(mReceiveStreamPos) & 0x000000FF;
                        Log.i(TAG, "---- mismatch @"
                                + index
                                + " [0x" + Integer.toHexString(gotValue)
                                + " : 0x" + Integer.toHexString(expectedValue)
                                + "]");
                    }
                    break;
                } else {
                    mReceiveStreamPos++;
                }
            }
        }

        if (DEBUG) {
            Log.i(TAG, "  returns:" + matches);
        }

        return matches;
    }

    private void portSend(MidiInputPort inputPort, byte[] bytes, int offset, int length,
                          boolean throttle) {
        if (DEBUG) {
            Log.i(TAG, "portSend() throttle:" + throttle);
        }
        try {
            if (throttle) {
                try {
                    for (int index = 0; index < length; index += THROTTLE_MAX_PACKET_SIZE) {
                        int packetSize = Math.min(length - index, THROTTLE_MAX_PACKET_SIZE);
                        inputPort.send(bytes, offset + index, packetSize);
                        Thread.sleep(THROTTLE_PERIOD_MS);
                    }
                } catch (InterruptedException ex) {
                    Log.i(TAG, "---- InterruptedException " + ex);
                }
            } else {
                inputPort.send(bytes, offset, length);
            }
        } catch (IOException ex) {
            Log.i(TAG, "---- IOException " + ex);
        }
    }

    /**
     * Writes out the list of MIDI messages to the output port.
     */
    private void sendMessages() {
        if (DEBUG) {
            Log.i(TAG, "---- sendMessages()...");
        }

        synchronized (mTestLock) {
            int totalSent = 0;
            if (mIODevice.mSendPort != null) {
                // Send a warm-up message...
                logByteArray("warm-up: ", mWarmUpMsg, 0, mWarmUpMsg.length);
                portSend(mIODevice.mSendPort, mWarmUpMsg, 0, mWarmUpMsg.length,
                        false /*throttleData*/);
                for (TestMessage msg : mTestMessages) {
                    if (DEBUG) {
                        logByteArray("send: ", msg.mMsgBytes, 0, msg.mMsgBytes.length);
                    }
                    portSend(mIODevice.mSendPort, msg.mMsgBytes, 0, msg.mMsgBytes.length,
                            false /*throttleData*/);
                    totalSent += msg.mMsgBytes.length;
                }
            }
            if (DEBUG) {
                Log.i(TAG, "---- totalSent:" + totalSent);
            }
        }
    }

    private class TestMessage {
        public byte[]   mMsgBytes;

        TestMessage(byte[] msgBytes) {
            mMsgBytes = msgBytes;
        }

        public boolean matches(byte[] msg, int offset, int count) {
            // Length
            if (DEBUG) {
                Log.i(TAG, "  count [" + count + " : " + mMsgBytes.length + "]");
            }
            if (count != mMsgBytes.length) {
                return false;
            }

            // Data
            for (int index = 0; index < count; index++) {
                if (DEBUG) {
                    Log.i(TAG, "  [" + msg[offset + index] + " : " + mMsgBytes[index] + "]");
                }
                if (msg[offset + index] != mMsgBytes[index]) {
                    return false;
                }
            }
            return true;
        }
    } /* class TestMessage */

    private static byte makeMIDICmd(int cmd, int channel) {
        return (byte) ((cmd << 4) | (channel & 0x0F));
    }

    /**
     * Logging Utility
     */
    public static void logByteArray(String prefix, byte[] value, int offset, int count) {
        StringBuilder builder = new StringBuilder(prefix);
        for (int i = 0; i < count; i++) {
            builder.append(String.format("0x%02X", value[offset + i]));
            if (i != value.length - 1) {
                builder.append(", ");
            }
        }
        Log.d(TAG, builder.toString());
    }

    /**
     * Logging Utility
     */
    public static void logByteArray(String prefix, ArrayList<Byte> value, int offset) {
        StringBuilder builder = new StringBuilder(prefix);
        for (int i = 0; i < value.size(); i++) {
            builder.append(String.format("0x%02X", value.get(offset + i)));
            if (i != value.size() - 1) {
                builder.append(", ");
            }
        }
        Log.d(TAG, builder.toString());
    }

    /**
     * Listens for MIDI device opens. Opens I/O ports and sends out the apriori
     * setup messages.
     */
    public class TestModuleOpenListener implements MidiManager.OnDeviceOpenedListener {
        @Override
        public void onDeviceOpened(MidiDevice device) {
            if (DEBUG) {
                Log.i(TAG, "---- onDeviceOpened()");
            }
            openPorts(device);
            sendMessages();
        }
    }

    /**
     * A MidiReceiver subclass whose job it is to monitor incoming messages
     * and match them against the stream sent by the test.
     */
    private class MidiMatchingReceiver extends MidiReceiver {
        private static final String TAG = "MidiMatchingReceiver";

        @Override
        public void onSend(byte[] msg, int offset, int count, long timestamp) throws IOException {
            if (DEBUG) {
                Log.i(TAG, "---- onSend(offset:" + offset
                        + " count:" + count + ") mTestRunning:" + mTestRunning);
                logByteArray("bytes-received: ", msg, offset, count);
            }
            synchronized (mTestLock) {
                if (!mTestRunning) {
                    return;
                }

                mTestMismatched = !matchStream(msg, offset, count);

                if (DEBUG) {
                    Log.i(TAG, "  mTestMismatched:" + mTestMismatched);
                    Log.i(TAG, "  mReceiveStreamPos:" + mReceiveStreamPos + " size:"
                            + mMatchStream.size());
                }
                if (mTestMismatched || mReceiveStreamPos == mMatchStream.size()) {
                    mTestRunning = false;

                    if (DEBUG) {
                        Log.i(TAG, "---- Test Complete");
                    }
                    // defer closing the ports to outside of this callback.
                    new Thread(new Runnable() {
                        public void run() {
                            closePorts();
                        }
                    }).start();

                    mTestStatus = mTestMismatched
                            ? TESTSTATUS_FAILED_MISMATCH : TESTSTATUS_PASSED;
                    enableTestButtonsAbstract(true);

                    updateTestStateUIAbstract();
                }
            }
        }
    } /* class MidiMatchingReceiver */
} /* class JavaMidiTestModule */
