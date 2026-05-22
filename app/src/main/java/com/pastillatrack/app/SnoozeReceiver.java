package com.pastillatrack.app;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class SnoozeReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        NotificationManager nm = (NotificationManager)
            context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(1001);

        String pillName = intent.getStringExtra("pill_name");
        String notes    = intent.getStringExtra("notes");

        // 10 minutos = 1/144 de día, calculamos hora/min
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.add(java.util.Calendar.MINUTE, 10);
        int hour = cal.get(java.util.Calendar.HOUR_OF_DAY);
        int min  = cal.get(java.util.Calendar.MINUTE);

        BootReceiver.scheduleNext(context, hour, min, pillName, notes, 0);
    }
}
