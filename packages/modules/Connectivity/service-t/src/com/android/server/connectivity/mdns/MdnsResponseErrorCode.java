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

package com.android.server.connectivity.mdns;

/**
 * The list of error code for parsing mDNS response.
 *
 * @hide
 */
public class MdnsResponseErrorCode {
    public static final int SUCCESS = 0;
    public static final int ERROR_NOT_RESPONSE_MESSAGE = 1;
    public static final int ERROR_NO_ANSWERS = 2;
    public static final int ERROR_READING_RECORD_NAME = 3;
    public static final int ERROR_READING_A_RDATA = 4;
    public static final int ERROR_READING_AAAA_RDATA = 5;
    public static final int ERROR_READING_PTR_RDATA = 6;
    public static final int ERROR_SKIPPING_PTR_RDATA = 7;
    public static final int ERROR_READING_SRV_RDATA = 8;
    public static final int ERROR_SKIPPING_SRV_RDATA = 9;
    public static final int ERROR_READING_TXT_RDATA = 10;
    public static final int ERROR_SKIPPING_UNKNOWN_RECORD = 11;
    public static final int ERROR_END_OF_FILE = 12;
    public static final int ERROR_READING_NSEC_RDATA = 13;
    public static final int ERROR_READING_ANY_RDATA = 14;
}