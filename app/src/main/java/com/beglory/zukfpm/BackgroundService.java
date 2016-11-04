/*
 *           Copyright © 2016 Stanislav Petriakov
 *  Distributed under the Boost Software License, Version 1.0.
 *     (See accompanying file LICENSE_1_0.txt or copy at
 *           http://www.boost.org/LICENSE_1_0.txt)
 */

package com.beglory.zukfpm;

import android.app.ActivityManager;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.Nullable;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static android.app.ActivityManager.MOVE_TASK_NO_USER_ACTION;

public class BackgroundService extends Service {
    private final static String FPC_HAL = "fpc_fingerprint_hal:D";
    private final static String FILTER = "logcat -s " + FPC_HAL;
    private final static String CLEAR = "logcat -c";

    public final static String ACTION_START = "com.beglory.zukfpm.START";
    public final static String ACTION_STOP = "com.beglory.zukfpm.STOP";
    public final static String ACTION_CHANGE = "com.beglory.zukfpm.CHANGE";

    private long mLastCheck;
    private SimpleDateFormat mDateFormat;
    private Thread mThread;
    private volatile boolean mIsRunning;
    private ActivityManager mActivityManager;

    @Override
    public void onCreate() {
        super.onCreate();
        mActivityManager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        mDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action;
        action = intent != null && intent.getAction() != null ? intent.getAction() : "";
        switch (action) {
            case ACTION_START:
                start();
                break;
            case ACTION_STOP:
                mIsRunning = false;
                break;
            case ACTION_CHANGE:

                break;
            default:
                if (mThread == null)
                    start();
                break;
        }

        return START_STICKY;
    }

    private void start() {
        mIsRunning = true;
        mThread = new Thread(new Runnable() {
            public void run() {
                while (mIsRunning) {
                    try {
                        Process process = Runtime.getRuntime().exec("su");
                        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                        DataOutputStream dos = new DataOutputStream(process.getOutputStream());
                        dos.writeBytes(FILTER + "\n");
                        dos.flush();

                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (line.contains("nav_event_report")) {
                                long time = mDateFormat.parse("2016-" + line.substring(0, 18)).getTime();
                                if (time > mLastCheck) {
                                    int start = line.indexOf("Key: ");
                                    final String finalLine = line.substring(start + 5, start + 8);

                                    if (finalLine.equals("102")) {
                                        Intent startMain = new Intent(Intent.ACTION_MAIN);
                                        startMain.addCategory(Intent.CATEGORY_HOME);
                                        startMain.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                        startActivity(startMain);
                                    }

                                    if (finalLine.equals("249")) {
                                        Process activities = Runtime.getRuntime().exec("su");
                                        DataOutputStream dos2 = new DataOutputStream(activities.getOutputStream());
                                        dos2.writeBytes("dumpsys activity activities\n");
                                        dos2.flush();
                                        BufferedReader reader2 = new BufferedReader(new InputStreamReader(activities.getInputStream()));

                                        List<Integer> list = new ArrayList<>();
                                        while ((line = reader2.readLine()) != null) {
                                            if (line.contains("Run #")) {
                                                int id = parseTaskId(line);
                                                list.add(id);
                                            } else if (line.contains("mFocusedActivity"))
                                                break;
                                        }

                                        if (list.size() > 1)
                                            mActivityManager.moveTaskToFront(list.get(1), MOVE_TASK_NO_USER_ACTION);
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
                stopSelf();
            }
        });
        mThread.start();
    }

    private int parseTaskId(String line) {
        String[] elements = line.split(" ");
        String id = elements[elements.length - 1];
        id = id.substring(1, id.length() - 1);
        return Integer.parseInt(id);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
