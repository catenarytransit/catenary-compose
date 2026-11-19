package com.catenarymaps.catenary

/**
 * Return the difference between a reported real-time instant and its scheduled
 * instant. A reported on-time service is 0, not null. Null means that one of
 * the two timestamps was not supplied.
 */
internal fun knownRealtimeDelaySeconds(realtime: Long?, scheduled: Long?): Long? =
    if (realtime != null && scheduled != null) realtime - scheduled else null
