package com.ordersniper;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
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
    private TextView tvKeywordsHint;

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

        // 加载已保存的关键词
        SharedPreferences prefs = getSharedPreferences(AppConstants.PREFS_NAME, MODE_PRIVATE);
        String saved = prefs.getString(AppConstants.KEY_KEYWORDS, "东方学院");
        etKeywords.setText(saved);

        // 保存关键词
        btnSave.setOnClickListener(v -> {
            String keywords = etKeywords.getText().toString().trim();
            if (TextUtils.isEmpty(keywords)) {
                Toast.makeText(this, "请至少输入一个关键词", Toast.LENGTH_SHORT).show();
                return;
            }
            prefs.edit().putString(AppConstants.KEY_KEYWORDS, keywords).apply();
            // 通知服务更新关键词
            Intent intent = new Intent(OrderSniperService.ACTION_KEYWORDS_CHANGED);
            sendBroadcast(intent);
            Toast.makeText(this, "关键词已保存：" + keywords, Toast.LENGTH_SHORT).show();
        });

        // 开启悬浮窗
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

        // 跳转无障碍设置
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
        // 无障碍服务状态
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

        // 悬浮窗权限
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
}
