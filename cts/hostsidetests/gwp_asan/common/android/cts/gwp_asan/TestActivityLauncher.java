/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.cts.gwp_asan;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

public class TestActivityLauncher extends Activity {
    static final String TAG = "TestActivityLauncher";

    private int mResult;
    private final Object mFinishEvent = new Object();

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        mResult = resultCode;
        synchronized (mFinishEvent) {
            mFinishEvent.notify();
        }
    }

    public void callActivity(Class<?> cls, int testId) throws Exception {
        Thread thread =
                new Thread() {
                    @Override
                    public void run() {
                        Intent intent = new Intent(getApplicationContext(), cls);
                        intent.putExtra("testId", testId);
                        startActivity(intent);
                    }
                };
        thread.start();
    }

    public void callActivityAndCheckSuccess(Class<?> cls, int testId) throws Exception {
        Thread thread =
                new Thread() {
                    @Override
                    public void run() {
                        try {
                            Intent intent = new Intent(getApplicationContext(), cls);
                            intent.putExtra("testId", testId);
                            startActivityForResult(intent, 0);

                            synchronized (mFinishEvent) {
                                mFinishEvent.wait();
                            }
                        } catch (Exception e) {
                            Log.d(TAG, "callActivity got an exception " + e.toString());
                        }
                    }
                };
        thread.start();
        thread.join(50000 /* millis */);

        if (mResult != Utils.TEST_SUCCESS) {
            throw new Exception();
        }
    }
}
