/** © 2024 Bernhard Eierle. All rights reserved. */

package com.github.p4leking.rylai.utils

import org.bukkit.Material

/** The ability name and description can be chosen freely.
 * The enum name must be equivalent to the function name of the ability for it to be cast correctly.
 * The icon should not be obtainable, because it will get item cooldown on cast.
 * The cooldown should be the number of game ticks (seconds * 20).
 * The force value (damage/duration/percentage) is specified for level 1. Level 100 players will do double that amount.
 * A force value of 0 implies that the skill doesn't scale with level */
enum class Abilities(val abilityName: String, val icon: Material, val cooldown: Int, val force: Double, vararg val description: String) {
    IMPACT("Impact", Material.PANDA_SPAWN_EGG, 400, 8.0, "Leap into the air and crash back","down. When hitting the ground, the","impact from the superhero landing","damages and slows nearby enemies."),
    BLESSING("Blessing", Material.SHEEP_SPAWN_EGG, 400, 1.0, "Gain a shield if you are full hp.","Otherwise restore some health."),
    EARTHQUAKE("Earthquake", Material.RAVAGER_SPAWN_EGG, 400, 30.0, "Stomp the ground, knocking up","nearby enemies and pulling","them towards you."),
    BATTLECRY("Battle cry", Material.PILLAGER_SPAWN_EGG, 600, 100.0, "Shouting somehow makes you","and your allies run faster."),
    BLADESTORM("Bladestorm", Material.ENDERMAN_SPAWN_EGG, 300, 6.0, "Jump forward and damage all enemies","in your path. This ability's cooldown","is reset when hitting an enemy."),
    DEATHMARK("Deathmark", Material.AXOLOTL_SPAWN_EGG,400, 2.0, "Shoot a projectile that marks enemies","hit. This ability can be reactivated to","teleport to the marked target."),
    EVISCERATE("Eviscerate", Material.WITHER_SKELETON_SPAWN_EGG, 200, 0.15, "Your next critical strike deals bonus","damage based on missing health. If","the enemy was struck from behind,","its cooldown will get refreshed."),
    INVISIBILITY("Invisibility", Material.FOX_SPAWN_EGG,600, 100.0, "Make yourself invisible to others and","gain increased health regeneration","based on missing health."),
    DRAGONFIRE("Dragonfire", Material.STRIDER_SPAWN_EGG, 400, 9.0, "Damage enemies in front of you","with hot dragonfire."),
    DISTORTION("Distortion", Material.BAT_SPAWN_EGG, 360, 8.0, "Teleport in the direction you are facing","and deal damage to nearby enemies. This","ability can be reactivated to teleport","back to the previous location."),
    STASIS("Frozen Tomb", Material.POLAR_BEAR_SPAWN_EGG, 600, 5.0, "Freeze yourself temporarily","blocking all incoming attacks."),
    METEOR("Meteor", Material.SKELETON_SPAWN_EGG, 340, 8.0, "Summon a Meteor that falls from","the sky dealing damage on impact.","Picking it up refunds half of its","cooldown."),
    HOOKSHOT("Hookshot", Material.CAT_SPAWN_EGG, 600, 0.0, "Your next arrow pulls the user to","its location if it hits terrain or","pulls the hit enemy to you."),
    NETSHOT("Net shot", Material.SPIDER_SPAWN_EGG, 500, 30.0, "Your next arrow gets replaced with","a net trapping anyone nearby."),
    MARK("Hunter's Mark", Material.CAVE_SPIDER_SPAWN_EGG, 300, 60.0, "Your next arrow will mark all","nearby enemies causing them","to take increased damage."),
    BACKFLIP("Backflip", Material.EVOKER_SPAWN_EGG, 400, 0.15, "Jump backwards and deal bonus","damage with your next shot."),
    TUMBLE("Tumble", Material.BEE_SPAWN_EGG, 300, 0.0, "The player tumbles in the direction he is","moving and briefly becomes invisible."),
    ASCENSION("Ascension", Material.SALMON_SPAWN_EGG, 600, 100.0, "While this ability is active, your","arrows will get stronger for","each arrow hit in a row."),
    ANKLEBREAKER("Anklebreaker", Material.PIGLIN_BRUTE_SPAWN_EGG, 200, 6.0, "Enemies in front of you take","an arrow to the knee."),
    TRIPMINE("Trip mine", Material.CREEPER_SPAWN_EGG, 300, 40.0, "Place a poison trip mine","in front of you.")
}