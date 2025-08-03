/*
 * Copyright 2021 HIMSA II K/S - www.himsa.com.
 * Represented by EHIMA - www.ehima.com.
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

import android.annotation.NonNull;

/**
 * Media search request, from client to Media Player
 */
public final class SearchRequest {
    private final int mType;
    private final String mStringArg;

    /**
     * Media search request constructor
     *
     * @param type Search request type
     * @param arg  Search request argument
     */
    public SearchRequest(int type, String arg) {
        this.mType = type;
        this.mStringArg = arg;
    }

    /**
     * Media search request type getter
     *
     * @return Search request type
     */
    public int getType() {
        return mType;
    }

    /**
     * Media search request string argument getter
     *
     * @return search request argument
     */
    public @NonNull String getStringArg() {
        return mStringArg;
    }

    /**
     * Media search request results definition
     */
    public enum Results {
        SUCCESS(0x01),
        FAILURE(0x02);

        private final int mValue;

        Results(int value) {
            mValue = value;
        }

        public int getValue() {
            return mValue;
        }
    }

    /**
     * Media search request types definition
     */
    public final static class Types {
        public static final int TRACK_NAME = 0x01;
        public static final int ARTIST_NAME = 0x02;
        public static final int ALBUM_NAME = 0x03;
        public static final int GROUP_NAME = 0x04;
        public static final int EARLIEST_YEAR = 0x05;
        public static final int LATEST_YEAR = 0x06;
        public static final int GENRE = 0x07;
        public static final int ONLY_TRACKS = 0x08;
        public static final int ONLY_GROUPS = 0x09;
        private Types() {
            // not called
        }
    }

}
