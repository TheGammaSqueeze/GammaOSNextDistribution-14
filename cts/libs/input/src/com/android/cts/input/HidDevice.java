/*
 * Copyright (C) 2018 The Android Open Source Project
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
 * Represents a virtual HID device registered through /dev/uhid.
 */
public final class HidDevice extends VirtualInputDevice {
    private static final String TAG = "HidDevice";
    // hid executable expects "-" argument to read from stdin instead of a file
    private static final String HID_COMMAND = "hid -";

    @GuardedBy("mLock")
    private List<HidResultData> mResults = new ArrayList<HidResultData>();

    @Override
    protected String getShellCommand() {
        return HID_COMMAND;
    }

    @Override
    protected void readResults() {
        try {
            mReader.beginObject();
            HidResultData result = new HidResultData();
            while (mReader.hasNext()) {
                String fieldName = mReader.nextName();
                if (fieldName.equals("eventId")) {
                    result.eventId = Byte.decode(mReader.nextString());
                }
                if (fieldName.equals("deviceId")) {
                    result.deviceId = Integer.decode(mReader.nextString());
                }
                if (fieldName.equals("reportType")) {
                    result.reportType = Byte.decode(mReader.nextString());
                }
                if (fieldName.equals("reportData")) {
                    result.reportData = readData();
                }
            }
            mReader.endObject();
            addResult(result);
        } catch (IOException ex) {
            Log.w(TAG, "Exiting JSON Result reader. " + ex);
        }
    }

    public HidDevice(Instrumentation instrumentation, int id,
            int vendorId, int productId, int sources, String registerCommand) {
        super(instrumentation, id, vendorId, productId, sources, registerCommand);
    }

    /**
     * Get hid command return results as list of HidResultData
     *
     * @return List of HidResultData results
     */
    public synchronized List<HidResultData> getResults(int deviceId, byte eventId)
            throws IOException {
        List<HidResultData> results = new ArrayList<HidResultData>();
        synchronized (mLock) {
            for (HidResultData result : mResults) {
                if (deviceId == result.deviceId && eventId == result.eventId) {
                    results.add(result);
                }
            }
        }
        return results;
    }

    /**
     * Add hid command returned HidResultData result
     *
     * @param result HidResultData result
     */
    public synchronized void addResult(HidResultData result) {
        synchronized (mLock) {
            if (mId == result.deviceId && mResults != null) {
                mResults.add(result);
            }
        }
    }

    /**
     * Send a HID report to the device. The report should follow the report descriptor
     * that was specified during device registration.
     * An example report:
     * String report = "[0x01, 0x00, 0x00, 0x02]";
     *
     * @param report The report to send (a JSON-formatted array of hex)
     */
    public void sendHidReport(String report) {
        JSONObject json = new JSONObject();
        try {
            json.put("command", "report");
            json.put("id", mId);
            json.put("report", new JSONArray(report));
        } catch (JSONException e) {
            throw new RuntimeException("Could not process HID report: " + report);
        }
        writeCommands(json.toString().getBytes());
    }

}
