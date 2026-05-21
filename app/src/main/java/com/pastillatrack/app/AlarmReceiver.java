package com.pastillatrack.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import androidx.core.app.NotificationCompat;

public class AlarmReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences p = context.getSharedPreferences(
            MainActivity.PREFS, Context.MODE_PRIVATE);

        String pillName = intent.getStringExtra("pill_name");
        String notes    = intent.getStringExtra("notes");
        if (pillName == null || pillName.isEmpty())
            pillName = p.getString("pill_name", "tu pastilla");
        if (notes == null)
            notes = p.getString("notes", "");

        // Vibrar
        try {
            android.os.Vibrator v = (android.os.Vibrator)
                context.getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) {
                long[] pattern = {0, 400, 200, 400, 200, 600};
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(android.os.VibrationEffect.createWaveform(pattern, -1));
                } else { v.vibrate(pattern, -1); }
            }
        } catch (Exception ignored) {}

        // Intent abrir app
        Intent openApp = new Intent(context, MainActivity.class);
        openApp.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingOpen = PendingIntent.getActivity(context, 0, openApp,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Intent snooze
        Intent snoozeIntent = new Intent(context, SnoozeReceiver.class);
        snoozeIntent.putExtra("pill_name", pillName);
        snoozeIntent.putExtra("notes",     notes);
        PendingIntent pendingSnooze = PendingIntent.getBroadcast(context, 1, snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String body = notes.isEmpty()
            ? "No te olvides. Tocá para abrir la app."
            : notes + " \u2014 Tocá para abrir la app.";

        Uri soundUri = Uri.parse(
            "android.resource://" + context.getPackageName() + "/raw/alarm_sound");

        NotificationCompat.Builder builder = new NotificationCompat.Builder(
                context, MainActivity.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_pill)
            .setContentTitle("\uD83D\uDC8A \u00a1Hora de " + pillName + "!")
            .setContentText(body)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pendingOpen)
            .setAutoCancel(true)
            .setVibrate(new long[]{0, 400, 200, 400, 200, 600})
            .addAction(android.R.drawable.ic_media_pause,
                "\u23F0 10 min", pendingSnooze)
            .addAction(android.R.drawable.ic_menu_agenda,
                "\u2705 Abrir", pendingOpen);

        // Sonido en Android < 8 (en Android 8+ lo maneja el canal)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            builder.setSound(soundUri, android.media.AudioManager.STREAM_ALARM);
        }

        NotificationManager nm = (NotificationManager)
            context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(1001, builder.build());

        // Reprogramar para el día siguiente (setAlarmClock no repite)
        try {
            String timeStr = p.getString("alarm_time", "");
            if (!timeStr.isEmpty() && p.getBoolean("alarm_enabled", false)) {
                String[] parts = timeStr.split(":");
                int hour = Integer.parseInt(parts[0]);
                int min  = Integer.parseInt(parts[1]);
                Intent mainIntent = new Intent(context, MainActivity.class);
                // Usamos un broadcast para reprogramar sin abrir la activity
                Intent reschedule = new Intent(context, BootReceiver.class);
                reschedule.setAction("com.pastillatrack.RESCHEDULE");
                reschedule.putExtra("hour",      hour);
                reschedule.putExtra("min",       min);
                reschedule.putExtra("pill_name", pillName);
                reschedule.putExtra("notes",     notes);
                context.sendBroadcast(reschedule);
            }
        } catch (Exception e) { e.printStackTrace(); }
    }
}
