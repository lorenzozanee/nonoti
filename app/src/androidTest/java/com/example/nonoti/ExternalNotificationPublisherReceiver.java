package com.example.nonoti;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class ExternalNotificationPublisherReceiver extends BroadcastReceiver {
    public static final String ACTION_POST = "com.example.nonoti.test.POST_NOTIFICATION";
    public static final String ACTION_CANCEL = "com.example.nonoti.test.CANCEL_NOTIFICATION";
    public static final String ACTION_CANCEL_ALL = "com.example.nonoti.test.CANCEL_ALL_NOTIFICATIONS";
    public static final String EXTRA_ID = "notification_id";
    public static final String EXTRA_TITLE = "notification_title";
    public static final String EXTRA_TEXT = "notification_text";
    public static final String EXTRA_CATEGORY = "notification_category";
    public static final String EXTRA_ONGOING = "notification_ongoing";
    public static final String EXTRA_GROUP = "notification_group";
    public static final String EXTRA_GROUP_SUMMARY = "notification_group_summary";
    private static final String CHANNEL_ID = "external-test";

    @Override
    public void onReceive(Context context, Intent intent) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        String action = intent.getAction();
        if (ACTION_POST.equals(action)) {
            manager.createNotificationChannel(
                    new NotificationChannel(
                            CHANNEL_ID,
                            "External test notifications",
                            NotificationManager.IMPORTANCE_HIGH));
            Notification.Builder builder = new Notification.Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_email)
                    .setContentTitle(intent.getStringExtra(EXTRA_TITLE))
                    .setContentText(intent.getStringExtra(EXTRA_TEXT))
                    .setCategory(intent.getStringExtra(EXTRA_CATEGORY))
                    .setOngoing(intent.getBooleanExtra(EXTRA_ONGOING, false))
                    .setAutoCancel(true)
                    ;
            String group = intent.getStringExtra(EXTRA_GROUP);
            if (group != null) {
                builder.setGroup(group)
                        .setGroupSummary(intent.getBooleanExtra(EXTRA_GROUP_SUMMARY, false));
            }
            Notification notification = builder.build();
            manager.notify(intent.getIntExtra(EXTRA_ID, 0), notification);
        } else if (ACTION_CANCEL.equals(action)) {
            manager.cancel(intent.getIntExtra(EXTRA_ID, 0));
        } else if (ACTION_CANCEL_ALL.equals(action)) {
            manager.cancelAll();
        }
    }
}
