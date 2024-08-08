/** © 2024 Bernhard Eierle. All rights reserved. */

package com.github.p4leking.rylai.classes

import com.comphenix.protocol.PacketType
import com.comphenix.protocol.wrappers.EnumWrappers
import com.comphenix.protocol.wrappers.Pair
import com.github.p4leking.rylai.Rylai
import com.github.p4leking.rylai.utils.Abilities
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.entity.*
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitRunnable
import java.util.*

class Assassin {
    companion object {
        private val bladestormResets = mutableMapOf<UUID, MutableSet<UUID>>()
        private val bladestormCooldown = mutableSetOf<UUID>()
        private val invisibilityCooldown = mutableSetOf<UUID>()
        val invisiblePlayers = mutableSetOf<UUID>()
        private val deathmarkCooldown = mutableSetOf<UUID>()
        private val deathmarkTarget = mutableMapOf<UUID, UUID>()
        val deathmarkArrow = mutableSetOf<UUID>()
        val eviscerateCooldown = mutableMapOf<UUID, Int>()
        val eviscerateActive = mutableMapOf<UUID, Int>()

        /** Lets the player dash a fixed distance, deals damage and resets cooldown if a new entity was hit.
         * Doesn't reset cooldown for hits done after players velocity went back to normal */
        fun bladestorm(plugin: Rylai, player: Player){
            val playerID = player.uniqueId

            /** Checking for cooldown & hidden cooldown & setting cooldown to prevent spamming */
            if(!bladestormCooldown.add(playerID)){
                return
            }

            /** Dash towards player cross-hair */
            player.velocity = player.location.direction.multiply(1.3)

            /** Sound Effect */
            player.world.playSound(player, Sound.ENTITY_GHAST_SHOOT, 1.5F, 1F)

            /** Deal damage while dashing and handle cooldown */
            var gotReset = false
            val wasHit = arrayListOf<UUID>()
            val damage = plugin.levelScaling(playerID, Abilities.BLADESTORM.force)
            val group = plugin.players[playerID]!!.group

            object: BukkitRunnable() {
                override fun run() {
                    /** Was high enough to stop it even with speed 2 */
                    if (player.velocity.lengthSquared() < 0.2){
                        if(gotReset){
                            /** Player got reset */
                            bladestormCooldown.remove(playerID)
                        }else{
                            /** Player didn't get reset */
                            player.setCooldown(Abilities.BLADESTORM.icon, Abilities.BLADESTORM.cooldown)
                            plugin.server.scheduler.scheduleSyncDelayedTask(plugin, {
                                bladestormCooldown.remove(playerID)
                            }, Abilities.BLADESTORM.cooldown.toLong())
                        }
                        cancel()
                    }

                    if(dmgNearbyEntities(plugin, player, damage, group, gotReset, wasHit)){
                        gotReset = true
                    }
                }
            }.runTaskTimer(plugin,0,2)
        }

        /** Damage instances for dash, returns if unit was hit */
        private fun dmgNearbyEntities(plugin: Rylai, player: Player, damage: Double, group: String?, reset: Boolean, wasHit: ArrayList<UUID>): Boolean{
            val nearby = player.getNearbyEntities(1.75,2.0,1.75)
            var gotReset = reset
            val playerID = player.uniqueId

            for(entity in nearby){
                if(entity !is LivingEntity){
                    continue
                }

                /** Check for valid target */
                if(entity !is Creature && entity !is Player){
                    continue
                }

                val entityID = entity.uniqueId
                if(entityID in wasHit){
                    continue
                }

                if(entity is Player && !plugin.playerIsValidTarget(group, entity)) {
                    continue
                }

                /** Calculate damage with player level */
                entity.damage(damage, player)
                wasHit.add(entityID)

                /** Check if this jump already scored a reset */
                if(gotReset){
                    continue
                }

                /** Check if target already gave a reset */
                if(!bladestormResets.containsKey(playerID)){
                    bladestormResets[playerID] = mutableSetOf(entityID)
                    gotReset = true

                    /** Set hidden cooldown for this entity */
                    plugin.server.scheduler.scheduleSyncDelayedTask(plugin, {
                        val resets = bladestormResets[playerID]!!  
                        resets.remove(entityID)
                        if(resets.isEmpty()){
                            bladestormResets.remove(playerID)
                        }
                    }, Abilities.BLADESTORM.cooldown * 2L)
                    continue
                }

                if(bladestormResets[playerID]!!.add(entityID)){
                    gotReset = true

                    /** Set hidden cooldown for this entity */
                    plugin.server.scheduler.scheduleSyncDelayedTask(plugin, {
                        val resets = bladestormResets[playerID]!!  
                        resets.remove(entityID)
                        if(resets.isEmpty()){
                            bladestormResets.remove(playerID)
                        }
                    }, Abilities.BLADESTORM.cooldown * 2L)
                }
            }
            return gotReset
        }

        /** Vanishes the player and removes current mob aggro,
         * gets cancelled when the player attacks an entity and
         * mob aggro range is reduced in this duration (by invis potion effect)*/
        fun invisibility(plugin: Rylai, player: Player){
            val playerID = player.uniqueId

            /** Cooldown */
            if(!invisibilityCooldown.add(playerID)){
                return
            }

            player.setCooldown(Abilities.INVISIBILITY.icon, Abilities.INVISIBILITY.cooldown)

            plugin.server.scheduler.scheduleSyncDelayedTask(plugin, {
                invisibilityCooldown.remove(playerID)
            }, Abilities.INVISIBILITY.cooldown.toLong())

            /** Regeneration 1 for duration of invisibility based on missing health (Max ~1.8 hearts) */
            val force = plugin.levelScaling(playerID, Abilities.INVISIBILITY.force).toInt()
            player.addPotionEffect(PotionEffect(PotionEffectType.REGENERATION,
                    (force * (1.0 - (player.health.toInt() / (player.getAttribute(Attribute.GENERIC_MAX_HEALTH)?.value ?: 20.0)))).toInt(),
                    1, false, false, false))

            /** Remove hunters mark glow */
            player.removePotionEffect(PotionEffectType.GLOWING)

            cloakPlayer(plugin, player, force)
        }

        /** Makes the player disappear */
        fun cloakPlayer(plugin: Rylai, player: Player, duration: Int){
            player.addPotionEffect(PotionEffect(PotionEffectType.INVISIBILITY, duration, 0, false, false, false))

            /** Make player equipment invisible to other players - must happen before packet lock */
            val packet = plugin.protocolManager.createPacket(PacketType.Play.Server.ENTITY_EQUIPMENT)
            packet.integers.write(0, player.entityId)

            val equipment = listOf<Pair<EnumWrappers.ItemSlot, ItemStack>>(
                    Pair(EnumWrappers.ItemSlot.MAINHAND, null),
                    Pair(EnumWrappers.ItemSlot.OFFHAND, null),
                    Pair(EnumWrappers.ItemSlot.FEET, null),
                    Pair(EnumWrappers.ItemSlot.LEGS, null),
                    Pair(EnumWrappers.ItemSlot.CHEST, null),
                    Pair(EnumWrappers.ItemSlot.HEAD, null)
            )
            packet.slotStackPairLists.write(0, equipment)

            for(p in plugin.server.onlinePlayers){
                if(p != player){
                    plugin.protocolManager.sendServerPacket(p, packet)
                }
            }

            /** Block other armor change packets & track invisibility */
            invisiblePlayers.add(player.uniqueId)

            /** Cancel invisibility after duration */
            plugin.server.scheduler.scheduleSyncDelayedTask(plugin, {
                cancelInvis(plugin, player)
            }, duration.toLong())

            /** Reset mob aggro (Invulnerable Toggle makes mobs that were hit recently lose aggro too) */
            player.isInvulnerable = true
            for(entity in player.getNearbyEntities(100.0,100.0,100.0)){
                if(entity is Monster){
                    if(entity.target == player) {
                        entity.target = null
                    }
                }
            }
            plugin.server.scheduler.scheduleSyncDelayedTask(plugin, {
                player.isInvulnerable = false
            }, 1)
        }

        /** Makes the player reappear */
        fun cancelInvis(plugin: Rylai, player: Player){
            if(!invisiblePlayers.remove(player.uniqueId)){
                return
            }

            player.removePotionEffect(PotionEffectType.INVISIBILITY)
            player.removePotionEffect(PotionEffectType.REGENERATION)

            /** Update player equipment for other players - must happen after packet lock */
            val packet = plugin.protocolManager.createPacket(PacketType.Play.Server.ENTITY_EQUIPMENT)
            packet.integers.write(0, player.entityId)
            val equipment = listOf<Pair<EnumWrappers.ItemSlot, ItemStack>>(
                    Pair(EnumWrappers.ItemSlot.MAINHAND, player.inventory.itemInMainHand),
                    Pair(EnumWrappers.ItemSlot.OFFHAND, player.inventory.itemInOffHand),
                    Pair(EnumWrappers.ItemSlot.FEET, player.inventory.boots),
                    Pair(EnumWrappers.ItemSlot.LEGS, player.inventory.leggings),
                    Pair(EnumWrappers.ItemSlot.CHEST, player.inventory.chestplate),
                    Pair(EnumWrappers.ItemSlot.HEAD, player.inventory.helmet)
            )
            packet.slotStackPairLists.write(0, equipment)
            for(p in plugin.server.onlinePlayers){
                /** Skip yourself */
                if(p == player){
                    continue
                }
                plugin.protocolManager.sendServerPacket(p, packet)
            }
        }

        /** Ability that makes the player shoot an arrow. If the arrow is hit,
         * the player can reactivate the ability for a certain time to teleport to the targets position. */
        fun deathmark(plugin: Rylai, player: Player){
            val playerID = player.uniqueId

            /** Checking for cooldown & hidden cooldown & setting cooldown to prevent spamming */
            if(!deathmarkCooldown.add(playerID)){
                return
            }

            /** Player has hit deathmark before and now jumps to enemy */
            if(deathmarkTarget.containsKey(playerID)){

                /** Just to be safe check for null value */
                if(deathmarkTarget[playerID] == null){
                    deathmarkTarget.remove(playerID)
                    startDeathmarkCooldown(plugin, player)
                    return
                }

                /** Jump to entity if exists & is not dead (for mobs) */
                val target = plugin.server.getEntity(deathmarkTarget[playerID]!!)
                if(target != null && target is LivingEntity && !target.isDead){
                    val direction = player.location.direction.setY(0)
                    val targetLocation = target.location.subtract(direction.multiply(2))
                    targetLocation.direction = direction

                    /** Checks if location is not in wall, & is reachable from the target position
                     * then teleports to location or on player if it wasn't valid */
                    val blockAboveTarget = targetLocation.clone()
                    blockAboveTarget.y += 1.0
                    if(targetLocation.block.isPassable && blockAboveTarget.block.isPassable && target.hasLineOfSight(targetLocation)){
                        player.teleport(targetLocation)
                    }else{
                        player.teleport(target.location)
                    }
                }
                deathmarkTarget.remove(playerID)
                startDeathmarkCooldown(plugin, player)
                return
            }

            /** Safety for bugged arrows that never hit something or arrows that get shot into the void
             * cooldown ends if normal cooldown hasn't started after the normal cooldown has almost ended.
             * (Does not do anything if triggered in reactivation period.) */
            plugin.server.scheduler.scheduleSyncDelayedTask(plugin, {
                if(!player.hasCooldown(Abilities.DEATHMARK.icon)){
                    deathmarkCooldown.remove(player.uniqueId)
                }
            }, (Abilities.DEATHMARK.cooldown-1).toLong())

            /** Shoot arrow projectile */
            val arrow = player.launchProjectile(Arrow::class.java, player.location.direction.multiply(4))
            arrow.damage = plugin.levelScaling(playerID, Abilities.DEATHMARK.force)
            deathmarkArrow.add(arrow.uniqueId)

            /** Sound Effect */
            player.world.playSound(player, Sound.ENTITY_BLAZE_DEATH, 3F, 1F)
        }

        /** Starts reactivation period and starts cooldown if time runs out before reactivation */
        fun onDeathmarkHit(plugin: Rylai, attacker: Player, target: UUID){
            cancelInvis(plugin, attacker)
            deathmarkCooldown.remove(attacker.uniqueId)
            deathmarkTarget[attacker.uniqueId] = target

            /** Start cooldown if the reactivation duration ran out
             * not if the target died and the player got a reset */
            plugin.server.scheduler.scheduleSyncDelayedTask(plugin, {
                if(!deathmarkCooldown.contains(attacker.uniqueId) && deathmarkTarget[attacker.uniqueId] == target){
                    deathmarkTarget.remove(attacker.uniqueId)
                    deathmarkCooldown.add(attacker.uniqueId)
                    startDeathmarkCooldown(plugin, attacker)
                }
            }, 100)
        }

        /** Starts Deathmark cooldown period */
        fun startDeathmarkCooldown(plugin: Plugin, player: Player){
            player.setCooldown(Abilities.DEATHMARK.icon, Abilities.DEATHMARK.cooldown)
            plugin.server.scheduler.scheduleSyncDelayedTask(plugin, {
                deathmarkCooldown.remove(player.uniqueId)
            }, Abilities.DEATHMARK.cooldown.toLong())
        }

        /** Removes any related deathmarks on player death */
        fun removeDeathmarkOnDeath(playerID: UUID){
            deathmarkTarget.remove(playerID)
            with(deathmarkTarget.iterator()) {
                forEach {
                    if (it.value == playerID) {
                        /** Player gets cooldown reset if the marked target dies before jumping to it */
                        remove()
                    }
                }
            }
        }

        /** Auto attack dmg boost with execute, Cooldown reset if player is hit from behind */
        fun eviscerate(plugin: Plugin, player: Player){
            val playerID = player.uniqueId

            /** Cooldown */
            if(eviscerateCooldown.contains(playerID)){
                return
            }

            eviscerateCooldown[playerID] = plugin.server.scheduler.scheduleSyncDelayedTask(plugin, {
                eviscerateCooldown.remove(playerID)
            }, Abilities.EVISCERATE.cooldown.toLong())

            player.setCooldown(Abilities.EVISCERATE.icon, Abilities.EVISCERATE.cooldown)

            /** Start damage boost duration */
            eviscerateActive[playerID] = plugin.server.scheduler.scheduleSyncDelayedTask(plugin, {
                eviscerateActive.remove(playerID)
            }, 100)
        }
    }
}