/*
 * Copyright 2021 HIMSA II K/S - www.himsa.com.
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

package com.android.bluetooth.mcp;

/**
 * Supported playing order definition
 */
public final class SupportedPlayingOrder {
    public static final int SINGLE_ONCE = 0x0001;
    public static final int SINGLE_REPEAT = 0x0002;
    public static final int IN_ORDER_ONCE = 0x0004;
    public static final int IN_ORDER_REPEAT = 0x0008;
    public static final int OLDEST_ONCE = 0x0010;
    public static final int OLDEST_REPEAT = 0x0020;
    public static final int NEWEST_ONCE = 0x0040;
    public static final int NEWEST_REPEAT = 0x0080;
    public static final int SHUFFLE_ONCE = 0x0100;
    public static final int SHUFFLE_REPEAT = 0x0200;
}
