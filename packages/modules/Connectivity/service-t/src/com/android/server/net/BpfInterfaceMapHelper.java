/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.server.net;

import android.os.Build;
import android.system.ErrnoException;
import android.util.IndentingPrintWriter;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.android.internal.annotations.VisibleForTesting;
import com.android.net.module.util.BpfDump;
import com.android.net.module.util.BpfMap;
import com.android.net.module.util.IBpfMap;
import com.android.net.module.util.Struct.S32;

/**
 * Monitor interface added (without removed) and right interface name and its index to bpf map.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
public class BpfInterfaceMapHelper {
    private static final String TAG = BpfInterfaceMapHelper.class.getSimpleName();
    // This is current path but may be changed soon.
    private static final String IFACE_INDEX_NAME_MAP_PATH =
            "/sys/fs/bpf/netd_shared/map_netd_iface_index_name_map";
    private final IBpfMap<S32, InterfaceMapValue> mIndexToIfaceBpfMap;

    public BpfInterfaceMapHelper() {
        this(new Dependencies());
    }

    @VisibleForTesting
    public BpfInterfaceMapHelper(Dependencies deps) {
        mIndexToIfaceBpfMap = deps.getInterfaceMap();
    }

    /**
     * Dependencies of BpfInerfaceMapUpdater, for injection in tests.
     */
    @VisibleForTesting
    public static class Dependencies {
        /** Create BpfMap for updating interface and index mapping. */
        public IBpfMap<S32, InterfaceMapValue> getInterfaceMap() {
            try {
                return new BpfMap<>(IFACE_INDEX_NAME_MAP_PATH,
                    S32.class, InterfaceMapValue.class);
            } catch (ErrnoException e) {
                Log.e(TAG, "Cannot create interface map: " + e);
                return null;
            }
        }
    }

    /** get interface name by interface index from bpf map */
    public String getIfNameByIndex(final int index) {
        try {
            final InterfaceMapValue value = mIndexToIfaceBpfMap.getValue(new S32(index));
            if (value == null) {
                Log.e(TAG, "No if name entry for index " + index);
                return null;
            }
            return value.getInterfaceNameString();
        } catch (ErrnoException e) {
            Log.e(TAG, "Failed to get entry for index " + index + ": " + e);
            return null;
        }
    }

    /**
     * Dump BPF map
     *
     * @param pw print writer
     */
    public void dump(final IndentingPrintWriter pw) {
        pw.println("BPF map status:");
        pw.increaseIndent();
        BpfDump.dumpMapStatus(mIndexToIfaceBpfMap, pw, "IfaceIndexNameMap",
                IFACE_INDEX_NAME_MAP_PATH);
        pw.decreaseIndent();
        pw.println("BPF map content:");
        pw.increaseIndent();
        BpfDump.dumpMap(mIndexToIfaceBpfMap, pw, "IfaceIndexNameMap",
                (key, value) -> "ifaceIndex=" + key.val
                        + " ifaceName=" + value.getInterfaceNameString());
        pw.decreaseIndent();
    }
}
