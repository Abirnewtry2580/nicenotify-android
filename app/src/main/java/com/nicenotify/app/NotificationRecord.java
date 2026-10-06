package com.nicenotify.app;

public final class NotificationRecord {
    public final long id;
    public final String sourceKey;
    public final String packageName;
    public final String appName;
    public final String title;
    public final String body;
    public final long postedAt;
    public final boolean read;
    public final boolean clearable;
    public final String groupKey;

    NotificationRecord(long id, String sourceKey, String packageName, String appName,
                       String title, String body, long postedAt, boolean read,
                       boolean clearable, String groupKey) {
        this.id = id;
        this.sourceKey = sourceKey;
        this.packageName = packageName;
        this.appName = appName;
        this.title = title;
        this.body = body;
        this.postedAt = postedAt;
        this.read = read;
        this.clearable = clearable;
        this.groupKey = groupKey;
    }
}
