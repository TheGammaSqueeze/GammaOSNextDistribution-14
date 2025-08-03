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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.storage.DiskInfo;

import java.util.ArrayList;
import java.util.List;

public class DiskInfoCompat {
    private final DiskInfo mDiskInfo;
    public final String mId;
    public long mSize;
    public int mVolumeCount;

    DiskInfoCompat(DiskInfo diskInfo) {
        this.mDiskInfo = diskInfo;
        this.mId = diskInfo.id;
        this.mSize = diskInfo.size;
        this.mVolumeCount = diskInfo.volumeCount;
    }

    static List<DiskInfoCompat> convert(List<DiskInfo> infos) {
        List<DiskInfoCompat> list = new ArrayList<>();
        for (DiskInfo info : infos) {
            list.add(new DiskInfoCompat(info));
        }
        return list;
    }

    public boolean isAdoptable() {
        return mDiskInfo.isAdoptable();
    }

    public @NonNull
    String getId() {
        return mDiskInfo.getId();
    }

    public static final String EXTRA_DISK_ID = "android.os.storage.extra.DISK_ID";

    public @Nullable
    String getDescription() {
        return mDiskInfo.getDescription();
    }
}
