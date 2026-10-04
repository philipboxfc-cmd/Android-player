package app.fca.player.ui.navigation

object Routes {
    const val LIBRARY = "library"
    const val SEARCH = "search"
    const val NOW_PLAYING = "now_playing"
    const val EQUALIZER = "equalizer"
    const val AI_IMPORT = "ai_import"

    const val ALBUM = "album/{id}"
    fun album(id: Long) = "album/$id"

    const val ARTIST = "artist/{id}"
    fun artist(id: Long) = "artist/$id"

    const val PLAYLIST = "playlist/{id}"
    fun playlist(id: String) = "playlist/$id"

    const val PLAYLIST_EDIT = "playlist_edit?id={id}&preselect={preselect}"
    fun playlistEdit(id: String? = null, preselectTrackId: Long? = null): String {
        val params = buildList {
            if (id != null) add("id=$id")
            if (preselectTrackId != null) add("preselect=$preselectTrackId")
        }
        return if (params.isEmpty()) "playlist_edit" else "playlist_edit?" + params.joinToString("&")
    }
}
