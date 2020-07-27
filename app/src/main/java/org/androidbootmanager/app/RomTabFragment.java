package org.androidbootmanager.app;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import java.util.ArrayList;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView;
import android.widget.Adapter;
import android.view.View;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.content.DialogInterface;
import android.widget.EditText;
import android.widget.TextView;

public class RomTabFragment extends BaseFragment
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
	
	@Override
	protected void onInit() {
		myList = (ListView) getView().findViewById(R.id.tabromListView);
		roms = new ArrayList<>();
		romsListView = new ArrayList<>();
		for (String romFile : Shell.doRoot("find /data/bootset/lk2nd/entries -type f").split("\n")) {
			ROM r = new ROM(romFile);
				if(r.config.get("xRom") != null) roms.add(r);
		}
		adapter = new ArrayAdapter<>(xcontext, android.R.layout.simple_list_item_1, romsListView);
		regenListView();
		myList.setAdapter(adapter);
		myList.setOnItemClickListener(new OnItemClickListener(){
				@Override
				public void onItemClick(AdapterView<?> parent, View view, int position, long p4) {
					if (((String) parent.getItemAtPosition(position)).equals(xcontext.getResources().getString(R.string.entry_create))) {
						// TODO: Implement adding a ROM
					} else {
						final ROM rom = findEntry((String) parent.getItemAtPosition(position));
						final ConfigFile proposed = ConfigFile.importFromString(Shell.doRoot("cat " + rom.file + " 2>/dev/null"));
						View dialog = LayoutInflater.from(xcontext).inflate(R.layout.edit_rom,null);
						((EditText) dialog.findViewById(R.id.editromTitle)).setText(rom.config.get("title"));
						((EditText) dialog.findViewById(R.id.editromTitle)).addTextChangedListener(new ConfigTextWatcher(proposed, "title"));
						((TextView) dialog.findViewById(R.id.editromDataPart)).setText(": " + rom.config.get("xRomData"));
						((TextView) dialog.findViewById(R.id.editromSystemPart)).setText(": " + rom.config.get("xRomSystem"));
						new AlertDialog.Builder(xcontext)
						.setTitle(R.string.add_rom)
						.setPositiveButton(R.string.save, new DialogInterface.OnClickListener(){
								@Override
								public void onClick(DialogInterface p1, int p2) {
									rom.config = proposed;
									rom.save();
								}
							})
						.setNegativeButton(R.string.delete, new DialogInterface.OnClickListener(){
								@Override
								public void onClick(DialogInterface p1, int p2) {
									p1.dismiss();
									new AlertDialog.Builder(xcontext)
										.setTitle(R.string.delete)
										.setMessage(R.string.sure_title)
										.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener(){
											@Override
											public void onClick(DialogInterface p1, int p2) {
												p1.dismiss();
											}
										})
										.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener(){
											@Override
											public void onClick(DialogInterface p1, int p2) {
												p1.dismiss();
												Shell.doRoot("rm " + rom.file);
												roms.remove(rom);
												regenListView();
												// TODO: Implement deleting partitions
											}
										})
										.show();
								}
							})
						.setNeutralButton(R.string.cancel, new DialogInterface.OnClickListener(){
								@Override
								public void onClick(DialogInterface p1, int p2) {
									p1.dismiss();
								}
							})
						.setCancelable(true)
						.setView(dialog)
						.show();
					}
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
	
	private class ROM {
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
