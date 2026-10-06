package com.nicenotify.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public final class NotificationDatabase extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "notification_history.db";
    private static final int DATABASE_VERSION = 1;
    private static final String TABLE = "notifications";
    private static volatile NotificationDatabase instance;

    public static NotificationDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (NotificationDatabase.class) {
                if (instance == null) instance = new NotificationDatabase(context.getApplicationContext());
            }
        }
        return instance;
    }

    private NotificationDatabase(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "source_key TEXT NOT NULL UNIQUE," +
                "package_name TEXT NOT NULL," +
                "app_name TEXT NOT NULL," +
                "title TEXT NOT NULL," +
                "body TEXT NOT NULL," +
                "posted_at INTEGER NOT NULL," +
                "is_read INTEGER NOT NULL DEFAULT 0," +
                "clearable INTEGER NOT NULL DEFAULT 0," +
                "group_key TEXT)");
        db.execSQL("CREATE INDEX idx_notifications_posted_at ON " + TABLE + "(posted_at)");
        db.execSQL("CREATE INDEX idx_notifications_package ON " + TABLE + "(package_name)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Schema changes will be migrated here to preserve notification history.
    }

    public synchronized void save(String sourceKey, String packageName, String appName, String title,
                                  String body, long postedAt, boolean clearable, String groupKey) {
        if (sourceKey == null || packageName == null) return;
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("source_key", sourceKey);
        values.put("package_name", packageName);
        values.put("app_name", appName == null ? packageName : appName);
        values.put("title", title == null ? "" : title);
        values.put("body", body == null ? "" : body);
        values.put("posted_at", postedAt);
        values.put("clearable", clearable ? 1 : 0);
        values.put("group_key", groupKey);

        int changed = db.update(TABLE, values, "source_key=?", new String[]{sourceKey});
        if (changed == 0) {
            values.put("is_read", 0);
            db.insertWithOnConflict(TABLE, null, values, SQLiteDatabase.CONFLICT_IGNORE);
        }
    }

    public synchronized List<NotificationRecord> recent(int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, 1000));
        ArrayList<NotificationRecord> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query(TABLE, null, null, null, null, null,
                "posted_at DESC, _id DESC", String.valueOf(limit))) {
            while (cursor.moveToNext()) {
                result.add(new NotificationRecord(
                        cursor.getLong(cursor.getColumnIndexOrThrow("_id")),
                        cursor.getString(cursor.getColumnIndexOrThrow("source_key")),
                        cursor.getString(cursor.getColumnIndexOrThrow("package_name")),
                        cursor.getString(cursor.getColumnIndexOrThrow("app_name")),
                        cursor.getString(cursor.getColumnIndexOrThrow("title")),
                        cursor.getString(cursor.getColumnIndexOrThrow("body")),
                        cursor.getLong(cursor.getColumnIndexOrThrow("posted_at")),
                        cursor.getInt(cursor.getColumnIndexOrThrow("is_read")) != 0,
                        cursor.getInt(cursor.getColumnIndexOrThrow("clearable")) != 0,
                        cursor.getString(cursor.getColumnIndexOrThrow("group_key"))));
            }
        }
        return result;
    }

    public synchronized void markRead(long id) {
        ContentValues values = new ContentValues();
        values.put("is_read", 1);
        getWritableDatabase().update(TABLE, values, "_id=?", new String[]{String.valueOf(id)});
    }

    public synchronized void delete(long id) {
        getWritableDatabase().delete(TABLE, "_id=?", new String[]{String.valueOf(id)});
    }

    public synchronized void clearAll() {
        getWritableDatabase().delete(TABLE, null, null);
    }

    public synchronized int count() {
        try (Cursor cursor = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM " + TABLE, null)) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }
}
