package com.hitomic.hitomicdraw.model;

import org.bukkit.Material;

import java.awt.Color;
import java.util.List;

/**
 * Paleta fixa do editor (27 cores = 3 linhas do inventario). Cada cor tem um item
 * para a GUI e um RGB; na hora de desenhar no mapa o Minecraft escolhe a cor mais
 * proxima disponivel na paleta de mapas do jogo.
 */
public final class Palette {

    public record Entry(String name, Material material, Color color) {
        public String hex() {
            return String.format("#%06X", color.getRGB() & 0xFFFFFF);
        }
    }

    /** Indice da cor de fundo (branco). */
    public static final byte BACKGROUND = 0;
    /** Cor inicial do pincel (preto). */
    public static final byte DEFAULT_COLOR = 3;

    private static final List<Entry> ENTRIES = List.of(
            entry("Branco", Material.WHITE_CONCRETE, 249, 255, 254),
            entry("Cinza claro", Material.LIGHT_GRAY_CONCRETE, 157, 157, 151),
            entry("Cinza", Material.GRAY_CONCRETE, 71, 79, 82),
            entry("Preto", Material.BLACK_CONCRETE, 8, 10, 15),
            entry("Marrom", Material.BROWN_CONCRETE, 96, 60, 32),
            entry("Vermelho", Material.RED_CONCRETE, 142, 33, 33),
            entry("Laranja", Material.ORANGE_CONCRETE, 224, 97, 0),
            entry("Amarelo", Material.YELLOW_CONCRETE, 240, 175, 21),
            entry("Verde-limao", Material.LIME_CONCRETE, 94, 168, 24),
            entry("Verde", Material.GREEN_CONCRETE, 73, 91, 36),
            entry("Ciano", Material.CYAN_CONCRETE, 21, 119, 136),
            entry("Azul claro", Material.LIGHT_BLUE_CONCRETE, 36, 137, 199),
            entry("Azul", Material.BLUE_CONCRETE, 44, 46, 143),
            entry("Roxo", Material.PURPLE_CONCRETE, 100, 31, 156),
            entry("Magenta", Material.MAGENTA_CONCRETE, 169, 48, 159),
            entry("Rosa", Material.PINK_CONCRETE, 213, 101, 142),
            entry("Pele", Material.ORANGE_TERRACOTTA, 240, 200, 170),
            entry("Areia", Material.SAND, 219, 207, 163),
            entry("Terra", Material.DIRT, 134, 96, 67),
            entry("Pedra", Material.STONE, 125, 125, 125),
            entry("Musgo", Material.MOSS_BLOCK, 89, 109, 45),
            entry("Rosa claro", Material.PINK_TERRACOTTA, 250, 180, 200),
            entry("Azul-marinho", Material.BLUE_TERRACOTTA, 25, 30, 80),
            entry("Ouro", Material.GOLD_BLOCK, 246, 208, 61),
            entry("Prata", Material.IRON_BLOCK, 220, 220, 220),
            entry("Esmeralda", Material.EMERALD_BLOCK, 42, 203, 87),
            entry("Diamante", Material.DIAMOND_BLOCK, 98, 237, 228)
    );

    private Palette() {
    }

    private static Entry entry(String name, Material material, int r, int g, int b) {
        return new Entry(name, material, new Color(r, g, b));
    }

    public static int size() {
        return ENTRIES.size();
    }

    public static Entry get(int index) {
        if (index < 0 || index >= ENTRIES.size()) {
            return ENTRIES.get(BACKGROUND);
        }
        return ENTRIES.get(index);
    }

    /** Indice da cor da paleta mais proxima do RGB informado. */
    public static byte nearest(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        int best = 0;
        long bestDistance = Long.MAX_VALUE;
        for (int i = 0; i < ENTRIES.size(); i++) {
            Color c = ENTRIES.get(i).color();
            long dr = r - c.getRed();
            long dg = g - c.getGreen();
            long db = b - c.getBlue();
            long distance = dr * dr + dg * dg + db * db;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return (byte) best;
    }

    /** Converte "#RRGGBB" para o indice da paleta; qualquer erro vira o fundo. */
    public static byte fromHex(String hex) {
        try {
            String h = hex.trim();
            if (h.startsWith("#")) {
                h = h.substring(1);
            }
            return nearest(Integer.parseInt(h, 16));
        } catch (RuntimeException ex) {
            return BACKGROUND;
        }
    }
}
