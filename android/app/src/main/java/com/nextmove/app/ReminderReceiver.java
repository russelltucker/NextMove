package com.nextmove.app;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String id = intent.getStringExtra("id");
        String title = intent.getStringExtra("title");
        String body = intent.getStringExtra("body");
        long interval = intent.getLongExtra("interval", 0L);
        showNotification(context, title, body, id, "task", id);
        if (interval > 0L) scheduleNext(context, id, title, body, interval);
    }

    public static void scheduleNext(Context context, String id, String title, String body, long interval) {
        long next = System.currentTimeMillis() + interval;
        MainActivity.scheduleNative(next, interval, context, id, title, body);
    }

    public static void showNotification(Context context, String title, String body, String id) {
        showNotification(context, title, body, id, "", "");
    }

    public static void showNotification(Context context, String title, String body, String id,
                                        String destination, String targetId) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null || !manager.areNotificationsEnabled()) return;

        Intent openIntent = new Intent(context, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (destination != null && !destination.isEmpty()) {
            openIntent.putExtra(MainActivity.EXTRA_DESTINATION, destination);
            openIntent.putExtra(MainActivity.EXTRA_TARGET_ID, targetId == null ? "" : targetId);
        }

        PendingIntent contentIntent = PendingIntent.getActivity(
                context,
                (id + "-open").hashCode(),
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new Notification.Builder(context, MainActivity.CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(title)
                .setContentText(body)
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .build();
        manager.notify(id.hashCode(), notification);
    }
}
