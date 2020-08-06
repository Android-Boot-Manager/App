package org.androidbootmanager.app;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import java.util.ArrayList;
import java.util.Objects;

public class RomTabFragment extends ConfiguratorActivity.BaseFragment
{
	
	ListView myList;
	ArrayAdapter<String> adapter;
	ArrayList<String> romsListView;
	ArrayList<ROM> roms;

	@Override
	protected void onPreInit()
	{
		layout = R.layout.tab_rom;
	}
	
	@SuppressLint("SetTextI18n")
	@Override
	protected void onInit() {
		myList = (ListView) Objects.requireNonNull(getView()).findViewById(R.id.tabromListView);
		roms = new ArrayList<>();
		romsListView = new ArrayList<>();
		for (String romFile : Shell.doRoot("find /data/bootset/lk2nd/entries -type f").split("\n")) {
			ROM r = new ROM(romFile);
				if(r.config.get("xRom") != null) roms.add(r);
		}
		adapter = new ArrayAdapter<>(xcontext, android.R.layout.simple_list_item_1, romsListView);
		regenListView();
		myList.setAdapter(adapter);
		myList.setOnItemClickListener((OnItemClickListener) (parent, view, position, p4) -> {
			if (((String) parent.getItemAtPosition(position)).equals(xcontext.getResources().getString(R.string.entry_create))) {
				// TODO: Implement adding a ROM
			} else {
				final ROM rom = findEntry((String) parent.getItemAtPosition(position));
				assert rom != null;
				final ConfigFile proposed = ConfigFile.importFromString(Shell.doRoot("cat " + rom.file + " 2>/dev/null"));
				View dialog = LayoutInflater.from(xcontext).inflate(R.layout.edit_rom,null);
				((EditText) dialog.findViewById(R.id.editromTitle)).setText(rom.config.get("title"));
				((EditText) dialog.findViewById(R.id.editromTitle)).addTextChangedListener(new ConfigTextWatcher(proposed, "title"));
				((TextView) dialog.findViewById(R.id.editromDataPart)).setText(": " + rom.config.get("xRomData"));
				((TextView) dialog.findViewById(R.id.editromSystemPart)).setText(": " + rom.config.get("xRomSystem"));
				new AlertDialog.Builder(xcontext)
				.setTitle(R.string.add_rom)
				.setPositiveButton(R.string.save, (p1, p2) -> {
					rom.config = proposed;
					rom.save();
				})
				.setNegativeButton(R.string.delete, (p1, p2) -> {
					p1.dismiss();
					new AlertDialog.Builder(xcontext)
						.setTitle(R.string.delete)
						.setMessage(R.string.sure_title)
						.setNegativeButton(R.string.cancel, (p11, p21) -> p11.dismiss())
						.setPositiveButton(R.string.ok, (p112, p212) -> {
							p112.dismiss();
							Shell.doRoot("rm " + rom.file);
							roms.remove(rom);
							regenListView();
							// TODO: Implement deleting partitions
						})
						.show();
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
	
	private static class ROM {
		public String file;
		public ConfigFile config;
		public ROM(String outFile) {
			file = outFile;
			config = ConfigFile.importFromString(Shell.doRoot("cat " + file + " 2>/dev/null"));
		}
		public void save() {
			config.exportToPrivFile("rom.conf",file);
		}
	}
}
