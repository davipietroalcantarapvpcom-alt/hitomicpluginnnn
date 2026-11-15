package com.hitomic.hitomicdraw;

import com.hitomic.hitomicdraw.command.DrawCommand;
import com.hitomic.hitomicdraw.gui.EditorManager;
import com.hitomic.hitomicdraw.gui.GuiItems;
import com.hitomic.hitomicdraw.gui.ReviewGui;
import com.hitomic.hitomicdraw.listener.EditorListener;
import com.hitomic.hitomicdraw.listener.MapListener;
import com.hitomic.hitomicdraw.service.DrawingService;
import com.hitomic.hitomicdraw.service.MapService;
import com.hitomic.hitomicdraw.storage.DataStore;
import com.hitomic.hitomicdraw.util.Messages;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;

public final class HitomicDrawPlugin extends JavaPlugin {

    public static final String PERM_USE = "hitomicdraw.use";
    public static final String PERM_ADMIN = "hitomicdraw.admin";

    private Messages messages;
    private DataStore store;
    private EditorManager editor;
    private DateTimeFormatter dateFormat;

    @Override
    public void onEnable() {
        try {
            saveDefaultConfig();
            for (String name : new String[]{"drawings.yml", "submissions.yml"}) {
                if (!new File(getDataFolder(), name).exists()) {
                    saveResource(name, false);
                }
            }
            messages = new Messages(this);
            reloadSettings();

            store = new DataStore(this);
            store.load();

            MapService maps = new MapService(this, store);
            maps.reattachAll();

            GuiItems items = new GuiItems(this);
            DrawingService service = new DrawingService(this, store, maps, messages);
            editor = new EditorManager(this, store, maps, service, messages, items);
            ReviewGui review = new ReviewGui(this, store, maps, service, messages, items);

            PluginManager pluginManager = getServer().getPluginManager();
            pluginManager.registerEvents(new EditorListener(this, editor, review, items, service), this);
            pluginManager.registerEvents(new MapListener(this, store, maps, service, messages), this);

            DrawCommand command = new DrawCommand(this, store, maps, service, editor, review, messages);
            PluginCommand pluginCommand = getCommand("desenhar");
            if (pluginCommand == null) {
                throw new IllegalStateException("Comando 'desenhar' ausente do plugin.yml");
            }
            pluginCommand.setExecutor(command);
            pluginCommand.setTabCompleter(command);

            store.startAutosave();
            getLogger().info("HitomicDraw ativado.");
        } catch (Exception ex) {
            getLogger().log(Level.SEVERE, "Falha ao iniciar o HitomicDraw; desativando.", ex);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (editor != null) {
            editor.closeAll();
        }
        if (store != null) {
            store.shutdown();
        }
    }

    /** Recarrega config.yml e o formato de data. */
    public void reloadSettings() {
        reloadConfig();
        ZoneId zone;
        try {
            zone = ZoneId.of(getConfig().getString("settings.timezone", "America/Sao_Paulo"));
        } catch (RuntimeException ex) {
            zone = ZoneId.systemDefault();
        }
        DateTimeFormatter formatter;
        try {
            formatter = DateTimeFormatter.ofPattern(getConfig().getString("settings.date-format", "dd/MM/yyyy HH:mm:ss"));
        } catch (IllegalArgumentException ex) {
            formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
        }
        dateFormat = formatter.withZone(zone);
    }

    public String formatTime(long millis) {
        if (millis <= 0) {
            return "-";
        }
        return dateFormat.format(Instant.ofEpochMilli(millis));
    }

    public Messages messages() {
        return messages;
    }
}
