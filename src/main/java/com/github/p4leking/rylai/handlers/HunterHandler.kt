/** © 2024 Bernhard Eierle. All rights reserved. */

package com.github.p4leking.rylai.handlers

import com.github.p4leking.rylai.Rylai
import com.github.p4leking.rylai.classes.Hunter
import com.github.p4leking.rylai.utils.Classes
import io.papermc.paper.event.entity.EntityLoadCrossbowEvent
import net.kyori.adventure.text.TextComponent
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Arrow
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityShootBowEvent
import org.bukkit.event.entity.EntityToggleGlideEvent
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.CrossbowMeta
import org.bukkit.inventory.meta.Damageable
import java.util.*

class HunterHandler(plugin: Rylai) : Listener {
    private val quickChargeLevel = mutableMapOf<UUID, Int>()

    init {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    @EventHandler
    fun crossbowCooldown(event: EntityShootBowEvent){
        val crossbow = event.bow ?: return
        if(crossbow.type != Material.CROSSBOW) return

        val player = event.entity
        if(player !is Player) return
        if(!crossbow.hasItemMeta()) return

        /** Reload crossbow */
        val meta = crossbow.itemMeta
        if(meta !is CrossbowMeta) return

        /** Subtract durability */
        val unbreaking = meta.getEnchantLevel(Enchantment.DURABILITY)
        if((0..1 + unbreaking).random() == 0){
            (meta as Damageable).damage += 1
        }

        /** Set crossbow cooldown as reload time */
        player.setCooldown(Material.CROSSBOW, 60)

        /** Reload class crossbow and buff damage depending on quick charge level */
        if(((crossbow.lore() ?: return)[0] as? TextComponent ?: return).content().endsWith(Classes.HUNTER.name)){
            meta.setChargedProjectiles(arrayListOf(ItemStack(Material.ARROW)))
            quickChargeLevel[event.projectile.uniqueId] = meta.getEnchantLevel(Enchantment.QUICK_CHARGE)
        }

        /** event.hand is bugged. Shooting a crossbow with a drinkable potion in offhand dupes the crossbow
         * if item in event hand is replaced */
        if(player.inventory.itemInMainHand == crossbow){
            crossbow.itemMeta = meta
            player.inventory.setItemInMainHand(crossbow)
        }else if(player.inventory.itemInOffHand == crossbow){
            crossbow.itemMeta = meta
            player.inventory.setItemInOffHand(crossbow)
        }

        /** Sound effect while ascension is active */
        if(Hunter.ascensionActive.contains(player.uniqueId)){
            player.world.playSound(player, Sound.ENTITY_ENDER_DRAGON_FLAP, 3F, 1F)
        }
    }

    @EventHandler
    fun disableFireworkReload(event: EntityLoadCrossbowEvent){
        val player = event.entity
        if(player !is Player) return
        if(player.inventory.itemInOffHand.type == Material.FIREWORK_ROCKET){
            event.isCancelled = true
        }
    }

    @EventHandler
    fun abilityHits(event: ProjectileHitEvent){
        if(event.entity.shooter !is Player) return
        if(event.entity !is Arrow) return

        /** Filter for valid arrow hits */
        val arrow = (event.entity as Arrow)
        if(!arrow.isShotFromCrossbow) return

        /** Only boost dmg from class weapon crossbows */
        val level = quickChargeLevel.remove(arrow.uniqueId)
        if(level != null){
            arrow.damage = (11 + level).toDouble()
        }

        /** Handle ascension arrows */
        val playerID = (arrow.shooter as Player).uniqueId
        if(!Hunter.ascensionActive.contains(playerID)){
            return
        }

        /** Miss */
        if(event.hitBlock != null){
            Hunter.ascensionStacks.remove(playerID)
            return
        }

        /** Hit */
        val targetId = (event.hitEntity ?: return).uniqueId
        val stacks = Hunter.ascensionStacks[playerID] ?: mutableMapOf()

        /** Apply stacks and do bonus damage */
        val amount = stacks[targetId] ?: 0
        if(amount == 1){
            arrow.damage = arrow.damage * 1.4
        }else if(amount > 1){
            arrow.damage = arrow.damage * 1.8
        }
        stacks[targetId] = amount + 1
        Hunter.ascensionStacks[playerID] = stacks
    }

    @EventHandler
    fun tumbleAnimation(event: EntityToggleGlideEvent){
        if(Hunter.tumbleActive.contains(event.entity.uniqueId)){
            event.isCancelled = true
        }
    }
}