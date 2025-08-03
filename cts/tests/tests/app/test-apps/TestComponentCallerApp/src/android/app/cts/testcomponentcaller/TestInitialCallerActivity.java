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

import static android.app.cts.testcomponentcaller.Constants.TEST_ACTION_ID;
import static android.app.cts.testcomponentcaller.Constants.EXTRA_CHECK_CONTENT_URI_PERMISSION_RESULT;
import static android.app.cts.testcomponentcaller.Constants.EXTRA_ILLEGAL_ARG_EXCEPTION_CAUGHT;
import static android.app.cts.testcomponentcaller.Constants.EXTRA_UNKNOWN;
import static android.app.cts.testcomponentcaller.Constants.IS_NEW_INTENT;
import static android.app.cts.testcomponentcaller.Constants.IS_RESULT;
import static android.app.cts.testcomponentcaller.Constants.MODE_FLAGS_TO_CHECK;
import static android.app.cts.testcomponentcaller.Constants.EXTRA_SECURITY_EXCEPTION_CAUGHT;
import static android.app.cts.testcomponentcaller.Constants.INVALID_PERMISSION_RESULT;
import static android.app.cts.testcomponentcaller.Constants.NONE_PROVIDED_USE_HELPER_APP_URI_LOCATION_ID;
import static android.app.cts.testcomponentcaller.Constants.SEND_TEST_BROADCAST_ACTION_ID;
import static android.app.cts.testcomponentcaller.Constants.START_TEST_ACTIVITY_ACTION_ID;
import static android.app.cts.testcomponentcaller.Constants.TEST_INITIAL_CALLER_ACTIVITY;
import static android.app.cts.testcomponentcaller.Constants.TEST_RECEIVER;
import static android.app.cts.testcomponentcaller.Constants.TEST_RECEIVER_ACTION;
import static android.app.cts.testcomponentcaller.Constants.TRY_TO_RETRIEVE_EXTRA_STREAM_REFERRER_NAME;
import static android.app.cts.testcomponentcaller.Constants.URI_IN_ARRAY_LIST_EXTRA_STREAMS_LOCATION_ID;
import static android.app.cts.testcomponentcaller.Constants.URI_IN_CLIP_DATA_LOCATION_ID;
import static android.app.cts.testcomponentcaller.Constants.URI_IN_DATA_LOCATION_ID;
import static android.app.cts.testcomponentcaller.Constants.URI_IN_EXTRA_STREAM_LOCATION_ID;
import static android.app.cts.testcomponentcaller.Constants.URI_IN_EXTRA_UNKNOWN_LOCATION_ID;
import static android.app.cts.testcomponentcaller.Constants.URI_LOCATION_ID;

import android.app.Activity;
import android.app.ComponentCaller;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

public class TestInitialCallerActivity extends Activity {
    private static final String TAG = "TestInitialCallerActivity";
    @Override
    public void onStart() {
        super.onStart();
        if (!getIntent().getBooleanExtra(IS_RESULT, false)) {
            Log.i(TAG, "onStart: " + getIntent());
            performTest(getIntent(), getInitialCaller());
        }
    }

    protected void performTest(Intent intent, ComponentCaller caller) {
        int uriLocationId = intent.getIntExtra(URI_LOCATION_ID, -1);
        int actionId = intent.getIntExtra(TEST_ACTION_ID, -1);
        int modeFlags = intent.getIntExtra(MODE_FLAGS_TO_CHECK, -1);
        boolean isNewIntent = intent.getBooleanExtra(IS_NEW_INTENT, false);

        switch (actionId) {
            case START_TEST_ACTIVITY_ACTION_ID ->
                    startTestActivity(getUriToCheck(uriLocationId, intent), modeFlags, isNewIntent);
            case SEND_TEST_BROADCAST_ACTION_ID ->
                    sendTestBroadcast(getUriToCheck(uriLocationId, intent), modeFlags, caller,
                            isNewIntent);
            case TRY_TO_RETRIEVE_EXTRA_STREAM_REFERRER_NAME ->
                    tryToRetrieveExtraStreamReferrerName(intent);
            default -> throw new RuntimeException("Invalid action ID");
        }
    }

    public ComponentName getTestActivity() {
        return TEST_INITIAL_CALLER_ACTIVITY;
    }

    private void startTestActivity(Uri uri, int modeFlags, boolean isNewIntent) {
        Intent intent = new Intent();
        intent.setComponent(getTestActivity());
        intent.setData(uri);
        intent.putExtra(MODE_FLAGS_TO_CHECK, modeFlags);
        intent.putExtra(IS_NEW_INTENT, isNewIntent);
        intent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | modeFlags);
        startActivity(intent);
        if (!isNewIntent) {
            finish();
        }
    }

    private void sendTestBroadcast(Uri uri, int modeFlags, ComponentCaller caller,
            boolean isNewIntent) {
        boolean securityExceptionCaught = false;
        boolean illegalArgExceptionCaught = false;
        int permissionResult = INVALID_PERMISSION_RESULT;

        try {
            if (uri != null) {
                permissionResult = caller.checkContentUriPermission(uri, modeFlags);
            } else {
                throw new RuntimeException("Test URI is null");
            }
        } catch (Exception e) {
            if (e instanceof SecurityException) {
                securityExceptionCaught = true;
            } else if (e instanceof IllegalArgumentException) {
                illegalArgExceptionCaught = true;
            }
        }

        Intent intent = new Intent();
        intent.setComponent(TEST_RECEIVER);
        intent.setAction(TEST_RECEIVER_ACTION);

        intent.putExtra(EXTRA_SECURITY_EXCEPTION_CAUGHT, securityExceptionCaught);
        intent.putExtra(EXTRA_ILLEGAL_ARG_EXCEPTION_CAUGHT, illegalArgExceptionCaught);
        intent.putExtra(EXTRA_CHECK_CONTENT_URI_PERMISSION_RESULT, permissionResult);

        sendBroadcast(intent);
        if (!isNewIntent) {
            finish();
        }
    }

    private void tryToRetrieveExtraStreamReferrerName(Intent intent) {
        String streamString = intent.getStringExtra(Intent.EXTRA_STREAM);
        String referrerName = intent.getStringExtra(Intent.EXTRA_REFERRER_NAME);

        Intent broadcastIntent = new Intent();
        broadcastIntent.setComponent(TEST_RECEIVER);
        broadcastIntent.setAction(TEST_RECEIVER_ACTION);

        broadcastIntent.putExtra(Intent.EXTRA_STREAM, streamString);
        broadcastIntent.putExtra(Intent.EXTRA_REFERRER_NAME, referrerName);

        sendBroadcast(broadcastIntent);
        finish();
    }

    private Uri getUriToCheck(int uriLocationId, Intent intent) {
        return switch (uriLocationId) {
            case URI_IN_DATA_LOCATION_ID -> intent.getData();
            case URI_IN_CLIP_DATA_LOCATION_ID -> getClipDataUri(intent);
            case URI_IN_EXTRA_STREAM_LOCATION_ID ->
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri.class);
            case URI_IN_ARRAY_LIST_EXTRA_STREAMS_LOCATION_ID -> getArrayListExtraStreams(intent);
            case URI_IN_EXTRA_UNKNOWN_LOCATION_ID ->
                    intent.getParcelableExtra(EXTRA_UNKNOWN, Uri.class);
            case NONE_PROVIDED_USE_HELPER_APP_URI_LOCATION_ID -> TestProvider.CONTENT_URI;
            default -> throw new RuntimeException("Invalid URI location ID: " + uriLocationId);
        };
    }

    private Uri getClipDataUri(Intent intent) {
        ClipData clip = intent.getClipData();
        if (clip == null || clip.getItemCount() == 0) {
            throw new RuntimeException("Testing clip data, but it wasn't provided");
        }
        return intent.getClipData().getItemAt(0).getUri();
    }

    private Uri getArrayListExtraStreams(Intent intent) {
        try {
            return intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri.class).get(0);
        } catch (Exception e) {
            throw new RuntimeException("Testing ArrayList EXTRA_STREAM, but getting this exception:"
                    + e);
        }
    }
}
