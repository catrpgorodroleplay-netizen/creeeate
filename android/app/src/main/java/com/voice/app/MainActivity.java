package com.voice.app;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    private static final int REQUEST_MICROPHONE = 100;
    private Button minimizeButton;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Запрос микрофона
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_MICROPHONE);
        }

        // Добавляем кнопку
        addMinimizeButton();

        // Включаем режим PiP для активности
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            setPictureInPictureParams(new android.app.PictureInPictureParams.Builder().build());
        }
    }

    private void addMinimizeButton() {
        minimizeButton = new Button(this);
        minimizeButton.setText("🔘 СВЕРНУТЬ В ОКНО");
        minimizeButton.setTextColor(android.graphics.Color.WHITE);
        minimizeButton.setBackgroundColor(android.graphics.Color.parseColor("#4CAF50"));
        minimizeButton.setAllCaps(false);
        minimizeButton.setPadding(40, 25, 40, 25);
        minimizeButton.setTextSize(18);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL;
        params.bottomMargin = 80;

        View rootView = findViewById(android.R.id.content);
        if (rootView instanceof FrameLayout) {
            ((FrameLayout) rootView).addView(minimizeButton, params);
        }

        minimizeButton.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Входим в режим PiP
                enterPictureInPictureMode();
                Toast.makeText(this, "📱 Приложение в фоне, микрофон работает", Toast.LENGTH_SHORT).show();
            } else {
                // Для старых версий просто сворачиваем
                moveTaskToBack(true);
                Toast.makeText(this, "Приложение свернуто", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode);
        
        // Когда мы в режиме PiP, кнопка не нужна
        if (minimizeButton != null) {
            minimizeButton.setVisibility(isInPictureInPictureMode ? View.GONE : View.VISIBLE);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (minimizeButton != null) {
            minimizeButton.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_MICROPHONE && grantResults.length > 0) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "🎤 Микрофон разрешён", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "🎤 Микрофон НЕ разрешён", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
