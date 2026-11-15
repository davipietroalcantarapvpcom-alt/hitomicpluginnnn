package com.hitomic.hitomicdraw.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Identifica as telas do editor (desenho e ferramentas) sem depender do titulo. */
public final class EditorHolder implements InventoryHolder {

    private final EditorSession session;
    private final boolean tools;
    private Inventory inventory;

    EditorHolder(EditorSession session, boolean tools) {
        this.session = session;
        this.tools = tools;
    }

    EditorSession session() {
        return session;
    }

    boolean isTools() {
        return tools;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
