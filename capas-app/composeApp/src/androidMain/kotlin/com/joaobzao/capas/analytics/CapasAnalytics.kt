package com.joaobzao.capas.analytics

import com.google.firebase.Firebase
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.analytics
import com.google.firebase.analytics.logEvent

/**
 * Central place for every analytics event the app sends. Screens call these
 * typed functions instead of talking to Firebase directly, so event and
 * parameter names live in a single file.
 */
object CapasAnalytics {

    private const val PARAM_CAPA_ID = "capa_id"
    private const val PARAM_CAPA_NOME = "capa_nome"
    private const val PARAM_SOURCE = "source"
    private const val PARAM_SCREEN = "screen"
    private const val PARAM_CATEGORY = "category"
    private const val PARAM_LINK = "link"
    private const val PARAM_ACTION = "action"

    private val analytics get() = Firebase.analytics

    fun trackScreenView(screenName: String) {
        analytics.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW) {
            param(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
        }
    }

    /** A capa foi aberta a partir de uma grelha (lista ou favoritos). */
    fun trackCapaOpened(id: String, nome: String, source: String) {
        analytics.logEvent(FirebaseAnalytics.Event.SELECT_CONTENT) {
            param(FirebaseAnalytics.Param.ITEM_ID, id)
            param(FirebaseAnalytics.Param.ITEM_NAME, nome)
            param(FirebaseAnalytics.Param.CONTENT_TYPE, "capa")
            param(PARAM_SOURCE, source)
        }
    }

    /** Uma página do detalhe ficou visível (inclui swipes e botões anterior/próxima). */
    fun trackCapaViewed(id: String, nome: String) {
        analytics.logEvent("capa_viewed") {
            param(PARAM_CAPA_ID, id)
            param(PARAM_CAPA_NOME, nome)
        }
    }

    fun trackCapaZoomed(id: String) {
        analytics.logEvent("capa_zoomed") {
            param(PARAM_CAPA_ID, id)
        }
    }

    fun trackCategorySelected(category: String) {
        analytics.logEvent("category_selected") {
            param(PARAM_CATEGORY, category)
        }
    }

    fun trackSearch(query: String, screen: String) {
        analytics.logEvent(FirebaseAnalytics.Event.SEARCH) {
            param(FirebaseAnalytics.Param.SEARCH_TERM, query)
            param(PARAM_SCREEN, screen)
        }
    }

    fun trackFavoriteAdded(id: String, nome: String, screen: String) {
        analytics.logEvent("favorite_added") {
            param(PARAM_CAPA_ID, id)
            param(PARAM_CAPA_NOME, nome)
            param(PARAM_SCREEN, screen)
        }
    }

    fun trackFavoriteRemoved(id: String, nome: String, screen: String) {
        analytics.logEvent("favorite_removed") {
            param(PARAM_CAPA_ID, id)
            param(PARAM_CAPA_NOME, nome)
            param(PARAM_SCREEN, screen)
        }
    }

    /** Capa arrastada para o caixote do lixo. */
    fun trackCapaRemoved(id: String, nome: String) {
        analytics.logEvent("capa_removed") {
            param(PARAM_CAPA_ID, id)
            param(PARAM_CAPA_NOME, nome)
        }
    }

    fun trackCapaRestored(id: String, nome: String) {
        analytics.logEvent("capa_restored") {
            param(PARAM_CAPA_ID, id)
            param(PARAM_CAPA_NOME, nome)
        }
    }

    fun trackReorder(screen: String, category: String?) {
        analytics.logEvent("capas_reordered") {
            param(PARAM_SCREEN, screen)
            if (category != null) param(PARAM_CATEGORY, category)
        }
    }

    fun trackOnboardingCompleted() {
        analytics.logEvent(FirebaseAnalytics.Event.TUTORIAL_COMPLETE) {}
    }

    /** link: donate | email | privacy */
    fun trackAboutLinkClicked(link: String) {
        analytics.logEvent("about_link_clicked") {
            param(PARAM_LINK, link)
        }
    }

    /** action: see_covers | dismiss */
    fun trackInternationalAnnouncementSeen(action: String) {
        analytics.logEvent("intl_announcement_seen") {
            param(PARAM_ACTION, action)
        }
    }

    /** O fluxo automático de avaliação in-app foi lançado após N aberturas. */
    fun trackRatePromptShown() {
        analytics.logEvent("rate_prompt_shown") {}
    }

    /** O utilizador tocou em "Avaliar a app" no ecrã Sobre. */
    fun trackRateButtonClicked() {
        analytics.logEvent("rate_button_clicked") {}
    }
}
