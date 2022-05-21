package org.andbootmgr.app.legacy.devices;

import org.andbootmgr.app.legacy.ui.home.InstalledViewModel;
import org.andbootmgr.app.legacy.ui.installer.DeviceInstallerViewModel;
import org.andbootmgr.app.legacy.ui.installer.DeviceInstallerWizardPageFragment;
import org.andbootmgr.app.legacy.ui.installer.DoInstallWizardPageFragment;
import org.andbootmgr.app.legacy.ui.installer.DroidBootSelectorWizardPageFragment;
import org.andbootmgr.app.legacy.ui.wizard.WizardViewModel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

@SuppressWarnings("CommentedOutCode")
public class DeviceList {
    public static final ArrayList<String> deviceList = new ArrayList<>();
    public static final HashMap<String, List<String>> bspList = new HashMap<>();
    static {
        //deviceList.add("cedric");
        deviceList.add("yggdrasil");
        bspList.put("k63v2_64_bsp", Collections.singletonList("yggdrasil"));
    }

    public static DeviceModel getModel(String codename) {
        DeviceModel d;
        //noinspection SwitchStatementWithTooFewBranches
        switch(codename) {
            case "yggdrasil":
                d = new DeviceModel();
                d.codename = "yggdrasil";
                d.viewname = "Volla Phone";
                d.bdev = "/dev/block/mmcblk1";
                d.pbdev = "/dev/block/mmcblk1p";
                d.spartsize = 7340031f;
                d.flow = Arrays.asList(DeviceInstallerWizardPageFragment.class, DroidBootSelectorWizardPageFragment.class, DoInstallWizardPageFragment.class);
                break;
            /*case "cedric":
                d = new DeviceModel();
                d.codename = "cedric";
                d.viewname = "Moto G5";
                d.bdev = "/dev/block/mmcblk1";
                d.pbdev = "/dev/block/mmcblk1p";
                d.usesLegacyDir = true;
                d.flow = Arrays.asList(DeviceInstallerWizardPageFragment.class, DroidBootSelectorWizardPageFragment.class, ExampleWizardPageFragment.class);
                break;*/
            default:
                throw new RuntimeException(new IllegalStateException("DeviceModel not found: unknown device '" + codename + "'"));
        }
        return d;
    }

    public static DeviceModel getModel(InstalledViewModel m) {
        return getModel(Objects.requireNonNull(m.getCodename().getValue()));
    }

    public static DeviceModel getModel(WizardViewModel m) {
        return getModel(Objects.requireNonNull(m.getCodename().getValue()));
    }

    public static DeviceModel getModel(DeviceInstallerViewModel m) {
        return getModel(Objects.requireNonNull(m.getCodename().getValue()));
    }

    public static List<DeviceModel> getModels() {
        List<DeviceModel> list = new ArrayList<>();
        deviceList.forEach(device ->  list.add(DeviceList.getModel(device)));

        return list;
    }
}
