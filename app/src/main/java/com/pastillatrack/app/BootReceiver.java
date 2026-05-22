package com.pastillatrack.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if ("com.pastillatrack.RESCHEDULE".equals(action)) {
            int hour     = intent.getIntExtra("hour", -1);
            int min      = intent.getIntExtra("min",  -1);
            String pill  = intent.getStringExtra("pill_name");
            String notes = intent.getStringExtra("notes");
            if (hour >= 0 && min >= 0)
                scheduleNext(context, hour, min, pill, notes, 1);
            return;
        }

        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)) return;

        SharedPreferences p = context.getSharedPreferences(
            MainActivity.PREFS, Context.MODE_PRIVATE);
        if (!p.getBoolean("alarm_enabled", false)) return;

        String timeStr = p.getString("alarm_time", "");
        if (timeStr.isEmpty()) return;

        try {
            String[] parts = timeStr.split(":");
            scheduleNext(context,
                Integer.parseInt(parts[0]),
                Integer.parseInt(parts[1]),
                p.getString("pill_name","tu pastilla"),
                p.getString("notes",""), 0);
        } catch (Exception e) { e.printStackTrace(); }
    }

    public static void scheduleNext(Context ctx, int hour, int min,
                                     String pillName, String notes, int dayOffset) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent i = new Intent(ctx, AlarmReceiver.class);
        i.putExtra("pill_name", pillName);
        i.putExtra("notes",     notes);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, 0, i,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);

        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.set(java.util.Calendar.HOUR_OF_DAY, hour);
        cal.set(java.util.Calendar.MINUTE,      min);
        cal.set(java.util.Calendar.SECOND,      0);
        cal.set(java.util.Calendar.MILLISECOND, 0);

        // Si dayOffset > 0 siempre avanzar (para re-schedule post-disparo)
        if (dayOffset > 0) {
            cal.add(java.util.Calendar.DAY_OF_YEAR, dayOffset);
        } else if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
            cal.add(java.util.Calendar.DAY_OF_YEAR, 1);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (am.canScheduleExactAlarms()) {
                am.setAlarmClock(
                    new AlarmManager.AlarmClockInfo(cal.getTimeInMillis(), pi), pi);
            } else {
                am.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
        }
    }
}
