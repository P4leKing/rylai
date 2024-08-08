package com.github.p4leking.rylai.commands

import com.github.p4leking.rylai.Rylai
import com.github.p4leking.rylai.utils.Classes
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.format.TextColor
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack

class Skills(val plugin: Rylai) : Listener, CommandExecutor, TabCompleter {
    private val overview : String = "Class selection"
    private val inventorySize = 9 * 5

    init {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    override fun onTabComplete(sender: CommandSender, command: Command, label: String, args: Array<out String>): MutableList<String> {
        if(args.size > 1){
            return mutableListOf()
        }
        return mutableListOf("remove", "load", "save")
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        if(event.view.title() != Component.text(overview)){
            return
        }

        event.isCancelled = true

        if(event.clickedInventory == event.whoClicked.inventory){
            return
        }

        /** Filter blocker clicks */
        val item = event.currentItem ?: return
        if(!item.hasItemMeta()){
            return
        }
        val meta = item.itemMeta ?: return
        if(!meta.hasLore()){
            return
        }

        /** Player clicked into overview GUI*/
        val displayName = meta.displayName() ?: return
        val className = (displayName as? TextComponent ?: return).content()
        val combatClass = Classes.values().find{ it.className == className } ?: return
        val inventorySize = 9 * 4
        val skills = Bukkit.createInventory(event.whoClicked, inventorySize, displayName)

        /** Create skill icon from enum */
        var index = 10
        combatClass.abilities.forEach {
            skills.setItem(index, plugin.getItem(ItemStack(it.icon), it.abilityName,
                    "skill:${combatClass.name}:${it.name.lowercase()}", "Cooldown: ${it.cooldown / 20}s", *it.description))
            index += 2
        }

        /** Add back button */
        val back = ItemStack(Material.RED_STAINED_GLASS_PANE)
        val backMeta = back.itemMeta
        backMeta.displayName(Component.text("Back to overview"))
        backMeta.lore(arrayListOf<Component>(Component.text("back", TextColor.color(0))))
        back.itemMeta = backMeta
        skills.setItem(35, back)

        /** Add class selector button */
        val select = ItemStack(Material.GREEN_STAINED_GLASS_PANE)
        val selectMeta = select.itemMeta
        selectMeta.displayName(Component.text("Select this as your class"))
        selectMeta.lore(arrayListOf<Component>(Component.text("select", TextColor.color(0))))
        select.itemMeta = selectMeta
        skills.setItem(31, select)

        /** Block remaining inventory spaces and open inventory */
        event.whoClicked.openInventory(createBlockers(skills, inventorySize - 6))
    }

    @EventHandler
    fun onSkillClick(event: InventoryClickEvent) {
        val title = (event.view.title() as? TextComponent ?: return).content()
        val combatClass = Classes.values().find{ it.className == title } ?: return
        event.isCancelled = true

        /** Make sure player clicked GUI */
        if(event.clickedInventory == event.whoClicked.inventory){
            return
        }

        /** Make sure clicked item had lore */
        val clickedItem = event.currentItem ?: return
        val id = ((clickedItem.lore() ?: return)[0] as? TextComponent ?: return).content()
        val playerInventory = event.whoClicked.inventory

        /** Handling button presses */
        if(!id.startsWith("skill")){

            /** Back button */
            if(id.startsWith("back")){
                if (event.whoClicked is Player) {
                    event.whoClicked.openInventory(classSelectorGUI(event.whoClicked as Player))
                }
            }

            /** Select class button */
            if(id.startsWith("select")){

                /** Prevent swap if the player has class armor equipped */
                for(slot in playerInventory.armorContents){
                    if(!(((slot ?: continue).lore() ?: continue)[0] as? TextComponent ?: continue).content().startsWith("armor")){
                        continue
                    }

                    event.whoClicked.sendMessage("${ChatColor.YELLOW}Please unequip all class armor pieces before switching to another class.")
                    return
                }

                removeAllSkills(playerInventory)
                if(plugin.players[event.whoClicked.uniqueId]!!.switchClass(combatClass)){  
                    event.whoClicked.sendMessage("${ChatColor.GREEN}You successfully selected ${ChatColor.GOLD}${combatClass.className}${ChatColor.GREEN} as your class.")
                }
            }

            return
        }

        /** Removes instances of this skill if it was in the inventory already. */
        playerInventory.removeItem(clickedItem)

        if(plugin.players[event.whoClicked.uniqueId]!!.selectedClass == combatClass.name){  
            playerInventory.addItem(clickedItem)
        }else{
            event.whoClicked.sendMessage("You have to select ${combatClass.className} as your class to equip this skill.")
        }
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player){
            sender.sendMessage("Only Players can run this command.")
            return true
        }

        /** Execute default command */
        if(args.isEmpty()){
            sender.openInventory(classSelectorGUI(sender))
            return true
        }

        /** Execute subcommand */
        if(args[0].lowercase() == "remove"){
            removeAllSkills(sender.inventory)
            return true
        }

        if(args[0].lowercase() == "save"){
            savePreset(sender)
            return true
        }

        if(args[0].lowercase() == "load"){
            loadPreset(sender)
            return true
        }

        return false
    }

    /** Create and fill overview GUI */
    private fun classSelectorGUI(player: Player): Inventory{
        val inventory = Bukkit.createInventory(player, inventorySize, Component.text(overview))

        /** Get skill item stacks from player data object */
        val playerData = plugin.players[player.uniqueId]!!  
        val classItemList = playerData.getClassIcons()

        /** Add skill items to the inventory */
        var index = 11 //TODO set to 10 if new class is added
        for(skill in classItemList){
            inventory.setItem(index, skill)
            index += 1
        }

        val skillItemList = playerData.getSkillIcons()
        index = 29 //TODO set to 28 if new class is added
        for(skill in skillItemList){
            inventory.setItem(index, skill)
            index += 1
        }

        /** Block remaining inventory spaces and open inventory */
        return createBlockers(inventory, inventorySize - skillItemList.size)
    }

    /** Removes all skills from the given inventory */
    private fun removeAllSkills(inventory: Inventory){
        val inventoryIterator = inventory.iterator()
        while(inventoryIterator.hasNext()){
            val currentItem = inventoryIterator.next() ?: continue

            if(((currentItem.lore() ?: continue)[0] as? TextComponent ?: continue).content().startsWith("skill")){
                inventory.setItem(inventoryIterator.previousIndex(), null)
            }
        }
    }

    /** Click spam in empty slots with item on cursor can lead to the item getting transferred (client sided)
     * to prevent this all empty slots get filled with glass */
    private fun createBlockers(inventory: Inventory, requiredBlockers: Int): Inventory{
        val blocker = ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE)
        val meta = blocker.itemMeta
        for(i in 1 .. requiredBlockers){
            meta.displayName(Component.text("Blocker$i", TextColor.color(0)))
            blocker.itemMeta = meta
            inventory.addItem(blocker)
        }
        return inventory
    }

    /** Loads the saved preset values (if any), checks if they are free and adds the skills if possible */
    private fun loadPreset(player: Player){
        plugin.players[player.uniqueId]?.loadPreset(player.inventory)
    }

    /** Saves the players current ability placements as a preset for the active class */
    private fun savePreset(player: Player){
        val preset = arrayOf(-1, -1, -1, -1)
        val playerData = plugin.players[player.uniqueId] ?: return
        val abilities = Classes.valueOf(playerData.selectedClass).abilities

        for(slot in 0..8){
            val lore = (player.inventory.getItem(slot)?.lore()?.get(0) as? TextComponent)?.content() ?: continue
            if(!lore.startsWith("skill")){
                continue
            }

            for((i, ability) in abilities.withIndex()){
                if(lore.endsWith(ability.name.lowercase())){
                    preset[i] = slot
                    break
                }
            }
        }

        playerData.savePreset(preset)
        player.sendMessage("${ChatColor.GREEN}Preset saved.")
    }
}