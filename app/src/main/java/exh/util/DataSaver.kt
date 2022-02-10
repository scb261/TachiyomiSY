package exh.util

import com.tfcporciuncula.flow.Preference
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import tachiyomi.source.Source

interface DataSaver {

    fun compress(imageUrl: String): String

    companion object {
        val NoOp = object : DataSaver {
            override fun compress(imageUrl: String): String {
                return imageUrl
            }
        }
    }
}

fun DataSaver(source: Source, preferences: PreferencesHelper): DataSaver {
    return if (preferences.dataSaver().get() && source.id.toString() !in preferences.dataSaverExcludedSources().get()) {
        return DataSaverImpl(preferences)
    } else {
        DataSaver.NoOp
    }
}

private class DataSaverImpl(preferences: PreferencesHelper): DataSaver {
    private val dataSavedServer = preferences.dataSaverServer().get().trimEnd('/')

    private val ignoreJpg = preferences.ignoreJpeg().get()
    private val ignoreGif = preferences.ignoreGif().get()

    private val format = preferences.dataSaverImageFormatJpeg().toIntRepresentation()
    private val quality = preferences.dataSaverImageQuality().get()
    private val colorBW = preferences.dataSaverColorBW().toIntRepresentation()

    override fun compress(imageUrl: String): String {
        return if (dataSavedServer.isNotBlank() && !imageUrl.contains(dataSavedServer)) {
            when {
                imageUrl.contains(".jpeg", true) || imageUrl.contains(".jpg", true) -> if (ignoreJpg) imageUrl else getUrl(imageUrl)
                imageUrl.contains(".gif", true) -> if (ignoreGif) imageUrl else getUrl(imageUrl)
                else -> getUrl(imageUrl)
            }
        } else imageUrl
    }

    private fun getUrl(imageUrl: String): String {
        return "$dataSavedServer/?jpg=$format&l=$quality&bw=$colorBW&url=$imageUrl"
    }

    private fun Preference<Boolean>.toIntRepresentation() = if (get()) "1" else "0"
}
