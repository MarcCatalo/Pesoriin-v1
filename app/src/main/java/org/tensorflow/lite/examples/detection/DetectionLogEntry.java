package org.tensorflow.lite.examples.detection;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;

public class DetectionLogEntry implements Serializable {
    private long _id;
    private String item;
    private float confidence;
    private long timestamp;
    private float value; // This will represent the currency value

    public DetectionLogEntry(long _id, String item, float confidence, long timestamp, float value) {
        this._id = _id;
        this.item = item;
        this.confidence = confidence;
        this.timestamp = timestamp;
        this.value = value;
    }

    public long getId() {
        return _id;
    }

    public String getItem() {
        return item;
    }

    public float getConfidence() {
        return confidence;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public float getValue() {
        return value;
    }

    @Override
    public String toString() {
        Date date = new Date(timestamp);
        SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy");
        SimpleDateFormat stf = new SimpleDateFormat("hh:mm a");
        String formattedDate = sdf.format(date);
        String formattedTime = stf.format(date);

        double multipliedConfidence = confidence * 100;
        String formattedConfidence = String.format("%.2f", multipliedConfidence);

        if (item.equals("Subtraction")) {
            return value + " Pesos, Confidence: " + formattedConfidence +
                    "%              Date: " + formattedDate + ", Time: " + formattedTime;
        } else {
            return item + " Pesos," + " Confidence: " + formattedConfidence +
                    "%              Date: " + formattedDate + ", Time: " + formattedTime;
        }    }
}