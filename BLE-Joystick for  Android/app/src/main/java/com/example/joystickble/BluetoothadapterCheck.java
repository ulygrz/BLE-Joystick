/*
Handy App
 */

package com.example.joystickble;

import android.app.ActivityManager;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class BluetoothadapterCheck extends AppCompatActivity {

    private static final int ENABLE_BLUETOOTH_REQUEST = 17;
    private static int NOTIFICATION_ID = 0;
    private Button find;

    public int connectionState = 0;
    boolean isJoystickRunning = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Handler mhandler = new Handler();
        find = (Button) findViewById(R.id.findtemi);


        find.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final BluetoothAdapter bluetoothAdapter;
                final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
                bluetoothAdapter = bluetoothManager.getAdapter();

                if (bluetoothAdapter == null) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(BluetoothadapterCheck.this);
                    builder.setTitle("Service");
                    builder.setMessage("Bluetooth connected").setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            finish();
                        }
                    });
                    builder.show();

                } else if (!bluetoothAdapter.isEnabled()) {
                    Log.d("AQUI1","entro aqui");
                    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    BluetoothadapterCheck.this.startActivityForResult(enableBtIntent, ENABLE_BLUETOOTH_REQUEST);

                } else if (!bluetoothAdapter.isMultipleAdvertisementSupported()) {

                    Log.d("AQUI2","entro aqui");
                    AlertDialog.Builder builder = new AlertDialog.Builder(BluetoothadapterCheck.this);
                    builder.setTitle("Error");
                    builder.setMessage("Return true if the multi advertisement is supported by the chipset")
                            .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    finish();
                                }
                            });
                    builder.show();

                }else {
                    startJoystick();
                }
            }
        });
    }



    @Override
    protected void onStart() {
        super.onStart();
        Log.d("Main", "onStart");


    }

    @Override
    protected void onResume() {
        super.onResume();

    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }


    private void startJoystick(){
        //Intent intentJoystick =new Intent(this, Joystick.class);
        Intent intentJoystick =new Intent(this, Joystick_GattServer.class);

        startActivity(intentJoystick);
        isJoystickRunning = true;
        Log.d("Main", "Joystick started");

    }


}

