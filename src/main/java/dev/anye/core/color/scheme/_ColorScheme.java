package dev.anye.core.color.scheme;

import java.util.HashMap;

public class _ColorScheme implements _ColorSchemeInterface {
    protected final Color defaultColor;
    protected final HashMap<String,Color> colors;
    protected _ColorScheme(HashMap<String,Color> colors, Color defaultColor){
        this.colors = colors;
        this.defaultColor = defaultColor;
        pushColor();
    }
    protected _ColorScheme(Color defaultColor){
        this(new HashMap<>(),defaultColor);
    }
    protected _ColorScheme(HashMap<String,Color> colors){
        this(colors,new Color());
    }
    protected _ColorScheme(){
        this(new HashMap<>(),new Color());
    }

    @Override
    public HashMap<String, Color> getColors() {
        return this.colors;
    }

    @Override
    public Color getColor(String index) {
        return getColors().getOrDefault(index,defaultColor);
    }

    public record Color(int UsualColor, int HoverColor, int SelectColor){
        public Color() {
            this(0xffff0000,0xff00ff00,0xff0000ff);
        }
        public Color(int usualColor) {
            this(usualColor,usualColor,usualColor);
        }
        public Color(int usualColor,int hoverColor) {
            this(usualColor,hoverColor,0x00000000);
        }
    }
    protected void pushColor(){}
}
