package org.androidbootmanager.app.roms;

import android.os.strictmode.IntentReceiverLeakedViolation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ROM {
    public String viewname;
    public String scriptname;
    public String fullPath;
    public Map<String, String> requiredFiles;
    public ArrayList<String> parts;
}
