package org.koitharu.kotatsu.parsers

import androidx.annotation.CallSuper
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.Headers
import org.koitharu.kotatsu.parsers.config.ConfigKey
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.network.OkHttpWebClient
import org.koitharu.kotatsu.parsers.network.WebClient
import org.koitharu.kotatsu.parsers.util.FaviconParser
import org.koitharu.kotatsu.parsers.util.RelatedMangaFinder
import org.koitharu.kotatsu.parsers.util.domain
import org.koitharu.kotatsu.parsers.util.toAbsoluteUrl
import java.util.*

abstract class MangaParser @InternalParsersApi constructor(
	@property:InternalParsersApi val context: MangaLoaderContext,
	val source: MangaParserSource,
) {

	/**
	 * Supported [SortOrder] variants. Must not be empty.
	 *
	 * For better performance use [EnumSet] for more than one item.
	 */
	abstract val availableSortOrders: Set<SortOrder>

	/**
	 * Supported [MangaState] variants for filtering. May be empty.
	 *
	 * For better performance use [EnumSet] for more than one item.
	 */
	@Deprecated("")
	open val availableStates: Set<MangaState>
		get() = emptySet()

	open val filterCapabilities: MangaListFilterCapabilities
		get() = MangaListFilterCapabilities(
			isMultipleTagsSupported = isMultipleTagsSupported,
			isTagsExclusionSupported = isTagsExclusionSupported,
			isSearchSupported = isSearchSupported,
			isSearchWithFiltersSupported = searchSupportedWithMultipleFilters,
			isYearSupported = isSearchYearSupported,
			isYearRangeSupported = isSearchYearRangeSupported,
			isSourceLocaleSupported = isSearchOriginalLanguages,
		)

	/**
	 * Supported [ContentRating] variants for filtering. May be empty.
	 *
	 * For better performance use [EnumSet] for more than one item.
	 */
	@Deprecated("Use getListFilterCapabilities instead")
	open val availableContentRating: Set<ContentRating>
		get() = emptySet()

	/**
	 * Supported [ContentType] variants for filtering. May be empty.
	 *
	 * For better performance use [EnumSet] for more than one item.
	 */
	@Deprecated("Use getListFilterCapabilities instead")
	open val availableContentTypes: Set<ContentType>
		get() = emptySet()

	/**
	 * Supported [Demographic] variants for filtering. May be empty.
	 *
	 * For better performance use [EnumSet] for more than one item.
	 */
	@Deprecated("Use getListFilterCapabilities instead")
	open val availableDemographics: Set<Demographic>
		get() = emptySet()

	/**
	 * Whether parser supports filtering by more than one tag
	 */
	@Deprecated("Use getListFilterCapabilities instead")
	open val isMultipleTagsSupported: Boolean = true

	/**
	 * Whether parser supports tagsExclude field in filter
	 */
	@Deprecated("Use getListFilterCapabilities instead")
	open val isTagsExclusionSupported: Boolean = false

	/**
	 * Whether parser supports searching by string query using [MangaListFilter.Search]
	 */
	@Deprecated("Use getListFilterCapabilities instead")
	open val isSearchSupported: Boolean = true

	/**
	 * Whether parser supports searching by string query using [MangaListFilter.Advanced]
	 */
	@Deprecated("Use getListFilterCapabilities instead")
	open val searchSupportedWithMultipleFilters: Boolean = false

	/**
	 * Whether parser supports searching by year
	 */
	@Deprecated("Use getListFilterCapabilities instead")
	open val isSearchYearSupported: Boolean = false

	/**
	 * Whether parser supports searching by year range
	 */
	@Deprecated("Use getListFilterCapabilities instead")
	open val isSearchYearRangeSupported: Boolean = false

	/**
	 * Whether parser supports searching Original Languages
	 */
	@Deprecated("Use getListFilterCapabilities instead")
	open val isSearchOriginalLanguages: Boolean = false


	@Deprecated(
		message = "Use availableSortOrders instead",
		replaceWith = ReplaceWith("availableSortOrders"),
	)
	open val sortOrders: Set<SortOrder>
		get() = availableSortOrders

	val config by lazy { context.getConfig(source) }

	open val sourceLocale: Locale
		get() = if (source.locale.isEmpty()) Locale.ROOT else Locale(source.locale)

	val isNsfwSource = source.contentType == ContentType.HENTAI

	/**
	 * Provide default domain and available alternatives, if any.
	 *
	 * Never hardcode domain in requests, use [domain] instead.
	 */
	@InternalParsersApi
	abstract val configKeyDomain: ConfigKey.Domain

	protected open val userAgentKey = ConfigKey.UserAgent(context.getDefaultUserAgent())

	open fun getRequestHeaders(): Headers = Headers.Builder()
		.add("User-Agent", config[userAgentKey])
		.build()

	/**
	 * Used as fallback if value of `sortOrder` passed to [getList] is null
	 */
	open val defaultSortOrder: SortOrder
		get() {
			val supported = availableSortOrders
			return SortOrder.entries.first { it in supported }
		}

	@JvmField
	protected val webClient: WebClient = OkHttpWebClient(context.httpClient, source)

	/**
	 * Parse list of manga by specified criteria
	 *
	 * @param offset starting from 0 and used for pagination.
	 * Note than passed value may not be divisible by internal page size, so you should adjust it manually.
	 * @param order one of [availableSortOrders] or null for default value
	 * @param filter is a set of filter rules
	 */
	abstract suspend fun getList(offset: Int, order: SortOrder, filter: MangaListFilterV2): List<Manga>

	/**
	 * Parse details for [Manga]: chapters list, description, large cover, etc.
	 * Must return the same manga, may change any fields excepts id, url and source
	 * @see Manga.copy
	 */
	abstract suspend fun getDetails(manga: Manga): Manga

	/**
	 * Parse pages list for specified chapter.
	 * @see MangaPage for details
	 */
	abstract suspend fun getPages(chapter: MangaChapter): List<MangaPage>

	/**
	 * Fetch direct link to the page image.
	 */
	open suspend fun getPageUrl(page: MangaPage): String = page.url.toAbsoluteUrl(domain)

	/**
	 * Fetch available tags (genres) for source
	 */
	@Deprecated("Use getListFilterDatasets instead")
	open suspend fun getAvailableTags(): Set<MangaTag> = emptySet()

	open suspend fun getFilterOptions(): MangaListFilterOptions = coroutineScope {
		val tagsDeferred = async { getAvailableTags() }
		val localesDeferred = async { getAvailableLocales() }
		MangaListFilterOptions(
			availableTags = tagsDeferred.await(),
			availableStates = availableStates,
			availableContentRating = availableContentRating,
			availableContentTypes = availableContentTypes,
			availableDemographics = availableDemographics,
			availableLocales = localesDeferred.await(),
		)
	}

	/**
	 * Fetch available locales for multilingual sources
	 */
	@Deprecated("Use getListFilterDatasets instead")
	open suspend fun getAvailableLocales(): Set<Locale> = emptySet()

	@Deprecated(
		message = "Use getAvailableTags instead",
		replaceWith = ReplaceWith("getAvailableTags()"),
	)
	suspend fun getTags(): Set<MangaTag> = getAvailableTags()

	/**
	 * Parse favicons from the main page of the source`s website
	 */
	open suspend fun getFavicons(): Favicons {
		return FaviconParser(webClient, domain).parseFavicons()
	}

	@CallSuper
	open fun onCreateConfig(keys: MutableCollection<ConfigKey<*>>) {
		keys.add(configKeyDomain)
	}

	open suspend fun getRelatedManga(seed: Manga): List<Manga> {
		return RelatedMangaFinder(listOf(this)).invoke(seed)
	}

	protected fun getParser(source: MangaParserSource) = if (this.source == source) {
		this
	} else {
		context.newParserInstance(source)
	}
}
