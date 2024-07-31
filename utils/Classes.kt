package com.github.p4leking.rylai.utils

import org.bukkit.Material

enum class Classes(override val className: String, override val icon: Material, override val description: String,
                   vararg val abilities: Abilities) : Skill {
    WARRIOR("Warrior", Material.CHAINMAIL_HELMET, "Verprügelt Bitches", Abilities.IMPACT, Abilities.BLESSING, Abilities.EARTHQUAKE, Abilities.BATTLECRY),
    ASSASSIN("Assassin", Material.LEATHER_HELMET, "Definitv nicht Akali", Abilities.BLADESTORM, Abilities.INVISIBILITY, Abilities.DEATHMARK, Abilities.EVISCERATE),
    MAGE("Mage", Material.STICK, "Hat im ersten Film überhaupt nicht gezaubert", Abilities.DRAGONFIRE, Abilities.DISTORTION, Abilities.STASIS, Abilities.METEOR),
    ARCHER("Archer", Material.BOW, "Moin Maxl", Abilities.HOOKSHOT, Abilities.NETSHOT, Abilities.MARK, Abilities.BACKFLIP),
    HUNTER("Hunter", Material.CROSSBOW, "WIILHEEEEEEEELM", Abilities.TUMBLE, Abilities.ASCENSION, Abilities.ANKLEBREAKER, Abilities.TRIPMINE)
}