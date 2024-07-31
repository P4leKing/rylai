package com.github.p4leking.rylai.utils

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import org.bukkit.inventory.ItemStack

class LevelData(var level : Int = 1, var experience : Int = 0) {
    companion object{
        @JvmStatic var xpMultiplier = 1.0
    }

    private var breakpoint = calcBreakpoint()

    /** Handle experience gain in this skill. Returns true if a level was gained. */
    fun gainExperience(xp : Int) : Boolean{
        experience += (xp * xpMultiplier).toInt()

        /** Check if player can still level up */
        if (level >= 100) {
            return false
        }

        /** Check if the player has leveled up */
        if (experience < breakpoint) {
            return false
        }

        /** Level up - accidental double levels through rapid xp gain possible? */
        experience -= breakpoint
        level += 1
        breakpoint = calcBreakpoint()

        /** In case multiple level ups have been gained level up repeatedly */
        while(experience >= breakpoint && level < 100){
            experience -= breakpoint
            level += 1
            breakpoint = calcBreakpoint()
        }
        return true
    }

    /** Generates the itemStack used as the UI element */
    fun generateSkillItem(skill: Skill) : ItemStack {
        val item = ItemStack(skill.icon)
        val meta = item.itemMeta

        meta.displayName(Component.text(skill.className))

        /** Add level, experience and description to the lore */
        val text : ArrayList<Component> = ArrayList()
        text.add(Component.text("Level: $level", TextColor.color(100, 0, 100)))

        /** Don't show the breakpoint for level 100 skills */
        if(level < 100){
            text.add(Component.text("XP: $experience / $breakpoint", TextColor.color(96, 0, 205)))
        }else{
            text.add(Component.text("XP: $experience", TextColor.color(96, 0, 205)))
        }

        text.add(Component.text(skill.description, TextColor.color(39,223,236)))
        meta.lore(text)

        /** Override and return */
        item.itemMeta = meta
        return item
    }

    /** Calculates the new breakpoint after a level up */
    private fun calcBreakpoint(): Int {
        return 4 * level * level + 50 * level + 946
    }
}