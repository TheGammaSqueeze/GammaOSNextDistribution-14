/*
 * Copyright 2023 The Android Open Source Project
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

import android.app.Activity;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaCodecInfo.VideoCapabilities;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.Bundle;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;

public class ResourceManagerCodecActivity extends Activity {
    private static final String TAG = "ResourceManagerCodecActivity";
    private static final int MAX_INSTANCES = 32;
    private static final int FRAME_RATE = 30;
    private static final int IFRAME_INTERVAL = 10;
    private boolean mHighResolution = false;
    private volatile boolean mGotReclaimedException = false;
    private int mWidth = 0;
    private int mHeight = 0;
    private int mBitrate = 0;
    private String mMime = MediaFormat.MIMETYPE_VIDEO_AVC;
    private ArrayList<MediaCodec> mCodecs = new ArrayList<MediaCodec>();
    private Thread mWorkerThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate called.");
        super.onCreate(savedInstanceState);
        // Making this as a background Activity
        // so that high priority Activities can reclaim codec from this.
        moveTaskToBack(true);

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            mHighResolution = extras.getBoolean("high-resolution", mHighResolution);
            mMime = extras.getString("mime", mMime);
        }

        if (allocateCodecs(MAX_INSTANCES) == MAX_INSTANCES) {
            // As we haven't reached the limit with MAX_INSTANCES,
            // no need to wait for reclaim exception.
            Log.d(TAG, "We may not get reclaim event");
        }

        useCodecs();
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy called.");
        super.onDestroy();
    }

    // MediaCodec callback
    private static class TestCodecCallback extends MediaCodec.Callback {
        @Override
        public void onInputBufferAvailable(MediaCodec codec, int index) {
        }

        @Override
        public void onOutputBufferAvailable(
                MediaCodec codec, int index, MediaCodec.BufferInfo info) {
        }

        @Override
        public void onError(MediaCodec codec, MediaCodec.CodecException e) {
            Log.d(TAG, "onError " + codec.toString() + " errorCode " + e.getErrorCode());
        }

        @Override
        public void onOutputFormatChanged(MediaCodec codec, MediaFormat format) {
            Log.d(TAG, "onOutputFormatChanged " + codec.toString());
        }
    }

    private MediaCodec.Callback mCallback = new TestCodecCallback();

    // Get a HW Codec info for a given mime (mMime, which is either AVC or HEVC)
    private MediaCodecInfo getCodecInfo(boolean lookForDecoder) {
        MediaCodecList mcl = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
        for (MediaCodecInfo info : mcl.getCodecInfos()) {
            if (info.isSoftwareOnly()) {
                // not testing the sw codecs for now as currently there are't
                // any limit on how many concurrent sw codecs can be created.
                // Allowing too many codecs may lead system into low memory
                // situation and lmkd will kill the test activity and eventually
                // failing the test case.
                continue;
            }
            boolean isEncoder = info.isEncoder();
            if (lookForDecoder && isEncoder) {
                // Looking for a decoder, but found an encoder.
                // Skip it
                continue;
            }
            if (!lookForDecoder && !isEncoder) {
                // Looking for an encoder, but found a decoder.
                // Skip it
                continue;
            }

            // Return the first codec capable of the specified MIME type
            String[] types = info.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                if (types[j].equalsIgnoreCase(mMime)) {
                    return info;
                }
            }
        }

        // no matching codec, return null.
        return null;
    }

    private MediaFormat createVideoFormat(MediaCodecInfo info) {
        CodecCapabilities caps = info.getCapabilitiesForType(mMime);
        VideoCapabilities vcaps = caps.getVideoCapabilities();

        if (mHighResolution) {
            mWidth = vcaps.getSupportedWidths().getUpper();
            mHeight = vcaps.getSupportedHeightsFor(mWidth).getUpper();
            mBitrate = vcaps.getBitrateRange().getUpper();
        } else {
            mWidth = vcaps.getSupportedWidths().getLower();
            mHeight = vcaps.getSupportedHeightsFor(mWidth).getLower();
            mBitrate = vcaps.getBitrateRange().getLower();
        }

        Log.d(TAG, "Mime: " + mMime + " Resolution: " + mWidth + "x" + mHeight
                 + " Bitrate: " + mBitrate);
        MediaFormat format = MediaFormat.createVideoFormat(mMime, mWidth, mHeight);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, caps.colorFormats[0]);
        format.setInteger(MediaFormat.KEY_BIT_RATE, mBitrate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);

        return format;
    }

    private MediaFormat createVideoEncoderFormat(MediaCodecInfo info) {
        MediaFormat format = createVideoFormat(info);
        format.setInteger("profile", 1);
        format.setInteger("level", 1);
        format.setInteger("priority", 1);
        format.setInteger("color-format", CodecCapabilities.COLOR_FormatYUV420Flexible);
        format.setInteger("bitrate-mode", 0);
        format.setInteger("quality", 1);

        return format;
    }

    // Allocates at most max number of codecs
    protected int allocateCodecs(int max) {
        boolean shouldSkip = false;
        MediaCodecInfo info = getCodecInfo(true);
        if (info != null) {
            // Try allocating max number of decoders first.
            String name = info.getName();
            MediaFormat decoderFormat = createVideoFormat(info);
            allocateCodecs(max, name, decoderFormat, true);

            // Try allocating max number of encoder next.
            info = getCodecInfo(false);
            if (info != null) {
                name = info.getName();
                MediaFormat encoderFormat = createVideoEncoderFormat(info);
                allocateCodecs(max, name, encoderFormat, false);
            }
        } else {
            shouldSkip = true;
        }

        if (shouldSkip) {
            Log.d(TAG, "test skipped as there's no supported codec.");
            finishWithResult(ResourceManagerStubActivity.RESULT_CODE_NO_DECODER);
        }

        Log.d(TAG, "allocateCodecs returned " + mCodecs.size());
        return mCodecs.size();
    }


    protected void allocateCodecs(int max, String name, MediaFormat format, boolean decoder) {
        MediaCodec codec = null;
        max += mCodecs.size();
        int flag = decoder ? 0 : MediaCodec.CONFIGURE_FLAG_ENCODE;

        for (int i = mCodecs.size(); i < max; ++i) {
            try {
                Log.d(TAG, "Create codec " + name + " #" + i);
                codec = MediaCodec.createByCodecName(name);
                codec.setCallback(mCallback);
                Log.d(TAG, "Configure Codec: " + format);
                codec.configure(format, null, null, flag);
                Log.d(TAG, "Start codec ");
                codec.start();
                mCodecs.add(codec);
                codec = null;
            } catch (IllegalArgumentException e) {
                Log.d(TAG, "IllegalArgumentException " + e.getMessage());
                break;
            } catch (IOException e) {
                Log.d(TAG, "IOException " + e.getMessage());
                break;
            } catch (MediaCodec.CodecException e) {
                Log.d(TAG, "CodecException 0x" + Integer.toHexString(e.getErrorCode()));
                break;
            } finally {
                if (codec != null) {
                    Log.d(TAG, "release codec");
                    codec.release();
                    codec = null;
                }
            }
        }
    }

    protected void finishWithResult(int result) {
        for (int i = 0; i < mCodecs.size(); ++i) {
            Log.d(TAG, "release codec #" + i);
            mCodecs.get(i).release();
        }
        mCodecs.clear();
        setResult(result);
        finish();
        Log.d(TAG, "Activity finished with: " + result);
    }

    private boolean doUseCodecs() {
        int current = 0;
        try {
            for (current = 0; current < mCodecs.size(); ++current) {
                mCodecs.get(current).getName();
            }
        } catch (MediaCodec.CodecException e) {
            Log.d(TAG, "useCodecs got CodecException 0x" + Integer.toHexString(e.getErrorCode()));
            if (e.getErrorCode() == MediaCodec.CodecException.ERROR_RECLAIMED) {
                Log.d(TAG, "Remove codec " + current + " from the list");
                mCodecs.get(current).release();
                mCodecs.remove(current);
                mGotReclaimedException = true;
            }
            return false;
        }
        return true;
    }

    protected void useCodecs() {
        mWorkerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                Log.i(TAG, "Started the thread");
                long start = System.currentTimeMillis();
                long timeSinceStartedMs = 0;
                boolean success = true;
                while (success && (timeSinceStartedMs < 15000)) {  // timeout in 15s
                    success = doUseCodecs();
                    try {
                        // wait for 50ms before calling doUseCodecs again.
                        Thread.sleep(50 /* millis */);
                    } catch (InterruptedException e) { }
                    timeSinceStartedMs = System.currentTimeMillis() - start;
                }
                if (mGotReclaimedException) {
                    Log.d(TAG, "Got expected reclaim exception.");
                    // As expected a Codec was reclaimed from this (background) Activity.
                    // So, finish with success.
                    finishWithResult(RESULT_OK);
                } else if (success) {
                    Log.d(TAG, "No codec reclaim exception, but codec operations successful.");
                    // Though we were expecting reclaim event, it could be possible that
                    // oem was able to allocate another codec (had enough resources) for the
                    // foreground app. In those case, we need to pass this.
                    finishWithResult(RESULT_OK);
                } else {
                    Log.d(TAG, "Stopped with an unexpected codec exception.");
                    // We were expecting reclaim event OR codec operations to be successful.
                    // In neither of the case, some unexpected error happened.
                    // So, fail the case.
                    finishWithResult(RESULT_CANCELED);
                }
            }
        });
        mWorkerThread.start();
    }
}
