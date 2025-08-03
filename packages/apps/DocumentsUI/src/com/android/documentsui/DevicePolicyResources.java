/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.documentsui;

import android.app.admin.DevicePolicyManager;

/**
 * Class containing the required identifiers to update device management resources.
 *
 * <p>See {@link DevicePolicyManager#getDrawable} and {@link DevicePolicyManager#getString}.
 */
public class DevicePolicyResources {

    /**
     * Class containing the identifiers used to update device management-related system strings.
     */
    public static final class Strings {
        private static final String PREFIX = "DocumentsUi.";

        /**
         * An ID for any string that can't be updated.
         */
        public static final String UNDEFINED = "UNDEFINED";

        /**
         * Title for error message shown when work profile is turned off.
         */
        public static final String WORK_PROFILE_OFF_ERROR_TITLE =
                PREFIX + "WORK_PROFILE_OFF_ERROR_TITLE";

        /**
         * Button text shown when work profile is turned off.
         */
        public static final String WORK_PROFILE_OFF_ENABLE_BUTTON =
                PREFIX + "WORK_PROFILE_OFF_ENABLE_BUTTON";

        /**
         * Title for error message shown when a user's IT admin does not allow the user to
         * select work files from a personal app.
         */
        public static final String CANT_SELECT_WORK_FILES_TITLE =
                PREFIX + "CANT_SELECT_WORK_FILES_TITLE";

        /**
         * Message shown when a user's IT admin does not allow the user to select work files
         * from a personal app.
         */
        public static final String CANT_SELECT_WORK_FILES_MESSAGE =
                PREFIX + "CANT_SELECT_WORK_FILES_MESSAGE";

        /**
         * Title for error message shown when a user's IT admin does not allow the user to
         * select personal files from a work app.
         */
        public static final String CANT_SELECT_PERSONAL_FILES_TITLE =
                PREFIX + "CANT_SELECT_PERSONAL_FILES_TITLE";

        /**
         * Message shown when a user's IT admin does not allow the user to select personal files
         * from a work app.
         */
        public static final String CANT_SELECT_PERSONAL_FILES_MESSAGE =
                PREFIX + "CANT_SELECT_PERSONAL_FILES_MESSAGE";

        /**
         * Title for error message shown when a user's IT admin does not allow the user to save
         * files from their personal profile to their work profile.
         */
        public static final String CANT_SAVE_TO_WORK_TITLE =
                PREFIX + "CANT_SAVE_TO_WORK_TITLE";

        /**
         * Message shown when a user's IT admin does not allow the user to save files from their
         * personal profile to their work profile.
         */
        public static final String CANT_SAVE_TO_WORK_MESSAGE =
                PREFIX + "CANT_SAVE_TO_WORK_MESSAGE";

        /**
         * Title for error message shown when a user's IT admin does not allow the user to save
         * files from their work profile to their personal profile.
         */
        public static final String CANT_SAVE_TO_PERSONAL_TITLE =
                PREFIX + "CANT_SAVE_TO_PERSONAL_TITLE";

        /**
         * Message shown when a user's IT admin does not allow the user to save files from their
         * work profile to their personal profile.
         */
        public static final String CANT_SAVE_TO_PERSONAL_MESSAGE =
                PREFIX + "CANT_SAVE_TO_PERSONAL_MESSAGE";

        /**
         * Title for error message shown when a user tries to do something on their work
         * device, but that action isn't allowed by their IT admin.
         */
        public static final String CROSS_PROFILE_NOT_ALLOWED_TITLE =
                PREFIX + "CROSS_PROFILE_NOT_ALLOWED_TITLE";

        /**
         * Message shown when a user tries to do something on their work device, but that action
         * isn't allowed by their IT admin.
         */
        public static final String CROSS_PROFILE_NOT_ALLOWED_MESSAGE =
                PREFIX + "CROSS_PROFILE_NOT_ALLOWED_MESSAGE";

        /**
         * Content description text that's spoken by a screen reader for previewing a work file
         * before opening it. Accepts file name as a param.
         */
        public static final String PREVIEW_WORK_FILE_ACCESSIBILITY =
                PREFIX + "PREVIEW_WORK_FILE_ACCESSIBILITY";

        /**
         * Label for tab and sidebar to indicate personal content.
         */
        public static final String PERSONAL_TAB = PREFIX + "PERSONAL_TAB";

        /**
         * Label for tab and sidebar tab to indicate work content
         */
        public static final String WORK_TAB = PREFIX + "WORK_TAB";

    }

    /**
     * Class containing the identifiers used to update device management-related system drawable.
     */
    public static final class Drawables {
        /**
         * Specifically used to badge work profile app icons.
         */
        public static final String WORK_PROFILE_ICON_BADGE = "WORK_PROFILE_ICON_BADGE";

        /**
         * General purpose work profile icon (i.e. generic icon badging). For badging app icons
         * specifically, see {@link #WORK_PROFILE_ICON_BADGE}.
         */
        public static final String WORK_PROFILE_ICON = "WORK_PROFILE_ICON";

        /**
         * General purpose icon representing the work profile off state.
         */
        public static final String WORK_PROFILE_OFF_ICON = "WORK_PROFILE_OFF_ICON";

        /**
         * General purpose icon for the work profile user avatar.
         */
        public static final String WORK_PROFILE_USER_ICON = "WORK_PROFILE_USER_ICON";

        /**
         * Class containing the style identifiers used to update device management-related system
         * drawable.
         */
        public static final class Style {

            /**
             * A style identifier indicating that the updatable drawable should use the default
             * style.
             */
            public static final String DEFAULT = "DEFAULT";

            /**
             * A style identifier indicating that the updatable drawable has a solid color fill.
             */
            public static final String SOLID_COLORED = "SOLID_COLORED";

            /**
             * A style identifier indicating that the updatable drawable has a solid non-colored
             * fill.
             */
            public static final String SOLID_NOT_COLORED = "SOLID_NOT_COLORED";

            /**
             * A style identifier indicating that the updatable drawable is an outline.
             */
            public static final String OUTLINE = "OUTLINE";
        }
    }
}
