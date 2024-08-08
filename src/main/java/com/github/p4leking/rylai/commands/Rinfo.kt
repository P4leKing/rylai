/** © 2024 Bernhard Eierle. All rights reserved. */

package com.github.p4leking.rylai.commands

import com.github.p4leking.rylai.Rylai
import com.github.p4leking.rylai.utils.Classes
import com.github.p4leking.rylai.utils.CraftingSkills
import org.bukkit.ChatColor
import org.bukkit.OfflinePlayer
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class Rinfo(private val plugin: Rylai) : CommandExecutor, TabCompleter {

    override fun onTabComplete(sender: CommandSender, command: Command, label: String, args: Array<out String>): MutableList<String> {
        if(args.size != 1){
            return mutableListOf()
        }

        val players = mutableListOf<String>()
        for(player in plugin.server.onlinePlayers){
            if(player.name.startsWith(args[0], true)){
                players.add(player.name)
            }
        }
        return players
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("Only Players can run this command.")
            return true
        }

        /** Get info about the sender */
        if(args.isEmpty()){
            info(sender, sender)
            return true
        }

        if(args.size > 1){
            return false
        }

        /** Get info about the specific player */
        val name = args[0]
        val player = plugin.server.getPlayer(name) ?: plugin.server.getOfflinePlayerIfCached(name) ?: return false

        info(sender, player)
        return true
    }

    /** Create and send info about any player to the sender */
    private fun info(sender: Player, player: OfflinePlayer){
        val group : String
        var combatSkills = ""
        var craftingSkills = ""

        val configData = plugin.playerConfig.config.getConfigurationSection("${player.uniqueId}")

        /** New Players don't have config data yet */
        if(configData == null){
            sender.sendMessage("${ChatColor.GOLD}${player.name}${ChatColor.DARK_AQUA} is a new Player, go say hello!")
            return
        }

        if(player.isOnline){
            val data = plugin.players[player.uniqueId] ?: return

            /** Get group */
            group = data.group ?: ""

            /** Get list of all combat skills and corresponding levels */
            for(skill in Classes.values()){
                val name = skill.name
                combatSkills += if(name == data.selectedClass){
                    if(combatSkills == ""){
                        "${ChatColor.DARK_PURPLE}${skill.className}${ChatColor.DARK_AQUA}: ${ChatColor.GOLD}${data.combat.level}"
                    }else{
                        "${ChatColor.DARK_AQUA}, ${ChatColor.DARK_PURPLE}${skill.className}${ChatColor.DARK_AQUA}: ${ChatColor.GOLD}${data.combat.level}"
                    }
                }else{
                    levelString(combatSkills == "", skill.className, configData.getInt("$name.Level", 1))
                }
            }

            /** Get list of all crafting skills and corresponding levels */
            for(skill in data.skills){
                craftingSkills += levelString(craftingSkills == "", skill.key.className, skill.value.level)
            }
        }else{

            /** Get group */
            group = configData.getString("group") ?: ""

            /** Get list of all combat skills and corresponding levels */
            for(skill in Classes.values()){
                combatSkills += levelString(combatSkills == "", skill.className,
                        configData.getInt("${skill.name}.Level", 1))
            }

            /** Get list of all crafting skills and corresponding levels */
            for(skill in CraftingSkills.values()){
                craftingSkills += levelString(craftingSkills == "", skill.className,
                        configData.getInt("${skill.name}.Level", 1))
            }
        }

        sender.sendMessage("${ChatColor.GRAY}---------------------------\n" +
                "${ChatColor.GOLD}${player.name}\n \n" +
                "${ChatColor.BLUE}Group: ${ChatColor.DARK_AQUA}$group\n \n" +
                "${ChatColor.BLUE}Combat: $combatSkills\n \n" +
                "${ChatColor.BLUE}Trade: $craftingSkills\n" +
                "${ChatColor.GRAY}---------------------------")
    }

    private fun levelString(isFirst: Boolean, name: String, level: Int): String{
        return if(isFirst){
            "${ChatColor.DARK_AQUA}$name: ${ChatColor.GOLD}$level"
        }else{
            "${ChatColor.DARK_AQUA}, $name: ${ChatColor.GOLD}$level"
        }
    }
}