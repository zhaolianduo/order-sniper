package com.ordersniper;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

/**
 * 悬浮窗服务
 * 显示一个可拖动的浮层，包含：
 * - 开关按钮（启动/停止抢单）
 * - 状态文字
 * - 关闭按钮
 */
public class FloatWindowService extends Service {

    private static final String CHANNEL_ID = "order_sniper_channel";
    private static final int NOTIFICATION_ID = 1001;

    public static final String ACTION_STATUS_UPDATE = "com.ordersniper.STATUS_UPDATE";

    private WindowManager windowManager;
    private View floatView;
    private WindowManager.LayoutParams params;

    private TextView tvStatus;
    private View btnToggle;
    private boolean isRunning = false;

    private BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_STATUS_UPDATE.equals(intent.getAction())) {
                isRunning = intent.getBooleanExtra("running", false);
                updateUI();
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        initFloatWindow();

        IntentFilter filter = new IntentFilter(ACTION_STATUS_UPDATE);
        registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
    }

    private void initFloatWindow() {
        if (!Settings.canDrawOverlays(this)) return;

        floatView = LayoutInflater.from(this).inflate(R.layout.float_window, null);
        tvStatus = floatView.findViewById(R.id.tv_status);
        btnToggle = floatView.findViewById(R.id.btn_toggle);
        View btnClose = floatView.findViewById(R.id.btn_close);
        View btnSettings = floatView.findViewById(R.id.btn_settings);

        int layoutType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.END;
        params.x = 10;
        params.y = 200;

        // 拖动逻辑
        floatView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            private long downTime;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        downTime = System.currentTimeMillis();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX - (int)(event.getRawX() - initialTouchX);
                        params.y = initialY + (int)(event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(floatView, params);
                        return true;
                }
                return false;
            }
        });

        // 开关按钮
        btnToggle.setOnClickListener(v -> {
            Intent intent = new Intent(OrderSniperService.ACTION_TOGGLE);
            sendBroadcast(intent);
        });

        // 关闭悬浮窗（不停止服务）
        btnClose.setOnClickListener(v -> {
            if (floatView.getParent() != null) {
                windowManager.removeView(floatView);
            }
        });

        // 打开设置界面
        btnSettings.setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        });

        windowManager.addView(floatView, params);
        updateUI();
    }

    private void updateUI() {
        if (tvStatus == null) return;
        if (isRunning) {
            tvStatus.setText("抢单中");
            tvStatus.setTextColor(Color.parseColor("#FF6600"));
            btnToggle.setBackgroundColor(Color.parseColor("#FF6600"));
        } else {
            tvStatus.setText("已停止");
            tvStatus.setTextColor(Color.parseColor("#999999"));
            btnToggle.setBackgroundColor(Color.parseColor("#CCCCCC"));
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatView != null && floatView.getParent() != null) {
            windowManager.removeView(floatView);
        }
        try {
            unregisterReceiver(statusReceiver);
        } catch (Exception ignored) {}
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "抢单助手", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("保持服务在后台运行");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("抢单助手运行中")
                .setContentText("正在监控订单关键词")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }
}
