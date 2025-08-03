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

package com.android.libraries.rcs.simpleclient.protocol.cpim;

import android.text.TextUtils;
import com.google.auto.value.AutoValue;
import com.google.common.base.Ascii;
import com.google.common.base.Utf8;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.CharStreams;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The CPIM implementation as per RFC 3862. This class supports minimal fields that is required to
 * represent a simple message for test purpose.
 */
@AutoValue
public abstract class SimpleCpimMessage {
    private static final String CRLF = "\r\n";
    private static final String COLSP = ": ";
    private static final Pattern NAMESPACE_HEADER_PATTERN =
            Pattern.compile("NS:\\s+(\\S+)\\s+<(.+)>");
    private static final Pattern HEADER_PATTERN = Pattern.compile("([^\\s:]+):\\s+(.+)");

    public abstract ImmutableMap<String, String> namespaces();

    public abstract ImmutableMap<String, String> headers();

    public abstract String contentType();

    public abstract String content();

    public String encode() {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : namespaces().entrySet()) {
            builder
                    .append("NS: ")
                    .append(entry.getKey())
                    .append(" <")
                    .append(entry.getValue())
                    .append(">")
                    .append(CRLF);
        }

        for (Map.Entry<String, String> entry : headers().entrySet()) {
            builder.append(entry.getKey()).append(COLSP).append(entry.getValue()).append(CRLF);
        }

        builder.append(CRLF);
        builder.append("Content-Type").append(COLSP).append(contentType());
        builder.append(CRLF);
        builder.append("Content-Length").append(COLSP).append(Utf8.encodedLength(content()));
        builder.append(CRLF).append(CRLF);
        builder.append(content());

        return builder.toString();
    }

    public static SimpleCpimMessage parse(byte[] content) throws IOException {
        BufferedReader reader =
                new BufferedReader(new InputStreamReader(new ByteArrayInputStream(content)));
        Builder builder = newBuilder();

        String line = reader.readLine();
        while (!TextUtils.isEmpty(line)) {
            Matcher namespaceMatcher = NAMESPACE_HEADER_PATTERN.matcher(line);
            Matcher headerMatcher = HEADER_PATTERN.matcher(line);
            if (namespaceMatcher.matches()) {
                builder.addNamespace(namespaceMatcher.group(1), namespaceMatcher.group(2));
            } else if (headerMatcher.matches()) {
                builder.addHeader(headerMatcher.group(1), headerMatcher.group(2));
            }

            line = reader.readLine();
        }

        line = reader.readLine();
        while (!TextUtils.isEmpty(line)) {
            Matcher headerMatcher = HEADER_PATTERN.matcher(line);
            if (headerMatcher.matches()) {
                if (Ascii.equalsIgnoreCase("content-type", headerMatcher.group(1))) {
                    builder.setContentType(headerMatcher.group(2));
                }
            }

            line = reader.readLine();
        }

        String body = CharStreams.toString(reader);
        builder.setContent(body);

        return builder.build();
    }

    @AutoValue.Builder
    public abstract static class Builder {
        public abstract ImmutableMap.Builder<String, String> namespacesBuilder();

        public abstract ImmutableMap.Builder<String, String> headersBuilder();

        public abstract Builder setContentType(String value);

        public abstract Builder setContent(String value);

        public abstract SimpleCpimMessage build();

        public Builder addNamespace(String name, String value) {
            namespacesBuilder().put(name, value);
            return this;
        }

        public Builder addHeader(String name, String value) {
            headersBuilder().put(name, value);
            return this;
        }
    }

    public static SimpleCpimMessage.Builder newBuilder() {
        return new AutoValue_SimpleCpimMessage.Builder();
    }
}
