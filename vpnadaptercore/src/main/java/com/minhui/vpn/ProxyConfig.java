package com.minhui.vpn;


import android.content.Context;
import android.support.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Created by zengzheying on 15/12/28.
 */
public class ProxyConfig {
    public static final ProxyConfig Instance = new ProxyConfig();
    private List<VpnStatusListener> mVpnStatusListeners = new ArrayList<>();

    private ProxyConfig() {
    }

    public void registerVpnStatusListener(@NonNull VpnStatusListener vpnStatusListener) {
        mVpnStatusListeners.add(vpnStatusListener);
    }

    public void unregisterVpnStatusListener(@NonNull VpnStatusListener vpnStatusListener) {
        mVpnStatusListeners.remove(vpnStatusListener);
    }

    public void onVpnStart(@NonNull Context context) {
        VpnStatusListener[] vpnStatusListeners = new VpnStatusListener[mVpnStatusListeners.size()];
        mVpnStatusListeners.toArray(vpnStatusListeners);
        for (VpnStatusListener listener : vpnStatusListeners) {
            listener.onVpnStart(context);
        }
    }


    public void onVpnEnd(@NonNull Context context) {
        VpnStatusListener[] vpnStatusListeners = new VpnStatusListener[mVpnStatusListeners.size()];
        mVpnStatusListeners.toArray(vpnStatusListeners);
        for (VpnStatusListener listener : vpnStatusListeners) {
            listener.onVpnEnd(context);
        }
    }

    @NonNull
    public IPAddress getDefaultLocalIP() {
        return new IPAddress("10.8.0.2", 32);
    }

    public interface VpnStatusListener {
        void onVpnStart(@NonNull Context context);

        void onVpnEnd(@NonNull Context context);
    }

    public static class IPAddress {
        public final String Address;
        public final int PrefixLength;

        IPAddress(@NonNull String address, int prefixLength) {
            Address = address;
            PrefixLength = prefixLength;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof IPAddress)) {
                return false;
            } else {
                return this.toString().equals(o.toString());
            }
        }

        @Override
        @NonNull
        public String toString() {
            return String.format(Locale.getDefault(), "%s/%d", Address, PrefixLength);
        }
    }
}
