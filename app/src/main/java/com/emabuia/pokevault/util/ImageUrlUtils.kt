package com.emabuia.pokevault.util

import android.net.Uri
import com.emabuia.pokevault.BuildConfig

/**
 * Single home for image-URL fixups that were previously copy-pasted across
 * 7 screens (SetDetailScreen, WishlistDetailScreen, ScannerScreen,
 * CreateGoalAlbumScreen, GoalAlbumDetailScreen, CardDetailScreen,
 * CollectionScreen) -- see MIGRATION_PLAN.md sez. 8, voce #12.
 */
object ImageUrlUtils {

    /**
     * Percent-encodes characters that break Coil's URL parsing when present
     * unencoded in an image URL (spaces and parentheses show up in some
     * PokeWallet card/set names). Safe to call on an already-encoded URL:
     * none of the three replaced characters are legal in one.
     */
    fun safeImageUrl(url: String): String {
        return url
            .replace(" ", "%20")
            .replace("(", "%28")
            .replace(")", "%29")
    }

    /**
     * Rewrites a direct `api.pokewallet.io` image URL to go through the
     * app's own Cloudflare proxy when one is configured, so URLs stored
     * locally as-is (e.g. `PokemonCard.imageUrl` in Room) still benefit
     * from the proxy/cache layer at render time instead of hitting
     * PokeWallet directly. No-op for any other host, or when the proxy
     * isn't enabled/configured. Moved from CollectionScreen.kt, the only
     * screen that needs it -- the other 6 render `TcgCard.images.*` URLs,
     * which are already proxied at fetch time.
     */
    fun proxyPokeWalletUrl(url: String): String {
        if (url.isBlank()) return url
        val proxyBase = BuildConfig.POKEWALLET_PROXY_URL.trim().trimEnd('/')
        if (!BuildConfig.POKEWALLET_PROXY_ENABLED || proxyBase.isBlank()) return url

        return try {
            val parsed = Uri.parse(url)
            val host = parsed.host?.lowercase().orEmpty()
            if (host != "api.pokewallet.io") return url

            val encodedPath = parsed.encodedPath?.trimStart('/').orEmpty()
            val encodedQuery = parsed.encodedQuery?.let { "?$it" }.orEmpty()
            "$proxyBase/$encodedPath$encodedQuery"
        } catch (_: Exception) {
            url
        }
    }

    /** [proxyPokeWalletUrl] then [safeImageUrl] -- what CollectionScreen needs in one call. */
    fun safeProxiedImageUrl(url: String): String = safeImageUrl(proxyPokeWalletUrl(url))
}
