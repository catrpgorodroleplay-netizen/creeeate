package com.voice.app;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;

public class OverlayCharacterManager {

    private WindowManager windowManager;
    private ImageView characterView;
    private WindowManager.LayoutParams params;
    private boolean isFixed = false;
    private float startX, startY;
    private int initialX, initialY;

    public OverlayCharacterManager(WindowManager wm) {
        this.windowManager = wm;
    }

    public void loadCharacter(Bitmap characterBitmap) {
        Bitmap processedBitmap = removeGreenScreen(characterBitmap);
        showCharacter(processedBitmap);
    }

    private void showCharacter(Bitmap bitmap) {
        if (characterView != null) {
            windowManager.removeView(characterView);
        }

        characterView = new ImageView(windowManager.getContext());
        characterView.setImageBitmap(bitmap);
        characterView.setScaleType(ImageView.ScaleType.FIT_XY);

        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;

        params = new WindowManager.LayoutParams(
                200, 200,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                flags,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 500;
        params.y = 500;

        characterView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (isFixed) return false;
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = event.getRawX();
                        startY = event.getRawY();
                        initialX = params.x;
                        initialY = params.y;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX + (int) (event.getRawX() - startX);
                        params.y = initialY + (int) (event.getRawY() - startY);
                        windowManager.updateViewLayout(characterView, params);
                        return true;
                    default:
                        return false;
                }
            }
        });

        windowManager.addView(characterView, params);
    }

    private Bitmap removeGreenScreen(Bitmap source) {
        int width = source.getWidth();
        int height = source.getHeight();
        Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int pixel = source.getPixel(x, y);
                int red = Color.red(pixel);
                int green = Color.green(pixel);
                int blue = Color.blue(pixel);

                if (green > 150 && red < 80 && blue < 80) {
                    result.setPixel(x, y, Color.TRANSPARENT);
                } else {
                    result.setPixel(x, y, pixel);
                }
            }
        }
        return result;
    }

    public void fixCharacter() {
        isFixed = true;
    }

    public void unfixCharacter() {
        isFixed = false;
    }

    public void setSize(int width, int height) {
        if (characterView != null) {
            params.width = width;
            params.height = height;
            windowManager.updateViewLayout(characterView, params);
        }
    }

    public void removeCharacter() {
        if (characterView != null && windowManager != null) {
            windowManager.removeView(characterView);
            characterView = null;
        }
    }
}
