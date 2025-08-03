/*
 * Copyright 2018 The Android Open Source Project
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

package com.android.bluetooth.audio_util;

import android.content.Context;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.android.bluetooth.Utils;
import com.android.internal.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * This class provides a way to connect to multiple browsable players at a time.
 * It will attempt to simultaneously connect to a list of services that support
 * the MediaBrowserService. After a timeout, the list of connected players will
 * be returned via callback.
 *
 * The main use of this class is to check whether a player can be browsed despite
 * using the MediaBrowserService. This way we do not have to do the same checks
 * when constructing BrowsedPlayerWrappers by hand.
 */
public class BrowsablePlayerConnector extends Handler {
    private static final String TAG = "AvrcpBrowsablePlayerConnector";
    private static final boolean DEBUG = true;
    private static final long CONNECT_TIMEOUT_MS = 10000; // Time in ms to wait for a connection

    private static final int MSG_GET_FOLDER_ITEMS_CB = 0;
    private static final int MSG_CONNECT_CB = 1;
    private static final int MSG_TIMEOUT = 2;

    private static BrowsablePlayerConnector sInjectConnector;
    private PlayerListCallback mCallback;

    private List<BrowsedPlayerWrapper> mResults = new ArrayList<BrowsedPlayerWrapper>();
    private Set<BrowsedPlayerWrapper> mPendingPlayers = new HashSet<BrowsedPlayerWrapper>();

    interface PlayerListCallback {
        void run(List<BrowsedPlayerWrapper> result);
    }

    /**
     * @hide
     */
    @VisibleForTesting
    static void setInstanceForTesting(BrowsablePlayerConnector connector) {
        Utils.enforceInstrumentationTestMode();
        sInjectConnector = connector;
    }

    static BrowsablePlayerConnector connectToPlayers(
            Context context,
            Looper looper,
            List<ResolveInfo> players,
            PlayerListCallback cb) {
        if (sInjectConnector != null) {
            return sInjectConnector;
        }
        if (cb == null) {
            Log.wtf(TAG, "Null callback passed");
            return null;
        }

        BrowsablePlayerConnector newConnector = new BrowsablePlayerConnector(looper, cb);

        // Try to start connecting all the browsed player wrappers
        for (ResolveInfo info : players) {
            BrowsedPlayerWrapper player = BrowsedPlayerWrapper.wrap(
                            context,
                            looper,
                            info.serviceInfo.packageName,
                            info.serviceInfo.name);
            newConnector.mPendingPlayers.add(player);
            player.connect((int status, BrowsedPlayerWrapper wrapper) -> {
                // Use the handler to avoid concurrency issues
                if (DEBUG) {
                    Log.d(TAG, "Browse player callback called: package="
                            + info.serviceInfo.packageName
                            + " : status=" + status);
                }
                newConnector.obtainMessage(MSG_CONNECT_CB, status, 0, wrapper).sendToTarget();
            });
        }

        newConnector.sendEmptyMessageDelayed(MSG_TIMEOUT, CONNECT_TIMEOUT_MS);
        return newConnector;
    }

    private BrowsablePlayerConnector(Looper looper, PlayerListCallback cb) {
        super(looper);
        mCallback = cb;
    }

    private void removePendingPlayers() {
        for (BrowsedPlayerWrapper wrapper : mPendingPlayers) {
            if (DEBUG) Log.d(TAG, "Disconnecting " + wrapper.getPackageName());
            wrapper.disconnect();
        }
        mPendingPlayers.clear();
    }

    void cleanup() {
        if (mPendingPlayers.size() != 0) {
            Log.i(TAG, "Bluetooth turn off with " + mPendingPlayers.size() + " pending player(s)");
            removePendingPlayers();
            removeCallbacksAndMessages(null);
        }
    }

    @Override
    public void handleMessage(Message msg) {
        if (DEBUG) Log.d(TAG, "Received a message: msg.what=" + msg.what);
        switch(msg.what) {
            case MSG_GET_FOLDER_ITEMS_CB: {
                int status = msg.arg1;
                int results_size = msg.arg2;
                BrowsedPlayerWrapper wrapper = (BrowsedPlayerWrapper) msg.obj;

                // If we failed to remove the wrapper from the pending set, that
                // means a timeout occurred and the callback was triggered afterwards
                // or the connector was cleaned up.
                if (!mPendingPlayers.remove(wrapper)) {
                    return;
                }

                if (status == BrowsedPlayerWrapper.STATUS_SUCCESS && results_size != 0) {
                    Log.i(TAG, "Successfully added package to results: "
                            + wrapper.getPackageName());
                    mResults.add(wrapper);
                }
                break;
            }

            case MSG_CONNECT_CB: {
                BrowsedPlayerWrapper wrapper = (BrowsedPlayerWrapper) msg.obj;

                if (msg.arg1 != BrowsedPlayerWrapper.STATUS_SUCCESS) {
                    Log.i(TAG, wrapper.getPackageName() + " is not browsable");
                    // If we failed to remove the wrapper from the pending set, that
                    // means a timeout occurred and the callback was triggered afterwards
                    if (!mPendingPlayers.remove(wrapper)) {
                        return;
                    }
                    break;
                }

                // Check to see if the root folder has any items
                if (DEBUG) {
                    Log.i(TAG, "Checking root contents for " + wrapper.getPackageName());
                }
                wrapper.getFolderItems(wrapper.getRootId(),
                        (int status, String mediaId, List<ListItem> results) -> {
                            // Send the response as a message so that it is properly
                            // synchronized
                            obtainMessage(MSG_GET_FOLDER_ITEMS_CB, status, results.size(), wrapper)
                                    .sendToTarget();
                        });
                break;
            }

            case MSG_TIMEOUT: {
                Log.v(TAG, "Timed out waiting for players");
                removePendingPlayers();
                break;
            }
        }

        if (mPendingPlayers.size() == 0) {
            Log.i(TAG, "Successfully connected to "
                    + mResults.size() + " browsable players.");
            removeMessages(MSG_TIMEOUT);
            mCallback.run(mResults);
        }
    }
}
