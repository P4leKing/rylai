/** © 2024 Bernhard Eierle. All rights reserved. */

package com.github.p4leking.rylai.commands

import com.github.p4leking.rylai.Rylai
import com.github.p4leking.rylai.utils.Classes
import com.github.p4leking.rylai.utils.CraftingSkills
import com.github.p4leking.rylai.utils.SkillRanking
import org.bukkit.ChatColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import java.util.*

class Ranking(private val plugin: Rylai) : CommandExecutor, TabCompleter {
    private val warrior = SkillRanking(plugin, Classes.WARRIOR.name)
    private val assassin = SkillRanking(plugin, Classes.ASSASSIN.name)
    private val mage = SkillRanking(plugin, Classes.MAGE.name)
    private val archer = SkillRanking(plugin, Classes.ARCHER.name)
    private val hunter = SkillRanking(plugin, Classes.HUNTER.name)
    private val mining = SkillRanking(plugin, CraftingSkills.MINING.name)
    private val fishing = SkillRanking(plugin, CraftingSkills.FISHING.name)
    private val woodcutting = SkillRanking(plugin, CraftingSkills.WOODCUTTING.name)
    private val smithing = SkillRanking(plugin, CraftingSkills.SMITHING.name)
    private val alchemy = SkillRanking(plugin, CraftingSkills.ALCHEMY.name)

    /** Used to iterate through all rankings and access a specific ranking by name */
    val allRankings = setOf(warrior, assassin, mage, archer, hunter, mining, fishing, woodcutting, smithing, alchemy)
            .associateBy { it.name }

    /** Total ranking only gets updated on restart */
    private val numberOfSkills = allRankings.size
    private val total = generateTotalRanking()

    /** Generate the total ranking list - updated at every server restart */
    private fun generateTotalRanking(): ArrayList<Triple<UUID, Int, Int>>{
        val allRankingEntries = mutableListOf<Triple<UUID,Int,Int>>()
        allRankings.forEach { allRankingEntries += it.value.ranking }

        /** Players by amount of entries in the rankings */
        val possibleEntries = mutableMapOf<UUID, Int>()

        /** Calc amount of entries in different rankings each player has */
        for(rankingEntry in allRankingEntries){
            val entryNum = possibleEntries[rankingEntry.first] ?: 0
            possibleEntries[rankingEntry.first] = entryNum + 1
        }

        /** Sort by number of entries */
        val distribution = mutableMapOf<Int, MutableSet<UUID>>()
        for(entry in possibleEntries){
            val currentDist = distribution[entry.value] ?: mutableSetOf()
            currentDist.add(entry.key)
            distribution[entry.value] = currentDist
        }

        /** Add players with the highest number of total entries to the list */
        val qualifiedPlayers = arrayListOf<Triple<UUID, Int, Int>>()
        for(i in numberOfSkills downTo 1){
            if(qualifiedPlayers.size > 10){
                break
            }

            /** Calc total level */
            for(playerID in distribution[i] ?: continue){
                val totalLevel = calcTotalLevel(playerID)
                qualifiedPlayers.add(Triple(playerID, totalLevel.first, totalLevel.second))
            }

            /** Sort players who have maxed every skill by total xp */
            if(i == numberOfSkills){
                qualifiedPlayers.sortByDescending{it.third}
            }
        }

        /** Sort list by total level - Should not mess with the xp sort for players who maxed every skill */
        qualifiedPlayers.sortByDescending{it.second}
        return qualifiedPlayers
    }

    /** Calculates the players total level (and xp if max level) */
    private fun calcTotalLevel(playerID: UUID): Pair<Int, Int>{
        var totalLevel = 0
        for(combatClass in Classes.values()){
            totalLevel += plugin.playerConfig.config.getInt("$playerID.${combatClass.name}.Level", 1)
        }
        for(craftingSkill in CraftingSkills.values()){
            totalLevel += plugin.playerConfig.config.getInt("$playerID.${craftingSkill.name}.Level", 1)
        }

        /** Only return total level if below max level */
        if(totalLevel < numberOfSkills * 100){
            return Pair(totalLevel, 0)
        }

        /** Calc total xp */
        var totalXP = 0
        for(combatClass in Classes.values()){
            totalXP += plugin.playerConfig.config.getInt("$playerID.${combatClass.name}.XP", 0)
        }
        for(craftingSkill in CraftingSkills.values()){
            totalXP += plugin.playerConfig.config.getInt("$playerID.${craftingSkill.name}.XP", 0)
        }
        return Pair(totalLevel, totalXP)
    }


    /** Provide suggestions for the commands input string */
    override fun onTabComplete(sender: CommandSender, command: Command, label: String, args: Array<out String>): MutableList<String> {
        if(args.isEmpty()){
            return mutableListOf()
        }

        /** Fill list with matches for input string */
        val params = mutableListOf<String>()

        CraftingSkills.values()
            .filter { it.className.startsWith(args[0], true) }
            .mapTo(params) { it.className }

        Classes.values()
            .filter { it.className.startsWith(args[0], true) }
            .mapTo(params) { it.className }

        return params
    }

    /** Command for looking at the rankings in the different skills */
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("Only Players can run this command.")
            return true
        }

        if(args.isEmpty()) {
            sendRankingMessage(sender, total, "${ChatColor.DARK_AQUA}Top players overall:\n \n")
            return true
        }

        val ranking = allRankings[args[0].uppercase()]?.ranking ?: run {
            sender.sendMessage("${ChatColor.DARK_RED}No ranking with that name was found.")
            return false
        }

        sendRankingMessage(sender, ranking, "${ChatColor.DARK_AQUA}Top players by ${ChatColor.GOLD}${args[0]}${ChatColor.DARK_AQUA} level:\n \n")

        return true
    }

    private fun sendRankingMessage(sender: Player, ranking: ArrayList<Triple<UUID, Int, Int>>, title: String){
        var msg = "${ChatColor.GRAY}---------------------------\n" + title

        var i = 1
        for(entry in ranking){
            val stringIndex = if(i < 10){
                "$i. "
            }else{
                "$i."
            }
            msg += if(entry.second == 100){
                "${ChatColor.BLUE}$stringIndex ${ChatColor.GOLD}${plugin.server.getOfflinePlayer(entry.first).name}" +
                        "${ChatColor.BLUE} - Level: ${ChatColor.GOLD}${entry.second}" +
                        "${ChatColor.BLUE}, XP: ${ChatColor.GOLD}${entry.third}\n"
            }else{
                "${ChatColor.BLUE}$stringIndex. ${ChatColor.GOLD}${plugin.server.getOfflinePlayer(entry.first).name}" +
                        "${ChatColor.BLUE} - Level: ${ChatColor.GOLD}${entry.second}\n"
            }
            i++
        }

        msg += "${ChatColor.GRAY}---------------------------"
        sender.sendMessage(msg)
    }

    /** Save lists to config */
    fun saveToConfig(){
        allRankings.forEach{ it.value.saveToConfig() }
        plugin.rankingConfig.save()
    }
}