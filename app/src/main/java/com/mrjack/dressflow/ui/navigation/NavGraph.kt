package com.mrjack.dressflow.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import kotlinx.coroutines.flow.MutableStateFlow

val LocalNavController = compositionLocalOf<NavController> { error("No NavController") }

object WaDeeplink {
    val targetPhone = MutableStateFlow<String?>(null)
}

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Dashboard       : Screen("dashboard",       "Controle",       Icons.Default.Dashboard)
    object MeuPainel       : Screen("meu_painel",      "Meu Painel",     Icons.Default.Person)
    object Clientes        : Screen("clientes",        "Clientes",       Icons.Default.People)
    object Vendas          : Screen("vendas",          "Vendas",         Icons.Default.ShoppingBag)
    object Agenda          : Screen("agenda",          "Agenda",         Icons.Default.CalendarToday)
    object Padronizacoes   : Screen("padronizacoes",   "Padronizações",  Icons.Default.Groups)
    object Mural           : Screen("mural",           "Mural",          Icons.Default.Forum)
    object WhatsApp        : Screen("whatsapp",        "WhatsApp",       Icons.Default.Chat)
    object Academia        : Screen("academia",        "Academia",       Icons.Default.School)
}

fun screensParaNivel(nivel: String): List<Screen> = when (nivel) {
    "ADMIN", "GERENCIA", "DIRETOR" -> listOf(
        Screen.Dashboard,
        Screen.Clientes,
        Screen.Vendas,
        Screen.Agenda,
        Screen.Padronizacoes,
        Screen.Mural,
        Screen.Academia,
    )
    else -> listOf(
        Screen.MeuPainel,
        Screen.Clientes,
        Screen.Vendas,
        Screen.Agenda,
        Screen.Padronizacoes,
        Screen.Mural,
        Screen.Academia,
    )
}
