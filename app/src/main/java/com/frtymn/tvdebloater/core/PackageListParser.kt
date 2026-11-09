package com.frtymn.tvdebloater.core

/** Parsers for the plain-text output of `pm list packages`, `dumpsys meminfo` and `df`. */
object PackageListParser {

    /**
     * Parses `pm list packages` output. Accepts `package:com.foo`, the `-f` form
     * `package:/system/app/Foo.apk=com.foo`, Windows line endings and junk lines.
     */
    fun parsePackages(output: String): Set<String> {
        val result = linkedSetOf<String>()
        for (raw in output.lineSequence()) {
            val line = raw.trim().trimEnd('\r')
            if (!line.startsWith("package:")) continue
            var name = line.removePrefix("package:").trim()
            val eq = name.lastIndexOf('=')
            if (eq >= 0) name = name.substring(eq + 1)
            // `pm list packages -U` appends " uid:1000"; anything else with spaces is rejected.
            name = name.replace(Regex("\\s+uid:\\d+$"), "").trim()
            if (CommandBuilder.isValidPackageName(name)) result += name
        }
        return result
    }

    /** Combines the three lists into a per-package state for user 0. */
    fun buildStates(
        systemOutput: String,
        disabledOutput: String,
        includingUninstalledOutput: String,
    ): Map<String, PkgState> {
        val installed = parsePackages(systemOutput)
        val disabled = parsePackages(disabledOutput)
        val all = parsePackages(includingUninstalledOutput)
        val states = linkedMapOf<String, PkgState>()
        for (p in installed) states[p] = if (p in disabled) PkgState.DISABLED else PkgState.ENABLED
        for (p in all) if (p !in states) states[p] = PkgState.UNINSTALLED
        return states
    }

    private val RAM_LINE = Regex("(Total|Free|Used) RAM:\\s*([0-9][0-9,.]*)\\s*([KMG]?)B?", RegexOption.IGNORE_CASE)

    /** Parses `dumpsys meminfo` (summary lines) or falls back to `/proc/meminfo`. */
    fun parseMemInfo(output: String): MemInfo? {
        var total: Long? = null
        var free: Long? = null
        var used: Long? = null
        for (m in RAM_LINE.findAll(output)) {
            val kb = toKb(m.groupValues[2], m.groupValues[3].ifEmpty { "K" }) ?: continue
            when (m.groupValues[1].lowercase()) {
                "total" -> if (total == null) total = kb
                "free" -> if (free == null) free = kb
                "used" -> if (used == null) used = kb
            }
        }
        if (total != null && free != null) return MemInfo(total, free, used)
        // /proc/meminfo fallback
        val map = Regex("^(\\w+):\\s+(\\d+)\\s*kB", RegexOption.MULTILINE).findAll(output)
            .associate { it.groupValues[1] to it.groupValues[2].toLong() }
        val t = map["MemTotal"] ?: return null
        val f = map["MemAvailable"] ?: map["MemFree"] ?: return null
        return MemInfo(t, f, t - f)
    }

    /**
     * Parses `df /data`. Supports toybox 1K-block output, human-readable sizes (`-h` style)
     * and filesystem names that wrap onto their own line.
     */
    fun parseDf(output: String): StorageInfo? {
        val lines = output.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return null
        val header = lines.first().lowercase()
        val blockKb = when {
            header.contains("1k-blocks") -> 1L
            header.contains("512-blocks") || header.contains("512b-blocks") -> -2L
            else -> 0L // sizes carry their own unit, or are plain kilobytes
        }
        val body = lines.drop(if (header.startsWith("filesystem")) 1 else 0)
        val tokens = body.joinToString(" ").split(Regex("\\s+"))
        val numbers = mutableListOf<Long>()
        for (tok in tokens) {
            val kb = parseSize(tok, blockKb)
            if (kb != null) {
                numbers += kb
                if (numbers.size == 3) break
            } else if (numbers.isNotEmpty()) {
                numbers.clear()
            }
        }
        if (numbers.size < 3) return null
        return StorageInfo(totalKb = numbers[0], usedKb = numbers[1], availableKb = numbers[2])
    }

    private fun parseSize(token: String, blockKb: Long): Long? {
        val m = Regex("^([0-9]+(?:\\.[0-9]+)?)([KMGT]?)$", RegexOption.IGNORE_CASE).matchEntire(token) ?: return null
        val value = m.groupValues[1].toDouble()
        val unit = m.groupValues[2].uppercase()
        return when {
            unit.isNotEmpty() -> toKb(m.groupValues[1], unit)
            blockKb == -2L -> (value / 2).toLong()
            else -> value.toLong()
        }
    }

    private fun toKb(number: String, unit: String): Long? {
        val v = number.replace(",", "").toDoubleOrNull() ?: return null
        val mult = when (unit.uppercase()) {
            "", "K" -> 1.0
            "M" -> 1024.0
            "G" -> 1024.0 * 1024
            "T" -> 1024.0 * 1024 * 1024
            else -> return null
        }
        return (v * mult).toLong()
    }
}
