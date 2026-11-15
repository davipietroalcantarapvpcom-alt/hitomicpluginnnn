package com.hitomic.hitomicdraw.listener;

import com.hitomic.hitomicdraw.HitomicDrawPlugin;
import com.hitomic.hitomicdraw.model.Drawing;
import com.hitomic.hitomicdraw.service.DrawingService;
import com.hitomic.hitomicdraw.service.MapService;
import com.hitomic.hitomicdraw.storage.DataStore;
import com.hitomic.hitomicdraw.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.server.MapInitializeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.map.MapView;

/**
 * Reconecta os mapas ao carregar, trata o "dropar o mapa = enviar participacao" e
 * bloqueia as formas vanilla de copiar/alterar os mapas dos desenhos.
 */
public final class MapListener implements Listener {

    private final HitomicDrawPlugin plugin;
    private final DataStore store;
    private final MapService maps;
    private final DrawingService service;
    private final Messages messages;

    public MapListener(HitomicDrawPlugin plugin, DataStore store, MapService maps,
                       DrawingService service, Messages messages) {
        this.plugin = plugin;
        this.store = store;
        this.maps = maps;
        this.service = service;
        this.messages = messages;
    }

    /** O Minecraft carregou um mapa: se for de um desenho, liga o renderizador. */
    @EventHandler
    public void onMapInitialize(MapInitializeEvent event) {
        MapView view = event.getMap();
        Drawing drawing = store.getDrawingByMap(view.getId());
        if (drawing != null) {
            maps.attach(view, drawing);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        Item drop = event.getItemDrop();
        ItemStack stack = drop.getItemStack();
        Integer drawingId = maps.readDrawingId(stack);
        if (drawingId == null) {
            return;
        }
        Player player = event.getPlayer();

        // Copia de visualizacao de um administrador: some ao ser dropada.
        if (maps.isViewCopy(stack)) {
            destroy(drop);
            return;
        }

        Drawing drawing = store.getDrawing(drawingId);
        if (drawing == null || !player.hasPermission(HitomicDrawPlugin.PERM_USE)) {
            event.setCancelled(true);
            return;
        }
        if (!drawing.getOwner().equals(player.getUniqueId())) {
            event.setCancelled(true);
            messages.send(player, "drop-not-owner", "owner", drawing.getOwnerName());
            return;
        }
        switch (drawing.getState()) {
            case DRAFT -> {
                event.setCancelled(true);
                messages.send(player, "drop-must-finish", "id", String.valueOf(drawing.getId()));
            }
            case SUBMITTED -> {
                // Copia de um desenho que ja foi enviado: nao vale como nova participacao.
                destroy(drop);
                messages.send(player, "drop-already-submitted", "id", String.valueOf(drawing.getId()));
            }
            case FINISHED -> {
                if (service.submit(player, drawing) != null) {
                    destroy(drop);
                } else {
                    event.setCancelled(true);
                    messages.send(player, "drop-already-submitted", "id", String.valueOf(drawing.getId()));
                }
            }
        }
    }

    /** Remove o item dropado no proximo tick e impede que alguem o pegue antes. */
    private void destroy(Item drop) {
        drop.setPickupDelay(Short.MAX_VALUE);
        Bukkit.getScheduler().runTask(plugin, drop::remove);
    }

    /** Impede clonar mapas de desenhos na bancada de criacao. */
    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        for (ItemStack ingredient : event.getInventory().getMatrix()) {
            if (maps.readDrawingId(ingredient) != null) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    /** Impede copiar/estender/travar mapas de desenhos na mesa de cartografia. */
    @EventHandler(ignoreCancelled = true)
    public void onCartography(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getType() != InventoryType.CARTOGRAPHY) {
            return;
        }
        boolean involved = maps.readDrawingId(event.getCurrentItem()) != null
                || maps.readDrawingId(event.getCursor()) != null;
        if (!involved && event.getClick() == ClickType.NUMBER_KEY && event.getHotbarButton() >= 0) {
            involved = maps.readDrawingId(event.getWhoClicked().getInventory().getItem(event.getHotbarButton())) != null;
        }
        if (!involved && event.getClick() == ClickType.SWAP_OFFHAND) {
            involved = maps.readDrawingId(event.getWhoClicked().getInventory().getItemInOffHand()) != null;
        }
        if (involved) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                messages.send(player, "cartography-blocked");
            }
        }
    }
}
