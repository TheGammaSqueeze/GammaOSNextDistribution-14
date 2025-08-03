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

package com.android.server.connectivity.mdns;

import android.annotation.Nullable;

import androidx.annotation.VisibleForTesting;

import com.android.server.connectivity.mdns.MdnsServiceInfo.TextEntry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** An mDNS "TXT" record, which contains a list of {@link TextEntry}. */
@VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
public class MdnsTextRecord extends MdnsRecord {
    private List<TextEntry> entries;

    public MdnsTextRecord(String[] name, MdnsPacketReader reader) throws IOException {
        this(name, reader, false);
    }

    public MdnsTextRecord(String[] name, MdnsPacketReader reader, boolean isQuestion)
            throws IOException {
        super(name, TYPE_TXT, reader, isQuestion);
    }

    public MdnsTextRecord(String[] name, boolean isUnicast) {
        super(name, TYPE_TXT,
                MdnsConstants.QCLASS_INTERNET | (isUnicast ? MdnsConstants.QCLASS_UNICAST : 0),
                0L /* receiptTimeMillis */, false /* cacheFlush */, 0L /* ttlMillis */);
    }

    public MdnsTextRecord(String[] name, long receiptTimeMillis, boolean cacheFlush, long ttlMillis,
            List<TextEntry> entries) {
        super(name, TYPE_TXT, MdnsConstants.QCLASS_INTERNET, receiptTimeMillis, cacheFlush,
                ttlMillis);
        this.entries = entries;
    }

    /** Returns the list of strings. */
    public List<String> getStrings() {
        final List<String> list = new ArrayList<>(entries.size());
        for (TextEntry entry : entries) {
            list.add(entry.toString());
        }
        return Collections.unmodifiableList(list);
    }

    /** Returns the list of TXT key-value pairs. */
    public List<TextEntry> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    @Override
    protected void readData(MdnsPacketReader reader) throws IOException {
        entries = new ArrayList<>();
        while (reader.getRemaining() > 0) {
            TextEntry entry = reader.readTextEntry();
            if (entry != null) {
                entries.add(entry);
            }
        }
    }

    @Override
    protected void writeData(MdnsPacketWriter writer) throws IOException {
        if (entries != null) {
            for (TextEntry entry : entries) {
                writer.writeTextEntry(entry);
            }
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("TXT: {");
        if (entries != null) {
            for (TextEntry entry : entries) {
                sb.append(' ').append(entry);
            }
        }
        sb.append("}");

        return sb.toString();
    }

    @Override
    public int hashCode() {
        return (super.hashCode() * 31) + Objects.hash(entries);
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MdnsTextRecord)) {
            return false;
        }

        return super.equals(other) && Objects.equals(entries, ((MdnsTextRecord) other).entries);
    }
}