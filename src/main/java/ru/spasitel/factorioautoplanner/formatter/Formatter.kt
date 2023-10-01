package ru.spasitel.factorioautoplanner.formatter

import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.zip.DeflaterInputStream
import java.util.zip.InflaterInputStream

object Formatter {
    @JvmStatic
    fun decode(str: String): String? {
        return try {
            val decodedString = Base64.getDecoder().decode(str.substring(1).toByteArray(StandardCharsets.UTF_8))
            val bais = ByteArrayInputStream(decodedString)
            val iis = InflaterInputStream(bais)
            String(iis.readAllBytes())
        } catch (e: IOException) {
            null
        }
    }

    @JvmStatic
    fun encode(str: String): String? {
        return try {
            val bais = ByteArrayInputStream(str.toByteArray(StandardCharsets.UTF_8))
            val dis = DeflaterInputStream(bais)
            "0" + String(Base64.getEncoder().encode(dis.readAllBytes()))
        } catch (e: IOException) {
            null
        }
    }
}