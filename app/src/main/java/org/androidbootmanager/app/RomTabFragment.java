package org.androidbootmanager.app;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

@SuppressLint("SdCardPath")
public class RomTabFragment extends ConfiguratorActivity.BaseFragment {

    ListView myList;
    ArrayAdapter<String> adapter;
    ArrayList<String> romsListView;
    ArrayList<ROM> roms;
    String codename = Shell.doRoot("cat /data/abm-codename.cfg");

    @Override
    protected void onPreInit() {
        layout = R.layout.tab_rom;
    }

    @SuppressLint("SetTextI18n")
    @Override
    protected void onInit() {
        myList = Objects.requireNonNull(getView()).findViewById(R.id.tabromListView);
        roms = new ArrayList<>();
        romsListView = new ArrayList<>();
        for (String romFile : Shell.doRoot("find /data/bootset/lk2nd/entries -type f").split("\n")) {
            ROM r = new ROM(romFile);
            if (r.config.get("xRom") != null) roms.add(r);
        }
        adapter = new ArrayAdapter<>(xcontext, android.R.layout.simple_list_item_1, romsListView);
        regenListView();
        myList.setAdapter(adapter);
        myList.setOnItemClickListener((parent, view, position, p4) -> {
            if (parent.getItemAtPosition(position).equals(xcontext.getResources().getString(R.string.entry_create))) {
                if (!Shell.doRoot("/data/data/org.androidbootmanager.app/assets/Scripts/detect_abm_storage.sh").contains("sd")) {
                    AlertDialog.Builder d = new AlertDialog.Builder(xcontext)
                            .setTitle(R.string.add_rom)
                            .setMessage(R.string.no_storage_found)
                            .setNegativeButton(R.string.cancel, (dialog, which) -> dialog.dismiss());
                    if (Shell.doRoot("/data/data/org.androidbootmanager.app/assets/Scripts/storage_detect.sh").contains("sd")) {
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
										.setPositiveButton(R.string.ok, (dialog1, which1) -> new Thread(() -> Shell.doRoot("/data/data/org.androidbootmanager.app/assets/Scripts/format_device.sh sd")).start())
										.show();
                        });
                    }
                    d.show();
                } else {
                	ArrayList<String> oses = new ArrayList<>(Arrays.asList(Shell.doRoot("ls /data/data/org.androidbootmanager.app/assets/Scripts/add_os/" + codename).split("\\s")));
                	ArrayList<String> items = new ArrayList<>();
                    for (String x : oses) {
                        switch(x) {
                            case "add_rom_zip.sh":
                                items.add(getString(R.string.rom_type_add_rom_zip));
                                break;
                            case "add_ubuntutouch_sytemimage_haliumboot_rootfs.sh":
                                items.add(getString(R.string.rom_type_add_ut_sysimg_halium_rootfs));
                                break;
                            case "add_ubuntutouch_sytemimage_haliumboot.sh":
                                items.add(getString(R.string.rom_type_add_ut_sysimg_halium));
                                break;
                        }
                    }
                	new AlertDialog.Builder(xcontext)
							.setIcon(R.drawable.ic_launcher)
							.setTitle(R.string.add_rom)
							.setCancelable(true)
							.setNegativeButton(R.string.cancel, (p1,p2) -> p1.dismiss())
							.setItems(items.toArray(new String[]{}), (dialog, which) -> {
							    dialog.dismiss();
							    switch (oses.get(which)) {
                                    case "add_ubuntutouch_sytemimage_haliumboot_rootfs.sh":
                                        xcontext.runVM(Shell.doShell("cat /data/data/org.androidbootmanager.app/assets/Scripts/add_os/META-INF/add_ubuntutouch_sytemimage_haliumboot_rootfs.sh"));
                                        break;
                                    case "add_ubuntutouch_sytemimage_haliumboot.sh":
                                        // TODO: Add yggdrasil UT installer
                                        break;
                                    default:
                                        new AlertDialog.Builder(xcontext)
                                                .setTitle(R.string.fatal)
                                                .setMessage(R.string.unsupported_os)
                                                .setNegativeButton(R.string.cancel, (p1,p2) -> p1.dismiss())
                                                .show();
                                        break;
							    }
                            })
							.show();
				}
            } else {
                final ROM rom = findEntry((String) parent.getItemAtPosition(position));
                assert rom != null;
                final ConfigFile proposed = ConfigFile.importFromString(Shell.doRoot("cat " + rom.file + " 2>/dev/null"));
                View dialog = LayoutInflater.from(xcontext).inflate(R.layout.edit_rom, null);
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
                                            Shell.doRoot("rm " + rom.file);
                                            roms.remove(rom);
                                            regenListView();
                                            if ((!rom.config.get("xRomSystem").equals("")) && (!rom.config.get("xRomData").equals("")))
                                                System.out.println(Shell.doRoot("sgdisk /dev/block/mmcblk1 --delete " + rom.config.get("xRomData") + " --delete " + rom.config.get("xRomSystem")));
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
        return Shell.doRoot("blockdev --rereadpt /dev/block/mmcblk1 2>&1").contains("busy");
    }

    private static class ROM {
        public String file;
        public ConfigFile config;

        public ROM(String outFile) {
            file = outFile;
            config = ConfigFile.importFromString(Shell.doRoot("cat " + file + " 2>/dev/null"));
        }

        public void save() {
            config.exportToPrivFile("rom.conf", file);
        }
    }
}
