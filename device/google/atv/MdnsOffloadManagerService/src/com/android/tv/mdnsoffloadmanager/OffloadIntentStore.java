package com.android.tv.mdnsoffloadmanager;

import android.os.IBinder;
import android.os.UserHandle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import device.google.atv.mdns_offload.IMdnsOffload;
import device.google.atv.mdns_offload.IMdnsOffloadManager;

/**
 * Class to store OffloadIntents made by clients and assign record keys.
 */
public class OffloadIntentStore {

    private static final String TAG = OffloadIntentStore.class.getSimpleName();

    private final AtomicInteger mNextKey = new AtomicInteger(1);
    private final ConcurrentMap<Integer, OffloadIntent> mOffloadIntentsByRecordKey =
            new ConcurrentHashMap<>();
    // Note that we need to preserve the order of passthrough intents.
    private final List<PassthroughIntent> mPassthroughIntents = new ArrayList<>();

    private final PriorityListManager mPriorityListManager;

    /**
     * Only listed packages may offload data or manage the passthrough list, requests from any other
     * packages are dropped.
     */
    private final Set<Integer> mAppIdAllowlist = new HashSet<>();

    OffloadIntentStore(@NonNull PriorityListManager priorityListManager) {
        mPriorityListManager = priorityListManager;
    }

    @WorkerThread
    void setAppIdAllowlist(Set<Integer> appIds) {
        mAppIdAllowlist.clear();
        mAppIdAllowlist.addAll(appIds);
    }

    /**
     * Register the intention to offload an mDNS service. The system will do its best to offload it
     * when possible (considering dependencies, network conditions etc.).
     * <p>
     * The offload intent will be associated with the caller via the clientToken, stored in the
     * internal memory store, and be assigned a unique record key.
     */
    OffloadIntent registerOffloadIntent(
            String networkInterface,
            IMdnsOffloadManager.OffloadServiceInfo serviceInfo,
            IBinder clientToken,
            int callerUid) {
        int recordKey = mNextKey.getAndIncrement();
        IMdnsOffload.MdnsProtocolData mdnsProtocolData = convertToMdnsProtocolData(serviceInfo);
        int priority = mPriorityListManager.getPriority(mdnsProtocolData, recordKey);
        int appId = UserHandle.getAppId(callerUid);
        OffloadIntent offloadIntent = new OffloadIntent(
                networkInterface, recordKey, mdnsProtocolData, clientToken, priority, appId);
        mOffloadIntentsByRecordKey.put(recordKey, offloadIntent);
        return offloadIntent;
    }

    /**
     * Retrieve all offload intents for a given interface.
     */
    @WorkerThread
    Collection<OffloadIntent> getOffloadIntentsForInterface(String networkInterface) {
        return mOffloadIntentsByRecordKey
                .values()
                .stream()
                .filter(intent -> intent.mNetworkInterface.equals(networkInterface)
                        && mAppIdAllowlist.contains(intent.mOwnerAppId))
                .toList();
    }

    /**
     * Retrieve an offload intent by its record key and remove from internal database.
     * <p>
     * Only permitted if the offload intent was registered by the same caller.
     */
    @WorkerThread
    OffloadIntent getAndRemoveOffloadIntent(int recordKey, IBinder clientToken) {
        OffloadIntent offloadIntent = mOffloadIntentsByRecordKey.get(recordKey);
        if (offloadIntent == null) {
            Log.e(TAG, "Failed to remove protocol responses, bad record key {"
                    + recordKey + "}.");
            return null;
        }
        if (!offloadIntent.mClientToken.equals(clientToken)) {
            Log.e(TAG, "Failed to remove protocol messages, bad client token {"
                    + clientToken + "}.");
            return null;
        }
        mOffloadIntentsByRecordKey.remove(recordKey);
        return offloadIntent;
    }

    @WorkerThread
    Collection<Integer> getRecordKeys() {
        return mOffloadIntentsByRecordKey.keySet();
    }

    /**
     * Create a passthrough intent, representing the intention to add a DNS query name to the
     * passthrough list. The system will do its best to configure the passthrough when possible.
     * <p>
     * The passthrough intent will be associated with the caller via the clientToken, stored in the
     * internal memory store, and identified by the passthrough QNAME.
     */
    @WorkerThread
    PassthroughIntent registerPassthroughIntent(
            String networkInterface,
            String qname,
            IBinder clientToken,
            int callerUid) {
        String canonicalQName = mPriorityListManager.canonicalQName(qname);
        int priority = mPriorityListManager.getPriority(canonicalQName, 0);
        int appId = UserHandle.getAppId(callerUid);
        PassthroughIntent passthroughIntent = new PassthroughIntent(
                networkInterface, qname, canonicalQName, clientToken, priority, appId);
        mPassthroughIntents.add(passthroughIntent);
        return passthroughIntent;
    }

    /**
     * Retrieve all passthrough intents for a given interface.
     */
    @WorkerThread
    List<PassthroughIntent> getPassthroughIntentsForInterface(String networkInterface) {
        return mPassthroughIntents
                .stream()
                .filter(intent -> intent.mNetworkInterface.equals(networkInterface)
                        && mAppIdAllowlist.contains(intent.mOwnerAppId))
                .toList();
    }

    /**
     * Retrieve a passthrough intent by its QNAME remove from internal database.
     * <p>
     * Only permitted if the passthrough intent was registered by the same caller.
     */
    @WorkerThread
    boolean removePassthroughIntent(String qname, IBinder clientToken) {
        String canonicalQName = mPriorityListManager.canonicalQName(qname);
        boolean removed = mPassthroughIntents.removeIf(
                pt -> pt.mCanonicalQName.equals(canonicalQName)
                        && pt.mClientToken.equals(clientToken));
        if (!removed) {
            Log.e(TAG, "Failed to remove passthrough intent, bad QNAME or client token.");
            return false;
        }
        return true;
    }

    private static IMdnsOffload.MdnsProtocolData convertToMdnsProtocolData(
            IMdnsOffloadManager.OffloadServiceInfo serviceData) {
        IMdnsOffload.MdnsProtocolData data = new IMdnsOffload.MdnsProtocolData();
        data.rawOffloadPacket = serviceData.rawOffloadPacket;
        data.matchCriteriaList = MdnsPacketParser.extractMatchCriteria(
                serviceData.rawOffloadPacket);
        return data;
    }

    @WorkerThread
    void dump(PrintWriter writer) {
        writer.println("OffloadIntentStore:");
        writer.println("offload intents:");
        mOffloadIntentsByRecordKey.values()
                .forEach(intent -> writer.println("* %s".formatted(intent)));
        writer.println("passthrough intents:");
        mPassthroughIntents.forEach(intent -> writer.println("* %s".formatted(intent)));
        writer.println();
    }

    /**
     * Create a detailed dump of the OffloadIntents, including a hexdump of the raw packets.
     */
    @WorkerThread
    void dumpProtocolData(PrintWriter writer) {
        writer.println("Protocol data dump:");
        mOffloadIntentsByRecordKey.values().forEach(intent -> {
            writer.println("mRecordKey=%d".formatted(intent.mRecordKey));
            IMdnsOffload.MdnsProtocolData data = intent.mProtocolData;
            writer.println("match criteria:");
            data.matchCriteriaList.forEach(criteria ->
                    writer.println("* %s".formatted(formatMatchCriteria(criteria))));
            writer.println("raw offload packet:");
            hexDump(writer, data.rawOffloadPacket);
        });
        writer.println();
    }

    /**
     * Class representing the intention to offload mDNS protocol data.
     */
    static class OffloadIntent {
        final String mNetworkInterface;
        final int mRecordKey;
        final IMdnsOffload.MdnsProtocolData mProtocolData;
        final IBinder mClientToken;
        final int mPriority; // Lower values take precedence.
        final int mOwnerAppId;

        private OffloadIntent(
                String networkInterface,
                int recordKey,
                IMdnsOffload.MdnsProtocolData protocolData,
                IBinder clientToken,
                int priority,
                int ownerAppId
        ) {
            mNetworkInterface = networkInterface;
            mRecordKey = recordKey;
            mProtocolData = protocolData;
            mClientToken = clientToken;
            mPriority = priority;
            mOwnerAppId = ownerAppId;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("OffloadIntent{");
            sb.append("mNetworkInterface='").append(mNetworkInterface).append('\'');
            sb.append(", mRecordKey=").append(mRecordKey);
            sb.append(", mPriority=").append(mPriority);
            sb.append(", mOwnerAppId=").append(mOwnerAppId);
            sb.append('}');
            return sb.toString();
        }
    }

    /**
     * Class representing the intention to configure mDNS passthrough for a given query name.
     */
    static class PassthroughIntent {
        final String mNetworkInterface;
        // Preserving the original upper/lowercase format.
        final String mOriginalQName;
        final String mCanonicalQName;
        final IBinder mClientToken;
        final int mPriority;
        final int mOwnerAppId;

        PassthroughIntent(
                String networkInterface,
                String originalQName,
                String canonicalQName,
                IBinder clientToken,
                int priority,
                int ownerAppId) {
            mNetworkInterface = networkInterface;
            mOriginalQName = originalQName;
            mCanonicalQName = canonicalQName;
            mClientToken = clientToken;
            mPriority = priority;
            mOwnerAppId = ownerAppId;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("PassthroughIntent{");
            sb.append("mNetworkInterface='").append(mNetworkInterface).append('\'');
            sb.append(", mOriginalQName='").append(mOriginalQName).append('\'');
            sb.append(", mCanonicalQName='").append(mCanonicalQName).append('\'');
            sb.append(", mPriority=").append(mPriority);
            sb.append(", mOwnerAppId=").append(mOwnerAppId);
            sb.append('}');
            return sb.toString();
        }
    }

    private String formatMatchCriteria(IMdnsOffload.MdnsProtocolData.MatchCriteria matchCriteria) {
        return "MatchCriteria{type=%d, nameOffset=%d}"
                .formatted(matchCriteria.type, matchCriteria.nameOffset);
    }

    private void hexDump(PrintWriter writer, byte[] data) {
        final int width = 16;
        for (int rowOffset = 0; rowOffset < data.length; rowOffset += width) {
            writer.printf("%06d:  ", rowOffset);

            for (int index = 0; index < width; index++) {
                if (rowOffset + index < data.length) {
                    writer.printf("%02x ", data[rowOffset + index]);
                } else {
                    writer.print("   ");
                }
            }

            int asciiWidth = Math.min(width, data.length - rowOffset);
            writer.print("  |  ");
            writer.println(new String(data, rowOffset, asciiWidth, StandardCharsets.US_ASCII)
                    .replaceAll("[^\\x20-\\x7E]", "."));
        }
    }
}
