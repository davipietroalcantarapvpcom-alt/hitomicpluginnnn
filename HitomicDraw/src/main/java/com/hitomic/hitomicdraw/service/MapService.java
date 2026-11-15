package com.hitomic.hitomicdraw.service;

import com.hitomic.hitomicdraw.HitomicDrawPlugin;
import com.hitomic.hitomicdraw.model.Drawing;
import com.hitomic.hitomicdraw.model.DrawingState;
import com.hitomic.hitomicdraw.storage.DataStore;
import com.hitomic.hitomicdraw.util.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cria mapas reais (FILLED_MAP) e liga cada um ao seu desenho. O desenho em si fica
 * em drawings.yml; apos reiniciar o servidor o renderizador e reconectado ao mapa
 * (ao carregar o plugin e no MapInitializeEvent).
 */
public final class MapService {

    private final HitomicDrawPlugin plugin;
    private final DataStore store;
    private final NamespacedKey drawingKey;
    private final NamespacedKey viewKey;
    private final Map<Integer, MapView> views = new HashMap<>();

    public MapService(HitomicDrawPlugin plugin, DataStore store) {
        this.plugin = plugin;
        this.store = store;
        this.drawingKey = new NamespacedKey(plugin, "drawing_id");
        this.viewKey = new NamespacedKey(plugin, "view_copy");
    }

    // ------------------------------------------------------------------ mapas

    /** Cria um MapView novo do Bukkit (ainda sem renderizador). */
    public MapView createMap() {
        World world = Bukkit.getWorlds().get(0);
        String configured = plugin.getConfig().getString("settings.map-world", "");
        if (configured != null && !configured.isBlank()) {
            World w = Bukkit.getWorld(configured);
            if (w != null) {
                world = w;
            }
        }
        MapView view = Bukkit.createMap(world);
        views.put(view.getId(), view);
        return view;
    }

    /** Liga o renderizador do desenho ao mapa, removendo os renderizadores padrao. */
    public void attach(MapView view, Drawing drawing) {
        List<MapRenderer> current = new ArrayList<>(view.getRenderers());
        for (MapRenderer renderer : current) {
            if (renderer instanceof DrawingRenderer dr && dr.getDrawing() == drawing) {
                return;
            }
        }
        for (MapRenderer renderer : current) {
            view.removeRenderer(renderer);
        }
        view.setTrackingPosition(false);
        view.setUnlimitedTracking(false);
        view.addRenderer(new DrawingRenderer(drawing));
        drawing.markDirtyForRender();
    }

    /** Reconecta todos os desenhos salvos aos seus mapas (chamado ao ligar o plugin). */
    @SuppressWarnings("deprecation")
    public void reattachAll() {
        for (Drawing drawing : store.getDrawings()) {
            if (drawing.getMapId() < 0) {
                continue;
            }
            MapView view = Bukkit.getMap(drawing.getMapId());
            if (view == null) {
                plugin.getLogger().warning("Mapa #" + drawing.getMapId() + " do desenho #" + drawing.getId()
                        + " nao existe mais; sera recriado quando necessario.");
                continue;
            }
            views.put(view.getId(), view);
            attach(view, drawing);
        }
    }

    /** MapView do desenho, recriando-o se o arquivo do mapa tiver sumido. */
    @SuppressWarnings("deprecation")
    public MapView getView(Drawing drawing) {
        MapView view = views.get(drawing.getMapId());
        if (view == null && drawing.getMapId() >= 0) {
            view = Bukkit.getMap(drawing.getMapId());
        }
        if (view == null) {
            view = createMap();
            store.bindMap(drawing, view.getId());
            plugin.getLogger().warning("Desenho #" + drawing.getId() + " recebeu um novo mapa #" + view.getId() + ".");
        }
        views.put(view.getId(), view);
        attach(view, drawing);
        return view;
    }

    /** Envia o mapa atualizado ao jogador (so tem custo se o desenho mudou). */
    public void pushUpdate(Player player, Drawing drawing) {
        try {
            player.sendMap(getView(drawing));
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Falha ao atualizar mapa do desenho #" + drawing.getId() + ": " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------------ itens

    public ItemStack createItem(Drawing drawing, boolean viewCopy) {
        MapView view = getView(drawing);
        ItemStack item = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) item.getItemMeta();
        meta.setMapView(view);

        Messages messages = plugin.messages();
        String id = String.valueOf(drawing.getId());
        meta.displayName(Messages.item(messages.raw("item-name", "id", id)));
        List<Component> lore = new ArrayList<>();
        for (String line : messages.list(loreKey(drawing, viewCopy),
                "id", id,
                "player", drawing.getOwnerName(),
                "date", plugin.formatTime(drawing.getCreatedAt()))) {
            lore.add(Messages.item(line));
        }
        meta.lore(lore);

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(drawingKey, PersistentDataType.INTEGER, drawing.getId());
        if (viewCopy) {
            pdc.set(viewKey, PersistentDataType.BYTE, (byte) 1);
        }
        item.setItemMeta(meta);
        return item;
    }

    private static String loreKey(Drawing drawing, boolean viewCopy) {
        if (viewCopy) {
            return "item-lore-view";
        }
        DrawingState state = drawing.getState();
        if (state == DrawingState.DRAFT) {
            return "item-lore-draft";
        }
        if (state == DrawingState.FINISHED) {
            return "item-lore-finished";
        }
        return "item-lore-submitted";
    }

    /** Id do desenho gravado no item, ou null se nao for um mapa do plugin. */
    public Integer readDrawingId(ItemStack item) {
        if (item == null || item.getType() != Material.FILLED_MAP || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().get(drawingKey, PersistentDataType.INTEGER);
    }

    public boolean isViewCopy(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(viewKey, PersistentDataType.BYTE);
    }

    // ------------------------------------------------------------- inventario

    public boolean hasInInventory(Player player, int drawingId) {
        for (ItemStack stack : player.getInventory().getContents()) {
            Integer id = readDrawingId(stack);
            if (id != null && id == drawingId && !isViewCopy(stack)) {
                return true;
            }
        }
        return false;
    }

    /** Remove todos os mapas (nao-copias de visualizacao) deste desenho do inventario. */
    public void removeFromInventory(Player player, int drawingId) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length; i++) {
            Integer id = readDrawingId(contents[i]);
            if (id != null && id == drawingId && !isViewCopy(contents[i])) {
                inventory.setItem(i, null);
            }
        }
    }

    /**
     * Troca o primeiro mapa deste desenho encontrado no inventario por {@code replacement}
     * e remove eventuais duplicatas.
     *
     * @return true se algum mapa foi trocado
     */
    public boolean replaceInInventory(Player player, int drawingId, ItemStack replacement) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        boolean replaced = false;
        for (int i = 0; i < contents.length; i++) {
            Integer id = readDrawingId(contents[i]);
            if (id == null || id != drawingId || isViewCopy(contents[i])) {
                continue;
            }
            if (!replaced) {
                inventory.setItem(i, replacement.clone());
                replaced = true;
            } else {
                inventory.setItem(i, null);
            }
        }
        return replaced;
    }
}
