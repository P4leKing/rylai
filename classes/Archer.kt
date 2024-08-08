/** © 2024 Bernhard Eierle. All rights reserved. */

package com.github.p4leking.rylai.classes

import com.github.p4leking.rylai.utils.Abilities
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.util.*

class Archer {
    companion object{
        val hookshotActive = mutableSetOf<UUID>()
        val netshotActive = mutableSetOf<UUID>()
        val markActive = mutableSetOf<UUID>()
        val flipActive = mutableSetOf<UUID>()
        private val hookshotCooldown = mutableSetOf<UUID>()
        private val netshotCooldown = mutableSetOf<UUID>()
        private val markCooldown = mutableSetOf<UUID>()
        private val flipCooldown = mutableSetOf<UUID>()

        /** Makes the next arrow teleport the user to its location if it hits terrain
         * or teleports the damaged target to you. */
        fun hookshot(plugin: Plugin, player: Player){
            val playerID = player.uniqueId

            /** Handle cooldown */
            if(!hookshotCooldown.add(playerID)){
                return
            }

            player.setCooldown(Abilities.HOOKSHOT.icon, Abilities.HOOKSHOT.cooldown)

            plugin.server.scheduler.scheduleSyncDelayedTask(plugin, {
                hookshotCooldown.remove(playerID)
            }, Abilities.HOOKSHOT.cooldown.toLong())

            /** Remove other arrows if there are any */
            netshotActive.remove(playerID)
            markActive.remove(playerID)

            /** Activate hookshot for next arrow */
            hookshotActive.add(playerID)
            plugin.server.scheduler.scheduleSyncDelayedTask(plugin, {
                if(hookshotActive.remove(playerID)){
                    player.sendMessage("You clumsily dropped your hookshot arrow.")
                }
            }, 100)
        }

        /** The players next arrow gets replaced with a net that traps anyone nearby. */
        fun netshot(plugin: Plugin, player: Player){
            val playerID = player.uniqueId

            /** Handle cooldown */
            if(!netshotCooldown.add(playerID)){
                return
            }

            player.setCooldown(Abilities.NETSHOT.icon, Abilities.NETSHOT.cooldown)

            plugin.server.scheduler.scheduleSyncDelayedTask(plugin, {
                netshotCooldown.remove(playerID)
            }, Abilities.NETSHOT.cooldown.toLong())

            /** Remove other arrows if there are any */
            hookshotActive.remove(playerID)
            markActive.remove(playerID)

            /** Activate netshot for next arrow */
            netshotActive.add(playerID)
            plugin.server.scheduler.scheduleSyncDelayedTask(plugin, {
                if(netshotActive.remove(playerID)){
                    player.sendMessage("You clumsily dropped your netshot arrow.")
                }
            }, 100)
        }

        /** Makes your next arrow mark all nearby enemies causing them to take increased damage from the player. */
        fun mark(plugin: Plugin, player: Player){
            val playerID = player.uniqueId

            /** Handle cooldown */
            if(!markCooldown.add(playerID)){
                return
            }

            player.setCooldown(Abilities.MARK.icon, Abilities.MARK.cooldown)

            plugin.server.scheduler.scheduleSyncDelayedTask(plugin, {
                markCooldown.remove(playerID)
            }, Abilities.MARK.cooldown.toLong())

            /** Remove other arrows if there are any */
            hookshotActive.remove(playerID)
            netshotActive.remove(playerID)

            /** Activate netshot for next arrow */
            markActive.add(playerID)
            plugin.server.scheduler.scheduleSyncDelayedTask(plugin, {
                if(markActive.remove(playerID)){
                    player.sendMessage("You clumsily dropped your hunter's mark arrow.")
                }
            }, 100)
        }

        /** Jump backwards and deal bonus damage with your next shot within 2.5 seconds. */
        fun backflip(plugin: Plugin, player: Player){
            val playerID = player.uniqueId

            /** Handle cooldown */
            if(!flipCooldown.add(playerID)){
                return
            }

            player.setCooldown(Abilities.BACKFLIP.icon, Abilities.BACKFLIP.cooldown)

            plugin.server.scheduler.scheduleSyncDelayedTask(plugin, {
                flipCooldown.remove(playerID)
            }, Abilities.BACKFLIP.cooldown.toLong())

            /** Dash */
            val dashVector = player.location
            dashVector.pitch = -30F
            dashVector.yaw += 180
            player.velocity = dashVector.direction.multiply(1)

            /** Boost next arrow */
            flipActive.add(playerID)
            plugin.server.scheduler.scheduleSyncDelayedTask(plugin, {
                flipActive.remove(playerID)
            }, 50)
        }
    }
}