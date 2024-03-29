package com.alessandrocosma.btlepairingserver;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.util.Log;

import java.util.UUID;


public class RemoteLedProfile {

    /* Remote LED Service UUID */
    public static UUID REMOTE_LED_SERVICE = UUID.fromString ("00001805-0000-1000-8000-00805f9b34fb");
    /* Remote LED Data Characteristic */
    public static UUID REMOTE_LED_DATA = UUID.fromString("00002a2b-0000-1000-8000-00805f9b34fb");

    private final static String TAG = RemoteLedProfile.class.getSimpleName();


    /**
     * Return a configured {@link BluetoothGattService} instance for the Remote LED Service.
     */
    public static BluetoothGattService createRemoteLedService() {
        Log.d(TAG, "createRemoteLedService()");
        BluetoothGattService service = new BluetoothGattService(REMOTE_LED_SERVICE,
                BluetoothGattService.SERVICE_TYPE_PRIMARY);

        BluetoothGattCharacteristic ledData = new BluetoothGattCharacteristic(REMOTE_LED_DATA,
                //Read-only characteristic, supports notifications
                BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED);

        service.addCharacteristic(ledData);

        return service;
    }
}
