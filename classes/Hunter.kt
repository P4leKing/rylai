package com.github.p4leking.rylai.classes

import com.github.p4leking.rylai.Rylai
import com.github.p4leking.rylai.utils.Abilities
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Creature
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitRunnable
import java.util.*

class Hunter {
    companion object {
        private val tumbleCooldown = mutableMapOf<UUID, Int>()
        private val ascensionCooldown = mutableSetOf<UUID>()
        private val anklebreakerCooldown = mutableSetOf<UUID>()
        private val tripmineCooldown = mutableSetOf<UUID>()
        val tumbleActive = mutableSetOf<UUID>()
        val ascensionActive = mutableSetOf<UUID>()
        val ascensionStacks = mutableMapOf<UUID, MutableMap<UUID, Int>>()

        /** Dash and short invisibility */
        fun tumble(plugin: Rylai, player: Player){
            val playerID = player.uniqueId

            /** Manage Cooldown */
            if(tumbleCooldown.contains(playerID)){
                return
            }

            tumbleCooldown[playerID] = plugin.server.scheduler.scheduleSyncDelayedTask(plugin, {
                tumbleCooldown.remove(playerID)
            }, Abilities.TUMBLE.cooldown.toLong())

            player.setCooldown(Abilities.TUMBLE.icon, Abilities.TUMBLE.cooldown)

            /** Horizontal dash */
            val dashVector = player.location
            dashVector.pitch = 0F
            player.velocity = dashVector.direction.multiply(1.0)

            /** Use elytra gliding for rolling animation */
            player.isGliding = true
            tumbleActive.add(playerID)

            plugin.server.scheduler.scheduleSyncDelayedTask(plugin, {
                player.isGliding = false
                tumbleActive.remove(playerID)
            }, 5)

            /** Reload Crossbow */
            if(player.hasCooldown(Material.CROSSBOW)){
                player.setCooldown(Material.CROSSBOW, 0)
            }

            /** Invisibility */
            Assassin.cloakPlayer(plugin, player, 20)
        }

        /** Makes the player deal bonus damage if he hits 3 consecutive shots on the same target in a short timeframe */
        fun ascension(plugin: Rylai, player: Player){
            val playerID = player.uniqueId

            /** Manage Cooldown */
            if(!ascensionCooldown.add(playerID)){
                return
            }
            player.setCooldown(Abilities.ASCENSION.icon, Abilities.ASCENSION.cooldown)

            plugin.server.scheduler.scheduleSyncDelayedTask(plugin, {
                ascensionCooldown.remove(playerID)
            }, Abilities.ASCENSION.cooldown.toLong())

            /** Ascension duration (10 sec makes 6 hits impossible but gives the player the freedom
             * of using the resets to compensate for misses) - Change duration if crossbow attack speed gets changed */
            ascensionActive.add(playerID)
            plugin.server.scheduler.scheduleSyncDelayedTask(plugin, {
                ascensionActive.remove(playerID)
                ascensionStacks.remove(playerID)
            }, plugin.levelScaling(playerID, Abilities.ASCENSION.force).toLong())

            /** Cancel old cooldown duration */
            val oldTask = tumbleCooldown[playerID]
            if(oldTask != null){
                plugin.server.scheduler.cancelTask(oldTask)
            }

            /** Set new shortened tumble cooldown */
            val newCooldown = player.getCooldown(Abilities.TUMBLE.icon) / 2

            tumbleCooldown[playerID] = plugin.server.scheduler.scheduleSyncDelayedTask(plugin, {
                tumbleCooldown.remove(playerID)
            }, newCooldown.toLong())

            player.setCooldown(Abilities.TUMBLE.icon, newCooldown)

            /** Starting sound */
            player.world.playSound(player, Sound.ENTITY_WITHER_SPAWN, 3F, 1F)

            /** Particles */
            object: BukkitRunnable() {
                override fun run() {
                    if(!ascensionActive.contains(playerID)){
                        cancel()
                    }
                    if(!Assassin.invisiblePlayers.contains(playerID)){
                        val loc = player.location
                        loc.y += 1
                        player.world.spawnParticle(Particle.LAVA, loc, 5)
                    }
                }
            }.runTaskTimer(plugin,0,15)
        }

        /** Arrow to the knee (anklebreaker): stab nearby players kneecaps with an arrow, slowing them down and reload your crossbow afterwards */
        fun anklebreaker(plugin: Rylai, player: Player){
            val playerID = player.uniqueId

            /** Manage Cooldown */
            if(!anklebreakerCooldown.add(playerID)){
                return
            }
            player.setCooldown(Abilities.ANKLEBREAKER.icon, Abilities.ANKLEBREAKER.cooldown)

            plugin.server.scheduler.scheduleSyncDelayedTask(plugin, {
                anklebreakerCooldown.remove(playerID)
            }, Abilities.ANKLEBREAKER.cooldown.toLong())

            /** Ability cast */
            val playerHeadLocation = player.eyeLocation
            val areaOfEffect = playerHeadLocation.add(playerHeadLocation.direction)
            val group = plugin.players[playerID]!!.group  

            val dmg = plugin.levelScaling(playerID, Abilities.ANKLEBREAKER.force)
            for(entity in areaOfEffect.getNearbyEntities(2.5, 2.5, 2.5)){
                if(entity !is LivingEntity){
                    return
                }

                if(entity !is Creature && entity !is Player) {
                    continue
                }

                /** Necessary because of location.getNearbyEntities instead of player.getNearbyEntities */
                if(entity == player){
                    continue
                }

                /** Damage */
                entity.damage(dmg, player)

                /** Slow */
                if(entity is Player){
                    if(plugin.playerIsValidTarget(group, entity)){
                        entity.addPotionEffect(PotionEffect(PotionEffectType.SLOW, 60, 1))
                    }
                }else{
                    entity.addPotionEffect(PotionEffect(PotionEffectType.SLOW, 100, 2))
                }
            }

            /** Animation & sound */
            player.world.playSound(player, Sound.ITEM_CROSSBOW_SHOOT, 3F, 1F)
            player.world.spawnParticle(Particle.SWEEP_ATTACK, playerHeadLocation, 1)

            /** Reload Crossbow */
            if(player.hasCooldown(Material.CROSSBOW)){
                player.setCooldown(Material.CROSSBOW, 0)
            }
        }

        /** Place a trip mine that poisons the first player to step on it. */
        fun tripmine(plugin: Rylai, player: Player){
            val playerID = player.uniqueId

            /** Manage Cooldown */
            if(!tripmineCooldown.add(playerID)){
                return
            }
            player.setCooldown(Abilities.TRIPMINE.icon, Abilities.TRIPMINE.cooldown)

            plugin.server.scheduler.scheduleSyncDelayedTask(plugin, {
                tripmineCooldown.remove(playerID)
            }, Abilities.TRIPMINE.cooldown.toLong())

            /** Place tripmine */
            val loc = player.location
            loc.y += 0.25

            /** Placing sound effect */
            loc.world.playSound(loc, Sound.ITEM_CROSSBOW_LOADING_START, 0.75F, 1F)

            val group = plugin.players[playerID]!!.group
            val tripmineEffectDuration = plugin.levelScaling(playerID, Abilities.TRIPMINE.force).toInt()

            var i = 30
            object: BukkitRunnable() {
                override fun run() {

                    /** Remove tripmine after 10 seconds */
                    if(i >= 200){
                        cancel()
                    }
                    if(i == 30){
                        /** Placing sound effect */
                        loc.world.playSound(loc, Sound.ITEM_CROSSBOW_LOADING_END, 0.75F, 1F)
                    }
                    i += 3

                    /** Hard to see indicator */
                    loc.world.spawnParticle(Particle.ASH, loc, 2)

                    /** Trigger tripmine if something stepped on it */
                    var valid = false
                    for(entity in loc.getNearbyEntities(3.0, 3.0, 3.0)){

                        /** Filter invalid targets */
                        if(entity !is Creature && entity !is Player){
                            continue
                        }
                        if(entity.uniqueId == playerID){
                            continue
                        }

                        /** Blind hit players and damage and slow hit monsters */
                        if(entity is Player){
                            if(!plugin.playerIsValidTarget(group, entity)){
                                continue
                            }
                            entity.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, tripmineEffectDuration, 0))
                            plugin.silence(entity, tripmineEffectDuration.toLong())
                        }else{
                            (entity as Creature).addPotionEffect(PotionEffect(PotionEffectType.SLOW, tripmineEffectDuration * 2, 1))
                            entity.damage(8.0)
                        }

                        valid = true
                    }

                    /** Play sound and remove trap on hit */
                    if(valid){
                        loc.world.playSound(loc, Sound.ENTITY_ARROW_HIT, 1F, 1F)
                        loc.world.spawnParticle(Particle.EXPLOSION_LARGE, loc, 1)
                        cancel()
                    }
                }
            }.runTaskTimer(plugin,30,4)
        }
    }
}