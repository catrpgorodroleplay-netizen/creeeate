package cr.arcade;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

/** Прозрачная активность: открывает системный выбор файла для импорта/экспорта конфигов. */
public class ConfigPickerActivity extends Activity {
    public interface UriCb { void on(Uri uri); }

    private static UriCb callback;
    private static final int RC = 77;

    public static void launch(Context c, boolean export, UriCb cb) {
        callback = cb;
        Intent i = new Intent(c, ConfigPickerActivity.class);
        i.putExtra("export", export);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        c.startActivity(i);
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Intent i;
        if (getIntent().getBooleanExtra("export", false)) {
            i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.setType("application/json");
            i.putExtra(Intent.EXTRA_TITLE, "arcade_macros.json");
        } else {
            i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.setType("*/*");
        }
        i.addCategory(Intent.CATEGORY_OPENABLE);
        try { startActivityForResult(i, RC); } catch (Exception e) { finish(); }
    }

    @Override protected void onActivityResult(int rc, int res, Intent data) {
        super.onActivityResult(rc, res, data);
        UriCb cb = callback;
        callback = null;
        if (cb != null) cb.on(res == RESULT_OK && data != null ? data.getData() : null);
        finish();
        overridePendingTransition(0, 0);
    }
}
