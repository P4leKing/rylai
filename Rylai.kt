package com.github.p4leking.rylai

import com.comphenix.protocol.PacketType
import com.comphenix.protocol.ProtocolLibrary
import com.comphenix.protocol.ProtocolManager
import com.comphenix.protocol.events.ListenerPriority
import com.comphenix.protocol.events.PacketAdapter
import com.comphenix.protocol.events.PacketEvent
import com.github.p4leking.rylai.classes.Assassin
import com.github.p4leking.rylai.commands.*
import com.github.p4leking.rylai.handlers.*
import com.github.p4leking.rylai.utils.*
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import net.kyori.adventure.text.format.TextColor
import org.bukkit.*
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.ShapedRecipe
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.plugin.java.JavaPlugin
import java.util.*

class Rylai : JavaPlugin() {
    /** New class/skill checklist:
     * for all:
     * - update enum
     * - create ranking
     * - add to list of rankings
     * - create levelData in playerData
     * - if required change the /skill ui (set start 1 to the left -
     *      when there are more than 7 classes or skills, seperate them into different ui windows)
     * for skill:
     * - add to skill list in player data
     * - create XP Event with xp gain and level up */

    /** This projects artifact needs to include the extracted version of the kotlin-stdlib.jar */
    lateinit var protocolManager: ProtocolManager

    val playerConfig = Config(this, "players.yml")
    val groupConfig = Config(this, "groups.yml")
    val persistentConfig = Config(this, "persistentData.yml")
    val rankingConfig = Config(this, "rankings.yml")
    private val settings = Config(this, "settings.yml")

    val players = mutableMapOf<UUID, PlayerData>()
    val groups = mutableMapOf<String, GroupData>()
    val rankings = Ranking(this)

    private val silencedPlayers = mutableMapOf<UUID, Int>()

    val customRecipes = mutableSetOf<NamespacedKey>()

    //hashmap/set element access needs constant time, thus search time doesn't increase with list size.
    // If loading from config on every login is too slow, preload all player data on startup. (More ram usage)

    /** Plugin startup logic */
    override fun onEnable() {
        protocolManager = ProtocolLibrary.getProtocolManager()

        /** Prevent item switches during invisibility from being sent to other players */
        protocolManager.addPacketListener(object: PacketAdapter(this, ListenerPriority.NORMAL, PacketType.Play.Server.ENTITY_EQUIPMENT) {
            override fun onPacketSending(event: PacketEvent) {
                val entityID = event.packet.integers.read(0) ?: return

                /** Make sure player is seeing all of their own swaps */
                if(event.player.entityId == entityID) return

                /** Cancel all packets to other players so player stays armor & weaponless */
                val entity = protocolManager.getEntityFromID(event.player.world, entityID) ?: return
                if(Assassin.invisiblePlayers.contains(entity.uniqueId)){
                    event.isCancelled = true
                }
            }
        })

        /** Load settings */
        val xpMultiplier = settings.config.getDouble("ExperienceMultiplier")
        if(xpMultiplier > 1){
            LevelData.xpMultiplier = xpMultiplier
        }

        loadGroupData()

        /** Register commands */
        getCommand("skills")?.setExecutor(Skills(this))
        getCommand("group")?.setExecutor(Group(this))
        getCommand("ranking")?.setExecutor(rankings)
        getCommand("rinfo")?.setExecutor(Rinfo(this))
        getCommand("multiplier")?.setExecutor(Multiplier())
        getCommand("chat")?.setExecutor(Chat(this))

        /** Register event handlers */
        CastHandler(this)
        PlayerDataHandler(this)
        AssassinHandler(this)
        CombatHandler(this)
        ArcherHandler(this)
        HunterHandler(this)
        ItemHandler(this)
        MageHandler(this)
        WarriorHandler(this)
        Chat(this)
        EnchantmentHandler(this)

        /** Register recipes */
        customWeapons()
        customArmor()
        customTools()
        customItems()
    }

    /** Plugin shutdown logic */
    override fun onDisable() {
        savePlayerData()
        saveGroupData()
        rankings.saveToConfig()
        persistentConfig.save()
        settings.config["ExperienceMultiplier"] = LevelData.xpMultiplier
        settings.save()
    }

    /** Silence a player for a specified duration */
    fun silence(player: Player, duration: Long){
        val playerID = player.uniqueId

        /** Cancel old duration if the player was silenced already */
        val task = silencedPlayers[playerID]
        if(task != null){
            server.scheduler.cancelTask(task)
        }

        /** Freeze animation */
        player.freezeTicks = duration.toInt() * 2

        /** Start silence duration */
        silencedPlayers[playerID] = server.scheduler.scheduleSyncDelayedTask(this, {
            silencedPlayers.remove(playerID)
        }, duration)
    }

    /** Check if a player is silenced */
    fun isSilenced(playerID: UUID): Boolean{
        if(silencedPlayers.contains(playerID)) return true
        return false
    }

    /** Saves the data of any logged in players to the config on restart */
    private fun savePlayerData(){
        for (entry in players.entries) {
            entry.value.saveToConfig()
        }
        playerConfig.save()
    }

    /** Loads the selected classes from the config */
    private fun loadGroupData() {
        for (groupName in groupConfig.config.getKeys(false)) {
            val configData = groupConfig.config.getConfigurationSection(groupName) ?: continue

            /** Load admin list */
            val aList = configData.getStringList("admins")
            val adminList = mutableSetOf<UUID>()
            for(entry in aList){
                adminList.add(UUID.fromString(entry))
            }

            /** Load member list */
            val mList = configData.getStringList("members")
            val memberList = mutableSetOf<UUID>()
            for(entry in mList){
                memberList.add(UUID.fromString(entry))
            }

            /** Get group leader UUID */
            val groupLeader = UUID.fromString(configData.getString("leader") ?: continue)

            /** Create object */
            groups[groupName] = GroupData(this, groupName, configData.getString("tag"),
                    groupLeader, adminList, memberList)
        }
    }

    /** Saves the given map to the config */
    private fun saveGroupData(){
        for(entry in groups) {
            groupConfig.config["${entry.key}.leader"] = entry.value.leader.toString()

            if(entry.value.admins.isNotEmpty()){
                val adminList = arrayListOf<String>()
                for(admin in entry.value.admins){
                    adminList.add(admin.toString())
                }
                groupConfig.config["${entry.key}.admins"] = adminList
            }

            if(entry.value.members.isNotEmpty()){
                val memberList = arrayListOf<String>()
                for(member in entry.value.members){
                    memberList.add(member.toString())
                }
                groupConfig.config["${entry.key}.members"] = memberList
            }

            if(entry.value.tag != null){
                groupConfig.config["${entry.key}.tag"] = entry.value.tag
            }
        }
        groupConfig.save()
    }

    /** Returns true if the player can be damaged by the attacker and false otherwise. */
    fun playerIsValidTarget(groupName: String?, target: Player) : Boolean {
        if (target.gameMode == GameMode.CREATIVE || target.gameMode == GameMode.SPECTATOR || target.isInvulnerable ||
                (groupName != null && groupName == players[target.uniqueId]!!.group)) {  
            return false
        }
        return true
    }

    /** Provides level scaled force value */
    fun levelScaling(playerID: UUID, force: Double): Double{
        return force + force * ((players[playerID] ?: return 0.0).combat.level / 100.0)
    }

    //TODO second scaling function with 3:2 scaling (60% base) / 2:1 scaling (66% base) only for directly damaging abilites
    // (only if flat dmg reduction of armor is too high)

    /** Produces a correctly formatted chat message */
    fun formatMessage(player: Player, msg: String, chatID: Char, color: ChatColor): TextComponent {
        return Component.text("$color[$chatID]${players[player.uniqueId]?.title ?: ""}")
            .append(player.displayName())
            .append(Component.text("${ChatColor.DARK_GRAY}: $color$msg"))
    }

    /** Sends a message to the senders group */
    fun groupChatMessage(player: Player, msg: String){
        val groupName = players[player.uniqueId]?.group
        val group = groups[groupName] ?: run {
            player.sendMessage("${ChatColor.YELLOW}You have not joined a group yet. List all commands with ${ChatColor.GOLD}/g help")
            return
        }
        val formattedMsg = formatMessage(player, msg, 'G', ChatColor.DARK_AQUA)
        group.message(formattedMsg)
    }

    /** Crafting recipes for specific custom items and utility recipes */
    private fun customItems(){
        val nsKey = NamespacedKey(this, "String")
        customRecipes.add(nsKey)
        Bukkit.addRecipe(ShapedRecipe(nsKey, ItemStack(Material.STRING, 4)).shape("W").setIngredient('W', Material.WHITE_WOOL))
    }

    /** Registers the custom weapon recipes */
    private fun customWeapons(){
        //mby use netherite ingot/netherite scrap in place of ender pearl for some items (both dungeon loot)
        /** Warrior */
        registerCustomRecipe(Material.DIAMOND_SWORD, "Prügel", Classes.WARRIOR.name, "weapon",
                "Sword", " D ", "IEI", " S ", Pair('D', Material.DIAMOND),
                Pair('E', Material.ENDER_PEARL), Pair('S', Material.STICK), Pair('I', Material.IRON_INGOT))

        /** Assassin */
        registerCustomRecipe(Material.NETHERITE_SWORD, "Dolchstoß Legende", Classes.ASSASSIN.name,
                "weapon", "Sword", " SI", " E ", "DS ", Pair('D', Material.DIAMOND),
                Pair('E', Material.ENDER_PEARL), Pair('S', Material.INK_SAC), Pair('I', Material.IRON_INGOT))

        /** Mage */
        registerCustomRecipe(Material.DIAMOND_AXE, "Spitfire", Classes.MAGE.name, "weapon", "Wand",
                " E ", " D ", " S ", Pair('D', Material.DIAMOND), Pair('E', Material.ENDER_EYE),
                Pair('S', Material.STICK))

        /** Archer */
        registerCustomRecipe(Material.BOW, "Skill Issue", Classes.ARCHER.name, "weapon", "Bow",
                "SW ", "SED", "SW ", Pair('D', Material.DIAMOND), Pair('E', Material.ENDER_PEARL),
                Pair('S', Material.STRING), Pair('W', Material.STICK))

        /** Hunter */
        registerCustomRecipe(Material.CROSSBOW, "WIIIILHEEEEELM", Classes.HUNTER.name, "weapon",
                "Crossbow", "WDW", "SES", " T ", Pair('D', Material.DIAMOND),
                Pair('E', Material.ENDER_PEARL), Pair('T', Material.TRIPWIRE_HOOK), Pair('W', Material.STICK),
                Pair('S', Material.STRING))
    }

    /** Registers the custom tool recipes */
    private fun customTools(){
        //mby use netherite ingot/netherite scrap in place of ender pearl for some items (both dungeon loot)
        /** Mining */
        registerCustomRecipe(Material.DIAMOND_PICKAXE, "Mine All Day", CraftingSkills.MINING.name,
                "tool", "Pickaxe", "IDI", " E ", " S ", Pair('D', Material.DIAMOND),
                Pair('E', Material.ENDER_PEARL), Pair('S', Material.STICK), Pair('I', Material.IRON_INGOT))
        registerCustomRecipe(Material.DIAMOND_SHOVEL, "Diggy Diggy Hole", CraftingSkills.MINING.name,
                "tool", "Shovel", " D ", " E ", " S ", Pair('D', Material.DIAMOND),
                Pair('E', Material.ENDER_PEARL), Pair('S', Material.STICK))

        /** Woodcutting */
        registerCustomRecipe(Material.DIAMOND_AXE, "Se Choppa", CraftingSkills.WOODCUTTING.name, "tool",
                "Axe", " DI", " EI", " S ", Pair('D', Material.DIAMOND), Pair('E', Material.ENDER_PEARL),
                Pair('S', Material.STICK), Pair('I', Material.IRON_INGOT))

        /** Fishing */
        registerCustomRecipe(Material.FISHING_ROD, "Scambling", CraftingSkills.FISHING.name, "tool",
                "Rod", "  D", " ES", "W S", Pair('D', Material.DIAMOND), Pair('E', Material.ENDER_PEARL),
                Pair('S', Material.STRING), Pair('W', Material.STICK))
    }

    /** Function that registers a custom recipe with 3 lines */
    private fun registerCustomRecipe(base: Material, name: String, className: String, type: String, key:String, line1: String, line2: String, line3: String, vararg ingredients: Pair<Char, Material>){
        val item = ItemStack(base)
        val meta = item.itemMeta
        meta.displayName(Component.text(name))
        item.itemMeta = meta

        /** Add custom lore */
        item.lore(listOf(Component.text("$type:$className", TextColor.color(0))))
        if(base == Material.BOW || base == Material.CROSSBOW){
            item.addUnsafeEnchantment(Enchantment.ARROW_INFINITE, 1)
        }

        /** Register recipe */
        val nsKey = NamespacedKey(this, "${className}_$key")
        customRecipes.add(nsKey)

        val recipe = ShapedRecipe(nsKey, item).shape(line1, line2, line3)

        for(ingredient in ingredients){
            recipe.setIngredient(ingredient.first, ingredient.second) //this method can use ItemStack/RecipeChoice/MaterialData objects to set custom items as ingredients
        }

        Bukkit.addRecipe(recipe)
    }

    /** Registers the custom weapon recipes */
    private fun customArmor(){
        //other possible mats: copper/kelp/prismarine - trident, gold drops - paladin like healer/tank, Bamboo - Samurai/Ninja,
        // Cactus - Tank with recoil function, Bone - Warlock, nether quarz, WOOL(dyed) ...
        //TODO mby do custom items as materials here that are crafted from leather and something or string and
        // something or iron and something
        registerClassArmorSet(Classes.WARRIOR, Material.DIAMOND_HELMET, Material.DIAMOND_BOOTS, Material.IRON_INGOT)
        registerClassArmorSet(Classes.ASSASSIN, Material.IRON_HELMET, Material.DIAMOND_BOOTS, Material.LEATHER)
        registerClassArmorSet(Classes.MAGE, Material.IRON_HELMET, Material.IRON_BOOTS, Material.STRING)
        registerClassArmorSet(Classes.ARCHER, Material.IRON_HELMET, Material.IRON_BOOTS, Material.GLASS)
        registerClassArmorSet(Classes.HUNTER, Material.IRON_HELMET, Material.IRON_BOOTS, Material.PAPER)
    }

    /** Registers the custom crafting recipes for this classes armor set */
    private fun registerClassArmorSet(combatClass: Classes, helmet:Material, boots:Material, material: Material){
        registerCustomArmor(helmet, "Helmet", combatClass, material, "MDM", "M M")
        registerCustomArmor(Material.DIAMOND_CHESTPLATE, "Chestplate", combatClass, material, "M M", "MDM", "MMM")
        registerCustomArmor(Material.DIAMOND_LEGGINGS, "Leggings", combatClass, material, "MDM", "M M", "M M")
        registerCustomArmor(boots, "Boots", combatClass, material, " D ", "M M", "M M")
    }

    /** Function that registers a custom armor pieces recipe */
    private fun registerCustomArmor(base: Material, name: String, combatClass: Classes, material: Material, vararg lines: String){
        val item = ItemStack(base)
        val meta = item.itemMeta
        meta.displayName(Component.text("${combatClass.className} $name"))

        /** Add proj protection to chest */
        if(base == Material.DIAMOND_CHESTPLATE){
            item.addEnchantment(Enchantment.PROTECTION_PROJECTILE, 1)
        }

        item.itemMeta = meta
        item.lore(listOf(Component.text("armor:${combatClass.name}", TextColor.color(0))))

        val nsKey = NamespacedKey(this, "${combatClass.name}_$name")
        customRecipes.add(nsKey)

        val recipe = ShapedRecipe(nsKey, item).shape(*lines)
        Bukkit.addRecipe(recipe.setIngredient('D', Material.DIAMOND).setIngredient('M', material))
    }

    /** Helper function to create items with specific metadata formatting */
    fun getItem(item: ItemStack, name: String, id: String, cd: String, vararg description: String) : ItemStack {
        val meta : ItemMeta = item.itemMeta
        meta.displayName(Component.text(name))

        val text : ArrayList<Component> = ArrayList()

        text.add(Component.text(id, TextColor.color(0)))
        text.add(Component.text(cd, TextColor.color(96, 0, 205)))

        for(line in description){
            text.add(Component.text(line, TextColor.color(39,223,236)))
        }

        meta.lore(text)

        item.itemMeta = meta
        return item
    }
}