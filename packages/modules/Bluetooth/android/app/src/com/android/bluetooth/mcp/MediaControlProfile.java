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

package com.android.bluetooth.mcp;

import static java.util.Map.entry;

import android.annotation.NonNull;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothLeAudio;
import android.bluetooth.BluetoothUuid;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.media.session.PlaybackState;
import android.os.Looper;
import android.os.ParcelUuid;
import android.os.SystemClock;
import android.util.Log;

import com.android.bluetooth.BluetoothEventLogger;
import com.android.bluetooth.Utils;
import com.android.bluetooth.audio_util.MediaData;
import com.android.bluetooth.audio_util.MediaPlayerList;
import com.android.bluetooth.audio_util.MediaPlayerWrapper;
import com.android.bluetooth.le_audio.ContentControlIdKeeper;
import com.android.internal.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/*
 * Generic Media Control Profile hooks into the currently active media player using the MediaSession
 * and MediaController wrapper helpers. It registers a single GMCS instance and connects the two
 * pieces together. It exposes current media player state through the registered MCS GATT service
 * instance and reacts on bluetooth peer device commands with calls to the proper media player's
 * controller. GMCS should be able to control any media player which works nicely with the
 * Android's Media Session framework.
 *
 * Implemented according to Media Control Service v1.0 specification.
 */
public class MediaControlProfile implements MediaControlServiceCallbacks {
    private static final String TAG = "MediaControlProfile";
    private static final boolean DBG = Log.isLoggable(TAG, Log.DEBUG);
    private final Context mContext;

    private static final int LOG_NB_EVENTS = 100;
    private final BluetoothEventLogger mEventLogger;

    // Media players data
    private MediaPlayerList mMediaPlayerList;
    private MediaData mCurrentData;

    private McpService mMcpService;
    // MCP service instance
    private MediaControlGattServiceInterface mGMcsService;

    // MCP Service requests for stete fields needed to fill the characteristic values
    private List<PlayerStateField> mPendingStateRequest;

    private MediaPlayerWrapper mLastActivePlayer = null;

    static MediaPlayerList sMediaPlayerListForTesting = null;
    static void setsMediaPlayerListForTesting(MediaPlayerList mediaPlayerList) {
        sMediaPlayerListForTesting = mediaPlayerList;
    }

    // Same base feature set as the player item features defined in `avrcp/get_foder_items.cc`
    private static final long BASE_PLAYER_ACTION_SET =
            PlaybackState.ACTION_PLAY
                    | PlaybackState.ACTION_STOP
                    | PlaybackState.ACTION_PAUSE
                    | PlaybackState.ACTION_REWIND
                    | PlaybackState.ACTION_FAST_FORWARD
                    | PlaybackState.ACTION_SKIP_TO_NEXT
                    | PlaybackState.ACTION_SKIP_TO_PREVIOUS;

    @VisibleForTesting
    long getCurrentPlayerSupportedActions() {
        // Notice: Stay compatible with the currently hard-codded ACRVP supported player features
        if (mCurrentData != null && mCurrentData.state != null) {
            return Long.valueOf(mCurrentData.state.getActions() | BASE_PLAYER_ACTION_SET);
        }
        return Long.valueOf(BASE_PLAYER_ACTION_SET);
    }

    public class ListCallback implements MediaPlayerList.MediaUpdateCallback {
        @Override
        public void run(MediaData data) {
            boolean metadata = !Objects.equals(mCurrentData.metadata, data.metadata);
            boolean state = !MediaPlayerWrapper.playstateEquals(mCurrentData.state, data.state);
            boolean queue = !Objects.equals(mCurrentData.queue, data.queue);

            if (metadata || state || queue) {
                mEventLogger.logd(
                        TAG,
                        "onMediaUpdated: track_changed="
                                + metadata
                                + " state_changed="
                                + state
                                + " queue_changed="
                                + queue);
            }

            mCurrentData = data;

            onCurrentPlayerStateUpdated(state, metadata);
            if (queue) onCurrentPlayerQueueUpdated();
            processPendingPlayerStateRequest();
        }

        @Override
        public void run(boolean availablePlayers, boolean addressedPlayers, boolean uids) {
            mEventLogger.logd(
                    TAG,
                    "onFolderUpdated: available_players= "
                            + availablePlayers
                            + " addressedPlayers="
                            + addressedPlayers
                            + " uids="
                            + uids);
        }
    }

    @VisibleForTesting
    long getCurrentTrackDuration() {
        if (mCurrentData != null && mCurrentData.metadata != null) {
            return Long.valueOf(mCurrentData.metadata.duration);
        }
        return MediaControlGattServiceInterface.TRACK_DURATION_UNAVAILABLE;
    }

    private void onCurrentPlayerQueueUpdated() {
        if (DBG) Log.d(TAG, "onCurrentPlayerQueueUpdated: not implemented");

        /* TODO: Implement once we have the Object Transfer Service */
        if (mCurrentData.queue == null) return;
    }

    @VisibleForTesting
    void onCurrentPlayerStateUpdated(boolean stateChanged, boolean metadataChanged) {
        Map<PlayerStateField, Object> state_map = new HashMap<>();

        if (mMediaPlayerList.getActivePlayer() != mLastActivePlayer) {
            String playerName = getCurrentPlayerName();
            if (playerName != null) {
                state_map.put(PlayerStateField.PLAYER_NAME, playerName);
            }
        }

        if (stateChanged) {
            if (mCurrentData.state != null) {
                MediaState playback_state = playerState2McsState(mCurrentData.state.getState());
                state_map.put(PlayerStateField.PLAYBACK_STATE, playback_state);

                if ((mCurrentData.state.getActions() & BASE_PLAYER_ACTION_SET)
                        != BASE_PLAYER_ACTION_SET) {
                    mEventLogger.loge(
                            TAG,
                            "onCurrentPlayerStateUpdated: base actions not supported, player"
                                + " actions= "
                                    + Long.toHexString(mCurrentData.state.getActions())
                                    + ", expected at least= "
                                    + Long.toHexString(BASE_PLAYER_ACTION_SET));
                }

                int opcodes = playerActions2McsSupportedOpcodes(getCurrentPlayerSupportedActions());
                state_map.put(PlayerStateField.OPCODES_SUPPORTED, opcodes);

                if (playback_state != MediaState.INACTIVE) {
                    state_map.put(
                            PlayerStateField.SEEKING_SPEED, mCurrentData.state.getPlaybackSpeed());
                    state_map.put(
                            PlayerStateField.PLAYBACK_SPEED, mCurrentData.state.getPlaybackSpeed());
                    state_map.put(PlayerStateField.TRACK_POSITION,
                            getDriftCorrectedTrackPosition(mCurrentData.state));
                }

                mEventLogger.logd(
                        TAG,
                        "onCurrentPlayerStateUpdated state= "
                                + playback_state
                                + ", supported opcodes= "
                                + Request.SupportedOpcodes.toString(opcodes)
                                + ", track position= "
                                + getDriftCorrectedTrackPosition(mCurrentData.state)
                                + " ("
                                + mCurrentData.state.getPosition()
                                + ")");
            } else {
                // Just update the state and the service should set it's characteristics as required
                state_map.put(PlayerStateField.PLAYBACK_STATE, MediaState.INACTIVE);
            }
        }

        if (metadataChanged) {
            if (mCurrentData.metadata != null) {
                mEventLogger.logd(
                        TAG,
                        "onCurrentPlayerStateUpdated metadata: title= '"
                                + mCurrentData.metadata.title
                                + "', duration= "
                                + mCurrentData.metadata.duration);

                state_map.put(PlayerStateField.TRACK_DURATION,
                        mCurrentData.metadata.duration != null
                                ? Long.valueOf(mCurrentData.metadata.duration)
                                : Long.valueOf(MediaControlGattServiceInterface
                                                       .TRACK_DURATION_UNAVAILABLE));

                state_map.put(PlayerStateField.TRACK_TITLE,
                        mCurrentData.metadata.title != null ? mCurrentData.metadata.title : "");

                // Update the position if track has changed
                state_map.put(PlayerStateField.TRACK_POSITION,
                        mCurrentData.state != null
                                ? getDriftCorrectedTrackPosition(mCurrentData.state)
                                : Long.valueOf(MediaControlGattServiceInterface
                                                       .TRACK_POSITION_UNAVAILABLE));
            } else {
                state_map.put(PlayerStateField.TRACK_DURATION,
                        Long.valueOf(MediaControlGattServiceInterface.TRACK_DURATION_UNAVAILABLE));
                state_map.put(PlayerStateField.TRACK_TITLE, "");
                state_map.put(PlayerStateField.TRACK_POSITION,
                        Long.valueOf(MediaControlGattServiceInterface.TRACK_POSITION_UNAVAILABLE));
            }
        }

        // If any of these were previously requested, just clean-up the requests
        removePendingStateRequests(state_map.keySet());

        if (mGMcsService != null) {
            mGMcsService.updatePlayerState(state_map);
        }
    }

    private synchronized void removePendingStateRequests(Set<PlayerStateField> fields) {
        if (mPendingStateRequest == null) return;

        for (PlayerStateField field : fields) {
            mPendingStateRequest.remove(field);
        }

        if (mPendingStateRequest.isEmpty()) mPendingStateRequest = null;
    }

    public MediaControlProfile(@NonNull McpService mcpService) {
        Log.v(TAG, "Creating Generic Media Control Service");

        mContext = mcpService;
        mMcpService = mcpService;
        mServiceMap = new HashMap<>();

        if (sMediaPlayerListForTesting != null) {
            mMediaPlayerList = sMediaPlayerListForTesting;
        } else {
            mMediaPlayerList = new MediaPlayerList(Looper.myLooper(), mContext);
        }

        mEventLogger = new BluetoothEventLogger(LOG_NB_EVENTS, TAG + " event log");
    }

    @Override
    public void onServiceInstanceRegistered(ServiceStatus status,
            MediaControlGattServiceInterface service) {
        mEventLogger.logd(TAG, "onServiceInstanceRegistered: status= " + status);
        mGMcsService = service;
    }

    @Override
    public void onServiceInstanceUnregistered(ServiceStatus status) {
        mEventLogger.logd(TAG, "onServiceInstanceUnregistered: status= " + status);
        mGMcsService = null;
    }

    private long TrackPositionRelativeToAbsolute(long position) {
        /* MCS v1.0; Sec. 3.7.1
         * "If the value is zero or greater, then the current playing position shall be set to
         * the offset from the start of the track. If the value is less than zero, then the
         * current playing position shall be set to the offset from the end of the track and
         * the value of the Track Position characteristic shall be set to the offset from the start
         * of the track to the new playing position.
         * If the value written does not correspond to a valid track position, the server shall
         * set the Track Position characteristic to a valid value."
         */

        // Limit the possible position to valid track positions
        long track_duration = getCurrentTrackDuration();
        if (position < 0) return Math.max(0, position + track_duration);
        return Math.min(track_duration, position);
    }

    @Override
    public void onCurrentTrackObjectIdSet(long objectId) {
        // TODO: Implement once we have Object Transfer Service
    }

    @Override
    public void onNextTrackObjectIdSet(long objectId) {
        // TODO: Implement once we have Object Transfer Service
    }

    @Override
    public void onCurrentGroupObjectIdSet(long objectId) {
        // TODO: Implement once we have Object Transfer Service
    }

    @Override
    public void onPlayerStateRequest(PlayerStateField[] stateFields) {
        synchronized (this) {
            mPendingStateRequest = Stream.of(stateFields).collect(Collectors.toList());
        }
        processPendingPlayerStateRequest();
    }

    @Override
    public long onGetFeatureFlags() {
        return SUPPORTED_FEATURES;
    }

    @Override
    public long onGetCurrentTrackPosition() {
        mEventLogger.logd(TAG, "getCurrentTrackPosition");
        return getLatestTrackPosition();
    }

    @Override
    public void onTrackPositionSetRequest(long position) {
        mEventLogger.logd(TAG, "GMCS onTrackPositionSetRequest");

        if (mMediaPlayerList.getActivePlayer() == null) return;
        if ((getCurrentPlayerSupportedActions() & PlaybackState.ACTION_SEEK_TO) != 0) {
            mMediaPlayerList.getActivePlayer().seekTo(TrackPositionRelativeToAbsolute(position));
        } else {
            // player does not support seek to command, notify last known track position only
            Map<PlayerStateField, Object> state_map = new HashMap<>();
            state_map.put(PlayerStateField.TRACK_POSITION, getLatestTrackPosition());

            if (mGMcsService != null) {
              mGMcsService.updatePlayerState(state_map);
            }
        }
    }

    @Override
    public void onCurrentTrackMetadataRequest() {
        mEventLogger.logd(TAG, "GMCS onCurrentTrackMetadataRequest");
        // FIXME: Seems to be not used right now
    }

    @Override
    public void onPlayingOrderSetRequest(int order) {
        mEventLogger.logd(TAG, "GMCS onPlayingOrderSetRequest");
        // Notice: MediaPlayerWrapper does not support play order control.
        // Ignore the request for now.
    }

    @Override
    public void onPlaybackSpeedSetRequest(float speed) {
        mEventLogger.logd(TAG, "GMCS onPlaybackSpeedSetRequest");
        if (mMediaPlayerList.getActivePlayer() == null) return;
        mMediaPlayerList.getActivePlayer().setPlaybackSpeed(speed);
    }

    @Override
    public void onSetObjectIdRequest(int objField, long objectId) {
        mEventLogger.logd(TAG, "GMCS onSetObjectIdRequest");
        // TODO: Implement once we have the Object Transfer Service
    }

    @Override
    public void onSearchRequest(SearchRequest request) {
        mEventLogger.logd(TAG, "GMCS onSearchRequest");
        // TODO: Implement once we have the Object Transfer Service
    }

    @Override
    public void onMediaControlRequest(Request request) {
        mEventLogger.logd(
                TAG,
                "GMCS onMediaControlRequest: opcode= "
                        + Request.Opcodes.toString(request.getOpcode()));
        Request.Results status = Request.Results.COMMAND_CANNOT_BE_COMPLETED;

        if (mMediaPlayerList.getActivePlayer() == null && mGMcsService != null) {
            mGMcsService.setMediaControlRequestResult(request, status);
        }

        long actions = getCurrentPlayerSupportedActions();
        switch (request.getOpcode()) {
            case Request.Opcodes.PLAY:
                if ((actions & PlaybackState.ACTION_PLAY) != 0
                        || (actions & PlaybackState.ACTION_PLAY_PAUSE) != 0) {
                    mMediaPlayerList.getActivePlayer().playCurrent();
                    status = Request.Results.SUCCESS;
                }
                break;
            case Request.Opcodes.PAUSE:
                if ((actions & PlaybackState.ACTION_PAUSE) != 0
                        || (actions & PlaybackState.ACTION_PLAY_PAUSE) != 0) {
                    // Notice: Pause may function as Pause/Play toggle switch when triggered on
                    // a Media Player which is already in Paused state.
                    if (mCurrentData.state.getState() != PlaybackState.STATE_PAUSED) {
                        mMediaPlayerList.getActivePlayer().pauseCurrent();
                    }
                    status = Request.Results.SUCCESS;
                }
                break;
            case Request.Opcodes.STOP:
                if ((actions & PlaybackState.ACTION_STOP) != 0) {
                    mMediaPlayerList.getActivePlayer().seekTo(0);
                    mMediaPlayerList.getActivePlayer().stopCurrent();
                    status = Request.Results.SUCCESS;
                }
                break;
            case Request.Opcodes.PREVIOUS_TRACK:
                if ((actions & PlaybackState.ACTION_SKIP_TO_PREVIOUS) != 0) {
                    mMediaPlayerList.getActivePlayer().skipToPrevious();
                    status = Request.Results.SUCCESS;
                }
                break;
            case Request.Opcodes.NEXT_TRACK:
                if ((actions & PlaybackState.ACTION_SKIP_TO_NEXT) != 0) {
                    mMediaPlayerList.getActivePlayer().skipToNext();
                    status = Request.Results.SUCCESS;
                }
                break;
            case Request.Opcodes.FAST_REWIND:
                if ((actions & PlaybackState.ACTION_REWIND) != 0) {
                    mMediaPlayerList.getActivePlayer().rewind();
                    status = Request.Results.SUCCESS;
                }
                break;
            case Request.Opcodes.FAST_FORWARD:
                if ((actions & PlaybackState.ACTION_FAST_FORWARD) != 0) {
                    mMediaPlayerList.getActivePlayer().fastForward();
                    status = Request.Results.SUCCESS;
                }
                break;
            case Request.Opcodes.MOVE_RELATIVE:
                if ((actions & PlaybackState.ACTION_SEEK_TO) != 0) {
                    long requested_offset_ms = request.getIntArg();
                    long current_pos_ms = getLatestTrackPosition();
                    long track_duration_ms = getCurrentTrackDuration();

                    if (track_duration_ms != MediaControlGattServiceInterface.TRACK_DURATION_UNAVAILABLE) {
                        current_pos_ms = current_pos_ms + requested_offset_ms;
                        if (current_pos_ms < 0) {
                            current_pos_ms = 0;
                        } else if (current_pos_ms > track_duration_ms) {
                            current_pos_ms = track_duration_ms;
                        }

                        mMediaPlayerList.getActivePlayer().seekTo(current_pos_ms);
                        status = Request.Results.SUCCESS;
                    }
                }
                break;
        }

        // These LE Audio opcodes can't be mapped to Android media session actions:
        // Request.Opcodes.PREVIOUS_SEGMENT:
        // Request.Opcodes.NEXT_SEGMENT:
        // Request.Opcodes.FIRST_SEGMENT:
        // Request.Opcodes.LAST_SEGMENT:
        // Request.Opcodes.GOTO_SEGMENT:
        // Request.Opcodes.FIRST_TRACK:
        // Request.Opcodes.LAST_TRACK:
        // Request.Opcodes.GOTO_TRACK:
        // Request.Opcodes.PREVIOUS_GROUP:
        // Request.Opcodes.NEXT_GROUP:
        // Request.Opcodes.FIRST_GROUP:
        // Request.Opcodes.LAST_GROUP:
        // Request.Opcodes.GOTO_GROUP:

        if (mGMcsService != null) {
            mGMcsService.setMediaControlRequestResult(request, status);
        }
    }

    private synchronized long getLatestTrackPosition() {
        if (mMediaPlayerList.getActivePlayer() != null) {
            PlaybackState state = mMediaPlayerList.getActivePlayer().getPlaybackState();
            if (state != null) return getDriftCorrectedTrackPosition(state);
        }
        return MediaControlGattServiceInterface.TRACK_POSITION_UNAVAILABLE;
    }

    private long getDriftCorrectedTrackPosition(PlaybackState state) {
        long position = state.getPosition();
        if (playerState2McsState(state.getState()) == MediaState.PLAYING) {
            position = position + SystemClock.elapsedRealtime() - state.getLastPositionUpdateTime();
        }

        // Limit the possible position to valid track positions
        if (position < 0) return 0;

        long duration = getCurrentTrackDuration();
        if (duration == MediaControlGattServiceInterface.TRACK_DURATION_UNAVAILABLE) {
            return position;
        }

        return Math.min(duration, position);
    }

    @VisibleForTesting
    static int playerActions2McsSupportedOpcodes(long supportedPlayerActions) {
        int opcodesSupported = 0;

        if ((supportedPlayerActions & PlaybackState.ACTION_STOP) != 0) {
            opcodesSupported |= Request.SupportedOpcodes.STOP;
        }

        if ((supportedPlayerActions & PlaybackState.ACTION_PAUSE) != 0
                || (supportedPlayerActions & PlaybackState.ACTION_PLAY_PAUSE) != 0) {
            opcodesSupported |= Request.SupportedOpcodes.PAUSE;
        }

        if ((supportedPlayerActions & PlaybackState.ACTION_PLAY) != 0
                || (supportedPlayerActions & PlaybackState.ACTION_PLAY_PAUSE) != 0) {
            opcodesSupported |= Request.SupportedOpcodes.PLAY;
        }

        if ((supportedPlayerActions & PlaybackState.ACTION_REWIND) != 0) {
            opcodesSupported |= Request.SupportedOpcodes.FAST_REWIND;
        }

        if ((supportedPlayerActions & PlaybackState.ACTION_SKIP_TO_PREVIOUS) != 0) {
            opcodesSupported |= Request.SupportedOpcodes.PREVIOUS_TRACK;
        }

        if ((supportedPlayerActions & PlaybackState.ACTION_SKIP_TO_NEXT) != 0) {
            opcodesSupported |= Request.SupportedOpcodes.NEXT_TRACK;
        }

        if ((supportedPlayerActions & PlaybackState.ACTION_FAST_FORWARD) != 0) {
            opcodesSupported |= Request.SupportedOpcodes.FAST_FORWARD;
        }

        if ((supportedPlayerActions & PlaybackState.ACTION_SEEK_TO) != 0) {
            opcodesSupported |= Request.SupportedOpcodes.MOVE_RELATIVE;
        }

        // This Android media session actions can't be mapped to LE Audio:
        // PlaybackState.ACTION_SET_RATING
        // PlaybackState.ACTION_PLAY_FROM_MEDIA_ID
        // PlaybackState.ACTION_PLAY_FROM_SEARCH
        // PlaybackState.ACTION_SKIP_TO_QUEUE_ITEM
        // PlaybackState.ACTION_PLAY_FROM_URI
        // PlaybackState.ACTION_PREPARE
        // PlaybackState.ACTION_PREPARE_FROM_MEDIA_ID
        // PlaybackState.ACTION_PREPARE_FROM_SEARCH
        // PlaybackState.ACTION_PREPARE_FROM_URI

        return opcodesSupported;
    }

    private void processPendingPlayerStateRequest() {
        if (DBG) Log.d(TAG, "GMCS processPendingPlayerStateRequest");

        Map<PlayerStateField, Object> handled_request_map = new HashMap<>();

        synchronized (this) {
            if (mPendingStateRequest == null) return;
            // Notice: If we are unable to provide the requested field it will stay queued until we
            //         are able to provide it.
            for (PlayerStateField settings_field : mPendingStateRequest) {
                switch (settings_field) {
                    case PLAYBACK_STATE:
                        if (mCurrentData.state != null) {
                            handled_request_map.put(settings_field,
                                    playerState2McsState(mCurrentData.state.getState()));
                        }
                        break;
                    case TRACK_DURATION:
                        handled_request_map.put(settings_field, getCurrentTrackDuration());
                        break;
                    case PLAYBACK_SPEED:
                        if (mCurrentData.state != null) {
                            handled_request_map.put(
                                    settings_field, mCurrentData.state.getPlaybackSpeed());
                        }
                        break;
                    case SEEKING_SPEED:
                        float seeking_speed = 1.0f;
                        if (mCurrentData.state != null) {
                            if ((mCurrentData.state.getState()
                                    == PlaybackState.STATE_FAST_FORWARDING)
                                    || (mCurrentData.state.getState()
                                            == PlaybackState.STATE_REWINDING)) {
                                seeking_speed = mCurrentData.state.getPlaybackSpeed();
                            }
                        }

                        handled_request_map.put(settings_field, seeking_speed);
                        break;
                    case PLAYING_ORDER:
                        handled_request_map.put(settings_field, getCurrentPlayerPlayingOrder());
                        break;
                    case TRACK_POSITION:
                        if (mCurrentData.state != null) {
                            handled_request_map.put(
                                    settings_field, getDriftCorrectedTrackPosition(
                                            mCurrentData.state));
                        }
                        break;
                    case PLAYER_NAME:
                        String player_name = getCurrentPlayerName();
                        if (player_name != null) {
                            handled_request_map.put(settings_field, player_name);
                        }
                        break;
                    case ICON_URL:
                        // Not implemented
                        break;
                    case ICON_OBJ_ID:
                        // TODO: Implement once we have Object Transfer Service
                        break;
                    case PLAYING_ORDER_SUPPORTED:
                        Integer playing_order = getSupportedPlayingOrder();
                        if (playing_order != null) {
                            handled_request_map.put(settings_field, playing_order.intValue());
                        }
                        break;
                    case OPCODES_SUPPORTED:
                        if (mCurrentData.state != null) {
                            int opcodes =
                                    playerActions2McsSupportedOpcodes(
                                            getCurrentPlayerSupportedActions());
                            handled_request_map.put(settings_field, opcodes);
                            mEventLogger.logd(
                                    TAG,
                                    "updateSupportedOpcodes setting supported opcodes to: "
                                            + opcodes);
                        }
                        break;
                }
            }
        }

        if (!handled_request_map.isEmpty()) {
            removePendingStateRequests(handled_request_map.keySet());
            if (mGMcsService != null) {
              mGMcsService.updatePlayerState(handled_request_map);
            }
        }

        if (DBG) {
            synchronized (this) {
                if (mPendingStateRequest != null && !mPendingStateRequest.isEmpty()) {
                    Log.w(TAG, "MCS service state fields left unhandled: ");
                    for (PlayerStateField item : mPendingStateRequest) {
                        Log.w(TAG, "   > " + item);
                    }
                }
            }
        }
    }

    @VisibleForTesting
    PlayingOrder getCurrentPlayerPlayingOrder() {
        MediaPlayerWrapper mp = mMediaPlayerList.getActivePlayer();
        if (mp == null) return PlayingOrder.IN_ORDER_ONCE;

        // Notice: We don't support all the possible MCP playing orders
        if (mp.isShuffleSet()) {
            if (mp.isRepeatSet()) {
                return PlayingOrder.SHUFFLE_REPEAT;
            } else {
                return PlayingOrder.SHUFFLE_ONCE;
            }

        } else {
            if (mp.isRepeatSet()) {
                return PlayingOrder.IN_ORDER_REPEAT;
            } else {
                return PlayingOrder.IN_ORDER_ONCE;
            }
        }
    }

    @VisibleForTesting
    Integer getSupportedPlayingOrder() {
        MediaPlayerWrapper mp = mMediaPlayerList.getActivePlayer();
        if (mp == null) return null;

        // Notice: We don't support all the possible MCP playing orders
        int playing_order = SupportedPlayingOrder.IN_ORDER_ONCE;
        if (mp.isRepeatSupported()) playing_order |= SupportedPlayingOrder.IN_ORDER_REPEAT;

        if (mp.isShuffleSupported()) {
            if (mp.isRepeatSupported()) {
                playing_order |= SupportedPlayingOrder.SHUFFLE_REPEAT;
            } else {
                playing_order |= SupportedPlayingOrder.SHUFFLE_ONCE;
            }
        }
        return playing_order;
    }

    private String getCurrentPlayerName() {
        MediaPlayerWrapper player = mMediaPlayerList.getActivePlayer();
        if (player == null) return null;

        String player_name = player.getPackageName();
        try {
            PackageManager pm = mContext.getApplicationContext().getPackageManager();
            ApplicationInfo info = pm.getApplicationInfo(player.getPackageName(), 0);
            player_name = info.loadLabel(pm).toString();
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return player_name;
    }

    public void init() {
        mCurrentData = new MediaData(null, null, null);
        mMediaPlayerList.init(new ListCallback());

        String appToken = mContext.getPackageName();
        synchronized (mServiceMap) {
            if (mServiceMap.get(appToken) != null) {
                Log.w(TAG, "Was already registered: " + appToken);
                return;
            }

            // Instantiate a Service Instance and it's state machine
            int ccid = ContentControlIdKeeper.acquireCcid(BluetoothUuid.GENERIC_MEDIA_CONTROL,
                    BluetoothLeAudio.CONTEXT_TYPE_MEDIA | BluetoothLeAudio.CONTEXT_TYPE_LIVE);
            if (ccid == ContentControlIdKeeper.CCID_INVALID) {
                Log.e(TAG, "Unable to acquire valid CCID!");
                return;
            }

            // Only the bluetooth app is allowed to create generic media control service
            boolean isGenericMcs = appToken.equals(mContext.getPackageName());

            mEventLogger.logd(
                    TAG,
                    "Register MediaControlGattService instance ccid= "
                            + ccid
                            + ", features= "
                            + ServiceFeature.featuresToString(SUPPORTED_FEATURES, "\n\t\t\t"));

            MediaControlGattService svc = new MediaControlGattService(mMcpService, this, ccid);
            svc.init(isGenericMcs ? BluetoothUuid.GENERIC_MEDIA_CONTROL.getUuid()
                    : BluetoothUuid.MEDIA_CONTROL.getUuid());
            mServiceMap.put(appToken, svc);
        }
    }

    public void injectGattServiceForTesting(String appToken, MediaControlGattServiceInterface svc) {
        mServiceMap.put(appToken, svc);
    }

    public void cleanup() {
        if (mMediaPlayerList != null) {
            mMediaPlayerList.cleanup();
        }
        mMediaPlayerList = null;

        unregisterServiceInstance(mContext.getPackageName());

        // Shut down each registered service
        for (MediaControlGattServiceInterface svc : mServiceMap.values()) {
            svc.destroy();
        }
        mServiceMap.clear();
    }

    @VisibleForTesting
    static MediaState playerState2McsState(int playerState) {
        MediaState playback_state = sPlayerState2McsStateMap.get(playerState);

        if (playback_state == null) playback_state = MediaState.INACTIVE;

        return playback_state;
    }

    private static final Map<Integer, MediaState> sPlayerState2McsStateMap = Map.ofEntries(
            entry(PlaybackState.STATE_NONE, MediaState.INACTIVE),
            entry(PlaybackState.STATE_STOPPED, MediaState.PAUSED),
            entry(PlaybackState.STATE_PAUSED, MediaState.PAUSED),
            entry(PlaybackState.STATE_PLAYING, MediaState.PLAYING),
            entry(PlaybackState.STATE_FAST_FORWARDING, MediaState.SEEKING),
            entry(PlaybackState.STATE_REWINDING, MediaState.SEEKING),
            entry(PlaybackState.STATE_BUFFERING, MediaState.PAUSED),
            entry(PlaybackState.STATE_ERROR, MediaState.INACTIVE),
            entry(PlaybackState.STATE_CONNECTING, MediaState.INACTIVE),
            entry(PlaybackState.STATE_SKIPPING_TO_PREVIOUS, MediaState.PAUSED),
            entry(PlaybackState.STATE_SKIPPING_TO_NEXT, MediaState.PAUSED),
            entry(PlaybackState.STATE_SKIPPING_TO_QUEUE_ITEM, MediaState.PAUSED));

    private static final long SUPPORTED_FEATURES = ServiceFeature.PLAYER_NAME
            | ServiceFeature.PLAYER_NAME_NOTIFY
            // It seems that can't provide player icon URIs that easily
            // BluetoothMcs.ServiceFeature.PLAYER_ICON_URL |
            | ServiceFeature.TRACK_CHANGED | ServiceFeature.TRACK_TITLE
            | ServiceFeature.TRACK_TITLE_NOTIFY | ServiceFeature.TRACK_DURATION
            | ServiceFeature.TRACK_DURATION_NOTIFY | ServiceFeature.TRACK_POSITION
            | ServiceFeature.TRACK_POSITION_NOTIFY | ServiceFeature.PLAYBACK_SPEED
            | ServiceFeature.PLAYBACK_SPEED_NOTIFY | ServiceFeature.SEEKING_SPEED
            | ServiceFeature.SEEKING_SPEED_NOTIFY | ServiceFeature.PLAYING_ORDER
            | ServiceFeature.PLAYING_ORDER_NOTIFY | ServiceFeature.PLAYING_ORDER_SUPPORTED
            | ServiceFeature.MEDIA_STATE | ServiceFeature.MEDIA_CONTROL_POINT
            | ServiceFeature.MEDIA_CONTROL_POINT_OPCODES_SUPPORTED
            | ServiceFeature.MEDIA_CONTROL_POINT_OPCODES_SUPPORTED_NOTIFY
            | ServiceFeature.CONTENT_CONTROL_ID;


    private final Map<String, MediaControlGattServiceInterface> mServiceMap;

    public void unregisterServiceInstance(String appToken) {
        mEventLogger.logd(TAG, "unregisterServiceInstance");

        synchronized (mServiceMap) {
            MediaControlGattServiceInterface service = mServiceMap.get(appToken);
            if (service != null) {
                Integer ccid = service.getContentControlId();

                // Destroy will call the appropriate callback
                service.destroy();

                // Release ccid
                ContentControlIdKeeper.releaseCcid(ccid);

                mServiceMap.remove(appToken);
            }
        }
    }

    public void onDeviceAuthorizationSet(BluetoothDevice device) {
        // Notify all service instances in case of pending operations
        for (MediaControlGattServiceInterface svc : mServiceMap.values()) {
            svc.onDeviceAuthorizationSet(device);
        }
    }

    private boolean isGenericMediaService(int ccid) {
        for (MediaControlGattServiceInterface svc : mServiceMap.values()) {
            if (svc.getContentControlId() == ccid) {
                return svc.getServiceUuid().equals(BluetoothUuid.GENERIC_MEDIA_CONTROL.getUuid());
            }
        }
        return false;
    }

    List<ParcelUuid> getNotificationSubscriptions(int ccid, BluetoothDevice device) {
        // TODO: Support multiple MCS instances
        if (isGenericMediaService(ccid)) {
            byte[] gmcs_cccd = device.getMetadata(BluetoothDevice.METADATA_GMCS_CCCD);
            if ((gmcs_cccd != null) && (gmcs_cccd.length != 0)) {
                return Arrays.asList(Utils.byteArrayToUuid(gmcs_cccd));
            }
        }
        return Collections.emptyList();
    }

    void setNotificationSubscription(
            int ccid, BluetoothDevice device, ParcelUuid charUuid, boolean doNotify) {
        // TODO: Support multiple MCS instances
        if (isGenericMediaService(ccid)) {
            byte[] gmcs_cccd = device.getMetadata(BluetoothDevice.METADATA_GMCS_CCCD);
            List<ParcelUuid> uuidList;

            if ((gmcs_cccd == null) || (gmcs_cccd.length == 0)) {
                uuidList = new ArrayList<ParcelUuid>();
            } else {
                uuidList =
                        new ArrayList<ParcelUuid>(Arrays.asList(Utils.byteArrayToUuid(gmcs_cccd)));
            }

            boolean updateDb = false;
            if (doNotify) {
                if (!uuidList.contains(charUuid)) {
                    uuidList.add(charUuid);
                    updateDb = true;
                }
            } else if (uuidList.contains(charUuid)) {
                uuidList.remove(charUuid);
                updateDb = true;
            }

            if (updateDb) {
                if (!device.setMetadata(
                        BluetoothDevice.METADATA_GMCS_CCCD,
                        Utils.uuidsToByteArray(uuidList.toArray(new ParcelUuid[0])))) {
                    Log.e(
                            TAG,
                            "Can't set CCCD for GMCS characteristic UUID: "
                                    + charUuid.toString()
                                    + ", (remove)");
                }
            }
        }
    }

    public void dump(StringBuilder sb) {
        sb.append("Media Control Service instance list:\n");
        for (MediaControlGattServiceInterface svc : mServiceMap.values()) {
            svc.dump(sb);
            sb.append("\n");
        }

        mEventLogger.dump(sb);
        sb.append("\n");
    }
}
