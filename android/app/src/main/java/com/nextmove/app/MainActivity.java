package com.nextmove.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;

public class MainActivity extends Activity {
    public static final String CHANNEL_ID = "nextmove_reminders";
    private static final int NOTIFICATION_PERMISSION_REQUEST = 1001;

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        WebView webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);

        // WebChromeClient is required for JavaScript alert()/confirm() dialogs.
        // Without it, destructive actions such as Delete Project can appear to do nothing.
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("NextMove")
                        .setMessage(message)
                        .setPositiveButton("OK", (dialog, which) -> result.confirm())
                        .setOnCancelListener(dialog -> result.cancel())
                        .show();
                return true;
            }

            @Override
            public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("NextMove")
                        .setMessage(message)
                        .setPositiveButton("Delete", (dialog, which) -> result.confirm())
                        .setNegativeButton("Cancel", (dialog, which) -> result.cancel())
                        .setOnCancelListener(dialog -> result.cancel())
                        .show();
                return true;
            }
        });

        webView.addJavascriptInterface(this, "Android");
        setContentView(webView);
        webView.loadUrl("file:///android_asset/index.html");

        try {
            createNotificationChannel();
        } catch (Throwable ignored) { }

        if (hasBackgroundSyncConfig()) {
            BackgroundSync.scheduleSafely(this);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (hasBackgroundSyncConfig()) {
            BackgroundSync.runSoonSafely(this);
        }
    }

    private boolean hasBackgroundSyncConfig() {
        SharedPreferences prefs = getSharedPreferences("nextmove_sync", MODE_PRIVATE);
        return !prefs.getString("url", "").isEmpty()
                && !prefs.getString("key", "").isEmpty()
                && !prefs.getString("session", "").isEmpty();
    }

    private void createNotificationChannel() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "NextMove reminders", NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("Upcoming and overdue task reminders");
        manager.createNotificationChannel(channel);
    }

    @JavascriptInterface
    public void requestNotifications() {
        try {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_REQUEST);
        } catch (Throwable ignored) { }
    }

    @JavascriptInterface
    public void schedule(String id, String title, String body, String first, String interval) {
        try {
            scheduleNative(Long.parseLong(first), Long.parseLong(interval), this, id, title, body);
        } catch (Throwable ignored) { }
    }

    @JavascriptInterface
    public void cancel(String id) {
        try { cancelNative(this, id); } catch (Throwable ignored) { }
    }

    @JavascriptInterface
    public void test() {
        try {
            ReminderReceiver.showNotification(this, "NextMove", "Test reminder. The nagging machinery is operational.", "nextmove-test");
        } catch (Throwable ignored) { }
    }

    @JavascriptInterface
    public void configureBackgroundSync(String url, String key, String sessionJson) {
        getSharedPreferences("nextmove_sync", MODE_PRIVATE).edit()
                .putString("url", url == null ? "" : url.trim())
                .putString("key", key == null ? "" : key.trim())
                .putString("session", sessionJson == null ? "" : sessionJson)
                .apply();
        if (hasBackgroundSyncConfig()) {
            BackgroundSync.scheduleSafely(this);
            BackgroundSync.runSoonSafely(this);
        }
    }

    @JavascriptInterface
    public void disableBackgroundSync() {
        getSharedPreferences("nextmove_sync", MODE_PRIVATE).edit().clear().apply();
        BackgroundSync.cancelSafely(this);
    }

    public static void scheduleNative(long triggerAtMillis, long intervalMillis, Context context,
                                      String id, String title, String body) {
        Intent intent = new Intent(context, ReminderReceiver.class)
                .putExtra("id", id)
                .putExtra("title", title)
                .putExtra("body", body)
                .putExtra("interval", intervalMillis);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, id.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
        }
    }

    public static void cancelNative(Context context, String id) {
        Intent intent = new Intent(context, ReminderReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, id.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) alarmManager.cancel(pendingIntent);
        pendingIntent.cancel();
    }
}
