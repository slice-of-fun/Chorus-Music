

package pushkar.chorus.music.ui.utils

fun String.resize(
    width: Int? = null,
    height: Int? = null,
): String {
    if (width == null && height == null) return this

    
    
    
    
    if (this.contains("i.ytimg.com")) {
        val targetQuality = if (width != null && width >= 1200) "maxresdefault.jpg" else "hqdefault.jpg"
        return this.replace(
            Regex("(default|mqdefault|hqdefault|sddefault|maxresdefault)\\.jpg"),
            targetQuality
        )
    }

    if (this.contains("googleusercontent.com") && this.contains("=w")) {
        val baseUrl = this.split("=w")[0]
        val size = if ((width ?: 0) >= 1000 || (height ?: 0) >= 1000) 1200 else 500
        val afterW = this.substringAfter("=w")
        val suffix = if (afterW.contains("-")) "-" + afterW.substringAfter("-") else ""
        return "$baseUrl=w$size-h$size$suffix"
    }

    
    if (this.contains("yt3.ggpht.com")) {
        val baseUrl = this.split("=")[0]
        val afterEq = if (this.contains("=")) this.substringAfter("=") else ""
        val suffix = if (afterEq.contains("-")) "-" + afterEq.substringAfter("-") else ""
        val size = width ?: height ?: 1200
        return "$baseUrl=s$size$suffix"
    }

    
    "https://lh\\d\\.googleusercontent\\.com/.*".toRegex().matchEntire(this)?.let {
        val size = if ((width ?: 0) >= 1000 || (height ?: 0) >= 1000) 1200 else 500
        val baseUrl = this.split("=")[0]
        val afterEq = if (this.contains("=")) this.substringAfter("=") else ""
        val suffix = if (afterEq.contains("-")) "-" + afterEq.substringAfter("-") else ""
        return "$baseUrl=w$size-h$size$suffix"
    }

    return this
}
