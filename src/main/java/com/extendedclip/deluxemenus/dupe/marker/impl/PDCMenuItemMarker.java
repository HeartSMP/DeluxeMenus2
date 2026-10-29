package com.extendedclip.deluxemenus.dupe.marker.impl;

import com.extendedclip.deluxemenus.DeluxeMenus;
import com.extendedclip.deluxemenus.dupe.marker.ItemMarker;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

/**
 * Marks items through {@link ItemStack#editPersistentDataContainer}, which only touches the custom data component.
 * Going through {@code getItemMeta}/{@code setItemMeta} instead re-applies the whole meta, and for a player head
 * Paper then re-resolves the skull profile (a Mojang request when the profile has no textures).
 */
public class PDCMenuItemMarker implements ItemMarker {

    private final NamespacedKey mark;

    public PDCMenuItemMarker(@NotNull final DeluxeMenus plugin, @NotNull final String mark) {
        this.mark = new NamespacedKey(plugin, mark);
    }

    @Override
    public @NotNull ItemStack mark(@NotNull ItemStack itemStack) {
        if (itemStack.isEmpty()) {
            return itemStack;
        }

        itemStack.editPersistentDataContainer(container -> container.set(
                mark,
                PersistentDataType.BYTE,
                (byte) 1
        ));
        return itemStack;
    }

    @Override
    public @NotNull ItemStack unmark(@NotNull ItemStack itemStack) {
        if (itemStack.isEmpty()) {
            return itemStack;
        }

        itemStack.editPersistentDataContainer(container -> container.remove(mark));
        return itemStack;
    }

    @Override
    public boolean isMarked(@NotNull ItemStack itemStack) {
        if (itemStack.isEmpty()) {
            return false;
        }

        return itemStack.getPersistentDataContainer().has(mark, PersistentDataType.BYTE);
    }
}
