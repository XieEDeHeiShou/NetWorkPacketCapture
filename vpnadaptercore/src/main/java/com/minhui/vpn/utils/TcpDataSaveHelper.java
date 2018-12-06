package com.minhui.vpn.utils;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.minhui.vpn.VPNLog;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * @author minhui.zhu
 * Created by minhui.zhu on 2018/5/7.
 * Copyright © 2017年 Oceanwing. All rights reserved.
 */

public class TcpDataSaveHelper {
    private static final String TAG = "TcpDataSaveHelper";
    private static final String REQUEST = "request";
    private static final String RESPONSE = "response";
    @NonNull
    private final String dir;
    private SaveData lastSaveData;
    private File lastSaveFile;
    private int requestNum = 0;
    private int responseNum = 0;

    public TcpDataSaveHelper(@NonNull String dir) {
        this.dir = dir;
    }

    public void addData(@NonNull final SaveData data) {
        ThreadProxy.getInstance().execute(new Runnable() {
            @Override
            public void run() {
                if (lastSaveData == null || (lastSaveData.isRequest ^ data.isRequest)) {
                    newFileAndSaveData(data);
                } else {
                    appendFileData(data);
                }
                lastSaveData = data;
            }
        });

    }

    private void appendFileData(@NonNull SaveData data) {
        RandomAccessFile randomAccessFile;
        try {
            randomAccessFile = new RandomAccessFile(lastSaveFile.getAbsolutePath(), "rw");
            long length = randomAccessFile.length();
            randomAccessFile.seek(length);
            randomAccessFile.write(data.needParseData, data.offSet, data.playoffSize);
        } catch (IOException ignore) {
        }
    }

    private void close(@Nullable Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception e) {
                VPNLog.d(TAG, "failed to close closeable");
            }
        }
    }

    private void newFileAndSaveData(@NonNull SaveData data) {
        int saveNum;
        if (data.isRequest) {
            saveNum = requestNum;
            requestNum++;
        } else {
            saveNum = responseNum;
            responseNum++;
        }
        File file = new File(dir);
        if (!file.exists()) {
            //noinspection ResultOfMethodCallIgnored
            file.mkdirs();
        }
        String childName = (data.isRequest ? REQUEST : RESPONSE) + saveNum;
        lastSaveFile = new File(file, childName);
        FileOutputStream fileOutputStream = null;

        try {
            fileOutputStream = new FileOutputStream(lastSaveFile);
            fileOutputStream.write(data.needParseData, data.offSet, data.playoffSize);
            fileOutputStream.flush();
        } catch (Exception e) {
            VPNLog.d(TAG, "failed to saveData" + e.getMessage());
        } finally {
            close(fileOutputStream);
        }

    }

    public static class SaveData {
        boolean isRequest;
        byte[] needParseData;
        int offSet;
        int playoffSize;

        private SaveData(@NonNull Builder builder) {
            isRequest = builder.isRequest;
            needParseData = builder.needParseData;
            offSet = builder.offSet;
            playoffSize = builder.length;
        }


        public static final class Builder {
            private boolean isRequest;
            private byte[] needParseData;
            private int offSet;
            private int length;

            public Builder() {
            }

            @NonNull
            public Builder isRequest(boolean val) {
                isRequest = val;
                return this;
            }

            @NonNull
            public Builder needParseData(@NonNull byte[] val) {
                needParseData = val;
                return this;
            }

            @NonNull
            public Builder offSet(int val) {
                offSet = val;
                return this;
            }

            @NonNull
            public Builder length(int val) {
                length = val;
                return this;
            }

            @NonNull
            public SaveData build() {
                return new SaveData(this);
            }
        }
    }
}
