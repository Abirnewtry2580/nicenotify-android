package com.nicenotify.app;

import android.app.Notification;
import android.os.Bundle;
import android.os.Parcelable;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Receives system notifications after the user enables Notification Access. */
public final class NotificationCaptureService extends NotificationListenerService {
    private final ExecutorService writes = Executors.newSingleThreadExecutor();

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || sbn.getPackageName() == null || getPackageName().equals(sbn.getPackageName())) return;

        Notification notification = sbn.getNotification();
        Bundle extras = notification == null ? null : notification.extras;
        String title = text(extras, Notification.EXTRA_TITLE);
        if (TextUtils.isEmpty(title)) title = text(extras, Notification.EXTRA_CONVERSATION_TITLE);
        String body = text(extras, Notification.EXTRA_BIG_TEXT);
        if (TextUtils.isEmpty(body)) body = text(extras, Notification.EXTRA_TEXT);
        if (TextUtils.isEmpty(body)) body = messageLines(extras);
        if (TextUtils.isEmpty(body) && sbn.getNotification() != null && sbn.getNotification().tickerText != null) {
            body = sbn.getNotification().tickerText.toString();
        }
        if (TextUtils.isEmpty(title) && TextUtils.isEmpty(body)) return;

        String appName;
        try {
            appName = getPackageManager().getApplicationLabel(
                    getPackageManager().getApplicationInfo(sbn.getPackageName(), 0)).toString();
        } catch (Exception ignored) {
            appName = sbn.getPackageName();
        }
        final String savedTitle = limit(title, 500);
        final String savedBody = limit(body, 10000);
        final String sourceKey = sbn.getKey() == null
                ? sbn.getPackageName() + ":" + sbn.getId() + ":" + sbn.getPostTime()
                : sbn.getKey();
        final String packageName = sbn.getPackageName();
        final String savedAppName = limit(appName, 200);
        final long postedAt = sbn.getPostTime();
        final boolean clearable = sbn.isClearable();
        final String groupKey = sbn.getGroupKey();

        writes.execute(() -> NotificationDatabase.getInstance(getApplicationContext())
                .save(sourceKey, packageName, savedAppName, savedTitle, savedBody, postedAt, clearable, groupKey));
    }

    private static String text(Bundle extras, String key) {
        if (extras == null) return "";
        CharSequence value = extras.getCharSequence(key);
        return value == null ? "" : value.toString().trim();
    }

    private static String messageLines(Bundle extras) {
        if (extras == null) return "";
        Parcelable[] messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES);
        if (messages == null) return "";
        StringBuilder result = new StringBuilder();
        for (Parcelable item : messages) {
            if (!(item instanceof Bundle)) continue;
            Bundle message = (Bundle) item;
            CharSequence messageText = message.getCharSequence("text");
            if (messageText == null || messageText.length() == 0) continue;
            if (result.length() > 0) result.append('\n');
            CharSequence sender = message.getCharSequence("sender");
            if (sender != null && sender.length() > 0) result.append(sender).append(": ");
            result.append(messageText);
        }
        return result.toString();
    }

    private static String limit(String value, int max) {
        if (value == null) return "";
        String clean = value.trim();
        return clean.length() <= max ? clean : clean.substring(0, max);
    }

    @Override
    public void onDestroy() {
        writes.shutdown();
        super.onDestroy();
    }
}
