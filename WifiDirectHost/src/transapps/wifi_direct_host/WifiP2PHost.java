package transapps.wifi_direct_host;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.wifi.p2p.*;
import android.net.wifi.p2p.WifiP2pManager.*;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.*;
import android.widget.AdapterView.OnItemClickListener;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class WifiP2PHost extends Activity {

    private static final String TAG = "WiFiDirectActivity";

    private ListView listView;
    private ArrayAdapter aa;
//    private TextView tv;
    private Button buttonDiscover;

    IntentFilter peerfilter;
    IntentFilter connectionfilter;
    IntentFilter p2pEnabled;

    private Handler handler = new Handler();

    /**
     * Listing 16-18: Initializing Wi-Fi Direct
     */
    private WifiP2pManager wifiP2pManager;
    private Channel wifiDirectChannel;

    private void initializeWiFiDirect() {
        wifiP2pManager =
                (WifiP2pManager)getSystemService(Context.WIFI_P2P_SERVICE);

        wifiDirectChannel = wifiP2pManager.initialize(this, getMainLooper(),
                new ChannelListener() {
                    public void onChannelDisconnected() {
                        initializeWiFiDirect();
                    }
                }
        );
    }

    /**
     * Listing 16-19: Creating a WiFi P2P Manager Action Listener
     */
    private ActionListener actionListener = new ActionListener() {
        public void onFailure(int reason) {
            String errorMessage = "WiFi Direct Failed: ";
            switch (reason) {
                case WifiP2pManager.BUSY :
                    errorMessage += "Framework busy."; break;
                case WifiP2pManager.ERROR :
                    errorMessage += "Internal error."; break;
                case WifiP2pManager.P2P_UNSUPPORTED :
                    errorMessage += "Unsupported."; break;
                default:
                    errorMessage += "Unknown error."; break;
            }
            Log.d(TAG, errorMessage);
        }

        public void onSuccess() {
            // Success!
            // Return values will be returned using a Broadcast Intent
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

//        tv = (TextView)findViewById(R.id.textView);

        listView = (ListView)findViewById(R.id.listView);
        aa = new ArrayAdapter<WifiP2pDevice>(this, android.R.layout.simple_list_item_1, deviceList);
        listView.setAdapter(aa);

        initializeWiFiDirect();

        peerfilter = new IntentFilter(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        connectionfilter = new IntentFilter(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        p2pEnabled = new IntentFilter(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);

        buttonDiscover = (Button)findViewById(R.id.buttonDiscover);
        buttonDiscover.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                discoverPeers();
            }
        });

        Button buttonEnable = (Button)findViewById(R.id.buttonEnable);
        buttonEnable.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                /**
                 * Listing 16-20: Enabling Wi-Fi Direct on a device
                 */
                Intent intent = new Intent(
                        android.provider.Settings.ACTION_WIRELESS_SETTINGS);

                startActivity(intent);
            }
        });

        listView.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> arg0, View arg1, int index,
                                    long arg3) {
                connectTo(deviceList.get(index));
            }
        });
    }

    /**
     * Listing 16-21: Receiving a Wi-Fi Direct status change
     */
    BroadcastReceiver p2pStatusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int state = intent.getIntExtra(
                    WifiP2pManager.EXTRA_WIFI_STATE,
                    WifiP2pManager.WIFI_P2P_STATE_DISABLED);

            switch (state) {
                case (WifiP2pManager.WIFI_P2P_STATE_ENABLED):
                    buttonDiscover.setEnabled(true);
                    break;
                default:
                    buttonDiscover.setEnabled(false);
            }
        }
    };

    /**
     * Listing 16-22: Discovering Wi-Fi Direct peers
     */
    private void discoverPeers() {
        wifiP2pManager.discoverPeers(wifiDirectChannel, actionListener);
    }

    BroadcastReceiver peerDiscoveryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            wifiP2pManager.requestPeers(wifiDirectChannel,
                    new PeerListListener() {
                        public void onPeersAvailable(WifiP2pDeviceList peers) {
                            deviceList.clear();
                            deviceList.addAll(peers.getDeviceList());
                            aa.notifyDataSetChanged();
                        }
                    });
        }
    };

    /**
     * Listing 16-23: Requesting a connection to a Wi-Fi Direct peer
     */
    private void connectTo(WifiP2pDevice device) {
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = device.deviceAddress;

        wifiP2pManager.connect(wifiDirectChannel, config, actionListener);
    }

    /**
     * Listing 16-24: Connecting to a Wi-Fi Direct peer
     */
    BroadcastReceiver connectionChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            // Extract the NetworkInfo
            String extraKey = WifiP2pManager.EXTRA_NETWORK_INFO;
            NetworkInfo networkInfo =
                    (NetworkInfo)intent.getParcelableExtra(extraKey);

            // Check if we're connected
            if (networkInfo.isConnected()) {
                wifiP2pManager.requestConnectionInfo(wifiDirectChannel,
                        new ConnectionInfoListener() {
                            public void onConnectionInfoAvailable(WifiP2pInfo info) {
                                // If the connection is established
                                if (info.groupFormed) {
                                    // If we're the server
                                    if (info.isGroupOwner) {
                                        // TODO Initiate server socket.
                                        initiateServerSocket();
                                    }
                                    // If we're the client
                                    else if (info.groupFormed) {
                                        // TODO Initiate client socket.
                                        initiateClientSocket(info.groupOwnerAddress.toString());
                                    }
                                }
                            }
                        });
            } else {
                Log.d(TAG, "Wi-Fi Direct Disconnected");
            }
        }
    };

    private void initiateServerSocket() {
        ServerSocket serverSocket;
        try {
            /**
             * Listing 16-25: Creating a Server Socket
             */
            serverSocket = new ServerSocket(8666);
            Socket serverClient = serverSocket.accept();

            // TODO Start Sending Messages
        } catch (IOException e) {
            Log.e(TAG, "I/O Exception", e);
        }
    }

    private void initiateClientSocket(String hostAddress) {
        /**
         * Listing 16-26: Creating a client Socket
         */
        int timeout = 10000;
        int port = 8666;

        InetSocketAddress socketAddress
                = new InetSocketAddress(hostAddress, port);

        try {
            Socket socket = new Socket();
            socket.bind(null);
            socket.connect(socketAddress, timeout);
        } catch (IOException e) {
            Log.e(TAG, "IO Exception.", e);
        }

        // TODO Start Receiving Messages
    }

    @Override
    protected void onPause() {
        unregisterReceiver(peerDiscoveryReceiver);
        unregisterReceiver(connectionChangedReceiver);
        unregisterReceiver(p2pStatusReceiver);
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(peerDiscoveryReceiver, peerfilter);
        registerReceiver(connectionChangedReceiver, connectionfilter);
        registerReceiver(p2pStatusReceiver, p2pEnabled);
    }

    private List<WifiP2pDevice> deviceList = new ArrayList<WifiP2pDevice>();
}