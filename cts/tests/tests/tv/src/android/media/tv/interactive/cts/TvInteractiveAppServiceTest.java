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

package android.media.tv.interactive.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.media.PlaybackParams;
import android.media.tv.AdBuffer;
import android.media.tv.AdRequest;
import android.media.tv.AdResponse;
import android.media.tv.AitInfo;
import android.media.tv.BroadcastInfoRequest;
import android.media.tv.BroadcastInfoResponse;
import android.media.tv.CommandRequest;
import android.media.tv.CommandResponse;
import android.media.tv.DsmccRequest;
import android.media.tv.DsmccResponse;
import android.media.tv.PesRequest;
import android.media.tv.PesResponse;
import android.media.tv.SectionRequest;
import android.media.tv.SectionResponse;
import android.media.tv.SignalingDataInfo;
import android.media.tv.SignalingDataRequest;
import android.media.tv.SignalingDataResponse;
import android.media.tv.StreamEventRequest;
import android.media.tv.StreamEventResponse;
import android.media.tv.TableRequest;
import android.media.tv.TableResponse;
import android.media.tv.TimelineRequest;
import android.media.tv.TimelineResponse;
import android.media.tv.TsRequest;
import android.media.tv.TsResponse;
import android.media.tv.TvContentRating;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.media.tv.TvRecordingClient;
import android.media.tv.TvRecordingInfo;
import android.media.tv.TvTrackInfo;
import android.media.tv.TvView;
import android.media.tv.interactive.TvInteractiveAppManager;
import android.media.tv.interactive.TvInteractiveAppService;
import android.media.tv.interactive.TvInteractiveAppServiceInfo;
import android.media.tv.interactive.TvInteractiveAppView;
import android.net.Uri;
import android.net.http.SslCertificate;
import android.os.Bundle;
import android.os.ConditionVariable;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.SharedMemory;
import android.tv.cts.R;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.SurfaceView;
import android.view.View;

import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.RequiredFeatureRule;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Test {@link android.media.tv.interactive.TvInteractiveAppService}.
 */
@RunWith(AndroidJUnit4.class)
public class TvInteractiveAppServiceTest {
    private static final long TIME_OUT_MS = 20000L;
    private static final Uri CHANNEL_0 = TvContract.buildChannelUri(0);

    private Instrumentation mInstrumentation;
    private ActivityScenario<TvInteractiveAppViewStubActivity> mActivityScenario;
    private TvInteractiveAppViewStubActivity mActivity;
    private TvInteractiveAppView mTvIAppView;

    private TvView mTvView;
    private TvInteractiveAppManager mManager;
    private TvInteractiveAppServiceInfo mStubInfo;
    private StubTvInteractiveAppService.StubSessionImpl mSession;
    private TvInputManager mTvInputManager;
    private TvInputInfo mTvInputInfo;
    private StubTvInputService2.StubSessionImpl2 mInputSession;
    private StubTvInputService2.StubRecordingSessionImpl mRecordingSession;
    private TvRecordingClient mTvRecordingClient;

    @Rule
    public RequiredFeatureRule featureRule = new RequiredFeatureRule(
            PackageManager.FEATURE_LIVE_TV);

    private final MockCallback mCallback = new MockCallback();
    private final MockTvInputCallback mTvInputCallback = new MockTvInputCallback();
    private final TvRecordingClient.RecordingCallback mRecordingCallback =
            new TvRecordingClient.RecordingCallback() {
                @Override
                public void onConnectionFailed(String inputId) {
                    super.onConnectionFailed(inputId);
                }

                @Override
                public void onDisconnected(String inputId) {
                    super.onDisconnected(inputId);
                }

                @Override
                public void onTuned(Uri channelUri) {
                    super.onTuned(channelUri);
                }

                @Override
                public void onRecordingStopped(Uri recordedProgramUri) {
                    super.onRecordingStopped(recordedProgramUri);
                }

                @Override
                public void onError(int error) {
                    super.onError(error);
                }

                @Override
                public void onEvent(String inputId, String eventType, Bundle eventArgs) {
                    super.onEvent(inputId, eventType, eventArgs);
                }
            };

    public static class MockCallback extends TvInteractiveAppView.TvInteractiveAppCallback {
        private int mRequestCurrentChannelUriCount = 0;
        private int mRequestCurrentVideoBoundsCount = 0;
        private int mStateChangedCount = 0;
        private int mBiIAppCreatedCount = 0;
        private int mRequestSigningCount = 0;
        private int mRequestStartRecordingCount = 0;
        private int mRequestStopRecordingCount = 0;
        private int mRequestScheduleRecordingCount = 0;
        private int mSetTvRecordingInfoCount = 0;
        private int mRequestTvRecordingInfoCount = 0;
        private int mRequestTvRecordingInfoListCount = 0;
        private int mRequestAvailableSpeedsCount = 0;
        private int mRequestTimeShiftModeCount = 0;
        private int mSendTimeShiftCommandCount = 0;
        private int mRequestSelectedTrackInfoCount = 0;
        private int mRequestSigningSSLCount = 0;
        private int mRequestCertificateCount = 0;

        private String mIAppServiceId = null;
        private Integer mState = null;
        private Integer mErr = null;
        private Uri mBiIAppUri = null;
        private String mBiIAppId = null;
        private String mInputId = null;
        private Uri mChannelUri = null;
        private Uri mProgramUri = null;
        private String mRecordingId = null;
        private String mRequestId = null;
        private TvRecordingInfo mTvRecordingInfo = null;
        private Integer mRecordingType = null;
        private Long mStartTime = null;
        private Long mDuration = null;
        private Integer mRepeatedDays = null;
        private Bundle mParams = null;
        private String mTimeShiftCommandType = null;
        private String mSigningId = null;
        private String mAlgorithm = null;
        private String mHost = null;
        private String mAlias = null;
        private Integer mPort = null;
        private byte[] mData = null;

        private void resetValues() {
            mRequestCurrentChannelUriCount = 0;
            mRequestCurrentVideoBoundsCount = 0;
            mRequestStartRecordingCount = 0;
            mRequestStopRecordingCount = 0;
            mRequestScheduleRecordingCount = 0;
            mStateChangedCount = 0;
            mBiIAppCreatedCount = 0;
            mRequestSigningCount = 0;
            mSetTvRecordingInfoCount = 0;
            mRequestTvRecordingInfoCount = 0;
            mRequestTvRecordingInfoListCount = 0;
            mRequestAvailableSpeedsCount = 0;
            mRequestTimeShiftModeCount = 0;
            mRequestSigningSSLCount = 0;
            mRequestCertificateCount = 0;

            mIAppServiceId = null;
            mState = null;
            mErr = null;
            mBiIAppUri = null;
            mBiIAppId = null;
            mInputId = null;
            mChannelUri = null;
            mProgramUri = null;
            mRecordingId = null;
            mRequestId = null;
            mTvRecordingInfo = null;
            mRecordingType = null;
            mStartTime = null;
            mDuration = null;
            mRepeatedDays = null;
            mParams = null;
            mTimeShiftCommandType = null;
            mSigningId = null;
            mAlgorithm = null;
            mHost = null;
            mPort = null;
            mData = null;
            mAlias = null;
        }

        @Override
        public void onRequestCurrentChannelUri(String iAppServiceId) {
            super.onRequestCurrentChannelUri(iAppServiceId);
            mRequestCurrentChannelUriCount++;
        }

        @Override
        public void onRequestCurrentVideoBounds(String iAppServiceId) {
            super.onRequestCurrentVideoBounds(iAppServiceId);
            mRequestCurrentVideoBoundsCount++;
        }

        @Override
        public void onRequestSigning(String iAppServiceId, String signingId,
                String algorithm, String alias, byte[] data) {
            super.onRequestSigning(iAppServiceId, signingId, algorithm, alias, data);
            mRequestSigningCount++;
            mSigningId = signingId;
            mAlgorithm = algorithm;
            mAlias = alias;
            mData = data;
        }

        @Override
        public void onRequestSigning(String iAppServiceId, String signingId, String algorithm,
                String host, int port, byte[] data) {
            mRequestSigningSSLCount++;
            mSigningId = signingId;
            mAlgorithm = algorithm;
            mHost = host;
            mPort = port;
            mData = data;
        }

        @Override
        public void onRequestCertificate(String iAppServiceId, String host, int port) {
            mRequestCertificateCount++;
            mHost = host;
            mPort = port;
        }

        @Override
        public void onStateChanged(String iAppServiceId, int state, int err) {
            super.onStateChanged(iAppServiceId, state, err);
            mStateChangedCount++;
            mIAppServiceId = iAppServiceId;
            mState = state;
            mErr = err;
        }

        @Override
        public void onBiInteractiveAppCreated(String iAppServiceId, Uri biIAppUri,
                String biIAppId) {
            super.onBiInteractiveAppCreated(iAppServiceId, biIAppUri, biIAppId);
            mBiIAppCreatedCount++;
            mIAppServiceId = iAppServiceId;
            mBiIAppUri = biIAppUri;
            mBiIAppId = biIAppId;
        }

        @Override
        public void onPlaybackCommandRequest(String id, String type, Bundle bundle) {
            super.onPlaybackCommandRequest(id, type, bundle);
        }

        @Override
        public void onTimeShiftCommandRequest(String id, String type, Bundle bundle) {
            super.onTimeShiftCommandRequest(id, type, bundle);
            mSendTimeShiftCommandCount++;
            mTimeShiftCommandType = type;
            mParams = bundle;
        }

        @Override
        public void onRequestCurrentChannelLcn(String id) {
            super.onRequestCurrentChannelLcn(id);
        }

        @Override
        public void onRequestCurrentTvInputId(String id) {
            super.onRequestCurrentTvInputId(id);
        }

        @Override
        public void onRequestStreamVolume(String id) {
            super.onRequestStreamVolume(id);
        }

        @Override
        public void onRequestTrackInfoList(String id) {
            super.onRequestTrackInfoList(id);
        }

        @Override
        public void onSetVideoBounds(String id, Rect rect) {
            super.onSetVideoBounds(id, rect);
        }

        @Override
        public void onTeletextAppStateChanged(String id, int state) {
            super.onTeletextAppStateChanged(id, state);
        }

        @Override
        public void onRequestStartRecording(String id, String requestId, Uri programUri) {
            super.onRequestStartRecording(id, requestId, programUri);
            mRequestStartRecordingCount++;
            mProgramUri = programUri;
            mRequestId = requestId;
        }

        @Override
        public void onRequestStopRecording(String id, String recordingId) {
            super.onRequestStopRecording(id, recordingId);
            mRequestStopRecordingCount++;
            mRecordingId = recordingId;
        }

        @Override
        public void onRequestScheduleRecording(String id, String requestId,
                String inputId, Uri channelUri, Uri programUri, Bundle params) {
            super.onRequestScheduleRecording(
                    id, requestId, inputId, channelUri, programUri, params);
            mRequestScheduleRecordingCount++;
            mRequestId = requestId;
            mInputId = inputId;
            mChannelUri = channelUri;
            mProgramUri = programUri;
            mParams = params;
        }

        @Override
        public void onRequestScheduleRecording(String id, String requestId,
                String inputId, Uri channelUri, long startTime, long duration, int repeated,
                Bundle params) {
            super.onRequestScheduleRecording(
                    id, requestId, inputId, channelUri, startTime, duration, repeated, params);
            mRequestScheduleRecordingCount++;
            mRequestId = requestId;
            mInputId = inputId;
            mChannelUri = channelUri;
            mStartTime = startTime;
            mDuration = duration;
            mRepeatedDays = repeated;
            mParams = params;
        }

        @Override
        public void onSetTvRecordingInfo(String id, String recordingId,
                TvRecordingInfo recordingInfo) {
            super.onSetTvRecordingInfo(id, recordingId, recordingInfo);
            mSetTvRecordingInfoCount++;
            mTvRecordingInfo = recordingInfo;
            mRecordingId = recordingId;
        }

        @Override
        public void onRequestTvRecordingInfo(String id, String recordingId) {
            super.onRequestTvRecordingInfo(id, recordingId);
            mRequestTvRecordingInfoCount++;
            mRecordingId = recordingId;
        }

        @Override
        public void onRequestTvRecordingInfoList(String id, int type) {
            super.onRequestTvRecordingInfoList(id, type);
            mRequestTvRecordingInfoListCount++;
            mRecordingType = type;
        }

        @Override
        public void onRequestSelectedTrackInfo(String id) {
            super.onRequestSelectedTrackInfo(id);
            mRequestSelectedTrackInfoCount++;
        }

        @Override
        public void onRequestAvailableSpeeds(String id) {
            super.onRequestAvailableSpeeds(id);
            mRequestAvailableSpeedsCount++;
        }

        @Override
        public void onRequestTimeShiftMode(String id) {
            super.onRequestTimeShiftMode(id);
            mRequestTimeShiftModeCount++;
        }
    }

    public static class MockTvInputCallback extends TvView.TvInputCallback {
        private int mAitInfoUpdatedCount = 0;

        private AitInfo mAitInfo = null;

        private void resetValues() {
            mAitInfoUpdatedCount = 0;

            mAitInfo = null;
        }

        public void onAitInfoUpdated(String inputId, AitInfo aitInfo) {
            super.onAitInfoUpdated(inputId, aitInfo);
            mAitInfoUpdatedCount++;
            mAitInfo = aitInfo;
        }
        public void onSignalStrengthUpdated(String inputId, int strength) {
            super.onSignalStrengthUpdated(inputId, strength);
        }

        public void onCueingMessageAvailability(String inputId, boolean available) {
            super.onCueingMessageAvailability(inputId, available);
        }

        public void onTimeShiftMode(String inputId, int mode) {
            super.onTimeShiftMode(inputId, mode);
        }

        public void onAvailableSpeeds(String inputId, float[] speeds) {
            super.onAvailableSpeeds(inputId, speeds);
        }

        public void onTuned(String inputId, Uri uri) {
            super.onTuned(inputId, uri);
        }
    }

    private TvInteractiveAppView findTvInteractiveAppViewById(int id) {
        return (TvInteractiveAppView) mActivity.findViewById(id);
    }

    private TvView findTvViewById(int id) {
        return (TvView) mActivity.findViewById(id);
    }

    private void runTestOnUiThread(final Runnable r) throws Throwable {
        final Throwable[] exceptions = new Throwable[1];
        mInstrumentation.runOnMainSync(new Runnable() {
            public void run() {
                try {
                    r.run();
                } catch (Throwable throwable) {
                    exceptions[0] = throwable;
                }
            }
        });
        if (exceptions[0] != null) {
            throw exceptions[0];
        }
    }

    private void linkTvView() {
        assertNotNull(mSession);
        mSession.resetValues();
        mTvView.setCallback(mTvInputCallback);
        mTvView.tune(mTvInputInfo.getId(), CHANNEL_0);
        PollingCheck.waitFor(TIME_OUT_MS, () -> mTvView.getInputSession() != null);
        mInputSession = StubTvInputService2.sStubSessionImpl2;
        assertNotNull(mInputSession);
        mInputSession.resetValues();

        mTvIAppView.setTvView(mTvView);
        mTvView.setInteractiveAppNotificationEnabled(true);
    }

    private void linkTvRecordingClient() {
        assertNotNull(mSession);
        mSession.resetValues();
        mTvRecordingClient = new TvRecordingClient(
                mActivity, "tag", mRecordingCallback, new Handler(Looper.getMainLooper()));
        mTvRecordingClient.tune(mTvInputInfo.getId(), CHANNEL_0);
        PollingCheck.waitFor(TIME_OUT_MS, () -> StubTvInputService2.sStubRecordingSession != null);
        mRecordingSession = StubTvInputService2.sStubRecordingSession;
        mTvRecordingClient.setTvInteractiveAppView(mTvIAppView, "recording_id1");
    }

    private Executor getExecutor() {
        return Runnable::run;
    }

    private static Bundle createTestBundle() {
        Bundle b = new Bundle();
        b.putString("stringKey", new String("Test String"));
        return b;
    }

    private static Uri createTestUri() {
        return createTestUri("content://com.example/");
    }

    private static Uri createTestUri(String uriString) {
        return Uri.parse(uriString);
    }

    private static TvRecordingInfo createMockRecordingInfo(String recordingId) {
        return new TvRecordingInfo(recordingId, 0, 0, 0, "testName", "testDescription", 0, 0,
                createTestUri(), createTestUri(), new ArrayList<TvContentRating>(), createTestUri(),
                0, 0);
    }

    /**
     * Creates a mock {@link TvTrackInfo} for testing.
     * @return a mock {@link TvTrackInfo}
     */
    public static TvTrackInfo createMockTvTrackInfo() {
        return new TvTrackInfo.Builder(TvTrackInfo.TYPE_SUBTITLE, "testId").build();
    }

    public static void compareTvRecordingInfo(TvRecordingInfo expected, TvRecordingInfo actual) {
        assertThat(expected.getRecordingId())
                .isEqualTo(actual.getRecordingId());
        assertThat(expected.getRecordingUri())
                .isEqualTo(actual.getRecordingUri());
        assertThat(expected.getRecordingStartTimeMillis())
                .isEqualTo(actual.getRecordingStartTimeMillis());
        assertThat(expected.getRecordingDurationMillis())
                .isEqualTo(actual.getRecordingDurationMillis());
        assertThat(expected.getRepeatDays())
                .isEqualTo(actual.getRepeatDays());
        assertThat(expected.getChannelUri())
                .isEqualTo(actual.getChannelUri());
        assertThat(expected.getDescription())
                .isEqualTo(actual.getDescription());
        assertThat(expected.getEndPaddingMillis())
                .isEqualTo(actual.getEndPaddingMillis());
        assertThat(expected.getStartPaddingMillis())
                .isEqualTo(actual.getStartPaddingMillis());
        assertThat(expected.getScheduledStartTimeMillis())
                .isEqualTo(actual.getScheduledStartTimeMillis());
        assertThat(expected.getScheduledDurationMillis())
                .isEqualTo(actual.getScheduledDurationMillis());
        assertThat(expected.getName())
                .isEqualTo(actual.getName());
        assertThat(expected.getContentRatings())
                .isEqualTo(actual.getContentRatings());
        assertThat(expected.getProgramUri())
                .isEqualTo(actual.getProgramUri());
    }

    @Before
    public void setUp() throws Throwable {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClass(
                mInstrumentation.getTargetContext(), TvInteractiveAppViewStubActivity.class);

        // DO NOT use ActivityScenario.launch(Class), which can cause ActivityNotFoundException
        // related to BootstrapActivity.
        mActivityScenario = ActivityScenario.launch(intent);
        ConditionVariable activityReferenceObtained = new ConditionVariable();
        mActivityScenario.onActivity(activity -> {
            mActivity = activity;
            activityReferenceObtained.open();
        });
        activityReferenceObtained.block(TIME_OUT_MS);

        assertNotNull("Failed to acquire activity reference.", mActivity);
        mTvIAppView = findTvInteractiveAppViewById(R.id.tviappview);
        assertNotNull("Failed to find TvInteractiveAppView.", mTvIAppView);
        mTvView = findTvViewById(R.id.tviapp_tvview);
        assertNotNull("Failed to find TvView.", mTvView);

        mManager = (TvInteractiveAppManager) mActivity.getSystemService(
                Context.TV_INTERACTIVE_APP_SERVICE);
        assertNotNull("Failed to get TvInteractiveAppManager.", mManager);

        for (TvInteractiveAppServiceInfo info : mManager.getTvInteractiveAppServiceList()) {
            if (info.getServiceInfo().name.equals(StubTvInteractiveAppService.class.getName())) {
                mStubInfo = info;
            }
        }
        assertNotNull(mStubInfo);
        mTvIAppView.setCallback(getExecutor(), mCallback);
        mTvIAppView.setOnUnhandledInputEventListener(getExecutor(),
                new TvInteractiveAppView.OnUnhandledInputEventListener() {
                    @Override
                    public boolean onUnhandledInputEvent(InputEvent event) {
                        return true;
                    }
                });
        mTvIAppView.prepareInteractiveApp(mStubInfo.getId(), 1);
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mTvIAppView.getInteractiveAppSession() != null);
        mSession = StubTvInteractiveAppService.sSession;

        mTvInputManager = (TvInputManager) mActivity.getSystemService(Context.TV_INPUT_SERVICE);
        assertNotNull("Failed to get TvInputManager.", mTvInputManager);

        for (TvInputInfo info : mTvInputManager.getTvInputList()) {
            if (info.getServiceInfo().name.equals(StubTvInputService2.class.getName())) {
                mTvInputInfo = info;
            }
        }
        assertNotNull(mTvInputInfo);
    }

    @After
    public void tearDown() throws Throwable {
        runTestOnUiThread(new Runnable() {
            public void run() {
                mTvIAppView.reset();
                mTvView.reset();
            }
        });
        if (mTvRecordingClient != null) {
            mTvRecordingClient.release();
        }
        mInstrumentation.waitForIdleSync();
        mActivity = null;
        if (mActivityScenario != null) {
            mActivityScenario.close();
        }
    }

    @Test
    public void testNotifyRecordingTuned() throws Throwable {
        linkTvRecordingClient();
        mRecordingSession.notifyTuned(CHANNEL_0);
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mRecordingTunedCount > 0);
        assertThat(mSession.mTunedUri).isEqualTo(CHANNEL_0);
        assertThat(mSession.mRecordingId).isEqualTo("recording_id1");
    }

    @Test
    public void testRecordingConnectionFailed() throws Throwable {
        assertNotNull(mSession);
        mSession.resetValues();
        mTvRecordingClient = new TvRecordingClient(
                mActivity, "tag", mRecordingCallback, new Handler(Looper.getMainLooper()));
        mTvRecordingClient.setTvInteractiveAppView(mTvIAppView, "recording_id2");
        String invalidInputId = "___invalid_input_id___";
        mTvRecordingClient.tune(invalidInputId, CHANNEL_0);

        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mRecordingConnectionFailedCount > 0);
        assertThat(mSession.mRecordingConnectionFailedCount).isEqualTo(1);
        assertThat(mSession.mInputId).isEqualTo(invalidInputId);
        assertThat(mSession.mRecordingId).isEqualTo("recording_id2");
    }

    @Test
    public void testRecordingDisconnected() throws Throwable {
        linkTvRecordingClient();
        mTvRecordingClient.getSessionCallback().onSessionReleased(null);
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mRecordingDisconnected > 0);
        assertThat(mSession.mRecordingDisconnected).isEqualTo(1);
        assertThat(mSession.mInputId).isEqualTo(mTvInputInfo.getId());
        assertThat(mSession.mRecordingId).isEqualTo("recording_id1");
    }

    @Test
    public void testRecordingError() throws Throwable {
        linkTvRecordingClient();
        mRecordingSession.notifyError(TvInputManager.RECORDING_ERROR_UNKNOWN);
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mRecordingErrorCount > 0);

        assertThat(mSession.mRecordingErrorCount).isEqualTo(1);
        assertThat(mSession.mRecordingId).isEqualTo("recording_id1");
        assertThat(mSession.mRecordingError).isEqualTo(TvInputManager.RECORDING_ERROR_UNKNOWN);
    }

    @Test
    public void testRequestCurrentChannelUri() throws Throwable {
        assertNotNull(mSession);
        mCallback.resetValues();
        mSession.requestCurrentChannelUri();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mCallback.mRequestCurrentChannelUriCount > 0);

        assertThat(mCallback.mRequestCurrentChannelUriCount).isEqualTo(1);
    }

    @Test
    public void testRequestCurrentVideoBounds() throws Throwable {
        assertNotNull(mSession);
        mCallback.resetValues();
        mSession.requestCurrentVideoBounds();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mCallback.mRequestCurrentVideoBoundsCount > 0);

        assertThat(mCallback.mRequestCurrentVideoBoundsCount).isEqualTo(1);
    }

    @Test
    public void testNotifyRecordingStarted() throws Throwable {
        final String recordingId = "testRecording";
        assertNotNull(mSession);
        mSession.resetValues();
        mTvIAppView.notifyRecordingStarted(recordingId, "request_1");
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mRecordingStartedCount > 0);
        assertThat(mSession.mRecordingStartedCount).isEqualTo(1);
        assertThat(mSession.mRecordingId).isEqualTo(recordingId);
        assertThat(mSession.mRequestId).isEqualTo("request_1");
    }

    @Test
    public void testNotifyRecordingStopped() throws Throwable {
        final String recordingId = "testRecording";
        assertNotNull(mSession);
        mSession.resetValues();
        mTvIAppView.notifyRecordingStopped(recordingId);
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mRecordingStoppedCount > 0);
        assertThat(mSession.mRecordingStoppedCount).isEqualTo(1);
        assertThat(mSession.mRecordingId).isEqualTo(recordingId);
    }

    @Test
    public void testNotifyRecordingScheduled() throws Throwable {
        final String recordingId = "testRecording2";
        assertNotNull(mSession);
        mSession.resetValues();
        mTvIAppView.notifyRecordingScheduled(recordingId, "request_2");
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mRecordingScheduledCount > 0);
        assertThat(mSession.mRecordingScheduledCount).isEqualTo(1);
        assertThat(mSession.mRecordingId).isEqualTo(recordingId);
        assertThat(mSession.mRequestId).isEqualTo("request_2");
    }

    @Test
    public void testSendTimeShiftMode() throws Throwable {
        assertNotNull(mSession);
        mSession.resetValues();
        mTvIAppView.sendTimeShiftMode(TvInputManager.TIME_SHIFT_MODE_AUTO);
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mTimeShiftModeCount > 0);
        assertThat(mSession.mTimeShiftModeCount).isEqualTo(1);
        assertThat(mSession.mTimeShiftMode).isEqualTo(TvInputManager.TIME_SHIFT_MODE_AUTO);
    }

    @Test
    public void testSendAvailableSpeeds() throws Throwable {
        assertNotNull(mSession);
        mSession.resetValues();
        final float[] testSpeeds = new float[] {1.0f, 0.0f, 1.5f};
        mTvIAppView.sendAvailableSpeeds(testSpeeds);
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mAvailableSpeedsCount > 0);
        assertThat(mSession.mAvailableSpeedsCount).isEqualTo(1);
        assertThat(mSession.mAvailableSpeeds).isEqualTo(testSpeeds);
    }

    @Test
    public void testNotifyTimeShiftPlaybackParams() throws Throwable {
        assertNotNull(mSession);
        mSession.resetValues();
        final PlaybackParams testParams = new PlaybackParams().setSpeed(2.0f)
                .setAudioFallbackMode(PlaybackParams.AUDIO_FALLBACK_MODE_DEFAULT)
                .setPitch(0.5f)
                .setAudioStretchMode(PlaybackParams.AUDIO_STRETCH_MODE_VOICE);
        mTvIAppView.notifyTimeShiftPlaybackParams(testParams);
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mPlaybackParamCount > 0);
        assertThat(mSession.mPlaybackParamCount).isEqualTo(1);
        assertThat(mSession.mPlaybackParams.getSpeed()).isEqualTo(testParams.getSpeed());
        assertThat(mSession.mPlaybackParams.getAudioFallbackMode())
                .isEqualTo(testParams.getAudioFallbackMode());
        assertThat(mSession.mPlaybackParams.getPitch())
                .isEqualTo(testParams.getPitch());
        assertThat(mSession.mPlaybackParams.getAudioStretchMode())
                .isEqualTo(testParams.getAudioStretchMode());
    }

    @Test
    public void testNotifyTimeShiftStatusChanged() throws Throwable {
        assertNotNull(mSession);
        mSession.resetValues();
        String testInputId = "TestInput";
        mTvIAppView.notifyTimeShiftStatusChanged(testInputId,
                TvInputManager.TIME_SHIFT_STATUS_AVAILABLE);
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mTimeShiftStatusCount > 0);
        assertThat(mSession.mTimeShiftStatus).isEqualTo(TvInputManager.TIME_SHIFT_STATUS_AVAILABLE);
        assertThat(mSession.mInputId).isEqualTo(testInputId);
    }

    @Test
    public void testNotifyTimeShiftStartPositionChanged() throws Throwable {
        assertNotNull(mSession);
        mSession.resetValues();
        long testPosition = 1010;
        String testInputId = "TestInput";
        mTvIAppView.notifyTimeShiftStartPositionChanged(testInputId, testPosition);
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mTimeShiftStartPositionCount > 0);
        assertThat(mSession.mTimeShiftStartPositionCount).isEqualTo(1);
        assertThat(mSession.mTimeShiftStartPosition).isEqualTo(testPosition);
        assertThat(mSession.mInputId).isEqualTo(testInputId);
    }

    @Test
    public void testNotifyTimeShiftCurrentPositionChanged() throws Throwable {
        assertNotNull(mSession);
        mSession.resetValues();
        long testPosition = 1010;
        String testInputId = "TestInput";
        mTvIAppView.notifyTimeShiftCurrentPositionChanged(testInputId, testPosition);
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mTimeShiftCurrentPositionCount > 0);
        assertThat(mSession.mTimeShiftCurrentPositionCount).isEqualTo(1);
        assertThat(mSession.mTimeShiftCurrentPosition).isEqualTo(testPosition);
        assertThat(mSession.mInputId).isEqualTo(testInputId);
    }

    @Test
    public void testNotifyTvMessage() throws Throwable {
        Bundle testBundle = createTestBundle();
        assertNotNull(mSession);
        mSession.resetValues();
        mTvIAppView.notifyTvMessage(TvInputManager.TV_MESSAGE_TYPE_WATERMARK, testBundle);
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mTvMessageCount > 0);
        assertThat(mSession.mTvMessageCount).isEqualTo(1);
        assertThat(mSession.mTvMessageType).isEqualTo(TvInputManager.TV_MESSAGE_TYPE_WATERMARK);
        assertBundlesAreEqual(mSession.mTvMessageData, testBundle);
    }

    @Test
    public void testRequestSigning() throws Throwable {
        assertNotNull(mSession);
        mCallback.resetValues();
        String testId = "id";
        String testAlgo = "algo";
        String testAlias = "alias";
        byte[] testData = new byte[1];
        mSession.requestSigning(testId, testAlgo, testAlias, testData);
        PollingCheck.waitFor(TIME_OUT_MS, () -> mCallback.mRequestSigningCount > 0);

        assertThat(mCallback.mRequestSigningCount).isEqualTo(1);
        assertThat(mCallback.mSigningId).isEqualTo(testId);
        assertThat(mCallback.mAlgorithm).isEqualTo(testAlgo);
        assertThat(mCallback.mAlias).isEqualTo(testAlias);
        assertArrayEquals(mCallback.mData, testData);
    }

    @Test
    public void testRequestSSLSigning() throws Throwable {
        assertNotNull(mSession);
        mCallback.resetValues();
        String testId = "id";
        String testAlgo = "algo";
        String testHost = "gooooogle.com";
        int testPort = 1337;
        byte[] testData = new byte[1];
        mSession.requestSigning(testId, testAlgo, testHost, testPort, testData);
        PollingCheck.waitFor(TIME_OUT_MS, () -> mCallback.mRequestSigningSSLCount > 0);

        assertThat(mCallback.mRequestSigningSSLCount).isEqualTo(1);
        assertThat(mCallback.mSigningId).isEqualTo(testId);
        assertThat(mCallback.mAlgorithm).isEqualTo(testAlgo);
        assertThat(mCallback.mHost).isEqualTo(testHost);
        assertThat(mCallback.mPort).isEqualTo(testPort);
        assertArrayEquals(mCallback.mData, testData);
    }

    @Test
    public void testRequestCertificate() throws Throwable {
        assertNotNull(mSession);
        String testHost = "gooooooogle.com";
        int testPort = 1337;
        mCallback.resetValues();
        mSession.requestCertificate(testHost, testPort);
        PollingCheck.waitFor(TIME_OUT_MS, () -> mCallback.mRequestCertificateCount > 0);

        assertThat(mCallback.mRequestCertificateCount).isEqualTo(1);
        assertThat(mCallback.mHost).isEqualTo(testHost);
        assertThat(mCallback.mPort).isEqualTo(testPort);
    }

    @Test
    public void testSendCertificate() throws Throwable {
        assertNotNull(mSession);
        mSession.resetValues();

        String testRequester = "gooogle.com";
        String testHost = "gooooooogle.com";
        int testPort = 1337;
        SslCertificate testCert = new SslCertificate(testRequester, testHost, "past", "future");
        mTvIAppView.sendCertificate(testHost, testPort, testCert);
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mSendCertificateCount > 0);

        assertThat(mSession.mSendCertificateCount).isEqualTo(1);
        assertThat(mSession.mHost).isEqualTo(testHost);
        assertThat(mSession.mPort).isEqualTo(testPort);
        assertThat(mSession.mCertificate.getIssuedBy().getDName()).isEqualTo(testHost);
        assertThat(mSession.mCertificate.getIssuedTo().getDName()).isEqualTo(testRequester);
    }

    @Test
    public void testSendSigningResult() {
        assertNotNull(mSession);
        mSession.resetValues();

        mTvIAppView.sendSigningResult("id", new byte[1]);
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mSigningResultCount > 0);

        assertThat(mSession.mSigningResultCount).isEqualTo(1);
        // TODO: check values
    }

    @Test
    public void testNotifyVideoFreezeUpdated() throws Throwable {
        assertNotNull(mSession);
        mSession.resetValues();
        mTvIAppView.notifyVideoFreezeUpdated(true);
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mVideoFreezeUpdatedCount > 0);
        assertThat(mSession.mVideoFreezeUpdatedCount).isEqualTo(1);
        assertThat(mSession.mVideoFrozen).isEqualTo(true);
    }

    @Test
    public void testNotifyError() {
        assertNotNull(mSession);
        mSession.resetValues();

        mTvIAppView.notifyError("msg", new Bundle());
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mErrorCount > 0);

        assertThat(mSession.mErrorCount).isEqualTo(1);
        // TODO: check values
    }

    @Test
    public void testSetSurface() throws Throwable {
        assertNotNull(mSession);

        assertThat(mSession.mSetSurfaceCount).isEqualTo(1);
    }

    @Test
    public void testLayoutSurface() throws Throwable {
        assertNotNull(mSession);

        final int left = 10;
        final int top = 20;
        final int right = 30;
        final int bottom = 40;

        mSession.layoutSurface(left, top, right, bottom);

        new PollingCheck(TIME_OUT_MS) {
            @Override
            protected boolean check() {
                int childCount = mTvIAppView.getChildCount();
                for (int i = 0; i < childCount; ++i) {
                    View v = mTvIAppView.getChildAt(i);
                    if (v instanceof SurfaceView) {
                        return v.getLeft() == left
                            && v.getTop() == top
                            && v.getRight() == right
                            && v.getBottom() == bottom;
                    }
                }
                return false;
            }
        }.run();
        assertThat(mSession.mSurfaceChangedCount > 0).isTrue();
    }

    @Test
    public void testSessionStateChanged() throws Throwable {
        assertNotNull(mSession);
        mCallback.resetValues();
        mSession.notifySessionStateChanged(
                TvInteractiveAppManager.INTERACTIVE_APP_STATE_ERROR,
                TvInteractiveAppManager.ERROR_UNKNOWN);
        PollingCheck.waitFor(TIME_OUT_MS, () -> mCallback.mStateChangedCount > 0);

        assertThat(mCallback.mStateChangedCount).isEqualTo(1);
        assertThat(mCallback.mIAppServiceId).isEqualTo(mStubInfo.getId());
        assertThat(mCallback.mState)
                .isEqualTo(TvInteractiveAppManager.INTERACTIVE_APP_STATE_ERROR);
        assertThat(mCallback.mErr).isEqualTo(TvInteractiveAppManager.ERROR_UNKNOWN);
    }

    @Test
    public void testStartStopInteractiveApp() throws Throwable {
        assertNotNull(mSession);
        mSession.resetValues();
        mTvIAppView.startInteractiveApp();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mStartInteractiveAppCount > 0);
        assertThat(mSession.mStartInteractiveAppCount).isEqualTo(1);

        assertNotNull(mSession);
        mSession.resetValues();
        mTvIAppView.stopInteractiveApp();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mStopInteractiveAppCount > 0);
        assertThat(mSession.mStopInteractiveAppCount).isEqualTo(1);
    }

    @Test
    public void testDispatchKeyDown() {
        assertNotNull(mSession);
        mSession.resetValues();
        final int keyCode = KeyEvent.KEYCODE_Q;
        final KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, keyCode);

        mTvIAppView.dispatchKeyEvent(event);
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mKeyDownCount > 0);

        assertThat(mSession.mKeyDownCount).isEqualTo(1);
        assertThat(mSession.mKeyDownCode).isEqualTo(keyCode);
        assertKeyEventEquals(mSession.mKeyDownEvent, event);
    }

    @Test
    public void testDispatchKeyUp() {
        assertNotNull(mSession);
        mSession.resetValues();
        final int keyCode = KeyEvent.KEYCODE_I;
        final KeyEvent event = new KeyEvent(KeyEvent.ACTION_UP, keyCode);

        mTvIAppView.dispatchKeyEvent(event);
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mKeyUpCount > 0);

        assertThat(mSession.mKeyUpCount).isEqualTo(1);
        assertThat(mSession.mKeyUpCode).isEqualTo(keyCode);
        assertKeyEventEquals(mSession.mKeyUpEvent, event);
    }

    @Test
    public void testDispatchKeyMultiple() {
        assertNotNull(mSession);
        mSession.resetValues();
        final int keyCode = KeyEvent.KEYCODE_L;
        final KeyEvent event = new KeyEvent(KeyEvent.ACTION_MULTIPLE, keyCode);

        mTvIAppView.dispatchKeyEvent(event);
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mKeyMultipleCount > 0);

        assertThat(mSession.mKeyMultipleCount).isEqualTo(1);
        assertThat(mSession.mKeyMultipleCode).isEqualTo(keyCode);
        assertKeyEventEquals(mSession.mKeyMultipleEvent, event);
    }

    @Test
    public void testDispatchUnhandledInputEvent() {
        final int keyCode = KeyEvent.KEYCODE_I;
        final KeyEvent event = new KeyEvent(KeyEvent.ACTION_UP, keyCode);

        assertThat(mTvIAppView.dispatchUnhandledInputEvent(event)).isTrue();
    }

    @Test
    public void testCreateBiInteractiveApp() {
        assertNotNull(mSession);
        mSession.resetValues();
        mCallback.resetValues();
        final Bundle bundle = createTestBundle();
        final Uri uri = createTestUri();
        final String biIAppId = "biIAppId";

        mTvIAppView.createBiInteractiveApp(uri, bundle);
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mCallback.mBiIAppCreatedCount > 0);

        assertThat(mSession.mCreateBiIAppCount).isEqualTo(1);
        assertThat(mSession.mCreateBiIAppUri).isEqualTo(uri);
        assertBundlesAreEqual(mSession.mCreateBiIAppParams, bundle);

        assertThat(mCallback.mIAppServiceId).isEqualTo(mStubInfo.getId());
        assertThat(mCallback.mBiIAppUri).isEqualTo(uri);
        assertThat(mCallback.mBiIAppId).isEqualTo(biIAppId);

        mTvIAppView.destroyBiInteractiveApp(biIAppId);
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mDestroyBiIAppCount > 0);

        assertThat(mSession.mDestroyBiIAppCount).isEqualTo(1);
        assertThat(mSession.mDestroyBiIAppId).isEqualTo(biIAppId);
    }

    @Test
    public void testTuned() {
        linkTvView();

        mInputSession.notifyTuned(CHANNEL_0);
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mTunedCount > 0);

        assertThat(mSession.mTunedCount).isEqualTo(1);
        assertThat(mSession.mTunedUri).isEqualTo(CHANNEL_0);
    }

    @Test
    public void testNotifyTrackSelectedFromInput() {
        linkTvView();
        String testTrackId = "testTrackId";
        mInputSession.notifyTrackSelected(0, testTrackId);
        mInstrumentation.waitForIdleSync();

        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mTrackSelectedCount > 0);

        assertThat(mSession.mTrackSelectedCount).isEqualTo(1);
        assertThat(mSession.mSelectedTrackId).isEqualTo(testTrackId);
    }

    @Test
    public void testVideoAvailable() {
        linkTvView();

        mInputSession.notifyVideoAvailable();
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mVideoAvailableCount > 0);

        assertThat(mSession.mVideoAvailableCount).isEqualTo(1);
    }

    @Test
    public void testAdRequest() throws Throwable {
        linkTvView();

        File tmpFile = File.createTempFile("cts_tv_interactive_app", "tias_test");
        ParcelFileDescriptor fd =
                ParcelFileDescriptor.open(tmpFile, ParcelFileDescriptor.MODE_READ_WRITE);
        Bundle testBundle = createTestBundle();
        AdRequest adRequest = new AdRequest(
                567, AdRequest.REQUEST_TYPE_START, fd, 787L, 989L, 100L, "MMM", testBundle);
        mSession.requestAd(adRequest);
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mInputSession.mAdRequestCount > 0);

        assertThat(mInputSession.mAdRequestCount).isEqualTo(1);
        assertThat(mInputSession.mAdRequest.getId()).isEqualTo(567);
        assertThat(mInputSession.mAdRequest.getRequestType())
                .isEqualTo(AdRequest.REQUEST_TYPE_START);
        assertNotNull(mInputSession.mAdRequest.getFileDescriptor());
        assertThat(mInputSession.mAdRequest.getStartTimeMillis()).isEqualTo(787L);
        assertThat(mInputSession.mAdRequest.getStopTimeMillis()).isEqualTo(989L);
        assertThat(mInputSession.mAdRequest.getEchoIntervalMillis()).isEqualTo(100L);
        assertThat(mInputSession.mAdRequest.getMediaFileType()).isEqualTo("MMM");
        assertThat(mInputSession.mAdRequest.getUri()).isEqualTo(null);
        assertBundlesAreEqual(mInputSession.mAdRequest.getMetadata(), testBundle);

        fd.close();
        tmpFile.delete();
    }

    @Test
    public void testAdRequestWithUri() throws Throwable {
        linkTvView();
        Uri testUri = createTestUri();
        Bundle testBundle = createTestBundle();
        AdRequest adRequest = new AdRequest(567, AdRequest.REQUEST_TYPE_START, testUri, 787L, 989L,
                100L, testBundle);
        mSession.requestAd(adRequest);
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mInputSession.mAdRequestCount > 0);

        assertThat(mInputSession.mAdRequestCount).isEqualTo(1);
        assertThat(mInputSession.mAdRequest.getId()).isEqualTo(567);
        assertThat(mInputSession.mAdRequest.getRequestType())
                .isEqualTo(AdRequest.REQUEST_TYPE_START);
        assertThat(mInputSession.mAdRequest.getStartTimeMillis()).isEqualTo(787L);
        assertThat(mInputSession.mAdRequest.getStopTimeMillis()).isEqualTo(989L);
        assertThat(mInputSession.mAdRequest.getEchoIntervalMillis()).isEqualTo(100L);
        assertBundlesAreEqual(mInputSession.mAdRequest.getMetadata(), testBundle);
        assertThat(mInputSession.mAdRequest.getUri()).isEqualTo(testUri);
    }

    @Test
    public void testAdBufferConsumed() throws Throwable {
        linkTvView();
        AdBuffer testAdBuffer = new AdBuffer(0, "mimeType", SharedMemory.create("test", 8), 0, 0, 0,
                0);
        mInputSession.notifyAdBufferConsumed(testAdBuffer);
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mAdBufferConsumedCount > 0);

        assertThat(mSession.mAdBufferConsumedCount).isEqualTo(1);
        assertThat(mSession.mAdBuffer.getId()).isEqualTo(testAdBuffer.getId());
        assertThat(mSession.mAdBuffer.getMimeType()).isEqualTo(testAdBuffer.getMimeType());
        assertThat(mSession.mAdBuffer.getOffset()).isEqualTo(testAdBuffer.getOffset());
        assertThat(mSession.mAdBuffer.getLength()).isEqualTo(testAdBuffer.getLength());
        assertThat(mSession.mAdBuffer.getPresentationTimeUs())
                .isEqualTo(testAdBuffer.getPresentationTimeUs());
        assertThat(mSession.mAdBuffer.getFlags()).isEqualTo(testAdBuffer.getFlags());
    }

    @Test
    public void testNotifyAdBufferReady() throws Throwable {
        linkTvView();
        SharedMemory sm = SharedMemory.create("test", 5);
        ByteBuffer byteBuffer = sm.mapReadWrite();
        byte[] data = new byte[] {77, -25, 103, 96, 127};
        byteBuffer.put(data);
        byteBuffer.flip();
        SharedMemory.unmap(byteBuffer);

        AdBuffer testAdBuffer = new AdBuffer(0, "mimeType", sm, 0, 0, 0, 0);
        mSession.notifyAdBufferReady(testAdBuffer);
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mInputSession.mAdBufferCount > 0);

        assertThat(mInputSession.mAdBufferCount).isEqualTo(1);
        assertThat(mInputSession.mAdBuffer.getId()).isEqualTo(testAdBuffer.getId());
        assertThat(mInputSession.mAdBuffer.getMimeType()).isEqualTo(testAdBuffer.getMimeType());
        assertThat(mInputSession.mAdBuffer.getOffset()).isEqualTo(testAdBuffer.getOffset());
        assertThat(mInputSession.mAdBuffer.getLength()).isEqualTo(testAdBuffer.getLength());
        assertThat(mInputSession.mAdBuffer.getPresentationTimeUs())
                .isEqualTo(testAdBuffer.getPresentationTimeUs());
        assertThat(mInputSession.mAdBuffer.getFlags()).isEqualTo(testAdBuffer.getFlags());

        assertSharedMemoryDataEquals(mInputSession.mAdBuffer.getSharedMemory(), sm);
    }

    @Test
    public void testAdResponse() throws Throwable {
        linkTvView();

        AdResponse adResponse = new AdResponse(767, AdResponse.RESPONSE_TYPE_PLAYING, 909L);
        mInputSession.notifyAdResponse(adResponse);
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mAdResponseCount > 0);

        assertThat(mSession.mAdResponseCount).isEqualTo(1);
        assertThat(mSession.mAdResponse.getId()).isEqualTo(767);
        assertThat(mSession.mAdResponse.getResponseType())
                .isEqualTo(AdResponse.RESPONSE_TYPE_PLAYING);
        assertThat(mSession.mAdResponse.getElapsedTimeMillis()).isEqualTo(909L);
    }

    @Test
    public void testAitInfo() throws Throwable {
        linkTvView();
        mTvInputCallback.resetValues();

        mInputSession.notifyAitInfoUpdated(
                new AitInfo(TvInteractiveAppServiceInfo.INTERACTIVE_APP_TYPE_HBBTV, 2));
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mTvInputCallback.mAitInfoUpdatedCount > 0);

        assertThat(mTvInputCallback.mAitInfoUpdatedCount).isEqualTo(1);
        assertThat(mTvInputCallback.mAitInfo.getType())
                .isEqualTo(TvInteractiveAppServiceInfo.INTERACTIVE_APP_TYPE_HBBTV);
        assertThat(mTvInputCallback.mAitInfo.getVersion()).isEqualTo(2);
    }

    @Test
    public void testSignalStrength() throws Throwable {
        linkTvView();

        mInputSession.notifySignalStrength(TvInputManager.SIGNAL_STRENGTH_STRONG);
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testRemoveBroadcastInfo() throws Throwable {
        linkTvView();

        mSession.removeBroadcastInfo(23);
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testNotifyBiInteractiveAppCreated() throws Throwable {
        mSession.notifyBiInteractiveAppCreated(createTestUri(), "testAppId");
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testTeletextAppState() throws Throwable {
        mSession.notifyTeletextAppStateChanged(TvInteractiveAppManager.TELETEXT_APP_STATE_HIDE);
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testRequestCurrentChannelLcn() throws Throwable {
        mSession.requestCurrentChannelLcn();
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testRequestCurrentTvInputId() throws Throwable {
        mSession.requestCurrentTvInputId();
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testRequestStreamVolume() throws Throwable {
        mSession.requestStreamVolume();
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testRequestTrackInfoList() throws Throwable {
        mSession.requestTrackInfoList();
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testRequestStartRecording() throws Throwable {
        final Uri testUri = createTestUri();
        mSession.requestStartRecording("request_id1", testUri);
        mCallback.resetValues();
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mCallback.mRequestStartRecordingCount > 0);

        assertThat(mCallback.mRequestStartRecordingCount).isEqualTo(1);
        assertThat(mCallback.mProgramUri).isEqualTo(testUri);
        assertThat(mCallback.mRequestId).isEqualTo("request_id1");
    }

    @Test
    public void testRequestStopRecording() throws Throwable {
        final String testRecordingId = "testRecordingId";
        mSession.requestStopRecording(testRecordingId);
        mCallback.resetValues();
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mCallback.mRequestStopRecordingCount > 0);

        assertThat(mCallback.mRequestStopRecordingCount).isEqualTo(1);
        assertThat(mCallback.mRecordingId).isEqualTo(testRecordingId);
    }

    @Test
    public void testRequestScheduleRecordingWithProgram() throws Throwable {
        final Uri testChannelUri = createTestUri("content://com.example/channel");
        final Uri testProgramUri = createTestUri("content://com.example/program");
        final Bundle testBundle = createTestBundle();
        mSession.requestScheduleRecording(
                "request_id2", "inputId1", testChannelUri, testProgramUri, testBundle);
        mCallback.resetValues();
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mCallback.mRequestScheduleRecordingCount > 0);

        assertThat(mCallback.mRequestScheduleRecordingCount).isEqualTo(1);
        assertThat(mCallback.mRequestId).isEqualTo("request_id2");
        assertThat(mCallback.mInputId).isEqualTo("inputId1");
        assertThat(mCallback.mChannelUri).isEqualTo(testChannelUri);
        assertThat(mCallback.mProgramUri).isEqualTo(testProgramUri);
        assertBundlesAreEqual(mCallback.mParams, testBundle);
    }

    @Test
    public void testRequestScheduleRecordingWithTime() throws Throwable {
        final Uri testChannelUri = createTestUri("content://com.example/channel");
        final long startTime = 374280000L;
        final long duration = 3600000L;
        final int repeated = TvRecordingInfo.MONDAY | TvRecordingInfo.FRIDAY;
        final Bundle testBundle = createTestBundle();
        mSession.requestScheduleRecording(
                "request_id3", "inputId2", testChannelUri, startTime, duration, repeated,
                testBundle);
        mCallback.resetValues();
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mCallback.mRequestScheduleRecordingCount > 0);

        assertThat(mCallback.mRequestScheduleRecordingCount).isEqualTo(1);
        assertThat(mCallback.mRequestId).isEqualTo("request_id3");
        assertThat(mCallback.mInputId).isEqualTo("inputId2");
        assertThat(mCallback.mChannelUri).isEqualTo(testChannelUri);
        assertThat(mCallback.mStartTime).isEqualTo(startTime);
        assertThat(mCallback.mDuration).isEqualTo(duration);
        assertThat(mCallback.mRepeatedDays).isEqualTo(repeated);
        assertBundlesAreEqual(mCallback.mParams, testBundle);
    }

    @Test
    public void testRequestAvailableSpeeds() throws Throwable {
        mSession.requestAvailableSpeeds();
        mCallback.resetValues();
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mCallback.mRequestAvailableSpeedsCount > 0);

        assertThat(mCallback.mRequestAvailableSpeedsCount).isEqualTo(1);
    }

    @Test
    public void testRequestTimeShiftMode() throws Throwable {
        mSession.requestTimeShiftMode();
        mCallback.resetValues();
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mCallback.mRequestTimeShiftModeCount > 0);

        assertThat(mCallback.mRequestTimeShiftModeCount).isEqualTo(1);
    }

    @Test
    public void testRequestTvRecordingInfo() throws Throwable {
        String mockRecordingId = "testRecordingId";
        mSession.requestTvRecordingInfo(mockRecordingId);
        mCallback.resetValues();
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mCallback.mRequestTvRecordingInfoCount > 0);
        assertThat(mCallback.mRequestTvRecordingInfoCount).isEqualTo(1);
        assertThat(mCallback.mRecordingId).isEqualTo(mockRecordingId);
    }

    @Test
    public void testRequestTvRecordingInfoList() throws Throwable {
        mSession.requestTvRecordingInfoList(TvRecordingInfo.RECORDING_SCHEDULED);
        mCallback.resetValues();
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mCallback.mRequestTvRecordingInfoListCount > 0);
        assertThat(mCallback.mRequestTvRecordingInfoListCount).isEqualTo(1);
        assertThat(mCallback.mRecordingType).isEqualTo(TvRecordingInfo.RECORDING_SCHEDULED);
    }

    @Test
    public void testRequestSelectedTrackInfo() throws Throwable {
        mSession.requestSelectedTrackInfo();
        mCallback.resetValues();
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mCallback.mRequestSelectedTrackInfoCount > 0);
        assertThat(mCallback.mRequestSelectedTrackInfoCount).isEqualTo(1);
    }

    @Test
    public void testSetTvRecordingInfo() throws Throwable {
        String mockRecordingId = "testRecordingId";
        TvRecordingInfo mockRecordingInfo = createMockRecordingInfo(mockRecordingId);
        mockRecordingInfo.setDescription("modifiedDescription");
        mockRecordingInfo.setName("modifiedName");
        mSession.setTvRecordingInfo(mockRecordingId, mockRecordingInfo);
        mCallback.resetValues();
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mCallback.mSetTvRecordingInfoCount > 0);

        assertThat(mCallback.mSetTvRecordingInfoCount).isEqualTo(1);
        compareTvRecordingInfo(mockRecordingInfo, mCallback.mTvRecordingInfo);
        assertThat(mCallback.mRecordingId).isEqualTo(mockRecordingId);
    }

    @Test
    public void testSendTvRecordingInfo() throws Throwable {
        TvRecordingInfo mockRecordingInfo = createMockRecordingInfo("testRecordingId");
        mSession.resetValues();
        mTvIAppView.sendTvRecordingInfo(mockRecordingInfo);
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mSendTvRecordingInfoCount > 0);

        assertThat(mSession.mSendTvRecordingInfoCount).isEqualTo(1);
        compareTvRecordingInfo(mockRecordingInfo, mSession.mTvRecordingInfo);
    }

    @Test
    public void testSendTvRecordingInfoList() throws Throwable {
        TvRecordingInfo mockRecordingInfo = createMockRecordingInfo("testRecordingId");
        ArrayList<TvRecordingInfo> tvRecordingInfos = new ArrayList<>();
        tvRecordingInfos.add(mockRecordingInfo);
        mSession.resetValues();
        mTvIAppView.sendTvRecordingInfoList(tvRecordingInfos);
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mSendTvRecordingInfoListCount > 0);

        assertThat(mSession.mSendTvRecordingInfoListCount).isEqualTo(1);
        assertThat(mSession.mTvRecordingInfoList.size()).isEqualTo(1);
        compareTvRecordingInfo(mockRecordingInfo, mSession.mTvRecordingInfoList.get(0));
    }

    @Test
    public void testSendSelectedTrackInfo() throws Throwable {
        TvTrackInfo tvTrackInfo = createMockTvTrackInfo();
        ArrayList<TvTrackInfo> trackInfos = new ArrayList<>();
        trackInfos.add(tvTrackInfo);
        mSession.resetValues();
        mTvIAppView.sendSelectedTrackInfo(trackInfos);
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mSendSelectedTrackInfoCount > 0);

        assertThat(mSession.mSendSelectedTrackInfoCount).isEqualTo(1);
        assertThat(mSession.mSelectedTrackInfoList.size()).isEqualTo(1);
        TvTrackInfo receivedTrackInfo = mSession.mSelectedTrackInfoList.get(0);
        assertThat(receivedTrackInfo.getId()).isEqualTo(tvTrackInfo.getId());
        assertThat(receivedTrackInfo.getType()).isEqualTo(tvTrackInfo.getType());
    }

    @Test
    public void testSendPlaybackCommandRequest() throws Throwable {
        mSession.sendPlaybackCommandRequest(mStubInfo.getId(), createTestBundle());
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testSendTimeShiftCommandRequest() throws Throwable {
        assertNotNull(mSession);
        mSession.resetValues();
        Bundle testBundle = createTestBundle();
        mSession.sendTimeShiftCommandRequest(
                TvInteractiveAppService.TIME_SHIFT_COMMAND_TYPE_RESUME, testBundle);
        PollingCheck.waitFor(TIME_OUT_MS, () -> mCallback.mSendTimeShiftCommandCount > 0);

        assertThat(mCallback.mSendTimeShiftCommandCount).isEqualTo(1);
        assertThat(mCallback.mTimeShiftCommandType)
                .isEqualTo(TvInteractiveAppService.TIME_SHIFT_COMMAND_TYPE_RESUME);
        assertBundlesAreEqual(mCallback.mParams, testBundle);
    }

    @Test
    public void testSetMediaViewEnabled() throws Throwable {
        mSession.setMediaViewEnabled(false);
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testSetVideoBounds() throws Throwable {
        mSession.setVideoBounds(new Rect());
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testResetInteractiveApp() throws Throwable {
        mTvIAppView.resetInteractiveApp();
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testSendCurrentChannelLcn() throws Throwable {
        mTvIAppView.sendCurrentChannelLcn(1);
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testSendCurrentChannelUri() throws Throwable {
        mTvIAppView.sendCurrentChannelUri(createTestUri());
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testSendCurrentTvInputId() throws Throwable {
        mTvIAppView.sendCurrentTvInputId("input_id");
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testSendStreamVolume() throws Throwable {
        mTvIAppView.sendStreamVolume(0.1f);
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testSendTrackInfoList() throws Throwable {
        mTvIAppView.sendTrackInfoList(new ArrayList<TvTrackInfo>());
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testSendCurrentVideoBounds() throws Throwable {
        assertNotNull(mSession);
        mSession.resetValues();
        Rect rect = new Rect(1, 2, 6, 7);
        mTvIAppView.sendCurrentVideoBounds(rect);
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mCurrentVideoBoundsCount > 0);
        assertThat(mSession.mCurrentVideoBoundsCount).isEqualTo(1);
        assertThat(mSession.mCurrentVideoBounds).isEqualTo(rect);
    }

    @Test
    public void testSetTeletextAppEnabled() throws Throwable {
        mTvIAppView.setTeletextAppEnabled(false);
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testTsRequest() throws Throwable {
        linkTvView();

        TsRequest request = new TsRequest(1, BroadcastInfoRequest.REQUEST_OPTION_REPEAT, 11);
        mSession.requestBroadcastInfo(request);
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mInputSession.mBroadcastInfoRequestCount > 0);

        request = (TsRequest) mInputSession.mBroadcastInfoRequest;
        assertThat(mInputSession.mBroadcastInfoRequestCount).isEqualTo(1);
        assertThat(request.getType()).isEqualTo(TvInputManager.BROADCAST_INFO_TYPE_TS);
        assertThat(request.getRequestId()).isEqualTo(1);
        assertThat(request.getOption()).isEqualTo(BroadcastInfoRequest.REQUEST_OPTION_REPEAT);
        assertThat(request.getTsPid()).isEqualTo(11);
    }

    @Test
    public void testSignalingDataRequest() throws Throwable {
        linkTvView();

        ArrayList<String> signalingInfoList = new ArrayList<>();
        signalingInfoList.add(SignalingDataRequest.SIGNALING_METADATA_AEAT);
        SignalingDataRequest request = new SignalingDataRequest(0, 0,
                SignalingDataRequest.SIGNALING_DATA_NO_GROUP_ID, signalingInfoList);
        mSession.requestBroadcastInfo(request);
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mInputSession.mBroadcastInfoRequestCount > 0);

        request = (SignalingDataRequest) mInputSession.mBroadcastInfoRequest;
        assertThat(mInputSession.mBroadcastInfoRequestCount).isEqualTo(1);
        assertThat(request.getType()).isEqualTo(TvInputManager.BROADCAST_INFO_TYPE_SIGNALING_DATA);
        assertThat(request.getRequestId()).isEqualTo(0);
        assertThat(request.getOption()).isEqualTo(0);
        assertThat(request.getGroup()).isEqualTo(SignalingDataRequest.SIGNALING_DATA_NO_GROUP_ID);
        assertThat(request.getSignalingDataTypes().get(0))
                .isEqualTo(SignalingDataRequest.SIGNALING_METADATA_AEAT);
    }

    @Test
    public void testCommandRequest() throws Throwable {
        linkTvView();

        CommandRequest request = new CommandRequest(2, BroadcastInfoRequest.REQUEST_OPTION_REPEAT,
                "nameSpace1", "name2", "requestArgs", CommandRequest.ARGUMENT_TYPE_XML);
        mSession.requestBroadcastInfo(request);
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mInputSession.mBroadcastInfoRequestCount > 0);

        request = (CommandRequest) mInputSession.mBroadcastInfoRequest;
        assertThat(mInputSession.mBroadcastInfoRequestCount).isEqualTo(1);
        assertThat(request.getType()).isEqualTo(TvInputManager.BROADCAST_INFO_TYPE_COMMAND);
        assertThat(request.getRequestId()).isEqualTo(2);
        assertThat(request.getOption()).isEqualTo(BroadcastInfoRequest.REQUEST_OPTION_REPEAT);
        assertThat(request.getNamespace()).isEqualTo("nameSpace1");
        assertThat(request.getName()).isEqualTo("name2");
        assertThat(request.getArguments()).isEqualTo("requestArgs");
        assertThat(request.getArgumentType()).isEqualTo(CommandRequest.ARGUMENT_TYPE_XML);
    }

    @Test
    public void testDsmccRequest() throws Throwable {
        linkTvView();

        final Uri uri = createTestUri();
        DsmccRequest request = new DsmccRequest(3, BroadcastInfoRequest.REQUEST_OPTION_REPEAT,
                uri);
        mSession.requestBroadcastInfo(request);
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mInputSession.mBroadcastInfoRequestCount > 0);

        request = (DsmccRequest) mInputSession.mBroadcastInfoRequest;
        assertThat(mInputSession.mBroadcastInfoRequestCount).isEqualTo(1);
        assertThat(request.getType()).isEqualTo(TvInputManager.BROADCAST_INFO_TYPE_DSMCC);
        assertThat(request.getRequestId()).isEqualTo(3);
        assertThat(request.getOption()).isEqualTo(BroadcastInfoRequest.REQUEST_OPTION_REPEAT);
        assertThat(request.getUri()).isEqualTo(uri);
    }

    @Test
    public void testPesRequest() throws Throwable {
        linkTvView();

        PesRequest request = new PesRequest(4, BroadcastInfoRequest.REQUEST_OPTION_REPEAT, 44, 444);
        mSession.requestBroadcastInfo(request);
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mInputSession.mBroadcastInfoRequestCount > 0);

        request = (PesRequest) mInputSession.mBroadcastInfoRequest;
        assertThat(mInputSession.mBroadcastInfoRequestCount).isEqualTo(1);
        assertThat(request.getType()).isEqualTo(TvInputManager.BROADCAST_INFO_TYPE_PES);
        assertThat(request.getRequestId()).isEqualTo(4);
        assertThat(request.getOption()).isEqualTo(BroadcastInfoRequest.REQUEST_OPTION_REPEAT);
        assertThat(request.getTsPid()).isEqualTo(44);
        assertThat(request.getStreamId()).isEqualTo(444);
    }

    @Test
    public void testSectionRequest() throws Throwable {
        linkTvView();

        SectionRequest request = new SectionRequest(5, BroadcastInfoRequest.REQUEST_OPTION_REPEAT,
                55, 555, 5555);
        mSession.requestBroadcastInfo(request);
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mInputSession.mBroadcastInfoRequestCount > 0);

        request = (SectionRequest) mInputSession.mBroadcastInfoRequest;
        assertThat(mInputSession.mBroadcastInfoRequestCount).isEqualTo(1);
        assertThat(request.getType()).isEqualTo(TvInputManager.BROADCAST_INFO_TYPE_SECTION);
        assertThat(request.getRequestId()).isEqualTo(5);
        assertThat(request.getOption()).isEqualTo(BroadcastInfoRequest.REQUEST_OPTION_REPEAT);
        assertThat(request.getTsPid()).isEqualTo(55);
        assertThat(request.getTableId()).isEqualTo(555);
        assertThat(request.getVersion()).isEqualTo(5555);
    }

    @Test
    public void testStreamEventRequest() throws Throwable {
        linkTvView();

        final Uri uri = createTestUri();
        StreamEventRequest request = new StreamEventRequest(6,
                BroadcastInfoRequest.REQUEST_OPTION_REPEAT, uri, "testName");
        mSession.requestBroadcastInfo(request);
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mInputSession.mBroadcastInfoRequestCount > 0);

        request = (StreamEventRequest) mInputSession.mBroadcastInfoRequest;
        assertThat(mInputSession.mBroadcastInfoRequestCount).isEqualTo(1);
        assertThat(request.getType()).isEqualTo(TvInputManager.BROADCAST_INFO_STREAM_EVENT);
        assertThat(request.getRequestId()).isEqualTo(6);
        assertThat(request.getOption()).isEqualTo(BroadcastInfoRequest.REQUEST_OPTION_REPEAT);
        assertThat(request.getTargetUri()).isEqualTo(uri);
        assertThat(request.getEventName()).isEqualTo("testName");
    }

    @Test
    public void testTableRequest() throws Throwable {
        linkTvView();

        TableRequest request = new TableRequest(7, BroadcastInfoRequest.REQUEST_OPTION_REPEAT, 77,
                TableRequest.TABLE_NAME_PMT, 777);
        mSession.requestBroadcastInfo(request);
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mInputSession.mBroadcastInfoRequestCount > 0);

        request = (TableRequest) mInputSession.mBroadcastInfoRequest;
        assertThat(mInputSession.mBroadcastInfoRequestCount).isEqualTo(1);
        assertThat(request.getType()).isEqualTo(TvInputManager.BROADCAST_INFO_TYPE_TABLE);
        assertThat(request.getRequestId()).isEqualTo(7);
        assertThat(request.getOption()).isEqualTo(BroadcastInfoRequest.REQUEST_OPTION_REPEAT);
        assertThat(request.getTableId()).isEqualTo(77);
        assertThat(request.getTableName()).isEqualTo(TableRequest.TABLE_NAME_PMT);
        assertThat(request.getVersion()).isEqualTo(777);
    }

    @Test
    public void testTimelineRequest() throws Throwable {
        linkTvView();

        TimelineRequest request = new TimelineRequest(8, BroadcastInfoRequest.REQUEST_OPTION_REPEAT,
                8000);
        mSession.requestBroadcastInfo(request);
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mInputSession.mBroadcastInfoRequestCount > 0);

        request = (TimelineRequest) mInputSession.mBroadcastInfoRequest;
        assertThat(mInputSession.mBroadcastInfoRequestCount).isEqualTo(1);
        assertThat(request.getType()).isEqualTo(TvInputManager.BROADCAST_INFO_TYPE_TIMELINE);
        assertThat(request.getRequestId()).isEqualTo(8);
        assertThat(request.getOption()).isEqualTo(BroadcastInfoRequest.REQUEST_OPTION_REPEAT);
        assertThat(request.getIntervalMillis()).isEqualTo(8000);
        assertThat(request.getSelector()).isEqualTo(null);
    }

    @Test
    public void testTimelineRequestWithSelector() throws Throwable {
        linkTvView();

        TimelineRequest requestSent = new TimelineRequest(10,
                BroadcastInfoRequest.REQUEST_OPTION_AUTO_UPDATE, 2532, "selector1");
        mSession.requestBroadcastInfo(requestSent);
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mInputSession.mBroadcastInfoRequestCount > 0);

        TimelineRequest request = (TimelineRequest) mInputSession.mBroadcastInfoRequest;
        assertThat(mInputSession.mBroadcastInfoRequestCount).isEqualTo(1);
        assertThat(request.getType()).isEqualTo(TvInputManager.BROADCAST_INFO_TYPE_TIMELINE);
        assertThat(request.getRequestId()).isEqualTo(requestSent.getRequestId());
        assertThat(request.getOption()).isEqualTo(requestSent.getOption());
        assertThat(request.getIntervalMillis()).isEqualTo(requestSent.getIntervalMillis());
        assertThat(request.getSelector()).isEqualTo(requestSent.getSelector());
    }

    @Test
    public void testTsResponse() throws Throwable {
        linkTvView();

        TsResponse response = new TsResponse(1, 11, BroadcastInfoResponse.RESPONSE_RESULT_OK,
                "TestToken");
        mInputSession.notifyBroadcastInfoResponse(response);
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mBroadcastInfoResponseCount > 0);

        response = (TsResponse) mSession.mBroadcastInfoResponse;
        assertThat(mSession.mBroadcastInfoResponseCount).isEqualTo(1);
        assertThat(response.getType()).isEqualTo(TvInputManager.BROADCAST_INFO_TYPE_TS);
        assertThat(response.getRequestId()).isEqualTo(1);
        assertThat(response.getSequence()).isEqualTo(11);
        assertThat(response.getResponseResult()).isEqualTo(
                BroadcastInfoResponse.RESPONSE_RESULT_OK);
        assertThat(response.getSharedFilterToken()).isEqualTo("TestToken");
    }

    @Test
    public void testSignalingResponse() throws Throwable {
        linkTvView();

        String testTable = "data";
        SignalingDataInfo testSignalingDataInfo =
                new SignalingDataInfo(testTable, SignalingDataRequest.SIGNALING_METADATA_AEAT, 1,
                        SignalingDataInfo.LLS_NO_GROUP_ID);
        ArrayList<String> signalingDataTypes = new ArrayList<>();
        ArrayList<SignalingDataInfo> signalingDataInfoArrayList = new ArrayList<>();
        signalingDataInfoArrayList.add(testSignalingDataInfo);
        signalingDataTypes.add(SignalingDataRequest.SIGNALING_METADATA_AEAT);
        SignalingDataResponse response = new SignalingDataResponse(1, 11,
                BroadcastInfoResponse.RESPONSE_RESULT_OK, signalingDataTypes,
                signalingDataInfoArrayList);
        mInputSession.notifyBroadcastInfoResponse(response);
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mBroadcastInfoResponseCount > 0);

        response = (SignalingDataResponse) mSession.mBroadcastInfoResponse;
        assertThat(mSession.mBroadcastInfoResponseCount).isEqualTo(1);
        assertThat(response.getType()).isEqualTo(TvInputManager.BROADCAST_INFO_TYPE_SIGNALING_DATA);
        assertThat(response.getRequestId()).isEqualTo(1);
        assertThat(response.getSequence()).isEqualTo(11);
        assertThat(response.getResponseResult())
                .isEqualTo(BroadcastInfoResponse.RESPONSE_RESULT_OK);
        assertThat(response.getSignalingDataTypes().get(0))
                .isEqualTo(SignalingDataRequest.SIGNALING_METADATA_AEAT);
        SignalingDataInfo receivedInfo = response.getSignalingDataInfoList().get(0);
        assertThat(receivedInfo.getEncoding()).isEqualTo(SignalingDataInfo.CONTENT_ENCODING_UTF_8);
        assertThat(receivedInfo.getGroup()).isEqualTo(SignalingDataInfo.LLS_NO_GROUP_ID);
        assertThat(receivedInfo.getTable()).isEqualTo(testTable);
        assertThat(receivedInfo.getSignalingDataType())
                .isEqualTo(SignalingDataRequest.SIGNALING_METADATA_AEAT);
        assertThat(receivedInfo.getVersion()).isEqualTo(1);
    }

    @Test
    public void testSignalingResponseWithEncoding() throws Throwable {
        linkTvView();

        String testTable = "data";
        SignalingDataInfo testSignalingDataInfo =
                new SignalingDataInfo(testTable, SignalingDataRequest.SIGNALING_METADATA_AEAT, 1,
                        SignalingDataInfo.LLS_NO_GROUP_ID,
                        SignalingDataInfo.CONTENT_ENCODING_BASE64);
        ArrayList<String> signalingDataTypes = new ArrayList<>();
        ArrayList<SignalingDataInfo> signalingDataInfoArrayList = new ArrayList<>();
        signalingDataInfoArrayList.add(testSignalingDataInfo);
        signalingDataTypes.add(SignalingDataRequest.SIGNALING_METADATA_AEAT);
        SignalingDataResponse response = new SignalingDataResponse(1, 11,
                BroadcastInfoResponse.RESPONSE_RESULT_OK, signalingDataTypes,
                signalingDataInfoArrayList);
        mInputSession.notifyBroadcastInfoResponse(response);
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mBroadcastInfoResponseCount > 0);

        response = (SignalingDataResponse) mSession.mBroadcastInfoResponse;
        assertThat(mSession.mBroadcastInfoResponseCount).isEqualTo(1);
        assertThat(response.getType()).isEqualTo(TvInputManager.BROADCAST_INFO_TYPE_SIGNALING_DATA);
        assertThat(response.getRequestId()).isEqualTo(1);
        assertThat(response.getSequence()).isEqualTo(11);
        assertThat(response.getResponseResult())
                .isEqualTo(BroadcastInfoResponse.RESPONSE_RESULT_OK);
        assertThat(response.getSignalingDataTypes().get(0))
                .isEqualTo(SignalingDataRequest.SIGNALING_METADATA_AEAT);
        SignalingDataInfo receivedInfo = response.getSignalingDataInfoList().get(0);
        assertThat(receivedInfo.getEncoding()).isEqualTo(SignalingDataInfo.CONTENT_ENCODING_BASE64);
        assertThat(receivedInfo.getGroup()).isEqualTo(SignalingDataInfo.LLS_NO_GROUP_ID);
        assertThat(receivedInfo.getTable()).isEqualTo(testTable);
        assertThat(receivedInfo.getSignalingDataType())
                .isEqualTo(SignalingDataRequest.SIGNALING_METADATA_AEAT);
        assertThat(receivedInfo.getVersion()).isEqualTo(1);
    }


    @Test
    public void testCommandResponse() throws Throwable {
        linkTvView();

        CommandResponse response = new CommandResponse(2, 22,
                BroadcastInfoResponse.RESPONSE_RESULT_OK, "commandResponse",
                CommandResponse.RESPONSE_TYPE_JSON);
        mInputSession.notifyBroadcastInfoResponse(response);
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mBroadcastInfoResponseCount > 0);

        response = (CommandResponse) mSession.mBroadcastInfoResponse;
        assertThat(mSession.mBroadcastInfoResponseCount).isEqualTo(1);
        assertThat(response.getType()).isEqualTo(TvInputManager.BROADCAST_INFO_TYPE_COMMAND);
        assertThat(response.getRequestId()).isEqualTo(2);
        assertThat(response.getSequence()).isEqualTo(22);
        assertThat(response.getResponseResult()).isEqualTo(
                BroadcastInfoResponse.RESPONSE_RESULT_OK);
        assertThat(response.getResponse()).isEqualTo("commandResponse");
        assertThat(response.getResponseType()).isEqualTo(CommandResponse.RESPONSE_TYPE_JSON);
    }

    @Test
    public void testDsmccResponse() throws Throwable {
        linkTvView();

        File tmpFile = File.createTempFile("cts_tv_interactive_app", "tias_test");
        ParcelFileDescriptor fd =
                ParcelFileDescriptor.open(tmpFile, ParcelFileDescriptor.MODE_READ_WRITE);
        final List<String> childList = new ArrayList(Arrays.asList("c1", "c2", "c3"));
        final int[] eventIds = new int[] {1, 2, 3};
        final String[] eventNames = new String[] {"event1", "event2", "event3"};
        DsmccResponse response = new DsmccResponse(3, 3, BroadcastInfoResponse.RESPONSE_RESULT_OK,
                fd);

        mInputSession.notifyBroadcastInfoResponse(response);
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mBroadcastInfoResponseCount > 0);

        response = (DsmccResponse) mSession.mBroadcastInfoResponse;
        assertThat(mSession.mBroadcastInfoResponseCount).isEqualTo(1);
        assertThat(response.getType()).isEqualTo(TvInputManager.BROADCAST_INFO_TYPE_DSMCC);
        assertThat(response.getRequestId()).isEqualTo(3);
        assertThat(response.getResponseResult()).isEqualTo(
                BroadcastInfoResponse.RESPONSE_RESULT_OK);
        assertThat(response.getBiopMessageType()).isEqualTo(DsmccResponse.BIOP_MESSAGE_TYPE_FILE);
        assertNotNull(response.getFile());

        response = new DsmccResponse(3, 3, BroadcastInfoResponse.RESPONSE_RESULT_OK, true,
                childList);
        mInputSession.notifyBroadcastInfoResponse(response);
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mBroadcastInfoResponseCount > 1);

        response = (DsmccResponse) mSession.mBroadcastInfoResponse;
        assertThat(mSession.mBroadcastInfoResponseCount).isEqualTo(2);
        assertThat(response.getBiopMessageType()).isEqualTo(
                DsmccResponse.BIOP_MESSAGE_TYPE_SERVICE_GATEWAY);
        assertNotNull(response.getChildList());

        response = new DsmccResponse(3, 3, BroadcastInfoResponse.RESPONSE_RESULT_OK, eventIds,
                eventNames);
        mInputSession.notifyBroadcastInfoResponse(response);
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mBroadcastInfoResponseCount > 2);

        response = (DsmccResponse) mSession.mBroadcastInfoResponse;
        assertThat(mSession.mBroadcastInfoResponseCount).isEqualTo(3);
        assertThat(response.getBiopMessageType()).isEqualTo(DsmccResponse.BIOP_MESSAGE_TYPE_STREAM);
        assertNotNull(response.getStreamEventIds());
        assertNotNull(response.getStreamEventNames());

        fd.close();
        tmpFile.delete();
    }

    @Test
    public void testPesResponse() throws Throwable {
        linkTvView();

        PesResponse response = new PesResponse(4, 44, BroadcastInfoResponse.RESPONSE_RESULT_OK,
                "testShardFilterToken");
        mInputSession.notifyBroadcastInfoResponse(response);
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mBroadcastInfoResponseCount > 0);

        response = (PesResponse) mSession.mBroadcastInfoResponse;
        assertThat(mSession.mBroadcastInfoResponseCount).isEqualTo(1);
        assertThat(response.getType()).isEqualTo(TvInputManager.BROADCAST_INFO_TYPE_PES);
        assertThat(response.getRequestId()).isEqualTo(4);
        assertThat(response.getSequence()).isEqualTo(44);
        assertThat(response.getResponseResult()).isEqualTo(
                BroadcastInfoResponse.RESPONSE_RESULT_OK);
        assertThat(response.getSharedFilterToken()).isEqualTo("testShardFilterToken");
    }

    @Test
    public void testSectionResponse() throws Throwable {
        linkTvView();

        final Bundle bundle = createTestBundle();
        SectionResponse response = new SectionResponse(5, 55,
                BroadcastInfoResponse.RESPONSE_RESULT_OK, 555, 5555, bundle);
        mInputSession.notifyBroadcastInfoResponse(response);
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mBroadcastInfoResponseCount > 0);

        response = (SectionResponse) mSession.mBroadcastInfoResponse;
        assertThat(mSession.mBroadcastInfoResponseCount).isEqualTo(1);
        assertThat(response.getType()).isEqualTo(TvInputManager.BROADCAST_INFO_TYPE_SECTION);
        assertThat(response.getRequestId()).isEqualTo(5);
        assertThat(response.getSequence()).isEqualTo(55);
        assertThat(response.getResponseResult()).isEqualTo(
                BroadcastInfoResponse.RESPONSE_RESULT_OK);
        assertThat(response.getSessionId()).isEqualTo(555);
        assertThat(response.getVersion()).isEqualTo(5555);
        assertBundlesAreEqual(response.getSessionData(), bundle);
    }

    @Test
    public void testStreamEventResponse() throws Throwable {
        linkTvView();

        final byte[] data = new byte[] {1, 2, 3};
        StreamEventResponse response = new StreamEventResponse(6, 66,
                BroadcastInfoResponse.RESPONSE_RESULT_OK, 666, 6666, data);
        mInputSession.notifyBroadcastInfoResponse(response);
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mBroadcastInfoResponseCount > 0);

        response = (StreamEventResponse) mSession.mBroadcastInfoResponse;
        assertThat(mSession.mBroadcastInfoResponseCount).isEqualTo(1);
        assertThat(response.getType()).isEqualTo(TvInputManager.BROADCAST_INFO_STREAM_EVENT);
        assertThat(response.getRequestId()).isEqualTo(6);
        assertThat(response.getSequence()).isEqualTo(66);
        assertThat(response.getResponseResult()).isEqualTo(
                BroadcastInfoResponse.RESPONSE_RESULT_OK);
        assertThat(response.getEventId()).isEqualTo(666);
        assertThat(response.getNptMillis()).isEqualTo(6666);
        assertNotNull(response.getData());
    }

    @Test
    public void testTableResponse() throws Throwable {
        linkTvView();

        final Uri uri = createTestUri();
        TableResponse response = new TableResponse(7, 77, BroadcastInfoResponse.RESPONSE_RESULT_OK,
                uri, 777, 7777);
        mInputSession.notifyBroadcastInfoResponse(response);
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mBroadcastInfoResponseCount > 0);

        response = (TableResponse) mSession.mBroadcastInfoResponse;
        assertThat(mSession.mBroadcastInfoResponseCount).isEqualTo(1);
        assertThat(response.getType()).isEqualTo(TvInputManager.BROADCAST_INFO_TYPE_TABLE);
        assertThat(response.getRequestId()).isEqualTo(7);
        assertThat(response.getSequence()).isEqualTo(77);
        assertThat(response.getResponseResult()).isEqualTo(
                BroadcastInfoResponse.RESPONSE_RESULT_OK);
        assertThat(response.getTableUri()).isEqualTo(uri);
        assertThat(response.getVersion()).isEqualTo(777);
        assertThat(response.getSize()).isEqualTo(7777);
    }

    @Test
    public void testTableResponseWithByteArray() throws Throwable {
        linkTvView();

        byte[] bytes = new byte[] {-1, 22, 54};
        TableResponse responseSent = new TableResponse
                .Builder(23, 42, BroadcastInfoResponse.RESPONSE_RESULT_OK, 675, 3)
                .setTableByteArray(bytes)
                .build();
        mInputSession.notifyBroadcastInfoResponse(responseSent);
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mBroadcastInfoResponseCount > 0);

        TableResponse responseReceived = (TableResponse) mSession.mBroadcastInfoResponse;
        assertThat(mSession.mBroadcastInfoResponseCount).isEqualTo(1);
        assertThat(responseReceived.getType()).isEqualTo(responseSent.getType());
        assertThat(responseReceived.getRequestId()).isEqualTo(responseSent.getRequestId());
        assertThat(responseReceived.getSequence()).isEqualTo(responseSent.getSequence());
        assertThat(responseReceived.getResponseResult())
                .isEqualTo(responseSent.getResponseResult());
        assertThat(responseReceived.getVersion()).isEqualTo(responseSent.getVersion());
        assertThat(responseReceived.getSize()).isEqualTo(responseSent.getSize());
        assertArrayEquals(responseSent.getTableByteArray(), responseReceived.getTableByteArray());
    }

    @Test
    public void testTableResponseWithSharedMemory() throws Throwable {
        linkTvView();

        SharedMemory sm = SharedMemory.create("test", 5);
        ByteBuffer byteBuffer = sm.mapReadWrite();
        byte[] bytes = new byte[] {-3, -67, 0, 98, 23};
        byteBuffer.put(bytes);
        byteBuffer.flip();
        SharedMemory.unmap(byteBuffer);

        TableResponse responseSent = new TableResponse
                .Builder(23, 42, BroadcastInfoResponse.RESPONSE_RESULT_OK, 675, 3)
                .setTableSharedMemory(sm)
                .build();
        mInputSession.notifyBroadcastInfoResponse(responseSent);
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mBroadcastInfoResponseCount > 0);

        TableResponse responseReceived = (TableResponse) mSession.mBroadcastInfoResponse;
        assertThat(mSession.mBroadcastInfoResponseCount).isEqualTo(1);
        assertThat(responseReceived.getType()).isEqualTo(responseSent.getType());
        assertThat(responseReceived.getRequestId()).isEqualTo(responseSent.getRequestId());
        assertThat(responseReceived.getSequence()).isEqualTo(responseSent.getSequence());
        assertThat(responseReceived.getResponseResult())
                .isEqualTo(responseSent.getResponseResult());
        assertThat(responseReceived.getVersion()).isEqualTo(responseSent.getVersion());
        assertThat(responseReceived.getSize()).isEqualTo(responseSent.getSize());

        assertSharedMemoryDataEquals(responseReceived.getTableSharedMemory(),
                responseReceived.getTableSharedMemory());
    }

    @Test
    public void testTableResponseWithUri() throws Throwable {
        linkTvView();

        Uri testUri = createTestUri();
        TableResponse responseSent = new TableResponse
                .Builder(838, 52, BroadcastInfoResponse.RESPONSE_RESULT_OK, 7543, 232)
                .setTableUri(testUri)
                .build();
        mInputSession.notifyBroadcastInfoResponse(responseSent);
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mBroadcastInfoResponseCount > 0);

        TableResponse responseReceived = (TableResponse) mSession.mBroadcastInfoResponse;
        assertThat(mSession.mBroadcastInfoResponseCount).isEqualTo(1);
        assertThat(responseReceived.getType()).isEqualTo(responseSent.getType());
        assertThat(responseReceived.getRequestId()).isEqualTo(responseSent.getRequestId());
        assertThat(responseReceived.getSequence()).isEqualTo(responseSent.getSequence());
        assertThat(responseReceived.getResponseResult())
                .isEqualTo(responseSent.getResponseResult());
        assertThat(responseReceived.getVersion()).isEqualTo(responseSent.getVersion());
        assertThat(responseReceived.getSize()).isEqualTo(responseSent.getSize());
        assertThat(responseSent.getTableUri()).isEqualTo(responseReceived.getTableUri());
    }

    @Test
    public void testTimelineResponse() throws Throwable {
        linkTvView();

        TimelineResponse response = new TimelineResponse(8, 88,
                BroadcastInfoResponse.RESPONSE_RESULT_OK, "test_selector", 1, 10, 100, 1000);
        mInputSession.notifyBroadcastInfoResponse(response);
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mBroadcastInfoResponseCount > 0);

        response = (TimelineResponse) mSession.mBroadcastInfoResponse;
        assertThat(mSession.mBroadcastInfoResponseCount).isEqualTo(1);
        assertThat(response.getType()).isEqualTo(TvInputManager.BROADCAST_INFO_TYPE_TIMELINE);
        assertThat(response.getRequestId()).isEqualTo(8);
        assertThat(response.getSequence()).isEqualTo(88);
        assertThat(response.getResponseResult()).isEqualTo(
                BroadcastInfoResponse.RESPONSE_RESULT_OK);
        assertThat(response.getSelector().toString()).isEqualTo("test_selector");
        assertThat(response.getUnitsPerTick()).isEqualTo(1);
        assertThat(response.getUnitsPerSecond()).isEqualTo(10);
        assertThat(response.getWallClock()).isEqualTo(100);
        assertThat(response.getTicks()).isEqualTo(1000);
    }

    @Test
    public void testViewOnAttachedToWindow() {
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mTvIAppView.onAttachedToWindow();
            }
        });

    }

    @Test
    public void testViewOnDetachedFromWindow() {
        mTvIAppView.onDetachedFromWindow();
    }

    @Test
    public void testViewOnLayout() {
        int left = 1, top = 10, right = 5, bottom = 20;
        mTvIAppView.onLayout(true, left, top, right, bottom);
    }

    @Test
    public void testViewOnMeasure() {
        int widthMeasureSpec = 5, heightMeasureSpec = 10;
        mTvIAppView.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Test
    public void testViewOnVisibilityChanged() {
        mTvIAppView.onVisibilityChanged(mTvIAppView, View.VISIBLE);
    }

    @Test
    public void testOnUnhandledInputEvent() {
        final int keyCode = KeyEvent.KEYCODE_Q;
        final KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, keyCode);
        mTvIAppView.onUnhandledInputEvent(event);
    }

    public static void assertKeyEventEquals(KeyEvent actual, KeyEvent expected) {
        if (expected != null && actual != null) {
            assertThat(actual.getDownTime()).isEqualTo(expected.getDownTime());
            assertThat(actual.getEventTime()).isEqualTo(expected.getEventTime());
            assertThat(actual.getAction()).isEqualTo(expected.getAction());
            assertThat(actual.getKeyCode()).isEqualTo(expected.getKeyCode());
            assertThat(actual.getRepeatCount()).isEqualTo(expected.getRepeatCount());
            assertThat(actual.getMetaState()).isEqualTo(expected.getMetaState());
            assertThat(actual.getDeviceId()).isEqualTo(expected.getDeviceId());
            assertThat(actual.getScanCode()).isEqualTo(expected.getScanCode());
            assertThat(actual.getFlags()).isEqualTo(expected.getFlags());
            assertThat(actual.getSource()).isEqualTo(expected.getSource());
            assertThat(actual.getCharacters()).isEqualTo(expected.getCharacters());
        } else {
            assertThat(actual).isEqualTo(expected);
        }
    }

    private static void assertBundlesAreEqual(Bundle actual, Bundle expected) {
        if (expected != null && actual != null) {
            assertThat(actual.keySet()).isEqualTo(expected.keySet());
            for (String key : expected.keySet()) {
                assertThat(actual.get(key)).isEqualTo(expected.get(key));
            }
        } else {
            assertThat(actual).isEqualTo(expected);
        }
    }

    private static void assertSharedMemoryDataEquals(SharedMemory actual, SharedMemory expected)
            throws Exception {
        if (expected != null && actual != null) {
            Assert.assertArrayEquals(getSharedMemoryData(actual), getSharedMemoryData(expected));
        } else {
            assertThat(actual).isEqualTo(expected);
        }
    }

    private static byte[] getSharedMemoryData(SharedMemory sm) throws Exception {
        ByteBuffer byteBuffer = sm.mapReadOnly();
        byte[] data = new byte[byteBuffer.remaining()];
        byteBuffer.get(data);
        SharedMemory.unmap(byteBuffer);
        return data;
    }
}
