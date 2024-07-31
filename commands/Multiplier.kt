package com.github.p4leking.rylai.commands

import com.github.p4leking.rylai.utils.LevelData
import org.bukkit.ChatColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter

class Multiplier : CommandExecutor, TabCompleter {
    override fun onTabComplete(sender: CommandSender, command: Command, label: String, args: Array<out String>): MutableList<String> {
        return mutableListOf()
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if(args.isEmpty()){
            sender.sendMessage("${ChatColor.YELLOW}Please enter a value to change the server wide XP multiplier to. (e.g. 1.5 for a 50% boost)")
            return false
        }

        val newMultiplier = args[0].toDoubleOrNull()

        if(newMultiplier == null || newMultiplier < 1.0){
            sender.sendMessage("${ChatColor.DARK_RED}Invalid multiplier value")
            return false
        }

        LevelData.xpMultiplier = newMultiplier
        sender.sendMessage("${ChatColor.GREEN}Updated global XP multiplier to $newMultiplier")
        return true
    }

}