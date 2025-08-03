/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.cellbroadcastservice;

/**
 * Utility for metrics of cellbroadcast to check-in easy and simple
 */
public class CellBroadcastMetrics {
    private static final String TAG = "CellBroadcastMetrics";

    // Values for CellBroadcastMessageReported.type
    public static final int RPT_UNKNOWN =
            CellBroadcastModuleStatsLog.CELL_BROADCAST_MESSAGE_REPORTED__TYPE__UNKNOWN_TYPE;
    public static final int RPT_GSM =
            CellBroadcastModuleStatsLog.CELL_BROADCAST_MESSAGE_REPORTED__TYPE__GSM;
    public static final int RPT_CDMA =
            CellBroadcastModuleStatsLog.CELL_BROADCAST_MESSAGE_REPORTED__TYPE__CDMA;
    public static final int RPT_SPC =
            CellBroadcastModuleStatsLog.CELL_BROADCAST_MESSAGE_REPORTED__TYPE__CDMA_SPC;

    // Values for CellBroadcastMessageReported.source
    public static final int SRC_UNKNOWN =
            CellBroadcastModuleStatsLog.CELL_BROADCAST_MESSAGE_REPORTED__SOURCE__UNKNOWN_SOURCE;
    public static final int SRC_FWK =
            CellBroadcastModuleStatsLog.CELL_BROADCAST_MESSAGE_REPORTED__SOURCE__FRAMEWORK;
    public static final int SRC_CBS =
            CellBroadcastModuleStatsLog.CELL_BROADCAST_MESSAGE_REPORTED__SOURCE__CB_SERVICE;
    public static final int SRC_CBR =
            CellBroadcastModuleStatsLog.CELL_BROADCAST_MESSAGE_REPORTED__SOURCE__CB_RECEIVER_APP;

    // Values for CellBroadcastMessageError.type
    public static final int ERR_UNKNOWN =
            CellBroadcastModuleStatsLog.CELL_BROADCAST_MESSAGE_ERROR__TYPE__UNKNOWN_TYPE;
    public static final int ERR_CDMA_DECODING =
            CellBroadcastModuleStatsLog.CELL_BROADCAST_MESSAGE_ERROR__TYPE__CDMA_DECODING_ERROR;
    public static final int ERR_SCP_EMPTY =
            CellBroadcastModuleStatsLog.CELL_BROADCAST_MESSAGE_ERROR__TYPE__CDMA_SCP_EMPTY;
    public static final int ERR_SCP_HANDLING =
            CellBroadcastModuleStatsLog.CELL_BROADCAST_MESSAGE_ERROR__TYPE__CDMA_SCP_HANDLING_ERROR;
    public static final int ERR_GSM_INVALID_HEADER =
            CellBroadcastModuleStatsLog
                    .CELL_BROADCAST_MESSAGE_ERROR__TYPE__GSM_INVALID_HEADER_LENGTH;
    public static final int ERR_GSM_UNSUPPORTED_HEADER_MSG =
            CellBroadcastModuleStatsLog
                    .CELL_BROADCAST_MESSAGE_ERROR__TYPE__GSM_UNSUPPORTED_HEADER_MESSAGE_TYPE;
    public static final int ERR_GSM_UNSUPPORTED_HEADER_DCS =
            CellBroadcastModuleStatsLog
                    .CELL_BROADCAST_MESSAGE_ERROR__TYPE__GSM_UNSUPPORTED_HEADER_DATA_CODING_SCHEME;
    public static final int ERR_GSM_INVALID_PDU =
            CellBroadcastModuleStatsLog.CELL_BROADCAST_MESSAGE_ERROR__TYPE__GSM_INVALID_PDU;
    public static final int ERR_GSM_INVALID_GEO_FENCING_DATA =
            CellBroadcastModuleStatsLog
                    .CELL_BROADCAST_MESSAGE_ERROR__TYPE__GSM_INVALID_GEO_FENCING_DATA;
    public static final int ERR_GSM_UMTS_INVALID_WAC =
            CellBroadcastModuleStatsLog.CELL_BROADCAST_MESSAGE_ERROR__TYPE__GSM_UMTS_INVALID_WAC;
    public static final int ERR_FAILED_TO_INSERT_TO_DB =
            CellBroadcastModuleStatsLog.CELL_BROADCAST_MESSAGE_ERROR__TYPE__FAILED_TO_INSERT_TO_DB;
    public static final int ERR_UNEXPECTED_GEOMETRY_FROM_FWK =
            CellBroadcastModuleStatsLog
                    .CELL_BROADCAST_MESSAGE_ERROR__TYPE__UNEXPECTED_GEOMETRY_FROM_FWK;
    public static final int ERR_UNEXPECTED_GSM_MSG_FROM_FWK =
            CellBroadcastModuleStatsLog
                    .CELL_BROADCAST_MESSAGE_ERROR__TYPE__UNEXPECTED_GSM_MESSAGE_TYPE_FROM_FWK;
    public static final int ERR_UNEXPECTED_CDMA_MSG_FROM_FWK =
            CellBroadcastModuleStatsLog
                    .CELL_BROADCAST_MESSAGE_ERROR__TYPE__UNEXPECTED_CDMA_MESSAGE_TYPE_FROM_FWK;
    public static final int ERR_UNEXPECTED_SPC_MSG_FROM_FWK =
            CellBroadcastModuleStatsLog
                    .CELL_BROADCAST_MESSAGE_ERROR__TYPE__UNEXPECTED_CDMA_SCP_MESSAGE_TYPE_FROM_FWK;
    public static final int ERR_NO_CONNECTION_TO_CBS =
            CellBroadcastModuleStatsLog
                    .CELL_BROADCAST_MESSAGE_ERROR__TYPE__NO_CONNECTION_TO_CB_SERVICE;

    // Values for CellBroadcastMessageFiltered.type
    public static final int FILTER_UNKNOWN =
            CellBroadcastModuleStatsLog.CELL_BROADCAST_MESSAGE_FILTERED__TYPE__UNKNOWN_TYPE;
    public static final int FILTER_GSM =
            CellBroadcastModuleStatsLog.CELL_BROADCAST_MESSAGE_FILTERED__TYPE__GSM;
    public static final int FILTER_CDMA =
            CellBroadcastModuleStatsLog.CELL_BROADCAST_MESSAGE_FILTERED__TYPE__CDMA;
    public static final int FILTER_SPC =
            CellBroadcastModuleStatsLog.CELL_BROADCAST_MESSAGE_FILTERED__TYPE__CDMA_SPC;

    // Values for CellBroadcastMessageFiltered.filter
    public static final int FILTER_NOTFILTERED =
            CellBroadcastModuleStatsLog.CELL_BROADCAST_MESSAGE_FILTERED__FILTER__NOT_FILTERED;
    public static final int FILTER_DUPLICATE =
            CellBroadcastModuleStatsLog.CELL_BROADCAST_MESSAGE_FILTERED__FILTER__DUPLICATE_MESSAGE;
    public static final int FILTER_GEOFENCED =
            CellBroadcastModuleStatsLog.CELL_BROADCAST_MESSAGE_FILTERED__FILTER__GEOFENCED_MESSAGE;
    public static final int FILTER_AREAINFO =
            CellBroadcastModuleStatsLog.CELL_BROADCAST_MESSAGE_FILTERED__FILTER__AREA_INFO_MESSAGE;
    public static final int FILTER_DISABLEDBYOEM =
            CellBroadcastModuleStatsLog.CELL_BROADCAST_MESSAGE_FILTERED__FILTER__DISABLED_BY_OEM;
    public static final int FILTER_NOTSHOW_ECBM =
            CellBroadcastModuleStatsLog.CELL_BROADCAST_MESSAGE_FILTERED__FILTER__NOTSHOW_ECBM;
    public static final int FILTER_NOTSHOW_USERPREF =
            CellBroadcastModuleStatsLog.CELL_BROADCAST_MESSAGE_FILTERED__FILTER__NOTSHOW_USER_PREF;
    public static final int FILTER_NOTSHOW_EMPTYBODY =
            CellBroadcastModuleStatsLog.CELL_BROADCAST_MESSAGE_FILTERED__FILTER__NOTSHOW_EMPTY_BODY;
    public static final int FILTER_NOTSHOW_MISMATCH_PREF_SECONDLANG =
            CellBroadcastModuleStatsLog
                    .CELL_BROADCAST_MESSAGE_FILTERED__FILTER__NOTSHOW_MISMATCH_PREF_SECOND_LANG;
    public static final int FILTER_NOTSHOW_PREF_SECONDLANG_OFF =
            CellBroadcastModuleStatsLog
                    .CELL_BROADCAST_MESSAGE_FILTERED__FILTER__NOTSHOW_PREF_SECONDLANG_OFF;
    public static final int FILTER_NOTSHOW_MISMATCH_DEVICE_LANG_SETTING =
            CellBroadcastModuleStatsLog
                    .CELL_BROADCAST_MESSAGE_FILTERED__FILTER__NOTSHOW_MISMATCH_DEVICE_LANG_SETTING;
    public static final int FILTER_NOTSHOW_TESTMODE =
            CellBroadcastModuleStatsLog
                    .CELL_BROADCAST_MESSAGE_FILTERED__FILTER__NOTSHOW_MESSAGE_FOR_TESTMODE;
    public static final int FILTER_NOTSHOW_FILTERED =
            CellBroadcastModuleStatsLog
                    .CELL_BROADCAST_MESSAGE_FILTERED__FILTER__NOTSHOW_FILTER_STRING;

    // Values for CellBroadcastError.source
    public static final int ERRSRC_UNKNOWN =
            CellBroadcastModuleStatsLog.CELL_BROADCAST_MODULE_ERROR_REPORTED__SOURCE__UNKNOWN_SOURCE;
    public static final int ERRSRC_FWK =
            CellBroadcastModuleStatsLog.CELL_BROADCAST_MODULE_ERROR_REPORTED__SOURCE__FRAMEWORK;
    public static final int ERRSRC_CBS =
            CellBroadcastModuleStatsLog.CELL_BROADCAST_MODULE_ERROR_REPORTED__SOURCE__CB_SERVICE;
    public static final int ERRSRC_CBR =
            CellBroadcastModuleStatsLog.CELL_BROADCAST_MODULE_ERROR_REPORTED__SOURCE__CB_RECEIVER_APP;

    // Values for CellBroadcastError.type
    public static final int ERRTYPE_UNKNOWN =
            CellBroadcastModuleStatsLog.CELL_BROADCAST_MODULE_ERROR_REPORTED__TYPE__ERROR_UNKNOWN;
    public static final int ERRTYPE_BADCONFIG =
            CellBroadcastModuleStatsLog.CELL_BROADCAST_MODULE_ERROR_REPORTED__TYPE__ERROR_BAD_CONFIG;
    public static final int ERRTYPE_DBMIGRATION =
            CellBroadcastModuleStatsLog.CELL_BROADCAST_MODULE_ERROR_REPORTED__TYPE__ERROR_DB_MIGRATION;
    public static final int ERRTYPE_DEFAULTRES =
            CellBroadcastModuleStatsLog.CELL_BROADCAST_MODULE_ERROR_REPORTED__TYPE__ERROR_DEFAULT_RES;
    public static final int ERRTYPE_ENABLECHANNEL =
            CellBroadcastModuleStatsLog.CELL_BROADCAST_MODULE_ERROR_REPORTED__TYPE__ERROR_ENABLE_CHANNEL;
    public static final int ERRTYPE_GETLOCATION =
            CellBroadcastModuleStatsLog.CELL_BROADCAST_MODULE_ERROR_REPORTED__TYPE__ERROR_GET_LOCATION;
    public static final int ERRTYPE_MISSINGRES =
            CellBroadcastModuleStatsLog.CELL_BROADCAST_MODULE_ERROR_REPORTED__TYPE__ERROR_MISSING_RES;
    public static final int ERRTYPE_PLAYFLASH =
            CellBroadcastModuleStatsLog.CELL_BROADCAST_MODULE_ERROR_REPORTED__TYPE__ERROR_PLAY_FLASH;
    public static final int ERRTYPE_PLAYSOUND =
            CellBroadcastModuleStatsLog.CELL_BROADCAST_MODULE_ERROR_REPORTED__TYPE__ERROR_PLAY_SOUND;
    public static final int ERRTYPE_PLAYTTS =
            CellBroadcastModuleStatsLog.CELL_BROADCAST_MODULE_ERROR_REPORTED__TYPE__ERROR_PLAY_TTS;
    public static final int ERRTYPE_PREFMIGRATION =
            CellBroadcastModuleStatsLog.CELL_BROADCAST_MODULE_ERROR_REPORTED__TYPE__ERROR_PREF_MIGRATION;
    public static final int ERRTYPE_PROVIDERINIT =
            CellBroadcastModuleStatsLog.CELL_BROADCAST_MODULE_ERROR_REPORTED__TYPE__ERROR_PROVIDER_INIT;
    public static final int ERRTYPE_CHANNEL_R =
            CellBroadcastModuleStatsLog.CELL_BROADCAST_MODULE_ERROR_REPORTED__TYPE__ERROR_RESET_CHANNEL_R;
    public static final int ERRTYPE_STATUSBAR =
            CellBroadcastModuleStatsLog.CELL_BROADCAST_MODULE_ERROR_REPORTED__TYPE__ERROR_STATUS_BAR;
    public static final int ERRTYPE_REMINDERINTERVAL =
            CellBroadcastModuleStatsLog.CELL_BROADCAST_MODULE_ERROR_REPORTED__TYPE__ERROR_REMINDER_INTERVAL;
    public static final int ERRTYPE_ICONRESOURCE =
            CellBroadcastModuleStatsLog.CELL_BROADCAST_MODULE_ERROR_REPORTED__TYPE__ERROR_ICON_RESOURCE;
    public static final int ERRTYPE_CHANNELRANGEPARSE =
            CellBroadcastModuleStatsLog
                    .CELL_BROADCAST_MODULE_ERROR_REPORTED__TYPE__ERROR_CHANNEL_RANGE_PARSE;
    public static final int ERRTYPE_DBINIT =
            CellBroadcastModuleStatsLog.CELL_BROADCAST_MODULE_ERROR_REPORTED__TYPE__ERROR_DB_INIT;
    public static final int ERRTYPE_NOTFOUND_DEFAULTCBRPKGS =
            CellBroadcastModuleStatsLog
                    .CELL_BROADCAST_MODULE_ERROR_REPORTED__TYPE__ERROR_NOT_FOUND_DEFAULT_CBR_PKGS;
    public static final int ERRTYPE_FOUND_MULTIPLECBRPKGS =
            CellBroadcastModuleStatsLog
                    .CELL_BROADCAST_MODULE_ERROR_REPORTED__TYPE__ERROR_FOUND_MULTIPLE_CBR_PKGS;
}
