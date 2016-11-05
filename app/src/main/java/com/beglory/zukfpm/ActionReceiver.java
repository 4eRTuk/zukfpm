/*
 *           Copyright © 2016 Stanislav Petriakov
 *  Distributed under the Boost Software License, Version 1.0.
 *     (See accompanying file LICENSE_1_0.txt or copy at
 *           http://www.boost.org/LICENSE_1_0.txt)
 */

package com.beglory.zukfpm;

import android.content.Context;
import android.content.Intent;
import android.support.v4.content.WakefulBroadcastReceiver;

public class ActionReceiver extends WakefulBroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Intent service = new Intent(context, BackgroundService.class);

        if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
            service.setAction(BackgroundService.ACTION_STOP);
        } else if (intent.getAction().equals(Intent.ACTION_SCREEN_ON) || intent.getAction().equals(Intent.ACTION_BOOT_COMPLETED)) {
            service.setAction(BackgroundService.ACTION_START);
        } else {
            service.setAction(intent.getAction());
        }

        if (intent.getExtras() != null)
            service.putExtras(intent.getExtras());

        startWakefulService(context, service);
    }
}
