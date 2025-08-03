package com.android.tv.mdnsoffloadmanager;

import android.os.IInterface;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import device.google.atv.mdns_offload.IMdnsOffload;

/**
 * Lightweight test double, mimicking the behavior we expect from the real vendor service
 * implementation. Refer to {@link IMdnsOffload} for the API specification.
 *
 * For testing purposes only, see go/choose-test-double#faking-definition.
 */
public class FakeMdnsOffloadService extends IMdnsOffload.Stub {

    private static final String TAG = FakeMdnsOffloadService.class.getSimpleName();
    private static final int MAX_QNAME_LENGTH = 255;

    /**
     * No. of records that can be held in memory, per interface. Further records will be dropped.
     */
    private static final int OFFLOAD_CAPACITY = 3;

    /**
     * No. of QNames in passthrough list that can be held in memory, per interface. Further entries
     * will be dropped.
     */
    private static final int PASSTHROUGH_CAPACITY = 4;

    static class OffloadData {
        byte passthroughBehavior = PassthroughBehavior.DROP_ALL;
        final List<MdnsProtocolData> offloadedRecords = new ArrayList<>();
        final List<String> passthroughQNames = new ArrayList<>();
    }

    boolean mOffloadState = false;
    int mNextId = 0;
    int mMissCounter = 0;
    final Map<String, OffloadData> mOffloadDataByInterface = new HashMap<>();
    final Map<Integer, MdnsProtocolData> mProtocolDataById = new HashMap<>();
    final Map<Integer, Integer> mHitCounters = new HashMap<>();

    OffloadData getOffloadData(String iface) {
        return mOffloadDataByInterface.computeIfAbsent(iface, ifc -> new OffloadData());
    }

    @Override
    public void resetAll() throws RemoteException {
        mNextId = 0;
        mMissCounter = 0;
        mProtocolDataById.clear();
        mOffloadDataByInterface.clear();
        mHitCounters.clear();
    }

    @Override
    public boolean setOffloadState(boolean enabled) throws RemoteException {
        mOffloadState = enabled;
        return mOffloadState;
    }

    /**
     * @see IMdnsOffload#addProtocolResponses
     *
     * Note that we do not deduplicate records here, since it is explicitly not part of the vendor
     * spec.
     */
    @Override
    public int addProtocolResponses(String iface, MdnsProtocolData protocolData)
        throws RemoteException {
        OffloadData offloadData = getOffloadData(iface);
        if (offloadData.offloadedRecords.size() < OFFLOAD_CAPACITY) {
            offloadData.offloadedRecords.add(protocolData);
            int id = mNextId++;
            mProtocolDataById.put(id, protocolData);
            log("Added offloaded data with key %d on iface %s", id, iface);
            return id;
        }
        log("Failed to add offloaded data on iface %s", iface);
        return -1;
    }

    @Override
    public void removeProtocolResponses(int recordKey) throws RemoteException {
        for (OffloadData offloadData : mOffloadDataByInterface.values()) {
            offloadData.offloadedRecords.remove(mProtocolDataById.get(recordKey));
        }
        MdnsProtocolData removed = mProtocolDataById.remove(recordKey);
        if (removed != null) {
            log("Removed offloaded record %d.", recordKey);
        } else {
            log("Failed to remove offloaded record %s.", recordKey);
        }
    }

    @Override
    public int getAndResetHitCounter(int recordKey) throws RemoteException {
        int count = mHitCounters.getOrDefault(recordKey, 0);
        mHitCounters.remove(recordKey);
        return count;
    }

    @Override
    public int getAndResetMissCounter() throws RemoteException {
        int count = mMissCounter;
        mMissCounter = 0;
        return count;
    }

    @Override
    public boolean addToPassthroughList(String iface, String qname)
        throws RemoteException {
        OffloadData offloadData = getOffloadData(iface);
        if (offloadData.passthroughQNames.size() < PASSTHROUGH_CAPACITY
                && qname.length() <= MAX_QNAME_LENGTH) {
            offloadData.passthroughQNames.add(qname);
            log("Added %s to PT list for iface %s", qname, iface);
            return true;
        }
        log("Failed to add %s to PT list for iface %s", qname, iface);
        return false;
    }

    @Override
    public void removeFromPassthroughList(String iface, String qname)
        throws RemoteException {
        boolean removed = getOffloadData(iface).passthroughQNames.remove(qname);
        if (removed) {
            log("Removed %s from PT list for iface %s", qname, iface);
        } else {
            log("Failed to remove %s from PT list for iface %s", qname, iface);
        }
    }

    @Override
    public void setPassthroughBehavior(String iface, byte behavior)
        throws RemoteException {
        if (!List.of(
            PassthroughBehavior.FORWARD_ALL,
            PassthroughBehavior.DROP_ALL,
            PassthroughBehavior.PASSTHROUGH_LIST).contains(behavior)) {
            throw new IllegalArgumentException("Invalid passthrough behavior");
        }
        getOffloadData(iface).passthroughBehavior = behavior;
        log("Set PT behavior to %d for iface %s", behavior, iface);
    }

    @Override
    public int getInterfaceVersion() throws RemoteException {
        return 0;
    }

    @Override
    public String getInterfaceHash() throws RemoteException {
        return null;
    }

    @Nullable
    @Override
    public IInterface queryLocalInterface(@NonNull String descriptor) {
        return this;
    }

    private void log(String pattern, Object... args) {
        Log.d(TAG, String.format(pattern, args));
    }
}
