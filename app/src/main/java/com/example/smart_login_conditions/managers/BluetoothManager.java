package com.example.smart_login_conditions.managers;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Handler;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;

import com.example.smart_login_conditions.utils.PermissionUtils;

import java.util.List;

@RequiresApi(api = Build.VERSION_CODES.S)
public class BluetoothManager {
    private static final String TAG = "BluetoothManager";

    private final Context context;
    private final BluetoothAdapter bluetoothAdapter;
    private final String targetDeviceName;

    public interface BluetoothStatusListener {
        void onStatusUpdate(String status, boolean isSuccess);
    }

    private BluetoothStatusListener statusListener;

    public static final int REQUEST_ENABLE_BT = 2001;
    public static final int REQUEST_CODE_BT_PERMISSIONS = 1001;

    public static final String[] BLUETOOTH_PERMISSIONS = new String[]{
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
    };

    public BluetoothManager(Context context, String targetDeviceName) {
        this.context = context;
        this.targetDeviceName = targetDeviceName;
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    public void setStatusListener(BluetoothStatusListener listener) {
        this.statusListener = listener;
    }

    public boolean isBluetoothSupported() {
        return bluetoothAdapter != null;
    }

    public boolean isBluetoothEnabled() {
        return isBluetoothSupported() && bluetoothAdapter.isEnabled();
    }

    public boolean isLocationEnabled() {
        LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        return locationManager != null && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
    }

    @SuppressLint("MissingPermission")
    public void checkBluetoothPermissionsAndScan() {
        if (PermissionUtils.hasPermission((Activity) context, BLUETOOTH_PERMISSIONS)) {
            if (!isLocationEnabled()) {
                PermissionUtils.showEnableLocationDialog((Activity) context);
                updateStatus("❌ Location Off", false);
                return;
            }

            if (!isBluetoothEnabled()) {
                updateStatus("❌ Bluetooth Off", false);
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                ((Activity) context).startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                return;
            }

            checkIfDeviceAlreadyConnected();
        } else {
            PermissionUtils.requestPermission((Activity) context, BLUETOOTH_PERMISSIONS, REQUEST_CODE_BT_PERMISSIONS);
        }
    }

    @SuppressLint("MissingPermission")
    private void checkIfDeviceAlreadyConnected() {
        bluetoothAdapter.getProfileProxy(context, new BluetoothProfile.ServiceListener() {
            @SuppressLint("MissingPermission")
            @Override
            public void onServiceConnected(int profile, BluetoothProfile proxy) {
                List<BluetoothDevice> connectedDevices = proxy.getConnectedDevices();
                boolean deviceFound = false;

                for (BluetoothDevice device : connectedDevices) {
                    if (device.getName() != null && device.getName().contains(targetDeviceName)) {
                        updateStatus("✔ Connected to " + device.getName(), true);
                        deviceFound = true;
                        break;
                    }
                }

                if (!deviceFound)
                    scanForBluetoothDevices();

                bluetoothAdapter.closeProfileProxy(profile, proxy);
            }

            @Override
            public void onServiceDisconnected(int profile) {
            }
        }, BluetoothProfile.HEADSET);
    }

    @SuppressLint("MissingPermission")
    private void scanForBluetoothDevices() {
        updateStatus("🔍 Scanning...", false);

        BroadcastReceiver receiver = new BroadcastReceiver() {
            private boolean found = false;

            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();

                if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if (device != null && device.getName() != null && device.getName().contains(targetDeviceName)) {
                        found = true;
                        updateStatus("✔ " + device.getName() + " Found", true);
                    }
                }

                if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                    if (!found) {
                        updateStatus("❌ Not Found", false);
                    }

                    try {
                        context.unregisterReceiver(this);
                    } catch (Exception ignored) {
                        Log.w(TAG, "Receiver already unregistered");
                    }

                    if (bluetoothAdapter.isDiscovering()) {
                        bluetoothAdapter.cancelDiscovery();
                    }
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        context.registerReceiver(receiver, filter);

        bluetoothAdapter.startDiscovery();

        // set timeout to cancel discovery if it takes too long
        new Handler().postDelayed(() -> {
            if (bluetoothAdapter.isDiscovering()) {
                bluetoothAdapter.cancelDiscovery();
            }
        }, 10000);
    }

    private void updateStatus(String status, boolean isSuccess) {
        if (statusListener != null) {
            statusListener.onStatusUpdate(status, isSuccess);
        }
    }

    @SuppressLint("MissingPermission")
    public void stopDiscovery() {
        if (PermissionUtils.hasPermission((Activity) context, new String[]{Manifest.permission.BLUETOOTH_SCAN})) {
            if (bluetoothAdapter != null && bluetoothAdapter.isDiscovering()) {
                bluetoothAdapter.cancelDiscovery();
            }
        } else {
            Log.w(TAG, "Skipping stopDiscovery: missing BLUETOOTH_SCAN permission");
        }
    }

}