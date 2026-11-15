```java
package com.hitomic.hitomicdraw.gui;

import com.hitomic.hitomicdraw.HitomicDrawPlugin;
import com.hitomic.hitomicdraw.model.Drawing;
import com.hitomic.hitomicdraw.model.Submission;
import com.hitomic.hitomicdraw.model.SubmissionStatus;
import com.hitomic.hitomicdraw.service.DrawingService;
import com.hitomic.hitomicdraw.service.MapService;
import com.hitomic.hitomicdraw.storage.DataStore;
import com.hitomic.hitomicdraw.util.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Tela de avaliacao para administradores: mostra o jogador, a data de envio e o mapa
 * desenhado, com botoes de aprovar e rejeitar. Clicar no mapa entrega uma copia de
 * visualizacao (destruida quando dropada) para ver o desenho em tamanho real.
 */
public final class ReviewGui {

    private static final int APPROVE = 11;
    private static final int MAP = 13;
    private static final int REJECT = 15;
    private static final int CLOSE = 22;

    private final HitomicDrawPlugin plugin;
    private final DataStore store;
    private final MapService maps;
    private final DrawingService service;
    private final Messages messages;
    private final GuiItems items;

    public ReviewGui(HitomicDrawPlugin plugin, DataStore store, MapService maps,
                     DrawingService service, Messages messages, GuiItems items) {
        this.plugin = plugin;
        this.store = store;
        this.maps = maps;
        this.service = service;
        this.messages = messages;
        this.items = items;
    }

    public void open(Player admin, Submission submission) {
        ReviewHolder holder = new ReviewHolder(submission.getId());
        Inventory inventory = Bukkit.createInventory(holder, 27,
                messages.get("gui-review-title", "id", String.valueOf(submission.getId())));
        holder.setInventory(inventory);
        render(inventory, submission);
        admin.openInventory(inventory);
    }

    private void render(Inventory inventory, Submission submission) {
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, items.filler());
        }
        Drawing drawing = store.getDrawing(submission.getDrawingId());
        if (drawing != null) {
            inventory.setItem(MAP, mapDisplay(drawing, submission));
        } else {
            inventory.setItem(MAP, items.named(Material.BARRIER, "&cDesenho nao encontrado"));
        }
        if (submission.getStatus() == SubmissionStatus.PENDING) {
            inventory.setItem(APPROVE, items.named(Material.LIME_CONCRETE, "&a&lAprovar",
                    "&7Aprova este desenho e avisa o jogador."));
            inventory.setItem(REJECT, items.named(Material.RED_CONCRETE, "&c&lRejeitar",
                    "&7Rejeita este desenho e avisa o jogador.",
                    "&8Para dar um motivo use /desenhar rejeitar <id> <motivo>"));
        }
        inventory.setItem(CLOSE, items.named(Material.BARRIER, "&7Fechar"));
    }

    private ItemStack mapDisplay(Drawing drawing, Submission submission) {
        ItemStack item = maps.createItem(drawing, true);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Messages.item(messages.raw("review-map-name", "id", String.valueOf(submission.getId()))));
        List<Component> lore = new ArrayList<>();
        for (String line : messages.list("review-map-lore",
                "player", submission.getPlayerName(),
                "date", plugin.formatTime(submission.getSubmittedAt()),
                "status", statusText(submission.getStatus()),
                "reviewer", String.valueOf(submission.getReviewerName()),
                "reviewed", plugin.formatTime(submission.getReviewedAt()),
                "note", submission.getNote() == null ? "-" : submission.getNote())) {
            if (submission.getStatus() == SubmissionStatus.PENDING
                    && (line.contains("{reviewer}") || line.contains("Avaliado") || line.contains("Motivo"))) {
                continue;
            }
            lore.add(Messages.item(line));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return items.flag(item);
    }

    public String statusText(SubmissionStatus status) {
        return switch (status) {
            case PENDING -> messages.raw("status-pending");
            case APPROVED -> messages.raw("status-approved");
            case REJECTED -> messages.raw("status-rejected");
        };
    }

    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (player.getGameMode() == GameMode.CREATIVE) {
            Bukkit.getScheduler().runTask(plugin, (Runnable) player::updateInventory);
        }
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder(false) instanceof ReviewHolder holder)) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= top.getSize()) {
            return;
        }
        if (!player.hasPermission(HitomicDrawPlugin.PERM_ADMIN)) {
            Bukkit.getScheduler().runTask(plugin, (Runnable) player::closeInventory);
            return;
        }
        Submission submission = store.getSubmission(holder.submissionId());
        if (submission == null) {
            Bukkit.getScheduler().runTask(plugin, (Runnable) player::closeInventory);
            return;
        }
        if (slot == CLOSE) {
            Bukkit.getScheduler().runTask(plugin, (Runnable) player::closeInventory);
        } else if (slot == APPROVE || slot == REJECT) {
            if (submission.getStatus() != SubmissionStatus.PENDING) {
                return;
            }
            boolean approve = slot == APPROVE;
            if (service.review(player, submission, approve, null)) {
                messages.send(player, approve ? "review-done-approved" : "review-done-rejected",
                        "id", String.valueOf(submission.getId()), "player", submission.getPlayerName());
            }
            render(top, submission);
        } else if (slot == MAP) {
            Drawing drawing = store.getDrawing(submission.getDrawingId());
            if (drawing == null) {
                return;
            }
            if (service.giveItem(player, maps.createItem(drawing, true))) {
                messages.send(player, "view-copy-given");
            } else {
                messages.send(player, "inventory-full", "id", String.valueOf(drawing.getId()));
            }
        }
    }

    public void handleDrag(InventoryDragEvent event) {
        event.setCancelled(true);
    }
}
```
