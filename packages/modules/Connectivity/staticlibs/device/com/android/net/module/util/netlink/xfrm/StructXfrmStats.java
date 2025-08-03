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
package com.android.net.module.util.netlink.xfrm;

import com.android.net.module.util.Struct;

/**
 * Struct xfrm_lifetime_cur
 *
 * <p>see include/uapi/linux/xfrm.h
 *
 * <pre>
 * struct xfrm_stats {
 *      __u32 replay_window;
 *      __u32 replay;
 *      __u32 integrity_failed;
 * };
 * </pre>
 *
 * @hide
 */
public class StructXfrmStats extends Struct {
    public static final int STRUCT_SIZE = 12;

    /** Number of packets that fall out of the replay window */
    @Field(order = 0, type = Type.U32)
    public final long replayWindow;

    /** Number of replayed packets */
    @Field(order = 1, type = Type.U32)
    public final long replay;

    /** Number of packets that failed authentication */
    @Field(order = 2, type = Type.U32)
    public final long integrityFailed;

    // Constructor that allows Strutc.parse(Class<T>, ByteBuffer) to work
    public StructXfrmStats(long replayWindow, long replay, long integrityFailed) {
        this.replayWindow = replayWindow;
        this.replay = replay;
        this.integrityFailed = integrityFailed;
    }

    // Constructor to build a new message
    public StructXfrmStats() {
        this(0, 0, 0);
    }
}
