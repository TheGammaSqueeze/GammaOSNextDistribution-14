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

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.hardware.lights.Light;
import android.util.ArrayMap;
import android.util.SparseArray;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


/**
 * Parse json resource file that contains the test commands for HidDevice
 *
 * For files containing reports and input events, each entry should be in the following format:
 * <code>
 * {"name": "test case name",
 *  "reports": reports,
 *  "events": input_events
 * }
 * </code>
 *
 * {@code reports} - an array of strings that contain hex arrays.
 * {@code input_events} - an array of dicts in the following format:
 * <code>
 * {"action": "down|move|up", "axes": {"axis_x": x, "axis_y": y}, "keycode": "button_a"}
 * </code>
 * {@code "axes"} should only be defined for motion events, and {@code "keycode"} for key events.
 * Timestamps will not be checked.

 * Example:
 * <code>
 * [{ "name": "press button A",
 *    "reports": ["report1",
 *                "report2",
 *                "report3"
 *               ],
 *    "events": [{"action": "down", "axes": {"axis_y": 0.5, "axis_x": 0.1}},
 *               {"action": "move", "axes": {"axis_y": 0.0, "axis_x": 0.0}}
 *              ]
 *  },
 *  ... more tests like that
 * ]
 * </code>
 */
public class InputJsonParser {
    private static final String TAG = "InputJsonParser";

    private Context mContext;

    public InputJsonParser(Context context) {
        mContext = context;
    }

    /**
     * Convenience function to create JSONArray from resource.
     * The resource specified should contain JSON array as the top-level structure.
     *
     * @param resourceId The resourceId that contains the json data (typically inside R.raw)
     */
    private JSONArray getJsonArrayFromResource(int resourceId) {
        String data = readRawResource(resourceId);
        try {
            return new JSONArray(data);
        } catch (JSONException e) {
            throw new RuntimeException(
                    "Could not parse resource " + resourceId + ", received: " + data,
                    e);
        }
    }

    /**
     * Convenience function to read in an entire file as a String.
     *
     * @param id resourceId of the file
     * @return contents of the raw resource file as a String
     */
    private String readRawResource(int id) {
        InputStream inputStream = mContext.getResources().openRawResource(id);
        try {
            return readFully(inputStream);
        } catch (IOException e) {
            throw new RuntimeException("Could not read resource id " + id);
        }
    }

    /**
     * Read register command from raw resource.
     *
     * @param resourceId the raw resource id that contains the command
     * @return the command to register device that can be passed to HidDevice constructor
     */
    public String readRegisterCommand(int resourceId) {
        return readRawResource(resourceId);
    }

    /**
     * Read entire input stream until no data remains.
     *
     * @param inputStream
     * @return content of the input stream
     * @throws IOException
     */
    private String readFully(InputStream inputStream) throws IOException {
        OutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int read = inputStream.read(buffer);
        while (read >= 0) {
            baos.write(buffer, 0, read);
            read = inputStream.read(buffer);
        }
        return baos.toString();
    }

    /**
     * Extract the device id from the raw resource file. This is needed in order to register
     * a HidDevice.
     *
     * @param resourceId resource file that contains the register command.
     * @return hid device id
     */
    public int readDeviceId(int resourceId) {
        try {
            JSONObject json = new JSONObject(readRawResource(resourceId));
            return json.getInt("id");
        } catch (JSONException e) {
            throw new RuntimeException("Could not read device id from resource " + resourceId);
        }
    }

    /**
     * Extract the Vendor id from the raw resource file.
     *
     * @param resourceId resource file that contains the register command.
     * @return device vendor id
     */
    public int readVendorId(int resourceId) {
        try {
            JSONObject json = new JSONObject(readRawResource(resourceId));
            return json.getInt("vid");
        } catch (JSONException e) {
            throw new RuntimeException("Could not read vendor id from resource " + resourceId);
        }
    }

    /**
     * Extract the input sources from the raw resource file.
     *
     * @param resourceId resource file that contains the register command.
     * @return device sources
     */
    public int readSources(int resourceId) {
        try {
            JSONObject json = new JSONObject(readRawResource(resourceId));
            return sourceFromString(json.optString("source"));
        } catch (JSONException e) {
            throw new RuntimeException("Could not read resource from resource " + resourceId);
        }
    }

    /**
     * Extract the Product id from the raw resource file.
     *
     * @param resourceId resource file that contains the register command.
     * @return device product id
     */
    public int readProductId(int resourceId) {
        try {
            JSONObject json = new JSONObject(readRawResource(resourceId));
            return json.getInt("pid");
        } catch (JSONException e) {
            throw new RuntimeException("Could not read prduct id from resource " + resourceId);
        }
    }

    private List<Long> getLongList(JSONArray array) {
        List<Long> data = new ArrayList<Long>();
        for (int i = 0; i < array.length(); i++) {
            try {
                data.add(array.getLong(i));
            } catch (JSONException e) {
                throw new RuntimeException("Could not read array index " + i);
            }
        }
        return data;
    }

    private List<Integer> getIntList(JSONArray array) {
        List<Integer> data = new ArrayList<Integer>();
        for (int i = 0; i < array.length(); i++) {
            try {
                data.add(array.getInt(i));
            } catch (JSONException e) {
                throw new RuntimeException("Could not read array index " + i);
            }
        }
        return data;
    }

    private InputEvent parseInputEvent(int testCaseNumber, int source, JSONObject entry) {
        try {
            InputEvent event;
            if (entry.has("keycode")) {
                event = parseKeyEvent(source, entry);
            } else if (entry.has("axes")) {
                event = parseMotionEvent(source, entry);
            } else {
                throw new RuntimeException(
                        "Input event is not specified correctly. Received: " + entry);
            }
            return event;
        } catch (JSONException e) {
            throw new RuntimeException("Could not process entry " + testCaseNumber + " : " + entry);
        }
    }

    /**
     * Read json resource, and return a {@code List} of HidTestData, which contains
     * the name of each test, along with the HID reports and the expected input events.
     */
    public List<HidTestData> getHidTestData(int resourceId) {
        JSONArray json = getJsonArrayFromResource(resourceId);
        List<HidTestData> tests = new ArrayList<HidTestData>();
        for (int testCaseNumber = 0; testCaseNumber < json.length(); testCaseNumber++) {
            HidTestData testData = new HidTestData();

            try {
                JSONObject testcaseEntry = json.getJSONObject(testCaseNumber);
                testData.name = testcaseEntry.getString("name");
                JSONArray reports = testcaseEntry.getJSONArray("reports");

                for (int i = 0; i < reports.length(); i++) {
                    String report = reports.getString(i);
                    testData.reports.add(report);
                }

                final int source = sourceFromString(testcaseEntry.optString("source"));
                JSONArray events = testcaseEntry.getJSONArray("events");
                for (int i = 0; i < events.length(); i++) {
                    testData.events.add(parseInputEvent(i, source, events.getJSONObject(i)));
                }
                tests.add(testData);
            } catch (JSONException e) {
                throw new RuntimeException("Could not process entry " + testCaseNumber);
            }
        }
        return tests;
    }

    /**
     * Read json resource, and return a {@code List} of HidVibratorTestData, which contains
     * the vibrator FF effect strength data index, and the hid output verification data.
     */
    public List<HidVibratorTestData> getHidVibratorTestData(int resourceId) {
        JSONArray json = getJsonArrayFromResource(resourceId);
        List<HidVibratorTestData> tests = new ArrayList<HidVibratorTestData>();
        for (int testCaseNumber = 0; testCaseNumber < json.length(); testCaseNumber++) {
            HidVibratorTestData testData = new HidVibratorTestData();
            try {
                JSONObject testcaseEntry = json.getJSONObject(testCaseNumber);
                testData.leftFfIndex = testcaseEntry.getInt("leftFfIndex");
                testData.rightFfIndex = testcaseEntry.getInt("rightFfIndex");

                JSONArray durationsArray = testcaseEntry.getJSONArray("durations");
                JSONArray amplitudesArray = testcaseEntry.getJSONArray("amplitudes");
                assertEquals(durationsArray.length(), amplitudesArray.length());
                testData.durations = new ArrayList<Long>();
                testData.amplitudes = new ArrayList<Integer>();
                for (int i = 0; i < durationsArray.length(); i++) {
                    testData.durations.add(durationsArray.getLong(i));
                    testData.amplitudes.add(amplitudesArray.getInt(i));
                }

                JSONArray outputArray = testcaseEntry.getJSONArray("output");
                testData.verifyMap = new ArrayMap<Integer, Integer>();
                for (int i = 0; i < outputArray.length(); i++) {
                    JSONObject item = outputArray.getJSONObject(i);
                    int index = item.getInt("index");
                    int data = item.getInt("data");
                    testData.verifyMap.put(index, data);
                }
                tests.add(testData);
            } catch (JSONException e) {
                throw new RuntimeException("Could not process entry " + testCaseNumber);
            }
        }
        return tests;
    }

    /**
     * Read json resource, and return a {@code List} of HidBatteryTestData, which contains
     * the name of each test, along with the HID reports and the expected batttery status.
     */
    public List<HidBatteryTestData> getHidBatteryTestData(int resourceId) {
        JSONArray json = getJsonArrayFromResource(resourceId);
        List<HidBatteryTestData> tests = new ArrayList<HidBatteryTestData>();
        for (int testCaseNumber = 0; testCaseNumber < json.length(); testCaseNumber++) {
            HidBatteryTestData testData = new HidBatteryTestData();
            try {
                JSONObject testcaseEntry = json.getJSONObject(testCaseNumber);
                testData.name = testcaseEntry.getString("name");
                JSONArray reports = testcaseEntry.getJSONArray("reports");

                for (int i = 0; i < reports.length(); i++) {
                    String report = reports.getString(i);
                    testData.reports.add(report);
                }

                JSONArray capacitiesArray = testcaseEntry.getJSONArray("capacities");
                testData.capacities = new float[capacitiesArray.length()];
                for (int i = 0; i < capacitiesArray.length(); i++) {
                    testData.capacities[i] = Float.valueOf(capacitiesArray.getString(i));
                }
                testData.status = testcaseEntry.getInt("status");
                tests.add(testData);
            } catch (JSONException e) {
                throw new RuntimeException("Could not process entry " + testCaseNumber + " " + e);
            }
        }
        return tests;
    }

    /**
     * Read json resource, and return a {@code List} of HidLightTestData, which contains
     * the light type and light state request, and the hid output verification data.
     */
    public List<HidLightTestData> getHidLightTestData(int resourceId) {
        JSONArray json = getJsonArrayFromResource(resourceId);
        List<HidLightTestData> tests = new ArrayList<HidLightTestData>();
        for (int testCaseNumber = 0; testCaseNumber < json.length(); testCaseNumber++) {
            HidLightTestData testData = new HidLightTestData();
            try {
                JSONObject testcaseEntry = json.getJSONObject(testCaseNumber);
                testData.lightType = lightTypeFromString(
                    testcaseEntry.getString("type"));
                testData.lightCapabilities = lightCapabilityFromString(
                        testcaseEntry.getString("capabilities"));
                testData.lightName = testcaseEntry.getString("name");
                testData.lightColor = testcaseEntry.getInt("color");
                testData.lightPlayerId = testcaseEntry.getInt("playerId");
                testData.hidEventType = uhidEventFromString(
                        testcaseEntry.getString("hidEventType"));
                testData.report = testcaseEntry.getString("report");

                JSONArray outputArray = testcaseEntry.getJSONArray("ledsHidOutput");
                testData.expectedHidData = new ArrayMap<Integer, Integer>();
                for (int i = 0; i < outputArray.length(); i++) {
                    JSONObject item = outputArray.getJSONObject(i);
                    int index = item.getInt("index");
                    int data = item.getInt("data");
                    testData.expectedHidData.put(index, data);
                }
                tests.add(testData);
            } catch (JSONException e) {
                throw new RuntimeException("Could not process entry " + testCaseNumber + " : " + e);
            }
        }
        return tests;
    }

    /**
     * Read json resource, and return a {@code List} of UinputVibratorTestData, which contains
     * the vibrator FF effect of durations and amplitudes.
     */
    public List<UinputVibratorTestData> getUinputVibratorTestData(int resourceId) {
        JSONArray json = getJsonArrayFromResource(resourceId);
        List<UinputVibratorTestData> tests = new ArrayList<UinputVibratorTestData>();
        for (int testCaseNumber = 0; testCaseNumber < json.length(); testCaseNumber++) {
            UinputVibratorTestData testData = new UinputVibratorTestData();
            try {
                JSONObject testcaseEntry = json.getJSONObject(testCaseNumber);
                JSONArray durationsArray = testcaseEntry.getJSONArray("durations");
                JSONArray amplitudesArray = testcaseEntry.getJSONArray("amplitudes");
                assertEquals("Duration array length not equal to amplitude array length",
                        durationsArray.length(), amplitudesArray.length());
                testData.durations = getLongList(durationsArray);
                testData.amplitudes = getIntList(amplitudesArray);
                tests.add(testData);
            } catch (JSONException e) {
                throw new RuntimeException("Could not process entry " + testCaseNumber);
            }
        }
        return tests;
    }

    /**
     * Read json resource, and return a {@code List} of UinputVibratorManagerTestData, which
     * contains the vibrator Ids and FF effect of durations and amplitudes.
     */
    public List<UinputVibratorManagerTestData> getUinputVibratorManagerTestData(int resourceId) {
        JSONArray json = getJsonArrayFromResource(resourceId);
        List<UinputVibratorManagerTestData> tests = new ArrayList<UinputVibratorManagerTestData>();
        for (int testCaseNumber = 0; testCaseNumber < json.length(); testCaseNumber++) {
            UinputVibratorManagerTestData testData = new UinputVibratorManagerTestData();
            try {
                JSONObject testcaseEntry = json.getJSONObject(testCaseNumber);
                JSONArray durationsArray = testcaseEntry.getJSONArray("durations");
                testData.durations = getLongList(durationsArray);
                testData.amplitudes = new SparseArray<>();
                JSONObject amplitudesObj = testcaseEntry.getJSONObject("amplitudes");
                for (int i = 0; i < amplitudesObj.names().length(); i++) {
                    String vibratorId = amplitudesObj.names().getString(i);
                    JSONArray amplitudesArray = amplitudesObj.getJSONArray(vibratorId);
                    testData.amplitudes.append(Integer.valueOf(vibratorId),
                            getIntList(amplitudesArray));
                    assertEquals("Duration array length not equal to amplitude array length",
                            durationsArray.length(), amplitudesArray.length());
                }
                tests.add(testData);
            } catch (JSONException e) {
                throw new RuntimeException("Could not process entry " + testCaseNumber);
            }
        }
        return tests;
    }

    /**
     * Read json resource, and return a {@code List} of UinputTestData, which contains
     * the name of each test, along with the uinput injections and the expected input events.
     */
    public List<UinputTestData> getUinputTestData(int resourceId) {
        JSONArray json = getJsonArrayFromResource(resourceId);
        List<UinputTestData> tests = new ArrayList<UinputTestData>();
        for (int testCaseNumber = 0; testCaseNumber < json.length(); testCaseNumber++) {
            UinputTestData testData = new UinputTestData();

            try {
                JSONObject testcaseEntry = json.getJSONObject(testCaseNumber);
                testData.name = testcaseEntry.getString("name");
                JSONArray reports = testcaseEntry.getJSONArray("injections");
                for (int i = 0; i < reports.length(); i++) {
                    String injections = reports.getString(i);
                    testData.evdevEvents.add(injections);
                }

                final int source = sourceFromString(testcaseEntry.optString("source"));

                JSONArray events = testcaseEntry.getJSONArray("events");
                for (int i = 0; i < events.length(); i++) {
                    testData.events.add(parseInputEvent(i, source, events.getJSONObject(i)));
                }
                tests.add(testData);
            } catch (JSONException e) {
                throw new RuntimeException("Could not process entry " + testCaseNumber);
            }
        }
        return tests;
    }

    private KeyEvent parseKeyEvent(int source, JSONObject entry) throws JSONException {
        int action = keyActionFromString(entry.getString("action"));
        int keyCode = KeyEvent.keyCodeFromString(entry.getString("keycode"));
        int metaState = metaStateFromString(entry.optString("metaState"));
        // We will only check select fields of the KeyEvent. Times are not checked.
        return new KeyEvent(/* downTime */ 0, /* eventTime */ 0, action, keyCode,
                /* repeat */ 0, metaState, /* deviceId */ 0, /* scanCode */ 0,
                /* flags */ 0, source);
    }

    private MotionEvent parseMotionEvent(int source, JSONObject entry) throws JSONException {
        JSONArray pointers = entry.optJSONArray("axes");
        int pointerCount = pointers == null ? 1 : pointers.length();

        MotionEvent.PointerProperties[] properties =
                new MotionEvent.PointerProperties[pointerCount];
        for (int i = 0; i < pointerCount; i++) {
            properties[i] = new MotionEvent.PointerProperties();
            properties[i].id = i;
            properties[i].toolType = MotionEvent.TOOL_TYPE_UNKNOWN;
        }

        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[pointerCount];
        for (int i = 0; i < pointerCount; i++) {
            coords[i] = new MotionEvent.PointerCoords();
        }

        int action = motionActionFromString(entry);

        int buttonState = 0;
        JSONArray buttons = entry.optJSONArray("buttonState");
        if (buttons != null) {
            for (int i = 0; i < buttons.length(); i++) {
                String buttonStr = buttons.getString(i);
                buttonState |= motionButtonFromString(buttonStr);
            }
        }

        // "axes" field should be an array if there are multiple pointers
        for (int i = 0; i < pointerCount; i++) {
            JSONObject axes;
            if (pointers == null) {
                axes = entry.getJSONObject("axes");
            } else {
                axes = pointers.getJSONObject(i);
            }
            Iterator<String> keys = axes.keys();
            while (keys.hasNext()) {
                String axis = keys.next();
                float value = (float) axes.getDouble(axis);
                coords[i].setAxisValue(MotionEvent.axisFromString(axis), value);
            }
        }

        // Times are not checked
        return MotionEvent.obtain(/* downTime */ 0, /* eventTime */ 0, action,
                pointerCount, properties, coords, 0, buttonState, 0f, 0f,
                0, 0, source, 0);
    }

    private static int keyActionFromString(String action) {
        switch (action.toUpperCase()) {
            case "DOWN":
                return KeyEvent.ACTION_DOWN;
            case "UP":
                return KeyEvent.ACTION_UP;
        }
        throw new RuntimeException("Unknown action specified: " + action);
    }

    private static int metaStateFromString(String metaStateString) {
        int metaState = 0;
        if (metaStateString.isEmpty()) {
            return metaState;
        }
        final String[] metaKeys = metaStateString.split("\\|");
        for (final String metaKeyString : metaKeys) {
            final String trimmedKeyString = metaKeyString.trim();
            switch (trimmedKeyString.toUpperCase()) {
                case "SHIFT_LEFT":
                    metaState |= KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_LEFT_ON;
                    break;
                case "SHIFT_RIGHT":
                    metaState |= KeyEvent.META_SHIFT_ON | KeyEvent.META_SHIFT_RIGHT_ON;
                    break;
                case "CTRL_LEFT":
                    metaState |= KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_LEFT_ON;
                    break;
                case "CTRL_RIGHT":
                    metaState |= KeyEvent.META_CTRL_ON | KeyEvent.META_CTRL_RIGHT_ON;
                    break;
                case "ALT_LEFT":
                    metaState |= KeyEvent.META_ALT_ON | KeyEvent.META_ALT_LEFT_ON;
                    break;
                case "ALT_RIGHT":
                    metaState |= KeyEvent.META_ALT_ON | KeyEvent.META_ALT_RIGHT_ON;
                    break;
                case "META_LEFT":
                    metaState |= KeyEvent.META_META_ON | KeyEvent.META_META_LEFT_ON;
                    break;
                case "META_RIGHT":
                    metaState |= KeyEvent.META_META_ON | KeyEvent.META_META_RIGHT_ON;
                    break;
                case "CAPS_LOCK":
                    metaState |= KeyEvent.META_CAPS_LOCK_ON;
                    break;
                case "NUM_LOCK":
                    metaState |= KeyEvent.META_NUM_LOCK_ON;
                    break;
                case "SCROLL_LOCK":
                    metaState |= KeyEvent.META_SCROLL_LOCK_ON;
                    break;
                default:
                    throw new RuntimeException("Unknown meta state chunk: " + trimmedKeyString
                            + " in meta state string: " + metaStateString);
            }
        }
        return metaState;
    }

    private static int motionActionFromString(JSONObject entry) {
        String action;
        int motionAction = 0;

        try {
            action = entry.getString("action").toUpperCase();
        } catch (JSONException e) {
            throw new RuntimeException("Action not specified. ");
        }

        switch (action) {
            case "DOWN":
                motionAction = MotionEvent.ACTION_DOWN;
                break;
            case "MOVE":
                motionAction = MotionEvent.ACTION_MOVE;
                break;
            case "UP":
                motionAction = MotionEvent.ACTION_UP;
                break;
            case "BUTTON_PRESS":
                motionAction = MotionEvent.ACTION_BUTTON_PRESS;
                break;
            case "BUTTON_RELEASE":
                motionAction = MotionEvent.ACTION_BUTTON_RELEASE;
                break;
            case "HOVER_ENTER":
                motionAction = MotionEvent.ACTION_HOVER_ENTER;
                break;
            case "HOVER_MOVE":
                motionAction = MotionEvent.ACTION_HOVER_MOVE;
                break;
            case "HOVER_EXIT":
                motionAction = MotionEvent.ACTION_HOVER_EXIT;
                break;
            case "POINTER_DOWN":
                motionAction = MotionEvent.ACTION_POINTER_DOWN;
                break;
            case "POINTER_UP":
                motionAction = MotionEvent.ACTION_POINTER_UP;
                break;
            case "CANCEL":
                motionAction = MotionEvent.ACTION_CANCEL;
                break;
            default:
                throw new RuntimeException("Unknown action specified: " + action);
        }
        int pointerId;
        try {
            if (motionAction == MotionEvent.ACTION_POINTER_UP
                    || motionAction == MotionEvent.ACTION_POINTER_DOWN) {
                pointerId = entry.getInt("pointerId");
            } else {
                pointerId = entry.optInt("pointerId", 0);
            }
        } catch (JSONException e) {
            throw new RuntimeException("PointerId not specified: " + action);
        }
        return motionAction | (pointerId << MotionEvent.ACTION_POINTER_INDEX_SHIFT);
    }

    private static int sourceFromString(String sourceString) {
        if (sourceString.isEmpty()) {
            return InputDevice.SOURCE_UNKNOWN;
        }
        int source = 0;
        final String[] sourceEntries = sourceString.split("\\|");
        for (final String sourceEntry : sourceEntries) {
            final String trimmedSourceEntry = sourceEntry.trim();
            switch (trimmedSourceEntry.toUpperCase()) {
                case "MOUSE":
                    source |= InputDevice.SOURCE_MOUSE;
                    break;
                case "MOUSE_RELATIVE":
                    source |= InputDevice.SOURCE_MOUSE_RELATIVE;
                    break;
                case "JOYSTICK":
                    source |= InputDevice.SOURCE_JOYSTICK;
                    break;
                case "KEYBOARD":
                    source |= InputDevice.SOURCE_KEYBOARD;
                    break;
                case "GAMEPAD":
                    source |= InputDevice.SOURCE_GAMEPAD;
                    break;
                case "DPAD":
                    source |= InputDevice.SOURCE_DPAD;
                    break;
                case "TOUCHPAD":
                    source |= InputDevice.SOURCE_TOUCHPAD;
                    break;
                case "SENSOR":
                    source |= InputDevice.SOURCE_SENSOR;
                    break;
                default:
                    throw new RuntimeException("Unknown source chunk: " + trimmedSourceEntry
                            + " in source string: " + sourceString);
            }
        }
        return source;
    }

    private static int motionButtonFromString(String button) {
        switch (button.toUpperCase()) {
            case "BACK":
                return MotionEvent.BUTTON_BACK;
            case "FORWARD":
                return MotionEvent.BUTTON_FORWARD;
            case "PRIMARY":
                return MotionEvent.BUTTON_PRIMARY;
            case "SECONDARY":
                return MotionEvent.BUTTON_SECONDARY;
            case "STYLUS_PRIMARY":
                return MotionEvent.BUTTON_STYLUS_PRIMARY;
            case "STYLUS_SECONDARY":
                return MotionEvent.BUTTON_STYLUS_SECONDARY;
            case "TERTIARY":
                return MotionEvent.BUTTON_TERTIARY;
        }
        throw new RuntimeException("Unknown button specified: " + button);
    }

    private static int lightTypeFromString(String typeString) {
        switch (typeString.toUpperCase()) {
            case "INPUT":
                return Light.LIGHT_TYPE_INPUT;
            case "PLAYER_ID":
                return Light.LIGHT_TYPE_PLAYER_ID;
            default:
                throw new RuntimeException("Unknown light type specified: " + typeString);
        }
    }

    private static int lightCapabilityFromString(String capString) {
        int capabilities = 0;
        final String[] entries = capString.split("\\|");
        for (final String entry : entries) {
            final String trimmedEntry = entry.trim();
            switch (trimmedEntry.toUpperCase()) {
                case "BRIGHTNESS":
                    capabilities |= Light.LIGHT_CAPABILITY_BRIGHTNESS;
                    break;
                case "RGB":
                    capabilities |= Light.LIGHT_CAPABILITY_RGB;
                    break;
                case "NONE":
                    break;
                default:
                    throw new RuntimeException("Unknown capability specified: " + capString);
            }
        }
        return capabilities;
    }

    // Return the enum uhid_event_type in kernel linux/uhid.h.
    private static byte uhidEventFromString(String eventString) {
        switch (eventString.toUpperCase()) {
            case "UHID_OUTPUT":
                return 6;
            case "UHID_GET_REPORT":
                return 9;
            case "UHID_SET_REPORT":
                return 13;
        }
        throw new RuntimeException("Unknown uhid event type specified: " + eventString);
    }
}
