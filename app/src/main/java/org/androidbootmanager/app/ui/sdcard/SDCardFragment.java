package org.androidbootmanager.app.ui.sdcard;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.slider.RangeSlider;
import com.topjohnwu.superuser.Shell;
import com.topjohnwu.superuser.io.SuFile;
import com.topjohnwu.superuser.io.SuFileInputStream;
import com.topjohnwu.superuser.io.SuFileOutputStream;

import org.androidbootmanager.app.R;
import org.androidbootmanager.app.devices.DeviceList;
import org.androidbootmanager.app.ui.activities.SplashActivity;
import org.androidbootmanager.app.ui.addrom.DeviceROMInstallerWizardPageFragment;
import org.androidbootmanager.app.ui.home.InstalledViewModel;
import org.androidbootmanager.app.util.MiscUtils;
import org.androidbootmanager.app.util.SDUtils;
import org.androidbootmanager.app.util.SOUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.androidbootmanager.app.util.SDUtils.generateMeta;

public class SDCardFragment extends Fragment {

    private InstalledViewModel model;
    private RecyclerView recyclerView;
    public String outpath;

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
                final String bdev = DeviceList.getModel(model).bdev;
                final String pbdev = DeviceList.getModel(model).pbdev;
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
                        Runnable rs = () -> {
                            Editable s = start.getText();
                            if ((float) (Long.parseLong(s.toString()) - meta.dumpS(id).startSector) < slider.getValueFrom()) {
                                slider.setValues(0f, 1f);
                            } else if ((float) (Long.parseLong(s.toString()) - meta.dumpS(id).startSector) > slider.getValues().get(1)) {
                                slider.setValues(slider.getValues().get(1) - 1, slider.getValues().get(1));
                            } else {
                                slider.setValues((float) (Long.parseLong(s.toString()) - meta.dumpS(id).startSector), slider.getValues().get(1));
                            }
                        };
                        Runnable re = () -> {
                            Editable s = end.getText();
                            if ((float) (Long.parseLong(s.toString()) - meta.dumpS(id).startSector) > slider.getValueTo()) {
                                slider.setValues(slider.getValues().get(0), slider.getValueTo());
                            } else if ((float) (Long.parseLong(s.toString()) - meta.dumpS(id).startSector) < slider.getValues().get(0)) {
                                slider.setValues(slider.getValues().get(0) - 1, slider.getValues().get(0));
                            } else {
                                slider.setValues(slider.getValues().get(0), (float) (Long.parseLong(s.toString()) - meta.dumpS(id).startSector));
                            }
                        };
                        slider.addOnChangeListener((a, b, c) -> {
                            List<Float> values = slider.getValues();
                            float from = values.get(0);
                            float to = values.get(1);
                            if (!start.getText().toString().equals(String.valueOf(meta.dumpS(id).startSector + (long) from))) {
                                start.setText(String.valueOf(meta.dumpS(id).startSector + (long) from)); rs.run();
                            }
                            if (!end.getText().toString().equals(String.valueOf(meta.dumpS(id).startSector + (long) to))) {
                                end.setText(String.valueOf(meta.dumpS(id).startSector + (long) to)); re.run();
                            }
                            if (!size.getText().toString().equals(SOUtils.humanReadableByteCountBin((long) (to - from) * meta.logicalSectorSizeBytes)))
                                size.setText(SOUtils.humanReadableByteCountBin((long) (to - from) * meta.logicalSectorSizeBytes));
                        });
                        dd.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                            @Override
                            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                                if (ddresolv[position].equals(ddresolv[3]))
                                    slider.setValues(0f, DeviceList.getModel(model).spartsize);
                            }

                            @Override
                            public void onNothingSelected(AdapterView<?> parent) {
                            }
                        });
                        AtomicInteger xid = new AtomicInteger();
                        start.setOnFocusChangeListener((view1, hasFocus) -> rs.run());
                        end.setOnFocusChangeListener((view1, hasFocus) -> re.run());
                        start.addTextChangedListener(new TextWatcher() {
                            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
                            @Override
                            public void afterTextChanged(Editable s) { int mid = xid.incrementAndGet(); new Handler().postDelayed(() -> {
                                if (mid == xid.get())
                                    rs.run();
                            }, 1000); }
                        });
                        end.addTextChangedListener(new TextWatcher() {
                            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
                            @Override
                            public void afterTextChanged(Editable s) { int mid = xid.incrementAndGet(); new Handler().postDelayed(() -> {
                                if (mid == xid.get())
                                    rs.run();
                            }, 1000); }
                        });
                        dd.setAdapter(new ArrayAdapter<>(requireActivity(), android.R.layout.simple_spinner_dropdown_item, new String[]{getString(R.string.portable_part), getString(R.string.data_part), getString(R.string.meta_part), getString(R.string.system_part), getString(R.string.unknown_part)}));
                        start.setText(String.valueOf(meta.dumpS(id).startSector));
                        end.setText(String.valueOf(meta.dumpS(id).endSector));
                        ArrayList<String> out = new ArrayList<>();
                        ArrayList<String> err = new ArrayList<>();
                        new AlertDialog.Builder(requireContext())
                                .setTitle(getString(R.string.free_space_size, meta.dumpS(id).sizeFancy))
                                .setView(v)
                                .setNegativeButton(R.string.cancel, (d, p) -> d.dismiss())
                                .setPositiveButton(R.string.create, (d, p) -> MiscUtils.w(requireContext(), R.string.creating_prog, () -> Shell.su(SDUtils.umsd(meta) + " && sgdisk " + bdev + " --new " + meta.nid + ":" + start.getText() + ":" + end.getText() + " --typecode " + meta.nid + ":" + ddresolv[dd.getSelectedItemPosition()] + " --change-name " + meta.nid + ":'" + label.getText().toString().replace("'", "") + "' && sleep 1 && ls " + pbdev + meta.nid + (ddresolv[dd.getSelectedItemPosition()].equals("0700") ? (" && sm format public:" + meta.major + "," + (meta.minor + meta.nid)) : (ddresolv[dd.getSelectedItemPosition()].equals("8301") ? " && mkfs.ext4 " + pbdev + meta.nid : " && echo Warning: Unsure on how to format this partition."))).to(out, err).submit(MiscUtils.w2((r) -> new AlertDialog.Builder(requireContext())
                                        .setTitle(r.isSuccess() ? R.string.successful : R.string.failed)
                                        .setMessage(String.join("\n", out) + "\n" + String.join("\n", err) + (String.join("", out).contains("old") ? "IMPORTANT: Please reboot!" : ""))
                                        .setPositiveButton(R.string.ok, (g, l) -> recyclerView.setAdapter(new SDRecyclerViewAdapter(generateMeta(DeviceList.getModel(model)))))
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
                        final Toolbar t = new Toolbar(requireContext());
                        final Toolbar bt = new Toolbar(requireContext());
                        final AlertDialog[] bdialog = new AlertDialog[1];
                        t.setBackgroundColor(getResources().getColor(R.color.colorAccent, requireActivity().getTheme()));
                        bt.setBackgroundColor(getResources().getColor(R.color.colorAccent, requireActivity().getTheme()));
                        t.setTitle(getString(R.string.partidn, meta.dumpS(id).id));
                        bt.setTitle(getString(R.string.partidn, meta.dumpS(id).id));
                        t.setNavigationIcon(android.R.drawable.ic_menu_close_clear_cancel);
                        bt.setNavigationIcon(android.R.drawable.ic_menu_close_clear_cancel);
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
                        ArrayList<String> out = new ArrayList<>();
                        ArrayList<String> err = new ArrayList<>();
                        final AlertDialog[] dialog = {new AlertDialog.Builder(requireContext())
                                .setCustomTitle(t)
                                .setNegativeButton(R.string.delete, (w, p1) -> MiscUtils.sure(requireContext(), w, getString(R.string.delete_msg, meta.dumpS(id).id, SDUtils.codes.get(meta.dumpS(id).code), meta.dumpS(id).name), (d, p) -> {
                                    MiscUtils.w(requireContext(), R.string.delete_prog, () -> Shell.su(SDUtils.umsd(meta) + " && sgdisk " + bdev + " --delete " + meta.dumpS(id).id).to(out, err).submit(MiscUtils.w2((r) -> new AlertDialog.Builder(requireContext())
                                            .setTitle(r.isSuccess() ? R.string.successful : R.string.failed)
                                            .setMessage(String.join("\n", out) + "\n" + String.join("", err) + (String.join("", out).contains("old") ? "IMPORTANT: Please reboot!" : ""))
                                            .setPositiveButton(R.string.ok, (g, s) -> recyclerView.setAdapter(new SDRecyclerViewAdapter(generateMeta(DeviceList.getModel(model)))))
                                            .setCancelable(false)
                                            .show())));
                                }))
                                .setNeutralButton(R.string.rename, (d, p) -> MiscUtils.w(requireContext(), R.string.renaming_prog, () -> Shell.su(SDUtils.umsd(meta) + " && sgdisk " + bdev + " --change-name " + meta.dumpS(id).id + ":'" + label.getText().toString().replace("'", "") + "'").to(out, err).submit(MiscUtils.w2((r) -> new AlertDialog.Builder(requireContext())
                                        .setTitle(r.isSuccess() ? R.string.successful : R.string.failed)
                                        .setMessage(String.join("\n", out) + "\n" + String.join("", err) + (String.join("", out).contains("old") ? "IMPORTANT: Please reboot!" : ""))
                                        .setPositiveButton(R.string.ok, (g, s) -> recyclerView.setAdapter(new SDRecyclerViewAdapter(generateMeta(DeviceList.getModel(model)))))
                                        .setCancelable(false)
                                        .show()))))
                                .setPositiveButton(R.string.backup, (d, p) -> bdialog[0] = new AlertDialog.Builder(requireContext())
                                        .setCustomTitle(bt)
                                        .setMessage(R.string.backup_msg)
                                        .setPositiveButton(R.string.backup, (d2, p2) -> {
                                            outpath = meta.ppath + meta.dumpS(id).id;
                                            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                                            intent.setType("application/octet-stream");
                                            intent.addCategory(Intent.CATEGORY_OPENABLE);
                                            intent.putExtra(Intent.EXTRA_TITLE, "abm_backup_" + meta.dumpS(id).id + ".img");
                                            startActivityForResult(intent, 5211);
                                        })
                                        .setNegativeButton(R.string.restore, (d2, p2) -> {
                                            outpath = meta.ppath + meta.dumpS(id).id;
                                            Intent intent = new Intent();
                                            intent.setType("*/*");
                                            intent.setAction(Intent.ACTION_GET_CONTENT);
                                            startActivityForResult(intent, 5212);
                                        })
                                        .setNeutralButton(R.string.restoresparse, (d2, p2) -> {
                                            outpath = meta.ppath + meta.dumpS(id).id;
                                            Intent intent = new Intent();
                                            intent.setType("*/*");
                                            intent.setAction(Intent.ACTION_GET_CONTENT);
                                            startActivityForResult(intent, 5299);
                                        })
                                        .show())
                                .setView(v)
                                .create()};
                        t.setNavigationOnClickListener((a) -> dialog[0].dismiss());
                        bt.setNavigationOnClickListener(a -> bdialog[0].dismiss());
                        dialog[0].show();
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
        final String bdev = DeviceList.getModel(model).bdev;
        SDUtils.setupCodes(requireContext());
        AtomicReference<SDUtils.SDPartitionMeta> meta = new AtomicReference<>(generateMeta(DeviceList.getModel(model)));
        if (meta.get() == null) {
            if (String.join("",Shell.su("sgdisk " + bdev + " --print").exec().getOut()).contains("invalid GPT and valid MBR")) {
                ArrayList<String> out = new ArrayList<>();
                ArrayList<String> err = new ArrayList<>();
                new AlertDialog.Builder(requireActivity())
                        .setNegativeButton("Close", (d, p) -> requireActivity().finish())
                        .setCancelable(false)
                        .setMessage(R.string.sd_mbr)
                        .setTitle(R.string.fatal)
                        .setPositiveButton(R.string.convert, (d, p) -> MiscUtils.w(requireContext(), R.string.convert_prog, () -> Shell.su("sm unmount `sm list-volumes public` && sgdisk " + bdev + " --mbrtogpt").to(out, err).submit(MiscUtils.w2((r) -> new AlertDialog.Builder(requireActivity())
                                .setTitle(r.isSuccess() ? R.string.successful : R.string.failed)
                                .setMessage(String.join("\n", out) + "\n" + String.join("\n", err))
                                .setCancelable(false)
                                .setPositiveButton(R.string.ok, (d2, p2) -> {
                                    if (r.isSuccess()) {
                                        meta.set(generateMeta(DeviceList.getModel(model)));
                                        recyclerView.setAdapter(new SDRecyclerViewAdapter(meta.get()));
                                    } else {
                                        requireActivity().finish();
                                    }
                                })
                                .show()))))
                        .show();
            } else
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

    @SuppressLint("SdCardPath")
    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == 5212) {
            if (resultCode == Activity.RESULT_OK) {
                assert data != null;
                Uri selectedUri = data.getData();
                try {
                    InputStream initialStream;
                    if (selectedUri != null) {
                        initialStream = requireActivity().getContentResolver().openInputStream(selectedUri);
                    } else {
                        Toast.makeText(requireContext(), R.string.failure, Toast.LENGTH_LONG).show();
                        new IllegalStateException("null selected").printStackTrace();
                        return;
                    }
                    MiscUtils.w(requireContext(), R.string.restore, () -> new Thread(MiscUtils.w2t(requireActivity(), () -> {
                        try {
                            File targetFile = new File(outpath);
                            assert initialStream != null;
                            OutputStream outStream = SuFileOutputStream.open(targetFile);
                            SplashActivity.copyFile(initialStream, outStream);
                            initialStream.close();
                            outStream.close();
                        } catch (IOException e) {
                            requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(), R.string.failure, Toast.LENGTH_LONG).show());
                            e.printStackTrace();
                        }
                    })).start());
                } catch (IOException e) {
                    Toast.makeText(requireContext(), R.string.failure, Toast.LENGTH_LONG).show();
                    e.printStackTrace();
                }
            } else {
                Toast.makeText(requireContext(), R.string.failure, Toast.LENGTH_LONG).show();
                new IllegalStateException("Result not OK but " + resultCode).printStackTrace();
            }
        } else if (requestCode == 5211) {
            if (resultCode == Activity.RESULT_OK) {
                assert data != null;
                Uri selectedUri = data.getData();
                try {
                    OutputStream initialStream;
                    if (selectedUri != null) {
                        initialStream = requireActivity().getContentResolver().openOutputStream(selectedUri);
                    } else {
                        Toast.makeText(requireContext(), R.string.failure, Toast.LENGTH_LONG).show();
                        new IllegalStateException("null selected").printStackTrace();
                        return;
                    }
                    MiscUtils.w(requireContext(), R.string.backup, () -> new Thread(MiscUtils.w2t(requireActivity(), () -> {
                        try {
                            File targetFile = new File(outpath);
                            InputStream inStream = SuFileInputStream.open(targetFile);
                            assert initialStream != null;
                            SplashActivity.copyFile(inStream, initialStream);
                            inStream.close();
                            initialStream.close();
                        } catch (IOException e) {
                            requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(), R.string.failure, Toast.LENGTH_LONG).show());
                            e.printStackTrace();
                        }
                    })).start());
                } catch (IOException e) {
                    Toast.makeText(requireContext(), R.string.failure, Toast.LENGTH_LONG).show();
                    e.printStackTrace();
                }
            } else {
                Toast.makeText(requireContext(), R.string.failure, Toast.LENGTH_LONG).show();
                new IllegalStateException("Result not OK but " + resultCode).printStackTrace();
            }
        } else if (requestCode == 5299) {
            if (resultCode == Activity.RESULT_OK) {
                assert data != null;
                Uri selectedUri = data.getData();
                try {
                    InputStream initialStream;
                    if (selectedUri != null) {
                        initialStream = requireActivity().getContentResolver().openInputStream(selectedUri);
                    } else {
                        Toast.makeText(requireContext(), R.string.failure, Toast.LENGTH_LONG).show();
                        new IllegalStateException("null selected").printStackTrace();
                        return;
                    }
                    MiscUtils.w(requireContext(), R.string.backup, () -> new Thread(MiscUtils.w2t(requireActivity(), () -> {
                        try {
                            File targetFile = new File("/data/data/org.androidbootmanager.app/cache/unsparse.img");
                            assert initialStream != null;
                            OutputStream outStream = SuFileOutputStream.open(targetFile);
                            SplashActivity.copyFile(initialStream, outStream);
                            initialStream.close();
                            outStream.close();
                            Log.i("ABM","copy done");
                            Shell.su("/data/data/org.androidbootmanager.app/assets/Toolkit/simg2img /data/data/org.androidbootmanager.app/cache/unsparse.img " + outpath).exec();
                            Log.i("ABM","Result of delete: " + SuFile.open(targetFile.getAbsolutePath()).delete());
                        } catch (IOException e) {
                            requireActivity().runOnUiThread(() -> Toast.makeText(requireContext(), R.string.failure, Toast.LENGTH_LONG).show());
                            e.printStackTrace();
                        }
                    })).start());
                } catch (IOException e) {
                    Toast.makeText(requireContext(), R.string.failure, Toast.LENGTH_LONG).show();
                    e.printStackTrace();
                }
            } else {
                Toast.makeText(requireContext(), R.string.failure, Toast.LENGTH_LONG).show();
                new IllegalStateException("Result not OK but " + resultCode).printStackTrace();
            }
        }
        else super.onActivityResult(requestCode, resultCode, data);
    }
}
