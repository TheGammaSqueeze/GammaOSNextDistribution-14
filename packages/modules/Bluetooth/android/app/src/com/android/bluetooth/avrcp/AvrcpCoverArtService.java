/*
 * Copyright 2020 The Android Open Source Project
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

package com.android.bluetooth.avrcp;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.util.Log;

import com.android.bluetooth.BluetoothObexTransport;
import com.android.bluetooth.IObexConnectionHandler;
import com.android.bluetooth.ObexServerSockets;
import com.android.bluetooth.audio_util.Image;
import com.android.obex.ServerSession;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;

/**
 * The AVRCP Cover Art Service
 *
 * This service handles allocation of image handles and storage of images. It also owns the
 * BIP OBEX server that handles requests to get AVRCP cover artwork.
 */
public class AvrcpCoverArtService {
    private static final String TAG = "AvrcpCoverArtService";
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private static final int COVER_ART_STORAGE_MAX_ITEMS = 32;

    /**
     * Limiting transmit packet size because some carkits are disconnected if
     * AVRCP Cover Art OBEX packet size exceed 1024 bytes.
     */
    private static final int MAX_TRANSMIT_PACKET_SIZE = 1024;

    private final Context mContext;

    // Cover Art and Image Handle objects
    private final AvrcpCoverArtStorage mStorage;

    // BIP Server Objects
    private volatile boolean mShutdown = true;
    private final SocketAcceptor mAcceptThread;
    private ObexServerSockets mServerSockets = null;
    private final HashMap<BluetoothDevice, ServerSession> mClients =
            new HashMap<BluetoothDevice, ServerSession>();
    private final Object mClientsLock = new Object();
    private final Object mServerLock = new Object();

    // Native interface
    private AvrcpNativeInterface mNativeInterface;

    public AvrcpCoverArtService(Context context) {
        mContext = context;
        mNativeInterface = AvrcpNativeInterface.getInstance();
        mAcceptThread = new SocketAcceptor();
        mStorage = new AvrcpCoverArtStorage(COVER_ART_STORAGE_MAX_ITEMS);
    }

    /**
     * Start the AVRCP Cover Art Service.
     *
     * This will start up a BIP OBEX server and record the l2cap psm in the SDP record, and begin
     * accepting connections.
     */
    public boolean start() {
        debug("Starting service");
        if (!isShutdown()) {
            error("Service already started");
            return true;
        }
        mStorage.clear();
        return startBipServer();
    }

    /**
     * Stop the AVRCP Cover Art Service.
     *
     * Tear down existing connections, remove ourselved from the SDP record.
     */
    public boolean stop() {
        debug("Stopping service");
        stopBipServer();
        synchronized (mClientsLock) {
            for (ServerSession session : mClients.values()) {
                session.close();
            }
            mClients.clear();
        }
        mStorage.clear();
        return true;
    }

    private boolean startBipServer() {
        debug("Starting BIP OBEX server");
        synchronized (mServerLock) {
            mServerSockets = ObexServerSockets.create(mAcceptThread);
            if (mServerSockets == null) {
                error("Failed to get a server socket. Can't setup cover art service");
                return false;
            }
            registerBipServer(mServerSockets.getL2capPsm());
            mShutdown = false;
            debug("Service started, psm=" + mServerSockets.getL2capPsm());
        }
        return true;
    }

    private boolean stopBipServer() {
        debug("Stopping BIP OBEX server");
        synchronized (mServerLock) {
            mShutdown = true;
            unregisterBipServer();
            if (mServerSockets != null) {
                mServerSockets.shutdown(false);
                mServerSockets = null;
            }
        }
        return true;
    }

    private boolean isShutdown() {
        synchronized (mServerLock) {
            return mShutdown;
        }
    }

    private int getL2capPsm() {
        synchronized (mServerLock) {
            return (mServerLock != null ? mServerSockets.getL2capPsm() : 0);
        }
    }

    /**
     * Store an image with the service and gets the image handle it's associated with.
     */
    public String storeImage(Image image) {
        debug("storeImage(image='" + image + "')");
        if (image == null || image.getImage() == null) return null;
        return mStorage.storeImage(new CoverArt(image));
    }

    /**
     * Get the image stored at the given image handle, if it exists
     */
    public CoverArt getImage(String imageHandle) {
        debug("getImage(" + imageHandle + ")");
        return mStorage.getImage(imageHandle);
    }

    /**
     * Add a BIP L2CAP PSM to the AVRCP Target SDP Record
     */
    private void registerBipServer(int psm) {
        debug("Add our PSM (" + psm + ") to the AVRCP Target SDP record");
        mNativeInterface.registerBipServer(psm);
        return;
    }

    /**
     * Remove any BIP L2CAP PSM from the AVRCP Target SDP Record
     */
    private void unregisterBipServer() {
        debug("Remove the PSM remove the AVRCP Target SDP record");
        mNativeInterface.unregisterBipServer();
        return;
    }

    /**
     * Connect a device with the server
     *
     * Since the server cannot explicitly make clients connect, this function is internal only and
     * provides a place for us to do required book keeping when we've decided to accept a client
     */
    private boolean connect(BluetoothDevice device, BluetoothSocket socket) {
        debug("Connect '" + device + "'");
        synchronized (mClientsLock) {

            // Only allow one client at all
            if (mClients.size() >= 1) return false;

            // Only allow one client per device
            if (mClients.containsKey(device)) return false;

            // Create a BIP OBEX Server session for the client and connect
            AvrcpBipObexServer s = new AvrcpBipObexServer(this, new AvrcpBipObexServer.Callback() {
                public void onConnected() {
                    mNativeInterface.setBipClientStatus(device.getAddress(), true);
                }

                public void onDisconnected() {
                    mNativeInterface.setBipClientStatus(device.getAddress(), false);
                }

                public void onClose() {
                    disconnect(device);
                }
            });
            BluetoothObexTransport transport = new BluetoothObexTransport(socket,
                    MAX_TRANSMIT_PACKET_SIZE,
                    BluetoothObexTransport.PACKET_SIZE_UNSPECIFIED);
            transport.setConnectionForCoverArt(true);
            try {
                ServerSession session = new ServerSession(transport, s, null);
                mClients.put(device, session);
                return true;
            } catch (IOException e) {
                error(e.toString());
                return false;
            }
        }
    }

    /**
     * Explicitly disconnect a device from our BIP server if its connected.
     */
    public void disconnect(BluetoothDevice device) {
        debug("disconnect '" + device + "'");
        // Closing the server session closes the underlying transport, which closes the underlying
        // socket as well. No need to maintain and close anything else.
        synchronized (mClientsLock) {
            if (mClients.containsKey(device)) {
                mNativeInterface.setBipClientStatus(device.getAddress(), false);
                ServerSession session = mClients.get(device);
                mClients.remove(device);
                session.close();
            }
        }
    }

    /**
     * A Socket Acceptor to handle incoming connections to our BIP Server.
     *
     * If we are accepting connections and the device is permitted, then this class will create a
     * session with our AvrcpBipObexServer.
     */
    private class SocketAcceptor implements IObexConnectionHandler {
        @Override
        public synchronized boolean onConnect(BluetoothDevice device, BluetoothSocket socket) {
            debug("onConnect() - device=" + device + ", socket=" + socket);
            if (isShutdown()) return false;
            return connect(device, socket);
        }

        @Override
        public synchronized void onAcceptFailed() {
            error("OnAcceptFailed()");
            if (isShutdown()) {
                error("Failed to accept incoming connection due to shutdown");
            } else {
                // restart
                stop();
                start();
            }
        }
    }

    /**
     * Dump useful debug information about this service to a string
     */
    public void dump(StringBuilder sb) {
        int psm = getL2capPsm();
        sb.append("AvrcpCoverArtService:");
        sb.append("\n\tpsm = " + (psm == 0 ? "null" : psm));
        mStorage.dump(sb);
        synchronized (mClientsLock) {
            sb.append("\n\tclients = " + Arrays.toString(mClients.keySet().toArray()));
        }
        sb.append("\n");
    }

    /**
     * Print a message to DEBUG if debug output is enabled
     */
    private void debug(String msg) {
        if (DEBUG) {
            Log.d(TAG, msg);
        }
    }

    /**
     * Print a message to ERROR
     */
    private void error(String msg) {
        Log.e(TAG, msg);
    }
}
