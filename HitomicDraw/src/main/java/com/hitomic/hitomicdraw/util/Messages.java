package com.hitomic.hitomicdraw.util;

import com.hitomic.hitomicdraw.HitomicDrawPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;

/** Mensagens configuraveis (config.yml, secao "messages") com codigos de cor "&". */
public final class Messages {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .build();

    private final HitomicDrawPlugin plugin;

    public Messages(HitomicDrawPlugin plugin) {
        this.plugin = plugin;
    }

    /** Texto com codigos "&" para Component (para chat e titulos). */
    public static Component legacy(String text) {
        return LEGACY.deserialize(text);
    }

    /** Igual a legacy, mas sem italico padrao (para nomes e lores de itens). */
    public static Component item(String text) {
        return LEGACY.deserialize(text).decoration(TextDecoration.ITALIC, false);
    }

    public String raw(String key, String... placeholders) {
        String value = plugin.getConfig().getString("messages." + key);
        if (value == null) {
            value = "&c[mensagem ausente: " + key + "]";
        }
        return apply(value, placeholders);
    }

    public List<String> list(String key, String... placeholders) {
        List<String> source = plugin.getConfig().getStringList("messages." + key);
        List<String> result = new ArrayList<>(source.size());
        for (String line : source) {
            result.add(apply(line, placeholders));
        }
        return result;
    }

    /** Mensagem sem prefixo. */
    public Component get(String key, String... placeholders) {
        return legacy(raw(key, placeholders));
    }

    /** Mensagem com o prefixo do plugin. */
    public Component prefixed(String key, String... placeholders) {
        return legacy(prefix() + raw(key, placeholders));
    }

    public void send(CommandSender to, String key, String... placeholders) {
        to.sendMessage(prefixed(key, placeholders));
    }

    public String prefix() {
        return plugin.getConfig().getString("messages.prefix", "");
    }

    private static String apply(String text, String... placeholders) {
        String result = text;
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            result = result.replace("{" + placeholders[i] + "}", String.valueOf(placeholders[i + 1]));
        }
        return result;
    }
}
