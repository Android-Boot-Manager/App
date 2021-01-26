package org.androidbootmanager.app.roms;

import android.os.strictmode.IntentReceiverLeakedViolation;

import java.util.Map;

public class ROM {
    public String viewname;
    public String scriptname;
    public String fullPath;
    public Map<String, String> requiredFiles;
    public Map<Integer, String> parts;
}
