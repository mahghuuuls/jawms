package com.mahghuuuls.jawms.client;

public final class HudTextLayout {

    private HudTextLayout() {
    }

    public static Result resolve(
            int unscaledTextWidth,
            int unscaledTextHeight,
            float configuredScale,
            HudLayout.Rectangle bar,
            int screenWidth,
            int screenHeight
    ) {
        if (unscaledTextWidth < 0 || unscaledTextHeight < 1) {
            throw new IllegalArgumentException("Text dimensions are invalid.");
        }
        if (!Float.isFinite(configuredScale) || configuredScale <= 0.0F) {
            throw new IllegalArgumentException("Text scale must be finite and positive.");
        }
        if (bar == null) {
            throw new NullPointerException("bar");
        }
        if (screenWidth < 1 || screenHeight < 1) {
            throw new IllegalArgumentException("Screen dimensions must be positive.");
        }

        float effectiveScale = configuredScale;
        if (unscaledTextWidth > 0) {
            effectiveScale = Math.min(
                    effectiveScale,
                    screenWidth / (float) unscaledTextWidth
            );
        }
        effectiveScale = Math.min(
                effectiveScale,
                screenHeight / (float) unscaledTextHeight
        );
        while ((double) unscaledTextWidth * effectiveScale > screenWidth
                || (double) unscaledTextHeight * effectiveScale > screenHeight) {
            effectiveScale = Math.nextAfter(effectiveScale, 0.0D);
        }

        int renderedWidth = Math.min(
                screenWidth,
                (int) Math.ceil(unscaledTextWidth * (double) effectiveScale)
        );
        int renderedHeight = Math.min(
                screenHeight,
                (int) Math.ceil(unscaledTextHeight * (double) effectiveScale)
        );
        int x = clamp(
                bar.getX() + (bar.getWidth() - renderedWidth) / 2,
                0,
                screenWidth - renderedWidth
        );
        int preferredY = bar.getY() >= renderedHeight + 1
                ? bar.getY() - renderedHeight - 1
                : bar.getY() + bar.getHeight() + 2;
        int y = clamp(preferredY, 0, screenHeight - renderedHeight);
        return new Result(
                x,
                y,
                renderedWidth,
                renderedHeight,
                effectiveScale
        );
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public static final class Result {

        private final int x;
        private final int y;
        private final int width;
        private final int height;
        private final float scale;

        private Result(int x, int y, int width, int height, float scale) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.scale = scale;
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

        public float getScale() {
            return scale;
        }
    }
}
