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

package android.healthconnect.cts.utils;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.compatibility.common.util.UiAutomatorUtils.getUiDevice;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.test.core.app.ActivityScenario;

/**
 * Activity which starts another activity and forwards the result back to the caller.
 *
 * <p>The intent to start a new activity with is encoded as a parcelable extra with key
 * Intent.EXTRA_INTENT.
 *
 * <p>This is useful for two reasons: 1. ActivityScenario often have problems with launching an
 * activity which belongs to another process. ProxyActivity can be used as a workaround. 2. It can
 * be used by a test app so that a CTS test can start an activity on behalf of the test app.
 */
public class ProxyActivity extends Activity {
    public static final String PROXY_ACTIVITY_ACTION =
            "android.healthconnect.cts.ACTION_START_ACTIVITY_FOR_RESULT";
    public static final String PROXY_ACTIVITY_ERROR =
            "android.healthconnect.cts.PROXY_ACTIVITY_ERROR";
    private static final int REQUEST_CODE = 1;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        var requestIntent = getIntent().getParcelableExtra(Intent.EXTRA_INTENT, Intent.class);

        if (requestIntent == null) {
            finishWithException(new IllegalArgumentException("Missing EXTRA_INTENT extra"));
            return;
        }

        try {
            startActivityForResult(requestIntent, REQUEST_CODE);
        } catch (Exception e) {
            finishWithException(e);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_CODE) {
            finishWithException(
                    new IllegalArgumentException("Unexpected request code " + requestCode));
            return;
        }

        setResult(resultCode, data);
        finish();
    }

    /**
     * Launch an activity for result with the given intent and executes the runnable once the
     * activity gets created.
     *
     * <p>ActivityScenario.launchActivityForResult doesn't work with activities running in another
     * process as it times out on waiting for the remote activity to become CREATED. Apparently this
     * happens because ActivityScenario uses ActivityLifecycleMonitor to track the activity state,
     * however state changes of the activities which belong to other processes don't trigger the
     * lifecycle callback and therefore ActivityScenario thinks that the target activity is always
     * in the PRE_ON_CREATE state even when it's not true. As a workaround this method launches the
     * proxy activity in the same process which then forwards the intent to the target remote
     * activity.
     *
     * <p>The app calling this method must have {@link ProxyActivity} declared in the manifest.
     */
    public static Instrumentation.ActivityResult launchActivityForResult(
            Intent intent, Runnable runnable) throws Exception {

        Intent containerIntent = new Intent(getInstrumentation().getContext(), ProxyActivity.class);
        containerIntent.putExtra(Intent.EXTRA_INTENT, intent);

        var scenario = ActivityScenario.launchActivityForResult(containerIntent);
        scenario.onActivity(
                activity -> {
                    getUiDevice().waitForIdle();
                    runnable.run();
                });

        Instrumentation.ActivityResult result = scenario.getResult();

        Exception exception =
                result.getResultData().getParcelableExtra(PROXY_ACTIVITY_ERROR, Exception.class);

        if (exception != null) {
            throw exception;
        }

        return result;
    }

    private void finishWithException(Exception e) {
        Intent errorIntent = new Intent();
        errorIntent.putExtra(PROXY_ACTIVITY_ERROR, e);
        setResult(RESULT_CANCELED, errorIntent);
        finish();
    }
}
