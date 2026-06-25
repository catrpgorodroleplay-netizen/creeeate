package com.voice.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.drawable.BitmapDrawable;
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

    // 1. Загружаем персонажа из ресурсов или картинки
    public void loadCharacter(Bitmap characterBitmap) {
        // Если фон зеленый — удаляем его
        Bitmap processedBitmap = removeGreenScreen(characterBitmap);
        showCharacter(processedBitmap);
    }

    // 2. Показываем персонажа на экране (НЕ БЛОКИРУЕТ КАСАНИЯ!)
    private void showCharacter(Bitmap bitmap) {
        if (characterView != null) {
            windowManager.removeView(characterView);
        }

        characterView = new ImageView(windowManager.getContext());
        characterView.setImageBitmap(bitmap);
        characterView.setScaleType(ImageView.ScaleType.FIT_XY);

        // КЛЮЧЕВОЙ МОМЕНТ: НЕ БЛОКИРУЕМ КАСАНИЯ
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH;

        params = new WindowManager.LayoutParams(
                200, // Ширина (можно менять)
                200, // Высота (можно менять)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, // Поверх всех окон
                flags,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 500;
        params.y = 500;

        // Добавляем возможность перетаскивания (только когда не зафиксирован)
        characterView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (isFixed) return false; // Если зафиксирован — не двигаем

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

    // 3. Удаление зеленого фона (chroma key)
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

                // Определяем зелёный фон
                if (green > 150 && red < 80 && blue < 80) {
                    // Делаем прозрачным
                    result.setPixel(x, y, Color.TRANSPARENT);
                } else {
                    // Оставляем оригинальный цвет
                    result.setPixel(x, y, pixel);
                }
            }
        }
        return result;
    }

    // 4. Фиксация (после нажатия кнопки "ЗАКРЕПИТЬ")
    public void fixCharacter() {
        isFixed = true;
        // Делаем прозрачным для касаний (уже есть FLAG_NOT_TOUCHABLE)
        // Но можно добавить визуальный эффект (например, обводку)
    }

    // 5. Разблокировка
    public void unfixCharacter() {
        isFixed = false;
    }

    // 6. Изменение размера по XYZ (упрощённо — масштабирование)
    public void setSize(int width, int height) {
        if (characterView != null) {
            params.width = width;
            params.height = height;
            windowManager.updateViewLayout(characterView, params);
        }
    }

    // 7. Удаление с экрана
    public void removeCharacter() {
        if (characterView != null && windowManager != null) {
            windowManager.removeView(characterView);
            characterView = null;
        }
    }
}
