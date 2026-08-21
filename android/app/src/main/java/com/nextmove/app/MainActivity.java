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
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {
    public static final String CHANNEL_ID = "nextmove_reminders";
    public static final String EXTRA_DESTINATION = "nextmove_destination";
    public static final String EXTRA_TARGET_ID = "nextmove_target_id";
    private static final int NOTIFICATION_PERMISSION_REQUEST = 1001;
    private WebView webView;
    private boolean webReady = false;
    private String pendingDestination = "";
    private String pendingTargetId = "";

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);

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

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                webReady = true;
                deliverPendingNavigation();
            }
        });

        webView.addJavascriptInterface(this, "Android");
        setContentView(webView);
        captureNavigation(getIntent());
        webView.loadUrl("file:///android_asset/index.html");

        try { createNotificationChannel(); } catch (Throwable ignored) { }

        if (hasBackgroundSyncConfig()) BackgroundSync.scheduleSafely(this);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        captureNavigation(intent);
        deliverPendingNavigation();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (hasBackgroundSyncConfig()) BackgroundSync.runSoonSafely(this);
        notifyWebPermissionState();
    }

    private void captureNavigation(Intent intent) {
        if (intent == null) return;
        String destination = intent.getStringExtra(EXTRA_DESTINATION);
        String targetId = intent.getStringExtra(EXTRA_TARGET_ID);
        if (destination == null || destination.isEmpty() || targetId == null || targetId.isEmpty()) return;
        pendingDestination = destination;
        pendingTargetId = targetId;
        intent.removeExtra(EXTRA_DESTINATION);
        intent.removeExtra(EXTRA_TARGET_ID);
    }

    private void deliverPendingNavigation() {
        if (!webReady || webView == null || pendingDestination.isEmpty() || pendingTargetId.isEmpty()) return;

        final String destination = pendingDestination;
        final String targetId = pendingTargetId;
        pendingDestination = "";
        pendingTargetId = "";

        String escapedId = jsString(targetId);
        String script;
        if ("project".equals(destination)) {
            script = "(()=>{const id='" + escapedId + "';"
                    + "if(typeof openProject==='function'&&typeof data!=='undefined'&&Array.isArray(data.projects)&&data.projects.some(p=>p.id===id))openProject(id);"
                    + "})()";
        } else if ("task".equals(destination)) {
            script = "(()=>{const id='" + escapedId + "';"
                    + "if(typeof openTask==='function'&&typeof data!=='undefined'&&Array.isArray(data.tasks)&&data.tasks.some(t=>t.id===id))openTask(id);"
                    + "})()";
        } else {
            return;
        }
        webView.post(() -> webView.evaluateJavascript(script, null));
    }

    private static String jsString(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\u2028", "\\u2028")
                .replace("\u2029", "\\u2029");
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
    public boolean notificationsAllowed() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true;
        return checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    @JavascriptInterface
    public void requestNotifications() {
        runOnUiThread(() -> {
            try {
                if (notificationsAllowed()) {
                    notifyWebPermissionState();
                    return;
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_REQUEST);
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST) notifyWebPermissionState();
    }

    private void notifyWebPermissionState() {
        if (webView == null) return;
        final boolean allowed = notificationsAllowed();
        webView.post(() -> webView.evaluateJavascript(
                "window.nextMoveNotificationPermissionResult && window.nextMoveNotificationPermissionResult(" + allowed + ");",
                null));
    }

    @JavascriptInterface
    public void schedule(String id, String title, String body, String first, String interval) {
        try { scheduleNative(Long.parseLong(first), Long.parseLong(interval), this, id, title, body); }
        catch (Throwable ignored) { }
    }

    @JavascriptInterface
    public void cancel(String id) {
        try { cancelNative(this, id); } catch (Throwable ignored) { }
    }

    @JavascriptInterface
    public void test() {
        try { ReminderReceiver.showNotification(this, "NextMove", "Test reminder. The nagging machinery is operational.", "nextmove-test"); }
        catch (Throwable ignored) { }
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
        if (alarmManager != null) alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
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
