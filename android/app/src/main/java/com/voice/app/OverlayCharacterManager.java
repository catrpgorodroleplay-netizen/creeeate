package com.voice.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;

public class OverlayCharacterManager {

    private Context context;
    private WindowManager windowManager;
    private ImageView characterView;
    private WindowManager.LayoutParams params;
    private boolean isFixed = false;
    private float startX, startY;
    private int initialX, initialY;

    public OverlayCharacterManager(Context context, WindowManager wm) {
        this.context = context;
        this.windowManager = wm;
    }

    public void loadCharacter(Bitmap characterBitmap) {
        if (windowManager == null) {
            if (context != null) {
                Toast.makeText(context, "WindowManager не инициализирован", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        if (characterBitmap == null) {
            if (context != null) {
                Toast.makeText(context, "Ошибка: картинка не загружена", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        // Проверяем, что картинка не слишком большая
        if (characterBitmap.getWidth() > 2000 || characterBitmap.getHeight() > 2000) {
            Bitmap scaled = Bitmap.createScaledBitmap(characterBitmap, 800, 800, true);
            characterBitmap = scaled;
        }

        Bitmap processedBitmap = removeGreenScreen(characterBitmap);
        showCharacter(processedBitmap);
    }

    private void showCharacter(Bitmap bitmap) {
        if (windowManager == null) return;

        try {
            if (characterView != null) {
                windowManager.removeView(characterView);
                characterView = null;
            }
        } catch (Exception ignored) {}

        characterView = new ImageView(context);
        characterView.setImageBitmap(bitmap);
        characterView.setScaleType(ImageView.ScaleType.FIT_XY);

        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;

        params = new WindowManager.LayoutParams(
                250, 250,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                flags,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 300;
        params.y = 300;

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
                        try {
                            windowManager.updateViewLayout(characterView, params);
                        } catch (Exception ignored) {}
                        return true;
                    default:
                        return false;
                }
            }
        });

        try {
            windowManager.addView(characterView, params);
        } catch (Exception e) {
            if (context != null) {
                Toast.makeText(context, "Ошибка отображения персонажа: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private Bitmap removeGreenScreen(Bitmap source) {
        if (source == null) return null;

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
        if (characterView != null && windowManager != null && params != null) {
            params.width = width;
            params.height = height;
            try {
                windowManager.updateViewLayout(characterView, params);
            } catch (Exception ignored) {}
        }
    }

    public void removeCharacter() {
        if (characterView != null && windowManager != null) {
            try {
                windowManager.removeView(characterView);
            } catch (Exception ignored) {}
            characterView = null;
        }
    }
}
