private void saveProxySettings() {
    String server = etServer.getText().toString().trim();
    String port = etPort.getText().toString().trim();
    String secret = etSecret.getText().toString().trim();

    if (server.isEmpty() || port.isEmpty() || secret.isEmpty()) {
        Toast.makeText(this, "Заполните все поля", Toast.LENGTH_SHORT).show();
        return;
    }

    // Проверяем порт
    try {
        Integer.parseInt(port);
    } catch (NumberFormatException e) {
        Toast.makeText(this, "Порт должен быть числом", Toast.LENGTH_SHORT).show();
        return;
    }

    // === НОВАЯ ПРОВЕРКА: секрет может быть 32 или 34 символа ===
    // Секрет должен быть hex (только 0-9, a-f, A-F)
    if (!secret.matches("[0-9a-fA-F]+")) {
        Toast.makeText(this, "Секрет должен содержать только hex-символы (0-9, a-f)", Toast.LENGTH_SHORT).show();
        return;
    }

    // Проверяем длину: 32 или 34 символа
    if (secret.length() != 32 && secret.length() != 34) {
        Toast.makeText(this, "Секрет должен быть 32 или 34 hex-символа", Toast.LENGTH_SHORT).show();
        return;
    }

    // Если секрет 34 символа — проверяем, что начинается с "dd"
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

    Toast.makeText(this, "✅ Прокси сохранён (секрет: " + secret.length() + " симв.)", Toast.LENGTH_SHORT).show();
    finish();
}
