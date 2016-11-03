/*
 *           Copyright © 2016 Stanislav Petriakov
 *  Distributed under the Boost Software License, Version 1.0.
 *     (See accompanying file LICENSE_1_0.txt or copy at
 *           http://www.boost.org/LICENSE_1_0.txt)
 */

package com.beglory.zukfpm;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
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

public class BackgroundService extends Service {
    final static String FPC_HAL = "fpc_fingerprint_hal:D";
    final static String FILTER = "logcat -s " + FPC_HAL;
    final static String CLEAR = "logcat -c";

    protected Handler mHandler;
    protected long mLastCheck;
    private SimpleDateFormat mDateFormat;

    @Override
    public void onCreate() {
        super.onCreate();

        try {
            Process process = Runtime.getRuntime().exec("su");
            DataOutputStream dos = new DataOutputStream(process.getOutputStream());
            dos.writeBytes(CLEAR + "\n");
            dos.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }

        mHandler = new Handler(getMainLooper());
        mDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        new Thread(new Runnable() {
            public void run() {
                while (true) {
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
                                        BufferedReader reader2 = new BufferedReader(new InputStreamReader(activities.getInputStream()));
                                        DataOutputStream dos2 = new DataOutputStream(activities.getOutputStream());
                                        dos2.writeBytes("dumpsys activity activities" + "\n");
                                        dos2.flush();

                                        boolean isAtHome = false;
                                        String activity = Long.MAX_VALUE + "-" + Long.MIN_VALUE;
                                        List<String> list = new ArrayList<>();
                                        while ((line = reader2.readLine()) != null) {
                                            if (line.contains("intent=")) {
                                                String clazz = parseActivity(line);
                                                if (clazz == null)
                                                    continue;

                                                if (line.contains("android.intent.category.HOME"))
                                                    activity = clazz;
                                                else
                                                    list.add(clazz);
                                            }

                                            if (line.contains("mFocusedActivity")) {
                                                isAtHome = line.contains(activity);
                                                break;
                                            }
                                        }

                                        if (isAtHome)
                                            activity = list.get(0);
                                        else
                                            activity = list.get(1);

                                        dos2.writeBytes("am start -n " + activity + "\n");
                                        dos2.flush();
                                    }

                                    mLastCheck = time;
                                }
                            }
                        }

                        Thread.sleep(1250);
                    } catch (InterruptedException | IOException | ParseException ignored) {}
                }
            }
        }).start();

        return START_STICKY;
    }

    private String parseActivity(String intent) {
        String[] elements = intent.split(" ");
        for (String element : elements)
            if (element.startsWith("cmp="))
                return element.replace("cmp=", "").replace("}", "").replace(",", "");

        return null;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
