package org.androidbootmanager.app.devices;

import androidx.fragment.app.Fragment;

import java.util.List;

public class DeviceModel {
    public String codename = "";
    public String viewname = "";
    public List<Class<? extends Fragment>> flow;
    public boolean usesLegacyDir = false;
}