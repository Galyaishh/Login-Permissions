package com.example.smart_login_conditions.utils;

import static android.content.Context.LOCATION_SERVICE;
import static androidx.core.content.ContextCompat.getSystemService;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.Uri;
import android.provider.Settings;

import androidx.core.app.ActivityCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class PermissionUtils {
    public static boolean hasPermission(Activity activity, String[] permissions) {
        for (String permission : permissions) {
            if (activity.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    public static void requestPermission(Activity activity, String[] permissions, int requestCode) {
        ActivityCompat.requestPermissions(activity, permissions, requestCode);
    }

    public static boolean allPermissionGranted(int[] grantResults) {
        if(grantResults.length == 0)return false;
        for (int result : grantResults) {
            if(result != PackageManager.PERMISSION_GRANTED)return false;
        }
        return true;
    }


    public static void showPermissionsSettingsDialog(Activity activity, String title, String message) {
        new MaterialAlertDialogBuilder(activity)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Open Settings", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", activity.getPackageName(), null));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    activity.startActivity(intent);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    public static void showEnableLocationDialog(Activity activity) {
        new MaterialAlertDialogBuilder(activity)
                .setTitle("Location Required")
                .setMessage("Please enable Location services to scan for Bluetooth devices.")
                .setPositiveButton("Open Settings", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                    activity.startActivity(intent);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    public static boolean isLocationEnabled(Activity activity) {
        LocationManager locationManager = (LocationManager) activity.getSystemService(Context.LOCATION_SERVICE);
        return locationManager != null && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
    }


}
