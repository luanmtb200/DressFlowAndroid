package com.mrjack.dressflow.ui.screens.meupanel

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mrjack.dressflow.data.api.NetworkModule
import com.mrjack.dressflow.data.model.Locacao
import com.mrjack.dressflow.ui.components.WaBotao
import com.mrjack.dressflow.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// ── ViewModel ─────────────────────────────────────────────────────────────────

class MeuPainelViewModel(app: Application) : AndroidViewModel(app) {
    private val api = NetworkModule.provideApiService(app)

    val locacoesMes = MutableStateFlow<List<Locacao>>(emptyList())
    val orcamentos = MutableStateFlow<List<Locacao>>(emptyList())
    val orcamentosCancelados = MutableStateFlow<List<Locacao>>(emptyList())
    val isLoadingMes = MutableStateFlow(false)
    val isLoadingOrc = MutableStateFlow(false)
    val erro = MutableStateFlow<String?>(null)

    fun carregarLocacoesMes(vendedorId: Int, mes: String) {
        viewModelScope.launch {
            isLoadingMes.value = true
            erro.value = null
            try {
                locacoesMes.value = withContext(Dispatchers.IO) {
                    api.locacoesDoVendedor(vendedorId, mes).body() ?: emptyList()
                }
            } catch (e: Exception) {
                erro.value = "Erro ao carregar locações: ${e.message}"
            } finally {
                isLoadingMes.value = false
            }
        }
    }

    fun carregarOrcamentos(vendedorId: Int) {
        viewModelScope.launch {
            isLoadingOrc.value = true
            try {
                // Paralelo: busca ativos e cancelados ao mesmo tempo
                val dAtivos    = async(Dispatchers.IO) { api.orcamentosDoVendedor(vendedorId).body() ?: emptyList() }
                val dCancelados = async(Dispatchers.IO) { api.orcamentosCanceladosDoVendedor(vendedorId).body() ?: emptyList() }
                orcamentos.value           = dAtivos.await()
                orcamentosCancelados.value = dCancelados.await()
            } catch (e: Exception) {
                erro.value = "Erro ao carregar orçamentos: ${e.message}"
            } finally {
                isLoadingOrc.value = false
            }
        }
    }

    fun salvarAnotacoes(id: Int, motivoNaoFechar: String?, observacoes: String?) {
        viewModelScope.launch {
            try {
                val body = mapOf<String, String?>(
                    "motivoNaoFechar" to motivoNaoFechar,
                    "observacoes" to observacoes,
                )
                api.atualizarAnotacoes(id, body)
            } catch (_: Exception) {}
        }
    }

    fun solicitarCancelamento(id: Int, motivo: String, vendedorId: Int, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                api.solicitarCancelamento(id, mapOf("motivo" to motivo))
                carregarOrcamentos(vendedorId)
                onSuccess()
            } catch (e: Exception) {
                erro.value = "Erro: ${e.message}"
            }
        }
    }

    fun editarMedidas(id: Int, body: Map<String, Any?>, vendedorId: Int, mes: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { api.atualizarLocacao(id, body) }
                // Recarrega locações e orçamentos em paralelo após edição
                val dLoc = async { withContext(Dispatchers.IO) { api.locacoesDoVendedor(vendedorId, mes).body() ?: emptyList() } }
                val dOrc = async { withContext(Dispatchers.IO) { api.orcamentosDoVendedor(vendedorId).body() ?: emptyList() } }
                val dCan = async { withContext(Dispatchers.IO) { api.orcamentosCanceladosDoVendedor(vendedorId).body() ?: emptyList() } }
                locacoesMes.value          = dLoc.await()
                orcamentos.value           = dOrc.await()
                orcamentosCancelados.value = dCan.await()
                onSuccess()
            } catch (e: Exception) {
                erro.value = "Erro: ${e.message}"
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun mesAtualStr(): String {
    val d = LocalDate.now()
    return "%04d-%02d".format(d.year, d.monthValue)
}

private fun mesLabel(mes: String): String {
    return try {
        val parts = mes.split("-")
        val date = LocalDate.of(parts[0].toInt(), parts[1].toInt(), 1)
        date.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale("pt", "BR")))
            .replaceFirstChar { it.uppercase() }
    } catch (_: Exception) { mes }
}

private fun brl(v: Double): String {
    return "R$ %,.2f".format(v).replace(",", "X").replace(".", ",").replace("X", ".")
}

private fun fmtData(s: String?): String {
    if (s == null) return "—"
    return try { "${s.substring(8, 10)}/${s.substring(5, 7)}/${s.substring(0, 4)}" } catch (_: Exception) { s }
}

private enum class AlertaData { PASSADO, URGENTE, NENHUM }

private fun getAlerta(dataEvento: String?): AlertaData {
    if (dataEvento == null) return AlertaData.NENHUM
    return try {
        val hoje = LocalDate.now()
        val evento = LocalDate.parse(dataEvento.substring(0, 10))
        val diff = java.time.temporal.ChronoUnit.DAYS.between(hoje, evento)
        when {
            diff < 0   -> AlertaData.PASSADO
            diff <= 7  -> AlertaData.URGENTE
            else       -> AlertaData.NENHUM
        }
    } catch (_: Exception) { AlertaData.NENHUM }
}

// ── Tela principal ────────────────────────────────────────────────────────────

@Composable
fun MeuPainelScreen(
    nomeVendedor: String,
    vendedorId: Int?,
    vm: MeuPainelViewModel = viewModel(),
) {
    var aba by remember { mutableIntStateOf(0) }
    val mesAtual = remember { mesAtualStr() }
    var mes by remember { mutableStateOf(mesAtual) }
    var verTodosMeses by remember { mutableStateOf(false) }
    var buscaLoc by remember { mutableStateOf("") }
    var buscaOrc by remember { mutableStateOf("") }
    var editarLocacao by remember { mutableStateOf<Locacao?>(null) }
    var modalOrc by remember { mutableStateOf<Locacao?>(null) }

    val locacoesMes by vm.locacoesMes.collectAsState()
    val orcamentosRaw by vm.orcamentos.collectAsState()
    val orcamentosCancelados by vm.orcamentosCancelados.collectAsState()
    val isLoadingMes by vm.isLoadingMes.collectAsState()
    val isLoadingOrc by vm.isLoadingOrc.collectAsState()
    val erro by vm.erro.collectAsState()

    // Um único LaunchedEffect que dispara locações e orçamentos ao mesmo tempo
    LaunchedEffect(vendedorId, mes) {
        if (vendedorId != null) {
            vm.carregarLocacoesMes(vendedorId, mes)
            vm.carregarOrcamentos(vendedorId)
        }
    }

    val orcamentos by remember {
        derivedStateOf {
            if (verTodosMeses) orcamentosRaw
            else orcamentosRaw.filter { it.createdAt.take(7) == mesAtual }
        }
    }
    val passados by remember { derivedStateOf { orcamentos.count { getAlerta(it.dataEvento) == AlertaData.PASSADO } } }
    val urgentes by remember { derivedStateOf { orcamentos.count { getAlerta(it.dataEvento) == AlertaData.URGENTE } } }

    val locFiltradas by remember {
        derivedStateOf {
            locacoesMes
                .filter { buscaLoc.isBlank() || it.cliente?.nome?.contains(buscaLoc, ignoreCase = true) == true }
                .sortedBy { it.cliente?.nome ?: "" }
        }
    }
    val orcFiltrados by remember {
        derivedStateOf {
            orcamentos
                .filter { buscaOrc.isBlank() || it.cliente?.nome?.contains(buscaOrc, ignoreCase = true) == true }
                .sortedBy { it.cliente?.nome ?: "" }
        }
    }

    if (vendedorId == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Perfil sem vendedorId configurado.", color = Gray500, fontSize = 14.sp)
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        // Cabeçalho
        Column(Modifier.padding(horizontal = 20.dp).padding(top = 20.dp, bottom = 8.dp)) {
            Text(
                "Olá, ${nomeVendedor.split(" ").first()}",
                fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Gray900,
            )
        }

        if (erro != null) {
            Card(
                modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Red100),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text(erro!!, modifier = Modifier.padding(12.dp), color = Red500, fontSize = 13.sp)
            }
            Spacer(Modifier.height(8.dp))
        }

        // Tabs
        TabRow(
            selectedTabIndex = aba,
            containerColor = Color.White,
            contentColor = Blue600,
            divider = { HorizontalDivider(color = Gray200) },
        ) {
            Tab(selected = aba == 0, onClick = { aba = 0 }) {
                Row(
                    modifier = Modifier.padding(vertical = 14.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Minhas Locações", fontSize = 13.sp, fontWeight = if (aba == 0) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
            Tab(selected = aba == 1, onClick = { aba = 1 }) {
                Row(
                    modifier = Modifier.padding(vertical = 14.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Meus Orçamentos", fontSize = 13.sp, fontWeight = if (aba == 1) FontWeight.SemiBold else FontWeight.Normal)
                    if (orcamentos.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Amber100)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text("${orcamentos.size}", fontSize = 11.sp, color = Yellow700, fontWeight = FontWeight.Medium)
                        }
                    }
                    if (passados > 0) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Red100)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text("$passados vencido${if (passados != 1) "s" else ""}", fontSize = 10.sp, color = Red500, fontWeight = FontWeight.Medium)
                        }
                    } else if (urgentes > 0) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Yellow200)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text("$urgentes urgente${if (urgentes != 1) "s" else ""}", fontSize = 10.sp, color = Yellow800, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }

        when (aba) {
            0 -> MinhasLocacoesTab(
                locacoes = locFiltradas,
                isLoading = isLoadingMes,
                mes = mes,
                busca = buscaLoc,
                onBuscaChange = { buscaLoc = it },
                onMesChange = { mes = it; buscaLoc = "" },
                onEditar = { editarLocacao = it },
            )
            1 -> MeusOrcamentosTab(
                orcamentos = orcFiltrados,
                cancelados = orcamentosCancelados.filter {
                    buscaOrc.isBlank() || it.cliente?.nome?.contains(buscaOrc, ignoreCase = true) == true
                },
                isLoading = isLoadingOrc,
                busca = buscaOrc,
                verTodosMeses = verTodosMeses,
                onBuscaChange = { buscaOrc = it },
                onToggleMeses = { verTodosMeses = !verTodosMeses },
                onMarcarPerdido = { modalOrc = it },
                onEditar = { editarLocacao = it },
                vm = vm,
            )
        }
    }

    // Modal editar medidas
    editarLocacao?.let { loc ->
        EditarMedidasModal(
            locacao = loc,
            onDismiss = { editarLocacao = null },
            onSalvar = { body ->
                vm.editarMedidas(loc.id, body, vendedorId, mes) { editarLocacao = null }
            },
        )
    }

    // Modal marcar como perdido
    modalOrc?.let { loc ->
        MarcarPerdidoModal(
            locacao = loc,
            onDismiss = { modalOrc = null },
            onConfirmar = { motivo ->
                vm.solicitarCancelamento(loc.id, motivo, vendedorId) { modalOrc = null }
            },
        )
    }
}

// ── Aba: Minhas Locações ──────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MinhasLocacoesTab(
    locacoes: List<Locacao>,
    isLoading: Boolean,
    mes: String,
    busca: String,
    onBuscaChange: (String) -> Unit,
    onMesChange: (String) -> Unit,
    onEditar: (Locacao) -> Unit,
) {
    val total = locacoes.sumOf { it.valor.toDoubleOrNull() ?: 0.0 }
    var showMesPicker by remember { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Card de stats do mês
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Blue50),
                border = androidx.compose.foundation.BorderStroke(1.dp, Blue200),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(mesLabel(mes), fontSize = 13.sp, color = Blue600, fontWeight = FontWeight.Medium)
                        Text(brl(total), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E3A8A))
                        Text(
                            "${locacoes.size} registro${if (locacoes.size != 1) "s" else ""}",
                            fontSize = 12.sp, color = Blue600,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Selecionar mês", fontSize = 11.sp, color = Blue600)
                        OutlinedButton(
                            onClick = { showMesPicker = true },
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Blue200),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text(mes, fontSize = 13.sp, color = Blue600)
                        }
                    }
                }
            }
        }

        // Campo de busca
        item {
            OutlinedTextField(
                value = busca,
                onValueChange = onBuscaChange,
                placeholder = { Text("Buscar cliente...", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Blue600,
                    unfocusedBorderColor = Gray200,
                ),
            )
        }

        if (isLoading) {
            item {
                Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Blue600)
                }
            }
        } else if (locacoes.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (busca.isNotBlank()) "Nenhum resultado encontrado." else "Nenhuma locação neste mês.",
                        color = Gray500, fontSize = 14.sp,
                    )
                }
            }
        } else {
            // Tabela
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Gray200),
                ) {
                    // Header da tabela
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Gray50)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text("Cliente", Modifier.weight(2f), fontSize = 11.sp, color = Gray500, fontWeight = FontWeight.Medium)
                        Text("Evento", Modifier.weight(1.5f), fontSize = 11.sp, color = Gray500, fontWeight = FontWeight.Medium)
                        Text("Tipo", Modifier.weight(1f), fontSize = 11.sp, color = Gray500, fontWeight = FontWeight.Medium)
                        Text("Valor", Modifier.weight(1f), fontSize = 11.sp, color = Gray500, fontWeight = FontWeight.Medium, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                        Spacer(Modifier.width(36.dp))
                    }
                    HorizontalDivider(color = Gray200)

                    locacoes.forEachIndexed { i, l ->
                        if (i > 0) HorizontalDivider(color = Gray100)
                        LocacaoRow(l, onEditar)
                    }

                    // Footer total
                    HorizontalDivider(color = Gray200)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Gray50)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(
                            "Total",
                            modifier = Modifier.weight(4.5f),
                            fontWeight = FontWeight.SemiBold, color = Gray700, fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        )
                        Text(
                            brl(total),
                            modifier = Modifier.weight(1f),
                            fontWeight = FontWeight.SemiBold, color = Gray900, fontSize = 13.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        )
                        Spacer(Modifier.width(36.dp))
                    }
                }
            }
        }
    }

    if (showMesPicker) {
        MesPickerDialog(mesAtual = mes, onDismiss = { showMesPicker = false }, onConfirm = { onMesChange(it); showMesPicker = false })
    }
}

@Composable
private fun LocacaoRow(l: Locacao, onEditar: (Locacao) -> Unit) {
    val (badgeBg, badgeText, badgeLabel) = when (l.tipo) {
        "LOCACAO"   -> Triple(Blue100,  Blue600,  "Loc")
        "ORCAMENTO" -> Triple(Amber100, Amber500, "Orç")
        else        -> Triple(Green100, Green600, "Vnd")
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(2f)) {
            Text(l.cliente?.nome ?: "—", fontWeight = FontWeight.Medium, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(l.cliente?.telefone ?: "—", fontSize = 11.sp, color = Gray500)
        }
        Column(Modifier.weight(1.5f)) {
            Text(l.evento ?: l.traje, fontSize = 12.sp, color = Gray700, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(fmtData(l.dataEvento), fontSize = 11.sp, color = Gray500)
        }
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier.clip(RoundedCornerShape(50)).background(badgeBg).padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(badgeLabel, fontSize = 10.sp, color = badgeText, fontWeight = FontWeight.Medium)
            }
        }
        Text(
            brl(l.valor.toDoubleOrNull() ?: 0.0),
            modifier = Modifier.weight(1f),
            fontWeight = FontWeight.Medium, fontSize = 12.sp, color = Gray900,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
        WaBotao(l.cliente?.telefone, modifier = Modifier.size(36.dp))
        IconButton(onClick = { onEditar(l) }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Edit, contentDescription = "Editar", tint = Blue600, modifier = Modifier.size(16.dp))
        }
    }
}

// ── Aba: Meus Orçamentos ──────────────────────────────────────────────────────

@Composable
private fun MeusOrcamentosTab(
    orcamentos: List<Locacao>,
    cancelados: List<Locacao>,
    isLoading: Boolean,
    busca: String,
    verTodosMeses: Boolean,
    onBuscaChange: (String) -> Unit,
    onToggleMeses: () -> Unit,
    onMarcarPerdido: (Locacao) -> Unit,
    onEditar: (Locacao) -> Unit,
    vm: MeuPainelViewModel,
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Toolbar: descrição + toggle meses
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Orçamentos ativos aguardando confirmação.", fontSize = 12.sp, color = Gray500)
                OutlinedButton(
                    onClick = onToggleMeses,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (verTodosMeses) Blue600 else Color.Transparent,
                        contentColor = if (verTodosMeses) Color.White else Gray700,
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (verTodosMeses) Blue600 else Gray200),
                ) {
                    Text(if (verTodosMeses) "Todos os meses" else "Mês atual", fontSize = 11.sp)
                }
            }
        }

        // Busca
        item {
            OutlinedTextField(
                value = busca,
                onValueChange = onBuscaChange,
                placeholder = { Text("Buscar cliente...", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Amber500,
                    unfocusedBorderColor = Gray200,
                ),
            )
        }

        if (isLoading) {
            item {
                Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Amber500)
                }
            }
        } else if (orcamentos.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (busca.isNotBlank()) "Nenhum resultado encontrado." else "Nenhum orçamento ativo.",
                        color = Gray500, fontSize = 14.sp,
                    )
                }
            }
        } else {
            items(orcamentos, key = { it.id }) { l ->
                OrcamentoCard(
                    locacao = l,
                    onMarcarPerdido = onMarcarPerdido,
                    onEditar = onEditar,
                    vm = vm,
                )
            }
        }

        // Orçamentos cancelados
        if (cancelados.isNotEmpty()) {
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    "ORÇAMENTOS CANCELADOS (${cancelados.size})",
                    fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Gray500,
                    letterSpacing = 0.5.sp,
                )
                HorizontalDivider(modifier = Modifier.padding(top = 4.dp), color = Gray200)
            }
            items(cancelados, key = { "c${it.id}" }) { l ->
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Gray200),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(l.cliente?.nome ?: "—", fontWeight = FontWeight.Medium, fontSize = 13.sp, color = Gray700)
                            Text(
                                "${l.evento ?: l.traje} — ${fmtData(l.dataEvento)}",
                                fontSize = 11.sp, color = Gray500,
                            )
                            if (!l.motivoCancelamento.isNullOrBlank()) {
                                Text("Motivo: ${l.motivoCancelamento}", fontSize = 11.sp, color = Red500)
                            }
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Red100)
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            Text("Cancelado", fontSize = 10.sp, color = Red500, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

// ── Card de orçamento ─────────────────────────────────────────────────────────

@Composable
private fun OrcamentoCard(
    locacao: Locacao,
    onMarcarPerdido: (Locacao) -> Unit,
    onEditar: (Locacao) -> Unit,
    vm: MeuPainelViewModel,
) {
    val alerta = getAlerta(locacao.dataEvento)
    var motivoNaoFechar by remember(locacao.id) { mutableStateOf(locacao.motivoNaoFechar ?: "") }
    var observacoes by remember(locacao.id) { mutableStateOf(locacao.observacoes ?: "") }

    // Auto-save com debounce 800ms
    LaunchedEffect(motivoNaoFechar) {
        delay(800)
        vm.salvarAnotacoes(locacao.id, motivoNaoFechar, null)
    }
    LaunchedEffect(observacoes) {
        delay(800)
        vm.salvarAnotacoes(locacao.id, null, observacoes)
    }

    val borderColor = when (alerta) {
        AlertaData.PASSADO -> Red300
        AlertaData.URGENTE -> Yellow300
        AlertaData.NENHUM  -> Gray200
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
    ) {
        Column {
            // Banner de alerta
            when (alerta) {
                AlertaData.PASSADO -> Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Red50)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("⚠ Evento já passou!", fontSize = 13.sp, color = Red500, fontWeight = FontWeight.SemiBold)
                    Text("Envie como orçamento perdido se não fechou.", fontSize = 11.sp, color = Red500)
                }
                AlertaData.URGENTE -> Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Yellow50)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("⏰ Evento se aproximando!", fontSize = 13.sp, color = Yellow700, fontWeight = FontWeight.SemiBold)
                    Text("Falta 1 semana ou menos.", fontSize = 11.sp, color = Yellow700)
                }
                else -> {}
            }

            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Nome + valor
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column {
                        Text(locacao.cliente?.nome ?: "—", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Gray900)
                        if (!locacao.cliente?.tipoCliente.isNullOrBlank()) {
                            Text(locacao.cliente?.tipoCliente ?: "", fontSize = 12.sp, color = Gray500)
                        }
                    }
                    Text(brl(locacao.valor.toDoubleOrNull() ?: 0.0), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Gray900)
                }

                // Grid: telefone, evento, data evento, pagamento
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoField("Telefone", locacao.cliente?.telefone ?: "—", Modifier.weight(1f))
                    InfoField("Evento", locacao.evento ?: "—", Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val dataText = fmtData(locacao.dataEvento)
                    val dataColor = when (alerta) {
                        AlertaData.PASSADO -> Red500
                        AlertaData.URGENTE -> Yellow700
                        else               -> Gray700
                    }
                    InfoField("Data do Evento", dataText, Modifier.weight(1f), valueColor = dataColor)
                    InfoField("Pagamento", locacao.formaPagamento, Modifier.weight(1f))
                }

                // Motivo não fechar
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Motivo de não fechar", fontSize = 12.sp, color = Gray500, fontWeight = FontWeight.Medium)
                    OutlinedTextField(
                        value = motivoNaoFechar,
                        onValueChange = { motivoNaoFechar = it },
                        placeholder = { Text("Ex: achou caro, foi para outro lugar...", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        minLines = 2,
                        maxLines = 3,
                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Amber500,
                            unfocusedBorderColor = Gray200,
                            unfocusedContainerColor = Gray50,
                            focusedContainerColor = Color.White,
                        ),
                    )
                }

                // Observações
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Observações", fontSize = 12.sp, color = Gray500, fontWeight = FontWeight.Medium)
                    OutlinedTextField(
                        value = observacoes,
                        onValueChange = { observacoes = it },
                        placeholder = { Text("Anotações gerais sobre o cliente/orçamento...", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        minLines = 2,
                        maxLines = 3,
                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Blue600,
                            unfocusedBorderColor = Gray200,
                            unfocusedContainerColor = Gray50,
                            focusedContainerColor = Color.White,
                        ),
                    )
                }

                // Botões de ação
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    WaBotao(locacao.cliente?.telefone, modifier = Modifier.size(40.dp))
                    OutlinedButton(
                        onClick = { onEditar(locacao) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Blue200),
                        contentPadding = PaddingValues(vertical = 8.dp),
                    ) {
                        Text("✏ Editar", fontSize = 12.sp, color = Blue600)
                    }

                    if (locacao.cancelamentoPendente) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Orange100)
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("Aguardando aprovação", fontSize = 11.sp, color = Orange700, fontWeight = FontWeight.Medium)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { onMarcarPerdido(locacao) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Red300),
                            contentPadding = PaddingValues(vertical = 8.dp),
                        ) {
                            Text("Orç. Perdido", fontSize = 12.sp, color = Red500)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoField(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = Gray700) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label.uppercase(), fontSize = 10.sp, color = Gray500, letterSpacing = 0.3.sp)
        Text(value, fontSize = 13.sp, color = valueColor)
    }
}

// ── Modal: Editar medidas ─────────────────────────────────────────────────────

@Composable
private fun EditarMedidasModal(
    locacao: Locacao,
    onDismiss: () -> Unit,
    onSalvar: (Map<String, Any?>) -> Unit,
) {
    val isFem = locacao.sexo == "F"
    var tamanhoPaleto  by remember { mutableStateOf(locacao.tamanhoPaleto ?: "") }
    var tamanhoManga   by remember { mutableStateOf(locacao.tamanhoManga ?: "") }
    var camisa         by remember { mutableStateOf(locacao.camisa ?: "") }
    var calca          by remember { mutableStateOf(locacao.calca ?: "") }
    var tamanhoCalca   by remember { mutableStateOf(locacao.tamanhoCalca ?: "") }
    var cinto          by remember { mutableStateOf(locacao.cinto ?: "") }
    var sapato         by remember { mutableStateOf(locacao.sapato ?: "") }
    var abotoadura     by remember { mutableStateOf(locacao.abotoadura ?: "") }
    var gravata        by remember { mutableStateOf(locacao.gravata ?: "") }
    var tamanhoColete  by remember { mutableStateOf(locacao.tamanhoColete ?: "") }
    var torax          by remember { mutableStateOf(locacao.torax ?: "") }
    var abdomen        by remember { mutableStateOf(locacao.abdomen ?: "") }
    var quadril        by remember { mutableStateOf(locacao.quadril ?: "") }
    var panturrilha    by remember { mutableStateOf(locacao.panturrilha ?: "") }
    var busto          by remember { mutableStateOf(locacao.busto ?: "") }
    var cintura        by remember { mutableStateOf(locacao.cintura ?: "") }
    var ajustes        by remember { mutableStateOf(locacao.ajustes ?: "") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.heightIn(max = 560.dp)) {
                // Header
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Editar medidas e ajustes", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Gray900)
                        Text(
                            "${locacao.cliente?.nome ?: "—"} — ${locacao.traje}",
                            fontSize = 12.sp, color = Gray500,
                        )
                    }
                    TextButton(onClick = onDismiss) { Text("✕", fontSize = 18.sp, color = Gray500) }
                }
                HorizontalDivider(color = Gray200)

                // Campos com scroll
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // Tamanhos masculinos
                    if (!isFem) {
                        Text("TAMANHOS", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Gray500, letterSpacing = 0.5.sp)
                        val tamanhoFields = listOf(
                            "Paletó" to tamanhoPaleto,   "Manga" to tamanhoManga,
                            "Camisa" to camisa,          "Calça" to calca,
                            "Tam. Calça" to tamanhoCalca, "Cinto" to cinto,
                            "Sapato" to sapato,          "Abotoadura" to abotoadura,
                            "Gravata" to gravata,        "Colete" to tamanhoColete,
                        )
                        val setters: List<(String) -> Unit> = listOf(
                            { tamanhoPaleto = it }, { tamanhoManga = it },
                            { camisa = it },        { calca = it },
                            { tamanhoCalca = it },  { cinto = it },
                            { sapato = it },        { abotoadura = it },
                            { gravata = it },       { tamanhoColete = it },
                        )
                        MedidasGrid(tamanhoFields, setters)
                    }

                    Text("MEDIDAS (cm)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Gray500, letterSpacing = 0.5.sp)
                    val medidaFields = listOf(
                        "Tórax" to torax,       "Abdômen" to abdomen,
                        "Quadril" to quadril,   "Panturrilha" to panturrilha,
                        "Busto" to busto,       "Cintura" to cintura,
                    )
                    val medidaSetters: List<(String) -> Unit> = listOf(
                        { torax = it }, { abdomen = it },
                        { quadril = it }, { panturrilha = it },
                        { busto = it }, { cintura = it },
                    )
                    MedidasGrid(medidaFields, medidaSetters)

                    Text("AJUSTES", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Gray500, letterSpacing = 0.5.sp)
                    OutlinedTextField(
                        value = ajustes,
                        onValueChange = { ajustes = it },
                        placeholder = { Text("Descreva ajustes necessários...", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        minLines = 3,
                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Blue600,
                            unfocusedBorderColor = Gray200,
                        ),
                    )
                }

                HorizontalDivider(color = Gray200)
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Cancelar", color = Gray500) }
                    Button(
                        onClick = {
                            onSalvar(mapOf(
                                "tamanhoPaleto" to tamanhoPaleto.ifBlank { null },
                                "tamanhoManga"  to tamanhoManga.ifBlank { null },
                                "camisa"        to camisa.ifBlank { null },
                                "calca"         to calca.ifBlank { null },
                                "tamanhoCalca"  to tamanhoCalca.ifBlank { null },
                                "cinto"         to cinto.ifBlank { null },
                                "sapato"        to sapato.ifBlank { null },
                                "abotoadura"    to abotoadura.ifBlank { null },
                                "gravata"       to gravata.ifBlank { null },
                                "tamanhoColete" to tamanhoColete.ifBlank { null },
                                "torax"         to torax.ifBlank { null },
                                "abdomen"       to abdomen.ifBlank { null },
                                "quadril"       to quadril.ifBlank { null },
                                "panturrilha"   to panturrilha.ifBlank { null },
                                "busto"         to busto.ifBlank { null },
                                "cintura"       to cintura.ifBlank { null },
                                "ajustes"       to ajustes.ifBlank { null },
                            ))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Blue600),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text("Salvar")
                    }
                }
            }
        }
    }
}

@Composable
private fun MedidasGrid(fields: List<Pair<String, String>>, setters: List<(String) -> Unit>) {
    val rows = fields.chunked(3)
    rows.forEachIndexed { rowIdx, row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEachIndexed { colIdx, (label, value) ->
                val globalIdx = rowIdx * 3 + colIdx
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(label, fontSize = 11.sp, color = Gray500)
                    OutlinedTextField(
                        value = value,
                        onValueChange = setters[globalIdx],
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(6.dp),
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Blue600,
                            unfocusedBorderColor = Gray200,
                        ),
                    )
                }
            }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

// ── Modal: Marcar como perdido ────────────────────────────────────────────────

@Composable
private fun MarcarPerdidoModal(
    locacao: Locacao,
    onDismiss: () -> Unit,
    onConfirmar: (String) -> Unit,
) {
    var motivo by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("Marcar como Orçamento Perdido", fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = Gray900)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Cliente: ${locacao.cliente?.nome ?: "—"}", fontSize = 13.sp, color = Gray700)
                    Text(
                        "Evento: ${locacao.evento ?: "—"} — ${fmtData(locacao.dataEvento)}",
                        fontSize = 13.sp, color = Gray700,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Motivo *", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Gray700)
                    OutlinedTextField(
                        value = motivo,
                        onValueChange = { motivo = it },
                        placeholder = { Text("Informe o motivo pelo qual o orçamento foi perdido...", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        minLines = 3,
                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Red500,
                            unfocusedBorderColor = Gray200,
                        ),
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar", color = Gray500)
                    }
                    Button(
                        onClick = { if (motivo.isNotBlank()) onConfirmar(motivo) },
                        enabled = motivo.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = Red500),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text("Enviar para aprovação")
                    }
                }
            }
        }
    }
}

// ── Seletor de mês ────────────────────────────────────────────────────────────

@Composable
private fun MesPickerDialog(mesAtual: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val hoje = LocalDate.now()
    val anos = (hoje.year - 2)..(hoje.year + 1)
    val meses = (1..12).map { "%02d".format(it) to mesNome(it) }

    val partes = mesAtual.split("-")
    var anoSel by remember { mutableStateOf(partes.getOrNull(0)?.toIntOrNull() ?: hoje.year) }
    var mesSel by remember { mutableStateOf(partes.getOrNull(1)?.toIntOrNull() ?: hoje.monthValue) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Selecionar mês", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Gray900)

                // Seletor de ano
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { if (anoSel > anos.first) anoSel-- }) { Text("‹", fontSize = 20.sp) }
                    Text("$anoSel", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Gray900)
                    TextButton(onClick = { if (anoSel < anos.last) anoSel++ }) { Text("›", fontSize = 20.sp) }
                }

                // Grid de meses
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    meses.chunked(4).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { (num, nome) ->
                                val m = num.toInt()
                                val selecionado = m == mesSel
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (selecionado) Blue600 else Gray100)
                                        .clickable { mesSel = m }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        nome,
                                        fontSize = 12.sp,
                                        color = if (selecionado) Color.White else Gray700,
                                        fontWeight = if (selecionado) FontWeight.SemiBold else FontWeight.Normal,
                                    )
                                }
                            }
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
                    TextButton(onClick = onDismiss) { Text("Cancelar", color = Gray500) }
                    Button(
                        onClick = { onConfirm("%04d-%02d".format(anoSel, mesSel)) },
                        colors = ButtonDefaults.buttonColors(containerColor = Blue600),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text("Confirmar")
                    }
                }
            }
        }
    }
}

private fun mesNome(m: Int) = listOf("Jan","Fev","Mar","Abr","Mai","Jun","Jul","Ago","Set","Out","Nov","Dez")[m - 1]
