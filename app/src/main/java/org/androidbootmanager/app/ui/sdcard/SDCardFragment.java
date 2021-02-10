package org.androidbootmanager.app.ui.sdcard;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.slider.RangeSlider;
import com.topjohnwu.superuser.Shell;

import org.androidbootmanager.app.R;
import org.androidbootmanager.app.devices.DeviceList;
import org.androidbootmanager.app.ui.home.InstalledViewModel;
import org.androidbootmanager.app.util.MiscUtils;
import org.androidbootmanager.app.util.SDUtils;
import org.androidbootmanager.app.util.SOUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import static org.androidbootmanager.app.util.SDUtils.generateMeta;

public class SDCardFragment extends Fragment {

    private InstalledViewModel model;
    private RecyclerView recyclerView;

    public class SDRecyclerViewAdapter extends RecyclerView.Adapter<SDRecyclerViewAdapter.ViewHolder> {

        private final SDUtils.SDPartitionMeta meta;

        public SDRecyclerViewAdapter(SDUtils.SDPartitionMeta meta) {
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
            SDUtils.Partition p;
            p = meta.dumpS(position);
            holder.id = position;
            switch (p.type) {
                case ADOPTED:
                    holder.icon.setImageDrawable(ContextCompat.getDrawable(requireActivity(), R.drawable.ic_adopted));
                    holder.text.setText(R.string.adopted_part);
                    break;
                case PORTABLE:
                    holder.icon.setImageDrawable(ContextCompat.getDrawable(requireActivity(), R.drawable.ic_sd));
                    holder.text.setText(R.string.portable_part);
                    break;
                case RESERVED:
                    holder.icon.setImageDrawable(ContextCompat.getDrawable(requireActivity(), R.drawable.ic_meta));
                    holder.text.setText(R.string.meta_part);
                    break;
                case DATA:
                    holder.icon.setImageDrawable(ContextCompat.getDrawable(requireActivity(), R.drawable.ic_data));
                    holder.text.setText(R.string.data_part);
                    break;
                case SYSTEM:
                    holder.icon.setImageDrawable(ContextCompat.getDrawable(requireActivity(), R.drawable.ic_system));
                    holder.text.setText(R.string.system_part);
                    break;
                case FREE:
                    holder.icon.setVisibility(View.INVISIBLE);
                    holder.text.setText(R.string.free_space);
                    break;
                case UNKNOWN:
                default:
                    holder.icon.setImageDrawable(ContextCompat.getDrawable(requireActivity(), R.drawable.ic_unknown));
                    holder.text.setText(getString(R.string.unknown_part));
                    break;
            }
            holder.size.setText(p.sizeFancy);
        }

        @Override
        public int getItemCount() {
            return meta.count();
        }

        public class ViewHolder extends RecyclerView.ViewHolder {
            public final ImageView icon;
            public final TextView text;
            public final TextView size;
            public final View container;
            public int id;

            public ViewHolder(View view) {
                super(view);
                final String bdev = DeviceList.getModel(Objects.requireNonNull(model.getCodename().getValue())).bdev;
                final String pbdev = DeviceList.getModel(Objects.requireNonNull(model.getCodename().getValue())).pbdev;
                icon = view.findViewById(R.id.sd_icon);
                container = view.findViewById(R.id.sd_container);
                text = view.findViewById(R.id.sd_label);
                size = view.findViewById(R.id.sd_size);
                container.setOnClickListener(e -> {
                    if (meta.dumpS(id).type == SDUtils.PartitionType.FREE) {
                        View v = getLayoutInflater().inflate(R.layout.create_part, null);
                        final String[] ddresolv = new String[]{"0700", "8302", "8301", "8305", "8300"};
                        final EditText start = v.findViewById(R.id.create_part_start);
                        final EditText end = v.findViewById(R.id.create_part_end);
                        final EditText label = v.findViewById(R.id.create_part_label);
                        final Spinner dd = v.findViewById(R.id.create_part_dd);
                        final RangeSlider slider = v.findViewById(R.id.create_part_slide);
                        final TextView size = v.findViewById(R.id.create_part_size);
                        size.setText(meta.dumpS(id).sizeFancy);
                        slider.setValueFrom(0f);
                        slider.setValueTo((float) (meta.dumpS(id).endSector - meta.dumpS(id).startSector));
                        slider.setValues(0f, (float) (meta.dumpS(id).endSector - meta.dumpS(id).startSector));
                        slider.setStepSize(1);
                        slider.setMinSeparationValue(2048);
                        slider.addOnChangeListener((a, b, c) -> {
                            List<Float> values = slider.getValues();
                            float from = values.get(0);
                            float to = values.get(1);
                            if (!start.getText().toString().equals(String.valueOf(meta.dumpS(id).startSector + (long) from)))
                                start.setText(String.valueOf(meta.dumpS(id).startSector + (long) from));
                            if (!end.getText().toString().equals(String.valueOf(meta.dumpS(id).startSector + (long) to)))
                                end.setText(String.valueOf(meta.dumpS(id).startSector + (long) to));
                            if (!size.getText().toString().equals(SOUtils.humanReadableByteCountBin((long) (to - from) * meta.logicalSectorSizeBytes)))
                                size.setText(SOUtils.humanReadableByteCountBin((long) (to - from) * meta.logicalSectorSizeBytes));
                        });
                        dd.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                            @Override
                            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                                if (ddresolv[position].equals(ddresolv[3]))
                                    slider.setValues(0f, 7340031f);
                            }

                            @Override
                            public void onNothingSelected(AdapterView<?> parent) {
                            }
                        });
                        start.addTextChangedListener(new TextWatcher() {
                            @Override
                            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                            }

                            @Override
                            public void onTextChanged(CharSequence s, int start, int before, int count) {
                            }

                            @Override
                            public void afterTextChanged(Editable s) {
                                if ((float) (Long.parseLong(s.toString()) - meta.dumpS(id).startSector) < slider.getValueFrom()) {
                                    slider.setValues(0f, 1f);
                                } else if ((float) (Long.parseLong(s.toString()) - meta.dumpS(id).startSector) > slider.getValues().get(1)) {
                                    slider.setValues(slider.getValues().get(1) - 1, slider.getValues().get(1));
                                } else {
                                    slider.setValues((float) (Long.parseLong(s.toString()) - meta.dumpS(id).startSector), slider.getValues().get(1));
                                }
                            }
                        });
                        end.addTextChangedListener(new TextWatcher() {
                            @Override
                            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                            }

                            @Override
                            public void onTextChanged(CharSequence s, int start, int before, int count) {
                            }

                            @Override
                            public void afterTextChanged(Editable s) {
                                if ((float) (Long.parseLong(s.toString()) - meta.dumpS(id).startSector) > slider.getValueTo()) {
                                    slider.setValues(slider.getValues().get(0), slider.getValueTo());
                                } else if ((float) (Long.parseLong(s.toString()) - meta.dumpS(id).startSector) < slider.getValues().get(0)) {
                                    slider.setValues(slider.getValues().get(0) - 1, slider.getValues().get(0));
                                } else {
                                    slider.setValues(slider.getValues().get(0), (float) (Long.parseLong(s.toString()) - meta.dumpS(id).startSector));
                                }
                            }
                        });
                        dd.setAdapter(new ArrayAdapter<>(requireActivity(), android.R.layout.simple_spinner_dropdown_item, new String[]{getString(R.string.portable_part), getString(R.string.data_part), getString(R.string.meta_part), getString(R.string.system_part), getString(R.string.unknown_part)}));
                        start.setText(String.valueOf(meta.dumpS(id).startSector));
                        end.setText(String.valueOf(meta.dumpS(id).endSector));
                        new AlertDialog.Builder(requireContext())
                                .setTitle(getString(R.string.free_space_size, meta.dumpS(id).sizeFancy))
                                .setView(v)
                                .setNegativeButton(R.string.cancel, (d, p) -> d.dismiss())
                                .setPositiveButton(R.string.create, (d, p) -> MiscUtils.w(requireContext(), R.string.creating_prog, () -> Shell.su(SDUtils.umsd(meta) + " && sgdisk " + bdev + " --new " + meta.nid + ":" + start.getText() + ":" + end.getText() + " --typecode " + meta.nid + ":" + ddresolv[dd.getSelectedItemPosition()] + " --change-name " + meta.nid + ":'" + label.getText().toString().replace("'", "") + "' && ls " + pbdev + meta.nid + (ddresolv[dd.getSelectedItemPosition()].equals("0700") ? (" && sm format public:" + meta.major + "," + (meta.minor + meta.nid)) : (ddresolv[dd.getSelectedItemPosition()].equals("8301") ? " && mkfs.ext4 " + pbdev + meta.nid : " && true"))).submit(MiscUtils.w2((r) -> new AlertDialog.Builder(requireContext())
                                        .setTitle(r.isSuccess() ? R.string.successful : R.string.failed)
                                        .setMessage(String.join("\n", r.getOut()) + "\n" + String.join("\n", r.getErr()) + (String.join("", r.getOut()).contains("old") ? "IMPORTANT: Please reboot!" : ""))
                                        .setPositiveButton(R.string.ok, (g, l) -> recyclerView.setAdapter(new SDRecyclerViewAdapter(generateMeta(DeviceList.getModel(Objects.requireNonNull(model.getCodename().getValue()))))))
                                        .setCancelable(false)
                                        .show()))))
                                .show();
                    } else {
                        View v = getLayoutInflater().inflate(R.layout.create_part, null);
                        final String[] ddresolv = new String[]{"0700", "8302", "8301", "8305", "8300"};
                        final EditText start = v.findViewById(R.id.create_part_start);
                        final EditText end = v.findViewById(R.id.create_part_end);
                        final EditText label = v.findViewById(R.id.create_part_label);
                        final Spinner dd = v.findViewById(R.id.create_part_dd);
                        final RangeSlider slider = v.findViewById(R.id.create_part_slide);
                        final TextView size = v.findViewById(R.id.create_part_size);
                        size.setText(meta.dumpS(id).sizeFancy);
                        slider.setValueFrom(0f);
                        slider.setValueTo((float) (meta.dumpS(id).endSector - meta.dumpS(id).startSector));
                        slider.setValues(0f, (float) (meta.dumpS(id).endSector - meta.dumpS(id).startSector));
                        slider.setStepSize(1);
                        slider.setMinSeparationValue(2048);
                        dd.setAdapter(new ArrayAdapter<>(requireActivity(), android.R.layout.simple_spinner_dropdown_item, new String[]{getString(R.string.portable_part), getString(R.string.data_part), getString(R.string.meta_part), getString(R.string.system_part), getString(R.string.unknown_part)}));
                        start.setText(String.valueOf(meta.dumpS(id).startSector));
                        end.setText(String.valueOf(meta.dumpS(id).endSector));
                        dd.setSelection(Arrays.asList(ddresolv).indexOf(meta.dumpS(id).code));
                        start.setEnabled(false);
                        end.setEnabled(false);
                        dd.setEnabled(false);
                        slider.setEnabled(false);
                        label.setText(meta.dumpS(id).name);
                        new AlertDialog.Builder(requireContext())
                                .setTitle(getString(R.string.partid, meta.dumpS(id).id, meta.dumpS(id).name))
                                .setNeutralButton(R.string.cancel, (d, p) -> d.dismiss())
                                .setNegativeButton(R.string.delete, (w, p1) -> MiscUtils.sure(requireContext(), w, getString(R.string.delete_msg, meta.dumpS(id).id, SDUtils.codes.get(meta.dumpS(id).code), meta.dumpS(id).name), (d, p) -> {
                                    MiscUtils.w(requireContext(), R.string.delete_prog, () -> Shell.su(SDUtils.umsd(meta) + " && sgdisk " + bdev + " --delete " + meta.dumpS(id).id).submit(MiscUtils.w2((r) -> new AlertDialog.Builder(requireContext())
                                            .setTitle(r.isSuccess() ? R.string.successful : R.string.failed)
                                            .setMessage(String.join("\n", r.getOut()) + "\n" + String.join("", r.getErr()) + (String.join("", r.getOut()).contains("old") ? "IMPORTANT: Please reboot!" : ""))
                                            .setPositiveButton(R.string.ok, (g, s) -> recyclerView.setAdapter(new SDRecyclerViewAdapter(generateMeta(DeviceList.getModel(Objects.requireNonNull(model.getCodename().getValue()))))))
                                            .setCancelable(false)
                                            .show())));
                                }))
                                .setPositiveButton(R.string.rename, (d, p) -> MiscUtils.w(requireContext(), R.string.renaming_prog, () -> Shell.su(SDUtils.umsd(meta) + " && sgdisk " + bdev + " --change-name " + meta.dumpS(id).id + ":'" + label.getText().toString().replace("'","") + "'").submit(MiscUtils.w2((r) -> new AlertDialog.Builder(requireContext())
                                        .setTitle(r.isSuccess() ? R.string.successful : R.string.failed)
                                        .setMessage(String.join("\n", r.getOut()) + "\n" + String.join("", r.getErr()) + (String.join("", r.getOut()).contains("old") ? "IMPORTANT: Please reboot!" : ""))
                                        .setPositiveButton(R.string.ok, (g, s) -> recyclerView.setAdapter(new SDRecyclerViewAdapter(generateMeta(DeviceList.getModel(Objects.requireNonNull(model.getCodename().getValue()))))))
                                        .setCancelable(false)
                                        .show()))))
                                .setView(v)
                                .show();
                    }
                });
            }
        }
    }

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        model = new ViewModelProvider(requireActivity()).get(InstalledViewModel.class);
        View root = inflater.inflate(R.layout.wizard_installer_finddevice, container, false);
        recyclerView = root.findViewById(R.id.wizard_installer_finddevice);
        LinearLayoutManager recyclerLayoutManager = new LinearLayoutManager(requireContext());
        recyclerView.setLayoutManager(recyclerLayoutManager);
        recyclerView.addItemDecoration(new DividerItemDecoration(recyclerView.getContext(), recyclerLayoutManager.getOrientation()));
        final String bdev = DeviceList.getModel(Objects.requireNonNull(model.getCodename().getValue())).bdev;
        SDUtils.setupCodes(requireContext());
        AtomicReference<SDUtils.SDPartitionMeta> meta = new AtomicReference<>(generateMeta(DeviceList.getModel(Objects.requireNonNull(model.getCodename().getValue()))));
        if (meta.get() == null) {
            if (String.join("",Shell.su("sgdisk " + bdev + " --print").exec().getOut()).contains("invalid GPT and valid MBR"))
                new AlertDialog.Builder(requireActivity())
                    .setNegativeButton("Close", (d, p) -> requireActivity().finish())
                    .setCancelable(false)
                    .setMessage(R.string.sd_mbr)
                    .setTitle(R.string.fatal)
                    .setPositiveButton(R.string.convert, (d, p) -> MiscUtils.w(requireContext(), R.string.convert_prog, () -> Shell.su("sm unmount `sm list-volumes public` && sgdisk " + bdev + " --mbrtogpt").submit(MiscUtils.w2((r) -> new AlertDialog.Builder(requireActivity())
                            .setTitle(r.isSuccess() ? R.string.successful : R.string.failed)
                            .setMessage(String.join("\n", r.getOut()) + "\n" + String.join("\n", r.getErr()))
                            .setCancelable(false)
                            .setPositiveButton(R.string.ok, (d2, p2) -> {
                                if (r.isSuccess()) {
                                    meta.set(generateMeta(DeviceList.getModel(Objects.requireNonNull(model.getCodename().getValue()))));
                                    recyclerView.setAdapter(new SDRecyclerViewAdapter(meta.get()));
                                } else {
                                    requireActivity().finish();
                                }
                            })
                            .show()))))
                    .show();
            else
                new AlertDialog.Builder(requireActivity())
                .setNegativeButton("Close", (d, p) -> requireActivity().finish())
                .setCancelable(false)
                .setTitle(R.string.fatal)
                .setMessage(R.string.sd_err)
                .show();
        } else
            recyclerView.setAdapter(new SDRecyclerViewAdapter(meta.get()));
        return root;
    }
}
