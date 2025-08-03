/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.car.settings;

import static android.car.feature.Flags.FLAG_CAR_NIGHT_GLOBAL_SETTING;
import static android.car.feature.Flags.FLAG_PERSIST_AP_SETTINGS;

import android.annotation.FlaggedApi;
import android.annotation.SystemApi;

/**
 * System-level, car-related settings.
 *
 * @hide
 */
@SystemApi
public class CarSettings {

    private CarSettings() {
        throw new UnsupportedOperationException("this class only provide constants");
    }

    /**
     * Global car settings, containing preferences that always apply identically
     * to all defined users.  Applications can read these but are not allowed to write;
     * like the "Secure" settings, these are for preferences that the user must
     * explicitly modify through the system UI or specialized APIs for those values.
     *
     * <p>To read/write the global car settings, use {@link android.provider.Settings.Global}
     * with the keys defined here.
     *
     * @hide
     */
    @SystemApi
    public static final class Global {

        private Global() {
            throw new UnsupportedOperationException("this class only provide constants");
        }

        /**
         * Whether default restrictions for users have been set.
         *
         * @hide
         */
        public static final String DEFAULT_USER_RESTRICTIONS_SET =
                "android.car.DEFAULT_USER_RESTRICTIONS_SET";

        /**
         * Developer settings String used to explicitly disable the instrumentation service (when
         * set to {@code "true"}.
         *
         * @hide
         */
        public static final String DISABLE_INSTRUMENTATION_SERVICE =
                "android.car.DISABLE_INSTRUMENTATION_SERVICE";

        /**
         * Developer settings String used to explicitly enable the user switch message when
         * set to {@code "true"}.
         *
         * @hide
         */
        public static final String ENABLE_USER_SWITCH_DEVELOPER_MESSAGE =
                "android.car.ENABLE_USER_SWITCH_DEVELOPER_MESSAGE";

        /**
         * User id of the last foreground user
         *
         * @hide
         */
        public static final String LAST_ACTIVE_USER_ID =
                        "android.car.LAST_ACTIVE_USER_ID";

        /**
         * User id of the last persistent (i.e, not counting ephemeral guests) foreground user
         *
         * @hide
         */
        public static final String LAST_ACTIVE_PERSISTENT_USER_ID =
                        "android.car.LAST_ACTIVE_PERSISTENT_USER_ID";

        /**
         * Defines global runtime overrides to system bar policy.
         *
         * See {@link com.android.systemui.wm.BarControlPolicy} for value format.
         *
         * @hide
         */
        public static final String SYSTEM_BAR_VISIBILITY_OVERRIDE =
                "android.car.SYSTEM_BAR_VISIBILITY_OVERRIDE";

        /**
         * Defines non-current visible users to assign per each occupant zone.
         *
         * <p>The value of this will be a ',' separated list of zoneId:userId. zoneId and userId
         * should be a string of decimal integer. Example can be "1:10,2:11" where zone 1 has
         * user 10 and zone 2 has user 11 allocated.
         *
         * <p>When system boots up, car service will allocate those users to the specified zones.
         * If any entry in the value is invalid or if there are duplicate entries, the value will be
         * ignored and no user will be assigned.
         *
         * @hide
         */
        @SystemApi
        public static final String GLOBAL_VISIBLE_USER_ALLOCATION_PER_ZONE =
                "android.car.GLOBAL_VISIBLE_USER_ALLOCATION_PER_ZONE";

        /**
         * Defines passenger displays to lock their touch input.
         *
         * <p> The value of this will be a ',' separated list of display's unique id. For example,
         * "local:4630946674560563248,local:4630946674560563349" with input lock enabled for both
         * displays.
         *
         * <p> Input lock will be applied to those passenger displays. If any entry in the value
         * is invalid, then the invalid entry is ignored. If there are duplicate entries, then
         * only one entry is valid and the other duplicates are ignored.
         * @hide
         */
        @SystemApi
        public static final String DISPLAY_INPUT_LOCK =
                        "android.car.DISPLAY_INPUT_LOCK";

        /**
         * Defines display power mode to assign per each display.
         *
         * <p>The value of this will be a ',' separated list of displayPort:mode.
         * display port and mode should be a string of decimal integer.
         * Example can be "0:2,1:0,2:1" where display 0 set mode 2, display 1 set mode 0
         * and display 2 set mode 1 allocated.
         *
         * <p>When system boots up, car service will set those modes to the specified displays.
         * If any entry in the value is invalid, the value will be ignored and no mode will be set.
         * If there are duplicate entries, the last entry will be applied.
         *
         * <p>The mode is an integer (0, 1 or 2) where:
         * <ul>
         * <li>0 indicates OFF should applied to intentionally turn off the display and not be
         * allowed to manually turn on the display
         * <li>1 indicates ON should be applied to screen off timeout and allowed to manually turn
         * off the display.
         * <li>2 indicates ALWAYS ON should be applied to keep the display on and allowed to
         * manually turn off the display
         * </ul>
         *
         * @hide
         */
        @SystemApi
        public static final String DISPLAY_POWER_MODE = "android.car.DISPLAY_POWER_MODE";

        /**
         * Indicates which {@link CarNightService.DayNightSensorMode} is currently set.
         *
         * <p>The mode is an integer (0, 1 or 2) where:
         * <ul>
         * <li>0 indicates sensor mode, where the {@link VehicleProperty.NIGHT_MODE} will be used
         * to set the system's UI mode.
         * <li>1 indicates the day UI mode should always be used
         * <li>2 indicates the night UI mode should always be used
         * </ul>
         *
         * @hide
         */
        @FlaggedApi(FLAG_CAR_NIGHT_GLOBAL_SETTING)
        @SystemApi
        public static final String FORCED_DAY_NIGHT_MODE = "android.car.FORCED_DAY_NIGHT_MODE";

        /**
         * Enables persistent tethering when set to {@code "true"}.
         *
         * <p>When enabled, tethering is started when the car is started given that the hotspot
         * was enabled at shutdown and all tethering sessions will remain on even if no devices are
         * connected to it.
         *
         * <p>When disabled, hotspot will turn off automatically if no devices are connected and
         * will no longer persist through drives.
         *
         * @hide
         */
        @FlaggedApi(FLAG_PERSIST_AP_SETTINGS)
        @SystemApi
        public static final String ENABLE_PERSISTENT_TETHERING =
                "android.car.ENABLE_PERSISTENT_TETHERING";
    }

    /**
     * Default garage mode wake up time 00:00
     *
     * @hide
     */
    public static final int[] DEFAULT_GARAGE_MODE_WAKE_UP_TIME = {0, 0};

    /**
     * Default garage mode maintenance window 10 mins.
     *
     * @hide
     */
    public static final int DEFAULT_GARAGE_MODE_MAINTENANCE_WINDOW = 10 * 60 * 1000;

    /**
     * @hide
     */
    @SystemApi
    public static final class Secure {

        private Secure() {
            throw new UnsupportedOperationException("this class only provide constants");
        }

        /**
         * Key to indicate whether audio focus requests for
         * {@link android.hardware.automotive.audiocontrol.V1_0.ContextNumber.NAVIGATION} should
         * be rejected if focus is currently held by
         * {@link android.hardware.automotive.audiocontrol.V1_0.ContextNumber.CALL}.
         * <p>The value is a boolean (1 or 0) where:
         * <ul>
         * <li>1 indicates {@code NAVIGATION} should be rejected when a {@code CALL} is in progress.
         * <li>0 indicates {@code NAVIGATION} and {@code CALL} should be allowed to hold focus
         * concurrently.
         * </ul>
         *
         * <p>Recommended {@code false} as default value.
         *
         * @hide
         */
        @SystemApi
        public static final String KEY_AUDIO_FOCUS_NAVIGATION_REJECTED_DURING_CALL =
                "android.car.KEY_AUDIO_FOCUS_NAVIGATION_REJECTED_DURING_CALL";

        /**
         * Key to indicate if mute state should be persisted across boot cycles.
         * <p>The value is a boolean (1 or 0) where:
         * <ul>
         * <li>1 indicates volume group mute states should be persisted across boot cycles.
         * <li>0 indicates volume group mute states should not be persisted across boot cycles.
         * </ul>
         *
         * @hide
         */
        @SystemApi
        public static final String KEY_AUDIO_PERSIST_VOLUME_GROUP_MUTE_STATES =
                "android.car.KEY_AUDIO_PERSIST_VOLUME_GROUP_MUTE_STATES";

        /**
         * Key for a list of devices to automatically connect on Bluetooth.
         * Written to and read by {@link com.android.car.BluetoothDeviceManager}
         * @hide
         */
        public static final String KEY_BLUETOOTH_DEVICES =
                "android.car.KEY_BLUETOOTH_DEVICES";

        /**
         * Key for storing temporarily-disconnected devices and profiles.
         * Read and written by {@link com.android.car.BluetoothProfileInhibitManager}.
         * @hide
         */
        public static final String KEY_BLUETOOTH_PROFILES_INHIBITED =
                "android.car.BLUETOOTH_PROFILES_INHIBITED";

        /**
         * Key to enable / disable rotary key event filtering. When enabled, a USB keyboard can be
         * used as a stand-in for a rotary controller.
         * The value is boolean (1 or 0).
         * @hide
         */
        @SystemApi
        public static final String KEY_ROTARY_KEY_EVENT_FILTER =
                "android.car.ROTARY_KEY_EVENT_FILTER";

        /**
         * Key to enable / disable initial notice screen that will be shown for all user-starting
         * moments including cold boot, wake up from suspend, and user switching.
         * The value is boolean (1 or 0).
         * @hide
         */
        @SystemApi
        public static final String KEY_ENABLE_INITIAL_NOTICE_SCREEN_TO_USER =
                "android.car.ENABLE_INITIAL_NOTICE_SCREEN_TO_USER";

        /**
         * Key to indicate Setup Wizard is in progress. It differs from USER_SETUP_COMPLETE in
         * that this flag can be reset to 0 in deferred Setup Wizard flow.
         * The value is boolean (1 or 0).
         * @hide
         */
        @SystemApi
        public static final String KEY_SETUP_WIZARD_IN_PROGRESS =
                "android.car.SETUP_WIZARD_IN_PROGRESS";

        /**
         * Key for a {@code ;} separated list of packages disabled on resource overuse.
         *
         * <p>The value is written by {@link com.android.car.watchdog.CarWatchdogService}.
         *
         * <p>The value is read by user interfaces (such as launcher) that show applications
         * disabled on resource overuse. When a user selects any application from this list,
         * the user interface should either enable the application immediately or provide user
         * affordance to enable the application when the driving conditions are safe.
         *
         * <p>When an application (which is on this list) is enabled, CarService will immediately
         * remove the application's package name form the list.
         * @hide
         */
        @SystemApi
        public static final String KEY_PACKAGES_DISABLED_ON_RESOURCE_OVERUSE =
                "android.car.KEY_PACKAGES_DISABLED_ON_RESOURCE_OVERUSE";

        /**
        * Key for an int value to indicate whether the user has accepted the Terms of
        * Service.
        *
        * <p>The value is an int value where:
        * <ul>
        * <li>0 - the acceptance value is unknown. In this case, functionality
        * should not be restricted.
        * <li>1 - the acceptance value is {@code false}. In this case, some system
        * functionality is restricted.
        * <li>2 - the acceptance value is {@code true}. In this case, system functionality is
        * not restricted.
        * </ul>
        *
        * <p>Recommended 0 as default value.
        *
        * @hide
        */
       @SystemApi
       public static final String KEY_USER_TOS_ACCEPTED = "android.car.KEY_USER_TOS_ACCEPTED";


       /**
        * Key for a string value to indicate which apps are disabled because the
        * user has not accepted the Terms of Service.
        *
        * <p>The value is a string value of comma-separated package names. For example,
        * {@code "com.company.maps,com.company.voiceassistant,com.company.appstore"}
        *
        * <p>Recommended "" as default value.
        *
        * @hide
        */
       @SystemApi
       public static final String KEY_UNACCEPTED_TOS_DISABLED_APPS =
               "android.car.KEY_UNACCEPTED_TOS_DISABLED_APPS";

        /**
         * Defines non-current visible users to assign per each occupant zone.
         *
         * <p>For the format of the value, check {@link Global#VISIBLE_USER_ALLOCATION_PER_ZONE}.
         * This is per user setting and system will apply this when this user is the
         * current user during the boot up.
         *
         * <p>If both {@link Global#VISIBLE_USER_ALLOCATION_PER_ZONE} and this value is
         * set, this value will be used and {@link Global#VISIBLE_USER_ALLOCATION_PER_ZONE} will
         * be ignored.
         *
         * @hide
         */
        @SystemApi
        public static final String VISIBLE_USER_ALLOCATION_PER_ZONE =
                "android.car.VISIBLE_USER_ALLOCATION_PER_ZONE";

        /**
         * Key to indicate whether to allow the driver user to allow controlling media sessions of
         * a passenger user.
         *
         * <p>This is per user setting and the drvier's Media Control Center app will query this
         * to check whether it can connect/control other user's media session.
         * The value type is boolean (1 for true, or 0 for false. false by default).
         *
         * @hide
         */
        @SystemApi
        public static final String KEY_DRIVER_ALLOWED_TO_CONTROL_MEDIA =
                "android.car.DRIVER_ALLOWED_TO_CONTROL_MEDIA";
    }
}
