/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.wallpaper.module;

/**
 * Key constants used to index into implementations of {@link WallpaperPreferences}.
 */
public class WallpaperPreferenceKeys {
    public static final String KEY_WALLPAPER_PRESENTATION_MODE = "wallpaper_presentation_mode";

    public static final String KEY_HOME_WALLPAPER_ATTRIB_1 = "home_wallpaper_attribution_line_1";
    public static final String KEY_HOME_WALLPAPER_ATTRIB_2 = "home_wallpaper_attribution_line_2";
    public static final String KEY_HOME_WALLPAPER_ATTRIB_3 = "home_wallpaper_attribution_line_3";
    public static final String KEY_HOME_WALLPAPER_ACTION_URL = "home_wallpaper_action_url";
    public static final String KEY_HOME_WALLPAPER_COLLECTION_ID = "home_wallpaper_collection_id";
    public static final String KEY_HOME_WALLPAPER_HASH_CODE = "home_wallpaper_hash_code";

    public static final String KEY_LOCK_WALLPAPER_ATTRIB_1 = "lock_wallpaper_attribution_line_1";
    public static final String KEY_LOCK_WALLPAPER_ATTRIB_2 = "lock_wallpaper_attribution_line_2";
    public static final String KEY_LOCK_WALLPAPER_ATTRIB_3 = "lock_wallpaper_attribution_line_3";
    public static final String KEY_LOCK_WALLPAPER_ACTION_URL = "lock_wallpaper_action_url";
    public static final String KEY_LOCK_WALLPAPER_HASH_CODE = "lock_wallpaper_hash_code";
    public static final String KEY_LOCK_WALLPAPER_COLLECTION_ID = "lock_wallpaper_collection_id";
    public static final String KEY_HAS_PREVIEW_TOOLTIP_BEEN_SHOWN =
            "has_preview_tooltip_been_shown";

    /**
     * Preferences with these keys should not be backed up
     */
    public interface NoBackupKeys {
        String KEY_APP_LAUNCH_COUNT = "app_launch_count";
        String KEY_FIRST_LAUNCH_DATE_SINCE_SETUP =
                "first_launch_date_since_setup";
        String KEY_FIRST_WALLPAPER_APPLY_DATE_SINCE_SETUP =
                "first_wallpaper_apply_date_since_setup";
        String KEY_HOME_WALLPAPER_BASE_IMAGE_URL =
                "home_wallpaper_base_image_url";
        String KEY_HOME_WALLPAPER_MANAGER_ID = "home_wallpaper_id";
        String KEY_HOME_WALLPAPER_RECENTS_KEY = "home_wallpaper_recents_key";
        String KEY_HOME_WALLPAPER_REMOTE_ID = "home_wallpaper_remote_id";
        String KEY_HOME_WALLPAPER_BACKING_FILE = "home_wallpaper_backing_file";
        String KEY_LOCK_WALLPAPER_MANAGER_ID = "lock_wallpaper_id";
        String KEY_LOCK_WALLPAPER_RECENTS_KEY = "lock_wallpaper_recents_key";
        String KEY_LOCK_WALLPAPER_REMOTE_ID = "lock_wallpaper_remote_id";
        String KEY_LOCK_WALLPAPER_BACKING_FILE = "lock_wallpaper_backing_file";
        String KEY_DAILY_ROTATION_TIMESTAMPS = "daily_rotation_timestamps";
        String KEY_DAILY_WALLPAPER_ENABLED_TIMESTAMP =
                "daily_wallpaper_enabled_timestamp";
        String KEY_LAST_DAILY_LOG_TIMESTAMP = "last_daily_log_timestamp";
        String KEY_LAST_APP_ACTIVE_TIMESTAMP = "last_app_active_timestamp";
        String KEY_LAST_ROTATION_STATUS = "last_rotation_status";
        String KEY_LAST_ROTATION_STATUS_TIMESTAMP =
                "last_rotation_status_timestamp";
        String KEY_LAST_SYNC_TIMESTAMP = "last_sync_timestamp";
        String KEY_PENDING_WALLPAPER_SET_STATUS =
                "pending_wallpaper_set_status";
        String KEY_PENDING_DAILY_WALLPAPER_UPDATE_STATUS =
                "pending_daily_wallpaper_update_status";
        String KEY_NUM_DAYS_DAILY_ROTATION_FAILED =
                "num_days_daily_rotation_failed";
        String KEY_NUM_DAYS_DAILY_ROTATION_NOT_ATTEMPTED =
                "num_days_daily_rotation_not_attempted";
        String KEY_HOME_WALLPAPER_SERVICE_NAME = "home_wallpaper_service_name";
        String KEY_LOCK_WALLPAPER_SERVICE_NAME = "lock_wallpaper_service_name";
        String KEY_PREVIEW_WALLPAPER_COLOR_ID = "preview_wallpaper_color_id";
        String KEY_HOME_WALLPAPER_EFFECTS = "home_wallpaper_effects";
        String KEY_LOCK_WALLPAPER_EFFECTS = "lock_wallpaper_effects";
    }
}
