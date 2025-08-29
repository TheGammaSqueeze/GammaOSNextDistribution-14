/*
 * Copyright (C) 2016 The CyanogenMod Project
 * Copyright (c) 2017 The LineageOS Project
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

package com.android.systemui.qs.tiles;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.service.quicksettings.Tile;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.internal.logging.MetricsLogger;
import com.android.systemui.R;
import com.android.systemui.plugins.qs.QSTile.BooleanState;
import com.android.systemui.plugins.qs.QSTile.Icon;
import com.android.systemui.qs.QSHost;
import com.android.systemui.qs.QsEventLogger;
import com.android.systemui.qs.logging.QSLogger;
import com.android.systemui.qs.tileimpl.QSTileImpl;
import com.android.systemui.qs.tileimpl.QSTileImpl.ResourceIcon;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.inject.Inject;

import static com.android.internal.logging.MetricsLogger.VIEW_UNKNOWN;

/**
 * Quick settings tile: Mapping Editor.
 */
public class MappingEditorTile extends QSTileImpl<BooleanState> {
    public static final String TILE_SPEC = "mappingeditor";

    private final Icon mIcon = ResourceIcon.get(R.drawable.ic_more_vert);
    private final QSHost mQsHost;

    @Inject
    public MappingEditorTile(
            QSHost host,
            QsEventLogger qsEventLogger,
            @com.android.systemui.dagger.qualifiers.Background Looper bg,
            @com.android.systemui.dagger.qualifiers.Main Handler main,
            com.android.systemui.plugins.FalsingManager falsing,
            MetricsLogger metrics,
            com.android.systemui.plugins.statusbar.StatusBarStateController statusBarStateController,
            com.android.systemui.plugins.ActivityStarter activityStarter,
            QSLogger qsLogger
    ) {
        super(host, qsEventLogger, bg, main, falsing, metrics, statusBarStateController,
              activityStarter, qsLogger);
        mQsHost = host;
    }

    @Override
    public BooleanState newTileState() { return new BooleanState(); }

    @Override
    protected void handleClick(@Nullable View view) {
        Intent intent = new Intent(mContext, MappingEditorActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mActivityStarter.postStartActivityDismissingKeyguard(intent, 0);
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        state.label = "Edit Mappings";
        state.icon  = mIcon;
        state.state = Tile.STATE_INACTIVE;
    }

    @Override public Intent getLongClickIntent() { return null; }

    @Override
    protected void handleLongClick(@Nullable View view) {
        Intent intent = new Intent(mContext, MappingEditorActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mActivityStarter.postStartActivityDismissingKeyguard(intent, 0);
    }

    @Override public CharSequence getTileLabel() { return "Edit Button Mappings"; }
    @Override public int getMetricsCategory() { return VIEW_UNKNOWN; }

    /**
     * Embedded editing Activity.
     */
    public static class MappingEditorActivity extends Activity {
        private static final String PATH = "/data/GammaPad/MAPPINGS";

        // Keys extracted
        private static final String[] ALL_KEYS = {
            "BTN_0","BTN_1","BTN_2","BTN_3","BTN_4","BTN_5","BTN_6","BTN_7","BTN_8","BTN_9",
            "BTN_A","BTN_B","BTN_BACK","BTN_BASE","BTN_BASE2","BTN_BASE3","BTN_BASE4","BTN_BASE5","BTN_BASE6","BTN_C",
            "BTN_DEAD","BTN_DIGI","BTN_DPAD_DOWN","BTN_DPAD_LEFT","BTN_DPAD_RIGHT","BTN_DPAD_UP","BTN_EAST","BTN_EXTRA","BTN_FORWARD","BTN_GAMEPAD",
            "BTN_GEAR_DOWN","BTN_GEAR_UP","BTN_JOYSTICK","BTN_LEFT","BTN_MIDDLE","BTN_MISC","BTN_MODE","BTN_MOUSE","BTN_NORTH","BTN_PINKIE",
            "BTN_RIGHT","BTN_SELECT","BTN_SIDE","BTN_SOUTH","BTN_START","BTN_STYLUS","BTN_STYLUS2","BTN_STYLUS3","BTN_TASK","BTN_THUMB",
            "BTN_THUMB2","BTN_THUMBL","BTN_THUMBR","BTN_TL","BTN_TL2","BTN_TOOL_AIRBRUSH","BTN_TOOL_BRUSH","BTN_TOOL_DOUBLETAP","BTN_TOOL_FINGER","BTN_TOOL_LENS",
            "BTN_TOOL_MOUSE","BTN_TOOL_PEN","BTN_TOOL_PENCIL","BTN_TOOL_QUADTAP","BTN_TOOL_QUINTTAP","BTN_TOOL_RUBBER","BTN_TOOL_TRIPLETAP","BTN_TOP","BTN_TOP2","BTN_TOUCH",
            "BTN_TR","BTN_TR2","BTN_TRIGGER","BTN_WEST","BTN_WHEEL","BTN_X","BTN_Y","BTN_Z","KEY_0","KEY_1",
            "KEY_102ND","KEY_10CHANNELSDOWN","KEY_10CHANNELSUP","KEY_2","KEY_3","KEY_4","KEY_5","KEY_6","KEY_7","KEY_8",
            "KEY_9","KEY_A","KEY_AB","KEY_ADDRESSBOOK","KEY_AGAIN","KEY_ALL_APPLICATIONS","KEY_ALS_TOGGLE","KEY_ALTERASE","KEY_ANGLE","KEY_ANGLE",
            "KEY_APOSTROPHE","KEY_ARCHIVE","KEY_ARCHIVE","KEY_ASPECT_RATIO","KEY_ASPECT_RATIO","KEY_ATTACK_MODE","KEY_ATTENDANT_OFF","KEY_ATTENDANT_ON","KEY_ATTENDANT_TOGGLE","KEY_AUDIO",
            "KEY_AUTO_PROGRAM_SELECT","KEY_AUTO_TARE_CALIBRATION","KEY_AUX","KEY_AUX","KEY_B","KEY_BACK","KEY_BACKLIGHT_MINUS","KEY_BACKLIGHT_PLUS","KEY_BACKSLASH","KEY_BACKSPACE",
            "KEY_BASSBOOST","KEY_BASSBOOST","KEY_BATTERY","KEY_BLUE","KEY_BLUETOOTH","KEY_BOOKMARKS","KEY_BREAK","KEY_BRIGHTNESSDOWN","KEY_BRIGHTNESSUP","KEY_BRIGHTNESS_AUTO",
            "KEY_BRIGHTNESS_CYCLE","KEY_BRIGHTNESS_TOGGLE","KEY_BRIGHTNESS_ZERO","KEY_BRL_DOT1","KEY_BRL_DOT10","KEY_BRL_DOT2","KEY_BRL_DOT3","KEY_BRL_DOT4","KEY_BRL_DOT5","KEY_BRL_DOT6",
            "KEY_BRL_DOT7","KEY_BRL_DOT8","KEY_BRL_DOT9","KEY_C","KEY_CALC","KEY_CALENDAR","KEY_CAMERA","KEY_CAMERA_DOWN","KEY_CAMERA_FOCUS","KEY_CAMERA_LEFT",
            "KEY_CAMERA_RIGHT","KEY_CAMERA_UP","KEY_CAMERA_ZOOMIN","KEY_CAMERA_ZOOMOUT","KEY_CANCEL","KEY_CANCEL","KEY_CAPSLOCK","KEY_CAPSLOCK","KEY_CD","KEY_CHANNEL",
            "KEY_CHANNEL","KEY_CHANNELDOWN","KEY_CHANNELDOWN","KEY_CHANNELUP","KEY_CHANNELUP","KEY_CHAT","KEY_CLEAR","KEY_CLEARVU_SONAR","KEY_CLOSE","KEY_CLOSE",
            "KEY_CLOSECD","KEY_CLOSECD","KEY_CLR_AGENDA","KEY_COFFEE","KEY_COMMA","KEY_COMPOSE","KEY_COMPOSE","KEY_COMPUTER","KEY_COMPUTER","KEY_CONFIG",
            "KEY_CONFIG","KEY_CONNECT","KEY_CONTEXT_MENU","KEY_CONTEXT_MENU","KEY_CONTROLPANEL","KEY_COOLING_FAN_ON","KEY_COPY","KEY_COPY","KEY_CUT","KEY_CYCLEWINDOWS",
            "KEY_D","KEY_DASHBOARD","KEY_DATABASE","KEY_DELETE","KEY_DELETEFILE","KEY_DEL_EOL","KEY_DEL_EOL","KEY_DEL_EOS","KEY_DEL_EOS","KEY_DEL_LINE",
            "KEY_DIGITS","KEY_DIRECTION","KEY_DIRECTORY","KEY_DISPLAYTOGGLE","KEY_DISPLAY_OFF","KEY_DOCUMENTS","KEY_DOLLAR","KEY_DOT","KEY_DOWN","KEY_DVD",
            "KEY_E","KEY_EDIT","KEY_EDITOR","KEY_EJECTCD","KEY_EJECTCLOSECD","KEY_EMAIL","KEY_END","KEY_ENTER","KEY_EPG","KEY_EQUAL",
            "KEY_ESC","KEY_EURO","KEY_EXIT","KEY_F","KEY_F1","KEY_F10","KEY_F11","KEY_F12","KEY_F13","KEY_F14",
            "KEY_F15","KEY_F16","KEY_F17","KEY_F18","KEY_F19","KEY_F2","KEY_F20","KEY_F21","KEY_F22","KEY_F23",
            "KEY_F24","KEY_F3","KEY_F4","KEY_F5","KEY_F6","KEY_F7","KEY_F8","KEY_F9","KEY_FASTFORWARD","KEY_FAVORITES",
            "KEY_FILE","KEY_FINANCE","KEY_FIND","KEY_FIRST","KEY_FN","KEY_FN_1","KEY_FN_2","KEY_FN_B","KEY_FN_D","KEY_FN_E",
            "KEY_FN_ESC","KEY_FN_F","KEY_FN_F1","KEY_FN_F10","KEY_FN_F11","KEY_FN_F12","KEY_FN_F2","KEY_FN_F3","KEY_FN_F4","KEY_FN_F5",
            "KEY_FN_F6","KEY_FN_F7","KEY_FN_F8","KEY_FN_F9","KEY_FN_RIGHT_SHIFT","KEY_FN_S","KEY_FORWARD","KEY_FORWARDMAIL","KEY_FRAMEBACK","KEY_FRAMEFORWARD",
            "KEY_FRONT","KEY_FULL_SCREEN","KEY_G","KEY_GAMES","KEY_GOTO","KEY_GRAPHICSEDITOR","KEY_GRAVE","KEY_GREEN","KEY_H","KEY_HANGEUL",
            "KEY_HANGUEL","KEY_HANGUP_PHONE","KEY_HANJA","KEY_HELP","KEY_HENKAN","KEY_HIRAGANA","KEY_HOME","KEY_HOMEPAGE","KEY_HP","KEY_I",
            "KEY_IMAGES","KEY_INFO","KEY_INSERT","KEY_INS_LINE","KEY_ISO","KEY_J","KEY_K","KEY_KATAKANA","KEY_KATAKANAHIRAGANA","KEY_KBDILLUMDOWN",
            "KEY_KBDILLUMTOGGLE","KEY_KBDILLUMUP","KEY_KEYBOARD","KEY_KP0","KEY_KP1","KEY_KP2","KEY_KP3","KEY_KP4","KEY_KP5","KEY_KP6",
            "KEY_KP7","KEY_KP8","KEY_KP9","KEY_KPASTERISK","KEY_KPCOMMA","KEY_KPDOT","KEY_KPENTER","KEY_KPEQUAL","KEY_KPJPCOMMA","KEY_KPLEFTPAREN",
            "KEY_KPMINUS","KEY_KPPLUS","KEY_KPPLUSMINUS","KEY_KPRIGHTPAREN","KEY_KPSLASH","KEY_L","KEY_LANGUAGE","KEY_LAST","KEY_LEFT","KEY_LEFTALT",
            "KEY_LEFTBRACE","KEY_LEFTCTRL","KEY_LEFTMETA","KEY_LEFTSHIFT","KEY_LIGHTS_TOGGLE","KEY_LINEFEED","KEY_LIST","KEY_LOGOFF","KEY_M","KEY_MACRO",
            "KEY_MAIL","KEY_MEDIA","KEY_MEDIA_REPEAT","KEY_MEMO","KEY_MENU","KEY_MESSENGER","KEY_MHP","KEY_MICMUTE","KEY_MINUS","KEY_MODE",
            "KEY_MOVE","KEY_MP3","KEY_MSDOS","KEY_MUHENKAN","KEY_MUTE","KEY_N","KEY_NEW","KEY_NEWS","KEY_NEXT","KEY_NEXTSONG",
            "KEY_NOTIFICATION_CENTER","KEY_NUMERIC_0","KEY_NUMERIC_1","KEY_NUMERIC_2","KEY_NUMERIC_3","KEY_NUMERIC_4","KEY_NUMERIC_5","KEY_NUMERIC_6","KEY_NUMERIC_7","KEY_NUMERIC_8",
            "KEY_NUMERIC_9","KEY_NUMERIC_A","KEY_NUMERIC_B","KEY_NUMERIC_C","KEY_NUMERIC_D","KEY_NUMERIC_POUND","KEY_NUMERIC_STAR","KEY_NUMLOCK","KEY_O","KEY_OK",
            "KEY_OPEN","KEY_OPTION","KEY_P","KEY_PAGEDOWN","KEY_PAGEUP","KEY_PASTE","KEY_PAUSE","KEY_PAUSECD","KEY_PC","KEY_PHONE",
            "KEY_PICKUP_PHONE","KEY_PLAY","KEY_PLAYCD","KEY_PLAYER","KEY_PLAYPAUSE","KEY_POWER","KEY_POWER2","KEY_PRESENTATION","KEY_PREVIOUS","KEY_PREVIOUSSONG",
            "KEY_PRINT","KEY_PROG1","KEY_PROG2","KEY_PROG3","KEY_PROG4","KEY_PROGRAM","KEY_PROPS","KEY_PVR","KEY_Q","KEY_QUESTION",
            "KEY_R","KEY_RADIO","KEY_RECORD","KEY_RED","KEY_REDO","KEY_REFRESH","KEY_REPLY","KEY_RESERVED","KEY_RESTART","KEY_REWIND",
            "KEY_RFKILL","KEY_RIGHT","KEY_RIGHTALT","KEY_RIGHTBRACE","KEY_RIGHTCTRL","KEY_RIGHTMETA","KEY_RIGHTSHIFT","KEY_RO","KEY_ROTATE_DISPLAY","KEY_S",
            "KEY_SAT","KEY_SAT2","KEY_SAVE","KEY_SCALE","KEY_SCREEN","KEY_SCREENLOCK","KEY_SCROLLDOWN","KEY_SCROLLLOCK","KEY_SCROLLUP","KEY_SEARCH",
            "KEY_SELECT","KEY_SEMICOLON","KEY_SEND","KEY_SENDFILE","KEY_SETUP","KEY_SHOP","KEY_SHUFFLE","KEY_SLASH","KEY_SLEEP","KEY_SLOW",
            "KEY_SOUND","KEY_SPACE","KEY_SPELLCHECK","KEY_SPORT","KEY_SPREADSHEET","KEY_STOP","KEY_STOPCD","KEY_SUBTITLE","KEY_SUSPEND","KEY_SWITCHVIDEOMODE",
            "KEY_SYSRQ","KEY_T","KEY_TAB","KEY_TAPE","KEY_TEEN","KEY_TEXT","KEY_TIME","KEY_TITLE","KEY_TOUCHPAD_OFF","KEY_TOUCHPAD_ON",
            "KEY_TOUCHPAD_TOGGLE","KEY_TUNER","KEY_TV","KEY_TV2","KEY_TWEN","KEY_U","KEY_UNDO","KEY_UNKNOWN","KEY_UP","KEY_UWB",
            "KEY_V","KEY_VCR","KEY_VCR2","KEY_VENDOR","KEY_VIDEO","KEY_VIDEOPHONE","KEY_VIDEO_NEXT","KEY_VIDEO_PREV","KEY_VOICEMAIL","KEY_VOLUMEDOWN",
            "KEY_VOLUMEUP","KEY_W","KEY_WAKEUP","KEY_WIMAX","KEY_WLAN","KEY_WORDPROCESSOR","KEY_WPS_BUTTON","KEY_WWAN","KEY_WWW","KEY_X",
            "KEY_XFER","KEY_Y","KEY_YELLOW","KEY_YEN","KEY_Z","KEY_ZENKAKUHANKAKU","KEY_ZOOM","KEY_ZOOMIN","KEY_ZOOMOUT","KEY_ZOOMRESET",
        };

        private RecyclerView recycler;
        private Button save, cancel, reset;
        private MappingAdapter adapter;
        private List<Mapping> mappings = new ArrayList<>();

        @Override
        protected void onCreate(Bundle s) {
            super.onCreate(s);
            // Root layout
            LinearLayout root = new LinearLayout(this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

            // Recycler
            recycler = new RecyclerView(this);
            recycler.setLayoutManager(new LinearLayoutManager(this));
            adapter = new MappingAdapter(this, mappings);
            recycler.setAdapter(adapter);
            root.addView(recycler,
                new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

            // Buttons row
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            save   = new Button(this); save.setText("Save");
            cancel = new Button(this); cancel.setText("Cancel");
            reset  = new Button(this); reset.setText("Reset");
            row.addView(save,   new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(cancel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(reset,  new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            root.addView(row,
                new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            setContentView(root);

            // make the window itself span the full screen width:
            getWindow().setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            );

            loadMappings();
            save.setOnClickListener(v -> { saveMappings(); finish(); });
            cancel.setOnClickListener(v -> finish());
            reset.setOnClickListener(v -> {
                for (Mapping m : mappings) m.current = m.original;
                adapter.notifyDataSetChanged();
            });
        }

        private void loadMappings() {
            mappings.clear();
            try (BufferedReader br = new BufferedReader(new FileReader(PATH))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String[] p = line.trim().split("\\s+");
                    if (p.length == 2) mappings.add(new Mapping(p[0], p[1]));
                }
            } catch (Exception ignored) {}
            adapter.notifyDataSetChanged();
        }

        private void saveMappings() {
            try (BufferedWriter bw = new BufferedWriter(new FileWriter(PATH, false))) {
                for (Mapping m : mappings) {
                    bw.write(m.original + " " + m.current + "\n");
                }
            } catch (Exception e) {
                new AlertDialog.Builder(this)
                    .setMessage("Failed to save mappings")
                    .setPositiveButton("OK", null)
                    .show();
            }
        }

        private static class Mapping {
            String original, current;
            Mapping(String o, String c) { original = o; current = c; }
        }

        private static class MappingAdapter extends RecyclerView.Adapter<MappingAdapter.VH> {
            private final Context ctx;
            private final List<Mapping> data;

            MappingAdapter(Context c, List<Mapping> d) { ctx = c; data = d; }

            @Override
            public VH onCreateViewHolder(ViewGroup parent, int viewType) {
                LinearLayout lay = new LinearLayout(ctx);
                lay.setOrientation(LinearLayout.HORIZONTAL);
                lay.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));

                // LEFT LABEL
                TextView tv = new TextView(ctx);
                tv.setEms(20);                       // up to 20 chars
                tv.setSingleLine(true);
                tv.setMaxEms(20);
                tv.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);

                // RIGHT SPINNER
                Spinner sp = new Spinner(ctx);
                ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(
                    ctx, android.R.layout.simple_spinner_item, ALL_KEYS);
                spinnerAdapter.setDropDownViewResource(
                    android.R.layout.simple_spinner_dropdown_item);
                sp.setAdapter(spinnerAdapter);
                // align the chosen text to the right
                sp.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
                LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 3f);

                lay.addView(tv, lp1);
                lay.addView(sp, lp2);

                return new VH(lay, tv, sp);
            }

            @Override
            public void onBindViewHolder(VH holder, int position) {
                Mapping m = data.get(position);
                holder.left.setText(m.original);
                int idx = Arrays.asList(ALL_KEYS).indexOf(m.current);
                holder.spinner.setSelection(idx >= 0 ? idx : 0);
                holder.spinner.setOnItemSelectedListener(new SimpleListener() {
                    @Override
                    public void onItemSelected(int p) {
                        m.current = ALL_KEYS[p];
                    }
                });
            }

            @Override
            public int getItemCount() {
                return data.size();
            }

            static class VH extends RecyclerView.ViewHolder {
                final TextView left;
                final Spinner spinner;
                VH(View v, TextView t, Spinner s) {
                    super(v);
                    left = t;
                    spinner = s;
                }
            }
        }

        private abstract static class SimpleListener
                implements android.widget.AdapterView.OnItemSelectedListener {
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent,
                                       View v, int pos, long id) {
                onItemSelected(pos);
            }
            public abstract void onItemSelected(int pos);
        }
    }
}
