package com.voice.app;

import android.app.Activity;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class RecordActivity extends AppCompatActivity {

    private static final int REQUEST_SCREEN_RECORD = 103;
    private TextView tvStatus, tvTimer;
    private Button btnStart, btnStop, btnClose;
    private MediaProjectionManager projectionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_record);

        tvStatus = findViewById(R.id.tvRecordStatus);
        tvTimer = findViewById(R.id.tvRecordTimer);
        btnStart = findViewById(R.id.btnStartRecord);
        btnStop = findViewById(R.id.btnStopRecord);
        btnClose = findViewById(R.id.btnCloseRecord);
        
        projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        btnStop.setEnabled(false);

        btnStart.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Intent intent = projectionManager.createScreenCaptureIntent();
                startActivityForResult(intent, REQUEST_SCREEN_RECORD);
            }
        });

        btnStop.setOnClickListener(v -> {
            if (ScreenRecordService.isRunning) {
                Intent stopIntent = new Intent(this, ScreenRecordService.class);
                stopIntent.setAction("STOP");
                ContextCompat.startForegroundService(this, stopIntent);
                btnStart.setEnabled(true);
                btnStop.setEnabled(false);
                tvStatus.setText("⏹ Запись остановлена");
                tvTimer.setText("00:00");
            }
        });

        btnClose.setOnClickListener(v -> finish());
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SCREEN_RECORD) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                Intent serviceIntent = new Intent(this, ScreenRecordService.class);
                serviceIntent.setAction("START");
                serviceIntent.putExtra("resultCode", resultCode);
                serviceIntent.putExtra("data", data);
                ContextCompat.startForegroundService(this, serviceIntent);
                btnStart.setEnabled(false);
                btnStop.setEnabled(true);
                tvStatus.setText("⏺ Идёт запись...");
                Toast.makeText(this, "Запись экрана начата", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Запись не разрешена", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (ScreenRecordService.isRunning) {
            btnStart.setEnabled(false);
            btnStop.setEnabled(true);
            tvStatus.setText("⏺ Идёт запись...");
        }
    }
}
