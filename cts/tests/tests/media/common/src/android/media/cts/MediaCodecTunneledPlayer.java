/*
 * Copyright (C) 2014 The Android Open Source Project
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
package android.media.cts;

import android.content.Context;
import android.media.AudioTimestamp;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.util.Log;
import android.view.SurfaceHolder;

import com.google.common.collect.ImmutableList;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * JB(API 21) introduces {@link MediaCodec} tunneled mode API.  It allows apps
 * to use MediaCodec to delegate their Audio/Video rendering to a vendor provided
 * Codec component.
 */
public class MediaCodecTunneledPlayer implements MediaTimeProvider {
    private static final String TAG = MediaCodecTunneledPlayer.class.getSimpleName();

    /** State the player starts in, before configuration. */
    private static final int STATE_IDLE = 1;
    /** State of the player during initial configuration. */
    private static final int STATE_PREPARED = 2;
    /** State of the player after starting the codecs */
    private static final int STATE_STARTED = 3;
    /** State of the player during playback. */
    private static final int STATE_PLAYING = 4;
    /** State of the player when playback is paused. */
    private static final int STATE_PAUSED = 5;

    private final Object mThreadStartedLock = new Object();
    private boolean mThreadStarted = false;
    private byte[] mSessionId;
    private CodecState mAudioTrackState;
    private int mMediaFormatHeight;
    private int mMediaFormatWidth;
    private Float mMediaFormatFrameRate;
    private final Object mStateLock = new Object();
    private int mState;
    private long mDeltaTimeUs;
    private long mDurationUs;
    private Map<Integer, CodecState> mAudioCodecStates;
    private Map<Integer, CodecState> mVideoCodecStates;
    private Map<String, String> mAudioHeaders;
    private Map<String, String> mVideoHeaders;
    private MediaExtractor mAudioExtractor;
    private MediaExtractor mVideoExtractor;
    private SurfaceHolder mSurfaceHolder;
    private Thread mThread;
    private Uri mAudioUri;
    private Uri mVideoUri;
    private boolean mIsTunneled;
    private int mAudioSessionId;
    private Context mContext;

    /*
     * Media player class to playback video using tunneled MediaCodec.
     */
    public MediaCodecTunneledPlayer(Context context, SurfaceHolder holder, boolean tunneled, int AudioSessionId) {
        mContext = context;
        mSurfaceHolder = holder;
        mIsTunneled = tunneled;
        mAudioTrackState = null;
        mState = STATE_IDLE;
        mAudioSessionId = AudioSessionId;
        mThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    synchronized (mThreadStartedLock) {
                        if (mThreadStarted == false) {
                            break;
                        }
                    }
                    synchronized (mStateLock) {
                        if (mState == STATE_PLAYING) {
                            doSomeWork();
                            if (mAudioTrackState != null) {
                                mAudioTrackState.processAudioTrack();
                            }
                        }
                    }
                    try {
                        Thread.sleep(5);
                    } catch (InterruptedException ex) {
                        Log.d(TAG, "Thread interrupted");
                    }
                }
            }
        });
    }

    public void setFrameRate(float frameRate) {
        mMediaFormatFrameRate = frameRate;
    }

    public void setAudioDataSource(Uri uri, Map<String, String> headers) {
        mAudioUri = uri;
        mAudioHeaders = headers;
    }

    public void setVideoDataSource(Uri uri, Map<String, String> headers) {
        mVideoUri = uri;
        mVideoHeaders = headers;
    }

    public final int getMediaFormatHeight() {
        return mMediaFormatHeight;
    }

    public final int getMediaFormatWidth() {
        return mMediaFormatWidth;
    }

    private boolean prepareAudio() throws IOException {
        for (int i = mAudioExtractor.getTrackCount(); i-- > 0;) {
            MediaFormat format = mAudioExtractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);

            if (!mime.startsWith("audio/")) {
                continue;
            }

            Log.d(TAG, "audio track #" + i + " " + format + " " + mime +
                  " Is ADTS:" + getMediaFormatInteger(format, MediaFormat.KEY_IS_ADTS) +
                  " Sample rate:" + getMediaFormatInteger(format, MediaFormat.KEY_SAMPLE_RATE) +
                  " Channel count:" +
                  getMediaFormatInteger(format, MediaFormat.KEY_CHANNEL_COUNT));

            mAudioExtractor.selectTrack(i);
            if (!addTrack(i, format)) {
                Log.e(TAG, "prepareAudio - addTrack() failed!");
                return false;
            }

            if (format.containsKey(MediaFormat.KEY_DURATION)) {
                long durationUs = format.getLong(MediaFormat.KEY_DURATION);

                if (durationUs > mDurationUs) {
                    mDurationUs = durationUs;
                }
                Log.d(TAG, "audio track format #" + i +
                        " Duration:" + mDurationUs + " microseconds");
            }
        }
        return true;
    }

    private boolean prepareVideo() throws IOException {
        for (int i = mVideoExtractor.getTrackCount(); i-- > 0;) {
            MediaFormat format = mVideoExtractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);

            if (!mime.startsWith("video/")) {
                continue;
            }

            mMediaFormatHeight = getMediaFormatInteger(format, MediaFormat.KEY_HEIGHT);
            mMediaFormatWidth = getMediaFormatInteger(format, MediaFormat.KEY_WIDTH);
            Log.d(TAG, "video track #" + i + " " + format + " " + mime +
                  " Width:" + mMediaFormatWidth + ", Height:" + mMediaFormatHeight);

            mVideoExtractor.selectTrack(i);
            if (!addTrack(i, format)) {
                Log.e(TAG, "prepareVideo - addTrack() failed!");
                return false;
            }

            if (format.containsKey(MediaFormat.KEY_DURATION)) {
                long durationUs = format.getLong(MediaFormat.KEY_DURATION);

                if (durationUs > mDurationUs) {
                    mDurationUs = durationUs;
                }
                Log.d(TAG, "track format #" + i + " Duration:" +
                        mDurationUs + " microseconds");
            }
        }
        return true;
    }

    // Creates the extractors, identifies tracks and formats, and then calls MediaCodec.configure
    public boolean prepare() throws IOException {
        if (mState != STATE_IDLE) {
            throw new IllegalStateException("Expected STATE_IDLE, got " + mState);
        }

        if (null == mAudioExtractor) {
            mAudioExtractor = new MediaExtractor();
            if (null == mAudioExtractor) {
                Log.e(TAG, "prepare - Cannot create Audio extractor.");
                return false;
            }
        }

        if (null == mVideoExtractor){
            mVideoExtractor = new MediaExtractor();
            if (null == mVideoExtractor) {
                Log.e(TAG, "prepare - Cannot create Video extractor.");
                return false;
            }
        }

        mAudioExtractor.setDataSource(mContext, mAudioUri, mAudioHeaders);
        if (mVideoUri != null) {
            mVideoExtractor.setDataSource(mContext, mVideoUri, mVideoHeaders);
        }

        if (null == mVideoCodecStates) {
            mVideoCodecStates = new HashMap<Integer, CodecState>();
        } else {
            mVideoCodecStates.clear();
        }

        if (null == mAudioCodecStates) {
            mAudioCodecStates = new HashMap<Integer, CodecState>();
        } else {
            mAudioCodecStates.clear();
        }

        if (!prepareAudio()) {
            Log.e(TAG,"prepare - prepareAudio() failed!");
            return false;
        }
        if (!prepareVideo()) {
            Log.e(TAG,"prepare - prepareVideo() failed!");
            return false;
        }

        mState = STATE_PREPARED;
        return true;
    }

    private boolean addTrack(int trackIndex, MediaFormat format) throws IOException {
        String mime = format.getString(MediaFormat.KEY_MIME);
        boolean isVideo = mime.startsWith("video/");
        boolean isAudio = mime.startsWith("audio/");
        MediaCodec codec;

        // setup tunneled video codec if needed
        if (isVideo && mIsTunneled) {
            format.setFeatureEnabled(MediaCodecInfo.CodecCapabilities.FEATURE_TunneledPlayback,
                        true);
            MediaCodecList mcl = new MediaCodecList(MediaCodecList.ALL_CODECS);
            String codecName = mcl.findDecoderForFormat(format);
            if (codecName == null) {
                Log.e(TAG,"addTrack - Could not find Tunneled playback codec for "+mime+
                        " format!");
                return false;
            }

            codec = MediaCodec.createByCodecName(codecName);
            if (codec == null) {
                Log.e(TAG, "addTrack - Could not create Tunneled playback codec "+
                        codecName+"!");
                return false;
            }

            if (mAudioTrackState != null) {
                format.setInteger(MediaFormat.KEY_AUDIO_SESSION_ID, mAudioSessionId);
            }
        }
        else {
            codec = MediaCodec.createDecoderByType(mime);
            if (codec == null) {
                Log.e(TAG, "addTrack - Could not create regular playback codec for mime "+
                        mime+"!");
                return false;
            }
        }
        if (isVideo && mMediaFormatFrameRate != null) {
            format.setFloat(MediaFormat.KEY_FRAME_RATE, mMediaFormatFrameRate);
        }
        codec.configure(
                format,
                isVideo ? mSurfaceHolder.getSurface() : null, null, 0);

        CodecState state;
        if (isVideo) {
            state = new CodecState((MediaTimeProvider)this, mVideoExtractor,
                            trackIndex, format, codec, true, mIsTunneled, mAudioSessionId);
            mVideoCodecStates.put(Integer.valueOf(trackIndex), state);
        } else {
            state = new CodecState((MediaTimeProvider)this, mAudioExtractor,
                            trackIndex, format, codec, true, mIsTunneled, mAudioSessionId);
            mAudioCodecStates.put(Integer.valueOf(trackIndex), state);
        }

        if (isAudio) {
            mAudioTrackState = state;
        }

        return true;
    }

    protected int getMediaFormatInteger(MediaFormat format, String key) {
        return format.containsKey(key) ? format.getInteger(key) : 0;
    }

    // Calls MediaCodec.start
    public void startCodec() {
        Log.d(TAG, "start");

        if (mState != STATE_PREPARED) {
            throw new IllegalStateException("Expected STATE_PREAPRED, got " + mState);
        }

        for (CodecState state : mVideoCodecStates.values()) {
            state.startCodec();
        }

        for (CodecState state : mAudioCodecStates.values()) {
            state.startCodec();
        }

        mDeltaTimeUs = -1;
        mState = STATE_STARTED;
    }

    // Starts the decoding threads and then starts AudioTrack playback
    public void play() {
        if (mState != STATE_STARTED) {
            throw new IllegalStateException("Expected STATE_STARTED, got " + mState);
        }
        mState = STATE_PLAYING;

        synchronized (mThreadStartedLock) {
            mThreadStarted = true;
            mThread.start();
        }

        for (CodecState state : mVideoCodecStates.values()) {
            state.play();
        }

        for (CodecState state : mAudioCodecStates.values()) {
            state.play();
        }
    }

    // Pauses playback by pausing the AudioTrack
    public void pause() {
        Log.d(TAG, "pause");

        if (mState != STATE_PLAYING) {
            throw new IllegalStateException("Expected STATE_PLAYING, got " + mState);
        }

        synchronized (mStateLock) {
            for (CodecState state : mVideoCodecStates.values()) {
                state.pause();
            }

            for (CodecState state : mAudioCodecStates.values()) {
                state.pause();
            }

            mState = STATE_PAUSED;
        }
    }

    // Resume playback when paused
    public void resume() {
        Log.d(TAG, "resume");

        if (mState != STATE_PAUSED) {
            throw new IllegalStateException("Expected STATE_PAUSED, got " + mState);
        }

        synchronized (mStateLock) {
            for (CodecState state : mVideoCodecStates.values()) {
                state.play();
            }

            for (CodecState state : mAudioCodecStates.values()) {
                state.play();
            }

            mState = STATE_PLAYING;
        }
    }

    public void flush() {
        Log.d(TAG, "flush");

        if (mState != STATE_PAUSED) {
            throw new IllegalStateException("Expected STATE_PAUSED, got " + mState);
        }

        for (CodecState state : mAudioCodecStates.values()) {
            state.flush();
        }

        for (CodecState state : mVideoCodecStates.values()) {
            state.flush();
        }
    }

    /** Seek all tracks to the first sample time.
     *
     * @param  presentationTimeOffsetUs The offset for the presentation time to start at.
     * @throws IllegalStateException  if the player is not paused
     */
    public void seekToBeginning(long presentationTimeOffsetUs) {
        Log.d(TAG, "seekToBeginning");
        if (mState != STATE_PAUSED) {
            throw new IllegalStateException("Expected STATE_PAUSED, got " + mState);
        }

        for (CodecState state : mVideoCodecStates.values()) {
            state.seekToBeginning(presentationTimeOffsetUs);
        }

        for (CodecState state : mAudioCodecStates.values()) {
            state.seekToBeginning(presentationTimeOffsetUs);
        }
    }

    /**
     * Enables or disables looping. Should be called after {@link #prepare()}.
     */
    public void setLoopEnabled(boolean enabled) {
        if (mState != STATE_PREPARED) {
            throw new IllegalStateException("Expected STATE_PREPARED, got " + mState);
        }

        for (CodecState state : mVideoCodecStates.values()) {
            state.setLoopEnabled(enabled);
        }

        for (CodecState state : mAudioCodecStates.values()) {
            state.setLoopEnabled(enabled);
        }
    }

    public void reset() {
        if (mState == STATE_PLAYING) {
            pause();
        }
        if (mVideoCodecStates != null) {
            for (CodecState state : mVideoCodecStates.values()) {
                state.release();
            }
            mVideoCodecStates = null;
        }

        if (mAudioCodecStates != null) {
            for (CodecState state : mAudioCodecStates.values()) {
                state.release();
            }
            mAudioCodecStates = null;
        }

        if (mAudioExtractor != null) {
            mAudioExtractor.release();
            mAudioExtractor = null;
        }

        if (mVideoExtractor != null) {
            mVideoExtractor.release();
            mVideoExtractor = null;
        }

        mDurationUs = -1;
        mState = STATE_IDLE;

        synchronized (mThreadStartedLock) {
            mThreadStarted = false;
        }
        try {
            mThread.join();
        } catch (InterruptedException ex) {
            Log.d(TAG, "mThread.join ", ex);
        }
    }

    public boolean isEnded() {
        for (CodecState state : mVideoCodecStates.values()) {
          if (!state.isEnded()) {
            return false;
          }
        }

        for (CodecState state : mAudioCodecStates.values()) {
            if (!state.isEnded()) {
              return false;
            }
        }

        return true;
    }

    private void doSomeWork() {
        try {
            for (CodecState state : mVideoCodecStates.values()) {
                state.doSomeWork();
            }
        } catch (IllegalStateException e) {
            throw new Error("Video CodecState.doSomeWork", e);
        }

        try {
            for (CodecState state : mAudioCodecStates.values()) {
                state.doSomeWork();
            }
        } catch (IllegalStateException e) {
            throw new Error("Audio CodecState.doSomeWork", e);
        }

    }

    public long getNowUs() {
        if (mAudioTrackState == null) {
            return System.currentTimeMillis() * 1000;
        }

        return mAudioTrackState.getAudioTimeUs();
    }

    public long getRealTimeUsForMediaTime(long mediaTimeUs) {
        if (mDeltaTimeUs == -1) {
            long nowUs = getNowUs();
            mDeltaTimeUs = nowUs - mediaTimeUs;
        }

        return mDeltaTimeUs + mediaTimeUs;
    }

    public int getDuration() {
        return (int)((mDurationUs + 500) / 1000);
    }

    /**
     * Retrieve the presentation timestamp of the latest queued output sample.
     * In tunnel mode, retrieves the presentation timestamp of the latest rendered video frame.
     * @return presentation timestamp in microseconds, or {@code CodecState.UNINITIALIZED_TIMESTAMP}
     * if playback has not started.
    */
    public int getCurrentPosition() {
        if (mVideoCodecStates == null) {
            return CodecState.UNINITIALIZED_TIMESTAMP;
        }

        long positionUs = CodecState.UNINITIALIZED_TIMESTAMP;

        for (CodecState state : mVideoCodecStates.values()) {
            long trackPositionUs = state.getCurrentPositionUs();
            if (trackPositionUs > positionUs) {
                positionUs = trackPositionUs;
            }
        }

        if (positionUs == CodecState.UNINITIALIZED_TIMESTAMP) {
            return CodecState.UNINITIALIZED_TIMESTAMP;
        }
        return (int) (positionUs + 500) / 1000;
    }

    /**
     * Returns the system time of the latest rendered frame in any of the video codecs.
     */
    public long getCurrentRenderedSystemTimeNano() {
        if (mVideoCodecStates == null) {
            return 0;
        }

        long position = 0;

        for (CodecState state : mVideoCodecStates.values()) {
            long trackPosition = state.getRenderedVideoSystemTimeNano();

            if (trackPosition > position) {
                position = trackPosition;
            }
        }
        return position;
    }

    /**
     * Returns the timestamp of the last written audio sample, in microseconds.
     */
    public long getAudioTrackPositionUs() {
        if (mAudioTrackState == null) {
            return 0;
        }
        return mAudioTrackState.getCurrentPositionUs();
    }

    /**
     * Returns the presentation timestamp of the last rendered video frame.
     *
     * Note: This assumes there is exactly one video codec running in the player.
     */
    public long getVideoTimeUs() {
        if (mVideoCodecStates == null || mVideoCodecStates.get(0) == null) {
            return CodecState.UNINITIALIZED_TIMESTAMP;
        }
        return mVideoCodecStates.get(0).getVideoTimeUs();
    }

    public long getVideoSystemTimeNs() {
        if (mVideoCodecStates == null || mVideoCodecStates.get(0) == null) {
            return -1;
        }
        return mVideoCodecStates.get(0).getVideoTimeUs();

    }

    /**
     * Returns the ordered list of video frame timestamps rendered in tunnel mode.
     *
     * Note: This assumes there is exactly one video codec running in the player.
     */
    public ImmutableList<Long> getRenderedVideoFrameTimestampList() {
        return mVideoCodecStates.get(0).getRenderedVideoFrameTimestampList();
    }

    /**
     * Returns the ordered list of system times of rendered video frames in tunnel-mode.
     *
     * Note: This assumes there is at most one tunneled mode video codec running in the player.
     */
    public ImmutableList<Long> getRenderedVideoFrameSystemTimeList() {
        if (mVideoCodecStates == null) {
            return ImmutableList.<Long>of();
        }

        for (CodecState state : mVideoCodecStates.values()) {
            ImmutableList<Long> timestamps = state.getRenderedVideoFrameSystemTimeList();
            if (!timestamps.isEmpty())
                return timestamps;
        }
        return ImmutableList.<Long>of();
    }

    /**
     * When the player is on stand-by, tries to queue one frame worth of video per video codec.
     *
     * Returns arbitrarily the timestamp of any frame queued this way by one of the video codecs.
     * Returns null if no video frame were queued.
     */
    public Long queueOneVideoFrame() {
        Log.d(TAG, "queueOneVideoFrame");

        if (mState != STATE_STARTED && mState != STATE_PAUSED) {
            throw new IllegalStateException("Expected STARTED or PAUSED, got " + mState);
        }

        Long result = null;
        if (mVideoCodecStates != null) {
            for (CodecState state : mVideoCodecStates.values()) {
                Long timestamp = state.doSomeWork(true /* mustWait */);
                if (timestamp != null) {
                    result = timestamp;
                }
            }
        }
        return result;
    }

    /**
     * Configure video peek for the video codecs attached to the player.
     */
    public void setVideoPeek(boolean enable) {
        Log.d(TAG, "setVideoPeek");
        if (mVideoCodecStates == null) {
            return;
        }

        for (CodecState state: mVideoCodecStates.values()) {
            state.setVideoPeek(enable);
        }
    }

    public AudioTimestamp getTimestamp() {
        if (mAudioCodecStates == null) {
            return null;
        }

        AudioTimestamp timestamp = new AudioTimestamp();
        if (mAudioCodecStates.size() != 0) {
            timestamp =
                    mAudioCodecStates.entrySet().iterator().next().getValue().getTimestamp();
        }
        return timestamp;
    }

    /** Queries the attached video codecs for video peek ready signals.
     *
     * Returns true if any of the video codecs have video peek ready.
     * Returns false otherwise.
     */
    public boolean isFirstTunnelFrameReady() {
        Log.d(TAG, "firstTunnelFrameReady");
        if (mVideoCodecStates == null) {
            return false;
        }

        for (CodecState state : mVideoCodecStates.values()) {
            if (state.isFirstTunnelFrameReady()) {
                return true;
            }
        }
        return false;
    }

    /** Returns the number of frames that have been sent down to the HAL. */
    public int getAudioFramesWritten() {
        if (mAudioCodecStates == null) {
            return -1;
        }
        return mAudioCodecStates.entrySet().iterator().next().getValue().getFramesWritten();
    }

    /** Configure underrun simulation on audio codecs. */
    public void stopDrainingAudioOutputBuffers(boolean stop) {
        for (CodecState state: mAudioCodecStates.values()) {
            state.stopDrainingOutputBuffers(stop);
        }
    }

    /** Configure an offset (in Ns) to audio content to simulate track desynchronization. */
    public void setAudioTrackOffsetNs(long audioOffsetNs) {
        if (mAudioTrackState != null) {
            mAudioTrackState.setAudioOffsetNs(audioOffsetNs);
        }
    }

    /** Returns the underlying {@code AudioTrack}, if any. */
    public AudioTrack getAudioTrack() {
        if (mAudioTrackState != null) {
            return mAudioTrackState.getAudioTrack();
        }
        return null;
    }
}
