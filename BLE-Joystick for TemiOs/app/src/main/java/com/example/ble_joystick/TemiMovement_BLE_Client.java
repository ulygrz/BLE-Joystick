/*
 * App for TemiOS
 * */

package com.example.ble_joystick;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import com.example.ble_joystick.R;

import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import static android.bluetooth.BluetoothDevice.BOND_BONDED;
import static android.bluetooth.BluetoothDevice.BOND_BONDING;
import static android.bluetooth.BluetoothDevice.BOND_NONE;
import static android.bluetooth.BluetoothDevice.TRANSPORT_AUTO;
import static android.bluetooth.BluetoothDevice.TRANSPORT_LE;
import static android.bluetooth.BluetoothGatt.GATT_SUCCESS;
import static android.bluetooth.BluetoothProfile.STATE_CONNECTED;

import com.robotemi.sdk.Robot;


public class TemiMovement_BLE_Client extends AppCompatActivity {

    private ProgressDialog dialog;
    private BluetoothDevice device;
    private BluetoothGatt currentGatt;
    private BluetoothGattCharacteristic characteristic;
    private Button stopConnection;
    private Handler handler = new Handler();
    private Handler bleHandler = new Handler();
    //private @Nullable Runnable discoverServicesRunnable;
    private List<BluetoothGattService> services;
    private ArrayAdapter<String> servicesAdapter;
    private List<String> servicesListNames;
    public List<Float> dir;
    private volatile int state = BluetoothProfile.STATE_DISCONNECTED;
    private Handler starthandler;
    private Robot robot;
    //TODO: erase the double slash above

    private boolean connectionState = false;
    private boolean disconnecting = false;
    private boolean discoveryStarted = false;


    private @NotNull String cachedName = "";


    //public static final ParcelUuid UUID = ParcelUuid.fromString("0000FED8-0000-1000-8000-00805F9B34FB");
    public static final java.util.UUID SERVICE_UUID = java.util.UUID.fromString("00001111-0000-1000-8000-00805F9B34FB");
    public static final java.util.UUID CHAR_UUID = java.util.UUID.fromString("00002222-0000-1000-8000-00805F9B34FB");
    public static final java.util.UUID DES_UUID = java.util.UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    public String bluetoothAdapter;


    TextView xTemi, yTemi, angleTemi;
    float xSkidJoy, ySkidJoy, angleTurnBy; //Variables for SDK

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ble_client);

        robot = Robot.getInstance();

        stopConnection = findViewById(R.id.stop);
        xTemi = findViewById(R.id.xtemi);
        yTemi = findViewById(R.id.ytemi);
        angleTemi = (TextView) findViewById(R.id.angletemi);
        dialog = new ProgressDialog(this);
        dialog.setCancelable(false);
        dialog.setMessage("Loading");
        dialog.show();
        device = getIntent().getExtras().getParcelable("device");
        //bluetoothAdapter = getIntent().getExtras().getParcelable("bluetoothAdapter");

        refreshServicesCache(currentGatt);

        stopConnection.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                disconnect();
                disconnecting = true;
                connectionState = false;
                dialog.cancel();

                Handler disconnectHandler = new Handler();
                disconnectHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {

                        finish();
                    }
                },20000);
            }
        });

        Handler connectionhandler = new Handler();
        connectionhandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Log.d("onStart", "connecGatt onCreate");
                connectGatt();
            }
        },1000);
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onStart() {
        super.onStart();

    }

    @Override
    protected void onStop() {
        super.onStop();
        disconnect();
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void connectGatt(){
        Log.d("connectGatt", "Device: "+device.toString());
        currentGatt = device.connectGatt(this, false, gattCallback, TRANSPORT_LE);
        Log.d("Connecting","connecting Device");
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            Log.d("onConnectionStateChange", "gatt:"+gatt.toString());
            Log.d("onConnectionStateChange", "status:"+ status);
            Log.d("onConnectionStateChange", "newState:"+ newState);

            final int previousState = state;
            state =newState;

            if(status == GATT_SUCCESS) {

                switch (newState) {
                    case BluetoothProfile.STATE_CONNECTED:
                        int bondstate = device.getBondState();
                        // Take action depending on the bond state
                        if (bondstate == BOND_NONE || bondstate == BOND_BONDED) {
                            // Connected to device. Discover it's services with a delay
                            //For Android 8 (SDK = 26) or newer: delay = 0! For Android 7 or lower add a delay
                            int delayWhenBonded = 1000;
                            final int delay = bondstate == BOND_BONDED ? delayWhenBonded : 0;
                            bleHandler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    Log.d("onConnectionStateChange", String.format(Locale.ENGLISH, "Discovering services of '%s' with a delay of %d ms", getName(), delay));
                                    //boolean result = gatt.discoverServices();
                                    boolean result = currentGatt.discoverServices();
                                    if (result && currentGatt != null) {
                                        connectionState = true;
                                        discoveryStarted = true;
                                    } else {
                                        Log.d("onConnectionStateChange", "discoverServices failed to start");
                                        connectionState = true;
                                    }
                                }
                            }, delay);
                        } else if (bondstate == BOND_BONDING) {
                            // Bonding process in progress
                            Log.i("onConnectionStateChange", "waiting for bonding to complete");
                            //currentGatt.discoverServices();
                            //Log.d("COnnecting", "Device connected:");
                        }
                        break;

                    case BluetoothProfile.STATE_DISCONNECTED:
                        connectionState = false;
                        completeDisconnect();
                        break;

                    case BluetoothProfile.STATE_CONNECTING:
                        Log.d("onConnectionStateChange", "device is connecting");
                        break;
                    case BluetoothProfile.STATE_DISCONNECTING:
                        connectionState = false;

                        Log.d("onConnectionStateChange", "device is disconnecting");
                        break;
                    default:
                        connectionState = false;
                        Log.d("onConnectionStateChange", "Unknown state received: " + newState);
                        break;

                }
            }else {
                connectionStateChangeUnsuccessful(status, previousState,newState);
            }

            if(dialog.isShowing()){
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        dialog.hide();
                    }
                });
            }
        }


        @Override
        public void onServicesDiscovered(final BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);

            if (status == GATT_SUCCESS){
                if (dialog.isShowing()){
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            dialog.hide();
                        }
                    });
                }
                services = currentGatt.getServices();

                if (services != null){
                    connectionState = true;
                    startReading();
                }

                Log.d("ServicesDiscovered", " Services = " + services.size());
                Log.d("ServicesDiscovered", " Gatt II- Services = " + gatt.getServices().size());
                Log.d("ServicesDiscovered", " Services = " + services);

                for(BluetoothGattService service : services){
                    Log.d("ServicesDiscovered", "Uuid = " + service.getUuid().toString());
                    characteristic = service.getCharacteristic(CHAR_UUID);
                    if(characteristic != null){
                        try{
                            Log.d("ServicesDiscovered", " Service = " + service.getUuid());
                            Log.d("ServicesDiscovered", "Characteristic= " +characteristic);
                            Log.d("ServicesDiscovered", "Characteristic= " +characteristic.getUuid());

                            Log.d("ServicesDiscovered", "CharacteristicsValue= " +characteristic.getValue());
                            Log.d("ServicesDiscovered", "Characteristics Value_lang= " +gatt.getService(SERVICE_UUID).getCharacteristic(CHAR_UUID).getValue());

                            Log.d("ServicesDiscovered", "Characteristics Properties= " +characteristic.getProperties());
                            Log.d("ServicesDiscovered", "Reading starting");

                        }catch (NullPointerException e){
                            Log.d("ServicesDiscovered", "NullpointerException");
                        }
                    }
                }
            } else if (status != GATT_SUCCESS){
                disconnect();
                connectionState = false;
                if (dialog.isShowing()){
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            dialog.hide();
                        }
                    });
                }
                return;
            }
            //taskmaster();
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);

            //Log.d("onCharacteristicRead", "F"+1);
            byte[] value = characteristic.getValue();
            directionValue(value);
            //Log.d("onCharacteristicRead", "onCharacteristicRead"+value.toString());


            //String message = characteristic.getStringValue();
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            //gatt.executeReliableWrite();
            //Log.d("onCharacteristicWrite", "onCharacteristicWrite " +);
            Log.d("onCharacteristicWrite", "F"+2);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            byte [] value = characteristic.getValue();
            directionValue(value);
            Log.d("onCharacteristicChanged", "onCharacteristicChanged"+value.toString());
            Log.d("onCharacteristicChanged", "F"+3);
        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorRead(gatt, descriptor, status);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            super.onDescriptorWrite(gatt, descriptor, status);
        }

        @Override
        public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
            super.onReliableWriteCompleted(gatt, status);
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            super.onReadRemoteRssi(gatt, rssi, status);
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            super.onMtuChanged(gatt, mtu, status);
        }
    };

    private void connectionStateChangeUnsuccessful(int status, int previousState, int newState) {
        boolean servicesDiscovered = false;
        connectionState = false;
        if(services != null){
            servicesDiscovered = !services.isEmpty();
        }

        if (previousState == BluetoothProfile.STATE_CONNECTING) {

        } else if (previousState == BluetoothProfile.STATE_CONNECTED && newState == BluetoothProfile.STATE_DISCONNECTED && !servicesDiscovered){
            completeDisconnect();
        } else {
            if (newState == BluetoothProfile.STATE_DISCONNECTED){
                Log.d("connectionUnsuccessful",String.format(Locale.ENGLISH,"device '%s' disconnected with status '%d'",getName(),status));
            } else{
                Log.d("connectionUnsuccessful",String.format(Locale.ENGLISH,"unexpected connection state change for '%s'   status '%d'",getName(),status));
            }
            completeDisconnect();
        }
        /**
         * Last change was the next if-condition and the removeDevice function
         */
        if(status == 133){
            removeDevice();
        }
    }

    private void removeDevice() {
        try{
            Method removeMethod = device.getClass().getMethod("removeBond", (Class[])null);
            removeMethod.invoke(device,(Object[]) null);
        }catch (Exception e){
            Log.d("removeDevice", e.getMessage());
        }
    }

    private void completeDisconnect() {
        if (currentGatt != null){
            currentGatt.close();
            currentGatt = null;
        }
    }

    private void startReading() {

        Handler handlerreadstarter =new Handler(Looper.getMainLooper());
        handlerreadstarter.postDelayed(new Runnable() {
            @Override
            public void run() {
                if(connectionState == true){

                    checkGattCharacteristic(characteristic, true);
                    //taskmaster();
                }

            }
        },7000);

    }

    private boolean refreshServicesCache(BluetoothGatt gatt){
        BluetoothGatt localBluetoothGatt = gatt;
        if (localBluetoothGatt == null) return false;

        boolean result = false;
        try{
            Method refreshMethod = localBluetoothGatt.getClass().getMethod("refresh");
            if(refreshMethod != null){
                result = ((boolean) refreshMethod.invoke(localBluetoothGatt));//change localBluetoothGatt with currentGatt if it doesn't works
            }
        }  catch (Exception e) {
            Log.d("ClearCache", "ERROR: Could not invoke refresh method");
        }
        return result;
    }

    private void directionValue(byte[] value) {
        if(value == null){
            value = "0,0,0".getBytes();
        }
        String message = new String(value);
        String[] stringMessageDirection = message.split(",");
        Log.d("directionValue", "messagespliter: "+message);

        xSkidJoy = Float.parseFloat(stringMessageDirection[0]);
        ySkidJoy = Float.parseFloat(stringMessageDirection[1]);
        angleTurnBy = Float.parseFloat(stringMessageDirection[2]);

        Log.d("directionValue", "TemiMovement : x : "+xSkidJoy +"  y : "+ySkidJoy+"  a : "+ angleTurnBy);
        /*
        xtemi.setText("Temi X : " + xSkidJoy);
        ytemi.setText("Temi Y : " + ySkidJoy);
        angletemi.setText("Temi Angle : " + angleTurnBy);
         */

        //robot.skidJoy(xSkidJoy, ySkidJoy);

    }


    private void taskmaster(BluetoothGattCharacteristic newCharacteristic){
        ScheduledExecutorService scheduleTaskExecutor = Executors.newScheduledThreadPool(1);

        scheduleTaskExecutor.scheduleAtFixedRate(new Runnable() {

            @Override
            public void run() {
                try{


                    //Get message as String
                    currentGatt.readCharacteristic(newCharacteristic);
                    Log.d("Robot", "Taskmaster Running...");
                    //Log.d("Taskmaster", "characteristic: "+currentGatt.readCharacteristic(characteristic));

                    if(xSkidJoy != 0.0 || ySkidJoy != 0.0 || angleTurnBy != 0.0){
                        robot.skidJoy(xSkidJoy, ySkidJoy);
                        //TODO: check if the line under really works, cause it looks like it doesn't do anything, try != --> ==
                    } else if (xSkidJoy != 0.0 && ySkidJoy != 0.0){
                        robot.stopMovement();
                    }


                    //xtemi.setText("Temi X : " + xSkidJoy);
                    //ytemi.setText("Temi Y : " + ySkidJoy);
                    //angletemi.setText("Temi Angle : " + angleTurnBy);

                } catch (Exception e){
                    Log.d("taskmaster", "Exception in Taskmaster: "+ e);
                }finally {

                }
                if (connectionState == false) {
                    Log.d("Taskmaster", "ConectionState: Disconnected, ScheduleTaskExecutor stoped");
                    scheduleTaskExecutor.shutdown();
                }


            }
        },0,500, TimeUnit.MILLISECONDS);

    }

    public void checkGattCharacteristic(BluetoothGattCharacteristic characteristic, boolean enabled) {

        if (currentGatt == null) {
            Log.d("checkGattCharacteristic", "BluetoothAdapter not initialized");
            return;
        }
        //check if the service is available on the device
        BluetoothGattService mCustomService = currentGatt.getService(SERVICE_UUID);
        if(mCustomService == null) {

            Log.d("checkGattCharacteristic", "Custom BLE Service not found");
            return;
        }
        //get the read characteristic from the service
        BluetoothGattCharacteristic mReadCharacteristic = mCustomService.getCharacteristic(CHAR_UUID);
        if(!currentGatt.readCharacteristic(mReadCharacteristic)){
            Log.d("checkGattCharacteristic", "Failed to read characteristic");
            return;
        }

        //currentGatt.readCharacteristic(characteristic);
        taskmaster(characteristic);
    }

    public void close() {
        if (currentGatt == null) {
            return;
        }
        currentGatt.close();
        //currentGatt = null;
    }

    @NotNull
    public String getName() {
        final String name = device.getName();
        if (name != null) {
            // Cache the name so that we even know it when bluetooth is switched off
            cachedName = name;
            return name;
        }
        return cachedName;
    }

    private void disconnect() {
        if (state == BluetoothProfile.STATE_CONNECTED || state == BluetoothProfile.STATE_CONNECTING) {
            Log.d("disconnect", "Disconnecting");
            Log.d("disconnect", "State : "+this.state);
            connectionState = false;
            this.state = BluetoothProfile.STATE_DISCONNECTING;
            Handler mainHandler = new Handler();
             mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (state == BluetoothProfile.STATE_DISCONNECTING /*&& currentGatt != null*/) {
                        currentGatt.disconnect();
                        Log.d("Disconnnect", "Device disconnected");
                    }
                }
            });
        }
    }
}