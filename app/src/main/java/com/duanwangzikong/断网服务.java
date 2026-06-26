package com.duanwangzikong;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class 断网服务 extends VpnService implements Runnable {
    public static final String 动作_开启 = "com.duanwangzikong.开启";
    public static final String 动作_停止 = "com.duanwangzikong.停止";
    private static final String 通知频道ID = "duanwangzikong_vpn";
    private ParcelFileDescriptor VPN接口;
    private Thread 工作线程;
    private volatile boolean 正在运行;

    @Override
    public int onStartCommand(Intent 意图, int 标志, int 启动编号) {
        if (意图 != null && 动作_停止.equals(意图.getAction())) {
            停止服务();
            return START_NOT_STICKY;
        }
        启动前台通知();
        启动VPN();
        return START_STICKY;
    }

    private void 启动VPN() {
        停止VPN接口();
        try {
            Builder 构建器 = new Builder();
            构建器.setSession("断网自控");
            构建器.addAddress("10.88.0.2", 32);
            构建器.addRoute("0.0.0.0", 0);
            配置应用规则(构建器);
            VPN接口 = 构建器.establish();
            正在运行 = VPN接口 != null;
            if (正在运行) {
                工作线程 = new Thread(this, "断网自控黑洞线程");
                工作线程.start();
            }
            getSharedPreferences("配置", MODE_PRIVATE).edit().putBoolean("正在运行", 正在运行).apply();
        } catch (Exception 异常) {
            getSharedPreferences("配置", MODE_PRIVATE).edit().putBoolean("正在运行", false).putString("最后错误", 异常.toString()).apply();
            stopSelf();
        }
    }

    private void 配置应用规则(Builder 构建器) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return;
        }
        SharedPreferences 配置 = getSharedPreferences("配置", MODE_PRIVATE);
        String 模式 = 配置.getString("模式", "白名单");
        Set<String> 包名集合 = 获取受控包名();
        包名集合.remove(getPackageName());
        try {
            if ("黑名单".equals(模式)) {
                for (String 包名 : 包名集合) {
                    try {
                        构建器.addAllowedApplication(包名);
                    } catch (Exception 忽略) {
                    }
                }
            } else {
                构建器.addDisallowedApplication(getPackageName());
                for (String 包名 : 包名集合) {
                    try {
                        构建器.addDisallowedApplication(包名);
                    } catch (Exception 忽略) {
                    }
                }
            }
        } catch (Exception 忽略) {
        }
    }

    private Set<String> 获取受控包名() {
        SharedPreferences 配置 = getSharedPreferences("配置", MODE_PRIVATE);
        String 原始文本 = 配置.getString("受控包名", 配置.getString("白名单", ""));
        Set<String> 结果 = new HashSet<>();
        for (String 行 : 原始文本.split("\\n")) {
            String 包名 = 行.trim();
            if (!包名.isEmpty() && !包名.startsWith("#")) {
                结果.add(包名);
            }
        }
        return 结果;
    }

    @Override
    public void run() {
        byte[] 缓冲区 = new byte[32767];
        try (FileInputStream 输入流 = new FileInputStream(VPN接口.getFileDescriptor())) {
            while (正在运行) {
                int 读取长度 = 输入流.read(缓冲区);
                if (读取长度 < 0) {
                    break;
                }
            }
        } catch (IOException 忽略) {
        } finally {
            停止VPN接口();
        }
    }

    private void 启动前台通知() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel 频道 = new NotificationChannel(通知频道ID, "断网自控运行状态", NotificationManager.IMPORTANCE_LOW);
            NotificationManager 通知管理器 = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (通知管理器 != null) {
                通知管理器.createNotificationChannel(频道);
            }
        }

        Intent 打开意图 = new Intent(this, 主界面.class);
        int 标志 = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            标志 |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent 待处理意图 = PendingIntent.getActivity(this, 0, 打开意图, 标志);
        int 图标资源 = getResources().getIdentifier("ic_launcher", "mipmap", getPackageName());
        if (图标资源 == 0) {
            图标资源 = android.R.drawable.ic_lock_lock;
        }
        Notification.Builder 构建器 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, 通知频道ID)
                : new Notification.Builder(this);
        Notification 通知 = 构建器
                .setContentTitle("断网自控正在运行")
                .setContentText("按当前黑/白名单规则阻止联网")
                .setSmallIcon(图标资源)
                .setLargeIcon(BitmapFactory.decodeResource(getResources(), 图标资源))
                .setContentIntent(待处理意图)
                .setOngoing(true)
                .build();
        startForeground(1, 通知);
    }

    private void 停止服务() {
        getSharedPreferences("配置", MODE_PRIVATE).edit().putBoolean("正在运行", false).apply();
        停止VPN接口();
        stopForeground(true);
        stopSelf();
    }

    @Override
    public void onTaskRemoved(Intent 根意图) {
        if (getSharedPreferences("配置", MODE_PRIVATE).getBoolean("正在运行", false)) {
            尝试自启();
        }
        super.onTaskRemoved(根意图);
    }

    private void 尝试自启() {
        try {
            Intent 重启意图 = new Intent(this, 断网服务.class);
            重启意图.setAction(动作_开启);
            android.app.PendingIntent 待处理 = android.app.PendingIntent.getService(
                    this, 1, 重启意图,
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                            ? android.app.PendingIntent.FLAG_IMMUTABLE | android.app.PendingIntent.FLAG_UPDATE_CURRENT
                            : android.app.PendingIntent.FLAG_UPDATE_CURRENT);
            android.app.AlarmManager 闹钟 = (android.app.AlarmManager) getSystemService(ALARM_SERVICE);
            if (闹钟 != null) {
                闹钟.set(android.app.AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 1500, 待处理);
            }
        } catch (Exception 忽略) {
        }
    }

    private void 停止VPN接口() {
        正在运行 = false;
        if (VPN接口 != null) {
            try {
                VPN接口.close();
            } catch (IOException 忽略) {
            }
            VPN接口 = null;
        }
    }

    @Override
    public void onDestroy() {
        boolean 应保持 = getSharedPreferences("配置", MODE_PRIVATE).getBoolean("正在运行", false);
        停止VPN接口();
        if (应保持) {
            尝试自启();
        }
        super.onDestroy();
    }
}