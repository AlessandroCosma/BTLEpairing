package com.alessandrocosma.btlepairing;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.ComponentName;
import android.os.Handler;
import android.os.Bundle;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import java.util.Set;
import java.util.UUID;
import java.io.IOException;
import java.util.List;
import java.lang.reflect.Method;

import com.google.android.things.bluetooth.BluetoothConnectionManager;
import com.google.android.things.bluetooth.BluetoothPairingCallback;
import com.google.android.things.bluetooth.PairingParams;
import com.google.android.things.bluetooth.BluetoothProfileManager;
import com.google.android.things.bluetooth.BluetoothConfigManager;
import com.google.android.things.contrib.driver.button.Button;
import com.google.android.things.contrib.driver.rainbowhat.RainbowHat;
import com.google.android.things.pio.Gpio;


public class MainActivity extends Activity {

    private static final String TAG = MainActivity.class.getSimpleName();

    private Gpio ledG, ledR, ledB;

    /* Remote LED Service UUID */
    public static UUID REMOTE_LED_SERVICE = UUID.fromString("00001805-0000-1000-8000-00805f9b34fb");
    /* Remote LED Data Characteristic */
    public static UUID REMOTE_LED_DATA = UUID.fromString("00002a2b-0000-1000-8000-00805f9b34fb");

    private static final String ADAPTER_FRIENDLY_NAME = "PicoPiDevice";
    private static final String ANDROID_DEVICE_NAME = "S8AlessandroBLE";
    private static final int DISCOVERABLE_TIMEOUT_MS = 60000;
    private static final long SCAN_PERIOD = 10000;
    private static final int REQUEST_CODE_ENABLE_DISCOVERABLE = 100;

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothProfileManager mBluetoothProfileManager;
    private BluetoothConnectionManager mBluetoothConnectionManager;
    private BluetoothConfigManager mBluetoothConfigManager;
    private BluetoothLeService mBluetoothLeService;


    // RainbowHat Button
    private Button mScanningButton;
    //private Button mDisconnectAllButton;
    private Button mCloseButton;
    //private Button mConnetingButton;
    private Button mPairingButton;

    // Android Button on lcd
    android.widget.Button lcdButton;
    private TextView mPasswordTextView;

    private Set<BluetoothDevice> pairedDevices;

    private boolean mConnected = false;
    private boolean mScanning = false;
    //private boolean isDeviceFound = false;
    private Handler mHandler;

    private String mRemoteDeviceAddress;
    private BluetoothDevice mRemoteBluetoothDevice;
    private BluetoothGattCharacteristic mNotifyCharacteristic;

    private Handler pairingHandler;
    public static HandlerThread pairingThread;


    /**
     * Handle an intent that is broadcast by the Bluetooth adapter whenever it changes its
     * state (after calling enable(), for example).
     * Action is {@link BluetoothAdapter#ACTION_STATE_CHANGED} and extras describe the old
     * and the new states. You can use this intent to indicate that the device is ready to go.
     */
    private final BroadcastReceiver mAdapterStateChangeReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            int oldState = getPreviousAdapterState(intent);
            int newState = getCurrentAdapterState(intent);
            Log.d(TAG, "Bluetooth Adapter changing state from " + oldState + " to " + newState);
            if (newState == BluetoothAdapter.STATE_ON) {
                Log.i(TAG, "Bluetooth Adapter is ready");
                init();
            }
        }
    };

    private BroadcastReceiver bluetoothDeviceReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if( BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals( intent.getAction() ) ) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                switch (device.getBondState()) {
                    case BluetoothDevice.BOND_BONDING:
                        Log.d(TAG, "Start pairing with "+device.getName()+": "+device.getAddress());
                        break;
                    case BluetoothDevice.BOND_BONDED:
                        Log.d(TAG, "Start pairing with "+device.getName()+": "+device.getAddress());
                        break;
                    case BluetoothDevice.BOND_NONE:
                        Log.d(TAG, "cancel");
                        break;
                    case BluetoothDevice.ERROR:
                        Log.d(TAG, "error");
                    default:
                        break;
                }

            }
        }
    };


    private BluetoothPairingCallback mBluetoothPairingCallback = new BluetoothPairingCallback() {

        @Override
        public void onPairingInitiated(BluetoothDevice bluetoothDevice,
                                       PairingParams pairingParams) {
            // Handle incoming pairing request or confirmation of outgoing pairing request
            Log.i(TAG, "Handle incoming pairing request or confirmation of outgoing pairing request");

            // Creo un nuovo thread per la gestione del pairing
            pairingThread = new HandlerThread("pairingThread");
            // Avvio il thread
            pairingThread.start();
            // Creo l'handler che gestisce il pairing
            pairingHandler = new Handler(pairingThread.getLooper());
            // Avvio l'handler
            pairingHandler.post( new HandlePairingRequestRunnable(bluetoothDevice, pairingParams));
        }

        @Override
        public void onPaired(BluetoothDevice bluetoothDevice) {
            // Device pairing complete
            Log.i(TAG,"Device pairing complete");

            /* Decommentare nel caso il pairing sia "manuale" e la caratteristica sia BluetoothGattCharacteristic.PERMISSION_READ */
            //Intent gattServiceIntent = new Intent(MainActivity.this, BluetoothLeService.class);
            //bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
        }

        @Override
        public void onUnpaired(BluetoothDevice bluetoothDevice) {
            // Device unpaired
            Log.i(TAG, "Device unpaired");
        }

        @Override
        public void onPairingError(BluetoothDevice bluetoothDevice,
                                   BluetoothPairingCallback.PairingError pairingError) {
            // Something went wrong!
            Log.e(TAG,"onPairingError "+pairingError.getErrorCode());
        }
    };



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Spengo i led
        turnOffLeds();

        mPasswordTextView = (TextView) findViewById(R.id.password);

        mBluetoothProfileManager = BluetoothProfileManager.getInstance();
        mBluetoothConnectionManager = BluetoothConnectionManager.getInstance();
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mBluetoothConfigManager = BluetoothConfigManager.getInstance();
        mHandler = new Handler();

        // Lista device associati
        listBondedDevices();

        // Setto la capacità del device a IO_CAPABILITY_NONE: il device non ha capacità di I/O
        //mBluetoothConfigManager.setLeIoCapability(BluetoothConfigManager.IO_CAPABILITY_NONE);

        // Setto la capacità del device a IO_CAPABILITY_IO: il device ha un display e può accettare
        // input YES/NO
        //mBluetoothConfigManager.setLeIoCapability(BluetoothConfigManager.IO_CAPABILITY_IO);

        // Setto la capacità del device a IO_CAPABILITY_OUT: il device ha solo una display
        mBluetoothConfigManager.setLeIoCapability(BluetoothConfigManager.IO_CAPABILITY_OUT);

        // Setto la capacità del device a IO_CAPABILITY_IN: il device accetta solo input da tastiera
        //mBluetoothConfigManager.setLeIoCapability(BluetoothConfigManager.IO_CAPABILITY_IN);


        if (mBluetoothAdapter == null) {
            Log.w(TAG, "No default Bluetooth adapter. Device likely does not support bluetooth.");
            return;
        }


        // Rimuovo i dispositivi BT associati
        /*
        pairedDevices =  mBluetoothAdapter.getBondedDevices();
        for (BluetoothDevice device: pairedDevices){
            try {
                Method m = device.getClass()
                        .getMethod("removeBond", (Class[]) null);
                m.invoke(device, (Object[]) null);
            } catch (Exception e) {
                Log.e("Removing has been failed.", e.getMessage());
            }
        }
        */

        registerReceiver(mAdapterStateChangeReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));

        registerReceiver(bluetoothDeviceReceiver, new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED));

        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());

        mBluetoothConnectionManager.registerPairingCallback(mBluetoothPairingCallback);


        if (mBluetoothAdapter.isEnabled()) {
            Log.d(TAG, "Bluetooth Adapter is already enabled.");
            init();
        } else {
            Log.d(TAG, "Bluetooth adapter not enabled. Enabling.");
            mBluetoothAdapter.enable();
        }

    }

    private void listBondedDevices(){
        pairedDevices = mBluetoothAdapter.getBondedDevices();
        if (pairedDevices.size() > 0) {
            Log.d(TAG, "|Bondend Devices|");
            for (BluetoothDevice device: pairedDevices){
                Log.d(TAG, "|Device = "+device.getName()+" (address = "+device.getAddress()+") |");
            }
        }
    }


    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }
    /*
    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart()");
        //registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
    }

    @Override
    protected void onPause(){
        super.onPause();
        Log.d(TAG, "onPause()");
        //unregisterReceiver(mGattUpdateReceiver);
    }
    */

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");

        unregisterReceiver(mGattUpdateReceiver);

        try {
            mScanningButton.close();
            //mPairingButton.close();
            //mConnetingButton.close();
            mCloseButton.close();

        } catch (IOException e) {
            Log.e(TAG, "Unable to close button");
        }

        if(mServiceConnection != null)
            unbindService(mServiceConnection);

        unregisterReceiver(mAdapterStateChangeReceiver);
        unregisterReceiver(bluetoothDeviceReceiver);
        mBluetoothConnectionManager.unregisterPairingCallback(mBluetoothPairingCallback);

        mBluetoothAdapter.disable();
    }


    private int getPreviousAdapterState(Intent intent) {
        return intent.getIntExtra(BluetoothAdapter.EXTRA_PREVIOUS_STATE, -1);
    }

    private int getCurrentAdapterState(Intent intent) {
        return intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1);
    }


    public class HandlePairingRequestRunnable implements Runnable {

        BluetoothDevice bluetoothDevice;
        PairingParams pairingParams;

        public HandlePairingRequestRunnable(BluetoothDevice bluetoothDevice, PairingParams pairingParams) {
            this.bluetoothDevice = bluetoothDevice;
            this.pairingParams = pairingParams;
        }

        public void run() {
            switch (pairingParams.getPairingType()) {
                case PairingParams.PAIRING_VARIANT_DISPLAY_PIN:
                case PairingParams.PAIRING_VARIANT_DISPLAY_PASSKEY:
                    Log.d(TAG, "case 1");
                    // Display the required PIN to the user
                    Log.d(TAG, "Display Passkey - " + pairingParams.getPairingPin());
                    break;
                case PairingParams.PAIRING_VARIANT_PIN:
                case PairingParams.PAIRING_VARIANT_PIN_16_DIGITS:
                    Log.d(TAG, "case 2");
                    // Obtain PIN from the user
                    String pin = "";

                    if(mBluetoothConfigManager.getLeIoCapability() == BluetoothConfigManager.IO_CAPABILITY_NONE)
                        pin = "0000";

                    else {

                        synchronized (lcdButton) {
                            try {
                                lcdButton.wait();
                            } catch (InterruptedException e) {
                                Log.e(TAG, Thread.currentThread().getName() + " error: " + e.toString());
                            }
                        }

                        pin = mPasswordTextView.getText().toString();
                    }

                    Log.d(TAG, "pin = "+pin);
                    // Pass the result to complete pairing
                    mBluetoothConnectionManager.finishPairing(bluetoothDevice, pin);
                    break;
                case PairingParams.PAIRING_VARIANT_CONSENT:
                    Log.d(TAG, "case 3");
                    // Complete the pairing process
                    mBluetoothConnectionManager.finishPairing(bluetoothDevice);
                    break;

                case PairingParams.PAIRING_VARIANT_PASSKEY_CONFIRMATION:
                    Log.d(TAG, "case 4");
                    // Show confirmation of pairing to the user
                    // Complete the pairing process
                    mBluetoothConnectionManager.finishPairing(bluetoothDevice);
                    break;
            }
        }
    }


    /**
     * Initiate
     */
    private void init() {
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Log.e(TAG, "Bluetooth adapter not available or not enabled.");
            return;
        }

        Log.d(TAG, "Set up Bluetooth Adapter name ");
        mBluetoothAdapter.setName(ADAPTER_FRIENDLY_NAME);

        // configure button
        configureButton();
    }



    private void configureButton() {
        try {
            mScanningButton = RainbowHat.openButtonA();
            mScanningButton.setOnButtonEventListener(new Button.OnButtonEventListener() {
                @Override
                public void onButtonEvent(Button button, boolean pressed) {
                    Log.d(TAG, "button A pressed");

                    if(!mScanning) {
                        // Enable Pairing mode (discoverable)
                        enableDiscoverable();
                        scanLeDevice();
                    }

                    else
                        Log.d(TAG,"Scan already started!");
                }
            });



            mCloseButton = RainbowHat.openButtonC();
            mCloseButton.setOnButtonEventListener(new Button.OnButtonEventListener() {
                @Override
                public void onButtonEvent(Button button, boolean pressed) {
                    Log.d(TAG, "button C pressed");
                    //Close the application
                    MainActivity.this.finish();
                }
            });


            lcdButton = (android.widget.Button) findViewById(R.id.lcdButton);
            lcdButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    synchronized (lcdButton) { lcdButton.notify(); }
                }
            });


        }
        catch (IOException e){
            Log.e(TAG, "Unable to open button");
        }
    }

    private void scanLeDevice() {
        Log.d(TAG, "scanLeDevice()");
        // Stops scanning after a pre-defined scan period.
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                mScanning = false;
                mBluetoothAdapter.stopLeScan(mLeScanCallback);
            }
        }, SCAN_PERIOD);

        mScanning = true;
        mBluetoothAdapter.startLeScan(mLeScanCallback);
    }

    private BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {

                @Override
                public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
                    Boolean isDeviceFound = false;
                    if(device != null){
                        final String deviceName = device.getName();
                        if (deviceName != null && deviceName.length() > 0) {
                            Log.d(TAG, "Found device: "+deviceName);
                            if(deviceName.replaceAll("\\s+", "").equals(ANDROID_DEVICE_NAME)){
                                isDeviceFound = true;
                                mRemoteBluetoothDevice = device;
                                mRemoteDeviceAddress = device.getAddress();
                            }
                        }
                    }

                    /*
                    if(isDeviceFound && !mConnected) {
                        mRemoteBluetoothDevice.createBond();
                        //startPairing(mRemoteBluetoothDevice);
                        mConnected = true;
                    }
                    */

                    /*
                    if(isDeviceFound && !mConnected){
                        Intent gattServiceIntent = new Intent(MainActivity.this, BluetoothLeService.class);
                        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
                        mConnected = true;
                    }
                    */


                    if(isDeviceFound && !mConnected) {
                        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
                        boolean b = pairedDevices.contains(mRemoteBluetoothDevice);
                        Log.e(TAG, "TEST lista dispositivi: "+b);
                        if(b){
                            Intent gattServiceIntent = new Intent(MainActivity.this, BluetoothLeService.class);
                            bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
                        }
                        else{
                            //mRemoteBluetoothDevice.createBond();
                            mBluetoothConnectionManager.initiatePairing(mRemoteBluetoothDevice);
                        }
                    }



                }
            };

    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            Log.d(TAG, "onServiceConnected()");
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }

            Boolean result = mBluetoothLeService.connect(mRemoteDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
            mConnected = true;
            if(mScanning) {
                mScanning = false;
                mBluetoothAdapter.stopLeScan(mLeScanCallback);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Log.d(TAG, "onServiceDisconnected()");
            mBluetoothLeService = null;
        }

        /*
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(mRemoteDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
        */
    };
    /*
    private void startPairing(BluetoothDevice remoteDevice) {
        //Log.d(TAG, "Start pairing with "+remoteDevice.getName()+": "+remoteDevice.getAddress());
        mBluetoothConnectionManager.initiatePairing(remoteDevice);
    }
    */

    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "onReceive()");

            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                //Log.e(TAG, "mConnected = true");

            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                //Log.e(TAG, "mConnected = false");


            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the user interface.
                List<BluetoothGattService> services = mBluetoothLeService.getSupportedGattServices();
                if(services != null){
                    for (BluetoothGattService gattService : services) {
                        if(gattService.getUuid().equals(REMOTE_LED_SERVICE)){
                            final BluetoothGattCharacteristic characteristic = gattService.getCharacteristic(REMOTE_LED_DATA);
                            if (characteristic != null) {
                                final int charaProp = characteristic.getProperties();
                                if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
                                    // If there is an active notification on a characteristic, clear
                                    // it first so it doesn't update the data field on the user interface.
                                    if (mNotifyCharacteristic != null) {
                                        mBluetoothLeService.setCharacteristicNotification(
                                                mNotifyCharacteristic, false);
                                        mNotifyCharacteristic = null;
                                    }
                                    mBluetoothLeService.readCharacteristic(characteristic);
                                }
                                if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                                    mNotifyCharacteristic = characteristic;
                                    mBluetoothLeService.setCharacteristicNotification(
                                            characteristic, true);
                                }
                            }
                        }
                    }
                }
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                String data = intent.getStringExtra(BluetoothLeService.EXTRA_DATA);
                if (data == null)
                    return;
                if(data.contains("false")){
                    setLedValue(false);
                }else if(data.contains("true")){
                    setLedValue(true);
                }
            }
        }
    };


    /**
     * Update the output value of the LED.
     */
    private void setLedValue(boolean value) {

        try {
            ledG = RainbowHat.openLedGreen();
            ledG.setValue(value);
            ledG.close();
        }
        catch (IOException e){
            Log.e(TAG, "Error updating value of led "+String.valueOf(ledG));
        }
    }

    private void turnOffLeds() {

        try {
            ledG = RainbowHat.openLedGreen();
            ledG.setValue(false);
            ledG.close();

            ledR = RainbowHat.openLedRed();
            ledR.setValue(false);
            ledR.close();

            ledB = RainbowHat.openLedBlue();
            ledB.setValue(false);
            ledB.close();

        } catch (IOException e) {
            Log.e(TAG, "Unable to turn off leds'light: " + e);

        }
    }

    /*
     * Enable the current {@link BluetoothAdapter} to be discovered (available for pairing) for
     * the next {@link #DISCOVERABLE_TIMEOUT_MS} ms.
     * */
    private void enableDiscoverable() {
        Log.d(TAG, "Registering for discovery.");
        Intent discoverableIntent =
                new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION,
                DISCOVERABLE_TIMEOUT_MS);
        startActivityForResult(discoverableIntent, REQUEST_CODE_ENABLE_DISCOVERABLE);
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_ENABLE_DISCOVERABLE) {
            Log.d(TAG, "Enable discoverable returned with result " + resultCode);

            // ResultCode, as described in BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE, is either
            // RESULT_CANCELED or the number of milliseconds that the device will stay in
            // discoverable mode. In a regular Android device, the user will see a popup requesting
            // authorization, and if they cancel, RESULT_CANCELED is returned. In Android Things,
            // on the other hand, the authorization for pairing is always given without user
            // interference, so RESULT_CANCELED should never be returned.
            if (resultCode == RESULT_CANCELED) {
                Log.e(TAG, "Enable discoverable has been cancelled by the user. " +
                        "This should never happen in an Android Things device.");
                return;
            }
            Log.i(TAG, "Bluetooth adapter successfully set to discoverable mode. " +
                    "Any source can find it with the name " + ADAPTER_FRIENDLY_NAME +
                    " and pair for the next " + DISCOVERABLE_TIMEOUT_MS + " ms. " +
                    "Try looking for it on your phone, for example.");

            // There is nothing else required here.
            // Most relevant Bluetooth events, like connection/disconnection, will
            // generate corresponding broadcast intents or profile proxy events that you can
            // listen to and react appropriately.


        }
    }


    /*
    private void disconnectConnectedDevices() {
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            return;
        }

        for (BluetoothDevice device: mA2DPSinkProxy.getConnectedDevices()) {
            Log.i(TAG, "Disconnecting device " + device);
            A2dpSinkHelper.disconnect(mA2DPSinkProxy, device);
        }
    }
    */


}
