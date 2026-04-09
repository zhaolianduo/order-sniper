package com.ordersniper;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 核心无障碍服务
 * 逻辑：遍历屏幕节点树，找到包含关键词的订单卡片，
 * 然后在同一卡片内找到"抢单"按钮并点击。
 */
public class OrderSniperService extends AccessibilityService {

    private static final String TAG = "OrderSniperService";
    public static final String ACTION_TOGGLE = "com.ordersniper.TOGGLE";
    public static final String ACTION_KEYWORDS_CHANGED = "com.ordersniper.KEYWORDS_CHANGED";

    // 单例引用，方便外部查询服务状态
    public static OrderSniperService instance;

    private boolean isRunning = false;
    private List<String> keywords = new ArrayList<>();
    private Handler handler = new Handler(Looper.getMainLooper());

    // 防重复点击：记录上次点击时间
    private long lastClickTime = 0;
    private static final long CLICK_COOLDOWN_MS = 3000;

    private BroadcastReceiver controlReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_TOGGLE.equals(action)) {
                isRunning = !isRunning;
                Log.d(TAG, "抢单服务状态: " + (isRunning ? "开启" : "关闭"));
                // 通知悬浮窗更新状态
                Intent update = new Intent(FloatWindowService.ACTION_STATUS_UPDATE);
                update.putExtra("running", isRunning);
                sendBroadcast(update);
            } else if (ACTION_KEYWORDS_CHANGED.equals(action)) {
                loadKeywords();
            }
        }
    };

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        loadKeywords();

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_TOGGLE);
        filter.addAction(ACTION_KEYWORDS_CHANGED);
        registerReceiver(controlReceiver, filter, Context.RECEIVER_NOT_EXPORTED);

        Log.d(TAG, "无障碍服务已连接");
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

    /**
     * 扫描节点树：
     * 找到所有文本节点，判断是否含关键词，
     * 若含关键词，则向上找到订单卡片容器，再在卡片内搜"抢单"按钮并点击。
     */
    private void scanAndClick(AccessibilityNodeInfo root) {
        // 防冷却
        long now = System.currentTimeMillis();
        if (now - lastClickTime < CLICK_COOLDOWN_MS) return;

        // 遍历所有节点
        List<AccessibilityNodeInfo> allNodes = new ArrayList<>();
        collectAllNodes(root, allNodes);

        for (AccessibilityNodeInfo node : allNodes) {
            CharSequence text = node.getText();
            if (text == null) continue;
            String textStr = text.toString();

            for (String keyword : keywords) {
                if (textStr.contains(keyword)) {
                    Log.d(TAG, "命中关键词 [" + keyword + "] 在文本: " + textStr);
                    // 找到包含该文字节点的"卡片"容器，然后在其中寻找抢单按钮
                    if (tryClickSnatButton(node)) {
                        lastClickTime = System.currentTimeMillis();
                        vibrateFeedback();
                        return; // 点击一次后立刻退出，避免重复点击
                    }
                }
            }
        }

        // 回收节点
        for (AccessibilityNodeInfo n : allNodes) {
            if (n != null) n.recycle();
        }
    }

    /**
     * 从命中关键词的节点向上爬，找到卡片容器，再在其中找抢单按钮
     */
    private boolean tryClickSnatButton(AccessibilityNodeInfo keywordNode) {
        // 向上最多爬 10 层，找到一个包含可点击"抢单"按钮的祖先节点
        AccessibilityNodeInfo current = keywordNode;
        for (int i = 0; i < 12; i++) {
            AccessibilityNodeInfo parent = current.getParent();
            if (parent == null) break;

            // 在 parent 的子树中找"抢单"按钮
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

    /**
     * 在节点子树中找文本为"抢单"的可点击节点
     */
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
