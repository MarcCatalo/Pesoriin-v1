package org.tensorflow.lite.examples.detection;

import androidx.appcompat.app.AppCompatActivity;


import android.content.Intent;

import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;

import android.speech.tts.TextToSpeech;
import android.widget.VideoView;

import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    public static final float MINIMUM_CONFIDENCE_TF_OD_API = 0.03f;
    private TextToSpeech textToSpeech;

    private static final int SPLASH_TIMEOUT = 5000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        VideoView videoView = findViewById(R.id.splashVideoView);
        Uri videoUri = Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.splash_screen);
        videoView.setVideoURI(videoUri);
        videoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mediaPlayer) {
                // When video is done, finish the activity and proceed to the main app or next screen.
                finish();
            }
        });
        videoView.start();

        // Initialize TextToSpeech
        textToSpeech = new TextToSpeech(getApplicationContext(), status -> {
            if (status != TextToSpeech.ERROR) {
                textToSpeech.setLanguage(Locale.US);
                String text = "Welcome to Pesoriin Philippine Denomination Detector";
                textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
            }
        });

        new android.os.Handler().postDelayed(()->{
            Intent mainIntent = new Intent(MainActivity.this, DetectorActivity.class);
            startActivity(mainIntent);
        }, SPLASH_TIMEOUT);
    }

    @Override
    protected void onDestroy() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        super.onDestroy();
    }
}



