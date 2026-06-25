package com.voice.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.InputStream;

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

    public void loadCharacterFromUri(Uri imageUri) {
        if (windowManager == null || context == null) return;
        try {
            InputStream inputStream = context.getContentResolver().openInputStream(imageUri);
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            if (bitmap != null) {
                showCharacter(bitmap);
                Toast.makeText(context, "🦸 Персонаж загружен", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(context, "Ошибка загрузки: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void showCharacter(Bitmap bitmap) {
        if (windowManager == null) return;

        if (characterView != null) {
            try { windowManager.removeView(characterView); } catch (Exception ignored) {}
            characterView = null;
        }

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

        characterView.setOnTouchListener((v, event) -> {
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
        });

        windowManager.addView(characterView, params);
    }

    public void fixCharacter() {
        isFixed = true;
        Toast.makeText(context, "🔒 Персонаж зафиксирован", Toast.LENGTH_SHORT).show();
    }

    public void removeCharacter() {
        if (characterView != null && windowManager != null) {
            try { windowManager.removeView(characterView); } catch (Exception ignored) {}
            characterView = null;
            isFixed = false;
            Toast.makeText(context, "🗑 Персонаж удалён", Toast.LENGTH_SHORT).show();
        }
    }

    public boolean isCharacterLoaded() {
        return characterView != null;
    }
}
