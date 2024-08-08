/** © 2024 Bernhard Eierle. All rights reserved. */

package com.github.p4leking.rylai.utils

import com.github.p4leking.rylai.Rylai
import java.util.*

class SkillRanking(private val plugin: Rylai, val name: String) {
    val ranking = loadFromConfig()
    private var lowestLevel = plugin.rankingConfig.config.getInt("$name.lowestLevel", 0)
    private val maxIndex = 9

    /** Loads the players in the ranking from config and resorts the level 100 players based on current xp */
    private fun loadFromConfig(): ArrayList<Triple<UUID, Int, Int>>{
        val playerIDs = plugin.rankingConfig.config.getStringList("$name.ranking.playerIDs")
        val newRanking = arrayListOf<Triple<UUID, Int, Int>>()

        var sorted = false
        for(id in playerIDs){
            val level = plugin.playerConfig.config.getInt("$id.${name}.Level", 1)

            /** Load xp from config if player is level 100 and
             * sort list of level 100 players once the first non 100 player is added. */
            val exp = if(level != 100){
                if(!sorted){
                    newRanking.sortByDescending{it.third}
                    sorted = true
                }
                0
            }else{
                plugin.playerConfig.config.getInt("$id.${name}.XP", 0)
            }
            newRanking.add(Triple(UUID.fromString(id), level, exp))
        }

        /** Sort list by total level or xp if everyone is lvl 100 -
         * Should not mess with the xp sort for players who maxed every skill */
        if(!sorted){
            newRanking.sortByDescending{it.third}
        }else{
            newRanking.sortByDescending{it.second}
        }
        return newRanking
    }

    /** Function for updating the ranking on a level up */
    fun update(playerID: UUID, level: Int){

        /** Check if they are high enough to be on the list */
        if(level <= lowestLevel){
            if(level == 100){
                ranking.add(Triple(playerID, level, 0))
            }
            return
        }

        /** Add if list is empty */
        if(ranking.isEmpty()){
            ranking.add(Triple(playerID, level, 0))
            return
        }

        /** Look for the right spot in the list */
        var targetIndex = if(maxIndex < ranking.size){
            maxIndex
        }else{
            ranking.size - 1
        }

        for(i in 0 .. targetIndex){
            val currentEntry = ranking[i]

            /** Keep looking for the new spot */
            if(currentEntry.second >= level){
                continue
            }

            /** New spot is at the bottom of the list and replaces last */
            if(i == maxIndex){
                ranking[i] = Triple(playerID, level, 0)
                lowestLevel = level
                return
            }

            /** Player's spot on the list doesn't change, and he is not the lowest */
            if(currentEntry.first == playerID){
                ranking[i] = Triple(playerID, level, 0)
                return
            }

            /** New spot is found */
            targetIndex = i
            break
        }

        /** Insert new entry, move the others down and remove possible duplicates or the last one */
        var previousEntry = Triple(playerID, level, 0)
        for(i in targetIndex until ranking.size){
            val currentEntry = ranking[i]

            /** Replace last or duplicate entry */
            if(i == maxIndex || currentEntry.first == playerID){
                ranking[i] = previousEntry
                return
            }

            /** List is not full so don't remove any objects and append */
            if(i == ranking.size - 1 && lowestLevel == 0){
                ranking[i] = previousEntry
                ranking.add(currentEntry)
                return
            }

            /** Move entry down */
            ranking[i] = previousEntry
            previousEntry = currentEntry
        }
    }

    fun saveToConfig(){
        plugin.rankingConfig.config["$name.lowestLevel"] = lowestLevel

        /** Create serializable list of player IDs */
        val idList = arrayListOf<String>()
        for(entry in ranking){
            idList.add(entry.first.toString())
        }

        /** Save them to config */
        plugin.rankingConfig.config["$name.ranking.playerIDs"] = idList
    }
}