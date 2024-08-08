/** © 2024 Bernhard Eierle. All rights reserved. */

package com.github.p4leking.rylai.handlers

import com.github.p4leking.rylai.Rylai
import com.github.p4leking.rylai.classes.Warrior
import com.github.p4leking.rylai.utils.Abilities
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

class WarriorHandler(private val plugin: Rylai) : Listener {
    init {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    @EventHandler
    fun impact(event: EntityDamageEvent){
        if(event.cause != EntityDamageEvent.DamageCause.FALL){
            return
        }

        val player = event.entity
        if(player !is Player){
            return
        }

        /** Generate explosion */
        if(Warrior.impactActive.remove(event.entity.uniqueId)){

            /** Particles and Sound */
            player.world.spawnParticle(Particle.EXPLOSION_HUGE, player.location, 1)
            player.world.playSound(player, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 2F, 1.05F)

            /** AoE damage & slow */
            val group = plugin.players[player.uniqueId]!!.group  
            val dmg = plugin.levelScaling(player.uniqueId, Abilities.IMPACT.force)
            for(entity in player.getNearbyEntities(4.0,4.0,4.0)){
                if(entity !is LivingEntity) {
                    continue
                }

                /** Check if target is eligible */
                if(entity is Player){
                    if(!plugin.playerIsValidTarget(group, entity)){
                        continue
                    }
                }

                entity.damage(dmg, player)
                entity.addPotionEffect(PotionEffect(PotionEffectType.SLOW, 60, 1))
            }

            /** Cancel fall damage */
            event.isCancelled = true
        }
    }
}