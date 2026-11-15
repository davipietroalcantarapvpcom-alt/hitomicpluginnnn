package com.hitomic.hitomicdraw.service;

import com.hitomic.hitomicdraw.HitomicDrawPlugin;
import com.hitomic.hitomicdraw.model.Drawing;
import com.hitomic.hitomicdraw.model.DrawingState;
import com.hitomic.hitomicdraw.model.Submission;
import com.hitomic.hitomicdraw.model.SubmissionStatus;
import com.hitomic.hitomicdraw.storage.DataStore;
import com.hitomic.hitomicdraw.util.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.map.MapView;

import java.util.Map;

/** Regras de negocio: criar, finalizar, enviar e avaliar desenhos. */
public final class DrawingService {

    private final HitomicDrawPlugin plugin;
    private final DataStore store;
    private final MapService maps;
    private final Messages messages;

    public DrawingService(HitomicDrawPlugin plugin, DataStore store, MapService maps, Messages messages) {
        this.plugin = plugin;
        this.store = store;
        this.maps = maps;
        this.messages = messages;
    }

    /** Cria o desenho e o mapa em branco (ainda nao entrega o item). */
    public Drawing createDrawing(Player player) {
        Drawing drawing = store.createDrawing(player.getUniqueId(), player.getName());
        try {
            MapView view = maps.createMap();
            store.bindMap(drawing, view.getId());
            maps.attach(view, drawing);
        } catch (RuntimeException ex) {
            store.removeDrawing(drawing);
            throw ex;
        }
        store.saveNow();
        return drawing;
    }

    /** Entrega o item; se o inventario estiver cheio nada e dropado e retorna false. */
    public boolean giveItem(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        return leftover.isEmpty();
    }

    /** Marca como finalizado e entrega o mapa final ao dono. */
    public void finish(Player player, Drawing drawing) {
        if (drawing.getState() != DrawingState.DRAFT) {
            return;
        }
        drawing.setState(DrawingState.FINISHED);
        drawing.setFinishedAt(System.currentTimeMillis());
        store.markDrawingsDirty();
        store.saveNow();

        ItemStack item = maps.createItem(drawing, false);
        boolean replaced = maps.replaceInInventory(player, drawing.getId(), item);
        if (!replaced && !giveItem(player, item)) {
            messages.send(player, "inventory-full", "id", String.valueOf(drawing.getId()));
        }
        maps.pushUpdate(player, drawing);
        messages.send(player, "finished", "id", String.valueOf(drawing.getId()));
    }

    /**
     * Registra a participacao de um desenho finalizado.
     *
     * @return a participacao criada, ou null se o desenho nao pode ser enviado (ja enviado etc.)
     */
    public Submission submit(Player player, Drawing drawing) {
        if (drawing.getState() != DrawingState.FINISHED) {
            return null;
        }
        Submission submission = store.createSubmission(drawing, player.getName());
        if (submission == null) {
            return null;
        }
        drawing.setOwnerName(player.getName());
        drawing.setState(DrawingState.SUBMITTED);
        drawing.setSubmittedAt(submission.getSubmittedAt());
        store.markDrawingsDirty();
        store.saveNow();

        maps.removeFromInventory(player, drawing.getId());
        messages.send(player, "submitted", "id", String.valueOf(submission.getId()));
        plugin.getLogger().info("Nova participacao #" + submission.getId() + " de " + player.getName()
                + " (desenho #" + drawing.getId() + ").");
        notifyAdmins(submission);
        return submission;
    }

    private void notifyAdmins(Submission submission) {
        if (!plugin.getConfig().getBoolean("settings.notify-admins", true)) {
            return;
        }
        Component line = messages.prefixed("admin-new-submission",
                        "id", String.valueOf(submission.getId()),
                        "player", submission.getPlayerName())
                .clickEvent(ClickEvent.runCommand("/desenhar ver " + submission.getId()))
                .hoverEvent(HoverEvent.showText(messages.get("click-to-view")));
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.hasPermission(HitomicDrawPlugin.PERM_ADMIN)) {
                online.sendMessage(line);
            }
        }
    }

    /**
     * Aprova ou rejeita uma participacao pendente e avisa o jogador (agora, se estiver
     * online, ou no proximo login).
     *
     * @return false se a participacao ja tinha sido avaliada
     */
    public boolean review(CommandSender admin, Submission submission, boolean approve, String note) {
        if (submission.getStatus() != SubmissionStatus.PENDING) {
            return false;
        }
        submission.setStatus(approve ? SubmissionStatus.APPROVED : SubmissionStatus.REJECTED);
        submission.setReviewer(admin instanceof Player p ? p.getUniqueId() : null);
        submission.setReviewerName(admin.getName());
        submission.setReviewedAt(System.currentTimeMillis());
        submission.setNote(note == null || note.isBlank() ? null : note.trim());
        submission.setNotified(false);

        Player target = Bukkit.getPlayer(submission.getPlayer());
        if (target != null) {
            notifyResult(target, submission);
            submission.setNotified(true);
        }
        store.markSubmissionsDirty();
        store.saveNow();
        plugin.getLogger().info("Participacao #" + submission.getId() + " " + submission.getStatus()
                + " por " + admin.getName() + ".");
        return true;
    }

    private void notifyResult(Player target, Submission submission) {
        boolean approved = submission.getStatus() == SubmissionStatus.APPROVED;
        String id = String.valueOf(submission.getId());
        messages.send(target, approved ? "result-approved" : "result-rejected",
                "id", id, "reviewer", String.valueOf(submission.getReviewerName()));
        if (submission.getNote() != null) {
            messages.send(target, "result-note", "note", submission.getNote());
        }
        target.showTitle(Title.title(
                messages.get(approved ? "title-approved" : "title-rejected"),
                messages.get("title-subtitle", "id", id)));
        target.playSound(target.getLocation(),
                approved ? Sound.ENTITY_PLAYER_LEVELUP : Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
    }

    /** Entrega avaliacoes que sairam enquanto o jogador estava offline. */
    public void deliverPending(Player player) {
        boolean any = false;
        for (Submission submission : store.getUnnotified(player.getUniqueId())) {
            notifyResult(player, submission);
            submission.setNotified(true);
            any = true;
        }
        if (any) {
            store.markSubmissionsDirty();
            store.saveNow();
        }
    }
}
