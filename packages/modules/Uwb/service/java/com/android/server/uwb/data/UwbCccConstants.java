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
package com.android.server.uwb.data;

public class UwbCccConstants {

    /* CCC Hopping [S]*/
    public static final int HOPPING_CONFIG_MODE_NONE = 0X00;
    public static final int HOPPING_CONFIG_MODE_CONTINUOUS_DEFAULT = 0X03;
    public static final int HOPPING_CONFIG_MODE_CONTINUOUS_AES = 0X05;

    public static final int HOPPING_CONFIG_MODE_MODE_ADAPTIVE_DEFAULT = 0X02;
    public static final int HOPPING_CONFIG_MODE_MODE_ADAPTIVE_AES = 0X04;


    public static final String KEY_STARTING_STS_INDEX = "starting_sts_index";
    public static final String KEY_UWB_TIME_0 = "uwb_time_0";
    public static final String KEY_HOP_MODE_KEY = "hop_mode_key";
    public static final String KEY_SYNC_CODE_INDEX = "sync_code_index";
    public static final String KEY_RAN_MULTIPLIER = "ran_multiplier";
    /* CCC Hopping [E]*/
}
