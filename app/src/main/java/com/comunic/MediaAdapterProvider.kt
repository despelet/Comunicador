import com.comunic.adapters.MediaAdapter

interface MediaAdapterProvider {
    fun getMediaAdapter(): MediaAdapter?
}
