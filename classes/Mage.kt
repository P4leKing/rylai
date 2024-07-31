package com.github.p4leking.rylai.classes

import com.github.p4leking.rylai.Rylai
import com.github.p4leking.rylai.utils.Abilities
import org.bukkit.*
import org.bukkit.entity.Damageable
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.BlockIterator
import org.bukkit.util.Vector
import java.util.*
import kotlin.math.cos
import kotlin.math.sin

class Mage{
    companion object{
        private val dragonfireCooldown = mutableSetOf<UUID>()
        private val distortionCooldown = mutableSetOf<UUID>()
        private val stasisCooldown = mutableSetOf<UUID>()
        val meteorCooldown = mutableMapOf<UUID, Int>()
        val frozenPlayers = mutableSetOf<UUID>()
        val distortionReturn = mutableMapOf<UUID, Location>()
        val meteorBlocks = mutableMapOf<UUID, UUID>()

        fun dragonfire(plugin: Rylai, player: Player){
            val playerID = player.uniqueId

            /** Manage Cooldown */
            if(!dragonfireCooldown.add(playerID)){
                return
            }
            player.setCooldown(Abilities.DRAGONFIRE.icon, Abilities.DRAGONFIRE.cooldown)

            plugin.server.scheduler.scheduleSyncDelayedTask(plugin, {
                dragonfireCooldown.remove(playerID)
            }, Abilities.DRAGONFIRE.cooldown.toLong())

            /** Play sound */
            player.world.playSound(player, Sound.ENTITY_ENDER_DRAGON_GROWL, 4F, 1F)

            /** AOE Damage */
            val playerHeadLocation = player.eyeLocation
            var axis = playerHeadLocation.direction
            val areaClosest = playerHeadLocation.add(axis)
            val areaMiddle = areaClosest.clone().add(axis.multiply(4))
            val areaFurthest = areaMiddle.clone().add(axis)
            val damage = plugin.levelScaling(player.uniqueId, Abilities.DRAGONFIRE.force)
            dragonfireDamage(player, areaClosest, 1.25, damage)
            plugin.server.scheduler.scheduleSyncDelayedTask(plugin, {
                dragonfireDamage(player, areaMiddle, 2.25, damage)
            }, 4)
            plugin.server.scheduler.scheduleSyncDelayedTask(plugin, {
                dragonfireDamage(player, areaFurthest, 3.25, damage)
            }, 10)

            /** Particles */
            var t = 0.0
            var r = 1.0
            /** Unit vector orthogonal to player direction */
            val orthogonalVector1 = playerHeadLocation.clone()
            orthogonalVector1.pitch += 90F
            val v1 = orthogonalVector1.direction.normalize()

            /** Unit vector orthogonal to both previous vectors */
            val v2 = playerHeadLocation.direction.crossProduct(v1).normalize()
            val step = Math.PI/16

            /** Vector for direction and distance of helix */
            axis = playerHeadLocation.direction.multiply(12)

            /** Spawn particles */
            object: BukkitRunnable() {

                /** One particle in the helix */
                private fun spawnParticle(){
                    r += 0.025
                    t += step
                    dragonfireParticle(player, playerHeadLocation, axis, v1, v2, t, r)
                }

                /** Particles generated within that game-tick */
                override fun run(){
                    /** Helix particles */
                    spawnParticle()
                    spawnParticle()
                    spawnParticle()
                    spawnParticle()
                    spawnParticle()
                    spawnParticle()
                    spawnParticle()
                    spawnParticle()
                    spawnParticle()
                    spawnParticle()

                    /** Middle particle */
                    player.world.spawnParticle(Particle.FLAME,
                            playerHeadLocation.clone().add(playerHeadLocation.direction.multiply(t/(Math.PI))),
                            1, 0.0, 0.0, 0.0, 0.0)

                    /** Stop after desired amount of Loops */
                    if(t > Math.PI*10){
                        cancel()
                    }
                }
            }.runTaskTimer(plugin,0,1)
        }

        /** Generates particles for dragonfire skill */
        private fun dragonfireParticle(player: Player, loc: Location, axis: Vector, v1: Vector,
                                       v2: Vector, t: Double, r: Double){
            /** Calculate relative position */
            val step = Math.PI/16
            val rCosT = r * cos(t)
            val rSinT = r * sin(t)
            val alpha = (t*step)/(2*Math.PI)
            val x = rCosT * v1.x + rSinT * v2.x + alpha * axis.x
            val y = rCosT * v1.y + rSinT * v2.y + alpha * axis.y
            val z = rCosT * v1.z + rSinT * v2.z + alpha * axis.z
            loc.add(x, y, z)

            /** Spawn particle */
            player.world.spawnParticle(Particle.FLAME, loc, 1, 0.0, 0.0, 0.0, 0.0)
            loc.subtract(x, y, z)
        }

        /** Does damage in the defined areas */
        private fun dragonfireDamage(player: Player, loc: Location, radius: Double, damage: Double){
            for(entity in loc.getNearbyEntities(radius, radius, radius)){
                if(entity !is Damageable) {
                    continue
                }
                /** Necessary because of location.getNearbyEntities instead of player.getNearbyEntities */
                if(entity != player){
                    entity.damage(damage, player)
                }
            }
        }


        fun distortion(plugin: Rylai, player: Player){
            val playerID = player.uniqueId

            /** Manage Cooldown */
            if(distortionCooldown.contains(playerID)){
                return
            }else if(distortionReturn.containsKey(playerID)) {
                /** Play sound */
                player.world.playSound(player, Sound.ENTITY_EVOKER_PREPARE_WOLOLO, 1.5F, 1F)

                /** Jump to saved location */
                val loc = distortionReturn[playerID]
                if(loc is Location){
                    player.teleport(loc)
                }
                distortionReturn.remove(playerID)
                startDistortionCooldown(plugin, player)
                return
            }

            /** Calculate teleport location */
            val blockIterator = BlockIterator(player, 16)
            var teleportLocation = player.location
            while(blockIterator.hasNext()){
                val nextBlock = blockIterator.next()
                val aboveNext = nextBlock.location.clone()
                aboveNext.y += 1
                val belowNext = nextBlock.location.clone()
                belowNext.y -= 1
                if(nextBlock.isPassable && (aboveNext.block.isPassable || belowNext.block.isPassable)){
                    teleportLocation =
                            if(aboveNext.block.isPassable){
                                nextBlock.location
                            }else{
                                belowNext
                            }
                }else{
                    break
                }
            }

            /** Save original location for jump back */
            distortionReturn[playerID] = player.location

            /** Teleport player */
            teleportLocation.direction = player.location.direction
            player.teleport(teleportLocation)

            /** Sound and animation */
            player.world.playSound(player, Sound.ENTITY_ENDERMAN_TELEPORT, 1.5F, 1F)
            player.world.spawnParticle(Particle.TOTEM, player.location, 100)

            /** Damage */
            val dmg = plugin.levelScaling(playerID, Abilities.DISTORTION.force)
            for(entity in player.getNearbyEntities(4.0,4.0,4.0)){
                if(entity !is LivingEntity){
                    continue
                }
                entity.damage(dmg, player)
            }

            /** Reactivation duration */
            plugin.server.scheduler.scheduleSyncDelayedTask(plugin, {
                if(!distortionCooldown.contains(playerID)){
                    distortionReturn.remove(playerID)
                    startDistortionCooldown(plugin, player)
                }
            }, 100)
        }

        /** Starts distortion cooldown period */
        private fun startDistortionCooldown(plugin: Plugin, player: Player){
            distortionCooldown.add(player.uniqueId)
            player.setCooldown(Abilities.DISTORTION.icon, Abilities.DISTORTION.cooldown)

            plugin.server.scheduler.scheduleSyncDelayedTask(plugin, {
                distortionCooldown.remove(player.uniqueId)
            }, Abilities.DISTORTION.cooldown.toLong())
        }

        /** Turns the player invulnerable but also leaves them unable to act for a short duration */
        fun stasis(plugin: Rylai, player: Player){
            val playerID = player.uniqueId

            /** Manage Cooldown */
            if(!stasisCooldown.add(playerID)){
                return
            }
            player.setCooldown(Abilities.STASIS.icon, Abilities.STASIS.cooldown)

            plugin.server.scheduler.scheduleSyncDelayedTask(plugin, {
                stasisCooldown.remove(playerID)
            }, Abilities.STASIS.cooldown.toLong())

            /** Prevent disabled fly in creative mode */
            if(player.gameMode != GameMode.SURVIVAL){
                return
            }

            /** Prevent sliding */
            val loc = player.location.toBlockLocation().add(0.5,0.1,0.5)
            var ring = false

            val ice1 = loc.clone().add(1.0,0.0,0.0)
            val ice2 = loc.clone().add(-1.0,0.0,0.0)
            val ice3 = loc.clone().add(0.0,0.0,1.0)
            val ice4 = loc.clone().add(0.0,0.0,-1.0)

            val locBelow = loc.clone()
            locBelow.y -= 1
            if(!locBelow.block.isPassable){
                ring = true
                val ice = Material.ICE.createBlockData()
                player.sendBlockChange(ice1, ice)
                player.sendBlockChange(ice2, ice)
                player.sendBlockChange(ice3, ice)
                player.sendBlockChange(ice4, ice)
            }

            /** Freeze player */
            frozenPlayers.add(playerID)
            player.allowFlight = true
            player.teleport(loc)
            player.flySpeed = 0F
            player.isFlying = true
            player.isInvulnerable = true

            plugin.silence(player, 40)

            /** Real block pillar, because fake one is buggy... */
            val loc2 = loc.clone()
            loc2.y += 1

            //TODO skip in safezones

            val type1 = loc.block.type
            val type2 = loc2.block.type

            loc.block.type = Material.POWDER_SNOW
            loc2.block.type = Material.POWDER_SNOW

            /** Sound */
            player.world.playSound(player, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 2F, 1F)

            /** Unfreeze player after duration */
            plugin.server.scheduler.scheduleSyncDelayedTask(plugin, {
                unfreeze(plugin, player) //this player object works even if they log off during cast, but playerQuitEvent doesn't xD

                /** Remove snow blocks */
                if(type1 != Material.COBWEB){
                    loc.block.type = type1
                }else{
                    loc.block.type = Material.AIR
                }
                if(type2 != Material.COBWEB){
                    loc2.block.type = type2
                }else{
                    loc2.block.type = Material.AIR
                }

                if(ring){
                    player.sendBlockChange(ice1, ice1.block.blockData)
                    player.sendBlockChange(ice2, ice2.block.blockData)
                    player.sendBlockChange(ice3, ice3.block.blockData)
                    player.sendBlockChange(ice4, ice4.block.blockData)
                }
            }, 40)
        }

        private fun unfreeze(plugin: Rylai, player: Player){
            player.allowFlight = false
            player.isInvulnerable = false
            player.isFlying = false
            player.flySpeed = 0.1F
            player.freezeTicks = 0

            /** Damage nearby entities */
            val dmg = plugin.levelScaling(player.uniqueId, Abilities.STASIS.force)
            for(entity in player.getNearbyEntities(2.5, 2.5, 2.5)){
                if(entity !is LivingEntity){
                    continue
                }
                entity.damage(dmg, player)
            }

            frozenPlayers.remove(player.uniqueId)
        }

        fun meteor(plugin: Plugin, player: Player){
            val playerID = player.uniqueId

            /** Manage Cooldown */
            if(meteorCooldown.contains(playerID)){
                return
            }

            meteorCooldown[playerID] = plugin.server.scheduler.scheduleSyncDelayedTask(plugin, {
                meteorCooldown.remove(playerID)
            }, Abilities.METEOR.cooldown.toLong())

            player.setCooldown(Abilities.METEOR.icon, Abilities.METEOR.cooldown)

            /** Calculate target location */
            val blockIterator = BlockIterator(player, 16)
            var blockLoc = player.location
            while(blockIterator.hasNext()){
                val nextBlock = blockIterator.next()
                if(nextBlock.isPassable){
                    blockLoc = nextBlock.location
                }else{
                    break
                }
            }

            /** Spawn falling block */
            blockLoc.y += 10
            blockLoc = blockLoc.toCenterLocation()
            val block = blockLoc.world.spawnFallingBlock(blockLoc, Material.OBSIDIAN.createBlockData())

            block.setHurtEntities(false)
            block.dropItem = false
            meteorBlocks[block.uniqueId] = playerID

            /** Remove data here to prevent memory leaks if event doesn't fire */
            plugin.server.scheduler.scheduleSyncDelayedTask(plugin, {
                meteorBlocks.remove(block.uniqueId)
                block.remove()
            }, 150)
        }
    }
}