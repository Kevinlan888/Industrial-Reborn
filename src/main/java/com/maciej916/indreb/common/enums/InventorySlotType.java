package com.maciej916.indreb.common.enums;

public enum InventorySlotType {
    INPUT("input"),
    NORMAL("normal"),
    OUTPUT("output"),
    BATTERY("battery"),
    HELMET("helmet"),
    CHESTPLATE("chestplate"),
    LEGGINGS("leggings"),
    BOOTS("boots"),
    DISABLED("disabled");

    private final String type;

    InventorySlotType(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }
}
