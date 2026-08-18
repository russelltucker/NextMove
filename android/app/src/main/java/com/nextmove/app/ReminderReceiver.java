package com.nextmove.app;

import android.app.Notification;
import android.app.NotificationManager;
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
        showNotification(context, title, body, id);
        if (interval > 0L) scheduleNext(context, id, title, body, interval);
    }

    public static void scheduleNext(Context context, String id, String title, String body, long interval) {
        long next = System.currentTimeMillis() + interval;
        MainActivity.scheduleNative(next, interval, context, id, title, body);
    }

    public static void showNotification(Context context, String title, String body, String id) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (!manager.areNotificationsEnabled()) return;
        Notification notification = new Notification.Builder(context, MainActivity.CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .build();
        manager.notify(id.hashCode(), notification);
    }
}
