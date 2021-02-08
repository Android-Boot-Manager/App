package org.androidbootmanager.app.roms;

import org.androidbootmanager.app.ui.addrom.AddROMViewModel;

public interface CmdlineGenerator {
    void gen(AddROMViewModel imodel, String menuName, String folderName);
}
