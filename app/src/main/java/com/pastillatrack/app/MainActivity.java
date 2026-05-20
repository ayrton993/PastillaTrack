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
import android.os.Build;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;

public class MainActivity extends Activity {

    private WebView webView;
    public static final String CHANNEL_ID = "pastillatrack_channel";
    public static final String PREFS = "PastillaTrackPrefs";
    private static final int NOTIF_PERM_CODE = 101;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        createNotificationChannel();
        requestNotifPermissionIfNeeded();

        webView = new WebView(this);
        setContentView(webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);

        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        webView.loadUrl("file:///android_asset/index.html");
    }

    private void requestNotifPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    new String[]{"android.permission.POST_NOTIFICATIONS"},
                    NOTIF_PERM_CODE
                );
            }
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "Recordatorio de Pastilla",
                NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Recordatorio diario para tomar tu pastilla");
            ch.enableVibration(true);
            ch.setVibrationPattern(new long[]{0, 300, 150, 300});
            ch.setShowBadge(true);
            NotificationManager nm = (NotificationManager)
                getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    public class AndroidBridge {

        @JavascriptInterface
        public boolean hasNotifPermission() {
            if (Build.VERSION.SDK_INT >= 33) {
                return checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                    == PackageManager.PERMISSION_GRANTED;
            }
            return true;
        }

        @JavascriptInterface
        public void requestNotifPermission() {
            requestNotifPermissionIfNeeded();
        }

        @JavascriptInterface
        public void setAlarm(String timeStr, String pillName, String notes) {
            try {
                String[] parts = timeStr.split(":");
                int hour   = Integer.parseInt(parts[0]);
                int minute = Integer.parseInt(parts[1]);

                SharedPreferences.Editor ed =
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit();
                ed.putString("alarm_time", timeStr);
                ed.putString("pill_name", pillName);
                ed.putString("notes", notes);
                ed.putBoolean("alarm_enabled", true);
                ed.apply();

                AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
                Intent intent  = new Intent(MainActivity.this, AlarmReceiver.class);
                intent.putExtra("pill_name", pillName);
                intent.putExtra("notes", notes);

                PendingIntent pi = PendingIntent.getBroadcast(
                    MainActivity.this, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

                java.util.Calendar cal = java.util.Calendar.getInstance();
                cal.set(java.util.Calendar.HOUR_OF_DAY, hour);
                cal.set(java.util.Calendar.MINUTE, minute);
                cal.set(java.util.Calendar.SECOND, 0);
                cal.set(java.util.Calendar.MILLISECOND, 0);
                if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
                    cal.add(java.util.Calendar.DAY_OF_YEAR, 1);
                }

                if (am != null) {
                    am.setRepeating(AlarmManager.RTC_WAKEUP,
                        cal.getTimeInMillis(), AlarmManager.INTERVAL_DAY, pi);
                }
            } catch (Exception e) { e.printStackTrace(); }
        }

        @JavascriptInterface
        public void cancelAlarm() {
            try {
                AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
                Intent intent = new Intent(MainActivity.this, AlarmReceiver.class);
                PendingIntent pi = PendingIntent.getBroadcast(
                    MainActivity.this, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                if (am != null) am.cancel(pi);
                SharedPreferences.Editor ed =
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit();
                ed.putBoolean("alarm_enabled", false);
                ed.apply();
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
                } else {
                    v.vibrate(80);
                }
            } catch (Exception e) { e.printStackTrace(); }
        }

        // Lanzar notificación de prueba desde JS
        @JavascriptInterface
        public void testNotification(String pillName, String notes) {
            try {
                Intent openApp = new Intent(MainActivity.this, MainActivity.class);
                openApp.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                PendingIntent pi = PendingIntent.getActivity(
                    MainActivity.this, 99, openApp,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

                String title = "\uD83D\uDC8A \u00a1Hora de " + pillName + "!";
                String body  = (notes != null && !notes.isEmpty())
                    ? notes + " — Tocá para registrarla"
                    : "No te olvides de tomar tu pastilla";

                androidx.core.app.NotificationCompat.Builder builder =
                    new androidx.core.app.NotificationCompat.Builder(
                        MainActivity.this, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_pill)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                    .setContentIntent(pi)
                    .setAutoCancel(true);

                NotificationManager nm = (NotificationManager)
                    getSystemService(Context.NOTIFICATION_SERVICE);
                if (nm != null) nm.notify(2001, builder.build());
            } catch (Exception e) { e.printStackTrace(); }
        }
    }
}
