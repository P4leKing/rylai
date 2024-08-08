/** © 2024 Bernhard Eierle. All rights reserved. */

package com.github.p4leking.rylai.commands

import com.github.p4leking.rylai.Rylai
import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.TextComponent
import org.bukkit.ChatColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.util.*

class Chat(private val plugin: Rylai): Listener, CommandExecutor, TabCompleter {
    init {
        plugin.server.pluginManager.registerEvents(this, plugin)
    }

    private val mutedTrade = mutableSetOf<UUID>()
    private val mutedGlobal = mutableSetOf<UUID>()
    private val lastWhisper = mutableMapOf<UUID, UUID>()

    companion object{
        private val ignoredBy = mutableMapOf<UUID, MutableSet<UUID>>()
        val localChat = mutableSetOf<UUID>()
        val tradeChat = mutableSetOf<UUID>()

        fun isIgnoredBy(sender: UUID, recipient: UUID): Boolean{
            return ignoredBy[sender]?.contains(recipient) == true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onChat(event : AsyncChatEvent){
        event.isCancelled = true
        val msg = (event.message() as TextComponent).content()
        val id = event.player.uniqueId

        /** Group chat */
        if(Group.groupChat.contains(id)){
            plugin.groupChatMessage(event.player, msg)
            return
        }

        /** Local chat */
        if(localChat.contains(id)){
            localChatMessage(event.player, msg)
            return
        }

        /** Trade chat */
        if(tradeChat.contains(id)){
            if(mutedTrade.contains(id)){
                event.player.sendMessage("${ChatColor.DARK_GRAY}Trade chat has been muted. To enable it use ${ChatColor.GOLD}/trading enable")
                return
            }

            tradeChatMessage(event.player, msg)
            return
        }

        if(mutedGlobal.contains(id)){
            event.player.sendMessage("${ChatColor.DARK_GREEN}Global chat has been muted. To enable it use ${ChatColor.GOLD}/global enable")
            return
        }

        /** Global chat */
        globalChatMessage(event.player, msg)
    }

    override fun onTabComplete(sender: CommandSender, command: Command, label: String, args: Array<out String>): MutableList<String> {
        val recommended = mutableListOf<String>()
        if(label in setOf("w", "ignore", "unignore") && args.size == 1){
            for(player in plugin.server.onlinePlayers){
                if(player.name.startsWith(args[0], true)){
                    recommended.add(player.name)
                }
            }
        }else if(label in setOf("global", "trading")){
            if(args.isEmpty()){
                return mutableListOf("mute", "unmute")
            }else if(args.size == 1){
                for(option in setOf("mute", "unmute")){
                    if(option.startsWith(args[0], true)){
                        recommended.add(option)
                    }
                }
            }
        }
        return recommended
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("Only Players can run this command.")
            return true
        }

        val id = sender.uniqueId

        if(label == "ignore"){
            if(args.isEmpty()){
                return false
            }

            val target = plugin.server.getPlayer(args[0]) ?: return false
            if(!target.isOnline || target == sender){
                sender.sendMessage("${ChatColor.DARK_RED}I'm sorry Dave, I'm afraid I can't do that.")
                return true
            }

            val ignored = ignoredBy[target.uniqueId]?.add(id) ?: run {
                ignoredBy[target.uniqueId] = mutableSetOf(id)
                true
            }
            if(ignored){
                sender.sendMessage("${target.displayName()}${ChatColor.GRAY} has been ignored.")
            }
            return true
        }

        if(label == "unignore"){
            if(args.isEmpty()){
                return false
            }

            val target = plugin.server.getPlayer(args[0]) ?: return false
            if(!target.isOnline || target == sender){
                sender.sendMessage("${ChatColor.DARK_RED}I'm sorry Dave, I'm afraid I can't do that.")
                return true
            }

            if(ignoredBy[target.uniqueId]?.remove(id) == true){
                sender.sendMessage("${target.displayName()}${ChatColor.GRAY} has been unignored.")
            }
            return true
        }

        if(label == "w"){
            if(args.size < 2){
                return false
            }

            val recipient = plugin.server.getPlayer(args[0]) ?: return false
            if(!recipient.isOnline || recipient == sender){
                sender.sendMessage("${ChatColor.DARK_RED}I'm sorry Dave, I'm afraid I can't do that.")
                return true
            }

            lastWhisper[recipient.uniqueId] = id
            whisper(sender, recipient, buildMessage(args.sliceArray(1 until args.size)))
            return true
        }

        if(label == "r"){
            val recipientID = lastWhisper[id] ?: return false
            if(args.isEmpty()){
                return false
            }

            val recipient = plugin.server.getPlayer(recipientID) ?: return false
            if(!recipient.isOnline){
                return false
            }

            lastWhisper[recipient.uniqueId] = id
            whisper(sender, recipient, buildMessage(args))
            return true
        }

        /** Local chat */
        if(label == "local"){
            if(args.isEmpty()) {
                if(localChat.add(id)){
                    tradeChat.remove(id)
                    Group.groupChat.remove(id)
                    sender.sendMessage("${ChatColor.YELLOW}Local chat activated.")
                }
                return true
            }

            localChatMessage(sender, buildMessage(args))
            return true
        }

        /** Trade chat */
        if(label == "trading"){
            if(args.isEmpty()) {
                if(tradeChat.add(id)){
                    localChat.remove(id)
                    Group.groupChat.remove(id)
                    sender.sendMessage("${ChatColor.GRAY}Trade chat activated.")
                }
                return true
            }

            if(args[0] == "mute"){
                if(mutedTrade.add(id)){
                    tradeChat.remove(id)
                    sender.sendMessage("${ChatColor.GRAY}Trade chat muted.")
                }
                return true
            }

            if(args[0] == "unmute"){
                if(mutedTrade.remove(id)){
                    sender.sendMessage("${ChatColor.GRAY}Trade chat unmuted.")
                }
                return true
            }

            if(mutedTrade.contains(id)){
                sender.sendMessage("${ChatColor.GRAY}Trade chat has been muted. To enable it use ${ChatColor.GOLD}/trading unmute")
                return true
            }

            tradeChatMessage(sender, buildMessage(args))
            return true
        }

        /** Global chat */
        if(args.isEmpty()){
            if(localChat.remove(id) || tradeChat.remove(id) || Group.groupChat.remove(id)){
                sender.sendMessage("${ChatColor.DARK_GREEN}Global chat activated.")
            }
            return true
        }

        if(args[0] == "mute"){
            if(mutedGlobal.add(id)){
                sender.sendMessage("${ChatColor.DARK_GREEN}Global chat muted.")
            }
            return true
        }

        if(args[0] == "unmute"){
            if(mutedGlobal.remove(id)){
                sender.sendMessage("${ChatColor.DARK_GREEN}Global chat unmuted.")
            }
            return true
        }

        if(mutedGlobal.contains(id)){
            sender.sendMessage("${ChatColor.DARK_GREEN}Global chat has been muted. To enable it use ${ChatColor.GOLD}/global unmute")
            return true
        }

        globalChatMessage(sender, buildMessage(args))
        return true
    }

    /** Builds a message string from the args array */
    private fun buildMessage(args: Array<out String>): String{
        var msg = ""
        for (arg in args) {
            msg += "$arg "
        }
        return msg
    }

    /** Sends a message in global chat */
    private fun globalChatMessage(sender: Player, msg: String){
        val formattedMsg = plugin.formatMessage(sender, msg, 'A', ChatColor.DARK_GREEN)

        for(player in plugin.server.onlinePlayers){
            if(mutedGlobal.contains(player.uniqueId)){
                continue
            }
            sendMessage(sender.uniqueId, player, formattedMsg)
        }
    }

    /** Sends a message in local chat */
    private fun localChatMessage(player: Player, msg: String){
        val formattedMsg = plugin.formatMessage(player, msg, 'L', ChatColor.YELLOW)
        val id = player.uniqueId
        plugin.server.scheduler.callSyncMethod(plugin) {
            for(entity in player.location.getNearbyEntities(150.0, 150.0, 150.0)) {
                if (entity !is Player) {
                    continue
                }
                sendMessage(id, entity, formattedMsg)
            }
        }
    }

    /** Sends a message in trade chat */
    private fun tradeChatMessage(sender: Player, msg: String){
        val formattedMsg = plugin.formatMessage(sender, msg, 'T', ChatColor.GRAY)

        for(player in plugin.server.onlinePlayers){
            if(mutedTrade.contains(player.uniqueId)){
                continue
            }
            sendMessage(sender.uniqueId, player, formattedMsg)
        }
    }

    /** Sends a message in trade chat */
    private fun whisper(sender: Player, recipient: Player, msg: String){
        val formattedMsg = plugin.formatMessage(sender, msg, 'W', ChatColor.DARK_PURPLE)
        sender.sendMessage(formattedMsg)
        sendMessage(sender.uniqueId, recipient, formattedMsg)
    }

    /** Sends the given message to the recipient unless they ignore the sender */
    private fun sendMessage(sender: UUID, recipient: Player, msg: TextComponent){
        if(isIgnoredBy(sender, recipient.uniqueId)){
            return
        }
        recipient.sendMessage(msg)
    }

    @EventHandler
    fun loadIgnores(event: PlayerJoinEvent){
        val id = event.player.uniqueId
        val stringList = plugin.persistentConfig.config.getStringList("${id}.ignoredBy")
        val uuidList = mutableSetOf<UUID>()
        for(entry in stringList){
            uuidList += UUID.fromString(entry)
        }
        ignoredBy[id] = uuidList
    }

    @EventHandler
    fun cleanupChats(event: PlayerQuitEvent){
        val id = event.player.uniqueId

        /** Cleanup chat data */
        lastWhisper.remove(id)

        /** Save ignores to config */
        val stringList = arrayListOf<String>()
        for(player in ignoredBy[id] ?: return){
            stringList.add(player.toString())
        }
        plugin.persistentConfig.config["${id}.ignoredBy"] = stringList
    }
}