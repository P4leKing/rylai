package com.github.p4leking.rylai.handlers

import com.github.p4leking.rylai.Rylai
import com.github.p4leking.rylai.classes.Archer.Companion.backflip
import com.github.p4leking.rylai.classes.Archer.Companion.hookshot
import com.github.p4leking.rylai.classes.Archer.Companion.mark
import com.github.p4leking.rylai.classes.Archer.Companion.netshot
import com.github.p4leking.rylai.classes.Assassin.Companion.bladestorm
import com.github.p4leking.rylai.classes.Assassin.Companion.deathmark
import com.github.p4leking.rylai.classes.Assassin.Companion.eviscerate
import com.github.p4leking.rylai.classes.Assassin.Companion.invisibility
import com.github.p4leking.rylai.classes.Hunter.Companion.anklebreaker
import com.github.p4leking.rylai.classes.Hunter.Companion.ascension
import com.github.p4leking.rylai.classes.Hunter.Companion.tripmine
import com.github.p4leking.rylai.classes.Hunter.Companion.tumble
import com.github.p4leking.rylai.classes.Mage.Companion.distortion
import com.github.p4leking.rylai.classes.Mage.Companion.dragonfire
import com.github.p4leking.rylai.classes.Mage.Companion.meteor
import com.github.p4leking.rylai.classes.Mage.Companion.stasis
import com.github.p4leking.rylai.classes.Warrior.Companion.battlecry
import com.github.p4leking.rylai.classes.Warrior.Companion.blessing
import com.github.p4leking.rylai.classes.Warrior.Companion.earthquake
import com.github.p4leking.rylai.classes.Warrior.Companion.impact
import net.kyori.adventure.text.TextComponent
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerItemHeldEvent

class CastHandler(private val plugin: Rylai) : Listener {
    /** Casting the ability by name requires every ability to have the same parameters */
    private val abilitiesByName = setOf(
            ::bladestorm, ::invisibility, ::deathmark, ::eviscerate, ::hookshot, ::impact, ::dragonfire,
            ::distortion, ::tumble, ::blessing, ::netshot, ::earthquake, ::mark, ::ascension, ::stasis,
            ::anklebreaker, ::backflip, ::meteor, ::battlecry, ::tripmine
    ).associateBy { it.name }

    init {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    /** Casts ability by name */
    @EventHandler(ignoreCancelled = true)
    fun onCast(event: PlayerItemHeldEvent) {
        val abilityID = (((event.player.inventory.getItem(event.newSlot) ?: return).lore() ?: return)[0] as? TextComponent ?: return).content()

        /** Check if item is an ability */
        if(!abilityID.startsWith("skill")){
            return
        }

        /** Switch to previous held item after casting by cancelling */
        event.isCancelled = true

        /** Check if player is silenced */
        if(plugin.isSilenced(event.player.uniqueId)){
            event.player.sendMessage("${ChatColor.DARK_RED}You are silenced and cannot use abilities right now.")
            return
        }

        /** Do string operation only once */
        val strippedID = abilityID.removePrefix("skill:").split(":")
        if(strippedID.size != 2){
            return
        }

        /** Check if player has actually selected this class. This is a failsafe to
         * prevent exploit abuse if players could somehow transfer the ability item. */
        if(strippedID[0] != plugin.players[event.player.uniqueId]!!.selectedClass){  
            event.player.sendMessage("${ChatColor.DARK_RED}You can't use abilities of another class.")
            return
        }

        /** Gets ability function */
        val ability = abilitiesByName[strippedID[1]] ?: return

        /** Uses ability or does nothing when no fitting ability was found */
        ability(plugin, event.player)

        /** Cancel eating or drinking */
        val type = event.player.activeItem.type
        if(type.isEdible || type == Material.POTION){
            event.player.clearActiveItem()
        }
    }
}
