package com.github.avbox.osc.download

interface PlayUrlResolver {
    suspend fun resolve(sourceKey: String?, playFlag: String?, progressKey: String?, rawUrl: String): String?
}

object PlayUrlResolverHolder {
    @Volatile
    var resolver: PlayUrlResolver? = null
}
