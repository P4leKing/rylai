package com.github.p4leking.rylai.handlers

import com.github.p4leking.rylai.Rylai
import com.github.p4leking.rylai.utils.Classes
import com.github.p4leking.rylai.utils.CraftingSkills
import com.github.p4leking.rylai.utils.PlayerData
import net.kyori.adventure.text.Component
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Item
import org.bukkit.entity.Monster
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.inventory.FurnaceExtractEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerFishEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.AnvilInventory
import org.bukkit.inventory.ItemStack
import kotlin.math.roundToInt

class PlayerDataHandler(private val plugin: Rylai) : Listener {

    private val logSet = setOf(
            Material.ACACIA_LOG, Material.STRIPPED_ACACIA_LOG, Material.BIRCH_LOG, Material.STRIPPED_BIRCH_LOG,
            Material.DARK_OAK_LOG, Material.STRIPPED_DARK_OAK_LOG, Material.JUNGLE_LOG, Material.STRIPPED_JUNGLE_LOG,
            Material.MANGROVE_LOG, Material.STRIPPED_MANGROVE_LOG, Material.OAK_LOG, Material.STRIPPED_OAK_LOG,
            Material.SPRUCE_LOG, Material.STRIPPED_SPRUCE_LOG, Material.CRIMSON_STEM, Material.STRIPPED_CRIMSON_STEM,
            Material.WARPED_STEM, Material.STRIPPED_WARPED_STEM, Material.MELON, Material.PUMPKIN, Material.ACACIA_WOOD,
            Material.STRIPPED_ACACIA_WOOD, Material.BIRCH_WOOD, Material.STRIPPED_BIRCH_WOOD, Material.DARK_OAK_WOOD,
            Material.STRIPPED_DARK_OAK_WOOD, Material.JUNGLE_WOOD, Material.STRIPPED_JUNGLE_WOOD, Material.MANGROVE_WOOD,
            Material.STRIPPED_MANGROVE_WOOD, Material.OAK_WOOD, Material.STRIPPED_OAK_WOOD, Material.SPRUCE_WOOD,
            Material.STRIPPED_SPRUCE_WOOD, Material.CRIMSON_HYPHAE, Material.STRIPPED_CRIMSON_HYPHAE,
            Material.WARPED_HYPHAE, Material.STRIPPED_WARPED_HYPHAE)

    init {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    /** Load player data from config on login */
    @EventHandler(priority = EventPriority.HIGHEST)
    fun loadPlayerData(event: PlayerJoinEvent){
        val player = event.player
        player.discoverRecipes(plugin.customRecipes)

        val playerData = PlayerData(plugin, player.uniqueId)
        plugin.players[player.uniqueId] = playerData

        /** Change player list name and add group tag if required */
        val groupName = playerData.group ?: run {
            plugin.server.scheduler.scheduleSyncDelayedTask(plugin, {
                event.player.playerListName(player.displayName())
            }, 2)
            return
        }
        val groupTag = plugin.groups[groupName]!!.tag ?: run {
            plugin.server.scheduler.scheduleSyncDelayedTask(plugin, {
                event.player.playerListName(player.displayName())
            }, 2)
            return
        }
        plugin.server.scheduler.scheduleSyncDelayedTask(plugin, {
            event.player.playerListName(Component.text("${ChatColor.DARK_GRAY}<${ChatColor.AQUA}${groupTag}${ChatColor.DARK_GRAY}>")
                .append(player.displayName()))
        }, 2)
    }

    /** Save player data to config on logout. */
    @EventHandler
    fun savePlayerData(event: PlayerQuitEvent){

        /** Kill player if in combat */
        if(CombatHandler.inCombat.contains(event.player.uniqueId)){
            event.player.health = 0.0
        }

        plugin.players[event.player.uniqueId]!!.saveToConfig()  
        plugin.players.remove(event.player.uniqueId)
    }

    /** Level up notification */
    private fun levelUp(player: Player, id: String, className: String, level: Int){
        plugin.rankings.allRankings[id]!!.update(player.uniqueId, level)  
        player.world.playSound(player, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 2F, 1F)
        player.sendMessage("${ChatColor.GREEN}Congratulations, you are now ${ChatColor.GOLD}$className " +
                "${ChatColor.GREEN}Level ${ChatColor.GOLD}$level${ChatColor.GREEN}.")

        if(level < 100){
            return
        }

        /** Level 100 notification */
        for(players in plugin.server.onlinePlayers){
            if(player.uniqueId == players.uniqueId){
                continue
            }

            players.sendMessage("${ChatColor.DARK_PURPLE}${player.name}${ChatColor.GOLD} has reached " +
                    "${ChatColor.DARK_PURPLE}$className ${ChatColor.GOLD}Level 100.")
        }
    }

    /** XP gain in the currently selected class depending on the killed mob */
    @EventHandler
    fun gainCombatXP(event: EntityDeathEvent){
        //TODO import mythic api and use MythicMobDeathEvent - access xp and add it to killer
        if(event.entity !is Monster) return

        val killer = event.entity.killer ?: return
        //TODO xp from Mythic Mobs
        val experienceGained = 1500

        /** Add experience and play level up sound if needed */
        val playerData = plugin.players[killer.uniqueId] ?: return
        if(playerData.combat.gainExperience(experienceGained)){
            levelUp(killer, playerData.selectedClass, Classes.valueOf(playerData.selectedClass).className,
                    playerData.combat.level)
        }
    }

    /** Enchantment limitation and XP gain in the gathering skills */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun gainGatheringXP(event: BlockBreakEvent){
        val player = event.player
        val playerData = plugin.players[player.uniqueId]!!  

        /** Level restrict enchantments */
        val item = player.inventory.itemInMainHand
        if(item.type == Material.DIAMOND_PICKAXE){ //TODO diese checks in funktion auslagern?
            val miningLevel = playerData.mining.level
            if (miningLevel < 100 && (item.getEnchantmentLevel(Enchantment.DIG_SPEED) > miningLevel / CraftingSkills.MINING.efficiencyIncrement
                            || item.getEnchantmentLevel(Enchantment.LOOT_BONUS_BLOCKS) > miningLevel / CraftingSkills.MINING.luckIncrement)) {
                event.isCancelled = true
                return
            }
        } else if(item.type == Material.DIAMOND_SHOVEL){
            val miningLevel = playerData.mining.level + 10
            if (miningLevel < 100 && item.getEnchantmentLevel(Enchantment.DIG_SPEED) > miningLevel / CraftingSkills.MINING.efficiencyIncrement) {
                event.isCancelled = true
                return
            }
        } else if(item.type == Material.DIAMOND_AXE) {
            val woodcuttingLevel = playerData.woodcutting.level
            if (woodcuttingLevel < 100 && item.getEnchantmentLevel(Enchantment.DIG_SPEED) > woodcuttingLevel / CraftingSkills.WOODCUTTING.efficiencyIncrement) {
                event.isCancelled
                return
            }
        }

        /** Gain woodcutting xp */
        if(event.block.type in logSet){
            if(playerData.woodcutting.gainExperience(750)){
                levelUp(player, CraftingSkills.WOODCUTTING.name, CraftingSkills.WOODCUTTING.className, playerData.woodcutting.level)
            }
            return
        }

        /** Gain mining xp */
        val miningXP = when(event.block.type){
            Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE, Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE -> 10000
            Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE, Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE -> 3000
            Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE, Material.NETHER_GOLD_ORE -> 1500
            Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE, Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE,
            Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE, Material.NETHER_QUARTZ_ORE -> 750
            else -> return
        }

        if(playerData.mining.gainExperience(miningXP)){
            levelUp(event.player, CraftingSkills.MINING.name, CraftingSkills.MINING.className, playerData.mining.level)
        }
    }

    /** XP gain in the fishing skill */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun gainFishingXP(event: PlayerFishEvent){
        if(event.state != PlayerFishEvent.State.CAUGHT_FISH){
            return
        }

        /** Gain fishing xp depending on the value of the caught item */
        val catchItem = event.caught as? Item ?: return
        val catchXP = when(catchItem.itemStack.type){
            Material.COD -> 1500
            Material.SALMON -> 2000
            Material.PUFFERFISH -> 4000
            Material.BOW, Material.FISHING_ROD -> {
                catchItem.itemStack = ItemStack(Material.ENDER_PEARL)
                10000
            }
            Material.TROPICAL_FISH, Material.ENCHANTED_BOOK, Material.NAME_TAG, Material.NAUTILUS_SHELL, Material.SADDLE -> 10000
            else -> return
        }

        val fishingData = plugin.players[event.player.uniqueId]!!.fishing
        val fishingLevel = fishingData.level

        /** Level restrict tool enchantments */
        val item = event.player.activeItem
        if(fishingLevel < 90 && item.getEnchantmentLevel(Enchantment.LUCK) > fishingLevel / CraftingSkills.FISHING.luckIncrement
                && item.getEnchantmentLevel(Enchantment.LURE) > fishingLevel / CraftingSkills.FISHING.efficiencyIncrement){
            event.isCancelled = true
            return
        }

        if(fishingData.gainExperience(catchXP)){
            levelUp(event.player, CraftingSkills.FISHING.name, CraftingSkills.FISHING.className, fishingData.level)
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun gainSmithingXP(event:InventoryClickEvent){
        //TODO move to EnchantItemEvent once it supports anvils - way more efficient
        if(!event.isLeftClick){
            return
        }

        /** Check if clicked item is anvil result */
        val inv = event.clickedInventory as? AnvilInventory ?: return
        if(event.slot != 2 || event.cursor?.type != Material.AIR){
            return
        }

        /** Valid enchant */
        val player = event.whoClicked as Player
        val playerData = plugin.players[player.uniqueId]!!.smithing

        /** Check if player has enough xp to enchant */
        val baseCost = inv.repairCost
        if(baseCost > player.level){
            return
        }

        /** Reduce enchantment cost depending on smithing level */
        inv.repairCost = when{
            baseCost < 1 -> return
            baseCost == 1 -> 1
            else -> {
                val reducedCost = (baseCost * (1.0 - 0.69 * playerData.level / 100.0)).roundToInt()
                if (reducedCost > 1) {
                    reducedCost
                } else {
                    1
                }
            }
        }

        /** Gain smithing xp based on the level cost of the anvil operation */
        if(playerData.gainExperience(baseCost * 2500)){
            levelUp(player, CraftingSkills.SMITHING.name, CraftingSkills.SMITHING.className, playerData.level)
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun gainOvenXP(event: FurnaceExtractEvent){
        val type = event.itemType
        val smithingXP = when(type){
            Material.GOLD_INGOT -> 1200
            Material.IRON_INGOT -> 800
            Material.COPPER_INGOT -> 400
            else -> return
        }

        val player = event.player
        val smithingData = plugin.players[player.uniqueId]!!.smithing
        val amount = event.itemAmount
        val bonus = (amount * (smithingData.level / 100.0)).toInt()

        /** Gain smithing xp based on the level cost of the anvil operation */
        if(smithingData.gainExperience(smithingXP * (amount + bonus))){
            levelUp(player, CraftingSkills.SMITHING.name, CraftingSkills.SMITHING.className, smithingData.level)
        }

        /** Add bonus to inventory or drop it on the ground */
        val overflow = player.inventory.addItem(ItemStack(type, bonus))
        for (item in overflow){
            player.world.dropItem(player.location, item.value)
        }
    }

    //TODO cooking xp für essen braten, mehr output skalierend mit level der skills (wie oben in furnaceExtractEvent)
    //TODO xp für crop harvest
}