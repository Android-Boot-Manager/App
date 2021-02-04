package org.androidbootmanager.app.legacy;

import android.annotation.SuppressLint;
import android.util.ArrayMap;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.io.SuFile;

import org.androidbootmanager.app.util.ActionAbortedCleanlyError;
import org.androidbootmanager.app.util.ConfigFile;
import org.androidbootmanager.app.util.ConfigTextWatcher;
import org.androidbootmanager.app.R;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

@SuppressLint("SdCardPath")
public class RomTabFragment extends ConfiguratorActivity.BaseFragment {

    ListView myList;
    ArrayAdapter<String> adapter;
    ArrayList<String> romsListView;
    ArrayList<ROM> roms;
    String codename;

    @Override
    protected void onPreInit() {
        layout = R.layout.legacy_tab_rom;
    }

    @SuppressLint("SetTextI18n")
    @Override
    protected void onInit() {
        myList = requireView().findViewById(R.id.tabromListView);
        roms = new ArrayList<>();
        romsListView = new ArrayList<>();
        for (String romFile : String.join("", Shell.su("find /data/bootset/lk2nd/entries -type f").exec().getOut()).split("\n")) {
            ROM r;
            try {
                r = new ROM(romFile);
            } catch (ActionAbortedCleanlyError actionAbortedCleanlyError) {
                actionAbortedCleanlyError.printStackTrace();
                Toast.makeText(xcontext, "Loading entry: Error. Action aborted cleanly.", Toast.LENGTH_LONG).show();
                continue;
            }
            if (r.config.get("xRom") != null) roms.add(r);
        }
        adapter = new ArrayAdapter<>(xcontext, android.R.layout.simple_list_item_1, romsListView);
        regenListView();
        myList.setAdapter(adapter);
        myList.setOnItemClickListener((parent, view, position, p4) -> {
            if (parent.getItemAtPosition(position).equals(xcontext.getResources().getString(R.string.entry_create))) {
                if (!String.join("", Shell.su("/data/data/org.androidbootmanager.app/assets/Scripts/detect_abm_storage.sh").exec().getOut()).contains("sd")) {
                    AlertDialog.Builder d = new AlertDialog.Builder(xcontext)
                            .setTitle(R.string.add_rom)
                            .setMessage(R.string.no_storage_found)
                            .setNegativeButton(R.string.cancel, (dialog, which) -> dialog.dismiss());
                    if (String.join("", Shell.su("/data/data/org.androidbootmanager.app/assets/Scripts/storage_detect.sh").exec().getOut()).contains("sd")) {
                        d.setPositiveButton(R.string.format, (dialog, which) -> {
                            if (sdIsMounted())
                                new AlertDialog.Builder(xcontext)
                                        .setTitle(R.string.fatal)
                                        .setMessage(R.string.storage_in_use)
                                        .setNegativeButton(R.string.cancel, (dialog1, which1) -> dialog1.dismiss())
                                        .show();
                            else
                                new AlertDialog.Builder(xcontext)
                                        .setTitle(R.string.sure_title)
                                        .setMessage(R.string.format_msg)
                                        .setNegativeButton(R.string.cancel, (dialog1, which1) -> dialog1.dismiss())
                                        .setPositiveButton(R.string.ok, (dialog1, which1) -> Shell.su("/data/data/org.androidbootmanager.app/assets/Scripts/format_device.sh sd").submit())
                                        .show();
                        });
                    }
                    d.show();
                } else {
                    ArrayList<String> oses = new ArrayList<>(Arrays.asList(String.join("", Shell.sh("ls /data/data/org.androidbootmanager.app/assets/Scripts/add_os/" + codename).exec().getOut()).split("\\s")));
                    ArrayList<String> items = new ArrayList<>();
                    ArrayMap<String, String> x = new ArrayMap<>();
                    x.put("add_rom_zip.sh", getString(R.string.rom_type_add_rom_zip));
                    x.put("add_ubuntutouch_sytemimage_haliumboot_rootfs.sh", getString(R.string.rom_type_add_ut_sysimg_halium_rootfs));
                    x.put("add_ubuntutouch_sytemimage_haliumboot.sh", getString(R.string.rom_type_add_ut_sysimg_halium));
                    x.put("add_sailfish.sh", getString(R.string.rom_type_add_sailfish));
                    for (String y : oses)
                        if (x.containsKey(y)) items.add(x.get(y));
                        else items.add(y);
                    new AlertDialog.Builder(xcontext)
                            .setIcon(R.mipmap.ic_launcher)
                            .setTitle(R.string.add_rom)
                            .setCancelable(true)
                            .setNegativeButton(R.string.cancel, (p1, p2) -> p1.dismiss())
                            .setItems(items.toArray(new String[]{}), (dialog, which) -> {
                                dialog.dismiss();

                                if (new File("/data/data/org.androidbootmanager.app/assets/Scripts/add_os/META-INF/" + oses.get(which)).exists())
                                    throw new RuntimeException("metascript removed, to be replaced soon(tm)");
                                else
                                    new AlertDialog.Builder(xcontext)
                                            .setTitle(R.string.fatal)
                                            .setMessage(R.string.unsupported_os)
                                            .setNegativeButton(R.string.cancel, (p1, p2) -> p1.dismiss())
                                            .show();
                            })
                            .show();
                }
            } else {
                final ROM rom = findEntry((String) parent.getItemAtPosition(position));
                assert rom != null;
                ConfigFile proposed_;
                try {
                    proposed_ = ConfigFile.importFromFile(rom.file);
                } catch (ActionAbortedCleanlyError actionAbortedCleanlyError) {
                    actionAbortedCleanlyError.printStackTrace();
                    Toast.makeText(xcontext, "Loading configuration file: Error. Action aborted cleanly. Creating new.", Toast.LENGTH_LONG).show();
                    proposed_ = new ConfigFile();
                }
                final ConfigFile proposed = proposed_;
                View dialog = LayoutInflater.from(xcontext).inflate(R.layout.legacy_edit_rom, null);
                ((EditText) dialog.findViewById(R.id.editromTitle)).setText(rom.config.get("title"));
                ((EditText) dialog.findViewById(R.id.editromTitle)).addTextChangedListener(new ConfigTextWatcher(proposed, "title"));
                ((TextView) dialog.findViewById(R.id.editromDataPart)).setText(": " + rom.config.get("xRom"));
                new AlertDialog.Builder(xcontext)
                        .setTitle(R.string.add_rom)
                        .setPositiveButton(R.string.save, (p1, p2) -> {
                            rom.config = proposed;
                            rom.save();
                        })
                        .setNegativeButton(R.string.delete, (p1, p2) -> {
                            p1.dismiss();
                            if (rom.config.get("xRom").equals("real")) {
                                new AlertDialog.Builder(xcontext)
                                        .setTitle(R.string.fatal)
                                        .setMessage(R.string.delete_real_rom)
                                        .setCancelable(true)
                                        .show();
                                return;
                            }
                            if (sdIsMounted()) {
                                new AlertDialog.Builder(xcontext)
                                        .setTitle(R.string.fatal)
                                        .setMessage(R.string.storage_in_use)
                                        .setNegativeButton(R.string.cancel, (dialog1, which1) -> dialog1.dismiss())
                                        .show();
                            } else {
                                new AlertDialog.Builder(xcontext)
                                        .setTitle(R.string.delete)
                                        .setMessage(R.string.sure_title)
                                        .setNegativeButton(R.string.cancel, (p11, p21) -> p11.dismiss())
                                        .setPositiveButton(R.string.ok, (p112, p212) -> {
                                            p112.dismiss();
                                            SuFile.open(rom.file).delete();
                                            roms.remove(rom);
                                            regenListView();
                                            if ((!rom.config.get("xRomSystem").equals("")) && (!rom.config.get("xRomData").equals("")))
                                                Shell.su("sgdisk /dev/block/mmcblk1 --delete " + rom.config.get("xRomData") + " --delete " + rom.config.get("xRomSystem")).exec();
                                        })
                                        .show();
                            }
                        })
                        .setNeutralButton(R.string.cancel, (p1, p2) -> p1.dismiss())
                        .setCancelable(true)
                        .setView(dialog)
                        .show();
            }
        });

    }

    private void regenListView() {
        romsListView.clear();
        for (ROM rom : roms) {
            romsListView.add(rom.config.get("title"));
        }
        romsListView.add(getResources().getString(R.string.entry_create));
        adapter.notifyDataSetChanged();
    }

    private ROM findEntry(String title) {
        for (ROM r : roms) {
            if (r.config.get("title").equals(title)) return r;
        }
        return null;
    }

    private boolean sdIsMounted() {
        return String.join("", Shell.su("blockdev --rereadpt /dev/block/mmcblk1 2>&1").exec().getOut()).contains("busy");
    }

    private static class ROM {
        public String file;
        public ConfigFile config;

        public ROM(String outFile) throws ActionAbortedCleanlyError {
            file = outFile;
            config = ConfigFile.importFromFile(file);
        }

        public void save() {
            config.exportToPrivFile("rom.conf", file);
        }
    }
}