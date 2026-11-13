package com.example.watermark;

import java.util.List;

public record WatermarkOptions(Type type, Visibility visibility, String text, String color,
                               double opacity, int fontSize, int angle, String layout,
                               String density, List<Integer> sheets) {
    public enum Type { TEXT, IMAGE, BOTH }
    public enum Visibility { SCREEN, PRINT, BOTH }

    public WatermarkOptions {
        if (type == null || visibility == null) throw new IllegalArgumentException("请选择水印类型和生效场景");
        text = text == null ? "" : text.strip();
        if (type != Type.IMAGE && (text.isBlank() || text.length() > 160)) {
            throw new IllegalArgumentException("水印文字需为 1～160 个字符");
        }
        if (color == null || !color.matches("#[0-9a-fA-F]{6}")) throw new IllegalArgumentException("颜色格式不正确");
        if (!Double.isFinite(opacity) || opacity < 0.05 || opacity > 0.8) throw new IllegalArgumentException("透明度需在 5%～80% 之间");
        if (fontSize < 18 || fontSize > 80) throw new IllegalArgumentException("字号需在 18～80 之间");
        if (angle < -60 || angle > 60) throw new IllegalArgumentException("角度需在 -60～60 度之间");
        if (!List.of("tile", "single").contains(layout)) throw new IllegalArgumentException("水印布局不正确");
        if (!List.of("sparse", "normal", "dense").contains(density)) throw new IllegalArgumentException("水印疏密不正确");
        sheets = sheets == null ? List.of() : List.copyOf(sheets);
    }

    public boolean screen() { return visibility != Visibility.PRINT; }
    public boolean print() { return visibility != Visibility.SCREEN; }
}
