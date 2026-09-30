package com.nezurstandalone.module;

/**
 * HUD modules that can be repositioned while chat is open.
 */
public interface DraggableHud {
    String getHudKey();

    boolean isHudVisible();

    /** Where this HUD sits before the user has ever dragged it. */
    double getDefaultHudX();

    double getDefaultHudY();

    /**
     * Current position, saved override first.
     *
     * <p>Sixteen modules carried a byte-identical body for this, differing only in the
     * name of the constant they passed - so the constant is what the interface asks for
     * now, and the lookup lives in one place.
     */
    default int getHudX() {
        return (int) com.nezurstandalone.utils.HudPositionManager.getX(getHudKey(), getDefaultHudX());
    }

    default int getHudY() {
        return (int) com.nezurstandalone.utils.HudPositionManager.getY(getHudKey(), getDefaultHudY());
    }

    int getHudWidth();

    int getHudHeight();

    default boolean isHudCenterAnchored() {
        return false;
    }

    default int getRenderX() {
        return com.nezurstandalone.utils.HudBounds.clampX(
                getHudX(), Math.max(1, getHudWidth()), isHudCenterAnchored());
    }

    default int getRenderY() {
        return com.nezurstandalone.utils.HudBounds.clampY(
                com.nezurstandalone.utils.HudStackManager.getStackedY(this), Math.max(1, getHudHeight()));
    }

    default boolean isStackedList() {
        return false;
    }

    default int getConfigY() {
        return getHudY();
    }

    default void renderStacked() {}
}
