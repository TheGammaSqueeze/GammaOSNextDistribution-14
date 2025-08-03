/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.google.uwb.support.fira;

import android.os.PersistableBundle;
import android.uwb.UwbAddress;

import androidx.annotation.Nullable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Uwb data transfer phase configuration
 */
public class FiraDataTransferPhaseConfig extends FiraParams {
    private static final int BUNDLE_VERSION_1 = 1;
    private static final int BUNDLE_VERSION_CURRENT = BUNDLE_VERSION_1;
    private static final int SHORT_MAC_ADDRESS = 0;

    private final byte mDtpcmRepetition;
    private final byte mDataTransferControl;
    private final List<FiraDataTransferPhaseManagementList> mDataTransferPhaseManagementList;

    private static final String KEY_BUNDLE_VERSION = "bundle_version";
    private static final String KEY_DTPCM_REPETITION = "dtpcm_repetition";
    private static final String KEY_DATA_TRANSFER_CONTROL = "data_transfer_control";
    private static final String KEY_MAC_ADDRESS_LIST = "mac_address";
    private static final String KEY_SLOT_BITMAP = "slot_bitmap";

    @Override
    public int getBundleVersion() {
        return BUNDLE_VERSION_CURRENT;
    }

    public byte getDtpcmRepetition() {
        return mDtpcmRepetition;
    }

    public byte getDataTransferControl() {
        return mDataTransferControl;
    }

    public List<FiraDataTransferPhaseManagementList> getDataTransferPhaseManagementList() {
        return mDataTransferPhaseManagementList;
    }

    private FiraDataTransferPhaseConfig(byte dtpcmRepetition, byte dataTransferControl,
            List<FiraDataTransferPhaseManagementList> dataTransferPhaseManagementList) {
        mDtpcmRepetition = dtpcmRepetition;
        mDataTransferControl = dataTransferControl;
        mDataTransferPhaseManagementList = dataTransferPhaseManagementList;
    }

    @Override
    public PersistableBundle toBundle() {
        PersistableBundle bundle = super.toBundle();
        bundle.putInt(KEY_BUNDLE_VERSION, getBundleVersion());
        bundle.putInt(KEY_DTPCM_REPETITION, mDtpcmRepetition);
        bundle.putInt(KEY_DATA_TRANSFER_CONTROL, mDataTransferControl);

        long[] macAddressList = new long[mDataTransferPhaseManagementList.size()];
        int i = 0;
        ByteBuffer slotBitmapByteBuffer = ByteBuffer.allocate(
                mDataTransferPhaseManagementList.size()
                        * (1 << ((mDataTransferControl & 0x0F) >> 1)));

        slotBitmapByteBuffer.order(ByteOrder.LITTLE_ENDIAN);

        for (FiraDataTransferPhaseManagementList dataTransferPhaseManagementList :
                mDataTransferPhaseManagementList) {
            macAddressList[i++] = uwbAddressToLong(
                dataTransferPhaseManagementList.getUwbAddress());
            slotBitmapByteBuffer.put(dataTransferPhaseManagementList.getSlotBitMap());
        }

        bundle.putLongArray(KEY_MAC_ADDRESS_LIST, macAddressList);
        bundle.putIntArray(KEY_SLOT_BITMAP, byteArrayToIntArray(slotBitmapByteBuffer.array()));

        return bundle;
    }

    @Nullable
    protected static int[] byteArrayToIntArray(@Nullable byte[] bytes) {
        if (bytes == null) {
            return null;
        }

        int[] values = new int[bytes.length];
        for (int i = 0; i < values.length; i++) {
            values[i] = bytes[i];
        }
        return values;
    }

    @Nullable
    protected static byte[] intArrayToByteArray(@Nullable int[] values) {
        if (values == null) {
            return null;
        }
        byte[] bytes = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            bytes[i] = (byte) values[i];
        }
        return bytes;
    }

    public static FiraDataTransferPhaseConfig fromBundle(PersistableBundle bundle) {
        switch (bundle.getInt(KEY_BUNDLE_VERSION)) {
            case BUNDLE_VERSION_1:
                return parseVersion1(bundle);
            default:
                throw new IllegalArgumentException("Invalid bundle version");
        }
    }

    private static FiraDataTransferPhaseConfig parseVersion1(PersistableBundle bundle) {
        FiraDataTransferPhaseConfig.Builder builder = new FiraDataTransferPhaseConfig.Builder();

        builder.setDtpcmRepetition((byte) bundle.getInt(KEY_DTPCM_REPETITION));
        byte dataTransferControl = (byte) bundle.getInt(KEY_DATA_TRANSFER_CONTROL);
        builder.setMacAddressMode((byte) (dataTransferControl & 0x01));
        builder.setSlotBitmapSize((byte) ((dataTransferControl & 0x0F) >> 1));

        List<FiraDataTransferPhaseManagementList> mDataTransferPhaseManagementList =
                new ArrayList<>();
        List<UwbAddress> macAddressList = new ArrayList<>();
        List<byte[]> slotBitmapList = new ArrayList<>();

        long[] macAddress = bundle.getLongArray(KEY_MAC_ADDRESS_LIST);
        for (int i = 0; i < macAddress.length; i++) {
            macAddressList.add(longToUwbAddress(macAddress[i],
                    ((dataTransferControl & 0x01) == SHORT_MAC_ADDRESS)
                    ? UwbAddress.SHORT_ADDRESS_BYTE_LENGTH
                        : UwbAddress.EXTENDED_ADDRESS_BYTE_LENGTH));
        }

        byte[] buffer = intArrayToByteArray(bundle.getIntArray(KEY_SLOT_BITMAP));
        ByteBuffer slotBitmapByteBuffer = ByteBuffer.wrap(buffer);
        int chunkBufferSize = 1 << (dataTransferControl >> 1);

        while (slotBitmapByteBuffer.hasRemaining()) {
            byte[] data = new byte[chunkBufferSize];
            int bytesToRead = Math.min(chunkBufferSize, slotBitmapByteBuffer.remaining());
            slotBitmapByteBuffer.get(data, 0, bytesToRead);
            slotBitmapList.add(data);
        }

        for (int i = 0; i < macAddressList.size(); i++) {
            mDataTransferPhaseManagementList.add(new FiraDataTransferPhaseManagementList(
                    macAddressList.get(i), slotBitmapList.get(i)));
        }

        builder.setDataTransferPhaseManagementList(mDataTransferPhaseManagementList);

        return builder.build();
    }

    /** Defines parameters for data transfer phase management list */
    public static class FiraDataTransferPhaseManagementList {
        private final UwbAddress mUwbAddress;
        private final byte[] mSlotBitMap;

        public FiraDataTransferPhaseManagementList(UwbAddress uwbAddress, byte[] slotBitmap) {
            mUwbAddress = uwbAddress;
            mSlotBitMap = slotBitmap;
        }

        public UwbAddress getUwbAddress() {
            return mUwbAddress;
        }

        public byte[] getSlotBitMap() {
            return mSlotBitMap;
        }
    }

    /** Builder */
    public static class Builder {
        private byte mDtpcmRepetition;
        private byte mMacAddressMode;
        private byte mSlotBitMapSize;
        private List<FiraDataTransferPhaseManagementList> mDataTransferPhaseManagementList =
                new ArrayList<>();

        public FiraDataTransferPhaseConfig.Builder setDtpcmRepetition(byte dtpcmRepetition) {
            mDtpcmRepetition = dtpcmRepetition;
            return this;
        }

        public FiraDataTransferPhaseConfig.Builder setMacAddressMode(byte macAddressMode) {
            mMacAddressMode = macAddressMode;
            return this;
        }

        public FiraDataTransferPhaseConfig.Builder setSlotBitmapSize(byte slotBitmapSize) {
            mSlotBitMapSize = slotBitmapSize;
            return this;
        }

        public FiraDataTransferPhaseConfig.Builder setDataTransferPhaseManagementList(
                List<FiraDataTransferPhaseManagementList> dataTransferPhaseManagementList) {
            mDataTransferPhaseManagementList = dataTransferPhaseManagementList;
            return this;
        }

        public FiraDataTransferPhaseConfig build() {
            return new FiraDataTransferPhaseConfig(
                mDtpcmRepetition,
                (byte) ((mSlotBitMapSize << 1) | (mMacAddressMode & 0x01)),
                mDataTransferPhaseManagementList);
        }
    }
}
