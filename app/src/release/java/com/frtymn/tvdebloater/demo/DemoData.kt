package com.frtymn.tvdebloater.demo

/** Release builds have no demo mode. */
object DemoData {
    val available: Boolean get() = false
    fun snapshot(): DemoSnapshot? = null
}
