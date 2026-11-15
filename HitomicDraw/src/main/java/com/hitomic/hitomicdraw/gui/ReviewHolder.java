package com.hitomic.hitomicdraw.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Identifica a tela de avaliacao de uma participacao. */
public final class ReviewHolder implements InventoryHolder {

    private final int submissionId;
    private Inventory inventory;

    ReviewHolder(int submissionId) {
        this.submissionId = submissionId;
    }

    int submissionId() {
        return submissionId;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
