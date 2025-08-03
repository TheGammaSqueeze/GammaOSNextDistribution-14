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

package com.android.permissioncontroller.safetycenter;

/** App-global constants */
public class SafetyCenterConstants {
    /**
     * Key for the argument noting that it is the quick settings safety center dashboard fragment
     */
    public static final String QUICK_SETTINGS_SAFETY_CENTER_FRAGMENT =
            "QUICK_SETTINGS_SAFETY_CENTER_FRAGMENT";

    public static final String EXPAND_ISSUE_GROUP_QS_FRAGMENT_KEY =
            "expand_issue_group_qs_fragment_key";

    public static final String EXTRA_NAVIGATION_SOURCE = "navigation_source_intent_extra";

    /** Intent extra indicating whether a subpage in Safety Center was opened from the homepage */
    public static final String EXTRA_OPENED_FROM_HOMEPAGE = "opened_from_homepage_intent_extra";

    /** Suffix used to identify a source in the Safety Center personal profile */
    public static final String PERSONAL_PROFILE_SUFFIX = "personal";

    /** Suffix used to identify a source in the Safety Center work profile */
    public static final String WORK_PROFILE_SUFFIX = "work";

    /** Intent extra representing the preference key of a search result */
    public static final String EXTRA_SETTINGS_FRAGMENT_ARGS_KEY = ":settings:fragment_args_key";

    /** Identifier for the group of privacy safety sources */
    public static final String PRIVACY_SOURCES_GROUP_ID = "AndroidPrivacySources";
}
