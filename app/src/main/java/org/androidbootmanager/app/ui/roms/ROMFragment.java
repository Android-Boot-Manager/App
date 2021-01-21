package org.androidbootmanager.app.ui.roms;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.io.SuFile;

import org.androidbootmanager.app.R;
import org.androidbootmanager.app.devices.DeviceModel;
import org.androidbootmanager.app.legacy.EntryTabFragment;
import org.androidbootmanager.app.ui.installer.DeviceInstallerWizardPageFragment;
import org.androidbootmanager.app.ui.installer.FindDeviceWizardPageFragment;
import org.androidbootmanager.app.util.ActionAbortedCleanlyError;
import org.androidbootmanager.app.util.ConfigFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ROMFragment extends Fragment {

    RecyclerView recyclerView;
    ArrayList<Entry> entries;
    ROMRecyclerViewAdapter adapter;

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

    public class ROMRecyclerViewAdapter extends RecyclerView.Adapter<ROMRecyclerViewAdapter.ViewHolder> {

        private final List<Entry> entryList;

        public ROMRecyclerViewAdapter(List<Entry> entryListIn) {
            entryList = entryListIn;
        }

        @NotNull
        @Override
        public ROMRecyclerViewAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.wizard_installer_finddevice_element, parent, false);
            return new ROMRecyclerViewAdapter.ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NotNull ROMRecyclerViewAdapter.ViewHolder holder, int position) {
            //
        }

        public void updateEntries() {
            ROMFragment.this.updateEntries();
        }

        @Override
        public int getItemCount() {
            return entryList.size();
        }

        public class ViewHolder extends RecyclerView.ViewHolder {

            public ViewHolder(View view) {
                super(view);
            }

            public void updateEntries() {
                ROMRecyclerViewAdapter.this.updateEntries();
            }
        }
    }

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        final View root = inflater.inflate(R.layout.wizard_installer_finddevice, container, false);
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
        for (String entryFile : Objects.requireNonNull(SuFile.open("/data/abm/bootset/lk2nd/entries").list())) {
            try {
                entries.add(new Entry(entryFile));
            } catch (ActionAbortedCleanlyError actionAbortedCleanlyError) {
                actionAbortedCleanlyError.printStackTrace();
                Toast.makeText(requireContext(), "Loading entry: Error. Action aborted cleanly.", Toast.LENGTH_LONG).show();
            }
        }
        adapter.notifyDataSetChanged();
    }
}