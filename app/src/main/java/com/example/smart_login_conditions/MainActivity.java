package com.example.smart_login_conditions;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.smart_login_conditions.adapters.ConditionAdapter;
import com.example.smart_login_conditions.databinding.ActivityMainBinding;
import com.example.smart_login_conditions.managers.BluetoothManager;
import com.example.smart_login_conditions.managers.SensorHandler;
import com.example.smart_login_conditions.models.Condition;
import com.example.smart_login_conditions.utils.PermissionUtils;

import java.util.ArrayList;
import java.util.List;

@RequiresApi(api = Build.VERSION_CODES.S)
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private ConditionAdapter conditionAdapter;
    private List<Condition> conditionList;

    private static final int REQUEST_CODE_VOICE = 3001;
    private static final int REQUEST_CODE_CALL_PERMISSION = 4001;

    private final String VOICE_PASSWORD = "Password";
    private final String[] CALL_PERMISSION = new String[]{
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_PHONE_NUMBERS
    };

    private static final int REQUEST_CODE_MIC_PERMISSION = 5001;
    private static final String[] MIC_PERMISSION = new String[]{
            Manifest.permission.RECORD_AUDIO
    };

    private BluetoothManager bluetoothManager;
    private SensorHandler sensorHandler;
    private BroadcastReceiver callUpdateReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sensorHandler = new SensorHandler(this);
        bluetoothManager = new BluetoothManager(this, "AirPods");

        bluetoothManager.setStatusListener((status, isSuccess) ->
                updateConditionStatus("Bluetooth Device", status, isSuccess));

        setupCondition();
        setupRecyclerView();

        startGyroscopeMonitoring();
        startLightSensorMonitoring();

        loadLastCallerFromPrefs();
        setupListener();
    }

    private void setupListener() {
        binding.mainBTNLogin.setOnClickListener(v -> validateLogin());
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

    private void handleConditionAction(Condition condition) {
        switch (condition.getName()) {
            case "Bluetooth Device":
                bluetoothManager.checkBluetoothPermissionsAndScan();
                break;
            case "Voice Command":
                if (PermissionUtils.hasPermission(this, MIC_PERMISSION))
                    startVoiceRecognition();
                else
                    PermissionUtils.requestPermission(this, MIC_PERMISSION, REQUEST_CODE_MIC_PERMISSION);
                break;
            case "Call Match":
                requestCallPermissions();
                break;
        }
    }

    private void validateLogin() {
        for (Condition condition : conditionList) {
            if (!condition.isPassed()) {
                Toast.makeText(this, "Login Failed, make sure all conditions are passed", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        Toast.makeText(this, "Login Successful", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(this, SuccessActivity.class);
        startActivity(intent);
        finish();
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
        if (callUpdateReceiver != null)
            unregisterReceiver(callUpdateReceiver);
    }

    private void loadLastCallerFromPrefs() {
        SharedPreferences prefs = getSharedPreferences("CallLog", MODE_PRIVATE);
        String lastCaller = prefs.getString("last_caller_name", null);

        if (conditionAdapter != null)
            conditionAdapter.setLastCallerName(lastCaller);
    }

    private void requestCallPermissions() {
        if (!PermissionUtils.hasPermission(this, CALL_PERMISSION))
            PermissionUtils.requestPermission(this, CALL_PERMISSION, REQUEST_CODE_CALL_PERMISSION);
    }

    private void startGyroscopeMonitoring() {
        sensorHandler.startGyroscopeMonitoring((status, passed) ->
                updateConditionStatus("Device Spin", status, passed));
    }

    private void startLightSensorMonitoring() {
        sensorHandler.startLightMonitoring((status, passed) ->
                updateConditionStatus("Room is bright", status, passed));
    }


    private void startVoiceRecognition() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US");
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, VOICE_PASSWORD);

        try {
            startActivityForResult(intent, REQUEST_CODE_VOICE);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Voice recognition not supported on this device", Toast.LENGTH_SHORT).show();
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
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == BluetoothManager.REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK)
                bluetoothManager.checkBluetoothPermissionsAndScan();
            else
                Toast.makeText(this, "Bluetooth must be enabled", Toast.LENGTH_SHORT).show();
        }

        if (requestCode == REQUEST_CODE_VOICE && resultCode == RESULT_OK && data != null) {
            ArrayList<String> results = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (results != null && !results.isEmpty()) {
                String spokenText = results.get(0).toLowerCase();

                if (spokenText.contains(VOICE_PASSWORD.toLowerCase()))
                    updateConditionStatus("Voice Command", "✔ Password matched", true);
                else
                    updateConditionStatus("Voice Command", "❌ Wrong Password", false);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case BluetoothManager.REQUEST_CODE_BT_PERMISSIONS:
                if (PermissionUtils.allPermissionGranted(grantResults)) {
                    Log.d("BluetoothPermission", "Permission granted");
                    bluetoothManager.checkBluetoothPermissionsAndScan();
                } else {
                    Log.d("BluetoothPermission", "Permission denied");
                    PermissionUtils.showPermissionsSettingsDialog(this, "Permissions Required",
                            "Bluetooth & Location permissions are required to detect your device.");
                }
                break;

            case REQUEST_CODE_CALL_PERMISSION:
                if (!PermissionUtils.allPermissionGranted(grantResults))
                    PermissionUtils.showPermissionsSettingsDialog(this, "Permissions Required",
                            "Call permissions are required to get your password.");
                break;

            case REQUEST_CODE_MIC_PERMISSION:
                if (PermissionUtils.allPermissionGranted(grantResults))
                    startVoiceRecognition();
                else {
                    PermissionUtils.showPermissionsSettingsDialog(this, "Permissions Required",
                            "Microphone permission is required to use voice command.");
                    Toast.makeText(this, "Microphone permission is required to use voice command", Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorHandler.stopAll();
        bluetoothManager.stopDiscovery();
    }
}