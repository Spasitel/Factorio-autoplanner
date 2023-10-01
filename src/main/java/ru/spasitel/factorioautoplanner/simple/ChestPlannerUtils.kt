package ru.spasitel.factorioautoplanner.simple

import ru.spasitel.factorioautoplanner.simple.building.*
import java.util.*

object ChestPlannerUtils {
    fun calculateChests(state: State): State? {
        var prev = state
        var current = addMandatoryChests(state)

        //поставить для которых нет другого места
        while (current != null && prev != current) {
            prev = current
            current = addMandatoryChests(prev)
        }
        if (current == null) return null

        // поставить те, которые другим не мешают
        current = addArbitraryChests(current)
        while (current != null && prev != current) {
            prev = current
            current = addArbitraryChests(prev)
        }
        return current
    }

    fun addArbitraryChests(state: State): State {
        //0 пусто, 1 сундук, 2 - инсетер, 10 - конфликт
        val chestMap = Array(Main.SIZE) { IntArray(Main.SIZE) }
        var last: Building? = null
        for (b in state.buildings) {
            if (last == null ||
                (b.type == Type.SMELTER || b.type == Type.BEACON) &&
                b.x > last.x || b.x == last.x && b.y > last.y
            ) {
                last = b
            }
            if (b.type != Type.SMELTER
                || (b as Smelter).connected.size == 2
            ) continue
            val s = b
            Arrays.stream(InsertersPlaces.values()).forEach { ip: InsertersPlaces ->
                val cx = ip.cX + s.x
                if (cx < 0 || cx >= Main.SIZE) return@forEach
                val cy = ip.cY + s.y
                if (cy < 0 || cy >= Main.SIZE) return@forEach
                val ix = ip.iX + s.x
                if (ix < 0 || ix >= Main.SIZE) return@forEach
                val iy = ip.iY + s.y
                if (iy < 0 || iy >= Main.SIZE) return@forEach
                if (state.map[ix]?.get(iy) != null ||
                    state.map[cx]?.get(cy) != null &&
                    state.map[cx]?.get(cy)?.type != Type.PROVIDER_CHEST
                ) return@forEach
                if (chestMap[cx][cy] <= 1 && chestMap[ix][iy] == 0) {
                    chestMap[cx][cy] = 1
                    chestMap[ix][iy] = 2
                } else {
                    chestMap[cx][cy] = 3
                    if (chestMap[ix][iy] > 1) chestMap[ix][iy] = 3 else chestMap[ix][iy] = 1
                }
            }
        }
        for (y in 0 until Main.SIZE - 2) {
            val startX = if (y > last!!.y) last?.x else (last!!.x) + 1
            var possible: Smelter? = null
            for (x in startX until Main.SIZE - 2) {
                val s = Smelter(x, y)
                if (state.addBuilding(s) != null) {
                    possible = s
                    break
                }
            }
            if (possible != null) {
                for (py in possible.y until possible.y + possible.size) {
                    if (possible.x > 1) {
                        if (chestMap[possible.x - 2][py] > 1) chestMap[possible.x - 2][py] =
                            3 else chestMap[possible.x - 2][py] = 1
                    }
                    for (px in Math.max(0, possible.x - 1) until Main.SIZE) chestMap[px][py] = 3
                }
                if (possible.y > 1) {
                    val py = possible.y - 2
                    for (px in possible.x until Main.SIZE) {
                        if (chestMap[px][py] > 1) chestMap[px][py] = 3 else chestMap[px][py] = 1
                    }
                }
                if (possible.y < Main.SIZE - 5) {
                    val py = possible.y + 5
                    for (px in possible.x until Main.SIZE) {
                        if (chestMap[px][py] > 1) chestMap[px][py] = 3 else chestMap[px][py] = 1
                    }
                }
                if (possible.y < Main.SIZE - 4) {
                    val py = possible.y + 4
                    for (px in possible.x until Main.SIZE) chestMap[px][py] = 3
                }
                if (possible.y > 0) {
                    val py = possible.y - 1
                    for (px in possible.x until Main.SIZE) chestMap[px][py] = 3
                }
            }
        }
        val newBuilding: List<Building> = ArrayList()
        var current = state
        for (b in state.buildings) {
            if (b.type != Type.SMELTER
                || (b as Smelter).connected.size == 2
            ) continue
            val ips: MutableList<InsertersPlaces> = ArrayList()
            for (ip in InsertersPlaces.values()) {
                val cx = ip.cX + b.x
                if (cx < 0 || cx >= Main.SIZE) continue
                val cy = ip.cY + b.y
                if (cy < 0 || cy >= Main.SIZE) continue
                val ix = ip.iX + b.x
                if (ix < 0 || ix >= Main.SIZE) continue
                val iy = ip.iY + b.y
                if (iy < 0 || iy >= Main.SIZE) continue
                if (current.map[ix]!![iy] != null) continue
                if (current.map[cx]!![cy] != null &&
                    current.map[cx]!![cy]!!.type != Type.PROVIDER_CHEST
                ) continue
                if (chestMap[cx][cy] == 1 && chestMap[ix][iy] == 2) {
                    ips.add(ip)
                    if (b.connected.size + ips.size <= 2) {
                        current = addChestAndInserter(b.x, b.y, current, ip)
                    }
                }
            }
        }
        return current
    }

    @JvmStatic
    fun addMandatoryChests(`in`: State): State? {
        var state = `in`
        for (b in `in`.buildings) {
            if (b.type == Type.SMELTER && (b as Smelter).connected.size < 2) {
                val s = b
                val insertersPlaces: MutableList<InsertersPlaces> = ArrayList()
                for (places in InsertersPlaces.values()) {
                    val cx = places.cX + s.x
                    if (cx < 0 || cx >= Main.SIZE) continue
                    val cy = places.cY + s.y
                    if (cy < 0 || cy >= Main.SIZE) continue
                    val ix = places.iX + s.x
                    if (ix < 0 || ix >= Main.SIZE) continue
                    val iy = places.iY + s.y
                    if (iy < 0 || iy >= Main.SIZE) continue
                    if ((state.map[cx]!![cy] == null
                                || state.map[cx]!![cy]!!.type == Type.PROVIDER_CHEST)
                        && state.map[ix]!![iy] == null
                    ) insertersPlaces.add(places)
                }
                if (insertersPlaces.size < 2 - s.connected.size) return null
                if (insertersPlaces.size == 2 - s.connected.size) {
                    for (ip in insertersPlaces) {
                        state = addChestAndInserter(s.x, s.y, state, ip)
                    }
                }
            }
        }
        return state
    }

    fun addChestAndInserter(sx: Int, sy: Int, result: State?, ip: InsertersPlaces): State {
        var result = result
        val newInserter = Inserter(ip.iX + sx, ip.iY + sy, ip.direction)
        result = result!!.addBuilding(newInserter)
        if (result == null) throw RuntimeException()
        var newChest = result.map[ip.cX + sx]!![ip.cY + sy] as Chest?
        if (newChest == null) {
            newChest = Chest(ip.cX + sx, ip.cY + sy)
            result = result.addBuilding(newChest)
            if (result == null) throw RuntimeException()
        }
        val newSmelter = Smelter(sx, sy)
        newSmelter.connected.putAll((result.map[newSmelter.x]!![newSmelter.y] as Smelter).connected)
        newSmelter.connected[newChest] = newInserter
        result = result.replaceSmelter(newSmelter)
        if (result == null) throw RuntimeException()
        return result
    }
}