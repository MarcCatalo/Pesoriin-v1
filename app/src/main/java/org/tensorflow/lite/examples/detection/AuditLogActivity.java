package org.tensorflow.lite.examples.detection;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.tensorflow.lite.examples.detection.tracking.LogDBHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AuditLogActivity extends Activity {
    private TextView totalCurrencyTextView;
    private TextToSpeech auditLogScreen, instruction, updatedTotalValue;
    private boolean isTTSInstructionInitialized = false;

    private static final int SPEECH_REQUEST_CODE = 123;

    private float totalCurrencyValue;

    private List<DetectionLogEntry> detectionLogs = new ArrayList<>();
    private ArrayAdapter<DetectionLogEntry> logsAdapter;

    private LogDBHelper dbHelper;
    private SQLiteDatabase database;

    private float x1, x2, y1, y2;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.audit_log_activity);

        totalCurrencyTextView = findViewById(R.id.totalCurrencyValueText);

        ListView listView = findViewById(R.id.list_view_logs);

        dbHelper = new LogDBHelper(this);
        database = dbHelper.getWritableDatabase(); // Use getWritableDatabase to ensure write access

        // Retrieve the detectionLogs from the SQLite database
        detectionLogs = getLogsFromDatabase();

        // Get the total currency value directly from the database
        totalCurrencyValue = dbHelper.getTotalValue();

        // Update the TextView with the total currency value
        updateTotalCurrencyTextView(totalCurrencyValue);

        // Initialize TextToSpeech
        auditLogScreen = new TextToSpeech(getApplicationContext(), status -> {
            if (status != TextToSpeech.ERROR) {
                auditLogScreen.setLanguage(Locale.US);
                String text = "You are now in the Audit Log. Your total is " + totalCurrencyValue + "Pesos";
                auditLogScreen.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
            }
        });

        if (!isTTSInstructionInitialized) {
            initializeTTSInstruction();
            isTTSInstructionInitialized = true;
        }


        // Use an ArrayAdapter to show the logs in the ListView
        logsAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, detectionLogs);
        listView.setAdapter(logsAdapter);

        // Add a button or any UI element to trigger voice recognition
        Button voiceCommandButton = findViewById(R.id.voiceCommandButton);
        voiceCommandButton.setOnClickListener(v -> startVoiceRecognition());
    }

    @Override
    public boolean onTouchEvent(MotionEvent touchEvent) {
        switch (touchEvent.getAction()) {
            case MotionEvent.ACTION_DOWN:
                x1 = touchEvent.getX();
                y1 = touchEvent.getY();
                break;
            case MotionEvent.ACTION_UP:
                x2 = touchEvent.getX();
                y2 = touchEvent.getY();
                if (x2 > x1) {
                    Intent i = new Intent(AuditLogActivity.this, DetectorActivity.class);
                    startActivity(i);
                }
                break;
        }
        return super.onTouchEvent(touchEvent);
    }

    private void initializeTTSInstruction() {
        instruction = new TextToSpeech(getApplicationContext(), status -> {
            if (status != TextToSpeech.ERROR) {
                instruction.setLanguage(Locale.US);
                String text = "To subtract an amount, press on the screen and say subtract followed by the desired amount.";
                instruction.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
            }
        });
    }

    private List<DetectionLogEntry> getLogsFromDatabase() {
        List<DetectionLogEntry> logs = new ArrayList<>();
        Cursor cursor = database.query("detection_logs", null, null, null, null, null, null);


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

    private float calculateTotalCurrencyValue(List<DetectionLogEntry> logs) {
        float totalValue = 0;
        for (DetectionLogEntry log : logs) {
            totalValue += log.getValue();
        }
        return totalValue;
    }

    private void startVoiceRecognition() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Say 'subtract [amount]' to update the total.");

        try {
            startActivityForResult(intent, SPEECH_REQUEST_CODE);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Speech recognition is not available on this device.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == SPEECH_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            ArrayList<String> matches = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (matches != null && !matches.isEmpty()) {
                String spokenText = matches.get(0);

                if (spokenText.toLowerCase().contains("subtract")) {
                    String[] words = spokenText.split(" ");
                    for (int i = 0; i < words.length; i++) {
                        if (words[i].equals("subtract") && i + 1 < words.length) {
                            try {

                                String amountString = words[i + 1].replace(",", "");
                                float amount = Float.parseFloat(amountString);
                                totalCurrencyValue -= amount;

                                // Update the total value in the database
                                updateTotalValueInDatabase(totalCurrencyValue);

                                // Add a new log entry for the subtraction
                                DetectionLogEntry subtractionLog = new DetectionLogEntry(
                                        System.currentTimeMillis(), "Subtraction", 0.0f, // Set confidence to 0 for subtraction
                                        System.currentTimeMillis(), -amount // Use negative amount for subtraction
                                );
                                detectionLogs.add(subtractionLog);

                                // Notify the adapter about the change in the dataset
                                logsAdapter.notifyDataSetChanged();

                                // Update the total value TextView
                                updateTotalCurrencyTextView(totalCurrencyValue);

                                // Insert the subtraction log into the database
                                insertSubtractionLogIntoDatabase(subtractionLog);

                                updatedTotalValue = new TextToSpeech(getApplicationContext(), status -> {
                                    if (status != TextToSpeech.ERROR) {
                                        updatedTotalValue.setLanguage(Locale.US);
                                        String text = "Your new total is " + totalCurrencyValue + " Pesos";
                                        updatedTotalValue.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
                                    }
                                });

                            } catch (NumberFormatException e) {
                                Toast.makeText(this, "Invalid amount specified.", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                }
            }
        }
    }

    // Method to insert the subtraction log into the database
    private void insertSubtractionLogIntoDatabase(DetectionLogEntry log) {
        try {
            database.beginTransaction();
            ContentValues values = new ContentValues();
            values.put("item", log.getItem());
            values.put("confidence", log.getConfidence());
            values.put("timestamp", log.getTimestamp());
            values.put("value", log.getValue());
            database.insert("detection_logs", null, values);
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
        Log.d("AuditLogActivity", "Subtraction log inserted into the database: " + log);
    }

    private void updateTotalCurrencyTextView(float value) {
        String updatedCurrencyText = getString(R.string.total_currency_value, value);
        totalCurrencyTextView.setText(updatedCurrencyText);
    }

    private void updateTotalValueInDatabase(float value) {
        try {
            database.beginTransaction();
            dbHelper.updateTotalValue(value);
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
        Log.d("AuditLogActivity", "Total value updated in the database: " + value);
    }

    @Override
    public void onBackPressed() {
        Intent intent = new Intent(this, DetectorActivity.class);
        startActivity(intent);
    }
}