package com.androchill.reversegeocache;

import java.util.ArrayList;


import org.json.JSONArray;
import org.json.JSONException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.telephony.SmsMessage;

public class SMSUpdateReceiver extends BroadcastReceiver {

	// Note: this code is inherently thread unsafe because the activity may
	// accidentally read from the list of updates while the SMSUpdateReceiver is
	// writing to it. However, unless an SMS comes in exactly when the app
	// opens, this should not be a problem
	// This also means that updates will only occur when the app is first opened
	@Override
	public void onReceive(Context context, Intent intent) {
		Bundle bundle = intent.getExtras();
		if (bundle != null) {

			// Fetch SMS messages and concatenate message text
			Object[] pdus = (Object[]) bundle.get("pdus");
			final SmsMessage[] messages = new SmsMessage[pdus.length];
			for (int i = 0; i < pdus.length; i++) {
				messages[i] = SmsMessage.createFromPdu((byte[]) pdus[i]);
			}
			StringBuffer content = new StringBuffer();
			if (messages.length > 0) {
				for (int i = 0; i < messages.length; i++) {
					content.append(messages[i].getMessageBody());
				}
			}
			String mySmsText = content.toString();

			// See if the messages contain update data
			if (mySmsText.indexOf("UPDATE") == 0) {

				// Intercept the SMS so that it doesn't show up in messaging app
				abortBroadcast();

				// Save new update
				SharedPreferences prefs = PreferenceManager
						.getDefaultSharedPreferences(context);
				SharedPreferences.Editor editor = prefs.edit();
				editor.putString("update", mySmsText);
				editor.commit();
			}
		}
	}

	public static void setStringArrayPref(Context context, String key,
			ArrayList<String> values) {
		SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(context);
		SharedPreferences.Editor editor = prefs.edit();
		JSONArray a = new JSONArray();
		for (int i = 0; i < values.size(); i++) {
			a.put(values.get(i));
		}
		if (!values.isEmpty()) {
			editor.putString(key, a.toString());
		} else {
			editor.putString(key, null);
		}
		editor.commit();
	}

	public static ArrayList<String> getStringArrayPref(Context context,
			String key) {
		SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(context);
		String json = prefs.getString(key, null);
		ArrayList<String> urls = new ArrayList<String>();
		if (json != null) {
			try {
				JSONArray a = new JSONArray(json);
				for (int i = 0; i < a.length(); i++) {
					String url = a.optString(i);
					urls.add(url);
				}
			} catch (JSONException e) {
				e.printStackTrace();
			}
		}
		return urls;
	}
}
