package com.example.smart_login_conditions.receivers;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.telephony.TelephonyManager;
import android.util.Log;

public class CallReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(intent.getAction())) {
            String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);

            if (TelephonyManager.EXTRA_STATE_RINGING.equals(state)) {
                Log.d("CallReceiver", "Phone is ringing...");
                String incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);

                if (incomingNumber != null && !incomingNumber.isEmpty()) {
                    String contactName = getContactName(context, incomingNumber);

                    if (contactName != null) {
                        Log.d("CallReceiver", "Incoming call from: " + contactName);

                        // Save only incoming ringing calls
                        SharedPreferences prefs = context.getSharedPreferences("CallLog", Context.MODE_PRIVATE);
                        prefs.edit()
                                .putString("last_caller_name", contactName)
                                .putLong("last_call_timestamp", System.currentTimeMillis())
                                .apply();

                        Intent updateIntent = new Intent("com.example.smart_login_conditions.CALLER_UPDATED");
                        context.sendBroadcast(updateIntent);

                    }
                }
            }
        }
    }

    private String getContactName(Context context, String incomingNumber) {
        ContentResolver cr = context.getContentResolver();
        Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(incomingNumber));
        Cursor cursor = cr.query(uri, new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME}, null, null, null);
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                String name = cursor.getString(0);
                cursor.close();
                return name;
            }
            cursor.close();
        }
        return null;
    }

    private String getLastCallNumber(Context context) {
        Cursor cursor = context.getContentResolver().query(
                android.provider.CallLog.Calls.CONTENT_URI,
                null, null, null,
                android.provider.CallLog.Calls.DATE + " DESC"
        );

        if (cursor != null && cursor.moveToFirst()) {
            String number = cursor.getString(cursor.getColumnIndexOrThrow(android.provider.CallLog.Calls.NUMBER));
            cursor.close();
            return number;
        }
        return null;
    }

}

