package com.hitomic.hitomicdraw.service;

import com.hitomic.hitomicdraw.model.Drawing;
import com.hitomic.hitomicdraw.model.Palette;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import java.awt.Color;

/**
 * Pinta o desenho 9x6 no mapa 128x128 (cada pixel logico vira um bloco ~14x21).
 * A conversao para as cores do mapa e feita pelo proprio Minecraft, que escolhe a
 * cor mais proxima da paleta de mapas. So redesenha quando o desenho mudou.
 */
public final class DrawingRenderer extends MapRenderer {

    private static final int MAP_SIZE = 128;

    private final Drawing drawing;

    public DrawingRenderer(Drawing drawing) {
        super(false);
        this.drawing = drawing;
    }

    public Drawing getDrawing() {
        return drawing;
    }

    @Override
    public void render(MapView map, MapCanvas canvas, Player player) {
        if (!drawing.consumeMapDirty()) {
            return;
        }
        Color[] colors = new Color[Palette.size()];
        for (int i = 0; i < colors.length; i++) {
            colors[i] = Palette.get(i).color();
        }
        for (int y = 0; y < MAP_SIZE; y++) {
            int row = (y * Drawing.HEIGHT / MAP_SIZE) * Drawing.WIDTH;
            for (int x = 0; x < MAP_SIZE; x++) {
                int cell = row + (x * Drawing.WIDTH / MAP_SIZE);
                canvas.setPixelColor(x, y, colors[drawing.getPixel(cell)]);
            }
        }
    }
}
