package com.ordersniper;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private EditText etKeywords;
    private Button btnSave;
    private Button btnStartFloat;
    private Button btnOpenAccessibility;
    private TextView tvAccessibilityStatus;
    private TextView tvFloatStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etKeywords = findViewById(R.id.et_keywords);
        btnSave = findViewById(R.id.btn_save);
        btnStartFloat = findViewById(R.id.btn_start_float);
        btnOpenAccessibility = findViewById(R.id.btn_open_accessibility);
        tvAccessibilityStatus = findViewById(R.id.tv_accessibility_status);
        tvFloatStatus = findViewById(R.id.tv_float_status);

        // 请求忽略电池优化
        requestBatteryOptimization();

        // 加载已保存的关键词
        SharedPreferences prefs = getSharedPreferences(AppConstants.PREFS_NAME, MODE_PRIVATE);
        String saved = prefs.getString(AppConstants.KEY_KEYWORDS, "东方学院");
        etKeywords.setText(saved);

        btnSave.setOnClickListener(v -> {
            String keywords = etKeywords.getText().toString().trim();
            if (TextUtils.isEmpty(keywords)) {
                Toast.makeText(this, "请至少输入一个关键词", Toast.LENGTH_SHORT).show();
                return;
            }
            prefs.edit().putString(AppConstants.KEY_KEYWORDS, keywords).apply();
            Intent intent = new Intent(OrderSniperService.ACTION_KEYWORDS_CHANGED);
            sendBroadcast(intent);
            Toast.makeText(this, "关键词已保存：" + keywords, Toast.LENGTH_SHORT).show();
        });

        btnStartFloat.setOnClickListener(v -> {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "请先授予悬浮窗权限", Toast.LENGTH_SHORT).show();
                requestOverlayPermission();
                return;
            }
            Intent intent = new Intent(this, FloatWindowService.class);
            startService(intent);
            Toast.makeText(this, "悬浮窗已启动，可切换到目标App", Toast.LENGTH_SHORT).show();
        });

        btnOpenAccessibility.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private void refreshStatus() {
        boolean accessibilityOn = OrderSniperService.instance != null;
        if (accessibilityOn) {
            tvAccessibilityStatus.setText("✅ 无障碍服务：已开启");
            tvAccessibilityStatus.setTextColor(getColor(R.color.status_ok));
            btnOpenAccessibility.setText("无障碍设置（已开启）");
        } else {
            tvAccessibilityStatus.setText("❌ 无障碍服务：未开启（必须手动开启）");
            tvAccessibilityStatus.setTextColor(getColor(R.color.status_error));
            btnOpenAccessibility.setText("前往开启无障碍服务 →");
        }

        boolean overlayOk = Settings.canDrawOverlays(this);
        if (overlayOk) {
            tvFloatStatus.setText("✅ 悬浮窗权限：已授予");
            tvFloatStatus.setTextColor(getColor(R.color.status_ok));
        } else {
            tvFloatStatus.setText("❌ 悬浮窗权限：未授予");
            tvFloatStatus.setTextColor(getColor(R.color.status_error));
        }
    }

    private void requestOverlayPermission() {
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        startActivity(intent);
    }

    private void requestBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                try {
                    startActivity(intent);
                } catch (Exception e) {
                    // 部分手机可能不支持
                }
            }
        }
    }
}