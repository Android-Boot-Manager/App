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
            r.requiredFiles = new HashMap<>();
            r.parts = new ArrayList<>();
            r.strings = new HashMap<>();
            ArrayList<String> a = new ArrayList<>(Arrays.asList(Objects.requireNonNull(SuFile.open("/data/abm/bootset/db/entries/").list())));
            a.removeIf((c) -> !c.contains("rom"));
            a.sort((c, d) -> Integer.compare(Integer.parseInt(c.replace("rom","").replace(".conf","")), Integer.parseInt(d.replace("rom","").replace(".conf",""))));
            int b = a.size() > 0 ? Integer.parseInt(a.get(a.size()-1).replace("rom","").replace(".conf",""))+1 : 0;
            switch (r.scriptname) {
                case "add_ubuntutouch_systemimage_haliumboot.sh":
                    r.viewname = c.getString(R.string.rom_type_add_ut_sysimg_halium);
                    r.requiredFiles.put("halium-boot.img", c.getString(R.string.select_halium_boot));
                    r.requiredFiles.put("system.img", c.getString(R.string.select_system_image));
                    r.parts.add(c.getString(R.string.select_part, c.getString(R.string.system_part)));
                    r.parts.add(c.getString(R.string.select_part, c.getString(R.string.data_part)));
                    r.strings.put(c.getString(R.string.enter_rom_name), "Ubuntu Touch");
                    r.strings.put(c.getString(R.string.enter_rom_folder), "rom" + b);
                    r.gen = (imodel, menuName, folderName) -> imodel.setCmdline(Objects.requireNonNull(imodel.getROM().getValue()).fullPath + " '" + folderName + "' '" + menuName + "' " + Objects.requireNonNull(imodel.getParts().getValue()).get(0) + " " + imodel.getParts().getValue().get(1) + " /data/data/org.androidbootmanager.app/cache/system.img /data/data/org.androidbootmanager.app/cache/halium-boot.img");
                    break;
                case "add_sailfish.sh":
                    r.viewname = c.getString(R.string.rom_type_add_sailfish);
                    r.requiredFiles.put("hybris-boot.img", c.getString(R.string.select_hybris_boot));
                    r.requiredFiles.put("sailfish.img001", c.getString(R.string.select_system_image));
                    r.parts.add(c.getString(R.string.select_part, c.getString(R.string.data_part)));
                    r.strings.put(c.getString(R.string.enter_rom_name), "SailfishOS");
                    r.strings.put(c.getString(R.string.enter_rom_folder), "rom" + b);
                    r.gen = (imodel, menuName, folderName) -> imodel.setCmdline(Objects.requireNonNull(imodel.getROM().getValue()).fullPath + " '" + folderName + "' '" + menuName + "' " + Objects.requireNonNull(imodel.getParts().getValue()).get(0) + " /data/data/org.androidbootmanager.app/cache/sailfish.img001 /data/data/org.androidbootmanager.app/cache/hybris-boot.img");
                    break;
                case "other_os.sh":
                    r.viewname = c.getString(R.string.other_os);
                    r.requiredFiles.put("boot.img", c.getString(R.string.select_boot));
                    r.strings.put(c.getString(R.string.enter_rom_name), "");
                    r.strings.put(c.getString(R.string.enter_rom_folder), "rom" + b);
                    r.gen = (imodel, menuName, folderName) -> imodel.setCmdline(Objects.requireNonNull(imodel.getROM().getValue()).fullPath + " '" + folderName + "' '" + menuName + "' /data/data/org.androidbootmanager.app/cache/boot.img");
                    break;
                case "entry_only.sh":
                    r.viewname = c.getString(R.string.empty_entry);
                    r.strings.put(c.getString(R.string.enter_rom_name), "");
                    r.strings.put(c.getString(R.string.enter_rom_folder), "rom" + b);
                    r.gen = (imodel, menuName, folderName) -> imodel.setCmdline(Objects.requireNonNull(imodel.getROM().getValue()).fullPath + " '" + folderName + "' '" + menuName + "'");
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
