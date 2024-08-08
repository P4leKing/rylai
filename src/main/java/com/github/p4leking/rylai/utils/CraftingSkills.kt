package com.github.p4leking.rylai.utils

import org.bukkit.Material

enum class CraftingSkills(override val className: String, override val icon: Material, val efficiencyIncrement: Int,
                          val luckIncrement: Int, override val description: String) : Skill {
    MINING("Mining", Material.DIAMOND, 20, 30, "Digging a Hole"),
    FISHING("Fishing", Material.SALMON, 25, 30, "AFK"),
    WOODCUTTING("Woodcutting", Material.OAK_WOOD, 20, 0, "Ich und mein Holz"),
    SMITHING("Smithing", Material.ANVIL, 0, 0, "The cooler enchanting"),
    ALCHEMY("Alchemy", Material.BLAZE_POWDER, 0, 0, "")
}