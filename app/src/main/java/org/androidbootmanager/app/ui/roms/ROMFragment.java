package org.androidbootmanager.app.ui.roms;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.androidbootmanager.app.R;
import org.androidbootmanager.app.devices.DeviceModel;
import org.androidbootmanager.app.ui.installer.DeviceInstallerWizardPageFragment;
import org.androidbootmanager.app.ui.installer.FindDeviceWizardPageFragment;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ROMFragment extends Fragment {

    RecyclerView recyclerView;

    public class ROMRecyclerViewAdapter extends RecyclerView.Adapter<ROMRecyclerViewAdapter.ViewHolder> {

        private final List<DeviceModel> devicesList;

        private int lastSelectedPosition = -1;

        public ROMRecyclerViewAdapter(List<DeviceModel> devicesListIn) {
            devicesList = devicesListIn;
        }

        @NotNull
        @Override
        public ROMRecyclerViewAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.wizard_installer_finddevice_element, parent, false);
            return new ROMRecyclerViewAdapter.ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ROMRecyclerViewAdapter.ViewHolder holder, int position) {
            holder.selectionState.setText(devicesList.get(position).viewname);
            holder.selectionState.setChecked(lastSelectedPosition == position);
        }

        @Override
        public int getItemCount() {
            return devicesList.size();
        }

        public class ViewHolder extends RecyclerView.ViewHolder {
            public RadioButton selectionState;

            public ViewHolder(View view) {
                super(view);
                selectionState = view.findViewById(R.id.wizard_installer_finddevice_radio);
                selectionState.setOnClickListener(v -> {
                    lastSelectedPosition = getAdapterPosition();
                    notifyDataSetChanged();
                });
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
        return root;
    }
}