package com.android.tv.samples.sampletunertvinput;

import static android.media.tv.TvInputManager.VIDEO_UNAVAILABLE_REASON_TUNING;
import static android.media.tv.TvInputManager.VIDEO_UNAVAILABLE_REASON_UNKNOWN;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaFormat;
import android.media.tv.TvContract;
import android.media.tv.tuner.dvr.DvrPlayback;
import android.media.tv.tuner.dvr.DvrSettings;
import android.media.tv.tuner.filter.Filter;
import android.media.tv.tuner.filter.FilterCallback;
import android.media.tv.tuner.filter.FilterEvent;
import android.media.tv.tuner.filter.MediaEvent;
import android.media.tv.tuner.Tuner;
import android.media.tv.TvInputService;
import android.media.tv.tuner.filter.SectionEvent;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;
import android.view.Surface;

import com.android.tv.common.util.Clock;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;


/** SampleTunerTvInputService */
public class SampleTunerTvInputService extends TvInputService {
    private static final String TAG = "SampleTunerTvInput";
    private static final boolean DEBUG = true;

    private static final int TIMEOUT_US = 100000;
    private static final boolean SAVE_DATA = false;
    private static final boolean USE_DVR = true;
    private static final String MEDIA_INPUT_FILE_NAME = "media.ts";
    private static final MediaFormat VIDEO_FORMAT;

    static {
        // format extracted for the specific input file
        VIDEO_FORMAT = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 480, 360);
        VIDEO_FORMAT.setInteger(MediaFormat.KEY_TRACK_ID, 1);
        VIDEO_FORMAT.setLong(MediaFormat.KEY_DURATION, 10000000);
        VIDEO_FORMAT.setInteger(MediaFormat.KEY_LEVEL, 256);
        VIDEO_FORMAT.setInteger(MediaFormat.KEY_PROFILE, 65536);
        ByteBuffer csd = ByteBuffer.wrap(
                new byte[] {0, 0, 0, 1, 103, 66, -64, 30, -39, 1, -32, -65, -27, -64, 68, 0, 0, 3,
                        0, 4, 0, 0, 3, 0, -16, 60, 88, -71, 32});
        VIDEO_FORMAT.setByteBuffer("csd-0", csd);
        csd = ByteBuffer.wrap(new byte[] {0, 0, 0, 1, 104, -53, -125, -53, 32});
        VIDEO_FORMAT.setByteBuffer("csd-1", csd);
    }

    public static final String INPUT_ID =
            "com.android.tv.samples.sampletunertvinput/.SampleTunerTvInputService";
    private String mSessionId;
    private Uri mChannelUri;

    @Override
    public TvInputSessionImpl onCreateSession(String inputId, String sessionId) {
        TvInputSessionImpl session =  new TvInputSessionImpl(this);
        if (DEBUG) {
            Log.d(TAG, "onCreateSession(inputId=" + inputId + ", sessionId=" + sessionId + ")");
        }
        mSessionId = sessionId;
        return session;
    }

    @Override
    public TvInputSessionImpl onCreateSession(String inputId) {
        if (DEBUG) {
            Log.d(TAG, "onCreateSession(inputId=" + inputId + ")");
        }
        return new TvInputSessionImpl(this);
    }

    class TvInputSessionImpl extends Session {

        private final Context mContext;
        private Handler mHandler;

        private Surface mSurface;
        private Filter mAudioFilter;
        private Filter mVideoFilter;
        private Filter mSectionFilter;
        private DvrPlayback mDvr;
        private Tuner mTuner;
        private MediaCodec mMediaCodec;
        private Thread mDecoderThread;
        private Deque<MediaEventData> mDataQueue;
        private List<MediaEventData> mSavedData;
        private long mCurrentLoopStartTimeUs = 0;
        private long mLastFramePtsUs = 0;
        private boolean mVideoAvailable;
        private boolean mDataReady = false;


        public TvInputSessionImpl(Context context) {
            super(context);
            mContext = context;
        }

        @Override
        public void onRelease() {
            if (DEBUG) {
                Log.d(TAG, "onRelease");
            }
            if (mDecoderThread != null) {
                mDecoderThread.interrupt();
                mDecoderThread = null;
            }
            if (mMediaCodec != null) {
                mMediaCodec.release();
                mMediaCodec = null;
            }
            if (mAudioFilter != null) {
                mAudioFilter.close();
            }
            if (mVideoFilter != null) {
                mVideoFilter.close();
            }
            if (mSectionFilter != null) {
                mSectionFilter.close();
            }
            if (mDvr != null) {
                mDvr.close();
                mDvr = null;
            }
            if (mTuner != null) {
                mTuner.close();
                mTuner = null;
            }
            mDataQueue = null;
            mSavedData = null;
        }

        @Override
        public boolean onSetSurface(Surface surface) {
            if (DEBUG) {
                Log.d(TAG, "onSetSurface");
            }
            this.mSurface = surface;
            return true;
        }

        @Override
        public void onSetStreamVolume(float v) {
            if (DEBUG) {
                Log.d(TAG, "onSetStreamVolume " + v);
            }
        }

        @Override
        public boolean onTune(Uri uri) {
            if (DEBUG) {
                Log.d(TAG, "onTune " + uri);
            }
            if (!initCodec()) {
                Log.e(TAG, "null codec!");
                return false;
            }
            mChannelUri = uri;
            mHandler = new Handler();
            mVideoAvailable = false;
            notifyVideoUnavailable(VIDEO_UNAVAILABLE_REASON_TUNING);

            mDecoderThread =
                    new Thread(
                            this::decodeInternal,
                            "sample-tuner-tis-decoder-thread");
            mDecoderThread.start();
            return true;
        }

        @Override
        public void onSetCaptionEnabled(boolean b) {
            if (DEBUG) {
                Log.d(TAG, "onSetCaptionEnabled " + b);
            }
        }

        private FilterCallback videoFilterCallback() {
            return new FilterCallback() {
                @Override
                public void onFilterEvent(Filter filter, FilterEvent[] events) {
                    if (DEBUG) {
                        Log.d(TAG, "onFilterEvent video, size=" + events.length);
                    }
                    for (int i = 0; i < events.length; i++) {
                        if (DEBUG) {
                            Log.d(TAG, "events[" + i + "] is "
                                    + events[i].getClass().getSimpleName());
                        }
                        if (events[i] instanceof MediaEvent) {
                            MediaEvent me = (MediaEvent) events[i];

                            MediaEventData storedEvent = MediaEventData.generateEventData(me);
                            if (storedEvent == null) {
                                continue;
                            }
                            mDataQueue.add(storedEvent);
                            if (SAVE_DATA) {
                                mSavedData.add(storedEvent);
                            }
                        }
                    }
                }

                @Override
                public void onFilterStatusChanged(Filter filter, int status) {
                    if (DEBUG) {
                        Log.d(TAG, "onFilterEvent video, status=" + status);
                    }
                    if (status == Filter.STATUS_DATA_READY) {
                        mDataReady = true;
                    }
                }
            };
        }

        private FilterCallback sectionFilterCallback() {
            return new FilterCallback() {
                @Override
                public void onFilterEvent(Filter filter, FilterEvent[] events) {
                    if (DEBUG) {
                        Log.d(TAG, "onFilterEvent section, size=" + events.length);
                    }
                    for (int i = 0; i < events.length; i++) {
                        if (DEBUG) {
                            Log.d(TAG, "events[" + i + "] is "
                                    + events[i].getClass().getSimpleName());
                        }
                        if (events[i] instanceof SectionEvent) {
                            SectionEvent sectionEvent = (SectionEvent) events[i];
                            int dataSize = (int)sectionEvent.getDataLengthLong();
                            if (DEBUG) {
                                Log.d(TAG, "section dataSize:" + dataSize);
                            }

                            byte[] data = new byte[dataSize];
                            filter.read(data, 0, dataSize);

                            handleSection(data);
                        }
                    }
                }

                @Override
                public void onFilterStatusChanged(Filter filter, int status) {
                    if (DEBUG) {
                        Log.d(TAG, "onFilterStatusChanged section, status=" + status);
                    }
                }
            };
        }

        private boolean initCodec() {
            if (mMediaCodec != null) {
                mMediaCodec.release();
                mMediaCodec = null;
            }
            try {
                mMediaCodec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
                mMediaCodec.configure(VIDEO_FORMAT, mSurface, null, 0);
            } catch (IOException e) {
                Log.e(TAG, "Error in initCodec: " + e.getMessage());
            }

            if (mMediaCodec == null) {
                Log.e(TAG, "null codec!");
                mVideoAvailable = false;
                notifyVideoUnavailable(VIDEO_UNAVAILABLE_REASON_UNKNOWN);
                return false;
            }
            return true;
        }

        private void decodeInternal() {
            mDataQueue = new ArrayDeque<>();
            mSavedData = new ArrayList<>();
            mTuner = new Tuner(mContext, mSessionId,
                    TvInputService.PRIORITY_HINT_USE_CASE_TYPE_LIVE);

            mAudioFilter = SampleTunerTvInputUtils.createAvFilter(mTuner, mHandler,
                    SampleTunerTvInputUtils.createDefaultLoggingFilterCallback("audio"), true);
            mVideoFilter = SampleTunerTvInputUtils.createAvFilter(mTuner, mHandler,
                    videoFilterCallback(), false);
            mSectionFilter = SampleTunerTvInputUtils.createSectionFilter(mTuner, mHandler,
                    sectionFilterCallback());
            mAudioFilter.start();
            mVideoFilter.start();
            mSectionFilter.start();

            // Dvr Playback can be used to read a file instead of relying on physical tuner
            if (USE_DVR) {
                mDvr = SampleTunerTvInputUtils.configureDvrPlayback(mTuner, mHandler,
                        DvrSettings.DATA_FORMAT_TS);
                SampleTunerTvInputUtils.readFilePlaybackInput(getApplicationContext(), mDvr,
                        MEDIA_INPUT_FILE_NAME);
                mDvr.start();
            } else {
                SampleTunerTvInputUtils.tune(mTuner, mHandler);
            }
            mMediaCodec.start();

            try {
                while (!Thread.interrupted()) {
                    if (!mDataReady) {
                        Thread.sleep(100);
                        continue;
                    }
                    if (!mDataQueue.isEmpty()) {
                        if (handleDataBuffer(mDataQueue.getFirst())) {
                            // data consumed, remove.
                            mDataQueue.pollFirst();
                        }
                    }
                    else if (SAVE_DATA) {
                        if (DEBUG) {
                            Log.d(TAG, "Adding saved data to data queue");
                        }
                        mDataQueue.addAll(mSavedData);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error in decodeInternal: " + e.getMessage());
            }
        }

        private void handleSection(byte[] data) {
            SampleTunerTvInputSectionParser.EitEventInfo eventInfo =
                    SampleTunerTvInputSectionParser.parseEitSection(data);
            if (eventInfo == null) {
                Log.e(TAG, "Did not receive event info from parser");
                return;
            }

            // We assume that our program starts at the current time
            long startTimeMs = Clock.SYSTEM.currentTimeMillis();
            long endTimeMs = startTimeMs + ((long)eventInfo.getLengthSeconds() * 1000);

            // Remove any other programs which conflict with our start and end time
            Uri conflictsUri =
                    TvContract.buildProgramsUriForChannel(mChannelUri, startTimeMs, endTimeMs);
            int programsDeleted = mContext.getContentResolver().delete(conflictsUri, null, null);
            if (DEBUG) {
                Log.d(TAG, "Deleted " + programsDeleted + " conflicting program(s)");
            }

            // Insert our new program into the newly opened time slot
            ContentValues values = new ContentValues();
            values.put(TvContract.Programs.COLUMN_CHANNEL_ID, ContentUris.parseId(mChannelUri));
            values.put(TvContract.Programs.COLUMN_START_TIME_UTC_MILLIS, startTimeMs);
            values.put(TvContract.Programs.COLUMN_END_TIME_UTC_MILLIS, endTimeMs);
            values.put(TvContract.Programs.COLUMN_TITLE, eventInfo.getEventTitle());
            values.put(TvContract.Programs.COLUMN_SHORT_DESCRIPTION, "");
            if (DEBUG) {
                Log.d(TAG, "Inserting program with values: " + values);
            }
            mContext.getContentResolver().insert(TvContract.Programs.CONTENT_URI, values);
        }

        private boolean handleDataBuffer(MediaEventData mediaEventData) {
            boolean success = false;
            if (queueCodecInputBuffer(mediaEventData.getData(), mediaEventData.getDataSize(),
                    mediaEventData.getPts())) {
                releaseCodecOutputBuffer();
                success = true;
            }
            return success;
        }

        private boolean queueCodecInputBuffer(byte[] data, int size, long pts) {
            int res = mMediaCodec.dequeueInputBuffer(TIMEOUT_US);
            if (res >= 0) {
                ByteBuffer buffer = mMediaCodec.getInputBuffer(res);
                if (buffer == null) {
                    throw new RuntimeException("Null decoder input buffer");
                }

                if (DEBUG) {
                    Log.d(
                        TAG,
                        "Decoder: Send data to decoder."
                            + " pts="
                            + pts
                            + " size="
                            + size);
                }
                // fill codec input buffer
                buffer.put(data, 0, size);

                mMediaCodec.queueInputBuffer(res, 0, size, pts, 0);
            } else {
                if (DEBUG) Log.d(TAG, "queueCodecInputBuffer res=" + res);
                return false;
            }
            return true;
        }

        private void releaseCodecOutputBuffer() {
            // play frames
            BufferInfo bufferInfo = new BufferInfo();
            int res = mMediaCodec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US);
            if (res >= 0) {
                long currentFramePtsUs = bufferInfo.presentationTimeUs;

                // We know we are starting a new loop if the loop time is not set or if
                // the current frame is before the last frame
                if (mCurrentLoopStartTimeUs == 0 || currentFramePtsUs < mLastFramePtsUs) {
                    mCurrentLoopStartTimeUs = System.nanoTime() / 1000;
                }
                mLastFramePtsUs = currentFramePtsUs;

                long desiredUs = mCurrentLoopStartTimeUs + currentFramePtsUs;
                long nowUs = System.nanoTime() / 1000;
                long sleepTimeUs = desiredUs - nowUs;

                if (DEBUG) {
                    Log.d(TAG, "currentFramePts: " + currentFramePtsUs
                            + " sleeping for: " + sleepTimeUs);
                }
                if (sleepTimeUs > 0) {
                    try {
                        Thread.sleep(
                                /* millis */ sleepTimeUs / 1000,
                                /* nanos */ (int) (sleepTimeUs % 1000) * 1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        if (DEBUG) {
                            Log.d(TAG, "InterruptedException:\n" + Log.getStackTraceString(e));
                        }
                        return;
                    }
                }
                mMediaCodec.releaseOutputBuffer(res, true);
                if (!mVideoAvailable) {
                    mVideoAvailable = true;
                    notifyVideoAvailable();
                    if (DEBUG) {
                        Log.d(TAG, "notifyVideoAvailable");
                    }
                }
            } else if (res == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat format = mMediaCodec.getOutputFormat();
                if (DEBUG) {
                    Log.d(TAG, "releaseCodecOutputBuffer: Output format changed:" + format);
                }
            } else if (res == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (DEBUG) {
                    Log.d(TAG, "releaseCodecOutputBuffer: timeout");
                }
            } else {
                if (DEBUG) {
                    Log.d(TAG, "Return value of releaseCodecOutputBuffer:" + res);
                }
            }
        }

    }

    /**
     * MediaEventData is a helper class which is used to hold the data within MediaEvents
     * locally in our Java code, instead of in the position allocated by our native code
     */
    public static class MediaEventData {
        private final long mPts;
        private final int mDataSize;
        private final byte[] mData;

        public MediaEventData(long pts, int dataSize, byte[] data) {
            mPts = pts;
            mDataSize = dataSize;
            mData = data;
        }

        /**
         * Parses a MediaEvent, including copying its data and freeing the underlying LinearBlock
         * @return {@code null} if the event has no LinearBlock
         */
        public static MediaEventData generateEventData(MediaEvent event) {
            if(event.getLinearBlock() == null) {
                if (DEBUG) {
                    Log.d(TAG, "MediaEvent had null LinearBlock");
                }
                return null;
            }

            ByteBuffer memoryBlock = event.getLinearBlock().map();
            int eventOffset = (int)event.getOffset();
            int eventDataLength = (int)event.getDataLength();
            if (DEBUG) {
                Log.d(TAG, "MediaEvent has length=" + eventDataLength
                        + " offset=" + eventOffset
                        + " capacity=" + memoryBlock.capacity()
                        + " limit=" + memoryBlock.limit());
            }
            if (eventOffset < 0 || eventDataLength < 0 || eventOffset >= memoryBlock.limit()) {
                if (DEBUG) {
                    Log.e(TAG, "MediaEvent length or offset was invalid");
                }
                event.getLinearBlock().recycle();
                event.release();
                return null;
            }
            // We allow the case of eventOffset + eventDataLength > memoryBlock.limit()
            // When it occurs, we read until memoryBlock.limit
            int dataSize = Math.min(eventDataLength, memoryBlock.limit() - eventOffset);
            memoryBlock.position(eventOffset);

            byte[] memoryData = new byte[dataSize];
            memoryBlock.get(memoryData, 0, dataSize);
            MediaEventData eventData = new MediaEventData(event.getPts(), dataSize, memoryData);

            event.getLinearBlock().recycle();
            event.release();
            return eventData;
        }

        public long getPts() {
            return mPts;
        }

        public int getDataSize() {
            return mDataSize;
        }

        public byte[] getData() {
            return mData;
        }
    }
}
