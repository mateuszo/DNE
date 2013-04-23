package com.example.btproject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.UUID;



import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;





public class BtInterface{
	
	//messages for the handler
	protected static final int SUCCESS_CONNECT = 0;
	protected static final int MESSAGE_READ = 1;
	protected static final int NEIGHBORS_LIST = 2;
	protected static final int MESSAGE_WRITE = 3;
	
    // Name for the SDP record when creating server socket
    private static final String NAME = "DNE";

    // Unique UUID for this application
    private static final UUID MY_UUID = UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66");
	
    private final BluetoothAdapter btAdapter;
    private final Handler mHandler;
    public ArrayAdapter<String> devicesAdapter;
    public ArrayList<String> devicesList;

    AcceptThread serverThread;
    ConnectThread clientThread;
    ConnectedThread connectionManager;
    
    Context ctx;
	IntentFilter filter;

    
    
	BtInterface(Context context, Handler handler) {
		btAdapter = BluetoothAdapter.getDefaultAdapter();
		mHandler = handler;
		ctx = context;
		devicesAdapter = new ArrayAdapter<String>(ctx, android.R.layout.simple_list_item_1);
		
        filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        ctx.registerReceiver(mReceiver, filter); // Don't forget to unregister during onDestroy
        filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        ctx.registerReceiver(mReceiver, filter); // Don't forget to unregister during onDestroy

	}
			
    /**
     * Start device discover with the BluetoothAdapter
     */
    public void doDiscovery() {

        devicesAdapter.clear();

        // If we're already discovering, stop it
        if (btAdapter.isDiscovering()) {
            btAdapter.cancelDiscovery();
        }

        // Request discover from BluetoothAdapter
        btAdapter.startDiscovery();
    }
	
    
    //starts the server
    public void startServer() {
        try {
			if (serverThread == null) {
			    serverThread = new AcceptThread();
			    serverThread.start();
			    Toast.makeText(ctx, "Server started", Toast.LENGTH_SHORT).show();
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }

    // The BroadcastReceiver that listens for discovered devices
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            // When discovery finds a device
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Get the BluetoothDevice object from the Intent
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device.getBondState() == BluetoothDevice.BOND_BONDED) {
                    devicesAdapter.add(device.getName() + "\n" + device.getAddress());
                }
                else {
                    devicesAdapter.add(device.getName() + "\n" + device.getAddress());
                }
            }
            // When discovery is finished, change the Activity title
            else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
             }

            
            }
        };
        
        
        
        
        //Server thread
        
        private class AcceptThread extends Thread {
            private final BluetoothServerSocket mmServerSocket;
         
            public AcceptThread() {
                // Use a temporary object that is later assigned to mmServerSocket,
                // because mmServerSocket is final
                BluetoothServerSocket tmp = null;
                try {
                    // MY_UUID is the app's UUID string, also used by the client code
                    tmp = btAdapter.listenUsingRfcommWithServiceRecord(NAME, MY_UUID);
                } catch (IOException e) { }
                mmServerSocket = tmp;
            }
         
            public void run() {
                BluetoothSocket socket = null;
                // Keep listening until exception occurs or a socket is returned
                while (true) {
                    try {
                        socket = mmServerSocket.accept();
                    } catch (IOException e) {
                        break;
                    }
                    // If a connection was accepted
                    if (socket != null) {
                        // Do work to manage the connection (in a separate thread)
  
                        connectionManager = new ConnectedThread(socket);
                        connectionManager.start();
                        try {
							mmServerSocket.close();
						} catch (IOException e) {
							e.printStackTrace();
						}
                        break; // remove this to listen for the next connections 
                    }
                }
            }
         


			/** Will cancel the listening socket, and cause the thread to finish */
            public void cancel() {
                try {
                    mmServerSocket.close();
                } catch (IOException e) { }
            }
        }
        
        
        
        private class ConnectThread extends Thread {
            private final BluetoothSocket mmSocket;
            private final BluetoothDevice mmDevice;
         
            public ConnectThread(BluetoothDevice device) {
                // Use a temporary object that is later assigned to mmSocket,
                // because mmSocket is final
                BluetoothSocket tmp = null;
                mmDevice = device;
         
                // Get a BluetoothSocket to connect with the given BluetoothDevice
                try {
                    // MY_UUID is the app's UUID string, also used by the server code
                    tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
                } catch (IOException e) { }
                mmSocket = tmp;
            }
         
            public void run() {
                // Cancel discovery because it will slow down the connection
                btAdapter.cancelDiscovery();
         
                try {
                    // Connect the device through the socket. This will block
                    // until it succeeds or throws an exception
                    mmSocket.connect();
                } catch (IOException connectException) {
                    // Unable to connect; close the socket and get out
                    try {
                        mmSocket.close();
                    } catch (IOException closeException) { }
                    return;
                }
         
                // Do work to manage the connection (in a separate thread)
                connectionManager = new ConnectedThread(mmSocket);
                connectionManager.start();
            }
         

			/** Will cancel an in-progress connection, and close the socket */
            public void cancel() {
                try {
                    mmSocket.close();
                } catch (IOException e) { }
            }
        }
        
       
        
        /**
         * Start the ConnectThread to initiate a connection to a remote device.
         * @param device  The BluetoothDevice to connect
         */
        public synchronized void connect(String address) {

         /*   // Cancel any thread attempting to make a connection
            if (mState == STATE_CONNECTING) {
                if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}
            }
          */
        	// Get the BLuetoothDevice object
            BluetoothDevice device = btAdapter.getRemoteDevice(address);
			// Cancel any thread currently running a connection
            if (clientThread != null) {clientThread.cancel(); clientThread = null;}

            // Start the thread to connect with the given device
            clientThread = new ConnectThread(device);
            clientThread.start();
        }
        
        /**
         * This thread runs during a connection with a remote device.
         * It handles all incoming and outgoing transmissions.
         */
        class ConnectedThread extends Thread {
            private final BluetoothSocket mmSocket;
            private final InputStream mmInStream;
            private final OutputStream mmOutStream;

            public ConnectedThread(BluetoothSocket socket) {
                mmSocket = socket;
                InputStream tmpIn = null;
                OutputStream tmpOut = null;

                // Get the BluetoothSocket input and output streams
                try {
                    tmpIn = socket.getInputStream();
                    tmpOut = socket.getOutputStream();
                } catch (IOException e) {
                }

                mmInStream = tmpIn;
                mmOutStream = tmpOut;
            }

            public void run() {
                byte[] buffer = new byte[1024];
                int bytes;

                // Keep listening to the InputStream while connected
                while (true) {
                    try {
                        // Read from the InputStream
                        bytes = mmInStream.read(buffer);

                        // Send the obtained bytes to the UI Activity
                        mHandler.obtainMessage(MESSAGE_READ, bytes, -1, buffer)
                                .sendToTarget();
                    } catch (IOException e) {
                        break;
                    }
                }
            }

            /**
             * Write to the connected OutStream.
             * @param buffer  The bytes to write
             */
            public void write(byte[] buffer) {

                    try {
						mmOutStream.write(buffer);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}

                    // Share the sent message back to the UI Activity
                    mHandler.obtainMessage(MESSAGE_WRITE, -1, -1, buffer)
                            .sendToTarget();

            }

            public void cancel() {

                    try {
						mmSocket.close();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}

            }
        }
        
        
        
	
}