package android.net

class FakeUri(private val uriString: String) : Uri() {
    override fun isHierarchical(): Boolean = true
    override fun isRelative(): Boolean = false
    override fun getScheme(): String? = when {
        uriString.startsWith("file://") -> "file"
        uriString.startsWith("content://") -> "content"
        uriString.startsWith("http://") -> "http"
        uriString.startsWith("https://") -> "https"
        else -> "content"
    }
    override fun getSchemeSpecificPart(): String = uriString
    override fun getEncodedSchemeSpecificPart(): String = uriString
    override fun getAuthority(): String? = "test.authority"
    override fun getEncodedAuthority(): String? = "test.authority"
    override fun getUserInfo(): String? = null
    override fun getEncodedUserInfo(): String? = null
    override fun getHost(): String? = null
    override fun getPort(): Int = -1
    override fun getPath(): String = when {
        uriString.startsWith("file://") -> uriString.removePrefix("file://")
        else -> uriString
    }
    override fun getEncodedPath(): String = uriString
    override fun getQuery(): String? = null
    override fun getEncodedQuery(): String? = null
    override fun getFragment(): String? = null
    override fun getEncodedFragment(): String? = null
    override fun getPathSegments(): List<String> = uriString.split("/").filter { it.isNotEmpty() }
    override fun getLastPathSegment(): String? = getPathSegments().lastOrNull()
    override fun buildUpon(): Builder = throw UnsupportedOperationException()
    override fun toString(): String = uriString
    override fun equals(other: Any?): Boolean = other is FakeUri && other.uriString == uriString
    override fun hashCode(): Int = uriString.hashCode()
    override fun compareTo(other: Uri?): Int = uriString.compareTo(other?.toString() ?: "")
    override fun describeContents(): Int = 0
    override fun writeToParcel(dest: android.os.Parcel, flags: Int) {}
}
