/*
Handy App
 */

package com.example.joystickble;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelUuid;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;

import com.example.joystickjhr.JoystickJhr;

import java.util.Arrays;


public class Joystick_GattServer extends AppCompatActivity {

    TextView xjoystick, yjoystick, anglejoystick, xtemi, ytemi, angletemi, msgfortemi;
    float xskidjoy, yskidjoy, angleturnby;
    private volatile String message;
    private Button stopService;

    Handler handler = new Handler();
    int delay = 500;
    private ProgressDialog dialog;


    private static int NOTIFICATION_ID = 0;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGattServer server;
    private BluetoothLeAdvertiser bluetoothLeAdvertiser;
    private boolean start;
    public static final ParcelUuid UUID = ParcelUuid.fromString("0000FED8-0000-1000-8000-00805F9B34FB");
    public static final java.util.UUID SERVICE_UUID = java.util.UUID.fromString("00001111-0000-1000-8000-00805F9B34FB");
    public static final java.util.UUID CHAR_UUID = java.util.UUID.fromString("00002222-0000-1000-8000-00805F9B34FB");
    public static final java.util.UUID DES_UUID = java.util.UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    //public List<Float> dir;
    public String messageData;
    int connectionState = 0;
    String data = "0,0,0";

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_joystick);

        dialog = new ProgressDialog(this);
        dialog.setCancelable(false);
        dialog.setMessage("Loading");
        dialog.show();

        if (connectionState != BluetoothProfile.STATE_CONNECTED){
            setupBluetooth();
        }


        stopService = (Button) findViewById(R.id.stop_connection);
        stopService.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                Intent intent = new Intent(Joystick_GattServer.this, BluetoothadapterCheck.class);
                //bluetoothAdapter.disable();
                server.clearServices();
                server.close();
                dialog.cancel();
                startActivity(intent);
                finish();
            }
        });


    }

    @Override
    protected void onStart() {
        super.onStart();

        final JoystickJhr joystickJhr = findViewById(R.id.joystickJhr);

        xjoystick = findViewById(R.id.xjoystick);
        yjoystick = findViewById(R.id.yjoystick);
        anglejoystick = findViewById(R.id.anglejoystick);
        xtemi = findViewById(R.id.xtemi);
        ytemi = findViewById(R.id.ytemi);
        angletemi = findViewById(R.id.angletemi);
        msgfortemi = findViewById(R.id.msgfortemi);


        handler.postDelayed(new Runnable() {
            public void run() {

                joystickJhr.setOnTouchListener(new View.OnTouchListener() {
                    @SuppressLint("ClickableViewAccessibility")
                    @Override
                    public boolean onTouch(View view, MotionEvent motionEvent) {
                        joystickJhr.move(motionEvent);
                        xjoystick.setText("Position X : " + joystickJhr.joyX());
                        yjoystick.setText("Position Y : " + joystickJhr.joyY());
                        anglejoystick.setText("Angle : " + joystickJhr.angle());

                        xskidjoy = joystickJhr.joyY() / 178;
                        yskidjoy = joystickJhr.joyX() / -178;
                        angleturnby = joystickJhr.angle();

                        xtemi.setText("Temi X : " + xskidjoy);
                        ytemi.setText("Temi Y : " + yskidjoy);
                        angletemi.setText("Temi Angle :" + angleturnby);

                        float[] dir = {xskidjoy, yskidjoy, angleturnby};
                        sendmessage(dir);

                        return true;
                    }


                });


                msgfortemi.setText("X : " + xskidjoy + "  Y : " + yskidjoy + "  Angle : " + angleturnby);

                //TODO: Test if the code with the arrows above works as usual. It should lose the problem with sending the message only when the user clicks the joystick


                //DATA to Show
                data = (String.valueOf(xskidjoy) + "," + String.valueOf(yskidjoy) + "," + String.valueOf(angleturnby));
                message = (String.valueOf(data));
                //byte[] messagebytes = message.getBytes();
                //String newbytes = new String(messagebytes);


                handler.postDelayed(this, delay); //TODO: see if this handler does anything???
            }
        }, delay);
    }


    public void sendmessage(float [] dir) {
        int angle = Math.round(dir[2]);
        int[] message = {0, 0, 0};
        for (int i = 0; i < 2; i++) {
            if (dir[i] >= 0.5) {
                dir[i] = 1;
                message[i] = Math.round(dir[i]);
            } else if (dir[i] < 0.5 && dir[i] > -0.5) {
                dir[i] = 0;
                message[i] = Math.round(dir[i]);
            } else if (dir[i] <= -0.5) {
                dir[i] = -1;
                message[i] = Math.round(dir[i]);

            }
        }
        message[2] = angle;
        messageData = (String.valueOf(message[0])+","+String.valueOf(message[1])+","+String.valueOf(message[2]));
        changeCharacteristicServer();

    }

    //---------------------------------------- GATT Server ----------------------------------------

    private void setupBluetooth() {
        Log.d("setupBluetooth","SetBluetooth");
        BluetoothManager bluetoothManager = (BluetoothManager) this.getApplicationContext().getSystemService(Context.BLUETOOTH_SERVICE);
        server = bluetoothManager.openGattServer(this, serverCallback);                                     //server Starting
        initServer();
        bluetoothAdapter = bluetoothManager.getAdapter();
        advertise();
    }

    private void initServer() {
        Log.d("initServer","initServer");                  //service Starting
        BluetoothGattService service = new BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);
        BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(CHAR_UUID, BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ|BluetoothGattCharacteristic.PERMISSION_WRITE|BluetoothGattCharacteristic.PROPERTY_NOTIFY);
        characteristic.addDescriptor(new BluetoothGattDescriptor(DES_UUID, BluetoothGattCharacteristic.PERMISSION_WRITE));
        characteristic.setValue("0,0,0".getBytes());
        String str = new String(characteristic.getValue());
        Log.d("initServer","Value"+str);
        Log.d("initServer","characteristicValue"+characteristic.getValue());
        service.addCharacteristic(characteristic);
        sendNotification("Server Started");
        server.addService(service);
    }

    private void advertise() {
        Log.d("advertise","advertise");

        bluetoothLeAdvertiser = bluetoothAdapter.getBluetoothLeAdvertiser();
        Log.d("advertise","bluetoothAdvertiser:"+bluetoothLeAdvertiser.toString());
        AdvertiseData advertisementData = getAdvertisementData();
        Log.d("advertise","advertisementData:"+advertisementData.toString());
        AdvertiseSettings advertiseSettings = getAdvertiseSettings();
        Log.d("advertise","advertiseSettings:"+advertiseSettings.toString());
        bluetoothLeAdvertiser.startAdvertising(advertiseSettings, advertisementData, advertiseCallback);
        Log.d("advertise","advertise:"+advertisementData.toString());
        start = true;

            Handler mhandler = new Handler();
            mhandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (connectionState != BluetoothProfile.STATE_CONNECTED) {
                        bluetoothLeAdvertiser.stopAdvertising(advertiseCallback);
                        server.clearServices();
                        server.close();
                        dialog.cancel();
                        Intent intent = new Intent(Joystick_GattServer.this, BluetoothadapterCheck.class);
                        startActivity(intent);
                        finish();
                    }
                }
            },60000);


    }

    private AdvertiseData getAdvertisementData() {
        Log.d("getAdvertisementData","getAdvertisementData");
        AdvertiseData.Builder builder = new AdvertiseData.Builder();
        builder.setIncludeTxPowerLevel(true);
        builder.addServiceUuid(UUID);
        builder.setIncludeDeviceName(true);
        return builder.build();
    }

    private AdvertiseSettings getAdvertiseSettings() {
        Log.d("getAdvertiseSettings","getAdvertiseSettings");
        AdvertiseSettings.Builder builder = new AdvertiseSettings.Builder();
        builder.setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED);
        builder.setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH);
        builder.setConnectable(true);
        return builder.build();
    }

    private final AdvertiseCallback advertiseCallback = new AdvertiseCallback() {
        @SuppressLint("Override")
        @Override
        public void onStartSuccess(AdvertiseSettings advertiseSettings) {
            Log.d("onStartSuccess","Starting Advertising Callback");
            final String message = "Advertisement successful";
            sendNotification(message);
        }

        @SuppressLint("Override")
        @Override
        public void onStartFailure(int i) {
            final String message = "Advertisement failed error code: " + i;
            Log.e( "BLE", "Advertising onStartFailure: " + i );
            sendNotification(message);

        }

    };

    private BluetoothGattServerCallback serverCallback = new BluetoothGattServerCallback() {
        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            Log.d("onConnectionStateChange","ServerCallback");
            super.onConnectionStateChange(device, status, newState);

            if(newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d("onConnectionStateChange","onConnectionStateChange newState: "+newState);
                Log.d("onConnectionStateChange","onConnectionStateChange device"+device.toString());
                connectionState = newState;
                Intent intent =  new Intent("ServerState");
                intent.putExtra("STATE_CONNECTION", connectionState);
                //intent.setAction("Filter");
                sendBroadcast(intent);
                sendNotification("Client connected");

                if (dialog.isShowing()){
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            dialog.hide();
                        }
                    });

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED){
                connectionState = newState;
                sendNotification("Client Disconnected");
                    server.clearServices();
                    server.close();
                    finish();
                }

            }
        }

        @Override
        public void onServiceAdded(int status, BluetoothGattService service) {
            super.onServiceAdded(status, service);
        }

        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicReadRequest (device, requestId, offset, characteristic);
            byte value[] = messageData.getBytes();
            Log.d("onCharacteristicReadReq","onCharacteristicReadRequest: "+value);

            String characteristica = new String (value);
            Log.d("onCharacteristicReadReq","onCharacteristicReadRequest: "+characteristica);

            value = Arrays.copyOfRange(value,offset,value.length);
            server.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);

        }

        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value);
            //byte[] bytes = value;
            //String message = new String(bytes);
            // String message = new String(value);
            // server.sendResponse(device, requestId, 0, offset, value);
            //messagespliter(message);
            //messageDirection = message;
            //getDirection();                                                                  //message will be send to MainActivity
            // sendNotification(message);
            //Log.d("Robot","value"+ messageDirection);
        }

        @Override
        public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattDescriptor descriptor) {
            super.onDescriptorReadRequest(device, requestId, offset, descriptor);
            //aqui poner el valor recivido de Joystick
            //server.sendResponse(device, requestId,BluetoothGatt.GATT_SUCCESS, offset, descriptor);
        }

        @Override
        public void onDescriptorWriteRequest(BluetoothDevice device, int requestId, BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            super.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded, offset, value);
        }

        @Override
        public void onExecuteWrite(BluetoothDevice device, int requestId, boolean execute) {
            super.onExecuteWrite(device, requestId, execute);
        }

        @Override
        public void onNotificationSent(BluetoothDevice device, int status) {
            super.onNotificationSent(device, status);
        }

        @Override
        public void onMtuChanged(BluetoothDevice device, int mtu) {
            super.onMtuChanged(device, mtu);
        }
    };

    public void changeCharacteristicServer(){
        server.getService(SERVICE_UUID).getCharacteristic(CHAR_UUID).setValue(messageData.getBytes());
        byte [] characteristic = server.getService(SERVICE_UUID).getCharacteristic(CHAR_UUID).getValue();

        String characteristicstring = new String(characteristic);
        Log.d("changeCharacteristicSer","changeCharacteristicServer:"+characteristicstring);


    }

    @Override
    public void onDestroy() {
        if(start){
            bluetoothLeAdvertiser.stopAdvertising(advertiseCallback);
        }
        super.onDestroy();
        if (dialog.isShowing()){
            handler.post(new Runnable() {
                @Override
                public void run() {
                    dialog.hide();
                }
            });

        }

    }

    private void sendNotification(String message){
        NotificationManager mNotificationManager = (NotificationManager)
                this.getSystemService(Context.NOTIFICATION_SERVICE);


        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(this)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setContentTitle(getString(R.string.app_name))
                        .setStyle(new NotificationCompat.BigTextStyle()
                                .bigText(message))
                        .setAutoCancel(true)
                        .setContentText(message);
        Notification note = mBuilder.build();
        note.defaults |= Notification.DEFAULT_VIBRATE;
        note.defaults |= Notification.DEFAULT_SOUND;
        mNotificationManager.notify(NOTIFICATION_ID++, note);
        Log.d("sendNotification","Send Notification "+message);
    }
}