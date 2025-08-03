package com.android.tv.mdnsoffloadmanager;

import static device.google.atv.mdns_offload.IMdnsOffload.PassthroughBehavior.DROP_ALL;
import static device.google.atv.mdns_offload.IMdnsOffload.PassthroughBehavior.PASSTHROUGH_LIST;

import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.PrintWriter;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import device.google.atv.mdns_offload.IMdnsOffload;

@WorkerThread
public class OffloadWriter {

    private static final String TAG = OffloadWriter.class.getSimpleName();
    private static final int INVALID_OFFLOAD_KEY = -1;

    private boolean mOffloadState = false;
    private IMdnsOffload mVendorService;

    @NonNull
    private static String convertQNameForVendorService(String qname) {
        // We strip the trailing '.' when we provide QNames to the vendor service.
        if (qname.endsWith(".")) {
            return qname.substring(0, qname.length() - 1);
        }
        return qname;
    }

    private static String passthroughBehaviorToString(
            @IMdnsOffload.PassthroughBehavior byte passthroughBehavior) {
        switch (passthroughBehavior) {
            case IMdnsOffload.PassthroughBehavior.FORWARD_ALL:
                return "FORWARD_ALL";
            case IMdnsOffload.PassthroughBehavior.DROP_ALL:
                return "DROP_ALL";
            case IMdnsOffload.PassthroughBehavior.PASSTHROUGH_LIST:
                return "PASSTHROUGH_LIST";
        }
        throw new IllegalArgumentException("No such passthrough behavior " + passthroughBehavior);
    }

    void setVendorService(@Nullable IMdnsOffload vendorService) {
        mVendorService = vendorService;
    }

    boolean isVendorServiceConnected() {
        return mVendorService != null;
    }

    void resetAll() {
        if (!isVendorServiceConnected()) {
            Log.e(TAG, "Cannot reset vendor service, service is not connected.");
            return;
        }
        try {
            mVendorService.resetAll();
        } catch (RemoteException | ServiceSpecificException e) {
            Log.e(TAG, "Failed to reset vendor service.", e);
        }
    }

    /**
     * Apply the desired offload state on the vendor service. It may be necessary to refresh it,
     * after we bind to the vendor service to set the initial state, or restore the previous state.
     */
    void applyOffloadState() {
        setOffloadState(mOffloadState);
    }

    /**
     * Set the desired offload state and propagate to the vendor service.
     */
    void setOffloadState(boolean enabled) {
        if (!isVendorServiceConnected()) {
            Log.e(TAG, "Cannot set offload state, vendor service is not connected.");
            return;
        }
        try {
            mVendorService.setOffloadState(enabled);
        } catch (RemoteException | ServiceSpecificException e) {
            Log.e(TAG, "Failed to set offload state to {" + enabled + "}.", e);
        }
        mOffloadState = enabled;
    }

    /**
     * Retrieve and clear all metric counters.
     * <p>
     * TODO(b/270115511) do something with these metrics.
     */
    void retrieveAndClearMetrics(Collection<Integer> recordKeys) {
        try {
            int missCounter = mVendorService.getAndResetMissCounter();
            Log.d(TAG, "Missed queries:" + missCounter);
        } catch (RemoteException | ServiceSpecificException e) {
            Log.e(TAG, "getAndResetMissCounter failure", e);
        }
        for (int recordKey : recordKeys) {
            try {
                int hitCounter = mVendorService.getAndResetHitCounter(recordKey);
                Log.d(TAG, "Hits for record " + recordKey + " : " + hitCounter);
            } catch (RemoteException | ServiceSpecificException e) {
                Log.e(TAG, "getAndResetHitCounter failure for recordKey {" + recordKey + "}", e);
            }
        }
    }

    /**
     * Offload a list of records. Records are prioritized by their priority value, and lower
     * priority records may be dropped if not all fit in memory.
     *
     * @return The offload keys of successfully offloaded protocol responses.
     */
    Collection<Integer> writeOffloadData(
            String networkInterface, Collection<OffloadIntentStore.OffloadIntent> offloadIntents) {
        List<OffloadIntentStore.OffloadIntent> orderedOffloadIntents = offloadIntents
                .stream()
                .sorted(Comparator.comparingInt(offloadIntent -> offloadIntent.mPriority))
                .toList();
        Set<Integer> offloaded = new HashSet<>();
        for (OffloadIntentStore.OffloadIntent offloadIntent : orderedOffloadIntents) {
            Integer offloadKey = tryAddProtocolResponses(networkInterface, offloadIntent);
            if (offloadKey != null) {
                offloaded.add(offloadKey);
            }
        }
        return offloaded;
    }

    /**
     * Remove a set of protocol responses.
     *
     * @return The offload keys of deleted protocol responses.
     */
    Collection<Integer> deleteOffloadData(Set<Integer> offloadKeys) {
        Set<Integer> deleted = new HashSet<>();
        for (Integer offloadKey : offloadKeys) {
            if (tryRemoveProtocolResponses(offloadKey)) {
                deleted.add(offloadKey);
            }
        }
        return deleted;
    }

    /**
     * Add a list of entries to the passthrough list. Entries will be prioritized based on the
     * supplied priority value, where the supplied order will be maintained for equal values. Lower
     * priority records may be dropped if not all fit in memory.
     *
     * @return The set of successfully added passthrough entries.
     */
    Collection<String> writePassthroughData(
            String networkInterface,
            List<OffloadIntentStore.PassthroughIntent> ptIntents) {
        byte passthroughMode = ptIntents.isEmpty() ? DROP_ALL : PASSTHROUGH_LIST;
        trySetPassthroughBehavior(networkInterface, passthroughMode);

        // Note that this is a stable sort, therefore the provided order will be preserved for
        // entries that are not on the priority list.
        List<OffloadIntentStore.PassthroughIntent> orderedPtIntents = ptIntents
                .stream()
                .sorted(Comparator.comparingInt(pt -> pt.mPriority))
                .toList();
        Set<String> added = new HashSet<>();
        for (OffloadIntentStore.PassthroughIntent ptIntent : orderedPtIntents) {
            if (tryAddToPassthroughList(networkInterface, ptIntent)) {
                added.add(ptIntent.mOriginalQName);
            }
        }
        return added;
    }

    /**
     * Delete a set of entries on the passthrough list.
     *
     * @return The set of entries that were deleted.
     */
    Collection<String> deletePassthroughData(String networkInterface, Collection<String> qnames) {
        Set<String> deleted = new HashSet<>();
        for (String qname : qnames) {
            if (tryRemoveFromPassthroughList(networkInterface, qname)) {
                deleted.add(qname);
            }
        }
        return deleted;
    }

    @Nullable
    private Integer tryAddProtocolResponses(
            String networkInterface, OffloadIntentStore.OffloadIntent offloadIntent) {
        int offloadKey;
        try {
            offloadKey = mVendorService.addProtocolResponses(
                    networkInterface, offloadIntent.mProtocolData);
        } catch (RemoteException | ServiceSpecificException e) {
            String msg = "Failed to offload mDNS protocol response for record key {" +
                    offloadIntent.mRecordKey + "} on iface {" + networkInterface + "}";
            Log.e(TAG, msg, e);
            return null;
        }
        if (offloadKey == INVALID_OFFLOAD_KEY) {
            Log.e(TAG, "Failed to offload mDNS protocol data, vendor service returned error.");
            return null;
        }
        return offloadKey;
    }

    private boolean tryRemoveProtocolResponses(Integer offloadKey) {
        try {
            mVendorService.removeProtocolResponses(offloadKey);
            return true;
        } catch (RemoteException | ServiceSpecificException e) {
            String msg = "Failed to remove offloaded mDNS protocol response for offload key {"
                    + offloadKey + "}";
            Log.e(TAG, msg, e);
        }
        return false;
    }

    private void trySetPassthroughBehavior(String networkInterface, byte passthroughMode) {
        try {
            mVendorService.setPassthroughBehavior(networkInterface, passthroughMode);
        } catch (RemoteException | ServiceSpecificException e) {
            String msg = "Failed to set passthrough mode {"
                    + passthroughBehaviorToString(passthroughMode) + "}"
                    + " on iface {" + networkInterface + "}";
            Log.e(TAG, msg, e);
        }
    }

    private boolean tryAddToPassthroughList(
            String networkInterface,
            OffloadIntentStore.PassthroughIntent ptIntent) {
        String simpleQName = convertQNameForVendorService(ptIntent.mOriginalQName);
        boolean addedEntry;
        try {
            addedEntry = mVendorService.addToPassthroughList(networkInterface, simpleQName);
        } catch (RemoteException | ServiceSpecificException e) {
            String msg = "Failed to add passthrough list entry for qname {"
                    + ptIntent.mOriginalQName + "} on iface {" + networkInterface + "}";
            Log.e(TAG, msg, e);
            return false;
        }
        if (!addedEntry) {
            String msg = "Failed to add passthrough list entry for qname {"
                    + ptIntent.mOriginalQName + "} on iface {" + networkInterface + "}.";
            Log.e(TAG, msg);
            return false;
        }
        return true;
    }

    private boolean tryRemoveFromPassthroughList(String networkInterface, String qname) {
        String simpleQName = convertQNameForVendorService(qname);
        try {
            mVendorService.removeFromPassthroughList(networkInterface, simpleQName);
            return true;
        } catch (RemoteException | ServiceSpecificException e) {
            String msg = "Failed to remove passthrough for qname {" + qname + "}.";
            Log.e(TAG, msg, e);
        }
        return false;
    }

    void dump(PrintWriter writer) {
        writer.println("OffloadWriter:");
        writer.println("mOffloadState=%b".formatted(mOffloadState));
        writer.println("isVendorServiceConnected=%b".formatted(isVendorServiceConnected()));
        writer.println();
    }
}
