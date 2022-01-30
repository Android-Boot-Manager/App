package org.androidbootmanager.app.legacy.roms;

import org.androidbootmanager.app.legacy.ui.addrom.AddROMViewModel;

public interface CmdlineGenerator {
    void gen(AddROMViewModel imodel, String menuName, String folderName);
}
