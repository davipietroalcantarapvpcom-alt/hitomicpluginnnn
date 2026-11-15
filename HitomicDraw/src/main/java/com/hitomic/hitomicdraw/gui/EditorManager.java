package com.hitomic.hitomicdraw.gui;

import com.hitomic.hitomicdraw.HitomicDrawPlugin;
import com.hitomic.hitomicdraw.model.Drawing;
import com.hitomic.hitomicdraw.model.Palette;
import com.hitomic.hitomicdraw.service.DrawingService;
import com.hitomic.hitomicdraw.service.MapService;
import com.hitomic.hitomicdraw.storage.DataStore;
import com.hitomic.hitomicdraw.util.Messages;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Editor em duas telas de bau (54 slots):
 * <ul>
 *   <li><b>Desenho</b>: os 54 slots sao a grade 9x6; cada slot e um pixel.</li>
 *   <li><b>Ferramentas</b>: paleta, pincel, borracha, balde, desfazer, refazer, limpar e finalizar.</li>
 * </ul>
 * Todos os cliques sao cancelados e validados no servidor; nenhum item da GUI pode ser retirado.
 */
public final class EditorManager {

    private static final int PALETTE_START = 9;
    private static final int BACK = 45;
    private static final int BRUSH = 46;
    private static final int ERASER = 47;
    private static final int FILL = 48;
    private static final int INFO = 49;
    private static final int UNDO = 50;
    private static final int REDO = 51;
    private static final int CLEAR = 52;
    private static final int FINISH = 53;

    private final HitomicDrawPlugin plugin;
    private final DataStore store;
    private final MapService maps;
    private final DrawingService service;
    private final Messages messages;
    private final GuiItems items;
    private final Map<UUID, EditorSession> sessions = new HashMap<>();

    public EditorManager(HitomicDrawPlugin plugin, DataStore store, MapService maps,
                         DrawingService service, Messages messages, GuiItems items) {
        this.plugin = plugin;
        this.store = store;
        this.maps = maps;
        this.service = service;
        this.messages = messages;
        this.items = items;
    }

    // --------------------------------------------------------------- abertura

    /** Abre o editor. O chamador ja deve ter validado dono e estado do desenho. */
    public void open(Player player, Drawing drawing) {
        EditorSession session = new EditorSession(player, drawing);
        String id = String.valueOf(drawing.getId());

        EditorHolder canvasHolder = new EditorHolder(session, false);
        session.canvasInv = Bukkit.createInventory(canvasHolder, 54, messages.get("gui-canvas-title", "id", id));
        canvasHolder.setInventory(session.canvasInv);

        EditorHolder toolsHolder = new EditorHolder(session, true);
        session.toolsInv = Bukkit.createInventory(toolsHolder, 54, messages.get("gui-tools-title", "id", id));
        toolsHolder.setInventory(session.toolsInv);

        renderCanvas(session);
        renderTools(session);
        sessions.put(player.getUniqueId(), session);
        player.openInventory(session.canvasInv);
        player.sendActionBar(messages.get("ab-editor-hint"));
    }

    public boolean isEditing(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    /** Fecha todos os editores (usado ao desligar o plugin). */
    public void closeAll() {
        List<EditorSession> copy = new ArrayList<>(sessions.values());
        for (EditorSession session : copy) {
            session.player.closeInventory();
        }
        sessions.clear();
    }

    // ----------------------------------------------------------------- eventos

    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (player.getGameMode() == GameMode.CREATIVE) {
            Bukkit.getScheduler().runTask(plugin, player::updateInventory);
        }
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder(false) instanceof EditorHolder holder)) {
            return;
        }
        EditorSession session = holder.session();
        if (sessions.get(player.getUniqueId()) != session) {
            closeLater(player);
            return;
        }
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= top.getSize()) {
            return;
        }
        if (!session.drawing.isEditable()) {
            closeLater(player);
            return;
        }
        try {
            if (holder.isTools()) {
                handleTools(session, slot, event.getClick());
            } else {
                handleCanvas(session, slot, event.getClick());
            }
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Erro no editor de " + player.getName() + ": " + ex);
            closeLater(player);
        }
    }

    public void handleDrag(InventoryDragEvent event) {
        event.setCancelled(true);
    }

    public void handleClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof EditorHolder holder)) {
            return;
        }
        EditorSession session = holder.session();
        if (session.switching) {
            return;
        }
        Player player = session.player;
        if (sessions.remove(player.getUniqueId(), session)) {
            maps.pushUpdate(player, session.drawing);
            store.saveNow();
        }
        Bukkit.getScheduler().runTask(plugin, () -> items.purge(player));
    }

    public void handleQuit(Player player) {
        EditorSession session = sessions.remove(player.getUniqueId());
        if (session != null) {
            store.saveNow();
        }
        items.purge(player);
    }

    // ------------------------------------------------------------- tela desenho

    private void handleCanvas(EditorSession s, int slot, ClickType click) {
        switch (click) {
            case LEFT, DOUBLE_CLICK -> applyTool(s, slot);
            case SHIFT_LEFT -> pickColor(s, slot);
            case RIGHT, SHIFT_RIGHT -> switchTo(s, true);
            case DROP, CONTROL_DROP -> undo(s);
            case SWAP_OFFHAND -> redo(s);
            default -> {
            }
        }
    }

    private void applyTool(EditorSession s, int index) {
        if (index < 0 || index >= Drawing.SIZE) {
            return;
        }
        Drawing drawing = s.drawing;
        byte[] before = drawing.snapshot();
        boolean changed = switch (s.tool) {
            case BRUSH -> drawing.setPixel(index, s.color);
            case ERASER -> drawing.setPixel(index, Palette.BACKGROUND);
            case FILL -> floodFill(drawing, index, s.color);
        };
        if (!changed) {
            return;
        }
        pushUndo(s, before);
        s.redo.clear();
        refreshCanvas(s, before);
        afterChange(s);
    }

    private void pickColor(EditorSession s, int index) {
        if (index < 0 || index >= Drawing.SIZE) {
            return;
        }
        s.color = s.drawing.getPixel(index);
        s.tool = EditorSession.Tool.BRUSH;
        s.player.sendActionBar(messages.get("ab-color", "color", Palette.get(s.color).name()));
    }

    private static boolean floodFill(Drawing drawing, int start, byte color) {
        byte target = drawing.getPixel(start);
        if (target == color) {
            return false;
        }
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        boolean[] seen = new boolean[Drawing.SIZE];
        queue.add(start);
        seen[start] = true;
        while (!queue.isEmpty()) {
            int i = queue.poll();
            drawing.setPixel(i, color);
            int x = i % Drawing.WIDTH;
            int y = i / Drawing.WIDTH;
            if (x > 0) {
                enqueue(drawing, seen, queue, i - 1, target);
            }
            if (x < Drawing.WIDTH - 1) {
                enqueue(drawing, seen, queue, i + 1, target);
            }
            if (y > 0) {
                enqueue(drawing, seen, queue, i - Drawing.WIDTH, target);
            }
            if (y < Drawing.HEIGHT - 1) {
                enqueue(drawing, seen, queue, i + Drawing.WIDTH, target);
            }
        }
        return true;
    }

    private static void enqueue(Drawing drawing, boolean[] seen, ArrayDeque<Integer> queue, int index, byte target) {
        if (!seen[index] && drawing.getPixel(index) == target) {
            seen[index] = true;
            queue.add(index);
        }
    }

    // --------------------------------------------------------- tela ferramentas

    private void handleTools(EditorSession s, int slot, ClickType click) {
        if (click != ClickType.LEFT && click != ClickType.RIGHT && click != ClickType.SHIFT_LEFT
                && click != ClickType.DOUBLE_CLICK) {
            return;
        }
        long now = System.currentTimeMillis();
        int paletteIndex = slot - PALETTE_START;
        if (paletteIndex >= 0 && paletteIndex < Palette.size()) {
            s.color = (byte) paletteIndex;
            if (s.tool == EditorSession.Tool.ERASER) {
                s.tool = EditorSession.Tool.BRUSH;
            }
            s.player.sendActionBar(messages.get("ab-color", "color", Palette.get(paletteIndex).name()));
            switchTo(s, false);
        } else if (slot == BACK) {
            switchTo(s, false);
        } else if (slot == BRUSH) {
            selectTool(s, EditorSession.Tool.BRUSH);
        } else if (slot == ERASER) {
            selectTool(s, EditorSession.Tool.ERASER);
        } else if (slot == FILL) {
            selectTool(s, EditorSession.Tool.FILL);
        } else if (slot == UNDO) {
            undo(s);
            switchTo(s, false);
        } else if (slot == REDO) {
            redo(s);
            switchTo(s, false);
        } else if (slot == CLEAR) {
            if (s.clearConfirmUntil >= now) {
                s.clearConfirmUntil = 0;
                clearAll(s);
                switchTo(s, false);
            } else {
                s.clearConfirmUntil = now + confirmMillis();
                s.finishConfirmUntil = 0;
                renderTools(s);
                s.player.sendActionBar(messages.get("ab-confirm-clear"));
            }
        } else if (slot == FINISH) {
            if (s.finishConfirmUntil >= now) {
                s.finishConfirmUntil = 0;
                finishNow(s);
            } else {
                s.finishConfirmUntil = now + confirmMillis();
                s.clearConfirmUntil = 0;
                renderTools(s);
                s.player.sendActionBar(messages.get("ab-confirm-finish"));
            }
        }
    }

    private void selectTool(EditorSession s, EditorSession.Tool tool) {
        s.tool = tool;
        s.player.sendActionBar(messages.get("ab-tool", "tool", tool.label));
        switchTo(s, false);
    }

    private long confirmMillis() {
        return Math.max(1L, plugin.getConfig().getLong("settings.confirm-timeout-seconds", 5L)) * 1000L;
    }

    private void clearAll(EditorSession s) {
        byte[] before = s.drawing.snapshot();
        if (!s.drawing.clear()) {
            s.player.sendActionBar(messages.get("ab-already-clear"));
            return;
        }
        pushUndo(s, before);
        s.redo.clear();
        refreshCanvas(s, before);
        afterChange(s);
        s.player.sendActionBar(messages.get("ab-cleared"));
    }

    private void finishNow(EditorSession s) {
        Player player = s.player;
        sessions.remove(player.getUniqueId(), s);
        service.finish(player, s.drawing);
        closeLater(player);
    }

    // ---------------------------------------------------- desfazer / refazer

    private void pushUndo(EditorSession s, byte[] snapshot) {
        s.undo.push(snapshot);
        trim(s.undo);
    }

    private void trim(Deque<byte[]> deque) {
        int limit = Math.max(1, plugin.getConfig().getInt("settings.undo-limit", 50));
        while (deque.size() > limit) {
            deque.removeLast();
        }
    }

    private void undo(EditorSession s) {
        if (s.undo.isEmpty()) {
            s.player.sendActionBar(messages.get("ab-nothing-undo"));
            return;
        }
        byte[] current = s.drawing.snapshot();
        s.redo.push(current);
        trim(s.redo);
        s.drawing.restore(s.undo.pop());
        refreshCanvas(s, current);
        afterChange(s);
    }

    private void redo(EditorSession s) {
        if (s.redo.isEmpty()) {
            s.player.sendActionBar(messages.get("ab-nothing-redo"));
            return;
        }
        byte[] current = s.drawing.snapshot();
        s.undo.push(current);
        trim(s.undo);
        s.drawing.restore(s.redo.pop());
        refreshCanvas(s, current);
        afterChange(s);
    }

    // ------------------------------------------------------------ renderizacao

    private void renderCanvas(EditorSession s) {
        for (int i = 0; i < Drawing.SIZE; i++) {
            s.canvasInv.setItem(i, items.pixel(s.drawing.getPixel(i)));
        }
    }

    /** Atualiza apenas os slots cujo pixel mudou em relacao a {@code before}. */
    private void refreshCanvas(EditorSession s, byte[] before) {
        for (int i = 0; i < Drawing.SIZE; i++) {
            byte now = s.drawing.getPixel(i);
            if (before[i] != now) {
                s.canvasInv.setItem(i, items.pixel(now));
            }
        }
    }

    private void renderTools(EditorSession s) {
        Inventory inv = s.toolsInv;
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, items.filler());
        }
        inv.setItem(4, items.named(Material.PAPER, "&e&lFerramentas &7- Desenho #" + s.drawing.getId(),
                "&7Escolha uma cor ou ferramenta.", "&7Depois volte ao desenho."));

        List<String> paletteLore = List.of("&7Clique para usar esta cor");
        for (int i = 0; i < Palette.size(); i++) {
            inv.setItem(PALETTE_START + i, items.paletteEntry(i, i == s.color, paletteLore));
        }

        long now = System.currentTimeMillis();
        inv.setItem(BACK, items.named(Material.ARROW, "&e&lVoltar ao desenho", "&7Continuar pintando."));
        inv.setItem(BRUSH, toolItem(s, EditorSession.Tool.BRUSH, Material.BRUSH, "&b&lPincel",
                "&7Pinta um pixel com a cor atual."));
        inv.setItem(ERASER, toolItem(s, EditorSession.Tool.ERASER, Material.SPONGE, "&b&lBorracha",
                "&7Apaga o pixel (volta ao branco)."));
        inv.setItem(FILL, toolItem(s, EditorSession.Tool.FILL, Material.BUCKET, "&b&lBalde de preenchimento",
                "&7Preenche a area de mesma cor."));
        Palette.Entry current = Palette.get(s.color);
        inv.setItem(INFO, items.named(current.material(), "&fCor atual: &e" + current.name(),
                "&7Ferramenta: &f" + s.tool.label));
        inv.setItem(UNDO, items.named(Material.ORANGE_DYE, "&6&lDesfazer",
                "&7Passos disponiveis: &f" + s.undo.size(), "&8Atalho: tecla Q sobre a grade"));
        inv.setItem(REDO, items.named(Material.LIME_DYE, "&a&lRefazer",
                "&7Passos disponiveis: &f" + s.redo.size(), "&8Atalho: tecla F sobre a grade"));
        if (s.clearConfirmUntil >= now) {
            inv.setItem(CLEAR, items.named(Material.TNT, "&c&lCONFIRMAR: limpar tudo?",
                    "&7Clique de novo para apagar o desenho."));
        } else {
            inv.setItem(CLEAR, items.named(Material.LAVA_BUCKET, "&c&lLimpar",
                    "&7Apaga todo o desenho.", "&8Pede confirmacao."));
        }
        if (s.finishConfirmUntil >= now) {
            inv.setItem(FINISH, items.named(Material.EMERALD_BLOCK, "&a&lCONFIRMAR: finalizar?",
                    "&7Depois de finalizado nao da mais", "&7para editar. Clique de novo."));
        } else {
            inv.setItem(FINISH, items.named(Material.EMERALD, "&a&lFinalizar",
                    "&7Conclui o desenho e entrega o mapa.", "&8Pede confirmacao."));
        }
    }

    private org.bukkit.inventory.ItemStack toolItem(EditorSession s, EditorSession.Tool tool,
                                                    Material material, String name, String description) {
        if (s.tool == tool) {
            return items.named(material, name, description, "&a* Ferramenta ativa");
        }
        return items.named(material, name, description, "&8Clique para selecionar");
    }

    // ----------------------------------------------------------------- suporte

    private void afterChange(EditorSession s) {
        store.markDrawingsDirty();
        scheduleMapUpdate(s);
    }

    /** Junta varias pinceladas seguidas em uma unica atualizacao do mapa. */
    private void scheduleMapUpdate(EditorSession s) {
        if (s.updatePending) {
            return;
        }
        s.updatePending = true;
        long delay = Math.max(1L, plugin.getConfig().getLong("settings.map-update-delay-ticks", 10L));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            s.updatePending = false;
            if (s.player.isOnline()) {
                maps.pushUpdate(s.player, s.drawing);
            }
        }, delay);
    }

    /** Troca de tela no proximo tick (nao se abre inventario dentro do evento de clique). */
    private void switchTo(EditorSession s, boolean tools) {
        if (tools) {
            renderTools(s);
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!s.player.isOnline() || sessions.get(s.player.getUniqueId()) != s) {
                return;
            }
            s.switching = true;
            try {
                s.player.openInventory(tools ? s.toolsInv : s.canvasInv);
            } finally {
                s.switching = false;
            }
        });
    }

    private void closeLater(Player player) {
        Bukkit.getScheduler().runTask(plugin, (Runnable) player::closeInventory);
    }
}
