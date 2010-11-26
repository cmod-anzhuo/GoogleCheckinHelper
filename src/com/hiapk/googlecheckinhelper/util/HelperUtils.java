package com.hiapk.googlecheckinhelper.util;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Calendar;
import java.util.List;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import com.hiapk.googlecheckinhelper.CheckHostsReceiver;

public final class HelperUtils {

	private static final String LOGTAG = "HelperUtils";
	private static final String HiDialer_pName = "com.hiapk.dialer";
	private static final String SystemUpdater_pName = "com.hiapk.updater";
	private static final float Lowest_HiDialer_verName = 1.38f;
	private static final int Lowest_HiDialer_verCode = 1;
	
	/**
	 * 获取当日15：00的UTC
	 * 
	 * @return 当日15：00的UTC
	 */
	public static long getToday() {
		Calendar cal = Calendar.getInstance();
		cal.setTimeInMillis(System.currentTimeMillis());
		int year = cal.get(Calendar.YEAR);
		int month = cal.get(Calendar.MONTH);
		int day = cal.get(Calendar.DAY_OF_MONTH);
		cal.set(year, month, day, 15, 0);
		return cal.getTimeInMillis();
	}
	
	/**
	 * 检查当前集成HiDialer版本
	 * 
	 * @return 若HiDialer版本低于1.38返回true， 否则返回false
	 */
	public static boolean checkHardware(Context context) {
		Log.v(LOGTAG, "checkHardware");
		try {
			PackageManager aPackageManager = context.getPackageManager();
			List<PackageInfo> allPackages = aPackageManager
					.getInstalledPackages(0);
			for (PackageInfo pi : allPackages) {
				Log.v(LOGTAG, "found:" + pi.packageName);
				if (pi.packageName.equals(SystemUpdater_pName)) {
					Log.v(LOGTAG, "Hiapk system updater found");
					return false;
				}
				if (pi.packageName.equals(HiDialer_pName)
						&& ((pi.versionCode > Lowest_HiDialer_verCode) || (Float
								.valueOf(pi.versionName) >= Lowest_HiDialer_verName))) {
					Log.v(LOGTAG, "HiDialer found.");
					return false;
				}
			}
			return true;
		} catch (Exception e) {
			Log.e(LOGTAG, "Try to find hidialer failer."
					+ e.getLocalizedMessage());
			return true;
		}
	}
	
	/**
	 * 设置一个定时周期闹钟
	 * @param context 上下文进程
	 * @param enabled 启用或停止定时
	 * @param triggerTime	定时闹钟触发时刻
	 * @param interval	周期
	 */
	public static void setCheckAlarm(Context context, boolean enabled,
			long triggerTime, long interval) {
		AlarmManager aAlarmManager = (AlarmManager) context
				.getSystemService(Context.ALARM_SERVICE);
		Intent intent = new Intent(CheckHostsReceiver.CHECK_ACTION_NAME);
		PendingIntent sender = PendingIntent.getBroadcast(context, 0, intent,
				PendingIntent.FLAG_CANCEL_CURRENT);
		aAlarmManager.cancel(sender);
		if (enabled) {
			aAlarmManager.setInexactRepeating(AlarmManager.RTC, triggerTime,
					interval, sender);
		}
	}
	
	/**
	 * 以root权限执行命令
	 * 
	 * @param 需要执行的指令
	 * @return -1 执行失败； 0 执行正常
	 */
	public static synchronized int rootCMD(String cmd) {
		int result = -1;
		DataOutputStream os = null;
		InputStream err = null;
		InputStream out = null;
		try {
			Process process = Runtime.getRuntime().exec("su");
			err = process.getErrorStream();
			BufferedReader bre = new BufferedReader(new InputStreamReader(err),
					1024 * 8);

			out = process.getInputStream();

			os = new DataOutputStream(process.getOutputStream());

			os.writeBytes(cmd + "\n");
			os.flush();
			os.writeBytes("exit\n");
			os.flush();

			String resp;
			while ((resp = bre.readLine()) != null) {
				Log.v(LOGTAG, resp);
			}
			result = process.waitFor();
			if (result == 0)
				Log.v(LOGTAG, cmd + " exec success");
			else {
				Log.v(LOGTAG, cmd + " exec with result" + result);
			}
			os.close();
			process.destroy();
		} catch (IOException e) {
			Log.e(LOGTAG, e.getLocalizedMessage());
		} catch (InterruptedException e) {
			Log.e(LOGTAG, e.getLocalizedMessage());
		} finally {
			try {
				if (os != null) {
					os.close();
				}
			} catch (IOException e) {
			}

		}

		return result;
	}
}
