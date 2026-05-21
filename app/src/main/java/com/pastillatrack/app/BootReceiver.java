package com.pastillatrack.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        // Reprogramar desde AlarmReceiver después de dispararse
        if ("com.pastillatrack.RESCHEDULE".equals(action)) {
            int hour      = intent.getIntExtra("hour", -1);
            int min       = intent.getIntExtra("min",  -1);
            String pill   = intent.getStringExtra("pill_name");
            String notes  = intent.getStringExtra("notes");
            if (hour >= 0 && min >= 0) {
                scheduleNext(context, hour, min, pill, notes);
            }
            return;
        }

        // Reprogramar al reiniciar el teléfono
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)) return;

        SharedPreferences p = context.getSharedPreferences(
            MainActivity.PREFS, Context.MODE_PRIVATE);
        if (!p.getBoolean("alarm_enabled", false)) return;

        String timeStr = p.getString("alarm_time", "");
        if (timeStr.isEmpty()) return;

        try {
            String[] parts = timeStr.split(":");
            int hour = Integer.parseInt(parts[0]);
            int min  = Integer.parseInt(parts[1]);
            scheduleNext(context, hour, min,
                p.getString("pill_name", "tu pastilla"),
                p.getString("notes", ""));
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void scheduleNext(Context ctx, int hour, int min,
                               String pillName, String notes) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent intent = new Intent(ctx, AlarmReceiver.class);
        intent.putExtra("pill_name", pillName);
        intent.putExtra("notes",     notes);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.set(java.util.Calendar.HOUR_OF_DAY, hour);
        cal.set(java.util.Calendar.MINUTE,      min);
        cal.set(java.util.Calendar.SECOND,      0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        // Siempre programar para mañana (ya sonó hoy)
        cal.add(java.util.Calendar.DAY_OF_YEAR, 1);

        AlarmManager.AlarmClockInfo info =
            new AlarmManager.AlarmClockInfo(cal.getTimeInMillis(), pi);
        am.setAlarmClock(info, pi);
    }
}
