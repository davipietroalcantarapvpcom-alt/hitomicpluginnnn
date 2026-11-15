package com.hitomic.hitomicdraw.gui;

import com.hitomic.hitomicdraw.model.Drawing;
import com.hitomic.hitomicdraw.model.Palette;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.ArrayDeque;
import java.util.Deque;

/** Estado de edicao de um jogador (ferramenta, cor, historico de desfazer/refazer). */
final class EditorSession {

    enum Tool {
        BRUSH("Pincel"),
        ERASER("Borracha"),
        FILL("Balde");

        final String label;

        Tool(String label) {
            this.label = label;
        }
    }

    final Player player;
    final Drawing drawing;
    Tool tool = Tool.BRUSH;
    byte color = Palette.DEFAULT_COLOR;
    final Deque<byte[]> undo = new ArrayDeque<>();
    final Deque<byte[]> redo = new ArrayDeque<>();
    long clearConfirmUntil;
    long finishConfirmUntil;
    boolean switching;
    boolean updatePending;
    Inventory canvasInv;
    Inventory toolsInv;

    EditorSession(Player player, Drawing drawing) {
        this.player = player;
        this.drawing = drawing;
    }
}
