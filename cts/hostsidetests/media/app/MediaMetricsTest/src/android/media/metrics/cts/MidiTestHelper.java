/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.media.metrics.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.pm.PackageManager;
import android.media.midi.MidiDevice;
import android.media.midi.MidiDeviceInfo;
import android.media.midi.MidiInputPort;
import android.media.midi.MidiManager;
import android.media.midi.MidiOutputPort;
import android.media.midi.MidiReceiver;
import android.util.Log;

import com.android.midi.HostSideMidiEchoService;
import com.android.midi.MidiEchoTestService;

import java.io.IOException;
import java.util.ArrayList;

/**
 * Test MIDI using a virtual MIDI device that echos input to output.
 */
public class MidiTestHelper {
    private static final String TAG = "MidiEchoTest";
    private static final boolean DEBUG = false;

    private static final int TIMEOUT_OPEN_MSEC = 1000; // arbitrary
    private static final int TIMEOUT_STATUS_MSEC = 500; // arbitrary

    private Context mContext;

    MidiTestHelper(Context context) {
        mContext = context;
    }

    // Store device and ports related to the Echo service.
    static class MidiTestContext {
        public MidiDeviceInfo echoInfo;
        public MidiDevice echoDevice;
        public MidiInputPort echoInputPort;
        public MidiOutputPort echoOutputPort;
    }

    // Store complete MIDI message so it can be put in an array.
    static class MidiMessage {
        public final byte[] data;
        public final long timestamp;
        public final long timeReceived;

        MidiMessage(byte[] buffer, int offset, int length, long timestamp) {
            timeReceived = System.nanoTime();
            data = new byte[length];
            System.arraycopy(buffer, offset, data, 0, length);
            this.timestamp = timestamp;
        }
    }

    // Listens for an asynchronous device open and notifies waiting foreground
    // test.
    class MyTestOpenCallback implements MidiManager.OnDeviceOpenedListener {
        MidiDevice mDevice;

        @Override
        public synchronized void onDeviceOpened(MidiDevice device) {
            mDevice = device;
            notifyAll();
        }

        public synchronized MidiDevice waitForOpen(int msec)
                throws InterruptedException {
            long deadline = System.currentTimeMillis() + msec;
            long timeRemaining = msec;
            while (mDevice == null && timeRemaining > 0) {
                wait(timeRemaining);
                timeRemaining = deadline - System.currentTimeMillis();
            }
            return mDevice;
        }
    }

    // Store received messages in an array.
    class MyLoggingReceiver extends MidiReceiver {
        ArrayList<MidiMessage> mMessages = new ArrayList<MidiMessage>();
        int mByteCount;

        @Override
        public synchronized void onSend(byte[] data, int offset, int count,
                long timestamp) {
            mMessages.add(new MidiMessage(data, offset, count, timestamp));
            mByteCount += count;
            notifyAll();
        }

        public synchronized int getMessageCount() {
            return mMessages.size();
        }

        public synchronized int getByteCount() {
            return mByteCount;
        }

        public synchronized MidiMessage getMessage(int index) {
            return mMessages.get(index);
        }

        /**
         * Wait until count messages have arrived. This is a cumulative total.
         *
         * @param count
         * @param timeoutMs
         * @throws InterruptedException
         */
        public synchronized void waitForMessages(int count, int timeoutMs)
                throws InterruptedException {
            long endTimeMs = System.currentTimeMillis() + timeoutMs + 1;
            long timeToWait = timeoutMs + 1;
            while ((getMessageCount() < count)
                    && (timeToWait > 0)) {
                wait(timeToWait);
                timeToWait = endTimeMs - System.currentTimeMillis();
            }
        }

        /**
         * Wait until count bytes have arrived. This is a cumulative total.
         *
         * @param count
         * @param timeoutMs
         * @throws InterruptedException
         */
        public synchronized void waitForBytes(int count, int timeoutMs)
                throws InterruptedException {
            long endTimeMs = System.currentTimeMillis() + timeoutMs + 1;
            long timeToWait = timeoutMs + 1;
            while ((getByteCount() < count)
                    && (timeToWait > 0)) {
                wait(timeToWait);
                timeToWait = endTimeMs - System.currentTimeMillis();
            }
        }
    }

    protected MidiTestContext setUpEchoServer() throws Exception {
        if (DEBUG) {
            Log.i(TAG, "setUpEchoServer()");
        }
        MidiManager midiManager = mContext.getSystemService(MidiManager.class);

        MidiDeviceInfo echoInfo = HostSideMidiEchoService.findEchoDevice(mContext);

        // Open device.
        MyTestOpenCallback callback = new MyTestOpenCallback();
        midiManager.openDevice(echoInfo, callback, null);
        MidiDevice echoDevice = callback.waitForOpen(TIMEOUT_OPEN_MSEC);
        assertTrue("could not open "
                + HostSideMidiEchoService.getEchoServerName(), echoDevice != null);

        // Query echo service directly to see if it is getting status updates.
        MidiEchoTestService echoService = HostSideMidiEchoService.getInstance();
        assertEquals("virtual device status, input port before open", false,
                echoService.inputOpened);
        assertEquals("virtual device status, output port before open", 0,
                echoService.outputOpenCount);

        // Open input port.
        MidiInputPort echoInputPort = echoDevice.openInputPort(0);
        assertTrue("could not open input port", echoInputPort != null);
        assertEquals("input port number", 0, echoInputPort.getPortNumber());
        assertEquals("virtual device status, input port after open", true,
                echoService.inputOpened);
        assertEquals("virtual device status, output port before open", 0,
                echoService.outputOpenCount);

        // Open output port.
        MidiOutputPort echoOutputPort = echoDevice.openOutputPort(0);
        assertTrue("could not open output port", echoOutputPort != null);
        assertEquals("output port number", 0, echoOutputPort.getPortNumber());
        assertEquals("virtual device status, input port after open", true,
                echoService.inputOpened);
        assertEquals("virtual device status, output port after open", 1,
                echoService.outputOpenCount);

        MidiTestContext mc = new MidiTestContext();
        mc.echoInfo = echoInfo;
        mc.echoDevice = echoDevice;
        mc.echoInputPort = echoInputPort;
        mc.echoOutputPort = echoOutputPort;
        return mc;
    }

    /**
     * Close ports and check device status.
     *
     * @param mc
     */
    protected void tearDownEchoServer(MidiTestContext mc) throws IOException {
        // Query echo service directly to see if it is getting status updates.
        MidiEchoTestService echoService = HostSideMidiEchoService.getInstance();
        assertEquals("virtual device status, input port before close", true,
                echoService.inputOpened);
        assertEquals("virtual device status, output port before close", 1,
                echoService.outputOpenCount);

        // Close output port.
        mc.echoOutputPort.close();
        assertEquals("virtual device status, input port before close", true,
                echoService.inputOpened);
        assertEquals("virtual device status, output port after close", 0,
                echoService.outputOpenCount);
        mc.echoOutputPort.close();
        mc.echoOutputPort.close(); // should be safe to close twice

        // Close input port.
        mc.echoInputPort.close();
        assertEquals("virtual device status, input port after close", false,
                echoService.inputOpened);
        assertEquals("virtual device status, output port after close", 0,
                echoService.outputOpenCount);
        mc.echoInputPort.close();
        mc.echoInputPort.close(); // should be safe to close twice

        mc.echoDevice.close();
        mc.echoDevice.close(); // should be safe to close twice
    }

    // Send a variable sized message. The actual
    // size will be a multiple of 3 because it sends NoteOns.
    public void testEchoVariableMessage(int messageSize) throws Exception {
        PackageManager pm = mContext.getPackageManager();
        if (!pm.hasSystemFeature(PackageManager.FEATURE_MIDI)) {
            return; // Not supported so don't test it.
        }

        MidiTestContext mc = setUpEchoServer();

        MyLoggingReceiver receiver = new MyLoggingReceiver();
        mc.echoOutputPort.connect(receiver);

        // Send an integral number of notes
        int numNotes = messageSize / 3;
        int noteSize = numNotes * 3;
        final byte[] buffer = new byte[noteSize];
        int index = 0;
        for (int i = 0; i < numNotes; i++) {
            buffer[index++] = (byte) (0x90 + (i & 0x0F)); // NoteOn
            buffer[index++] = (byte) 0x47; // Pitch
            buffer[index++] = (byte) 0x52; // Velocity
        }
        long timestamp = 0x0123765489ABFEDCL;

        mc.echoInputPort.send(buffer, 0, 0, timestamp); // should be a NOOP
        mc.echoInputPort.send(buffer, 0, buffer.length, timestamp);
        mc.echoInputPort.send(buffer, 0, 0, timestamp); // should be a NOOP

        // Wait for message to pass quickly through echo service.
        // Message sent may have been split into multiple received messages.
        // So wait until we receive all the expected bytes.
        final int numBytesExpected = buffer.length;
        final int timeoutMs = 20;
        synchronized (receiver) {
            receiver.waitForBytes(numBytesExpected, timeoutMs);
        }

        // Check total size.
        final int numReceived = receiver.getMessageCount();
        int totalBytesReceived = 0;
        for (int i = 0; i < numReceived; i++) {
            MidiMessage message = receiver.getMessage(i);
            totalBytesReceived += message.data.length;
            assertEquals("timestamp in message", timestamp, message.timestamp);
        }
        assertEquals("byte count of messages", numBytesExpected,
                totalBytesReceived);

        // Make sure the payload was not corrupted.
        int sentIndex = 0;
        for (int i = 0; i < numReceived; i++) {
            MidiMessage message = receiver.getMessage(i);
            for (int k = 0; k < message.data.length; k++) {
                assertEquals("message byte[" + i + "]",
                        buffer[sentIndex++] & 0x0FF,
                        message.data[k] & 0x0FF);
            }
        }

        mc.echoOutputPort.disconnect(receiver);
        tearDownEchoServer(mc);
    }
}
