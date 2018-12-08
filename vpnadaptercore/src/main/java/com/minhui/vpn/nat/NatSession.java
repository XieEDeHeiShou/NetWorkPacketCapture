package com.minhui.vpn.nat;


import android.support.annotation.NonNull;

import com.minhui.vpn.processparse.AppInfo;
import com.minhui.vpn.utils.CommonMethods;

import java.io.Serializable;
import java.util.Locale;

/**
 * @author zengzheying on 15/12/29.
 * @see <a href="https://en.wikipedia.org/wiki/Network_address_translation">wiki: Network address translation</a>
 */
public class NatSession implements Serializable {
    public static final String TCP = "TCP";
    public static final String UDP = "UPD";
    public String type;
    public short remotePort;
    public String remoteHost;
    public short localPort;
    public int bytesSent;
    public int packetSent;
    public long receiveByteNum;
    public long receivePacketNum;
    public long lastRefreshTime;
    public boolean isHttpsSession;
    public String pathUrl;
    public String method;
    public AppInfo appInfo;
    public long vpnStartTime;
    private String requestUrl;
    private boolean isHttp;
    private int remoteIP;
    private long connectionStartTime = System.currentTimeMillis();
    private String ipAndPort;

    @NonNull
    @Override
    public String toString() {
        return String.format(Locale.getDefault(), "%s/%s:%d packet: %d", remoteHost,
                CommonMethods.ipIntToString(remoteIP), remotePort & 0xFFFF, packetSent);
    }

    public String getUniqueName() {
        String uinID = ipAndPort + connectionStartTime;
        return String.valueOf(uinID.hashCode());
    }

    void refreshIpAndPort() {
        int remoteIPStr1 = (remoteIP & 0XFF000000) >> 24 & 0XFF;
        int remoteIPStr2 = (remoteIP & 0X00FF0000) >> 16;
        int remoteIPStr3 = (remoteIP & 0X0000FF00) >> 8;
        int remoteIPStr4 = remoteIP & 0X000000FF;
        String remoteIPStr = "" + remoteIPStr1 + ":" + remoteIPStr2 + ":" + remoteIPStr3 + ":" + remoteIPStr4;
        ipAndPort = type + ":" + remoteIPStr + ":" + remotePort + " " + ((int) localPort & 0XFFFF);
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getIpAndPort() {
        return ipAndPort;
    }

    public void setIpAndPort(String ipAndPort) {
        this.ipAndPort = ipAndPort;
    }

    public int getRemoteIP() {
        return remoteIP;
    }

    public void setRemoteIP(int remoteIP) {
        this.remoteIP = remoteIP;
    }

    public short getRemotePort() {
        return remotePort;
    }

    public void setRemotePort(short remotePort) {
        this.remotePort = remotePort;
    }

    public String getRemoteHost() {
        return remoteHost;
    }

    public void setRemoteHost(String remoteHost) {
        this.remoteHost = remoteHost;
    }

    public short getLocalPort() {
        return localPort;
    }

    public void setLocalPort(short localPort) {
        this.localPort = localPort;
    }

    public int getBytesSent() {
        return bytesSent;
    }

    public void setBytesSent(int bytesSent) {
        this.bytesSent = bytesSent;
    }

    public int getPacketSent() {
        return packetSent;
    }

    public void setPacketSent(int packetSent) {
        this.packetSent = packetSent;
    }

    public long getReceiveByteNum() {
        return receiveByteNum;
    }

    public void setReceiveByteNum(long receiveByteNum) {
        this.receiveByteNum = receiveByteNum;
    }

    public long getReceivePacketNum() {
        return receivePacketNum;
    }

    public void setReceivePacketNum(long receivePacketNum) {
        this.receivePacketNum = receivePacketNum;
    }

    public long getRefreshTime() {
        return lastRefreshTime;
    }

    public boolean isHttpsSession() {
        return isHttpsSession;
    }

    public void setHttpsSession(boolean httpsSession) {
        isHttpsSession = httpsSession;
    }

    public String getRequestUrl() {
        return requestUrl;
    }

    public void setRequestUrl(String requestUrl) {
        this.requestUrl = requestUrl;
    }

    public String getPathUrl() {
        return pathUrl;
    }

    public void setPathUrl(String pathUrl) {
        this.pathUrl = pathUrl;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public AppInfo getAppInfo() {
        return appInfo;
    }

    public void setAppInfo(AppInfo appInfo) {
        this.appInfo = appInfo;
    }

    public long getConnectionStartTime() {
        return connectionStartTime;
    }

    public void setConnectionStartTime(long connectionStartTime) {
        this.connectionStartTime = connectionStartTime;
    }

    public long getVpnStartTime() {
        return vpnStartTime;
    }

    public void setVpnStartTime(long vpnStartTime) {
        this.vpnStartTime = vpnStartTime;
    }

    public long getLastRefreshTime() {
        return lastRefreshTime;
    }

    public void setLastRefreshTime(long lastRefreshTime) {
        this.lastRefreshTime = lastRefreshTime;
    }

    public boolean isHttp() {
        return isHttp;
    }

    public void setHttp(boolean http) {
        isHttp = http;
    }

    public static class NatSessionComparator implements java.util.Comparator<NatSession> {

        @Override
        public int compare(NatSession o1, NatSession o2) {
            if (o1 == o2) {
                return 0;
            }
            return Long.compare(o2.lastRefreshTime, o1.lastRefreshTime);
        }
    }
}
