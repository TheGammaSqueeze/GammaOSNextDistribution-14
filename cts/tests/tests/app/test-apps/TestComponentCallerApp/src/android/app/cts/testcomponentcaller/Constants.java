/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.app.cts.testcomponentcaller;

import android.content.ComponentName;
import android.net.Uri;

/** Constants shared with {@link android.app.cts.ComponentCallerTest} */
public class Constants {
    public static final String HELPER_APP_PACKAGE = "android.app.cts.testcomponentcaller";
    public static final ComponentName HELPER_APP_INITIAL_CALLER_ACTIVITY = new ComponentName(
            HELPER_APP_PACKAGE, HELPER_APP_PACKAGE + ".TestInitialCallerActivity");
    public static final ComponentName HELPER_APP_NEW_INTENT_GET_CURRENT_CALLER_ACTIVITY =
            new ComponentName(HELPER_APP_PACKAGE, HELPER_APP_PACKAGE
                    + ".TestNewIntentCallerActivities$TestNewIntentGetCurrentCallerActivity");
    public static final ComponentName HELPER_APP_NEW_INTENT_OVERLOAD_CALLER_ACTIVITY =
            new ComponentName(HELPER_APP_PACKAGE, HELPER_APP_PACKAGE
                    + ".TestNewIntentCallerActivities$TestNewIntentOverloadCallerActivity");
    public static final ComponentName HELPER_APP_RESULT_GET_CURRENT_CALLER_ACTIVITY =
            new ComponentName(HELPER_APP_PACKAGE, HELPER_APP_PACKAGE
                    + ".TestResultCallerActivities$TestResultGetCurrentCallerActivity");
    public static final ComponentName HELPER_APP_RESULT_OVERLOAD_CALLER_ACTIVITY =
            new ComponentName(HELPER_APP_PACKAGE, HELPER_APP_PACKAGE
                    + ".TestResultCallerActivities$TestResultOverloadCallerActivity");
    public static final Uri HELPER_APP_URI =
            Uri.parse("content://android.app.cts.testcomponentcaller.provider");

    private static final String TEST_PACKAGE = "android.app.cts";
    public static final ComponentName TEST_RECEIVER = new ComponentName(TEST_PACKAGE,
            TEST_PACKAGE + ".ComponentCallerTest$TestReceiver");
    public static final ComponentName TEST_SET_RESULT_ACTIVITY = new ComponentName(TEST_PACKAGE,
            TEST_PACKAGE + ".ComponentCallerTest$SetResultTestActivity");
    public static final ComponentName TEST_INITIAL_CALLER_ACTIVITY = new ComponentName(TEST_PACKAGE,
            TEST_PACKAGE + ".ComponentCallerTest$InitialCallerTestActivity");
    public static final ComponentName TEST_NEW_INTENT_GET_CURRENT_CALLER_ACTIVITY =
            new ComponentName(TEST_PACKAGE, TEST_PACKAGE
                    + ".ComponentCallerTest$NewIntentGetCurrentCallerTestActivity");
    public static final ComponentName TEST_NEW_INTENT_OVERLOAD_CALLER_ACTIVITY =
            new ComponentName(TEST_PACKAGE, TEST_PACKAGE
                    + ".ComponentCallerTest$NewIntentOverloadCallerTestActivity");

    public static final String TEST_RECEIVER_ACTION = "android.app.cts.ACTIVITY_CALLER_ACTION";

    public static final String URI_LOCATION_ID = "uriLocationId";
    public static final int NONE_PROVIDED_USE_HELPER_APP_URI_LOCATION_ID = 0;
    public static final int URI_IN_DATA_LOCATION_ID = 1;
    public static final int URI_IN_CLIP_DATA_LOCATION_ID = 2;
    public static final int URI_IN_EXTRA_STREAM_LOCATION_ID = 3;
    public static final int URI_IN_ARRAY_LIST_EXTRA_STREAMS_LOCATION_ID = 4;
    public static final int URI_IN_EXTRA_UNKNOWN_LOCATION_ID = 5;

    public static final String TEST_ACTION_ID = "testActionId";
    public static final int START_TEST_ACTIVITY_ACTION_ID = 0;
    public static final int SEND_TEST_BROADCAST_ACTION_ID = 1;
    public static final int TRY_TO_RETRIEVE_EXTRA_STREAM_REFERRER_NAME = 2;

    public static final String SET_RESULT_ACTION_ID = "resultActionId";
    public static final int URI_PROVIDED_SET_RESULT_ACTION_ID = 0;
    public static final int NO_URI_PROVIDED_SET_RESULT_ACTION_ID = 1;
    public static final int PUT_MODE_FLAGS_TO_CHECK_SET_RESULT_ACTION_ID = 2;
    public static final int GRANT_FLAGS_SET_RESULT_ACTION_ID = 3;
    public static final int NO_ACTION_NEEDED_SET_RESULT_ACTION_ID = 4;
    public static final int PUT_EXTRA_UNKNOWN_REMOVE_EXTRA_STREAM_SET_RESULT_ACTION_ID = 5;
    public static final int PUT_NON_URI_EXTRA_STREAM_SET_RESULT_ACTION_ID = 6;

    public static final String IS_NEW_INTENT = "isNewIntent";
    public static final String IS_RESULT = "isResult";

    public static final String RESULT_URI_TYPE = "resultUriType";
    public static final int PROVIDER_RESULT_URI_TYPE = 0;
    public static final int NO_PERMISSION_URI_TYPE = 1;
    public static final int READ_PERMISSION_URI_TYPE = 2;
    public static final String PUT_MODE_FLAGS = "putModeFlags";
    public static final String GRANT_MODE_FLAGS = "grantModeFlags";
    public static final String RESULT_NON_URI_EXTRA_STREAM = "resultNonUriExtraStream";
    public static final String RESULT_EXTRA_REFERRER_NAME = "resultExtraReferrerName";

    public static final String MODE_FLAGS_TO_CHECK = "modeFlags";

    public static final String EXTRA_SECURITY_EXCEPTION_CAUGHT = "securityExceptionCaught";
    public static final String EXTRA_ILLEGAL_ARG_EXCEPTION_CAUGHT =
            "illegalArgumentExceptionCaught";
    public static final String EXTRA_CHECK_CONTENT_URI_PERMISSION_RESULT = "permissionResult";
    public static final String EXTRA_UNKNOWN = "unknown";
    public static final int INVALID_PERMISSION_RESULT = -2;
}
