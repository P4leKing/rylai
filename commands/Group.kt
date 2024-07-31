package com.github.p4leking.rylai.commands

import com.github.p4leking.rylai.Rylai
import com.github.p4leking.rylai.utils.GroupData
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import org.bukkit.ChatColor
import org.bukkit.OfflinePlayer
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import java.util.*

class Group(private val plugin: Rylai) : CommandExecutor, TabCompleter {
    private val groupInvites = mutableMapOf<UUID, String>()
    private val subcommands = setOf("create", "invite", "accept", "leave", "tag", "kick", "promote", "demote", "info", "help")

    companion object{
        val groupChat = mutableSetOf<UUID>()
    }

    /** This function returns the group object for the given player or null if there isn't one */
    private fun getGroup(player: Player): GroupData?{
        return plugin.groups[plugin.players[player.uniqueId]!!.group] ?: run {  
            player.sendMessage("${ChatColor.DARK_RED}You hit yourself in confusion.")
            player.damage(1.0)
            return null
        }
    }

    private fun getSubject(player: Player, subjectName: String): OfflinePlayer?{
        return plugin.server.getOfflinePlayerIfCached(subjectName) ?: run {
            player.sendMessage("${ChatColor.YELLOW}No Player with this name was found in the servers cache.")
            return null
        }
    }

    override fun onTabComplete(sender: CommandSender, command: Command, label: String, args: Array<out String>): MutableList<String> {
        if(args.isEmpty()){
            return subcommands.toMutableList()
        }
        if(args[0] in setOf("invite", "kick", "promote", "demote")){
            if(args.size != 2){
                return mutableListOf()
            }
            val players = mutableListOf<String>()
            for(player in plugin.server.onlinePlayers){
                if(player.name.startsWith(args[1], true)){
                    players.add(player.name)
                }
            }
            return players
        }
        if(args[0] in setOf("info", "create", "accept", "leave", "tag", "help")){
            return mutableListOf()
        }
        val params = subcommands.toMutableList()
        params.retainAll { s -> s.startsWith(args[0], true) }
        return params
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("Only Players can run this command.")
            return true
        }

        if(args.isEmpty()) {
            if(plugin.players[sender.uniqueId]!!.group == null){  
                sender.sendMessage("${ChatColor.YELLOW}You have not joined a group yet. " +
                        "For more information use /g help.")
                return true
            }

            if(groupChat.add(sender.uniqueId)){
                Chat.localChat.remove(sender.uniqueId)
                Chat.tradeChat.remove(sender.uniqueId)
                sender.sendMessage("${ChatColor.AQUA}Group chat activated.")
            }
            return true
        }

        /** group help */
        if(args[0].lowercase() == "help"){
            /** Send list of commands to the player */
            sender.sendMessage("${ChatColor.GRAY}---------------------\n" +
                    "${ChatColor.BLUE}/g info <groupName>: ${ChatColor.AQUA}Displays information about this" +
                    " group or your own if none was specified.\n \n" +
                    "${ChatColor.BLUE}/g create <groupName>: ${ChatColor.AQUA}Creates a group with the " +
                    "specified name if it is available.\n \n" +
                    "${ChatColor.BLUE}/g invite <playerName>: ${ChatColor.AQUA}Invites this player to " +
                    "join your group.\n \n" +
                    "${ChatColor.BLUE}/g accept: ${ChatColor.AQUA}Accepts the latest pending invitation.\n \n" +
                    "${ChatColor.BLUE}/g leave [confirm]: ${ChatColor.AQUA}Removes you from your current " +
                    "group. If you are the leader of your group, /g leave confirm disbands your group. If you are " +
                    "the leader of your group and want to leave your group but not disband it, please transfer group " +
                    "leadership to another player first.\n \n" +
                    "${ChatColor.BLUE}/g tag <groupTag>: ${ChatColor.AQUA}Sets the group tag that is " +
                    "displayed on tab your group.\n \n" +
                    "${ChatColor.BLUE}/g kick <playerName>: ${ChatColor.AQUA}Kicks this player out of the group.\n \n" +
                    "${ChatColor.BLUE}/g promote <playerName> [confirm]: ${ChatColor.AQUA}Promotes this " +
                    "player to admin rank. Promoting an admin to transfer leadership requires confirmation.\n \n" +
                    "${ChatColor.BLUE}/g demote <playerName>: ${ChatColor.AQUA}Removes the players admin " +
                    "rank if they have one.\n" +
                    "${ChatColor.GRAY}---------------------")
            return true
        }

        /** group info */
        if(args[0].lowercase() == "info"){
            val group = if(args.size > 1){
                plugin.groups[args[1]] ?: run {
                    sender.sendMessage("${ChatColor.DARK_RED}This group does not exist.")
                    return true
                }
            }else{
                plugin.groups[plugin.players[sender.uniqueId]!!.group] ?: run {  
                    sender.sendMessage("${ChatColor.YELLOW}You have not joined a group yet. Use ${ChatColor.GOLD}/g create${ChatColor.YELLOW} to create one" +
                            " and ${ChatColor.GOLD}/g help${ChatColor.YELLOW} to see all commands.")
                    return true
                }
            }

            group.info(sender)
            return true
        }

        /** group leave */
        if(args[0].lowercase() == "leave"){
            val group = getGroup(sender) ?: return true

            /** Leader leaves */
            if(group.leader == sender.uniqueId){

                /** Disband group if confirmed */
                if(args.size > 1 && args[1].lowercase() == "confirm"){
                    group.disbandGroup()
                    return true
                }

                /** Explain group disbanding */
                sender.sendMessage("${ChatColor.YELLOW}You are the leader of your group. When you leave, the group will" +
                        " get disbanded. If you don't want to disband the group you can transfer group leadership" +
                        " by using ${ChatColor.GOLD}/group promote <name>${ChatColor.YELLOW} confirm on any admin of your group. " +
                        "To confirm you want to disband the group type: ${ChatColor.GOLD}/g leave confirm")
                return true
            }

            /** Remove player from group */
            group.removePlayer(sender)
            sender.sendMessage("${ChatColor.YELLOW}You left the group ${ChatColor.GOLD}${group.name}${ChatColor.YELLOW}.")

            /** Notify others */
            val msg = "${ChatColor.GOLD}${sender.name}${ChatColor.DARK_RED} has left ${ChatColor.GOLD}${group.name}${ChatColor.DARK_RED}."
            for(member in group.members + group.admins + group.leader) {
                plugin.server.getPlayer(member)?.sendMessage(msg)
            }
            return true
        }

        /** group accept */
        if(args[0].lowercase() == "accept") {

            /** Check if player has an invitation */
            val groupName = groupInvites[sender.uniqueId] ?: run {
                sender.sendMessage("${ChatColor.DARK_RED}Seems like nobody wants you in their group.")
                return true
            }

            if(plugin.players[sender.uniqueId]!!.group != null) {  
                sender.sendMessage("${ChatColor.DARK_RED}You must leave your current group " +
                        "before accepting an invitation from another group.")
                return true
            }

            val group = plugin.groups[groupName] ?: run {
                sender.sendMessage("${ChatColor.DARK_RED}The group you are trying to join no longer exists.")
                groupInvites.remove(sender.uniqueId)
                return true
            }

            /** Add player to the group */
            groupInvites.remove(sender.uniqueId)
            group.addMember(sender)
            return true
        }

        /** One word group chat message. */
        if(args.size == 1) {
            plugin.groupChatMessage(sender, args[0])
            return true
        }

        /** group invite <name> */
        if(args[0].lowercase() == "invite"){

            /** Check if sender has a group */
            val group = getGroup(sender) ?: return true

            /** Only the leader and admins can invite */
            if(group.members.contains(sender.uniqueId)){
                sender.sendMessage("${ChatColor.DARK_RED}I'm sorry Dave, I'm afraid I can't do that.")
                return true
            }

            /** Check if target is online */
            val target = plugin.server.getPlayer(args[1]) ?: run {
                sender.sendMessage("${ChatColor.YELLOW}There is no online player with that name.")
                return true
            }

            /** Don't invite people that have the sender ignored */
            if(Chat.isIgnoredBy(sender.uniqueId, target.uniqueId)){
                return true
            }

            /** Legitimate invite - inviting yourself while still in the group before leaving is possible */
            sender.sendMessage("${ChatColor.GREEN}You invited ${ChatColor.GREEN}${target.name}${ChatColor.GREEN} to join ${ChatColor.GREEN}${group.name}.")

            val msg = Component.text("${ChatColor.GOLD}${sender.name}${ChatColor.GREEN} has invited you to " +
                    "join ${ChatColor.GOLD}${group.name}${ChatColor.GREEN}. " +
                    "Type ${ChatColor.GOLD}/g accept${ChatColor.GREEN} or click on this message to accept the most recent group invitation.")
                    .clickEvent(ClickEvent.runCommand("/g accept"))
            groupInvites[target.uniqueId] = group.name
            target.sendMessage(msg)
            return true
        }

        /** group promote <name> */
        if(args[0].lowercase() == "promote"){
            val group = getGroup(sender) ?: return true
            val subject = getSubject(sender, args[1]) ?: return true

            /** Allow leadership transfer if the promotion is confirmed */
            if(args.size == 3 && args[2].lowercase() == "confirm"){
                group.promote(sender, subject, true)
            }else {
                group.promote(sender, subject, false)
            }
            return true
        }

        /** group demote <name> */
        if(args[0].lowercase() == "demote"){
            val group = getGroup(sender) ?: return true
            val subject = getSubject(sender, args[1]) ?: return true
            group.demote(sender, subject)
            return true
        }

        /** group kick <name> */
        if(args[0].lowercase() == "kick"){
            val group = getGroup(sender) ?: return true
            val subject = getSubject(sender, args[1]) ?: return true
            group.kickMember(sender, subject)
            return true
        }

        /** group tag <tag> */
        if(args[0].lowercase() == "tag"){
            val group = getGroup(sender) ?: return true
            group.setTag(sender, args[1])
            return true
        }

        /** group create <name> */
        if(args[0].lowercase() == "create"){
            if(plugin.players[sender.uniqueId]!!.group != null){  
                sender.sendMessage("${ChatColor.YELLOW}Please leave your current group before creating a new one.")
                return true
            }

            val groupName = args[1]

            /** Check if the name is taken */
            for(groups in plugin.groups.entries){
                if(groups.key == groupName){
                    sender.sendMessage("${ChatColor.DARK_RED}A group with this name already exists.")
                    return true
                }
            }

            /** Create the group */
            plugin.groups[args[1]] = GroupData(plugin, groupName, null, sender.uniqueId, mutableSetOf(), mutableSetOf())

            /** Add it to the leaders player data */
            plugin.players[sender.uniqueId]!!.group = groupName  
            plugin.playerConfig.config["${sender.uniqueId}.group"] = groupName

            sender.sendMessage("${ChatColor.GREEN}You created the group ${ChatColor.GOLD}${groupName}${ChatColor.GREEN}." +
                    " Go invite some people using ${ChatColor.GOLD}/g invite <name>")
            return true
        }

        /** Build and send a long group chat message. */
        var msg = ""
        for (arg in args) {
            msg += "$arg "
        }
        plugin.groupChatMessage(sender, msg)
        return true
    }
}