package com.minhui.vpn.processparse;

import android.annotation.SuppressLint;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 通过读取 /proc/net 目录下的一系列文件, 将 source port 与 uid 进行映射
 *
 * @see <a href="http://lkml.iu.edu/hypermail/linux/kernel/0409.1/2166.html">/proc/net/tcp documentation</a>
 * @see <a href="http://www.tldp.org/LDP/Linux-Filesystem-Hierarchy/html/proc.html">Linux-Filesystem-Hierarchy proc</a>
 */
public class NetFileManager {
    private final static int TYPE_TCP = 0;
    private final static int TYPE_TCP6 = 1;
    private final static int TYPE_UDP = 2;
    private final static int TYPE_UDP6 = 3;
    private final static int TYPE_RAW = 4;
    private final static int TYPE_RAW6 = 5;
    private final static int TYPE_MAX = 6;
    private final static int DATA_LOCAL = 2;
    private final static int DATA_REMOTE = 3;
    private final static int DATA_UID = 8;
    private Map<Integer, Integer> processHost = new ConcurrentHashMap<>();
    private File[] file;
    private long[] lastTime;
    private StringBuilder sbBuilder = new StringBuilder();

    public static NetFileManager getInstance() {
        return InnerClass.instance;
    }

    void init() {
        final String PATH_TCP = "/proc/net/tcp";
        final String PATH_TCP6 = "/proc/net/tcp6";
        final String PATH_UDP = "/proc/net/udp";
        final String PATH_UDP6 = "/proc/net/udp6";
        final String PATH_RAW = "/proc/net/raw";
        final String PATH_RAW6 = "/proc/net/raw6";

        file = new File[TYPE_MAX];
        file[0] = new File(PATH_TCP);
        file[1] = new File(PATH_TCP6);
        file[2] = new File(PATH_UDP);
        file[3] = new File(PATH_UDP6);
        file[4] = new File(PATH_RAW);
        file[5] = new File(PATH_RAW6);

        lastTime = new long[TYPE_MAX];
        Arrays.fill(lastTime, 0);
    }

    public void execute(@Nullable String[] cmd, @Nullable String directory, int type) throws IOException {
        NetInfo netInfo;
        String sTmp;

        ProcessBuilder builder = new ProcessBuilder(cmd);

        if (directory != null) {
            builder.directory(new File(directory));
        }
        builder.redirectErrorStream(true);
        Process process = builder.start();
        InputStream is = process.getInputStream();

        Scanner s = new Scanner(is);
        s.useDelimiter("\n");
        while (s.hasNextLine()) {
            sTmp = s.nextLine();
            netInfo = parseDataNew(sTmp);
            if (netInfo != null) {
                netInfo.setType(type);
                saveToMap(netInfo);
            }
        }
    }

    private int strToInt(@Nullable String value, int iHex) {
        int iValue = 0;
        if (value == null) {
            return iValue;
        }

        try {
            iValue = Integer.parseInt(value, iHex);
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }

        return iValue;
    }

    private long strToLong(@Nullable String value) {
        long iValue = 0;
        if (value == null) {
            return iValue;
        }

        try {
            iValue = Long.parseLong(value, 16);
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }

        return iValue;
    }

    @Nullable
    private NetInfo parseDataNew(@NonNull String sData) {
        String sSplitItem[] = sData.split("\\s+");
        String sTmp;
        if (sSplitItem.length < 9) {
            return null;
        }

        NetInfo netInfo = new NetInfo();

        sTmp = sSplitItem[DATA_LOCAL];
        String sSourceItem[] = sTmp.split(":");
        if (sSourceItem.length < 2) {
            return null;
        }
        netInfo.setSourPort(strToInt(sSourceItem[1], 16));

        sTmp = sSplitItem[DATA_REMOTE];
        String sDesItem[] = sTmp.split(":");
        if (sDesItem.length < 2) {
            return null;
        }
        netInfo.setPort(strToInt(sDesItem[1], 16));


        sTmp = sDesItem[0];
        int len = sTmp.length();
        if (len < 8) {
            return null;
        }

        sTmp = sTmp.substring(len - 8);
        netInfo.setIp(strToLong(sTmp));

        sbBuilder.setLength(0);
        sbBuilder.append(strToInt(sTmp.substring(6, 8), 16))
                .append(".")
                .append(strToInt(sTmp.substring(4, 6), 16))
                .append(".")
                .append(strToInt(sTmp.substring(2, 4), 16))
                .append(".")
                .append(strToInt(sTmp.substring(0, 2), 16));

        sTmp = sbBuilder.toString();
        netInfo.setAddress(sTmp);

        if (sTmp.equals("0.0.0.0")) {
            return null;
        }

        sTmp = sSplitItem[DATA_UID];
        netInfo.setUid(strToInt(sTmp, 10));

        return netInfo;
    }

    private void saveToMap(@Nullable NetInfo netInfo) {
        if (netInfo == null) {
            return;
        }
        processHost.put(netInfo.getSourPort(), netInfo.getUid());
    }

    public void read(int type) {
        try {
            switch (type) {
                case TYPE_TCP:
                    String[] ARGS = {"cat", "/proc/net/tcp"};
                    execute(ARGS, "/", TYPE_TCP);

                    break;
                case TYPE_TCP6:
                    String[] ARGS1 = {"cat", "/proc/net/tcp6"};
                    execute(ARGS1, "/", TYPE_TCP6);
                    break;
                case TYPE_UDP:
                    String[] ARGS2 = {"cat", "/proc/net/udp"};
                    execute(ARGS2, "/", TYPE_UDP);
                    break;
                case TYPE_UDP6:
                    String[] ARGS3 = {"cat", "/proc/net/udp6"};
                    execute(ARGS3, "/", TYPE_UDP6);
                    break;
                case TYPE_RAW:
                    String[] ARGS4 = {"cat", "/proc/net/raw"};
                    execute(ARGS4, "/", TYPE_UDP);
                    break;
                case TYPE_RAW6:
                    String[] ARGS5 = {"cat", "/proc/net/raw6"};
                    execute(ARGS5, "/", TYPE_UDP6);
                    break;

                default:
                    break;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void refresh() {
        for (int i = 0; i < TYPE_MAX; i++) {
            long iTime = file[i].lastModified();
            if (iTime != lastTime[i]) {
                read(i);
                lastTime[i] = iTime;
            }
        }
    }

    @Nullable
    Integer getUid(int port) {
        return processHost.get(port);
    }

    static class InnerClass {
        @SuppressLint("StaticFieldLeak")
        static NetFileManager instance = new NetFileManager();
    }
}
