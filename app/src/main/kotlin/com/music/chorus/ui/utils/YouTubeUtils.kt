

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

    val size = if ((width ?: 0) >= 1000 || (height ?: 0) >= 1000) 1200 else 500

    if (this.contains("googleusercontent.com") || this.contains("yt3.ggpht.com")) {
        return this.replace(Regex("([=\\-])w\\d+"), "$1w$size")
            .replace(Regex("([=\\-])h\\d+"), "$1h$size")
            .replace(Regex("([=\\-])s\\d+"), "$1s$size")
    }

    return this
}
