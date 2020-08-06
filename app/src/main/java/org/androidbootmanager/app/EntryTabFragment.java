package org.androidbootmanager.app;

import android.annotation.SuppressLint;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;

import androidx.appcompat.app.AlertDialog;

import java.util.ArrayList;
import java.util.Objects;

public class EntryTabFragment extends ConfiguratorActivity.BaseFragment {
	
	ListView myList;
	ArrayAdapter<String> adapter;
	ArrayList<Entry> entries;
	ArrayList<String> entriesListView;

	@Override
	protected void onPreInit() {
		layout = R.layout.tab_entry;
	}

	@Override
	protected void onInit() {
		myList = (ListView) Objects.requireNonNull(getView()).findViewById(R.id.tabentryListView);
		entries = new ArrayList<>();
		entriesListView = new ArrayList<>();
		for (String entryFile : Shell.doRoot("find /data/bootset/lk2nd/entries -type f").split("\n")) {
			entries.add(new Entry(entryFile));
		}
		for (Entry entry : entries) {
			entriesListView.add(entry.config.get("title"));
		}
		entriesListView.add(getResources().getString(R.string.entry_create));
		adapter = new ArrayAdapter<>(xcontext, android.R.layout.simple_list_item_1, entriesListView);
		myList.setAdapter(adapter);
		myList.setOnItemClickListener(new OnItemClickListener(){
				@Override
				public void onItemClick(AdapterView<?> parent, View view, int position, long p4) {
					if (((String) parent.getItemAtPosition(position)).equals(xcontext.getResources().getString(R.string.entry_create))) {
						mkNewEntry();
						return;
					}
					final Entry entry = findEntry((String) parent.getItemAtPosition(position));
					assert entry != null;
					final ConfigFile proposed = ConfigFile.importFromString(Shell.doRoot("cat " + entry.file));
					View dialog = LayoutInflater.from(xcontext).inflate(R.layout.edit_entry,null);
					((EditText) dialog.findViewById(R.id.editentryTitle)).setText(entry.config.get("title"));
					((EditText) dialog.findViewById(R.id.editentryTitle)).addTextChangedListener(new ConfigTextWatcher(proposed, "title"));
					((EditText) dialog.findViewById(R.id.editentryKernel)).setText(entry.config.get("linux"));
					((EditText) dialog.findViewById(R.id.editentryKernel)).addTextChangedListener(new ConfigTextWatcher(proposed, "linux"));
					((EditText) dialog.findViewById(R.id.editentryDtb)).setText(entry.config.get("dtb"));
					((EditText) dialog.findViewById(R.id.editentryDtb)).addTextChangedListener(new ConfigTextWatcher(proposed, "dtb"));
					((EditText) dialog.findViewById(R.id.editentryInitrd)).setText(entry.config.get("initrd"));
					((EditText) dialog.findViewById(R.id.editentryInitrd)).addTextChangedListener(new ConfigTextWatcher(proposed, "initrd"));
					((EditText) dialog.findViewById(R.id.editentryCmdline)).setText(entry.config.get("options"));
					((EditText) dialog.findViewById(R.id.editentryCmdline)).addTextChangedListener(new ConfigTextWatcher(proposed, "options"));
					new AlertDialog.Builder(xcontext)
						.setCancelable(true)
						.setNeutralButton(R.string.cancel, (p1, p2) -> p1.dismiss())
						.setNegativeButton(R.string.delete, (p1, p2) -> {
							p1.dismiss();
							new AlertDialog.Builder(xcontext)
							.setTitle(R.string.delete)
							.setMessage(R.string.sure_title)
								.setNegativeButton(R.string.cancel, (p11, p21) -> p11.dismiss())
								.setPositiveButton(R.string.ok, (p112, p212) -> {
									p112.dismiss();
									Shell.doRoot("rm " + entry.file);
									entries.remove(entry);
									regenListView();
								})
							.show();
						})
						.setPositiveButton(R.string.save, (p1, p2) -> {
							entry.config = proposed;
							entry.save();
							regenListView();
						})
						.setTitle(R.string.edit_entry)
						.setView(dialog)
						.show();
				}
				
				@SuppressLint("SdCardPath")
				private void mkNewEntry() {
					final EditText input = new EditText(xcontext);
					input.setInputType(InputType.TYPE_CLASS_TEXT);
					new AlertDialog.Builder(xcontext)
					.setTitle(R.string.filename)
						.setPositiveButton(R.string.save, (p1, p2) -> {
							p1.dismiss();
							Shell.doRoot("/data/data/org.androidbootmanager.app/assets/mkentryfile.sh > /data/bootset/lk2nd/entries/" + input.getText().toString() + ".conf");
							entries.add(new Entry("/data/bootset/lk2nd/entries/" + input.getText().toString() + ".conf"));
							regenListView();
						})
						.setNegativeButton(R.string.cancel, (p1, p2) -> p1.dismiss())
					.setCancelable(true)
					.setView(input)
					.show();
				}
			});
	}
	
	private void regenListView() {
		entriesListView.clear();
		for (Entry entry : entries) {
			entriesListView.add(entry.config.get("title"));
		}
		entriesListView.add(getResources().getString(R.string.entry_create));
		adapter.notifyDataSetChanged();
	}
	
	private Entry findEntry(String title) {
		for (Entry e : entries) {
			if (e.config.get("title").equals(title)) return e;
		}
		return null;
	}

	private static class Entry {
		public String file;
		public ConfigFile config;
		public Entry(String outFile) {
			file = outFile;
			config = ConfigFile.importFromString(Shell.doRoot("cat " + file + " 2>/dev/null"));
		}
		public void save() {
			config.exportToPrivFile("entry.conf",file);
		}
	}
}
