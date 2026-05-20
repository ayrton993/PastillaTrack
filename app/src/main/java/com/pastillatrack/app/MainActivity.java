package com.pastillatrack.app;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;

import org.json.JSONObject;

public class MainActivity extends Activity {

    private WebView webView;
    public static final String CHANNEL_ID = "pastillatrack_channel";
    public static final String PREFS = "PastillaTrackPrefs";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        createNotificationChannel();

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setMediaPlaybackRequiresUserGesture(false);

        // Puente JS <-> Android nativo
        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");

        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());

        webView.loadUrl("file:///android_asset/index.html");
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Recordatorio de Pastilla",
                NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Recordatorio diario para tomar tu pastilla");
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0, 200, 100, 200});

            NotificationManager nm = getSystemService(NotificationManager.class);
            nm.createNotificationChannel(channel);
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    // Clase puente entre JavaScript y Android
    public class AndroidBridge {

        // Llamado desde JS para programar alarma
        @JavascriptInterface
        public void setAlarm(String timeStr, String pillName, String notes) {
            try {
                String[] parts = timeStr.split(":");
                int hour = Integer.parseInt(parts[0]);
                int minute = Integer.parseInt(parts[1]);

                // Guardar config
                SharedPreferences.Editor editor = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
                editor.putString("alarm_time", timeStr);
                editor.putString("pill_name", pillName);
                editor.putString("notes", notes);
                editor.putBoolean("alarm_enabled", true);
                editor.apply();

                AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
                Intent intent = new Intent(MainActivity.this, AlarmReceiver.class);
                intent.putExtra("pill_name", pillName);
                intent.putExtra("notes", notes);

                PendingIntent pi = PendingIntent.getBroadcast(
                    MainActivity.this, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                );

                // Calcular próxima alarma
                java.util.Calendar cal = java.util.Calendar.getInstance();
                cal.set(java.util.Calendar.HOUR_OF_DAY, hour);
                cal.set(java.util.Calendar.MINUTE, minute);
                cal.set(java.util.Calendar.SECOND, 0);
                cal.set(java.util.Calendar.MILLISECOND, 0);

                if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
                    cal.add(java.util.Calendar.DAY_OF_YEAR, 1);
                }

                // Repetir diariamente
                am.setRepeating(
                    AlarmManager.RTC_WAKEUP,
                    cal.getTimeInMillis(),
                    AlarmManager.INTERVAL_DAY,
                    pi
                );

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @JavascriptInterface
        public void cancelAlarm() {
            AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            Intent intent = new Intent(MainActivity.this, AlarmReceiver.class);
            PendingIntent pi = PendingIntent.getBroadcast(
                MainActivity.this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            am.cancel(pi);

            SharedPreferences.Editor editor = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
            editor.putBoolean("alarm_enabled", false);
            editor.apply();
        }

        @JavascriptInterface
        public boolean isAlarmEnabled() {
            return getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean("alarm_enabled", false);
        }

        @JavascriptInterface
        public String getAlarmTime() {
            return getSharedPreferences(PREFS, MODE_PRIVATE).getString("alarm_time", "");
        }

        // Vibrar el teléfono (para el pop del blíster)
        @JavascriptInterface
        public void vibrate() {
            android.os.Vibrator v = (android.os.Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(android.os.VibrationEffect.createOneShot(80, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    v.vibrate(80);
                }
            }
        }
    }
}
