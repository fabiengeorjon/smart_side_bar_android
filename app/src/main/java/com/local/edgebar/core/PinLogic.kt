package com.local.edgebar.core

/** Pure, order-preserving operations on the pinned-app list. No Android dependencies. */
object PinLogic {
    private val PACKAGE_NAME = Regex("""^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$""")

    fun isValidPackageName(name: String): Boolean = PACKAGE_NAME.matches(name)

    /** Trims, drops blanks/invalid names and duplicates (first occurrence wins). */
    fun sanitize(pins: List<String>): List<String> =
        pins.asSequence().map { it.trim() }.filter { isValidPackageName(it) }.distinct().toList()

    fun pin(pins: List<String>, pkg: String): List<String> =
        if (!isValidPackageName(pkg) || pkg in pins) pins else pins + pkg

    fun unpin(pins: List<String>, pkg: String): List<String> = pins.filterNot { it == pkg }

    fun toggle(pins: List<String>, pkg: String): List<String> =
        if (pkg in pins) unpin(pins, pkg) else pin(pins, pkg)

    /** Moves [pkg] by [delta] positions, clamped to the list bounds. Unknown packages are a no-op. */
    fun move(pins: List<String>, pkg: String, delta: Int): List<String> {
        val from = pins.indexOf(pkg)
        if (from < 0) return pins
        return moveTo(pins, from, from + delta)
    }

    fun moveTo(pins: List<String>, from: Int, to: Int): List<String> {
        if (from !in pins.indices) return pins
        val target = to.coerceIn(0, pins.lastIndex)
        if (target == from) return pins
        val out = pins.toMutableList()
        val item = out.removeAt(from)
        out.add(target, item)
        return out
    }

    data class PruneResult(val kept: List<String>, val removed: List<String>) {
        val changed: Boolean get() = removed.isNotEmpty()
    }

    /** Splits pins into those still launchable and those whose app is gone. Order is kept. */
    fun prune(pins: List<String>, isAvailable: (String) -> Boolean): PruneResult {
        val (kept, removed) = pins.partition(isAvailable)
        return PruneResult(kept, removed)
    }

    fun encode(pins: List<String>): String = sanitize(pins).joinToString("\n")

    fun decode(raw: String?): List<String> =
        if (raw.isNullOrEmpty()) emptyList() else sanitize(raw.split('\n'))
}
