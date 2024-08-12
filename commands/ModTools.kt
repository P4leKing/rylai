/** © 2024 Bernhard Eierle. All rights reserved. */

package com.github.p4leking.rylai.commands

import com.github.p4leking.rylai.Rylai
import org.bukkit.ChatColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.util.*

class ModTools(private val plugin: Rylai) : Listener, CommandExecutor, TabCompleter {
    companion object{
        val muted = mutableSetOf<UUID>()
    }

    override fun onTabComplete(sender: CommandSender, command: Command, label: String, args: Array<out String>): MutableList<String> {
        if(label.equals("modtools", true)){
            return mutableListOf()
        }

        if(args.size != 1){
            return mutableListOf()
        }

        return plugin.server.onlinePlayers
            .filter { it.name.startsWith(args[0], true) }
            .map { it.name }.toMutableList()
    }

    //TODO test muting others, esp. with relog and restart persistence and functionality in different chat events

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if(label.equals("modtools", true)){
            //TODO mby custom message?
            return false
        }

        val bannedPlayer = plugin.server.getPlayer(args[1]) ?: run{
            sender.sendMessage("${ChatColor.RED}There is no online Player with that name.")
            return true
        }

        if(label.equals("unmute", true)){
            if(muted.remove(bannedPlayer.uniqueId)){
                bannedPlayer.sendMessage("${ChatColor.GREEN}You have been unmuted.")
            }
            sender.sendMessage("${ChatColor.GOLD}${bannedPlayer.name}${ChatColor.GREEN} has been unmuted.")
            return true
        }

        if(bannedPlayer.isOp){
            sender.sendMessage("${ChatColor.DARK_RED}I'm sorry Dave, I'm afraid I can't do that.")
            return true
        }

        if(muted.add(bannedPlayer.uniqueId)){
            sender.sendMessage("${ChatColor.GOLD}${bannedPlayer.name}${ChatColor.DARK_GREEN} has been muted.")
            bannedPlayer.sendMessage("${ChatColor.RED}You have been muted.")
        }else{
            sender.sendMessage("${ChatColor.GOLD}${bannedPlayer.name}${ChatColor.DARK_GREEN} is already muted. " +
                    "To unmute them use /unmute ${bannedPlayer.name}")
        }
        return true
    }

    @EventHandler
    fun loadMutes(event: PlayerJoinEvent){
        val id = event.player.uniqueId
        if(plugin.persistentConfig.config.getBoolean("${id}.muted")){
            muted.add(id)
        }
    }

    @EventHandler
    fun saveMutes(event: PlayerQuitEvent){
        val id = event.player.uniqueId
        if(muted.remove(id)){
            plugin.persistentConfig.config["$id.muted"] = true
        }
    }
}