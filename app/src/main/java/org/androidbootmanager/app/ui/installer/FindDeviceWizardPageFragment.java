package org.androidbootmanager.app.ui.installer;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.androidbootmanager.app.R;
import org.androidbootmanager.app.devices.DeviceList;
import org.androidbootmanager.app.devices.DeviceModel;
import org.androidbootmanager.app.ui.wizard.WizardViewModel;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

public class FindDeviceWizardPageFragment extends Fragment {

    public class DeviceRecyclerViewAdapter extends RecyclerView.Adapter<DeviceRecyclerViewAdapter.ViewHolder> {

        private final List<DeviceModel> devicesList;
        private final Context context;

        private int lastSelectedPosition = -1;

        public DeviceRecyclerViewAdapter(List<DeviceModel> devicesListIn, Context ctx) {
            devicesList = devicesListIn;
            context = ctx;
        }

        @NotNull
        @Override
        public DeviceRecyclerViewAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.wizard_installer_finddevice_element, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(DeviceRecyclerViewAdapter.ViewHolder holder, int position) {
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
                selectionState = (RadioButton) view.findViewById(R.id.wizard_installer_finddevice_radio);
                selectionState.setOnClickListener(v -> {
                    lastSelectedPosition = getAdapterPosition();
                    notifyDataSetChanged();
                    Toast.makeText(DeviceRecyclerViewAdapter.this.context, "selected device is " + devicesList.get(lastSelectedPosition).codename, Toast.LENGTH_LONG).show();
                    FindDeviceWizardPageFragment.this.imodel.setCodename(devicesList.get(lastSelectedPosition).codename);
                    FindDeviceWizardPageFragment.this.model.setPositiveFragment(DeviceInstallerWizardPageFragment.class);
                });
            }
        }
    }

    protected WizardViewModel model;
    protected DeviceInstallerViewModel imodel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        model = new ViewModelProvider(requireActivity()).get(WizardViewModel.class);
        imodel = new ViewModelProvider(requireActivity()).get(DeviceInstallerViewModel.class);
        model.setPositiveFragment(null);
        model.setNegativeFragment(DeviceTestWizardPageFragment.class);
        model.setPositiveAction(() -> {});
        model.setNegativeAction(null);
        model.setPositiveText(getString(R.string.next));
        model.setNegativeText(getString(R.string.prev));
        final View root = inflater.inflate(R.layout.wizard_installer_finddevice, container, false);
        final RecyclerView recyclerView = root.findViewById(R.id.wizard_installer_finddevice);
        LinearLayoutManager recyclerLayoutManager = new LinearLayoutManager(requireContext());
        recyclerView.setLayoutManager(recyclerLayoutManager);
        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(recyclerView.getContext(), recyclerLayoutManager.getOrientation());
        recyclerView.addItemDecoration(dividerItemDecoration);
        DeviceRecyclerViewAdapter recyclerViewAdapter = new DeviceRecyclerViewAdapter(Arrays.asList(DeviceList.getModel("cedric"), DeviceList.getModel("yggdrasil")), requireContext());
        recyclerView.setAdapter(recyclerViewAdapter);
        return root;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }

}


