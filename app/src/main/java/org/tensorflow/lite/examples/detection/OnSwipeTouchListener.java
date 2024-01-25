package org.tensorflow.lite.examples.detection;

import android.content.Context;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

class OnSwipeTouchListener implements View.OnTouchListener {

    private final GestureDetector gestureDetector;

    public OnSwipeTouchListener(Context context) {
        gestureDetector = new GestureDetector(context, new GestureListener());
    }

    public void onSwipeLeft() {
        // To be overridden in the MainActivity
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        return gestureDetector.onTouchEvent(event);
    }

    private final class GestureListener extends GestureDetector.SimpleOnGestureListener {
        private static final int SWIPE_THRESHOLD = 120;
        private static final int SWIPE_VELOCITY_THRESHOLD = 120;

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            if (e1 == null || e2 == null) {
                return false;
            }

            float distanceX = e2.getX() - e1.getX();
            if (Math.abs(distanceX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                if (distanceX > 0) {
                    // Right swipe (not implemented in this example)
                } else {
                    Log.d("Swipe", "Left Swipe Detected");
                    onSwipeLeft();
                }
                return true;
            }
            return false;
        }
    }
}
