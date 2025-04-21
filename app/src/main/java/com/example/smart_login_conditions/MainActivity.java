package com.example.smart_login_conditions;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.smart_login_conditions.adapters.ConditionAdapter;
import com.example.smart_login_conditions.databinding.ActivityMainBinding;
import com.example.smart_login_conditions.models.Condition;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

@RequiresApi(api = Build.VERSION_CODES.S)
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private ConditionAdapter conditionAdapter;
    private List<Condition> conditionList;

    private static final int REQUEST_CODE_BT_PERMISSIONS = 1001;
    private static final int REQUEST_ENABLE_BT = 2001;
    private static final int REQUEST_CODE_VOICE = 3001;
    private static final int REQUEST_CODE_CALL_PERMISSION = 4001;

    private final String VOICE_PASSWORD = "Password";
    private final String[] BLUETOOTH_PERMISSIONS = new String[]{
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
    };

    private final String[] CALL_PERMISSION = new String[]{
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_PHONE_NUMBERS
    };

    private BluetoothAdapter bluetoothAdapter;
    private static final String TARGET_DEVICE_NAME = "AirPods";

    private SensorManager sensorManager;

    private Sensor lightSensor;
    private SensorEventListener lightListener;
    private static final float BRIGHT_THRESHOLD_LUX = 10f;

    private float accumulatedRotation = 0f;
    private long lastTimestamp = 0;

    private String lastCaller = null;

    private BroadcastReceiver callUpdateReceiver;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        setupCondition();
        setupRecyclerView();
        setupGyroscopeListener();
//        setupRoomSensorListener();
        requestCallPermissions();
        loadLastCallerFromPrefs();
        findViews();
        initViews();


    }

    private void findViews() {
        binding.mainBTNLogin.setOnClickListener(v-> validateLogin());
    }

    private void validateLogin() {
        for (Condition condition : conditionList) {
            if(!condition.isPassed()) {
                Toast.makeText(this, "Login Failed, make sure all conditions are passed", Toast.LENGTH_SHORT).show();
                return;
            }
            else{
                Toast.makeText(this, "Login Successful", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(this, SuccessActivity.class);
                startActivity(intent);
                finish();
            }
        }
    }

    private void initViews() {
    }

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    @Override
    protected void onStart() {
        super.onStart();
        callUpdateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if ("com.example.smart_login_conditions.CALLER_UPDATED".equals(intent.getAction())) {
                    loadLastCallerFromPrefs();
                }
            }
        };
        IntentFilter filter = new IntentFilter("com.example.smart_login_conditions.CALLER_UPDATED");
        registerReceiver(callUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (callUpdateReceiver != null) {
            unregisterReceiver(callUpdateReceiver);
        }
    }

    private void loadLastCallerFromPrefs() {
        SharedPreferences prefs = getSharedPreferences("CallLog", MODE_PRIVATE);
        String lastCaller = prefs.getString("last_caller_name", null);

        if (conditionAdapter != null) {
            conditionAdapter.setLastCallerName(lastCaller); // Update adapter with new caller name
        }
    }

    private void requestCallPermissions() {
        boolean allGranted = true;
        for (String permission : CALL_PERMISSION) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }

        if (!allGranted) {
            ActivityCompat.requestPermissions(this, CALL_PERMISSION, REQUEST_CODE_CALL_PERMISSION);
        }
    }

    private void setupRoomSensorListener() {
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        startLightSensorMonitoring();
    }

    private void setupCondition() {
        conditionList = new ArrayList<>();
        conditionList.add(new Condition("Bluetooth Device", "Waiting...", Condition.ConditionType.ACTION_BUTTON, false));
        conditionList.add(new Condition("Voice Command", "Waiting...", Condition.ConditionType.ACTION_BUTTON, false));
        conditionList.add(new Condition("Call Match", "Waiting...", Condition.ConditionType.INPUT_FIELD, false));
        conditionList.add(new Condition("Device Spin", "Waiting...", Condition.ConditionType.AUTOMATIC, false));
        conditionList.add(new Condition("Room is bright", "Waiting...", Condition.ConditionType.AUTOMATIC, false));
    }

    private void setupRecyclerView() {
        conditionAdapter = new ConditionAdapter(conditionList, this::handleConditionAction);
        binding.mainRVList.setLayoutManager(new LinearLayoutManager(this));
        binding.mainRVList.setAdapter(conditionAdapter);
    }

    private void startLightSensorMonitoring() {
        if (lightSensor == null) {
            updateConditionStatus("Room is dark", "❌ No Light Sensor", false);
            return;
        }

        lightListener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                float lux = event.values[0]; // level of lightning in lux
                Log.d("MainActivity", "Light level: " + lux);

                if (lux > BRIGHT_THRESHOLD_LUX)  // up to 10 lux - bright room
                    updateConditionStatus("Room is bright", "✔ Room is Bright", true);
                else
                    updateConditionStatus("Room is bright", "❌ Room is Dark", false);
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {
            }
        };

        sensorManager.registerListener(lightListener, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    private void handleConditionAction(Condition condition) {
        switch (condition.getName()) {
            case "Bluetooth Device":
                checkBluetoothPermissionsAndScan();
                break;
            case "Voice Command":
                startVoiceRecognition();
                break;
        }
    }


    private void setupGyroscopeListener() {
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        Sensor gyroscopeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);

        if (gyroscopeSensor == null) {
            updateConditionStatus("Device Spin", "❌ No Gyroscope", false);
            return;
        }

        SensorEventListener gyroscopeListener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                if (lastTimestamp == 0) {
                    lastTimestamp = event.timestamp;
                    return;
                }

                float zRotationRate = event.values[2];
                float deltaTime = (event.timestamp - lastTimestamp) * 1.0f / 1_000_000_000;
                lastTimestamp = event.timestamp;

                float deltaRotation = zRotationRate * deltaTime;
                accumulatedRotation += deltaRotation;

                float fullRotations = Math.abs(accumulatedRotation) / (float) (2 * Math.PI);

                if (fullRotations >= 2) {
                    updateConditionStatus("Device Spin", "✔ 2 Spins Detected!", true);
                    sensorManager.unregisterListener(this);
                }
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {
            }
        };

        sensorManager.registerListener(gyroscopeListener, gyroscopeSensor, SensorManager.SENSOR_DELAY_GAME);
    }

    private void requestPermissionsIfNeeded() {
        if (!arePermissionsGranted(CALL_PERMISSION)) {
            ActivityCompat.requestPermissions(this, CALL_PERMISSION, REQUEST_CODE_CALL_PERMISSION);
        }
    }

    private boolean arePermissionsGranted(String[] permissions) {
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }


    private void checkBluetoothPermissionsAndScan() {


        boolean allGranted = true;
        for (String permission : BLUETOOTH_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }

        if (allGranted && isLocationEnabled()) {
            checkIfBluetoothEnabled();
            if (bluetoothAdapter == null) {
                Log.e("MainActivity", "Bluetooth adapter is null");
                updateConditionStatus("Bluetooth Device", "❌ Bluetooth Not Supported", false);
            }
            checkIfDeviceAlreadyConnected(bluetoothAdapter);
        } else {
            ActivityCompat.requestPermissions(this, BLUETOOTH_PERMISSIONS, REQUEST_CODE_BT_PERMISSIONS);
        }
    }

    @SuppressLint("MissingPermission")
    private void checkIfBluetoothEnabled() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            updateConditionStatus("Bluetooth Device", "❌ Bluetooth Off", false);
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

    }

    private void checkIfDeviceAlreadyConnected(BluetoothAdapter adapter) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                Log.d("MainActivity", "Permission not granted");
                return;
            }

            adapter.getProfileProxy(this, new BluetoothProfile.ServiceListener() {
                @SuppressLint("MissingPermission")
                @Override
                public void onServiceConnected(int profile, BluetoothProfile proxy) {
                    List<BluetoothDevice> connectedDevices = proxy.getConnectedDevices();
                    for (BluetoothDevice device : connectedDevices) {
                        Log.d("MainActivity", "Connected device: " + device.getName());
                        if (device.getName() != null && device.getName().contains(TARGET_DEVICE_NAME)) {
                            updateConditionStatus("Bluetooth Device", "✔ Connected to " + device.getName(), true);
                            return;
                        }
                    }
                    scanForBluetoothDevices(adapter);
                }

                @Override
                public void onServiceDisconnected(int profile) {
                }
            }, BluetoothProfile.HEADSET);
        } else {
            scanForBluetoothDevices(adapter);
        }
    }


    private boolean isLocationEnabled() {
        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        return locationManager != null && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                checkIfDeviceAlreadyConnected(bluetoothAdapter);
            } else {
                Toast.makeText(this, "Bluetooth must be enabled", Toast.LENGTH_SHORT).show();
            }
        }

        if (requestCode == REQUEST_CODE_VOICE && resultCode == RESULT_OK && data != null) {
            ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty()) {
                String spokenText = results.get(0).toLowerCase();

                if (spokenText.contains(VOICE_PASSWORD)) {
                    updateConditionStatus("Voice Command", "✔ Password matched", true);
                } else {
                    updateConditionStatus("Voice Command", "❌ Wrong Password", false);
                }
            }
        }
    }

    private void scanForBluetoothDevices(BluetoothAdapter adapter) {
        updateConditionStatus("Bluetooth Device", "🔍 Scanning...", false);

        BroadcastReceiver receiver = new BroadcastReceiver() {
            private boolean found = false;

            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                Log.d("MainActivity", "Broadcast received: " + action);

                if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                            != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions((Activity) context, BLUETOOTH_PERMISSIONS, REQUEST_CODE_BT_PERMISSIONS);
                        return;
                    }

                    if (device != null && device.getName() != null) {
                        Log.d("MainActivity", "Found device: " + device.getName());

                        if (device.getName().contains(TARGET_DEVICE_NAME)) {
                            found = true;
                            updateConditionStatus("Bluetooth Device", "✔ " + device.getName() + " Found", true);
                        }
                    }
                }

                if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                    Log.d("MainActivity", "Discovery finished.");
                    if (!found) {
                        updateConditionStatus("Bluetooth Device", "❌ Not Found", false);
                    }
                    try {
                        unregisterReceiver(this);
                    } catch (Exception e) {
                        Log.w("MainActivity", "Receiver already unregistered");
                    }
                    if (adapter.isDiscovering()) {
                        adapter.cancelDiscovery();
                    }
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        registerReceiver(receiver, filter);

        boolean started = adapter.startDiscovery();
        Log.d("MainActivity", "startDiscovery() called, result: " + started);

        new Handler().postDelayed(() -> {
            Log.d("MainActivity", "Scan timeout reached (force stopping)");
        }, 10000);

    }

    private void startVoiceRecognition() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US");
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, VOICE_PASSWORD);

        try {
            startActivityForResult(intent, REQUEST_CODE_VOICE);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "", Toast.LENGTH_SHORT).show();
        }
    }


    private void updateConditionStatus(String name, String status, boolean passed) {
        for (int i = 0; i < conditionList.size(); i++) {
            Condition condition = conditionList.get(i);
            if (condition.getName().equals(name)) {
                condition.setStatus(status);
                condition.setPassed(passed);
                conditionAdapter.notifyItemChanged(i);
                break;
            }
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        boolean allGranted = true;
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }

        switch (requestCode) {
            case REQUEST_CODE_BT_PERMISSIONS:
                if (allGranted) {
                    Log.d("BluetoothPermission", "Permission granted");
                    if (!isLocationEnabled()) {
                        showEnableLocationDialog();
                        return;
                    }
                    scanForBluetoothDevices(bluetoothAdapter);
                } else {
                    Log.d("BluetoothPermission", "Permission denied");
                    showPermissionsSettingsDialog("Permissions Required", "Bluetooth permissions are required to detect your device.");
                }
                break;

            case REQUEST_CODE_CALL_PERMISSION:
                if (allGranted) {
                    Log.d("CallPermission", "Permission granted");

                } else {
                    Log.d("CallPermission", "Permission denied");
                    showPermissionsSettingsDialog("Permissions Required", "Call permissions are required to get your password.");
                }
                break;
        }
    }

    private void showPermissionsSettingsDialog(String title, String message) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Open Settings", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", getPackageName(), null));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }


//    private void showCallPermissionDeniedDialog() {
//        new MaterialAlertDialogBuilder(this)
//                .setTitle("Permissions Required")
//                .setMessage("Contacts and Calls permissions are required to get your password.")
//                .setPositiveButton("Open Settings", (dialog, which) -> {
//                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
//                            Uri.fromParts("package", getPackageName(), null));
//                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//                    startActivity(intent);
//                })
//                .setNegativeButton("Cancel", null)
//                .show();
//    }
//
//    private void showBluetoothPermissionDeniedDialog() {
//        new MaterialAlertDialogBuilder(this)
//                .setTitle("Permissions Required")
//                .setMessage("Bluetooth & Location permissions are required to detect your device.")
//                .setPositiveButton("Open Settings", (dialog, which) -> {
//                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
//                            Uri.fromParts("package", getPackageName(), null));
//                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//                    startActivity(intent);
//                })
//                .setNegativeButton("Cancel", null)
//                .show();
//    }

//    private void showLocationRequiredDialog() {
//        new MaterialAlertDialogBuilder(this)
//                .setTitle("Location Required")
//                .setMessage("Please enable Location services to scan for Bluetooth devices.")
//                .setPositiveButton("Open Settings", (dialog, which) -> {
//                    Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
//                    startActivity(intent);
//                })
//                .setNegativeButton("Cancel", null)
//                .show();
//    }

    private void showEnableLocationDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Location Required")
                .setMessage("Please enable Location services to scan for Bluetooth devices.")
                .setPositiveButton("Open Settings", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                    startActivity(intent);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }



    @Override
    protected void onPause() {
        super.onPause();
        if (lightListener != null) {
            sensorManager.unregisterListener(lightListener);
        }
    }

}
