package com.lakefront.tvlauncher.logic

/**
 * Ordered home pins. Fixed launcher entries (All Apps, File Manager, App Store, Add)
 * are not part of this list.
 */
object PinList {
    fun add(order: List<ComponentKey>, key: ComponentKey): List<ComponentKey> {
        if (order.any { it == key }) return order
        return order + key
    }

    fun remove(order: List<ComponentKey>, key: ComponentKey): List<ComponentKey> =
        order.filterNot { it == key }

    fun move(order: List<ComponentKey>, key: ComponentKey, delta: Int): List<ComponentKey> {
        if (delta == 0) return order
        val index = order.indexOf(key)
        if (index < 0) return order
        val target = index + delta
        if (target !in order.indices) return order
        val mutable = order.toMutableList()
        val item = mutable.removeAt(index)
        mutable.add(target, item)
        return mutable
    }

    /**
     * Drops pins whose package is gone. If the activity class changed but the package
     * still has a launchable, the pin follows that activity. Duplicate targets collapse.
     */
    fun reconcile(order: List<ComponentKey>, installed: List<ComponentKey>): List<ComponentKey> {
        val exact = installed.associateBy { it }
        val byPackage = installed.groupBy { it.packageName }
        val seen = LinkedHashSet<ComponentKey>()
        val result = ArrayList<ComponentKey>(order.size)
        for (key in order) {
            val resolved = exact[key] ?: byPackage[key.packageName]?.firstOrNull() ?: continue
            if (seen.add(resolved)) result += resolved
        }
        return result
    }
}
