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

import android.os.storage.DiskInfo;
import android.os.storage.StorageEventListener;
import android.os.storage.VolumeInfo;
import android.os.storage.VolumeRecord;

public class StorageEventListenerCompat {
    StorageEventListener listener;

    public StorageEventListenerCompat() {
        this.listener = new StorageEventListener() {
            @Override
            public void onStorageStateChanged(String path, String oldState, String newState) {
                StorageEventListenerCompat.this.onStorageStateChanged(path, oldState, newState);
            }

            @Override
            public void onVolumeStateChanged(VolumeInfo vol, int oldState, int newState) {
                StorageEventListenerCompat.this.onVolumeStateChanged(
                        new VolumeInfoCompat(vol), oldState, newState);
            }

            @Override
            public void onVolumeRecordChanged(VolumeRecord rec) {
                StorageEventListenerCompat.this.onVolumeRecordChanged(new VolumeRecordCompat(rec));
            }

            @Override
            public void onVolumeForgotten(String fsUuid) {
                StorageEventListenerCompat.this.onVolumeForgotten(fsUuid);
            }

            @Override
            public void onDiskScanned(DiskInfo disk, int volumeCount) {
                StorageEventListenerCompat.this.onDiskScanned(
                        new DiskInfoCompat(disk), volumeCount);
            }

            @Override
            public void onDiskDestroyed(DiskInfo disk) {
                StorageEventListenerCompat.this.onDiskDestroyed(new DiskInfoCompat(disk));
            }
        };
    }

    public void onDiskDestroyed(DiskInfoCompat disk) {
    }

    public void onVolumeStateChanged(VolumeInfoCompat vol, int oldState, int newState) {
    }

    public void onDiskScanned(DiskInfoCompat disk, int volumeCount) {
    }

    public void onVolumeRecordChanged(VolumeRecordCompat rec) {
    }

    public void onStorageStateChanged(String path, String oldState, String newState) {
    }

    public void onVolumeForgotten(String fsUuid) {
    }
}
