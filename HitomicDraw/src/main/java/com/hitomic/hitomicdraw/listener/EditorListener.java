package com.hitomic.hitomicdraw.listener;

import com.hitomic.hitomicdraw.HitomicDrawPlugin;
import com.hitomic.hitomicdraw.gui.EditorHolder;
import com.hitomic.hitomicdraw.gui.EditorManager;
import com.hitomic.hitomicdraw.gui.GuiItems;
import com.hitomic.hitomicdraw.gui.ReviewGui;
import com.hitomic.hitomicdraw.gui.ReviewHolder;
import com.hitomic.hitomicdraw.service.DrawingService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.InventoryHolder;

/** Encaminha os eventos de inventario para o editor e para a tela de avaliacao. */
public final class EditorListener implements Listener {

    private final HitomicDrawPlugin plugin;
    private final EditorManager editor;
    private final ReviewGui review;
    private final GuiItems items;
    private final DrawingService service;

    public EditorListener(HitomicDrawPlugin plugin, EditorManager editor, ReviewGui review,
                          GuiItems items, DrawingService service) {
        this.plugin = plugin;
        this.editor = editor;
        this.review = review;
        this.items = items;
        this.service = service;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder(false);
        if (holder instanceof EditorHolder) {
            editor.handleClick(event);
        } else if (holder instanceof ReviewHolder) {
            review.handleClick(event);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder(false);
        if (holder instanceof EditorHolder) {
            editor.handleDrag(event);
        } else if (holder instanceof ReviewHolder) {
            review.handleDrag(event);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        InventoryHolder holder = event.getInventory().getHolder(false);
        if (holder instanceof EditorHolder) {
            editor.handleClose(event);
        } else if (holder instanceof ReviewHolder && event.getPlayer() instanceof Player player) {
            Bukkit.getScheduler().runTask(plugin, () -> items.purge(player));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        editor.handleQuit(event.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        items.purge(player);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                service.deliverPending(player);
            }
        }, 40L);
    }
}
