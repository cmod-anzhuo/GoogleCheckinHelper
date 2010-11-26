package com.hiapk.googlecheckinhelper;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.hiapk.googlecheckinhelper.util.HelperUtils;
import com.hiapk.googlecheckinhelper.util.Log;

public class HelpMeActivity extends PreferenceActivity {

	private static final String LOGTAG = "HelpMeActivity";
	private static final int MENU_ABOUT = 1;
	public static boolean mEnforceNotification = false;
	private Preference mCheckHost;
	private Preference mRollBack;
	private static final String mAppFirstRunSpKeyName = "APP_FIRST_RUN";
	private SharedPreferences mSharedPreferences;
	private ListPreference mLpSetCheckInterval;
	private CheckBoxPreference mCbEnableAtuoCheck;
	private long mTriggerTime = 0L;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		mSharedPreferences = getSharedPreferences(getString(R.string.app_sharepreference_name), Context.MODE_WORLD_READABLE);

		mSharedPreferences.edit().putBoolean(getString(R.string.hiapkrom_sharepreference_name), true).commit();
		if (mSharedPreferences.getBoolean(mAppFirstRunSpKeyName, true)) {
			Log.v(LOGTAG, "app first run. set the default alarm");
			String defaultVal = getResources().getStringArray(R.array.entriesVal_alarm_interval)[1];

			long aTriggerTime = HelperUtils.getToday();
			aTriggerTime += Long.valueOf(getString(R.string.alarm_interval_default));
			HelperUtils.setCheckAlarm(this, true, aTriggerTime, Long.valueOf(defaultVal));
			mSharedPreferences.edit().putLong(getString(R.string.triggerTime_sharepreference_name), aTriggerTime).commit();
			mSharedPreferences.edit().putBoolean(mAppFirstRunSpKeyName, false).commit();
		}

		addPreferencesFromResource(R.layout.mainframe);

		mCbEnableAtuoCheck = (CheckBoxPreference) findPreference(getString(R.string.key_cbp_auto_check));
		mCbEnableAtuoCheck.setOnPreferenceChangeListener(mOnPreferenceChange);
		mLpSetCheckInterval = (ListPreference) findPreference(getString(R.string.key_lp_choseinterval));
		mLpSetCheckInterval.setOnPreferenceChangeListener(mOnPreferenceChange);
		mLpSetCheckInterval.setSummary(getStringByValue(mLpSetCheckInterval.getValue()));
		mCheckHost = (Preference) findPreference("check_host");
		mRollBack = (Preference) findPreference("roll_back");
		mCheckHost.setOnPreferenceClickListener(mOnClickListener);
		mRollBack.setOnPreferenceClickListener(mOnClickListener);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		menu.add(0, MENU_ABOUT, Menu.FIRST + 1, getString(R.string.menu_about)).setIcon(android.R.drawable.ic_menu_info_details);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	protected void onDestroy() {
		// TODO Auto-generated method stub
		super.onDestroy();
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case MENU_ABOUT:
			showAbout();
			break;
		default:
			break;
		}
		return super.onOptionsItemSelected(item);
	}

	public void showAbout() {
		View about = android.view.LayoutInflater.from(this).inflate(R.layout.hiapk_about, null);
		String version = "1.0"; // use versionName in manifest by default
		PackageManager pm = this.getPackageManager();
		try {
			version = pm.getPackageInfo(getPackageName(), 0).versionName;
		} catch (NameNotFoundException e) {
		}
		((TextView) about.findViewById(R.id.about)).setText(String.format(getString(R.string.about_content), version));
		new AlertDialog.Builder(this).setTitle(getString(R.string.about_title)).setView(about)
				.setPositiveButton(getString(android.R.string.ok), null).create().show();
	}

	private Preference.OnPreferenceClickListener mOnClickListener = new OnPreferenceClickListener() {

		public boolean onPreferenceClick(Preference preference) {
			// TODO Auto-generated method stub
			if (preference == mCheckHost) {
				Toast.makeText(HelpMeActivity.this, R.string.hint_check_now, Toast.LENGTH_LONG).show();
				mEnforceNotification = true;
				Intent intent = new Intent(CheckHostsReceiver.CHECK_ACTION_NAME);
				sendBroadcast(intent);
			} else if (preference == mRollBack) {
				rollBackHosts();
			}
			return false;
		}
	};

	private void rollBackHosts() {
		Log.v(LOGTAG, "start rollback");
		String lastUpdate = mSharedPreferences.getString("HOST_LAST_UPDATE", "");
		Log.v(LOGTAG, "lastUpdate:" + lastUpdate);
		if (TextUtils.isEmpty(lastUpdate)) {
			CheckHostsReceiver.notifyUpdateFinished(this, R.string.rollback_not_need);
			return;
		}
		try {
			if (HelperUtils.rootCMD(getString(R.string.cmd_remount_system)) != 0) {
				Log.e(LOGTAG, "remount system failer");
				CheckHostsReceiver.notifyUpdateFinished(this, R.string.rollback_fail);
				return;
			}
			String tmpFileName = CheckHostsReceiver.HostsPath + ".tmp";
			if (HelperUtils.rootCMD("echo '' > " + tmpFileName) != 0) {
				Log.e(LOGTAG, "rollback create tmp file failed");
				CheckHostsReceiver.notifyUpdateFinished(this, R.string.rollback_fail);
				return;
			}
			File mHostFile = new File(CheckHostsReceiver.HostsPath);
			File tmpFile = new File(tmpFileName);
			if (!tmpFile.exists()) {
				Log.e(LOGTAG, "rollback temp file not exists");
				CheckHostsReceiver.notifyUpdateFinished(this, R.string.rollback_fail);
				return;
			}
			BufferedInputStream bis = new BufferedInputStream(new FileInputStream(mHostFile));
			FileOutputStream fos = new FileOutputStream(tmpFile);
			byte[] buff = new byte[(int) mHostFile.length()];
			bis.read(buff);
			String[] origFileCont = new String(buff).split("\n");
			boolean notNeed = true;
			for (String line : origFileCont) {
				Log.v(LOGTAG, "read line: " + line);
				if (TextUtils.isEmpty(line) || line.trim().startsWith("#"))
					continue;
				String[] aPair = line.split("\\s+");
				if (!lastUpdate.contains(aPair[1])) {
					fos.write((line + "\n").getBytes());
				} else {
					notNeed = false;
				}
			}
			fos.flush();
			fos.close();
			bis.close();
			if (HelperUtils.rootCMD("mv " + tmpFileName + " " + CheckHostsReceiver.HostsPath) != 0 && !notNeed) {
				Log.e(LOGTAG, "rollback rename failed");
				CheckHostsReceiver.notifyUpdateFinished(this, R.string.rollback_fail);
				return;
			}
			if (notNeed)
				CheckHostsReceiver.notifyUpdateFinished(this, R.string.rollback_not_need);
			else
				CheckHostsReceiver.notifyUpdateFinished(this, R.string.rollback_success);
			Log.v(LOGTAG, "rollback finished");
		} catch (Exception e) {
			Log.e(LOGTAG, "error:" + e.getLocalizedMessage());
			CheckHostsReceiver.notifyUpdateFinished(this, R.string.rollback_fail);
		}
	}

	private Preference.OnPreferenceChangeListener mOnPreferenceChange = new OnPreferenceChangeListener() {

		public boolean onPreferenceChange(Preference preference, Object newValue) {
			// TODO Auto-generated method stub
			Log.v(LOGTAG, "onPreferenceChange ");

			if (preference.getKey().equals(getString(R.string.key_cbp_auto_check))) {
				Log.v(LOGTAG, "cbp newVal:" + newValue.toString() + " " + mLpSetCheckInterval.getValue());
				if (Boolean.valueOf(newValue.toString())) {
					long triggerT = HelperUtils.getToday() + Long.valueOf(mLpSetCheckInterval.getValue());
					HelperUtils.setCheckAlarm(HelpMeActivity.this, true, triggerT, Long.valueOf(mLpSetCheckInterval.getValue()));
					mSharedPreferences.edit().putLong(getString(R.string.triggerTime_sharepreference_name), triggerT).commit();
					mSharedPreferences.edit().putBoolean(getString(R.string.key_cbp_auto_check), true).commit();
				} else {
					HelperUtils.setCheckAlarm(HelpMeActivity.this, false, mTriggerTime, 0L);
					mSharedPreferences.edit().putBoolean(getString(R.string.key_cbp_auto_check), false).commit();
				}
			} else if (preference.getKey().equals(getString(R.string.key_lp_choseinterval))) {
				Log.v(LOGTAG, "lp newVal:" + newValue.toString());
				long triggerT = HelperUtils.getToday() + Long.valueOf(newValue.toString());
				HelperUtils.setCheckAlarm(HelpMeActivity.this, true, triggerT, Long.valueOf(newValue.toString()));
				preference.setSummary(getStringByValue(newValue.toString()));
				mSharedPreferences.edit().putLong(getString(R.string.triggerTime_sharepreference_name), triggerT).commit();
				mSharedPreferences.edit().putLong(getString(R.string.key_lp_choseinterval), Long.valueOf(newValue.toString())).commit();
			}
			return true;
		}
	};

	private CharSequence getStringByValue(String value) {
		CharSequence[] values = mLpSetCheckInterval.getEntryValues();
		CharSequence[] entries = mLpSetCheckInterval.getEntries();
		for (int i = 0; i < values.length; ++i) {
			if (values[i].equals(value))
				return entries[i];
		}
		return getString(R.string.lp_no_selected);
	}
}
