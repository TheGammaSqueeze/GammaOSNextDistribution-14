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
import android.os.IVold;
import android.os.storage.VolumeInfo;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class VolumeInfoCompat {
    VolumeInfo mVolumeInfo;
    public String fsUuid;

    public static final String ACTION_VOLUME_STATE_CHANGED = VolumeInfo.ACTION_VOLUME_STATE_CHANGED;
    public static final String EXTRA_VOLUME_ID = VolumeInfo.EXTRA_VOLUME_ID;
    public static final String EXTRA_VOLUME_STATE = VolumeInfo.EXTRA_VOLUME_STATE;

    /** Stub volume representing internal private storage */
    public static final String ID_PRIVATE_INTERNAL = VolumeInfo.ID_PRIVATE_INTERNAL;
    /** Real volume representing internal emulated storage */

    public static final int TYPE_PUBLIC = IVold.VOLUME_TYPE_PUBLIC;
    public static final int TYPE_PRIVATE = IVold.VOLUME_TYPE_PRIVATE;
    public static final int TYPE_EMULATED = IVold.VOLUME_TYPE_EMULATED;
    public static final int TYPE_ASEC = IVold.VOLUME_TYPE_ASEC;
    public static final int TYPE_OBB = IVold.VOLUME_TYPE_OBB;
    public static final int TYPE_STUB = IVold.VOLUME_TYPE_STUB;

    public static final int STATE_UNMOUNTED = IVold.VOLUME_STATE_UNMOUNTED;
    public static final int STATE_CHECKING = IVold.VOLUME_STATE_CHECKING;
    public static final int STATE_MOUNTED = IVold.VOLUME_STATE_MOUNTED;
    public static final int STATE_MOUNTED_READ_ONLY = IVold.VOLUME_STATE_MOUNTED_READ_ONLY;
    public static final int STATE_FORMATTING = IVold.VOLUME_STATE_FORMATTING;
    public static final int STATE_EJECTING = IVold.VOLUME_STATE_EJECTING;
    public static final int STATE_UNMOUNTABLE = IVold.VOLUME_STATE_UNMOUNTABLE;
    public static final int STATE_REMOVED = IVold.VOLUME_STATE_REMOVED;
    public static final int STATE_BAD_REMOVAL = IVold.VOLUME_STATE_BAD_REMOVAL;

    VolumeInfoCompat(VolumeInfo volumeInfo) {
        mVolumeInfo = volumeInfo;
        fsUuid = volumeInfo.fsUuid;
    }

    static List<VolumeInfoCompat> convert(List<VolumeInfo> infos) {
        List<VolumeInfoCompat> list = new ArrayList<>();
        for (VolumeInfo info : infos) {
            list.add(new VolumeInfoCompat(info));
        }
        return list;
    }

    public @Nullable
    String getFsUuid() {
        return mVolumeInfo.getFsUuid();
    }

    public @NonNull
    String getId() {
        return mVolumeInfo.getId();
    }

    public int getType() {
        return mVolumeInfo.getType();
    }

    public DiskInfoCompat getDisk() {
        return new DiskInfoCompat(mVolumeInfo.getDisk());
    }

    public @Nullable
    String getDiskId() {
        return mVolumeInfo.getDiskId();
    }

    public File getPath() {
        return mVolumeInfo.getPath();
    }

    public boolean isMountedWritable() {
        return mVolumeInfo.isMountedWritable();
    }

    public boolean isMountedReadable() {
        return mVolumeInfo.isMountedReadable();
    }

    public static @NonNull
    Comparator<VolumeInfoCompat> getDescriptionComparator() {
        return new Comparator<VolumeInfoCompat>() {
            @Override
            public int compare(VolumeInfoCompat o1, VolumeInfoCompat o2) {
                return VolumeInfo.getDescriptionComparator().compare(
                        o1.mVolumeInfo, o2.mVolumeInfo);
            }
        };
    }
}
