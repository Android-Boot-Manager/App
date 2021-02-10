package org.androidbootmanager.app.ui.roms;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.io.SuFile;

import org.androidbootmanager.app.R;
import org.androidbootmanager.app.ui.addrom.AddROMWelcomeWizardPageFragment;
import org.androidbootmanager.app.ui.home.InstalledViewModel;
import org.androidbootmanager.app.ui.wizard.WizardActivity;
import org.androidbootmanager.app.util.ActionAbortedCleanlyError;
import org.androidbootmanager.app.util.ConfigFile;
import org.androidbootmanager.app.util.ConfigTextWatcher;
import org.androidbootmanager.app.util.MiscUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ROMFragment extends Fragment {

    RecyclerView recyclerView;
    ArrayList<Entry> entries;
    ROMRecyclerViewAdapter adapter;
    InstalledViewModel model;
    FloatingActionButton fab;

    private static class Entry {
        public final String file;
        public ConfigFile config;
        public Entry(String outFile) throws ActionAbortedCleanlyError {
            file = outFile;
            config = ConfigFile.importFromFile(file);
        }
        public void save() {
            config.exportToPrivFile("entry.conf",file);
        }
    }

    public class ROMRecyclerViewAdapter extends RecyclerView.Adapter<ROMRecyclerViewAdapter.ViewHolder> {

        private final List<Entry> entryList;

        public ROMRecyclerViewAdapter(List<Entry> entryListIn) {
            entryList = entryListIn;
        }

        @NonNull
        @Override
        public ROMRecyclerViewAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.entry, parent, false);
            return new ROMRecyclerViewAdapter.ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ROMRecyclerViewAdapter.ViewHolder holder, int position) {
            holder.e = entries.get(position);
            switch (holder.e.config.get("xtype") != null ? holder.e.config.get("xtype") : "") {
                case "UT":
                    holder.label.setText(getString(R.string.ut));
                    holder.pic.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.ut_logo));
                    break;
                case "droid":
                    holder.label.setText(getString(R.string.android));
                    holder.pic.setImageDrawable(ContextCompat.getDrawable(requireContext(),R.drawable.ic_roms));
                    break;
                default:
                    holder.label.setText(getString(R.string.unknown_os));
                    break;
            }
            if (holder.e.config.get("title") != null)
                holder.name.setText(holder.e.config.get("title"));
        }

        public void updateEntries() {
            ROMFragment.this.updateEntries();
        }

        @Override
        public int getItemCount() {
            return entryList.size();
        }

        public class ViewHolder extends RecyclerView.ViewHolder {

            final ConstraintLayout container;
            final TextView label;
            final TextView name;
            final ImageView pic;
            Entry e;

            public ViewHolder(View view) {
                super(view);
                container = view.findViewById(R.id.entry_container);
                label = view.findViewById(R.id.entry_text);
                pic = view.findViewById(R.id.entry_pic);
                name = view.findViewById(R.id.entry_name);
                container.setOnClickListener((v) -> {
                    assert e != null;
                    ConfigFile proposed_;
                    try {
                        proposed_ = ConfigFile.importFromFile(e.file);
                    } catch (ActionAbortedCleanlyError actionAbortedCleanlyError) {
                        actionAbortedCleanlyError.printStackTrace();
                        Toast.makeText(requireContext(),"Loading configuration file: Error. Action aborted cleanly. Creating new.",Toast.LENGTH_LONG).show();
                        proposed_ = new ConfigFile();
                    }
                    final ConfigFile proposed = proposed_;
                    View dialog = LayoutInflater.from(requireContext()).inflate(R.layout.edit_entry,null);
                    ((EditText) dialog.findViewById(R.id.editentryTitle)).setText(e.config.get("title"));
                    ((EditText) dialog.findViewById(R.id.editentryTitle)).addTextChangedListener(new ConfigTextWatcher(proposed, "title"));
                    ((EditText) dialog.findViewById(R.id.editentryKernel)).setText(e.config.get("linux"));
                    ((EditText) dialog.findViewById(R.id.editentryKernel)).addTextChangedListener(new ConfigTextWatcher(proposed, "linux"));
                    ((EditText) dialog.findViewById(R.id.editentryDtb)).setText(e.config.get("dtb"));
                    ((EditText) dialog.findViewById(R.id.editentryDtb)).addTextChangedListener(new ConfigTextWatcher(proposed, "dtb"));
                    ((EditText) dialog.findViewById(R.id.editentryInitrd)).setText(e.config.get("initrd"));
                    ((EditText) dialog.findViewById(R.id.editentryInitrd)).addTextChangedListener(new ConfigTextWatcher(proposed, "initrd"));
                    ((EditText) dialog.findViewById(R.id.editentryCmdline)).setText(e.config.get("options"));
                    ((EditText) dialog.findViewById(R.id.editentryCmdline)).addTextChangedListener(new ConfigTextWatcher(proposed, "options"));
                    ((EditText) dialog.findViewById(R.id.editentryDataPart)).setText(e.config.get("xdata"));
                    dialog.findViewById(R.id.editentryDataPart).setEnabled(false);
                    ((EditText) dialog.findViewById(R.id.editentrySysPart)).setText(e.config.get("xsystem"));
                    dialog.findViewById(R.id.editentrySysPart).setEnabled(false);
                    new AlertDialog.Builder(requireContext())
                            .setCancelable(true)
                            .setNeutralButton(R.string.cancel, (p1, p2) -> p1.dismiss())
                            .setNegativeButton(R.string.delete, (p1, p2) -> MiscUtils.sure(requireContext(), p1, getString(R.string.delete_msg_2, e.config.get("title")), (p112, p212) -> {
                                if (e.config.get("xsystem") != null && e.config.get("xdata") != null) {
                                    if (e.config.get("xsystem").equals("real") || e.config.get("xdata").equals("real"))
                                        new AlertDialog.Builder(requireContext())
                                                .setTitle(R.string.failed)
                                                .setMessage(R.string.delete_real_rom)
                                                .setCancelable(true)
                                                .setNegativeButton(R.string.ok, (d, p) -> d.dismiss())
                                                .show();
                                } else {
                                    if (!SuFile.open(e.file).delete())
                                        Toast.makeText(requireContext(),"Deleting configuration file: Error.",Toast.LENGTH_LONG).show();
                                    Shell.su("rm -rf /data/abm/bootset/" + e.file.replace("/data/abm/bootset/lk2nd/entries/","").replace(".conf","")).submit();
                                    updateEntries();
                                }
                            }))
                            .setPositiveButton(R.string.save, (p1, p2) -> {
                                e.config = proposed;
                                e.save();
                                updateEntries();
                            })
                            .setTitle(R.string.edit_entry)
                            .setView(dialog)
                            .show();
                });
            }

            public void updateEntries() {
                ROMRecyclerViewAdapter.this.updateEntries();
            }
        }
    }

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        model = new ViewModelProvider(requireActivity()).get(InstalledViewModel.class);
        final View root = inflater.inflate(R.layout.fragment_roms, container, false);
        fab = root.findViewById(R.id.roms_fab);
        fab.setOnClickListener(view -> startActivity(new Intent(requireActivity(), WizardActivity.class).putExtra("codename",model.getCodename().getValue()).putExtra("StartFragment", AddROMWelcomeWizardPageFragment.class)));
        recyclerView = root.findViewById(R.id.wizard_installer_finddevice);
        LinearLayoutManager recyclerLayoutManager = new LinearLayoutManager(requireContext());
        recyclerView.setLayoutManager(recyclerLayoutManager);
        recyclerView.addItemDecoration(new DividerItemDecoration(recyclerView.getContext(), recyclerLayoutManager.getOrientation()));
        updateEntries();
        adapter = new ROMRecyclerViewAdapter(entries);
        recyclerView.setAdapter(adapter);
        return root;
    }

    public void updateEntries() {
        entries = new ArrayList<>();
        for (String entryFile : Objects.requireNonNull(SuFile.open("/data/abm/bootset/lk2nd/entries/").list())) {
            try {
                entries.add(new Entry("/data/abm/bootset/lk2nd/entries/" + entryFile));
            } catch (ActionAbortedCleanlyError actionAbortedCleanlyError) {
                actionAbortedCleanlyError.printStackTrace();
                Toast.makeText(requireContext(), "Loading entry: Error. Action aborted cleanly.", Toast.LENGTH_LONG).show();
            }
        }
        adapter = new ROMRecyclerViewAdapter(entries);
        recyclerView.setAdapter(adapter);
    }
}