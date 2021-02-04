package org.androidbootmanager.app.util;

import android.annotation.SuppressLint;

public class Constants {
    @SuppressLint("SdCardPath")
    public static final String appDir = "/data/data/org.androidbootmanager.app/";
    public static final String assetDir = appDir + "assets/";
    public static final String filesDir = appDir + "files/";
    public static final String tempDir = appDir + "cache/";
    public static final String scriptDir = assetDir + "Scripts/";
}
