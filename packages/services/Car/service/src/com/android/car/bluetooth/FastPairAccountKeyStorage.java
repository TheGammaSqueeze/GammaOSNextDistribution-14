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

package com.android.car.bluetooth;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;

import android.annotation.NonNull;
import android.car.builtin.util.Slogf;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.UserManager;
import android.util.Log;

import com.android.car.CarLog;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.IndentingPrintWriter;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

import javax.crypto.spec.SecretKeySpec;

/**
 * A collection of 128-bit Account Keys that are received over the Fast Pair protocol.
 *
 * The specification requires that we store at least 5 Account Keys, but places no upper bound on
 * how many we can store. It only mentions that they all must fit in our chosen packet size. To
 * support this, we have a variable fixed upper bound of the number of stored keys. If you input a
 * number less than five, it will be adjusted up.
 *
 * The specification also requires that we remove the least recently used key if we ever run out of
 * space. To support this, keys are stored in an LRU cache. Adding a key when storage is full will
 * automatically remove the least recently used key.
 *
 * The specification requires that keys are persisted. To support this, keys are written to the
 * user's Shared Preferences. There is one preferences for the count of keys, and then a preference
 * for each key in priority order, where an index preference is mapped to a key value, i.e. the
 * perference "0" would map to a 128-bit key.
 *
 * Keys are loaded from Shared Preferences upon creation of this object.
 */
public class FastPairAccountKeyStorage {
    private static final String TAG = CarLog.tagFor(FastPairAccountKeyStorage.class);
    private static final boolean DBG = Slogf.isLoggable(TAG, Log.DEBUG);

    private static final String FAST_PAIR_PREFERENCES = "com.android.car.bluetooth";
    private static final String NUM_ACCOUNT_KEYS = "AccountKeysCount";

    private final Context mContext;

    /**
     * Represents a 128-bit Account Key that can be received through the FastPair process.
     */
    public static class AccountKey {
        private final byte[] mKey;

        AccountKey(byte[] key) {
            mKey = key;
        }

        AccountKey(String key) {
            mKey = new BigInteger(key).toByteArray();
        }

        /**
         * Get a byte representation of this Account Key
         */
        public byte[] toBytes() {
            return mKey;
        }

        /**
         * Get a SecretKeySpec representation of this Account Key
         */
        public SecretKeySpec getKeySpec() {
            return new SecretKeySpec(mKey, "AES");
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(mKey);
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof AccountKey)) {
                return false;
            }
            AccountKey other = (AccountKey) obj;
            return other != null && Arrays.equals(toBytes(), other.toBytes());
        }

        @Override
        public String toString() {
            return Arrays.toString(mKey);
        }
    }

    /*
     * A LinkedHashSet is used as an LRU. Iterating on the LinkedHashSet will produce the items in
     * the order they were inserted-- first inserted, first iterated on.
     */
    private final LinkedHashSet<AccountKey> mKeys;
    private final Object mKeyLock = new Object();
    private final int mStorageSize;

    public FastPairAccountKeyStorage(Context context, int size) {
        if (size < 5) {
            throw new IllegalArgumentException("size < 5");
        }
        mContext = Objects.requireNonNull(context);
        mStorageSize = size;
        mKeys = new LinkedHashSet<AccountKey>(mStorageSize);
        load(); // A no-op if storage isn't unlocked yet
    }

    /**
     * Get the total number of account keys that can be stored
     */
    public int capacity() {
        return mStorageSize;
    }

    /**
     * Add an account key
     */
    public boolean add(@NonNull AccountKey key) {
        if (key == null) return false;
        Slogf.i("Adding key '%s'", key.toString());
        synchronized (mKeyLock) {
            // LinkedHashSet re-adds do not impact the ordering. To force the ordering to update,
            //  we'll remove the key first if its already in the set, then re-add it.
            if (mKeys.contains(key)) {
                mKeys.remove(key);
            }
            mKeys.add(key);
            trimToSize();
            commit();
            return true;
        }
    }

    /**
     * Remove an account key
     */
    public boolean remove(@NonNull AccountKey key) {
        if (key == null) return false;
        Slogf.i("Removing key '%s'", key.toString());
        synchronized (mKeyLock) {
            mKeys.remove(key);
            commit();
            return true;
        }
    }

    /**
     * Get a list of all the available account keys
     */
    public List<AccountKey> getAllAccountKeys() {
        synchronized (mKeyLock) {
            return new ArrayList<>(mKeys);
        }
    }

    /**
     * Clears all account keys from storage
     */
    public void clear() {
        synchronized (mKeyLock) {
            mKeys.clear();
            commit();
        }
    }

    /**
     * Removes the least recently used items until the size of our cache is less than or equal to
     * our configured maximum size.
     */
    private void trimToSize() {
        while (mKeys.size() > mStorageSize) {
            AccountKey key = mKeys.iterator().next();
            mKeys.remove(key);
            Slogf.d("Evicted key '%s'", key.toString());
        }
    }

    /**
     * Loads persisted account keys from Shared Preferences
     *
     * Account keys are stored in key value pairs of <integer> to <string>, where the integer is the
     * position in the LRU (higher is more recently used), and the string is a string version of the
     * bytes. There is also an "AccountKeysCount" preference indicating how many keys are stored.
     * Keys will have integer keys in the range [0, AccountKeysCount - 1].
     *
     * This cannot be called until the user is unlocked.
     */
    public boolean load() {
        if (!isUserUnlocked()) {
            // TODO (243016325): Determine a way to recover from a failed load()
            Slogf.w(TAG, "Loaded while user was not unlocked. Shared Preferences unavailable");
            return false;
        }

        List<AccountKey> keys = new ArrayList<>();
        SharedPreferences preferences =
                mContext.getSharedPreferences(FAST_PAIR_PREFERENCES, Context.MODE_PRIVATE);
        int numKeys = preferences.getInt(NUM_ACCOUNT_KEYS, 0);

        for (int i = 0; i < numKeys; i++) {
            String key = preferences.getString(Integer.toString(i), null);
            if (key != null) {
                keys.add(new AccountKey(key));
            }
        }
        Slogf.d(TAG, "Read %d/%d keys from SharedPreferences", keys.size(), numKeys);

        synchronized (mKeyLock) {
            mKeys.clear();
            for (AccountKey key : keys) {
                mKeys.add(key);
            }
            trimToSize();
            commit();
        }
        return true;
    }

    /**
     * Persists the set of Account Keys to Shared Preferences.
     *
     * Account keys are stored in key value pairs of <integer> to <string>, where the integer is the
     * position in the LRU (higher is more recently used), and the string is a string version of the
     * bytes. There is also an "AccountKeysCount" preference indicating how many keys are stored.
     * Keys will have integer keys in the range [0, AccountKeysCount - 1].
     */
    private boolean commit() {
        if (!isUserUnlocked()) {
            // TODO (243016325): Determine a way to recover from a failed commit()
            Slogf.w(TAG, "Committed while user was not unlocked. Shared Preferences unavailable");
            return false;
        }

        SharedPreferences preferences =
                mContext.getSharedPreferences(FAST_PAIR_PREFERENCES, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();

        // Get the current count of stored keys
        int accountKeyCount = preferences.getInt(NUM_ACCOUNT_KEYS, 0);
        int finalSize = mKeys.size();

        for (int i = accountKeyCount - 1; i >= finalSize; i--) {
            editor.remove(Integer.toString(i));
        }

        // Add the count of keys
        editor.putInt(NUM_ACCOUNT_KEYS, finalSize);

        // Add the keys themselves and apply
        int i = 0;
        for (AccountKey key : mKeys) {
            editor.putString(Integer.toString(i), new BigInteger(key.toBytes()).toString());
            i++;
        }
        editor.apply();

        if (DBG) {
            Slogf.d(TAG, "Committed keys to SharedPreferences, keys=%s", mKeys);
        }
        return true;
    }

    private boolean isUserUnlocked() {
        return mContext.getSystemService(UserManager.class).isUserUnlocked();
    }

    @Override
    public String toString() {
        return "FastPairAccountKeyStorage (Size=" + mKeys.size() + " / " + mStorageSize + ")";
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    void dump(IndentingPrintWriter writer) {
        writer.println(toString());
        writer.increaseIndent();
        List<AccountKey> keys = getAllAccountKeys();
        for (AccountKey key : keys) {
            writer.println("\n" + key);
        }
        writer.decreaseIndent();
    }
}
