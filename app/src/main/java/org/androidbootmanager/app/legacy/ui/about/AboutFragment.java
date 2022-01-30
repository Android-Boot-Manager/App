package org.androidbootmanager.app.legacy.ui.about;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.mikepenz.aboutlibraries.LibsBuilder;

import org.androidbootmanager.app.R;

public class AboutFragment extends Fragment {
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_about, container, false);
        root.findViewById(R.id.aboutlib).setOnClickListener((view) -> new LibsBuilder().withFields(R.string.class.getFields()).start(requireActivity()));
        return root;
    }

}
