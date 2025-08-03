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

package com.android.timezone.location.tools.tzs2.dump;

import com.android.storage.tools.block.dump.SingleFileDumper;
import com.android.timezone.location.storage.tzs2range.BankedTzIdSets;
import com.android.timezone.location.storage.tzs2range.TzS2RangeFileFormat;
import com.android.timezone.location.storage.tzs2range.read.HeaderBlock;

import java.io.File;
import java.util.List;

/** A {@link HeaderBlock.HeaderBlockVisitor} that dumps information to a file. */
final class HeaderBlockDumper extends SingleFileDumper implements HeaderBlock.HeaderBlockVisitor {

    HeaderBlockDumper(File headerBlockFile) {
        super(headerBlockFile);
    }

    @Override
    public void visitFileFormat(TzS2RangeFileFormat fileFormat) {
        println("File format");
        println("===========");
        println(fileFormat.toString());
        println();
    }

    @Override
    public void visitBankedTzIdSets(BankedTzIdSets bankedTzIdSets) {
        println("Banked TZ ID Sets");
        println("=================");
        mWriter.println("Strings");
        mWriter.println("-------");
        List<String> stringsByIndex = bankedTzIdSets.getStringsByIndex();
        for (int i = 0; i < stringsByIndex.size(); i++) {
            mWriter.println("[" + i + "]=\"" + stringsByIndex.get(i) + "\"");
        }
        mWriter.println();

        mWriter.println("Banks");
        mWriter.println("-----");
        for (int bankIndex = 0; bankIndex < bankedTzIdSets.getBankCount(); bankIndex++) {
            BankedTzIdSets.Bank bank = bankedTzIdSets.getBank(bankIndex);
            mWriter.println("Bank " + bank.getId());
            for (int tzIdSetIndex = 0; tzIdSetIndex < bank.getTzIdSetCount(); tzIdSetIndex++) {
                BankedTzIdSets.TzIdSet tzIdSet = bank.getTzIdSet(tzIdSetIndex);
                mWriter.println("[" + tzIdSetIndex + "]=" + tzIdSet.getTzIds()
                        + "(" + tzIdSet.getStringIds() + ")");
            }
            mWriter.println();
        }
        println();
    }
}
