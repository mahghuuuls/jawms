package com.mahghuuuls.jawms.client;

public final class HudLayout {

    private HudLayout() {
    }

    public static Rectangle resolve(
            ClientHudSettings settings,
            int screenWidth,
            int screenHeight
    ) {
        if (settings == null) {
            throw new NullPointerException("settings");
        }
        if (screenWidth < 1 || screenHeight < 1) {
            throw new IllegalArgumentException("Screen dimensions must be positive.");
        }
        boolean vertical = settings.getFillDirection().isVertical();
        int width = Math.min(screenWidth, vertical
                ? settings.getThickness()
                : settings.getLength());
        int height = Math.min(screenHeight, vertical
                ? settings.getLength()
                : settings.getThickness());
        int availableX = screenWidth - width;
        int availableY = screenHeight - height;
        long anchoredX = (long) availableX
                * settings.getAnchor().getHorizontalAlignment() / 2L;
        long anchoredY = (long) availableY
                * settings.getAnchor().getVerticalAlignment() / 2L;
        int x = clamp(anchoredX + settings.getOffsetX(), 0, availableX);
        int y = clamp(anchoredY + settings.getOffsetY(), 0, availableY);
        return new Rectangle(x, y, width, height);
    }

    public static Rectangle fill(
            Rectangle bar,
            ClientHudSettings.FillDirection direction,
            int current,
            int maximum
    ) {
        if (bar == null) {
            throw new NullPointerException("bar");
        }
        if (direction == null) {
            throw new NullPointerException("direction");
        }
        int innerWidth = Math.max(0, bar.getWidth() - 2);
        int innerHeight = Math.max(0, bar.getHeight() - 2);
        int clampedCurrent = maximum <= 0 ? 0 : Math.max(0, Math.min(current, maximum));
        if (direction.isVertical()) {
            int filled = maximum <= 0 ? 0
                    : (int) ((long) innerHeight * clampedCurrent / maximum);
            int y = direction == ClientHudSettings.FillDirection.BOTTOM_TO_TOP
                    ? bar.getY() + 1 + innerHeight - filled
                    : bar.getY() + 1;
            return new Rectangle(bar.getX() + 1, y, innerWidth, filled);
        }
        int filled = maximum <= 0 ? 0
                : (int) ((long) innerWidth * clampedCurrent / maximum);
        int x = direction == ClientHudSettings.FillDirection.RIGHT_TO_LEFT
                ? bar.getX() + 1 + innerWidth - filled
                : bar.getX() + 1;
        return new Rectangle(x, bar.getY() + 1, filled, innerHeight);
    }

    public static boolean shouldRender(
            boolean stateAvailable,
            boolean hideGui,
            boolean spectator,
            boolean hideWhenFull,
            int current,
            int maximum
    ) {
        return stateAvailable
                && !hideGui
                && !spectator
                && maximum > 0
                && (!hideWhenFull || current < maximum);
    }

    private static int clamp(long value, int minimum, int maximum) {
        return (int) Math.max(minimum, Math.min((long) maximum, value));
    }

    public static final class Rectangle {

        private final int x;
        private final int y;
        private final int width;
        private final int height;

        Rectangle(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }

        public int getWidth() {
            return width;
        }

        public int getHeight() {
            return height;
        }
    }
}
