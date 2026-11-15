package com.hitomic.hitomicdraw.model;

import java.util.UUID;

/** Uma participacao enviada para avaliacao. */
public final class Submission {

    private final int id;
    private final int drawingId;
    private final UUID player;
    private final String playerName;
    private final long submittedAt;
    private SubmissionStatus status = SubmissionStatus.PENDING;
    private UUID reviewer;
    private String reviewerName;
    private long reviewedAt;
    private String note;
    private boolean notified;

    public Submission(int id, int drawingId, UUID player, String playerName, long submittedAt) {
        this.id = id;
        this.drawingId = drawingId;
        this.player = player;
        this.playerName = playerName;
        this.submittedAt = submittedAt;
    }

    public int getId() {
        return id;
    }

    public int getDrawingId() {
        return drawingId;
    }

    public UUID getPlayer() {
        return player;
    }

    public String getPlayerName() {
        return playerName;
    }

    public long getSubmittedAt() {
        return submittedAt;
    }

    public SubmissionStatus getStatus() {
        return status;
    }

    public void setStatus(SubmissionStatus status) {
        this.status = status;
    }

    public UUID getReviewer() {
        return reviewer;
    }

    public void setReviewer(UUID reviewer) {
        this.reviewer = reviewer;
    }

    public String getReviewerName() {
        return reviewerName;
    }

    public void setReviewerName(String reviewerName) {
        this.reviewerName = reviewerName;
    }

    public long getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(long reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public boolean isNotified() {
        return notified;
    }

    public void setNotified(boolean notified) {
        this.notified = notified;
    }
}
