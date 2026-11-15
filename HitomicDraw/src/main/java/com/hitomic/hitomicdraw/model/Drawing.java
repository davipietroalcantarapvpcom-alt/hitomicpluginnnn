package com.hitomic.hitomicdraw.model;

import java.util.Arrays;
import java.util.UUID;

/** Um desenho 9x6. Cada pixel guarda o indice de uma cor da {@link Palette}. */
public final class Drawing {

    public static final int WIDTH = 9;
    public static final int HEIGHT = 6;
    public static final int SIZE = WIDTH * HEIGHT;

    private final int id;
    private final UUID owner;
    private String ownerName;
    private final byte[] pixels = new byte[SIZE];
    private int mapId = -1;
    private DrawingState state = DrawingState.DRAFT;
    private final long createdAt;
    private long finishedAt;
    private long submittedAt;
    private volatile boolean mapDirty = true;

    public Drawing(int id, UUID owner, String ownerName, long createdAt) {
        this.id = id;
        this.owner = owner;
        this.ownerName = ownerName;
        this.createdAt = createdAt;
        Arrays.fill(pixels, Palette.BACKGROUND);
    }

    public int getId() {
        return id;
    }

    public UUID getOwner() {
        return owner;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public int getMapId() {
        return mapId;
    }

    public void setMapId(int mapId) {
        this.mapId = mapId;
        this.mapDirty = true;
    }

    public DrawingState getState() {
        return state;
    }

    public void setState(DrawingState state) {
        this.state = state;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(long finishedAt) {
        this.finishedAt = finishedAt;
    }

    public long getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(long submittedAt) {
        this.submittedAt = submittedAt;
    }

    public boolean isEditable() {
        return state == DrawingState.DRAFT;
    }

    public byte getPixel(int index) {
        return pixels[index];
    }

    /** @return true se o pixel realmente mudou. */
    public boolean setPixel(int index, byte color) {
        if (pixels[index] == color) {
            return false;
        }
        pixels[index] = color;
        mapDirty = true;
        return true;
    }

    /** @return true se algum pixel mudou. */
    public boolean clear() {
        boolean changed = false;
        for (int i = 0; i < SIZE; i++) {
            if (pixels[i] != Palette.BACKGROUND) {
                pixels[i] = Palette.BACKGROUND;
                changed = true;
            }
        }
        if (changed) {
            mapDirty = true;
        }
        return changed;
    }

    public byte[] snapshot() {
        return pixels.clone();
    }

    public void restore(byte[] data) {
        System.arraycopy(data, 0, pixels, 0, SIZE);
        mapDirty = true;
    }

    public void markDirtyForRender() {
        mapDirty = true;
    }

    /** Usado pelo renderizador: devolve true uma unica vez apos cada alteracao. */
    public boolean consumeMapDirty() {
        boolean was = mapDirty;
        mapDirty = false;
        return was;
    }
}
