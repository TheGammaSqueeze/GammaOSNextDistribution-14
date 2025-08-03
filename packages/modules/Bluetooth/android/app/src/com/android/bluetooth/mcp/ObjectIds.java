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
 * Objects IDs definition
 */
public final class ObjectIds {
    public static final int PLAYER_ICON_OBJ_ID = (int) ServiceFeature.PLAYER_ICON_OBJ_ID;
    public static final int CURRENT_TRACK_SEGMENT_OBJ_ID =
            (int) ServiceFeature.CURRENT_TRACK_SEGMENT_OBJ_ID;
    public static final int CURRENT_TRACK_OBJ_ID = (int) ServiceFeature.CURRENT_TRACK_OBJ_ID;
    public static final int NEXT_TRACK_OBJ_ID = (int) ServiceFeature.NEXT_TRACK_OBJ_ID;
    public static final int CURRENT_GROUP_OBJ_ID = (int) ServiceFeature.CURRENT_GROUP_OBJ_ID;
    public static final int PARENT_GROUP_OBJ_ID = (int) ServiceFeature.PARENT_GROUP_OBJ_ID;
    public static final int SEARCH_RESULT_OBJ_ID = (int) ServiceFeature.SEARCH_RESULT_OBJ_ID;
    private ObjectIds() {
        // not called
    }

    public static int GetMatchingServiceFeature(int objectId) {
        return objectId;
    }
}
