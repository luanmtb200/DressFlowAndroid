package com.mrjack.dressflow.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Dashboard       : Screen("dashboard",       "Controle",       Icons.Default.Dashboard)
    object MeuPainel       : Screen("meu_painel",      "Meu Painel",     Icons.Default.Person)
    object Clientes        : Screen("clientes",        "Clientes",       Icons.Default.People)
    object Vendas          : Screen("vendas",          "Vendas",         Icons.Default.ShoppingBag)
    object Agenda          : Screen("agenda",          "Agenda",         Icons.Default.CalendarToday)
    object Padronizacoes   : Screen("padronizacoes",   "Padronizações",  Icons.Default.Groups)
    object Mural           : Screen("mural",           "Mural",          Icons.Default.Forum)
    object WhatsApp        : Screen("whatsapp",        "WhatsApp",       Icons.Default.Chat)
}

fun screensParaNivel(nivel: String): List<Screen> = when (nivel) {
    "ADMIN", "GERENCIA", "DIRETOR" -> listOf(
        Screen.Dashboard,
        Screen.Clientes,
        Screen.Vendas,
        Screen.Agenda,
        Screen.Padronizacoes,
        Screen.Mural,
        Screen.WhatsApp,
    )
    else -> listOf(
        Screen.MeuPainel,
        Screen.Clientes,
        Screen.Vendas,
        Screen.Agenda,
        Screen.Padronizacoes,
        Screen.Mural,
        Screen.WhatsApp,
    )
}
