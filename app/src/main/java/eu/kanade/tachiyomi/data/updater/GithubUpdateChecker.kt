package eu.kanade.tachiyomi.data.updater

import eu.kanade.tachiyomi.BuildConfig
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.network.parseAs
import eu.kanade.tachiyomi.util.lang.withIOContext
import uy.kohesive.injekt.injectLazy

class GithubUpdateChecker {

    private val networkService: NetworkHelper by injectLazy()

    private val repo: String by lazy {
        // Sy -->
        "scb261/TachiyomiXZM"
        // SY <--
    }

    suspend fun checkForUpdate(): GithubUpdateResult {
        return withIOContext {
            networkService.client
                .newCall(GET("https://api.github.com/repos/$repo/releases/latest"))
                .await()
                .parseAs<GithubRelease>()
                .let {
                    // Check if latest version is different from current version
                    if (/* SY --> */ isNewVersionXZM(it.version) /* SY <-- */) {
                        GithubUpdateResult.NewUpdate(it)
                    } else {
                        GithubUpdateResult.NoNewUpdate
                    }
                }
        }
    }

    // SY -->
    private fun isNewVersionXZM(versionTag: String) = versionTag != BuildConfig.VERSION_NAME
    // SY <--
}
