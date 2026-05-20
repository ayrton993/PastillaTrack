package com.pastillatrack.app;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class SnoozeReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // Cancelar notificación actual
        NotificationManager nm = (NotificationManager)
            context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(1001);

        // Programar nueva alarma en 10 minutos
        String pillName = intent.getStringExtra("pill_name");
        String notes = intent.getStringExtra("notes");

        Intent alarmIntent = new Intent(context, AlarmReceiver.class);
        alarmIntent.putExtra("pill_name", pillName);
        alarmIntent.putExtra("notes", notes);

        PendingIntent pi = PendingIntent.getBroadcast(
            context, 2, alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        am.setExact(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + 10 * 60 * 1000,
            pi
        );
    }
}
