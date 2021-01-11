package org.androidbootmanager.app.ui.sdcard;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
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
import java.util.List;

public class SDCardFragment extends Fragment {

    private InstalledViewModel model;

    public enum PartitionType {
            RESERVED, ADOPTED, PORTABLE, UNKNOWN
    }

    public static class Partition {
        public PartitionType type;
    }

    public static class SDPartitionMeta {
        public List<Partition> p = new ArrayList<>();

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

    public SDPartitionMeta generateMeta() {
        SDPartitionMeta meta = null;
        Shell.Result r = Shell.su("sgdisk /dev/block/mmcblk1 --print | tail -n +10 | sed -e 's/^[ \\t]*//' -e 's/    / /g' -e 's/   / /g' -e 's/  / /g' | cut -d' ' -O : -f '1,2,3,4-5,6,7-'").exec();
        Log.e("ABM",String.join("",r.getOut()));
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