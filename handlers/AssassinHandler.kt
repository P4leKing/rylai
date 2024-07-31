package com.github.p4leking.rylai.handlers

import com.github.p4leking.rylai.Rylai
import com.github.p4leking.rylai.classes.Assassin
import com.github.p4leking.rylai.utils.Abilities
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Arrow
import org.bukkit.entity.Creature
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityTargetLivingEntityEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.entity.ProjectileHitEvent

class AssassinHandler(private val plugin: Rylai) : Listener {
    init {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun handleAssassinAbilityHits(event: EntityDamageByEntityEvent){
        val attacker = event.damager

        /** Handle eviscerate hit */
        if(attacker is Player){

            /** Reveal invisible players on damage dealt */
            Assassin.cancelInvis(plugin, attacker)

            /** Check if damage was done with a critical hit */
            if(!event.isCritical){
                return
            }

            /** Handle eviscerate */
            val target = event.entity as? LivingEntity ?: return

            /** End eviscerate active duration & cancel the timer for its interval */
            val eviscerateActive = Assassin.eviscerateActive[attacker.uniqueId] ?: return
            plugin.server.scheduler.cancelTask(eviscerateActive)
            Assassin.eviscerateActive.remove(attacker.uniqueId)

            /** Bonus damage (1.25x - 1.75x) based on missing health */
            val multiplier = plugin.levelScaling(attacker.uniqueId, Abilities.EVISCERATE.force)
            event.damage = event.damage * (1.0 + multiplier + 0.5 * (1.0 - (target.health / (target.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.value ?: 20.0))))

            /** Cooldown reset if target was struck from behind
             * (yaw of players and entities work differently and can't be used) */
            val v1 = attacker.location.direction
            val v2 = target.location.direction
            v1.y = 0.0
            v2.y = 0.0

            if(v1.angle(v2) > Math.PI/2){
                /** Failed reset sound effect */
                attacker.world.playSound(attacker, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5F, 1F)
                return
            }

            /** Successful reset sound effect */
            attacker.world.playSound(attacker, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5F, 0.5F)

            /** Reset eviscerate cooldown */
            val cooldownTask = Assassin.eviscerateCooldown[attacker.uniqueId] ?: return
            plugin.server.scheduler.cancelTask(cooldownTask)
            Assassin.eviscerateCooldown.remove(attacker.uniqueId)
            attacker.setCooldown(Abilities.EVISCERATE.icon, 0)
            return
        }

        /** Handle deathmark hit. Group members & invulnerable players can't be damaged,
         * because they don't get collided with, so you can't mark them. */
        if(attacker !is Arrow) {
            return
        }

        /** Reveal invisible players on damage dealt */
        val player = attacker.shooter as? Player ?: return
        Assassin.cancelInvis(plugin, player)

        if(!Assassin.deathmarkArrow.contains(event.damager.uniqueId)){
            return
        }

        /** Only allow jump to valid targets */
        if(event.entity is Creature || event.entity is Player){
            Assassin.onDeathmarkHit(plugin, player, event.entity.uniqueId)
        }else{
            Assassin.startDeathmarkCooldown(plugin, player)
        }

        Assassin.deathmarkArrow.remove(attacker.uniqueId)
    }

    @EventHandler
    fun onDeathmarkMiss(event: ProjectileHitEvent){
        val arrowID = event.entity.uniqueId
        if(!Assassin.deathmarkArrow.contains(arrowID)){
            return
        }

        /** Check if player hit terrain */
        if(event.hitBlock == null){
            return
        }

        /** Remove from array & start cooldown */
        val player = event.entity.shooter
        if(player is Player){
            Assassin.startDeathmarkCooldown(plugin, player)
            Assassin.deathmarkArrow.remove(arrowID)
        }
    }

    @EventHandler
    fun onDeath(event: PlayerDeathEvent){
        Assassin.removeDeathmarkOnDeath(event.player.uniqueId)
    }

    /** Prevent mobs targeting invisible players */
    @EventHandler
    fun onTargetInvisiblePlayer(event: EntityTargetLivingEntityEvent){
        val player = event.target as? Player ?: return

        if(player.isInvisible){
            event.isCancelled = true
        }
    }
}