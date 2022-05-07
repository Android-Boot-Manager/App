package org.androidbootmanager.app.legacy.ui.addrom;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.androidbootmanager.app.R;
import org.androidbootmanager.app.legacy.roms.ROM;
import org.androidbootmanager.app.legacy.roms.ROMsList;
import org.androidbootmanager.app.legacy.ui.wizard.WizardViewModel;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class AddROMChooseWizardPageFragment extends Fragment {

    public class ROMRecyclerViewAdapter extends RecyclerView.Adapter<ROMRecyclerViewAdapter.ViewHolder> {

        private final List<ROM> romsList;

        private int lastSelectedPosition = -1;

        public ROMRecyclerViewAdapter(List<ROM> romsListIn) {
            romsList = romsListIn;
        }

        @NotNull
        @Override
        public ROMRecyclerViewAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.wizard_installer_finddevice_element, parent, false);
            return new ROMRecyclerViewAdapter.ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ROMRecyclerViewAdapter.ViewHolder holder, int position) {
            holder.selectionState.setText(romsList.get(position).viewname);
            holder.selectionState.setChecked(lastSelectedPosition == position);
        }

        @Override
        public int getItemCount() {
            return romsList.size();
        }

        public class ViewHolder extends RecyclerView.ViewHolder {
            public final RadioButton selectionState;

            public ViewHolder(View view) {
                super(view);
                selectionState = view.findViewById(R.id.wizard_installer_finddevice_radio);
                selectionState.setOnClickListener(v -> {
                    lastSelectedPosition = getBindingAdapterPosition();
                    notifyDataSetChanged();
                    AddROMChooseWizardPageFragment.this.imodel.setROM(romsList.get(lastSelectedPosition));
                    AddROMChooseWizardPageFragment.this.model.setPositiveFragment(DeviceROMInstallerWizardPageFragment.class);
                });
            }
        }
    }

    protected WizardViewModel model;
    protected AddROMViewModel imodel;
    protected ROMsList rl;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        model = new ViewModelProvider(requireActivity()).get(WizardViewModel.class);
        imodel = new ViewModelProvider(requireActivity()).get(AddROMViewModel.class);
        model.setPositiveFragment(null);
        model.setNegativeFragment(AddROMWelcomeWizardPageFragment.class);
        model.setPositiveAction(() -> {});
        model.setNegativeAction(null);
        model.setPositiveText(getString(R.string.next));
        model.setNegativeText(getString(R.string.prev));
        rl = new ROMsList(model.getCodename().getValue(), requireContext());
        final View root = inflater.inflate(R.layout.wizard_installer_finddevice, container, false);
        final RecyclerView recyclerView = root.findViewById(R.id.wizard_installer_finddevice);
        LinearLayoutManager recyclerLayoutManager = new LinearLayoutManager(requireContext());
        recyclerView.setLayoutManager(recyclerLayoutManager);
        DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(recyclerView.getContext(), recyclerLayoutManager.getOrientation());
        recyclerView.addItemDecoration(dividerItemDecoration);
        ROMRecyclerViewAdapter recyclerViewAdapter = new ROMRecyclerViewAdapter(rl.getROMs());
        recyclerView.setAdapter(recyclerViewAdapter);
        return root;
    }

}
