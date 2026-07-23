package kr.dorondo.cinematomusic.model

data class Playlist(
    val name: String,
    val displayName: String,
    val tracks: MutableList<Track> = mutableListOf(),
    val loop: Boolean = false,
    val shuffle: Boolean = false
) {
    fun addTrack(track: Track) {
        tracks.add(track)
    }
    
    fun removeTrack(index: Int) {
        if (index in tracks.indices) {
            tracks.removeAt(index)
        }
    }
    
    fun moveTrack(fromIndex: Int, toIndex: Int) {
        if (fromIndex in tracks.indices && toIndex in tracks.indices) {
            val track = tracks.removeAt(fromIndex)
            tracks.add(toIndex, track)
        }
    }
    
    fun getTrack(index: Int): Track? {
        return tracks.getOrNull(index)
    }
    
    fun size(): Int = tracks.size
}


