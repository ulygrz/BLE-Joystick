/*
* App for TemiOS
* */

package com.example.ble_joystick;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
//import android.os.Build;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelUuid;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class SelectDeviceActivity extends AppCompatActivity implements AdapterView.OnItemClickListener {

    private static final int REQUEST_ENABLE_BT = 1;
    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 1;
    private static final int PERMISSION_REQUEST_FINE_LOCATION = 1;


    private BluetoothAdapter bluetoothAdapter;
    private Button findJoyStick;
    private ListView devicesListView;
    private Handler handler;
    private boolean mScanning;
    private static final long SCAN_PERIOD = 5000;
    private BluetoothLeScanner bluetoothLeScanner;
    private Set<BluetoothDevice> deviceSet;
    private AdapterDev deviceAdapter;

    public static final ParcelUuid UUID = ParcelUuid.fromString("0000FED8-0000-1000-8000-00805F9B34FB");
    public static final java.util.UUID SERVICE_UUID = java.util.UUID.fromString("00001111-0000-1000-8000-00805F9B34FB");
    public static final java.util.UUID CHAR_UUID = java.util.UUID.fromString("00002222-0000-1000-8000-00805F9B34FB");



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        handler = new Handler();
        findJoyStick = (Button) findViewById(R.id.findjoystick);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            // Android M Permission check 
            if (ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED){
                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("This app needs location access");
                builder.setMessage("Please grant location access so this app can detect beacons.");
                builder.setPositiveButton(android.R.string.ok, null);
                builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_COARSE_LOCATION);
                        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_REQUEST_FINE_LOCATION);

                    }
                });
                builder.show();
            }
        }

        if(!hasRequiredPermissions()){
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH}, PackageManager.PERMISSION_GRANTED);
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_ADMIN}, PackageManager.PERMISSION_GRANTED);
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PackageManager.PERMISSION_GRANTED);
        }

        devicesListView = findViewById(R.id.devices_list);
        devicesListView.setOnItemClickListener(this);
        deviceSet = new HashSet<>();
        deviceAdapter = new AdapterDev();
        devicesListView.setAdapter(deviceAdapter);

        findJoyStick.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view){
                Log.d("SelectDeviceActivity", "Joystick will be searched");


                final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
                bluetoothAdapter = bluetoothManager.getAdapter();


                if(!bluetoothAdapter.isEnabled() || bluetoothAdapter == null){
                    Log.d("SelectDeviceActivity", "BLE Adapter disabled");
                    Intent enableIntent = new Intent(bluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
                }else{
                    updateUI();
                    Log.d("SelectDeviceActivity", "BLE Adapter enabled");
                    bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
                    scanLeDevice(true);

                }
                deviceAdapter.notifyDataSetChanged();
                //checkBleAdapter();
            }
        });

    }

    @Override
    protected void onStart() {
        super.onStart();
        deviceSet.clear();

    }

    private boolean hasRequiredPermissions(){
        boolean hasBluetoothPermission = hasPermission(Manifest.permission.BLUETOOTH);
        boolean hasBluetoothAdminPermission = hasPermission(Manifest.permission.BLUETOOTH_ADMIN);
        boolean hasLocationPermission = hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION);
        boolean hasFineLocationPermission = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION);

        Log.d("Permission", "BT Permission: "+hasBluetoothPermission+","+hasBluetoothAdminPermission+","+hasLocationPermission+","+hasFineLocationPermission);

        return hasBluetoothPermission && hasBluetoothAdminPermission && hasLocationPermission;
    }
    private boolean hasPermission(String permission){
        boolean permissionState = ActivityCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED){
            Log.d("Permission", "trying to get Permissions - "+permission + " "+permissionState);
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},1);
        }
        return permissionState;
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_COARSE_LOCATION: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d("Permission", "coarse location permission granted");
                } else {
                    final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Functionality limited");
                    builder.setMessage("Since location access has not been granted, this app will not be able to discover beacons when in the background.");
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialog) {
                        }
                    });
                    builder.show();
                }
                return;
            }
        }
    }

    private void updateUI() {
        findJoyStick.setEnabled(true);
    }

    private void checkBleAdapter() {

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Log.d("checkBleAdapter", "BLE Adapter disabled");
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }else{
            bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
            Log.d("checkBleAdapter", "BLE Adapter enabled");
            scanLeDevice(true);
        }
    }

    private void scanLeDevice(final boolean enable) {
        ScanFilter.Builder filter = new ScanFilter.Builder();
        filter.setServiceUuid(UUID);
        List <ScanFilter> filters = new ArrayList<>();
        filters.add(filter.build());
        if (enable) {

            //mScanning = true;
            bluetoothLeScanner.startScan(filters, new ScanSettings.Builder().build(), leScanCallback);
            Log.d("scanLeDevice", "Scan started");

        } else {
            //mScanning = false;
            bluetoothLeScanner.stopScan(leScanCallback);
            Log.d("scanLeDevice", "Scan stoped because false");
        }
    }

    private final ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, final ScanResult result){
            super.onScanResult(callbackType, result);
            handler.post(new Runnable() {
                @Override
                public void run() {
                    Log.d("onScanResult", "ScanCallback running");

                    BluetoothDevice device = result.getDevice();
                    deviceSet.add(device);
                    deviceAdapter.notifyDataSetChanged();
                    Log.d("onScanResult", "Scancallback - Device: "+device.toString());
                }
            });
        }

        @Override
        public void onScanFailed(int errorCode){
            super.onScanFailed(errorCode);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results){
            super.onBatchScanResults(results);
        }
    };

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        Log.d("onItemClick", "Device selected");
        BluetoothDevice device = deviceSet.toArray(new BluetoothDevice[0])[position];
        Intent intent = new Intent(this, TemiMovement_BLE_Client.class);
        intent.putExtra("device", device);
        bluetoothLeScanner.stopScan(leScanCallback);
        startActivity(intent);   //move to the new ClickListener for the devices list


        //bluetoothLeScanner.stopScan(leScanCallback);
    }




    private class AdapterDev extends ArrayAdapter<BluetoothDevice> {                                                     //Class to feed all the devices names to the list

        public AdapterDev() {
            super(SelectDeviceActivity.this, android.R.layout.simple_list_item_1);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent){                                          // Create new views (invoked by the layout manager)
            Log.d("AdapterDev", "AdapterDev started");
            TextView v = (TextView) getLayoutInflater().inflate(android.R.layout.simple_list_item_1, null);        // Create a new view
            BluetoothDevice device = deviceSet.toArray(new BluetoothDevice[0])[position];
            String name = device.getName();
            Log.d("AdapterDev", "AdapterDev Device Name: "+name);
            if (name == null || name.isEmpty()){
                name = device.getAddress();
            }
            v.setText(name);
            return v;
        }

        @Override
        public int getCount() { return deviceSet.size(); }

    }
}
