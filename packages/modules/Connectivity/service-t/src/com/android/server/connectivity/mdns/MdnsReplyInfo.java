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

package com.android.server.connectivity.mdns;

import android.annotation.NonNull;

import java.net.InetSocketAddress;
import java.util.List;

/**
 * Info about a mDNS reply to be sent.
 */
public final class MdnsReplyInfo {
    @NonNull
    public final List<MdnsRecord> answers;
    @NonNull
    public final List<MdnsRecord> additionalAnswers;
    public final long sendDelayMs;
    @NonNull
    public final InetSocketAddress destination;
    @NonNull
    public final InetSocketAddress source;
    @NonNull
    public final List<MdnsRecord> knownAnswers;

    public MdnsReplyInfo(
            @NonNull List<MdnsRecord> answers,
            @NonNull List<MdnsRecord> additionalAnswers,
            long sendDelayMs,
            @NonNull InetSocketAddress destination,
            @NonNull InetSocketAddress source,
            @NonNull List<MdnsRecord> knownAnswers) {
        this.answers = answers;
        this.additionalAnswers = additionalAnswers;
        this.sendDelayMs = sendDelayMs;
        this.destination = destination;
        this.source = source;
        this.knownAnswers = knownAnswers;
    }

    @Override
    public String toString() {
        return "{MdnsReplyInfo: " + source + " to " + destination
                + ", answers: " + answers.size()
                + ", additionalAnswers: " + additionalAnswers.size()
                + ", knownAnswers: " + knownAnswers.size()
                + ", sendDelayMs " + sendDelayMs + "}";
    }
}
