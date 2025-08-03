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

package android.view.cts.input;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.Manifest;
import android.app.Instrumentation;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.view.cts.R;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;
import com.android.compatibility.common.util.WindowUtil;
import com.android.cts.input.InputJsonParser;
import com.android.cts.input.UinputDevice;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * CTS test case for generic.kl key layout mapping.
 * This test utilize uinput command line tool to create a test device, and configure the virtual
 * device to have all keys need to be tested. The JSON format input for device configuration
 * and EV_KEY injection will be created directly from this test for uinput command.
 * Keep res/raw/Generic.kl in sync with framework/base/data/keyboards/Generic.kl, this file
 * will be loaded and parsed in this test, looping through all key labels and the corresponding
 * EV_KEY code, injecting the KEY_UP and KEY_DOWN event to uinput, then verify the KeyEvent
 * delivered to test application view. Except meta control keys and special keys not delivered
 * to apps, all key codes in generic.kl will be verified.
 *
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public class InputDeviceKeyLayoutMapTest {
    private static final String TAG = "InputDeviceKeyLayoutMapTest";
    private static final String LABEL_PREFIX = "KEYCODE_";
    private static final int DEVICE_ID = 1;
    private static final int EV_SYN = 0;
    private static final int EV_KEY = 1;
    private static final int EV_KEY_DOWN = 1;
    private static final int EV_KEY_UP = 0;
    private static final int UI_SET_EVBIT = 100;
    private static final int UI_SET_KEYBIT = 101;
    private static final int GOOGLE_VENDOR_ID = 0x18d1;
    private static final int GOOGLE_VIRTUAL_KEYBOARD_ID = 0x001f;
    private static final int POLL_EVENT_TIMEOUT_SECONDS = 5;

    private static final Set<String> EXCLUDED_KEYS = new HashSet<>(Arrays.asList(
                // Meta control keys.
                "META_LEFT",
                "META_RIGHT",
                // KeyEvents not delivered to apps.
                "APP_SWITCH",
                "ASSIST",
                "BACK",
                "BRIGHTNESS_DOWN",
                "BRIGHTNESS_UP",
                "HOME",
                "KEYBOARD_BACKLIGHT_DOWN",
                "KEYBOARD_BACKLIGHT_TOGGLE",
                "KEYBOARD_BACKLIGHT_UP",
                "LANGUAGE_SWITCH",
                "MACRO_1",
                "MACRO_2",
                "MACRO_3",
                "MACRO_4",
                "MUTE",
                "POWER",
                "RECENT_APPS",
                "SEARCH",
                "SLEEP",
                "SOFT_SLEEP",
                "SYSRQ",
                "WAKEUP",
                "VOICE_ASSIST",
                // Keys that cause the test activity to lose focus
                "CALCULATOR",
                "CALENDAR",
                "CONTACTS",
                "ENVELOPE",
                "EXPLORER",
                "MUSIC"
                ));

    private Map<String, Integer> mKeyLayout;
    private Instrumentation mInstrumentation;
    private UinputDevice mUinputDevice;
    private InputJsonParser mParser;
    private WindowManager mWindowManager;
    private boolean mIsLeanback;
    private boolean mVolumeKeysHandledInWindowManager;

    private static native Map<String, Integer> nativeLoadKeyLayout(String genericKeyLayout);

    static {
        System.loadLibrary("ctsview_jni");
    }

    @Rule(order = 0)
    public AdoptShellPermissionsRule mAdoptShellPermissionsRule = new AdoptShellPermissionsRule(
            androidx.test.platform.app.InstrumentationRegistry
                    .getInstrumentation().getUiAutomation(),
            Manifest.permission.START_ACTIVITIES_FROM_SDK_SANDBOX);

    @Rule(order = 1)
    public ActivityTestRule<InputDeviceKeyLayoutMapTestActivity> mActivityRule =
            new ActivityTestRule<>(InputDeviceKeyLayoutMapTestActivity.class);

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        WindowUtil.waitForFocus(mActivityRule.getActivity());
        Context context = mInstrumentation.getTargetContext();
        mParser = new InputJsonParser(context);
        mWindowManager = context.getSystemService(WindowManager.class);
        mIsLeanback = context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK);
        mVolumeKeysHandledInWindowManager = context.getResources().getBoolean(
                Resources.getSystem().getIdentifier("config_handleVolumeKeysInWindowManager",
                        "bool", "android"));
        mKeyLayout = nativeLoadKeyLayout(mParser.readRegisterCommand(R.raw.Generic));
        mUinputDevice = new UinputDevice(mInstrumentation, DEVICE_ID, GOOGLE_VENDOR_ID,
                GOOGLE_VIRTUAL_KEYBOARD_ID, InputDevice.SOURCE_KEYBOARD,
                createDeviceRegisterCommand());
    }

    @After
    public void tearDown() {
        if (mUinputDevice != null) {
            mUinputDevice.close();
        }
    }

    /**
     * Get a KeyEvent from event queue or timeout.
     *
     * @return KeyEvent delivered to test activity, null if timeout.
     */
    private KeyEvent getKeyEvent() {
        return mActivityRule.getActivity().getKeyEvent(POLL_EVENT_TIMEOUT_SECONDS);
    }

    private void assertReceivedKeyEvent(int action, int keyCode) {
        KeyEvent receivedKeyEvent = getKeyEvent();
        assertNotNull("Did not receive " + KeyEvent.keyCodeToString(keyCode), receivedKeyEvent);
        assertEquals(action, receivedKeyEvent.getAction());
        assertEquals(keyCode, receivedKeyEvent.getKeyCode());
    }

    /**
     * Create the uinput device registration command, in JSON format of uinput commandline tool.
     * Refer to {@link framework/base/cmds/uinput/README.md}
     */
    private String createDeviceRegisterCommand() {
        JSONObject json = new JSONObject();
        JSONArray arrayConfigs =  new JSONArray();
        try {
            json.put("id", DEVICE_ID);
            json.put("type", "uinput");
            json.put("command", "register");
            json.put("name", "Virtual All Buttons Device (Test)");
            json.put("vid", GOOGLE_VENDOR_ID);
            json.put("pid", GOOGLE_VIRTUAL_KEYBOARD_ID);
            json.put("bus", "bluetooth");

            JSONObject jsonSetEvBit = new JSONObject();
            JSONArray arraySetEvBit =  new JSONArray();
            arraySetEvBit.put(EV_KEY);
            jsonSetEvBit.put("type", UI_SET_EVBIT);
            jsonSetEvBit.put("data", arraySetEvBit);
            arrayConfigs.put(jsonSetEvBit);

            // Configure device have all keys from key layout map.
            JSONArray arraySetKeyBit = new JSONArray();
            for (Map.Entry<String, Integer> entry : mKeyLayout.entrySet()) {
                arraySetKeyBit.put(entry.getValue());
            }
            JSONObject jsonSetKeyBit = new JSONObject();
            jsonSetKeyBit.put("type", UI_SET_KEYBIT);
            jsonSetKeyBit.put("data", arraySetKeyBit);
            arrayConfigs.put(jsonSetKeyBit);
            json.put("configuration", arrayConfigs);
        } catch (JSONException e) {
            throw new RuntimeException(
                    "Could not create JSON object");
        }

        return json.toString();
    }

    /**
     * Simulate pressing a key.
     * @param evKeyCode The key scan code
     */
    private void pressKey(int evKeyCode) {
        int[] evCodesDown = new int[] {
                EV_KEY, evKeyCode, EV_KEY_DOWN,
                EV_SYN, 0, 0};
        mUinputDevice.injectEvents(Arrays.toString(evCodesDown));

        int[] evCodesUp = new int[] {
                EV_KEY, evKeyCode, EV_KEY_UP,
                EV_SYN, 0, 0 };
        mUinputDevice.injectEvents(Arrays.toString(evCodesUp));
    }

    /**
     * Whether one key code is a volume key code.
     * @param keyCode The key code
     */
    private static boolean isVolumeKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_VOLUME_UP
                || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
                || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE;
    }

    /**
     * Whether one key code should be forwarded to apps.
     * @param keyCode The key code
     */
    private boolean isForwardedToApps(int keyCode) {
        if (mWindowManager.isGlobalKey(keyCode)) {
            return false;
        }
        if (isVolumeKey(keyCode) && (mIsLeanback || mVolumeKeysHandledInWindowManager)) {
            return false;
        }
        return true;
    }

    @Test
    public void testLayoutKeyEvents() {
        for (Map.Entry<String, Integer> entry : mKeyLayout.entrySet()) {
            if (EXCLUDED_KEYS.contains(entry.getKey())) {
                continue;
            }

            String label = LABEL_PREFIX + entry.getKey();
            final int evKey = entry.getValue();
            final int keyCode = KeyEvent.keyCodeFromString(label);

            if (!isForwardedToApps(keyCode)) {
                continue;
            }

            pressKey(evKey);
            assertReceivedKeyEvent(KeyEvent.ACTION_DOWN, keyCode);
            assertReceivedKeyEvent(KeyEvent.ACTION_UP, keyCode);
        }
    }

}
