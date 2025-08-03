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

package com.android.permission.safetylabel;

import static java.lang.annotation.RetentionPolicy.SOURCE;

import android.annotation.StringDef;
import android.util.ArrayMap;

import java.lang.annotation.Retention;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Constants and util methods for determining valid {@link String} data categories for usage within
 * {@link SafetyLabel}, {@link DataCategory}, and {@link DataType}
 */
public class DataTypeConstants {

    /**
     * List of valid Safety Label data collection/sharing types for {@link
     * DataCategoryConstants#CATEGORY_PERSONAL}
     */
    @Retention(SOURCE)
    @StringDef(
            prefix = "PERSONAL_",
            value = {
                PERSONAL_NAME,
                PERSONAL_EMAIL_ADDRESS,
                PERSONAL_PHYSICAL_ADDRESS,
                PERSONAL_PHONE_NUMBER,
                PERSONAL_RACE_ETHNICITY,
                PERSONAL_POLITICAL_OR_RELIGIOUS_BELIEFS,
                PERSONAL_SEXUAL_ORIENTATION_OR_GENDER_IDENTITY,
                PERSONAL_IDENTIFIERS,
                PERSONAL_OTHER,
            })
    public @interface PersonalType {}

    public static final String PERSONAL_NAME = "name";
    public static final String PERSONAL_EMAIL_ADDRESS = "email_address";
    public static final String PERSONAL_PHYSICAL_ADDRESS = "physical_address";
    public static final String PERSONAL_PHONE_NUMBER = "phone_number";
    public static final String PERSONAL_RACE_ETHNICITY = "race_ethnicity";
    public static final String PERSONAL_POLITICAL_OR_RELIGIOUS_BELIEFS =
            "political_or_religious_beliefs";
    public static final String PERSONAL_SEXUAL_ORIENTATION_OR_GENDER_IDENTITY =
            "sexual_orientation_or_gender_identity";
    public static final String PERSONAL_IDENTIFIERS = "personal_identifiers";
    public static final String PERSONAL_OTHER = "other";

    @PersonalType
    private static final Set<String> VALID_TYPES_PERSONAL =
            Collections.unmodifiableSet(
                    new HashSet<>(
                            Arrays.asList(
                                    PERSONAL_NAME,
                                    PERSONAL_EMAIL_ADDRESS,
                                    PERSONAL_PHYSICAL_ADDRESS,
                                    PERSONAL_PHONE_NUMBER,
                                    PERSONAL_RACE_ETHNICITY,
                                    PERSONAL_POLITICAL_OR_RELIGIOUS_BELIEFS,
                                    PERSONAL_SEXUAL_ORIENTATION_OR_GENDER_IDENTITY,
                                    PERSONAL_IDENTIFIERS,
                                    PERSONAL_OTHER)));

    /**
     * List of valid Safety Label data collection/sharing types for {@link
     * DataCategoryConstants#CATEGORY_FINANCIAL}
     */
    @Retention(SOURCE)
    @StringDef(
            prefix = "FINANCIAL_",
            value = {
                FINANCIAL_CARD_BANK_ACCOUNT,
                FINANCIAL_PURCHASE_HISTORY,
                FINANCIAL_CREDIT_SCORE,
                FINANCIAL_OTHER,
            })
    public @interface FinancialType {}

    public static final String FINANCIAL_CARD_BANK_ACCOUNT = "card_bank_account";
    public static final String FINANCIAL_PURCHASE_HISTORY = "purchase_history";
    public static final String FINANCIAL_CREDIT_SCORE = "credit_score";
    public static final String FINANCIAL_OTHER = "other";

    @FinancialType
    private static final Set<String> VALID_TYPES_FINANCIAL =
            Collections.unmodifiableSet(
                    new HashSet<>(
                            Arrays.asList(
                                    FINANCIAL_CARD_BANK_ACCOUNT,
                                    FINANCIAL_PURCHASE_HISTORY,
                                    FINANCIAL_CREDIT_SCORE,
                                    FINANCIAL_OTHER)));

    /**
     * List of valid Safety Label data collection/sharing types for {@link
     * DataCategoryConstants#CATEGORY_LOCATION}
     */
    @Retention(SOURCE)
    @StringDef(
            prefix = "LOCATION_",
            value = {
                LOCATION_APPROX_LOCATION,
                LOCATION_PRECISE_LOCATION,
            })
    public @interface LocationType {}

    public static final String LOCATION_APPROX_LOCATION = "approx_location";
    public static final String LOCATION_PRECISE_LOCATION = "precise_location";

    @LocationType
    private static final Set<String> VALID_TYPES_LOCATION =
            Collections.unmodifiableSet(
                    new HashSet<>(
                            Arrays.asList(LOCATION_APPROX_LOCATION, LOCATION_PRECISE_LOCATION)));

    /**
     * List of valid Safety Label data collection/sharing types for {@link
     * DataCategoryConstants#CATEGORY_EMAIL_TEXT_MESSAGE}
     */
    @Retention(SOURCE)
    @StringDef(
            prefix = "EMAIL_TEXT_MESSAGE_",
            value = {
                EMAIL_TEXT_MESSAGE_EMAILS,
                EMAIL_TEXT_MESSAGE_TEXT_MESSAGES,
                EMAIL_TEXT_MESSAGE_OTHER,
            })
    public @interface EmailTextMessageType {}

    public static final String EMAIL_TEXT_MESSAGE_EMAILS = "emails";
    public static final String EMAIL_TEXT_MESSAGE_TEXT_MESSAGES = "text_messages";
    public static final String EMAIL_TEXT_MESSAGE_OTHER = "other";

    @EmailTextMessageType
    private static final Set<String> VALID_TYPES_EMAIL_TEXT_MESSAGE =
            Collections.unmodifiableSet(
                    new HashSet<>(
                            Arrays.asList(
                                    EMAIL_TEXT_MESSAGE_EMAILS,
                                    EMAIL_TEXT_MESSAGE_TEXT_MESSAGES,
                                    EMAIL_TEXT_MESSAGE_OTHER)));

    /**
     * List of valid Safety Label data collection/sharing types for {@link
     * DataCategoryConstants#CATEGORY_PHOTO_VIDEO}
     */
    @Retention(SOURCE)
    @StringDef(
            prefix = "PHOTO_VIDEO_",
            value = {
                PHOTO_VIDEO_PHOTOS,
                PHOTO_VIDEO_VIDEOS,
            })
    public @interface PhotoVideoType {}

    public static final String PHOTO_VIDEO_PHOTOS = "photos";
    public static final String PHOTO_VIDEO_VIDEOS = "videos";

    @PhotoVideoType
    private static final Set<String> VALID_TYPES_PHOTO_VIDEO =
            Collections.unmodifiableSet(
                    new HashSet<>(Arrays.asList(PHOTO_VIDEO_PHOTOS, PHOTO_VIDEO_VIDEOS)));

    /**
     * List of valid Safety Label data collection/sharing types for {@link
     * DataCategoryConstants#CATEGORY_AUDIO}
     */
    @Retention(SOURCE)
    @StringDef(
            prefix = "AUDIO_",
            value = {AUDIO_SOUND_RECORDINGS, AUDIO_MUSIC_FILES, AUDIO_OTHER})
    public @interface AudioType {}

    public static final String AUDIO_SOUND_RECORDINGS = "sound_recordings";
    public static final String AUDIO_MUSIC_FILES = "music_files";
    public static final String AUDIO_OTHER = "other";

    @AudioType
    private static final Set<String> VALID_TYPES_AUDIO =
            Collections.unmodifiableSet(
                    new HashSet<>(
                            Arrays.asList(AUDIO_SOUND_RECORDINGS, AUDIO_MUSIC_FILES, AUDIO_OTHER)));

    /**
     * List of valid Safety Label data collection/sharing types for {@link
     * DataCategoryConstants#CATEGORY_STORAGE}
     */
    @Retention(SOURCE)
    @StringDef(
            prefix = "STORAGE_",
            value = {
                STORAGE_FILES_DOCS,
            })
    public @interface StorageType {}

    public static final String STORAGE_FILES_DOCS = "files_docs";

    @StorageType
    private static final Set<String> VALID_TYPES_STORAGE =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList(STORAGE_FILES_DOCS)));

    /**
     * List of valid Safety Label data collection/sharing types for {@link
     * DataCategoryConstants#CATEGORY_HEALTH_FITNESS}
     */
    @Retention(SOURCE)
    @StringDef(
            prefix = "HEALTH_FITNESS_",
            value = {
                HEALTH_FITNESS_HEALTH,
                HEALTH_FITNESS_FITNESS,
            })
    public @interface HealthFitnessType {}

    public static final String HEALTH_FITNESS_HEALTH = "health";
    public static final String HEALTH_FITNESS_FITNESS = "fitness";

    @HealthFitnessType
    private static final Set<String> VALID_TYPES_HEALTH_FITNESS =
            Collections.unmodifiableSet(
                    new HashSet<>(Arrays.asList(HEALTH_FITNESS_HEALTH, HEALTH_FITNESS_FITNESS)));

    /**
     * List of valid Safety Label data collection/sharing types for {@link
     * DataCategoryConstants#CATEGORY_CONTACTS}
     */
    @Retention(SOURCE)
    @StringDef(
            prefix = "CONTACTS_",
            value = {
                CONTACTS_CONTACTS,
            })
    public @interface ContactsType {}

    public static final String CONTACTS_CONTACTS = "contacts";

    @ContactsType
    private static final Set<String> VALID_TYPES_CONTACTS =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList(CONTACTS_CONTACTS)));

    /**
     * List of valid Safety Label data collection/sharing types for {@link
     * DataCategoryConstants#CATEGORY_CALENDAR}
     */
    @Retention(SOURCE)
    @StringDef(
            prefix = "CALENDAR_",
            value = {
                CALENDAR_CALENDAR,
            })
    public @interface CalendarType {}

    public static final String CALENDAR_CALENDAR = "calendar";

    @CalendarType
    private static final Set<String> VALID_TYPES_CALENDAR =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList(CALENDAR_CALENDAR)));

    /**
     * List of valid Safety Label data collection/sharing types for {@link
     * DataCategoryConstants#CATEGORY_IDENTIFIERS}
     */
    @Retention(SOURCE)
    @StringDef(
            prefix = "IDENTIFIERS_",
            value = {
                IDENTIFIERS_OTHER,
            })
    public @interface IdentifiersType {}

    public static final String IDENTIFIERS_OTHER = "other";

    @IdentifiersType
    private static final Set<String> VALID_TYPES_IDENTIFIERS =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList(IDENTIFIERS_OTHER)));

    /**
     * List of valid Safety Label data collection/sharing types for {@link
     * DataCategoryConstants#CATEGORY_APP_PERFORMANCE}
     */
    @Retention(SOURCE)
    @StringDef(
            prefix = "APP_PERFORMANCE_",
            value = {
                APP_PERFORMANCE_CRASH_LOGS,
                APP_PERFORMANCE_PERFORMANCE_DIAGNOSTICS,
                APP_PERFORMANCE_OTHER,
            })
    public @interface AppPerformanceType {}

    public static final String APP_PERFORMANCE_CRASH_LOGS = "crash_logs";
    public static final String APP_PERFORMANCE_PERFORMANCE_DIAGNOSTICS = "performance_diagnostics";
    public static final String APP_PERFORMANCE_OTHER = "other";

    @AppPerformanceType
    private static final Set<String> VALID_TYPES_APP_PERFORMANCE =
            Collections.unmodifiableSet(
                    new HashSet<>(
                            Arrays.asList(
                                    APP_PERFORMANCE_CRASH_LOGS,
                                    APP_PERFORMANCE_PERFORMANCE_DIAGNOSTICS,
                                    APP_PERFORMANCE_OTHER)));

    /**
     * List of valid Safety Label data collection/sharing types for {@link
     * DataCategoryConstants#CATEGORY_ACTIONS_IN_APP}
     */
    @Retention(SOURCE)
    @StringDef(
            prefix = "ACTIONS_IN_APP_",
            value = {
                ACTIONS_IN_APP_USER_INTERACTION,
                ACTIONS_IN_APP_IN_APP_SEARCH_HISTORY,
                ACTIONS_IN_APP_INSTALLED_APPS,
                ACTIONS_IN_APP_USER_GENERATED_CONTENT,
                ACTIONS_IN_APP_OTHER,
            })
    public @interface ActionsInAppType {}

    public static final String ACTIONS_IN_APP_USER_INTERACTION = "user_interaction";
    public static final String ACTIONS_IN_APP_IN_APP_SEARCH_HISTORY = "in_app_search_history";
    public static final String ACTIONS_IN_APP_INSTALLED_APPS = "installed_apps";
    public static final String ACTIONS_IN_APP_USER_GENERATED_CONTENT = "user_generated_content";
    public static final String ACTIONS_IN_APP_OTHER = "other";

    @ActionsInAppType
    private static final Set<String> VALID_TYPES_ACTIONS_IN_APP =
            Collections.unmodifiableSet(
                    new HashSet<>(
                            Arrays.asList(
                                    ACTIONS_IN_APP_USER_INTERACTION,
                                    ACTIONS_IN_APP_IN_APP_SEARCH_HISTORY,
                                    ACTIONS_IN_APP_INSTALLED_APPS,
                                    ACTIONS_IN_APP_USER_GENERATED_CONTENT,
                                    ACTIONS_IN_APP_OTHER)));

    /**
     * List of valid Safety Label data collection/sharing types for {@link
     * DataCategoryConstants#CATEGORY_SEARCH_AND_BROWSING}
     */
    @Retention(SOURCE)
    @StringDef(
            prefix = "SEARCH_AND_BROWSING_",
            value = {
                SEARCH_AND_BROWSING_WEB_BROWSING_HISTORY,
            })
    public @interface SearchAndBrowsingType {}

    public static final String SEARCH_AND_BROWSING_WEB_BROWSING_HISTORY = "web_browsing_history";

    @SearchAndBrowsingType
    private static final Set<String> VALID_TYPES_SEARCH_AND_BROWSING =
            Collections.unmodifiableSet(
                    new HashSet<>(Arrays.asList(SEARCH_AND_BROWSING_WEB_BROWSING_HISTORY)));

    private static final Map<String, Set<String>> VALID_TYPES_FOR_CATEGORY_MAP;

    /** Returns {@link Set} of valid types for the specified {@link String} category key */
    public static Set<String> getValidDataTypesForCategory(
            @DataCategoryConstants.Category String category) {
        return VALID_TYPES_FOR_CATEGORY_MAP.containsKey(category)
                ? VALID_TYPES_FOR_CATEGORY_MAP.get(category)
                : Collections.emptySet();
    }

    static {
        VALID_TYPES_FOR_CATEGORY_MAP = new ArrayMap<>();
        VALID_TYPES_FOR_CATEGORY_MAP.put(
                DataCategoryConstants.CATEGORY_PERSONAL, VALID_TYPES_PERSONAL);
        VALID_TYPES_FOR_CATEGORY_MAP.put(
                DataCategoryConstants.CATEGORY_FINANCIAL, VALID_TYPES_FINANCIAL);
        VALID_TYPES_FOR_CATEGORY_MAP.put(
                DataCategoryConstants.CATEGORY_LOCATION, VALID_TYPES_LOCATION);
        VALID_TYPES_FOR_CATEGORY_MAP.put(
                DataCategoryConstants.CATEGORY_EMAIL_TEXT_MESSAGE, VALID_TYPES_EMAIL_TEXT_MESSAGE);
        VALID_TYPES_FOR_CATEGORY_MAP.put(
                DataCategoryConstants.CATEGORY_PHOTO_VIDEO, VALID_TYPES_PHOTO_VIDEO);
        VALID_TYPES_FOR_CATEGORY_MAP.put(DataCategoryConstants.CATEGORY_AUDIO, VALID_TYPES_AUDIO);
        VALID_TYPES_FOR_CATEGORY_MAP.put(
                DataCategoryConstants.CATEGORY_STORAGE, VALID_TYPES_STORAGE);
        VALID_TYPES_FOR_CATEGORY_MAP.put(
                DataCategoryConstants.CATEGORY_HEALTH_FITNESS, VALID_TYPES_HEALTH_FITNESS);
        VALID_TYPES_FOR_CATEGORY_MAP.put(
                DataCategoryConstants.CATEGORY_CONTACTS, VALID_TYPES_CONTACTS);
        VALID_TYPES_FOR_CATEGORY_MAP.put(
                DataCategoryConstants.CATEGORY_CALENDAR, VALID_TYPES_CALENDAR);
        VALID_TYPES_FOR_CATEGORY_MAP.put(
                DataCategoryConstants.CATEGORY_IDENTIFIERS, VALID_TYPES_IDENTIFIERS);
        VALID_TYPES_FOR_CATEGORY_MAP.put(
                DataCategoryConstants.CATEGORY_APP_PERFORMANCE, VALID_TYPES_APP_PERFORMANCE);
        VALID_TYPES_FOR_CATEGORY_MAP.put(
                DataCategoryConstants.CATEGORY_ACTIONS_IN_APP, VALID_TYPES_ACTIONS_IN_APP);
        VALID_TYPES_FOR_CATEGORY_MAP.put(
                DataCategoryConstants.CATEGORY_SEARCH_AND_BROWSING,
                VALID_TYPES_SEARCH_AND_BROWSING);
    }

    private DataTypeConstants() {
        /* do nothing - hide constructor */
    }
}
