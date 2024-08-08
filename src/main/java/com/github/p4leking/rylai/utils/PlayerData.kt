/** © 2024 Bernhard Eierle. All rights reserved. */

package com.github.p4leking.rylai.utils

import com.github.p4leking.rylai.Rylai
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import org.bukkit.ChatColor
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import java.util.*

class PlayerData(private val plugin: Rylai, private val uuid: UUID) {
    private val configData = plugin.playerConfig.config.getConfigurationSection("$uuid")

    /** Player data */
    var group : String?
    var selectedClass : String
    var combat : LevelData
    private var preset : Array<Int>
    val mining : LevelData
    val fishing : LevelData
    val woodcutting : LevelData
    val smithing : LevelData
    val alchemy : LevelData

    /** Load player data from config or set defaults for new players */
    init{
        if(configData == null){
            group = null

            /** Combat */
            selectedClass = Classes.WARRIOR.name
            combat = LevelData()
            preset = arrayOf()

            /** Crafts */
            mining = LevelData()
            fishing = LevelData()
            woodcutting = LevelData()
            smithing = LevelData()
            alchemy = LevelData()
        }else{
            group = configData.getString("group")

            /** Combat */
            selectedClass = configData.getString("class") ?: Classes.WARRIOR.name
            combat = LevelData(configData.getInt("${selectedClass}.Level", 1),
                    configData.getInt("${selectedClass}.XP"))
            preset = configData.getIntegerList("preset.$selectedClass").toTypedArray()

            /** Crafts */
            mining = LevelData(configData.getInt("${CraftingSkills.MINING.name}.Level", 1),
                    configData.getInt("${CraftingSkills.MINING.name}.XP"))
            fishing = LevelData(configData.getInt("${CraftingSkills.FISHING.name}.Level", 1),
                    configData.getInt("${CraftingSkills.FISHING.name}.XP"))
            woodcutting = LevelData(configData.getInt("${CraftingSkills.WOODCUTTING.name}.Level", 1),
                    configData.getInt("${CraftingSkills.WOODCUTTING.name}.XP"))
            smithing = LevelData(configData.getInt("${CraftingSkills.SMITHING.name}.Level", 1),
                    configData.getInt("${CraftingSkills.SMITHING.name}.XP"))
            alchemy = LevelData(configData.getInt("${CraftingSkills.ALCHEMY.name}.Level", 1),
                    configData.getInt("${CraftingSkills.ALCHEMY.name}.XP"))
        }
    }

    /** Set that contains all skill variables */
    val skills = mapOf(Pair(CraftingSkills.MINING, mining), Pair(CraftingSkills.FISHING, fishing),
            Pair(CraftingSkills.WOODCUTTING, woodcutting), Pair(CraftingSkills.SMITHING, smithing),
            Pair(CraftingSkills.ALCHEMY, alchemy))

    /** Loads the players title if they have one */
    var title = loadTitle()
    private fun loadTitle(): String? {
        val titleString = plugin.persistentConfig.config.getString("$uuid.title") ?: return null
        return "${ChatColor.DARK_GRAY}<${titleString}${ChatColor.DARK_GRAY}>"
    } //TODO bei Spielertitel eingabe keine sonderzeichen erlauben!!

    /** Method that changes the active class of this player to the specified new class */
    //TODO create a lastClassSwap variable. only allow switch if last switch was longer than 1d? ago.
    // don't give switch cooldown if the player is op or has rank.
    fun switchClass(newClass: Classes): Boolean{
        if(newClass.name == selectedClass){
            return false
        }

        /** Save switch to config */
        plugin.playerConfig.config["$uuid.class"] = newClass.name
        plugin.playerConfig.config["$uuid.$selectedClass.Level"] = combat.level
        plugin.playerConfig.config["$uuid.$selectedClass.XP"] = combat.experience

        /** Override LevelData object with new values */
        combat = LevelData(plugin.playerConfig.config.getInt("$uuid.${newClass.name}.Level", 1),
                plugin.playerConfig.config.getInt("$uuid.${newClass.name}.XP", 0))

        /** Set new class as selected class */
        selectedClass = newClass.name

        /** Update & load preset */
        val newPreset = plugin.playerConfig.config.getIntegerList("$uuid.preset.${newClass.name}")

        if(newPreset.isEmpty()){
            preset = arrayOf()
        }else{
            preset = newPreset.toTypedArray()
            loadPreset((plugin.server.getPlayer(uuid) ?: return true).inventory)
        }

        return true
    }

    /** Method that saves the contents of this object to the config */
    fun saveToConfig(){
        saveSkillToConfig(selectedClass, combat)
        for(skill in skills){
            saveSkillToConfig(skill.key.name, skill.value)
        }
    }

    private fun saveSkillToConfig(skillID: String, skill: LevelData){
        plugin.playerConfig.config["$uuid.${skillID}.Level"] = skill.level
        plugin.playerConfig.config["$uuid.${skillID}.XP"] = skill.experience
    }

    fun getClassIcons(): ArrayList<ItemStack> {
        val classList = arrayListOf<ItemStack>()

        /** Create class icons */
        for(skill in Classes.values()){
            if (skill.name == selectedClass){
                classList.add(combat.generateSkillItem(skill))
                continue
            }

            val item = ItemStack(skill.icon)

            /** Edit the item meta */
            val meta = item.itemMeta
            meta.displayName(Component.text(skill.className))

            /** Add level and description to the lore - remove config access if performance sucks */
            val text : ArrayList<Component> = ArrayList()
            text.add(Component.text("Level: ${plugin.playerConfig.config.getInt("$uuid.${skill.name}.Level", 1)}",
                    TextColor.color(100, 0, 100)))
            text.add(Component.text(skill.description, TextColor.color(39,223,236)))
            meta.lore(text)

            /** Override and save */
            item.itemMeta = meta
            classList.add(item)
        }

        return classList
    }

    fun getSkillIcons(): ArrayList<ItemStack> {
        val skillList = arrayListOf<ItemStack>()

        /** Create crafting skill icons */
        for(skill in skills){
            skillList.add(skill.value.generateSkillItem(skill.key))
        }

        return skillList
    }

    fun savePreset(newPreset: Array<Int>){
        preset = newPreset
        plugin.playerConfig.config["$uuid.preset.$selectedClass"] = newPreset
    }

    fun loadPreset(inv: Inventory){
        val abilities = (Classes.values().find { it.name == selectedClass } ?: return).abilities
        for((i, pos) in preset.withIndex()){

            /** Skip unbound abilities */
            if(pos == -1){
                continue
            }

            /** Don't override items */
            if(inv.getItem(pos) != null){
                continue
            }

            /** Add ability */
            val ability = abilities[i]
            inv.setItem(pos, plugin.getItem(ItemStack(ability.icon), ability.abilityName,
                    "skill:$selectedClass:${ability.name.lowercase()}", "Cooldown: ${ability.cooldown / 20}s",
                    *ability.description))
        }
    }
}