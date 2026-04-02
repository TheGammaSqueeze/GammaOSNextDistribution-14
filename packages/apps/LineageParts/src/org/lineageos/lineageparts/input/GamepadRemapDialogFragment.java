/*
 * GammaOS Gamepad Remap Dialog
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.lineageparts.input;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemProperties;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import org.lineageos.lineageparts.R;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GamepadRemapDialogFragment extends DialogFragment {

    private static final String TAG = "GamepadRemap";
    private static final String PROP_REMAP_BTN = "persist.gammaos.gamepad.remap_btn";
    private static final String PROP_REMAP_AXIS = "persist.gammaos.gamepad.remap_axis";
    private static final String PROP_CONFIG_VERSION = "persist.gammaos.gamepad.config_version";
    private static final String ARG_AXIS_MODE = "axis_mode";

    private boolean mAxisMode = false;

    // State for temporary remap disable during button source capture
    private boolean mRemapsDisabled = false;

    // Standard gamepad buttons: display name -> evdev code
    private static final Map<String, Integer> STANDARD_TARGETS = new LinkedHashMap<>();
    static {
        STANDARD_TARGETS.put("A", 0x130);        // BTN_A
        STANDARD_TARGETS.put("B", 0x131);        // BTN_B
        STANDARD_TARGETS.put("X", 0x133);        // BTN_X
        STANDARD_TARGETS.put("Y", 0x134);        // BTN_Y
        STANDARD_TARGETS.put("LB", 0x136);       // BTN_TL
        STANDARD_TARGETS.put("RB", 0x137);       // BTN_TR
        STANDARD_TARGETS.put("LT", 0x138);       // BTN_TL2
        STANDARD_TARGETS.put("RT", 0x139);       // BTN_TR2
        STANDARD_TARGETS.put("Select", 0x13a);   // BTN_SELECT
        STANDARD_TARGETS.put("Start", 0x13b);    // BTN_START
        STANDARD_TARGETS.put("Guide", 0x13c);    // BTN_MODE
        STANDARD_TARGETS.put("L3", 0x13d);       // BTN_THUMBL
        STANDARD_TARGETS.put("R3", 0x13e);       // BTN_THUMBR
    }

    // Standard gamepad axes: display name -> evdev code
    private static final Map<String, Integer> STANDARD_AXIS_TARGETS = new LinkedHashMap<>();
    static {
        STANDARD_AXIS_TARGETS.put("Left Stick X", 0x00);   // ABS_X
        STANDARD_AXIS_TARGETS.put("Left Stick Y", 0x01);   // ABS_Y
        STANDARD_AXIS_TARGETS.put("Left Trigger", 0x02);   // ABS_Z
        STANDARD_AXIS_TARGETS.put("Right Stick X", 0x03);  // ABS_RX
        STANDARD_AXIS_TARGETS.put("Right Stick Y", 0x04);  // ABS_RY
        STANDARD_AXIS_TARGETS.put("Right Trigger", 0x05);  // ABS_RZ
        STANDARD_AXIS_TARGETS.put("DPAD X", 0x10);         // ABS_HAT0X
        STANDARD_AXIS_TARGETS.put("DPAD Y", 0x11);         // ABS_HAT0Y
    }

    // Android keycode -> evdev code mapping (standard gamepad buttons)
    private static final Map<Integer, Integer> KEYCODE_TO_EVDEV = new LinkedHashMap<>();
    static {
        KEYCODE_TO_EVDEV.put(KeyEvent.KEYCODE_BUTTON_A, 0x130);
        KEYCODE_TO_EVDEV.put(KeyEvent.KEYCODE_BUTTON_B, 0x131);
        KEYCODE_TO_EVDEV.put(KeyEvent.KEYCODE_BUTTON_X, 0x133);
        KEYCODE_TO_EVDEV.put(KeyEvent.KEYCODE_BUTTON_Y, 0x134);
        KEYCODE_TO_EVDEV.put(KeyEvent.KEYCODE_BUTTON_L1, 0x136);
        KEYCODE_TO_EVDEV.put(KeyEvent.KEYCODE_BUTTON_R1, 0x137);
        KEYCODE_TO_EVDEV.put(KeyEvent.KEYCODE_BUTTON_L2, 0x138);
        KEYCODE_TO_EVDEV.put(KeyEvent.KEYCODE_BUTTON_R2, 0x139);
        KEYCODE_TO_EVDEV.put(KeyEvent.KEYCODE_BUTTON_SELECT, 0x13a);
        KEYCODE_TO_EVDEV.put(KeyEvent.KEYCODE_BUTTON_START, 0x13b);
        KEYCODE_TO_EVDEV.put(KeyEvent.KEYCODE_BUTTON_MODE, 0x13c);
        KEYCODE_TO_EVDEV.put(KeyEvent.KEYCODE_BUTTON_THUMBL, 0x13d);
        KEYCODE_TO_EVDEV.put(KeyEvent.KEYCODE_BUTTON_THUMBR, 0x13e);
    }

    // Evdev code -> display name for all known button/key codes.
    // Comprehensive list matching GammaPad reference (key_name_map.inc).
    private static final Map<Integer, String> EVDEV_NAMES = new LinkedHashMap<>();
    static {
        // === Gamepad buttons (0x130-0x13e) ===
        EVDEV_NAMES.put(0x130, "BTN_A");
        EVDEV_NAMES.put(0x131, "BTN_B");
        EVDEV_NAMES.put(0x132, "BTN_C");
        EVDEV_NAMES.put(0x133, "BTN_X");
        EVDEV_NAMES.put(0x134, "BTN_Y");
        EVDEV_NAMES.put(0x135, "BTN_Z");
        EVDEV_NAMES.put(0x136, "BTN_TL");
        EVDEV_NAMES.put(0x137, "BTN_TR");
        EVDEV_NAMES.put(0x138, "BTN_TL2");
        EVDEV_NAMES.put(0x139, "BTN_TR2");
        EVDEV_NAMES.put(0x13a, "BTN_SELECT");
        EVDEV_NAMES.put(0x13b, "BTN_START");
        EVDEV_NAMES.put(0x13c, "BTN_MODE");
        EVDEV_NAMES.put(0x13d, "BTN_THUMBL");
        EVDEV_NAMES.put(0x13e, "BTN_THUMBR");

        // === DPAD buttons (0x220-0x223) ===
        EVDEV_NAMES.put(0x220, "BTN_DPAD_UP");
        EVDEV_NAMES.put(0x221, "BTN_DPAD_DOWN");
        EVDEV_NAMES.put(0x222, "BTN_DPAD_LEFT");
        EVDEV_NAMES.put(0x223, "BTN_DPAD_RIGHT");

        // === Misc buttons (0x100-0x109) ===
        EVDEV_NAMES.put(0x100, "BTN_0");
        EVDEV_NAMES.put(0x101, "BTN_1");
        EVDEV_NAMES.put(0x102, "BTN_2");
        EVDEV_NAMES.put(0x103, "BTN_3");
        EVDEV_NAMES.put(0x104, "BTN_4");
        EVDEV_NAMES.put(0x105, "BTN_5");
        EVDEV_NAMES.put(0x106, "BTN_6");
        EVDEV_NAMES.put(0x107, "BTN_7");
        EVDEV_NAMES.put(0x108, "BTN_8");
        EVDEV_NAMES.put(0x109, "BTN_9");

        // === Mouse buttons (0x110-0x117) ===
        EVDEV_NAMES.put(0x110, "BTN_LEFT");
        EVDEV_NAMES.put(0x111, "BTN_RIGHT");
        EVDEV_NAMES.put(0x112, "BTN_MIDDLE");
        EVDEV_NAMES.put(0x113, "BTN_SIDE");
        EVDEV_NAMES.put(0x114, "BTN_EXTRA");
        EVDEV_NAMES.put(0x115, "BTN_FORWARD");
        EVDEV_NAMES.put(0x116, "BTN_BACK");
        EVDEV_NAMES.put(0x117, "BTN_TASK");

        // === Joystick buttons (0x120-0x12f) ===
        EVDEV_NAMES.put(0x120, "BTN_TRIGGER");
        EVDEV_NAMES.put(0x121, "BTN_THUMB");
        EVDEV_NAMES.put(0x122, "BTN_THUMB2");
        EVDEV_NAMES.put(0x123, "BTN_TOP");
        EVDEV_NAMES.put(0x124, "BTN_TOP2");
        EVDEV_NAMES.put(0x125, "BTN_PINKIE");
        EVDEV_NAMES.put(0x126, "BTN_BASE");
        EVDEV_NAMES.put(0x127, "BTN_BASE2");
        EVDEV_NAMES.put(0x128, "BTN_BASE3");
        EVDEV_NAMES.put(0x129, "BTN_BASE4");
        EVDEV_NAMES.put(0x12a, "BTN_BASE5");
        EVDEV_NAMES.put(0x12b, "BTN_BASE6");
        EVDEV_NAMES.put(0x12f, "BTN_DEAD");

        // === Wheel/gear buttons ===
        EVDEV_NAMES.put(0x150, "BTN_WHEEL");
        EVDEV_NAMES.put(0x150, "BTN_GEAR_DOWN");
        EVDEV_NAMES.put(0x151, "BTN_GEAR_UP");

        // === Keyboard keys ===
        EVDEV_NAMES.put(1, "KEY_ESC");
        EVDEV_NAMES.put(2, "KEY_1");
        EVDEV_NAMES.put(3, "KEY_2");
        EVDEV_NAMES.put(4, "KEY_3");
        EVDEV_NAMES.put(5, "KEY_4");
        EVDEV_NAMES.put(6, "KEY_5");
        EVDEV_NAMES.put(7, "KEY_6");
        EVDEV_NAMES.put(8, "KEY_7");
        EVDEV_NAMES.put(9, "KEY_8");
        EVDEV_NAMES.put(10, "KEY_9");
        EVDEV_NAMES.put(11, "KEY_0");
        EVDEV_NAMES.put(12, "KEY_MINUS");
        EVDEV_NAMES.put(13, "KEY_EQUAL");
        EVDEV_NAMES.put(14, "KEY_BACKSPACE");
        EVDEV_NAMES.put(15, "KEY_TAB");
        EVDEV_NAMES.put(16, "KEY_Q");
        EVDEV_NAMES.put(17, "KEY_W");
        EVDEV_NAMES.put(18, "KEY_E");
        EVDEV_NAMES.put(19, "KEY_R");
        EVDEV_NAMES.put(20, "KEY_T");
        EVDEV_NAMES.put(21, "KEY_Y");
        EVDEV_NAMES.put(22, "KEY_U");
        EVDEV_NAMES.put(23, "KEY_I");
        EVDEV_NAMES.put(24, "KEY_O");
        EVDEV_NAMES.put(25, "KEY_P");
        EVDEV_NAMES.put(28, "KEY_ENTER");
        EVDEV_NAMES.put(29, "KEY_LEFTCTRL");
        EVDEV_NAMES.put(30, "KEY_A");
        EVDEV_NAMES.put(31, "KEY_S");
        EVDEV_NAMES.put(32, "KEY_D");
        EVDEV_NAMES.put(33, "KEY_F");
        EVDEV_NAMES.put(34, "KEY_G");
        EVDEV_NAMES.put(35, "KEY_H");
        EVDEV_NAMES.put(36, "KEY_J");
        EVDEV_NAMES.put(37, "KEY_K");
        EVDEV_NAMES.put(38, "KEY_L");
        EVDEV_NAMES.put(42, "KEY_LEFTSHIFT");
        EVDEV_NAMES.put(44, "KEY_Z");
        EVDEV_NAMES.put(45, "KEY_X");
        EVDEV_NAMES.put(46, "KEY_C");
        EVDEV_NAMES.put(47, "KEY_V");
        EVDEV_NAMES.put(48, "KEY_B");
        EVDEV_NAMES.put(49, "KEY_N");
        EVDEV_NAMES.put(50, "KEY_M");
        EVDEV_NAMES.put(54, "KEY_RIGHTSHIFT");
        EVDEV_NAMES.put(56, "KEY_LEFTALT");
        EVDEV_NAMES.put(57, "KEY_SPACE");
        EVDEV_NAMES.put(58, "KEY_CAPSLOCK");
        EVDEV_NAMES.put(59, "KEY_F1");
        EVDEV_NAMES.put(60, "KEY_F2");
        EVDEV_NAMES.put(61, "KEY_F3");
        EVDEV_NAMES.put(62, "KEY_F4");
        EVDEV_NAMES.put(63, "KEY_F5");
        EVDEV_NAMES.put(64, "KEY_F6");
        EVDEV_NAMES.put(65, "KEY_F7");
        EVDEV_NAMES.put(66, "KEY_F8");
        EVDEV_NAMES.put(67, "KEY_F9");
        EVDEV_NAMES.put(68, "KEY_F10");
        EVDEV_NAMES.put(87, "KEY_F11");
        EVDEV_NAMES.put(88, "KEY_F12");
        EVDEV_NAMES.put(96, "KEY_KPENTER");
        EVDEV_NAMES.put(97, "KEY_RIGHTCTRL");
        EVDEV_NAMES.put(100, "KEY_RIGHTALT");
        EVDEV_NAMES.put(102, "KEY_HOME");
        EVDEV_NAMES.put(103, "KEY_UP");
        EVDEV_NAMES.put(104, "KEY_PAGEUP");
        EVDEV_NAMES.put(105, "KEY_LEFT");
        EVDEV_NAMES.put(106, "KEY_RIGHT");
        EVDEV_NAMES.put(107, "KEY_END");
        EVDEV_NAMES.put(108, "KEY_DOWN");
        EVDEV_NAMES.put(109, "KEY_PAGEDOWN");
        EVDEV_NAMES.put(110, "KEY_INSERT");
        EVDEV_NAMES.put(111, "KEY_DELETE");
        EVDEV_NAMES.put(113, "KEY_MUTE");
        EVDEV_NAMES.put(114, "KEY_VOLUMEDOWN");
        EVDEV_NAMES.put(115, "KEY_VOLUMEUP");
        EVDEV_NAMES.put(116, "KEY_POWER");
        EVDEV_NAMES.put(119, "KEY_PAUSE");
        EVDEV_NAMES.put(125, "KEY_LEFTMETA");
        EVDEV_NAMES.put(126, "KEY_RIGHTMETA");
        EVDEV_NAMES.put(127, "KEY_COMPOSE");
        EVDEV_NAMES.put(128, "KEY_STOP");
        EVDEV_NAMES.put(139, "KEY_MENU");
        EVDEV_NAMES.put(140, "KEY_CALC");
        EVDEV_NAMES.put(142, "KEY_SLEEP");
        EVDEV_NAMES.put(143, "KEY_WAKEUP");
        EVDEV_NAMES.put(150, "KEY_WWW");
        EVDEV_NAMES.put(155, "KEY_MAIL");
        EVDEV_NAMES.put(156, "KEY_BOOKMARKS");
        EVDEV_NAMES.put(158, "KEY_BACK");
        EVDEV_NAMES.put(159, "KEY_FORWARD");
        EVDEV_NAMES.put(163, "KEY_NEXTSONG");
        EVDEV_NAMES.put(164, "KEY_PLAYPAUSE");
        EVDEV_NAMES.put(165, "KEY_PREVIOUSSONG");
        EVDEV_NAMES.put(166, "KEY_STOPCD");
        EVDEV_NAMES.put(167, "KEY_RECORD");
        EVDEV_NAMES.put(168, "KEY_REWIND");
        EVDEV_NAMES.put(172, "KEY_HOMEPAGE");
        EVDEV_NAMES.put(176, "KEY_REFRESH");
        EVDEV_NAMES.put(177, "KEY_EXIT");
        EVDEV_NAMES.put(183, "KEY_F13");
        EVDEV_NAMES.put(184, "KEY_F14");
        EVDEV_NAMES.put(185, "KEY_F15");
        EVDEV_NAMES.put(186, "KEY_F16");
        EVDEV_NAMES.put(187, "KEY_F17");
        EVDEV_NAMES.put(188, "KEY_F18");
        EVDEV_NAMES.put(189, "KEY_F19");
        EVDEV_NAMES.put(190, "KEY_F20");
        EVDEV_NAMES.put(191, "KEY_F21");
        EVDEV_NAMES.put(192, "KEY_F22");
        EVDEV_NAMES.put(193, "KEY_F23");
        EVDEV_NAMES.put(194, "KEY_F24");
        EVDEV_NAMES.put(204, "KEY_ALL_APPLICATIONS");
        EVDEV_NAMES.put(207, "KEY_PLAY");
        EVDEV_NAMES.put(208, "KEY_FASTFORWARD");
        EVDEV_NAMES.put(210, "KEY_PRINT");
        EVDEV_NAMES.put(212, "KEY_CAMERA");
        EVDEV_NAMES.put(217, "KEY_SEARCH");
        EVDEV_NAMES.put(223, "KEY_BRIGHTNESSDOWN");
        EVDEV_NAMES.put(224, "KEY_BRIGHTNESSUP");
        EVDEV_NAMES.put(226, "KEY_MEDIA");
        EVDEV_NAMES.put(240, "KEY_UNKNOWN");

        // === Media/TV keys ===
        EVDEV_NAMES.put(0x160, "KEY_OK");
        EVDEV_NAMES.put(0x161, "KEY_SELECT");
        EVDEV_NAMES.put(0x162, "KEY_GOTO");
        EVDEV_NAMES.put(0x163, "KEY_CLEAR");
        EVDEV_NAMES.put(0x164, "KEY_POWER2");
        EVDEV_NAMES.put(0x166, "KEY_INFO");
        EVDEV_NAMES.put(0x16a, "KEY_PROGRAM");
        EVDEV_NAMES.put(0x16b, "KEY_CHANNEL");
        EVDEV_NAMES.put(0x16c, "KEY_FAVORITES");
        EVDEV_NAMES.put(0x170, "KEY_LANGUAGE");
        EVDEV_NAMES.put(0x174, "KEY_FULL_SCREEN");
        EVDEV_NAMES.put(0x175, "KEY_ZOOM");
        EVDEV_NAMES.put(0x176, "KEY_MODE");
        EVDEV_NAMES.put(0x179, "KEY_TV");
        EVDEV_NAMES.put(0x181, "KEY_RED");
        EVDEV_NAMES.put(0x182, "KEY_GREEN");
        EVDEV_NAMES.put(0x183, "KEY_YELLOW");
        EVDEV_NAMES.put(0x184, "KEY_BLUE");
        EVDEV_NAMES.put(0x192, "KEY_CHANNELUP");
        EVDEV_NAMES.put(0x193, "KEY_CHANNELDOWN");
        EVDEV_NAMES.put(0x197, "KEY_SHUFFLE");
        EVDEV_NAMES.put(0x1a2, "KEY_GAMES");
        EVDEV_NAMES.put(0x1a3, "KEY_ZOOMIN");
        EVDEV_NAMES.put(0x1a4, "KEY_ZOOMOUT");
        EVDEV_NAMES.put(0x1b7, "KEY_VOICECOMMAND");
        EVDEV_NAMES.put(0x1b8, "KEY_ASSISTANT");

        // === Fn keys ===
        EVDEV_NAMES.put(0x1d0, "KEY_FN");
        EVDEV_NAMES.put(0x1d1, "KEY_FN_ESC");
        EVDEV_NAMES.put(0x1d2, "KEY_FN_F1");
        EVDEV_NAMES.put(0x1d3, "KEY_FN_F2");
        EVDEV_NAMES.put(0x1d4, "KEY_FN_F3");
        EVDEV_NAMES.put(0x1d5, "KEY_FN_F4");
        EVDEV_NAMES.put(0x1d6, "KEY_FN_F5");
        EVDEV_NAMES.put(0x1d7, "KEY_FN_F6");
        EVDEV_NAMES.put(0x1d8, "KEY_FN_F7");
        EVDEV_NAMES.put(0x1d9, "KEY_FN_F8");
        EVDEV_NAMES.put(0x1da, "KEY_FN_F9");
        EVDEV_NAMES.put(0x1db, "KEY_FN_F10");
        EVDEV_NAMES.put(0x1dc, "KEY_FN_F11");
        EVDEV_NAMES.put(0x1dd, "KEY_FN_F12");

        // === Trigger happy buttons (0x2c0-0x2e7) ===
        for (int i = 0; i <= 39; i++) {
            EVDEV_NAMES.put(0x2c0 + i, "BTN_TRIGGER_HAPPY" + (i + 1));
        }

        // === Macro keys ===
        EVDEV_NAMES.put(0x290, "KEY_MACRO1");
        EVDEV_NAMES.put(0x291, "KEY_MACRO2");
        EVDEV_NAMES.put(0x292, "KEY_MACRO3");
        EVDEV_NAMES.put(0x293, "KEY_MACRO4");
        EVDEV_NAMES.put(0x294, "KEY_MACRO5");
        EVDEV_NAMES.put(0x295, "KEY_MACRO6");
        EVDEV_NAMES.put(0x296, "KEY_MACRO7");
        EVDEV_NAMES.put(0x297, "KEY_MACRO8");
        EVDEV_NAMES.put(0x298, "KEY_MACRO9");
        EVDEV_NAMES.put(0x299, "KEY_MACRO10");
        EVDEV_NAMES.put(0x29a, "KEY_MACRO11");
        EVDEV_NAMES.put(0x29b, "KEY_MACRO12");
        EVDEV_NAMES.put(0x29c, "KEY_MACRO13");
        EVDEV_NAMES.put(0x29d, "KEY_MACRO14");
        EVDEV_NAMES.put(0x29e, "KEY_MACRO15");
        EVDEV_NAMES.put(0x29f, "KEY_MACRO16");
        EVDEV_NAMES.put(0x2a0, "KEY_MACRO17");
        EVDEV_NAMES.put(0x2a1, "KEY_MACRO18");
        EVDEV_NAMES.put(0x2a2, "KEY_MACRO19");
        EVDEV_NAMES.put(0x2a3, "KEY_MACRO20");
        EVDEV_NAMES.put(0x2a4, "KEY_MACRO21");
        EVDEV_NAMES.put(0x2a5, "KEY_MACRO22");
        EVDEV_NAMES.put(0x2a6, "KEY_MACRO23");
        EVDEV_NAMES.put(0x2a7, "KEY_MACRO24");
        EVDEV_NAMES.put(0x2a8, "KEY_MACRO25");
        EVDEV_NAMES.put(0x2a9, "KEY_MACRO26");
        EVDEV_NAMES.put(0x2aa, "KEY_MACRO27");
        EVDEV_NAMES.put(0x2ab, "KEY_MACRO28");
        EVDEV_NAMES.put(0x2ac, "KEY_MACRO29");
        EVDEV_NAMES.put(0x2ad, "KEY_MACRO30");

        // === Vendor-specific high buttons (0x2e8-0x2ff) ===
        for (int i = 0x2e8; i <= 0x2ff; i++) {
            EVDEV_NAMES.put(i, "KEY_0x" + Integer.toHexString(i));
        }
    }

    // Evdev code -> display name for axis codes
    private static final Map<Integer, String> AXIS_EVDEV_NAMES = new LinkedHashMap<>();
    static {
        AXIS_EVDEV_NAMES.put(0x00, "ABS_X (0x00)");
        AXIS_EVDEV_NAMES.put(0x01, "ABS_Y (0x01)");
        AXIS_EVDEV_NAMES.put(0x02, "ABS_Z (0x02)");
        AXIS_EVDEV_NAMES.put(0x03, "ABS_RX (0x03)");
        AXIS_EVDEV_NAMES.put(0x04, "ABS_RY (0x04)");
        AXIS_EVDEV_NAMES.put(0x05, "ABS_RZ (0x05)");
        AXIS_EVDEV_NAMES.put(0x06, "ABS_THROTTLE (0x06)");
        AXIS_EVDEV_NAMES.put(0x07, "ABS_RUDDER (0x07)");
        AXIS_EVDEV_NAMES.put(0x08, "ABS_WHEEL (0x08)");
        AXIS_EVDEV_NAMES.put(0x09, "ABS_GAS (0x09)");
        AXIS_EVDEV_NAMES.put(0x0a, "ABS_BRAKE (0x0a)");
        AXIS_EVDEV_NAMES.put(0x10, "ABS_HAT0X (0x10)");
        AXIS_EVDEV_NAMES.put(0x11, "ABS_HAT0Y (0x11)");
        AXIS_EVDEV_NAMES.put(0x12, "ABS_HAT1X (0x12)");
        AXIS_EVDEV_NAMES.put(0x13, "ABS_HAT1Y (0x13)");
        AXIS_EVDEV_NAMES.put(0x14, "ABS_HAT2X (0x14)");
        AXIS_EVDEV_NAMES.put(0x15, "ABS_HAT2Y (0x15)");
        AXIS_EVDEV_NAMES.put(0x16, "ABS_HAT3X (0x16)");
        AXIS_EVDEV_NAMES.put(0x17, "ABS_HAT3Y (0x17)");
    }

    private boolean mListening = true;
    private int mCapturedEvdevCode = -1;

    // Existing remaps: evdev source -> evdev target
    private final Map<Integer, Integer> mRemaps = new LinkedHashMap<>();

    public static GamepadRemapDialogFragment newInstance(boolean axisMode) {
        GamepadRemapDialogFragment f = new GamepadRemapDialogFragment();
        Bundle args = new Bundle();
        args.putBoolean(ARG_AXIS_MODE, axisMode);
        f.setArguments(args);
        return f;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        if (args != null) {
            mAxisMode = args.getBoolean(ARG_AXIS_MODE, false);
        }
    }

    private String getRemapProp() {
        return mAxisMode ? PROP_REMAP_AXIS : PROP_REMAP_BTN;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        loadExistingRemaps();

        // If remaps exist, show management dialog; otherwise, go to source selection
        if (!mRemaps.isEmpty()) {
            return createManageDialog();
        }
        if (mAxisMode) {
            return createAxisSourceDialog();
        }
        return createCaptureDialog();
    }

    /**
     * Dialog showing existing remaps with options to add new or clear.
     */
    private AlertDialog createManageDialog() {
        final Context context = requireContext();
        final androidx.fragment.app.FragmentManager fm = getParentFragmentManager();

        List<String> items = new ArrayList<>();
        List<Integer> sourceKeys = new ArrayList<>();

        for (Map.Entry<Integer, Integer> entry : mRemaps.entrySet()) {
            String fromName = getDisplayName(entry.getKey());
            String toName = getDisplayName(entry.getValue());
            items.add(fromName + " \u2192 " + toName);
            sourceKeys.add(entry.getKey());
        }
        items.add(getString(R.string.gamepad_remap_add_new));
        items.add(getString(R.string.gamepad_remap_clear_all));

        String[] itemArray = items.toArray(new String[0]);

        return new AlertDialog.Builder(context)
                .setTitle(R.string.gamepad_remap_dialog_title)
                .setItems(itemArray, (d, which) -> {
                    if (which < sourceKeys.size()) {
                        int source = sourceKeys.get(which);
                        showClearRemapDialog(context, fm, source);
                    } else if (which == sourceKeys.size()) {
                        // "Add new remap..."
                        if (mAxisMode) {
                            showAxisSourceDialog(context, fm);
                        } else {
                            showCaptureDialog(context, fm);
                        }
                    } else {
                        // "Clear all remaps"
                        mRemaps.clear();
                        saveRemaps(context, fm);
                        Toast.makeText(context, R.string.gamepad_remap_cleared,
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .create();
    }

    private void showClearRemapDialog(Context context,
            androidx.fragment.app.FragmentManager fm, int sourceEvdev) {
        String fromName = getDisplayName(sourceEvdev);
        String toName = getDisplayName(mRemaps.getOrDefault(sourceEvdev, 0));

        new AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.gamepad_remap_clear_single_title,
                        fromName, toName))
                .setPositiveButton(R.string.gamepad_remap_clear_single, (d, w) -> {
                    mRemaps.remove(sourceEvdev);
                    saveRemaps(context, fm);
                    Toast.makeText(context, R.string.gamepad_remap_cleared_single,
                            Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ---- Button mode: key-press capture with temporary remap disable ----

    /**
     * Create initial capture dialog (when no remaps exist).
     * No remap disable needed since the property is already empty.
     */
    private AlertDialog createCaptureDialog() {
        final Context context = requireContext();
        final androidx.fragment.app.FragmentManager fm = getParentFragmentManager();

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.gamepad_remap_dialog_title);
        builder.setMessage(R.string.gamepad_remap_press_source);
        builder.setNegativeButton(R.string.cancel, null);

        AlertDialog dialog = builder.create();
        setupKeyCapture(dialog, context, fm);
        return dialog;
    }

    /**
     * Show capture dialog from manage dialog ("Add new remap...").
     * Temporarily disables existing remaps so physical button codes arrive
     * unmodified through the daemon.
     */
    private void showCaptureDialog(Context context,
            androidx.fragment.app.FragmentManager fm) {
        mListening = true;
        mCapturedEvdevCode = -1;

        // Temporarily disable existing remaps so physical codes arrive unmodified
        disableRemapsForCapture();

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.gamepad_remap_dialog_title)
                .setMessage(R.string.gamepad_remap_press_source)
                .setNegativeButton(R.string.cancel, null)
                .create();

        // Restore existing remaps and stop capture mode if dismissed without capture.
        dialog.setOnDismissListener(d -> {
            stopCaptureMode();
            if (mRemapsDisabled) {
                restoreRemaps();
            }
        });

        setupKeyCapture(dialog, context, fm);
        dialog.show();
    }

    /**
     * Clear the remap property and bump config version so the daemon
     * stops remapping during source capture.
     */
    private void disableRemapsForCapture() {
        String prop = getRemapProp();
        String current = SystemProperties.get(prop, "");
        if (!current.isEmpty()) {
            // Use "none" sentinel instead of empty string, which setprop rejects
            SystemProperties.set(prop, "none");
            bumpConfigVersion();
            mRemapsDisabled = true;
        }
    }

    /**
     * Write mRemaps (the in-memory set) back to the property.
     * Used to restore after capture cancel.
     */
    private void restoreRemaps() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Integer, Integer> entry : mRemaps.entrySet()) {
            if (sb.length() > 0) sb.append(",");
            sb.append(entry.getKey()).append(":").append(entry.getValue());
        }
        String value = sb.length() > 0 ? sb.toString() : "none";
        Log.d(TAG, "restoreRemaps: writing '" + value + "' (mRemaps.size="
                + mRemaps.size() + ")");
        SystemProperties.set(getRemapProp(), value);
        bumpConfigVersion();
        mRemapsDisabled = false;
    }

    private Handler mCaptureHandler;
    private Runnable mCapturePoll;

    private void setupKeyCapture(AlertDialog dialog, Context context,
            androidx.fragment.app.FragmentManager fm) {
        // Tell PhoneWindowManager to consume HOME and write captured scancode
        // to a property (HOME can't be passed to apps — Android always goes home).
        SystemProperties.set("sys.gammaos.gamepad.capture_mode", "1");
        SystemProperties.set("sys.gammaos.gamepad.captured_key", "");

        // Poll for system-intercepted keys (HOME) that PhoneWindowManager captured
        // on our behalf.  When found, inject a synthetic KeyEvent into the dialog so
        // it flows through the same OnKeyListener path as regular button captures.
        mCaptureHandler = new Handler(Looper.getMainLooper());
        mCapturePoll = new Runnable() {
            @Override
            public void run() {
                if (!mListening) return;
                String captured = SystemProperties.get(
                        "sys.gammaos.gamepad.captured_key", "");
                if (!captured.isEmpty()) {
                    SystemProperties.set("sys.gammaos.gamepad.captured_key", "");
                    int scanCode;
                    try {
                        scanCode = Integer.parseInt(captured);
                    } catch (NumberFormatException e) {
                        mCaptureHandler.postDelayed(this, 100);
                        return;
                    }
                    // Inject synthetic HOME key with the real scancode so the
                    // OnKeyListener captures it through the normal path.
                    long now = android.os.SystemClock.uptimeMillis();
                    KeyEvent synth = new KeyEvent(now, now,
                            KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HOME,
                            0 /* repeat */, 0 /* metaState */,
                            -1 /* deviceId */, scanCode);
                    dialog.dispatchKeyEvent(synth);
                    return;
                }
                mCaptureHandler.postDelayed(this, 100);
            }
        };
        mCaptureHandler.postDelayed(mCapturePoll, 100);

        dialog.setOnKeyListener((d, keyCode, event) -> {
            if (!mListening) return false;

            // Consume all key events (DOWN, UP, repeat) from non-touchscreen
            // devices while listening to prevent BACK dismiss and HOME navigation.
            if (isTouchscreenEvent(event)) return false;
            if (event.getAction() != KeyEvent.ACTION_DOWN) return true;

            int scanCode = event.getScanCode();
            int evdevCode;
            if (scanCode > 0) {
                evdevCode = scanCode;
            } else {
                evdevCode = androidToEvdev(keyCode);
                if (evdevCode < 0) return false;
            }

            stopCaptureMode();
            finishCapture(dialog, context, fm, evdevCode, keyCode, scanCode);
            return true;
        });
    }

    /** Stop capture mode polling and clear system properties. */
    private void stopCaptureMode() {
        if (mCaptureHandler != null && mCapturePoll != null) {
            mCaptureHandler.removeCallbacks(mCapturePoll);
        }
        SystemProperties.set("sys.gammaos.gamepad.capture_mode", "0");
        SystemProperties.set("sys.gammaos.gamepad.captured_key", "");
    }

    private void finishCapture(AlertDialog dialog, Context context,
            androidx.fragment.app.FragmentManager fm,
            int evdevCode, int keyCode, int scanCode) {
        mCapturedEvdevCode = evdevCode;
        mListening = false;

        Log.d(TAG, "Captured: keyCode=" + keyCode
                + " scanCode=" + scanCode
                + " evdevCode=" + evdevCode
                + " (0x" + Integer.toHexString(evdevCode) + ")"
                + " mRemaps.size=" + mRemaps.size());

        // Prevent dismiss handler from restoring — we'll handle it
        mRemapsDisabled = false;
        stopCaptureMode();
        dialog.setOnDismissListener(null);
        dialog.dismiss();
        showTargetSelection(context, fm);
    }

    /**
     * Accept any button from a gamepad/joystick source.
     */
    private boolean isGamepadKey(int keyCode, KeyEvent event) {
        if (KEYCODE_TO_EVDEV.containsKey(keyCode)) return true;
        if (keyCode >= KeyEvent.KEYCODE_BUTTON_1
                && keyCode <= KeyEvent.KEYCODE_BUTTON_16) return true;
        if (event.getDevice() != null) {
            int source = event.getDevice().getSources();
            if ((source & android.view.InputDevice.SOURCE_GAMEPAD) != 0
                    || (source & android.view.InputDevice.SOURCE_JOYSTICK) != 0) {
                return true;
            }
        }
        return false;
    }

    /** Reject events from touchscreen devices during capture. */
    private boolean isTouchscreenEvent(KeyEvent event) {
        if (event.getDevice() == null) return false;
        int source = event.getDevice().getSources();
        return (source & android.view.InputDevice.SOURCE_TOUCHSCREEN) != 0;
    }

    /**
     * Convert Android keycode to Linux evdev code.
     */
    private int androidToEvdev(int keyCode) {
        Integer evdev = KEYCODE_TO_EVDEV.get(keyCode);
        if (evdev != null) return evdev;

        // BUTTON_1..BUTTON_16 map to BTN_TRIGGER_HAPPY1..16
        if (keyCode >= KeyEvent.KEYCODE_BUTTON_1
                && keyCode <= KeyEvent.KEYCODE_BUTTON_16) {
            return 0x2c0 + (keyCode - KeyEvent.KEYCODE_BUTTON_1);
        }

        // Unknown keycodes: offset into vendor range
        return keyCode + 0x100;
    }

    // ---- Target selection (shared between button and axis mode) ----

    private void showTargetSelection(Context context,
            androidx.fragment.app.FragmentManager fm) {
        String sourceName = getDisplayName(mCapturedEvdevCode);

        Map<String, Integer> targets = mAxisMode
                ? STANDARD_AXIS_TARGETS : STANDARD_TARGETS;
        List<String> names = new ArrayList<>(targets.keySet());
        List<Integer> codes = new ArrayList<>(targets.values());
        if (!mAxisMode) {
            names.add(context.getString(R.string.gamepad_remap_custom));
        }

        String[] items = names.toArray(new String[0]);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(context.getString(
                        R.string.gamepad_remap_choose_target, sourceName))
                .setItems(items, (d, which) -> {
                    if (which < codes.size()) {
                        int targetCode = codes.get(which);
                        Log.d(TAG, "Target selected: " + names.get(which)
                                + " code=" + targetCode
                                + " (0x" + Integer.toHexString(targetCode) + ")"
                                + " for source=0x"
                                + Integer.toHexString(mCapturedEvdevCode)
                                + " mRemaps.size before=" + mRemaps.size());
                        mRemaps.put(mCapturedEvdevCode, targetCode);
                        Log.d(TAG, "mRemaps.size after=" + mRemaps.size());
                        saveRemaps(context, fm);
                        Toast.makeText(context, R.string.gamepad_remap_saved,
                                Toast.LENGTH_SHORT).show();
                    } else if (!mAxisMode) {
                        showCustomTargetSelection(context, fm);
                    }
                })
                .setNegativeButton(R.string.cancel, (d, w) -> restoreRemaps())
                .create();
        dialog.setOnCancelListener(d -> restoreRemaps());
        dialog.show();
    }

    private void showCustomTargetSelection(Context context,
            androidx.fragment.app.FragmentManager fm) {
        String sourceName = getDisplayName(mCapturedEvdevCode);

        Map<Integer, String> evdevNames = mAxisMode
                ? AXIS_EVDEV_NAMES : EVDEV_NAMES;
        List<String> names = new ArrayList<>();
        List<Integer> codes = new ArrayList<>();
        for (Map.Entry<Integer, String> entry : evdevNames.entrySet()) {
            codes.add(entry.getKey());
            names.add(entry.getValue());
        }

        String[] items = names.toArray(new String[0]);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(context.getString(
                        R.string.gamepad_remap_choose_target, sourceName))
                .setItems(items, (d, which) -> {
                    mRemaps.put(mCapturedEvdevCode, codes.get(which));
                    saveRemaps(context, fm);
                    Toast.makeText(context, R.string.gamepad_remap_saved,
                            Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel, (d, w) -> restoreRemaps())
                .create();
        dialog.setOnCancelListener(d -> restoreRemaps());
        dialog.show();
    }

    // ---- Axis mode: list-based source selection ----

    /**
     * Create axis source selection dialog (initial, no existing remaps).
     */
    private AlertDialog createAxisSourceDialog() {
        final Context context = requireContext();
        final androidx.fragment.app.FragmentManager fm = getParentFragmentManager();
        return buildAxisSourceDialog(context, fm);
    }

    /**
     * Show axis source selection from manage dialog ("Add new remap...").
     */
    private void showAxisSourceDialog(Context context,
            androidx.fragment.app.FragmentManager fm) {
        buildAxisSourceDialog(context, fm).show();
    }

    private AlertDialog buildAxisSourceDialog(Context context,
            androidx.fragment.app.FragmentManager fm) {
        List<String> names = new ArrayList<>(STANDARD_AXIS_TARGETS.keySet());
        List<Integer> codes = new ArrayList<>(STANDARD_AXIS_TARGETS.values());
        String[] items = names.toArray(new String[0]);

        return new AlertDialog.Builder(context)
                .setTitle(R.string.gamepad_remap_dialog_title)
                .setItems(items, (d, which) -> {
                    mCapturedEvdevCode = codes.get(which);
                    showTargetSelection(context, fm);
                })
                .setNegativeButton(R.string.cancel, null)
                .create();
    }

    // ---- Common helpers ----

    private String getDisplayName(int evdevCode) {
        if (mAxisMode) {
            for (Map.Entry<String, Integer> entry
                    : STANDARD_AXIS_TARGETS.entrySet()) {
                if (entry.getValue() == evdevCode) return entry.getKey();
            }
            String name = AXIS_EVDEV_NAMES.get(evdevCode);
            if (name != null) return name;
        } else {
            for (Map.Entry<String, Integer> entry
                    : STANDARD_TARGETS.entrySet()) {
                if (entry.getValue() == evdevCode) return entry.getKey();
            }
            String name = EVDEV_NAMES.get(evdevCode);
            if (name != null) return name;
        }
        return "0x" + Integer.toHexString(evdevCode);
    }

    private void loadExistingRemaps() {
        mRemaps.clear();
        String prop = SystemProperties.get(getRemapProp(), "");
        Log.d(TAG, "loadExistingRemaps: prop=" + getRemapProp()
                + " value='" + prop + "'");
        if (prop.isEmpty() || "none".equals(prop)) return;

        for (String pair : prop.split(",")) {
            String[] parts = pair.split(":");
            if (parts.length == 2) {
                try {
                    int from = Integer.parseInt(parts[0]);
                    int to = Integer.parseInt(parts[1]);
                    mRemaps.put(from, to);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        Log.d(TAG, "loadExistingRemaps: loaded " + mRemaps.size() + " remaps");
    }

    private void saveRemaps(Context context,
            androidx.fragment.app.FragmentManager fm) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Integer, Integer> entry : mRemaps.entrySet()) {
            if (sb.length() > 0) sb.append(",");
            sb.append(entry.getKey()).append(":").append(entry.getValue());
        }
        String value = sb.length() > 0 ? sb.toString() : "none";
        Log.d(TAG, "saveRemaps: writing '" + value + "' to " + getRemapProp()
                + " (mRemaps.size=" + mRemaps.size() + ")");
        SystemProperties.set(getRemapProp(), value);
        bumpConfigVersion();
        mRemapsDisabled = false;

        // Signal parent fragment that remaps changed
        if (fm != null) {
            Bundle result = new Bundle();
            result.putBoolean("changed", true);
            fm.setFragmentResult("remap_changed", result);
        }
    }

    private void bumpConfigVersion() {
        SystemProperties.set("persist.gammaos.gamepad.full_reload", "1");
        int version = SystemProperties.getInt(PROP_CONFIG_VERSION, 0);
        SystemProperties.set(PROP_CONFIG_VERSION, String.valueOf(version + 1));
    }
}
