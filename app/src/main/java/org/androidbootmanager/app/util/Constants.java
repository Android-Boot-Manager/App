package org.androidbootmanager.app.util;

import android.annotation.SuppressLint;

public class Constants {
    @SuppressLint("SdCardPath")
    public static String appDir = "/data/data/org.androidbootmanager.app/";
    public static String assetDir = appDir + "assets/";
    public static String filesDir = appDir + "files/";
    public static String tempDir = appDir + "cache/";
    public static String scriptDir = assetDir + "Scripts/";
}
