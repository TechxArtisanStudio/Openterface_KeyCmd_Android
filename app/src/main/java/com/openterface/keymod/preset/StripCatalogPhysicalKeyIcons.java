package com.openterface.keymod.preset;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;

import com.openterface.keymod.R;

/**
 * Drawable ids for fixed strip physical slots, aligned with
 * {@link com.openterface.keymod.CustomKeyboardView#buildFixedTopRowsPage0()} and
 * {@link com.openterface.keymod.CustomKeyboardView#buildFixedTopRowsPage1()}.
 */
public final class StripCatalogPhysicalKeyIcons {

    private StripCatalogPhysicalKeyIcons() {
    }

    /**
     * @return 0 when the slot uses text-only presentation in the keyboard strip.
     */
    @DrawableRes
    public static int iconForSlot(@NonNull String targetOs, int pageIndex, int stripRow, int col) {
        String os = targetOs != null ? targetOs : "macos";
        if (pageIndex == 0) {
            if (stripRow == 3 && col == 6) {
                return R.drawable.ic_swap_horiz_24;
            }
            return 0;
        }
        if (pageIndex == 1 && stripRow == 2) {
            switch (col) {
                case 0:
                    return "macos".equals(os) ? R.drawable.keyboard_control_key_24px : 0;
                case 1:
                    return "macos".equals(os) ? R.drawable.keyboard_option_key_24px : 0;
                case 2:
                    if ("macos".equals(os)) {
                        return R.drawable.keyboard_command_key_24px;
                    }
                    return "windows".equals(os) || "win".equals(os) ? R.drawable.windows : 0;
                case 3:
                    return R.drawable.keyboard_tab_24;
                case 4:
                    return R.drawable.keyboard_arrow_up_24;
                case 5:
                    return R.drawable.keyboard_return_24px;
                case 6:
                    return R.drawable.ic_keyboard_keymod_24;
                default:
                    return 0;
            }
        }
        if (pageIndex == 1 && stripRow == 3) {
            switch (col) {
                case 1:
                    return R.drawable.shift_24px;
                case 2:
                    return R.drawable.backspace_24;
                case 3:
                    return R.drawable.keyboard_arrow_left_24;
                case 4:
                    return R.drawable.keyboard_arrow_down_24;
                case 5:
                    return R.drawable.keyboard_arrow_right_24;
                case 6:
                    return R.drawable.ic_swap_horiz_24;
                default:
                    return 0;
            }
        }
        return 0;
    }
}
