package com.voice.app;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class ProxySettingsActivity extends AppCompatActivity {

    private EditText etServer, etPort, etSecret;
    private Button btnSave, btnClear;
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

        loadProxySettings();

        btnSave.setOnClickListener(v -> saveProxySettings());
        btnClear.setOnClickListener(v -> clearProxySettings());
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

        // === НОВАЯ ПРОВЕРКА ===
        if (!secret.matches("[0-9a-fA-F]+")) {
            Toast.makeText(this, "Секрет должен содержать только hex-символы (0-9, a-f)", Toast.LENGTH_SHORT).show();
            return;
        }

        if (secret.length() != 32 && secret.length() != 34) {
            Toast.makeText(this, "Секрет должен быть 32 или 34 hex-символа", Toast.LENGTH_SHORT).show();
            return;
        }

        if (secret.length() == 34 && !secret.startsWith("dd")) {
            Toast.makeText(this, "34-символьный секрет должен начинаться с 'dd'", Toast.LENGTH_SHORT).show();
            return;
        }

        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("server", server);
        editor.putString("port", port);
        editor.putString("secret", secret);
        editor.putBoolean("enabled", true);
        editor.apply();

        Toast.makeText(this, "✅ Прокси сохранён (" + secret.length() + " симв.)", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void clearProxySettings() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.clear();
        editor.apply();
        etServer.setText("");
        etPort.setText("");
        etSecret.setText("");
        Toast.makeText(this, "🗑 Прокси удалён", Toast.LENGTH_SHORT).show();
    }

    public static ProxyData getProxySettings(SharedPreferences prefs) {
        if (!prefs.getBoolean("enabled", false)) {
            return null;
        }

        String server = prefs.getString("server", "");
        String port = prefs.getString("port", "");
        String secret = prefs.getString("secret", "");

        if (server.isEmpty() || port.isEmpty() || secret.isEmpty()) {
            return null;
        }

        return new ProxyData(server, port, secret);
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
