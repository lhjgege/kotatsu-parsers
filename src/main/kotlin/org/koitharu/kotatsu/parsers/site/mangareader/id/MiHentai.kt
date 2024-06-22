package org.koitharu.kotatsu.parsers.site.mangareader.id

import org.koitharu.kotatsu.parsers.MangaLoaderContext
import org.koitharu.kotatsu.parsers.MangaSourceParser
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.site.mangareader.MangaReaderParser

@MangaSourceParser("MIHENTAI", "MiHentai", "id", ContentType.HENTAI)
internal class MiHentai(context: MangaLoaderContext) :
	MangaReaderParser(context, MangaSource.MIHENTAI, "mihentai.com", pageSize = 30, searchPageSize = 10) {
	override val datePattern = "MMM d, yyyy"
	override val isTagsExclusionSupported = false
}
