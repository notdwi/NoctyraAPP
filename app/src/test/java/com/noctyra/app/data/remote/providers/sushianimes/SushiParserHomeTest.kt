package com.noctyra.app.data.remote.providers.sushianimes

import org.jsoup.Jsoup
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class SushiParserHomeTest {
    @Test
    fun parsesHomeSections() {
        val path = System.getenv("SUSHI_HOME_HTML").orEmpty()
        assumeTrue("SUSHI_HOME_HTML não definido", path.isNotEmpty() && File(path).exists())

        val feed = SushiParser.parseHome(Jsoup.parse(File(path), "UTF-8", "https://sushianimes.com.br/"))

        println("hero=${feed.hero.size} new=${feed.newEpisodes.size} trending=${feed.trending.size} most=${feed.mostWatched.size} animes=${feed.latestAnimes.size} movies=${feed.latestMovies.size}")
        feed.hero.take(2).forEach { println("HERO $it") }
        feed.newEpisodes.take(2).forEach { println("NEW $it") }
        feed.trending.take(2).forEach { println("HOT $it") }
        feed.latestMovies.take(1).forEach { println("MOVIE $it") }

        assertTrue(feed.hero.isNotEmpty())
        assertTrue(feed.hero.all { it.bannerUrl.startsWith("http") && it.synopsis.isNotEmpty() })
        assertTrue(feed.newEpisodes.all { it.latestEpisode > 0 && it.latestSeason > 0 })
        assertTrue(feed.trending.isNotEmpty() && feed.latestAnimes.isNotEmpty() && feed.latestMovies.isNotEmpty())
        assertTrue(feed.latestMovies.all { it.isMovie && it.slug.endsWith("-filme") })
        assertTrue((feed.latestAnimes + feed.trending).all { it.posterUrl.startsWith("http") })
    }
}
