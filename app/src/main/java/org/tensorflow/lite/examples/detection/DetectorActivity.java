/*
 * Copyright 2019 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.tensorflow.lite.examples.detection;

import android.content.ContentValues;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.util.Size;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.tensorflow.lite.examples.detection.customview.OverlayView;
import org.tensorflow.lite.examples.detection.customview.OverlayView.DrawCallback;
import org.tensorflow.lite.examples.detection.env.BorderedText;
import org.tensorflow.lite.examples.detection.env.ImageUtils;
import org.tensorflow.lite.examples.detection.env.Logger;
import org.tensorflow.lite.examples.detection.tflite.Classifier;
import org.tensorflow.lite.examples.detection.tflite.DetectorFactory;
import org.tensorflow.lite.examples.detection.tflite.YoloV5Classifier;
import org.tensorflow.lite.examples.detection.tracking.LogDBHelper;
import org.tensorflow.lite.examples.detection.tracking.MultiBoxTracker;

import android.speech.tts.TextToSpeech.OnInitListener;


/**
 * An activity that uses a TensorFlowMultiBoxDetector and ObjectTracker to detect and then track
 * objects.
 */
public class DetectorActivity extends CameraActivity implements TextToSpeech.OnInitListener {
    private static final Logger LOGGER = new Logger();

    private static final DetectorMode MODE = DetectorMode.TF_OD_API;
    private static final float MINIMUM_CONFIDENCE_TF_OD_API = 0.3f;
    private static final boolean MAINTAIN_ASPECT = true;
    private static final Size DESIRED_PREVIEW_SIZE = new Size(640, 640);
    private static final boolean SAVE_PREVIEW_BITMAP = false;
    private static final float TEXT_SIZE_DIP = 10;
    OverlayView trackingOverlay;
    private Integer sensorOrientation;

    private YoloV5Classifier detector;

    private long lastProcessingTimeMs;
    private Bitmap rgbFrameBitmap = null;
    private Bitmap croppedBitmap = null;
    private Bitmap cropCopyBitmap = null;

    private boolean computingDetection = false;

    private long timestamp = 0;

    private Matrix frameToCropTransform;
    private Matrix cropToFrameTransform;

    private MultiBoxTracker tracker;

    private BorderedText borderedText;
    private List<DetectionLogEntry> detectionLogs = new ArrayList<>();

    private Set<String> detectedLabels = new HashSet<>();

    private float totalCurrencyValue;

    private float x1, x2, y1, y2;

    private LogDBHelper dbHelper;
    private SQLiteDatabase database;

    private boolean isAddToDetectionLogEnabled = true;
    private static final long DELAY_TIME_MILLIS = 6000; // 6 seconds

    private TextToSpeech tts, textToSpeech;


    private float extractCurrencyValue(String label) {
        try {
            return Float.parseFloat(label);
        } catch (NumberFormatException e) {
            Log.e("DetectorActivity", "Unable to parse currency value from label: " + label, e);
            return 0; // Return 0 if the label cannot be parsed as a float
        }
    }

    private void addToDetectionLog(String item, float confidence, float value, int detectedClass) {
        // Disable further calls to addToDetectionLog
        isAddToDetectionLogEnabled = false;

        ContentValues values = new ContentValues();
        values.put("item", item);
        values.put("confidence", confidence);
        values.put("timestamp", System.currentTimeMillis());
        values.put("value", value);

        // Extract the value from the values object
        float logEntryValue = values.getAsFloat("value");
        // Retrieve TotalValue from db
        totalCurrencyValue = dbHelper.getTotalValue();
        // Add LogEntryValue to TotalValue
        totalCurrencyValue += logEntryValue;
        // Save the updated total currency value to the database
        updateTotalValueInDatabase(totalCurrencyValue);

        long newRowId = database.insert("detection_logs", null, values);
        Log.d("DetectorActivity", "New row inserted with ID: " + newRowId);

        // Delayed execution to enable addToDetectionLog after a certain time
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                isAddToDetectionLogEnabled = true;
            }
        }, DELAY_TIME_MILLIS);
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        // ... (other initialization code)
        super.onCreate(savedInstanceState);
        setContentView(R.layout.tfe_od_activity_camera); // Ensure this is the correct layout file where you added the button
        tts = new TextToSpeech(this, (OnInitListener) this);

        dbHelper = new LogDBHelper(this);
        database = dbHelper.getWritableDatabase();

        textToSpeech = new TextToSpeech(getApplicationContext(), status -> {
            if (status != TextToSpeech.ERROR) {
                textToSpeech.setLanguage(Locale.US);
                String text = "Please scan your bills one at a time. To access your audit log, please swipe right";
                textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
            }
        });
    }

    public boolean onTouchEvent(MotionEvent touchEvent){
        switch (touchEvent.getAction()){
            case MotionEvent.ACTION_DOWN:
                x1 = touchEvent.getX();
                y1 = touchEvent.getY();
                break;
            case MotionEvent.ACTION_UP:
                x2 = touchEvent.getX();
                y2 = touchEvent.getY();
                if(x1 > x2){
                    Intent i = new Intent(DetectorActivity.this, AuditLogActivity.class);
                    startActivity(i);
                }
                break;
        }
        return false;
    }


    // Initialize TTS
    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            // Set TTS language if needed
            int result = tts.setLanguage(Locale.US); // Change to your desired language
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS", "Language not supported");
            }
        } else {
            Log.e("TTS", "Initialization failed");
        }
    }

    @Override
    public void onPreviewSizeChosen(final Size size, final int rotation) {
        final float textSizePx =
                TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, getResources().getDisplayMetrics());
        borderedText = new BorderedText(textSizePx);
        borderedText.setTypeface(Typeface.MONOSPACE);

        tracker = new MultiBoxTracker(this);

        final int modelIndex = modelView.getCheckedItemPosition();
        final String modelString = modelStrings.get(modelIndex);

        try {
            detector = DetectorFactory.getDetector(getAssets(), modelString);
        } catch (final IOException e) {
            e.printStackTrace();
            LOGGER.e(e, "Exception initializing classifier!");
            Toast toast =
                    Toast.makeText(
                            getApplicationContext(), "Classifier could not be initialized", Toast.LENGTH_SHORT);
            toast.show();
            finish();
        }

        int cropSize = detector.getInputSize();

        previewWidth = size.getWidth();
        previewHeight = size.getHeight();

        sensorOrientation = rotation - getScreenOrientation();
        LOGGER.i("Camera orientation relative to screen canvas: %d", sensorOrientation);

        LOGGER.i("Initializing at size %dx%d", previewWidth, previewHeight);
        rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Config.ARGB_8888);
        croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Config.ARGB_8888);

        frameToCropTransform =
                ImageUtils.getTransformationMatrix(
                        previewWidth, previewHeight,
                        cropSize, cropSize,
                        sensorOrientation, MAINTAIN_ASPECT);

        cropToFrameTransform = new Matrix();
        frameToCropTransform.invert(cropToFrameTransform);

        trackingOverlay = (OverlayView) findViewById(R.id.tracking_overlay);
        trackingOverlay.addCallback(
                new DrawCallback() {
                    @Override
                    public void drawCallback(final Canvas canvas) {
                        tracker.draw(canvas);
                        if (isDebug()) {
                            tracker.drawDebug(canvas);
                        }
                    }
                });
        tracker.setFrameConfiguration(previewWidth, previewHeight, sensorOrientation);
    }

    protected void updateActiveModel() {
        // Get UI information before delegating to background
        final int modelIndex = modelView.getCheckedItemPosition();
        final int deviceIndex = deviceView.getCheckedItemPosition();
        String threads = threadsTextView.getText().toString().trim();
        final int numThreads = Integer.parseInt(threads);

        handler. post(() -> {
            if (modelIndex == currentModel && deviceIndex == currentDevice
                    && numThreads == currentNumThreads) {
                return;
            }
            currentModel = modelIndex;

            // Disable classifier while updating
            if (detector != null) {
                detector.close();
                detector = null;
            }

            // Lookup names of parameters.
            String modelString = modelStrings.get(modelIndex);

            LOGGER.i("Changing model to " + modelString );

            // Try to load model.

            try {
                detector = DetectorFactory.getDetector(getAssets(), modelString);
                // Customize the interpreter to the type of device we want to use.
                if (detector == null) {
                    return;
                }
            } catch (IOException e) {
                e.printStackTrace();
                LOGGER.e(e, "Exception in updateActiveModel()");
                Toast toast =
                        Toast.makeText(
                                getApplicationContext(), "Classifier could not be initialized", Toast.LENGTH_SHORT);
                toast.show();
                finish();
            }


            int cropSize = detector.getInputSize();
            croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Config.ARGB_8888);

            frameToCropTransform =
                    ImageUtils.getTransformationMatrix(
                            previewWidth, previewHeight,
                            cropSize, cropSize,
                            sensorOrientation, MAINTAIN_ASPECT);

            cropToFrameTransform = new Matrix();
            frameToCropTransform.invert(cropToFrameTransform);
        });
    }

    @Override
    protected void processImage() {
        ++timestamp;
        final long currTimestamp = timestamp;
        trackingOverlay.postInvalidate();

        // No mutex needed as this method is not reentrant.
        if (computingDetection) {
            readyForNextImage();
            return;
        }
        computingDetection = true;
        LOGGER.i("Preparing image " + currTimestamp + " for detection in bg thread.");

        rgbFrameBitmap.setPixels(getRgbBytes(), 0, previewWidth, 0, 0, previewWidth, previewHeight);

        readyForNextImage();

        final Canvas canvas = new Canvas(croppedBitmap);
        canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null);
        // For examining the actual TF input.
        if (SAVE_PREVIEW_BITMAP) {
            ImageUtils.saveBitmap(croppedBitmap);
        }

        runInBackground(new Runnable() {
            @Override
            public void run() {
                LOGGER.i("Running detection on image " + currTimestamp);
                final long startTime = SystemClock.uptimeMillis();
                final List<Classifier.Recognition> results = detector.recognizeImage(croppedBitmap);
                lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;

                Log.e("CHECK", "run: " + results.size());

                cropCopyBitmap = Bitmap.createBitmap(croppedBitmap);
                final Canvas canvas = new Canvas(cropCopyBitmap);
                final Paint paint = new Paint();
                paint.setColor(Color.RED);
                paint.setStyle(Style.STROKE);
                paint.setStrokeWidth(2.0f);

                float minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
                switch (MODE) {
                    case TF_OD_API:
                        minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
                        break;
                }

                final List<Classifier.Recognition> mappedRecognitions =
                        new LinkedList<Classifier.Recognition>();

                for (final Classifier.Recognition result : results) {
                    final RectF location = result.getLocation();
                    if (location != null && result.getConfidence() >= 0.5f) {
                        canvas.drawRect(location, paint);

                        cropToFrameTransform.mapRect(location);

                        result.setLocation(location);
                        mappedRecognitions.add(result);

                        // Speak out the label of the detected object using TTS

                        String label = result.getTitle();
                        if (!detectedLabels.contains(label)) {
                            tts.speak("You have scanned" + label + "Pesos", TextToSpeech.QUEUE_ADD, null, null);
                            detectedLabels.add(label); // Add the label to the set

                            float currencyValue = extractCurrencyValue(result.getTitle());
                            addToDetectionLogWithDelay(result.getTitle(), result.getConfidence(), currencyValue, result.getDetectedClass());

                        }
                    }
                }

                if (results.isEmpty()) {
                    detectedLabels.clear();
                }

                tracker.trackResults(mappedRecognitions, currTimestamp);
                trackingOverlay.postInvalidate();

                computingDetection = false;

                runOnUiThread(
                        new Runnable() {
                            @Override
                            public void run() {
                                showFrameInfo(previewWidth + "x" + previewHeight);
                                showCropInfo(cropCopyBitmap.getWidth() + "x" + cropCopyBitmap.getHeight());
                                showInference(lastProcessingTimeMs + "ms");
                            }
                        });
            }
        });
    }

    private void addToDetectionLogWithDelay(final String item, final float confidence, final float value, final int detectedClass) {
        if (isAddToDetectionLogEnabled) {
            isAddToDetectionLogEnabled = false;

            addToDetectionLog(item, confidence, value, detectedClass);

            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    isAddToDetectionLogEnabled = true;
                }
            }, DELAY_TIME_MILLIS);
        }
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
    protected int getLayoutId() {
        return R.layout.tfe_od_camera_connection_fragment_tracking;
    }

    @Override
    protected Size getDesiredPreviewFrameSize() {
        return DESIRED_PREVIEW_SIZE;
    }

    @Override
    protected void setNumThreads(int numThreads) {
    }

    @Override
    protected void setUseNNAPI(boolean isChecked) {

    }
    // Which detection model to use: by default uses Tensorflow Object Detection API frozen
    // checkpoints.
    private enum DetectorMode {
        TF_OD_API;
    }

    @Override
    public void onDestroy() {
        dbHelper.close();
        super.onDestroy();
    }
}