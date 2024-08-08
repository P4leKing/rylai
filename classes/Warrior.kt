/** © 2024 Bernhard Eierle. All rights reserved. */

package com.github.p4leking.rylai.classes

import com.github.p4leking.rylai.Rylai
import com.github.p4leking.rylai.utils.Abilities
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Monster
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitRunnable
import java.util.*
import kotlin.math.cos
import kotlin.math.sin

class Warrior {
    companion object{
        private val impactCooldown = mutableSetOf<UUID>()
        private val blessingCooldown = mutableSetOf<UUID>()
        private val earthquakeCooldown = mutableSetOf<UUID>()
        private val battlecryCooldown = mutableSetOf<UUID>()
        val impactActive = mutableSetOf<UUID>()

        fun impact(plugin: Rylai, player: Player){
            val playerID = player.uniqueId

            /** Manage Cooldown */
            if(!impactCooldown.add(playerID)){
                return
            }
            player.setCooldown(Abilities.IMPACT.icon, Abilities.IMPACT.cooldown)

            plugin.server.scheduler.scheduleSyncDelayedTask(plugin, {
                impactCooldown.remove(playerID)
            }, Abilities.IMPACT.cooldown.toLong())

            /** Prevent fall damage for duration and do damage on landing */
            impactActive.add(playerID)

            /** Dash in 55 degree upwards angle */
            val dashVector = player.location
            dashVector.pitch = -55F
            player.velocity = dashVector.direction.multiply(1.50)

            /** Second dash in players direction - cant be delayed task because of ceilings */
            object: BukkitRunnable() {
                override fun run() {
                    /** Give second push */
                    if (player.velocity.lengthSquared() < 0.2){
                        player.velocity = player.location.direction.multiply(1.3)
                        cancel()
                    }
                }
            }.runTaskTimer(plugin,8,3)

            /** Cancel ability after certain amount of time (in case player didn't receive fall damage) */
            plugin.server.scheduler.scheduleSyncDelayedTask(plugin, {
                impactActive.remove(playerID)
            }, 60)
        }

        /** Gives the player golden hearts if on full hp or heals 2 hp if not */
        fun blessing(plugin: Rylai, player: Player){
            val playerID = player.uniqueId

            /** Manage Cooldown */
            if(!blessingCooldown.add(playerID)){
                return
            }
            player.setCooldown(Abilities.BLESSING.icon, Abilities.BLESSING.cooldown)

            plugin.server.scheduler.scheduleSyncDelayedTask(plugin, {
                blessingCooldown.remove(playerID)
            }, Abilities.BLESSING.cooldown.toLong())

            /** Play sound effect */
            player.world.playSound(player, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 3F, 1F)

            /** Give golden hearts if full hp */
            val maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.value ?: return
            if(player.health == maxHealth){
                player.addPotionEffect(PotionEffect(PotionEffectType.ABSORPTION, 120, 0))
                return
            }

            /** Heal player if not full hp */
            val healedHealth = player.health + plugin.levelScaling(playerID, Abilities.BLESSING.force)
            if(healedHealth >= maxHealth){
                player.health = maxHealth
            }else{
                player.health = healedHealth
            }
        }

        /** Pulls enemies towards the player */
        fun earthquake(plugin: Rylai, player: Player){
            val playerID = player.uniqueId

            /** Manage Cooldown */
            if(!earthquakeCooldown.add(playerID)){
                return
            }
            player.setCooldown(Abilities.EARTHQUAKE.icon, Abilities.EARTHQUAKE.cooldown)

            plugin.server.scheduler.scheduleSyncDelayedTask(plugin, {
                earthquakeCooldown.remove(playerID)
            }, Abilities.EARTHQUAKE.cooldown.toLong())

            /** Play sound & particles */
            player.world.playSound(player, Sound.ENTITY_GHAST_SHOOT, 2F, 2F)
            earthquakeParticles(plugin, player)

            /** Pull all entities in radius towards player */
            val playerPos = player.location
            val groupName = plugin.players[playerID]!!.group  
            val force = plugin.levelScaling(playerID, Abilities.EARTHQUAKE.force).toLong()
            for(entity in player.getNearbyEntities(5.0, 5.0, 5.0)){
                if(entity !is LivingEntity){
                    continue
                }

                /** Check if target is eligible */
                if(entity is Player) {
                    if(!plugin.playerIsValidTarget(groupName, entity)){
                        continue
                    }
                    plugin.silence(entity, force)
                }

                val vector = playerPos.toVector().subtract(entity.location.toVector())
                vector.y = 2.0
                entity.velocity = vector.normalize().multiply(0.69)

                if(entity is Monster){
                    entity.addPotionEffect(PotionEffect(PotionEffectType.WEAKNESS, 200, 1))
                    entity.target = player
                }
            }
        }

        /** Spawn earthquake particle waves */
        private fun earthquakeParticles(plugin: Plugin, player: Player){
            var block = player.location.block
            if(block.type == Material.AIR){
                val blockBelow = player.location
                blockBelow.y -= 1
                block = blockBelow.block
            }

            /** Single particle */
            fun particle(loc: Location, angle: Double, r: Int){
                loc.x += r * cos(angle)
                loc.z += r * sin(angle)
                player.world.spawnParticle(Particle.BLOCK_DUST, loc, 1,
                        0.0, 0.0, 0.0, 0.0, block.blockData)
            }

            /** One full circle */
            fun circle(t: Double, r: Int){
                var angle = 0.0
                while(angle < 2.0*Math.PI){
                    particle(player.location, angle, r)
                    angle += t
                }
            }

            /** Outer circle */
            circle(Math.PI/32, 5)

            /** Middle circle */
            plugin.server.scheduler.scheduleSyncDelayedTask(plugin, {
                circle(Math.PI/16, 3)
            }, 3)

            /** Inner circle */
            plugin.server.scheduler.scheduleSyncDelayedTask(plugin, {
                circle(Math.PI/8, 1)
            }, 6)
        }

        /** Buffs nearby allies speed */
        fun battlecry(plugin: Rylai, player: Player){
            val playerID = player.uniqueId

            /** Manage Cooldown */
            if(!battlecryCooldown.add(playerID)){
                return
            }
            player.setCooldown(Abilities.BATTLECRY.icon, Abilities.BATTLECRY.cooldown)

            plugin.server.scheduler.scheduleSyncDelayedTask(plugin, {
                battlecryCooldown.remove(playerID)
            }, Abilities.BATTLECRY.cooldown.toLong())

            val force = plugin.levelScaling(playerID, Abilities.BATTLECRY.force).toInt()
            player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, force, 0))

            /** Sound effect & Particles */
            val loc = player.eyeLocation
            player.world.spawnParticle(Particle.GLOW, loc, 20)
            player.world.playSound(player, Sound.ENTITY_POLAR_BEAR_WARNING, 2F, 0.9F)

            /** Buff teammates - if group is null, no need to look */
            val groupName = plugin.players[playerID]!!.group ?: return  
            for(entity in player.getNearbyEntities(15.0, 15.0, 15.0)){
                if(entity !is Player){
                    continue
                }
                if(groupName == plugin.players[entity.uniqueId]!!.group) {  
                    entity.addPotionEffect(PotionEffect(PotionEffectType.SPEED, force, 0))
                }
            }
        }
    }
}