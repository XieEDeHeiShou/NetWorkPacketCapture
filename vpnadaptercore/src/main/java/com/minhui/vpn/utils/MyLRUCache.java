package com.minhui.vpn.utils;

import android.support.annotation.NonNull;

import java.util.LinkedHashMap;

/**
 * Created by minhui.zhu on 2017/6/24.
 * Copyright © 2017年 minhui.zhu. All rights reserved.
 */
public class MyLRUCache<K, V> extends LinkedHashMap<K, V> {
    private int maxSize;
    private transient CleanupCallback<V> callback;

    public MyLRUCache(int maxSize, @NonNull CleanupCallback<V> callback) {
        super(maxSize + 1, 1, true);

        this.maxSize = maxSize;
        this.callback = callback;
    }

    @Override
    protected boolean removeEldestEntry(Entry<K, V> eldest) {
        if (size() > maxSize) {
            callback.cleanUp(eldest.getValue());
            return true;
        }
        return false;
    }

    public interface CleanupCallback<V> {
        /**
         * 清除对象
         *
         * @param v the object that just removed from the cache manager
         */
        //void onRemove(V);
        void cleanUp(V v);
    }
}
