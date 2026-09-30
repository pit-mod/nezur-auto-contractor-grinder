package com.nezurstandalone.module;

public enum Category {
    PLAYER("Player"),
    RENDER("Render"),
    SWAPPING("Swapping"),
    MISC("Misc"),
    AUTO("Auto");

    public String name;
    
    Category(String name) {
        this.name = name;
    }
}


