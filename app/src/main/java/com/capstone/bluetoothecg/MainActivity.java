package com.capstone.bluetoothecg;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.LinkedBlockingQueue;

public class MainActivity extends AppCompatActivity {

    private final static String TAG = MainActivity.class.getSimpleName();

    private RBLService mBluetoothLeService;
    private BluetoothAdapter mBluetoothAdapter;
    public static List<BluetoothDevice> mDevice = new ArrayList<BluetoothDevice>();

    Button lastDeviceBtn = null;
    Button scanAllBtn = null;
    TextView uuidTv = null;
    TextView lastUuid = null;

    private TextView AnalogInValueTv = null;
    private BluetoothGattCharacteristic characteristicTx = null;
    private Button AnalogInBtn;

    public static int Value;
    static LinkedBlockingQueue<Double> bluetoothQueueForUI = new LinkedBlockingQueue<Double>();


    private static final int REQUEST_ENABLE_BT = 1;
    private static final long SCAN_PERIOD = 3000;
    public static final int REQUEST_CODE = 30;
    private String mDeviceAddress;
    private String mDeviceName;
    private boolean flag = true;
    private boolean connState = false;

    String path = Environment.getExternalStorageDirectory().getAbsolutePath();
    String fname = "flash.txt";

    private byte[] data = new byte[3];


    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName,
                                       IBinder service) {
            mBluetoothLeService = ((RBLService.LocalBinder) service)
                    .getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (RBLService.ACTION_GATT_CONNECTED.equals(action)) {
                flag = true;
                connState = true;

                Toast.makeText(getApplicationContext(), "Connected",
                        Toast.LENGTH_SHORT).show();
                writeToFile(mDeviceName + " ( " + mDeviceAddress + " )");
                lastUuid.setText(mDeviceName + " ( " + mDeviceAddress + " )");
                lastDeviceBtn.setVisibility(View.GONE);
                scanAllBtn.setText("Disconnect");
                startReadRssi();

            } else if (RBLService.ACTION_GATT_SERVICES_DISCOVERED
                    .equals(action)) {
                Toast.makeText(getApplicationContext(), "Connected",
                        Toast.LENGTH_SHORT).show();

                getGattService(mBluetoothLeService.getSupportedGattService());
            }else if (RBLService.ACTION_GATT_DISCONNECTED.equals(action)) {
                flag = false;
                connState = false;

                Toast.makeText(getApplicationContext(), "Disconnected",
                        Toast.LENGTH_SHORT).show();
                scanAllBtn.setText("Scan All");
                uuidTv.setText("");
                lastDeviceBtn.setVisibility(View.VISIBLE);
            } else if (RBLService.ACTION_GATT_RSSI.equals(action)) {
                displayData(intent.getStringExtra(RBLService.EXTRA_DATA));
            } else if (RBLService.ACTION_DATA_AVAILABLE.equals(action)) {
                data = intent.getByteArrayExtra(RBLService.EXTRA_DATA);
                readAnalogInValue(data);

            }
        }
    };

    private void writeToFile(String flash) {
        File sdfile = new File(path, fname);
        try {
            FileOutputStream out = new FileOutputStream(sdfile);
            out.write(flash.getBytes());
            out.flush();
            out.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("resource")
    private String readConnDevice() {
        String filepath = path + "/" + fname;
        String line = null;

        File file = new File(filepath);
        try {
            FileInputStream f = new FileInputStream(file);
            InputStreamReader isr = new InputStreamReader(f, "GB2312");
            BufferedReader dr = new BufferedReader(isr);
            line = dr.readLine();
        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return line;
    }

    private void displayData(String data) {
        if (data != null) {
            uuidTv.setText("RSSI:\t" + data);
        }
    }



    private void startReadRssi() {
        new Thread() {
            public void run() {

                while (flag) {
                    mBluetoothLeService.readRssi();
                    try {
                        sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            };
        }.start();
    }

    private void getGattService(BluetoothGattService gattService) {
        if (gattService == null)
            return;

        startReadRssi();

        characteristicTx = gattService
                .getCharacteristic(RBLService.UUID_BLE_SHIELD_TX);

        BluetoothGattCharacteristic characteristicRx = gattService
                .getCharacteristic(RBLService.UUID_BLE_SHIELD_RX);
        mBluetoothLeService.setCharacteristicNotification(characteristicRx,
                true);
        mBluetoothLeService.readCharacteristic(characteristicRx);
    }

    private void readAnalogInValue(byte[] data) {
        for (int i = 0; i < data.length; i += 3) {
            if (data[i] == 0x0B) {

                Value = ((data[i + 1] << 8) & 0x0000ff00)
                        | (data[i + 2] & 0x000000ff);

                AnalogInValueTv.setText("Value: " + Value);

            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
//        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
//        setSupportActionBar(toolbar);

//        getSupportActionBar().show();
//        setTitle("Home");

        AnalogInValueTv = (TextView) findViewById(R.id.AIText);


        uuidTv = (TextView) findViewById(R.id.uuid);


        lastUuid = (TextView) findViewById(R.id.lastDevice);
        String connDeviceInfo = readConnDevice();
        if (connDeviceInfo == null) {
            lastUuid.setText("");
        } else {
            mDeviceName = connDeviceInfo.split("\\( ")[0].trim();
            String str = connDeviceInfo.split("\\( ")[1];
            mDeviceAddress = str.substring(0, str.length() - 2);
            lastUuid.setText(connDeviceInfo);
        }

        lastDeviceBtn = (Button) findViewById(R.id.ConnLastDevice);
        lastDeviceBtn.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                mDevice.clear();
                String connDeviceInfo = readConnDevice();
                if (connDeviceInfo == null) {
                    Toast toast = Toast.makeText(MainActivity.this,
                            "No Last connect device!", Toast.LENGTH_SHORT);
                    toast.setGravity(Gravity.CENTER, 0, 0);
                    toast.show();

                    return;
                }

                String str = connDeviceInfo.split("\\( ")[1];
                final String mDeviceAddress = str.substring(0, str.length() - 2);

                scanLeDevice();

                Timer mNewTimer = new Timer();
                mNewTimer.schedule(new TimerTask() {

                    @Override
                    public void run() {
                        for (BluetoothDevice device : mDevice)
                            if ((device.getAddress().equals(mDeviceAddress))) {
                                mBluetoothLeService.connect(mDeviceAddress);

                                return;
                            }

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast toast = Toast.makeText(MainActivity.this,
                                        "No Last connect device!",
                                        Toast.LENGTH_SHORT);
                                toast.setGravity(Gravity.CENTER, 0, 0);
                                toast.show();
                            }
                        });

                    }
                }, SCAN_PERIOD);
            }
        });

        AnalogInBtn = (Button) findViewById(R.id.AnalogInBtn);
        AnalogInBtn.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                byte[] buf = new byte[] { (byte) 0xA0, (byte) 0x00, (byte) 0x00 };

                buf[1] = 0x01;


                characteristicTx.setValue(buf);
                mBluetoothLeService.writeCharacteristic(characteristicTx);



                startActivity(new Intent(MainActivity.this, RealtimeGraph.class));
            }
        });

        scanAllBtn = (Button) findViewById(R.id.ScanAll);
        scanAllBtn.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                if (connState == false) {
                    scanLeDevice();

                    try {
                        Thread.sleep(SCAN_PERIOD);
                    } catch (InterruptedException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                    Intent intent = new Intent(getApplicationContext(),
                            Device.class);
                    startActivityForResult(intent, REQUEST_CODE);
                } else {
                    mBluetoothLeService.disconnect();
                    mBluetoothLeService.close();
                    scanAllBtn.setText("Scan All");
                    uuidTv.setText("");
                    lastDeviceBtn.setVisibility(View.VISIBLE);
                }
            }
        });

        if (!getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "Ble not supported", Toast.LENGTH_SHORT)
                    .show();
            finish();
        }

        final BluetoothManager mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, "Ble not supported", Toast.LENGTH_SHORT)
                    .show();
            finish();
            return;
        }

        Intent gattServiceIntent = new Intent(MainActivity.this, RBLService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(
                    BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();

        intentFilter.addAction(RBLService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(RBLService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(RBLService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(RBLService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(RBLService.ACTION_GATT_RSSI);

        return intentFilter;
    }

    private void scanLeDevice() {
        new Thread() {

            @Override
            public void run() {
                mBluetoothAdapter.startLeScan(mLeScanCallback);

                try {
                    Thread.sleep(SCAN_PERIOD);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                mBluetoothAdapter.stopLeScan(mLeScanCallback);
            }
        }.start();
    }

    private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {

        @Override
        public void onLeScan(final BluetoothDevice device, final int rssi,
                             byte[] scanRecord) {

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (device != null) {
                        if (mDevice.indexOf(device) == -1)
                            mDevice.add(device);
                    }
                }
            });
        }
    };

    @Override
    protected void onStop() {
        super.onStop();

        flag = false;

        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mServiceConnection != null)
            unbindService(mServiceConnection);

        System.exit(0);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // User chose not to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BT
                && resultCode == Activity.RESULT_CANCELED) {
            finish();
            return;
        } else if (requestCode == REQUEST_CODE
                && resultCode == Device.RESULT_CODE) {
            mDeviceAddress = data.getStringExtra(Device.EXTRA_DEVICE_ADDRESS);
            mDeviceName = data.getStringExtra(Device.EXTRA_DEVICE_NAME);
            mBluetoothLeService.connect(mDeviceAddress);
        }

        super.onActivityResult(requestCode, resultCode, data);
    }
}

