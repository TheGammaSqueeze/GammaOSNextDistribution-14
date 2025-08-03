/*
 * Copyright 2015 The Android Open Source Project
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
package android.media.misc.cts;

import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import junit.framework.Assert;

public class ResourceManagerStubActivity extends Activity {
    // Define all the error codes specific to this test case here
    // Test case was skipped as there aren't any supported decoder(s).
    public static final int RESULT_CODE_NO_DECODER = Activity.RESULT_FIRST_USER + 1;
    // Test case was skipped as there aren't any supported encoder(s).
    public static final int RESULT_CODE_NO_ENCODER = Activity.RESULT_FIRST_USER + 2;
    // Test case was skipped as the device doesn't have any camera available for recording.
    public static final int RESULT_CODE_NO_CAMERA = Activity.RESULT_FIRST_USER + 3;

    private static final String TAG = "ResourceManagerStubActivity";
    private final Object mFinishEvent = new Object();
    private int[] mRequestCodes = {0, 1};
    private int[] mResults = {RESULT_CANCELED, RESULT_CANCELED};
    private int mNumResults = 0;
    private int mType1 = ResourceManagerTestActivityBase.TYPE_NONSECURE;
    private int mType2 = ResourceManagerTestActivityBase.TYPE_NONSECURE;
    private boolean mWaitForReclaim = true;
    private boolean mStartedOnlyOneActivity = false;

    private static final String ERROR_INSUFFICIENT_RESOURCES =
            "* Please check if the omx component is returning OMX_ErrorInsufficientResources " +
            "properly when the codec failure is due to insufficient resource.\n";
    private static final String ERROR_SUPPORTS_MULTIPLE_SECURE_CODECS =
            "* Please check if this platform supports multiple concurrent secure codec " +
            "instances. If not, please add below setting in /etc/media_codecs.xml in order " +
            "to pass the test:\n" +
            "    <Settings>\n" +
            "       <Setting name=\"supports-multiple-secure-codecs\" value=\"false\" />\n" +
            "    </Settings>\n";
    private static final String ERROR_SUPPORTS_SECURE_WITH_NON_SECURE_CODEC =
            "* Please check if this platform supports co-exist of secure and non-secure codec. " +
            "If not, please add below setting in /etc/media_codecs.xml in order to pass the " +
            "test:\n" +
            "    <Settings>\n" +
            "       <Setting name=\"supports-secure-with-non-secure-codec\" value=\"false\" />\n" +
            "    </Settings>\n";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d(TAG, "Activity " + requestCode + " finished with resultCode " + resultCode);
        mResults[requestCode] = resultCode;
        if (++mNumResults == mResults.length || mStartedOnlyOneActivity) {
            synchronized (mFinishEvent) {
                mFinishEvent.notify();
            }
        }
    }

    private void processActivityResults() {
        boolean result = true;
        for (int i = 0; result && i < mResults.length; ++i) {
            switch (mResults[i]) {
                case RESULT_OK:
                    // Activity completed successfully.
                    break;
                case RESULT_CODE_NO_DECODER:
                    assumeTrue("Test case was skipped as there aren't any supported decoder(s).",
                            false);
                    break;
                case RESULT_CODE_NO_ENCODER:
                    assumeTrue("Test case was skipped as there aren't any supported encoder(s).",
                            false);
                    break;
                case RESULT_CODE_NO_CAMERA:
                    assumeTrue("Test case was skipped as the device doesn't have any "
                            + "camera available for recording.", false);
                    break;
                default:
                    Log.e(TAG, "Result from activity " + i + " is a fail.");
                    result = false;
                    break;
            }
        }

        if (!result) {
            String failMessage = "The potential reasons for the failure:\n";
            StringBuilder reasons = new StringBuilder();
            reasons.append(ERROR_INSUFFICIENT_RESOURCES);
            if (mType1 != mType2) {
                reasons.append(ERROR_SUPPORTS_SECURE_WITH_NON_SECURE_CODEC);
            }
            if (mType1 == ResourceManagerTestActivityBase.TYPE_MIX
                    && mType2 == ResourceManagerTestActivityBase.TYPE_SECURE) {
                reasons.append(ERROR_SUPPORTS_MULTIPLE_SECURE_CODECS);
            }
            Assert.assertTrue(failMessage + reasons.toString(), result);
        }
    }

    private void waitForActivitiesToComplete() throws InterruptedException {
        // Wait for all the activities to complete.
        synchronized (mFinishEvent) {
            while (mNumResults < mResults.length) {
                mFinishEvent.wait();
            }
        }
    }

    private void waitForOneActivityToComplete() throws InterruptedException {
        // Wait for one activity to complete.
        synchronized (mFinishEvent) {
            while (mNumResults != 1) {
                mFinishEvent.wait();
            }
        }
    }

    public void testReclaimResource(int type1, int type2, boolean highResolutionForActivity1,
            boolean highResolutionForActivity2) throws InterruptedException {
        mType1 = type1;
        mType2 = type2;
        if (type1 != ResourceManagerTestActivityBase.TYPE_MIX && type1 != type2) {
            // in this case, activity2 may not need to reclaim codec from activity1.
            mWaitForReclaim = false;
        } else {
            mWaitForReclaim = true;
        }
        Thread thread = new Thread() {
            @Override
            public void run() {
                try {
                    Context context = getApplicationContext();
                    Intent intent1 = new Intent(context, ResourceManagerTestActivity1.class);
                    intent1.putExtra("test-type", mType1);
                    intent1.putExtra("wait-for-reclaim", mWaitForReclaim);
                    intent1.putExtra("high-resolution", highResolutionForActivity1);
                    startActivityForResult(intent1, mRequestCodes[0]);
                    Thread.sleep(5000);  // wait for process to launch and allocate all codecs.

                    Intent intent2 = new Intent(context, ResourceManagerTestActivity2.class);
                    intent2.putExtra("test-type", mType2);
                    intent2.putExtra("high-resolution", highResolutionForActivity2);
                    startActivityForResult(intent2, mRequestCodes[1]);

                    waitForActivitiesToComplete();
                } catch (Exception e) {
                    Log.d(TAG, "testReclaimResource got exception " + e.toString());
                }
            }
        };
        thread.start();
        Log.i(TAG, "Started and waiting for Activities");
        thread.join();
        Log.i(TAG, "Activities completed");

        processActivityResults();
    }

    public void testVideoCodecReclaim(boolean highResolution, String mimeType)
            throws InterruptedException {
        Thread thread = new Thread() {
            @Override
            public void run() {
                try {
                    Context context = getApplicationContext();

                    // Start the transcoding activity first.
                    Log.d(TAG, "Starting ResourceManagerCodecActivity");
                    Intent decoders = new Intent(context, ResourceManagerCodecActivity.class);
                    decoders.putExtra("high-resolution", highResolution);
                    decoders.putExtra("mime", mimeType);
                    startActivityForResult(decoders, mRequestCodes[0]);
                    // wait for ResourceManagerCodecActivity to launch and allocate all codecs.
                    Thread.sleep(5000);

                    Log.d(TAG, "Starting ResourceManagerRecorderActivity");
                    // Start the Camera Recording next.
                    Intent recorder = new Intent(context, ResourceManagerRecorderActivity.class);
                    recorder.putExtra("high-resolution", highResolution);
                    recorder.putExtra("mime", mimeType);
                    startActivityForResult(recorder, mRequestCodes[1]);

                    waitForActivitiesToComplete();
                } catch (Exception e) {
                    Log.d(TAG, "testVideoCodecReclaim got exception " + e.toString());
                }
            }
        };

        thread.start();
        Log.i(TAG, "Started and waiting for Activities");
        thread.join();
        Log.i(TAG, "Activities completed");

        processActivityResults();
    }

    public void doTestReclaimResource(String codecName, String mimeType, int width, int height)
            throws InterruptedException {
        mWaitForReclaim = true;
        Thread thread = new Thread() {
            @Override
            public void run() {
                try {
                    Context context = getApplicationContext();
                    Intent intent1 = new Intent(context, ResourceManagerTestActivity1.class);
                    intent1.putExtra("test-type", mType1);
                    intent1.putExtra("wait-for-reclaim", mWaitForReclaim);
                    intent1.putExtra("name", codecName);
                    intent1.putExtra("mime", mimeType);
                    intent1.putExtra("width", width);
                    intent1.putExtra("height", height);
                    startActivityForResult(intent1, mRequestCodes[0]);
                    Thread.sleep(5000);  // wait for process to launch and allocate all codecs.

                    Intent intent2 = new Intent(context, ResourceManagerTestActivity2.class);
                    intent2.putExtra("test-type", mType2);
                    intent2.putExtra("name", codecName);
                    intent2.putExtra("mime", mimeType);
                    intent2.putExtra("width", width);
                    intent2.putExtra("height", height);
                    startActivityForResult(intent2, mRequestCodes[1]);

                    waitForActivitiesToComplete();
                } catch (Exception e) {
                    Log.d(TAG, "doTestReclaimResource got exception " + e.toString());
                }
            }
        };

        thread.start();
        Log.i(TAG, "Started and waiting for Activities");
        thread.join();
        Log.i(TAG, "Activities completed");

        processActivityResults();
    }

    /**
     * creates allowable number of decoders at given resolution.
     * All the codecs are configured with the default importance (highest)
     * But, when we get a INSUFFICIENT_RESOURCE, we lower the importance of the
     * first codec so that we can create/start one more codec by reclaiming the
     * first codec (that has lower importance now)
     */
    public void doTestCodecImportanceReclaimResource(
            String codecName, String mimeType, int width, int height,
            boolean highResolution, boolean changeImportanceAtConfig)
            throws InterruptedException {
        mWaitForReclaim = true;
        Thread thread = new Thread() {
            @Override
            public void run() {
                try {
                    Context context = getApplicationContext();
                    Intent intent = new Intent(context, ResourceManagerTestActivity2.class);
                    intent.putExtra("test-type", mType1);
                    intent.putExtra("wait-for-reclaim", mWaitForReclaim);
                    intent.putExtra("name", codecName);
                    intent.putExtra("mime", mimeType);
                    if (width == 0 || height == 0) {
                        intent.putExtra("high-resolution", highResolution);
                    } else {
                        intent.putExtra("width", width);
                        intent.putExtra("height", height);
                    }
                    if (changeImportanceAtConfig) {
                        intent.putExtra("codec-importance-at-config", true);
                    } else {
                        intent.putExtra("codec-importance-later", true);
                    }
                    mStartedOnlyOneActivity = true;
                    startActivityForResult(intent, mRequestCodes[0]);
                    waitForOneActivityToComplete();
                } catch (Exception e) {
                    Log.d(TAG, "doTestCodecImportanceReclaimResource got exception "
                            + e.toString());
                }
            }
        };

        thread.start();
        Log.i(TAG, "Started and waiting for Activities");
        thread.join();
        Log.i(TAG, "Activities completed");

        // Since we have started one activity, set the other activity code to success.
        mResults[1] = RESULT_OK;
        processActivityResults();
    }
}
