/*
 *           Copyright © 2016 Stanislav Petriakov
 *  Distributed under the Boost Software License, Version 1.0.
 *     (See accompanying file LICENSE_1_0.txt or copy at
 *           http://www.boost.org/LICENSE_1_0.txt)
 */

package com.beglory.zukfpm;

import android.app.SearchManager;
import android.app.Service;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.IBinder;
import android.os.PowerManager;
import android.preference.PreferenceManager;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BackgroundService extends Service {
    private final static String FPC_HAL = "fpc_fingerprint_hal:D";
    private final static String FILTER = "logcat -s " + FPC_HAL;
    private final static String CLEAR = "logcat -c";

    public final static String ACTION_START = "com.beglory.zukfpm.START";
    public final static String ACTION_PAUSE = "com.beglory.zukfpm.PAUSE";
    public final static String ACTION_STOP = "com.beglory.zukfpm.STOP";
    public final static String ACTION_CHANGE = "com.beglory.zukfpm.CHANGE";

    private final static String ACTION_NONE = "none";
    private final static String ACTION_HOME = "home";
    private final static String ACTION_LAST = "last";
    private final static String ACTION_ASSIST = "assist";
    private final static String ACTION_SEARCH = "search";

    private Thread mThread;
    private ActionReceiver mScreenReceiver = new ActionReceiver();
    private long mLastCheck;
    private SimpleDateFormat mDateFormat;
    private volatile boolean mIsRunning, mLongTap, mSwRight, mSwLeft;
    private volatile String mLauncherPackage, mLongTapAction, mSwRightAction, mSwLeftAction;

    @Override
    public void onCreate() {
        super.onCreate();

        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(mScreenReceiver, filter);

        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        ResolveInfo resolveInfo = getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
        mLauncherPackage = resolveInfo.activityInfo.packageName;

        readPreferences();

        mDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault());
    }

    private void readPreferences() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        mLongTapAction = preferences.getString(ZukPreferenceFragment.KEY_LONG_TAP, ACTION_HOME);
        mSwRightAction = preferences.getString(ZukPreferenceFragment.KEY_SWIPE_RIGHT, ACTION_LAST);
        mSwLeftAction = preferences.getString(ZukPreferenceFragment.KEY_SWIPE_LEFT, ACTION_NONE);
        mLongTap = !mLongTapAction.equals(ACTION_NONE);
        mSwRight = !mSwRightAction.equals(ACTION_NONE);
        mSwLeft = !mSwLeftAction.equals(ACTION_NONE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mScreenReceiver);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action;
        action = intent != null && intent.getAction() != null ? intent.getAction() : "";
        switch (action) {
            case ACTION_STOP:
                mIsRunning = false;
                mThread = null;
                stopSelf();
                break;
            case ACTION_PAUSE:
                mIsRunning = false;
                mThread = null;
                break;
            case ACTION_CHANGE:
                readPreferences();
                break;
            case ACTION_START:
            default:
                start();
                break;
        }

        return START_STICKY;
    }

    private void start() {
        if (mIsRunning && mThread != null)
            return;

        mIsRunning = true;
        mThread = new Thread(new Runnable() {
            public void run() {
                while (mIsRunning) {
                    try {
                        Process process = Runtime.getRuntime().exec("su");
                        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                        DataOutputStream dos = new DataOutputStream(process.getOutputStream());
                        dos.writeBytes(CLEAR + "\n");
                        dos.writeBytes(FILTER + "\n");
                        dos.flush();

                        String line;
                        while (mIsRunning && isScreenOn() && (line = reader.readLine()) != null) {
                            if (line.contains("nav_event_report")) {
                                long time = mDateFormat.parse("2016-" + line.substring(0, 18)).getTime();
                                if (time > mLastCheck) {
                                    int start = line.indexOf("Key: ");
                                    final String finalLine = line.substring(start + 5, start + 8);

                                    if (finalLine.equals("102") && mLongTap)
                                        selectAction(mLongTapAction);

                                    if (finalLine.equals("249") && mSwRight)
                                        selectAction(mSwRightAction);

                                    if (finalLine.equals("254") && mSwLeft)
                                        selectAction(mSwLeftAction);

                                    mLastCheck = time;
                                }
                            }
                        }

                        Thread.sleep(1250);
                    } catch (InterruptedException | IOException | ParseException ignored) {
                        ignored.printStackTrace();
                    }
                }
            }
        });
        mThread.start();
    }

    private void selectAction(String action) throws IOException {
        switch (action) {
            case ACTION_HOME:
                goToHome();
                break;
            case ACTION_LAST:
                switchLastApp();
                break;
            case ACTION_ASSIST:
                startAssist();
                break;
            case ACTION_SEARCH:
                startSearch();
                break;
        }
    }

    private void goToHome() {
        Intent startMain = new Intent(Intent.ACTION_MAIN);
        startMain.addCategory(Intent.CATEGORY_HOME);
        startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(startMain);
    }

    private void switchLastApp() throws IOException {
        Process activities = Runtime.getRuntime().exec("su");
        DataOutputStream dos = new DataOutputStream(activities.getOutputStream());
        dos.writeBytes("dumpsys activity recents\n");
        dos.flush();
        BufferedReader reader2 = new BufferedReader(new InputStreamReader(activities.getInputStream()));

        String line;
        List<LastApp> list = new ArrayList<>();
        while ((line = reader2.readLine()) != null) {
            if (line.contains("Recent #")) {
                if (line.contains(mLauncherPackage))
                    if (list.size() != 0)
                        continue;

                int taskId = parseTaskId(line);
                String affinity = parseAffinity(line);
                list.add(new LastApp(affinity, taskId));
            } else if (line.contains("realActivity")) {
                if (list.size() > 0) {
                    String affinity = list.get(list.size() - 1).getAffinity();
                    if (line.contains(affinity))
                        list.get(list.size() - 1).setActivity(parseActivity(line));
                }

                if (list.size() == 2)
                    break;
            } else if (line.contains("hasBeenVisible=false"))
                break;
        }

        if (list.size() > 1) {
            dos.writeBytes("am start " + list.get(1).getActivity() + "\n");
            dos.flush();
            dos.close();
        }
    }

    private int parseTaskId(String line) {
        String[] elements = line.split(" ");
        String id = elements[6];
        id = id.substring(1);
        return Integer.parseInt(id);
    }

    private String parseAffinity(String line) {
        String[] elements = line.split(" ");
        return elements[7].substring(2);
    }

    private String parseActivity(String line) {
        return line.replace("    realActivity=", "");
    }

    // https://github.com/CyanogenMod/android_frameworks_base/blob/19139443a777843efe4e0f02a77e939629bd249b
    // /services/core/java/com/android/server/policy/PhoneWindowManager.java#L3728
    private void startSearch() {
        Intent intent = new Intent(Intent.ACTION_SEARCH_LONG_PRESS);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
            if (searchManager != null)
                searchManager.stopSearch();
            startActivity(intent);
        } catch (ActivityNotFoundException ignored) { }
    }

    private void startAssist() throws IOException {
        Process activities = Runtime.getRuntime().exec("su");
        DataOutputStream dos = new DataOutputStream(activities.getOutputStream());
        dos.writeBytes("input keyevent 219\n");
        dos.flush();
        dos.close();
    }

    public boolean isScreenOn() {
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        return powerManager.isInteractive();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private class LastApp {
        private String mAffinity, mActivity;
        private int mTaskId;

        LastApp(String affinity, int taskId) {
            mAffinity = affinity;
            mTaskId = taskId;
        }

        void setActivity(String activity) {
            mActivity = activity;
        }

        String getAffinity() {
            return mAffinity;
        }

        String getActivity() {
            return mActivity;
        }
    }
}
