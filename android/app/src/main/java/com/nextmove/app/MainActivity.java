package com.nextmove.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;

public class MainActivity extends Activity {
    public static final String CHANNEL_ID = "nextmove_reminders";
    private static final int NOTIFICATION_PERMISSION_REQUEST = 1001;

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        createNotificationChannel();
        BackgroundSync.schedule(this);

        WebView webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        webView.addJavascriptInterface(this, "Android");
        setContentView(webView);
        webView.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onResume() {
        super.onResume();
        BackgroundSync.runSoon(this);
    }

    private void createNotificationChannel() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "NextMove reminders", NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("Upcoming and overdue task reminders");
        manager.createNotificationChannel(channel);
    }

    @JavascriptInterface
    public void requestNotifications() {
        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_REQUEST);
    }

    @JavascriptInterface
    public void schedule(String id, String title, String body, String first, String interval) {
        scheduleNative(Long.parseLong(first), Long.parseLong(interval), this, id, title, body);
    }

    @JavascriptInterface
    public void cancel(String id) { cancelNative(this, id); }

    @JavascriptInterface
    public void test() {
        ReminderReceiver.showNotification(this, "NextMove", "Test reminder. The nagging machinery is operational.", "nextmove-test");
    }

    @JavascriptInterface
    public void configureBackgroundSync(String url, String key, String sessionJson) {
        getSharedPreferences("nextmove_sync", MODE_PRIVATE).edit()
                .putString("url", url)
                .putString("key", key)
                .putString("session", sessionJson)
                .apply();
        BackgroundSync.schedule(this);
        BackgroundSync.runSoon(this);
    }

    @JavascriptInterface
    public void disableBackgroundSync() {
        getSharedPreferences("nextmove_sync", MODE_PRIVATE).edit().clear().apply();
        BackgroundSync.cancel(this);
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
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
    }

    public static void cancelNative(Context context, String id) {
        Intent intent = new Intent(context, ReminderReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, id.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.cancel(pendingIntent);
        pendingIntent.cancel();
    }
}
