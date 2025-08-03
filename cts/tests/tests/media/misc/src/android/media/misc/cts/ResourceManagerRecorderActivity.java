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

package android.media.misc.cts;

import static org.junit.Assert.assertTrue;

import android.hardware.Camera;
import android.hardware.cts.helpers.CameraUtils;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaRecorder;
import android.media.MediaRecorder.OnErrorListener;
import android.media.MediaRecorder.OnInfoListener;
import android.media.cts.MediaStubActivity;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Surface;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

// Does MediaRecording using camera(#0).
// The test can be configured to use
//  - use either AVC or HEVC video encoder
//  - record at lowest supported resolution or the highest resolution
public class ResourceManagerRecorderActivity extends MediaStubActivity {
    private static final int RECORD_TIME_MS = 3000;
    private static final float LATITUDE = 0.0000f;
    private static final float LONGITUDE  = -180.0f;
    private static final float TOLERANCE = 0.0002f;
    private static final String TAG = "ResourceManagerRecorderActivity";
    private final String mOutputPath;

    private int mResult = RESULT_CANCELED;
    private boolean mHighResolution = false;
    private int mVideoWidth = 0;
    private int mVideoHeight = 0;
    private int mVideoEncoderType = MediaRecorder.VideoEncoder.H264;
    private String mMime = MediaFormat.MIMETYPE_VIDEO_AVC;
    private Camera mCamera;
    private MediaRecorder mMediaRecorder;
    private Thread mWorkerThread;

    public ResourceManagerRecorderActivity() {
        mOutputPath = new File(Environment.getExternalStorageDirectory(),
                "record.out").getAbsolutePath();
        Log.d(TAG, "mOutputPath: " + mOutputPath);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate called.");
        super.onCreate(savedInstanceState);

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            mHighResolution = extras.getBoolean("high-resolution", mHighResolution);
            mMime = extras.getString("mime", mMime);
            if (mMime.equals(MediaFormat.MIMETYPE_VIDEO_HEVC)) {
                mVideoEncoderType = MediaRecorder.VideoEncoder.HEVC;
            }
        }
    }

    @Override
    protected void onStart() {
        Log.d(TAG, "onStart called.");
        super.onStart();
    }

    @Override
    protected void onRestart() {
        Log.d(TAG, "onRestart called.");
        super.onRestart();
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume called.");
        super.onResume();

        startRecording();
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause called.");
        super.onPause();
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop called.");
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy called.");
        super.onDestroy();
    }

    private void finishWithResult(int result) {
        setResult(result);
        finish();
        Log.d(TAG, "Activity finished with result: " + result);
    }

    // Creates a thread and does MediaRecording on that thread using Camera.
    private void startRecording() {
        mWorkerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                Log.i(TAG, "Started the thread");
                try {
                    recordVideoUsingCamera();
                } catch (Exception e) {
                    Log.e(TAG, "Caught exception: " + e);
                    finishWithResult(RESULT_CANCELED);
                }
                finishWithResult(mResult);
            }
        });
        mWorkerThread.start();
    }

    // Get the lowest or highest supported Video size
    // through the camera capture.
    private void setSupportedResolution(Camera camera) {
        List<Camera.Size> videoSizes = CameraUtils.getSupportedVideoSizes(camera);

        // Pick the max or min resolution (width * height) based
        // on the requirement.
        long curMaxResolution = 0;
        long curMinResolution = Long.MAX_VALUE;
        for (Camera.Size size : videoSizes) {
            long resolution = (long) size.width * size.height;
            if (!mHighResolution && (resolution < curMinResolution)) {
                curMinResolution = resolution;
                mVideoWidth = size.width;
                mVideoHeight = size.height;
            } else if (mHighResolution && (resolution > curMaxResolution)) {
                curMaxResolution = resolution;
                mVideoWidth = size.width;
                mVideoHeight = size.height;
            }
        }
    }

    // Validate the recorded media
    private boolean checkLocationInFile(String fileName) throws IOException {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(fileName);
        String location = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION);
        if (location == null) {
            retriever.release();
            Log.v(TAG, "No location information found in file " + fileName);
            return false;
        }

        // parsing String location and recover the location inforamtion in floats
        // Make sure the tolerance is very small - due to rounding errors?.
        Log.v(TAG, "location: " + location);

        // Trim the trailing slash, if any.
        int lastIndex = location.lastIndexOf('/');
        if (lastIndex != -1) {
            location = location.substring(0, lastIndex);
        }

        // Get the position of the -/+ sign in location String, which indicates
        // the beginning of the longitude.
        int index = location.lastIndexOf('-');
        if (index == -1) {
            index = location.lastIndexOf('+');
        }
        assertTrue("+ or - is not found", index != -1);
        assertTrue("+ or - is only found at the beginning", index != 0);
        float latitude = Float.parseFloat(location.substring(0, index));
        float longitude = Float.parseFloat(location.substring(index));
        assertTrue("Incorrect latitude: "
                + latitude, Math.abs(latitude - LATITUDE) <= TOLERANCE);
        assertTrue("Incorrect longitude: "
                + longitude, Math.abs(longitude - LONGITUDE) <= TOLERANCE);
        retriever.release();
        Files.deleteIfExists(Path.of(fileName));
        return true;
    }

    // Checks whether the device supports any encoder with given
    // configuration.
    private static boolean isEncoderSupported(String mime, int width, int height) {
        MediaCodecList mcl = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
        MediaFormat format = MediaFormat.createVideoFormat(mime, width, height);
        if (mcl.findEncoderForFormat(format) == null) {
            return false;
        }

        Log.i(TAG, "The device has an encoder for mime: " + mime
                + " resolution: " + width + "x" + height);
        return true;
    }

    // Open camera#0, start recording and validate the recorded video.
    private void recordVideoUsingCamera() throws Exception {
        int nCamera = Camera.getNumberOfCameras();
        int durMs = RECORD_TIME_MS;
        Log.d(TAG, "recordVideoUsingCamera: #of cameras: " + nCamera
                 + " Record Duration: " + durMs);
        // Record once with one camera(#0).
        if (nCamera > 0) {
            int cameraId = 0;
            mCamera = Camera.open(cameraId);
            setSupportedResolution(mCamera);
            // Make sure the device supports the encoder at given configuration before start
            // recording.
            if (isEncoderSupported(mMime, mVideoWidth, mVideoHeight)) {
                recordVideoUsingCamera(mCamera, mOutputPath, durMs);
                boolean success = checkLocationInFile(mOutputPath);
                mResult = success ? RESULT_OK : RESULT_CANCELED;
            } else {
                // We are skipping the test.
                Log.w(TAG, "The device doesn't support the encoder wth configuration("
                        + mMime + "," + mVideoWidth + "x" + mVideoHeight
                        + ") required for the Recording");
                mResult = ResourceManagerStubActivity.RESULT_CODE_NO_ENCODER;
            }
            mCamera.release();
            mCamera = null;
        } else {
            // Since there aren't any cameras on the device,
            // we are skipping the test.
            Log.w(TAG, "The device doesn't have any camera available for recording"
                    + " as android.hardware.Camera.getNumberOfCameras() returns: " + nCamera);
            mResult = ResourceManagerStubActivity.RESULT_CODE_NO_CAMERA;
        }
    }

    // Set up the MediaRecorder and record for durMs milliseconds
    private void recordVideoUsingCamera(Camera camera, String fileName, int durMs)
            throws Exception {
        Camera.Parameters params = camera.getParameters();
        int frameRate = params.getPreviewFrameRate();

        camera.unlock();
        mMediaRecorder = new MediaRecorder();
        mMediaRecorder.setOnInfoListener(new OnInfoListener() {
            public void onInfo(MediaRecorder mr, int what, int extra) {
                Log.v(TAG, "onInfo(" + what + ", " + extra + ")");
            }
        });
        mMediaRecorder.setOnErrorListener(new OnErrorListener() {
            public void onError(MediaRecorder mr, int what, int extra) {
                Log.e(TAG, "onError(" + what + ", " + extra + ")");
            }
        });

        // Make sure the preview surface is available for recording.
        Surface previewSurface = getSurface(getSurfaceHolder());
        if (previewSurface == null) {
            Log.e(TAG, "Failed to get the Surface");
            finishWithResult(RESULT_CANCELED);
        }

        mMediaRecorder.setCamera(camera);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.DEFAULT);
        mMediaRecorder.setVideoEncoder(mVideoEncoderType);
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);
        mMediaRecorder.setVideoFrameRate(frameRate);
        mMediaRecorder.setVideoSize(mVideoWidth, mVideoHeight);
        mMediaRecorder.setPreviewDisplay(previewSurface);
        mMediaRecorder.setOutputFile(fileName);
        mMediaRecorder.setLocation(LATITUDE, LONGITUDE);

        mMediaRecorder.prepare();
        Log.d(TAG, "recordVideoUsingCamera: Starting Recorder with Mime: " + mMime
                 + " Resolution: " + mVideoWidth + "x" + mVideoHeight
                 + " Framerate: " + frameRate);
        mMediaRecorder.start();
        // Sleep for duration ms so that mediarecorder can record for that duration.
        Thread.sleep(durMs);
        Log.d(TAG, "recordVideoUsingCamera: Stopping Recorder");
        mMediaRecorder.stop();
        mMediaRecorder.release();
        mMediaRecorder = null;
        finishWithResult(RESULT_OK);
    }

}
