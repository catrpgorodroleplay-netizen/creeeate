package com.voice.app;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

public class PopupActivity extends Activity {

    private WebView webView;
    private Button minimizeButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Окно без заголовка
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        
        // Настройки окна (плавающее, не на весь экран)
        Window window = getWindow();
        WindowManager.LayoutParams params = window.getAttributes();
        params.width = 600;
        params.height = 800;
        params.gravity = Gravity.CENTER;
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        window.setAttributes(params);
        
        // Прозрачный фон
        window.setBackgroundDrawableResource(android.R.color.transparent);
        
        // Создаём контейнер
        FrameLayout layout = new FrameLayout(this);
        layout.setBackgroundColor(0xDD1E1E1E); // Полупрозрачный фон
        layout.setPadding(10, 10, 10, 10);
        
        // WebView с сайтом
        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setDomStorageEnabled(true);
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(android.webkit.PermissionRequest request) {
                request.grant(new String[]{android.webkit.PermissionRequest.RESOURCE_AUDIO_CAPTURE});
            }
        });
        webView.setWebViewClient(new WebViewClient());
        webView.loadUrl("https://crconferensimessenger.vercel.app/");
        webView.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        
        // Кнопка "свернуть в кружок"
        minimizeButton = new Button(this);
        minimizeButton.setText("🔘");
        minimizeButton.setTextSize(20);
        minimizeButton.setBackgroundColor(0x88000000);
        minimizeButton.setPadding(15, 10, 15, 10);
        
        FrameLayout.LayoutParams buttonParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        buttonParams.gravity = Gravity.TOP | Gravity.END;
        buttonParams.setMargins(0, 10, 10, 0);
        minimizeButton.setLayoutParams(buttonParams);
        
        minimizeButton.setOnClickListener(v -> {
            // Сворачиваем окно — показываем кружок
            finish(); // Закрываем окно
            // Возвращаем кружок через MainActivity
            if (MainActivity.floatingCircle != null) {
                MainActivity.floatingCircle.setVisibility(View.VISIBLE);
            }
            Toast.makeText(this, "🔘 Окно свернуто в кружок", Toast.LENGTH_SHORT).show();
        });
        
        layout.addView(webView);
        layout.addView(minimizeButton);
        setContentView(layout);
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (webView != null) {
            webView.destroy();
        }
    }
}
