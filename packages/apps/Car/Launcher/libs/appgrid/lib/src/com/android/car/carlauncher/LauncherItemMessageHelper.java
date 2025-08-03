/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.car.carlauncher;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.car.carlauncher.LauncherItemProto.LauncherItemListMessage;
import com.android.car.carlauncher.LauncherItemProto.LauncherItemMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Helper class that provides method used by LauncherModel
 */
public class LauncherItemMessageHelper {
    /**
     * Convert a List of {@link LauncherItemMessage} to a single {@link LauncherItemListMessage}.
     */
    @Nullable
    public LauncherItemListMessage convertToMessage(List<LauncherItemMessage> msgList) {
        if (msgList == null) {
            return null;
        }
        LauncherItemListMessage.Builder builder =
                LauncherItemListMessage.newBuilder().addAllLauncherItemMessage(msgList);
        return builder.build();
    }

    /**
     * Converts {@link LauncherItemListMessage} to a List of {@link LauncherItemMessage},
     * sorts the LauncherItemList based on their relative order in the file, then return the list.
     */
    @NonNull
    public List<LauncherItemMessage> getSortedList(@Nullable LauncherItemListMessage protoLstMsg) {
        if (protoLstMsg == null) {
            return new ArrayList<>();
        }
        List<LauncherItemMessage> itemMsgList = protoLstMsg.getLauncherItemMessageList();
        List<LauncherItemMessage> sortedItemMsgList = new ArrayList<>();
        if (!itemMsgList.isEmpty() && itemMsgList.size() > 0) {
            // need to create a new list for sorting purposes since ProtobufArrayList is not mutable
            sortedItemMsgList.addAll(itemMsgList);
            Collections.sort(sortedItemMsgList,
                    Comparator.comparingInt(LauncherItemMessage::getRelativePosition));
        }
        return sortedItemMsgList;
    }
}

