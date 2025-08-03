package com.example.imsmediatestingapp;

import android.content.SharedPreferences;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

/**
 * The HandshakeReceiver handles and stores information about incoming packets
 * during the
 * handshake process.
 */
public class HandshakeReceiver implements Runnable {

    private static final int MAX_UDP_DATAGRAM_LEN = 65527;
    private final String HANDSHAKE_PORT_PREF = "HANDSHAKE_PORT_OPEN";
    private final String CONFIRMATION_MESSAGE = "CONNECTED";
    private static final String LOG_PREFIX = HandshakeReceiver.class.getName();
    private boolean running = true;
    private boolean isConfirmationReceived = false;
    private boolean isHandshakeReceived = false;
    private DeviceInfo receivedDeviceInfo;
    DatagramSocket socket = null;
    SharedPreferences.Editor editor;

    public HandshakeReceiver(SharedPreferences preferences) {
        editor = preferences.edit();
    }

    public void run() {
        DeviceInfo deviceInfo = null;
        String confirmation = null;
        byte[] buffer = new byte[MAX_UDP_DATAGRAM_LEN];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

        try {
            socket = new DatagramSocket();
            socket.setReuseAddress(true);

            editor.putBoolean(HANDSHAKE_PORT_PREF, true).apply();

            while (running) {
                socket.receive(packet);
                Object dataReceived = deserializePacket(packet.getData());

                if (dataReceived instanceof DeviceInfo && !isHandshakeReceived) {
                    deviceInfo = (DeviceInfo) dataReceived;
                    if (verifyHandshakePacket(deviceInfo)) {
                        isHandshakeReceived = true;
                        receivedDeviceInfo = deviceInfo;
                        Log.d(LOG_PREFIX, "RECEIVED: Device Info");
                    }

                } else if (dataReceived instanceof String && !isConfirmationReceived) {
                    confirmation = (String) dataReceived;
                    if (verifyConfirmationPacket(confirmation)) {
                        isConfirmationReceived = true;
                        Log.d(LOG_PREFIX, "RECEIVED: Confirmation");
                        running = false;

                    }
                }

            }
        } catch (Throwable e) {
            System.out.println(e.getLocalizedMessage());
        }
    }

    /**
     * Reads the data into a ByteArrayInputStream and ObjectInputStream to determine
     * the type of the data, then casts it to the correct type and returns it
     * @param data byte array from the packet received from the DatagramSocket
     * @param <T>  either a String or DeviceInfo
     * @return string value of hte conformation string, or the DeviceInfo
     */
    @SuppressWarnings("TypeParameterUnusedInFormals")
    private <T> T deserializePacket(byte[] data) {
        DeviceInfo deviceInfo = null;
        String confirmationMessage = null;
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(data);
        try {
            ObjectInputStream objectInputStream = new ObjectInputStream(byteArrayInputStream);
            Object readObject = objectInputStream.readObject();
            if (readObject instanceof DeviceInfo) {
                deviceInfo = (DeviceInfo) readObject;
                return (T) deviceInfo;

            } else if (readObject instanceof String) {
                confirmationMessage = (String) readObject;
                return (T) confirmationMessage;
            }

        } catch (IOException | ClassNotFoundException e) {
            Log.e(LOG_PREFIX, "Exception: " + e.toString());
            Log.d(LOG_PREFIX, new String(data));
            return (T) new String(data);
        }
        return null;
    }

    public int getBoundSocket() {
        return socket.getLocalPort();
    }

    public void close() {
        Log.d("", "Closing the socket on port: " + socket.getLocalPort());
        running = false;
        socket.close();
        editor.putBoolean(HANDSHAKE_PORT_PREF, false).apply();
    }

    /**
     * Verifies that the incoming DeviceInfo has all valid port numbers.
     * @param deviceInfo device info to verify
     * @return boolean if the DeviceInfo has all the right info
     */
    private boolean verifyHandshakePacket(DeviceInfo deviceInfo) {
        if (deviceInfo.getHandshakePort() == -1 || deviceInfo.getAudioRtpPort() == -1) {
            Log.d(LOG_PREFIX,
                    "One or more of the ports sent in the handshake have not been opened.");
            return false;
        }

        return true;
    }

    private boolean verifyConfirmationPacket(String confirmation) {
        return confirmation.equals(CONFIRMATION_MESSAGE);
    }

    public boolean isConfirmationReceived() {
        return isConfirmationReceived;
    }

    public boolean isHandshakeReceived() {
        return isHandshakeReceived;
    }

    public DeviceInfo getReceivedDeviceInfo() {
        return receivedDeviceInfo;
    }
}
