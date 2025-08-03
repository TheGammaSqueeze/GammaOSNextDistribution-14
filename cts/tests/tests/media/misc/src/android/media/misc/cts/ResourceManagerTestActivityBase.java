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

public class ResourceManagerTestActivityBase extends Activity {
    public static final int TYPE_NONSECURE = 0;
    public static final int TYPE_SECURE = 1;
    public static final int TYPE_MIX = 2;
    private static final int FRAME_RATE = 10;
    // 10 seconds between I-frames
    private static final int IFRAME_INTERVAL = 10;
    protected static final int MAX_INSTANCES = 32;
    // Less important codec of value 100.
    private static final int CODEC_IMPORTANCE_100 = 100;
    private static final MediaCodecList sMCL = new MediaCodecList(MediaCodecList.REGULAR_CODECS);

    private boolean mIsEncoder = false;
    protected boolean mChangingCodecImportance = false;
    private boolean mUseCodecImportanceAtConfig = false;
    private boolean mUseCodecImportanceLater = false;
    private int mWidth = 0;
    private int mHeight = 0;
    protected String TAG;
    private String mMime = MediaFormat.MIMETYPE_VIDEO_AVC;
    private String mCodecName = "none";

    private ArrayList<MediaCodec> mCodecs = new ArrayList<MediaCodec>();
    private MediaCodec mFirstMediaCodec;

    private class TestCodecCallback extends MediaCodec.Callback {
        @Override
        public void onInputBufferAvailable(MediaCodec codec, int index) {
            Log.v(TAG, "onInputBufferAvailable " + codec.toString());
        }

        @Override
        public void onOutputBufferAvailable(
                MediaCodec codec, int index, MediaCodec.BufferInfo info) {
            Log.v(TAG, "onOutputBufferAvailable " + codec.toString());
        }

        @Override
        public void onError(MediaCodec codec, MediaCodec.CodecException e) {
            int error = e.getErrorCode();
            Log.e(TAG, "onError " + codec.toString() + " errorCode " + error);
            if (mChangingCodecImportance && error == MediaCodec.CodecException.ERROR_RECLAIMED) {
                if (mFirstMediaCodec == codec) {
                    mGotReclaimedException = true;
                    Log.d(TAG, "Codec " + codec + " Was expected to be Reclaimed");
                }
                codec.release();
            }
        }

        @Override
        public void onOutputFormatChanged(MediaCodec codec, MediaFormat format) {
            Log.v(TAG, "onOutputFormatChanged " + codec.toString());
        }
    }

    private MediaCodec.Callback mCallback = new TestCodecCallback();

    private MediaFormat getTestFormat(CodecCapabilities caps, boolean securePlayback,
            boolean highResolution) {
        VideoCapabilities vcaps = caps.getVideoCapabilities();
        int bitrate = 0;

        if (highResolution) {
            if (mWidth == 0 || mHeight == 0) {
                mWidth = vcaps.getSupportedWidths().getUpper();
                mHeight = vcaps.getSupportedHeightsFor(mWidth).getUpper();
            }
            bitrate = vcaps.getBitrateRange().getUpper();
        } else {
            if (mWidth == 0 || mHeight == 0) {
                mWidth = vcaps.getSupportedWidths().getLower();
                mHeight = vcaps.getSupportedHeightsFor(mWidth).getLower();
            }
            bitrate = vcaps.getBitrateRange().getLower();
        }

        MediaFormat format = MediaFormat.createVideoFormat(mMime, mWidth, mHeight);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, caps.colorFormats[0]);
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);
        format.setFeatureEnabled(CodecCapabilities.FEATURE_SecurePlayback, securePlayback);

        if (mIsEncoder) {
            // TODO: Facilitate the verification of reclaim when the codec is configured
            // in realtime and non-realtime priorities.
            // format.setInteger(MediaFormat.KEY_PRIORITY, 1);
            // format.setInteger(MediaFormat.KEY_PRIORITY, 0);
            // TODO: Make sure this color format is supported by the encoder
            // If not, pick one that is supported.
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                    CodecCapabilities.COLOR_FormatYUV420Flexible);
        }
        return format;
    }

    private MediaCodecInfo getCodecInfo(boolean securePlayback) {
        if (mCodecName.equals("none")) {
            // We don't know the codec name yet, so look for a decoder
            // that supports the mime type.
            return getDecoderInfo(securePlayback);
        }

        // We already know the codec name, so return the info directly.
        for (MediaCodecInfo info : sMCL.getCodecInfos()) {
            if (info.getName().equals(mCodecName)) {
                mIsEncoder = info.isEncoder();
                return info;
            }
        }

        return null;
    }

    private MediaCodecInfo getDecoderInfo(boolean securePlayback) {
        MediaCodecInfo fallbackInfo = null;

        for (MediaCodecInfo info : sMCL.getCodecInfos()) {
            if (info.isEncoder()) {
                // Skip through encoders.
                continue;
            }
            CodecCapabilities caps;
            try {
                caps = info.getCapabilitiesForType(mMime);
                boolean securePlaybackSupported =
                        caps.isFeatureSupported(CodecCapabilities.FEATURE_SecurePlayback);
                boolean securePlaybackRequired =
                        caps.isFeatureRequired(CodecCapabilities.FEATURE_SecurePlayback);
                if ((securePlayback && securePlaybackSupported) ||
                        (!securePlayback && !securePlaybackRequired) ) {
                    Log.d(TAG, "securePlayback " + securePlayback + " will use " + info.getName());
                } else {
                    Log.d(TAG, "securePlayback " + securePlayback + " skip " + info.getName());
                    // If in case there is no secure decoder, use the first
                    // one as fallback.
                    if (fallbackInfo == null) {
                        fallbackInfo = info;
                    }
                    continue;
                }
            } catch (IllegalArgumentException e) {
                // mime is not supported
                continue;
            }
            return info;
        }

        return fallbackInfo;
    }

    protected int allocateCodecs(int max) {
        Bundle extras = getIntent().getExtras();
        int type = TYPE_NONSECURE;
        boolean highResolution = false;
        boolean isResolutionSet = true;
        if (extras != null) {
            type = extras.getInt("test-type", type);
            // Check if codec name has been passed.
            mCodecName = extras.getString("name", mCodecName);
            // Check if mime has been passed.
            mMime = extras.getString("mime", mMime);
            // Check if resolution has been passed.
            mWidth = extras.getInt("width");
            mHeight = extras.getInt("height");
            if (mWidth == 0 || mHeight == 0) {
                // Either no resolution has been passed or its invalid.
                isResolutionSet = false;
                // See if the high-resolution flag has been set.
                highResolution = extras.getBoolean("high-resolution", highResolution);
            } else if (mHeight >= 1080) {
                highResolution = true;
            }

            // See if we need to set codec-importance during config.
            mUseCodecImportanceAtConfig = extras.getBoolean("codec-importance-at-config", false);
            if (!mUseCodecImportanceAtConfig) {
                // See if we need to set codec-importance later (using setParameters)
                mUseCodecImportanceLater = extras.getBoolean("codec-importance-later", false);
            }
            // Setting this flag to track that we get an expected reclaim on expected codec.
            mChangingCodecImportance = mUseCodecImportanceAtConfig || mUseCodecImportanceLater;
        }

        boolean shouldSkip = false;
        boolean securePlayback;
        if (type == TYPE_NONSECURE || type == TYPE_MIX) {
            securePlayback = false;
            MediaCodecInfo info = getCodecInfo(securePlayback);
            if (info != null) {
                allocateCodecs(max, info, securePlayback, highResolution);
            } else {
                shouldSkip = true;
            }
        }

        if (!shouldSkip) {
            if (type == TYPE_SECURE || type == TYPE_MIX) {
                if (!isResolutionSet && type == TYPE_MIX) {
                    // clear previously set resolutions by the unsecure codecs,
                    // so that we read the secure codec resolutions again.
                    mWidth = 0;
                    mHeight = 0;
                }
                securePlayback = true;
                MediaCodecInfo info = getCodecInfo(securePlayback);
                if (info != null) {
                    allocateCodecs(max, info, securePlayback, highResolution);
                } else {
                    shouldSkip = true;
                }
            }
        }

        if (shouldSkip) {
            Log.d(TAG, "test skipped as there's no supported codec.");
            finishWithResult(ResourceManagerStubActivity.RESULT_CODE_NO_DECODER);
        }

        Log.d(TAG, "allocateCodecs(" +  mCodecName + ":" + mMime + ":" + mWidth
                + "x" + mHeight + ") returned " + mCodecs.size());
        return mCodecs.size();
    }

    private void changeCodecImportance(MediaCodec codec, int importance) {
        final Bundle params = new Bundle();
        params.putInt(MediaFormat.KEY_IMPORTANCE, importance);
        codec.setParameters(params);
    }

    protected void allocateCodecs(int max, MediaCodecInfo info, boolean securePlayback,
            boolean highResolution) {
        mCodecName = info.getName();
        int flag = mIsEncoder ? MediaCodec.CONFIGURE_FLAG_ENCODE : 0;
        CodecCapabilities caps = info.getCapabilitiesForType(mMime);
        MediaFormat format = getTestFormat(caps, securePlayback, highResolution);
        MediaCodec codec = null;
        boolean firstCodec = true;
        boolean loweredFirstCodecImportance = false;
        boolean lastCodecAttemptWithImportance = false;
        boolean loweredLastCodecImportance = false;
        for (int i = mCodecs.size(); i < max; ++i) {
            try {
                Log.d(TAG, "Create codec " + mCodecName + " #" + i);
                codec = MediaCodec.createByCodecName(mCodecName);
                codec.setCallback(mCallback);
                Log.d(TAG, "Configure codec " + format);

                // if it's the first codec and if we are to set the importance while configuring,
                // then set the same through MediaFormat with codec-importance as a lesser value.
                if (firstCodec && mUseCodecImportanceAtConfig) {
                    format.setInteger(MediaFormat.KEY_IMPORTANCE, CODEC_IMPORTANCE_100);
                    mFirstMediaCodec = codec;
                }
                // The last codec creation failed because of insufficient resources.
                // So, lets attempt to create one last codec with lesser importance (100)
                // and expect it to fail as well.
                if (lastCodecAttemptWithImportance) {
                    format.setInteger(MediaFormat.KEY_IMPORTANCE, CODEC_IMPORTANCE_100);
                    loweredLastCodecImportance = true;
                }
                codec.configure(format, null, null, flag);

                // We don't want to lower the importance for other codecs.
                // So, remove it from the format, if it were set above.
                // The remaining codecs will have the default codec-importance as highest (0).
                if (firstCodec && mUseCodecImportanceAtConfig) {
                    format.removeKey(MediaFormat.KEY_IMPORTANCE);
                    firstCodec = false;
                }
                Log.d(TAG, "Start codec " + format);
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
                int error = e.getErrorCode();
                Log.d(TAG, "CodecException 0x" + Integer.toHexString(error));
                if (mUseCodecImportanceLater && !loweredFirstCodecImportance
                        && error == MediaCodec.CodecException.ERROR_INSUFFICIENT_RESOURCE) {
                    // Making sure we have at least one codec started.
                    Log.d(TAG, "Make Codec 0 less important so that it will be reclaimed");
                    if (i > 0) {
                        mFirstMediaCodec = mCodecs.get(0);
                        changeCodecImportance(mFirstMediaCodec, CODEC_IMPORTANCE_100);
                        // We are doing it only once.
                        loweredFirstCodecImportance = true;
                        continue;
                    } else {
                        // We don't have any codecs to lower the importance.
                        break;
                    }
                } else if (!loweredLastCodecImportance && mChangingCodecImportance) {
                    // The last codec start failed because of insufficient resources.
                    // So, lets attempt to create one last codec with lesser importance (100)
                    // and expect it to fail as well.
                    Log.d(TAG, "Attempt creating less important codec and expect it to fail");
                    lastCodecAttemptWithImportance = true;
                    continue;
                } else {
                    break;
                }
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
            Log.d(TAG, "release codec #" + i + " : " + mCodecs.get(i).toString());
            mCodecs.get(i).release();
        }
        mCodecs.clear();
        setResult(result);
        finish();
        Log.d(TAG, "activity finished with: " + result);
    }

    private void doUseCodecs() {
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
                mUseCodecs = false;
            }
            return;
        }
    }

    protected boolean mWaitForReclaim = true;
    private Thread mWorkerThread;
    private volatile boolean mUseCodecs = true;
    private volatile boolean mGotReclaimedException = false;
    protected void useCodecs() {
        mWorkerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                long start = System.currentTimeMillis();
                long timeSinceStartedMs = 0;
                while (mUseCodecs && (timeSinceStartedMs < 15000)) {  // timeout in 15s
                    doUseCodecs();
                    try {
                        Thread.sleep(50 /* millis */);
                    } catch (InterruptedException e) {}
                    timeSinceStartedMs = System.currentTimeMillis() - start;
                }
                if (mGotReclaimedException) {
                    Log.d(TAG, "Got expected reclaim exception.");
                    finishWithResult(RESULT_OK);
                } else {
                    Log.d(TAG, "Stopped without getting reclaim exception.");
                    // if the test is supposed to wait for reclaim event then this is a failure,
                    // otherwise this is a pass.
                    finishWithResult(mWaitForReclaim ? RESULT_CANCELED : RESULT_OK);
                }
            }
        });
        mWorkerThread.start();
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy called.");
        super.onDestroy();
    }
}
