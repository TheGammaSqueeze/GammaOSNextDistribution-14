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

import com.google.auto.value.AutoValue;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The SDP implementation as per RFC 4566. This class supports minimal fields that is required to
 * represent MSRP session.
 */
@AutoValue
public abstract class SimpleSdpMessage {
    private static final String CRLF = "\r\n";

    private static final String PREFIX_VERSION = "v";
    private static final String PREFIX_ORIGIN = "o";
    private static final String PREFIX_SESSION = "s";
    private static final String PREFIX_CONNECTION = "c";
    private static final String PREFIX_TIME = "t";
    private static final String PREFIX_MEDIA = "m";
    private static final String PREFIX_ATTRIBUTE = "a";
    private static final String EQUAL = "=";

    public static SimpleSdpMessage parse(InputStream stream) throws ParseException, IOException {
        Builder builder = new AutoValue_SimpleSdpMessage.Builder();
        SdpMedia.Builder currentMediaBuilder = null;
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));

        String line = reader.readLine();
        while (line != null) {
            List<String> parts = Splitter.on("=").trimResults().limit(2).splitToList(line);
            if (parts.size() != 2) {
                throw new ParseException("Invalid SDP format", 0);
            }
            String prefix = parts.get(0);
            String value = parts.get(1);

            switch (prefix) {
                case PREFIX_VERSION:
                    builder.setVersion(value);
                    break;
                case PREFIX_ORIGIN:
                    builder.setOrigin(value);
                    break;
                case PREFIX_SESSION:
                    builder.setSession(value);
                    break;
                case PREFIX_CONNECTION:
                    builder.setConnection(value);
                    break;
                case PREFIX_TIME:
                    builder.setTime(value);
                    break;
                case PREFIX_MEDIA:
                    if (currentMediaBuilder != null) {
                        builder.addMedia(currentMediaBuilder.build());
                    }
                    currentMediaBuilder = SdpMedia.parseMediaLine(value);
                    break;
                case PREFIX_ATTRIBUTE:
                    if (currentMediaBuilder != null) {
                        List<String> kv = Splitter.on(":").trimResults().limit(2).splitToList(
                                value);
                        currentMediaBuilder.addAttribute(kv.get(0), kv.size() < 2 ? "" : kv.get(1));
                    }
                    break;
                default:
                    // Rest of the fields are ignored as they're not used for describing MSRP
                    // session.
                    break;
            }
            line = reader.readLine();
        }

        if (currentMediaBuilder != null) {
            builder.addMedia(currentMediaBuilder.build());
        }

        return builder.build();
    }

    private static String encodeLine(String prefix, String value) {
        return prefix + EQUAL + value + CRLF;
    }

    public static Builder newBuilder() {
        return new AutoValue_SimpleSdpMessage.Builder();
    }

    public abstract String version();

    public abstract String origin();

    public abstract String session();

    public abstract String connection();

    public abstract String time();

    public abstract ImmutableList<SdpMedia> media();

    /** Return the IP address in the connection line. */
    public Optional<String> getAddress() {
        if (connection() == null) {
            return Optional.empty();
        }

        List<String> parts = Splitter.on(" ").limit(3).trimResults().splitToList(connection());
        if (parts.size() != 3) {
            return Optional.empty();
        }

        return Optional.of(parts.get(2));
    }

    /** Return the port in the first media line. */
    public OptionalInt getPort() {
        if (media().isEmpty()) {
            return OptionalInt.empty();
        }

        return OptionalInt.of(media().get(0).port());
    }

    public Optional<String> getPath() {
        if (media().isEmpty()) {
            return Optional.empty();
        }

        return Optional.ofNullable(media().get(0).attributes().get("path"));
    }

    /** Encode the entire SDP fields as a string. */
    public String encode() {
        StringBuilder builder = new StringBuilder();
        builder
                .append(encodeLine(PREFIX_VERSION, version()))
                .append(encodeLine(PREFIX_ORIGIN, origin()))
                .append(encodeLine(PREFIX_SESSION, session()))
                .append(encodeLine(PREFIX_CONNECTION, connection()))
                .append(encodeLine(PREFIX_TIME, time()));

        for (SdpMedia media : media()) {
            builder.append(media.encode());
        }

        return builder.toString();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract Builder setVersion(String version);

        public abstract Builder setOrigin(String origin);

        public abstract Builder setSession(String session);

        public abstract Builder setConnection(String connection);

        public abstract Builder setTime(String connection);

        public abstract ImmutableList.Builder<SdpMedia> mediaBuilder();

        public Builder addMedia(SdpMedia media) {
            mediaBuilder().add(media);
            return this;
        }

        public abstract SimpleSdpMessage build();
    }
}
