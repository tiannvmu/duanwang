package com.duanwangzikong;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

public class 开机接收器 extends BroadcastReceiver {
    @Override
    public void onReceive(Context 上下文, Intent 意图) {
        if (意图 == null) {
            return;
        }
        SharedPreferences 配置 = 上下文.getSharedPreferences("配置", Context.MODE_PRIVATE);
        if (!配置.getBoolean("正在运行", false)) {
            return;
        }
        Intent 服务意图 = new Intent(上下文, 断网服务.class);
        服务意图.setAction(断网服务.动作_开启);
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                上下文.startForegroundService(服务意图);
            } else {
                上下文.startService(服务意图);
            }
        } catch (Exception 忽略) {
        }
    }
}