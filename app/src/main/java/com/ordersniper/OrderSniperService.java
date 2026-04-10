package com.ordersniper;

import android.accessibilityservice.AccessibilityService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.Vibrator;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.core.app.NotificationCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * 核心无障碍服务 - 前台服务版本
 * 启动为前台服务，显示常驻通知，防止被系统杀掉
 */
public class OrderSniperService extends AccessibilityService {

    private static final String TAG = "OrderSniperService";
    public static final String ACTION_TOGGLE = "com.ordersniper.TOGGLE";
    public static final String ACTION_KEYWORDS_CHANGED = "com.ordersniper.KEYWORDS_CHANGED";
    private static final String CHANNEL_ID = "order_sniper_service";
    private static final int NOTIFICATION_ID = 1001;

    // 单例引用
    public static OrderSniperService instance;

    private boolean isRunning = false;
    private List<String> keywords = new ArrayList<>();
    private Handler handler = new Handler(Looper.getMainLooper());

    // 防重复点击
    private long lastClickTime = 0;
    private static final long CLICK_COOLDOWN_MS = 3000;

    private BroadcastReceiver controlReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_TOGGLE.equals(action)) {
                isRunning = !isRunning;
                Log.d(TAG, "抢单服务状态: " + (isRunning ? "开启" : "关闭"));
                updateNotification();
                broadcastStatus();
            } else if (ACTION_KEYWORDS_CHANGED.equals(action)) {
                loadKeywords();
            }
        }
    };

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        createNotificationChannel();
        startForegroundService();
        loadKeywords();

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_TOGGLE);
        filter.addAction(ACTION_KEYWORDS_CHANGED);
        registerReceiver(controlReceiver, filter, Context.RECEIVER_NOT_EXPORTED);

        // 广播初始状态，让悬浮窗同步
        broadcastStatus();

        Log.d(TAG, "无障碍服务已连接");
    }

    private void broadcastStatus() {
        Intent update = new Intent(FloatWindowService.ACTION_STATUS_UPDATE);
        update.putExtra("running", isRunning);
        sendBroadcast(update);
    }

    private void createNotificationChannel() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "抢单服务",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("保持抢单服务运行");
            channel.setShowBadge(false);
            nm.createNotificationChannel(channel);
        }
    }

    private void startForegroundService() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🦞 抢单助手运行中")
            .setContentText(isRunning ? "监控中，等待抢单..." : "已暂停，点击开启")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void updateNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            Intent notificationIntent = new Intent(this, MainActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("🦞 抢单助手运行中")
                .setContentText(isRunning ? "监控中，等待抢单..." : "已暂停，点击开启")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();

            nm.notify(NOTIFICATION_ID, notification);
        }
    }

    private void loadKeywords() {
        SharedPreferences prefs = getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE);
        String raw = prefs.getString(AppConstants.KEY_KEYWORDS, "东方学院");
        keywords.clear();
        for (String kw : raw.split(",")) {
            String trimmed = kw.trim();
            if (!trimmed.isEmpty()) {
                keywords.add(trimmed);
            }
        }
        Log.d(TAG, "关键词列表: " + keywords);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!isRunning) return;
        if (keywords.isEmpty()) return;

        int type = event.getEventType();
        if (type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                && type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && type != AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            return;
        }

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;

        try {
            scanAndClick(root);
        } finally {
            root.recycle();
        }
    }

    private void scanAndClick(AccessibilityNodeInfo root) {
        long now = System.currentTimeMillis();
        if (now - lastClickTime < CLICK_COOLDOWN_MS) return;

        List<AccessibilityNodeInfo> allNodes = new ArrayList<>();
        collectAllNodes(root, allNodes);

        // 方案1: 直接扫描所有"抢单"按钮（优先级高）
        boolean clicked = scanForGrabButton(allNodes);
        if (clicked) {
            lastClickTime = System.currentTimeMillis();
            vibrateFeedback();
            return;
        }

        // 方案2: 如果关键词列表不为空，也扫描关键词匹配
        if (!keywords.isEmpty()) {
            for (AccessibilityNodeInfo node : allNodes) {
                CharSequence text = node.getText();
                if (text == null) continue;
                String textStr = text.toString();

                for (String keyword : keywords) {
                    if (textStr.contains(keyword)) {
                        Log.d(TAG, "命中关键词 [" + keyword + "] 在文本: " + textStr);
                        if (tryClickSnatButton(node)) {
                            lastClickTime = System.currentTimeMillis();
                            vibrateFeedback();
                            return;
                        }
                    }
                }
            }
        }

        for (AccessibilityNodeInfo n : allNodes) {
            if (n != null) n.recycle();
        }
    }

    // 直接扫描并点击"抢单"按钮
    private boolean scanForGrabButton(List<AccessibilityNodeInfo> allNodes) {
        for (AccessibilityNodeInfo node : allNodes) {
            CharSequence text = node.getText();
            if (text == null) continue;
            String textStr = text.toString();

            if (textStr.contains("抢单")) {
                Log.d(TAG, "找到抢单按钮: " + textStr);

                // 尝试直接点击当前节点
                if (node.isClickable()) {
                    Log.d(TAG, "直接点击抢单按钮");
                    boolean success = node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    if (success) return true;
                }

                // 尝试点击父节点
                AccessibilityNodeInfo parent = node.getParent();
                if (parent != null) {
                    if (parent.isClickable()) {
                        Log.d(TAG, "点击父节点抢单按钮");
                        boolean success = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                        parent.recycle();
                        if (success) return true;
                    }
                    parent.recycle();
                }
            }
        }
        return false;
    }

    private boolean tryClickSnatButton(AccessibilityNodeInfo keywordNode) {
        AccessibilityNodeInfo current = keywordNode;
        for (int i = 0; i < 12; i++) {
            AccessibilityNodeInfo parent = current.getParent();
            if (parent == null) break;

            AccessibilityNodeInfo btn = findSnatButton(parent);
            if (btn != null) {
                Log.d(TAG, "找到抢单按钮，准备点击: " + btn.getText());
                boolean success = btn.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                btn.recycle();
                return success;
            }
            current = parent;
        }
        return false;
    }

    private AccessibilityNodeInfo findSnatButton(AccessibilityNodeInfo root) {
        if (root == null) return null;
        CharSequence text = root.getText();
        if (text != null && text.toString().contains("抢单") && root.isClickable()) {
            return root;
        }
        for (int i = 0; i < root.getChildCount(); i++) {
            AccessibilityNodeInfo child = root.getChild(i);
            if (child == null) continue;
            AccessibilityNodeInfo result = findSnatButton(child);
            if (result != null) return result;
            child.recycle();
        }
        return null;
    }

    private void collectAllNodes(AccessibilityNodeInfo node, List<AccessibilityNodeInfo> list) {
        if (node == null) return;
        list.add(node);
        for (int i = 0; i < node.getChildCount(); i++) {
            collectAllNodes(node.getChild(i), list);
        }
    }

    private void vibrateFeedback() {
        try {
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null && v.hasVibrator()) {
                v.vibrate(200);
            }
        } catch (Exception e) {
            Log.e(TAG, "震动失败", e);
        }
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "无障碍服务中断");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
        try {
            unregisterReceiver(controlReceiver);
        } catch (Exception ignored) {}
    }

    public boolean isRunning() {
        return isRunning;
    }
}
