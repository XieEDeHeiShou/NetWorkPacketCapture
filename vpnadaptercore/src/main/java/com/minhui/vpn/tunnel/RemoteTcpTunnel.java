package com.minhui.vpn.tunnel;

import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;

import com.minhui.vpn.VPNConstants;
import com.minhui.vpn.nat.NatSession;
import com.minhui.vpn.nat.NatSessionManager;
import com.minhui.vpn.processparse.PortHostService;
import com.minhui.vpn.utils.ACache;
import com.minhui.vpn.utils.TcpDataSaveHelper;
import com.minhui.vpn.utils.ThreadProxy;
import com.minhui.vpn.utils.TimeFormatUtil;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Selector;

/**
 * Created by zengzheying on 15/12/31.
 */
public class RemoteTcpTunnel extends RawTcpTunnel {
    private final TcpDataSaveHelper helper;
    private final NatSession session;
    private final Handler handler;

    public RemoteTcpTunnel(@NonNull InetSocketAddress serverAddress, @NonNull Selector selector, short portKey) throws IOException {
        super(serverAddress, selector, portKey);
        session = NatSessionManager.getSession(portKey);
        if (session == null) {
            throw new IllegalStateException("No NAT session is mapped to " + portKey);
        }

        String helperDir = VPNConstants.DATA_DIR +
                TimeFormatUtil.formatYYMMDDHHMMSS(session.vpnStartTime) +
                "/" +
                session.getUniqueName();

        helper = new TcpDataSaveHelper(helperDir);
        handler = new Handler(Looper.getMainLooper());
    }


    @Override
    protected void afterReceived(ByteBuffer buffer) throws Exception {
        super.afterReceived(buffer);
        session.receivePacketNum++;
        session.receiveByteNum += buffer.limit();
        TcpDataSaveHelper.SaveData saveData = new TcpDataSaveHelper
                .SaveData
                .Builder()
                .isRequest(false)
                .needParseData(buffer.array())
                .length(buffer.limit())
                .offSet(0)
                .build();
        helper.addData(saveData);
    }

    @Override
    protected void beforeSend(ByteBuffer buffer) throws Exception {
        super.beforeSend(buffer);
        TcpDataSaveHelper.SaveData saveData = new TcpDataSaveHelper
                .SaveData
                .Builder()
                .isRequest(true)
                .needParseData(buffer.array())
                .length(buffer.limit())
                .offSet(0)
                .build();
        helper.addData(saveData);
        refreshAppInfo();
    }

    private void refreshAppInfo() {
        if (session.appInfo != null) {
            return;
        }
        if (PortHostService.getInstance() != null) {
            ThreadProxy.getInstance().execute(new Runnable() {
                @Override
                public void run() {
                    PortHostService.getInstance().refreshSessionInfo();
                }
            });
        }
    }

    @Override
    protected void onDispose() {
        super.onDispose();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                ThreadProxy.getInstance().execute(new Runnable() {
                    @Override
                    public void run() {
                        if (session.receiveByteNum == 0 && session.bytesSent == 0) {
                            return;
                        }

                        String configFileDir = VPNConstants.CONFIG_DIR
                                + TimeFormatUtil.formatYYMMDDHHMMSS(session.vpnStartTime);
                        File parentFile = new File(configFileDir);
                        if (!parentFile.exists()) {
                            //noinspection ResultOfMethodCallIgnored
                            parentFile.mkdirs();
                        }
                        //说已经存了
                        File file = new File(parentFile, session.getUniqueName());
                        if (file.exists()) {
                            return;
                        }
                        ACache configACache = ACache.get(parentFile);
                        configACache.put(session.getUniqueName(), session);
                    }
                });
            }
        }, 1000);
    }
}
