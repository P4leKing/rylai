package com.github.p4leking.rylai.handlers

import com.github.p4leking.rylai.Rylai
import com.github.p4leking.rylai.classes.Mage
import com.github.p4leking.rylai.utils.Abilities
import com.github.p4leking.rylai.utils.Classes
import net.kyori.adventure.text.TextComponent
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.Tag
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.FallingBlock
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.LlamaSpit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityChangeBlockEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerToggleFlightEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.Damageable
import org.bukkit.scheduler.BukkitRunnable
import java.util.*

class MageHandler(private val plugin: Rylai) : Listener {
    private val wandCooldown = mutableSetOf<UUID>()
    private val spitDamage = mutableMapOf<UUID, Int>()
    private val wandCooldownTime = 80

    init {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    @EventHandler
    fun wandSpellCast(event: PlayerInteractEvent){
        if(event.action != Action.RIGHT_CLICK_BLOCK && event.action != Action.RIGHT_CLICK_AIR){
            return
        }

        /** Check if cast was valid */
        val player = event.player
        val wand = player.inventory.itemInMainHand
        if(wand.type != Material.DIAMOND_AXE) return

        val type = event.clickedBlock?.type
        if(type?.isInteractable == true && !(Tag.STAIRS.isTagged(type)/* || Tag.FENCES.isTagged(type)*/)){
            return
        }

        if(!((wand.lore() ?: return)[0] as? TextComponent ?: return).content().endsWith(Classes.MAGE.name)){
            return
        }

        /** Check class */
        if(plugin.players[player.uniqueId]!!.selectedClass != Classes.MAGE.name){
            player.sendMessage("Only mages can use a wand.")
            return
        }

        event.isCancelled = true
        wandSpell(player, wand)
    }

    @EventHandler
    fun entityClickSpellCast(event: PlayerInteractEntityEvent){
        val player = event.player
        val wand = player.inventory.itemInMainHand
        if(wand.type != Material.DIAMOND_AXE) return

        if(!((wand.lore() ?: return)[0] as? TextComponent ?: return).content().endsWith(Classes.MAGE.name)){
            return
        }

        /** Check class */
        if(plugin.players[player.uniqueId]!!.selectedClass != Classes.MAGE.name){
            player.sendMessage("Only mages can use a wand.")
            return
        }

        wandSpell(player, wand)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun wandSpellDamage(event: EntityDamageByEntityEvent){
        if(event.cause != EntityDamageEvent.DamageCause.PROJECTILE) {
            return
        }
        val proj = event.damager as? LlamaSpit ?: return
        if (proj.shooter !is Player) return
        event.damage = 9.0 + (spitDamage[proj.uniqueId] ?: 0)
    }

    /** Casts the wand spell (currently just one for all wands) */
    private fun wandSpell(player: Player, wand: ItemStack){

        /** Don't fire if silenced */
        if(plugin.isSilenced(player.uniqueId)) return

        /** Manage Cooldown */
        if(!wandCooldown.add(player.uniqueId)) return
        player.setCooldown(Material.DIAMOND_AXE, wandCooldownTime)

        plugin.server.scheduler.scheduleSyncDelayedTask(plugin, {
            wandCooldown.remove(player.uniqueId)
        }, wandCooldownTime.toLong())


        /** Cast spell and scale projectile damage with power level */
        val spit = player.launchProjectile(LlamaSpit::class.java, player.location.direction.multiply(3))
        spitDamage[spit.uniqueId] = wand.getEnchantmentLevel(Enchantment.ARROW_DAMAGE)

        /** Subtract durability */
        val meta = wand.itemMeta
        val unbreaking = meta.getEnchantLevel(Enchantment.DURABILITY)
        if(unbreaking == 0 || (0..unbreaking).random() == 0){
            (meta as Damageable).damage += 1
            wand.itemMeta = meta
        }

        player.world.playSound(player, Sound.ENTITY_LLAMA_SPIT, 3F, 1F)
    }

    @EventHandler
    fun onDeath(event: PlayerDeathEvent){
        Mage.distortionReturn.remove(event.player.uniqueId)
    }

    @EventHandler
    fun onFrozenToggle(event: PlayerToggleFlightEvent){
        if(Mage.frozenPlayers.contains(event.player.uniqueId)){
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onMeteorImpact(event: EntityChangeBlockEvent){
        /** Only look at meteor blocks */
        val block = event.entity
        if(block !is FallingBlock || block.blockData.material != Material.OBSIDIAN){
            return
        }

        /** Prevent the block actually appearing */
        event.isCancelled = true

        /** Boom */
        val playerID = Mage.meteorBlocks[block.uniqueId] ?: return
        val player = plugin.server.getPlayer(playerID) ?: return
        val loc = event.block.location
        val dmg = plugin.levelScaling(playerID, Abilities.METEOR.force)
        for(target in loc.getNearbyEntities(4.0,4.0,4.0)){
            if(target !is LivingEntity) continue

            if(target != player){
                target.damage(dmg, player)
            }
        }

        player.world.spawnParticle(Particle.EXPLOSION_HUGE, loc, 2)
        player.world.playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 3F, 3.5F)
        player.world.playSound(loc, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 3F, 0.69F)

        /** Create reset indicator */
        player.sendBlockChange(loc, Material.OBSIDIAN.createBlockData())

        /** Runnable with nearby player detection on meteor location & maximum duration */
        var i = 0
        object: BukkitRunnable() {
            override fun run() {
                for(entity in loc.getNearbyEntities(3.0,3.0,3.0)){
                    if(entity.uniqueId != playerID) continue

                    /** Remove block indicator */
                    player.sendBlockChange(loc, event.block.blockData)

                    /** Cancel old cooldown task */
                    val task = Mage.meteorCooldown[playerID]
                    if(task != null){
                        plugin.server.scheduler.cancelTask(task)
                    }

                    /** Set halved cooldown duration */
                    val cd = player.getCooldown(Abilities.METEOR.icon) / 2
                    player.setCooldown(Abilities.METEOR.icon, cd)
                    Mage.meteorCooldown[playerID] = plugin.server.scheduler.scheduleSyncDelayedTask(plugin, {
                        Mage.meteorCooldown.remove(playerID)
                    }, cd.toLong())

                    cancel()
                }
                i++

                /** Timeframe for reset */
                if(i > 40){
                    player.sendBlockChange(loc, event.block.blockData)
                    cancel()
                }
            }
        }.runTaskTimer(plugin,1,2)
    }
}