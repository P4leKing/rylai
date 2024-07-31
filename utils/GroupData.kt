package com.github.p4leking.rylai.utils

import com.github.p4leking.rylai.Rylai
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import org.bukkit.ChatColor
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import java.util.*

class GroupData(private val plugin: Rylai, val name: String, var tag: String?,
                var leader: UUID, val admins: MutableSet<UUID>, val members: MutableSet<UUID>) {

    /** Kicks a member from the group if possible */
    fun kickMember(sender: Player, subject: OfflinePlayer){

        /** Only kick the subject if it belongs to the same group and its rank is lower than the senders */
        if(sender.uniqueId == leader){
            if(subject.uniqueId !in admins + members) {
                sender.sendMessage("${ChatColor.DARK_RED}You hit yourself in confusion.")
                sender.damage(1.0)
                return
            }
        }else if(!(admins.contains(sender.uniqueId) || members.contains(subject.uniqueId))){
            sender.sendMessage("${ChatColor.DARK_RED}You hit yourself in confusion.")
            sender.damage(1.0)
            return
        }

        /** Kick the subject */
        removePlayer(subject)

        /** Notify sender & subject */
        sender.sendMessage("${ChatColor.YELLOW}You have kicked ${ChatColor.GOLD}${subject.name}${ChatColor.YELLOW} from the group.")
        subject.player?.sendMessage("${ChatColor.GOLD}${sender.name}${ChatColor.DARK_RED} kicked you out of ${ChatColor.GOLD}$name${ChatColor.DARK_RED}.")

        /** Notify remaining members */
        val msg = "${ChatColor.GOLD}${sender.name}${ChatColor.YELLOW} kicked ${ChatColor.GOLD}${subject.name}${ChatColor.YELLOW} out of ${ChatColor.GOLD}$name${ChatColor.YELLOW}."
        for (playerID in members + admins + leader) {
            if(playerID == sender.uniqueId || playerID == subject.uniqueId){
                continue
            }
            plugin.server.getPlayer(playerID)?.sendMessage(msg)
        }
    }

    /** Removes a member from the group */
    fun removePlayer(member: OfflinePlayer){

        /** Remove group from playerData (if online) and config */
        plugin.players[member.uniqueId]?.group = null
        plugin.playerConfig.config["${member.uniqueId}.group"] = null

        /** Remove player from member or admin list */
        admins.remove(member.uniqueId)
        members.remove(member.uniqueId)

        /** Remove group tag */
        val online = member.player ?: return
        online.playerListName(online.displayName())
    }

    /** Adds a new member to the group */
    fun addMember(member: Player){

        /** Notify other members */
        message(Component.text("${ChatColor.GOLD}${member.name}${ChatColor.AQUA} has joined ${ChatColor.GOLD}$name${ChatColor.AQUA}."))

        /** Add player to the group */
        member.sendMessage("${ChatColor.AQUA}You have joined $name.")
        members.add(member.uniqueId)

        /** Add group to playerData and config */
        plugin.players[member.uniqueId]!!.group = name  
        plugin.playerConfig.config["${member.uniqueId}.group"] = name

        /** Give player group tag if the group has one */
        member.playerListName()
        if(tag != null){
            member.playerListName(Component.text(
                    "${ChatColor.DARK_GRAY}<${ChatColor.AQUA}${tag}${ChatColor.DARK_GRAY}>")
                    .append(member.displayName()))
        }
    }

    /** Disbands the group */
    fun disbandGroup(){

        /** Remove group from player data and config */
        for(playerID in members + admins + leader){
            plugin.players[playerID]?.group = null
            plugin.playerConfig.config["$playerID.group"] = null

            /** If online notify them and remove group tag */
            val onlineMember = plugin.server.getPlayer(playerID) ?: continue
            onlineMember.playerListName(onlineMember.displayName())
            onlineMember.sendMessage("${ChatColor.DARK_RED}Your group has been disbanded.")
        }

        /** Delete this object */
        plugin.groups.remove(name)
        plugin.groupConfig.config[name] = null
    }

    /** Promotes the subject if valid */
    fun promote(sender: Player, subject: OfflinePlayer, confirmed: Boolean){
        if(sender.uniqueId != leader){
            sender.sendMessage("${ChatColor.DARK_RED}I'm sorry Dave, I'm afraid I can't do that.")
            return
        }

        /** Promote subject and notify both (if online) */
        if(members.remove(subject.uniqueId)){
            admins.add(subject.uniqueId)
            sender.sendMessage("${ChatColor.GOLD}${subject.name}${ChatColor.GREEN} was promoted to group admin.")
            subject.player?.sendMessage("${ChatColor.GREEN}You were promoted to group admin.")
            return
        }

        /** Validate inputs for leadership transfer */
        if(!admins.contains(subject.uniqueId)){
            sender.sendMessage("${ChatColor.DARK_RED}You hit yourself in confusion.")
            sender.damage(1.0)
            return
        }

        if(!confirmed){
            sender.sendMessage("${ChatColor.AQUA}Promoting an admin makes them the new group leader. " +
                    "If your want to pass on group leadership use ${ChatColor.GOLD}/group promote <name> confirm")
            return
        }

        /** Transfer leadership */
        admins.add(sender.uniqueId)
        leader = subject.uniqueId
        admins.remove(subject.uniqueId)

        /** Notify members */
        message(Component.text("${ChatColor.GOLD}${subject.name}${ChatColor.AQUA} is the captain now."))
    }

    /** Demotes the subject if valid */
    fun demote(sender: Player, subject: OfflinePlayer){
        if(sender.uniqueId != leader || !admins.remove(subject.uniqueId)){
            sender.sendMessage("${ChatColor.DARK_RED}You hit yourself in confusion.")
            sender.damage(1.0)
            return
        }
        members.add(subject.uniqueId)

        /** Notify both (if online) */
        sender.sendMessage("${ChatColor.GOLD}${subject.name}${ChatColor.DARK_RED} was demoted to group member.")
        subject.player?.sendMessage("${ChatColor.DARK_RED}You were demoted to group member.")
    }

    /** Changes the group tag if possible */
    fun setTag(sender: Player, newTag: String){
        if(leader != sender.uniqueId){
            sender.sendMessage("${ChatColor.YELLOW}Only the group leader can change the tag.")
            return
        }

        /** Limit length */
        if(3 < newTag.length || newTag.isEmpty()){
            sender.sendMessage("${ChatColor.YELLOW}Group tags must be 1 to 3 characters long.")
            return
        }

        /** Check for duplicates */
        for(entry in plugin.groups){
            if(entry.value.tag != newTag){
                continue
            }

            if(entry.key == name){
                sender.sendMessage("${ChatColor.YELLOW}Your group already has this tag.")
                break
            }

            sender.sendMessage("${ChatColor.YELLOW}This group tag is already taken.")
            break
        }

        /** Save new tag */
        tag = newTag

        /** Change display names */
        val formattedTag = Component.text("${ChatColor.DARK_GRAY}<${ChatColor.AQUA}${newTag}${ChatColor.DARK_GRAY}>")
        for(playerID in members + admins + leader){
            val groupMember = plugin.server.getPlayer(playerID) ?: continue
            groupMember.playerListName(formattedTag.append(groupMember.displayName()))
        }
    }

    /** Print group data to chat */
    fun info(sender: Player){
        val tag = if(tag != null){
            " ${ChatColor.DARK_GRAY}(${ChatColor.GOLD}$tag${ChatColor.DARK_GRAY})"
        }else{
            " "
        }
        sender.sendMessage("${ChatColor.GRAY}---------------------------\n" +
                "${ChatColor.GOLD}$name$tag\n \n" +
                "${ChatColor.BLUE}Members:\n ${ChatColor.DARK_PURPLE}${plugin.server.getOfflinePlayer(leader).name}" +
                "${ChatColor.DARK_AQUA},${playerList(admins, ChatColor.GOLD)},${playerList(members, ChatColor.DARK_AQUA)}\n" +
                "${ChatColor.GRAY}---------------------------")
    }

    /** Creates a String with all member names */
    private fun playerList(players: MutableSet<UUID>, nameColor: ChatColor): String{
        var playerList = " "
        for(playerID in players){
            playerList += if(playerList == " "){
                "$nameColor${plugin.server.getOfflinePlayer(playerID).name}${ChatColor.DARK_AQUA}"
            }else{
                ", $nameColor${plugin.server.getOfflinePlayer(playerID).name}${ChatColor.DARK_AQUA}"
            }
        }
        return playerList
    }

    /** Sends the specified message to all online players */
    fun message(msg: TextComponent){
        for(recipient in members + admins + leader){
            plugin.server.getPlayer(recipient)?.sendMessage(msg)
        }
    }
}