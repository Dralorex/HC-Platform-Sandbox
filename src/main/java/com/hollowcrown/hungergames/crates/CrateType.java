package com.hollowcrown.hungergames.crates;

import org.bukkit.Material;

public enum CrateType {
    FOOD(Material.SMOKER),
    WEAPONS(Material.SMITHING_TABLE),
    BLOCKS(Material.CHEST),
    TOOLS(Material.CRAFTING_TABLE),
    KITS(Material.ENDER_CHEST);

    public final Material displayBlock;
    CrateType(Material displayBlock) { this.displayBlock = displayBlock; }
}


