package com.openterface.keymod.compose;

/**
 * User-saved snippet for Pro IME compose (and future Basic compose reuse).
 *
 * <p>Fields are Gson-friendly (public, mutable) like {@link com.openterface.keymod.MacrosManager.Macro}.
 */
public class SavedTextItem {

    public long id;
    /** User-visible title; may differ from first line of {@link #content}. */
    public String title;
    public String content;
    public boolean pinned;
    /** Millis when last pinned; meaningful when {@link #pinned} is true. */
    public long pinnedAt;
    public long createdAt;
    public long updatedAt;

    public SavedTextItem() {}

    public SavedTextItem(
            long id,
            String title,
            String content,
            boolean pinned,
            long pinnedAt,
            long createdAt,
            long updatedAt) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.pinned = pinned;
        this.pinnedAt = pinnedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
