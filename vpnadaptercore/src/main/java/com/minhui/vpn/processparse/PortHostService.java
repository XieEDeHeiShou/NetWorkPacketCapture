package com.minhui.vpn.processparse;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.minhui.vpn.VPNLog;
import com.minhui.vpn.nat.NatSession;
import com.minhui.vpn.nat.NatSessionManager;
import com.minhui.vpn.utils.VpnServiceHelper;

import java.util.Collection;

/**
 * @author minhui.zhu
 * Created by minhui.zhu on 2018/5/5.
 * Copyright © 2017年 Oceanwing. All rights reserved.
 */

public class PortHostService extends Service {
    private static final String TAG = "PortHostService";
    private static PortHostService instance;
    private boolean refreshing = false;

    @Nullable
    public static PortHostService getInstance() {
        return instance;
    }

    public static void start(@NonNull Context context) {
        Intent intent = new Intent(context, PortHostService.class);
        context.startService(intent);
    }

    public static void stop(@NonNull Context context) {
        Intent intent = new Intent(context, PortHostService.class);
        context.stopService(intent);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        NetFileManager.getInstance().init();
        instance = this;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
    }

    @NonNull
    public Collection<NatSession> getAndRefreshSessionInfo() {
        Collection<NatSession> allSession = NatSessionManager.getAllSession();
        refreshSessionInfo(allSession);
        return allSession;

    }

    public void refreshSessionInfo() {
        Collection<NatSession> allSession = NatSessionManager.getAllSession();
        refreshSessionInfo(allSession);
    }

    /**
     * 更新 Nat 会话池, 找到并更新没有 appInfo 的会话
     *
     * @param netConnections 待更新的 Nat 会话池
     */
    private void refreshSessionInfo(@NonNull Collection<NatSession> netConnections) {
        if (refreshing) {
            return;
        }
        //region 如果任意一个会话的应用信息(appInfo)为空, 则需要刷新
        boolean needRefresh = false;
        for (NatSession connection : netConnections) {
            if (connection.appInfo == null) {
                needRefresh = true;
                break;
            }
        }
        if (!needRefresh) {
            return;
        }
        //endregion
        refreshing = true;
        try {
            NetFileManager.getInstance().refresh();// 刷新 source port 与 uid 的对应关系

            for (NatSession connection : netConnections) {
                if (connection.appInfo == null) {
                    int searchPort = connection.localPort & 0XFFFF;
                    Integer uid = NetFileManager.getInstance().getUid(searchPort);
                    if (uid == null) {
                        VPNLog.d(TAG, "can not find uid");
                    } else {
                        connection.appInfo = AppInfo.createFromUid(VpnServiceHelper.getContext(), uid);
                    }
                }
            }
        } catch (Exception e) {
            VPNLog.d(TAG, "failed to refreshSessionInfo " + e.getMessage());
        }
        refreshing = false;
    }
}
