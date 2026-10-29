package com.extendedclip.deluxemenus.hooks;

import com.extendedclip.deluxemenus.DeluxeMenus;
import com.extendedclip.deluxemenus.cache.SimpleCache;
import com.extendedclip.deluxemenus.listener.Listener;
import com.extendedclip.deluxemenus.utils.SkullUtils;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public class NamedHeadHook extends Listener implements ItemHook, SimpleCache {

    public NamedHeadHook(@NotNull final DeluxeMenus plugin) {
        super(plugin);
    }

    @Override
    public ItemStack getItem(@NotNull final String... arguments) {
        if (arguments.length == 0) {
            return plugin.getHead().clone();
        }

        try {
            // SkullUtils keeps a bounded cache of resolved textures, so there is no ItemStack cache here anymore.
            // A cached ItemStack would keep a plain head for a player who joins later, and grew without limit.
            return SkullUtils.getSkullByName(plugin, arguments[0]);
        } catch (Exception exception) {
            plugin.printStacktrace(
                    "Something went wrong while trying to get a head by name" +
                            ": " + arguments[0],
                    exception
            );
        }

        return plugin.getHead().clone();
    }

    @Override
    public boolean itemMatchesIdentifiers(@NotNull ItemStack item, @NotNull String... arguments) {
        if (arguments.length == 0) {
            return false;
        }
        return arguments[0].equalsIgnoreCase(SkullUtils.getSkullOwner(item));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(final PlayerQuitEvent event) {
        // Remember the skin the player had (e.g. applied by a skin plugin) so their head keeps it while offline.
        SkullUtils.cacheTextures(event.getPlayer().getPlayerProfile());
    }

    @Override
    public String getPrefix() {
        return "head-";
    }

    @Override
    public void clearCache() {
        SkullUtils.clearTextureCache();
    }
}
