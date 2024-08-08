package com.github.p4leking.rylai.handlers

import com.github.p4leking.rylai.Rylai
import com.github.p4leking.rylai.classes.Archer
import com.github.p4leking.rylai.utils.Abilities
import org.bukkit.*
import org.bukkit.block.Block
import org.bukkit.entity.Arrow
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityShootBowEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.*


class ArcherHandler(private val plugin: Rylai) : Listener {
    private val hookshotArrow = mutableMapOf<UUID, UUID>()
    private val netshotArrow = mutableSetOf<UUID>()
    private val markArrow = mutableSetOf<UUID>()
    private val markedTargets = mutableMapOf<UUID, UUID>()

    init {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    /** Add arrow to list and remove buff if there was one */
    @EventHandler
    fun specialArrows(event: EntityShootBowEvent){
        if(event.entity !is Player){
            return
        }
        if(event.bow?.type != Material.BOW){
            return
        }

        val playerID = event.entity.uniqueId

        /** Hookshot fired */
        if(Archer.hookshotActive.remove(playerID)){
            hookshotArrow[event.projectile.uniqueId] = playerID

            /** Give the arrow "more gravity" */
            event.projectile.velocity = event.projectile.velocity.multiply(0.60)
            return
        }

        /** Netshot fired */
        if(Archer.netshotActive.remove(playerID)){
            netshotArrow += event.projectile.uniqueId

            /** Play Sound */
            event.entity.world.playSound(event.entity, Sound.ENTITY_SPIDER_HURT, 4F, 1F)
            return
        }

        /** Mark fired */
        if(Archer.markActive.remove(playerID)){
            markArrow += event.projectile.uniqueId
        }
    }

    /** Teleport target to player on hookshot hit */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun hookshotPull(event: EntityDamageByEntityEvent){
        if(event.damager !is Arrow){
            return
        }

        val player = (event.damager as Arrow).shooter
        if(player !is Player){
            return
        }

        val arrowID = event.damager.uniqueId

        /** Hookshot deals damage to an entity */
        if(hookshotArrow.remove(arrowID) == null){
            return
        }

        val target = event.entity
        if(target is LivingEntity){
            val teleportLocation = player.location
            teleportLocation.add(player.location.direction.setY(0).multiply(2))

            /** Make player face target */
            teleportLocation.yaw = teleportLocation.yaw + 180
            val blockAboveTarget = teleportLocation.clone()
            blockAboveTarget.y += 1
            if(teleportLocation.block.isPassable || blockAboveTarget.block.isPassable) {
                target.teleport(teleportLocation)
            } else {
                target.teleport(player.location)
            }

            /** Sound effect for hooking someone */
            player.world.playSound(target, Sound.ENTITY_VILLAGER_YES, 3F, 1F)
        }
    }

    /** Boost damage if target is marked */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun damageBoosts(event: EntityDamageByEntityEvent){
        /** Hunter's Mark boost */
        val mark = markedTargets[event.entity.uniqueId]
        if(mark != null){
            event.damage = event.damage * 1.2
        }

        /** Only boost arrows */
        if(event.damager !is Arrow){
            return
        }

        val shooter = (event.damager as Arrow).shooter
        if(shooter !is Player){
            return
        }

        /** Increased bonus damage for archer himself */
        if(mark == shooter.uniqueId){
            event.damage = event.damage * 1.1
        }

        /** Backflip boost */
        if(Archer.flipActive.contains(shooter.uniqueId)){
            Archer.flipActive.remove(shooter.uniqueId)
            event.damage = event.damage * (1.0 + plugin.levelScaling(shooter.uniqueId, Abilities.BACKFLIP.force))
        }
    }

    /** Handle special arrow hits */
    @EventHandler
    fun abilityHitBlockOrEntity(event: ProjectileHitEvent){
        if(event.entity !is Arrow){
            return
        }

        val player = event.entity.shooter
        if(player !is Player) {
            return
        }

        val arrowID = event.entity.uniqueId

        /** Net trap */
        if(netshotArrow.remove(arrowID)){
            val loc = if(event.hitBlock != null){
                event.hitBlock!!.location
            }else{
                event.hitEntity?.location ?:return
            }

            //TODO if loc is safe zone, don't build it

            /** Build net trap */
            val edge1 = loc.clone().set(loc.x-1, loc.y, loc.z-1)
            val edge2 = loc.clone().set(loc.x+1, loc.y+2, loc.z+1)
            val blocks = buildNetTrap(edge1, edge2)

            /** Remove net trap after duration */
            plugin.server.scheduler.scheduleSyncDelayedTask(plugin, {
                removeNetTrap(blocks)
            }, plugin.levelScaling(player.uniqueId, Abilities.NETSHOT.force).toLong())
            return
        }

        /** Hunter's mark */
        if(markArrow.remove(arrowID)){
            /** Create particles & sound */
            val loc = if(event.hitBlock != null){
                event.hitBlock!!.location.toCenterLocation()
            }else{
                event.hitEntity?.location ?:return
            }
            loc.y += 1

            player.world.spawnParticle(Particle.GLOW_SQUID_INK, loc, 100)
            player.world.playSound(loc, Sound.ENTITY_PHANTOM_DEATH, 1.5F, 1F)

            /** Create set of all marked targets */
            val targets = mutableSetOf<UUID>()
            val group = plugin.players[player.uniqueId]?.group
            val duration = plugin.levelScaling(player.uniqueId, Abilities.MARK.force)
            for(entity in loc.getNearbyEntities(3.5,3.5,3.5)){
                if(entity !is LivingEntity) {
                    continue
                }
                if(entity is Player && (entity.uniqueId == player.uniqueId || !plugin.playerIsValidTarget(group, entity))) {
                    continue
                }

                /** Mark target */
                entity.addPotionEffect(PotionEffect(PotionEffectType.GLOWING, duration.toInt(), 1))
                markedTargets[entity.uniqueId] = player.uniqueId
                targets += entity.uniqueId
            }

            /** Delete targets after duration ends */
            if(targets.isNotEmpty()){
                plugin.server.scheduler.scheduleSyncDelayedTask(plugin, {
                    for(target in targets){
                        markedTargets.remove(target)
                    }
                }, duration.toLong())
            }
            return
        }

        /** Handle Hookshot */
        if(hookshotArrow.remove(arrowID) == null){
            return
        }

        /** Make sure Player hit terrain */
        if(event.hitBlock == null) {
            return
        }

        /** Don't teleport player if he was silenced */
        if(plugin.isSilenced(player.uniqueId)){
            player.sendMessage("${ChatColor.DARK_RED}You could not teleport away because you are silenced.")
            return
        }

        /** Teleport player to arrow */
        val teleportLocation = event.entity.location
        teleportLocation.direction = player.location.direction
        player.teleport(teleportLocation)

        /** Sound effect for teleporting away */
        player.world.playSound(player, Sound.ENTITY_CHICKEN_HURT, 5F, 1F)
    }

    private fun getRegionBlocks(loc1: Location, loc2: Location): List<Block> {
        val blocks: MutableList<Block> = ArrayList<Block>()
        val world = loc1.world
        var x = loc1.x
        while (x <= loc2.x) {
            var y = loc1.y
            while (y <= loc2.y) {
                var z = loc1.z
                while (z <= loc2.z) {
                    val loc = Location(world, x, y, z)
                    blocks.add(loc.block)
                    z++
                }
                y++
            }
            x++
        }
        return blocks
    }

    private fun buildNetTrap(edge1: Location, edge2: Location): List<Block> {
        val blocks = getRegionBlocks(edge1, edge2)
        for(block in blocks){
            if(block.isEmpty || block.type == Material.GRASS || block.isLiquid){
                block.type = Material.COBWEB
            }
        }
        return blocks
    }

    private fun removeNetTrap(blocks: List<Block>){
        for(block in blocks){
            if(block.type == Material.COBWEB){
                block.type = Material.AIR
            }
        }
    }

    @EventHandler
    fun onDeath(event: PlayerDeathEvent){
        with(hookshotArrow.iterator()) {
            forEach {
                if (it.value == event.player.uniqueId) { remove() }
            }
        }
    }
}