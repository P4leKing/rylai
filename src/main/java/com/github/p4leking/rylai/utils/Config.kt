/** © 2024 Bernhard Eierle. All rights reserved. */

package com.github.p4leking.rylai.utils

import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.Plugin
import java.io.File


class Config(path: String) {
    var file: File
    var config: FileConfiguration

    init {
        file = File(path)
        config = YamlConfiguration.loadConfiguration(file)
    }

    constructor(plugin: Plugin, path: String) : this(plugin.dataFolder.absolutePath + "/" + path)

    fun save(): Boolean{
        return try {
            config.save(file)
            true
        } catch (e: Exception){
            e.printStackTrace()
            false
        }
    }
}