package com.github.p4leking.rylai.handlers

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent
import com.destroystokyo.paper.event.player.PlayerElytraBoostEvent
import com.github.p4leking.rylai.Rylai
import com.github.p4leking.rylai.utils.Classes
import com.github.p4leking.rylai.utils.CraftingSkills
import net.kyori.adventure.text.TextComponent
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Boat
import org.bukkit.entity.EntityType
import org.bukkit.entity.Horse
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockDispenseArmorEvent
import org.bukkit.event.entity.*
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.inventory.InventoryPickupItemEvent
import org.bukkit.event.inventory.SmithItemEvent
import org.bukkit.event.player.*
import org.bukkit.inventory.HorseInventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.PotionMeta
import org.bukkit.potion.PotionType
import org.bukkit.projectiles.BlockProjectileSource
import org.spigotmc.event.entity.EntityDismountEvent
import java.util.*

class ItemHandler(private val plugin: Rylai) : Listener {
    private val summonedHorses = mutableSetOf<UUID>()
    private val increasedDurabilityItems = setOf(Material.DIAMOND_SWORD, Material.BOW, Material.FISHING_ROD, Material.IRON_HELMET, Material.IRON_BOOTS)

    init {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun restrictClassArmorUsage(event: PlayerArmorChangeEvent) {
        val newItem = event.newItem ?: return
        val id = ((newItem.lore() ?: return)[0] as? TextComponent ?: return).content()

        /** Prevent equipping armor from the wrong class */
        if(!id.startsWith("armor")){
            return
        }

        val player = event.player
        if(id.endsWith(plugin.players[player.uniqueId]!!.selectedClass)){
            return
        }

        /** Remove newly equipped piece */
        val inventory = player.inventory
        when(event.slotType){
            PlayerArmorChangeEvent.SlotType.HEAD -> inventory.helmet = null
            PlayerArmorChangeEvent.SlotType.CHEST -> inventory.chestplate = null
            PlayerArmorChangeEvent.SlotType.LEGS -> inventory.leggings = null
            PlayerArmorChangeEvent.SlotType.FEET -> inventory.boots = null
        }

        /** Add item that cannot be equipped to inventory or drop it if it is full */
        val overflow = inventory.addItem(newItem)
        for (item in overflow){
            player.world.dropItem(player.location, item.value)
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun restrictClassArmorUsage(event: BlockDispenseArmorEvent) {
        event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun restrictClassWeaponUsage(event: EntityDamageByEntityEvent){
        val player = event.damager as? Player ?: return
        val weapon = player.inventory.itemInMainHand
        val id = ((weapon.lore() ?: return)[0] as? TextComponent ?: return).content()
        if(!id.startsWith("weapon")){
            return
        }

        if(!id.endsWith(plugin.players[player.uniqueId]!!.selectedClass)){  
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun restrictClassWeaponUsage(event: EntityShootBowEvent){
        val playerID = (event.entity as? Player ?: return).uniqueId
        val id = (((event.bow ?: return).lore() ?: return)[0] as? TextComponent ?: return).content()

        if(!id.startsWith("weapon")){
            return
        }

        if(!id.endsWith(plugin.players[playerID]!!.selectedClass)){  
            event.isCancelled = true
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun restrictItemUse(event: PlayerInteractEvent) {
        val item = event.item ?: return

        if(event.action == Action.LEFT_CLICK_AIR || event.action == Action.PHYSICAL){
            return
        }

        /** Disable shield and pearls */
        if (event.action == Action.RIGHT_CLICK_AIR || event.action == Action.RIGHT_CLICK_BLOCK) {
            if (item.type == Material.SHIELD || item.type == Material.ENDER_PEARL) {
                event.isCancelled = true
                return
            }
        }

        val id = ((item.lore() ?: return)[0] as? TextComponent ?: return).content()

        /** Disable skill spawn eggs */
        if(id.startsWith("skill")){
            event.isCancelled = true
            return
        }

        /** Provide horse whistle functionality */
        if(id.startsWith("whistle")){
            whistle(event.player)
            return
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun entityClickItemUse(event: PlayerInteractEntityEvent) {
        val mainType = event.player.inventory.itemInMainHand.type
        val offType = event.player.inventory.itemInOffHand.type
        if (offType == Material.SHIELD || offType == Material.ENDER_PEARL || mainType == Material.ENDER_PEARL || mainType == Material.SHIELD) {
            event.isCancelled = true
            return
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun disableSplashPotions(event: PotionSplashEvent){
        /** Prevent players and dispensers from using splash potions */
        if(event.potion.shooter is Player || event.potion.shooter is BlockProjectileSource){
            event.isCancelled = true
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun disableLingeringPotions(event: LingeringPotionSplashEvent) {
        /** Prevent players and dispensers from using lingering potions */
        if(event.entity.shooter is Player || event.entity.shooter is BlockProjectileSource){
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onConsumption(event: PlayerItemConsumeEvent){
        val itemType = event.item.type

        /** Change food healing and disable golden apple variants */
        if(itemType.isEdible){
            if(itemType == Material.GOLDEN_APPLE || itemType == Material.ENCHANTED_GOLDEN_APPLE){
                event.isCancelled = true
                return
            }

            val player = event.player

            /** Food healing out of combat */
            if(!CombatHandler.inCombat.contains(player.uniqueId)){
                player.saturatedRegenRate = 10
                return
            }

            /** Food healing in combat */
            player.saturatedRegenRate = 60

            /** Change max healing from food by reducing saturation */
            plugin.server.scheduler.scheduleSyncDelayedTask(plugin, {
                player.saturation = player.saturation / 2.7F
            }, 1)
            return
        }

        if(itemType != Material.POTION){
            return
        }

        /** For infinite potion item use event replacement */
        if((event.item.lore()?.get(0) as? TextComponent)?.content()?.startsWith("infinite") == true){
            event.replacement = event.item
        }

        val baseData = (event.item.itemMeta as PotionMeta).basePotionData
        val type = baseData.type

        //TODO define allowed T2 pots here
        /** Enable allowed tier 2 potions */
        if(type == PotionType.FIRE_RESISTANCE){
            return
        }

        /** Disable all other tier 2 potions */
        if(baseData.isUpgraded){
            event.isCancelled = true
            return
        }

        /** Set health potion cooldown */
        if(type == PotionType.INSTANT_HEAL){
            event.player.setCooldown(Material.POTION, 600)
            return
        }

        //TODO define allowed pots here
        /** Disable all tier 1 potions except these */
        if(type != PotionType.SPEED){
            event.isCancelled = true
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun disableTotems(event: EntityResurrectEvent){
        event.isCancelled = true
    }

    @EventHandler(ignoreCancelled = true)
    fun disableElytraBoost(event: PlayerElytraBoostEvent){
        event.isCancelled = true
    }

    @EventHandler(ignoreCancelled = true)
    fun disableEndCrystalExplosions(event: EntityDamageEvent){
        if(event.entityType == EntityType.ENDER_CRYSTAL){
            event.entity.remove()
            event.isCancelled = true
        }
    }
    
    @EventHandler(ignoreCancelled = true)
    fun disableSmithingTable(event: SmithItemEvent) {
        event.isCancelled = true
    }

    @EventHandler(ignoreCancelled = true)
    fun classItemDurability(event: PlayerItemDamageEvent){
        /** Only consider class weapons */
        val id = ((event.item.lore() ?: return)[0] as? TextComponent ?: return).content()
        if(event.item.type !in increasedDurabilityItems){
            return
        }

        /** Increase durability of iron class armor parts by 100% */
        if(id.startsWith("armor")){
            if((0..1).random() == 0){
                event.isCancelled = true
            }
            return
        }

        /** Increase durability of fishing rods by 400% */
        if(id.endsWith(CraftingSkills.FISHING.name)){
            if((0..3).random() != 0){
                event.isCancelled = true
            }
            return
        }

        /** Increase durability of diamond swords by 25% */
        if(id.endsWith(Classes.WARRIOR.name)){
            if((0..3).random() == 0){
                event.isCancelled = true
            }
            return
        }

        /** Increase durability of bows by 200% */
        if(id.endsWith(Classes.ARCHER.name)){
            if((0..2).random() != 0){
                event.isCancelled = true
            }
            return
        }
    }

    @EventHandler
    fun keepOnDeath(event: PlayerDeathEvent){
        val keptItems = mutableSetOf<ItemStack>()
        for(item in event.drops){
            val lore = ((item.lore() ?: continue)[0] as? TextComponent ?: continue).content()
            if(lore.startsWith("skill") || lore.contains("legendary")){
                keptItems.add(item)
            }
        }
        event.drops.removeAll(keptItems)
        event.itemsToKeep.addAll(keptItems)
    }

    private fun whistle(player: Player){
        if(player.isInsideVehicle || CombatHandler.inCombat.contains(player.uniqueId)){
            return
        }

        /** Spawn entity */
        val world = player.world
        val horse = world.spawnEntity(player.location, EntityType.HORSE) as Horse
        summonedHorses.add(horse.uniqueId)

        /** Set attribute */
        horse.jumpStrength = 1.0
        horse.getAttribute(Attribute.GENERIC_MAX_HEALTH)!!.baseValue = 40.0
        horse.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED)!!.baseValue = 0.3375

        /** Set ride requirements */
        horse.setAdult()
        horse.isTamed = true
        horse.owner = player
        horse.inventory.saddle = ItemStack(Material.SADDLE)

        /** Mount player */
        summonedHorses.add(horse.uniqueId)
        horse.addPassenger(player)
        world.playSound(player, Sound.ITEM_GOAT_HORN_SOUND_1, 1.0F, 1.0F)
    }

    @EventHandler
    fun handleHorseDeath(event: EntityDeathEvent){
        if(event.entity !is Horse){
            return
        }
        if(summonedHorses.remove(event.entity.uniqueId)){
            event.drops.clear()
        }
    }

    @EventHandler
    fun despawnOnDismount(event: EntityDismountEvent){
        val player = event.entity as? Player ?: return

        /** Despawn summoned horses on dismount */
        if(event.dismounted is Horse){
            if(summonedHorses.remove(event.dismounted.uniqueId)){
                event.dismounted.remove()
            }
            return
        }

        /** Directly add boats to inventory on dismount */
        val boat = (event.dismounted as? Boat ?: return)
        boat.remove()
        val overflow = player.inventory.addItem(ItemStack(boat.boatMaterial))
        for (item in overflow){
            player.world.dropItem(player.location, item.value)
        }
        return
    }

    @EventHandler
    fun despawnOnLogout(event: PlayerQuitEvent){
        (event.player.vehicle as? Horse ?: return).remove()
    }

    @EventHandler
    fun preventHorseInventoryAccess(event: InventoryOpenEvent){
        val inv = event.inventory as? HorseInventory ?: return
        val id = (inv.holder as? Horse ?: return).uniqueId
        if(summonedHorses.contains(id)){
            event.isCancelled = true
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun disableJumpGriefing(event: EntityChangeBlockEvent){
        if(event.block.type == Material.FARMLAND && event.to == Material.DIRT){
            event.isCancelled = true
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun hopperItemPickup(event: InventoryPickupItemEvent){
        event.isCancelled = true
    }

    @EventHandler(ignoreCancelled = true)
    fun preventSkillDrop(event: ItemSpawnEvent){
        val lore = ((event.entity.itemStack.lore() ?: return)[0] as? TextComponent ?: return).content()
        if(lore.startsWith("skill")){
            event.isCancelled = true
        }
    }
}