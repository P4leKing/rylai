package com.github.p4leking.rylai.handlers

import com.github.p4leking.rylai.Rylai
import com.github.p4leking.rylai.utils.Classes
import com.github.p4leking.rylai.utils.CraftingSkills
import net.kyori.adventure.text.TextComponent
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.enchantment.EnchantItemEvent
import org.bukkit.event.enchantment.PrepareItemEnchantEvent
import org.bukkit.event.inventory.PrepareAnvilEvent
import org.bukkit.inventory.AnvilInventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.Damageable
import org.bukkit.inventory.meta.EnchantmentStorageMeta
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.inventory.meta.Repairable

class EnchantmentHandler(private val plugin: Rylai) : Listener {
    init {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    @EventHandler
    fun disableEnchantmentTable(event: PrepareItemEnchantEvent) {
        event.isCancelled = true
    }

    @EventHandler
    fun disableEnchantmentTable(event: EnchantItemEvent) {
        event.isCancelled = true
    }

    @EventHandler
    fun anvilLimits(event: PrepareAnvilEvent) {
        val inv = event.inventory
        val firstItem = inv.firstItem ?: return
        val upgrade = inv.secondItem ?: return

        /** Handle repair */
        if(upgrade.type != Material.ENCHANTED_BOOK){

            /** Allow repair with flex tape */
            if(firstItem.itemMeta is Damageable && upgrade.type == Material.PHANTOM_MEMBRANE
                    && (upgrade.lore()?.get(0) as? TextComponent)?.content()?.startsWith("tape") == true){

                /** Create result */
                val result = firstItem.clone()
                val meta = result.itemMeta as Damageable
                meta.damage = 0
                result.itemMeta = meta
                event.result = result
                inv.repairCost = 1
                return
            }

            /** Disable regular repair */
            event.result = null
            return
        }

        /** Custom enchantment combinations */
        val product = event.result ?: run {

            /** Allowed illegal enchantments */
            val enchantment = when(firstItem.type){
                Material.DIAMOND_CHESTPLATE -> {
                    if(!(((firstItem.lore() ?: return)[0] as? TextComponent) ?: return).content().startsWith("armor")){
                        return
                    }
                    Enchantment.PROTECTION_ENVIRONMENTAL
                }
                Material.DIAMOND_AXE -> {
                    if(!(((firstItem.lore() ?: return)[0] as? TextComponent) ?: return).content().endsWith(Classes.MAGE.name)){
                        return
                    }
                    Enchantment.ARROW_DAMAGE
                }
                else -> return
            }

            /** Allow custom enchantment combination */
            event.result = allowIllegalCombination(inv, firstItem.clone(), upgrade.itemMeta, enchantment) ?: return
            return
        }

        /** Allow book combination */
        if(product.type == Material.ENCHANTED_BOOK){
            return
        }

        /** Disable enchantments for items without lore */
        val id = ((product.lore()?.get(0) as? TextComponent) ?: run {
            event.result = null
            return
        }).content()

        /** Custom enchantment caps (for all enchants that would be valid in vanilla) */
        if(id.startsWith("armor")){
            val protectionLevel = product.getEnchantmentLevel(Enchantment.PROTECTION_ENVIRONMENTAL)
            if(protectionLevel > 3){
                product.addEnchantment(Enchantment.PROTECTION_ENVIRONMENTAL, 3)
                return
            }

            /** Ensure Protection upgrade is possible even if another enchantment is present on the book */
            if(product.type != Material.DIAMOND_CHESTPLATE){
                return
            }

            allowIllegalCombination(inv, product, upgrade.itemMeta as EnchantmentStorageMeta,
                    Enchantment.PROTECTION_ENVIRONMENTAL, protectionLevel)
            return
        }

        if(id.endsWith(Classes.ASSASSIN.name) || id.endsWith(Classes.WARRIOR.name)){
            if(product.getEnchantmentLevel(Enchantment.DAMAGE_ALL) > 3){
                product.addEnchantment(Enchantment.DAMAGE_ALL, 3)
            }
            return
        }

        if(id.endsWith(Classes.MAGE.name)){
            product.removeEnchantment(Enchantment.DAMAGE_ALL)
            product.removeEnchantment(Enchantment.DIG_SPEED)
            allowIllegalCombination(inv, product, upgrade.itemMeta as EnchantmentStorageMeta, Enchantment.ARROW_DAMAGE,
                    product.getEnchantmentLevel(Enchantment.ARROW_DAMAGE))
            return
        }

        if(id.endsWith(Classes.ARCHER.name)){
            if(product.getEnchantmentLevel(Enchantment.ARROW_DAMAGE) > 3){
                product.addEnchantment(Enchantment.ARROW_DAMAGE, 3)
            }
            return
        }

        if(id.endsWith(Classes.HUNTER.name)){
            return
        }

        /** Block enchanting of vanilla items */
        if(!id.startsWith("tool")){
            event.result = null
            return
        }

        /** Dynamic enchantment level caps for tools */
        when(product.type){
            Material.DIAMOND_PICKAXE -> {
                val miningLevel = plugin.players[event.viewers.first().uniqueId]!!.mining.level  
                limitToolEnchantment(product, Enchantment.DIG_SPEED, miningLevel / CraftingSkills.MINING.efficiencyIncrement)
                limitToolEnchantment(product, Enchantment.LOOT_BONUS_BLOCKS, miningLevel / CraftingSkills.MINING.luckIncrement)
            }
            Material.DIAMOND_SHOVEL -> {
                product.removeEnchantment(Enchantment.LOOT_BONUS_BLOCKS)

                val miningLevel = plugin.players[event.viewers.first().uniqueId]!!.mining.level + 10 // offset by 10 to spread unlocks out  
                limitToolEnchantment(product, Enchantment.DIG_SPEED, miningLevel / CraftingSkills.MINING.efficiencyIncrement)
            }
            Material.DIAMOND_AXE -> {
                product.removeEnchantment(Enchantment.DAMAGE_ALL)
                product.removeEnchantment(Enchantment.LOOT_BONUS_BLOCKS)

                val woodcuttingLevel = plugin.players[event.viewers.first().uniqueId]!!.woodcutting.level  
                limitToolEnchantment(product, Enchantment.DIG_SPEED, woodcuttingLevel / CraftingSkills.WOODCUTTING.efficiencyIncrement)
            }
            Material.FISHING_ROD -> {
                val fishingLevel = plugin.players[event.viewers.first().uniqueId]!!.fishing.level  
                limitToolEnchantment(product, Enchantment.LUCK, fishingLevel / CraftingSkills.FISHING.luckIncrement)
                limitToolEnchantment(product, Enchantment.LURE, fishingLevel / CraftingSkills.FISHING.efficiencyIncrement)
            }
            else -> {
                event.result = null
            }
        }
    }

    /** Other valid enchantments are on the book because the result isn't null */
    private fun allowIllegalCombination(inv:AnvilInventory, product: ItemStack, bookMeta: EnchantmentStorageMeta, enchantment: Enchantment, itemLevel: Int){
        val bookLevel = bookMeta.getStoredEnchantLevel(enchantment)
        if(bookLevel == 0){
            return
        }
        if(itemLevel == 3){
            return
        }

        /** Calc new enchantment level */
        var newMax = when{
            itemLevel > bookLevel -> itemLevel
            itemLevel < bookLevel -> bookLevel
            else -> itemLevel + 1
        }
        if(newMax > 3){ newMax = 3 }

        /** Apply changes to the result */
        inv.repairCost = inv.repairCost + newMax - itemLevel
        product.addUnsafeEnchantment(enchantment, newMax)
    }

    /** No other valid enchants are on the book, otherwise result wouldn't be null */
    private fun allowIllegalCombination(inv: AnvilInventory, base: ItemStack, upgradeMeta: ItemMeta, enchantment: Enchantment): ItemStack?{
        val upgradeLevel = (upgradeMeta as EnchantmentStorageMeta).getStoredEnchantLevel(enchantment)
        if(upgradeLevel == 0){
            return null
        }

        /** Make sure custom enchant is actually needed */
        val itemLevel = base.getEnchantmentLevel(enchantment)
        if(itemLevel == 3){
            return null
        }

        /** Calc new enchantment level */
        var newMax = when{
            itemLevel > upgradeLevel -> itemLevel
            itemLevel < upgradeLevel -> upgradeLevel
            else -> itemLevel + 1
        }
        if(newMax > 3){ newMax = 3 }
        base.addUnsafeEnchantment(enchantment, newMax)

        /** Calculate the xp cost of the enchantment */
        val meta1 = base.itemMeta as? Repairable ?: return null
        val cost1 = meta1.repairCost
        val cost2 = (upgradeMeta as? Repairable)?.repairCost ?: -1
        val finalCost =  if(cost1 >= cost2){
            inv.repairCost = cost1 + newMax - itemLevel
            cost1 * 2 + 1
        }else{
            inv.repairCost = cost2 + newMax - itemLevel
            cost2 * 2 + 1
        }

        /** Set enchantment result */
        meta1.repairCost = finalCost
        base.itemMeta = meta1
        return base
    }


    private fun limitToolEnchantment(item: ItemStack, enchantment: Enchantment, customMax: Int){
        if(item.getEnchantmentLevel(enchantment) > customMax){
            if(customMax > 0){
                item.addEnchantment(enchantment, customMax)
            }else{
                item.removeEnchantment(enchantment)
            }
        }
    }
}