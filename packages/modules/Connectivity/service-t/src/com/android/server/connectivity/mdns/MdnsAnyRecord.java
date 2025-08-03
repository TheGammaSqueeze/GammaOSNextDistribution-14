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

package com.android.server.connectivity.mdns;

import android.net.DnsResolver;

import java.io.IOException;

/**
 * A mDNS "ANY" record, used in mDNS questions to query for any record type.
 */
public class MdnsAnyRecord extends MdnsRecord {

    protected MdnsAnyRecord(String[] name, MdnsPacketReader reader) throws IOException {
        super(name, TYPE_ANY, reader, true /* isQuestion */);
    }

    protected MdnsAnyRecord(String[] name, boolean unicast) {
        super(name, TYPE_ANY, DnsResolver.CLASS_IN /* cls */,
                0L /* receiptTimeMillis */, unicast /* cacheFlush */, 0L /* ttlMillis */);
    }

    @Override
    protected void readData(MdnsPacketReader reader) throws IOException {
        // No data to read
    }

    @Override
    protected void writeData(MdnsPacketWriter writer) throws IOException {
        // No data to write
    }
}
