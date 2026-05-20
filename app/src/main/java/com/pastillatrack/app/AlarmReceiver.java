package com.pastillatrack.app;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.core.app.NotificationCompat;

public class AlarmReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String pillName = intent.getStringExtra("pill_name");
        String notes = intent.getStringExtra("notes");

        if (pillName == null || pillName.isEmpty()) {
            SharedPreferences prefs = context.getSharedPreferences(
                MainActivity.PREFS, Context.MODE_PRIVATE
            );
            pillName = prefs.getString("pill_name", "tu pastilla");
            notes = prefs.getString("notes", "");
        }

        // Vibración al recibir
        android.os.Vibrator v = (android.os.Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                v.vibrate(android.os.VibrationEffect.createWaveform(
                    new long[]{0, 300, 150, 300}, -1
                ));
            } else {
                v.vibrate(new long[]{0, 300, 150, 300}, -1);
            }
        }

        // Intent para abrir la app al tocar la notificación
        Intent openApp = new Intent(context, MainActivity.class);
        openApp.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingOpen = PendingIntent.getActivity(
            context, 0, openApp,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Intent para posponer 10 minutos
        Intent snoozeIntent = new Intent(context, SnoozeReceiver.class);
        snoozeIntent.putExtra("pill_name", pillName);
        snoozeIntent.putExtra("notes", notes);
        PendingIntent pendingSnooze = PendingIntent.getBroadcast(
            context, 1, snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String bodyText = notes != null && !notes.isEmpty()
            ? notes + " — Abrí la app para registrarla"
            : "No te olvides. Abrí la app para registrarla.";

        NotificationCompat.Builder builder = new NotificationCompat.Builder(
            context, MainActivity.CHANNEL_ID
        )
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("\uD83D\uDC8A \u00a1Hora de " + pillName + "!")
            .setContentText(bodyText)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(bodyText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingOpen)
            .setAutoCancel(true)
            .setVibrate(new long[]{0, 300, 150, 300})
            .addAction(
                android.R.drawable.ic_media_pause,
                "\u23f0 10 minutos más",
                pendingSnooze
            )
            .addAction(
                android.R.drawable.ic_menu_agenda,
                "\u2705 Abrir app",
                pendingOpen
            );

        NotificationManager nm = (NotificationManager)
            context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(1001, builder.build());
    }
}
