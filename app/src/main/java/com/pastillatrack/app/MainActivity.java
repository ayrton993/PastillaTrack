package com.pastillatrack.app;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;

public class MainActivity extends Activity {

    private WebView webView;
    public static final String CHANNEL_ID = "pastillatrack_alarm";
    public static final String PREFS      = "PastillaTrackPrefs";
    private static final int NOTIF_PERM   = 101;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        createNotificationChannel();
        askNotifPermission();

        webView = new WebView(this);
        setContentView(webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);

        webView.addJavascriptInterface(new Bridge(), "AndroidBridge");
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        webView.loadUrl("file:///android_asset/index.html");
    }

    private void askNotifPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    new String[]{"android.permission.POST_NOTIFICATIONS"}, NOTIF_PERM);
            }
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Uri soundUri = Uri.parse(
                "android.resource://" + getPackageName() + "/raw/alarm_sound");

            AudioAttributes audioAttr = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();

            NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID,
                "Alarma de Pastilla",
                NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Recordatorio diario para tomar tu pastilla");
            ch.enableVibration(true);
            ch.setVibrationPattern(new long[]{0, 400, 200, 400, 200, 600});
            ch.setSound(soundUri, audioAttr);
            ch.setShowBadge(true);
            ch.enableLights(true);
            ch.setLightColor(0xFF2DD4A0);

            NotificationManager nm = (NotificationManager)
                getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    public class Bridge {

        @JavascriptInterface
        public boolean hasNotifPermission() {
            if (Build.VERSION.SDK_INT >= 33) {
                return checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                    == PackageManager.PERMISSION_GRANTED;
            }
            return true;
        }

        @JavascriptInterface
        public void requestNotifPermission() { askNotifPermission(); }

        @JavascriptInterface
        public void setAlarm(String timeStr, String pillName, String notes) {
            try {
                String[] p = timeStr.split(":");
                int hour = Integer.parseInt(p[0]);
                int min  = Integer.parseInt(p[1]);

                SharedPreferences.Editor ed =
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit();
                ed.putString("alarm_time", timeStr);
                ed.putString("pill_name",  pillName);
                ed.putString("notes",      notes);
                ed.putBoolean("alarm_enabled", true);
                ed.apply();

                scheduleAlarm(hour, min, pillName, notes);
            } catch (Exception e) { e.printStackTrace(); }
        }

        @JavascriptInterface
        public void cancelAlarm() {
            try {
                AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
                PendingIntent pi = buildAlarmIntent("", "");
                if (am != null) am.cancel(pi);
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putBoolean("alarm_enabled", false).apply();
            } catch (Exception e) { e.printStackTrace(); }
        }

        @JavascriptInterface
        public boolean isAlarmEnabled() {
            return getSharedPreferences(PREFS, MODE_PRIVATE)
                .getBoolean("alarm_enabled", false);
        }

        @JavascriptInterface
        public String getAlarmTime() {
            return getSharedPreferences(PREFS, MODE_PRIVATE)
                .getString("alarm_time", "");
        }

        @JavascriptInterface
        public void vibrate() {
            try {
                android.os.Vibrator v = (android.os.Vibrator)
                    getSystemService(Context.VIBRATOR_SERVICE);
                if (v == null) return;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(android.os.VibrationEffect.createOneShot(
                        80, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
                } else { v.vibrate(80); }
            } catch (Exception e) { e.printStackTrace(); }
        }

        @JavascriptInterface
        public void testNotification(String pillName, String notes) {
            sendNotification(pillName, notes, 2001);
        }
    }

    public void scheduleAlarm(int hour, int min, String pillName, String notes) {
        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        PendingIntent pi = buildAlarmIntent(pillName, notes);

        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.set(java.util.Calendar.HOUR_OF_DAY, hour);
        cal.set(java.util.Calendar.MINUTE,      min);
        cal.set(java.util.Calendar.SECOND,      0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
            cal.add(java.util.Calendar.DAY_OF_YEAR, 1);
        }

        // setAlarmClock es el más confiable para alarmas exactas en Android moderno
        AlarmManager.AlarmClockInfo info =
            new AlarmManager.AlarmClockInfo(cal.getTimeInMillis(), pi);
        am.setAlarmClock(info, pi);
    }

    private PendingIntent buildAlarmIntent(String pillName, String notes) {
        Intent intent = new Intent(this, AlarmReceiver.class);
        intent.putExtra("pill_name", pillName);
        intent.putExtra("notes",     notes);
        return PendingIntent.getBroadcast(this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public void sendNotification(String pillName, String notes, int id) {
        try {
            Intent open = new Intent(this, MainActivity.class);
            open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pi = PendingIntent.getActivity(this, id, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            String title = "\uD83D\uDC8A \u00a1Hora de " + pillName + "!";
            String body  = (notes != null && !notes.isEmpty())
                ? notes + " — Tocá para registrarla"
                : "No te olvides de tomar tu pastilla \u2014 Tocá para abrir la app";

            androidx.core.app.NotificationCompat.Builder b =
                new androidx.core.app.NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_pill)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setStyle(new androidx.core.app.NotificationCompat
                        .BigTextStyle().bigText(body))
                    .setPriority(androidx.core.app.NotificationCompat.PRIORITY_MAX)
                    .setCategory(androidx.core.app.NotificationCompat.CATEGORY_ALARM)
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .setVibrate(new long[]{0, 400, 200, 400, 200, 600});

            // Sonido en Android < 8
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                Uri soundUri = Uri.parse(
                    "android.resource://" + getPackageName() + "/raw/alarm_sound");
                b.setSound(soundUri);
            }

            NotificationManager nm = (NotificationManager)
                getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(id, b.build());
        } catch (Exception e) { e.printStackTrace(); }
    }
}
