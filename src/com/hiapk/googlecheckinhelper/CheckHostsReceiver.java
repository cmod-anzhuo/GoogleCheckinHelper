package com.hiapk.googlecheckinhelper;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Set;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.text.TextUtils;

import com.hiapk.googlecheckinhelper.util.HelperUtils;
import com.hiapk.googlecheckinhelper.util.Log;

public class CheckHostsReceiver extends BroadcastReceiver {

	public static final String CHECK_ACTION_NAME = "com.hiapk.googlecheckinhelper.action.checkhosts";

	private static final String LOGTAG = "CheckHostsReceiver";
	public static final String HostsPath = "/system/etc/hosts";
	private static final String mHostUri = "http://rom.hiapk.com/updatehosts.php";
	private Context mContext;
	private boolean mShowNotification = false;
	private SharedPreferences mSharedPreferences;

	@Override
	public void onReceive(Context context, Intent intent) {
		Log.v(LOGTAG, "onReceive");
		mSharedPreferences = context.getSharedPreferences(context
				.getString(R.string.app_sharepreference_name),
				Context.MODE_WORLD_READABLE);
		if (intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {
			if (mSharedPreferences.getBoolean(context
					.getString(R.string.hiapkrom_sharepreference_name), false)
					&& mSharedPreferences.getBoolean(context
							.getString(R.string.key_cbp_auto_check), true)) {
				HelperUtils
						.setCheckAlarm(
								context,
								true,
								mSharedPreferences
										.getLong(
												context
														.getString(R.string.triggerTime_sharepreference_name),
												System.currentTimeMillis()),
								mSharedPreferences
										.getLong(
												context
														.getString(R.string.key_lp_choseinterval),
												Long
														.valueOf(context
																.getString(R.string.alarm_interval_default))));
				Log.v(LOGTAG, "set alarm enabled after boot completed");
			} else {
				Log.v(LOGTAG, "set alarm disabled after boot completed");
				return;
			}
		} else {

			mContext = context;
			checkHosts();
		}
	}

	private void checkHosts() {

		new Thread(new Runnable() {

			@Override
			public void run() {
				DefaultHttpClient client = new DefaultHttpClient();
				HttpGet get = new HttpGet(mHostUri);
				BufferedReader httpBr = null;
				try {
					HttpResponse httpResponse = client.execute(get);
					InputStream httpIs = httpResponse.getEntity().getContent();
					httpBr = new BufferedReader(new InputStreamReader(httpIs));
					if (httpResponse.getEntity().getContentLength() == 1
							&& httpBr.read() == '0') {
						Log.v(LOGTAG, "no update found");
						notifyUpdateFinished(mContext, R.string.notification_no_update);
						return;
					}
				} catch (ClientProtocolException e1) {
					Log.e(LOGTAG, "connect to server failed."
							+ e1.getLocalizedMessage());
					notifyUpdateFinished(mContext, R.string.notification_update_failed);
					return;
				} catch (IOException e1) {
					Log.e(LOGTAG, "read http respose failed."
							+ e1.getLocalizedMessage());
					notifyUpdateFinished(mContext, R.string.notification_update_failed);
					return;
				}
				if (!HelpMeActivity.mEnforceNotification) {
					Log.v(LOGTAG, "reset alarm trigger time");
					long triggerTime = HelperUtils.getToday();
					triggerTime += mSharedPreferences
							.getLong(
									mContext
											.getString(R.string.key_lp_choseinterval),
									Long
											.valueOf(mContext
													.getString(R.string.alarm_interval_default)));
					mSharedPreferences
							.edit()
							.putLong(
									mContext
											.getString(R.string.triggerTime_sharepreference_name),
									triggerTime).commit();
				}
				UpdateHost(httpBr);

			}
		}).start();

	}

	private synchronized void UpdateHost(BufferedReader httpBr) {
		HashMap<String, String> hpNewHosts = new HashMap<String, String>();
		String host = null;

		try {
			while ((host = httpBr.readLine()) != null) {
				Log.v(LOGTAG, "get host:" + host);
				if (TextUtils.isEmpty(host) || host.trim().startsWith("#"))
					continue;
				String[] pair = host.split("\\s+");
				hpNewHosts.put(pair[1], pair[0]);
			}
			Set<String> keySet = hpNewHosts.keySet();
			if(keySet.size() <= 0) return;
			String lastUpdate = "";
			for(String key : keySet) {
				lastUpdate += key + " ";
			}
			//FOR rollback
			Log.v(LOGTAG, "lastUpdate:" + lastUpdate);
			mSharedPreferences.edit().putString("HOST_LAST_UPDATE", lastUpdate).commit();
			if (HelperUtils.rootCMD(mContext.getString(R.string.cmd_remount_system)) != 0) {
				Log.e(LOGTAG, "remount system failer");
				notifyUpdateFinished(mContext, R.string.notification_update_failed);
				return;
			}
			String tmpFileName = HostsPath + ".tmp";
			if (HelperUtils.rootCMD("echo '' > " + tmpFileName) != 0) {
				Log.e(LOGTAG, "create tmp file failed");
				notifyUpdateFinished(mContext, R.string.notification_update_failed);
				return;
			}
			File mHostFile = new File(HostsPath);
			File tmpFile = new File(tmpFileName);
			if (!tmpFile.exists()) {
				Log.e(LOGTAG, "temp file not exists");
				notifyUpdateFinished(mContext, R.string.notification_update_failed);
				return;
			}
			BufferedInputStream bis = new BufferedInputStream(
					new FileInputStream(mHostFile));
			FileOutputStream fos = new FileOutputStream(tmpFile);
			byte[] buff = new byte[(int) mHostFile.length()];
			bis.read(buff);
			String[] origFileCont = new String(buff).split("\n");
			for (String line : origFileCont) {
				Log.v(LOGTAG, "read line: " + line);
				if (TextUtils.isEmpty(line) || line.trim().startsWith("#"))
					continue;
				String[] aPair = line.split("\\s+");
				if (hpNewHosts.containsKey(aPair[1])) {
					if (!hpNewHosts.get(aPair[1]).equals(aPair[0])) {
						fos
								.write((hpNewHosts.get(aPair[1]) + "\t\t"
										+ aPair[1] + "\n").getBytes());
						mShowNotification = true;
					} else {
						fos.write((line + "\n").getBytes());
					}
					hpNewHosts.remove(aPair[1]);
				} else {
					fos.write((line + "\n").getBytes());
				}
			}
			for (String key : hpNewHosts.keySet()) {
				fos.write((hpNewHosts.get(key) + "\t\t" + key + "\n")
						.getBytes());
				mShowNotification = true;
			}
			fos.flush();
			fos.close();
			bis.close();
			if (HelperUtils.rootCMD("mv " + tmpFileName + " " + HostsPath) != 0) {
				Log.e(LOGTAG, "rename failed");
				notifyUpdateFinished(mContext, R.string.notification_update_failed);
				return;
			}
			Log.v(LOGTAG, "update finished");
			if (mShowNotification) {
				notifyUpdateFinished(mContext, R.string.notification_update_successfully);
			} else if (HelpMeActivity.mEnforceNotification) {
				HelpMeActivity.mEnforceNotification = false;
				notifyUpdateFinished(mContext, R.string.notification_no_update);
			}
		} catch (Exception e) {
			Log.e(LOGTAG, "error:" + e.getLocalizedMessage());
			notifyUpdateFinished(mContext, R.string.notification_update_failed);
		}
	}

	public static void notifyUpdateFinished(Context context, int msgId) {
		Intent intent = new Intent(context, CheckPopupAct.class);
		intent.putExtra(context.getString(R.string.checkpopup_extra), msgId);
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		context.startActivity(intent);
	}

}
