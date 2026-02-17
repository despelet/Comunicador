package com.comunic


object ItemKey {
    const val PIC_PREFIX = "PIC:"
    const val MED_PREFIX = "MED:"

    fun picto(id: String) = "$PIC_PREFIX$id"
    fun media(baseName: String) = "$MED_PREFIX$baseName"

    fun isPicto(key: String) = key.startsWith(PIC_PREFIX)
    fun isMedia(key: String) = key.startsWith(MED_PREFIX)

    fun pictoId(key: String) = key.removePrefix(PIC_PREFIX)
    fun mediaBase(key: String) = key.removePrefix(MED_PREFIX)
}