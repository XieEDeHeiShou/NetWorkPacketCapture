package com.minhui.vpn.service;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.support.annotation.NonNull;

import com.minhui.vpn.Packet;
import com.minhui.vpn.ProxyConfig;
import com.minhui.vpn.R;
import com.minhui.vpn.UDPServer;
import com.minhui.vpn.VPNLog;
import com.minhui.vpn.http.HttpRequestHeaderParser;
import com.minhui.vpn.nat.NatSession;
import com.minhui.vpn.nat.NatSessionManager;
import com.minhui.vpn.processparse.PortHostService;
import com.minhui.vpn.proxy.TcpProxyServer;
import com.minhui.vpn.tcpip.IPHeader;
import com.minhui.vpn.tcpip.TCPHeader;
import com.minhui.vpn.utils.AppDebug;
import com.minhui.vpn.utils.CommonMethods;
import com.minhui.vpn.utils.DebugLog;
import com.minhui.vpn.utils.ThreadProxy;
import com.minhui.vpn.utils.TimeFormatUtil;
import com.minhui.vpn.utils.VpnServiceHelper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedQueue;

import static com.minhui.vpn.VPNConstants.DEFAULT_PACKAGE_ID;
import static com.minhui.vpn.VPNConstants.VPN_SP_NAME;


/**
 * Created by zengzheying on 15/12/28.
 */
public class FirewallVpnService extends VpnService implements Runnable {
    @SuppressWarnings("unused")
    public static final String ACTION_START_VPN = "com.minhui.START_VPN";
    @SuppressWarnings("unused")
    public static final String ACTION_CLOSE_VPN = "com.minhui.roav.CLOSE_VPN";
    @SuppressWarnings("unused")
    public static final String BROADCAST_VPN_STATE = "com.minhui.localvpn.VPN_STATE";
    @SuppressWarnings("unused")
    public static final String SELECT_PACKAGE_ID = "select_protect_package_id";
    public static final int MUTE_SIZE = 2560;// maximum transmission unit: 超过该长度的内容将被分包
    @SuppressWarnings("unused")
    private static final String FACEBOOK_APP = "com.facebook.katana";
    @SuppressWarnings("unused")
    private static final String YOUTUBE_APP = "com.google.android.youtube";
    @SuppressWarnings("unused")
    private static final String GOOGLE_MAP_APP = "com.google.android.apps.maps";
    @SuppressWarnings("unused")
    private static final String VPN_ADDRESS = "10.0.0.2"; // Only IPv4 support for now
    private static final String VPN_ROUTE = "0.0.0.0"; // Intercept everything
    private static final String GOOGLE_DNS_FIRST = "8.8.8.8";
    private static final String GOOGLE_DNS_SECOND = "8.8.4.4";
    private static final String AMERICA = "208.67.222.222";
    @SuppressWarnings("unused")
    private static final String HK_DNS_SECOND = "205.252.144.228";
    private static final String CHINA_DNS_FIRST = "114.114.114.114";
    private static final String TAG = "FirewallVpnService";
    private static final boolean DEBUG_TCP_IN = false;
    private static final boolean DEBUG_TCP_OUT = false;
    private static final boolean DEBUG_UDP_IN = false;
    private static final boolean DEBUG_UDP_OUT = false;
    public static long vpnStartTime;
    public static String lastVpnStartTimeFormat = null;
    private static int ID;
    private static int LOCAL_IP;
    private boolean IsRunning = false;
    private Thread mVPNThread;
    private ParcelFileDescriptor mVPNInterface;
    private TcpProxyServer mTcpProxyServer;
    // private DnsProxy mDnsProxy;
    private FileOutputStream mVPNOutputStream;
    private byte[] mPacket;
    private IPHeader mIPHeader;
    private TCPHeader mTCPHeader;
    private ConcurrentLinkedQueue<Packet> udpQueue;
    private UDPServer udpServer;
    private SharedPreferences sp;

    public FirewallVpnService() {
        ID++;
        mPacket = new byte[MUTE_SIZE];
        mIPHeader = new IPHeader(mPacket, 0);
        //Offset = ip报文头部长度
        mTCPHeader = new TCPHeader(mPacket, 20);

        DebugLog.i("New VPNService(%d)\n", ID);
    }

    //启动Vpn工作线程
    @Override
    public void onCreate() {
        DebugLog.i("VPNService(%s) created.\n", ID);
        sp = getSharedPreferences(VPN_SP_NAME, Context.MODE_PRIVATE);
        VpnServiceHelper.onVpnServiceCreated(this);
        mVPNThread = new Thread(this, "VPNServiceThread");
        mVPNThread.start();
        setVpnRunningStatus(true);
        super.onCreate();

        if (DEBUG_TCP_IN || DEBUG_TCP_OUT) {
            File sample = getExternalFilesDir("packet/tcp");
            if (sample != null) {
                if (!sample.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    sample.mkdirs();
                }
            }
        }
        if (DEBUG_UDP_IN || DEBUG_UDP_OUT) {
            File sample = getExternalFilesDir("packet/udp");
            if (sample != null) {
                if (!sample.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    sample.mkdirs();
                }
            }
        }
    }

    //停止Vpn工作线程
    @Override
    public void onDestroy() {
        DebugLog.i("VPNService(%s) destroyed.\n", ID);
        if (mVPNThread != null) {
            mVPNThread.interrupt();
        }
        VpnServiceHelper.onVpnServiceDestroy();
        super.onDestroy();
    }

    //建立VPN，同时监听出口流量
    private void runVPN() throws Exception {
        this.mVPNInterface = establishVPN();
        startStream();
    }

    private void startStream() throws Exception {
        int size = 0;
        mVPNOutputStream = new FileOutputStream(mVPNInterface.getFileDescriptor());
        FileInputStream in = new FileInputStream(mVPNInterface.getFileDescriptor());
        while (size != -1 && IsRunning) {
            boolean hasTcpOutput = false;
            size = in.read(mPacket);
            if (size > 0) {
                if (mTcpProxyServer.isStopped()) {
                    in.close();
                    throw new Exception("LocalServer stopped.");
                }
                hasTcpOutput = onIPPacketReceived(mIPHeader, size);
            }
            if (!hasTcpOutput) {
                Packet packet = udpQueue.poll();
                if (packet != null) {
                    ByteBuffer bufferFromNetwork = packet.backingBuffer;
                    bufferFromNetwork.flip();
                    byte[] array = bufferFromNetwork.array();
                    mVPNOutputStream.write(array);

                    if (DEBUG_UDP_OUT) {
                        FileOutputStream debugOutput = new FileOutputStream(new File(getExternalFilesDir("packet/udp"), "out-" + System.currentTimeMillis() + ".bin"));
                        debugOutput.write(array);
                        debugOutput.close();
                    }
                }
            }
            Thread.sleep(10);
        }
        in.close();
        disconnectVPN();
    }

    /**
     * @return has tcp output
     */
    private boolean onIPPacketReceived(@NonNull IPHeader ipHeader, int size) throws IOException {
        switch (ipHeader.getProtocol()) {
            case IPHeader.TCP:
                return onTcpPacketReceived(ipHeader, size);
            case IPHeader.UDP:
                onUdpPacketReceived(ipHeader, size);
            default:
                return false;
        }
    }

    private void onUdpPacketReceived(@NonNull IPHeader ipHeader, int size) throws IOException {
        if (DEBUG_UDP_IN) {
            FileOutputStream debugOutput = new FileOutputStream(new File(getExternalFilesDir("sample/udp"), "in-" + System.currentTimeMillis() + ".bin"));
            debugOutput.write(ipHeader.mData, 0, size);
            debugOutput.close();
        }
        TCPHeader tcpHeader = mTCPHeader;
        short portKey = tcpHeader.getSourcePort();

        NatSession session = NatSessionManager.getSession(portKey);
        if (session == null || session.remoteIP != ipHeader.getDestinationIP() || session.remotePort
                != tcpHeader.getDestinationPort()) {
            session = NatSessionManager.createSession(portKey, ipHeader.getDestinationIP(), tcpHeader
                    .getDestinationPort(), NatSession.UDP);
            session.vpnStartTime = vpnStartTime;
            ThreadProxy.getInstance().execute(new Runnable() {
                @Override
                public void run() {
                    if (PortHostService.getInstance() != null) {
                        PortHostService.getInstance().refreshSessionInfo();
                    }
                }
            });
        }

        session.lastRefreshTime = System.currentTimeMillis();
        session.packetSent++; //注意顺序

        byte[] bytes = Arrays.copyOf(mPacket, mPacket.length);
        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes, 0, size);
        byteBuffer.limit(size);
        Packet packet = new Packet(byteBuffer);
        udpServer.processUDPPacket(packet, portKey);
    }

    private boolean onTcpPacketReceived(@NonNull IPHeader ipHeader, int size) throws IOException {
        if (DEBUG_TCP_IN) {
            FileOutputStream debugOutput = new FileOutputStream(new File(getExternalFilesDir("packet/tcp"), "in-" + System.currentTimeMillis() + ".bin"));
            debugOutput.write(ipHeader.mData, 0, size);
            debugOutput.close();
        }
        TCPHeader tcpHeader = mTCPHeader;
        //矫正TCPHeader里的偏移量，使它指向真正的TCP数据地址
        tcpHeader.mOffset = ipHeader.getHeaderLength();
        if (tcpHeader.getSourcePort() == mTcpProxyServer.getPort()) {
            VPNLog.d(TAG, "tcp packet from net ");
            NatSession session = NatSessionManager.getSession(tcpHeader.getDestinationPort());
            if (session != null) {
                ipHeader.setSourceIP(ipHeader.getDestinationIP());
                tcpHeader.setSourcePort(session.remotePort);
                ipHeader.setDestinationIP(LOCAL_IP);

                CommonMethods.ComputeTCPChecksum(ipHeader, tcpHeader);
                if (DEBUG_TCP_OUT) {
                    FileOutputStream debugOutput = new FileOutputStream(new File(getExternalFilesDir("packet/tcp"), "out-" + System.currentTimeMillis() + ".bin"));
                    debugOutput.write(ipHeader.mData, 0, size);
                    debugOutput.close();
                }
                mVPNOutputStream.write(ipHeader.mData, ipHeader.mOffset, size);
            } else {
                DebugLog.i("NoSession: %s %s\n", ipHeader.toString(), tcpHeader.toString());
            }
        } else {
            //添加端口映射
            short portKey = tcpHeader.getSourcePort();
            // 通过端口号获取 Nat 会话, Nat: 网络地址转换 https://en.wikipedia.org/wiki/Network_address_translation
            NatSession session = NatSessionManager.getSession(portKey);
            if (session == null || session.remoteIP != ipHeader.getDestinationIP() ||
                    session.remotePort != tcpHeader.getDestinationPort()) {
                // 暂无会话, 或会话目的地不一致, 则新建会话
                session = NatSessionManager.createSession(portKey, ipHeader.getDestinationIP(), tcpHeader
                        .getDestinationPort(), NatSession.TCP);
                session.vpnStartTime = vpnStartTime;// 记录该会话源自于哪一次 vpn 服务, 用于抓包记录
                ThreadProxy.getInstance().execute(new Runnable() {
                    @Override
                    public void run() {
                        PortHostService instance = PortHostService.getInstance();
                        if (instance != null) {
                            instance.refreshSessionInfo();
                        }
                    }
                });
            }

            session.lastRefreshTime = System.currentTimeMillis();
            session.packetSent++; //注意顺序
            int tcpDataSize = ipHeader.getDataLength() - tcpHeader.getHeaderLength();
            //丢弃tcp握手的第二个ACK报文。因为客户端发数据的时候也会带上ACK，这样可以在服务器Accept之前分析出HOST信息。
            if (session.packetSent == 2 && tcpDataSize == 0) {
                VPNLog.d(TAG, "tcp packet to net without payload");
                return false;
            }
            VPNLog.d(TAG, "tcp packet to net with payload");

            //分析数据，找到host
            if (session.bytesSent == 0 && tcpDataSize > 10) {// magic 10
                int dataOffset = tcpHeader.mOffset + tcpHeader.getHeaderLength();
                HttpRequestHeaderParser.parseHttpRequestHeader(session, tcpHeader.mData, dataOffset,
                        tcpDataSize);
                DebugLog.i("Host: %s\n", session.remoteHost);
                DebugLog.i("Request: %s %s\n", session.method, session.requestUrl);
            } else if (session.bytesSent > 0
                    && !session.isHttpsSession
                    && session.isHttp
                    && session.remoteHost == null
                    && session.requestUrl == null) {
                int dataOffset = tcpHeader.mOffset + tcpHeader.getHeaderLength();
                session.remoteHost = HttpRequestHeaderParser.extractRemoteHost(tcpHeader.mData, dataOffset,
                        tcpDataSize);
                session.requestUrl = "http://" + session.remoteHost + "/" + session.pathUrl;
            }

            //转发给本地TCP服务器
            ipHeader.setSourceIP(ipHeader.getDestinationIP());
            ipHeader.setDestinationIP(LOCAL_IP);
            tcpHeader.setDestinationPort(mTcpProxyServer.getPort());

            CommonMethods.ComputeTCPChecksum(ipHeader, tcpHeader);

            if (DEBUG_TCP_OUT) {
                FileOutputStream debugOutput = new FileOutputStream(new File(getExternalFilesDir("sample/tcp"), "out-" + System.currentTimeMillis() + ".bin"));
                debugOutput.write(ipHeader.mData, 0, size);
                debugOutput.close();
            }
            mVPNOutputStream.write(ipHeader.mData, ipHeader.mOffset, size);
            //注意顺序
            session.bytesSent += tcpDataSize;
        }
        return true;
    }

    private void waitUntilPrepared() {
        while (prepare(this) != null) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                if (AppDebug.IS_DEBUG) {
                    e.printStackTrace();
                }
                DebugLog.e("waitUntilPrepared catch an exception %s\n", e);
            }
        }
    }

    private ParcelFileDescriptor establishVPN() {
        Builder builder = new Builder();
        builder.setMtu(MUTE_SIZE);// maximum transmission unit
        String selectPackage = sp.getString(DEFAULT_PACKAGE_ID, null);
        DebugLog.i("setMtu: %d\n", MUTE_SIZE);

        ProxyConfig.IPAddress ipAddress = ProxyConfig.Instance.getDefaultLocalIP();
        LOCAL_IP = CommonMethods.ipStringToInt(ipAddress.Address);
        builder.addAddress(ipAddress.Address, ipAddress.PrefixLength);
        DebugLog.i("addAddress: %s/%d\n", ipAddress.Address, ipAddress.PrefixLength);

        builder.addRoute(VPN_ROUTE, 0);

        builder.addDnsServer(GOOGLE_DNS_FIRST);
        builder.addDnsServer(CHINA_DNS_FIRST);
        builder.addDnsServer(GOOGLE_DNS_SECOND);
        builder.addDnsServer(AMERICA);
        vpnStartTime = System.currentTimeMillis();
        lastVpnStartTimeFormat = TimeFormatUtil.formatYYMMDDHHMMSS(vpnStartTime);
        try {
            if (selectPackage != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    builder.addAllowedApplication(selectPackage);
                    builder.addAllowedApplication(getPackageName());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        builder.setSession(getString(R.string.app_name));
        return builder.establish();
    }

    @Override
    public void run() {
        try {
            DebugLog.i("VPNService(%s) work thread is Running...\n", ID);

            waitUntilPrepared();
            udpQueue = new ConcurrentLinkedQueue<>();

            //启动TCP代理服务
            mTcpProxyServer = new TcpProxyServer();
            mTcpProxyServer.start();
            udpServer = new UDPServer(this, udpQueue);
            udpServer.start();
            NatSessionManager.clearAllSession();
            if (PortHostService.getInstance() != null) {
                PortHostService.start(getApplicationContext());
            }
            DebugLog.i("DnsProxy started.\n");

            ProxyConfig.Instance.onVpnStart(this);
            while (IsRunning) {
                runVPN();
            }
        } catch (Exception e) {
            if (AppDebug.IS_DEBUG) {
                e.printStackTrace();
            }
            DebugLog.e("VpnService run catch an exception %s.\n", e);
        } finally {
            DebugLog.i("VpnService terminated");
            ProxyConfig.Instance.onVpnEnd(this);
            dispose();
        }
    }

    public void disconnectVPN() {
        try {
            if (mVPNInterface != null) {
                mVPNInterface.close();
                mVPNInterface = null;
            }
        } catch (Exception ignore) {
        }
        this.mVPNOutputStream = null;
    }

    private synchronized void dispose() {
        try {
            //断开VPN
            disconnectVPN();

            //停止TCP代理服务
            if (mTcpProxyServer != null) {
                mTcpProxyServer.stop();
                mTcpProxyServer = null;
                DebugLog.i("TcpProxyServer stopped.\n");
            }
            if (udpServer != null) {
                udpServer.closeAllUDPConn();
            }
            ThreadProxy.getInstance().execute(new Runnable() {
                @Override
                public void run() {
                    if (PortHostService.getInstance() != null) {
                        PortHostService.getInstance().refreshSessionInfo();
                    }
                    PortHostService.stop(getApplicationContext());
                }
            });
            stopSelf();
            setVpnRunningStatus(false);
        } catch (Exception ignore) {
        }
    }

    public boolean vpnRunningStatus() {
        return IsRunning;
    }

    public void setVpnRunningStatus(boolean isRunning) {
        IsRunning = isRunning;
    }
}
