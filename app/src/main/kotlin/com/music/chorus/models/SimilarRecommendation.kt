

package pushkar.chorus.music.models

import com.music.innertube.models.YTItem
import pushkar.chorus.music.db.entities.LocalItem

data class SimilarRecommendation(
    val title: LocalItem,
    val items: List<YTItem>,
)
