/*
 * Copyright 2020 HIMSA II K/S - www.himsa.com.
 * Represented by EHIMA - www.ehima.com
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

package com.android.bluetooth.le_audio;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothLeAudioCodecConfig;
import android.bluetooth.BluetoothLeBroadcastMetadata;

import java.util.List;

/**
 * Stack event sent via a callback from JNI to Java, or generated
 * internally by the LeAudio State Machine.
 */
public class LeAudioStackEvent {
    // Event types for STACK_EVENT message (coming from native in bt_le_audio.h)
    private static final int EVENT_TYPE_NONE = 0;
    public static final int EVENT_TYPE_CONNECTION_STATE_CHANGED = 1;
    public static final int EVENT_TYPE_GROUP_STATUS_CHANGED = 2;
    public static final int EVENT_TYPE_GROUP_NODE_STATUS_CHANGED = 3;
    public static final int EVENT_TYPE_AUDIO_CONF_CHANGED = 4;
    public static final int EVENT_TYPE_SINK_AUDIO_LOCATION_AVAILABLE = 5;
    public static final int EVENT_TYPE_AUDIO_LOCAL_CODEC_CONFIG_CAPA_CHANGED = 6;
    public static final int EVENT_TYPE_AUDIO_GROUP_CURRENT_CODEC_CONFIG_CHANGED = 7;
    public static final int EVENT_TYPE_AUDIO_GROUP_SELECTABLE_CODEC_CONFIG_CHANGED = 8;
    public static final int EVENT_TYPE_NATIVE_INITIALIZED = 9;
    public static final int EVENT_TYPE_HEALTH_BASED_DEV_RECOMMENDATION = 10;
    public static final int EVENT_TYPE_HEALTH_BASED_GROUP_RECOMMENDATION = 11;
    public static final int EVENT_TYPE_UNICAST_MONITOR_MODE_STATUS = 12;
    public static final int EVENT_TYPE_GROUP_STREAM_STATUS_CHANGED = 13;
    // -------- DO NOT PUT ANY NEW UNICAST EVENTS BELOW THIS LINE-------------
    public static final int EVENT_TYPE_UNICAST_MAX = 14;

    // Broadcast related events
    public static final int EVENT_TYPE_BROADCAST_CREATED = EVENT_TYPE_UNICAST_MAX + 1;
    public static final int EVENT_TYPE_BROADCAST_DESTROYED = EVENT_TYPE_UNICAST_MAX + 2;
    public static final int EVENT_TYPE_BROADCAST_STATE = EVENT_TYPE_UNICAST_MAX + 3;
    public static final int EVENT_TYPE_BROADCAST_METADATA_CHANGED = EVENT_TYPE_UNICAST_MAX + 4;

    // Do not modify without updating the HAL bt_le_audio.h files.
    // Match up with GroupStatus enum of bt_le_audio.h
    static final int CONNECTION_STATE_DISCONNECTED = 0;
    static final int CONNECTION_STATE_CONNECTING = 1;
    static final int CONNECTION_STATE_CONNECTED = 2;
    static final int CONNECTION_STATE_DISCONNECTING = 3;

    // Health based recommendation
    static final int HEALTH_RECOMMENDATION_ACTION_NONE = 0;
    static final int HEALTH_RECOMMENDATION_ACTION_DISABLE = 1;
    static final int HEALTH_RECOMMENDATION_ACTION_CONSIDER_DISABLING = 2;
    static final int HEALTH_RECOMMENDATION_ACTION_INACTIVATE_GROUP = 3;

    static final int GROUP_STATUS_INACTIVE = 0;
    static final int GROUP_STATUS_ACTIVE = 1;
    static final int GROUP_STATUS_TURNED_IDLE_DURING_CALL = 2;

    static final int GROUP_NODE_ADDED = 1;
    static final int GROUP_NODE_REMOVED = 2;

    // Do not modify without updating the HAL bt_le_audio.h files.
    // Match up with BroadcastState enum of bt_le_audio.h
    static final int BROADCAST_STATE_STOPPED = 0;
    static final int BROADCAST_STATE_CONFIGURING = 1;
    static final int BROADCAST_STATE_PAUSED = 2;
    static final int BROADCAST_STATE_STOPPING = 3;
    static final int BROADCAST_STATE_STREAMING = 4;

    // Do not modify without updating the HAL bt_le_audio.h files.
    // Match up with UnicastMonitorModeStatus enum of bt_le_audio.h
    static final int STATUS_LOCAL_STREAM_REQUESTED = 0;
    static final int STATUS_LOCAL_STREAM_STREAMING = 1;
    static final int STATUS_LOCAL_STREAM_SUSPENDED = 2;

    // Do not modify without updating le_audio_types.h
    // Match up with defines of le_audio_types.h
    static final int DIRECTION_SINK = 1;
    static final int DIRECTION_SOURCE = 2;

    static final int GROUP_STREAM_STATUS_IDLE = 0;
    static final int GROUP_STREAM_STATUS_STREAMING = 1;

    public int type = EVENT_TYPE_NONE;
    public BluetoothDevice device;
    public int valueInt1 = 0;
    public int valueInt2 = 0;
    public int valueInt3 = 0;
    public int valueInt4 = 0;
    public int valueInt5 = 0;
    public boolean valueBool1 = false;
    public BluetoothLeAudioCodecConfig valueCodec1;
    public BluetoothLeAudioCodecConfig valueCodec2;
    public List<BluetoothLeAudioCodecConfig> valueCodecList1;
    public List<BluetoothLeAudioCodecConfig> valueCodecList2;
    public BluetoothLeBroadcastMetadata broadcastMetadata;

    LeAudioStackEvent(int type) {
        this.type = type;
    }

    @Override
    public String toString() {
        // event dump
        StringBuilder result = new StringBuilder();
        result.append("LeAudioStackEvent {type:" + eventTypeToString(type));
        result.append(", device:" + device);

        if (type != EVENT_TYPE_AUDIO_LOCAL_CODEC_CONFIG_CAPA_CHANGED) {
            result.append(", value1:" + eventTypeValue1ToString(type, valueInt1));
            result.append(", value2:" + eventTypeValue2ToString(type, valueInt2));
            result.append(", value3:" + eventTypeValue3ToString(type, valueInt3));
            result.append(", value4:" + eventTypeValue4ToString(type, valueInt4));
            result.append(", value5:" + eventTypeValue5ToString(type, valueInt5));
            result.append(", valueBool1:" + eventTypeValueBool1ToString(type, valueBool1));
        } else {
            result.append(
                    ", valueCodecList1:" + eventTypeValueCodecList1ToString(type, valueCodecList1));
            result.append(
                    ", valueCodecList2:" + eventTypeValueCodecList2ToString(type, valueCodecList2));
        }

        if (type == EVENT_TYPE_AUDIO_GROUP_CURRENT_CODEC_CONFIG_CHANGED) {
            result.append(", valueCodec1:" + eventTypeValueCodec1ToString(type, valueCodec1));
            result.append(", valueCodec2:" + eventTypeValueCodec2ToString(type, valueCodec2));
        }

        if (type == EVENT_TYPE_AUDIO_GROUP_SELECTABLE_CODEC_CONFIG_CHANGED) {
            result.append(
                    ", valueCodecList1:" + eventTypeValueCodecList1ToString(type, valueCodecList1));
            result.append(
                    ", valueCodecList2:" + eventTypeValueCodecList2ToString(type, valueCodecList2));
        }

        if (type == EVENT_TYPE_BROADCAST_METADATA_CHANGED) {
            result.append(", broadcastMetadata:"
                    + eventTypeValueBroadcastMetadataToString(broadcastMetadata));
        }
        result.append("}");
        return result.toString();
    }

    private static String eventTypeToString(int type) {
        switch (type) {
            case EVENT_TYPE_NONE:
                return "EVENT_TYPE_NONE";
            case EVENT_TYPE_CONNECTION_STATE_CHANGED:
                return "EVENT_TYPE_CONNECTION_STATE_CHANGED";
            case EVENT_TYPE_GROUP_STATUS_CHANGED:
                return "EVENT_TYPE_GROUP_STATUS_CHANGED";
            case EVENT_TYPE_GROUP_NODE_STATUS_CHANGED:
                return "EVENT_TYPE_GROUP_NODE_STATUS_CHANGED";
            case EVENT_TYPE_AUDIO_CONF_CHANGED:
                return "EVENT_TYPE_AUDIO_CONF_CHANGED";
            case EVENT_TYPE_SINK_AUDIO_LOCATION_AVAILABLE:
                return "EVENT_TYPE_SINK_AUDIO_LOCATION_AVAILABLE";
            case EVENT_TYPE_BROADCAST_CREATED:
                return "EVENT_TYPE_BROADCAST_CREATED";
            case EVENT_TYPE_BROADCAST_DESTROYED:
                return "EVENT_TYPE_BROADCAST_DESTROYED";
            case EVENT_TYPE_BROADCAST_STATE:
                return "EVENT_TYPE_BROADCAST_STATE";
            case EVENT_TYPE_BROADCAST_METADATA_CHANGED:
                return "EVENT_TYPE_BROADCAST_METADATA_CHANGED";
            case EVENT_TYPE_AUDIO_LOCAL_CODEC_CONFIG_CAPA_CHANGED:
                return "EVENT_TYPE_AUDIO_LOCAL_CODEC_CONFIG_CAPA_CHANGED";
            case EVENT_TYPE_AUDIO_GROUP_CURRENT_CODEC_CONFIG_CHANGED:
                return "EVENT_TYPE_AUDIO_GROUP_CURRENT_CODEC_CONFIG_CHANGED";
            case EVENT_TYPE_AUDIO_GROUP_SELECTABLE_CODEC_CONFIG_CHANGED:
                return "EVENT_TYPE_AUDIO_GROUP_SELECTABLE_CODEC_CONFIG_CHANGED";
            case EVENT_TYPE_NATIVE_INITIALIZED:
                return "EVENT_TYPE_NATIVE_INITIALIZED";
            case EVENT_TYPE_HEALTH_BASED_DEV_RECOMMENDATION:
                return "EVENT_TYPE_HEALTH_BASED_DEV_RECOMMENDATION";
            case EVENT_TYPE_HEALTH_BASED_GROUP_RECOMMENDATION:
                return "EVENT_TYPE_HEALTH_BASED_GROUP_RECOMMENDATION";
            case EVENT_TYPE_UNICAST_MONITOR_MODE_STATUS:
                return "EVENT_TYPE_UNICAST_MONITOR_MODE_STATUS";
            case EVENT_TYPE_GROUP_STREAM_STATUS_CHANGED:
                return "EVENT_TYPE_GROUP_STREAM_STATUS_CHANGED";
            default:
                return "EVENT_TYPE_UNKNOWN:" + type;
        }
    }

    private static String eventTypeValue1ToString(int type, int value) {
        switch (type) {
            case EVENT_TYPE_CONNECTION_STATE_CHANGED:
                switch (value) {
                    case CONNECTION_STATE_DISCONNECTED:
                        return  "CONNECTION_STATE_DISCONNECTED";
                    case CONNECTION_STATE_CONNECTING:
                        return  "CONNECTION_STATE_CONNECTING";
                    case CONNECTION_STATE_CONNECTED:
                        return  "CONNECTION_STATE_CONNECTED";
                    case CONNECTION_STATE_DISCONNECTING:
                        return  "CONNECTION_STATE_DISCONNECTING";
                    default:
                        return "UNKNOWN";
                }
            case EVENT_TYPE_GROUP_NODE_STATUS_CHANGED:
                // same as EVENT_TYPE_GROUP_STATUS_CHANGED
            case EVENT_TYPE_AUDIO_GROUP_CURRENT_CODEC_CONFIG_CHANGED:
            case EVENT_TYPE_AUDIO_GROUP_SELECTABLE_CODEC_CONFIG_CHANGED:
                // same as EVENT_TYPE_GROUP_STATUS_CHANGED
            case EVENT_TYPE_GROUP_STREAM_STATUS_CHANGED:
                // same as EVENT_TYPE_GROUP_STATUS_CHANGED
            case EVENT_TYPE_GROUP_STATUS_CHANGED:
                return "{group_id:" + Integer.toString(value) + "}";
            case EVENT_TYPE_AUDIO_CONF_CHANGED:
                // FIXME: It should have proper direction names here
                return "{direction:" + value + "}";
            case EVENT_TYPE_SINK_AUDIO_LOCATION_AVAILABLE:
                return "{sink_audio_location:" + value + "}";
            case EVENT_TYPE_BROADCAST_CREATED:
                // same as EVENT_TYPE_BROADCAST_STATE
            case EVENT_TYPE_BROADCAST_DESTROYED:
                // same as EVENT_TYPE_BROADCAST_STATE
            case EVENT_TYPE_BROADCAST_METADATA_CHANGED:
                // same as EVENT_TYPE_BROADCAST_STATE
            case EVENT_TYPE_BROADCAST_STATE:
                return "{broadcastId:" + value + "}";
            case EVENT_TYPE_HEALTH_BASED_GROUP_RECOMMENDATION:
                return "{group_id: " + value + "}";
            case EVENT_TYPE_HEALTH_BASED_DEV_RECOMMENDATION:
                switch (value) {
                    case HEALTH_RECOMMENDATION_ACTION_DISABLE:
                        return "ACTION_DISABLE";
                    case HEALTH_RECOMMENDATION_ACTION_CONSIDER_DISABLING:
                        return "ACTION_CONSIDER_DISABLING";
                    case HEALTH_RECOMMENDATION_ACTION_INACTIVATE_GROUP:
                        return "ACTION_INACTIVATE_GROUP";
                    default:
                        return "UNKNOWN";
                }
            case EVENT_TYPE_UNICAST_MONITOR_MODE_STATUS:
                switch (value) {
                    case DIRECTION_SINK:
                        return "DIRECTION_SINK";
                    case DIRECTION_SOURCE:
                        return "DIRECTION_SOURCE";
                    default:
                        return "UNKNOWN";
                }
            default:
                break;
        }
        return Integer.toString(value);
    }

    private static String eventTypeValue2ToString(int type, int value) {
        switch (type) {
            case EVENT_TYPE_GROUP_STATUS_CHANGED:
                switch (value) {
                    case GROUP_STATUS_ACTIVE:
                        return "GROUP_STATUS_ACTIVE";
                    case GROUP_STATUS_INACTIVE:
                        return "GROUP_STATUS_INACTIVE";
                    case GROUP_STATUS_TURNED_IDLE_DURING_CALL:
                        return "GROUP_STATUS_TURNED_IDLE_DURING_CALL";
                    default:
                        break;
                }
                break;
            case EVENT_TYPE_GROUP_NODE_STATUS_CHANGED:
                switch (value) {
                    case GROUP_NODE_ADDED:
                        return "GROUP_NODE_ADDED";
                    case GROUP_NODE_REMOVED:
                        return "GROUP_NODE_REMOVED";
                    default:
                        return "UNKNOWN";
                }
            case EVENT_TYPE_AUDIO_CONF_CHANGED:
                return "{group_id:" + Integer.toString(value) + "}";
            case EVENT_TYPE_BROADCAST_STATE:
                return "{state:" + broadcastStateToString(value) + "}";
            case EVENT_TYPE_HEALTH_BASED_GROUP_RECOMMENDATION:
                switch (value) {
                    case HEALTH_RECOMMENDATION_ACTION_DISABLE:
                        return "ACTION_DISABLE";
                    case HEALTH_RECOMMENDATION_ACTION_CONSIDER_DISABLING:
                        return "ACTION_CONSIDER_DISABLING";
                    default:
                        return "UNKNOWN";
                }
            case EVENT_TYPE_UNICAST_MONITOR_MODE_STATUS:
                switch (value) {
                    case STATUS_LOCAL_STREAM_REQUESTED:
                        return "STATUS_LOCAL_STREAM_REQUESTED";
                    case STATUS_LOCAL_STREAM_STREAMING:
                        return "STATUS_LOCAL_STREAM_STREAMING";
                    case STATUS_LOCAL_STREAM_SUSPENDED:
                        return "STATUS_LOCAL_STREAM_SUSPENDED";
                    default:
                        return "UNKNOWN";
                }
            case EVENT_TYPE_GROUP_STREAM_STATUS_CHANGED:
                switch (value) {
                    case GROUP_STREAM_STATUS_IDLE:
                        return "GROUP_STREAM_STATUS_IDLE";
                    case GROUP_STREAM_STATUS_STREAMING:
                        return "GROUP_STREAM_STATUS_STREAMING";
                    default:
                        return "UNKNOWN";
                }
            default:
                break;
        }
        return Integer.toString(value);
    }

    private static String eventTypeValue3ToString(int type, int value) {
        switch (type) {
            case EVENT_TYPE_AUDIO_CONF_CHANGED:
                // FIXME: It should have proper location names here
                return "{snk_audio_loc:" + value + "}";
            default:
                break;
        }
        return Integer.toString(value);
    }

    private static String eventTypeValue4ToString(int type, int value) {
        switch (type) {
            case EVENT_TYPE_AUDIO_CONF_CHANGED:
                // FIXME: It should have proper location names here
                return "{src_audio_loc:" + value + "}";
            default:
                break;
        }
        return Integer.toString(value);
    }

    private static String eventTypeValue5ToString(int type, int value) {
        switch (type) {
            case EVENT_TYPE_AUDIO_CONF_CHANGED:
                return "{available_contexts:" + Integer.toBinaryString(value) + "}";
            default:
                break;
        }
        return Integer.toString(value);
    }

    private static String eventTypeValueBool1ToString(int type, boolean value) {
        switch (type) {
            case EVENT_TYPE_BROADCAST_CREATED:
                return "{success:" + value + "}";
            default:
                return "<unused>";
        }
    }

    private static String eventTypeValueCodec1ToString(int type,
                                    BluetoothLeAudioCodecConfig value) {
        switch (type) {
            case EVENT_TYPE_AUDIO_GROUP_CURRENT_CODEC_CONFIG_CHANGED:
                return "{input codec =" + value + "}";
            default:
                return "<unused>";
        }
    }

    private static String eventTypeValueCodec2ToString(int type,
                                    BluetoothLeAudioCodecConfig value) {
        switch (type) {
            case EVENT_TYPE_AUDIO_GROUP_CURRENT_CODEC_CONFIG_CHANGED:
                return "{output codec =" + value + "}";
            default:
                return "<unused>";
        }
    }

    private static String eventTypeValueCodecList1ToString(int type,
                                    List<BluetoothLeAudioCodecConfig> value) {
        String valueStr = "";
        switch (type) {
            case EVENT_TYPE_AUDIO_LOCAL_CODEC_CONFIG_CAPA_CHANGED:
                for (BluetoothLeAudioCodecConfig n : value) {
                    valueStr = valueStr.concat(n.toString() + "\n");
                }
                return "{input local capa codec = \n" + valueStr + "}";
            case EVENT_TYPE_AUDIO_GROUP_SELECTABLE_CODEC_CONFIG_CHANGED:
                for (BluetoothLeAudioCodecConfig n : value) {
                    valueStr = valueStr.concat(n.toString() + "\n");
                }
                return "{input selectable codec =" + valueStr + "}";
            default:
                return "<unused>";
        }
    }

    private static String eventTypeValueCodecList2ToString(int type,
                                    List<BluetoothLeAudioCodecConfig> value) {
        String valueStr = "";
        switch (type) {
            case EVENT_TYPE_AUDIO_LOCAL_CODEC_CONFIG_CAPA_CHANGED:
                for (BluetoothLeAudioCodecConfig n : value) {
                    valueStr = valueStr.concat(n.toString() + "\n");
                }
                return "{output local capa codec = \n" + valueStr + "}";
            case EVENT_TYPE_AUDIO_GROUP_SELECTABLE_CODEC_CONFIG_CHANGED:
                for (BluetoothLeAudioCodecConfig n : value) {
                    valueStr = valueStr.concat(n.toString() + "\n");
                }
                return "{output selectable codec =" + valueStr + "}";
            default:
                return "<unused>";
        }
    }

    private static String broadcastStateToString(int state) {
        switch (state) {
            case BROADCAST_STATE_STOPPED:
                return "BROADCAST_STATE_STOPPED";
            case BROADCAST_STATE_CONFIGURING:
                return "BROADCAST_STATE_CONFIGURING";
            case BROADCAST_STATE_PAUSED:
                return "BROADCAST_STATE_PAUSED";
            case BROADCAST_STATE_STOPPING:
                return "BROADCAST_STATE_STOPPING";
            case BROADCAST_STATE_STREAMING:
                return "BROADCAST_STATE_STREAMING";
            default:
                return "UNKNOWN";
        }
    }

    private static String eventTypeValueBroadcastMetadataToString(
            BluetoothLeBroadcastMetadata meta) {
        return meta.toString();
    }

    protected static String encodeHexString(byte[] pduData) {
        StringBuilder out = new StringBuilder(pduData.length * 2);
        for (int i = 0; i < pduData.length; i++) {
            // MS-nibble first
            out.append(Integer.toString((pduData[i] >> 4) & 0x0f, 16));
            out.append(Integer.toString(pduData[i] & 0x0f, 16));
        }
        return out.toString();
    }
}
