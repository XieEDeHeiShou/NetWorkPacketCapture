package com.minhui.vpn.processparse;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.util.LruCache;

import com.minhui.vpn.R;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * @author minhui.zhu
 * Created by minhui.zhu on 2018/4/30.
 * Copyright © 2017年 Oceanwing. All rights reserved.
 */

public class AppInfo implements Serializable {
    private static final LruCache<String, IconInfo> iconCache = new LruCache<>(50);
    private static Drawable defaultIcon = null;
    public final String leaderAppName;
    public final PackageNames pkgs;

    private AppInfo(@NonNull String leaderAppName, @NonNull String[] pkgs) {
        this.leaderAppName = leaderAppName;
        this.pkgs = PackageNames.newInstance(pkgs);
    }

    @Nullable
    static AppInfo createFromUid(@NonNull Context ctx, int uid) {
        PackageManager pm = ctx.getPackageManager();
        ArrayList<Entry> list = new ArrayList<>();
        if (uid > 0) {
            try {
                String[] pkgNames = pm.getPackagesForUid(uid);
                if (pkgNames == null || pkgNames.length <= 0) {
                    list.add(new Entry("System", "nonpkg.noname"));
                } else {
                    for (String pkgName : pkgNames) {
                        if (pkgName != null) {
                            try {
                                PackageInfo appPackageInfo = pm.getPackageInfo(pkgName, 0);
                                String appName = null;
                                if (appPackageInfo != null) {
                                    appName = appPackageInfo.applicationInfo.loadLabel(pm).toString();
                                }
                                if (appName == null || appName.equals("")) {
                                    appName = pkgName;
                                }
                                list.add(new Entry(appName, pkgName));
                            } catch (PackageManager.NameNotFoundException e) {
                                list.add(new Entry(pkgName, pkgName));
                            }
                        }
                    }
                }
            } catch (RuntimeException e2) {
                Log.i("NRFW", "error getPackagesForUid(). package manager has died");
                return null;
            }
        }
        if (list.size() == 0) {
            list.add(new Entry("System", "root.uid=0"));
        }
        Collections.sort(list, new Comparator<Entry>() {
            public int compare(Entry lhs, Entry rhs) {
                int ret = lhs.appName.compareToIgnoreCase(rhs.appName);
                if (ret == 0) {
                    return lhs.pkgName.compareToIgnoreCase(rhs.pkgName);
                }
                return ret;
            }
        });
        String[] pkgs = new String[list.size()];
        String[] apps = new String[list.size()];
        for (int i = 0; i < list.size(); i++) {
            pkgs[i] = list.get(i).pkgName;
            apps[i] = list.get(i).appName;
        }
        return new AppInfo(apps[0], pkgs);
    }

    @NonNull
    public static Drawable getIcon(@NonNull Context ctx, @NonNull String pkgName) {
        Drawable drawable;
        synchronized (AppInfo.class) {
            IconInfo iconInfo;
            if (defaultIcon == null) {
                defaultIcon = ctx.getResources().getDrawable(R.drawable.sym_def_app_icon);
            }
            PackageManager pm = ctx.getPackageManager();
            PackageInfo appPackageInfo = null;
            try {
                appPackageInfo = pm.getPackageInfo(pkgName, 0);
            } catch (PackageManager.NameNotFoundException ignore) {
            }
            if (appPackageInfo != null) {
                drawable = appPackageInfo.applicationInfo.loadIcon(pm);
                iconInfo = new IconInfo();
                iconInfo.date = appPackageInfo.lastUpdateTime;
                iconInfo.icon = drawable;
                iconCache.put(pkgName, iconInfo);
            } else {
                iconCache.remove(pkgName);
                drawable = defaultIcon;
            }
        }
        return drawable;
    }

    static class Entry {
        final String appName;
        final String pkgName;

        Entry(@NonNull String appName, @NonNull String pkgName) {
            this.appName = appName;
            this.pkgName = pkgName;
        }
    }

    static class IconInfo {
        long date;
        Drawable icon;
    }
}
