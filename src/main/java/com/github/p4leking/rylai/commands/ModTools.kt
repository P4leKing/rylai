/** © 2024 Bernhard Eierle. All rights reserved. */

package com.github.p4leking.rylai.commands

import com.github.p4leking.rylai.Rylai
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter

class ModTools(private val plugin: Rylai) : CommandExecutor, TabCompleter {
    override fun onTabComplete(sender: CommandSender, command: Command, label: String, args: Array<out String>): MutableList<String>? {
        if(label == "chatban"){
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

        //TODO test returning null instead of an empty list - performance might be better since i dont create a useless object?
        return null
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if(!sender.isOp){ //TODO allow other ranks with luckperms
            return false
        }

        if(label == "chatban"){

        }
        TODO("Not yet implemented")
    }
}