/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.libraries.rcs.simpleclient.protocol.sdp;

import android.text.TextUtils;

import com.google.auto.value.AutoValue;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;

import java.text.ParseException;
import java.util.List;
import java.util.Map;

/**
 * The media part of SDP implementation as per RFC 4566. This class supports minimal fields that is
 * required to represent MSRP session.
 */
@AutoValue
public abstract class SdpMedia {
    private static final String CRLF = "\r\n";

    public static Builder parseMediaLine(String line) throws ParseException {
        List<String> elements = Splitter.on(" ").limit(4).splitToList(line);

        // The valid media line should have 4 elements:
        // m=<name> <port> <protocol> <format>
        if (elements.size() != 4) {
            throw new ParseException("Invalid media line", 0);
        }

        // Parse each field from the media line.
        Builder builder = SdpMedia.newBuilder();
        builder
                .setName(elements.get(0))
                .setPort(Integer.parseInt(elements.get(1)))
                .setProtocol(elements.get(2))
                .setFormat(elements.get(3));

        return builder;
    }

    public static Builder newBuilder() {
        return new AutoValue_SdpMedia.Builder();
    }

    public abstract String name();

    public abstract int port();

    public abstract String protocol();

    public abstract String format();

    public abstract ImmutableMap<String, String> attributes();

    /** Encode the media section as a string. */
    public String encode() {
        StringBuilder builder = new StringBuilder();
        builder
                .append("m=")
                .append(name())
                .append(" ")
                .append(port())
                .append(" ")
                .append(protocol())
                .append(" ")
                .append(format())
                .append(CRLF);

        for (Map.Entry<String, String> attribute : attributes().entrySet()) {
            builder.append("a=").append(attribute.getKey());
            if (!TextUtils.isEmpty(attribute.getValue())) {
                builder.append(":").append(attribute.getValue());
            }
            builder.append(CRLF);
        }

        return builder.toString();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder setName(String name);

        public abstract Builder setPort(int port);

        public abstract Builder setProtocol(String protocol);

        public abstract Builder setFormat(String payload);

        public abstract ImmutableMap.Builder<String, String> attributesBuilder();

        public Builder addAttribute(String name, String value) {
            attributesBuilder().put(name, value);
            return this;
        }

        public Builder addAttribute(String name) {
            return addAttribute(name, "");
        }

        public abstract SdpMedia build();
    }
}
