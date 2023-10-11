package ru.spasitel.factorioautoplanner

import org.luaj.vm2.lib.jse.JsePlatform
import java.io.FileNotFoundException

object Main {
    const val PATH_TO_FACTORIO = "F:\\SteamLibrary\\steamapps\\common\\Factorio\\data\\base\\prototypes\\"

    @Throws(FileNotFoundException::class)
    @JvmStatic
    fun main(args: Array<String>) {
        val globals = JsePlatform.standardGlobals()
        /*Reader r = new FileReader(PATH_TO_FACTORIO + "entity\\sounds.lua");
        LuaValue sound = globals.load(r, "prototypes.entity.sounds");
        sound.call();
        LuaValue items = globals.loadfile(PATH_TO_FACTORIO + "item.lua");
        items.load(sound);*/
        val recipe = globals.loadfile(PATH_TO_FACTORIO + "recipe.lua")
        println("items")
    }
}