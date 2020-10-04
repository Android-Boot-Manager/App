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
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.io.SuFile;

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
		for (String entryFile : String.join("",Shell.su("find /data/bootset/lk2nd/entries -type f").exec().getOut()).split("\n")) {
			try {
				entries.add(new Entry(entryFile));
			} catch (ActionAbortedCleanlyError actionAbortedCleanlyError) {
				actionAbortedCleanlyError.printStackTrace();
				Toast.makeText(xcontext, "Loading entry: Error. Action aborted cleanly.", Toast.LENGTH_LONG).show();
			}
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
					ConfigFile proposed_;
					try {
						proposed_ = ConfigFile.importFromFile(entry.file);
					} catch (ActionAbortedCleanlyError actionAbortedCleanlyError) {
						actionAbortedCleanlyError.printStackTrace();
						Toast.makeText(xcontext,"Loading configuration file: Error. Action aborted cleanly. Creating new.",Toast.LENGTH_LONG).show();
						proposed_ = new ConfigFile();
					}
					final ConfigFile proposed = proposed_;
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
									if(!SuFile.open(entry.file).delete()) Toast.makeText(xcontext, "Failed to delete entry.", Toast.LENGTH_LONG).show();
									else entries.remove(entry);
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
							Shell.su("/data/data/org.androidbootmanager.app/assets/mkentryfile.sh > /data/bootset/lk2nd/entries/" + input.getText().toString() + ".conf").exec();
							try {
								entries.add(new Entry("/data/bootset/lk2nd/entries/" + input.getText().toString() + ".conf"));
							} catch (ActionAbortedCleanlyError actionAbortedCleanlyError) {
								actionAbortedCleanlyError.printStackTrace();
								Toast.makeText(xcontext,"Failed to add newly created entry. This should not happen.",Toast.LENGTH_LONG).show();
							}
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
		public Entry(String outFile) throws ActionAbortedCleanlyError {
			file = outFile;
			config = ConfigFile.importFromFile(file);
		}
		public void save() {
			config.exportToPrivFile("entry.conf",file);
		}
	}
}
