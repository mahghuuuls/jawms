package com.mahghuuuls.jawms.client;

public final class ClientHudSettings {

    public static final ClientHudSettings DEFAULT = new ClientHudSettings(
            true,
            true,
            TextDisplay.CURRENT,
            0.65F,
            Anchor.BOTTOM_LEFT,
            0,
            -50,
            FillDirection.BOTTOM_TO_TOP,
            81,
            7
    );

    private final boolean enabled;
    private final boolean hideWhenFull;
    private final TextDisplay textDisplay;
    private final float textScale;
    private final Anchor anchor;
    private final int offsetX;
    private final int offsetY;
    private final FillDirection fillDirection;
    private final int length;
    private final int thickness;

    public ClientHudSettings(
            boolean enabled,
            boolean hideWhenFull,
            TextDisplay textDisplay,
            float textScale,
            Anchor anchor,
            int offsetX,
            int offsetY,
            FillDirection fillDirection,
            int length,
            int thickness
    ) {
        if (anchor == null) {
            throw new NullPointerException("anchor");
        }
        if (textDisplay == null) {
            throw new NullPointerException("textDisplay");
        }
        if (fillDirection == null) {
            throw new NullPointerException("fillDirection");
        }
        if (length < 3 || thickness < 3) {
            throw new IllegalArgumentException("HUD dimensions must be at least 3.");
        }
        if (!Float.isFinite(textScale) || textScale < 0.5F || textScale > 3.0F) {
            throw new IllegalArgumentException("HUD text scale must be from 0.5 through 3.0.");
        }
        this.enabled = enabled;
        this.hideWhenFull = hideWhenFull;
        this.textDisplay = textDisplay;
        this.textScale = textScale;
        this.anchor = anchor;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.fillDirection = fillDirection;
        this.length = length;
        this.thickness = thickness;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isHideWhenFull() {
        return hideWhenFull;
    }

    public TextDisplay getTextDisplay() {
        return textDisplay;
    }

    public float getTextScale() {
        return textScale;
    }

    public Anchor getAnchor() {
        return anchor;
    }

    public int getOffsetX() {
        return offsetX;
    }

    public int getOffsetY() {
        return offsetY;
    }

    public FillDirection getFillDirection() {
        return fillDirection;
    }

    public int getLength() {
        return length;
    }

    public int getThickness() {
        return thickness;
    }

    public enum Anchor {
        TOP_LEFT(0, 0),
        TOP_CENTER(1, 0),
        TOP_RIGHT(2, 0),
        MIDDLE_LEFT(0, 1),
        MIDDLE_CENTER(1, 1),
        MIDDLE_RIGHT(2, 1),
        BOTTOM_LEFT(0, 2),
        BOTTOM_CENTER(1, 2),
        BOTTOM_RIGHT(2, 2);

        private final int horizontalAlignment;
        private final int verticalAlignment;

        Anchor(int horizontalAlignment, int verticalAlignment) {
            this.horizontalAlignment = horizontalAlignment;
            this.verticalAlignment = verticalAlignment;
        }

        int getHorizontalAlignment() {
            return horizontalAlignment;
        }

        int getVerticalAlignment() {
            return verticalAlignment;
        }
    }

    public enum FillDirection {
        LEFT_TO_RIGHT,
        RIGHT_TO_LEFT,
        BOTTOM_TO_TOP,
        TOP_TO_BOTTOM;

        public boolean isVertical() {
            return this == BOTTOM_TO_TOP || this == TOP_TO_BOTTOM;
        }
    }

    public enum TextDisplay {
        NONE,
        CURRENT,
        MAXIMUM,
        CURRENT_AND_MAXIMUM;

        public String format(int current, int maximum) {
            switch (this) {
                case CURRENT:
                    return Integer.toString(current);
                case MAXIMUM:
                    return Integer.toString(maximum);
                case CURRENT_AND_MAXIMUM:
                    return current + " / " + maximum;
                default:
                    return "";
            }
        }
    }
}
