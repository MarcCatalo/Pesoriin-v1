package org.tensorflow.lite.examples.detection.tracking;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.tensorflow.lite.examples.detection.DetectionLogEntry;

import java.util.ArrayList;
import java.util.List;

public class LogDBHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "LogDatabase";
    private static final int DATABASE_VERSION = 2;

    public LogDBHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // Create the table for logs
        db.execSQL("CREATE TABLE detection_logs (_id INTEGER PRIMARY KEY, item TEXT, confidence REAL, timestamp INTEGER, value REAL);");

        // Create a table for storing the total value
        db.execSQL("CREATE TABLE total (_id INTEGER PRIMARY KEY, total_value REAL);");

        // Initialize the total value to 0 in the 'total' table
        ContentValues initialValues = new ContentValues();
        initialValues.put("total_value", 0);
        db.insert("total", null, initialValues);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Handle database upgrades here
        db.execSQL("DROP TABLE IF EXISTS detection_logs");
        db.execSQL("DROP TABLE IF EXISTS total");
        onCreate(db);
    }

    public void updateTotalValue(float totalValue) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("total_value", totalValue);

        // Update the total value in the 'total' table (assuming there is only one row)
        db.update("total", values, "_id = 1", null);
    }

    public float getTotalValue() {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query("total", new String[]{"total_value"}, "_id = 1", null, null, null, null);

        float totalValue = 0;
        if (cursor.moveToFirst()) {
            totalValue = cursor.getFloat(cursor.getColumnIndex("total_value"));
        }

        cursor.close();
        return totalValue;
    }

    public void insertDetectionLog(DetectionLogEntry logEntry) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("item", logEntry.getItem());
        values.put("confidence", logEntry.getConfidence());
        values.put("timestamp", logEntry.getTimestamp());
        values.put("value", logEntry.getValue());

        // Insert the log into the 'detection_logs' table
        db.insert("detection_logs", null, values);
    }

    public List<DetectionLogEntry> getAllDetectionLogs() {
        List<DetectionLogEntry> logs = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query("detection_logs", null, null, null, null, null, null);

        while (cursor.moveToNext()) {
            long _id = cursor.getLong(cursor.getColumnIndex("_id"));
            String item = cursor.getString(cursor.getColumnIndex("item"));
            float confidence = cursor.getFloat(cursor.getColumnIndex("confidence"));
            long timestamp = cursor.getLong(cursor.getColumnIndex("timestamp"));
            float value = cursor.getFloat(cursor.getColumnIndex("value"));

            logs.add(new DetectionLogEntry(_id, item, confidence, timestamp, value));
        }

        cursor.close();
        return logs;
    }
}
