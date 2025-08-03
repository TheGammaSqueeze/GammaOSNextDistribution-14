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

import android.app.Instrumentation;
import android.util.Log;

import androidx.annotation.GuardedBy;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a virtual UINPUT device registered through /dev/uinput.
 */
public final class UinputDevice extends VirtualInputDevice {
    private static final String TAG = "UinputDevice";
    // uinput executable expects "-" argument to read from stdin instead of a file
    private static final String UINPUT_COMMAND = "uinput -";

    @GuardedBy("mLock")
    private List<UinputResultData> mResults = new ArrayList<UinputResultData>();

    @Override
    protected String getShellCommand() {
        return UINPUT_COMMAND;
    }

    @Override
    protected void readResults() {
        try {
            mReader.beginObject();
            UinputResultData result = new UinputResultData();
            while (mReader.hasNext()) {
                String fieldName = mReader.nextName();
                if (fieldName.equals("reason")) {
                    result.reason = mReader.nextString();
                }
                if (fieldName.equals("id")) {
                    result.deviceId = Integer.decode(mReader.nextString());
                }
                if (fieldName.equals("status")) {
                    result.status = Integer.decode(mReader.nextString());
                }
            }
            mReader.endObject();
            addResult(result);
        } catch (IOException ex) {
            Log.w(TAG, "Exiting JSON Result reader. " + ex);
        }
    }

    public UinputDevice(Instrumentation instrumentation, int id, int vendorId, int productId,
            int sources, String registerCommand) {
        super(instrumentation, id, vendorId, productId, sources, registerCommand);
    }

    /**
     * Create Uinput device using the provided resourceId.
     */
    public static UinputDevice create(Instrumentation instrumentation, int resourceId,
            int sources) {
        final InputJsonParser parser = new InputJsonParser(instrumentation.getTargetContext());
        final int resourceDeviceId = parser.readDeviceId(resourceId);
        final String registerCommand = parser.readRegisterCommand(resourceId);
        return new UinputDevice(instrumentation, resourceDeviceId,
                parser.readVendorId(resourceId), parser.readProductId(resourceId),
                sources, registerCommand);
    }

    /**
     * Get uinput command return results as list of UinputResultData
     *
     * @return List of UinputResultData results
     */
    public synchronized List<UinputResultData> getResults(int deviceId, String reason)
            throws IOException {
        List<UinputResultData> results = new ArrayList<UinputResultData>();
        synchronized (mLock) {
            for (UinputResultData result : mResults) {
                if (deviceId == result.deviceId && reason.equals(result.reason)) {
                    results.add(result);
                }
            }
        }
        return results;
    }

    /**
     * Add uinput command returned UinputResultData result
     *
     * @param result UinputResultData result
     */
    public synchronized void addResult(UinputResultData result) {
        synchronized (mLock) {
            if (mId == result.deviceId && mResults != null) {
                mResults.add(result);
            }
        }
    }

    /**
     * Inject array of uinput events to the device.  The events array should follow the below
     * format:
     *
     * String evdevEvents = "[1, 10, 1, 0, 0, 0]"
     * The above string represents an event array of [EV_KEY, KEY_9, DOWN, EV_SYN, SYN_REPORT, 0]
     * Hex strings ("0x01") are not supported inside the incoming string.
     * The number of entries in the provided string has to be a multiple of 3.
     * @param evdevEvents The uinput events to be injected.  (a JSON-formatted array of numbers)
     */
    public void injectEvents(String evdevEvents) {
        JSONObject json = new JSONObject();
        try {
            json.put("command", "inject");
            json.put("id", mId);
            json.put("events", new JSONArray(evdevEvents));
        } catch (JSONException e) {
            throw new RuntimeException("Could not inject events: " + evdevEvents);
        }
        writeCommands(json.toString().getBytes());
    }

    /**
     * Inject a delay into the uinput process, guaranteeing that it will wait for at least the
     * specified time before executing any more commands.
     *
     * @param delayMs The amount of time to delay, in milliseconds.
     */
    public void injectDelay(int delayMs) {
        JSONObject json = new JSONObject();
        try {
            json.put("command", "delay");
            json.put("id", mId);
            json.put("duration", delayMs);
        } catch (JSONException e) {
            throw new RuntimeException("Could not inject delay of " + delayMs + "ms");
        }
        writeCommands(json.toString().getBytes());
    }

}
