package com.mrjack.dressflow

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.runtime.CompositionLocalProvider
import com.mrjack.dressflow.ui.navigation.LocalNavController
import com.mrjack.dressflow.ui.navigation.Screen
import com.mrjack.dressflow.ui.navigation.screensParaNivel
import com.mrjack.dressflow.ui.screens.agenda.AgendaScreen
import com.mrjack.dressflow.ui.screens.meupanel.MeuPainelScreen
import com.mrjack.dressflow.ui.screens.clientes.ClientesScreen
import com.mrjack.dressflow.ui.screens.dashboard.DashboardScreen
import com.mrjack.dressflow.ui.screens.login.LoginScreen
import com.mrjack.dressflow.ui.screens.mural.MuralScreen
import com.mrjack.dressflow.ui.screens.mural.MuralViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mrjack.dressflow.ui.screens.padronizacoes.PadronizacoesScreen
import com.mrjack.dressflow.ui.screens.vendas.VendasScreen
import com.mrjack.dressflow.ui.screens.whatsapp.WhatsAppScreen
import com.mrjack.dressflow.ui.theme.*
import com.mrjack.dressflow.updater.AppVersion
import com.mrjack.dressflow.updater.checkForUpdate
import com.mrjack.dressflow.updater.downloadAndInstallSync
import com.mrjack.dressflow.viewmodel.AuthState
import com.mrjack.dressflow.viewmodel.AuthViewModel

class MainActivity : ComponentActivity() {
    private val authViewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DressFlowTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    DressFlowApp(authViewModel)
                }
            }
        }
    }
}

@Composable
fun DressFlowApp(authViewModel: AuthViewModel) {
    val authState by authViewModel.state.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    var updatePendente by remember { mutableStateOf<AppVersion?>(null) }
    var baixando by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val v = checkForUpdate(context)
        if (v != null) updatePendente = v
    }

    // Diálogo de atualização in-app
    updatePendente?.let { v ->
        AlertDialog(
            onDismissRequest = { if (!baixando) updatePendente = null },
            title = { Text("Atualização disponível", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Versão ${v.version} disponível. (Atual: ${BuildConfig.VERSION_NAME})", fontSize = 14.sp)
                    if (v.notes.isNotBlank()) Text(v.notes, fontSize = 13.sp, color = Gray500)
                    if (baixando) {
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Blue600)
                        Text("Baixando e instalando...", fontSize = 12.sp, color = Gray500)
                    }
                }
            },
            confirmButton = {
                if (!baixando) {
                    Button(
                        onClick = {
                            baixando = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Blue600),
                    ) { Text("Atualizar agora") }
                }
            },
            dismissButton = {
                if (!baixando) {
                    TextButton(onClick = { updatePendente = null }) { Text("Mais tarde") }
                }
            },
        )
    }

    // Inicia o download quando o usuário confirmar
    LaunchedEffect(baixando) {
        val u = updatePendente
        if (baixando && u != null) {
            val ok = downloadAndInstallSync(context, u.apkUrl)
            if (!ok) {
                // Fallback: abre o link do APK no navegador
                try {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(u.apkUrl))
                    context.startActivity(intent)
                } catch (_: Exception) {}
            }
            baixando = false
            updatePendente = null
        }
    }

    when (val s = authState) {
        is AuthState.Loading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(
                        modifier = androidx.compose.ui.Modifier
                            .size(72.dp)
                            .background(Blue600, RoundedCornerShape(18.dp)),
                        contentAlignment = androidx.compose.ui.Alignment.Center,
                    ) {
                        Text("D", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Black)
                    }
                    CircularProgressIndicator(color = Blue600)
                }
            }
        }
        is AuthState.LoggedOut, is AuthState.Error -> {
            LoginScreen(authViewModel)
        }
        is AuthState.LoggedIn -> {
            MainApp(user = s.user, onLogout = { authViewModel.logout() })
        }
    }
}

@Composable
fun MainApp(user: com.mrjack.dressflow.data.model.UsuarioLogado, onLogout: () -> Unit) {
    val navController = rememberNavController()
    val screens = screensParaNivel(user.nivel)
    val currentBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStack?.destination?.route
    val muralVm: MuralViewModel = viewModel()
    val totalMuralNaoLidas by muralVm.totalNaoLidas.collectAsState()

    fun navigate(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.startDestinationId) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    CompositionLocalProvider(LocalNavController provides navController) {
    Scaffold(
        topBar = {
            Surface(shadowElevation = 2.dp, color = Color.White) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(Blue600, RoundedCornerShape(8.dp)),
                        contentAlignment = androidx.compose.ui.Alignment.Center,
                    ) {
                        Text("D", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text("DressFlow", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Gray900, modifier = Modifier.weight(1f))
                    Text(user.nome.split(" ").first(), fontSize = 13.sp, color = Gray500)
                    Spacer(Modifier.width(12.dp))
                    IconButton(onClick = onLogout) {
                        Icon(imageVector = Icons.Default.ExitToApp, contentDescription = "Sair", tint = Gray500)
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                screens.forEach { screen ->
                    val isMural = screen.route == Screen.Mural.route
                    NavigationBarItem(
                        selected = currentRoute == screen.route,
                        onClick = { navigate(screen.route) },
                        icon = {
                            if (isMural && totalMuralNaoLidas > 0) {
                                BadgedBox(badge = {
                                    Badge(containerColor = Color(0xFFEF4444)) {
                                        Text(
                                            if (totalMuralNaoLidas > 99) "99+" else "$totalMuralNaoLidas",
                                            fontSize = 8.sp,
                                            color = Color.White,
                                        )
                                    }
                                }) {
                                    Icon(screen.icon, contentDescription = screen.label, modifier = Modifier.size(22.dp))
                                }
                            } else {
                                Icon(screen.icon, contentDescription = screen.label, modifier = Modifier.size(22.dp))
                            }
                        },
                        label = { Text(screen.label, fontSize = 9.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Blue600,
                            selectedTextColor = Blue600,
                            indicatorColor = Blue50,
                            unselectedIconColor = Gray500,
                            unselectedTextColor = Gray500,
                        ),
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            val startRoute = screens.firstOrNull()?.route ?: Screen.Vendas.route
            NavHost(navController = navController, startDestination = startRoute) {
                composable(Screen.Dashboard.route)     { DashboardScreen() }
                composable(Screen.MeuPainel.route)     { MeuPainelScreen(nomeVendedor = user.nome, vendedorId = user.vendedorId, usuarioId = user.id) }
                composable(Screen.Clientes.route)      { ClientesScreen() }
                composable(Screen.Vendas.route)        { VendasScreen() }
                composable(Screen.Agenda.route)        { AgendaScreen() }
                composable(Screen.Padronizacoes.route) { PadronizacoesScreen() }
                composable(Screen.Mural.route)         { MuralScreen(vm = muralVm) }
                composable(Screen.WhatsApp.route)      { WhatsAppScreen() }
            }
        }
    }
    } // CompositionLocalProvider
}
