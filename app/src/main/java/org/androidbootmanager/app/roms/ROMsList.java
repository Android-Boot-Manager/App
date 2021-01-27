package org.androidbootmanager.app.roms;

import android.annotation.SuppressLint;
import android.content.Context;

import com.topjohnwu.superuser.io.SuFile;

import org.androidbootmanager.app.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ROMsList {
    public String codename;
    public Context c;

    public ROMsList(String codename, Context context) {
        this.codename = codename;
        c = context;
    }

    @SuppressLint("SdCardPath")
    public List<ROM> getROMs() {
        ArrayList<ROM> l = new ArrayList<>();
        for (String sn : SuFile.open("/data/data/org.androidbootmanager.app/assets/Scripts/add_os/" + codename + "/").list()) {
            ROM r = new ROM();
            r.scriptname = sn;
            r.fullPath = "/data/data/org.androidbootmanager.app/assets/Scripts/add_os/" + codename + "/" + r.scriptname;
            switch (r.scriptname) {
                case "add_ubuntutouch_systemimage_haliumboot.sh":
                case "add_ubuntutouch_sytemimage_haliumboot.sh": // this is considered deprecated
                    r.viewname = c.getString(R.string.rom_type_add_ut_sysimg_halium);
                    r.requiredFiles = new HashMap<>();
                    r.requiredFiles.put("halium-boot.img", c.getString(R.string.select_halium_boot));
                    r.requiredFiles.put("system.img", c.getString(R.string.select_system_image));
                    r.parts = new ArrayList<>();
                    r.parts.add(c.getString(R.string.select_part, c.getString(R.string.system_part)));
                    r.parts.add(c.getString(R.string.select_part, c.getString(R.string.data_part)));
                    break;
                default:
                    r = null;
                    break;
            }
            if (r != null)
                l.add(r);
        }
        return l;
    }
}
