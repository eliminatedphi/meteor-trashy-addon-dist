// SPDX-License-Identifier: GPL-3.0-only
package phi.eliminated.trashyaddon;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.hud.Hud;
import phi.eliminated.trashyaddon.commands.*;
import phi.eliminated.trashyaddon.commands.argument.CEntitySelectorOptions;
import phi.eliminated.trashyaddon.hud.EntityCountHud;
import phi.eliminated.trashyaddon.hud.YawCompassHud;
import phi.eliminated.trashyaddon.modules.*;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.systems.modules.Modules;
import phi.eliminated.trashyaddon.modules.mapxerox.MapXerox;
import org.slf4j.Logger;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class TrashyAddon extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    //public static final Category CATEGORY = new Category("Example");
    //public static final HudGroup HUD_GROUP = new HudGroup("Example");

    @Override
    public void onInitialize() {
        LOG.info("Initializing Meteor Trashy Addons");
        CEntitySelectorOptions.register();

        // Modules
        Modules.get().add(new AutoTrade());
        Modules.get().add(new SlotHighlight());
        Modules.get().add(new MapXerox());
        Modules.get().add(new MapmanIntegration());
        Modules.get().add(new NBTTooltip());
        Modules.get().add(new AntiInventoryMove());

        // Private Modules
        var packageName = this.getClass().getPackageName() + ".modules.privati";
        try {
            Class<?> cl = Class.forName(packageName + ".PrivateModules");
            Method m = cl.getMethod("load");
            m.invoke(null);
        } catch (ClassNotFoundException | ClassCastException | NoSuchMethodException | InvocationTargetException | IllegalAccessException e) { MeteorClient.LOG.info("TrashyAddon: public build. ({} | {})", e.getCause(), e.getMessage()); }

        // Commands
        Commands.add(new EntityDataCommand());
        Commands.add(new BlockDataCommand());
        Commands.add(new MapDumpCommand());
        Commands.add(new MapTallyCommand());
        Commands.add(new MapLocateCommand());
        Commands.add(new MapItemHighlightCommand());
        Commands.add(new EntityHighlightCommand());
        Commands.add(new MapXeroxCommand());
        Commands.add(new MapFocusCommand());

        // HUD
        Hud.get().register(EntityCountHud.INFO);
        Hud.get().register(YawCompassHud.INFO);
    }

    /*
    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }
    */

    @Override
    public String getPackage() {
        return "phi.eliminated.trashyaddon";
    }
}
