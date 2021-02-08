package org.androidbootmanager.app.roms;

import android.annotation.SuppressLint;
import android.content.Context;

import com.topjohnwu.superuser.io.SuFile;

import org.androidbootmanager.app.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

public class ROMsList {
    public final String codename;
    public final Context c;

    public ROMsList(String codename, Context context) {
        this.codename = codename;
        c = context;
    }

    @SuppressLint("SdCardPath")
    public List<ROM> getROMs() {
        ArrayList<ROM> l = new ArrayList<>();
        for (String sn : Objects.requireNonNull(SuFile.open("/data/data/org.androidbootmanager.app/assets/Scripts/add_os/" + codename + "/").list())) {
            ROM r = new ROM();
            r.scriptname = sn;
            r.fullPath = "/data/data/org.androidbootmanager.app/assets/Scripts/add_os/" + codename + "/" + r.scriptname;
            switch (r.scriptname) {
                case "add_ubuntutouch_systemimage_haliumboot.sh":
                    //noinspection SpellCheckingInspection
                case "add_ubuntutouch_sytemimage_haliumboot.sh": // this is considered deprecated
                    r.viewname = c.getString(R.string.rom_type_add_ut_sysimg_halium);
                    r.requiredFiles = new HashMap<>();
                    r.requiredFiles.put("halium-boot.img", c.getString(R.string.select_halium_boot));
                    r.requiredFiles.put("system.img", c.getString(R.string.select_system_image));
                    r.parts = new ArrayList<>();
                    r.parts.add(c.getString(R.string.select_part, c.getString(R.string.system_part)));
                    r.parts.add(c.getString(R.string.select_part, c.getString(R.string.data_part)));
                    r.strings = new HashMap<>();
                    r.strings.put(c.getString(R.string.enter_rom_name), "");
                    List<String> a = Arrays.asList(Objects.requireNonNull(SuFile.open("/data/abm/db/bootset/entries/").list()));
                    a.removeIf((b) -> b.contains("rom"));
                    a.sort((b, c) -> Integer.compare(Integer.parseInt(b.replace("rom","")), Integer.parseInt(c.replace("rom",""))));
                    int b = a.size() > 0 ? Integer.parseInt(a.get(a.size()-1).replace("rom",""))+1 : 0;
                    r.strings.put(c.getString(R.string.enter_rom_folder), "rom" + b);
                    r.gen = (imodel, menuName, folderName) -> imodel.setCmdline(Objects.requireNonNull(imodel.getROM().getValue()).fullPath + " '" + folderName + "' '" + menuName + "' " + Objects.requireNonNull(imodel.getParts().getValue()).get(0) + " " + imodel.getParts().getValue().get(1) + " /data/data/org.androidbootmanager.app/cache/system.img /data/data/org.androidbootmanager.app/cache/halium-boot.img");
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
