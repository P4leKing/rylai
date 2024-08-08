package com.github.p4leking.rylai.handlers

import com.destroystokyo.paper.event.entity.ProjectileCollideEvent
import com.destroystokyo.paper.event.player.PlayerPostRespawnEvent
import com.github.p4leking.rylai.Rylai
import com.github.p4leking.rylai.utils.Classes
import org.bukkit.ChatColor
import org.bukkit.entity.Arrow
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.ArrowBodyCountChangeEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import java.util.*

class CombatHandler(private val plugin: Rylai) : Listener {
    companion object{
        val inCombat = mutableMapOf<UUID, Int>()
    }
    private val combatTicks = 1200L //1min
    private val cooldownsOnDeath = mutableMapOf<UUID, Pair<Long, List<Int>>>()
    private val combatCommands = setOf("rinfo", "ri", "rylaiinfo", "group", "rg", "rylaigroup", "chat", "global",
        "local", "trading", "w", "r", "ignore", "unignore")

    init {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    /** Removes arrows after they stopped flying to improve performance */
    @EventHandler
    fun removeGroundArrows(event: ProjectileHitEvent){
        if(event.hitBlock != null && event.entity is Arrow){
            event.entity.remove()
        }
    }

    /** Removes arrows in player bodies to make them fully invisible */
    @EventHandler
    fun removePlayerArrows(event: ArrowBodyCountChangeEvent){
        event.isCancelled = true
    }

    @EventHandler
    fun projectilesPassAlliedPlayers(event: ProjectileCollideEvent){
        val shooter = event.entity.shooter
        val target = event.collidedWith
        if((target is Player) && (shooter is Player)){
            if(!plugin.playerIsValidTarget(plugin.players[shooter.uniqueId]?.group, target)){
                event.isCancelled = true
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun handlePvpDamage(event: EntityDamageByEntityEvent){
        val target = event.entity as? Player ?: return

        /** Put both players in combat on projectile damage - don't need to check if target is valid since that was done during projectile collide */
        val attacker = event.damager
        if((attacker is Projectile) && (attacker.shooter is Player)){
            setPlayerInCombat(attacker.shooter as Player)
            setPlayerInCombat(target)
            return
        }

        /** Other damage */
        if(attacker !is Player){
            return
        }

        val hitGroup = plugin.players[target.uniqueId]!!.group  
        if(hitGroup != null && hitGroup == plugin.players[attacker.uniqueId]?.group){ // might be null e.g. on log out after dragonfire
            event.isCancelled = true
            return
        }

        /** On valid damage, put both players in combat */
        setPlayerInCombat(attacker)
        setPlayerInCombat(event.entity as Player)
    }

    private fun setPlayerInCombat(player: Player){
        val playerID = player.uniqueId
        val task = inCombat[playerID]
        if(task != null){
            /** Player is in combat already. Cancel old task. */
            plugin.server.scheduler.cancelTask(task)
        }else{
            /** Player is not in combat yet. Send a notification. */
            player.sendMessage("${ChatColor.DARK_RED}You have been attacked by a player. If you logout while in combat you will die!")
        }

        /** Create a new map entry and a delayed task and save its id */
        inCombat[playerID] = plugin.server.scheduler.scheduleSyncDelayedTask(plugin, {
            inCombat.remove(playerID)
            player.sendMessage("${ChatColor.GREEN}You are no longer in combat!")
        }, combatTicks)
    }

    @EventHandler
    fun blockCombatCommands(event: PlayerCommandPreprocessEvent){
        val player = event.player
        if(!inCombat.contains(player.uniqueId) || player.isOp){
            return
        }

        /** Allow whitelisted commands in combat */
        val command = event.message.removePrefix("/").substringBefore(" ").lowercase()
        if(command in combatCommands){
            return
        }

        /** Block execution of illegal command in combat and notify the player */
        event.isCancelled = true
        player.sendMessage("${ChatColor.DARK_RED}You cannot use ${ChatColor.GOLD}/$command${ChatColor.DARK_RED} while in combat.")
    }

    @EventHandler
    fun onDeath(event: PlayerDeathEvent){
        val player = event.player
        val playerID = player.uniqueId

        /** Remove in combat status of the player that died. */
        val taskID = inCombat[playerID]
        if(taskID != null){
            plugin.server.scheduler.cancelTask(taskID)
        }
        inCombat.remove(playerID)

        /** Save all ability item cooldowns to make them visible after respawning.
         * Might not track cooldowns for deaths through logout in combat */
        val selectedClass = plugin.players[playerID]?.selectedClass ?: return
        val combatClass = Classes.values().find{ it.name == selectedClass }!!  


        /** Save ability icon cooldowns and system time to calculate time spent dead */
        val cooldowns = mutableListOf<Int>()
        for(ability in combatClass.abilities) {
            cooldowns.add(player.getCooldown(ability.icon))
        }

        cooldownsOnDeath[playerID] = Pair(System.currentTimeMillis(), cooldowns)
    }

    @EventHandler
    fun onRespawn(event: PlayerPostRespawnEvent){
        /** Load all ability item cooldowns to make them visible after respawning */
        val player = event.player
        val playerID = player.uniqueId
        val entry = cooldownsOnDeath[playerID] ?: return
        val selectedClass = plugin.players[playerID]!!.selectedClass  
        val combatClass = Classes.values().find{ it.name == selectedClass }!!  

        /** Calculate time spent dead and convert it to ticks */
        val ticksDead = ((System.currentTimeMillis() - entry.first) * 0.02).toInt()

        for ((i, ability) in combatClass.abilities.withIndex()) {
            val remainingCD = entry.second[i] - ticksDead
            if(remainingCD > 0){
                player.setCooldown(ability.icon, remainingCD)
            }
        }

        cooldownsOnDeath.remove(playerID)
    }
}