package com.hitomic.hitomicdraw.gui;

import com.hitomic.hitomicdraw.HitomicDrawPlugin;
import com.hitomic.hitomicdraw.model.Palette;
import com.hitomic.hitomicdraw.util.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Fabrica dos itens decorativos das GUIs. Todos recebem uma marca invisivel; se por
 * algum bug/exploit (ex.: modo criativo) um deles chegar ao inventario do jogador,
 * {@link #purge(Player)} o remove.
 */
public final class GuiItems {

    private final NamespacedKey guiKey;
    private final ItemStack[] pixels = new ItemStack[Palette.size()];
    private final ItemStack filler;

    public GuiItems(HitomicDrawPlugin plugin) {
        this.guiKey = new NamespacedKey(plugin, "gui_item");
        this.filler = named(Material.GRAY_STAINED_GLASS_PANE, " ");
        List<String> lore = plugin.messages().list("gui-pixel-lore");
        for (int i = 0; i < pixels.length; i++) {
            Palette.Entry entry = Palette.get(i);
            pixels[i] = named(entry.material(), "&f" + entry.name(), lore);
        }
    }

    public ItemStack filler() {
        return filler.clone();
    }

    public ItemStack pixel(byte paletteIndex) {
        return pixels[paletteIndex].clone();
    }

    /** Item da paleta; o selecionado ganha uma seta no nome. */
    public ItemStack paletteEntry(int index, boolean selected, List<String> lore) {
        Palette.Entry entry = Palette.get(index);
        String name = selected ? "&a> &f" + entry.name() : "&f" + entry.name();
        return named(entry.material(), name, lore);
    }

    public ItemStack named(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Messages.item(name));
        if (lore != null && !lore.isEmpty()) {
            List<Component> lines = new ArrayList<>(lore.size());
            for (String line : lore) {
                lines.add(Messages.item(line));
            }
            meta.lore(lines);
        }
        meta.getPersistentDataContainer().set(guiKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack named(Material material, String name, String... lore) {
        return named(material, name, Arrays.asList(lore));
    }

    /** Marca um item ja pronto como "item de GUI". */
    public ItemStack flag(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(guiKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isGuiItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(guiKey, PersistentDataType.BYTE);
    }

    /** Remove itens de GUI que tenham escapado para o inventario ou o cursor do jogador. */
    public void purge(Player player) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        boolean changed = false;
        for (int i = 0; i < contents.length; i++) {
            if (isGuiItem(contents[i])) {
                inventory.setItem(i, null);
                changed = true;
            }
        }
        if (isGuiItem(player.getItemOnCursor())) {
            player.setItemOnCursor(null);
            changed = true;
        }
        if (changed) {
            player.updateInventory();
        }
    }
}
