package com.voice.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class ProxySettingsActivity extends AppCompatActivity {

    private EditText etServer, etPort, etSecret;
    private Button btnSave, btnClear, btnStatus;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_proxy_settings);

        prefs = getSharedPreferences("proxy_settings", MODE_PRIVATE);

        etServer = findViewById(R.id.etProxyServer);
        etPort = findViewById(R.id.etProxyPort);
        etSecret = findViewById(R.id.etProxySecret);
        btnSave = findViewById(R.id.btnSaveProxy);
        btnClear = findViewById(R.id.btnClearProxy);
        btnStatus = findViewById(R.id.btnProxyStatus);

        loadProxySettings();
        updateStatusButton();

        btnSave.setOnClickListener(v -> saveProxySettings());
        btnClear.setOnClickListener(v -> clearProxySettings());
        btnStatus.setOnClickListener(v -> toggleProxy());
    }

    private void loadProxySettings() {
        etServer.setText(prefs.getString("server", ""));
        etPort.setText(prefs.getString("port", ""));
        etSecret.setText(prefs.getString("secret", ""));
    }

    private void saveProxySettings() {
        String server = etServer.getText().toString().trim();
        String port = etPort.getText().toString().trim();
        String secret = etSecret.getText().toString().trim();

        if (server.isEmpty() || port.isEmpty() || secret.isEmpty()) {
            Toast.makeText(this, "Заполните все поля", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Integer.parseInt(port);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Порт должен быть числом", Toast.LENGTH_SHORT).show();
            return;
        }

        if (secret.length() > 32) {
            secret = secret.substring(0, 32);
            etSecret.setText(secret);
            Toast.makeText(this, "Секрет обрезан до 32 символов", Toast.LENGTH_SHORT).show();
        }

        if (secret.length() != 32 || !secret.matches("[0-9a-fA-F]+")) {
            Toast.makeText(this, "Секрет должен быть 32 hex-символа", Toast.LENGTH_SHORT).show();
            return;
        }

        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("server", server);
        editor.putString("port", port);
        editor.putString("secret", secret);
        editor.putBoolean("enabled", true);
        editor.apply();

        // Останавливаем старый прокси
        stopProxyService();

        // Запускаем новый
        startProxyService(server, Integer.parseInt(port), secret);

        Toast.makeText(this, "✅ Прокси сохранён и запущен", Toast.LENGTH_SHORT).show();
        updateStatusButton();
        finish();
    }

    private void clearProxySettings() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.clear();
        editor.apply();

        etServer.setText("");
        etPort.setText("");
        etSecret.setText("");

        stopProxyService();

        Toast.makeText(this, "🗑 Прокси удалён", Toast.LENGTH_SHORT).show();
        updateStatusButton();
    }

    private void startProxyService(String host, int port, String secret) {
        Intent intent = new Intent(this, LittleProxyService.class);
        intent.setAction("START");
        intent.putExtra("host", host);
        intent.putExtra("port", port);
        intent.putExtra("secret", secret);
        ContextCompat.startForegroundService(this, intent);
    }

    private void stopProxyService() {
        Intent intent = new Intent(this, LittleProxyService.class);
        intent.setAction("STOP");
        startService(intent);
    }

    private void toggleProxy() {
        if (LittleProxyService.isRunning) {
            stopProxyService();
            Toast.makeText(this, "🔓 Прокси остановлен", Toast.LENGTH_SHORT).show();
        } else {
            String server = prefs.getString("server", "");
            String port = prefs.getString("port", "");
            String secret = prefs.getString("secret", "");
            if (server.isEmpty() || port.isEmpty() || secret.isEmpty()) {
                Toast.makeText(this, "❌ Сначала сохраните настройки прокси", Toast.LENGTH_SHORT).show();
                return;
            }
            startProxyService(server, Integer.parseInt(port), secret);
            Toast.makeText(this, "🔒 Прокси запущен", Toast.LENGTH_SHORT).show();
        }
        updateStatusButton();
    }

    private void updateStatusButton() {
        if (LittleProxyService.isRunning) {
            btnStatus.setText("⏹ ОСТАНОВИТЬ ПРОКСИ");
            btnStatus.setBackgroundColor(0xFFE53935);
        } else {
            btnStatus.setText("▶ ЗАПУСТИТЬ ПРОКСИ");
            btnStatus.setBackgroundColor(0xFF4CAF50);
        }
    }

    public static class ProxyData {
        public String server;
        public String port;
        public String secret;

        public ProxyData(String server, String port, String secret) {
            this.server = server;
            this.port = port;
            this.secret = secret;
        }
    }
                }
