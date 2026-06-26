package com.duanwangzikong;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class 主界面 extends Activity {
    private static final int 请求码_创建VPN = 1001;
    private static final int 请求码_通知权限 = 1002;
    private SharedPreferences 配置;
    private WebView 网页视图;

    @Override
    protected void onCreate(Bundle 保存状态) {
        super.onCreate(保存状态);
        配置 = getSharedPreferences("配置", MODE_PRIVATE);
        构建网页界面();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 每次回到前台都重新检测门禁，防止用户中途把权限关掉
        刷新门禁();
    }

    private void 构建网页界面() {
        网页视图 = new WebView(this);
        WebSettings 设置 = 网页视图.getSettings();
        设置.setJavaScriptEnabled(true);
        设置.setDomStorageEnabled(true);
        设置.setAllowFileAccess(true);
        设置.setCacheMode(WebSettings.LOAD_NO_CACHE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            设置.setAllowFileAccessFromFileURLs(true);
            设置.setAllowUniversalAccessFromFileURLs(true);
        }
        网页视图.addJavascriptInterface(new 前端桥(), "AndroidBridge");
        网页视图.loadUrl("file:///android_asset/www/index.html");
        setContentView(网页视图);
    }

    // ============ 门禁检测：三项必须项 A通知 / B电池优化 / C VPN授权 ============

    private boolean 通知权限已授予() {
        if (Build.VERSION.SDK_INT >= 33) {
            return checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private boolean 电池优化已忽略() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        PowerManager 电源管理器 = (PowerManager) getSystemService(POWER_SERVICE);
        return 电源管理器 != null && 电源管理器.isIgnoringBatteryOptimizations(getPackageName());
    }

    private boolean VPN已授权() {
        return VpnService.prepare(this) == null;
    }

    private boolean 应用列表可用() {
        try {
            int 数量 = getPackageManager().getInstalledPackages(0).size();
            return 数量 > 3;
        } catch (Exception 异常) {
            return false;
        }
    }

    private boolean 门禁全部通过() {
        return 通知权限已授予() && 电池优化已忽略() && VPN已授权() && 应用列表可用();
    }

    private void 刷新门禁() {
        if (网页视图 != null) {
            网页视图.post(() -> 网页视图.evaluateJavascript("window.刷新门禁 && window.刷新门禁()", null));
        }
    }

    // ============ 三项必须项的申请动作 ============

    private void 申请通知权限() {
        if (Build.VERSION.SDK_INT < 33 || 通知权限已授予()) {
            return;
        }
        // 第一次：还没请求过，或系统允许再次弹窗 -> 直接弹系统授权框
        boolean 已弹过 = 配置.getBoolean("已弹过通知权限", false);
        if (!已弹过 || shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
            配置.edit().putBoolean("已弹过通知权限", true).apply();
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 请求码_通知权限);
        } else {
            // 已被永久拒绝（勾了不再询问），系统弹窗弹不出来 -> 直接跳通知设置页
            打开通知设置();
        }
    }

    private void 打开通知设置() {
        try {
            Intent 意图 = new Intent();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                意图.setAction(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                意图.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            } else {
                意图.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                意图.setData(Uri.parse("package:" + getPackageName()));
            }
            意图.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(意图);
            Toast.makeText(this, "请在系统设置里手动允许“断网自控”的通知权限", Toast.LENGTH_LONG).show();
        } catch (Exception 异常) {
            打开应用详情();
        }
    }

    private void 申请电池优化() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || 电池优化已忽略()) {
            return;
        }
        try {
            Intent 意图 = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            意图.setData(Uri.parse("package:" + getPackageName()));
            startActivity(意图);
        } catch (Exception 异常) {
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Exception 忽略) {
            }
        }
    }

    private void 申请VPN授权() {
        Intent 准备意图 = VpnService.prepare(this);
        if (准备意图 != null) {
            startActivityForResult(准备意图, 请求码_创建VPN);
        } else {
            刷新门禁();
        }
    }

    // ============ 自启动引导（方案乙：可选，主界面手动触发，不进强制门禁）============

    private void 引导厂商自启() {
        String 厂商 = Build.MANUFACTURER == null ? "" : Build.MANUFACTURER.toLowerCase();
        Intent 意图 = new Intent();
        try {
            if (厂商.contains("xiaomi") || 厂商.contains("redmi")) {
                意图.setComponent(new android.content.ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"));
            } else if (厂商.contains("huawei") || 厂商.contains("honor")) {
                意图.setComponent(new android.content.ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"));
            } else if (厂商.contains("oppo") || 厂商.contains("realme")) {
                意图.setComponent(new android.content.ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"));
            } else if (厂商.contains("vivo")) {
                意图.setComponent(new android.content.ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"));
            } else if (厂商.contains("oneplus")) {
                意图.setComponent(new android.content.ComponentName("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"));
            } else {
                打开应用详情();
                return;
            }
            意图.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(意图);
            Toast.makeText(this, "请把“断网自控”设为允许自启动，保活更稳", Toast.LENGTH_LONG).show();
        } catch (Exception 异常) {
            打开应用详情();
        }
    }

    private void 打开应用详情() {
        try {
            Intent 详情 = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            详情.setData(Uri.parse("package:" + getPackageName()));
            详情.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(详情);
        } catch (Exception 忽略) {
        }
    }

    // ============ 启停服务 ============

    private void 启动断网服务() {
        Intent 意图 = new Intent(this, 断网服务.class);
        意图.setAction(断网服务.动作_开启);
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(意图);
        } else {
            startService(意图);
        }
        配置.edit().putBoolean("正在运行", true).apply();
        Toast.makeText(this, "断网自控已开启", Toast.LENGTH_SHORT).show();
        通知前端状态();
    }

    private void 停止断网() {
        Intent 意图 = new Intent(this, 断网服务.class);
        意图.setAction(断网服务.动作_停止);
        startService(意图);
        配置.edit().putBoolean("正在运行", false).apply();
        Toast.makeText(this, "断网自控已停止", Toast.LENGTH_SHORT).show();
        通知前端状态();
    }

    private void 通知前端状态() {
        if (网页视图 != null) {
            网页视图.post(() -> 网页视图.evaluateJavascript("window.refreshFromAndroid && window.refreshFromAndroid()", null));
        }
    }

    @Override
    protected void onActivityResult(int 请求码, int 结果码, Intent 数据) {
        super.onActivityResult(请求码, 结果码, 数据);
        刷新门禁();
    }

    @Override
    public void onRequestPermissionsResult(int 请求码, String[] 权限, int[] 结果) {
        super.onRequestPermissionsResult(请求码, 权限, 结果);
        刷新门禁();
    }

    public class 前端桥 {
        @JavascriptInterface
        public String getApps() {
            return 获取应用列表JSON();
        }

        @JavascriptInterface
        public String getConfig() {
            return 获取配置JSON();
        }

        @JavascriptInterface
        public String getGateStatus() {
            try {
                JSONObject 对象 = new JSONObject();
                对象.put("通知", 通知权限已授予());
                对象.put("电池", 电池优化已忽略());
                对象.put("VPN", VPN已授权());
                对象.put("应用列表", 应用列表可用());
                对象.put("全部通过", 门禁全部通过());
                return 对象.toString();
            } catch (Exception 异常) {
                return "{}";
            }
        }

        @JavascriptInterface
        public void requestNotification() {
            runOnUiThread(() -> 申请通知权限());
        }

        @JavascriptInterface
        public void requestBattery() {
            runOnUiThread(() -> 申请电池优化());
        }

        @JavascriptInterface
        public void requestVpn() {
            runOnUiThread(() -> 申请VPN授权());
        }

        @JavascriptInterface
        public void openAutoStart() {
            runOnUiThread(() -> 引导厂商自启());
        }

        @JavascriptInterface
        public void openAppDetails() {
            runOnUiThread(() -> 打开应用详情());
        }

        @JavascriptInterface
        public void saveConfig(String 模式, String 包名JSON) {
            保存配置数据(模式, 包名JSON);
        }

        @JavascriptInterface
        public void toggleProtection() {
            runOnUiThread(() -> {
                if (!门禁全部通过()) {
                    刷新门禁();
                    return;
                }
                if (配置.getBoolean("正在运行", false)) {
                    停止断网();
                } else {
                    启动断网服务();
                }
            });
        }

        @JavascriptInterface
        public void showToast(String 文本) {
            runOnUiThread(() -> Toast.makeText(主界面.this, 文本, Toast.LENGTH_SHORT).show());
        }
    }

    // ============ 应用列表 / 配置 ============

    private String 获取应用列表JSON() {
        try {
            PackageManager 包管理器 = getPackageManager();
            Map<String, 应用条目> 应用映射 = new HashMap<>();

            List<PackageInfo> 包列表 = 包管理器.getInstalledPackages(0);
            for (PackageInfo 包信息 : 包列表) {
                ApplicationInfo 信息 = 包信息.applicationInfo;
                if (信息 == null || 包信息.packageName.equals(getPackageName())) {
                    continue;
                }
                添加应用条目(应用映射, 包管理器, 信息, false);
            }

            Intent 启动意图 = new Intent(Intent.ACTION_MAIN, null);
            启动意图.addCategory(Intent.CATEGORY_LAUNCHER);
            List<android.content.pm.ResolveInfo> 启动项列表 = 包管理器.queryIntentActivities(启动意图, 0);
            for (android.content.pm.ResolveInfo 启动项 : 启动项列表) {
                if (启动项.activityInfo == null || 启动项.activityInfo.applicationInfo == null) {
                    continue;
                }
                添加应用条目(应用映射, 包管理器, 启动项.activityInfo.applicationInfo, true);
            }

            List<应用条目> 应用列表 = new ArrayList<>(应用映射.values());
            Collections.sort(应用列表, (左, 右) -> {
                if (左.可启动 != 右.可启动) {
                    return 左.可启动 ? -1 : 1;
                }
                return 左.名称.compareToIgnoreCase(右.名称);
            });
            JSONArray 数组 = new JSONArray();
            for (应用条目 条目 : 应用列表) {
                JSONObject 对象 = new JSONObject();
                对象.put("名称", 条目.名称);
                对象.put("包名", 条目.包名);
                对象.put("系统应用", 条目.系统应用);
                对象.put("可启动", 条目.可启动);
                对象.put("图标", 条目.图标);
                数组.put(对象);
            }
            return 数组.toString();
        } catch (Exception 异常) {
            return "[]";
        }
    }

    private void 添加应用条目(Map<String, 应用条目> 应用映射, PackageManager 包管理器, ApplicationInfo 信息, boolean 可启动) {
        String 包名 = 信息.packageName;
        boolean 系统应用 = (信息.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        String 名称 = String.valueOf(包管理器.getApplicationLabel(信息));
        应用条目 已有 = 应用映射.get(包名);
        if (已有 == null) {
            应用映射.put(包名, new 应用条目(名称, 包名, 系统应用, 可启动, 编码图标(包管理器.getApplicationIcon(信息), 48)));
        } else if (可启动 && !已有.可启动) {
            应用映射.put(包名, new 应用条目(已有.名称, 已有.包名, 已有.系统应用, true, 已有.图标));
        }
    }

    private String 获取本应用图标() {
        try {
            return 编码图标(getPackageManager().getApplicationIcon(getPackageName()), 64);
        } catch (Exception 异常) {
            return "";
        }
    }

    private String 编码图标(Drawable 图标, int 尺寸DP) {
        try {
            int 尺寸 = (int) (尺寸DP * getResources().getDisplayMetrics().density + 0.5f);
            Bitmap 位图;
            if (图标 instanceof BitmapDrawable) {
                位图 = Bitmap.createScaledBitmap(((BitmapDrawable) 图标).getBitmap(), 尺寸, 尺寸, true);
            } else {
                位图 = Bitmap.createBitmap(尺寸, 尺寸, Bitmap.Config.ARGB_8888);
                Canvas 画布 = new Canvas(位图);
                图标.setBounds(0, 0, 画布.getWidth(), 画布.getHeight());
                图标.draw(画布);
            }
            ByteArrayOutputStream 输出流 = new ByteArrayOutputStream();
            位图.compress(Bitmap.CompressFormat.PNG, 85, 输出流);
            return "data:image/png;base64," + Base64.encodeToString(输出流.toByteArray(), Base64.NO_WRAP);
        } catch (Exception 异常) {
            return "";
        }
    }

    private String 获取配置JSON() {
        try {
            JSONObject 对象 = new JSONObject();
            对象.put("模式", 配置.getString("模式", "白名单"));
            对象.put("正在运行", 配置.getBoolean("正在运行", false));
            对象.put("本应用图标", 获取本应用图标());
            JSONArray 选中 = new JSONArray();
            for (String 包名 : 读取包名集合()) {
                选中.put(包名);
            }
            对象.put("选中包名", 选中);
            return 对象.toString();
        } catch (Exception 异常) {
            return "{}";
        }
    }

    private void 保存配置数据(String 模式, String 包名JSON) {
        try {
            JSONArray 数组 = new JSONArray(包名JSON);
            StringBuilder 构建器 = new StringBuilder();
            for (int 索引 = 0; 索引 < 数组.length(); 索引++) {
                构建器.append(数组.getString(索引)).append('\n');
            }
            配置.edit().putString("模式", 模式).putString("受控包名", 构建器.toString()).apply();
        } catch (Exception 忽略) {
        }
    }

    private Set<String> 读取包名集合() {
        Set<String> 结果 = new HashSet<>();
        String 原始文本 = 配置.getString("受控包名", 配置.getString("白名单", ""));
        for (String 行 : 原始文本.split("\\n")) {
            String 包名 = 行.trim();
            if (!包名.isEmpty() && !包名.startsWith("#")) {
                结果.add(包名);
            }
        }
        return 结果;
    }

    private static class 应用条目 {
        final String 名称;
        final String 包名;
        final boolean 系统应用;
        final boolean 可启动;
        final String 图标;

        应用条目(String 名称, String 包名, boolean 系统应用, boolean 可启动, String 图标) {
            this.名称 = 名称;
            this.包名 = 包名;
            this.系统应用 = 系统应用;
            this.可启动 = 可启动;
            this.图标 = 图标;
        }
    }
}