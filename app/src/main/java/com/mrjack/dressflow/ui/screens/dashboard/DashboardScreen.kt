package com.mrjack.dressflow.ui.screens.dashboard

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mrjack.dressflow.data.api.NetworkModule
import com.mrjack.dressflow.data.model.Locacao
import com.mrjack.dressflow.data.model.VendasDia
import com.mrjack.dressflow.ui.theme.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class DashboardViewModel(app: Application) : AndroidViewModel(app) {
    private val api = NetworkModule.provideApiService(app)

    val vendasDia = MutableStateFlow<VendasDia?>(null)
    val orcamentos = MutableStateFlow<List<Locacao>>(emptyList())
    val devolucoes = MutableStateFlow<List<Locacao>>(emptyList())
    val isLoading = MutableStateFlow(true)
    val erro = MutableStateFlow<String?>(null)

    init { carregar() }

    fun carregar() {
        viewModelScope.launch {
            isLoading.value = true
            erro.value = null
            try {
                vendasDia.value = api.vendasDia().body()
                orcamentos.value = api.orcamentosAtivos().body() ?: emptyList()
                devolucoes.value = api.devolucoesPendentes().body() ?: emptyList()
            } catch (e: Exception) {
                erro.value = e.message
            } finally {
                isLoading.value = false
            }
        }
    }
}

@Composable
fun DashboardScreen(vm: DashboardViewModel = viewModel()) {
    val vendasDia by vm.vendasDia.collectAsState()
    val orcamentos by vm.orcamentos.collectAsState()
    val devolucoes by vm.devolucoes.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val erro by vm.erro.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text("Controle", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Gray900)

        if (isLoading) {
            Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Blue600)
            }
            return@Column
        }

        if (erro != null) {
            ErroCard(erro!!) { vm.carregar() }
            return@Column
        }

        // Cards de métricas do dia
        vendasDia?.let { vd ->
            Text("Hoje", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Gray500)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard(
                    modifier = Modifier.weight(1f),
                    label = "Atendimentos",
                    valor = "${vd.total}",
                    cor = Blue600,
                )
                MetricCard(
                    modifier = Modifier.weight(1f),
                    label = "Locações",
                    valor = "${vd.totalLocacoes}",
                    cor = Green600,
                )
                MetricCard(
                    modifier = Modifier.weight(1f),
                    label = "Orçamentos",
                    valor = "${vd.totalOrcamentos}",
                    cor = Amber500,
                )
                MetricCard(
                    modifier = Modifier.weight(1f),
                    label = "Faturado",
                    valor = "R$ ${String.format("%.0f", vd.valorTotal)}",
                    cor = Indigo600,
                )
            }
        }

        // Devoluções pendentes
        if (devolucoes.isNotEmpty()) {
            SectionCard(
                title = "Devoluções pendentes",
                count = devolucoes.size,
                countColor = Amber500,
            ) {
                devolucoes.take(5).forEach { loc ->
                    ListItem(
                        headlineContent = { Text(loc.cliente?.nome ?: "—", fontWeight = FontWeight.Medium) },
                        supportingContent = { Text(loc.traje + " · Evento: " + fmtData(loc.dataEvento)) },
                    )
                    HorizontalDivider()
                }
            }
        }

        // Orçamentos ativos
        if (orcamentos.isNotEmpty()) {
            SectionCard(
                title = "Orçamentos ativos",
                count = orcamentos.size,
                countColor = Blue600,
            ) {
                orcamentos.take(5).forEach { loc ->
                    ListItem(
                        headlineContent = { Text(loc.cliente?.nome ?: "—", fontWeight = FontWeight.Medium) },
                        supportingContent = { Text(loc.evento ?: "Sem evento" + " · " + fmtData(loc.dataEvento)) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
fun MetricCard(modifier: Modifier, label: String, valor: String, cor: Color) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, fontSize = 11.sp, color = Gray500, fontWeight = FontWeight.Medium)
            Text(valor, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = cor)
        }
    }
}

@Composable
fun SectionCard(title: String, count: Int, countColor: Color, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Column {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Gray900)
                Badge(containerColor = countColor) { Text("$count", color = Color.White, fontSize = 11.sp) }
            }
            HorizontalDivider()
            content()
        }
    }
}

@Composable
fun ErroCard(mensagem: String, onRetry: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Red100),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(mensagem, color = Red500, fontSize = 13.sp)
            TextButton(onClick = onRetry) { Text("Tentar novamente") }
        }
    }
}

fun fmtData(dateStr: String?): String {
    if (dateStr == null) return "—"
    return try {
        val parts = dateStr.substring(0, 10).split("-")
        "${parts[2]}/${parts[1]}/${parts[0]}"
    } catch (e: Exception) { dateStr }
}
