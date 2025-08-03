/*
 * Copyright 2021 HIMSA II K/S - www.himsa.com.
 * Represented by EHIMA - www.ehima.com
 *
 * Licensed under the Apache License,mu Version 2.0 (the "License");
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

import static android.bluetooth.BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED;
import static android.bluetooth.BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_NOTIFY;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_READ;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_WRITE;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.util.Log;
import android.util.Pair;

import com.android.bluetooth.BluetoothEventLogger;
import com.android.bluetooth.Utils;
import com.android.bluetooth.a2dp.A2dpService;
import com.android.bluetooth.btservice.AdapterService;
import com.android.bluetooth.flags.Flags;
import com.android.bluetooth.hearingaid.HearingAidService;
import com.android.bluetooth.le_audio.LeAudioService;
import com.android.internal.annotations.VisibleForTesting;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * This implements Media Control Service object which is given back to the app who registers a new
 * MCS instance through the MCS Service Manager. It has no higher level logic to control the media
 * player itself, thus can be used either as an MCS or a single-instance GMCS. It implements only
 * the GATT Service logic, allowing the higher level layer to control the service state and react to
 * bluetooth peer device requests through the method calls and callback mechanism.
 *
 * Implemented according to Media Control Service v1.0 specification.
 */
public class MediaControlGattService implements MediaControlGattServiceInterface {
    private static final String TAG = "MediaControlGattService";
    private static final boolean DBG = Log.isLoggable(TAG, Log.INFO);
    private static final boolean VDBG = Log.isLoggable(TAG, Log.VERBOSE);

    /* MCS assigned UUIDs */
    public static final UUID UUID_PLAYER_NAME =
            UUID.fromString("00002b93-0000-1000-8000-00805f9b34fb");
    public static final UUID UUID_PLAYER_ICON_OBJ_ID =
            UUID.fromString("00002b94-0000-1000-8000-00805f9b34fb");
    public static final UUID UUID_PLAYER_ICON_URL =
            UUID.fromString("00002b95-0000-1000-8000-00805f9b34fb");
    public static final UUID UUID_TRACK_CHANGED =
            UUID.fromString("00002b96-0000-1000-8000-00805f9b34fb");
    public static final UUID UUID_TRACK_TITLE =
            UUID.fromString("00002b97-0000-1000-8000-00805f9b34fb");
    public static final UUID UUID_TRACK_DURATION =
            UUID.fromString("00002b98-0000-1000-8000-00805f9b34fb");
    public static final UUID UUID_TRACK_POSITION =
            UUID.fromString("00002b99-0000-1000-8000-00805f9b34fb");
    public static final UUID UUID_PLAYBACK_SPEED =
            UUID.fromString("00002b9a-0000-1000-8000-00805f9b34fb");
    public static final UUID UUID_SEEKING_SPEED =
            UUID.fromString("00002b9b-0000-1000-8000-00805f9b34fb");
    public static final UUID UUID_CURRENT_TRACK_SEGMENT_OBJ_ID =
            UUID.fromString("00002b9c-0000-1000-8000-00805f9b34fb");
    public static final UUID UUID_CURRENT_TRACK_OBJ_ID =
            UUID.fromString("00002b9d-0000-1000-8000-00805f9b34fb");
    public static final UUID UUID_NEXT_TRACK_OBJ_ID =
            UUID.fromString("00002b9e-0000-1000-8000-00805f9b34fb");
    public static final UUID UUID_CURRENT_GROUP_OBJ_ID =
            UUID.fromString("00002b9f-0000-1000-8000-00805f9b34fb");
    public static final UUID UUID_PARENT_GROUP_OBJ_ID =
            UUID.fromString("00002ba0-0000-1000-8000-00805f9b34fb");
    public static final UUID UUID_PLAYING_ORDER =
            UUID.fromString("00002ba1-0000-1000-8000-00805f9b34fb");
    public static final UUID UUID_PLAYING_ORDER_SUPPORTED =
            UUID.fromString("00002ba2-0000-1000-8000-00805f9b34fb");
    public static final UUID UUID_MEDIA_STATE =
            UUID.fromString("00002ba3-0000-1000-8000-00805f9b34fb");
    public static final UUID UUID_MEDIA_CONTROL_POINT =
            UUID.fromString("00002ba4-0000-1000-8000-00805f9b34fb");
    public static final UUID UUID_MEDIA_CONTROL_POINT_OPCODES_SUPPORTED =
            UUID.fromString("00002ba5-0000-1000-8000-00805f9b34fb");
    public static final UUID UUID_SEARCH_RESULT_OBJ_ID =
            UUID.fromString("00002ba6-0000-1000-8000-00805f9b34fb");
    public static final UUID UUID_SEARCH_CONTROL_POINT =
            UUID.fromString("00002ba7-0000-1000-8000-00805f9b34fb");
    public static final UUID UUID_CONTENT_CONTROL_ID =
            UUID.fromString("00002bba-0000-1000-8000-00805f9b34fb");

    private static final byte SEARCH_CONTROL_POINT_RESULT_SUCCESS = 0x01;
    private static final byte SEARCH_CONTROL_POINT_RESULT_FAILURE = 0x02;

    private static final float PLAY_SPEED_MIN = 0.25f;
    private static final float PLAY_SPEED_MAX = 3.957f;

    private static final int INTERVAL_UNAVAILABLE = 0xFFFFFFFF;

    /* This is to match AVRCP behavior */
    @VisibleForTesting
    static final int INITIAL_SUPPORTED_OPCODES =
            Request.SupportedOpcodes.PLAY
                    | Request.SupportedOpcodes.STOP
                    | Request.SupportedOpcodes.PAUSE
                    | Request.SupportedOpcodes.FAST_REWIND
                    | Request.SupportedOpcodes.FAST_FORWARD
                    | Request.SupportedOpcodes.NEXT_TRACK
                    | Request.SupportedOpcodes.PREVIOUS_TRACK;

    private final int mCcid;
    private Map<String, Map<UUID, Short>> mCccDescriptorValues = new HashMap<>();
    private long mFeatures;
    private Context mContext;
    private MediaControlServiceCallbacks mCallbacks;
    private BluetoothGattServerProxy mBluetoothGattServer;
    private BluetoothGattService mGattService = null;
    private Handler mHandler = new Handler(Looper.getMainLooper());
    private Map<Integer, BluetoothGattCharacteristic> mCharacteristics = new HashMap<>();
    private MediaState mCurrentMediaState = MediaState.INACTIVE;
    private Map<BluetoothDevice, List<GattOpContext>> mPendingGattOperations = new HashMap<>();
    private McpService mMcpService;
    private LeAudioService mLeAudioService;
    private AdapterService mAdapterService;

    private static final int LOG_NB_EVENTS = 200;
    private final BluetoothEventLogger mEventLogger;

    private static String mcsUuidToString(UUID uuid) {
        if (uuid.equals(UUID_PLAYER_NAME)) {
            return "PLAYER_NAME";
        } else if (uuid.equals(UUID_PLAYER_ICON_OBJ_ID)) {
            return "PLAYER_ICON_OBJ_ID";
        } else if (uuid.equals(UUID_PLAYER_ICON_URL)) {
            return "PLAYER_ICON_URL";
        } else if (uuid.equals(UUID_TRACK_CHANGED)) {
            return "TRACK_CHANGED";
        } else if (uuid.equals(UUID_TRACK_TITLE)) {
            return "TRACK_TITLE";
        } else if (uuid.equals(UUID_TRACK_DURATION)) {
            return "TRACK_DURATION";
        } else if (uuid.equals(UUID_TRACK_POSITION)) {
            return "TRACK_POSITION";
        } else if (uuid.equals(UUID_PLAYBACK_SPEED)) {
            return "PLAYBACK_SPEED";
        } else if (uuid.equals(UUID_SEEKING_SPEED)) {
            return "SEEKING_SPEED";
        } else if (uuid.equals(UUID_CURRENT_TRACK_SEGMENT_OBJ_ID)) {
            return "CURRENT_TRACK_SEGMENT_OBJ_ID";
        } else if (uuid.equals(UUID_CURRENT_TRACK_OBJ_ID)) {
            return "CURRENT_TRACK_OBJ_ID";
        } else if (uuid.equals(UUID_NEXT_TRACK_OBJ_ID)) {
            return "NEXT_TRACK_OBJ_ID";
        } else if (uuid.equals(UUID_CURRENT_GROUP_OBJ_ID)) {
            return "CURRENT_GROUP_OBJ_ID";
        } else if (uuid.equals(UUID_PARENT_GROUP_OBJ_ID)) {
            return "PARENT_GROUP_OBJ_ID";
        } else if (uuid.equals(UUID_PLAYING_ORDER)) {
            return "PLAYING_ORDER";
        } else if (uuid.equals(UUID_PLAYING_ORDER_SUPPORTED)) {
            return "PLAYING_ORDER_SUPPORTED";
        } else if (uuid.equals(UUID_MEDIA_STATE)) {
            return "MEDIA_STATE";
        } else if (uuid.equals(UUID_MEDIA_CONTROL_POINT)) {
            return "MEDIA_CONTROL_POINT";
        } else if (uuid.equals(UUID_MEDIA_CONTROL_POINT_OPCODES_SUPPORTED)) {
            return "MEDIA_CONTROL_POINT_OPCODES_SUPPORTED";
        } else if (uuid.equals(UUID_SEARCH_RESULT_OBJ_ID)) {
            return "SEARCH_RESULT_OBJ_ID";
        } else if (uuid.equals(UUID_SEARCH_CONTROL_POINT)) {
            return "SEARCH_CONTROL_POINT";
        } else if (uuid.equals(UUID_CONTENT_CONTROL_ID)) {
            return "CONTENT_CONTROL_ID";
        } else {
            return "UNKNOWN(" + uuid + ")";
        }
    }

    private static class GattOpContext {
        public enum Operation {
            READ_CHARACTERISTIC,
            WRITE_CHARACTERISTIC,
            READ_DESCRIPTOR,
            WRITE_DESCRIPTOR,
        }

        GattOpContext(Operation operation, int requestId,
                BluetoothGattCharacteristic characteristic, BluetoothGattDescriptor descriptor,
                boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            mOperation = operation;
            mRequestId = requestId;
            mCharacteristic = characteristic;
            mDescriptor = descriptor;
            mPreparedWrite = preparedWrite;
            mResponseNeeded = responseNeeded;
            mOffset = offset;
            mValue = value;
        }

        GattOpContext(Operation operation, int requestId,
                BluetoothGattCharacteristic characteristic, BluetoothGattDescriptor descriptor) {
            mOperation = operation;
            mRequestId = requestId;
            mCharacteristic = characteristic;
            mDescriptor = descriptor;
            mPreparedWrite = false;
            mResponseNeeded = false;
            mOffset = 0;
            mValue = null;
        }

        public Operation mOperation;
        public int mRequestId;
        public BluetoothGattCharacteristic mCharacteristic;
        public BluetoothGattDescriptor mDescriptor;
        public boolean mPreparedWrite;
        public boolean mResponseNeeded;
        public int mOffset;
        public byte[] mValue;
    }

    private final Map<UUID, CharacteristicWriteHandler> mCharWriteCallback = Map.of(
            UUID_TRACK_POSITION,
            (device, requestId, characteristic, preparedWrite, responseNeeded, offset, value) -> {
                if (VDBG) {
                    Log.d(TAG, "TRACK_POSITION write request");
                }
                int status = BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH;
                if (value.length == 4) {
                    status = BluetoothGatt.GATT_SUCCESS;
                    ByteBuffer bb = ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN);
                    handleTrackPositionRequest(bb.getInt());
                }
                if (responseNeeded) {
                    mBluetoothGattServer.sendResponse(device, requestId, status, offset, value);
                }
            },
            UUID_PLAYBACK_SPEED,
            (device, requestId, characteristic, preparedWrite, responseNeeded, offset, value) -> {
                if (VDBG) {
                    Log.d(TAG, "PLAYBACK_SPEED write request");
                }
                int status = BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH;
                if (value.length == 1) {
                    status = BluetoothGatt.GATT_SUCCESS;

                    Integer intVal = characteristic.getIntValue(
                            BluetoothGattCharacteristic.FORMAT_SINT8, 0);
                    // Don't bother player with the same value
                    if (intVal == value[0]) {
                        notifyCharacteristic(characteristic, null);
                    } else {
                        handlePlaybackSpeedRequest(value[0]);
                    }
                }
                if (responseNeeded) {
                    mBluetoothGattServer.sendResponse(device, requestId, status, offset, value);
                }
            },
            UUID_CURRENT_TRACK_OBJ_ID,
            (device, requestId, characteristic, preparedWrite, responseNeeded, offset, value) -> {
                if (VDBG) {
                    Log.d(TAG, "CURRENT_TRACK_OBJ_ID write request");
                }
                int status = BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH;
                if (value.length == 6) {
                    status = BluetoothGatt.GATT_SUCCESS;
                    handleObjectIdRequest(
                            ObjectIds.CURRENT_TRACK_OBJ_ID, byteArray2ObjId(value));
                }
                if (responseNeeded) {
                    mBluetoothGattServer.sendResponse(device, requestId, status, offset, value);
                }
            },
            UUID_NEXT_TRACK_OBJ_ID,
            (device, requestId, characteristic, preparedWrite, responseNeeded, offset, value) -> {
                if (VDBG) {
                    Log.d(TAG, "NEXT_TRACK_OBJ_ID write request");
                }
                int status = BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH;
                if (value.length == 6) {
                    status = BluetoothGatt.GATT_SUCCESS;
                    handleObjectIdRequest(ObjectIds.NEXT_TRACK_OBJ_ID, byteArray2ObjId(value));
                }
                if (responseNeeded) {
                    mBluetoothGattServer.sendResponse(device, requestId, status, offset, value);
                }
            },
            UUID_CURRENT_GROUP_OBJ_ID,
            (device, requestId, characteristic, preparedWrite, responseNeeded, offset, value) -> {
                if (VDBG) {
                    Log.d(TAG, "CURRENT_GROUP_OBJ_ID write request");
                }
                int status = BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH;
                if (value.length == 6) {
                    status = BluetoothGatt.GATT_SUCCESS;
                    handleObjectIdRequest(
                            ObjectIds.CURRENT_GROUP_OBJ_ID, byteArray2ObjId(value));
                }
                if (responseNeeded) {
                    mBluetoothGattServer.sendResponse(device, requestId, status, offset, value);
                }
            },
            UUID_PLAYING_ORDER,
            (device, requestId, characteristic, preparedWrite, responseNeeded, offset, value) -> {
                if (VDBG) {
                    Log.d(TAG, "PLAYING_ORDER write request");
                }
                int status = BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH;
                Integer currentPlayingOrder = null;

                if (characteristic.getValue() != null) {
                    currentPlayingOrder = characteristic.getIntValue(
                            BluetoothGattCharacteristic.FORMAT_UINT8, 0);
                }

                if (value.length == 1
                        && (currentPlayingOrder == null || currentPlayingOrder != value[0])) {
                    status = BluetoothGatt.GATT_SUCCESS;
                    BluetoothGattCharacteristic supportedPlayingOrderChar =
                            mCharacteristics.get(CharId.PLAYING_ORDER_SUPPORTED);
                    Integer supportedPlayingOrder =
                            supportedPlayingOrderChar.getIntValue(
                                    BluetoothGattCharacteristic.FORMAT_UINT16, 0);

                    if ((supportedPlayingOrder & (1 << (value[0] - 1))) != 0) {
                        handlePlayingOrderRequest(value[0]);
                    }
                }
                if (responseNeeded) {
                    mBluetoothGattServer.sendResponse(device, requestId, status, offset, value);
                }
            },
            UUID_MEDIA_CONTROL_POINT,
            (device, requestId, characteristic, preparedWrite, responseNeeded, offset, value) -> {
                if (VDBG) {
                    Log.d(TAG, "MEDIA_CONTROL_POINT write request");
                }
                int status = handleMediaControlPointRequest(device, value);
                if (responseNeeded) {
                    mBluetoothGattServer.sendResponse(device, requestId, status, offset, value);
                }
            },
            UUID_SEARCH_CONTROL_POINT,
            (device, requestId, characteristic, preparedWrite, responseNeeded, offset, value) -> {
                if (VDBG) {
                    Log.d(TAG, "SEARCH_CONTROL_POINT write request");
                }
                // TODO: There is no Object Trasfer Service implementation.
                if (responseNeeded) {
                    mBluetoothGattServer.sendResponse(device, requestId, 0, offset, value);
                }
            });

    private long millisecondsToMcsInterval(long interval) {
        /* MCS presents time in 0.01s intervals */
        return interval / 10;
    }

    private long mcsIntervalToMilliseconds(long interval) {
        /* MCS presents time in 0.01s intervals */
        return interval * 10L;
    }

    private int getDeviceAuthorization(BluetoothDevice device) {
        return mMcpService.getDeviceAuthorization(device);
    }

    private void onUnauthorizedCharRead(BluetoothDevice device, GattOpContext op) {
        UUID charUuid = op.mCharacteristic.getUuid();
        byte[] buffer = null;

        if (charUuid.equals(UUID_PLAYER_NAME)) {
            ByteBuffer bb = ByteBuffer.allocate(0).order(ByteOrder.LITTLE_ENDIAN);
            bb.put("".getBytes());
            buffer = bb.array();

        } else if (charUuid.equals(UUID_PLAYER_ICON_OBJ_ID)) {
            buffer = objId2ByteArray(-1);

        } else if (charUuid.equals(UUID_PLAYER_ICON_URL)) {
            ByteBuffer bb = ByteBuffer.allocate(0).order(ByteOrder.LITTLE_ENDIAN);
            bb.put("".getBytes());
            buffer = bb.array();

        } else if (charUuid.equals(UUID_TRACK_CHANGED)) {
            // No read is available on this characteristic

        } else if (charUuid.equals(UUID_TRACK_TITLE)) {
            ByteBuffer bb = ByteBuffer.allocate(0).order(ByteOrder.LITTLE_ENDIAN);
            bb.put("".getBytes());
            buffer = bb.array();

        } else if (charUuid.equals(UUID_TRACK_DURATION)) {
            ByteBuffer bb = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
            bb.putInt((int) TRACK_DURATION_UNAVAILABLE);
            buffer = bb.array();

        } else if (charUuid.equals(UUID_TRACK_POSITION)) {
            ByteBuffer bb = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
            bb.putInt((int) TRACK_POSITION_UNAVAILABLE);
            buffer = bb.array();

        } else if (charUuid.equals(UUID_PLAYBACK_SPEED)) {
            ByteBuffer bb = ByteBuffer.allocate(1).order(ByteOrder.LITTLE_ENDIAN);
            bb.put((byte) 1);
            buffer = bb.array();

        } else if (charUuid.equals(UUID_SEEKING_SPEED)) {
            ByteBuffer bb = ByteBuffer.allocate(1).order(ByteOrder.LITTLE_ENDIAN);
            bb.put((byte) 1);
            buffer = bb.array();

        } else if (charUuid.equals(UUID_CURRENT_TRACK_SEGMENT_OBJ_ID)) {
            buffer = objId2ByteArray(-1);

        } else if (charUuid.equals(UUID_CURRENT_TRACK_OBJ_ID)) {
            buffer = objId2ByteArray(-1);

        } else if (charUuid.equals(UUID_NEXT_TRACK_OBJ_ID)) {
            buffer = objId2ByteArray(-1);

        } else if (charUuid.equals(UUID_CURRENT_GROUP_OBJ_ID)) {
            buffer = objId2ByteArray(-1);

        } else if (charUuid.equals(UUID_PARENT_GROUP_OBJ_ID)) {
            buffer = objId2ByteArray(-1);

        } else if (charUuid.equals(UUID_PLAYING_ORDER)) {
            ByteBuffer bb = ByteBuffer.allocate(1).order(ByteOrder.LITTLE_ENDIAN);
            bb.put((byte) PlayingOrder.SINGLE_ONCE.getValue());
            buffer = bb.array();

        } else if (charUuid.equals(UUID_PLAYING_ORDER_SUPPORTED)) {
            ByteBuffer bb = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN);
            bb.putShort((short) SupportedPlayingOrder.SINGLE_ONCE);
            buffer = bb.array();

        } else if (charUuid.equals(UUID_MEDIA_STATE)) {
            ByteBuffer bb = ByteBuffer.allocate(1).order(ByteOrder.LITTLE_ENDIAN);
            bb.put((byte) MediaState.INACTIVE.getValue());
            buffer = bb.array();

        } else if (charUuid.equals(UUID_MEDIA_CONTROL_POINT)) {
            // No read is available on this characteristic

        } else if (charUuid.equals(UUID_MEDIA_CONTROL_POINT_OPCODES_SUPPORTED)) {
            ByteBuffer bb = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
            bb.putInt((int) Request.SupportedOpcodes.NONE);
            buffer = bb.array();

        } else if (charUuid.equals(UUID_SEARCH_RESULT_OBJ_ID)) {
            buffer = objId2ByteArray(-1);

        } else if (charUuid.equals(UUID_SEARCH_CONTROL_POINT)) {
            // No read is available on this characteristic

        } else if (charUuid.equals(UUID_CONTENT_CONTROL_ID)) {
            // It is ok, to send the real value for CCID
            if (op.mCharacteristic.getValue() != null) {
                buffer =
                        Arrays.copyOfRange(
                                op.mCharacteristic.getValue(),
                                op.mOffset,
                                op.mCharacteristic.getValue().length);
            }
        }

        if (buffer != null) {
            mBluetoothGattServer.sendResponse(
                    device, op.mRequestId, BluetoothGatt.GATT_SUCCESS, op.mOffset, buffer);
        } else {
            mEventLogger.loge(
                    TAG, "Missing characteristic value for char: " + mcsUuidToString(charUuid));
            mBluetoothGattServer.sendResponse(
                    device,
                    op.mRequestId,
                    BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH,
                    op.mOffset,
                    buffer);
        }
    }

    private void onUnauthorizedGattOperation(BluetoothDevice device, GattOpContext op) {
        UUID charUuid =
                (op.mCharacteristic != null
                        ? op.mCharacteristic.getUuid()
                        : (op.mDescriptor != null
                                ? op.mDescriptor.getCharacteristic().getUuid()
                                : null));
        mEventLogger.logw(
                TAG,
                "onUnauthorizedGattOperation: device= "
                        + device
                        + ", opcode= "
                        + op.mOperation
                        + ", characteristic= "
                        + (charUuid != null ? mcsUuidToString(charUuid) : "UNKNOWN"));

        switch (op.mOperation) {
            /* Allow not yet authorized devices to subscribe for notifications */
            case READ_DESCRIPTOR:
                if (op.mOffset > 1) {
                    mBluetoothGattServer.sendResponse(
                            device,
                            op.mRequestId,
                            BluetoothGatt.GATT_INVALID_OFFSET,
                            op.mOffset,
                            null);
                    return;
                }

                byte[] value = getCccBytes(device, op.mDescriptor.getCharacteristic().getUuid());
                if (value == null) {
                    mBluetoothGattServer.sendResponse(
                            device, op.mRequestId, BluetoothGatt.GATT_FAILURE, op.mOffset, null);
                    return;
                }

                value = Arrays.copyOfRange(value, op.mOffset, value.length);
                mBluetoothGattServer.sendResponse(
                        device, op.mRequestId, BluetoothGatt.GATT_SUCCESS, op.mOffset, value);
                return;
            case WRITE_DESCRIPTOR:
                int status = BluetoothGatt.GATT_SUCCESS;
                if (op.mPreparedWrite) {
                    status = BluetoothGatt.GATT_FAILURE;
                } else if (op.mOffset > 0) {
                    status = BluetoothGatt.GATT_INVALID_OFFSET;
                } else {
                    status = BluetoothGatt.GATT_SUCCESS;
                    setCcc(
                            device,
                            op.mDescriptor.getCharacteristic().getUuid(),
                            op.mOffset,
                            op.mValue,
                            true);
                }

                if (op.mResponseNeeded) {
                    mBluetoothGattServer.sendResponse(
                            device, op.mRequestId, status, op.mOffset, op.mValue);
                }
                return;
            case READ_CHARACTERISTIC:
                onUnauthorizedCharRead(device, op);
                return;
            case WRITE_CHARACTERISTIC:
                // store as pending operation
                break;
            default:
                break;
        }

        synchronized (mPendingGattOperations) {
            List<GattOpContext> operations = mPendingGattOperations.get(device);
            if (operations == null) {
                operations = new ArrayList<>();
                mPendingGattOperations.put(device, operations);
            }

            operations.add(op);
            // Send authorization request for each device only for it's first GATT request
            if (operations.size() == 1) {
                mMcpService.onDeviceUnauthorized(device);
            }
        }
    }

    private void onAuthorizedGattOperation(BluetoothDevice device, GattOpContext op) {
        UUID charUuid =
                (op.mCharacteristic != null
                        ? op.mCharacteristic.getUuid()
                        : (op.mDescriptor != null
                                ? op.mDescriptor.getCharacteristic().getUuid()
                                : null));
        mEventLogger.logd(
                TAG,
                "onAuthorizedGattOperation: device= "
                        + device
                        + ", opcode= "
                        + op.mOperation
                        + ", characteristic= "
                        + (charUuid != null ? mcsUuidToString(charUuid) : "UNKNOWN"));

        int status = BluetoothGatt.GATT_SUCCESS;

        switch (op.mOperation) {
            case READ_CHARACTERISTIC:
                // Always ask for the latest position
                if (op.mCharacteristic.getUuid().equals(
                        mCharacteristics.get(CharId.TRACK_POSITION).getUuid())) {
                    long positionMs = TRACK_POSITION_UNAVAILABLE;
                    positionMs = mCallbacks.onGetCurrentTrackPosition();
                    final int position =
                            (positionMs != TRACK_POSITION_UNAVAILABLE)
                                    ? (int) millisecondsToMcsInterval(positionMs)
                                    : INTERVAL_UNAVAILABLE;

                    ByteBuffer bb =
                            ByteBuffer.allocate(Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN);
                    bb.putInt(position);

                    mBluetoothGattServer.sendResponse(device, op.mRequestId,
                            BluetoothGatt.GATT_SUCCESS, op.mOffset,
                            Arrays.copyOfRange(bb.array(), op.mOffset, Integer.BYTES));
                    return;
                }

                if (op.mCharacteristic.getValue() != null) {
                    mBluetoothGattServer.sendResponse(device, op.mRequestId,
                            BluetoothGatt.GATT_SUCCESS, op.mOffset,
                            Arrays.copyOfRange(op.mCharacteristic.getValue(), op.mOffset,
                                    op.mCharacteristic.getValue().length));
                } else {
                    Log.e(TAG,
                            "Missing characteristic value for char: "
                                    + op.mCharacteristic.getUuid());
                    mBluetoothGattServer.sendResponse(device, op.mRequestId,
                            BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH, op.mOffset, new byte[]{});
                }
                break;

            case WRITE_CHARACTERISTIC:
                if (op.mPreparedWrite) {
                    status = BluetoothGatt.GATT_FAILURE;
                } else if (op.mOffset > 0) {
                    status = BluetoothGatt.GATT_INVALID_OFFSET;
                } else {
                    CharacteristicWriteHandler handler =
                            mCharWriteCallback.get(op.mCharacteristic.getUuid());
                    handler.onCharacteristicWriteRequest(
                            device, op.mRequestId, op.mCharacteristic, op.mPreparedWrite,
                            op.mResponseNeeded, op.mOffset, op.mValue);
                    break;
                }

                if (op.mResponseNeeded) {
                    mBluetoothGattServer.sendResponse(
                            device, op.mRequestId, status, op.mOffset, op.mValue);
                }
                break;

            case READ_DESCRIPTOR:
                if (op.mOffset > 1) {
                    mBluetoothGattServer.sendResponse(device, op.mRequestId,
                            BluetoothGatt.GATT_INVALID_OFFSET, op.mOffset, null);
                    break;
                }

                byte[] value = getCccBytes(device, op.mDescriptor.getCharacteristic().getUuid());
                if (value == null) {
                    mBluetoothGattServer.sendResponse(
                            device, op.mRequestId, BluetoothGatt.GATT_FAILURE, op.mOffset, null);
                    break;
                }

                value = Arrays.copyOfRange(value, op.mOffset, value.length);
                mBluetoothGattServer.sendResponse(
                        device, op.mRequestId, BluetoothGatt.GATT_SUCCESS, op.mOffset, value);
                break;

            case WRITE_DESCRIPTOR:
                if (op.mPreparedWrite) {
                    status = BluetoothGatt.GATT_FAILURE;
                } else if (op.mOffset > 0) {
                    status = BluetoothGatt.GATT_INVALID_OFFSET;
                } else {
                    status = BluetoothGatt.GATT_SUCCESS;
                    setCcc(device, op.mDescriptor.getCharacteristic().getUuid(), op.mOffset,
                            op.mValue, true);
                }

                if (op.mResponseNeeded) {
                    mBluetoothGattServer.sendResponse(
                            device, op.mRequestId, status, op.mOffset, op.mValue);
                }
                break;

            default:
                break;
        }
    }

    private void onRejectedAuthorizationGattOperation(BluetoothDevice device, GattOpContext op) {
        UUID charUuid =
                (op.mCharacteristic != null
                        ? op.mCharacteristic.getUuid()
                        : (op.mDescriptor != null
                                ? op.mDescriptor.getCharacteristic().getUuid()
                                : null));
        mEventLogger.logw(
                TAG,
                "onRejectedAuthorizationGattOperation: device= "
                        + device
                        + ", opcode= "
                        + op.mOperation
                        + ", characteristic= "
                        + (charUuid != null ? mcsUuidToString(charUuid) : "UNKNOWN"));

        switch (op.mOperation) {
            case READ_CHARACTERISTIC:
            case READ_DESCRIPTOR:
                mBluetoothGattServer.sendResponse(device, op.mRequestId,
                        BluetoothGatt.GATT_INSUFFICIENT_AUTHORIZATION, op.mOffset, null);
                break;
            case WRITE_CHARACTERISTIC:
                if (op.mResponseNeeded) {
                    mBluetoothGattServer.sendResponse(device, op.mRequestId,
                            BluetoothGatt.GATT_INSUFFICIENT_AUTHORIZATION, op.mOffset, null);
                } else {
                    // In case of control point operations we can send an application error code
                    if (op.mCharacteristic.getUuid().equals(UUID_MEDIA_CONTROL_POINT)) {
                        setMediaControlRequestResult(
                                new Request(op.mValue[0], 0),
                                Request.Results.COMMAND_CANNOT_BE_COMPLETED);
                    } else if (op.mCharacteristic.getUuid().equals(UUID_SEARCH_CONTROL_POINT)) {
                        setSearchRequestResult(null, SearchRequest.Results.FAILURE, 0);
                    }
                }
                break;
            case WRITE_DESCRIPTOR:
                if (op.mResponseNeeded) {
                    mBluetoothGattServer.sendResponse(device, op.mRequestId,
                            BluetoothGatt.GATT_INSUFFICIENT_AUTHORIZATION, op.mOffset, null);
                }
                break;

            default:
                break;
        }
    }

    private void ClearUnauthorizedGattOperations(BluetoothDevice device) {
        if (VDBG) {
            Log.d(TAG, "ClearUnauthorizedGattOperations: device= " + device);
        }

        synchronized (mPendingGattOperations) {
            mPendingGattOperations.remove(device);
        }
    }

    private void ProcessPendingGattOperations(BluetoothDevice device) {
        if (VDBG) {
            Log.d(TAG, "ProcessPendingGattOperations: device= " + device);
        }

        synchronized (mPendingGattOperations) {
            if (mPendingGattOperations.containsKey(device)) {
                if (getDeviceAuthorization(device) == BluetoothDevice.ACCESS_ALLOWED) {
                    for (GattOpContext op : mPendingGattOperations.get(device)) {
                        onAuthorizedGattOperation(device, op);
                    }
                } else {
                    for (GattOpContext op : mPendingGattOperations.get(device)) {
                        onRejectedAuthorizationGattOperation(device, op);
                    }
                }
                ClearUnauthorizedGattOperations(device);
            }
        }
    }

    private void restoreCccValuesForStoredDevices() {
        for (BluetoothDevice device : mAdapterService.getBondedDevices()) {
            List<ParcelUuid> uuidList = mMcpService.getNotificationSubscriptions(mCcid, device);
            mEventLogger.logd(
                    TAG,
                    "restoreCccValuesForStoredDevices: device= "
                            + device
                            + ", num_uuids= "
                            + uuidList.size());

            /* Restore CCCD values for device */
            for (ParcelUuid uuid : uuidList) {
                mEventLogger.logd(
                        TAG,
                        "restoreCccValuesForStoredDevices: device= " + device + ", char= " + uuid);
                setCcc(device, uuid.getUuid(), 0,
                        BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE, false);
            }
        }
    }

    private final AdapterService.BluetoothStateCallback mBluetoothStateChangeCallback =
            new AdapterService.BluetoothStateCallback() {
                public void onBluetoothStateChange(int prevState, int newState) {
                    if (DBG) {
                        Log.d(
                                TAG,
                                "onBluetoothStateChange: state="
                                        + BluetoothAdapter.nameForState(newState));
                    }
                    if (newState == BluetoothAdapter.STATE_ON) {
                        restoreCccValuesForStoredDevices();
                    }
                }
            };

    @VisibleForTesting
    final BluetoothGattServerCallback mServerCallback = new BluetoothGattServerCallback() {
        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            super.onConnectionStateChange(device, status, newState);
            if (VDBG) {
                Log.d(TAG, "BluetoothGattServerCallback: onConnectionStateChange");
            }
            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                ClearUnauthorizedGattOperations(device);
            }
        }

        @Override
        public void onServiceAdded(int status, BluetoothGattService service) {
            super.onServiceAdded(status, service);
            if (VDBG) {
                Log.d(TAG, "BluetoothGattServerCallback: onServiceAdded");
            }

            if (mCallbacks != null) {
                mCallbacks.onServiceInstanceRegistered((status != BluetoothGatt.GATT_SUCCESS)
                                ? ServiceStatus.UNKNOWN_ERROR
                                : ServiceStatus.OK,
                        MediaControlGattService.this);
            }

            mCharacteristics.get(CharId.CONTENT_CONTROL_ID)
                    .setValue(mCcid, BluetoothGattCharacteristic.FORMAT_UINT8, 0);
            restoreCccValuesForStoredDevices();
            setInitialCharacteristicValuesAndNotify();
            initialStateRequest();
        }

        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset,
                BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic);
            if (VDBG) {
                Log.d(TAG, "BluetoothGattServerCallback: onCharacteristicReadRequest offset= "
                        + offset + " entire value= " + Arrays.toString(characteristic.getValue()));
            }

            if ((characteristic.getProperties() & PROPERTY_READ) == 0) {
                mBluetoothGattServer.sendResponse(device, requestId,
                        BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED, offset, null);
                return;
            }

            GattOpContext op = new GattOpContext(
                    GattOpContext.Operation.READ_CHARACTERISTIC, requestId, characteristic, null);
            switch (getDeviceAuthorization(device)) {
                case BluetoothDevice.ACCESS_REJECTED:
                    onRejectedAuthorizationGattOperation(device, op);
                    break;
                case BluetoothDevice.ACCESS_UNKNOWN:
                    onUnauthorizedGattOperation(device, op);
                    break;
                default:
                    onAuthorizedGattOperation(device, op);
                    break;
            }
        }

        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId,
                BluetoothGattCharacteristic characteristic, boolean preparedWrite,
                boolean responseNeeded, int offset, byte[] value) {
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite,
                    responseNeeded, offset, value);
            if (VDBG) {
                Log.d(TAG,
                        "BluetoothGattServerCallback: "
                                + "onCharacteristicWriteRequest");
            }

            if ((characteristic.getProperties() & PROPERTY_WRITE)
                    == 0) {
                mBluetoothGattServer.sendResponse(
                        device, requestId, BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED, offset, value);
                return;
            }

            GattOpContext op = new GattOpContext(GattOpContext.Operation.WRITE_CHARACTERISTIC,
                    requestId, characteristic, null, preparedWrite, responseNeeded, offset, value);
            switch (getDeviceAuthorization(device)) {
                case BluetoothDevice.ACCESS_REJECTED:
                    onRejectedAuthorizationGattOperation(device, op);
                    break;
                case BluetoothDevice.ACCESS_UNKNOWN:
                    onUnauthorizedGattOperation(device, op);
                    break;
                default:
                    onAuthorizedGattOperation(device, op);
                    break;
            }
        }

        @Override
        public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset,
                BluetoothGattDescriptor descriptor) {
            super.onDescriptorReadRequest(device, requestId, offset, descriptor);
            if (VDBG) {
                Log.d(TAG,
                        "BluetoothGattServerCallback: "
                                + "onDescriptorReadRequest");
            }

            if ((descriptor.getPermissions() & BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED)
                    == 0) {
                mBluetoothGattServer.sendResponse(
                        device, requestId, BluetoothGatt.GATT_READ_NOT_PERMITTED, offset, null);
                return;
            }

            GattOpContext op = new GattOpContext(
                    GattOpContext.Operation.READ_DESCRIPTOR, requestId, null, descriptor);
            switch (getDeviceAuthorization(device)) {
                case BluetoothDevice.ACCESS_REJECTED:
                    onRejectedAuthorizationGattOperation(device, op);
                    break;
                case BluetoothDevice.ACCESS_UNKNOWN:
                    onUnauthorizedGattOperation(device, op);
                    break;
                default:
                    onAuthorizedGattOperation(device, op);
                    break;
            }
        }

        @Override
        public void onDescriptorWriteRequest(BluetoothDevice device, int requestId,
                BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded,
                int offset, byte[] value) {
            super.onDescriptorWriteRequest(
                    device, requestId, descriptor, preparedWrite, responseNeeded, offset, value);
            if (VDBG) {
                Log.d(TAG,
                        "BluetoothGattServerCallback: "
                                + "onDescriptorWriteRequest");
            }

            if ((descriptor.getPermissions() & BluetoothGattDescriptor.PERMISSION_WRITE_ENCRYPTED)
                    == 0) {
                mBluetoothGattServer.sendResponse(
                        device, requestId, BluetoothGatt.GATT_WRITE_NOT_PERMITTED, offset, value);
                return;
            }

            GattOpContext op = new GattOpContext(GattOpContext.Operation.WRITE_DESCRIPTOR,
                    requestId, null, descriptor, preparedWrite, responseNeeded, offset, value);
            switch (getDeviceAuthorization(device)) {
                case BluetoothDevice.ACCESS_REJECTED:
                    onRejectedAuthorizationGattOperation(device, op);
                    break;
                case BluetoothDevice.ACCESS_UNKNOWN:
                    onUnauthorizedGattOperation(device, op);
                    break;
                default:
                    onAuthorizedGattOperation(device, op);
                    break;
            }
        }
    };

    private void initialStateRequest() {
        List<PlayerStateField> field_list = new ArrayList<>();

        if (isFeatureSupported(ServiceFeature.MEDIA_STATE)) {
            field_list.add(PlayerStateField.PLAYBACK_STATE);
        }

        if (isFeatureSupported(ServiceFeature.PLAYER_ICON_URL)) {
            field_list.add(PlayerStateField.ICON_URL);
        }

        if (isFeatureSupported(ServiceFeature.PLAYER_ICON_OBJ_ID)) {
            field_list.add(PlayerStateField.ICON_OBJ_ID);
        }

        if (isFeatureSupported(ServiceFeature.PLAYER_NAME)) {
            field_list.add(PlayerStateField.PLAYER_NAME);
        }

        if (isFeatureSupported(ServiceFeature.PLAYING_ORDER_SUPPORTED)) {
            field_list.add(PlayerStateField.PLAYING_ORDER_SUPPORTED);
        }

        mCallbacks.onPlayerStateRequest(field_list.stream().toArray(PlayerStateField[]::new));
    }

    private void setInitialCharacteristicValues(boolean notify) {
        mEventLogger.logd(TAG, "setInitialCharacteristicValues");
        updateMediaStateChar(mCurrentMediaState.getValue());
        updatePlayerNameChar("", notify);
        updatePlayerIconUrlChar("");

        // Object IDs will have a length of 0;
        updateObjectID(ObjectIds.PLAYER_ICON_OBJ_ID, -1, notify);
        updateObjectID(ObjectIds.CURRENT_TRACK_SEGMENT_OBJ_ID, -1, notify);
        updateObjectID(ObjectIds.CURRENT_TRACK_OBJ_ID, -1, notify);
        updateObjectID(ObjectIds.NEXT_TRACK_OBJ_ID, -1, notify);
        updateObjectID(ObjectIds.CURRENT_GROUP_OBJ_ID, -1, notify);
        updateObjectID(ObjectIds.PARENT_GROUP_OBJ_ID, -1, notify);
        updateObjectID(ObjectIds.SEARCH_RESULT_OBJ_ID, -1, notify);
        updateTrackTitleChar("", notify);
        updateTrackDurationChar(TRACK_DURATION_UNAVAILABLE, notify);
        updateTrackPositionChar(TRACK_POSITION_UNAVAILABLE, notify);
        updatePlaybackSpeedChar(1, notify);
        updateSeekingSpeedChar(1, notify);
        updatePlayingOrderSupportedChar(SupportedPlayingOrder.SINGLE_ONCE);
        updatePlayingOrderChar(PlayingOrder.SINGLE_ONCE, notify);
        updateSupportedOpcodesChar(INITIAL_SUPPORTED_OPCODES, notify);
    }

    private void setInitialCharacteristicValues() {
        setInitialCharacteristicValues(false);
    }

    private void setInitialCharacteristicValuesAndNotify() {
        setInitialCharacteristicValues(true);
    }

    /**
     * A proxy class that facilitates testing of the McpService class.
     *
     * This is necessary due to the "final" attribute of the BluetoothGattServer class. In order to
     * test the correct functioning of the McpService class, the final class must be put into a
     * container that can be mocked correctly.
     */
    public class BluetoothGattServerProxy {
        private BluetoothGattServer mBluetoothGattServer;
        private BluetoothManager mBluetoothManager;

        public BluetoothGattServerProxy(BluetoothGattServer gatt, BluetoothManager manager) {
            mBluetoothManager = manager;
            mBluetoothGattServer = gatt;
        }

        public boolean addService(BluetoothGattService service) {
            return mBluetoothGattServer.addService(service);
        }

        public boolean removeService(BluetoothGattService service) {
            return mBluetoothGattServer.removeService(service);
        }

        public void close() {
            mBluetoothGattServer.close();
        }

        public boolean sendResponse(
                BluetoothDevice device, int requestId, int status, int offset, byte[] value) {
            return mBluetoothGattServer.sendResponse(device, requestId, status, offset, value);
        }

        public boolean notifyCharacteristicChanged(BluetoothDevice device,
                BluetoothGattCharacteristic characteristic, boolean confirm) {
            return mBluetoothGattServer.notifyCharacteristicChanged(
                    device, characteristic, confirm);
        }

        public List<BluetoothDevice> getConnectedDevices() {
            return mBluetoothManager.getConnectedDevices(BluetoothProfile.GATT_SERVER);
        }

        public boolean isDeviceConnected(BluetoothDevice device) {
            return mBluetoothManager.getConnectionState(device, BluetoothProfile.GATT_SERVER)
                    == BluetoothProfile.STATE_CONNECTED;
        }
    }

    protected MediaControlGattService(McpService mcpService,
            @NonNull MediaControlServiceCallbacks callbacks, int ccid) {
        mContext = mcpService;
        mCallbacks = callbacks;
        mCcid = ccid;

        mMcpService = mcpService;
        mAdapterService =  Objects.requireNonNull(AdapterService.getAdapterService(),
                "AdapterService shouldn't be null when creating MediaControlCattService");

        mAdapterService.registerBluetoothStateCallback(
                mContext.getMainExecutor(), mBluetoothStateChangeCallback);

        mEventLogger =
                new BluetoothEventLogger(
                        LOG_NB_EVENTS, TAG + " instance (CCID= " + ccid + ") event log");
    }

    protected boolean init(UUID scvUuid) {
        mFeatures = mCallbacks.onGetFeatureFlags();

        // Verify the minimum required set of supported player features
        if ((mFeatures & ServiceFeature.ALL_MANDATORY_SERVICE_FEATURES)
                != ServiceFeature.ALL_MANDATORY_SERVICE_FEATURES) {
            mCallbacks.onServiceInstanceRegistered(ServiceStatus.INVALID_FEATURE_FLAGS, null);
            return false;
        }

        mEventLogger.add("Initializing");

        // Init attribute database
        return initGattService(scvUuid);
    }

    private void handleObjectIdRequest(int objField, long objId) {
        mEventLogger.add("handleObjectIdRequest: obj= " + objField + ", objId= " + objId);
        mCallbacks.onSetObjectIdRequest(objField, objId);
    }

    private void handlePlayingOrderRequest(int order) {
        mEventLogger.add("handlePlayingOrderRequest: order= " + order);
        mCallbacks.onPlayingOrderSetRequest(order);
    }

    private void handlePlaybackSpeedRequest(int speed) {
        float floatingSpeed = (float) Math.pow(2, speed / 64);
        mEventLogger.add("handlePlaybackSpeedRequest: floatingSpeed= " + floatingSpeed);
        mCallbacks.onPlaybackSpeedSetRequest(floatingSpeed);
    }

    private void handleTrackPositionRequest(long position) {
        final long positionMs = (position != INTERVAL_UNAVAILABLE)
                ? mcsIntervalToMilliseconds(position)
                : TRACK_POSITION_UNAVAILABLE;
        mEventLogger.add("handleTrackPositionRequest: positionMs= " + positionMs);
        mCallbacks.onTrackPositionSetRequest(positionMs);
    }

    private static int getMediaControlPointRequestPayloadLength(int opcode) {
        switch (opcode) {
            case Request.Opcodes.MOVE_RELATIVE:
            case Request.Opcodes.GOTO_SEGMENT:
            case Request.Opcodes.GOTO_TRACK:
            case Request.Opcodes.GOTO_GROUP:
                return 4;
            default:
                return 0;
        }
    }

    @VisibleForTesting
    int handleMediaControlPointRequest(BluetoothDevice device, byte[] value) {
        final int payloadOffset = 1;
        final int opcode = value[0];

        // Test for RFU bits and currently supported opcodes
        if (!isOpcodeSupported(opcode)) {
            Log.i(TAG, "handleMediaControlPointRequest: " + Request.Opcodes.toString(opcode)
                     + " not supported");
            mHandler.post(() -> {
                setMediaControlRequestResult(new Request(opcode, 0),
                        Request.Results.OPCODE_NOT_SUPPORTED);
            });
            return BluetoothGatt.GATT_SUCCESS;
        }

        if (getMediaControlPointRequestPayloadLength(opcode) != (value.length - payloadOffset)) {
            Log.w(TAG, "handleMediaControlPointRequest: " + Request.Opcodes.toString(opcode)
                    + " bad payload length");
            return BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH;
        }

        // Only some requests have payload
        int intVal = 0;
        if (opcode == Request.Opcodes.MOVE_RELATIVE
                || opcode == Request.Opcodes.GOTO_SEGMENT
                || opcode == Request.Opcodes.GOTO_TRACK
                || opcode == Request.Opcodes.GOTO_GROUP) {
            intVal = ByteBuffer.wrap(value, payloadOffset, value.length - payloadOffset)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .getInt();

            // If the argument is time interval, convert to milliseconds time domain
            if (opcode == Request.Opcodes.MOVE_RELATIVE) {
                intVal = (int) mcsIntervalToMilliseconds(intVal);
            }
        }

        Request req = new Request(opcode, intVal);
        mEventLogger.logd(
                TAG,
                "handleMediaControlPointRequest: sending "
                        + Request.Opcodes.toString(opcode)
                        + " request up");

        // TODO: Activate/deactivate devices with ActiveDeviceManager
        if (mLeAudioService == null) {
            mLeAudioService = LeAudioService.getLeAudioService();
        }
        if (!isBroadcastActive() && req.getOpcode() == Request.Opcodes.PLAY) {
            if (mAdapterService.getActiveDevices(BluetoothProfile.A2DP).size() > 0) {
                A2dpService.getA2dpService().removeActiveDevice(false);
            }
            if (mAdapterService.getActiveDevices(BluetoothProfile.HEARING_AID).size() > 0) {
                HearingAidService.getHearingAidService().removeActiveDevice(false);
            }
            if (mLeAudioService != null) {
                mLeAudioService.setActiveDevice(device);
            }
        }
        mCallbacks.onMediaControlRequest(req);

        return BluetoothGatt.GATT_SUCCESS;
    }

    public void setCallbacks(MediaControlServiceCallbacks callbacks) {
        mCallbacks = callbacks;
    }

    @VisibleForTesting
    protected void setServiceManagerForTesting(McpService manager) {
        mMcpService = manager;
    }

    @VisibleForTesting
    void setBluetoothGattServerForTesting(BluetoothGattServerProxy proxy) {
        mBluetoothGattServer = proxy;
    }

    @VisibleForTesting
    void setLeAudioServiceForTesting(LeAudioService leAudioService) {
        mLeAudioService = leAudioService;
    }

    private boolean initGattService(UUID serviceUuid) {
        mEventLogger.logd(TAG, "initGattService: uuid= " + serviceUuid);

        if (mBluetoothGattServer == null) {
            BluetoothManager manager = mContext.getSystemService(BluetoothManager.class);
            BluetoothGattServer server = manager.openGattServer(mContext, mServerCallback);
            if (server == null) {
                Log.e(TAG, "Failed to start BluetoothGattServer for MCP");
                //TODO: This now effectively makes MCP unusable, but fixes tests
                // Handle this error more gracefully, verify BluetoothInstrumentationTests
                // are passing after fix is applied
                return false;
            }
            mBluetoothGattServer = new BluetoothGattServerProxy(server, manager);
        }

        mGattService =
                new BluetoothGattService(serviceUuid, BluetoothGattService.SERVICE_TYPE_PRIMARY);

        for (Pair<UUID, CharacteristicData> entry : getUuidCharacteristicList()) {
            CharacteristicData desc = entry.second;
            UUID uuid = entry.first;
            if (VDBG) {
                Log.d(TAG, "Checking uuid: " + uuid);
            }
            if ((mFeatures & desc.featureFlag) != 0) {
                int notifyProp = (((mFeatures & desc.ntfFeatureFlag) != 0)
                        ? PROPERTY_NOTIFY
                        : 0);

                BluetoothGattCharacteristic myChar = new BluetoothGattCharacteristic(
                        uuid, desc.properties | notifyProp, desc.permissions);

                // Add CCC descriptor if notification is supported
                if ((myChar.getProperties() & PROPERTY_NOTIFY) != 0) {
                    BluetoothGattDescriptor cccDesc = new BluetoothGattDescriptor(UUID_CCCD,
                            BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED
                                    | BluetoothGattDescriptor.PERMISSION_WRITE_ENCRYPTED);
                    if (VDBG) {
                        Log.d(TAG, "Adding descriptor: " + cccDesc);
                    }
                    myChar.addDescriptor(cccDesc);
                }

                if (VDBG) {
                    Log.d(TAG, "Adding char: " + myChar);
                }
                mCharacteristics.put(desc.id, myChar);
                mGattService.addCharacteristic(myChar);
            }
        }
        if (VDBG) {
            Log.d(TAG, "Adding service: " + mGattService);
        }
        return mBluetoothGattServer.addService(mGattService);
    }

    @VisibleForTesting
    void setCcc(BluetoothDevice device, UUID charUuid, int offset, byte[] value, boolean store) {
        Map<UUID, Short> characteristicCcc = mCccDescriptorValues.get(device.getAddress());
        if (characteristicCcc == null) {
            characteristicCcc = new HashMap<>();
            mCccDescriptorValues.put(device.getAddress(), characteristicCcc);
        }

        characteristicCcc.put(charUuid,
                ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN).getShort());

        if (!store) {
            return;
        }

        if (Arrays.equals(value, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) {
            mEventLogger.logd(TAG, "setCcc: device= " + device + ", notify: " + true);
            mMcpService.setNotificationSubscription(mCcid, device, new ParcelUuid(charUuid), true);
        } else if (Arrays.equals(value, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)) {
            mEventLogger.logd(TAG, "setCcc: device= " + device + ", notify: " + false);
            mMcpService.setNotificationSubscription(mCcid, device, new ParcelUuid(charUuid), false);
        } else {
            mEventLogger.loge(TAG, "Not handled CCC value: " + Arrays.toString(value));
        }
    }

    private byte[] getCccBytes(BluetoothDevice device, UUID charUuid) {
        Map<UUID, Short> characteristicCcc = mCccDescriptorValues.get(device.getAddress());
        if (characteristicCcc != null) {
            ByteBuffer bb = ByteBuffer.allocate(Short.BYTES).order(ByteOrder.LITTLE_ENDIAN);
            Short ccc = characteristicCcc.get(charUuid);
            if (ccc != null) {
                bb.putShort(characteristicCcc.get(charUuid));
                return bb.array();
            }
        }
        return BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE;
    }

    @Override
    public void updatePlaybackState(MediaState state) {
        if (DBG) {
            Log.d(TAG, "updatePlaybackState");
        }

        if ((state.getValue() <= MediaState.STATE_MAX.getValue())
                && (state.getValue() >= MediaState.STATE_MIN.getValue())) {
            updateMediaStateChar(state.getValue());
        }
    }

    @VisibleForTesting
    int getMediaStateChar() {
        if (!isFeatureSupported(ServiceFeature.MEDIA_STATE)) return MediaState.INACTIVE.getValue();

        BluetoothGattCharacteristic stateChar =
                mCharacteristics.get(CharId.MEDIA_STATE);

        if (stateChar.getValue() != null) {
            return stateChar.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
        }

        return MediaState.INACTIVE.getValue();
    }

    @VisibleForTesting
    void updateMediaStateChar(int state) {
        if (DBG) {
            Log.d(TAG, "updateMediaStateChar: state= " + MediaState.toString(state));
        }

        if (!isFeatureSupported(ServiceFeature.MEDIA_STATE)) return;

        mEventLogger.logd(TAG, "updateMediaStateChar: state= " + MediaState.toString(state));

        BluetoothGattCharacteristic stateChar =
                mCharacteristics.get(CharId.MEDIA_STATE);
        stateChar.setValue(state, BluetoothGattCharacteristic.FORMAT_UINT8, 0);
        notifyCharacteristic(stateChar, null);
    }

    private void updateObjectID(int objectIdField, long objectIdValue, boolean notify) {
        if (DBG) {
            Log.d(TAG, "updateObjectID");
        }
        int feature = ObjectIds.GetMatchingServiceFeature(objectIdField);

        if (!isFeatureSupported(feature)) return;

        mEventLogger.logd(
                TAG,
                "updateObjectIdChar: charId= "
                        + CharId.FromFeature(feature)
                        + ", objId= "
                        + objectIdValue);
        updateObjectIdChar(mCharacteristics.get(CharId.FromFeature(feature)),
                objectIdValue, null, notify);
    }

    @Override
    public void updateObjectID(int objectIdField, long objectIdValue) {
        updateObjectID(objectIdField, objectIdValue, true);
    }

    @Override
    public void setMediaControlRequestResult(Request request,
            Request.Results resultStatus) {
        if (DBG) {
            Log.d(TAG, "setMediaControlRequestResult");
        }

        if (getMediaStateChar() == MediaState.INACTIVE.getValue()) {
            resultStatus = Request.Results.MEDIA_PLAYER_INACTIVE;
        }

        ByteBuffer bb = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN);
        bb.put((byte) request.getOpcode());
        bb.put((byte) resultStatus.getValue());

        BluetoothGattCharacteristic characteristic =
                mCharacteristics.get(CharId.MEDIA_CONTROL_POINT);
        characteristic.setValue(bb.array());
        notifyCharacteristic(characteristic, null);
    }

    @Override
    public void setSearchRequestResult(SearchRequest request,
            SearchRequest.Results resultStatus, long resultObjectId) {
        if (DBG) {
            Log.d(TAG, "setSearchRequestResult");
        }

        // TODO: There is no Object Trasfer Service implementation.
        BluetoothGattCharacteristic characteristic =
                mCharacteristics.get(CharId.SEARCH_CONTROL_POINT);
        characteristic.setValue(new byte[]{SEARCH_CONTROL_POINT_RESULT_FAILURE});
        notifyCharacteristic(characteristic, null);
    }

    @Override
    public void updatePlayerState(Map stateFields) {
        if (stateFields.isEmpty()) {
            return;
        }

        if (stateFields.containsKey(PlayerStateField.PLAYBACK_STATE)) {
            MediaState playbackState =
                    (MediaState) stateFields.get(PlayerStateField.PLAYBACK_STATE);
            if (DBG) {
                Log.d(TAG,
                        "updatePlayerState: playbackState= "
                                + stateFields.get(PlayerStateField.PLAYBACK_STATE));
            }

            if (playbackState == MediaState.INACTIVE) {
                setInitialCharacteristicValues();
            }
        }
        final boolean doNotifyValueChange = true;

        // Additional fields that may be requested by the service to complete the new state info
        List<PlayerStateField> reqFieldList = null;

        if (stateFields.containsKey(PlayerStateField.PLAYBACK_SPEED)) {
            updatePlaybackSpeedChar(
                    (float) stateFields.get(PlayerStateField.PLAYBACK_SPEED), doNotifyValueChange);
        }

        if (stateFields.containsKey(PlayerStateField.PLAYING_ORDER_SUPPORTED)) {
            updatePlayingOrderSupportedChar(
                    (Integer) stateFields.get(PlayerStateField.PLAYING_ORDER_SUPPORTED));
        }

        if (stateFields.containsKey(PlayerStateField.PLAYING_ORDER)) {
            updatePlayingOrderChar((PlayingOrder) stateFields.get(PlayerStateField.PLAYING_ORDER),
                    doNotifyValueChange);
        }

        if (stateFields.containsKey(PlayerStateField.TRACK_POSITION)) {
            updateTrackPositionChar(
                    (long) stateFields.get(PlayerStateField.TRACK_POSITION), doNotifyValueChange);
        }

        if (stateFields.containsKey(PlayerStateField.PLAYER_NAME)) {
            String name = (String) stateFields.get(PlayerStateField.PLAYER_NAME);
            if ((getPlayerNameChar() != null) && (name.compareTo(getPlayerNameChar()) != 0)) {
                updatePlayerNameChar(name, doNotifyValueChange);

                // Most likely the player has changed - request critical info fields
                reqFieldList = new ArrayList<>();
                reqFieldList.add(PlayerStateField.PLAYBACK_STATE);
                reqFieldList.add(PlayerStateField.TRACK_DURATION);

                if (isFeatureSupported(ServiceFeature.MEDIA_CONTROL_POINT_OPCODES_SUPPORTED)) {
                    reqFieldList.add(PlayerStateField.OPCODES_SUPPORTED);
                }
                if (isFeatureSupported(ServiceFeature.PLAYING_ORDER_SUPPORTED)) {
                    reqFieldList.add(PlayerStateField.PLAYING_ORDER_SUPPORTED);
                }
                if (isFeatureSupported(ServiceFeature.PLAYING_ORDER)) {
                    reqFieldList.add(PlayerStateField.PLAYING_ORDER);
                }
                if (isFeatureSupported(ServiceFeature.PLAYER_ICON_OBJ_ID)) {
                    reqFieldList.add(PlayerStateField.ICON_OBJ_ID);
                }
                if (isFeatureSupported(ServiceFeature.PLAYER_ICON_URL)) {
                    reqFieldList.add(PlayerStateField.ICON_URL);
                }
            }
        }

        if (stateFields.containsKey(PlayerStateField.ICON_URL)) {
            updatePlayerIconUrlChar((String) stateFields.get(PlayerStateField.ICON_URL));
        }

        if (stateFields.containsKey(PlayerStateField.ICON_OBJ_ID)) {
            updateIconObjIdChar((Long) stateFields.get(PlayerStateField.ICON_OBJ_ID));
        }

        if (stateFields.containsKey(PlayerStateField.OPCODES_SUPPORTED)) {
            updateSupportedOpcodesChar(
                    (Integer) stateFields.get(PlayerStateField.OPCODES_SUPPORTED),
                    doNotifyValueChange);
        }

        // Notify track change if any of these have changed
        boolean notifyTrackChange = false;
        if (stateFields.containsKey(PlayerStateField.TRACK_TITLE)) {
            String newTitle = (String) stateFields.get(PlayerStateField.TRACK_TITLE);

            if (getTrackTitleChar().compareTo(newTitle) != 0) {
                updateTrackTitleChar((String) stateFields.get(PlayerStateField.TRACK_TITLE),
                        doNotifyValueChange);
                notifyTrackChange = true;
            }
        }

        if (stateFields.containsKey(PlayerStateField.TRACK_DURATION)) {
            long newTrackDuration = (long) (stateFields.get(PlayerStateField.TRACK_DURATION));
            if (getTrackDurationChar() != newTrackDuration) {
                updateTrackDurationChar(newTrackDuration, doNotifyValueChange);
                notifyTrackChange = true;
            }
        }

        if (stateFields.containsKey(PlayerStateField.PLAYBACK_STATE)) {
            mCurrentMediaState =
                    (MediaState) stateFields.get(PlayerStateField.PLAYBACK_STATE);
        }

        int mediaState = getMediaStateChar();
        if (mediaState != mCurrentMediaState.getValue()) {
            updateMediaStateChar(mCurrentMediaState.getValue());
        }

        if (stateFields.containsKey(PlayerStateField.SEEKING_SPEED)) {
            int playbackState = getMediaStateChar();
            // Seeking speed should be 1.0f (char. value of 0) when not in seeking state.
            // [Ref. Media Control Service v1.0, sec. 3.9]
            if (playbackState == MediaState.SEEKING.getValue()) {
                updateSeekingSpeedChar((float) stateFields.get(PlayerStateField.SEEKING_SPEED),
                        doNotifyValueChange);
            } else {
                updateSeekingSpeedChar(1.0f, doNotifyValueChange);
            }
        }

        // Notify track change as the last step of all track change related characteristic changes.
        // [Ref. Media Control Service v1.0, sec. 3.4.1]
        if (notifyTrackChange) {
            if (isFeatureSupported(ServiceFeature.TRACK_CHANGED)) {
                BluetoothGattCharacteristic myChar =
                        mCharacteristics.get(CharId.TRACK_CHANGED);
                myChar.setValue(new byte[]{});
                notifyCharacteristic(myChar, null);
            }
        }

        if (reqFieldList != null) {
            // Don't ask for those that we just got.
            reqFieldList.removeAll(stateFields.keySet());

            if (!reqFieldList.isEmpty()) {
                mCallbacks.onPlayerStateRequest(
                        reqFieldList.stream().toArray(PlayerStateField[]::new));
            }
        }
    }

    @Override
    public int getContentControlId() {
        return mCcid;
    }

    @Override
    public UUID getServiceUuid() {
        if (mGattService != null) {
            return mGattService.getUuid();
        }
        return new UUID(0, 0);
    }

    @Override
    public void onDeviceAuthorizationSet(BluetoothDevice device) {
        int auth = getDeviceAuthorization(device);
        mEventLogger.logd(
                TAG,
                "onDeviceAuthorizationSet: device= "
                        + device
                        + ", authorization= "
                        + (auth == BluetoothDevice.ACCESS_ALLOWED
                                ? "ALLOWED"
                                : (auth == BluetoothDevice.ACCESS_REJECTED
                                        ? "REJECTED"
                                        : "UNKNOWN")));
        ProcessPendingGattOperations(device);
        for (BluetoothGattCharacteristic characteristic : mCharacteristics.values()) {
            // Notify only the updated characteristics
            if (characteristic.getValue() != null) {
                notifyCharacteristic(device, characteristic);
            }
        }
    }

    @Override
    public void destroy() {
        if (DBG) {
            Log.d(TAG, "Destroy");
        }

        mAdapterService.unregisterBluetoothStateCallback(mBluetoothStateChangeCallback);

        if (mBluetoothGattServer == null) {
            return;
        }

        if (mBluetoothGattServer.removeService(mGattService)) {
            if (mCallbacks != null) {
                mCallbacks.onServiceInstanceUnregistered(ServiceStatus.OK);
            }
        }

        mBluetoothGattServer.close();
    }

    @VisibleForTesting
    void updatePlayingOrderChar(PlayingOrder order, boolean notify) {
        if (VDBG) {
            Log.d(TAG, "updatePlayingOrderChar: order= " + order);
        }
        if (!isFeatureSupported(ServiceFeature.PLAYING_ORDER)) return;

        BluetoothGattCharacteristic orderChar = mCharacteristics.get(CharId.PLAYING_ORDER);
        Integer playingOrder = null;

        if (orderChar.getValue() != null) {
            playingOrder = orderChar.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
        }

        if ((playingOrder == null) || (playingOrder != order.getValue())) {
            orderChar.setValue(order.getValue(), BluetoothGattCharacteristic.FORMAT_UINT8, 0);
            if (notify && isFeatureSupported(ServiceFeature.PLAYING_ORDER_NOTIFY)) {
                notifyCharacteristic(orderChar, null);
            }
            mEventLogger.logd(TAG, "updatePlayingOrderChar: order= " + order);
        }
    }

    private void notifyCharacteristic(
            @NonNull BluetoothDevice device, @NonNull BluetoothGattCharacteristic characteristic) {
        if (!mBluetoothGattServer.isDeviceConnected(device)) return;
        if (getDeviceAuthorization(device) != BluetoothDevice.ACCESS_ALLOWED) return;

        Map<UUID, Short> charCccMap = mCccDescriptorValues.get(device.getAddress());
        if (charCccMap == null) return;

        byte[] ccc = getCccBytes(device, characteristic.getUuid());
        if (VDBG) {
                Log.d(
                        TAG,
                        "notifyCharacteristic: char= "
                                + characteristic.getUuid().toString()
                                + " cccVal= "
                                + ByteBuffer.wrap(ccc).order(ByteOrder.LITTLE_ENDIAN).getShort());
        }
        if (!Arrays.equals(ccc, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) return;

        if (VDBG) Log.d(TAG, "notifyCharacteristic: sending notification");
        mBluetoothGattServer.notifyCharacteristicChanged(device, characteristic, false);
    }

    private void notifyCharacteristic(
            @NonNull BluetoothGattCharacteristic characteristic,
            @Nullable BluetoothDevice originDevice) {
        for (BluetoothDevice device : mBluetoothGattServer.getConnectedDevices()) {
            // Skip the origin device who changed the characteristic
            if (device.equals(originDevice)) {
                continue;
            }
            notifyCharacteristic(device, characteristic);
        }
    }

    private static int SpeedFloatToCharacteristicIntValue(float speed) {
        /* The spec. defined valid speed range is <0.25, 3.957> as float input, resulting in
         * <-128, 127> output integer range. */
        if (speed < 0) {
            speed = -speed;
        }
        if (speed < PLAY_SPEED_MIN) {
            speed = PLAY_SPEED_MIN;
        } else if (speed > PLAY_SPEED_MAX) {
            speed = PLAY_SPEED_MAX;
        }

        return (int) (64 * Math.log(speed) / Math.log(2));
    }

    private static float CharacteristicSpeedIntValueToSpeedFloat(Integer speed) {
        return (float) (Math.pow(2, (speed.floatValue() / 64.0f)));
    }

    @VisibleForTesting
    Float getSeekingSpeedChar() {
        Float speed = null;

        if (isFeatureSupported(ServiceFeature.SEEKING_SPEED)) {
            BluetoothGattCharacteristic characteristic =
                    mCharacteristics.get(CharId.SEEKING_SPEED);
            if (characteristic.getValue() != null) {
                Integer intVal =
                        characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT8, 0);
                speed = CharacteristicSpeedIntValueToSpeedFloat(intVal);
            }
        }

        return speed;
    }

    @VisibleForTesting
    void updateSeekingSpeedChar(float speed, boolean notify) {
        if (VDBG) {
            Log.d(TAG, "updateSeekingSpeedChar: speed= " + speed);
        }
        if (isFeatureSupported(ServiceFeature.SEEKING_SPEED)) {
            if ((getSeekingSpeedChar() == null) || (getSeekingSpeedChar() != speed)) {
                BluetoothGattCharacteristic characteristic =
                        mCharacteristics.get(CharId.SEEKING_SPEED);
                int intSpeed = SpeedFloatToCharacteristicIntValue(speed);
                characteristic.setValue(intSpeed, BluetoothGattCharacteristic.FORMAT_SINT8, 0);
                if (notify && isFeatureSupported(ServiceFeature.SEEKING_SPEED_NOTIFY)) {
                    notifyCharacteristic(characteristic, null);
                }
                mEventLogger.logd(
                        TAG,
                        "updateSeekingSpeedChar: intSpeed=" + intSpeed + ", speed= " + speed);
            }
        }
    }

    @VisibleForTesting
    Float getPlaybackSpeedChar() {
        Float speed = null;

        if (!isFeatureSupported(ServiceFeature.PLAYBACK_SPEED)) return null;

        BluetoothGattCharacteristic characteristic = mCharacteristics.get(CharId.PLAYBACK_SPEED);
        if (characteristic.getValue() != null) {
            Integer intVal =
                    characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT8, 0);
            speed = CharacteristicSpeedIntValueToSpeedFloat(intVal);
        }

        return speed;
    }

    @VisibleForTesting
    void updatePlaybackSpeedChar(float speed, boolean notify) {
        if (VDBG) {
            Log.d(TAG, "updatePlaybackSpeedChar: " + speed);
        }

        if (!isFeatureSupported(ServiceFeature.PLAYBACK_SPEED)) return;

        // Reject if no changes were made
        if ((getPlaybackSpeedChar() == null) || (getPlaybackSpeedChar() != speed)) {
            BluetoothGattCharacteristic characteristic =
                    mCharacteristics.get(CharId.PLAYBACK_SPEED);
            int intSpeed = SpeedFloatToCharacteristicIntValue(speed);
            characteristic.setValue(intSpeed, BluetoothGattCharacteristic.FORMAT_SINT8, 0);
            if (notify && isFeatureSupported(ServiceFeature.PLAYBACK_SPEED_NOTIFY)) {
                notifyCharacteristic(characteristic, null);
            }
            mEventLogger.logd(
                    TAG,
                    "updatePlaybackSpeedChar: intSpeed=" + intSpeed + ", speed= " + speed);
        }
    }

    @VisibleForTesting
    void updateTrackPositionChar(long positionMs, boolean forceNotify) {
        if (VDBG) {
            Log.d(TAG, "updateTrackPositionChar: " + positionMs);
        }
        if (!isFeatureSupported(ServiceFeature.TRACK_POSITION)) return;

        final int position =
                (positionMs != TRACK_POSITION_UNAVAILABLE)
                        ? (int) millisecondsToMcsInterval(positionMs)
                        : INTERVAL_UNAVAILABLE;

        BluetoothGattCharacteristic characteristic =
                mCharacteristics.get(CharId.TRACK_POSITION);
        characteristic.setValue(position, BluetoothGattCharacteristic.FORMAT_SINT32, 0);

        if (isFeatureSupported(ServiceFeature.TRACK_POSITION_NOTIFY)) {
            // Position should be notified only while seeking (frequency is implementation
            // specific), on pause, or position change, but not during the playback.
            if ((getMediaStateChar() == MediaState.PAUSED.getValue())
                    || (getMediaStateChar() == MediaState.SEEKING.getValue())
                    || forceNotify) {
                notifyCharacteristic(characteristic, null);
            }
        }
        mEventLogger.logd(
                TAG,
                "updateTrackPositionChar: positionMs= " + positionMs + ", position= " + position);
    }

    private long getTrackDurationChar() {
        if (!isFeatureSupported(ServiceFeature.TRACK_DURATION)) return TRACK_DURATION_UNAVAILABLE;

        BluetoothGattCharacteristic characteristic = mCharacteristics.get(CharId.TRACK_DURATION);
        if (characteristic.getValue() != null) {
            int duration =
                    characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_SINT32, 0);
            return (duration != INTERVAL_UNAVAILABLE) ? mcsIntervalToMilliseconds(duration)
                    : TRACK_DURATION_UNAVAILABLE;
        }
        return TRACK_DURATION_UNAVAILABLE;
    }

    @VisibleForTesting
    void updateTrackDurationChar(long durationMs, boolean notify) {
        if (VDBG) {
            Log.d(TAG, "updateTrackDurationChar: " + durationMs);
        }
        if (isFeatureSupported(ServiceFeature.TRACK_DURATION)) {
            final int duration =
                    (durationMs != TRACK_DURATION_UNAVAILABLE)
                            ? (int) millisecondsToMcsInterval(durationMs)
                            : INTERVAL_UNAVAILABLE;

            BluetoothGattCharacteristic characteristic =
                    mCharacteristics.get(CharId.TRACK_DURATION);
            characteristic.setValue(duration, BluetoothGattCharacteristic.FORMAT_SINT32, 0);
            if (notify && isFeatureSupported(ServiceFeature.TRACK_DURATION_NOTIFY)) {
                notifyCharacteristic(characteristic, null);
            }
            mEventLogger.logd(
                    TAG,
                    "updateTrackDurationChar: durationMs= "
                            + durationMs
                            + ", duration= "
                            + duration);
        }
    }

    private String getTrackTitleChar() {
        if (isFeatureSupported(ServiceFeature.TRACK_TITLE)) {
            BluetoothGattCharacteristic characteristic =
                    mCharacteristics.get(CharId.TRACK_TITLE);
            if (characteristic.getValue() != null) {
                return characteristic.getStringValue(0);
            }
        }

        return "";
    }

    @VisibleForTesting
    void updateTrackTitleChar(String title, boolean notify) {
        if (VDBG) {
            Log.d(TAG, "updateTrackTitleChar: " + title);
        }
        if (isFeatureSupported(ServiceFeature.TRACK_TITLE)) {
            BluetoothGattCharacteristic characteristic =
                    mCharacteristics.get(CharId.TRACK_TITLE);
            characteristic.setValue(title);
            if (notify && isFeatureSupported(ServiceFeature.TRACK_TITLE_NOTIFY)) {
                notifyCharacteristic(characteristic, null);
            }
            mEventLogger.logd(TAG, "updateTrackTitleChar: title= '" + title + "'");
        }
    }

    @VisibleForTesting
    void updateSupportedOpcodesChar(int opcodes, boolean notify) {
        if (VDBG) {
            Log.d(
                    TAG,
                    "updateSupportedOpcodesChar: opcodes= "
                            + Request.SupportedOpcodes.toString(opcodes));
        }
        if (!isFeatureSupported(ServiceFeature.MEDIA_CONTROL_POINT_OPCODES_SUPPORTED)) return;

        BluetoothGattCharacteristic characteristic = mCharacteristics.get(
                CharId.MEDIA_CONTROL_POINT_OPCODES_SUPPORTED);
        // Do nothing if nothing has changed
        if (characteristic.getValue() != null
                && characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32, 0) == opcodes) {
            return;
        }

        characteristic.setValue(opcodes, BluetoothGattCharacteristic.FORMAT_UINT32, 0);
        if (notify
                && isFeatureSupported(
                ServiceFeature.MEDIA_CONTROL_POINT_OPCODES_SUPPORTED_NOTIFY)) {
            notifyCharacteristic(characteristic, null);
        }
        mEventLogger.logd(
                TAG,
                "updateSupportedOpcodesChar: opcodes= "
                        + Request.SupportedOpcodes.toString(opcodes));
    }

    @VisibleForTesting
    void updatePlayingOrderSupportedChar(int supportedOrder) {
        if (VDBG) {
            Log.d(TAG, "updatePlayingOrderSupportedChar: " + supportedOrder);
        }
        if (isFeatureSupported(ServiceFeature.PLAYING_ORDER_SUPPORTED)) {
            mCharacteristics.get(CharId.PLAYING_ORDER_SUPPORTED)
                    .setValue(supportedOrder, BluetoothGattCharacteristic.FORMAT_UINT16, 0);
            mEventLogger.logd(TAG, "updatePlayingOrderSupportedChar: order= " + supportedOrder);
        }
    }

    private void updateIconObjIdChar(Long objId) {
        if (isFeatureSupported(ServiceFeature.PLAYER_ICON_OBJ_ID)) {
            mEventLogger.logd(
                    TAG,
                    "updateObjectIdChar charId= "
                            + CharId.PLAYER_ICON_OBJ_ID
                            + ", objId= "
                            + objId);
            updateObjectIdChar(mCharacteristics.get(CharId.PLAYER_ICON_OBJ_ID), objId,
                    null, true);
        }
    }

    @VisibleForTesting
    public long byteArray2ObjId(byte[] buffer) {
        ByteBuffer bb = ByteBuffer.allocate(Long.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        bb.put(buffer, 0, 6);
        // Move position to beginnng after putting data to buffer
        bb.position(0);
        return bb.getLong();
    }

    @VisibleForTesting
    public byte[] objId2ByteArray(long objId) {
        if (objId < 0) {
            return new byte[0];
        }

        ByteBuffer bb = ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN);
        bb.putInt((int) objId);
        bb.putShort((short) (objId >> Integer.SIZE));

        return bb.array();
    }

    private void updateObjectIdChar(BluetoothGattCharacteristic characteristic, long objId,
            BluetoothDevice originDevice, boolean notify) {
        characteristic.setValue(objId2ByteArray(objId));
        if ((characteristic.getProperties() & PROPERTY_NOTIFY) != 0) {
            // Notify all clients but not the originDevice
            if (notify) {
                notifyCharacteristic(characteristic, originDevice);
            }
        }
    }

    private void updatePlayerIconUrlChar(String url) {
        if (VDBG) {
            Log.d(TAG, "updatePlayerIconUrlChar: " + url);
        }
        if (isFeatureSupported(ServiceFeature.PLAYER_ICON_URL)) {
            mCharacteristics.get(CharId.PLAYER_ICON_URL).setValue(url);
            mEventLogger.logd(TAG, "updatePlayerIconUrlChar: " + url);
        }
    }

    private String getPlayerNameChar() {
        if (!isFeatureSupported(ServiceFeature.PLAYER_NAME)) return null;

        BluetoothGattCharacteristic characteristic = mCharacteristics.get(CharId.PLAYER_NAME);
        if (characteristic.getValue() != null) {
            return characteristic.getStringValue(0);
        }
        return null;
    }

    @VisibleForTesting
    void updatePlayerNameChar(String name, boolean notify) {
        if (VDBG) {
            Log.d(TAG, "updatePlayerNameChar: " + name);
        }

        if (!isFeatureSupported(ServiceFeature.PLAYER_NAME)) return;

        BluetoothGattCharacteristic characteristic =
                mCharacteristics.get(CharId.PLAYER_NAME);
        characteristic.setValue(name);
        mEventLogger.logd(TAG, "updatePlayerNameChar: name= '" + name + "'");
        if (notify && isFeatureSupported(ServiceFeature.PLAYER_NAME_NOTIFY)) {
            notifyCharacteristic(characteristic, null);
        }
    }

    private boolean isFeatureSupported(long featureBit) {
        if (DBG) {
            Log.w(TAG, "Feature " + ServiceFeature.toString(featureBit) + " support: "
                    + ((mFeatures & featureBit) != 0));
        }
        return (mFeatures & featureBit) != 0;
    }

    /**
     * Checks if le audio broadcasting is ON
     *
     * @return {@code true} if is broadcasting audio, {@code false} otherwise
     */
    private boolean isBroadcastActive() {
        if (!Flags.leaudioBroadcastFeatureSupport()) {
            // disable this if feature flag is false
            return false;
        }

        return mLeAudioService != null && mLeAudioService.isBroadcastActive();
    }

    @VisibleForTesting
    boolean isOpcodeSupported(int opcode) {
        if (opcode < Request.Opcodes.PLAY || opcode > Request.Opcodes.GOTO_GROUP) {
            return false;
        }

        if (!isFeatureSupported(ServiceFeature.MEDIA_CONTROL_POINT_OPCODES_SUPPORTED)) {
            return false;
        }

        Integer opcodeSupportBit = Request.OpcodeToOpcodeSupport.get(opcode);
        if (opcodeSupportBit == null) return false;

        return (mCharacteristics.get(CharId.MEDIA_CONTROL_POINT_OPCODES_SUPPORTED)
                    .getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32, 0)
                & opcodeSupportBit) == opcodeSupportBit;
    }

    private interface CharacteristicWriteHandler {
        void onCharacteristicWriteRequest(BluetoothDevice device, int requestId,
                BluetoothGattCharacteristic characteristic, boolean preparedWrite,
                boolean responseNeeded, int offset, byte[] value);
    }

    private static final class CharacteristicData {
        public final int id;
        public final int properties;
        public final int permissions;
        public final long featureFlag;
        public final long ntfFeatureFlag;

        private CharacteristicData(
                int id, long featureFlag, long ntfFeatureFlag, int properties, int permissions) {
            this.id = id;
            this.featureFlag = featureFlag;
            this.ntfFeatureFlag = ntfFeatureFlag;
            this.properties = properties;
            this.permissions = permissions;
        }
    }

    private final static class CharId {
        public static final int PLAYER_NAME =
                Long.numberOfTrailingZeros(ServiceFeature.PLAYER_NAME);
        public static final int PLAYER_ICON_OBJ_ID =
                Long.numberOfTrailingZeros(ServiceFeature.PLAYER_ICON_OBJ_ID);
        public static final int PLAYER_ICON_URL =
                Long.numberOfTrailingZeros(ServiceFeature.PLAYER_ICON_URL);
        public static final int TRACK_CHANGED =
                Long.numberOfTrailingZeros(ServiceFeature.TRACK_CHANGED);
        public static final int TRACK_TITLE =
                Long.numberOfTrailingZeros(ServiceFeature.TRACK_TITLE);
        public static final int TRACK_DURATION =
                Long.numberOfTrailingZeros(ServiceFeature.TRACK_DURATION);
        public static final int TRACK_POSITION =
                Long.numberOfTrailingZeros(ServiceFeature.TRACK_POSITION);
        public static final int PLAYBACK_SPEED =
                Long.numberOfTrailingZeros(ServiceFeature.PLAYBACK_SPEED);
        public static final int SEEKING_SPEED =
                Long.numberOfTrailingZeros(ServiceFeature.SEEKING_SPEED);
        public static final int CURRENT_TRACK_SEGMENT_OBJ_ID =
                Long.numberOfTrailingZeros(ServiceFeature.CURRENT_TRACK_SEGMENT_OBJ_ID);
        public static final int CURRENT_TRACK_OBJ_ID =
                Long.numberOfTrailingZeros(ServiceFeature.CURRENT_TRACK_OBJ_ID);
        public static final int NEXT_TRACK_OBJ_ID =
                Long.numberOfTrailingZeros(ServiceFeature.NEXT_TRACK_OBJ_ID);
        public static final int CURRENT_GROUP_OBJ_ID =
                Long.numberOfTrailingZeros(ServiceFeature.CURRENT_GROUP_OBJ_ID);
        public static final int PARENT_GROUP_OBJ_ID =
                Long.numberOfTrailingZeros(ServiceFeature.PARENT_GROUP_OBJ_ID);
        public static final int PLAYING_ORDER =
                Long.numberOfTrailingZeros(ServiceFeature.PLAYING_ORDER);
        public static final int PLAYING_ORDER_SUPPORTED =
                Long.numberOfTrailingZeros(ServiceFeature.PLAYING_ORDER_SUPPORTED);
        public static final int MEDIA_STATE =
                Long.numberOfTrailingZeros(ServiceFeature.MEDIA_STATE);
        public static final int MEDIA_CONTROL_POINT =
                Long.numberOfTrailingZeros(ServiceFeature.MEDIA_CONTROL_POINT);
        public static final int MEDIA_CONTROL_POINT_OPCODES_SUPPORTED =
                Long.numberOfTrailingZeros(ServiceFeature.MEDIA_CONTROL_POINT_OPCODES_SUPPORTED);
        public static final int SEARCH_RESULT_OBJ_ID =
                Long.numberOfTrailingZeros(ServiceFeature.SEARCH_RESULT_OBJ_ID);
        public static final int SEARCH_CONTROL_POINT =
                Long.numberOfTrailingZeros(ServiceFeature.SEARCH_CONTROL_POINT);
        public static final int CONTENT_CONTROL_ID =
                Long.numberOfTrailingZeros(ServiceFeature.CONTENT_CONTROL_ID);

        public static int FromFeature(long feature) {
            return Long.numberOfTrailingZeros(feature);
        }
    }

    private static final UUID UUID_CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    /* All characteristic attributes (UUIDs, properties, permissions and flags needed to enable
     * them) This is set according to the Media Control Service Specification.
     */
    private static List<Pair<UUID, CharacteristicData>> getUuidCharacteristicList() {
        List<Pair<UUID, CharacteristicData>> characteristics = new ArrayList<>();
        characteristics.add(new Pair<>(UUID_PLAYER_NAME,
                new CharacteristicData(CharId.PLAYER_NAME, ServiceFeature.PLAYER_NAME,
                        ServiceFeature.PLAYER_NAME_NOTIFY, PROPERTY_READ,
                        PERMISSION_READ_ENCRYPTED)));
        characteristics.add(new Pair<>(UUID_PLAYER_ICON_OBJ_ID,
                new CharacteristicData(CharId.PLAYER_ICON_OBJ_ID, ServiceFeature.PLAYER_ICON_OBJ_ID,
                        // Notifications unsupported
                        0, PROPERTY_READ, PERMISSION_READ_ENCRYPTED)));
        characteristics.add(new Pair<>(UUID_PLAYER_ICON_URL,
                new CharacteristicData(CharId.PLAYER_ICON_URL, ServiceFeature.PLAYER_ICON_URL,
                        // Notifications unsupported
                        0, PROPERTY_READ, PERMISSION_READ_ENCRYPTED)));
        characteristics.add(new Pair<>(UUID_TRACK_CHANGED,
                new CharacteristicData(CharId.TRACK_CHANGED, ServiceFeature.TRACK_CHANGED,
                        // Mandatory notification if char. exists.
                        ServiceFeature.TRACK_CHANGED, PROPERTY_NOTIFY, 0)));
        characteristics.add(new Pair<>(UUID_TRACK_TITLE,
                new CharacteristicData(CharId.TRACK_TITLE, ServiceFeature.TRACK_TITLE,
                        ServiceFeature.TRACK_TITLE_NOTIFY, PROPERTY_READ,
                        PERMISSION_READ_ENCRYPTED)));
        characteristics.add(new Pair<>(UUID_TRACK_DURATION,
                new CharacteristicData(CharId.TRACK_DURATION, ServiceFeature.TRACK_DURATION,
                        ServiceFeature.TRACK_DURATION_NOTIFY, PROPERTY_READ,
                        PERMISSION_READ_ENCRYPTED)));
        characteristics.add(new Pair<>(UUID_TRACK_POSITION,
                new CharacteristicData(CharId.TRACK_POSITION, ServiceFeature.TRACK_POSITION,
                        ServiceFeature.TRACK_POSITION_NOTIFY,
                        PROPERTY_READ | PROPERTY_WRITE | PROPERTY_WRITE_NO_RESPONSE,
                        PERMISSION_READ_ENCRYPTED | PERMISSION_WRITE_ENCRYPTED)));
        characteristics.add(new Pair<>(UUID_PLAYBACK_SPEED,
                new CharacteristicData(CharId.PLAYBACK_SPEED, ServiceFeature.PLAYBACK_SPEED,
                        ServiceFeature.PLAYBACK_SPEED_NOTIFY,
                        PROPERTY_READ | PROPERTY_WRITE | PROPERTY_WRITE_NO_RESPONSE,
                        PERMISSION_READ_ENCRYPTED | PERMISSION_WRITE_ENCRYPTED)));
        characteristics.add(new Pair<>(UUID_SEEKING_SPEED,
                new CharacteristicData(CharId.SEEKING_SPEED, ServiceFeature.SEEKING_SPEED,
                        ServiceFeature.SEEKING_SPEED_NOTIFY, PROPERTY_READ,
                        PERMISSION_READ_ENCRYPTED)));
        characteristics.add(new Pair<>(UUID_CURRENT_TRACK_SEGMENT_OBJ_ID,
                new CharacteristicData(CharId.CURRENT_TRACK_SEGMENT_OBJ_ID,
                        ServiceFeature.CURRENT_TRACK_SEGMENT_OBJ_ID,
                        // Notifications unsupported
                        0, PROPERTY_READ, PERMISSION_READ_ENCRYPTED)));
        characteristics.add(new Pair<>(UUID_CURRENT_TRACK_OBJ_ID,
                new CharacteristicData(CharId.CURRENT_TRACK_OBJ_ID,
                        ServiceFeature.CURRENT_TRACK_OBJ_ID,
                        ServiceFeature.CURRENT_TRACK_OBJ_ID_NOTIFY,
                        PROPERTY_READ | PROPERTY_WRITE | PROPERTY_WRITE_NO_RESPONSE,
                        PERMISSION_READ_ENCRYPTED | PERMISSION_WRITE_ENCRYPTED)));
        characteristics.add(new Pair<>(UUID_NEXT_TRACK_OBJ_ID,
                new CharacteristicData(CharId.NEXT_TRACK_OBJ_ID, ServiceFeature.NEXT_TRACK_OBJ_ID,
                        ServiceFeature.NEXT_TRACK_OBJ_ID_NOTIFY,
                        PROPERTY_READ | PROPERTY_WRITE | PROPERTY_WRITE_NO_RESPONSE,
                        PERMISSION_READ_ENCRYPTED | PERMISSION_WRITE_ENCRYPTED)));
        characteristics.add(new Pair<>(UUID_CURRENT_GROUP_OBJ_ID,
                new CharacteristicData(CharId.CURRENT_GROUP_OBJ_ID,
                        ServiceFeature.CURRENT_GROUP_OBJ_ID,
                        ServiceFeature.CURRENT_GROUP_OBJ_ID_NOTIFY,
                        PROPERTY_READ | PROPERTY_WRITE | PROPERTY_WRITE_NO_RESPONSE,
                        PERMISSION_READ_ENCRYPTED | PERMISSION_WRITE_ENCRYPTED)));
        characteristics.add(new Pair<>(UUID_PARENT_GROUP_OBJ_ID,
                new CharacteristicData(CharId.PARENT_GROUP_OBJ_ID,
                        ServiceFeature.PARENT_GROUP_OBJ_ID,
                        ServiceFeature.PARENT_GROUP_OBJ_ID_NOTIFY, PROPERTY_READ,
                        PERMISSION_READ_ENCRYPTED)));
        characteristics.add(new Pair<>(UUID_PLAYING_ORDER,
                new CharacteristicData(CharId.PLAYING_ORDER, ServiceFeature.PLAYING_ORDER,
                        ServiceFeature.PLAYING_ORDER_NOTIFY,
                        PROPERTY_READ | PROPERTY_WRITE | PROPERTY_WRITE_NO_RESPONSE,
                        PERMISSION_READ_ENCRYPTED | PERMISSION_WRITE_ENCRYPTED)));
        characteristics.add(new Pair<>(UUID_PLAYING_ORDER_SUPPORTED,
                new CharacteristicData(CharId.PLAYING_ORDER_SUPPORTED,
                        ServiceFeature.PLAYING_ORDER_SUPPORTED,
                        // Notifications unsupported
                        0, PROPERTY_READ, PERMISSION_READ_ENCRYPTED)));
        characteristics.add(new Pair<>(UUID_MEDIA_STATE,
                new CharacteristicData(CharId.MEDIA_STATE, ServiceFeature.MEDIA_STATE,
                        // Mandatory notification if char. exists.
                        ServiceFeature.MEDIA_STATE, PROPERTY_READ | PROPERTY_NOTIFY,
                        PERMISSION_READ_ENCRYPTED)));
        characteristics.add(new Pair<>(UUID_MEDIA_CONTROL_POINT,
                new CharacteristicData(CharId.MEDIA_CONTROL_POINT,
                        ServiceFeature.MEDIA_CONTROL_POINT,
                        // Mandatory notification if char. exists.
                        ServiceFeature.MEDIA_CONTROL_POINT,
                        PROPERTY_WRITE | PROPERTY_WRITE_NO_RESPONSE | PROPERTY_NOTIFY,
                        PERMISSION_WRITE_ENCRYPTED)));
        characteristics.add(new Pair<>(UUID_MEDIA_CONTROL_POINT_OPCODES_SUPPORTED,
                new CharacteristicData(CharId.MEDIA_CONTROL_POINT_OPCODES_SUPPORTED,
                        ServiceFeature.MEDIA_CONTROL_POINT_OPCODES_SUPPORTED,
                        ServiceFeature.MEDIA_CONTROL_POINT_OPCODES_SUPPORTED_NOTIFY, PROPERTY_READ,
                        PERMISSION_READ_ENCRYPTED)));
        characteristics.add(new Pair<>(UUID_SEARCH_RESULT_OBJ_ID,
                new CharacteristicData(CharId.SEARCH_RESULT_OBJ_ID,
                        ServiceFeature.SEARCH_RESULT_OBJ_ID,
                        // Mandatory notification if char. exists.
                        ServiceFeature.SEARCH_RESULT_OBJ_ID, PROPERTY_READ | PROPERTY_NOTIFY,
                        PERMISSION_READ_ENCRYPTED)));
        characteristics.add(new Pair<>(UUID_SEARCH_CONTROL_POINT,
                new CharacteristicData(CharId.SEARCH_CONTROL_POINT,
                        ServiceFeature.SEARCH_CONTROL_POINT,
                        // Mandatory notification if char. exists.
                        ServiceFeature.SEARCH_CONTROL_POINT,
                        PROPERTY_WRITE | PROPERTY_WRITE_NO_RESPONSE | PROPERTY_NOTIFY,
                        PERMISSION_WRITE_ENCRYPTED)));
        characteristics.add(new Pair<>(UUID_CONTENT_CONTROL_ID,
                new CharacteristicData(CharId.CONTENT_CONTROL_ID, ServiceFeature.CONTENT_CONTROL_ID,
                        // Notifications unsupported
                        0, PROPERTY_READ, PERMISSION_READ_ENCRYPTED)));
        return characteristics;
    }

    public void dump(StringBuilder sb) {
        sb.append("\tMediaControlService instance current state:");
        sb.append("\n\t\tCcid = " + mCcid);
        sb.append("\n\t\tFeatures:" + ServiceFeature.featuresToString(mFeatures, "\n\t\t\t"));

        BluetoothGattCharacteristic characteristic =
                mCharacteristics.get(CharId.PLAYER_NAME);
        if (characteristic == null) {
            sb.append("\n\t\tPlayer name: <No Player>");
        } else {
            sb.append("\n\t\tPlayer name: " + characteristic.getStringValue(0));
        }

        sb.append("\n\t\tCurrentPlaybackState = " + mCurrentMediaState);
        for (Map.Entry<String, Map<UUID, Short>> deviceEntry : mCccDescriptorValues.entrySet()) {
            sb.append("\n\t\tCCC states for device: " + "xx:xx:xx:xx:"
                    + deviceEntry.getKey().substring(12));
            for (Map.Entry<UUID, Short> entry : deviceEntry.getValue().entrySet()) {
                sb.append("\n\t\t\tCharacteristic: " + mcsUuidToString(entry.getKey()) + ", value: "
                        + Utils.cccIntToStr(entry.getValue()));
            }
        }

        sb.append("\n\n");
        mEventLogger.dump(sb);
    }
}
