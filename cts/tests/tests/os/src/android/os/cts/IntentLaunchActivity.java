/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.os.cts;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import java.net.URISyntaxException;

/**
 * Activity used to verify an UnsafeIntentLaunch StrictMode violation is reported when an Intent
 * is unparceled from the delivered Intent and used to start another activity.
 */
public class IntentLaunchActivity extends Activity {
    private static final String TAG = "IntentLaunchActivity";

    protected static final String EXTRA_INNER_INTENT = "inner-intent";
    private static final String EXTRA_INNER_INTENT_URI_STRING = "inner-intent-uri-string";

    private static final String ACTION_UNSAFE_INTENT_LAUNCH = "android.os.cts.UNSAFE_INTENT_LAUNCH";
    private static final String ACTION_UNSAFE_DATA_COPY_FROM_INTENT =
            "android.os.cts.UNSAFE_DATA_COPY_FROM_INTENT";
    private static final String ACTION_DATA_COPY_FROM_DELIVERED_INTENT_WITH_UNPARCELED_EXTRAS =
            "android.os.cts.DATA_COPY_FROM_DELIVERED_INTENT_WITH_UNPARCELED_EXTRAS";
    private static final String ACTION_UNSAFE_DATA_COPY_FROM_EXTRAS =
            "android.os.cts.UNSAFE_DATA_COPY_FROM_EXTRAS";
    private static final String ACTION_UNSAFE_INTENT_FROM_URI_LAUNCH =
            "android.os.cts.UNSAFE_INTENT_FROM_URI_LAUNCH";
    private static final String ACTION_SAFE_INTENT_FROM_URI_LAUNCH =
            "android.os.cts.SAFE_INTENT_FROM_URI_LAUNCH";

    private static final String ACTION_BROWSABLE_INTENT_LAUNCH =
            "android.os.cts.BROWSABLE_INTENT_LAUNCH";

    private static final String EXTRA_TEST_KEY = "android.os.cts.TEST_KEY";

    /**
     * Returns an Intent containing a parceled inner Intent that can be used to start this Activity
     * and verify the StrictMode UnsafeIntentLaunch violation is reported as expected.
     */
    public static Intent getUnsafeIntentLaunchTestIntent(Context context) {
        return getTestIntent(context, ACTION_UNSAFE_INTENT_LAUNCH);
    }

    /**
     * Returns an Intent containing a parceled Intent with data in the extras; the returned Intent
     * can be used to start this Activity and verify the StrictMode UnsafeIntentLaunch violation
     * is reported as expected when copying data from an unparceled Intent.
     */
    public static Intent getUnsafeDataCopyFromIntentTestIntent(Context context) {
        return getTestIntentWithExtrasInParceledIntent(context,
                ACTION_UNSAFE_DATA_COPY_FROM_INTENT);
    }

    /**
     * Returns an Intent containing a parceled Intent with data in the extras; the returned Intent
     * can be used to start this Activity and verify the StrictMode UnsafeIntentLaunch violation is
     * reported as expected when copying data from an Intent's extras without sanitation /
     * validation.
     */
    public static Intent getUnsafeDataCopyFromExtrasTestIntent(Context context) {
        return getTestIntentWithExtrasInParceledIntent(context,
                ACTION_UNSAFE_DATA_COPY_FROM_EXTRAS);
    }

    /**
     * Returns an Intent containing data in the extras; the returned Intent can be used to start
     * this Activity and verify the StrictMode UnsafeIntentLaunch violation is not reported since
     * the extras of the Intent delivered to a protected component are considered safe.
     */
    public static Intent getDataCopyFromDeliveredIntentWithUnparceledExtrasTestIntent(
            Context context) {
        // When an Intent is delivered to a protected component this delivered Intent's extras
        // can be copied unfiltered to another Intent since only a trusted component could send
        // this Intent. If the sending component were to attempt an unfiltered copy of extras from
        // an unparceled Intent or Bundle the violation would be triggered by that call.
        return getTestIntent(context,
                ACTION_DATA_COPY_FROM_DELIVERED_INTENT_WITH_UNPARCELED_EXTRAS);
    }

    /**
     * Returns an Intent with the specified {@code action} set and a parceled Intent with data in
     * the extras.
     */
    private static Intent getTestIntentWithExtrasInParceledIntent(Context context, String action) {
        Intent intent = getTestIntent(context, action);
        Intent innerIntent = intent.getParcelableExtra(EXTRA_INNER_INTENT);
        // Add an extra to the Intent so that it contains the extras Bundle; the data itself is not
        // important, just the fact that there is data to be copied without sanitation / validation.
        innerIntent.putExtra(EXTRA_TEST_KEY, "TEST_VALUE");
        return intent;
    }

    /**
     * Returns an Intent containing an Intent encoded as a URI in the extras; the returned Intent
     * can be used to start this Activity and verify the StrictMode UnsafeIntentLaunch violation is
     * reported as expected when launching an Intent parsed from a URI.
     */
    public static Intent getUnsafeIntentFromUriLaunchTestIntent(Context context) {
        return getTestIntentWithUriIntentInExtras(context, ACTION_UNSAFE_INTENT_FROM_URI_LAUNCH);
    }

    /**
     * Returns an Intent containing an Intent encoded as a URI in the extras; the returned Intent
     * can be used to start this Activity and verify the StrictMode UnsafeIntentLaunch violation is
     * not reported when launching an Intent parsed from a URI with the browsable category and
     * without an explicit component.
     */
    public static Intent getSafeIntentFromUriLaunchTestIntent(Context context) {
        return getTestIntentWithUriIntentInExtras(context, ACTION_SAFE_INTENT_FROM_URI_LAUNCH);
    }

    /**
     * Returns an Intent with the specified {@code action} set and containing an Intent encoded as a
     * URI in the extras.
     */
    private static Intent getTestIntentWithUriIntentInExtras(Context context, String action) {
        Intent intent = getTestIntent(context, action);
        Intent innerIntent = intent.getParcelableExtra(EXTRA_INNER_INTENT);
        innerIntent.addCategory(Intent.CATEGORY_BROWSABLE);
        innerIntent.setAction(ACTION_BROWSABLE_INTENT_LAUNCH);
        innerIntent.setPackage(context.getPackageName());
        String intentUriString = innerIntent.toUri(Intent.URI_ANDROID_APP_SCHEME);
        intent.putExtra(EXTRA_INNER_INTENT_URI_STRING, intentUriString);
        intent.removeExtra(EXTRA_INNER_INTENT);
        return intent;
    }

    /**
     * Returns an Intent with the specified {@code action} set and a parceled Intent.
     */
    private static Intent getTestIntent(Context context, String action) {
        Intent intent = new Intent(context, IntentLaunchActivity.class);
        intent.setAction(action);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Intent innerIntent = new Intent(context, SimpleTestActivity.class);
        intent.putExtra(EXTRA_INNER_INTENT, innerIntent);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent deliveredIntent = getIntent();
        Intent innerIntent = deliveredIntent.getParcelableExtra(EXTRA_INNER_INTENT);
        String action = deliveredIntent.getAction();
        switch (action) {
            case ACTION_UNSAFE_INTENT_LAUNCH: {
                if (innerIntent != null) {
                    startActivity(innerIntent);
                }
                break;
            }
            case ACTION_UNSAFE_DATA_COPY_FROM_INTENT: {
                if (innerIntent != null) {
                    // Instantiate a new Intent to be used as the target of the unfiltered data
                    // copy.
                    Intent intent = new Intent(getApplicationContext(), SimpleTestActivity.class);
                    intent.putExtras(innerIntent);
                    startActivity(intent);
                }
                break;
            }
            case ACTION_UNSAFE_DATA_COPY_FROM_EXTRAS: {
                if (innerIntent != null) {
                    Intent intent = new Intent(getApplicationContext(), SimpleTestActivity.class);
                    intent.putExtras(innerIntent.getExtras());
                    startActivity(intent);
                }
                break;
            }
            case ACTION_DATA_COPY_FROM_DELIVERED_INTENT_WITH_UNPARCELED_EXTRAS: {
                Intent intent = new Intent(getApplicationContext(), SimpleTestActivity.class);
                intent.putExtras(deliveredIntent);
                startActivity(intent);
                break;
            }
            case ACTION_UNSAFE_INTENT_FROM_URI_LAUNCH:
            case ACTION_SAFE_INTENT_FROM_URI_LAUNCH: {
                String intentUriString = deliveredIntent.getStringExtra(
                        EXTRA_INNER_INTENT_URI_STRING);
                if (intentUriString != null) {
                    try {
                        Intent intent = Intent.parseUri(intentUriString,
                                Intent.URI_ANDROID_APP_SCHEME);
                        // If this is a safe intent from URI launch then clear the component as a
                        // browsable Intent without a component set should not result in a
                        // violation.
                        if (ACTION_SAFE_INTENT_FROM_URI_LAUNCH.equals(action)) {
                            intent.setComponent(null);
                        }
                        startActivity(intent);
                    } catch (URISyntaxException e) {
                        Log.e(TAG, "Exception parsing URI: " + intentUriString, e);
                    }
                }
                break;
            }
            default:
                throw new IllegalArgumentException(
                        "An unexpected action of " + deliveredIntent.getAction()
                                + " was specified");
        }
        finish();
    }
}
