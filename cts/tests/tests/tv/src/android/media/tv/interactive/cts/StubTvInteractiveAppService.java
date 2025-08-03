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

import android.content.Context;
import android.graphics.Rect;
import android.media.PlaybackParams;
import android.media.tv.AdBuffer;
import android.media.tv.AdRequest;
import android.media.tv.AdResponse;
import android.media.tv.BroadcastInfoRequest;
import android.media.tv.BroadcastInfoResponse;
import android.media.tv.TvContentRating;
import android.media.tv.TvRecordingInfo;
import android.media.tv.TvTrackInfo;
import android.media.tv.interactive.AppLinkInfo;
import android.media.tv.interactive.TvInteractiveAppManager;
import android.media.tv.interactive.TvInteractiveAppService;
import android.net.Uri;
import android.net.http.SslCertificate;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;

import androidx.annotation.NonNull;

import java.util.List;

/**
 * Stub implementation of (@link android.media.tv.interactive.TvInteractiveAppService}.
 */
public class StubTvInteractiveAppService extends TvInteractiveAppService {

    public static StubSessionImpl sSession;
    public static int sType;
    public static Bundle sAppLinkCommand = null;
    public static AppLinkInfo sAppLinkInfo = null;

    @Override
    public Session onCreateSession(String iAppServiceId, int type) {
        sSession = new StubSessionImpl(this);
        return sSession;
    }

    @Override
    public void onAppLinkCommand(Bundle command) {
        super.onAppLinkCommand(command);
        sAppLinkCommand = command;
    }

    @Override
    public void onRegisterAppLinkInfo(AppLinkInfo bundle) {
        super.onRegisterAppLinkInfo(bundle);
        sAppLinkInfo = bundle;
    }

    @Override
    public void onUnregisterAppLinkInfo(AppLinkInfo bundle) {
        super.onUnregisterAppLinkInfo(bundle);
        sAppLinkInfo = null;
    }

    public static class StubSessionImpl extends Session {
        public int mSetSurfaceCount;
        public int mSurfaceChangedCount;
        public int mStartInteractiveAppCount;
        public int mStopInteractiveAppCount;
        public int mKeyDownCount;
        public int mKeyUpCount;
        public int mKeyMultipleCount;
        public int mVideoAvailableCount;
        public int mTunedCount;
        public int mCreateBiIAppCount;
        public int mDestroyBiIAppCount;
        public int mAdResponseCount;
        public int mAdBufferConsumedCount;
        public int mBroadcastInfoResponseCount;
        public int mSigningResultCount;
        public int mErrorCount;
        public int mRecordingStartedCount;
        public int mRecordingStoppedCount;
        public int mRecordingScheduledCount;
        public int mPlaybackParamCount;
        public int mTimeShiftModeCount;
        public int mAvailableSpeedsCount;
        public int mTimeShiftStatusCount;
        public int mTimeShiftStartPositionCount;
        public int mTimeShiftCurrentPositionCount;
        public int mTvMessageCount;
        public int mSendTvRecordingInfoCount;
        public int mSendTvRecordingInfoListCount;
        public int mCurrentVideoBoundsCount;
        public int mRecordingTunedCount;
        public int mRecordingConnectionFailedCount;
        public int mRecordingDisconnected;
        public int mRecordingErrorCount;
        public int mSendSelectedTrackInfoCount;
        public int mTrackSelectedCount;
        public int mSendCertificateCount;
        public int mVideoFreezeUpdatedCount;

        public Integer mKeyDownCode;
        public Integer mKeyUpCode;
        public Integer mKeyMultipleCode;
        public KeyEvent mKeyDownEvent;
        public KeyEvent mKeyUpEvent;
        public KeyEvent mKeyMultipleEvent;
        public Uri mTunedUri;
        public Uri mCreateBiIAppUri;
        public Bundle mCreateBiIAppParams;
        public String mDestroyBiIAppId;
        public AdResponse mAdResponse;
        public BroadcastInfoResponse mBroadcastInfoResponse;
        public String mRecordingId;
        public String mRequestId;
        public PlaybackParams mPlaybackParams;
        public Integer mTimeShiftMode;
        public float[] mAvailableSpeeds;
        public Integer mTimeShiftStatus;
        public String mInputId;
        public Long mTimeShiftStartPosition;
        public Long mTimeShiftCurrentPosition;
        public Integer mTvMessageType;
        public Bundle mTvMessageData;
        public AdBuffer mAdBuffer;
        public TvRecordingInfo mTvRecordingInfo;
        public List<TvRecordingInfo> mTvRecordingInfoList;
        public Rect mCurrentVideoBounds;
        public Integer mRecordingError;
        public List<TvTrackInfo> mSelectedTrackInfoList;
        public String mSelectedTrackId;
        public SslCertificate mCertificate;
        public String mHost;
        public Integer mPort;
        public Boolean mVideoFrozen;

        StubSessionImpl(Context context) {
            super(context);
        }

        public void resetValues() {
            mSetSurfaceCount = 0;
            mSurfaceChangedCount = 0;
            mStartInteractiveAppCount = 0;
            mStopInteractiveAppCount = 0;
            mKeyDownCount = 0;
            mKeyUpCount = 0;
            mKeyMultipleCount = 0;
            mVideoAvailableCount = 0;
            mTunedCount = 0;
            mCreateBiIAppCount = 0;
            mDestroyBiIAppCount = 0;
            mAdResponseCount = 0;
            mBroadcastInfoResponseCount = 0;
            mSigningResultCount = 0;
            mErrorCount = 0;
            mRecordingStartedCount = 0;
            mRecordingStoppedCount = 0;
            mRecordingScheduledCount = 0;
            mPlaybackParamCount = 0;
            mTimeShiftModeCount = 0;
            mAvailableSpeedsCount = 0;
            mTimeShiftStatusCount = 0;
            mTimeShiftCurrentPositionCount = 0;
            mTimeShiftStartPositionCount = 0;
            mTvMessageCount = 0;
            mAdBufferConsumedCount = 0;
            mSendTvRecordingInfoCount = 0;
            mSendTvRecordingInfoListCount = 0;
            mCurrentVideoBoundsCount = 0;
            mRecordingTunedCount = 0;
            mRecordingConnectionFailedCount = 0;
            mRecordingDisconnected = 0;
            mRecordingErrorCount = 0;
            mSendSelectedTrackInfoCount = 0;
            mTrackSelectedCount = 0;
            mSendCertificateCount = 0;
            mVideoFreezeUpdatedCount = 0;

            mKeyDownCode = null;
            mKeyUpCode = null;
            mKeyMultipleCode = null;
            mKeyDownEvent = null;
            mKeyUpEvent = null;
            mKeyMultipleEvent = null;
            mTunedUri = null;
            mCreateBiIAppUri = null;
            mCreateBiIAppParams = null;
            mDestroyBiIAppId = null;
            mAdResponse = null;
            mBroadcastInfoResponse = null;
            mRecordingId = null;
            mRequestId = null;
            mPlaybackParams = null;
            mTimeShiftMode = null;
            mAvailableSpeeds = new float[]{};
            mTimeShiftStatus = null;
            mInputId = null;
            mTimeShiftCurrentPosition = null;
            mTimeShiftStartPosition = null;
            mTvMessageData = null;
            mTvMessageType = null;
            mAdBuffer = null;
            mTvRecordingInfo = null;
            mTvRecordingInfoList = null;
            mCurrentVideoBounds = null;
            mRecordingError = null;
            mSelectedTrackInfoList = null;
            mSelectedTrackId = null;
            mCertificate = null;
            mHost = null;
            mPort = null;
            mVideoFrozen = null;
        }

        @Override
        public void layoutSurface(int left, int top, int right, int bottom) {
            super.layoutSurface(left, top, right, bottom);
        }

        @Override
        public void notifySessionStateChanged(int state, int err) {
            super.notifySessionStateChanged(state, err);
        }

        @Override
        public void removeBroadcastInfo(int requestId) {
            super.removeBroadcastInfo(requestId);
        }

        @Override
        public void requestAd(AdRequest request) {
            super.requestAd(request);
        }

        @Override
        public void requestBroadcastInfo(BroadcastInfoRequest request) {
            super.requestBroadcastInfo(request);
        }

        @Override
        public void requestCurrentChannelLcn() {
            super.requestCurrentChannelLcn();
        }

        @Override
        public void requestCurrentChannelUri() {
            super.requestCurrentChannelUri();
        }

        @Override
        public void requestCurrentTvInputId() {
            super.requestCurrentTvInputId();
        }

        @Override
        public void requestStreamVolume() {
            super.requestStreamVolume();
        }

        @Override
        public void requestTrackInfoList() {
            super.requestTrackInfoList();
        }

        @Override
        public void requestScheduleRecording(String requestId, String inputId, Uri channelUri,
                Uri programUri, Bundle params) {
            super.requestScheduleRecording(requestId, inputId, channelUri, programUri, params);
        }

        @Override
        public void requestScheduleRecording(String requestId, String inputId, Uri channelUri,
                long startTime, long duration, int repeatedDays, Bundle params) {
            super.requestScheduleRecording(
                    requestId, inputId, channelUri, startTime, duration, repeatedDays, params);
        }

        @Override
        public void requestAvailableSpeeds() {
            super.requestAvailableSpeeds();
        }

        @Override
        public void requestTimeShiftMode() {
            super.requestTimeShiftMode();
        }

        @Override
        public void sendPlaybackCommandRequest(String cmdType, Bundle parameters) {
            super.sendPlaybackCommandRequest(cmdType, parameters);
        }

        @Override
        public void sendTimeShiftCommandRequest(String cmdType, Bundle parameters) {
            super.sendTimeShiftCommandRequest(cmdType, parameters);
        }

        @Override
        public void setMediaViewEnabled(boolean enable) {
            super.setMediaViewEnabled(enable);
        }

        @Override
        public void setVideoBounds(Rect rect) {
            super.setVideoBounds(rect);
        }

        @Override
        public void onStartInteractiveApp() {
            super.onStartInteractiveApp();
            mStartInteractiveAppCount++;
            notifySessionStateChanged(
                    TvInteractiveAppManager.INTERACTIVE_APP_STATE_RUNNING,
                    TvInteractiveAppManager.ERROR_NONE);
        }

        @Override
        public void onStopInteractiveApp() {
            super.onStopInteractiveApp();
            mStopInteractiveAppCount++;
        }

        @Override
        public void onRelease() {
        }

        @Override
        public boolean onSetSurface(Surface surface) {
            mSetSurfaceCount++;
            return false;
        }

        @Override
        public void onSurfaceChanged(int format, int width, int height) {
            super.onSurfaceChanged(format, width, height);
            mSurfaceChangedCount++;
        }

        @Override
        public boolean onKeyDown(int keyCode, KeyEvent event) {
            super.onKeyDown(keyCode, event);
            mKeyDownCount++;
            mKeyDownCode = keyCode;
            mKeyDownEvent = event;
            return false;
        }

        @Override
        public boolean onKeyLongPress(int keyCode, KeyEvent event) {
            super.onKeyLongPress(keyCode, event);
            return false;
        }

        @Override
        public boolean onKeyUp(int keyCode, KeyEvent event) {
            super.onKeyUp(keyCode, event);
            mKeyUpCount++;
            mKeyUpCode = keyCode;
            mKeyUpEvent = event;
            return false;
        }

        @Override
        public boolean onKeyMultiple(int keyCode, int count, KeyEvent event) {
            super.onKeyMultiple(keyCode, count, event);
            mKeyMultipleCount++;
            mKeyMultipleCode = keyCode;
            mKeyMultipleEvent = event;
            return false;
        }

        @Override
        public void onCreateBiInteractiveAppRequest(Uri biIAppUri, Bundle params) {
            super.onCreateBiInteractiveAppRequest(biIAppUri, params);
            mCreateBiIAppCount++;
            mCreateBiIAppUri = biIAppUri;
            mCreateBiIAppParams = params;
            notifyBiInteractiveAppCreated(biIAppUri, "biIAppId");
        }

        @Override
        public void onDestroyBiInteractiveAppRequest(String biIAppId) {
            super.onDestroyBiInteractiveAppRequest(biIAppId);
            mDestroyBiIAppCount++;
            mDestroyBiIAppId = biIAppId;
        }

        @Override
        public void onTuned(Uri uri) {
            super.onTuned(uri);
            mTunedCount++;
            mTunedUri = uri;
        }

        @Override
        public void onVideoAvailable() {
            super.onVideoAvailable();
            mVideoAvailableCount++;
        }

        @Override
        public void onAdResponse(AdResponse response) {
            super.onAdResponse(response);
            mAdResponseCount++;
            mAdResponse = response;
        }

        @Override
        public void onBroadcastInfoResponse(BroadcastInfoResponse response) {
            super.onBroadcastInfoResponse(response);
            mBroadcastInfoResponseCount++;
            mBroadcastInfoResponse = response;
        }

        @Override
        public void onContentAllowed() {
            super.onContentAllowed();
        }

        @Override
        public void onContentBlocked(TvContentRating rating) {
            super.onContentBlocked(rating);
        }

        @Override
        public View onCreateMediaView() {
            super.onCreateMediaView();
            return null;
        }

        @Override
        public void onCurrentChannelLcn(int lcn) {
            super.onCurrentChannelLcn(lcn);
        }

        @Override
        public void onCurrentChannelUri(Uri uri) {
            super.onCurrentChannelUri(uri);
        }

        @Override
        public void onCurrentTvInputId(String id) {
            super.onCurrentTvInputId(id);
        }

        @Override
        public void onCurrentVideoBounds(Rect rect) {
            super.onCurrentVideoBounds(rect);
            mCurrentVideoBoundsCount++;
            mCurrentVideoBounds = rect;
        }

        @Override
        public boolean onGenericMotionEvent(MotionEvent event) {
            super.onGenericMotionEvent(event);
            return false;
        }

        @Override
        public void onMediaViewSizeChanged(int w, int h) {
            super.onMediaViewSizeChanged(w, h);
        }

        @Override
        public void onResetInteractiveApp() {
            super.onResetInteractiveApp();
        }

        @Override
        public void onSetTeletextAppEnabled(boolean enable) {
            super.onSetTeletextAppEnabled(enable);
        }

        @Override
        public void onSignalStrength(int strength) {
            super.onSignalStrength(strength);
        }

        @Override
        public void onStreamVolume(float v) {
            super.onStreamVolume(v);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            super.onTouchEvent(event);
            return false;
        }

        @Override
        public void onTrackInfoList(List<TvTrackInfo> infos) {
            super.onTrackInfoList(infos);
        }

        @Override
        public void onTrackSelected(int type, String id) {
            super.onTrackSelected(type, id);
            mTrackSelectedCount++;
            mSelectedTrackId = id;
        }

        @Override
        public boolean onTrackballEvent(MotionEvent event) {
            super.onTrackballEvent(event);
            return false;
        }

        @Override
        public void onTracksChanged(List<TvTrackInfo> infos) {
            super.onTracksChanged(infos);
        }

        @Override
        public void onVideoUnavailable(int reason) {
            super.onVideoUnavailable(reason);
        }

        @Override
        public void onSigningResult(String signingId, byte[] result) {
            super.onSigningResult(signingId, result);
            mSigningResultCount++;
        }

        @Override
        public void onError(String errMsg, Bundle params) {
            super.onError(errMsg, params);
            mErrorCount++;
        }

        @Override
        public void onRecordingStarted(String recordingId, String requestId) {
            super.onRecordingStarted(recordingId, requestId);
            mRecordingStartedCount++;
            mRecordingId = recordingId;
            mRequestId = requestId;
        }

        @Override
        public void onRecordingStopped(String recordingId) {
            super.onRecordingStopped(recordingId);
            mRecordingStoppedCount++;
            mRecordingId = recordingId;
        }

        @Override
        public void onRecordingScheduled(String recordingId, String requestId) {
            super.onRecordingScheduled(recordingId, requestId);
            mRecordingScheduledCount++;
            mRecordingId = recordingId;
            mRequestId = requestId;
        }

        @Override
        public void onTimeShiftPlaybackParams(PlaybackParams params) {
            super.onTimeShiftPlaybackParams(params);
            mPlaybackParamCount++;
            mPlaybackParams = params;
        }

        @Override
        public void onTimeShiftMode(int mode) {
            super.onTimeShiftMode(mode);
            mTimeShiftMode = mode;
            mTimeShiftModeCount++;
        }

        @Override
        public void onAvailableSpeeds(float[] availableSpeeds) {
            super.onAvailableSpeeds(availableSpeeds);
            mAvailableSpeedsCount++;
            mAvailableSpeeds = availableSpeeds;
        }

        @Override
        public void onTimeShiftStartPositionChanged(String inputId, long timeMs) {
            super.onTimeShiftStartPositionChanged(inputId, timeMs);
            mTimeShiftStartPosition = timeMs;
            mInputId = inputId;
            mTimeShiftStartPositionCount++;
        }

        @Override
        public void onTimeShiftCurrentPositionChanged(String inputId, long timeMs) {
            super.onTimeShiftCurrentPositionChanged(inputId, timeMs);
            mTimeShiftCurrentPosition = timeMs;
            mTimeShiftCurrentPositionCount++;
            mInputId = inputId;
        }

        @Override
        public void onTimeShiftStatusChanged(String inputId, int status) {
            super.onTimeShiftStatusChanged(inputId, status);
            mTimeShiftStatusCount++;
            mTimeShiftStatus = status;
            mInputId = inputId;
        }

        @Override
        public void onTvMessage(int type, Bundle data) {
            super.onTvMessage(type, data);
            mTvMessageCount++;
            mTvMessageType = type;
            mTvMessageData = data;
        }

        @Override
        public void onAdBufferConsumed(AdBuffer adBuffer) {
            super.onAdBufferConsumed(adBuffer);
            mAdBufferConsumedCount++;
            mAdBuffer = adBuffer;
        }

        @Override
        public void onTvRecordingInfo(TvRecordingInfo tvRecordingInfo) {
            super.onTvRecordingInfo(tvRecordingInfo);
            mSendTvRecordingInfoCount++;
            mTvRecordingInfo = tvRecordingInfo;
        }

        @Override
        public void onTvRecordingInfoList(List<TvRecordingInfo> recordingInfoList) {
            super.onTvRecordingInfoList(recordingInfoList);
            mSendTvRecordingInfoListCount++;
            mTvRecordingInfoList = recordingInfoList;
        }

        @Override
        public void onRecordingTuned(String recordingId, Uri channelUri) {
            mRecordingTunedCount++;
            mRecordingId = recordingId;
            mTunedUri = channelUri;
        }

        @Override
        public void onRecordingConnectionFailed(String recordingId, String inputId) {
            super.onRecordingConnectionFailed(recordingId, inputId);
            mRecordingConnectionFailedCount++;
            mRecordingId = recordingId;
            mInputId = inputId;
        }

        @Override
        public void onRecordingDisconnected(String recordingId, String inputId) {
            super.onRecordingDisconnected(recordingId, inputId);
            mRecordingDisconnected++;
            mRecordingId = recordingId;
            mInputId = inputId;
        }

        @Override
        public void onRecordingError(String recordingId, int err) {
            super.onRecordingError(recordingId, err);
            mRecordingErrorCount++;
            mRecordingId = recordingId;
            mRecordingError = err;
        }

        @Override
        public void onSelectedTrackInfo(@NonNull List<TvTrackInfo> trackInfoList) {
            super.onSelectedTrackInfo(trackInfoList);
            mSelectedTrackInfoList = trackInfoList;
            mSendSelectedTrackInfoCount++;
        }

        @Override
        public void onCertificate(String host, int port, SslCertificate certificate) {
            super.onCertificate(host, port, certificate);
            mSendCertificateCount++;
            mHost = host;
            mPort = port;
            mCertificate = certificate;
        }

        @Override
        public void onVideoFreezeUpdated(boolean isFrozen) {
            super.onVideoFreezeUpdated(isFrozen);
            mVideoFreezeUpdatedCount++;
            mVideoFrozen = isFrozen;
        }
    }
}
