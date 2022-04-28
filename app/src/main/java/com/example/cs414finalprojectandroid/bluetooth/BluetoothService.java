/*
 * Adapted from: https://github.com/googlearchive/android-BluetoothChat
 */

package com.example.cs414finalprojectandroid.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.example.cs414finalprojectandroid.Constants;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

public class BluetoothService {
    // Debugging
    private static final String TAG = "GBGBluetoothService";

    // Name for the SDP record when creating server socket
    private static final String SDP_RECORD_ID = "GBGBluetooth";

    private static final UUID BLUETOOTH_CLASSIC_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private final Handler handler;
    private final BluetoothAdapter bluetoothAdapter;

    private AcceptThread acceptThread;
    private ConnectThread connectThread;
    private ConnectedThread connectedThread;

    private int state = STATE_NONE;

    public static final int STATE_NONE = 0;       // we're doing nothing
    public static final int STATE_LISTEN = 1;     // now listening for incoming connections
    public static final int STATE_CONNECTING = 2; // now initiating an outgoing connection
    public static final int STATE_CONNECTED = 3;  // now connected to a remote device

    /**
     * Constructor. Prepares a new BluetoothChat session.
     *
     * @param handler A Handler to send messages back to the UI Activity
     */
    public BluetoothService(Handler handler) {
        this.handler = handler;
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    /**
     * Update UI title according to the current state of the chat connection
     */
    private synchronized void updateUserInterfaceTitle() {
        int state = getState();
        Log.d(TAG, "updateUserInterfaceTitle() " + state);

        // Give the new state to the Handler so the UI Activity can update
        handler.obtainMessage(Constants.MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
    }

    /**
     * Return the current connection state.
     */
    public synchronized int getState() {
        return state;
    }

    /**
     * Set the current connection state.
     */
    public synchronized void setState(int newState) {
        state = newState;
    }

    /**
     * Start the chat service. Specifically start AcceptThread to begin a
     * session in listening (server) mode. Called by the Activity onResume()
     */
    public synchronized void start() {
        Log.d(TAG, "start");

        // Cancel any thread attempting to make a connection
        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }

        // Cancel any thread currently running a connection
        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }

        // Start the thread to listen on a BluetoothServerSocket
        if (acceptThread == null) {
            acceptThread = new AcceptThread(true);
            acceptThread.start();
        }

        // Update UI title
        updateUserInterfaceTitle();
    }

    /**
     * Start the ConnectThread to initiate a connection to a remote device.
     *
     * @param device The BluetoothDevice to connect
     * @param secure Socket Security type - Secure (true) , Insecure (false)
     */
    public synchronized void connect(BluetoothDevice device, boolean secure) {
        Log.d(TAG, "connect to: " + device);

        // Cancel any thread attempting to make a connection
        if (getState() == STATE_CONNECTING) {
            if (connectThread != null) {
                connectThread.cancel();
                connectThread = null;
            }
        }

        // Cancel any thread currently running a connection
        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }

        // Start the thread to connect with the given device
        connectThread = new ConnectThread(device, secure);
        connectThread.start();

        // Update UI title
        updateUserInterfaceTitle();
    }

    /**
     * Start the ConnectedThread to begin managing a Bluetooth connection
     *
     * @param socket The BluetoothSocket on which the connection was made
     * @param device The BluetoothDevice that has been connected
     */
    public synchronized void connect(
            BluetoothSocket socket,
            BluetoothDevice device,
            final String socketType) {
        Log.d(TAG, "connected, Socket Type:" + socketType);

        // Cancel the thread that completed the connection
        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }

        // Cancel any thread currently running a connection
        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }

        // Cancel the accept thread because we only want to connect to one device
        if (acceptThread != null) {
            acceptThread.cancel();
            acceptThread = null;
        }

        // Start the thread to manage the connection and perform transmissions
        connectedThread = new ConnectedThread(socket, socketType);
        connectedThread.start();

        // Send the name of the connected device back to the UI Activity
        Message deviceNameMsg = handler.obtainMessage(Constants.MESSAGE_DEVICE_NAME);
        Bundle deviceNameBundle = new Bundle();
        deviceNameBundle.putString(Constants.DEVICE_NAME, device.getName());
        deviceNameMsg.setData(deviceNameBundle);
        handler.sendMessage(deviceNameMsg);

        // Send a failure message back to the Activity
        Message toastMsg = handler.obtainMessage(Constants.MESSAGE_TOAST);
        Bundle toastBundle = new Bundle();
        toastBundle.putString(Constants.TOAST, "Connected!");
        toastMsg.setData(toastBundle);
        handler.sendMessage(toastMsg);

        // Update UI title
        updateUserInterfaceTitle();
    }

    /**
     * Stop all threads
     */
    public synchronized void stop() {
        Log.d(TAG, "stop");

        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }

        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }

        if (acceptThread != null) {
            acceptThread.cancel();
            acceptThread = null;
        }

        setState(STATE_NONE);

        // Update UI title
        updateUserInterfaceTitle();
    }

    /**
     * Write to the ConnectedThread in an un-synchronized manner
     *
     * @param out The bytes to write
     * @see ConnectedThread#write(byte[])
     */
    public void write(byte[] out) {
        // Create temporary object
        ConnectedThread r;

        // Synchronize a copy of the ConnectedThread
        synchronized (this) {
            if (getState() != STATE_CONNECTED) return;
            r = connectedThread;
        }

        // Perform the write un-synchronized
        r.write(out);
    }

    /**
     * Indicate that the connection attempt failed and notify the UI Activity.
     */
    private void connectionFailed() {
        // Send a failure message back to the Activity
        Bundle bundle = new Bundle();
        bundle.putString(Constants.TOAST, "Unable to connect device");

        Message msg = handler.obtainMessage(Constants.MESSAGE_TOAST);
        msg.setData(bundle);

        handler.sendMessage(msg);

        setState(STATE_NONE);

        // Update UI title
        updateUserInterfaceTitle();

        // Start the service over to restart listening mode
        start();
    }


    private void connectionMade() {
        // Send a failure message back to the Activity
        Bundle bundle = new Bundle();
        bundle.putString(Constants.TOAST, "Connected!");

        Message msg = handler.obtainMessage(Constants.MESSAGE_TOAST);
        msg.setData(bundle);

        handler.sendMessage(msg);
    }

    /**
     * Indicate that the connection was lost and notify the UI Activity.
     */
    private void connectionLost() {
        // Send a failure message back to the Activity
        Bundle bundle = new Bundle();
        bundle.putString(Constants.TOAST, "Device connection was lost");

        Message msg = handler.obtainMessage(Constants.MESSAGE_TOAST);
        msg.setData(bundle);

        handler.sendMessage(msg);

        setState(STATE_NONE);

        // Update UI title
        updateUserInterfaceTitle();

        // Start the service over to restart listening mode
        start();
    }

    /**
     * This thread runs while listening for incoming connections. It behaves
     * like a server-side client. It runs until a connection is accepted
     * (or until cancelled).
     */
    private class AcceptThread extends Thread {
        // The local server socket
        private final String socketType;
        private BluetoothServerSocket bluetoothServerSocket;

        public AcceptThread(boolean secure) {
            socketType = secure ? "Secure" : "Insecure";

            // Create a new listening server socket
            try {
                // Check if there's a case to use other adapter 'listen' method
                bluetoothServerSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord(SDP_RECORD_ID, BLUETOOTH_CLASSIC_UUID);
            } catch (IOException e) {
                Log.e(TAG, "Socket Type: " + socketType + "listen() failed", e);
            }

            setState(STATE_LISTEN);
        }

        public void run() {
            Log.d(TAG, "Socket Type: " + socketType + "BEGIN mAcceptThread" + this);

            setName("AcceptThread" + socketType);

            BluetoothSocket socket;

            // Listen to the server socket if we're not connected
            while (BluetoothService.this.getState() != STATE_CONNECTED) {
                try {
                    // This is a blocking call and will only return on a
                    // successful connection or an exception
                    socket = bluetoothServerSocket.accept();
                } catch (IOException e) {
                    Log.e(TAG, "Socket Type: " + socketType + "accept() failed", e);
                    break;
                }

                // If a connection was accepted
                if (socket != null) {
                    synchronized (BluetoothService.this) {
                        switch (BluetoothService.this.getState()) {
                            case STATE_LISTEN:
                            case STATE_CONNECTING:
                                // Situation normal. Start the connected thread.
                                connect(socket, socket.getRemoteDevice(), socketType);
                                break;
                            case STATE_NONE:
                            case STATE_CONNECTED:
                                // Either not ready or already connected. Terminate new socket.
                                try {
                                    socket.close();
                                } catch (IOException e) {
                                    Log.e(TAG, "Could not close unwanted socket", e);
                                }
                                break;
                        }
                    }
                }
            }

            Log.i(TAG, "END mAcceptThread, socket Type: " + socketType);
        }

        public void cancel() {
            Log.d(TAG, "Socket Type" + socketType + "cancel " + this);

            try {
                bluetoothServerSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Socket Type" + socketType + "close() of server failed", e);
            }
        }
    }

    /**
     * This thread runs while attempting to make an outgoing connection
     * with a device. It runs straight through; the connection either
     * succeeds or fails.
     */
    private class ConnectThread extends Thread {
        private final String socketType;

        private BluetoothSocket bluetoothSocket;
        private final BluetoothDevice bluetoothDevice;

        public ConnectThread(BluetoothDevice device, boolean secure) {
            bluetoothDevice = device;
            socketType = secure ? "Secure" : "Insecure";

            // Get a BluetoothSocket for a connection with the
            // given BluetoothDevice
            try {
                if (secure) {
                    bluetoothSocket = device.createRfcommSocketToServiceRecord(BLUETOOTH_CLASSIC_UUID);
                } else {
                    bluetoothSocket = device.createInsecureRfcommSocketToServiceRecord(BLUETOOTH_CLASSIC_UUID);
                }
            } catch (IOException e) {
                Log.e(TAG, "Socket Type: " + socketType + "create() failed", e);
            }

            setState(STATE_CONNECTING);
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectThread SocketType:" + socketType);
            setName("ConnectThread" + socketType);

            // Always cancel discovery because it will slow down a connection
            bluetoothAdapter.cancelDiscovery();

            // Make a connection to the BluetoothSocket
            try {
                // This is a blocking call and will only return on a
                // successful connection or an exception
                bluetoothSocket.connect();
            } catch (IOException e) {
                // Close the socket
                try {
                    bluetoothSocket.close();
                } catch (IOException e2) {
                    Log.e(TAG, "unable to close() " + socketType + " socket during connection failure", e2);
                }

                connectionFailed();
                return;
            }

            // Reset the ConnectThread because we're done
            synchronized (BluetoothService.this) {
                connectThread = null;
            }

            // Start the connected thread
            connect(bluetoothSocket, bluetoothDevice, socketType);
        }

        public void cancel() {
            try {
                bluetoothSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect " + socketType + " socket failed", e);
            }
        }
    }

    /**
     * This thread runs during a connection with a remote device.
     * It handles all incoming and outgoing transmissions.
     */
    private class ConnectedThread extends Thread {
        private InputStream inputStream;
        private OutputStream outputStream;

        private final BluetoothSocket bluetoothSocket;

        private byte state = 0;

        private byte packetId;
        private int packetCRC;
        private byte accelerometerX, accelerometerY;

        public ConnectedThread(BluetoothSocket socket, String socketType) {
            Log.d(TAG, "create ConnectedThread: " + socketType);
            bluetoothSocket = socket;

            // Get the BluetoothSocket input and output streams
            try {
                inputStream = socket.getInputStream();
                outputStream = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "temp sockets not created", e);
            }

            setState(STATE_CONNECTED);
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectedThread");

            // Keep listening to the InputStream while connected
            while (BluetoothService.this.getState() == STATE_CONNECTED) {
                try {
                    // Read from the InputStream
                    // if the buffer is available and has anything in it
                    if (inputStream.available() > 0) {
                        int incomingByte = inputStream.read();

                        switch (state) {
                            case 0: // 1st Byte (0x1)
                                if (incomingByte == 0x1) ++state;
                                break;

                            case 1:
                                packetId = (byte) incomingByte;
                                ++state;
                                break;

                            case 2:
                                packetCRC = incomingByte << 24;
                                ++state;
                                break;

                            case 3:
                                packetCRC += incomingByte << 16;
                                ++state;
                                break;

                            case 4:
                                packetCRC += incomingByte << 8;
                                ++state;
                                break;

                            case 5:
                                packetCRC += incomingByte;
                                ++state;
                                break;

                            case 6:
                                if (incomingByte == 0x1 || incomingByte == 0x0) {
                                    // TODO: Do something with packet ACK
                                    boolean ack = incomingByte == 0x1;
                                    ++state;
                                } else {
                                    state = 0;
                                }
                                break;

                            case 7:
                                // TODO: Do something with the length of the packet
                                // int packetDataLength = incomingByte;
                                ++state;
                                break;

                            case 8:
                                accelerometerX = (byte) incomingByte;
                                ++state;
                                break;

                            case 9:
                                accelerometerY = (byte) incomingByte;
                                ++state;
                                break;

                            case 16: // 17th Byte (End of packet: 0x04)
                                // handle state based on ID
                                switch (packetId) {
                                    case (byte) ArduinoPacket.STOP_MOTORS_PACKET_ID:
                                        // TODO: Handle emergency stop message ID type
                                        break;

                                    case (byte) ArduinoPacket.SENSOR_DATA_PACKET_ID: {
                                        Log.d("EEE", "" + packetId + " " + packetCRC + " " + accelerometerX + " " + accelerometerY);

                                        // Send the obtained bytes to the UI Activity
                                        byte[] buffer = {accelerometerX, accelerometerY};
                                        handler.obtainMessage(Constants.MESSAGE_READ, buffer.length, -1, buffer).sendToTarget();
                                        break;
                                    }

                                    case (byte) ArduinoPacket.PARENTAL_CONTROL_PACKET_ID:
                                        // TODO: Handle parental override message ID type
                                        break;
                                }

                                // Go back to state 0 to look for a new packet
                                state = 0;

                                // reset packet_checksum
                                packetCRC = 0;
                                break;

                            default: // default case is to increase the state
                                state++;
                                break;
                        }
                    }
                } catch (IOException e) {
                    Log.e(TAG, "disconnected: " + e.getMessage());
                    connectionLost();
                    break;
                }
            }
        }

        /**
         * Write to the connected OutStream.
         *
         * @param buffer The bytes to write
         */
        public void write(byte[] buffer) {
            try {
                outputStream.write(buffer);

                // Share the sent message back to the UI Activity
                handler.obtainMessage(Constants.MESSAGE_WRITE, -1, -1, buffer).sendToTarget();
            } catch (IOException e) {
                Log.e(TAG, "Exception during write", e);
            }
        }

        public void cancel() {
            try {
                bluetoothSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }
    }
}
