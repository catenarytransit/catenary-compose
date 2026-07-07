package com.catenarymaps.catenary.utils

import android.content.Context
import org.json.JSONObject

object DbFernverkehrUtils {
    private var lookupTable: Map<String, String>? = null

    fun getDisplayName(context: Context, tripShortNameNoZeros: String): String? {
        if (lookupTable == null) {
            loadLookupTable(context)
        }
        return lookupTable?.get(tripShortNameNoZeros)
    }

    private fun loadLookupTable(context: Context) {
        try {
            val jsonString =
                context.assets.open("fernverkehr_2026_train_lookup.json").bufferedReader()
                    .use { it.readText() }
            val jsonObject = JSONObject(jsonString)
            val map = mutableMapOf<String, String>()

            val keys = jsonObject.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val array = jsonObject.getJSONArray(key)
                if (array.length() > 0) {
                    val firstItem = array.getJSONObject(0)
                    if (firstItem.has("display_name") && !firstItem.isNull("display_name")) {
                        map[key] = firstItem.getString("display_name")
                    }
                }
            }
            lookupTable = map
        } catch (e: Exception) {
            e.printStackTrace()
            lookupTable = emptyMap()
        }
    }
}
