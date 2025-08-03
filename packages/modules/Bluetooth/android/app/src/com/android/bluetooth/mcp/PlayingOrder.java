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
 * Playing order definition
 */
public enum PlayingOrder {
    SINGLE_ONCE(0x01),
    SINGLE_REPEAT(0x02),
    IN_ORDER_ONCE(0x03),
    IN_ORDER_REPEAT(0x04),
    OLDEST_ONCE(0x05),
    OLDEST_REPEAT(0x06),
    NEWEST_ONCE(0x07),
    NEWEST_REPEAT(0x08),
    SHUFFLE_ONCE(0x09),
    SHUFFLE_REPEAT(0x0A);

    private final int mValue;

    PlayingOrder(int value) {
        mValue = value;
    }

    public int getValue() {
        return mValue;
    }
}
