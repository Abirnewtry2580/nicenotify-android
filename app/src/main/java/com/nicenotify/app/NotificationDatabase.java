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
    private static final int DATABASE_VERSION = 2;
    private static final String TABLE = "notifications";
    private static volatile NotificationDatabase instance;
    private final Context appContext;

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
        appContext = context.getApplicationContext();
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
        if (oldVersion < 2) {
            ArrayList<ContentValues> migrations = new ArrayList<>();
            try (Cursor cursor = db.query(TABLE, new String[]{"_id", "app_name", "title", "body"},
                    null, null, null, null, null)) {
                while (cursor.moveToNext()) {
                    ContentValues values = new ContentValues();
                    values.put("_id", cursor.getLong(0));
                    values.put("app_name", cursor.getString(1));
                    values.put("title", cursor.getString(2));
                    values.put("body", cursor.getString(3));
                    migrations.add(values);
                }
            }
            for (ContentValues old : migrations) {
                long id = old.getAsLong("_id");
                ContentValues encrypted = new ContentValues();
                try {
                    encrypted.put("app_name", NotificationCrypto.encrypt(appContext, old.getAsString("app_name")));
                    encrypted.put("title", NotificationCrypto.encrypt(appContext, old.getAsString("title")));
                    encrypted.put("body", NotificationCrypto.encrypt(appContext, old.getAsString("body")));
                } catch (Exception e) {
                    throw new IllegalStateException("Could not encrypt existing notification history", e);
                }
                db.update(TABLE, encrypted, "_id=?", new String[]{String.valueOf(id)});
            }
        }
    }

    public synchronized void save(String sourceKey, String packageName, String appName, String title,
                                  String body, long postedAt, boolean clearable, String groupKey) {
        if (sourceKey == null || packageName == null) return;
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("source_key", sourceKey);
        values.put("package_name", packageName);
        try {
            values.put("app_name", NotificationCrypto.encrypt(appContext, appName == null ? packageName : appName));
            values.put("title", NotificationCrypto.encrypt(appContext, title == null ? "" : title));
            values.put("body", NotificationCrypto.encrypt(appContext, body == null ? "" : body));
        } catch (Exception e) {
            throw new IllegalStateException("Could not encrypt notification history", e);
        }
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
                        decrypt(cursor.getString(cursor.getColumnIndexOrThrow("app_name"))),
                        decrypt(cursor.getString(cursor.getColumnIndexOrThrow("title"))),
                        decrypt(cursor.getString(cursor.getColumnIndexOrThrow("body"))),
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

    private String decrypt(String value) {
        try { return NotificationCrypto.decrypt(appContext, value); }
        catch (Exception e) { return "[History unavailable: encryption key error]"; }
    }

    public synchronized void pruneOlderThanDays(int days) {
        if (days <= 0) return;
        long cutoff = System.currentTimeMillis() - days * 24L * 60L * 60L * 1000L;
        getWritableDatabase().delete(TABLE, "posted_at < ?", new String[]{String.valueOf(cutoff)});
    }

    public synchronized int count() {
        try (Cursor cursor = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM " + TABLE, null)) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }
}
