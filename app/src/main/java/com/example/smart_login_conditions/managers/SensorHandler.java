package com.example.smart_login_conditions.managers;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

import java.util.function.BiConsumer;

public class SensorHandler {

    private SensorManager sensorManager;

    private Sensor gyroscopeSensor;
    private Sensor lightSensor;

    private SensorEventListener gyroscopeListener;

    private SensorEventListener lightListener;
    private static final float BRIGHT_THRESHOLD_LUX = 10f;

    private float accumulatedRotation = 0f;
    private long lastTimestamp = 0;


    public SensorHandler(Context context) {
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
            gyroscopeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        }
    }

    public void startLightMonitoring(BiConsumer<String, Boolean> conditionStatusCallback) {
        if (lightSensor == null) {
            conditionStatusCallback.accept("❌ No Light Sensor", false);
            return;
        }

        lightListener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                float lux = event.values[0];
                Log.d("SensorHandler", "Light level: " + lux);

                if (lux > BRIGHT_THRESHOLD_LUX) {
                    conditionStatusCallback.accept("✔ Room is Bright", true);
                } else {
                    conditionStatusCallback.accept("❌ Room is Dark", false);
                }
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {}
        };

        sensorManager.registerListener(lightListener, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    public void startGyroscopeMonitoring(BiConsumer<String, Boolean> conditionStatusCallback) {
        if (gyroscopeSensor == null) {
            Log.d("SensorHandler", "No Gyroscope Sensor");
            conditionStatusCallback.accept("❌ No Gyroscope", false);
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
                    conditionStatusCallback.accept("✔ 2 Spins Detected!", true);
                    sensorManager.unregisterListener(this);
                }
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {
            }
        };

        sensorManager.registerListener(gyroscopeListener, gyroscopeSensor, SensorManager.SENSOR_DELAY_GAME);
    }


    public void stopAll() {
        if (lightListener != null) sensorManager.unregisterListener(lightListener);
        if (gyroscopeListener != null) sensorManager.unregisterListener(gyroscopeListener);
    }

}
