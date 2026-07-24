/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.server.policy;

import static android.view.WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.content.pm.ApplicationInfo;
import android.content.res.Configuration;
import android.content.res.ColorStateList;
import android.database.ContentObserver;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.os.Vibrator;
import android.provider.Settings;
import android.service.dreams.DreamService;
import android.service.dreams.IDreamManager;
import android.sysprop.TelephonyProperties;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.util.ArraySet;
import android.util.DisplayMetrics;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.WindowManagerGlobal;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.graphics.PorterDuff;
import android.widget.ListView;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.graphics.Paint;

import com.android.internal.R;
import com.android.internal.app.AlertController;
import com.android.internal.globalactions.Action;
import com.android.internal.globalactions.ActionsAdapter;
import com.android.internal.globalactions.ActionsDialog;
import com.android.internal.globalactions.LongPressAction;
import com.android.internal.globalactions.SinglePressAction;
import com.android.internal.globalactions.ToggleAction;
import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.internal.util.EmergencyAffordanceManager;
import com.android.internal.widget.LockPatternUtils;
import com.android.server.policy.WindowManagerPolicy.WindowManagerFuncs;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import android.app.AlertDialog;
import android.os.Looper;
import android.os.Handler;
import android.os.SystemProperties;
import android.app.Activity;
import android.widget.Toast;
import android.hardware.display.DisplayManager;
import android.os.Binder;
import android.view.Display;
import android.os.BatteryManager;
import android.widget.TextView;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.os.Build;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;


/**
 * Helper to show the global actions dialog.  Each item is an {@link Action} that
 * may show depending on whether the keyguard is showing, and whether the device
 * is provisioned.
 */
class LegacyGlobalActions implements DialogInterface.OnDismissListener, DialogInterface.OnClickListener  {

    private static final String TAG = "LegacyGlobalActions";

    private static final boolean SHOW_SILENT_TOGGLE = true;

    /* Valid settings for global actions keys.
     * see config.xml config_globalActionList */
    private static final String GLOBAL_ACTION_KEY_POWER = "power";
    private static final String GLOBAL_ACTION_KEY_AIRPLANE = "airplane";
    private static final String GLOBAL_ACTION_KEY_BUGREPORT = "bugreport";
    private static final String GLOBAL_ACTION_KEY_SILENT = "silent";
    private static final String GLOBAL_ACTION_KEY_USERS = "users";
    private static final String GLOBAL_ACTION_KEY_SETTINGS = "settings";
    private static final String GLOBAL_ACTION_KEY_LOCKDOWN = "lockdown";
    private static final String GLOBAL_ACTION_KEY_VOICEASSIST = "voiceassist";
    private static final String GLOBAL_ACTION_KEY_ASSIST = "assist";
    private static final String GLOBAL_ACTION_KEY_RESTART = "restart";

    private final Context mContext;
    private final WindowManagerFuncs mWindowManagerFuncs;
    private final AudioManager mAudioManager;
    private final IDreamManager mDreamManager;
    private final Runnable mOnDismiss;

    private ArrayList<Action> mItems;
    private ActionsDialog mDialog;

    private Action mSilentModeAction;
    private ToggleAction mAirplaneModeOn;

    private ActionsAdapter mAdapter;
    private int mBrightnessItemPosition = -1;

    private boolean mKeyguardShowing = false;
    private boolean mDeviceProvisioned = false;
    private ToggleAction.State mAirplaneState = ToggleAction.State.Off;
    private boolean mIsWaitingForEcmExit = false;
    private final boolean mHasTelephony;
    private boolean mHasVibrator;
    private final boolean mShowSilentToggle;
    private final EmergencyAffordanceManager mEmergencyAffordanceManager;

    private View headerView; // Member variable to hold the header view

    private Handler mMemoryHandler = new Handler(Looper.getMainLooper());
    private Runnable mMemoryUpdateRunnable;
    private static final int MEMORY_UPDATE_INTERVAL = 500; // 0.5 second

    private HashMap<Integer, Long> prevIdleTimes = new HashMap<>();
    private HashMap<Integer, Long> prevTotalTimes = new HashMap<>();

    // Aggregated CPU tracking + smoothing
    private long prevIdleAll = 0L;
    private long prevTotalAll = 0L;
    private float smoothedCpu = 0.0f;
    private static final float CPU_EMA_ALPHA = 0.3f;

    /**
     * @param context everything needs a context :(
     */
    public LegacyGlobalActions(Context context, WindowManagerFuncs windowManagerFuncs,
            Runnable onDismiss) {
        mContext = context;
        mWindowManagerFuncs = windowManagerFuncs;
        mOnDismiss = onDismiss;
        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        mDreamManager = IDreamManager.Stub.asInterface(
                ServiceManager.getService(DreamService.DREAM_SERVICE));

        // receive broadcasts
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(TelephonyManager.ACTION_EMERGENCY_CALLBACK_MODE_CHANGED);
        // By default CLOSE_SYSTEM_DIALOGS broadcast is sent only for current user, which is user
        // 10 on devices with headless system user enabled.
        // In order to receive the broadcast, register the broadcast receiver with UserHandle.ALL.
        context.registerReceiverAsUser(mBroadcastReceiver, UserHandle.ALL, filter, null, null,
                Context.RECEIVER_EXPORTED);

        mHasTelephony =
                context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY);

        // get notified of phone state changes
        TelephonyManager telephonyManager =
                (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        telephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_SERVICE_STATE);
        mContext.getContentResolver().registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.AIRPLANE_MODE_ON), true,
                mAirplaneModeObserver);
        Vibrator vibrator = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
        mHasVibrator = vibrator != null && vibrator.hasVibrator();

        mShowSilentToggle = SHOW_SILENT_TOGGLE && !mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_useFixedVolume);

        mEmergencyAffordanceManager = new EmergencyAffordanceManager(context);
    }

    /**
     * Show the global actions dialog (creating if necessary)
     * @param keyguardShowing True if keyguard is showing
     */
    public void showDialog(boolean keyguardShowing, boolean isDeviceProvisioned) {
        mKeyguardShowing = keyguardShowing;
        mDeviceProvisioned = isDeviceProvisioned;
        if (mDialog != null) {
            mDialog.dismiss();
            mDialog = null;
            // Show delayed, so that the dismiss of the previous dialog completes
            mHandler.sendEmptyMessage(MESSAGE_SHOW);
        } else {
            handleShow();
                        updateMemoryAndCpuUsage();  // Ensure memory update starts when showing the dialog
        }
    }
	
    /**
     * Helper to enable immersive mode on the global actions dialog.
     */
    private void enableImmersiveModeForDialog(ActionsDialog dialog) {
        if (dialog != null && dialog.getWindow() != null) {
            final View decorView = dialog.getWindow().getDecorView();
            decorView.setSystemUiVisibility(
                  View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
            decorView.setOnSystemUiVisibilityChangeListener(new View.OnSystemUiVisibilityChangeListener() {
                @Override
                public void onSystemUiVisibilityChange(int visibility) {
                    // If the system bars become visible, reapply immersive mode.
                    if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                        decorView.setSystemUiVisibility(
                              View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
                    }
                }
            });
        }
    }

    private void awakenIfNecessary() {
        if (mDreamManager != null) {
            try {
                if (mDreamManager.isDreaming()) {
                    mDreamManager.awaken();
                }
            } catch (RemoteException e) {
                // we tried
            }
        }
    }

    /**
     * Modified method to show the global actions dialog in immersive mode,
     * blur it, and size it to 50% width in landscape (full width in portrait),
     * re-applying on rotation/layout changes.
     */
    private void handleShow() {
        // GammaOS: whenever LegacyGlobalActions is invoked, ensure shaders are disabled
        try {
            SystemProperties.set("persist.gammaos.shader.enable", "0");
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "GammaOS shader disabled via persist.gammaos.shader.enable=0");
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to disable GammaOS shader on GlobalActions show", e);
        }
        awakenIfNecessary();
        mBrightnessPct = -1; // reset so create() reads fresh from DisplayManager
        mDialog = createDialog();
        prepareDialog();

        // If there's only one simple action, just fire it
        if (mAdapter.getCount() == 1
                && mAdapter.getItem(0) instanceof SinglePressAction
                && !(mAdapter.getItem(0) instanceof LongPressAction)) {
            ((SinglePressAction) mAdapter.getItem(0)).onPress();
            return;
        }

        // Otherwise show the full dialog
        if (mDialog == null) return;
        Window w = mDialog.getWindow();

        // 1) Set title on the window
        WindowManager.LayoutParams attrs = w.getAttributes();
        attrs.setTitle("LegacyGlobalActions");
        w.setAttributes(attrs);

        // 2) Show it
        mDialog.show();

        // 3) Tidy up the ListView (transparent, no dividers)
        ListView list = mDialog.getListView();
        list.setBackgroundColor(Color.TRANSPARENT);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setPadding(0, 0, 0, 0);

        // 4) Android 12+ cross-window blur
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            w.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.setBlurBehindRadius(20);
            lp.dimAmount = 0.1f;
            w.setAttributes(lp);
            w.setBackgroundBlurRadius(50);
        }

        // 5) Size & center the window based on current orientation
        applyAdaptiveWidth(w);

        // 6) Listen for any layout changes (including rotation) and re-apply
        View decor = w.getDecorView();
        decor.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v,
                                       int left, int top, int right, int bottom,
                                       int oldLeft, int oldTop, int oldRight, int oldBottom) {
                // if size really changed, re-apply
                if (right - left != oldRight - oldLeft
                        || bottom - top != oldBottom - oldTop) {
                    applyAdaptiveWidth(w);
                }
            }
        });

        // 7) Finally, immersive mode & start memory updates
        enableImmersiveModeForDialog(mDialog);
        updateMemoryAndCpuUsage();
    }

    /** 
     * Helper: sets the window width to MATCH_PARENT in portrait, 60% in landscape,
     * and always centers it.
     */
    private void applyAdaptiveWidth(Window w) {
        int orientation = mContext.getResources().getConfiguration().orientation;
        // Always size the dialog as a function of the display, not the
        // currently measured content height. This keeps the bottom row
        // stable even when header text (MEM/CPU) changes every update.
        DisplayMetrics dm = new DisplayMetrics();
        w.getWindowManager().getDefaultDisplay().getMetrics(dm);

        int width;
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            // 60% width in landscape so the dialog feels compact
            width = (int) (dm.widthPixels * 0.8f);
        } else {
           // Slight margin left/right in portrait so it is visually framed
            width = (int) (dm.widthPixels * 0.9f);
        }

        // Fix the height to a percentage of the screen so the visible area
        // does not grow/shrink as the header text changes; the ListView can
        // scroll inside this fixed viewport.
        int maxHeight = (int) (dm.heightPixels * 0.9f);
        w.setLayout(width, maxHeight);
        w.setGravity(Gravity.CENTER);
    }

    // === Icon tint helpers (force all icons/compound drawables to white) ===
    private void tintAllDrawablesWhite(View root) {
        if (root == null) return;

        if (root instanceof ImageView) {
            ImageView iv = (ImageView) root;
            // Prefer framework tint (handles states) then hard-tint the underlying drawable.
            iv.setImageTintList(ColorStateList.valueOf(Color.WHITE));
            Drawable d = iv.getDrawable();
            if (d != null) {
                d = d.mutate();
                d.setTint(Color.WHITE);
                // Fallback for OEM drawables that ignore setTint.
                d.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
                iv.setImageDrawable(d);
            }
        }

        if (root instanceof TextView) {
            TextView tv = (TextView) root;
            // Absolute compound drawables
            Drawable[] abs = tv.getCompoundDrawables();
            if (abs != null) {
                for (int i = 0; i < abs.length; i++) {
                    abs[i] = tintDrawableWhite(abs[i]);
                }
                tv.setCompoundDrawablesWithIntrinsicBounds(abs[0], abs[1], abs[2], abs[3]);
            }
            // Relative compound drawables (start/end)
            Drawable[] rel = tv.getCompoundDrawablesRelative();
            if (rel != null) {
                for (int i = 0; i < rel.length; i++) {
                    rel[i] = tintDrawableWhite(rel[i]);
                }
                tv.setCompoundDrawablesRelativeWithIntrinsicBounds(rel[0], rel[1], rel[2], rel[3]);
            }
        }

        if (root instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) root;
            for (int i = 0; i < vg.getChildCount(); i++) {
                tintAllDrawablesWhite(vg.getChildAt(i));
            }
        }
    }

    private Drawable tintDrawableWhite(Drawable d) {
        if (d == null) return null;
        try {
            d = d.mutate();
            d.setTint(Color.WHITE);
            d.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
        } catch (Throwable t) {
            try {
                d.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
            } catch (Throwable ignored) {
                // No-op. Better to show the icon untinted than crash Global Actions.
            }
        }
        return d;
    }

    /**
     * Create the global actions dialog.
     * @return A new dialog.
     */
    private ActionsDialog createDialog() {

        LayoutInflater inflater = LayoutInflater.from(mContext);
        headerView = inflater.inflate(R.layout.header_battery_status, null, false);

        // Ensure the header texts do not change their height as values update.
        // If MEM/CPU strings wrap between 1 and 2 lines, the dialog content
        // height changes and the bottom list items appear to "jump".
        try {
            TextView memoryText = headerView.findViewById(R.id.memory_usage);
            if (memoryText != null) {
                memoryText.setSingleLine(true);
                memoryText.setEllipsize(TextUtils.TruncateAt.END);
            }
            TextView cpuText = headerView.findViewById(R.id.cpu_usage);
            if (cpuText != null) {
                cpuText.setSingleLine(true);
                cpuText.setEllipsize(TextUtils.TruncateAt.END);
            }
            TextView batteryText = headerView.findViewById(R.id.battery_percentage);
            if (batteryText != null) {
                batteryText.setSingleLine(true);
                batteryText.setEllipsize(TextUtils.TruncateAt.END);
            }
        } catch (Throwable t) {
            Log.w(TAG, "Failed to enforce single-line header text; continuing", t);
        }

        // Ensure any icons present in the header are white too.
        tintAllDrawablesWhite(headerView);
        updateBatteryStatus(); // Initial update
            updateMemoryAndCpuUsage();   // Start memory update

        // Simple toggle style if there's no vibrator, otherwise use a tri-state
        if (!mHasVibrator) {
            mSilentModeAction = new SilentModeToggleAction();
        } else {
            mSilentModeAction = new SilentModeTriStateAction(mContext, mAudioManager, mHandler);
        }
        mAirplaneModeOn = new ToggleAction(
                R.drawable.ic_lock_airplane_mode,
                R.drawable.ic_lock_airplane_mode_off,
                R.string.global_actions_toggle_airplane_mode,
                R.string.global_actions_airplane_mode_on_status,
                R.string.global_actions_airplane_mode_off_status) {

            @Override
            public void onToggle(boolean on) {
                if (mHasTelephony && TelephonyProperties.in_ecm_mode().orElse(false)) {
                    mIsWaitingForEcmExit = true;
                    // Launch ECM exit dialog
                    Intent ecmDialogIntent =
                            new Intent(TelephonyManager.ACTION_SHOW_NOTICE_ECM_BLOCK_OTHERS, null);
                    ecmDialogIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    mContext.startActivity(ecmDialogIntent);
                } else {
                    changeAirplaneModeSystemSetting(on);
                }
            }

            @Override
            protected void changeStateFromPress(boolean buttonOn) {
                if (!mHasTelephony) return;

                // In ECM mode airplane state cannot be changed
                if (!TelephonyProperties.in_ecm_mode().orElse(false)) {
                    mState = buttonOn ? State.TurningOn : State.TurningOff;
                    mAirplaneState = mState;
                }
            }

            @Override
            public boolean showDuringKeyguard() {
                return true;
            }

            @Override
            public boolean showBeforeProvisioning() {
                return false;
            }
        };
        onAirplaneModeChanged();

        mItems = new ArrayList<Action>();
        String[] defaultActions = mContext.getResources().getStringArray(
                com.android.internal.R.array.config_globalActionsList);

        ArraySet<String> addedKeys = new ArraySet<String>();
        for (int i = 0; i < defaultActions.length; i++) {
            String actionKey = defaultActions[i];
            if (addedKeys.contains(actionKey)) {
                // If we already have added this, don't add it again.
                continue;
            }
            if (GLOBAL_ACTION_KEY_POWER.equals(actionKey)) {
                mItems.add(new PowerAction(mContext, mWindowManagerFuncs));
            } else if (GLOBAL_ACTION_KEY_AIRPLANE.equals(actionKey)) {
                mItems.add(mAirplaneModeOn);
            } else if (GLOBAL_ACTION_KEY_BUGREPORT.equals(actionKey)) {
                if (Settings.Global.getInt(mContext.getContentResolver(),
                        Settings.Global.BUGREPORT_IN_POWER_MENU, 0) != 0 && isCurrentUserOwner()) {
                    mItems.add(new BugReportAction());
                }
            } else if (GLOBAL_ACTION_KEY_SILENT.equals(actionKey)) {
                if (mShowSilentToggle) {
                    mItems.add(mSilentModeAction);
                }
            } else if (GLOBAL_ACTION_KEY_USERS.equals(actionKey)) {
                if (SystemProperties.getBoolean("fw.power_user_switcher", false)) {
                    addUsersToMenu(mItems);
                }
            } else if (GLOBAL_ACTION_KEY_VOICEASSIST.equals(actionKey)) {
                mItems.add(getVoiceAssistAction());
            } else if (GLOBAL_ACTION_KEY_ASSIST.equals(actionKey)) {
                mItems.add(getAssistAction());
            } else if (GLOBAL_ACTION_KEY_RESTART.equals(actionKey)) {
                mItems.add(new RestartAction(mContext, mWindowManagerFuncs));
            } else {
                Log.e(TAG, "Invalid global action key " + actionKey);
            }
            // Add here so we don't add more than one.
            addedKeys.add(actionKey);
        }

        if (mEmergencyAffordanceManager.needsEmergencyAffordance()) {
            mItems.add(getEmergencyAction());
        }

        // GammaOS - Add our own shortcuts
        boolean isNanoMode = "1".equals(SystemProperties.get("sys.gammaos.minimal_boot", "0"));
        // On ATV builds surface the brightness slider + performance + controller shortcuts in the
        // power menu regardless of nano mode (these used to be nano-only). The controller entry
        // drives the same persist.gammaos.gamepad.* props as the quick settings tiles.
        // NOTE: FEATURE_LEANBACK reads false in system_server on this GammaOS ATV GSI (it is listed
        // by pm but hasSystemFeature returns false), so gate on the TV build identity
        // (ro.build.characteristics=tv) with the still-working television feature as a fallback.
        boolean isAtv = SystemProperties.get("ro.build.characteristics", "").contains("tv")
                || mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEVISION);
        if (isAtv) {
            mItems.add(0, getBrightnessAction());
            mBrightnessItemPosition = 0;
            mItems.add(getPerformanceAction());
            mItems.add(getControllerAction());
        } else {
            mBrightnessItemPosition = -1;
        }
        if (isNanoMode) {
            mItems.add(getKillForegroundAppAction());
        } else {
            mItems.add(getKillForegroundAppAction());
            mItems.add(getSettingsAction());
            mItems.add(getKillBackgroundAppsAction());
            mItems.add(getKillAllAppsAction());
            mItems.add(getBootNanoAction());
        }

        // Override ActionsAdapter's getView method to set text color to white
        mAdapter = new ActionsAdapter(mContext, mItems,
                () -> mDeviceProvisioned, () -> mKeyguardShowing) {

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                // Get the default view for the item
                View view = super.getView(position, convertView, parent);

                // Traverse view hierarchy to find the TextView
                if (view instanceof ViewGroup) {
                    findAndSetTextColorWhite((ViewGroup) view);
                }
                // Force all icons/drawables (ImageView + TextView compound icons) to white
                if (view != null) {
                    LegacyGlobalActions.this.tintAllDrawablesWhite(view);
                }

                return view;
            }

            private void findAndSetTextColorWhite(ViewGroup viewGroup) {
                for (int i = 0; i < viewGroup.getChildCount(); i++) {
                    View child = viewGroup.getChildAt(i);
                    if (child instanceof TextView) {
                        ((TextView) child).setTextColor(Color.WHITE); // Set text color to white
                    } else if (child instanceof ViewGroup) {
                        findAndSetTextColorWhite((ViewGroup) child); // Recursively search for TextView
                    }
                }
            }
        };

        AlertController.AlertParams params = new AlertController.AlertParams(mContext);
        params.mAdapter = mAdapter;
        params.mOnClickListener = this;
        params.mForceInverseBackground = true;
        params.mCustomTitleView = headerView; // Set custom header

        ActionsDialog dialog = new ActionsDialog(mContext, params);
        dialog.setCanceledOnTouchOutside(false); // Handled by the custom class.

        ListView listView = dialog.getListView();
        listView.setItemsCanFocus(true);
        listView.setLongClickable(true);
        // Use a lighter selector for focused/selected items so it doesn't clash with white text
        android.graphics.drawable.StateListDrawable selector = new android.graphics.drawable.StateListDrawable();
        android.graphics.drawable.GradientDrawable focusedBg = new android.graphics.drawable.GradientDrawable();
        focusedBg.setColor(Color.parseColor("#FF3A5FCD")); // Blue highlight
        focusedBg.setCornerRadius(8);
        selector.addState(new int[]{android.R.attr.state_focused}, focusedBg);
        selector.addState(new int[]{android.R.attr.state_pressed}, focusedBg);
        android.graphics.drawable.GradientDrawable selectedBg = new android.graphics.drawable.GradientDrawable();
        selectedBg.setColor(Color.parseColor("#FF3A5FCD"));
        selectedBg.setCornerRadius(8);
        selector.addState(new int[]{android.R.attr.state_selected}, selectedBg);
        selector.addState(new int[]{}, new ColorDrawable(Color.TRANSPARENT));
        listView.setSelector(selector);
        // GammaOS Nano: store ListView ref and intercept DPAD left/right on brightness slider row
        mBrightnessListView = listView;
        if (mBrightnessItemPosition == 0) {
            dialog.setOnKeyListener((dlg, keyCode, event) -> {
                if (event.getAction() != android.view.KeyEvent.ACTION_DOWN) return false;
                int sel = listView.getSelectedItemPosition();
                if (sel != 0) return false; // brightness is at position 0
                if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) {
                    return handleBrightnessDpad(1);
                } else if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT) {
                    return handleBrightnessDpad(-1);
                }
                return false;
            });
        }
        listView.setOnItemLongClickListener(
                new AdapterView.OnItemLongClickListener() {
                    @Override
                    public boolean onItemLongClick(AdapterView<?> parent, View view, int position,
                            long id) {
                        final Action action = mAdapter.getItem(position);
                        if (action instanceof LongPressAction) {
                            return ((LongPressAction) action).onLongPress();
                        }
                        return false;
                    }
        });
        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG);
        // Don't acquire soft keyboard focus, to avoid destroying state when capturing bug reports
        dialog.getWindow().setFlags(FLAG_ALT_FOCUSABLE_IM, FLAG_ALT_FOCUSABLE_IM);

        // Define rounded corners
        float[] outerRadii = new float[] {16, 16, 16, 16, 16, 16, 16, 16}; // Set corner radius
        RoundRectShape roundedRect = new RoundRectShape(outerRadii, null, null);
        ShapeDrawable shapeDrawable = new ShapeDrawable(roundedRect);
        shapeDrawable.getPaint().setColor(Color.parseColor("#FA111111")); // Transparent black
        shapeDrawable.getPaint().setStyle(Paint.Style.FILL);

        // Apply the rounded background
        dialog.getWindow().setBackgroundDrawable(shapeDrawable);

        dialog.setOnDismissListener(this);

        return dialog;
    }

    private class BugReportAction extends SinglePressAction implements LongPressAction {

        public BugReportAction() {
            super(com.android.internal.R.drawable.ic_lock_bugreport, R.string.bugreport_title);
        }

        @Override
        public void onPress() {
            // don't actually trigger the bugreport if we are running stability
            // tests via monkey
            if (ActivityManager.isUserAMonkey()) {
                return;
            }
            // Add a little delay before executing, to give the
            // dialog a chance to go away before it takes a
            // screenshot.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    try {
                        // Take an "interactive" bugreport.
                        MetricsLogger.action(mContext,
                                MetricsEvent.ACTION_BUGREPORT_FROM_POWER_MENU_INTERACTIVE);
                        ActivityManager.getService().requestInteractiveBugReport();
                    } catch (RemoteException e) {
                    }
                }
            }, 500);
        }

        @Override
        public boolean onLongPress() {
            // don't actually trigger the bugreport if we are running stability
            // tests via monkey
            if (ActivityManager.isUserAMonkey()) {
                return false;
            }
            try {
                // Take a "full" bugreport.
                MetricsLogger.action(mContext, MetricsEvent.ACTION_BUGREPORT_FROM_POWER_MENU_FULL);
                ActivityManager.getService().requestFullBugReport();
            } catch (RemoteException e) {
            }
            return false;
        }

        @Override
        public boolean showDuringKeyguard() {
            return true;
        }

        @Override
        public boolean showBeforeProvisioning() {
            return false;
        }

        @Override
        public String getStatus() {
            return mContext.getString(
                    com.android.internal.R.string.bugreport_status,
                    Build.VERSION.RELEASE_OR_CODENAME,
                    Build.ID);
        }
    }

    private Action getSettingsAction() {
        return new SinglePressAction(com.android.internal.R.drawable.ic_settings,
                R.string.global_action_settings) {

            @Override
            public void onPress() {
                Intent intent = new Intent(Settings.ACTION_SETTINGS);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                mContext.startActivity(intent);
            }

            @Override
            public boolean showDuringKeyguard() {
                return true;
            }

            @Override
            public boolean showBeforeProvisioning() {
                return true;
            }
        };
    }

    private Action getHomeAction() {
        return new SinglePressAction(com.android.internal.R.drawable.ic_menu,
                R.string.accessibility_system_action_home_label) {

            @Override
            public void onPress() {
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.addCategory(Intent.CATEGORY_HOME);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                mContext.startActivity(intent);
            }

            @Override
            public boolean showDuringKeyguard() {
                return true;
            }

            @Override
            public boolean showBeforeProvisioning() {
                return true;
            }
        };
    }


    // Brightness slider state (held for DPAD key updates from ListView)
    private android.widget.ProgressBar mBrightnessPb;
    private android.widget.TextView mBrightnessTv;
    private ListView mBrightnessListView;
    private int mBrightnessPct = -1; // tracked pct to avoid dm.getBrightness() race

    private Action getBrightnessAction() {
        return new Action() {
            @Override
            public CharSequence getLabelForAccessibility(Context context) {
                return context.getString(R.string.gammaos_brightness_settings);
            }

            @Override
            public View create(Context context, View convertView, ViewGroup parent,
                    LayoutInflater inflater) {
                int pct;
                if (mBrightnessPct >= 0) {
                    pct = mBrightnessPct;
                } else {
                    final DisplayManager dm = (DisplayManager)
                            context.getSystemService(Context.DISPLAY_SERVICE);
                    float cur = 0.5f;
                    if (dm != null) {
                        float b = dm.getBrightness(Display.DEFAULT_DISPLAY);
                        if (!Float.isNaN(b) && b >= 0) cur = b;
                    }
                    pct = (int) (cur * 100);
                    mBrightnessPct = pct;
                }
                float density = context.getResources().getDisplayMetrics().density;

                // Match global_actions_item.xml: paddingStart=8dp, paddingEnd=16dp,
                // paddingTop/Bottom=6dp, minHeight=listPreferredItemHeight
                android.widget.LinearLayout ll = new android.widget.LinearLayout(context);
                ll.setOrientation(android.widget.LinearLayout.HORIZONTAL);
                ll.setGravity(Gravity.CENTER_VERTICAL);
                ll.setPadding((int) (8 * density), (int) (6 * density),
                        (int) (16 * density), (int) (6 * density));
                ll.setMinimumHeight((int) (64 * density));

                // Sun icon — 56dp container (matching icon ImageView in standard items)
                // with scaleType center so the 24dp icon is centered within 56dp
                ImageView icon = new ImageView(context);
                icon.setImageResource(R.drawable.ic_gammaos_brightness);
                icon.setColorFilter(0xFFFFFFFF, PorterDuff.Mode.SRC_IN);
                icon.setScaleType(ImageView.ScaleType.CENTER);
                android.widget.LinearLayout.LayoutParams iconLp =
                        new android.widget.LinearLayout.LayoutParams(
                                (int) (56 * density), (int) (56 * density));
                iconLp.setMarginEnd((int) (8 * density));
                ll.addView(icon, iconLp);

                // Progress bar
                mBrightnessPb = new android.widget.ProgressBar(context,
                        null, android.R.attr.progressBarStyleHorizontal);
                mBrightnessPb.setMax(100);
                mBrightnessPb.setProgress(pct);
                mBrightnessPb.setProgressTintList(
                        android.content.res.ColorStateList.valueOf(0xFFFFFFFF));
                mBrightnessPb.setProgressBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(0x40FFFFFF));
                android.widget.LinearLayout.LayoutParams pbLp =
                        new android.widget.LinearLayout.LayoutParams(
                                0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
                pbLp.setMarginEnd((int) (12 * density));
                ll.addView(mBrightnessPb, pbLp);

                // Percentage text
                mBrightnessTv = new android.widget.TextView(context);
                mBrightnessTv.setTextColor(Color.WHITE);
                mBrightnessTv.setTextSize(14);
                mBrightnessTv.setText(pct + "%");
                mBrightnessTv.setMinWidth((int) (40 * density));
                ll.addView(mBrightnessTv);

                return ll;
            }

            @Override
            public void onPress() {
                // Inline slider — no action on press
            }

            @Override
            public boolean showDuringKeyguard() { return true; }
            @Override
            public boolean showBeforeProvisioning() { return true; }
            @Override
            public boolean isEnabled() { return true; }
        };
    }

    /** Adjust brightness slider from DPAD left/right when brightness row is selected. */
    private boolean handleBrightnessDpad(int direction) {
        if (mBrightnessPct < 0) mBrightnessPct = 50;
        DisplayManager dm = (DisplayManager) mContext.getSystemService(Context.DISPLAY_SERVICE);
        if (dm == null) return false;
        int newPct = Math.max(1, Math.min(100, mBrightnessPct + direction * 5));
        mBrightnessPct = newPct;
        float brightness = newPct / 100f;
        final long token = Binder.clearCallingIdentity();
        try {
            dm.setBrightness(Display.DEFAULT_DISPLAY, brightness);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
        // Force ListView to rebuild views via adapter — create() will use mBrightnessPct.
        // Save/restore selection so notifyDataSetChanged doesn't reset it.
        if (mAdapter != null && mBrightnessListView != null) {
            int sel = mBrightnessListView.getSelectedItemPosition();
            mAdapter.notifyDataSetChanged();
            if (sel >= 0) {
                mBrightnessListView.setSelection(sel);
            }
        }
        return true;
    }

    private void applyDarkDialogTheme(AlertDialog dialog) {
        Window w = dialog.getWindow();
        if (w == null) return;
        float[] outerRadii = new float[] {16, 16, 16, 16, 16, 16, 16, 16};
        RoundRectShape roundedRect = new RoundRectShape(outerRadii, null, null);
        ShapeDrawable bg = new ShapeDrawable(roundedRect);
        bg.getPaint().setColor(Color.parseColor("#FA111111"));
        bg.getPaint().setStyle(Paint.Style.FILL);
        w.setBackgroundDrawable(bg);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            w.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.setBlurBehindRadius(20);
            lp.dimAmount = 0.1f;
            w.setAttributes(lp);
            w.setBackgroundBlurRadius(50);
        }
        // Style title white
        int titleId = mContext.getResources().getIdentifier("alertTitle", "id", "android");
        if (titleId != 0) {
            TextView title = dialog.findViewById(titleId);
            if (title != null) title.setTextColor(Color.WHITE);
        }
        // Style buttons white
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.WHITE);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.WHITE);
    }

    private Action getPerformanceAction() {
        return new SinglePressAction(R.drawable.ic_gammaos_performance,
                R.string.gammaos_performance_mode) {

            @Override
            public void onPress() {
                if (mDialog != null && mDialog.isShowing()) {
                    mDialog.dismiss();
                }
                mHandler.post(() -> showPerformanceDialog());
            }

            @Override
            public boolean showDuringKeyguard() {
                return true;
            }

            @Override
            public boolean showBeforeProvisioning() {
                return true;
            }
        };
    }

    private void showPerformanceDialog() {
        String current = SystemProperties.get("persist.gammaos.performance_mode", "stock");
        final String[] modes = {"stock", "max", "powersave"};
        final String[] labels = {"Normal", "Max Performance", "Power Saver"};
        int checkedItem = 0;
        for (int i = 0; i < modes.length; i++) {
            if (modes[i].equals(current)) {
                checkedItem = i;
                break;
            }
        }

        AlertDialog dialog = new AlertDialog.Builder(mContext, android.R.style.Theme_Material_Dialog)
                .setTitle(R.string.gammaos_performance_mode)
                .setSingleChoiceItems(labels, checkedItem, (dlg, which) -> {
                    final String mode = modes[which];
                    final long token = Binder.clearCallingIdentity();
                    try {
                        SystemProperties.set("persist.gammaos.performance_mode", mode);
                    } finally {
                        Binder.restoreCallingIdentity(token);
                    }
                    dlg.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG);
        dialog.show();
        applyDarkDialogTheme(dialog);
        // Style radio button text white
        ListView lv = dialog.getListView();
        if (lv != null) {
            lv.setBackgroundColor(Color.TRANSPARENT);
            for (int i = 0; i < lv.getChildCount(); i++) {
                View child = lv.getChildAt(i);
                if (child instanceof android.widget.CheckedTextView) {
                    ((android.widget.CheckedTextView) child).setTextColor(Color.WHITE);
                }
            }
            // Post to ensure items are laid out
            lv.post(() -> {
                for (int i = 0; i < lv.getChildCount(); i++) {
                    View child = lv.getChildAt(i);
                    if (child instanceof android.widget.CheckedTextView) {
                        ((android.widget.CheckedTextView) child).setTextColor(Color.WHITE);
                    }
                }
            });
        }
    }

    private Action getControllerAction() {
        return new SinglePressAction(R.drawable.ic_gammaos_controller,
                R.string.gammaos_controller_options) {

            @Override
            public void onPress() {
                if (mDialog != null && mDialog.isShowing()) {
                    mDialog.dismiss();
                }
                mHandler.post(() -> showControllerDialog());
            }

            @Override
            public boolean showDuringKeyguard() {
                return true;
            }

            @Override
            public boolean showBeforeProvisioning() {
                return true;
            }
        };
    }

    // GammaPad controller toggles. Each row drives the SAME persist.gammaos.gamepad.* prop as its
    // quick settings tile, and a change bumps config_version so GammaPad reloads the mapping live.
    private void showControllerDialog() {
        final String[] labels = {
                mContext.getString(R.string.gammaos_ctrl_abxy_swap),
                mContext.getString(R.string.gammaos_ctrl_dpad_analog_swap),
                mContext.getString(R.string.gammaos_ctrl_invert_left),
                mContext.getString(R.string.gammaos_ctrl_invert_right),
        };
        final boolean[] checked = {
                SystemProperties.getInt("persist.gammaos.gamepad.abxy_swap", 0) != 0,
                SystemProperties.getInt("persist.gammaos.gamepad.analog_to_dpad", 0) != 0,
                SystemProperties.getInt("persist.gammaos.gamepad.invert_left", 0) != 0,
                SystemProperties.getInt("persist.gammaos.gamepad.invert_right", 0) != 0,
        };

        AlertDialog dialog = new AlertDialog.Builder(mContext, android.R.style.Theme_Material_Dialog)
                .setTitle(R.string.gammaos_controller_options)
                .setMultiChoiceItems(labels, checked, (dlg, which, isChecked) -> {
                    final String val = isChecked ? "1" : "0";
                    final long token = Binder.clearCallingIdentity();
                    try {
                        switch (which) {
                            case 0:
                                SystemProperties.set("persist.gammaos.gamepad.abxy_swap", val);
                                break;
                            case 1:
                                // Mirror DpadAnalogToggleTile: both directions move together.
                                SystemProperties.set("persist.gammaos.gamepad.analog_to_dpad", val);
                                SystemProperties.set("persist.gammaos.gamepad.dpad_to_analog", val);
                                break;
                            case 2:
                                SystemProperties.set("persist.gammaos.gamepad.invert_left", val);
                                break;
                            case 3:
                                SystemProperties.set("persist.gammaos.gamepad.invert_right", val);
                                break;
                        }
                        bumpGamepadConfigVersion();
                    } finally {
                        Binder.restoreCallingIdentity(token);
                    }
                })
                .setPositiveButton(android.R.string.ok, null)
                .create();

        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG);
        dialog.show();
        applyDarkDialogTheme(dialog);
        ListView lv = dialog.getListView();
        if (lv != null) {
            lv.setBackgroundColor(Color.TRANSPARENT);
            final Runnable whiten = () -> {
                for (int i = 0; i < lv.getChildCount(); i++) {
                    View child = lv.getChildAt(i);
                    if (child instanceof android.widget.CheckedTextView) {
                        ((android.widget.CheckedTextView) child).setTextColor(Color.WHITE);
                    }
                }
            };
            whiten.run();
            lv.post(whiten);
        }
    }

    private void bumpGamepadConfigVersion() {
        int ver = SystemProperties.getInt("persist.gammaos.gamepad.config_version", 0);
        SystemProperties.set("persist.gammaos.gamepad.config_version", Integer.toString(ver + 1));
    }

    private Action getKillForegroundAppAction() {
        return new SinglePressAction(R.drawable.ic_close, R.string.gammaos_kill_app) {

            @Override
            public void onPress() {
                ActivityManager am = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
                List<ActivityManager.RunningTaskInfo> taskInfo = am.getRunningTasks(1); // Get the top (foreground) task

                if (taskInfo != null && !taskInfo.isEmpty()) {
                    String foregroundProcess = taskInfo.get(0).topActivity.getPackageName(); // Get the package name of the top activity
                    try {
                        am.forceStopPackage(foregroundProcess);
                        Toast.makeText(mContext, "Closed app: " + foregroundProcess, Toast.LENGTH_SHORT).show();

                        // Set sys.mem_clear=1 and then reset to 0 after 1 second
                        setMemoryClearProp();

                        // In nano mode, trigger return to nano menu after kill
                        if ("1".equals(SystemProperties.get("sys.gammaos.minimal_boot", "0"))
                                && "1".equals(SystemProperties.get(
                                        "sys.gammaos.nano.app_launched", "0"))) {
                            SystemProperties.set("sys.gammaos.nano.app_launched", "0");
                            SystemProperties.set("sys.gammaos.nano.launch_app",
                                    "com.retroarch.aarch64");
                            SystemProperties.set("sys.gammaos.nano.drop_input", "0");
                            SystemProperties.set("sys.gammaos.nano.restart", "1");
                        }

                    } catch (Exception e) {
                        Toast.makeText(mContext, "Close app error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                } else {
                    Toast.makeText(mContext, "No foreground app found", Toast.LENGTH_SHORT).show();
                }
            }

            private void setMemoryClearProp() {
                try {
                    // Set sys.mem_clear to 1
                    SystemProperties.set("sys.mem_clear", "1");
                    Log.d(TAG, "sys.mem_clear set to 1");

                    // Reset sys.mem_clear to 0 after 1 second
                    mHandler.postDelayed(() -> {
                        SystemProperties.set("sys.mem_clear", "0");
                        Log.d(TAG, "sys.mem_clear reset to 0");
                    }, 1000); // Delay of 1 second
                } catch (Exception e) {
                    Log.e(TAG, "Failed to set/reset sys.mem_clear", e);
                }
            }

            @Override
            public boolean showDuringKeyguard() {
                return true;
            }

            @Override
            public boolean showBeforeProvisioning() {
                return true;
            }
        };
    }

    private Action getKillBackgroundAppsAction() {
        return new SinglePressAction(R.drawable.ic_close, R.string.gammaos_kill_all_background_apps) {

            @Override
            public void onPress() {
                ActivityManager am = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
                PackageManager pm = mContext.getPackageManager();

                // Get the foreground app
                List<ActivityManager.RunningTaskInfo> taskInfo = am.getRunningTasks(1);
                String foregroundProcess = null;
                if (taskInfo != null && !taskInfo.isEmpty()) {
                    foregroundProcess = taskInfo.get(0).topActivity.getPackageName(); // Get foreground app package name
                }

                List<ActivityManager.RunningAppProcessInfo> runningAppProcesses = am.getRunningAppProcesses();

                if (runningAppProcesses != null && !runningAppProcesses.isEmpty()) {
                    for (ActivityManager.RunningAppProcessInfo processInfo : runningAppProcesses) {
                        String packageName = processInfo.processName;

                        // Skip the foreground app and system apps
                        try {
                            ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);

                            if (!packageName.equals(foregroundProcess) && (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
                                // Force stop the background app
                                am.forceStopPackage(packageName);
                            }
                        } catch (PackageManager.NameNotFoundException e) {
                            // Ignore any packages that cannot be found
                        } catch (Exception e) {
                            Toast.makeText(mContext, "Failed to kill app: " + packageName + " due to: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    }
                    Toast.makeText(mContext, "All background apps have been killed", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(mContext, "No running background apps found", Toast.LENGTH_SHORT).show();
                }
            }

            public boolean onLongPress() {
                return false;
            }

            @Override
            public boolean showDuringKeyguard() {
                return true;
            }

            @Override
            public boolean showBeforeProvisioning() {
                return true;
            }
        };
    }

    private Action getKillAllAppsAction() {
        return new SinglePressAction(R.drawable.ic_close, R.string.gammaos_kill_all_apps) {

            @Override
            public void onPress() {
                ActivityManager am = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
                PackageManager pm = mContext.getPackageManager();
                List<ActivityManager.RunningAppProcessInfo> runningAppProcesses = am.getRunningAppProcesses();

                if (runningAppProcesses != null && !runningAppProcesses.isEmpty()) {
                    for (ActivityManager.RunningAppProcessInfo processInfo : runningAppProcesses) {
                        String packageName = processInfo.processName;

                        try {
                            ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);

                            // Skip system apps and the current package
                            if ((appInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
                                // Force stop the package
                                am.forceStopPackage(packageName);
                            }
                        } catch (PackageManager.NameNotFoundException e) {
                            // Ignore any packages that cannot be found
                        } catch (Exception e) {
                            Toast.makeText(mContext, "Failed to kill app: " + packageName + " due to: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    }
                    Toast.makeText(mContext, "All apps have been killed", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(mContext, "No running apps found", Toast.LENGTH_SHORT).show();
                }
            }

            public boolean onLongPress() {
                return false;
            }

            @Override
            public boolean showDuringKeyguard() {
                return true;
            }

            @Override
            public boolean showBeforeProvisioning() {
                return true;
            }
        };
    }

    private Action getBootNanoAction() {
        return new SinglePressAction(R.drawable.ic_restart, R.string.gammaos_boot_nano) {

            @Override
            public void onPress() {
                // Clear the skip flag so init.rc takes the nano preload path
                SystemProperties.set("persist.bootanim.skip_nano", "0");
                // Reboot into nano mode
                mWindowManagerFuncs.reboot(false /* confirm */);
            }

            @Override
            public boolean showDuringKeyguard() {
                return true;
            }

            @Override
            public boolean showBeforeProvisioning() {
                return true;
            }
        };
    }

    private Action getEmergencyAction() {
        return new SinglePressAction(com.android.internal.R.drawable.emergency_icon,
                R.string.global_action_emergency) {
            @Override
            public void onPress() {
                mEmergencyAffordanceManager.performEmergencyCall();
            }

            @Override
            public boolean showDuringKeyguard() {
                return true;
            }

            @Override
            public boolean showBeforeProvisioning() {
                return true;
            }
        };
    }

    private Action getAssistAction() {
        return new SinglePressAction(com.android.internal.R.drawable.ic_action_assist_focused,
                R.string.global_action_assist) {
            @Override
            public void onPress() {
                Intent intent = new Intent(Intent.ACTION_ASSIST);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                mContext.startActivity(intent);
            }

            @Override
            public boolean showDuringKeyguard() {
                return true;
            }

            @Override
            public boolean showBeforeProvisioning() {
                return true;
            }
        };
    }

    private Action getVoiceAssistAction() {
        return new SinglePressAction(com.android.internal.R.drawable.ic_voice_search,
                R.string.global_action_voice_assist) {
            @Override
            public void onPress() {
                Intent intent = new Intent(Intent.ACTION_VOICE_ASSIST);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                mContext.startActivity(intent);
            }

            @Override
            public boolean showDuringKeyguard() {
                return true;
            }

            @Override
            public boolean showBeforeProvisioning() {
                return true;
            }
        };
    }

    private Action getLockdownAction() {
        return new SinglePressAction(com.android.internal.R.drawable.ic_lock_lock,
                R.string.global_action_lockdown) {

            @Override
            public void onPress() {
                new LockPatternUtils(mContext).requireCredentialEntry(UserHandle.USER_ALL);
                try {
                    WindowManagerGlobal.getWindowManagerService().lockNow(null);
                } catch (RemoteException e) {
                    Log.e(TAG, "Error while trying to lock device.", e);
                }
            }

            @Override
            public boolean showDuringKeyguard() {
                return true;
            }

            @Override
            public boolean showBeforeProvisioning() {
                return false;
            }
        };
    }

    private UserInfo getCurrentUser() {
        try {
            return ActivityManager.getService().getCurrentUser();
        } catch (RemoteException re) {
            return null;
        }
    }

    private boolean isCurrentUserOwner() {
        UserInfo currentUser = getCurrentUser();
        return currentUser == null || currentUser.isPrimary();
    }

    private void addUsersToMenu(ArrayList<Action> items) {
        UserManager um = (UserManager) mContext.getSystemService(Context.USER_SERVICE);
        if (um.isUserSwitcherEnabled()) {
            List<UserInfo> users = um.getUsers();
            UserInfo currentUser = getCurrentUser();
            for (final UserInfo user : users) {
                if (user.supportsSwitchToByUser()) {
                    boolean isCurrentUser = currentUser == null
                            ? user.id == 0 : (currentUser.id == user.id);
                    Drawable icon = user.iconPath != null ? Drawable.createFromPath(user.iconPath)
                            : null;
                    SinglePressAction switchToUser = new SinglePressAction(
                            com.android.internal.R.drawable.ic_menu_cc, icon,
                            (user.name != null ? user.name : "Primary")
                            + (isCurrentUser ? " \u2714" : "")) {
                        @Override
                        public void onPress() {
                            try {
                                ActivityManager.getService().switchUser(user.id);
                            } catch (RemoteException re) {
                                Log.e(TAG, "Couldn't switch user " + re);
                            }
                        }

                        @Override
                        public boolean showDuringKeyguard() {
                            return true;
                        }

                        @Override
                        public boolean showBeforeProvisioning() {
                            return false;
                        }
                    };
                    items.add(switchToUser);
                }
            }
        }
    }

    private void prepareDialog() {
        refreshSilentMode();
        mAirplaneModeOn.updateState(mAirplaneState);
        mAdapter.notifyDataSetChanged();
        mDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG);
        if (mShowSilentToggle) {
            IntentFilter filter = new IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION);
            mContext.registerReceiver(mRingerModeReceiver, filter);
        }
    }

    private void refreshSilentMode() {
        if (!mHasVibrator) {
            final boolean silentModeOn =
                    mAudioManager.getRingerMode() != AudioManager.RINGER_MODE_NORMAL;
            ((ToggleAction)mSilentModeAction).updateState(
                    silentModeOn ? ToggleAction.State.On : ToggleAction.State.Off);
        }
    }

        /** {@inheritDoc} */
        @Override
        public void onDismiss(DialogInterface dialog) {
                if (mOnDismiss != null) {
                        mOnDismiss.run();
                }
                if (mShowSilentToggle) {
                        try {
                                mContext.unregisterReceiver(mRingerModeReceiver);
                        } catch (IllegalArgumentException ie) {
                                // This will catch the exception if the receiver was already unregistered or not registered.
                                Log.w(TAG, "Attempted to unregister the ringer mode receiver that was not registered", ie);
                        }
                }
                try {
                        mContext.unregisterReceiver(batteryInfoReceiver); // Unregister the battery info receiver
                } catch (IllegalArgumentException ie) {
                        // This will catch the exception if the receiver was already unregistered or not registered.
                        Log.w(TAG, "Attempted to unregister the battery info receiver that was not registered", ie);
                }

                if (mMemoryHandler != null && mMemoryUpdateRunnable != null) {
                        mMemoryHandler.removeCallbacks(mMemoryUpdateRunnable);
                }

        }

    /** {@inheritDoc} */
    @Override
    public void onClick(DialogInterface dialog, int which) {
        Action action = mAdapter.getItem(which);
        // Don't dismiss for brightness slider (inline control) or silent mode
        if (!(action instanceof SilentModeTriStateAction)
                && which != mBrightnessItemPosition) {
            dialog.dismiss();
        }
        action.onPress();
    }

    // note: the scheme below made more sense when we were planning on having
    // 8 different things in the global actions dialog.  seems overkill with
    // only 3 items now, but may as well keep this flexible approach so it will
    // be easy should someone decide at the last minute to include something
    // else, such as 'enable wifi', or 'enable bluetooth'

    private class SilentModeToggleAction extends ToggleAction {
        public SilentModeToggleAction() {
            super(R.drawable.ic_audio_vol_mute,
                    R.drawable.ic_audio_vol,
                    R.string.global_action_toggle_silent_mode,
                    R.string.global_action_silent_mode_on_status,
                    R.string.global_action_silent_mode_off_status);
        }

        @Override
        public void onToggle(boolean on) {
            if (on) {
                mAudioManager.setRingerMode(AudioManager.RINGER_MODE_SILENT);
            } else {
                mAudioManager.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
            }
        }

        @Override
        public boolean showDuringKeyguard() {
            return true;
        }

        @Override
        public boolean showBeforeProvisioning() {
            return false;
        }
    }

    private static class SilentModeTriStateAction implements Action, View.OnClickListener {

        private final int[] ITEM_IDS = { R.id.option1, R.id.option2, R.id.option3 };

        private final AudioManager mAudioManager;
        private final Handler mHandler;
        private final Context mContext;

        SilentModeTriStateAction(Context context, AudioManager audioManager, Handler handler) {
            mAudioManager = audioManager;
            mHandler = handler;
            mContext = context;
        }

        private int ringerModeToIndex(int ringerMode) {
            // They just happen to coincide
            return ringerMode;
        }

        private int indexToRingerMode(int index) {
            // They just happen to coincide
            return index;
        }

        @Override
        public CharSequence getLabelForAccessibility(Context context) {
            return null;
        }

        @Override
        public View create(Context context, View convertView, ViewGroup parent,
                LayoutInflater inflater) {
            View v = inflater.inflate(R.layout.global_actions_silent_mode, parent, false);

            int selectedIndex = ringerModeToIndex(mAudioManager.getRingerMode());
            for (int i = 0; i < 3; i++) {
                View itemView = v.findViewById(ITEM_IDS[i]);
                itemView.setSelected(selectedIndex == i);
                // Set up click handler
                itemView.setTag(i);
                itemView.setOnClickListener(this);
            }
            return v;
        }

        @Override
        public void onPress() {
        }

        @Override
        public boolean showDuringKeyguard() {
            return true;
        }

        @Override
        public boolean showBeforeProvisioning() {
            return false;
        }

        @Override
        public boolean isEnabled() {
            return true;
        }

        void willCreate() {
        }

        @Override
        public void onClick(View v) {
            if (!(v.getTag() instanceof Integer)) return;

            int index = (Integer) v.getTag();
            mAudioManager.setRingerMode(indexToRingerMode(index));
            mHandler.sendEmptyMessageDelayed(MESSAGE_DISMISS, DIALOG_DISMISS_DELAY);
        }
    }

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_CLOSE_SYSTEM_DIALOGS.equals(action)
                    || Intent.ACTION_SCREEN_OFF.equals(action)) {
                String reason = intent.getStringExtra(PhoneWindowManager.SYSTEM_DIALOG_REASON_KEY);
                if (!PhoneWindowManager.SYSTEM_DIALOG_REASON_GLOBAL_ACTIONS.equals(reason)) {
                    mHandler.sendEmptyMessage(MESSAGE_DISMISS);
                }
            } else if (TelephonyManager.ACTION_EMERGENCY_CALLBACK_MODE_CHANGED.equals(action)) {
                // Airplane mode can be changed after ECM exits if airplane toggle button
                // is pressed during ECM mode
                if (!(intent.getBooleanExtra(TelephonyManager.EXTRA_PHONE_IN_ECM_STATE, false))
                        && mIsWaitingForEcmExit) {
                    mIsWaitingForEcmExit = false;
                    changeAirplaneModeSystemSetting(true);
                }
            }
        }
    };

    PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
        @Override
        public void onServiceStateChanged(ServiceState serviceState) {
            if (!mHasTelephony) return;
            final boolean inAirplaneMode = serviceState.getState() == ServiceState.STATE_POWER_OFF;
            mAirplaneState = inAirplaneMode ? ToggleAction.State.On : ToggleAction.State.Off;
            mAirplaneModeOn.updateState(mAirplaneState);
            mAdapter.notifyDataSetChanged();
        }
    };

    private BroadcastReceiver mRingerModeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(AudioManager.RINGER_MODE_CHANGED_ACTION)) {
                mHandler.sendEmptyMessage(MESSAGE_REFRESH);
            }
        }
    };

    private ContentObserver mAirplaneModeObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            onAirplaneModeChanged();
        }
    };

    private static final int MESSAGE_DISMISS = 0;
    private static final int MESSAGE_REFRESH = 1;
    private static final int MESSAGE_SHOW = 2;
    private static final int DIALOG_DISMISS_DELAY = 300; // ms

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MESSAGE_DISMISS:
                if (mDialog != null) {
                    mDialog.dismiss();
                    mDialog = null;
                }
                break;
            case MESSAGE_REFRESH:
                refreshSilentMode();
                mAdapter.notifyDataSetChanged();
                break;
            case MESSAGE_SHOW:
                handleShow();
                break;
            }
        }
    };

    private void onAirplaneModeChanged() {
        // Let the service state callbacks handle the state.
        if (mHasTelephony) return;

        boolean airplaneModeOn = Settings.Global.getInt(
                mContext.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON,
                0) == 1;
        mAirplaneState = airplaneModeOn ? ToggleAction.State.On : ToggleAction.State.Off;
        mAirplaneModeOn.updateState(mAirplaneState);
    }

    /**
     * Change the airplane mode system setting
     */
    private void changeAirplaneModeSystemSetting(boolean on) {
        Settings.Global.putInt(
                mContext.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON,
                on ? 1 : 0);
        Intent intent = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        intent.addFlags(Intent.FLAG_RECEIVER_REPLACE_PENDING);
        intent.putExtra("state", on);
        mContext.sendBroadcastAsUser(intent, UserHandle.ALL);
        if (!mHasTelephony) {
            mAirplaneState = on ? ToggleAction.State.On : ToggleAction.State.Off;
        }
    }


    private void updateBatteryStatus() {
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        // Log to confirm registration is happening
        Log.d(TAG, "Registering battery status receiver");
        mContext.registerReceiver(batteryInfoReceiver, filter);
    }

        private void updateMemoryAndCpuUsage() {
                // Define the Runnable to update memory and CPU usage every second
                mMemoryUpdateRunnable = new Runnable() {
                        @Override
                        public void run() {
                                try {
                                        // Get memory information from /proc/meminfo
                                        int totalMem = extractMemoryValue("MemTotal:");
                                        int freeMem = extractMemoryValue("MemFree:");
                                        int buffers = extractMemoryValue("Buffers:");
                                        int cached = extractMemoryValue("Cached:");
                                        int sreclaimable = extractMemoryValue("SReclaimable:");
                                        int shmem = extractMemoryValue("Shmem:");

                                        int usedMemory = (totalMem - freeMem - buffers - (cached + sreclaimable - shmem)) / 1024; // in MB
                                        int totalMemory = totalMem / 1024; // in MB
                                        String memoryUsage = "MEM: " + usedMemory + "/" + totalMemory + " MB";

                                        // Calculate average CPU utilization across all cores
                                        float avgCpuUsage = getAverageCpuUsage();
                                        String cpuUsageText = String.format("CPU: %.1f%%", avgCpuUsage);

                                        // Update UI on the main thread
                                        mHandler.post(new Runnable() {
                                                @Override
                                                public void run() {
                                                        TextView memoryText = headerView.findViewById(R.id.memory_usage);
                                                        TextView cpuText = headerView.findViewById(R.id.cpu_usage);

                                                        if (memoryText != null) {
                                                                memoryText.setText(memoryUsage);
                                                        } else {
                                                                Log.e(TAG, "Memory TextView not found");
                                                        }

                                                        if (cpuText != null) {
                                                                cpuText.setText(cpuUsageText);
                                                        } else {
                                                                Log.e(TAG, "CPU TextView not found");
                                                        }
                                                }
                                        });

                                } catch (Exception e) {
                                        Log.e(TAG, "Failed to update memory or CPU usage", e);
                                }

                                // Post the Runnable again for repeated execution
                                mMemoryHandler.postDelayed(mMemoryUpdateRunnable, MEMORY_UPDATE_INTERVAL);
                        }

                                private float getAverageCpuUsage() {
                                        try {
                                                java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader("/proc/stat"));
                                                String line = reader.readLine();
                                                float result = smoothedCpu;

                                                if (line != null && line.startsWith("cpu ")) {
                                                        String[] tokens = line.trim().split("\\s+");
                                                        // Expect at least: cpu user nice system idle iowait irq softirq [steal]
                                                        if (tokens.length >= 8) {
                                                                long user    = Long.parseLong(tokens[1]);
                                                                long nice    = Long.parseLong(tokens[2]);
                                                                long system  = Long.parseLong(tokens[3]);
                                                                long idle    = Long.parseLong(tokens[4]);
                                                                long iowait  = Long.parseLong(tokens[5]);
                                                                long irq     = Long.parseLong(tokens[6]);
                                                                long softirq = Long.parseLong(tokens[7]);
                                                                long steal   = (tokens.length > 8) ? Long.parseLong(tokens[8]) : 0L;

                                                                long idleAll  = idle + iowait; // treat iowait as idle
                                                                long totalAll = user + nice + system + idle + iowait + irq + softirq + steal;

                                                                if (prevTotalAll == 0L) {
                                                                        // Prime the deltas on first read to avoid a bogus initial spike.
                                                                        prevIdleAll  = idleAll;
                                                                        prevTotalAll = totalAll;
                                                                } else {
                                                                        long diffIdle  = idleAll  - prevIdleAll;
                                                                        long diffTotal = totalAll - prevTotalAll;

                                                                        prevIdleAll  = idleAll;
                                                                        prevTotalAll = totalAll;

                                                                        if (diffTotal > 0) {
                                                                                float sample = 100.0f * (diffTotal - diffIdle) / diffTotal;
                                                                                // Initialize EMA on first computed sample for faster lock-on.
                                                                                if (smoothedCpu == 0.0f) {
                                                                                        smoothedCpu = sample;
                                                                                } else {
                                                                                        smoothedCpu = CPU_EMA_ALPHA * sample + (1.0f - CPU_EMA_ALPHA) * smoothedCpu;
                                                                                }
                                                                                result = smoothedCpu;
                                                                        }
                                                                }
                                                        }
                                                }

                                                reader.close();
                                                return result;

                                        } catch (Exception e) {
                                                Log.e(TAG, "Failed to read CPU info", e);
                                        }

                                        return smoothedCpu;
                                }
                        };
                                // Start the memory and CPU updates
                mMemoryHandler.post(mMemoryUpdateRunnable);
        }

        private int extractMemoryValue(String key) {
                try {
                        java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader("/proc/meminfo"));
                        String line;
                        while ((line = reader.readLine()) != null) {
                                if (line.startsWith(key)) {
                                        reader.close();
                                        return Integer.parseInt(line.replaceAll("\\D+", ""));
                                }
                        }
                        reader.close();
                } catch (Exception e) {
                        Log.e(TAG, "Failed to read memory info", e);
                }
                return 0;
        }

    private BroadcastReceiver batteryInfoReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            final int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            final int batteryPct = (level >= 0 && scale > 0) ? (int) ((level / (float) scale) * 100) : -1;

            if (batteryPct >= 0) {
                // Post task to Handler to ensure it runs on the main thread
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        TextView batteryText = headerView.findViewById(R.id.battery_percentage);
                        if (batteryText != null) {
                            batteryText.setText(batteryPct + "%");
                            batteryText.setTextColor(Color.WHITE);
                        } else {
                            Log.e(TAG, "Battery TextView not found");
                        }
                    }
                });
            } else {
                Log.e(TAG, "Invalid battery level or scale");
            }
        }
    };


}
