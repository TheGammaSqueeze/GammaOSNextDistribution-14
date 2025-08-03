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

package android.server.wm.app;

import android.content.ComponentName;
import android.server.wm.TestJournalProvider;
import android.server.wm.component.ComponentsBase;

public class Components extends ComponentsBase {
    public static final ComponentName ALT_LAUNCHING_ACTIVITY = component("AltLaunchingActivity");
    public static final ComponentName ALWAYS_FOCUSABLE_PIP_ACTIVITY =
            component("AlwaysFocusablePipActivity");
    public static final ComponentName ANIMATION_TEST_ACTIVITY = component("AnimationTestActivity");
    public static final ComponentName ASSISTANT_ACTIVITY = component("AssistantActivity");
    public static final ComponentName BOTTOM_ACTIVITY = component("BottomActivity");
    public static final ComponentName BOTTOM_NON_RESIZABLE_ACTIVITY = component("BottomNonResizableActivity");
    public static final ComponentName BOTTOM_LEFT_LAYOUT_ACTIVITY =
            component("BottomLeftLayoutActivity");
    public static final ComponentName BOTTOM_RIGHT_LAYOUT_ACTIVITY =
            component("BottomRightLayoutActivity");
    public static final ComponentName BROADCAST_RECEIVER_ACTIVITY =
            component("BroadcastReceiverActivity");
    public static final ComponentName DIALOG_WHEN_LARGE_ACTIVITY =
            component("DialogWhenLargeActivity");
    public static final ComponentName DISMISS_KEYGUARD_ACTIVITY =
            component("DismissKeyguardActivity");
    public static final ComponentName DISMISS_KEYGUARD_METHOD_ACTIVITY =
            component("DismissKeyguardMethodActivity");
    public static final ComponentName DOCKED_ACTIVITY = component("DockedActivity");
    /**
     * This activity is an alias activity pointing {@link TrampolineActivity}
     * in AndroidManifest.xml
     */
    public static final ComponentName ENTRY_POINT_ALIAS_ACTIVITY =
            component("EntryPointAliasActivity");
    public static final ComponentName FONT_SCALE_ACTIVITY = component("FontScaleActivity");
    public static final ComponentName FONT_SCALE_NO_RELAUNCH_ACTIVITY =
            component("FontScaleNoRelaunchActivity");
    public static final ComponentName FREEFORM_ACTIVITY = component("FreeformActivity");
    public static final ComponentName HOST_ACTIVITY = component("HostActivity");
    public static final ComponentName HIDE_OVERLAY_WINDOWS_ACTIVITY =
            component("HideOverlayWindowsActivity");
    public static final ComponentName KEYGUARD_LOCK_ACTIVITY = component("KeyguardLockActivity");
    public static final ComponentName LANDSCAPE_ORIENTATION_ACTIVITY =
            component("LandscapeOrientationActivity");
    public static final ComponentName LAUNCH_ASSISTANT_ACTIVITY_FROM_SESSION =
            component("LaunchAssistantActivityFromSession");
    public static final ComponentName LAUNCH_ASSISTANT_ACTIVITY_INTO_STACK  =
            component("LaunchAssistantActivityIntoAssistantStack");
    public static final ComponentName LAUNCH_ENTER_PIP_ACTIVITY =
            component("LaunchEnterPipActivity");
    public static final ComponentName LAUNCH_INTO_PINNED_STACK_PIP_ACTIVITY =
            component("LaunchIntoPinnedStackPipActivity");
    public static final ComponentName LAUNCH_PIP_ON_PIP_ACTIVITY =
            component("LaunchPipOnPipActivity");
    public static final ComponentName LAUNCHING_ACTIVITY = component("LaunchingActivity");
    public static final ComponentName LOG_CONFIGURATION_ACTIVITY =
            component("LogConfigurationActivity");
    public static final ComponentName MOVE_TASK_TO_BACK_ACTIVITY =
            component("MoveTaskToBackActivity");
    public static final ComponentName MULTI_WINDOW_FULLSCREEN_ACTIVITY =
            component("MultiWindowFullscreenActivity");
    public static final ComponentName NIGHT_MODE_ACTIVITY = component("NightModeActivity");
    public static final ComponentName NO_DISPLAY_ACTIVITY = component("NoDisplayActivity");
    public static final ComponentName NO_HISTORY_ACTIVITY = component("NoHistoryActivity");
    public static final ComponentName NO_HISTORY_ACTIVITY2 = component("NoHistoryActivity2");
    public static final ComponentName NO_RELAUNCH_ACTIVITY = component("NoRelaunchActivity");
    public static final ComponentName NON_RESIZEABLE_ACTIVITY = component("NonResizeableActivity");
    public static final ComponentName PRESENTATION_ACTIVITY = component("PresentationActivity");
    public static final ComponentName PIP_ACTIVITY = component("PipActivity");
    public static final ComponentName PIP_ACTIVITY2 = component("PipActivity2");
    public static final ComponentName PIP_ACTIVITY_WITH_MINIMAL_SIZE = component(
            "PipActivityWithMinimalSize");
    public static final ComponentName PIP_ACTIVITY_WITH_TINY_MINIMAL_SIZE = component(
            "PipActivityWithTinyMinimalSize");
    public static final ComponentName PIP_ACTIVITY_WITH_SAME_AFFINITY =
            component("PipActivityWithSameAffinity");
    public static final ComponentName PIP_ON_STOP_ACTIVITY = component("PipOnStopActivity");
    public static final ComponentName LAUNCH_INTO_PIP_HOST_ACTIVITY =
            component("LaunchIntoPipHostActivity");
    public static final ComponentName LAUNCH_INTO_PIP_CONTAINER_ACTIVITY =
            component("LaunchIntoPipContainerActivity");
    public static final ComponentName PORTRAIT_ORIENTATION_ACTIVITY =
            component("PortraitOrientationActivity");
    public static final ComponentName RECURSIVE_ACTIVITY = component("RecursiveActivity");
    public static final ComponentName REPORT_FULLY_DRAWN_ACTIVITY =
            component("ReportFullyDrawnActivity");
    public static final ComponentName RESIZEABLE_ACTIVITY = component("ResizeableActivity");
    public static final ComponentName RESUME_WHILE_PAUSING_ACTIVITY =
            component("ResumeWhilePausingActivity");
    public static final ComponentName SHOW_WHEN_LOCKED_ACTIVITY =
            component("ShowWhenLockedActivity");
    public static final ComponentName SHOW_WHEN_LOCKED_ATTR_IME_ACTIVITY =
            component("ShowWhenLockedAttrImeActivity");
    public static final ComponentName SHOW_WHEN_LOCKED_ATTR_ACTIVITY =
            component("ShowWhenLockedAttrActivity");
    public static final ComponentName SHOW_WHEN_LOCKED_ATTR_ROTATION_ACTIVITY =
            component("ShowWhenLockedAttrRotationActivity");
    public static final ComponentName SHOW_WHEN_LOCKED_ATTR_REMOVE_ATTR_ACTIVITY =
            component("ShowWhenLockedAttrRemoveAttrActivity");
    public static final ComponentName INHERIT_SHOW_WHEN_LOCKED_ADD_ACTIVITY =
            component("InheritShowWhenLockedAddActivity");
    public static final ComponentName INHERIT_SHOW_WHEN_LOCKED_ATTR_ACTIVITY =
            component("InheritShowWhenLockedAttrActivity");
    public static final ComponentName INHERIT_SHOW_WHEN_LOCKED_REMOVE_ACTIVITY =
            component("InheritShowWhenLockedRemoveActivity");
    public static final ComponentName NO_INHERIT_SHOW_WHEN_LOCKED_ATTR_ACTIVITY =
            component("NoInheritShowWhenLockedAttrActivity");
    public static final ComponentName SHOW_WHEN_LOCKED_DIALOG_ACTIVITY =
            component("ShowWhenLockedDialogActivity");
    public static final ComponentName SHOW_WHEN_LOCKED_TRANSLUCENT_ACTIVITY =
            component("ShowWhenLockedTranslucentActivity");
    public static final ComponentName SHOW_WHEN_LOCKED_WITH_DIALOG_ACTIVITY =
            component("ShowWhenLockedWithDialogActivity");
    public static final ComponentName SINGLE_INSTANCE_ACTIVITY =
            component("SingleInstanceActivity");
    public static final ComponentName HOME_ACTIVITY = component("HomeActivity");
    public static final ComponentName SECONDARY_HOME_ACTIVITY = component("SecondaryHomeActivity");
    public static final ComponentName UI_SCALING_TEST_ACTIVITY =
            component("UiScalingTestActivity");
    public static final ComponentName SINGLE_HOME_ACTIVITY = component("SingleHomeActivity");
    public static final ComponentName SINGLE_SECONDARY_HOME_ACTIVITY =
            component("SingleSecondaryHomeActivity");
    public static final ComponentName SINGLE_TASK_ACTIVITY = component("SingleTaskActivity");
    public static final ComponentName SINGLE_TOP_ACTIVITY = component("SingleTopActivity");
    public static final ComponentName SPLASHSCREEN_ACTIVITY = component("SplashscreenActivity");
    public static final ComponentName DISABLE_PREVIEW_ACTIVITY =
            component("DisablePreviewActivity");
    public static final ComponentName SHOW_WHEN_LOCKED_NO_PREVIEW_ACTIVITY =
            component("ShowWhenLockedNoPreviewActivity");
    public static final ComponentName SHOW_WHEN_LOCKED_ATTR_NO_PREVIEW_ACTIVITY =
            component("ShowWhenLockedAttrNoPreviewActivity");
    public static final ComponentName SHOW_WHEN_LOCKED_ATTR_REMOVE_ATTR_NO_PREVIEW_ACTIVITY =
            component("ShowWhenLockedAttrRemoveAttrNoPreviewActivity");
    public static final ComponentName SHOW_WHEN_LOCKED_WITH_DIALOG_NO_PREVIEW_ACTIVITY =
            component("ShowWhenLockedWithDialogNoPreviewActivity");

    public static final ComponentName TEST_ACTIVITY = component("TestActivity");
    public static final ComponentName TOAST_ACTIVITY = component("ToastActivity");
    public static final ComponentName TOP_ACTIVITY = component("TopActivity");
    public static final ComponentName TOP_NON_RESIZABLE_ACTIVITY = component("TopNonResizableActivity");
    public static final ComponentName TOP_NON_RESIZABLE_WALLPAPER_ACTIVITY = component("TopNonResizableWallpaperActivity");
    public static final ComponentName TOP_WALLPAPER_ACTIVITY = component("TopWallpaperActivity");
    public static final ComponentName TEST_ACTIVITY_WITH_SAME_AFFINITY =
            component("TestActivityWithSameAffinity");
    public static final ComponentName TEST_LIVE_WALLPAPER_SERVICE = component("LiveWallpaper");
    public static final ComponentName TEST_INTERACTIVE_LIVE_WALLPAPER_SERVICE = component(
            "InteractiveLiveWallpaper");
    public static final ComponentName TOP_LEFT_LAYOUT_ACTIVITY = component("TopLeftLayoutActivity");
    public static final ComponentName TOP_RIGHT_LAYOUT_ACTIVITY =
            component("TopRightLayoutActivity");
    public static final ComponentName TRANSLUCENT_ACTIVITY = component("TranslucentActivity");
    public static final ComponentName TRANSLUCENT_ASSISTANT_ACTIVITY =
            component("TranslucentAssistantActivity");
    public static final ComponentName TRANSLUCENT_TOP_ACTIVITY =
            component("TranslucentTopActivity");
    public static final ComponentName TRANSLUCENT_TOP_WALLPAPER_ACTIVITY =
            component("TranslucentTopWallpaperActivity");
    public static final ComponentName TRANSLUCENT_TOP_NON_RESIZABLE_ACTIVITY =
        component("TranslucentTopNonResizableActivity");
    public static final ComponentName TRANSLUCENT_TEST_ACTIVITY =
            component("TranslucentTestActivity");
    public static final ComponentName TURN_SCREEN_ON_ACTIVITY = component("TurnScreenOnActivity");
    public static final ComponentName TURN_SCREEN_ON_ATTR_ACTIVITY =
            component("TurnScreenOnAttrActivity");
    public static final ComponentName TURN_SCREEN_ON_ATTR_DISMISS_KEYGUARD_ACTIVITY =
            component("TurnScreenOnAttrDismissKeyguardActivity");
    public static final ComponentName TURN_SCREEN_ON_ATTR_REMOVE_ATTR_ACTIVITY =
            component("TurnScreenOnAttrRemoveAttrActivity");
    public static final ComponentName TURN_SCREEN_ON_DISMISS_KEYGUARD_ACTIVITY =
            component("TurnScreenOnDismissKeyguardActivity");
    public static final ComponentName TURN_SCREEN_ON_SHOW_ON_LOCK_ACTIVITY =
            component("TurnScreenOnShowOnLockActivity");
    public static final ComponentName TURN_SCREEN_ON_SINGLE_TASK_ACTIVITY =
            component("TurnScreenOnSingleTaskActivity");
    public static final ComponentName TURN_SCREEN_ON_WITH_RELAYOUT_ACTIVITY =
            component("TurnScreenOnWithRelayoutActivity");
    public static final ComponentName UNRESPONSIVE_ACTIVITY = component("UnresponsiveActivity");
    public static final ComponentName VIRTUAL_DISPLAY_ACTIVITY =
            component("VirtualDisplayActivity");
    public static final ComponentName VR_TEST_ACTIVITY = component("VrTestActivity");
    public static final ComponentName WALLPAPAER_ACTIVITY = component("WallpaperActivity");
    public static final ComponentName LAUNCH_TEST_ON_DESTROY_ACTIVITY = component(
            "LaunchTestOnDestroyActivity");
    public static final ComponentName ALIAS_TEST_ACTIVITY = component("AliasTestActivity");
    public static final ComponentName DISPLAY_ACCESS_CHECK_EMBEDDING_ACTIVITY = component(
            "DisplayAccessCheckEmbeddingActivity");
    public static final ComponentName MAX_ASPECT_RATIO_ACTIVITY =
            component("MaxAspectRatioActivity");
    public static final ComponentName MAX_ASPECT_RATIO_RESIZABLE_ACTIVITY =
            component("MaxAspectRatioResizableActivity");
    public static final ComponentName MAX_ASPECT_RATIO_UNSET_ACTIVITY =
            component("MaxAspectRatioUnsetActivity");
    public static final ComponentName META_DATA_MAX_ASPECT_RATIO_ACTIVITY =
            component("MetaDataMaxAspectRatioActivity");
    public static final ComponentName MIN_ASPECT_RATIO_ACTIVITY =
            component("MinAspectRatioActivity");
    public static final ComponentName MIN_ASPECT_RATIO_LANDSCAPE_ACTIVITY =
            component("MinAspectRatioLandscapeActivity");
    public static final ComponentName MIN_ASPECT_RATIO_PORTRAIT_ACTIVITY =
            component("MinAspectRatioPortraitActivity");
    public static final ComponentName MIN_ASPECT_RATIO_UNSET_ACTIVITY =
            component("MinAspectRatioUnsetActivity");

    public static final ComponentName ASSISTANT_VOICE_INTERACTION_SERVICE =
            component("AssistantVoiceInteractionService");

    public static final ComponentName LAUNCH_BROADCAST_RECEIVER =
            component("LaunchBroadcastReceiver");

    public static final ComponentName CLICKABLE_TOAST_ACTIVITY =
            component("ClickableToastActivity");

    public static final ComponentName WALLPAPER_TARGET_ACTIVITY =
            component("WallpaperTargetActivity");

    public static class LaunchBroadcastReceiver {
        public static final String LAUNCH_BROADCAST_ACTION =
                "android.server.wm.app.LAUNCH_BROADCAST_ACTION";

        public static final String ACTION_TEST_ACTIVITY_START =
                "android.server.wm.app.ACTION_TEST_ACTIVITY_START";
        public static final String EXTRA_COMPONENT_NAME = "component_name";
        public static final String EXTRA_TARGET_DISPLAY = "target_display";
    }

    public static final ComponentName SINGLE_TASK_INSTANCE_DISPLAY_ACTIVITY =
            component("SingleTaskInstanceDisplayActivity");
    public static final ComponentName SINGLE_TASK_INSTANCE_DISPLAY_ACTIVITY2 =
            component("SingleTaskInstanceDisplayActivity2");
    public static final ComponentName SINGLE_TASK_INSTANCE_DISPLAY_ACTIVITY3 =
            component("SingleTaskInstanceDisplayActivity3");

    public static final ComponentName MPP_ACTIVITY =
            component("MinimalPostProcessingActivity");

    public static final ComponentName MPP_ACTIVITY2 =
            component("MinimalPostProcessingActivity2");

    public static final ComponentName MPP_ACTIVITY3 =
            component("MinimalPostProcessingManifestActivity");

    public static final ComponentName POPUP_MPP_ACTIVITY =
            component("PopupMinimalPostProcessingActivity");

    public static final ComponentName CRASHING_ACTIVITY =
            component("CrashingActivity");

    public static final ComponentName HANDLE_SPLASH_SCREEN_EXIT_ACTIVITY =
            component("HandleSplashScreenExitActivity");
    public static final ComponentName SPLASH_SCREEN_REPLACE_ICON_ACTIVITY =
            component("SplashScreenReplaceIconActivity");
    public static final ComponentName SPLASH_SCREEN_REPLACE_THEME_ACTIVITY =
            component("SplashScreenReplaceThemeActivity");
    public static final ComponentName SPLASH_SCREEN_STYLE_THEME_ACTIVITY =
            component("SplashScreenStyleThemeActivity");

    public static final ComponentName TEST_DREAM_SERVICE =
            component("TestDream");

    public static final ComponentName TEST_STUBBORN_DREAM_SERVICE =
            component("TestStubbornDream");

    public static final ComponentName OVERLAY_TEST_SERVICE =
            component("OverlayTestService");

    public static final ComponentName KEEP_CLEAR_RECTS_ACTIVITY =
            component("KeepClearRectsActivity");

    public static final ComponentName KEEP_CLEAR_RECTS_ACTIVITY2 =
            component("KeepClearRectsActivity2");

    /**
     * The keys are used for {@link TestJournalProvider} when testing starting window.
     */
    public static class TestStartingWindowKeys {
        public static final String HANDLE_SPLASH_SCREEN_EXIT = "HandleSplashScreenExitActivity";
        public static final String REPLACE_ICON_EXIT = "SplashScreenReplaceIconActivity";
        public static final String RECEIVE_SPLASH_SCREEN_EXIT = "receive_splash_screen_exit";
        public static final String CONTAINS_CENTER_VIEW = "contains_center_view";
        public static final String CONTAINS_BRANDING_VIEW = "contains_branding_view";
        public static final String ICON_BACKGROUND_COLOR = "icon_background_color";
        public static final String ICON_ANIMATION_DURATION = "icon_animation_duration";
        public static final String ICON_ANIMATION_START = "icon_animation_start";
        public static final String CENTER_VIEW_IS_SURFACE_VIEW = "center_view_is_surface_view";

        public static final String REQUEST_HANDLE_EXIT_ON_CREATE = "handle_exit_onCreate";
        public static final String REQUEST_HANDLE_EXIT_ON_RESUME = "handle_exit_onResume";
        public static final String CANCEL_HANDLE_EXIT = "cancel_handle_exit";

        public static final String REQUEST_SET_NIGHT_MODE_ON_CREATE = "night_mode_onCreate";
        public static final String GET_NIGHT_MODE_ACTIVITY_CHANGED = "get_night_mode_activity";
        public static final String DELAY_RESUME = "delay_resume";
        public static final String OVERRIDE_THEME_ENABLED = "override_theme_enabled";
        public static final String OVERRIDE_THEME_COLOR = "override_theme_color";
        public static final String OVERRIDE_THEME_COMPONENT = "override_theme_component";
        public static final String STYLE_THEME_COMPONENT = "style_theme_component";
    }

    /**
     * The keys are used for {@link TestJournalProvider} when testing wallpaper
     * component.
     */
    public static class TestLiveWallpaperKeys {
        public static final String COMPONENT = "LiveWallpaper";
        public static final String ENGINE_CREATED = "engine_created";
        public static final String ENGINE_DISPLAY_ID = "engine_display_Id";
    }

    /**
     * The keys are used for {@link TestJournalProvider} when testing interactive wallpaper
     * component.
     */
    public static class TestInteractiveLiveWallpaperKeys {
        public static final String COMPONENT = "InteractiveLiveWallpaper";
        public static final String LAST_RECEIVED_MOTION_EVENT = "LastReceivedMotionEvent";
    }

    /**
     * Action and extra key constants for {@link #TEST_ACTIVITY}.
     *
     * TODO(b/73346885): These constants should be in {@link android.server.wm.app.TestActivity}
     * once the activity is moved to test APK.
     */
    public static class TestActivity {
        // Finishes the activity
        public static final String TEST_ACTIVITY_ACTION_FINISH_SELF =
                "android.server.wm.app.TestActivity.finish_self";
        // Sets the fixed orientation (can be one of {@link ActivityInfo.ScreenOrientation}
        public static final String EXTRA_FIXED_ORIENTATION = "fixed_orientation";
        public static final String EXTRA_CONFIGURATION = "configuration";
        public static final String EXTRA_CONFIG_ASSETS_SEQ = "config_assets_seq";
        public static final String EXTRA_INTENT = "intent";
        public static final String EXTRA_INTENTS = "intents";
        public static final String EXTRA_NO_IDLE = "no_idle";
        public static final String COMMAND_NAVIGATE_UP_TO = "navigate_up_to";
        public static final String COMMAND_START_ACTIVITY = "start_activity";
        public static final String COMMAND_START_ACTIVITIES = "start_activities";
        public static final String EXTRA_OPTION = "option";
    }

    /**
     * Extra key constants for {@link #LAUNCH_ASSISTANT_ACTIVITY_INTO_STACK} and
     * {@link #LAUNCH_ASSISTANT_ACTIVITY_FROM_SESSION}.
     *
     * TODO(b/73346885): These constants should be in
     * {@link android.server.wm.app.AssistantActivity} once the activity is moved to test APK.
     */
    public static class AssistantActivity {
        // Launches the given activity in onResume
        public static final String EXTRA_ASSISTANT_LAUNCH_NEW_TASK = "launch_new_task";
        // Finishes this activity in onResume, this happens after EXTRA_ASSISTANT_LAUNCH_NEW_TASK
        public static final String EXTRA_ASSISTANT_FINISH_SELF = "finish_self";
        // Attempts to enter picture-in-picture in onResume
        public static final String EXTRA_ASSISTANT_ENTER_PIP = "enter_pip";
        // Display on which Assistant runs
        public static final String EXTRA_ASSISTANT_DISPLAY_ID = "assistant_display_id";
    }

    /**
     * Extra key constants for {@link android.server.wm.app.BottomActivity}.
     *
     * TODO(b/73346885): These constants should be in {@link android.server.wm.app.BottomActivity}
     * once the activity is moved to test APK.
     */
    public static class BottomActivity {
        public static final String EXTRA_BOTTOM_WALLPAPER = "USE_WALLPAPER";
        public static final String EXTRA_STOP_DELAY = "STOP_DELAY";
    }

    /**
     * Extra key constants for {@link android.server.wm.app.BroadcastReceiverActivity}.
     *
     * TODO(b/73346885): These constants should be in
     * {@link android.server.wm.app.BroadcastReceiverActivity} once the activity is moved to test APK.
     */
    public static class BroadcastReceiverActivity {
        public static final String ACTION_TRIGGER_BROADCAST = "trigger_broadcast";
        public static final String EXTRA_DISMISS_KEYGUARD = "dismissKeyguard";
        public static final String EXTRA_DISMISS_KEYGUARD_METHOD = "dismissKeyguardMethod";
        public static final String EXTRA_FINISH_BROADCAST = "finish";
        public static final String EXTRA_MOVE_BROADCAST_TO_BACK = "moveToBack";
        public static final String EXTRA_BROADCAST_ORIENTATION = "orientation";
        public static final String EXTRA_CUTOUT_EXISTS = "cutoutExists";
    }

    /** Extra key constants for {@link android.server.wm.app.LandscapeOrientationActivity}. */
    public static class LandscapeOrientationActivity {
        public static final String EXTRA_APP_CONFIG_INFO = "app_config_info";
        public static final String EXTRA_CONFIG_INFO_IN_ON_CREATE = "config_info_in_on_create";
        public static final String EXTRA_DISPLAY_REAL_SIZE = "display_real_size";
        public static final String EXTRA_SYSTEM_RESOURCES_CONFIG_INFO = "sys_config_info";
    }

    /** Extra key constants for {@link android.server.wm.app.FontScaleActivity}. */
    public static class FontScaleActivity {
        public static final String EXTRA_FONT_PIXEL_SIZE = "fontPixelSize";
        public static final String EXTRA_FONT_ACTIVITY_DPI = "fontActivityDpi";
    }

    /** Extra key constants for {@link android.server.wm.app.NoHistoryActivity}. */
    public static class NoHistoryActivity {
        public static final String EXTRA_SHOW_WHEN_LOCKED = "showWhenLocked";
    }

    /** Extra key constants for {@link android.server.wm.app.TurnScreenOnActivity}. */
    public static class TurnScreenOnActivity {
        // Turn on screen by window flags or APIs.
        public static final String EXTRA_USE_WINDOW_FLAGS = "useWindowFlags";
        public static final String EXTRA_SHOW_WHEN_LOCKED = "useShowWhenLocked";
        public static final String EXTRA_SLEEP_MS_IN_ON_CREATE = "sleepMsInOnCreate";
    }

    /** Extra key constants for {@link android.server.wm.app.MinimalPostProcessingActivity}. */
    public static class MinimalPostProcessingActivity {
        // Turn on minimal post processing (if available).
        public static final String EXTRA_PREFER_MPP = "preferMinimalPostProcessing";
    }

    /**
     *  Logging constants for {@link android.server.wm.app.KeyguardDismissLoggerCallback}.
     *
     * TODO(b/73346885): These constants should be in
     * {@link android.server.wm.app.KeyguardDismissLoggerCallback} once the class is moved to test APK.
     */
    public static class KeyguardDismissLoggerCallback {
        public static final String KEYGUARD_DISMISS_LOG_TAG = "KeyguardDismissLoggerCallback";
        public static final String ENTRY_ON_DISMISS_CANCELLED = "onDismissCancelled";
        public static final String ENTRY_ON_DISMISS_ERROR = "onDismissError";
        public static final String ENTRY_ON_DISMISS_SUCCEEDED = "onDismissSucceeded";
    }

    /**
     * Extra key constants for {@link #LAUNCH_ASSISTANT_ACTIVITY_INTO_STACK}.
     *
     * TODO(b/73346885): These constants should be in
     * {@link android.server.wm.app.LaunchAssistantActivityIntoAssistantStack} once the activity is
     * moved to test APK.
     */
    public static class LaunchAssistantActivityIntoAssistantStack {
        // Launches the translucent assist activity
        public static final String EXTRA_ASSISTANT_IS_TRANSLUCENT = "is_translucent";
    }

    /**
     * Extra constants for {@link android.server.wm.app.MoveTaskToBackActivity}.
     *
     * TODO(b/73346885): These constants should be in
     * {@link android.server.wm.app.MoveTaskToBackActivity} once the activity is moved to test APK.
     */
    public static class MoveTaskToBackActivity {
        public static final String EXTRA_FINISH_POINT = "finish_point";
        public static final String FINISH_POINT_ON_PAUSE = "on_pause";
        public static final String FINISH_POINT_ON_STOP = "on_stop";
    }

    /**
     * Action and extra key constants for {@link android.server.wm.app.PipActivity}.
     *
     * TODO(b/73346885): These constants should be in {@link android.server.wm.app.PipActivity}
     * once the activity is moved to test APK.
     */
    public static class PipActivity {
        // Intent action that this activity dynamically registers to enter picture-in-picture
        public static final String ACTION_ENTER_PIP = "android.server.wm.app.PipActivity.enter_pip";
        // Intent action that this activity dynamically registers to move itself to the back
        public static final String ACTION_MOVE_TO_BACK =
                "android.server.wm.app.PipActivity.move_to_back";
        // Intent action that this activity dynamically registers to expand itself.
        // If EXTRA_SET_ASPECT_RATIO_WITH_DELAY is set, it will also attempt to apply the aspect
        // ratio after a short delay.
        public static final String ACTION_EXPAND_PIP =
                "android.server.wm.app.PipActivity.expand_pip";
        // Intent action that this activity dynamically registers to receive update callback.
        // If EXTRA_SET_PIP_STASH is set, then onPictureInPictureStateChanged will be called
        // with the value.
        public static final String ACTION_UPDATE_PIP_STATE =
                "android.server.wm.app.PipActivity.update_pip_state";
        // Intent action that this activity dynamically registers to enter picture-in-picture
        // then wait for the onPictureInPictureUiStateChanged callback.
        public static final String ACTION_ENTER_PIP_AND_WAIT_FOR_UI_STATE =
                "android.server.wm.app.PipActivity.enter_pip_and_wait_for_ui_state";
        // Intent action that this activity dynamically registers to set requested orientation.
        // Will apply the orientation to the value set in the EXTRA_FIXED_ORIENTATION extra.
        public static final String ACTION_SET_REQUESTED_ORIENTATION =
                "android.server.wm.app.PipActivity.set_requested_orientation";
        // Intent action that will finish this activity
        public static final String ACTION_FINISH = "android.server.wm.app.PipActivity.finish";
        // Intent action that will request that the activity enters picture-in-picture.
        public static final String ACTION_ON_PIP_REQUESTED =
                "android.server.wm.app.PipActivity.on_pip_requested";
        // Intent action that will request that the activity initiates launch-into-pip
        public static final String ACTION_START_LAUNCH_INTO_PIP_CONTAINER =
                "android.server.wm.app.LaunchIntoPip.start_container_activity";
        // Intent action that will request the host activity of launch-into-pip to finish itself
        public static final String ACTION_FINISH_LAUNCH_INTO_PIP_HOST =
                "android.server.wm.app.LaunchIntoPip.finish_host_activity";
        // Intent action that will request the activity to change the PiP aspect ratio
        public static final String ACTION_CHANGE_ASPECT_RATIO =
                "android.server.wm.app.LaunchIntoPip.change_aspect_ratio";
        // Intent action that will request the activity to start a new translucent activity
        public static final String ACTION_LAUNCH_TRANSLUCENT_ACTIVITY =
                "android.server.wm.app.LaunchIntoPip.launch_translucent_activity";
        // Intent action that sets a RemoteCallback in PipActivity that will receive the
        // isInPictureInPictureMode result
        public static final String ACTION_SET_ON_PAUSE_REMOTE_CALLBACK =
                "android.server.wm.app.PipActivity.set_on_pause_remote_callback";

        // Adds an assertion that we do not ever get onStop() before we enter picture in picture
        public static final String EXTRA_ASSERT_NO_ON_STOP_BEFORE_PIP =
                "assert_no_on_stop_before_pip";
        // Calls enterPictureInPicture() on creation
        public static final String EXTRA_ENTER_PIP = "enter_pip";
        // Used with EXTRA_AUTO_ENTER_PIP, value specifies the aspect ratio to enter PIP with
        public static final String EXTRA_ENTER_PIP_ASPECT_RATIO_NUMERATOR =
                "enter_pip_aspect_ratio_numerator";
        // Used with EXTRA_AUTO_ENTER_PIP, value specifies the aspect ratio to enter PIP with
        public static final String EXTRA_ENTER_PIP_ASPECT_RATIO_DENOMINATOR =
                "enter_pip_aspect_ratio_denominator";
        // Calls requestAutoEnterPictureInPicture() with the value provided
        public static final String EXTRA_ENTER_PIP_ON_PAUSE = "enter_pip_on_pause";
        // Calls requestAutoEnterPictureInPicture() with the value provided
        public static final String EXTRA_ENTER_PIP_ON_USER_LEAVE_HINT =
                "enter_pip_on_user_leave_hint";
        // Calls requestAutoEnterPictureInPicture() with the value provided
        public static final String EXTRA_ENTER_PIP_ON_PIP_REQUESTED =
                "enter_pip_on_pip_requested";
        // Calls enterPictureInPictureMode when activity receives onBackPressed
        public static final String EXTRA_ENTER_PIP_ON_BACK_PRESSED =
                "enter_pip_on_back_pressed";
        public static final String EXTRA_EXPANDED_PIP_ASPECT_RATIO_NUMERATOR =
                "expanded_pip_numerator";
        public static final String EXTRA_EXPANDED_PIP_ASPECT_RATIO_DENOMINATOR =
                "expanded_pip_denomenator";
        // Sets auto PIP allowed on the activity picture-in-picture params.
        public static final String EXTRA_ALLOW_AUTO_PIP = "enter_pip_auto_pip_allowed";
        // Sets seamless resize enabled on the activity picture-in-picture params.
        public static final String EXTRA_IS_SEAMLESS_RESIZE_ENABLED =
                "enter_pip_is_seamless_resize_enabled";
        // Sets the given title on the activity picture-in-picture params.
        public static final String EXTRA_TITLE = "set_pip_title";
        // Sets the given subtitle on the activity picture-in-picture params.
        public static final String EXTRA_SUBTITLE = "set_pip_subtitle";
        // Finishes the activity at the end of onResume (after EXTRA_START_ACTIVITY is handled)
        public static final String EXTRA_FINISH_SELF_ON_RESUME = "finish_self_on_resume";
        // Similar to EXTRA_FINISH_SELF_ON_RESUME but only be used to finish the trampoline
        // Activity when it receives onResume (after the target Activity is launched).
        public static final String EXTRA_FINISH_TRAMPOLINE_ON_RESUME =
                "finish_trampoline_on_resume";
        // Sets the fixed orientation (can be one of {@link ActivityInfo.ScreenOrientation}
        public static final String EXTRA_PIP_ORIENTATION = "fixed_orientation";
        // The amount to delay to artificially introduce in onPause()
        // (before EXTRA_ENTER_PIP_ON_PAUSE is processed)
        public static final String EXTRA_ON_PAUSE_DELAY = "on_pause_delay";
        // Calls setPictureInPictureAspectRatio with the aspect ratio specified in the value
        public static final String EXTRA_SET_ASPECT_RATIO_DENOMINATOR =
                "set_aspect_ratio_denominator";
        // Calls setPictureInPictureAspectRatio with the aspect ratio specified in the value
        public static final String EXTRA_SET_ASPECT_RATIO_NUMERATOR = "set_aspect_ratio_numerator";
        // Calls setPictureInPictureAspectRatio with the aspect ratio specified in the value with a
        // fixed delay
        public static final String EXTRA_SET_ASPECT_RATIO_WITH_DELAY_NUMERATOR =
                "set_aspect_ratio_with_delay_numerator";
        // Calls setPictureInPictureAspectRatio with the aspect ratio specified in the value with a
        // fixed delay
        public static final String EXTRA_SET_ASPECT_RATIO_WITH_DELAY_DENOMINATOR =
                "set_aspect_ratio_with_delay_denominator";
        // Calls onPictureInPictureStateChange with the state specified in the value
        public static final String EXTRA_SET_PIP_STASHED = "set_pip_stashed";
        // Shows this activity over the keyguard
        public static final String EXTRA_SHOW_OVER_KEYGUARD = "show_over_keyguard";
        // Starts the activity (component name) provided by the value at the end of onCreate
        public static final String EXTRA_START_ACTIVITY = "start_activity";
        // Adds a click listener to finish this activity when it is clicked
        public static final String EXTRA_TAP_TO_FINISH = "tap_to_finish";
        // Dismiss keyguard when activity show.
        public static final String EXTRA_DISMISS_KEYGUARD = "dismiss_keyguard";
        // Number of custom actions should be set onto PictureInPictureParams
        public static final String EXTRA_NUMBER_OF_CUSTOM_ACTIONS = "number_of_custom_actions";
        // Whether a custom close action should be set in the PictureInPictureParams.
        public static final String EXTRA_CLOSE_ACTION = "set_pip_close_action";
        // Supplied when a callback is expected for pip
        public static final String EXTRA_SET_PIP_CALLBACK = "set_pip_callback";
        // Supplied when a callback is expected for pip when activity receives onPause
        public static final String EXTRA_PIP_ON_PAUSE_CALLBACK = "pip_on_pause_callback";
        // Result key for obtaining the PictureInPictureUiState#isStashed result
        public static final String UI_STATE_STASHED_RESULT = "ui_state_stashed_result";
        // Result key for obtaining the PictureInPictureUiState#isEnteringPip result
        public static final String UI_STATE_ENTERING_PIP_RESULT = "ui_state_entering_result";
        // Result key for obtaining the Activity#isInPictureInPictureMode result
        public static final String IS_IN_PIP_MODE_RESULT = "is_in_pip_mode_result";
    }

    /**
     * Extra key constants for {@link android.server.wm.app.TopActivity} and
     * {@link TranslucentTopActivity}.
     *
     * TODO(b/73346885): These constants should be in {@link android.server.wm.app.TopActivity}
     * once the activity is moved to test APK.
     */
    public static class TopActivity {
        public static final String EXTRA_FINISH_DELAY = "FINISH_DELAY";
        public static final String EXTRA_FINISH_IN_ON_CREATE = "FINISH_IN_ON_CREATE";
        public static final String ACTION_CONVERT_TO_TRANSLUCENT = "convert_to_translucent";
        public static final String ACTION_CONVERT_FROM_TRANSLUCENT = "convert_from_translucent";
    }

    public static class UnresponsiveActivity {
        public static final String EXTRA_ON_CREATE_DELAY_MS = "ON_CREATE_DELAY_MS";
        public static final String EXTRA_ON_KEYDOWN_DELAY_MS = "ON_KEYDOWN_DELAY_MS";
        public static final String EXTRA_ON_MOTIONEVENT_DELAY_MS = "ON_MOTIONEVENT_DELAY_MS";
        public static final String PROCESS_NAME = ".unresponsive_activity_process";
    }

    public static class RenderService {
        public static final String PROCESS_NAME = ".render_process";
        public static final String EXTRAS_BUNDLE = "EXTRAS_BUNDLE";
        public static final String EXTRAS_DISPLAY_ID = "EXTRAS_DISPLAY_ID";
        public static final String EXTRAS_HOST_TOKEN = "EXTRAS_HOST_TOKEN";
        public static final String BROADCAST_EMBED_CONTENT =
                "android.server.wm.app.RenderService.EMBED_CONTENT";
        public static final String EXTRAS_SURFACE_PACKAGE = "surfacePackage";
    }

    public static class MultiWindowFullscreenActivity {
        public static final String ACTION_REQUEST_FULLSCREEN =
                "android.server.wm.app.MultiWindowFullscreenActivity.action_request_fullscreen";
        public static final String ACTION_RESTORE_FREEFORM =
                "android.server.wm.app.MultiWindowFullscreenActivity.action_restore_freeform";
    }

    /**
     * Extra key constants for {@link android.server.wm.app.VirtualDisplayActivity}.
     *
     * TODO(b/73346885): These constants should be in
     * {@link android.server.wm.app.VirtualDisplayActivity} once the activity is moved to test APK.
     */
    public static class VirtualDisplayActivity {
        public static final String VIRTUAL_DISPLAY_PREFIX = "HostedVirtualDisplay";
        public static final String KEY_CAN_SHOW_WITH_INSECURE_KEYGUARD =
                "can_show_with_insecure_keyguard";
        public static final String KEY_COMMAND = "command";
        public static final String KEY_COUNT = "count";
        public static final String KEY_DENSITY_DPI = "density_dpi";
        public static final String KEY_PUBLIC_DISPLAY = "public_display";
        public static final String KEY_RESIZE_DISPLAY = "resize_display";
        public static final String KEY_SHOW_SYSTEM_DECORATIONS = "show_system_decorations";
        public static final String KEY_PRESENTATION_DISPLAY = "presentation_display";
        public static final String KEY_SUPPORTS_TOUCH = "supports_touch";
        // Value constants of {@link #KEY_COMMAND}.
        public static final String COMMAND_CREATE_DISPLAY = "create_display";
        public static final String COMMAND_DESTROY_DISPLAY = "destroy_display";
        public static final String COMMAND_RESIZE_DISPLAY = "resize_display";
    }

    public static class ClickableToastActivity {
        public static final String ACTION_TOAST_DISPLAYED = "toast_displayed";
        public static final String ACTION_TOAST_TAP_DETECTED = "toast_tap_detected";
    }

    public static class PresentationActivity {
        public static final String KEY_DISPLAY_ID = "display_id";
    }

    public static class LaunchingActivity {
        public static final String KEY_FINISH_BEFORE_LAUNCH = "finish_before_launch";
    }

    public static class OverlayTestService {
        public static final String EXTRA_LAYOUT_PARAMS = "layout_params";
    }

    public static class Notifications {
        public static final String CHANNEL_MAIN = "main";
        public static final int ID_OVERLAY_TEST_SERVICE = 1;
    }

    public static class HideOverlayWindowsActivity {
        public static final String ACTION = "hide_action";
        public static final String PONG = "pong_action";
        public static final String SHOULD_HIDE = "should_hide";
        public static final String REPORT_TOUCH = "report_touch";
        public static final String MOTION_EVENT_EXTRA = "motion_event_extra";
    }

    public static class BackgroundActivityTransition {
        public static final String TRANSITION_REQUESTED = "transition_requested";
    }

    /**
     * Extra constants for {@link android.server.wm.app.KeepClearRectsActivity}.
     */
    public static class KeepClearRectsActivity {
        public static final String EXTRA_KEEP_CLEAR_RECTS = "keep_clear_rects";
    }

    /**
     * Extra constants for {@link android.server.wm.app.UiScalingTestActivity}.
     */
    public static class UiScalingTestActivity {
        public static final String SUBVIEW_ID1 = "compat_scale_subview1";
        public static final String SUBVIEW_ID2 = "compat_scale_subview2";

        public static final String KEY_COMMAND_SUCCESS = "success";
        public static final String KEY_RESOURCES_CONFIG = "resources_config";
        public static final String KEY_SUBVIEW_ID = "subview_id";
        public static final String KEY_TEXT_SIZE = "text_size";
        public static final String KEY_VIEW_SIZE = "view_size";

        public static final String COMMAND_ADD_SUBVIEW = "add_subview";
        public static final String COMMAND_CLEAR_DEFAULT_VIEW = "clear_default_view";
        public static final String COMMAND_GET_SUBVIEW_SIZE = "get_subview_size";

        public static final String COMMAND_GET_RESOURCES_CONFIG = "get_resources_config";
        public static final String COMMAND_UPDATE_RESOURCES_CONFIG = "update_resources_config";
    }

    public static class WallpaperTargetActivity {
        public static final String EXTRA_ENABLE_WALLPAPER_TOUCH = "enable_wallpaper_touch";
    }
    private static ComponentName component(String className) {
        return component(Components.class, className);
    }

    public static String getPackageName() {
        return getPackageName(Components.class);
    }
}
