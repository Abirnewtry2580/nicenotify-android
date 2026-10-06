package com.nicenotify.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

public final class NotificationPreferences {
    private static final String FILE = "notification_preferences";
    private static final String EXCLUDED = "excluded_packages";

    private NotificationPreferences() {}

    public static synchronized boolean isExcluded(Context context, String packageName) {
        if (packageName == null) return false;
        return preferences(context).getStringSet(EXCLUDED, new HashSet<>()).contains(packageName);
    }

    public static synchronized void setExcluded(Context context, String packageName, boolean excluded) {
        if (packageName == null) return;
        Set<String> packages = new HashSet<>(preferences(context).getStringSet(EXCLUDED, new HashSet<>()));
        if (excluded) packages.add(packageName); else packages.remove(packageName);
        preferences(context).edit().putStringSet(EXCLUDED, packages).apply();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static int getRetentionDays(Context context) {
        return preferences(context).getInt("retention_days", 0);
    }

    public static void setRetentionDays(Context context, int days) {
        preferences(context).edit().putInt("retention_days", days).apply();
    }
}
