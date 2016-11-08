/*
 *           Copyright © 2016 Stanislav Petriakov
 *  Distributed under the Boost Software License, Version 1.0.
 *     (See accompanying file LICENSE_1_0.txt or copy at
 *           http://www.boost.org/LICENSE_1_0.txt)
 */

package com.beglory.zukfpm;

import android.app.Service;
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

    private Thread mThread;
    private ActionReceiver mScreenReceiver = new ActionReceiver();
    private long mLastCheck;
    private SimpleDateFormat mDateFormat;
    private volatile boolean mIsRunning, mHome, mLast;
    private volatile String mLauncherPackage, mHomeKey, mLastKey;

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
        mHome = preferences.getBoolean("action_home", true);
        mLast = preferences.getBoolean("action_last", true);
        mHomeKey = preferences.getString("home_key", "102");
        mLastKey = preferences.getString("last_key", "249");
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

                                    if (finalLine.equals(mHomeKey) && mHome) {
                                        Intent startMain = new Intent(Intent.ACTION_MAIN);
                                        startMain.addCategory(Intent.CATEGORY_HOME);
                                        startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                        startActivity(startMain);
                                    }

                                    if (finalLine.equals(mLastKey) && mLast) {
                                        Process activities = Runtime.getRuntime().exec("su");
                                        DataOutputStream dos2 = new DataOutputStream(activities.getOutputStream());
                                        dos2.writeBytes("dumpsys activity recents\n");
                                        dos2.flush();
                                        BufferedReader reader2 = new BufferedReader(new InputStreamReader(activities.getInputStream()));

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
                                            dos2.writeBytes("am start " + list.get(1).getActivity() + "\n");
                                            dos2.flush();
                                            dos2.close();
                                        }
                                    }

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
