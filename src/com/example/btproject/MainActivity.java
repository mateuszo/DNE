package com.example.btproject;



import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;






import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;

public class MainActivity extends Activity {
	
	//variables
	
    // Intent request codes
    private static final int REQUEST_ENABLE_DISCOVERABILITY = 1;
    private static final int REQUEST_ENABLE_BT = 2;
    public static String EXTRA_DEVICE_ADDRESS = "device_address";
    // Name for the SDP record when creating server socket
    private static final String NAME = "DNE";

    // Unique UUID for this application
    private static final UUID MY_UUID = UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66");
	protected static final int SUCCESS_CONNECT = 0;
	protected static final int MESSAGE_READ = 1;
	protected static final int NEIGHBORS_LIST = 2;
    
    
    IntentFilter filter;
    
	Button discoverBtn;
	Button enableDiscoverBtn;
	Button serverBtn;
	Button sendBtn;
	ListView listView;
	ListView chatList;
	ProgressBar progressBar;
	BtInterface btInt;
	EditText messageTxt;
	
	ArrayAdapter<String> devicesListAdapter;
	ArrayAdapter<String> chatListAdapter;
	ArrayList<String> devicesList;
	Set<BluetoothDevice> pairedDevices;
	BluetoothAdapter btAdapter;
	


	// message handler do komunikacji z w¹tkami
	Handler mHandler = new Handler(){
		@Override
		public void handleMessage(Message msg) {
			super.handleMessage(msg);
			switch(msg.what){
			case SUCCESS_CONNECT:
				Toast.makeText(getBaseContext(), "connected", 0).show();
				break;
			case MESSAGE_READ: //odbiór wiadomoœci
				byte[] readBuf = (byte[])msg.obj;
				String input = new String(readBuf);
				//TODO rozpoznawanie typu wiadomoœci i podejmowanie odpowiednich akcji
                // construct a string from the valid bytes in the buffer
                String readMessage = new String(readBuf, 0, msg.arg1);
                chatListAdapter.add(">>  " + readMessage);

				Toast.makeText(getBaseContext(), input, 0).show();
				break;
			}
		}
	};
	
	
	
	
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);		
		
		init();  //initialization
		  
	}

	
    @Override
    public void onStart() {
        super.onStart();  
    }
    
    
    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Make sure we're not doing discovery anymore
        if (btAdapter != null) {
            btAdapter.cancelDiscovery();
        }

        try {
			// Unregister broadcast listeners
			this.unregisterReceiver(mReceiver);
		} catch (Exception e) {
			//
			e.printStackTrace();
		}
    }

    @Override
    public synchronized void onPause() {
        super.onPause();
    }

    @Override
    public void onStop() {
        super.onStop();
    }
    
    
    //inicjalizacja zmiennych
	private void init() {
		btAdapter = BluetoothAdapter.getDefaultAdapter();
		// If the adapter is null, then Bluetooth is not supported
        if (btAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        
  
        // If BT is not on, request that it be enabled.
        // setupChat() will then be called during onActivityResult
        if (!btAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        // Otherwise, setup the chat session
        }
        
        
		btInt = new BtInterface(this, mHandler); //interfejs bluetooth
		
		//elementy layoutu
		discoverBtn = (Button)findViewById(R.id.btnDiscover);
		enableDiscoverBtn = (Button)findViewById(R.id.btnEnableDiscover);
		serverBtn = (Button)findViewById(R.id.btnServer);
		sendBtn = (Button)findViewById(R.id.sendBtn);
		listView = (ListView)findViewById(R.id.listView1);
		chatList =(ListView)findViewById(R.id.chatList);
        devicesList = new ArrayList<String>();
        listView.setAdapter(btInt.devicesAdapter); //pobranie listy s¹siadów
        chatListAdapter = new ArrayAdapter<String>(getApplicationContext(), android.R.layout.simple_list_item_1);
        chatList.setAdapter(chatListAdapter);
        messageTxt = (EditText)findViewById(R.id.messageTxt);
        progressBar = (ProgressBar) findViewById(R.id.progressBar1);
        progressBar.setVisibility(View.INVISIBLE);
        pairedDevices = btAdapter.getBondedDevices(); //BT

        
        //set Discover button
        discoverBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
            	btInt.doDiscovery();
            }
        });
        
        //set Server button
        serverBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                btInt.startServer();
            }
        });
        
        enableDiscoverBtn.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				ensureDiscoverable();
			}
		});
        
        sendBtn.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
                TextView view = (TextView) findViewById(R.id.messageTxt);
                String message = view.getText().toString();
                sendMessage(message);
			}
		});
        
        listView.setOnItemClickListener(mDeviceClickListener);  
        
        
                
        // Register the BroadcastReceiver
        filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        registerReceiver(mReceiver, filter); // Don't forget to unregister during onDestroy
        filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        registerReceiver(mReceiver, filter);
        
	}
	
	
    // The BroadcastReceiver that listens for discovered devices
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();


            // w³¹cza i wy³¹cza progress bar w zale¿noœci od discovery
            if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                    setTitle("discovery finished");
                    progressBar.setVisibility(View.INVISIBLE);
             }
            else if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
                    setTitle("scanning...");
                    progressBar.setVisibility(View.VISIBLE);
             }
            
            }
        };
	
        
    // The on-click listener for all devices in the ListViews
	// connect to the clicked device
    private OnItemClickListener mDeviceClickListener = new OnItemClickListener() {
        public void onItemClick(AdapterView<?> av, View v, int arg2, long arg3) {
            // Cancel discovery because it's costly and we're about to connect
            btAdapter.cancelDiscovery();
            setTitle("discovery Finished");
            progressBar.setVisibility(View.INVISIBLE);
            
            // Get the device MAC address, which is the last 17 chars in the View
            String info = ((TextView) v).getText().toString();
            String address = info.substring(info.length() - 17);

            
            // Attempt to connect to the device
            btInt.connect(address);
      
        }
    };
	
//Activity result
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
        case REQUEST_ENABLE_BT:
            // When the request to enable Bluetooth returns
            if (resultCode == Activity.RESULT_OK) {
                // Bluetooth is now enabled, so set up a chat session
                
            } else {
                // User did not enable Bluetooth or an error occured
                Toast.makeText(this, "You must enable BT", Toast.LENGTH_SHORT).show();
                finish();
            }
            break;
        }
    }
    

    
    //make the device discoverable
    private void ensureDiscoverable() {
        if (btAdapter.getScanMode() !=
            BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 0);
            startActivity(discoverableIntent);
        }
    }
    
    /**
     * Sends a message.
     * @param message  A string of text to send.
     */
    private void sendMessage(String message) {
        //TODO stany aplikacji

        // Check that there's actually something to send
        if (message.length() > 0) {
            // Get the message bytes and tell the BluetoothChatService to write
            byte[] send = message.getBytes();
            btInt.connectionManager.write(send);

        }
    }
        
}
