package com.android.tv.mdnsoffloadmanager;

import android.content.res.Resources;

import androidx.annotation.NonNull;

import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import device.google.atv.mdns_offload.IMdnsOffload;

public class PriorityListManager {

    public static final int PRIORITIZED_QNAMES_ID = R.array.config_mdnsOffloadPriorityQnames;
    private final Map<String, Integer> mPriorityMap;

    PriorityListManager(@NonNull Resources resources) {
        String[] priorityList = resources.getStringArray(PRIORITIZED_QNAMES_ID);
        // We assign negative sorting keys to qNames on the priority list to ensure they are
        // prioritized. Priorities for all other records are assigned on a first come first served
        // order based on their record key.
        int priorityListSize = priorityList.length;
        mPriorityMap = IntStream.range(0, priorityListSize)
                .boxed()
                .collect(Collectors.toUnmodifiableMap(
                        index -> canonicalQName(priorityList[index]),
                        index -> -priorityListSize + index));
    }

    String canonicalQName(String qName) {
        String upper = qName.toUpperCase(Locale.ROOT);
        if (upper.endsWith(".")) {
            return upper;
        }
        return upper + ".";
    }

    int getPriority(String qname, int defaultPriority) {
        return mPriorityMap.getOrDefault(canonicalQName(qname), defaultPriority);
    }

    int getPriority(IMdnsOffload.MdnsProtocolData protocolData, int recordKey) {
        return protocolData.matchCriteriaList.stream()
                .mapToInt(mc -> {
                    String qname = MdnsPacketParser.extractFullName(
                            protocolData.rawOffloadPacket, mc.nameOffset);
                    return getPriority(qname, recordKey);
                })
                .min()
                .orElse(recordKey);
    }

}
