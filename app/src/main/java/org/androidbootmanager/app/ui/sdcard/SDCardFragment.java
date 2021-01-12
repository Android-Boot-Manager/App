package org.androidbootmanager.app.ui.sdcard;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.topjohnwu.superuser.Shell;

import org.androidbootmanager.app.R;
import org.androidbootmanager.app.ui.home.InstalledViewModel;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SDCardFragment extends Fragment {

    private InstalledViewModel model;

    public enum PartitionType {
            RESERVED, ADOPTED, PORTABLE, UNKNOWN
    }

    public static class Partition {
        public PartitionType type;
        public int id;
        public int startSector;
        public int endSector;
        public int size;
        public String sizeFancy;
        public String code;
        public String name;

        @Override
        public String toString() {
            return "Partition{" +
                    "type=" + type +
                    ", id=" + id +
                    ", startSector=" + startSector +
                    ", endSector=" + endSector +
                    ", size=" + size +
                    ", sizeFancy='" + sizeFancy + '\'' +
                    ", code='" + code + '\'' +
                    ", name='" + name + '\'' +
                    '}';
        }
    }

    public static class SDPartitionMeta {
        public List<Partition> p = new ArrayList<>();
        public String friendlySize;
        public String guid;
        public int sectors;
        public int logicalSectorSizeBytes;
        public int maxEntries;
        public int firstUsableSector;
        public int lastUsableSector;
        public int alignSector;
        public int totalFreeSectors;
        public String totalFreeFancy;
        public int usableSectors;

        @Override
        public String toString() {
            return "SDPartitionMeta{" +
                    "p=" + p +
                    ", friendlySize='" + friendlySize + '\'' +
                    ", guid='" + guid + '\'' +
                    ", sectors=" + sectors +
                    ", logicalSectorSizeBytes=" + logicalSectorSizeBytes +
                    ", maxEntries=" + maxEntries +
                    ", firstUsableSector=" + firstUsableSector +
                    ", lastUsableSector=" + lastUsableSector +
                    ", alignSector=" + alignSector +
                    ", totalFreeSectors=" + totalFreeSectors +
                    ", totalFreeFancy='" + totalFreeFancy + '\'' +
                    ", usableSectors=" + usableSectors +
                    '}';
        }

        public int count() {
            return p.size();
        }

        public Partition dumpPartition(int id) {
            return p.get(id);
        }

    }

    public class SDRecyclerViewAdapter extends RecyclerView.Adapter<SDRecyclerViewAdapter.ViewHolder> {

        private final SDPartitionMeta meta;

        public SDRecyclerViewAdapter(SDPartitionMeta meta) {
            this.meta = meta;
        }

        @NotNull
        @Override
        public SDRecyclerViewAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.sd_part, parent, false);
            return new SDRecyclerViewAdapter.ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NotNull SDRecyclerViewAdapter.ViewHolder holder, int position) {
            Partition p;
            for (int i = 0; i < meta.count(); i++) {
                p = meta.dumpPartition(i);
                switch (p.type) {
                    case ADOPTED:
                        holder.icon.setImageDrawable(ContextCompat.getDrawable(requireActivity(), R.drawable.ic_adopted));
                        break;
                    case PORTABLE:
                        holder.icon.setImageDrawable(ContextCompat.getDrawable(requireActivity(), R.drawable.ic_sd));
                        break;
                    case RESERVED:
                        holder.icon.setImageDrawable(ContextCompat.getDrawable(requireActivity(), R.drawable.ic_meta));
                        break;
                    case UNKNOWN:
                    default:
                        holder.icon.setImageDrawable(ContextCompat.getDrawable(requireActivity(), R.drawable.ic_unknown));
                        break;
                }
            }
        }

        @Override
        public int getItemCount() {
            return meta.count();
        }

        public class ViewHolder extends RecyclerView.ViewHolder {
            public ImageView icon;
            public View container;

            public ViewHolder(View view) {
                super(view);
                icon = view.findViewById(R.id.wizard_installer_finddevice_radio);
                container.setOnClickListener(v -> {
                    notifyDataSetChanged();
                    //SDCardFragment.this
                });
            }
        }
    }

    @Nullable
    public SDPartitionMeta generateMeta() {
        SDPartitionMeta meta;
        Shell.Result r = Shell.su("sgdisk /dev/block/mmcblk1 --print").exec();
        if (r.isSuccess())
            meta = new SDPartitionMeta();
        else
            return null;
        List<String> out = r.getOut();
        List<List<String>> nout = new ArrayList<>();
        for (String o : out) {
            if (o.startsWith("Disk ") && !o.contains("GUID")) {
                String[] t = o.split(": ")[1].split(", ");
                meta.sectors = Integer.parseInt(t[0].replace(" sectors",""));
                meta.friendlySize = t[1];
            } else if(o.startsWith("Logical sector size: ") && o.endsWith(" bytes")) {
                meta.logicalSectorSizeBytes = Integer.parseInt(o.replace("Logical sector size: ","").replace(" bytes",""));
            } else if(o.startsWith("Disk identifier (GUID): ")) {
                meta.guid = o.replace("Disk identifier (GUID): ", "");
            } else if (o.startsWith("Partition table holds up to ")) {
                meta.maxEntries = Integer.parseInt(o.replace("Partition table holds up to ","").replace(" entries",""));
            } else if (o.startsWith("First usable sector is ")) {
                meta.firstUsableSector = Integer.parseInt(o.replace("First usable sector is ","").replaceFirst(", last usable sector is .*$",""));
                meta.lastUsableSector = Integer.parseInt(o.replace("First usable sector is " + meta.firstUsableSector + ", last usable sector is ",""));
                meta.usableSectors = meta.lastUsableSector - meta.firstUsableSector;
            } else if (o.startsWith("Partitions will be aligned on ") && o.endsWith("-sector boundaries")) {
                meta.alignSector = Integer.parseInt(o.replace("Partitions will be aligned on ","").replace("-sector boundaries",""));
            } else if (o.startsWith("Total free space is ") && o.contains("sectors")) {
                String[] t = o.replace(")","").split("\\(");
                meta.totalFreeSectors = Integer.parseInt(t[0].replace("Total free space is ","").replace(" sectors ",""));
                meta.totalFreeFancy = t[1];
            } else if(o.equals("") || o.startsWith("Number  Start (sector)    End (sector)  Size       Code  Name")) {
                assert true; //avoid empty statement warning but do nothing
            } else {
                String[] ocut = o.trim().replace("     "," ").replace("   "," ").replace("  "," ").split(" ");
                nout.add(Arrays.asList(ocut[0], ocut[1], ocut[2], ocut[3] + " " + ocut[4], ocut[5], String.join(" ",Arrays.copyOfRange(ocut, 6, ocut.length))));
            }
        }
        for (List<String> fields : nout) {
            Partition p = new Partition();
            p.id = Integer.parseInt(fields.get(0));
            p.startSector = Integer.parseInt(fields.get(1));
            p.endSector = Integer.parseInt(fields.get(2));
            p.sizeFancy = fields.get(3);
            p.size = p.endSector - p.startSector;
            p.code = fields.get(4);
            p.name = fields.get(5);
            meta.p.add(p);
        }
        Log.i("ABM",meta.toString());
        return meta;
    }

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        model = new ViewModelProvider(requireActivity()).get(InstalledViewModel.class);
        View root = inflater.inflate(R.layout.wizard_installer_finddevice, container, false);
        final RecyclerView recyclerView = root.findViewById(R.id.wizard_installer_finddevice);
        LinearLayoutManager recyclerLayoutManager = new LinearLayoutManager(requireContext());
        recyclerView.setLayoutManager(recyclerLayoutManager);
        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(recyclerView.getContext(), recyclerLayoutManager.getOrientation());
        recyclerView.addItemDecoration(dividerItemDecoration);
        SDRecyclerViewAdapter recyclerViewAdapter = new SDRecyclerViewAdapter(generateMeta());
        recyclerView.setAdapter(recyclerViewAdapter);
        return root;
    }
}