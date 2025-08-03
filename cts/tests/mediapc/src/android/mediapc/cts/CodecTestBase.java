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

package android.mediapc.cts;

import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface;
import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.graphics.ImageFormat;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaCrypto;
import android.media.MediaDrm;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.NotProvisionedException;
import android.media.ResourceBusyException;
import android.os.Build;
import android.util.Log;
import android.util.Pair;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Assert;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.regex.Pattern;

class CodecAsyncHandler extends MediaCodec.Callback {
    private static final String LOG_TAG = CodecAsyncHandler.class.getSimpleName();
    private final Lock mLock = new ReentrantLock();
    private final Condition mCondition = mLock.newCondition();
    private final LinkedList<Pair<Integer, MediaCodec.BufferInfo>> mCbInputQueue;
    private final LinkedList<Pair<Integer, MediaCodec.BufferInfo>> mCbOutputQueue;
    private MediaFormat mOutFormat;
    private boolean mSignalledOutFormatChanged;
    private volatile boolean mSignalledError;

    CodecAsyncHandler() {
        mCbInputQueue = new LinkedList<>();
        mCbOutputQueue = new LinkedList<>();
        mSignalledError = false;
        mSignalledOutFormatChanged = false;
    }

    void clearQueues() {
        mLock.lock();
        mCbInputQueue.clear();
        mCbOutputQueue.clear();
        mLock.unlock();
    }

    void resetContext() {
        clearQueues();
        mOutFormat = null;
        mSignalledOutFormatChanged = false;
        mSignalledError = false;
    }

    @Override
    public void onInputBufferAvailable(@NonNull MediaCodec codec, int bufferIndex) {
        assertTrue(bufferIndex >= 0);
        mLock.lock();
        mCbInputQueue.add(new Pair<>(bufferIndex, (MediaCodec.BufferInfo) null));
        mCondition.signalAll();
        mLock.unlock();
    }

    @Override
    public void onOutputBufferAvailable(@NonNull MediaCodec codec, int bufferIndex,
            @NonNull MediaCodec.BufferInfo info) {
        assertTrue(bufferIndex >= 0);
        mLock.lock();
        mCbOutputQueue.add(new Pair<>(bufferIndex, info));
        mCondition.signalAll();
        mLock.unlock();
    }

    @Override
    public void onError(@NonNull MediaCodec codec, MediaCodec.CodecException e) {
        mLock.lock();
        mSignalledError = true;
        mCondition.signalAll();
        mLock.unlock();
        Log.e(LOG_TAG, "received media codec error : " + e.getMessage());
    }

    @Override
    public void onOutputFormatChanged(@NonNull MediaCodec codec, @NonNull MediaFormat format) {
        mOutFormat = format;
        mSignalledOutFormatChanged = true;
        Log.i(LOG_TAG, "Output format changed: " + format.toString());
    }

    void setCallBack(MediaCodec codec, boolean isCodecInAsyncMode) {
        if (isCodecInAsyncMode) {
            codec.setCallback(this);
        } else {
            codec.setCallback(null);
        }
    }

    Pair<Integer, MediaCodec.BufferInfo> getOutput() throws InterruptedException {
        Pair<Integer, MediaCodec.BufferInfo> element = null;
        mLock.lock();
        while (!mSignalledError) {
            if (mCbOutputQueue.isEmpty()) {
                mCondition.await();
            } else {
                element = mCbOutputQueue.remove(0);
                break;
            }
        }
        mLock.unlock();
        return element;
    }

    Pair<Integer, MediaCodec.BufferInfo> getWork() throws InterruptedException {
        Pair<Integer, MediaCodec.BufferInfo> element = null;
        mLock.lock();
        while (!mSignalledError) {
            if (mCbInputQueue.isEmpty() && mCbOutputQueue.isEmpty()) {
                mCondition.await();
            } else {
                if (!mCbOutputQueue.isEmpty()) {
                    element = mCbOutputQueue.remove(0);
                    break;
                }
                if (!mCbInputQueue.isEmpty()) {
                    element = mCbInputQueue.remove(0);
                    break;
                }
            }
        }
        mLock.unlock();
        return element;
    }

    boolean hasSeenError() {
        return mSignalledError;
    }

    boolean hasOutputFormatChanged() {
        return mSignalledOutFormatChanged;
    }

    MediaFormat getOutputFormat() {
        return mOutFormat;
    }
}

abstract class CodecTestBase {
    private static final String LOG_TAG = CodecTestBase.class.getSimpleName();
    static final boolean ENABLE_LOGS = false;
    static final int PER_TEST_TIMEOUT_LARGE_TEST_MS = 300000;
    static final int PER_TEST_TIMEOUT_SMALL_TEST_MS = 60000;
    static final int SELECT_ALL = 0; // Select all codecs
    static final int SELECT_HARDWARE = 1; // Select Hardware codecs only
    static final int SELECT_SOFTWARE = 2; // Select Software codecs only
    static final int SELECT_AUDIO = 3; // Select Audio codecs only
    static final int SELECT_VIDEO = 4; // Select Video codecs only
    // Maintain Timeouts in sync with their counterpart in NativeMediaCommon.h
    static final long Q_DEQ_TIMEOUT_US = 5000; // block at most 5ms while looking for io buffers
    static final int RETRY_LIMIT = 100; // max poll counter before test aborts and returns error
    static final String mInpPrefix = WorkDir.getMediaDirString();
    public static final MediaCodecList MCL_ALL = new MediaCodecList(MediaCodecList.ALL_CODECS);
    public static final String CODEC_FILTER_KEY = "codec-filter";
    public static final String CODEC_PREFIX_KEY = "codec-prefix";
    public static final String MEDIA_TYPE_PREFIX_KEY = "media-type-prefix";
    public static Pattern codecFilter;
    public static String codecPrefix;
    public static String mediaTypePrefix;

    CodecAsyncHandler mAsyncHandle;
    boolean mIsCodecInAsyncMode;
    boolean mSawInputEOS;
    boolean mSawOutputEOS;
    boolean mSignalEOSWithLastFrame;
    int mInputCount;
    int mOutputCount;
    long mPrevOutputPts;
    boolean mSignalledOutFormatChanged;
    MediaFormat mOutFormat;
    boolean mIsAudio;

    MediaCodec mCodec;
    Surface mSurface;

    static {
        android.os.Bundle args = InstrumentationRegistry.getArguments();
        codecPrefix = args.getString(CODEC_PREFIX_KEY);
        mediaTypePrefix = args.getString(MEDIA_TYPE_PREFIX_KEY);
        String codecFilterStr = args.getString(CODEC_FILTER_KEY);
        if (codecFilterStr != null) {
            codecFilter = Pattern.compile(codecFilterStr);
        }
    }

    abstract void enqueueInput(int bufferIndex) throws IOException;

    // Callback for each time the output count changes.
    // This can be used to measure codec performance.
    Consumer<Integer> mOutputCountListener;

    // must not be called during doWork
    void setOutputCountListener(Consumer<Integer> listener) {
        mOutputCountListener = listener;
    }

    /**
     * Called to handle a dequeued output buffer.
     *
     * We account for EOS and the number of full output frames.
     */
    protected void dequeueOutput(int bufferIndex, MediaCodec.BufferInfo info) {
        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            mSawOutputEOS = true;
        }
        int outputCount = mOutputCount;
        // handle output count prior to releasing the buffer as that can take time
        if (info.size > 0 && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
            mOutputCount++;
            if (mOutputCountListener != null) {
                mOutputCountListener.accept(mOutputCount);
            }
        }
        releaseOutput(outputCount, bufferIndex, info);
    }

    /**
     * Called to handle releasing an output buffer.
     */
    abstract void releaseOutput(int bufferIndex, MediaCodec.BufferInfo info);

    /**
     * Called to handle releasing an output buffer.
     *
     * @param outputCount total count of full output frames prior to
     *                    this point (not including this buffer).
     */
    protected void releaseOutput(int outputCount, int bufferIndex, MediaCodec.BufferInfo info) {
        releaseOutput(bufferIndex, info);
    }

    void configureCodec(MediaFormat format, boolean isAsync, boolean signalEOSWithLastFrame,
            boolean isEncoder) throws Exception {
        resetContext(isAsync, signalEOSWithLastFrame);
        mAsyncHandle.setCallBack(mCodec, isAsync);
        // signalEOS flag has nothing to do with configure. We are using this flag to try all
        // available configure apis
        if (signalEOSWithLastFrame) {
            mCodec.configure(format, mSurface, null,
                    isEncoder ? MediaCodec.CONFIGURE_FLAG_ENCODE : 0);
        } else {
            mCodec.configure(format, mSurface, isEncoder ? MediaCodec.CONFIGURE_FLAG_ENCODE : 0,
                    null);
        }
    }

    void resetContext(boolean isAsync, boolean signalEOSWithLastFrame) {
        mAsyncHandle.resetContext();
        mIsCodecInAsyncMode = isAsync;
        mSawInputEOS = false;
        mSawOutputEOS = false;
        mSignalEOSWithLastFrame = signalEOSWithLastFrame;
        mInputCount = 0;
        mOutputCount = 0;
        mPrevOutputPts = Long.MIN_VALUE;
        mSignalledOutFormatChanged = false;
    }

    void enqueueEOS(int bufferIndex) {
        if (!mSawInputEOS) {
            mCodec.queueInputBuffer(bufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            mSawInputEOS = true;
            if (ENABLE_LOGS) {
                Log.v(LOG_TAG, "Queued End of Stream");
            }
        }
    }

    void doWork(int frameLimit) throws InterruptedException, IOException {
        int frameCount = 0;
        if (mIsCodecInAsyncMode) {
            // dequeue output after inputEOS is expected to be done in waitForAllOutputs()
            while (!mAsyncHandle.hasSeenError() && !mSawInputEOS && frameCount < frameLimit) {
                Pair<Integer, MediaCodec.BufferInfo> element = mAsyncHandle.getWork();
                if (element != null) {
                    int bufferID = element.first;
                    MediaCodec.BufferInfo info = element.second;
                    if (info != null) {
                        // <id, info> corresponds to output callback. Handle it accordingly
                        dequeueOutput(bufferID, info);
                    } else {
                        // <id, null> corresponds to input callback. Handle it accordingly
                        enqueueInput(bufferID);
                        frameCount++;
                    }
                }
            }
        } else {
            MediaCodec.BufferInfo outInfo = new MediaCodec.BufferInfo();
            // dequeue output after inputEOS is expected to be done in waitForAllOutputs()
            while (!mSawInputEOS && frameCount < frameLimit) {
                int outputBufferId = mCodec.dequeueOutputBuffer(outInfo, Q_DEQ_TIMEOUT_US);
                if (outputBufferId >= 0) {
                    dequeueOutput(outputBufferId, outInfo);
                } else if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    mOutFormat = mCodec.getOutputFormat();
                    mSignalledOutFormatChanged = true;
                }
                int inputBufferId = mCodec.dequeueInputBuffer(Q_DEQ_TIMEOUT_US);
                if (inputBufferId != -1) {
                    enqueueInput(inputBufferId);
                    frameCount++;
                }
            }
        }
    }

    void queueEOS() throws InterruptedException {
        if (mIsCodecInAsyncMode) {
            while (!mAsyncHandle.hasSeenError() && !mSawInputEOS) {
                Pair<Integer, MediaCodec.BufferInfo> element = mAsyncHandle.getWork();
                if (element != null) {
                    int bufferID = element.first;
                    MediaCodec.BufferInfo info = element.second;
                    if (info != null) {
                        dequeueOutput(bufferID, info);
                    } else {
                        enqueueEOS(element.first);
                    }
                }
            }
        } else {
            MediaCodec.BufferInfo outInfo = new MediaCodec.BufferInfo();
            while (!mSawInputEOS) {
                int outputBufferId = mCodec.dequeueOutputBuffer(outInfo, Q_DEQ_TIMEOUT_US);
                if (outputBufferId >= 0) {
                    dequeueOutput(outputBufferId, outInfo);
                } else if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    mOutFormat = mCodec.getOutputFormat();
                    mSignalledOutFormatChanged = true;
                }
                int inputBufferId = mCodec.dequeueInputBuffer(Q_DEQ_TIMEOUT_US);
                if (inputBufferId != -1) {
                    enqueueEOS(inputBufferId);
                }
            }
        }
    }

    void waitForAllOutputs() throws InterruptedException {
        if (mIsCodecInAsyncMode) {
            while (!mAsyncHandle.hasSeenError() && !mSawOutputEOS) {
                Pair<Integer, MediaCodec.BufferInfo> element = mAsyncHandle.getOutput();
                if (element != null) {
                    dequeueOutput(element.first, element.second);
                }
            }
        } else {
            MediaCodec.BufferInfo outInfo = new MediaCodec.BufferInfo();
            while (!mSawOutputEOS) {
                int outputBufferId = mCodec.dequeueOutputBuffer(outInfo, Q_DEQ_TIMEOUT_US);
                if (outputBufferId >= 0) {
                    dequeueOutput(outputBufferId, outInfo);
                } else if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    mOutFormat = mCodec.getOutputFormat();
                    mSignalledOutFormatChanged = true;
                }
            }
        }
    }

    static ArrayList<String> selectCodecs(String mime, ArrayList<MediaFormat> formats,
            String[] features, boolean isEncoder) {
        return selectCodecs(mime, formats, features, isEncoder, SELECT_ALL);
    }

    static ArrayList<String> selectHardwareCodecs(String mime, ArrayList<MediaFormat> formats,
            String[] features, boolean isEncoder) {
        return selectHardwareCodecs(mime, formats, features, isEncoder, false);
    }

    static ArrayList<String> selectHardwareCodecs(String mime, ArrayList<MediaFormat> formats,
            String[] features, boolean isEncoder, boolean allCodecs) {
        return selectCodecs(mime, formats, features, isEncoder, SELECT_HARDWARE, allCodecs);
    }

    static ArrayList<String> selectCodecs(String mime, ArrayList<MediaFormat> formats,
            String[] features, boolean isEncoder, int selectCodecOption) {
        return selectCodecs(mime, formats, features, isEncoder, selectCodecOption, false);
    }

    static ArrayList<String> selectCodecs(String mime, ArrayList<MediaFormat> formats,
            String[] features, boolean isEncoder, int selectCodecOption, boolean allCodecs) {
        int kind = allCodecs ? MediaCodecList.ALL_CODECS : MediaCodecList.REGULAR_CODECS;
        MediaCodecList codecList = new MediaCodecList(kind);
        MediaCodecInfo[] codecInfos = codecList.getCodecInfos();
        ArrayList<String> listOfCodecs = new ArrayList<>();
        for (MediaCodecInfo codecInfo : codecInfos) {
            if (codecInfo.isEncoder() != isEncoder) continue;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && codecInfo.isAlias()) continue;
            if (selectCodecOption == SELECT_HARDWARE && !codecInfo.isHardwareAccelerated())
                continue;
            else if (selectCodecOption == SELECT_SOFTWARE && !codecInfo.isSoftwareOnly())
                continue;
            String[] types = codecInfo.getSupportedTypes();
            for (String type : types) {
                if (type.equalsIgnoreCase(mime)) {
                    boolean isOk = true;
                    MediaCodecInfo.CodecCapabilities codecCapabilities =
                            codecInfo.getCapabilitiesForType(type);
                    if (formats != null) {
                        for (MediaFormat format : formats) {
                            if (!codecCapabilities.isFormatSupported(format)) {
                                isOk = false;
                                break;
                            }
                        }
                    }
                    if (features != null) {
                        for (String feature : features) {
                            if (!codecCapabilities.isFeatureSupported(feature)) {
                                isOk = false;
                                break;
                            }
                        }
                    }
                    if (isOk) listOfCodecs.add(codecInfo.getName());
                }
            }
        }
        return listOfCodecs;
    }

    static Set<String> getMimesOfAvailableCodecs(int codecAV, int codecType) {
        MediaCodecList codecList = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
        MediaCodecInfo[] codecInfos = codecList.getCodecInfos();
        Set<String> listOfMimes = new HashSet<>();
        for (MediaCodecInfo codecInfo : codecInfos) {
            if (codecType == SELECT_HARDWARE && !codecInfo.isHardwareAccelerated()) {
                continue;
            }
            if (codecType == SELECT_SOFTWARE && !codecInfo.isSoftwareOnly()) {
                continue;
            }
            String[] types = codecInfo.getSupportedTypes();
            for (String type : types) {
                if (codecAV == SELECT_AUDIO && !type.startsWith("audio/")) {
                    continue;
                }
                if (codecAV == SELECT_VIDEO && !type.startsWith("video/")) {
                    continue;
                }
                listOfMimes.add(type);
            }
        }
        return listOfMimes;
    }

    /**
     * Returns MediaCodecInfo for the given codec name
     */
    public static MediaCodecInfo getCodecInfo(String codecName) {
        for (MediaCodecInfo info : MCL_ALL.getCodecInfos()) {
            if (info.getName().equals(codecName)) {
                return info;
            }
        }
        return null;
    }

    /**
     * Checks if the codec supports all the given formats
     */
    public static boolean areFormatsSupported(String codecName, ArrayList<MediaFormat> formats) {
        boolean isSupported = true;
        MediaCodecInfo info = getCodecInfo(codecName);
        if (info == null) {
            return false;
        }
        for (MediaFormat format : formats) {
            String mediaType = format.getString(MediaFormat.KEY_MIME);
            MediaCodecInfo.CodecCapabilities codecCapabilities =
                    info.getCapabilitiesForType(mediaType);
            if (!codecCapabilities.isFormatSupported(format)) {
                Log.d(LOG_TAG, "Codec: " + codecName + " doesn't support format: " + format);
                return false;
            }
        }
        return true;
    }
}

class CodecDecoderTestBase extends CodecTestBase {
    private static final String LOG_TAG = CodecDecoderTestBase.class.getSimpleName();
    // Widevine Content Protection Identifier https://dashif.org/identifiers/content_protection/
    public static final UUID WIDEVINE_UUID = new UUID(0xEDEF8BA979D64ACEL, 0xA3C827DCD51D21EDL);

    String mMime;
    String mTestFile;
    boolean mIsInterlaced;
    boolean mSecureMode;
    byte[] mSessionID;

    ArrayList<ByteBuffer> mCsdBuffers;

    MediaExtractor mExtractor;
    MediaDrm mDrm = null;
    MediaCrypto mCrypto = null;

    CodecDecoderTestBase(String mime, String testFile, boolean secureMode) {
        mMime = mime;
        mTestFile = testFile;
        mAsyncHandle = new CodecAsyncHandler();
        mCsdBuffers = new ArrayList<>();
        mIsAudio = mMime.startsWith("audio/");
        mSecureMode = secureMode;
    }

    CodecDecoderTestBase(String mime, String testFile) {
        this(mime, testFile, false);
    }

    MediaFormat setUpSource(String srcFile) throws IOException {
        return setUpSource(mInpPrefix, srcFile);
    }

    boolean hasCSD(MediaFormat format) {
        return format.containsKey("csd-0");
    }

    private byte[] openSession(MediaDrm drm) {
        byte[] sessionId = null;
        int retryCount = 3;
        while (retryCount-- > 0) {
            try {
                sessionId = drm.openSession();
                break;
            } catch (NotProvisionedException eNotProvisioned) {
                Log.i(LOG_TAG, "Missing certificate, provisioning");
                try {
                    final ProvisionRequester provisionRequester = new ProvisionRequester(drm);
                    provisionRequester.send();
                } catch (Exception e) {
                    Log.e(LOG_TAG, "Provisioning fails because " + e.toString());
                }
            } catch (ResourceBusyException eResourceBusy) {
                Log.w(LOG_TAG, "Resource busy in openSession, retrying...");
                try {
                    Thread.sleep(1000);
                } catch (Exception ignored) {
                }
            }
        }
        return sessionId;
    }

    void configureCodec(MediaFormat format, boolean isAsync, boolean signalEOSWithLastFrame,
            boolean isEncoder, String serverURL) throws Exception {
        resetContext(isAsync, signalEOSWithLastFrame);
        mAsyncHandle.setCallBack(mCodec, isAsync);
        if (mSecureMode && serverURL != null) {
            if (mDrm == null) {
                mDrm = new MediaDrm(WIDEVINE_UUID);
            }
            if (mCrypto == null) {
                mSessionID = openSession(mDrm);
                assertNotNull("Failed to provision device.", mSessionID);
                mCrypto = new MediaCrypto(WIDEVINE_UUID, mSessionID);
            }
            mCodec.configure(format, mSurface, mCrypto,
                    isEncoder ? MediaCodec.CONFIGURE_FLAG_ENCODE : 0);

            Map<UUID, byte[]> psshInfo = mExtractor.getPsshInfo();
            byte[] emeInitData = null;

            // TODO(b/230682028) Remove the following once webm extractor returns PSSH info for VP9
            if (psshInfo == null && mMime.equals(MediaFormat.MIMETYPE_VIDEO_VP9)) {
                if (format.getInteger(MediaFormat.KEY_HEIGHT) == 1080) {
                    emeInitData = new byte[]{8, 1, 18, 1, 51, 26, 13, 119, 105, 100, 101, 118,
                            105, 110, 101, 95, 116, 101, 115, 116, 34, 10, 50, 48, 49, 53,
                            95, 116, 101, 97, 114, 115, 42, 2, 72, 68};
                } else if (format.getInteger(MediaFormat.KEY_HEIGHT) == 2160) {
                    emeInitData = new byte[]{8, 1, 18, 1, 56, 26, 13, 119, 105, 100, 101, 118,
                            105, 110, 101, 95, 116, 101, 115, 116, 34, 10, 50, 48, 49, 53,
                            95, 116, 101, 97, 114, 115, 42, 4, 85, 72, 68, 49};
                } else {
                    fail("unable to get pssh info for the given resolution in vp9");
                }
            } else {
                assertNotNull("Extractor is missing pssh info", psshInfo);
                emeInitData = psshInfo.get(WIDEVINE_UUID);
            }
            assertNotNull("Extractor pssh info is missing data for scheme: " + WIDEVINE_UUID,
                    emeInitData);
            KeyRequester requester =
                    new KeyRequester(mDrm, mSessionID, MediaDrm.KEY_TYPE_STREAMING, mMime,
                            emeInitData, serverURL, WIDEVINE_UUID);
            requester.send();
            return;
        }
        // signalEOS flag has nothing to do with configure. We are using this flag to try all
        // available configure apis
        if (signalEOSWithLastFrame) {
            mCodec.configure(format, mSurface, null,
                    isEncoder ? MediaCodec.CONFIGURE_FLAG_ENCODE : 0);
        } else {
            mCodec.configure(format, mSurface, isEncoder ? MediaCodec.CONFIGURE_FLAG_ENCODE : 0,
                    null);
        }
    }

    void configureCodec(MediaFormat format, boolean isAsync, boolean signalEOSWithLastFrame,
            boolean isEncoder) throws Exception {
        configureCodec(format, isAsync, signalEOSWithLastFrame, isEncoder, null);
    }

    MediaFormat setUpSource(String prefix, String srcFile) throws IOException {
        mExtractor = new MediaExtractor();
        mExtractor.setDataSource(prefix + srcFile);
        for (int trackID = 0; trackID < mExtractor.getTrackCount(); trackID++) {
            MediaFormat format = mExtractor.getTrackFormat(trackID);
            if (mMime.equalsIgnoreCase(format.getString(MediaFormat.KEY_MIME))) {
                mExtractor.selectTrack(trackID);
                if (!mIsAudio) {
                    if (mSurface == null) {
                        // COLOR_FormatYUV420Flexible must be supported by all components
                        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, COLOR_FormatYUV420Flexible);
                    } else {
                        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, COLOR_FormatSurface);
                    }
                }
                // TODO: determine this from the extractor format when it becomes exposed.
                mIsInterlaced = srcFile.contains("_interlaced_");
                return format;
            }
        }
        fail("No track with mime: " + mMime + " found in file: " + srcFile);
        return null;
    }

    void enqueueInput(int bufferIndex) {
        if (mExtractor.getSampleSize() < 0) {
            enqueueEOS(bufferIndex);
        } else {
            ByteBuffer inputBuffer = mCodec.getInputBuffer(bufferIndex);
            int size = mExtractor.readSampleData(inputBuffer, 0);
            long pts = mExtractor.getSampleTime();
            int extractorFlags = mExtractor.getSampleFlags();
            int codecFlags = 0;
            if ((extractorFlags & MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                codecFlags |= MediaCodec.BUFFER_FLAG_KEY_FRAME;
            }
            MediaCodec.CryptoInfo info = new MediaCodec.CryptoInfo();
            boolean isEncrypted = mExtractor.getSampleCryptoInfo(info);
            if (!mExtractor.advance() && mSignalEOSWithLastFrame) {
                codecFlags |= MediaCodec.BUFFER_FLAG_END_OF_STREAM;
                mSawInputEOS = true;
            }
            if (mSecureMode && isEncrypted) {
                mCodec.queueSecureInputBuffer(bufferIndex, 0, info, pts, codecFlags);
            } else {
                mCodec.queueInputBuffer(bufferIndex, 0, size, pts, codecFlags);
            }
            if (size > 0 && (codecFlags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                mInputCount++;
            }
        }
    }

    void releaseOutput(int bufferIndex, MediaCodec.BufferInfo info) {
        mCodec.releaseOutputBuffer(bufferIndex, false);
    }
}

class CodecEncoderTestBase extends CodecTestBase {
    private static final String LOG_TAG = CodecEncoderTestBase.class.getSimpleName();

    // files are in WorkDir.getMediaDirString();
    private static final String INPUT_AUDIO_FILE = "bbb_2ch_44kHz_s16le.raw";
    private static final String INPUT_VIDEO_FILE = "bbb_cif_yuv420p_30fps.yuv";
    private final int INP_FRM_WIDTH = 352;
    private final int INP_FRM_HEIGHT = 288;

    final String mMime;
    final String mInputFile;
    byte[] mInputData;
    int mNumBytesSubmitted;
    long mInputOffsetPts;

    int mWidth, mHeight;
    int mFrameRate;
    int mMaxBFrames;
    int mChannels;
    int mSampleRate;

    CodecEncoderTestBase(String mime) {
        mMime = mime;
        mWidth = INP_FRM_WIDTH;
        mHeight = INP_FRM_HEIGHT;
        mChannels = 1;
        mSampleRate = 8000;
        mFrameRate = 30;
        mMaxBFrames = 0;
        if (mime.equals(MediaFormat.MIMETYPE_VIDEO_MPEG4)) mFrameRate = 12;
        else if (mime.equals(MediaFormat.MIMETYPE_VIDEO_H263)) mFrameRate = 12;
        mAsyncHandle = new CodecAsyncHandler();
        mIsAudio = mMime.startsWith("audio/");
        mInputFile = mIsAudio ? INPUT_AUDIO_FILE : INPUT_VIDEO_FILE;
    }

    @Override
    void resetContext(boolean isAsync, boolean signalEOSWithLastFrame) {
        super.resetContext(isAsync, signalEOSWithLastFrame);
        mNumBytesSubmitted = 0;
        mInputOffsetPts = 0;
    }

    void setUpSource(String srcFile) throws IOException {
        String inpPath = mInpPrefix + srcFile;
        try (FileInputStream fInp = new FileInputStream(inpPath)) {
            int size = (int) new File(inpPath).length();
            mInputData = new byte[size];
            fInp.read(mInputData, 0, size);
        }
    }

    void fillImage(Image image) {
        Assert.assertTrue(image.getFormat() == ImageFormat.YUV_420_888);
        int imageWidth = image.getWidth();
        int imageHeight = image.getHeight();
        Image.Plane[] planes = image.getPlanes();
        int offset = mNumBytesSubmitted;
        for (int i = 0; i < planes.length; ++i) {
            ByteBuffer buf = planes[i].getBuffer();
            int width = imageWidth;
            int height = imageHeight;
            int tileWidth = INP_FRM_WIDTH;
            int tileHeight = INP_FRM_HEIGHT;
            int rowStride = planes[i].getRowStride();
            int pixelStride = planes[i].getPixelStride();
            if (i != 0) {
                width = imageWidth / 2;
                height = imageHeight / 2;
                tileWidth = INP_FRM_WIDTH / 2;
                tileHeight = INP_FRM_HEIGHT / 2;
            }
            if (pixelStride == 1) {
                if (width == rowStride && width == tileWidth && height == tileHeight) {
                    buf.put(mInputData, offset, width * height);
                } else {
                    for (int z = 0; z < height; z += tileHeight) {
                        int rowsToCopy = Math.min(height - z, tileHeight);
                        for (int y = 0; y < rowsToCopy; y++) {
                            for (int x = 0; x < width; x += tileWidth) {
                                int colsToCopy = Math.min(width - x, tileWidth);
                                buf.position((z + y) * rowStride + x);
                                buf.put(mInputData, offset + y * tileWidth, colsToCopy);
                            }
                        }
                    }
                }
            } else {
                // do it pixel-by-pixel
                for (int z = 0; z < height; z += tileHeight) {
                    int rowsToCopy = Math.min(height - z, tileHeight);
                    for (int y = 0; y < rowsToCopy; y++) {
                        int lineOffset = (z + y) * rowStride;
                        for (int x = 0; x < width; x += tileWidth) {
                            int colsToCopy = Math.min(width - x, tileWidth);
                            for (int w = 0; w < colsToCopy; w++) {
                                buf.position(lineOffset + (x + w) * pixelStride);
                                buf.put(mInputData[offset + y * tileWidth + w]);
                            }
                        }
                    }
                }
            }
            offset += tileWidth * tileHeight;
        }
    }

    void fillByteBuffer(ByteBuffer inputBuffer) {
        int offset = 0, frmOffset = mNumBytesSubmitted;
        for (int plane = 0; plane < 3; plane++) {
            int width = mWidth;
            int height = mHeight;
            int tileWidth = INP_FRM_WIDTH;
            int tileHeight = INP_FRM_HEIGHT;
            if (plane != 0) {
                width = mWidth / 2;
                height = mHeight / 2;
                tileWidth = INP_FRM_WIDTH / 2;
                tileHeight = INP_FRM_HEIGHT / 2;
            }
            for (int k = 0; k < height; k += tileHeight) {
                int rowsToCopy = Math.min(height - k, tileHeight);
                for (int j = 0; j < rowsToCopy; j++) {
                    for (int i = 0; i < width; i += tileWidth) {
                        int colsToCopy = Math.min(width - i, tileWidth);
                        inputBuffer.position(offset + (k + j) * width + i);
                        inputBuffer.put(mInputData, frmOffset + j * tileWidth, colsToCopy);
                    }
                }
            }
            offset += width * height;
            frmOffset += tileWidth * tileHeight;
        }
    }

    void enqueueInput(int bufferIndex) {
        ByteBuffer inputBuffer = mCodec.getInputBuffer(bufferIndex);
        if (mNumBytesSubmitted >= mInputData.length) {
            enqueueEOS(bufferIndex);
        } else {
            int size;
            int flags = 0;
            long pts = mInputOffsetPts;
            if (mIsAudio) {
                pts += mNumBytesSubmitted * 1000000L / (2 * mChannels * mSampleRate);
                size = Math.min(inputBuffer.capacity(), mInputData.length - mNumBytesSubmitted);
                inputBuffer.put(mInputData, mNumBytesSubmitted, size);
                if (mNumBytesSubmitted + size >= mInputData.length && mSignalEOSWithLastFrame) {
                    flags |= MediaCodec.BUFFER_FLAG_END_OF_STREAM;
                    mSawInputEOS = true;
                }
                mNumBytesSubmitted += size;
            } else {
                pts += mInputCount * 1000000L / mFrameRate;
                size = mWidth * mHeight * 3 / 2;
                int frmSize = INP_FRM_WIDTH * INP_FRM_HEIGHT * 3 / 2;
                if (mNumBytesSubmitted + frmSize > mInputData.length) {
                    fail("received partial frame to encode");
                } else {
                    Image img = mCodec.getInputImage(bufferIndex);
                    if (img != null) {
                        fillImage(img);
                    } else {
                        if (mWidth == INP_FRM_WIDTH && mHeight == INP_FRM_HEIGHT) {
                            inputBuffer.put(mInputData, mNumBytesSubmitted, size);
                        } else {
                            fillByteBuffer(inputBuffer);
                        }
                    }
                }
                if (mNumBytesSubmitted + frmSize >= mInputData.length && mSignalEOSWithLastFrame) {
                    flags |= MediaCodec.BUFFER_FLAG_END_OF_STREAM;
                    mSawInputEOS = true;
                }
                mNumBytesSubmitted += frmSize;
            }
            mCodec.queueInputBuffer(bufferIndex, 0, size, pts, flags);
            mInputCount++;
        }
    }

    void releaseOutput(int bufferIndex, MediaCodec.BufferInfo info) {
        mCodec.releaseOutputBuffer(bufferIndex, false);
    }
}

/**
 * The following class decodes the given testFile using decoder created by the given decoderName
 * in surface mode(uses PersistentInputSurface) and returns the achieved fps for decoding.
 */
class Decode extends CodecDecoderTestBase implements Callable<Double> {
    private static final String LOG_TAG = Decode.class.getSimpleName();

    final String mDecoderName;
    static final String WIDEVINE_LICENSE_SERVER_URL = "https://proxy.uat.widevine.com/proxy";
    static final String PROVIDER = "widevine_test";
    final String mServerURL =
            String.format("%s?video_id=%s&provider=%s", WIDEVINE_LICENSE_SERVER_URL,
                    "GTS_HW_SECURE_ALL", PROVIDER);
    final boolean mIsAsync;
    private int mInitialFramesToIgnoreCount = 1;
    private long mStartTimeMillis = 0;
    private long mEndTimeMillis = 0;

    Decode(String mime, String testFile, String decoderName, boolean isAsync) {
        this(mime, testFile,decoderName, isAsync, false);
    }

    Decode(String mime, String testFile, String decoderName, boolean isAsync, boolean secureMode) {
        super(mime, testFile);
        mDecoderName = decoderName;
        mSurface = MediaCodec.createPersistentInputSurface();
        mIsAsync = isAsync;
        mSecureMode = secureMode;
    }

    public void setInitialFramesToIgnoreCount(int count) {
        mInitialFramesToIgnoreCount = count;
    }

    // measure throughput at the output port
    private void onOutputCountListener(int count) {
        // keep the timestamp of the last output frame
        mEndTimeMillis = System.currentTimeMillis();

        // don't count the time for the initial frames that are ignored
        if (count == mInitialFramesToIgnoreCount) {
            mStartTimeMillis = mEndTimeMillis;
        }
    }

    public Double doDecode() throws Exception {
        MediaFormat format = setUpSource(mTestFile);
        ArrayList<MediaFormat> formats = new ArrayList<>();
        formats.add(format);
        // If the decoder doesn't support the formats, then return 0 to indicate that decode failed
        if (!areFormatsSupported(mDecoderName, formats)) {
            return (Double) 0.0;
        }
        mCodec = MediaCodec.createByCodecName(mDecoderName);
        mExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
        configureCodec(format, mIsAsync, false, false, mServerURL);
        // TODO(b/251003943) Remove once Surface from SurfaceView is used for secure decoders
        try {
            mCodec.start();
        } catch (Exception e) {
            Log.e(LOG_TAG, "Stopping the test because codec.start() failed.", e);
            mCodec.release();
            return (Double) 0.0;
        }

        // capture timestamps at receipt of output buffers
        setOutputCountListener(i -> onOutputCountListener(i));

        doWork(Integer.MAX_VALUE);
        queueEOS();
        waitForAllOutputs();

        mCodec.stop();
        mCodec.release();
        mExtractor.release();
        if (mCrypto != null) {
            mCrypto.release();
        }
        if (mDrm != null) {
            mDrm.close();
        }
        double fps = (mOutputCount - mInitialFramesToIgnoreCount) /
                ((mEndTimeMillis - mStartTimeMillis) / 1000.0);
        Log.d(LOG_TAG, "Decode Mime: " + mMime + " Decoder: " + mDecoderName +
                " Achieved fps: " + fps);
        return fps;
    }

    @Override
    public Double call() throws Exception {
        try {
            return doDecode();
        } catch (Exception e) {
            Log.d(LOG_TAG, "Decode Mime: " + mMime + " Decoder: " + mDecoderName
                    + " Failed due to: " + e);
            return -1.0;
        }
    }
}

/**
 * The following class decodes the given testFile using decoder created by the given decoderName
 * in surface mode(uses given valid surface) and render the output to surface.
 */
class DecodeToSurface extends Decode {

    DecodeToSurface(String mime, String testFile, String decoderName, Surface surface,
            boolean isAsync) {
        super(mime, testFile, decoderName, isAsync);
        mSurface = surface;
    }

    void releaseOutput(int bufferIndex, MediaCodec.BufferInfo info) {
        mCodec.releaseOutputBuffer(bufferIndex, true);
    }
}

/**
 * The following class encodes a YUV video file to a given mimeType using encoder created by the
 * given encoderName and configuring to 30fps format.
 */
class Encode extends CodecEncoderTestBase implements Callable<Double> {
    private static final String LOG_TAG = Encode.class.getSimpleName();

    private final String mEncoderName;
    private final boolean mIsAsync;
    private final int mBitrate;

    private int mInitialFramesToIgnoreCount = 1;
    private long mStartTimeMillis = 0;
    private long mEndTimeMillis = 0;

    Encode(String mime, String encoderName, boolean isAsync, int height, int width, int frameRate,
            int bitrate) {
        super(mime);
        mEncoderName = encoderName;
        mIsAsync = isAsync;
        mFrameRate = frameRate;
        mBitrate = bitrate;
        mHeight = height;
        mWidth = width;
    }

    public void setInitialFramesToIgnoreCount(int count) {
        mInitialFramesToIgnoreCount = count;
    }

    // measure throughput at the output port
    private void onOutputCountListener(int count) {
        // keep the timestamp of the last output frame
        mEndTimeMillis = System.currentTimeMillis();

        // don't count the time for the initial frames that are ignored
        if (count == mInitialFramesToIgnoreCount) {
            mStartTimeMillis = mEndTimeMillis;
        }
    }

    private MediaFormat setUpFormat() {
        MediaFormat format = new MediaFormat();
        format.setString(MediaFormat.KEY_MIME, mMime);
        format.setInteger(MediaFormat.KEY_BIT_RATE, mBitrate);
        format.setInteger(MediaFormat.KEY_WIDTH, mWidth);
        format.setInteger(MediaFormat.KEY_HEIGHT, mHeight);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, mFrameRate);
        format.setInteger(MediaFormat.KEY_MAX_B_FRAMES, 0);
        format.setFloat(MediaFormat.KEY_I_FRAME_INTERVAL, 1.0f);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
        return format;
    }


    public Double doEncode() throws Exception {
        MediaFormat format = setUpFormat();
        mWidth = format.getInteger(MediaFormat.KEY_WIDTH);
        mHeight = format.getInteger(MediaFormat.KEY_HEIGHT);
        setUpSource(mInputFile);
        mCodec = MediaCodec.createByCodecName(mEncoderName);
        configureCodec(format, mIsAsync, false, true);
        mCodec.start();

        // capture timestamps at receipt of output buffers
        setOutputCountListener(i -> onOutputCountListener(i));

        doWork(Integer.MAX_VALUE);
        queueEOS();
        waitForAllOutputs();

        mCodec.stop();
        mCodec.release();
        double fps = (mOutputCount - mInitialFramesToIgnoreCount) /
                ((mEndTimeMillis - mStartTimeMillis) / 1000.0);
        Log.d(LOG_TAG, "Encode Mime: " + mMime + " Encoder: " + mEncoderName +
                " Achieved fps: " + fps);
        return fps;
    }

    @Override
    public Double call() throws Exception {
        return doEncode();
    }
}
