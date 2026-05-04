package com.openterface.keymod.preset;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * One row in the strip catalog {@link androidx.recyclerview.widget.RecyclerView} when using
 * {@link StripCatalogGridAdapter} (page/row headers span two columns; slot cells are half width).
 */
public final class StripCatalogGridItem {

    public static final int VIEW_TYPE_PAGE_HEADER = 0;
    public static final int VIEW_TYPE_ROW_BAND_HEADER = 1;
    public static final int VIEW_TYPE_SLOT_CELL = 2;

    public final int viewType;

    @Nullable
    public final String pageTitle;
    @Nullable
    public final String pageSubtitle;

    @Nullable
    public final String rowBandTitle;

    @Nullable
    public final String slotKey;
    public final int pageIndex;
    public final int stripRow;
    public final int col;
    /** {@code true} = Fn modifier layer ({@code *_fn} key). */
    public final boolean fnLayer;
    /** Short label for the layer (e.g. Base / Fn). */
    @Nullable
    public final String layerLabel;
    /** Printed cap / hub label for this cell (Fn-off primary for page 2). */
    @Nullable
    public final String physicalLabel;
    /** Optional second line (e.g. page 2 cap when Fn latched on). */
    @Nullable
    public final String latchHintLine;
    /** Human-readable key/chord for list UI (not the internal {@link #slotKey}). */
    @Nullable
    public final String keyEventLabel;

    private StripCatalogGridItem(
            int viewType,
            @Nullable String pageTitle,
            @Nullable String pageSubtitle,
            @Nullable String rowBandTitle,
            @Nullable String slotKey,
            int pageIndex,
            int stripRow,
            int col,
            boolean fnLayer,
            @Nullable String layerLabel,
            @Nullable String physicalLabel,
            @Nullable String latchHintLine,
            @Nullable String keyEventLabel
    ) {
        this.viewType = viewType;
        this.pageTitle = pageTitle;
        this.pageSubtitle = pageSubtitle;
        this.rowBandTitle = rowBandTitle;
        this.slotKey = slotKey;
        this.pageIndex = pageIndex;
        this.stripRow = stripRow;
        this.col = col;
        this.fnLayer = fnLayer;
        this.layerLabel = layerLabel;
        this.physicalLabel = physicalLabel;
        this.latchHintLine = latchHintLine;
        this.keyEventLabel = keyEventLabel;
    }

    @NonNull
    public static StripCatalogGridItem pageHeader(@NonNull String title, @Nullable String subtitle) {
        return new StripCatalogGridItem(
                VIEW_TYPE_PAGE_HEADER, title, subtitle, null, null, -1, -1, -1, false, null, null, null, null);
    }

    @NonNull
    public static StripCatalogGridItem rowBandHeader(@NonNull String title) {
        return new StripCatalogGridItem(
                VIEW_TYPE_ROW_BAND_HEADER, null, null, title, null, -1, -1, -1, false, null, null, null, null);
    }

    @NonNull
    public static StripCatalogGridItem slotCell(
            @NonNull String slotKey,
            int pageIndex,
            int stripRow,
            int col,
            boolean fnLayer,
            @NonNull String layerLabel,
            @Nullable String physicalLabel,
            @Nullable String latchHintLine,
            @Nullable String keyEventLabel
    ) {
        return new StripCatalogGridItem(
                VIEW_TYPE_SLOT_CELL,
                null,
                null,
                null,
                slotKey,
                pageIndex,
                stripRow,
                col,
                fnLayer,
                layerLabel,
                physicalLabel,
                latchHintLine,
                keyEventLabel);
    }
}
