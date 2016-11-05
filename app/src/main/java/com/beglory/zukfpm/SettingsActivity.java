/*
 *           Copyright © 2016 Stanislav Petriakov
 *  Distributed under the Boost Software License, Version 1.0.
 *     (See accompanying file LICENSE_1_0.txt or copy at
 *           http://www.boost.org/LICENSE_1_0.txt)
 */

package com.beglory.zukfpm;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class SettingsActivity extends Activity implements View.OnClickListener {
    private TextView mServiceStatus;
    private Button mServiceButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean("autostart", true)) {
            Intent service = new Intent(this, BackgroundService.class);
            service.setAction(BackgroundService.ACTION_START);
            startService(service);
        }

        setContentView(R.layout.activity_settings);
        mServiceStatus = (TextView) findViewById(R.id.service_status);
        mServiceButton = (Button) findViewById(R.id.service_button);
        updateStatus();
    }

    private void updateStatus() {
        mServiceButton.setText(isServiceRunning() ? R.string.stop : R.string.start);
        mServiceStatus.setText(isServiceRunning() ? R.string.started : R.string.stopped);
        mServiceStatus.setTextColor(getColor(isServiceRunning() ? R.color.colorOk : R.color.colorAccent));
    }

    private boolean isServiceRunning() {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE))
            if (BackgroundService.class.getName().equals(service.service.getClassName()))
                return true;

        return false;
    }

    @Override
    public void onClick(View view) {
        Intent service = new Intent(this, BackgroundService.class);
        switch (view.getId()) {
            case R.id.service_button:
                String action = isServiceRunning() ? BackgroundService.ACTION_STOP : BackgroundService.ACTION_START;
                service.setAction(action);
                startService(service);
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        updateStatus();
                    }
                }, 1000);
                break;
            case R.id.save:
                if (isServiceRunning()) {
                    service.setAction(BackgroundService.ACTION_CHANGE);
                    startService(service);
                }
                break;
            case R.id.zuk:
                Intent zuk = new Intent(Intent.ACTION_VIEW);
                zuk.setData(Uri.parse("http://www.zukmobile.cc/tag/z2"));
                startActivity(zuk);
                break;
            case R.id.rr:
                Intent rr = new Intent(Intent.ACTION_VIEW);
                rr.setData(Uri.parse("http://4pda.ru/forum/index.php?showtopic=757674&st=1800#entry54421427"));
                startActivity(rr);
                break;
        }
    }
}
