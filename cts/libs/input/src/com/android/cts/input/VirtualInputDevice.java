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

package com.android.cts.input;

import static android.os.FileUtils.closeQuietly;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.hardware.input.InputManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.ParcelFileDescriptor;
import android.util.JsonReader;
import android.util.JsonToken;
import android.util.Log;
import android.view.InputDevice;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Declares a virtual INPUT device registered through /dev/uinput or /dev/hid.
 */
public abstract class VirtualInputDevice implements
        InputManager.InputDeviceListener, AutoCloseable {
    private static final String TAG = "VirtualInputDevice";
    private InputStream mInputStream;
    private OutputStream mOutputStream;
    private Instrumentation mInstrumentation;
    private final Thread mResultThread;
    private final HandlerThread mHandlerThread;
    private final Handler mHandler;
    private final InputManager mInputManager;
    private volatile CountDownLatch mDeviceAddedSignal; // to wait for onInputDeviceAdded signal
    private volatile CountDownLatch mDeviceRemovedSignal; // to wait for onInputDeviceRemoved signal
    // Input device ID assigned by input manager
    private int mDeviceId = Integer.MIN_VALUE;
    private final int mVendorId;
    private final int mProductId;
    private final int mSources;
    // Virtual device ID from the json file
    protected final int mId;
    protected JsonReader mReader;
    protected final Object mLock = new Object();

    /**
     * To be implemented with device specific shell command to execute.
     */
    abstract String getShellCommand();

    /**
     * To be implemented with device specific result reading function.
     */
    abstract void readResults();

    public VirtualInputDevice(Instrumentation instrumentation, int id, int vendorId, int productId,
            int sources, String registerCommand) {
        mInstrumentation = instrumentation;
        mInputManager = mInstrumentation.getContext().getSystemService(InputManager.class);
        setupPipes();

        mId = id;
        mVendorId = vendorId;
        mProductId = productId;
        mSources = sources;
        mHandlerThread = new HandlerThread("InputDeviceHandlerThread");
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());

        mDeviceAddedSignal = new CountDownLatch(1);
        mDeviceRemovedSignal = new CountDownLatch(1);

        mResultThread = new Thread(() -> {
            try {
                while (mReader.peek() != JsonToken.END_DOCUMENT) {
                    readResults();
                }
            } catch (IOException ex) {
                Log.w(TAG, "Exiting JSON Result reader. " + ex);
            }
        });
        // Start result reader thread
        mResultThread.start();
        // Register input device listener
        mInputManager.registerInputDeviceListener(VirtualInputDevice.this, mHandler);
        // Register virtual input device
        registerInputDevice(registerCommand);
    }

    protected byte[] readData() throws IOException {
        ArrayList<Integer> data = new ArrayList<Integer>();
        try {
            mReader.beginArray();
            while (mReader.hasNext()) {
                data.add(Integer.decode(mReader.nextString()));
            }
            mReader.endArray();
        } catch (IllegalStateException | NumberFormatException e) {
            mReader.endArray();
            throw new IllegalStateException("Encountered malformed data.", e);
        }
        byte[] rawData = new byte[data.size()];
        for (int i = 0; i < data.size(); i++) {
            int d = data.get(i);
            if ((d & 0xFF) != d) {
                throw new IllegalStateException("Invalid data, all values must be byte-sized");
            }
            rawData[i] = (byte) d;
        }
        return rawData;
    }

    /**
     * Register an input device. May cause a failure if the device added notification
     * is not received within the timeout period
     *
     * @param registerCommand The full json command that specifies how to register this device
     */
    private void registerInputDevice(String registerCommand) {
        Log.i(TAG, "registerInputDevice: " + registerCommand);
        writeCommands(registerCommand.getBytes());
        try {
            // Wait for input device added callback.
            mDeviceAddedSignal.await(20L, TimeUnit.SECONDS);
            if (mDeviceAddedSignal.getCount() != 0) {
                throw new RuntimeException("Did not receive device added notification in time");
            }
        } catch (InterruptedException ex) {
            throw new RuntimeException(
                    "Unexpectedly interrupted while waiting for device added notification.");
        }
    }

    /**
     * Add a delay between processing events.
     *
     * @param milliSeconds The delay in milliseconds.
     */
    public void delay(int milliSeconds) {
        JSONObject json = new JSONObject();
        try {
            json.put("command", "delay");
            json.put("id", mId);
            json.put("duration", milliSeconds);
        } catch (JSONException e) {
            throw new RuntimeException(
                    "Could not create JSON object to delay " + milliSeconds + " milliseconds");
        }
        writeCommands(json.toString().getBytes());
    }

    /**
     * Close the device, which would cause the associated input device to unregister.
     */
    @Override
    public void close() {
        closeQuietly(mInputStream);
        closeQuietly(mOutputStream);
        // mResultThread should exit when stream is closed.
        try {
            // Wait for input device removed callback.
            mDeviceRemovedSignal.await(20L, TimeUnit.SECONDS);
            if (mDeviceRemovedSignal.getCount() != 0) {
                throw new RuntimeException("Did not receive device removed notification in time");
            }
        } catch (InterruptedException ex) {
            throw new RuntimeException(
                    "Unexpectedly interrupted while waiting for device removed notification.");
        }
        // Unregister input device listener
        mInstrumentation.runOnMainSync(() -> {
            mInputManager.unregisterInputDeviceListener(VirtualInputDevice.this);
        });
    }

    public int getDeviceId() {
        return mDeviceId;
    }

    public int getRegisterCommandDeviceId() {
        return mId;
    }

    public int getVendorId() {
        return mVendorId;
    }

    public int getProductId() {
        return mProductId;
    }

    private void setupPipes() {
        UiAutomation ui = mInstrumentation.getUiAutomation();
        ParcelFileDescriptor[] pipes = ui.executeShellCommandRw(getShellCommand());

        mInputStream = new ParcelFileDescriptor.AutoCloseInputStream(pipes[0]);
        mOutputStream = new ParcelFileDescriptor.AutoCloseOutputStream(pipes[1]);
        try {
            mReader = new JsonReader(new InputStreamReader(mInputStream, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
        mReader.setLenient(true);
    }

    protected void writeCommands(byte[] bytes) {
        try {
            mOutputStream.write(bytes);
            mOutputStream.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void updateInputDevice(int deviceId) {
        InputDevice device = mInputManager.getInputDevice(deviceId);
        if (device == null) {
            return;
        }
        // Check if the device is what we expected
        if (device.getVendorId() == mVendorId && device.getProductId() == mProductId) {
            if ((device.getSources() & mSources) == mSources) {
                mDeviceId = device.getId();
                mDeviceAddedSignal.countDown();
            } else {
                Log.i(TAG, "Mismatching sources for " + device);
            }
        } else {
            Log.w(TAG, "Unexpected input device: " + device);
        }
    }

    // InputManager.InputDeviceListener functions
    @Override
    public void onInputDeviceAdded(int deviceId) {
        // Check the new added input device
        updateInputDevice(deviceId);
    }

    @Override
    public void onInputDeviceChanged(int deviceId) {
        // InputDevice may be updated with new input sources added
        updateInputDevice(deviceId);
    }

    @Override
    public void onInputDeviceRemoved(int deviceId) {
        if (deviceId == mDeviceId) {
            mDeviceRemovedSignal.countDown();
        }
    }
}
