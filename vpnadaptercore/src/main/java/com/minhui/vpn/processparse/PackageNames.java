package com.minhui.vpn.processparse;

import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.io.Serializable;

/**
 * @author minhui.zhu
 * Created by minhui.zhu on 2018/4/30.
 * Copyright © 2017年 Oceanwing. All rights reserved.
 */

public class PackageNames implements Parcelable, Serializable {
    public static final Creator<PackageNames> CREATOR = new Creator<PackageNames>() {
        @Override
        public PackageNames createFromParcel(Parcel in) {
            return new PackageNames(in);
        }

        @Override
        public PackageNames[] newArray(int size) {
            return new PackageNames[size];
        }
    };
    private final String[] pkgs;

    private PackageNames(@NonNull String[] pkgs) {
        this.pkgs = pkgs;
    }

    private PackageNames(@NonNull Parcel in) {
        this.pkgs = new String[in.readInt()];
        in.readStringArray(this.pkgs);
    }

    @NonNull
    static PackageNames newInstance(@NonNull String[] pkgs) {
        return new PackageNames(pkgs);
    }

    @Nullable
    public String getAt(int i) {
        if (this.pkgs.length > i) {
            return this.pkgs[i];
        }
        return null;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(this.pkgs.length);
        dest.writeStringArray(this.pkgs);
    }
}
