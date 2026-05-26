package com.mrjack.dressflow.ui.screens.vendas

import android.app.Application
import com.mrjack.dressflow.ui.components.DatePickerField
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mrjack.dressflow.data.api.NetworkModule
import com.mrjack.dressflow.data.model.*
import com.mrjack.dressflow.ui.theme.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

// ─── ViewModel de Relatórios ──────────────────────────────────────────────────

class VendasRelatorioViewModel(app: Application) : AndroidViewModel(app) {
    private val api = NetworkModule.provideApiService(app)

    private fun hojeStr(): String {
        val c = Calendar.getInstance()
        return "%04d-%02d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }
    private fun mesAtualStr(): String {
        val c = Calendar.getInstance()
        return "%04d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1)
    }

    val tabIdx       = MutableStateFlow(0)
    val dataDia      = MutableStateFlow(hojeStr())
    val mesSel       = MutableStateFlow(mesAtualStr())
    val ordemAlfa    = MutableStateFlow(false)
    val locacoesDia  = MutableStateFlow<List<Locacao>>(emptyList())
    val locacoesMes  = MutableStateFlow<List<Locacao>>(emptyList())
    val statsMes     = MutableStateFlow<VendasMesStats?>(null)
    val financeiro   = MutableStateFlow<FinanceiroResumo?>(null)
    val isLoadingDia = MutableStateFlow(false)
    val isLoadingMes = MutableStateFlow(false)

    init { carregarDia(); carregarMes() }

    fun carregarDia() {
        viewModelScope.launch {
            isLoadingDia.value = true
            locacoesDia.value = try { api.listarVendasDiaPorData(dataDia.value).body() ?: emptyList() } catch (_: Exception) { emptyList() }
            isLoadingDia.value = false
        }
    }

    fun carregarMes() {
        viewModelScope.launch {
            isLoadingMes.value = true
            val resp = try { api.listarVendasMes(mesSel.value).body() } catch (_: Exception) { null }
            locacoesMes.value = resp?.locacoes ?: emptyList()
            statsMes.value = resp?.stats
            financeiro.value = try { api.financeiroResumo(mesSel.value).body() } catch (_: Exception) { null }
            isLoadingMes.value = false
        }
    }

    fun setDataDia(d: String) { dataDia.value = d; carregarDia() }
    fun setMes(m: String) { mesSel.value = m; carregarMes() }
}

// ─── ViewModel CRUD (usado por ClientesScreen para Nova Locação) ──────────────

class VendasViewModel(app: Application) : AndroidViewModel(app) {
    private val api = NetworkModule.provideApiService(app)

    val isSaving        = MutableStateFlow(false)
    val erro            = MutableStateFlow<String?>(null)
    val sucesso         = MutableStateFlow<String?>(null)
    val clientesBusca   = MutableStateFlow<List<Cliente>>(emptyList())
    val buscandoCliente = MutableStateFlow(false)
    private var clienteJob: Job? = null

    fun buscarVestido(codigo: String, onResult: (Traje?) -> Unit) {
        viewModelScope.launch {
            try {
                val resp = api.buscarTrajePorCodigo(codigo)
                onResult(if (resp.isSuccessful && resp.body()?.tipo == "VESTIDO") resp.body() else null)
            } catch (_: Exception) { onResult(null) }
        }
    }

    fun buscarClientes(q: String) {
        clienteJob?.cancel()
        clienteJob = viewModelScope.launch {
            if (q.length < 2) { clientesBusca.value = emptyList(); return@launch }
            delay(300)
            buscandoCliente.value = true
            clientesBusca.value = try {
                api.listarClientes(busca = q, limit = 20).body()?.data ?: emptyList()
            } catch (_: Exception) { emptyList() }
            buscandoCliente.value = false
        }
    }

    fun salvarLocacao(form: LocacaoForm, onSuccess: () -> Unit) {
        viewModelScope.launch {
            isSaving.value = true
            erro.value = null
            try {
                val resp = api.criarLocacao(buildBody(form))
                if (resp.isSuccessful) {
                    sucesso.value = "Locação criada!"
                    clientesBusca.value = emptyList()
                    onSuccess()
                } else {
                    val msg = resp.errorBody()?.string()?.let {
                        try { com.google.gson.JsonParser.parseString(it).asJsonObject.get("error")?.asString } catch (_: Exception) { null }
                    } ?: "Erro ${resp.code()}"
                    erro.value = msg
                }
            } catch (e: Exception) { erro.value = e.message }
            finally { isSaving.value = false }
        }
    }

    private fun buildBody(form: LocacaoForm): Map<String, Any?> {
        val m = mutableMapOf<String, Any?>(
            "clienteId"      to form.clienteId,
            "tipo"           to form.tipo,
            "traje"          to form.traje,
            "evento"         to form.evento.ifBlank { null },
            "dataEvento"     to form.dataEvento,
            "formaPagamento" to form.formaPagamento,
            "valor"          to form.valor.replace(",", ".").toDoubleOrNull(),
            "parcelas"       to form.parcelas.toIntOrNull(),
            "sexo"           to form.sexo,
            "menorDeIdade"   to form.menorDeIdade,
        )
        mapOf(
            "tamanhoPaleto"   to form.tamanhoPaleto,
            "tamanhoManga"    to form.tamanhoManga,
            "camisa"          to form.camisa,
            "calca"           to form.calca,
            "tamanhoCalca"    to form.tamanhoCalca,
            "cinto"           to form.cinto,
            "sapato"          to form.sapato,
            "tamanhoColete"   to form.tamanhoColete,
            "torax"           to form.torax,
            "abdomen"         to form.abdomen,
            "quadril"         to form.quadril,
            "panturrilha"     to form.panturrilha,
            "busto"           to form.busto,
            "cintura"         to form.cintura,
            "ajustes"         to form.ajustes,
            "observacoes"     to form.observacoes,
            "motivoNaoFechar" to form.motivoNaoFechar.ifBlank { null },
            "nomeResponsavel" to if (form.menorDeIdade) form.nomeResponsavel.ifBlank { null } else null,
            "valorEntrada"    to form.valorEntrada.replace(",", ".").let { if (it.isBlank()) null else it.toDoubleOrNull() },
        ).forEach { (k, v) -> if (v != null && v.toString().isNotBlank()) m[k] = v }
        return m
    }

    fun completarCadastro(clienteId: Int, cpf: String, cidade: String, bairro: String, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                val body = mutableMapOf<String, Any?>()
                if (cpf.isNotBlank()) body["cpf"] = cpf
                if (cidade.isNotBlank()) body["cidade"] = cidade
                if (bairro.isNotBlank()) body["bairro"] = bairro
                if (body.isNotEmpty()) api.atualizarCliente(clienteId, body)
            } catch (_: Exception) { }
            onDone()
        }
    }
}

// ─── Form data class ──────────────────────────────────────────────────────────

data class LocacaoForm(
    var clienteId: Int = 0,
    var clienteNome: String = "",
    var tipo: String = "LOCACAO",
    var traje: String = "",
    var evento: String = "",
    var dataEvento: String = "",
    var formaPagamento: String = "Dinheiro",
    var valor: String = "",
    var valorEntrada: String = "",
    var parcelas: String = "1",
    var sexo: String = "M",
    var tamanhoPaleto: String = "",
    var tamanhoManga: String = "",
    var camisa: String = "",
    var calca: String = "",
    var tamanhoCalca: String = "",
    var cinto: String = "",
    var sapato: String = "",
    var tamanhoColete: String = "",
    var torax: String = "",
    var abdomen: String = "",
    var quadril: String = "",
    var panturrilha: String = "",
    var busto: String = "",
    var cintura: String = "",
    var ajustes: String = "",
    var observacoes: String = "",
    var motivoNaoFechar: String = "",
    var menorDeIdade: Boolean = false,
    var nomeResponsavel: String = "",
)

fun Locacao.toForm() = LocacaoForm(
    clienteId       = this.cliente?.id ?: 0,
    clienteNome     = this.cliente?.nome ?: "",
    tipo            = this.tipo,
    traje           = this.traje,
    evento          = this.evento ?: "",
    dataEvento      = this.dataEvento.take(10),
    formaPagamento  = this.formaPagamento,
    valor           = this.valor,
    valorEntrada    = this.valorEntrada ?: "",
    parcelas        = this.parcelas?.toString() ?: "1",
    sexo            = this.sexo ?: "M",
    tamanhoPaleto   = this.tamanhoPaleto ?: "",
    tamanhoManga    = this.tamanhoManga ?: "",
    camisa          = this.camisa ?: "",
    calca           = this.calca ?: "",
    tamanhoCalca    = this.tamanhoCalca ?: "",
    cinto           = this.cinto ?: "",
    sapato          = this.sapato ?: "",
    tamanhoColete   = this.tamanhoColete ?: "",
    torax           = this.torax ?: "",
    abdomen         = this.abdomen ?: "",
    quadril         = this.quadril ?: "",
    panturrilha     = this.panturrilha ?: "",
    busto           = this.busto ?: "",
    cintura         = this.cintura ?: "",
    ajustes         = this.ajustes ?: "",
    observacoes     = this.observacoes ?: "",
    motivoNaoFechar = this.motivoNaoFechar ?: "",
    menorDeIdade    = this.menorDeIdade,
    nomeResponsavel = this.nomeResponsavel ?: "",
)

// ─── Tela de Vendas (Relatórios) ──────────────────────────────────────────────

@Composable
fun VendasScreen(vm: VendasRelatorioViewModel = viewModel()) {
    val tabIdx by vm.tabIdx.collectAsState()

    Column(Modifier.fillMaxSize()) {
        Surface(shadowElevation = 1.dp, color = Color.White) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Vendas", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Gray900, modifier = Modifier.weight(1f))
                    IconButton(onClick = { if (tabIdx == 0) vm.carregarDia() else vm.carregarMes() }) {
                        Icon(Icons.Default.Refresh, null, tint = Gray500, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Vendas do Dia", "Vendas do Mês").forEachIndexed { i, label ->
                        if (tabIdx == i) {
                            Button(
                                onClick = { vm.tabIdx.value = i },
                                colors = ButtonDefaults.buttonColors(containerColor = Blue600),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            ) { Text(label, fontSize = 13.sp) }
                        } else {
                            OutlinedButton(
                                onClick = { vm.tabIdx.value = i },
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Gray200),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            ) { Text(label, fontSize = 13.sp, color = Gray500) }
                        }
                    }
                }
            }
        }

        when (tabIdx) {
            0 -> VendasDiaTab(vm)
            1 -> VendasMesTab(vm)
        }
    }
}

// ─── Aba Dia ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VendasDiaTab(vm: VendasRelatorioViewModel) {
    val locacoes  by vm.locacoesDia.collectAsState()
    val dataDia   by vm.dataDia.collectAsState()
    val isLoading by vm.isLoadingDia.collectAsState()
    val ordemAlfa by vm.ordemAlfa.collectAsState()

    var showPickerDia by remember { mutableStateOf(false) }
    val pickerStateDia = rememberDatePickerState(
        initialSelectedDateMillis = remember(dataDia) {
            val p = dataDia.split("-")
            if (p.size == 3) try {
                val cal = Calendar.getInstance()
                cal.set(p[0].toInt(), p[1].toInt() - 1, p[2].toInt(), 12, 0, 0)
                cal.timeInMillis
            } catch (_: Exception) { null } else null
        }
    )

    if (showPickerDia) {
        DatePickerDialog(
            onDismissRequest = { showPickerDia = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerStateDia.selectedDateMillis?.let { millis ->
                        val cal = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")); cal.timeInMillis = millis
                        vm.setDataDia("%04d-%02d-%02d".format(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)))
                    }
                    showPickerDia = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showPickerDia = false }) { Text("Cancelar") } },
        ) { DatePicker(state = pickerStateDia) }
    }

    val totalDia = locacoes.filter { it.tipo != "ORCAMENTO" }.sumOf { it.valor.toDoubleOrNull() ?: 0.0 }
    val grupos = locacoes.groupBy { it.vendedor?.nome ?: "Sem vendedor" }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Data:", fontSize = 13.sp, color = Gray700, fontWeight = FontWeight.Medium)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Gray200),
                    color = Color.White,
                    modifier = Modifier.clickable { showPickerDia = true },
                ) {
                    Text(
                        fmtDataCompleta(dataDia),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        fontSize = 13.sp, color = Gray900,
                    )
                }
                Text("${locacoes.size} registro${if (locacoes.size != 1) "s" else ""}", fontSize = 12.sp, color = Gray500)
                Spacer(Modifier.weight(1f))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (ordemAlfa) Blue600 else Gray200),
                    color = if (ordemAlfa) Blue600 else Color.White,
                    modifier = Modifier.clickable { vm.ordemAlfa.value = !ordemAlfa },
                ) {
                    Text(
                        "A→Z",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        fontSize = 12.sp,
                        color = if (ordemAlfa) Color.White else Gray500,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        item {
            Surface(
                color = Blue50,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Blue200),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Total do dia", color = Blue700, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Text(brl(totalDia), color = Color(0xFF1E3A8A), fontWeight = FontWeight.Black, fontSize = 22.sp)
                }
            }
        }

        if (isLoading) {
            item {
                Box(Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Blue600)
                }
            }
        } else if (locacoes.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                    Text("Nenhum registro neste dia.", color = Gray500)
                }
            }
        } else {
            grupos.forEach { (nome, locs) ->
                item { TabelaVendedorDia(nome, locs, ordemAlfa) }
            }
        }
    }
}

@Composable
fun TabelaVendedorDia(nome: String, locacoes: List<Locacao>, ordemAlfa: Boolean) {
    val fechadas = locacoes.filter { it.tipo != "ORCAMENTO" && it.status != "CANCELADO" }
    val sub = fechadas.sumOf { it.valor.toDoubleOrNull() ?: 0.0 }
    val assertivos = fechadas.size
    val assertividade = if (locacoes.isEmpty()) "—" else "${assertivos * 100 / locacoes.size}%"
    val assertBom = locacoes.isNotEmpty() && assertivos.toFloat() / locacoes.size >= 0.7f
    val linhas = if (ordemAlfa) locacoes.sortedBy { it.cliente?.nome ?: "" } else locacoes

    Column(Modifier.fillMaxWidth()) {
        Surface(color = Color(0xFF1F2937), shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(nome, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    VendasStatCol("ATENDIMENTOS", "${locacoes.size}", Color.White)
                    VendasStatCol("ASSERTIVIDADE", assertividade, if (assertBom) Color(0xFF86EFAC) else Color(0xFFFBBF24))
                    VendasStatCol("TOTAL", brl(sub), Color(0xFF86EFAC))
                }
            }
        }
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp),
            border = BorderStroke(1.dp, Gray200),
        ) {
            Column {
                Row(Modifier.fillMaxWidth().background(Gray50).padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Text("Cliente",   Modifier.weight(2f),   fontSize = 10.sp, color = Gray500, fontWeight = FontWeight.Medium)
                    Text("Telefone",  Modifier.weight(1.5f), fontSize = 10.sp, color = Gray500, fontWeight = FontWeight.Medium)
                    Text("Traje",     Modifier.weight(2f),   fontSize = 10.sp, color = Gray500, fontWeight = FontWeight.Medium)
                    Text("Tipo",      Modifier.weight(1f),   fontSize = 10.sp, color = Gray500, fontWeight = FontWeight.Medium)
                    Text("Pagamento", Modifier.weight(1.5f), fontSize = 10.sp, color = Gray500, fontWeight = FontWeight.Medium)
                    Text("Valor",     Modifier.weight(1f),   fontSize = 10.sp, color = Gray500, fontWeight = FontWeight.Medium, textAlign = TextAlign.End)
                }
                linhas.forEachIndexed { idx, l ->
                    if (idx > 0) HorizontalDivider(color = Gray100)
                    VendasDiaRow(l)
                }
            }
        }
    }
}

@Composable
fun VendasStatCol(label: String, valor: String, cor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 9.sp, color = Color(0xFF9CA3AF), fontWeight = FontWeight.Medium)
        Text(valor, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = cor)
    }
}

@Composable
fun VendasDiaRow(l: Locacao) {
    val cancelado = l.status == "CANCELADO"
    val (tipoBg, tipoCor, tipoLabel) = when {
        cancelado        -> Triple(Red100, Red500, "Perdido")
        l.tipo == "LOCACAO"   -> Triple(Blue100, Blue600, "Locação")
        l.tipo == "ORCAMENTO" -> Triple(Yellow50, Color(0xFFB45309), "Orçamento")
        else             -> Triple(Green100, Green600, "Venda")
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(l.cliente?.nome ?: "—", Modifier.weight(2f), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Gray900)
        Text(l.cliente?.telefone ?: "—", Modifier.weight(1.5f), fontSize = 11.sp, color = Gray500)
        Text(l.traje, Modifier.weight(2f), fontSize = 11.sp, color = Gray700, maxLines = 1)
        Box(Modifier.weight(1f)) {
            Surface(color = tipoBg, shape = RoundedCornerShape(12.dp)) {
                Text(
                    tipoLabel,
                    fontSize = 10.sp, color = tipoCor, fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        Text(
            l.formaPagamento.replace("_", " ") + if ((l.parcelas ?: 0) > 1) " ${l.parcelas}x" else "",
            Modifier.weight(1.5f), fontSize = 11.sp, color = Gray700,
        )
        Text(
            brl(l.valor.toDoubleOrNull() ?: 0.0),
            Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End,
        )
    }
}

// ─── Aba Mês ──────────────────────────────────────────────────────────────────

@Composable
fun VendasMesTab(vm: VendasRelatorioViewModel) {
    val locacoes   by vm.locacoesMes.collectAsState()
    val stats      by vm.statsMes.collectAsState()
    val financeiro by vm.financeiro.collectAsState()
    val mesSel     by vm.mesSel.collectAsState()
    val isLoading  by vm.isLoadingMes.collectAsState()
    val grupos = locacoes.groupBy { it.vendedor?.nome ?: "Sem vendedor" }
    var showMesPicker by remember { mutableStateOf(false) }

    if (showMesPicker) {
        val partes = mesSel.split("-")
        val anoInicial = partes.getOrNull(0)?.toIntOrNull() ?: Calendar.getInstance().get(Calendar.YEAR)
        val mesInicial = (partes.getOrNull(1)?.toIntOrNull() ?: (Calendar.getInstance().get(Calendar.MONTH) + 1)) - 1
        MesPickerDialogVendas(
            anoInicial = anoInicial,
            mesInicial = mesInicial,
            onSelect = { y, m -> vm.setMes("%04d-%02d".format(y, m + 1)); showMesPicker = false },
            onDismiss = { showMesPicker = false },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Mês:", fontSize = 13.sp, color = Gray700, fontWeight = FontWeight.Medium)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Gray200),
                    color = Color.White,
                    modifier = Modifier.clickable { showMesPicker = true },
                ) {
                    Text(
                        fmtMes(mesSel),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        fontSize = 13.sp, color = Gray900,
                    )
                }
            }
        }

        if (isLoading) {
            item {
                Box(Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Blue600)
                }
            }
        } else {
            stats?.let { s ->
                item { ResumoMesCard(s, locacoes, financeiro) }
            }
            if (locacoes.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                        Text("Nenhum registro neste mês.", color = Gray500)
                    }
                }
            } else {
                grupos.forEach { (nome, locs) ->
                    item {
                        Spacer(Modifier.height(4.dp))
                        CardVendedorMes(nome, locs)
                    }
                }
            }
        }
    }
}

@Composable
fun ResumoMesCard(stats: VendasMesStats, locacoes: List<Locacao>, financeiro: FinanceiroResumo?) {
    val totalMes = locacoes.filter { it.tipo != "ORCAMENTO" && it.status != "CANCELADO" }
        .sumOf { it.valor.toDoubleOrNull() ?: 0.0 }
    val meta1 = financeiro?.meta1 ?: 0.0
    val meta2 = financeiro?.meta2 ?: 0.0
    val projecao = financeiro?.projecao

    val locsMAtt  = locacoes.filter { it.sexo == "M" }
    val locsMFech = locsMAtt.filter { it.tipo != "ORCAMENTO" && it.status != "CANCELADO" }
    val valorM    = locsMFech.sumOf { it.valor.toDoubleOrNull() ?: 0.0 }
    val locsFAtt  = locacoes.filter { it.sexo == "F" }
    val locsFfech = locsFAtt.filter { it.tipo != "ORCAMENTO" && it.status != "CANCELADO" }
    val valorF    = locsFfech.sumOf { it.valor.toDoubleOrNull() ?: 0.0 }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Linha 1: Total + Meta1 + Meta2
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(color = Color(0xFF111827), shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) {
                Column(
                    Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("TOTAL DO MÊS", fontSize = 10.sp, color = Color(0xFF9CA3AF), fontWeight = FontWeight.Medium)
                    Text(brl(totalMes), fontSize = 22.sp, fontWeight = FontWeight.Black, color = Color(0xFF4ADE80))
                    Text(
                        "${stats.totalAtendimentos ?: 0} atendimento${if ((stats.totalAtendimentos ?: 0) != 1) "s" else ""}",
                        fontSize = 11.sp, color = Color(0xFF6B7280),
                    )
                }
            }
            // Meta 1
            Surface(
                color = Color.White, shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFBBF7D0)), modifier = Modifier.weight(1f),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("META 1 — +30%", fontSize = 10.sp, color = Gray500, fontWeight = FontWeight.Medium)
                    Text(if (meta1 > 0) brl(meta1) else "—", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF059669))
                    if (meta1 > 0) {
                        Text(
                            if (totalMes >= meta1) "Atingida! +${brl(totalMes - meta1)}" else "Faltam ${brl(meta1 - totalMes)}",
                            fontSize = 12.sp, fontWeight = FontWeight.Medium,
                            color = if (totalMes >= meta1) Color(0xFF059669) else Red500,
                        )
                        val pct1 = (totalMes / meta1).toFloat().coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = { pct1 },
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                            color = if (totalMes >= meta1) Color(0xFF10B981) else Blue600,
                            trackColor = Gray100,
                        )
                        Text("${(pct1 * 100).toInt()}% da meta", fontSize = 10.sp, color = Gray500)
                    }
                }
            }
            // Meta 2
            Surface(
                color = Color.White, shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFFDE68A)), modifier = Modifier.weight(1f),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("META 2 — +50%", fontSize = 10.sp, color = Gray500, fontWeight = FontWeight.Medium)
                    Text(if (meta2 > 0) brl(meta2) else "—", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD97706))
                    if (meta2 > 0) {
                        Text(
                            if (totalMes >= meta2) "Atingida! +${brl(totalMes - meta2)}" else "Faltam ${brl(meta2 - totalMes)}",
                            fontSize = 12.sp, fontWeight = FontWeight.Medium,
                            color = if (totalMes >= meta2) Color(0xFF059669) else Color(0xFFD97706),
                        )
                        val pct2 = (totalMes / meta2).toFloat().coerceIn(0f, 1f)
                        LinearProgressIndicator(
                            progress = { pct2 },
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                            color = if (totalMes >= meta2) Color(0xFF10B981) else Color(0xFFF59E0B),
                            trackColor = Gray100,
                        )
                        Text("${(pct2 * 100).toInt()}% da meta", fontSize = 10.sp, color = Gray500)
                    }
                }
            }
        }

        // Linha 2: Projeção + Masculino + Feminino
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(
                color = Color.White, shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFC7D2FE)), modifier = Modifier.weight(1f),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("PROJEÇÃO DO MÊS", fontSize = 10.sp, color = Gray500, fontWeight = FontWeight.Medium)
                    Text(
                        if (projecao?.valorCalculado != null) brl(projecao.valorCalculado) else "—",
                        fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4338CA),
                    )
                    if (projecao != null) {
                        Text(
                            "${projecao.diasUteisDecorridos ?: 0} dias úteis passados / ${projecao.totalDiasUteisMes ?: 0} no mês",
                            fontSize = 10.sp, color = Gray500,
                        )
                    }
                }
            }
            Surface(
                color = Color(0xFFEEF2FF), shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFC7D2FE)), modifier = Modifier.weight(1f),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("MASCULINO", fontSize = 10.sp, color = Color(0xFF4F46E5), fontWeight = FontWeight.SemiBold)
                    Text("${locsMAtt.size} atend.", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E1B4B))
                    Text(brl(valorM), fontSize = 13.sp, color = Color(0xFF4338CA), fontWeight = FontWeight.Medium)
                }
            }
            Surface(
                color = Color(0xFFFFF1F2), shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFFBCFE8)), modifier = Modifier.weight(1f),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("FEMININO", fontSize = 10.sp, color = Color(0xFFBE185D), fontWeight = FontWeight.SemiBold)
                    Text("${locsFAtt.size} atend.", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF831843))
                    Text(brl(valorF), fontSize = 13.sp, color = Color(0xFFBE185D), fontWeight = FontWeight.Medium)
                }
            }
        }

        // Linha 3: Contadores
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                Triple("Atendimentos", stats.totalAtendimentos ?: 0, Gray100 to Gray200),
                Triple("Locações",     stats.totalLocacoes ?: 0,     Blue50 to Blue200),
                Triple("Orçamentos",   stats.totalOrcamentos ?: 0,   Yellow50 to Yellow200),
                Triple("Vendas",       stats.totalVendas ?: 0,       Green100 to Color(0xFFBBF7D0)),
            ).forEach { (label, valor, cores) ->
                Surface(
                    color = cores.first, shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, cores.second), modifier = Modifier.weight(1f),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(label, fontSize = 10.sp, color = Gray500)
                        Text("$valor", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1F2937))
                    }
                }
            }
        }
    }
}

@Composable
fun CardVendedorMes(nome: String, locacoes: List<Locacao>) {
    val fechadas = locacoes.filter { it.tipo != "ORCAMENTO" && it.status != "CANCELADO" }
    val sub = fechadas.sumOf { it.valor.toDoubleOrNull() ?: 0.0 }
    val assertivos = fechadas.size
    val orcamentos = locacoes.filter { it.tipo == "ORCAMENTO" && it.status != "CANCELADO" }.size
    val perdidos   = locacoes.filter { it.tipo == "ORCAMENTO" && it.status == "CANCELADO" }.size
    val assertividade = if (locacoes.isEmpty()) "—" else "${assertivos * 100 / locacoes.size}%"
    val assertBom = locacoes.isNotEmpty() && assertivos.toFloat() / locacoes.size >= 0.7f

    val locsM     = locacoes.filter { it.sexo == "M" }
    val locsF     = locacoes.filter { it.sexo == "F" }
    val fechadasM = locsM.filter { it.tipo != "ORCAMENTO" && it.status != "CANCELADO" }
    val fechadasF = locsF.filter { it.tipo != "ORCAMENTO" && it.status != "CANCELADO" }
    val valorM    = fechadasM.sumOf { it.valor.toDoubleOrNull() ?: 0.0 }
    val valorF    = fechadasF.sumOf { it.valor.toDoubleOrNull() ?: 0.0 }
    val temF      = locsF.isNotEmpty()
    val assertMBom = locsM.isNotEmpty() && fechadasM.size.toFloat() / locsM.size >= 0.7f
    val assertFBom = locsF.isNotEmpty() && fechadasF.size.toFloat() / locsF.size >= 0.7f

    Surface(color = Color(0xFF1F2937), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(nome, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                Text(brl(sub), color = Color(0xFF86EFAC), fontWeight = FontWeight.Black, fontSize = 20.sp)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                CardVendedorStat("Atendimentos", "${locacoes.size}", Color.White)
                CardVendedorStat(
                    "Orçamentos",
                    "${orcamentos}${if (perdidos > 0) " ($perdidos perd.)" else ""}",
                    Color(0xFFFBBF24),
                )
                CardVendedorStat("Assertividade", assertividade, if (assertBom) Color(0xFF86EFAC) else Color(0xFFFBBF24))
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = Color(0xFF374151))
            Spacer(Modifier.height(12.dp))
            if (temF) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MFCardMes("Masculino", locsM.size, fechadasM.size, valorM, assertMBom, isIndigo = true,  Modifier.weight(1f))
                    MFCardMes("Feminino",  locsF.size, fechadasF.size, valorF, assertFBom, isIndigo = false, Modifier.weight(1f))
                }
            } else {
                MFCardMes("Masculino", locsM.size, fechadasM.size, valorM, assertMBom, isIndigo = true, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
fun CardVendedorStat(label: String, valor: String, cor: Color) {
    Column {
        Text(label, fontSize = 10.sp, color = Color(0xFF9CA3AF), fontWeight = FontWeight.Medium)
        Text(valor, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = cor)
    }
}

@Composable
fun MFCardMes(label: String, atend: Int, fechadas: Int, valor: Double, assertBom: Boolean, isIndigo: Boolean, modifier: Modifier) {
    val tituloColor = if (isIndigo) Color(0xFFA5B4FC) else Color(0xFFF9A8D4)
    val valorColor  = if (isIndigo) Color(0xFFA5B4FC) else Color(0xFFF9A8D4)
    val assertPct   = if (atend > 0) "${fechadas * 100 / atend}%" else "—"
    val assertColor = if (assertBom) Color(0xFF86EFAC) else Color(0xFFFBBF24)

    Surface(color = Color(0x4D374151), shape = RoundedCornerShape(10.dp), modifier = modifier) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(label.uppercase(), fontSize = 10.sp, color = tituloColor, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column {
                    Text("Atend.", fontSize = 10.sp, color = Color(0xFF9CA3AF))
                    Text("$atend", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
                Column {
                    Text("Assertiv.", fontSize = 10.sp, color = Color(0xFF9CA3AF))
                    Text(assertPct, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = assertColor)
                }
                Column {
                    Text("Valor loc.", fontSize = 10.sp, color = Color(0xFF9CA3AF))
                    Text(brl(valor), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = valorColor)
                }
            }
        }
    }
}

// ─── Seletor de mês ───────────────────────────────────────────────────────────

@Composable
fun MesPickerDialogVendas(anoInicial: Int, mesInicial: Int, onSelect: (Int, Int) -> Unit, onDismiss: () -> Unit) {
    var ano by remember { mutableStateOf(anoInicial) }
    val meses = listOf("Jan", "Fev", "Mar", "Abr", "Mai", "Jun", "Jul", "Ago", "Set", "Out", "Nov", "Dez")

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), color = Color.White) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Selecionar mês", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    IconButton(onClick = { ano-- }) { Icon(Icons.Default.ChevronLeft, null) }
                    Text("$ano", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    IconButton(onClick = { ano++ }) { Icon(Icons.Default.ChevronRight, null) }
                }
                meses.chunked(3).forEachIndexed { rowIdx, row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEachIndexed { colIdx, mes ->
                            val mesIdx = rowIdx * 3 + colIdx
                            val selected = mesIdx == mesInicial && ano == anoInicial
                            Surface(
                                color = if (selected) Blue600 else Blue50,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f).clickable { onSelect(ano, mesIdx) },
                            ) {
                                Text(
                                    mes,
                                    modifier = Modifier.padding(vertical = 10.dp).fillMaxWidth(),
                                    textAlign = TextAlign.Center, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                                    color = if (selected) Color.White else Blue600,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Completar Cadastro (após LOCACAO/VENDA) ─────────────────────────────────

@Composable
fun CompletarCadastroScreen(
    vm: VendasViewModel,
    clienteId: Int,
    clienteNome: String,
    onFechar: () -> Unit,
) {
    var cpf    by remember { mutableStateOf("") }
    var cidade by remember { mutableStateOf("") }
    var bairro by remember { mutableStateOf("") }
    val isSaving by vm.isSaving.collectAsState()

    Column(Modifier.fillMaxSize()) {
        Surface(shadowElevation = 2.dp, color = Color.White) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Completar cadastro", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.weight(1f).padding(start = 8.dp))
            }
        }
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(color = Blue50, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, Blue200), modifier = Modifier.fillMaxWidth()) {
                Text("Registro criado com sucesso! Complete os dados pessoais de $clienteNome abaixo.", fontSize = 13.sp, color = Blue700, modifier = Modifier.padding(12.dp))
            }
            Text("DADOS PESSOAIS", fontSize = 11.sp, color = Gray500, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = cpf, onValueChange = { cpf = it },
                label = { Text("CPF") }, modifier = Modifier.fillMaxWidth(),
                singleLine = true, shape = RoundedCornerShape(10.dp),
                placeholder = { Text("000.000.000-00") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = cidade, onValueChange = { cidade = it },
                    label = { Text("Cidade") }, modifier = Modifier.weight(1f),
                    singleLine = true, shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                )
                OutlinedTextField(
                    value = bairro, onValueChange = { bairro = it },
                    label = { Text("Bairro") }, modifier = Modifier.weight(1f),
                    singleLine = true, shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        vm.completarCadastro(clienteId, cpf, cidade, bairro) { onFechar() }
                    },
                    enabled = !isSaving,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Blue600),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    if (isSaving) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    else Text("Salvar dados", fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = onFechar,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                ) { Text("Pular") }
            }
        }
    }
}

// ─── Formulário Nova Locação (chamado de ClientesScreen) ─────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocacaoFormScreen(
    vm: VendasViewModel = viewModel(),
    clienteIdFixo: Int = 0,
    clienteNomeFixo: String = "",
    eventoInicial: String = "",
    dataEventoInicial: String = "",
    onFechar: () -> Unit,
) {
    val clientes        by vm.clientesBusca.collectAsState()
    val buscandoCliente by vm.buscandoCliente.collectAsState()
    val isSaving        by vm.isSaving.collectAsState()
    val erro            by vm.erro.collectAsState()

    var form by remember {
        mutableStateOf(
            if (clienteIdFixo > 0) LocacaoForm(
                clienteId = clienteIdFixo, clienteNome = clienteNomeFixo,
                evento = eventoInicial, dataEvento = dataEventoInicial,
            ) else LocacaoForm()
        )
    }
    var clienteBusca      by remember { mutableStateOf("") }
    var showClientes      by remember { mutableStateOf(false) }
    var mostrarCompletar  by remember { mutableStateOf(false) }
    var vestidoEncontrado by remember { mutableStateOf<com.mrjack.dressflow.data.model.Traje?>(null) }
    var buscandoVestido   by remember { mutableStateOf(false) }

    LaunchedEffect(form.traje, form.sexo) {
        if (form.sexo == "F" && form.traje.length >= 2) {
            delay(500)
            buscandoVestido = true
            vm.buscarVestido(form.traje.trim()) { t ->
                vestidoEncontrado = t
                buscandoVestido = false
            }
        } else {
            vestidoEncontrado = null
            buscandoVestido = false
        }
    }
    var clienteIdSalvo   by remember { mutableStateOf(0) }
    var clienteNomeSalvo by remember { mutableStateOf("") }
    var showPickerEvento by remember { mutableStateOf(false) }
    val pickerEvento = rememberDatePickerState(
        initialSelectedDateMillis = remember(form.dataEvento) {
            val p = form.dataEvento.split("-")
            if (p.size == 3) try {
                val cal = Calendar.getInstance()
                cal.set(p[0].toInt(), p[1].toInt() - 1, p[2].toInt(), 12, 0, 0)
                cal.timeInMillis
            } catch (_: Exception) { null } else null
        }
    )

    if (showPickerEvento) {
        DatePickerDialog(
            onDismissRequest = { showPickerEvento = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerEvento.selectedDateMillis?.let { millis ->
                        val cal = Calendar.getInstance(); cal.timeInMillis = millis
                        form = form.copy(dataEvento = "%04d-%02d-%02d".format(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)))
                    }
                    showPickerEvento = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showPickerEvento = false }) { Text("Cancelar") } },
        ) { DatePicker(state = pickerEvento) }
    }

    if (mostrarCompletar) {
        CompletarCadastroScreen(
            vm = vm,
            clienteId = clienteIdSalvo,
            clienteNome = clienteNomeSalvo,
            onFechar = onFechar,
        )
        return
    }

    val formasPagamento = listOf(
        "Dinheiro", "PIX", "Cartão de Crédito", "Cartão de Débito", "Boleto", "Cheque", "Parceria", "Permuta", "Outro",
    )

    fun tentarSalvar() {
        vm.erro.value = null
        when {
            form.clienteId == 0       -> vm.erro.value = "Selecione um cliente"
            form.traje.isBlank()      -> vm.erro.value = "Informe o traje"
            form.dataEvento.isBlank() -> vm.erro.value = "Informe a data do evento"
            form.valor.isBlank()      -> vm.erro.value = "Informe o valor"
            form.tipo == "ORCAMENTO" && form.motivoNaoFechar.isBlank() -> vm.erro.value = "Informe o motivo de não fechar"
            form.menorDeIdade && form.nomeResponsavel.isBlank() -> vm.erro.value = "Informe o nome do responsável"
            else -> vm.salvarLocacao(form) {
                if (form.tipo == "LOCACAO" || form.tipo == "VENDA") {
                    clienteIdSalvo = form.clienteId
                    clienteNomeSalvo = form.clienteNome
                    mostrarCompletar = true
                } else {
                    onFechar()
                }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Surface(shadowElevation = 2.dp, color = Color.White) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onFechar) { Icon(Icons.Default.Close, null) }
                Text("Novo registro", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Button(
                    onClick = { tentarSalvar() },
                    enabled = !isSaving,
                    colors = ButtonDefaults.buttonColors(containerColor = Blue600),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    if (isSaving) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    else Text("Registrar", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (erro != null) {
            Text(erro!!, color = Red500, fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth().background(Red100).padding(12.dp))
        }

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {

            // ── Tipo de registro ──────────────────────────────────────────────
            Text("TIPO DE REGISTRO", fontSize = 11.sp, color = Gray500, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("LOCACAO" to "Locação", "ORCAMENTO" to "Orçamento", "VENDA" to "Venda").forEach { (v, label) ->
                    val selected = form.tipo == v
                    val cor = when { selected && v == "LOCACAO" -> Blue600; selected && v == "ORCAMENTO" -> Color(0xFFD97706); selected -> Green600; else -> null }
                    if (selected && cor != null) {
                        Button(onClick = {}, colors = ButtonDefaults.buttonColors(containerColor = cor), shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
                            Text(label, fontSize = 13.sp)
                        }
                    } else {
                        OutlinedButton(onClick = { form = form.copy(tipo = v) }, shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, Gray200), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
                            Text(label, fontSize = 13.sp, color = Gray700)
                        }
                    }
                }
            }

            // ── Cliente ───────────────────────────────────────────────────────
            if (clienteIdFixo > 0) {
                Surface(color = Green100, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Person, null, tint = Green600, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(clienteNomeFixo, fontWeight = FontWeight.SemiBold, color = Green600, modifier = Modifier.weight(1f))
                    }
                }
            } else if (form.clienteId > 0 && clienteBusca.isBlank()) {
                Surface(color = Green100, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Person, null, tint = Green600, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(form.clienteNome, fontWeight = FontWeight.SemiBold, color = Green600, modifier = Modifier.weight(1f))
                        TextButton(onClick = { form = form.copy(clienteId = 0, clienteNome = "") }) { Text("Trocar", fontSize = 12.sp) }
                    }
                }
            } else {
                OutlinedTextField(
                    value = clienteBusca,
                    onValueChange = { clienteBusca = it; vm.buscarClientes(it); showClientes = it.length >= 2 },
                    label = { Text("Buscar cliente *") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = { if (buscandoCliente) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(10.dp),
                )
                if (showClientes && clientes.isNotEmpty()) {
                    Card(shape = RoundedCornerShape(10.dp), elevation = CardDefaults.cardElevation(4.dp)) {
                        Column {
                            clientes.take(6).forEach { c ->
                                ListItem(
                                    headlineContent = { Text(c.nome, fontWeight = FontWeight.Medium) },
                                    supportingContent = { Text(c.telefone ?: "") },
                                    modifier = Modifier.clickable {
                                        form = form.copy(clienteId = c.id, clienteNome = c.nome)
                                        clienteBusca = ""; showClientes = false; vm.clientesBusca.value = emptyList()
                                    },
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }

            // ── Dados do evento ───────────────────────────────────────────────
            Text("DADOS DO EVENTO", fontSize = 11.sp, color = Gray500, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                VField("Evento", form.evento, { form = form.copy(evento = it) }, caps = KeyboardCapitalization.Sentences, modifier = Modifier.weight(1.2f))
                Surface(
                    shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, Gray200), color = Color.White,
                    modifier = Modifier.weight(1f).clickable { showPickerEvento = true },
                ) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
                        Text("Data do evento *", fontSize = 12.sp, color = Gray500)
                        Text(if (form.dataEvento.isBlank()) "Selecionar" else fmtDataCompleta(form.dataEvento),
                            fontSize = 14.sp, color = if (form.dataEvento.isBlank()) Gray500 else Gray900)
                    }
                }
            }

            // ── Traje / Vestido ────────────────────────────────────────────────
            Text(if (form.sexo == "F") "VESTIDO" else "TRAJE", fontSize = 11.sp, color = Gray500, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                VField(if (form.sexo == "F") "Código do vestido *" else "Traje *", form.traje, { form = form.copy(traje = it) }, caps = KeyboardCapitalization.Sentences, modifier = Modifier.weight(1f))
                Column {
                    Text("Sexo", fontSize = 12.sp, color = Gray500)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("M" to "Masc", "F" to "Fem").forEach { (v, label) ->
                            FilterChip(selected = form.sexo == v, onClick = { form = form.copy(sexo = v) }, label = { Text(label, fontSize = 12.sp) })
                        }
                    }
                }
            }

            // Card de vestido encontrado (somente feminino)
            if (form.sexo == "F") {
                if (buscandoVestido) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Blue600)
                        Text("Buscando vestido...", fontSize = 12.sp, color = Gray500)
                    }
                } else if (vestidoEncontrado != null) {
                    val v = vestidoEncontrado!!
                    val preco = v.valorAluguel ?: v.valorVenda
                    LaunchedEffect(v.codigo) {
                        if (!preco.isNullOrBlank() && form.valor.isBlank()) {
                            form = form.copy(valor = preco)
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFF0F9FF),
                        border = BorderStroke(1.dp, Blue200),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (!v.imagemUrl.isNullOrBlank()) {
                                coil.compose.AsyncImage(
                                    model = v.imagemUrl,
                                    contentDescription = v.nome,
                                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop,
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(v.nome, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFF1E3A8A))
                                Text(v.codigo, fontSize = 11.sp, color = Blue600)
                                if (!preco.isNullOrBlank()) {
                                    Text("R$ $preco", fontSize = 12.sp, color = Blue700, fontWeight = FontWeight.Medium)
                                }
                            }
                            Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF16A34A), modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            // Tamanhos — masculino
            if (form.sexo == "M") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VField("Paletó", form.tamanhoPaleto, { form = form.copy(tamanhoPaleto = it) }, modifier = Modifier.weight(1f))
                    VField("Manga", form.tamanhoManga, { form = form.copy(tamanhoManga = it) }, modifier = Modifier.weight(1f))
                    VField("Colete", form.tamanhoColete, { form = form.copy(tamanhoColete = it) }, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VField("Camisa", form.camisa, { form = form.copy(camisa = it) }, modifier = Modifier.weight(1f))
                    VField("Calça", form.calca, { form = form.copy(calca = it) }, modifier = Modifier.weight(1f))
                    VField("Tam. calça", form.tamanhoCalca, { form = form.copy(tamanhoCalca = it) }, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VField("Cinto", form.cinto, { form = form.copy(cinto = it) }, modifier = Modifier.weight(1f))
                    VField("Sapato", form.sapato, { form = form.copy(sapato = it) }, modifier = Modifier.weight(1f))
                }
            }

            // ── Medidas corporais ─────────────────────────────────────────────
            Text("MEDIDAS CORPORAIS", fontSize = 11.sp, color = Gray500, fontWeight = FontWeight.SemiBold)
            if (form.sexo == "M") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VField("Tórax", form.torax, { form = form.copy(torax = it) }, modifier = Modifier.weight(1f))
                    VField("Abdômen", form.abdomen, { form = form.copy(abdomen = it) }, modifier = Modifier.weight(1f))
                    VField("Quadril", form.quadril, { form = form.copy(quadril = it) }, modifier = Modifier.weight(1f))
                    VField("Panturrilha", form.panturrilha, { form = form.copy(panturrilha = it) }, modifier = Modifier.weight(1f))
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VField("Busto", form.busto, { form = form.copy(busto = it) }, modifier = Modifier.weight(1f))
                    VField("Cintura", form.cintura, { form = form.copy(cintura = it) }, modifier = Modifier.weight(1f))
                    VField("Quadril", form.quadril, { form = form.copy(quadril = it) }, modifier = Modifier.weight(1f))
                }
            }

            // ── Menor de Idade ────────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Checkbox(checked = form.menorDeIdade, onCheckedChange = { form = form.copy(menorDeIdade = it, nomeResponsavel = if (!it) "" else form.nomeResponsavel) })
                Text("Menor de Idade", fontSize = 14.sp, color = Gray700)
            }
            if (form.menorDeIdade) {
                VField("Nome do responsável (pai/mãe) *", form.nomeResponsavel, { form = form.copy(nomeResponsavel = it) }, caps = KeyboardCapitalization.Words)
            }

            // ── Pagamento ─────────────────────────────────────────────────────
            Text("PAGAMENTO", fontSize = 11.sp, color = Gray500, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = form.valor, onValueChange = { form = form.copy(valor = it) },
                    label = { Text("Valor (R$) *") }, modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    singleLine = true, shape = RoundedCornerShape(10.dp),
                )
                OutlinedTextField(
                    value = form.valorEntrada, onValueChange = { form = form.copy(valorEntrada = it) },
                    label = { Text("Entrada") }, modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    singleLine = true, shape = RoundedCornerShape(10.dp),
                )
                OutlinedTextField(
                    value = form.parcelas, onValueChange = { form = form.copy(parcelas = it) },
                    label = { Text("Parcelas") }, modifier = Modifier.width(90.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    singleLine = true, shape = RoundedCornerShape(10.dp),
                )
            }
            Text("Forma de pagamento *", fontSize = 12.sp, color = Gray700, fontWeight = FontWeight.Medium)
            formasPagamento.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { fp ->
                        FilterChip(
                            selected = form.formaPagamento == fp,
                            onClick = { form = form.copy(formaPagamento = fp) },
                            label = { Text(fp, fontSize = 11.sp) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }

            // ── Ajustes ───────────────────────────────────────────────────────
            OutlinedTextField(
                value = form.ajustes, onValueChange = { form = form.copy(ajustes = it) },
                label = { Text("Ajustes") }, modifier = Modifier.fillMaxWidth(),
                minLines = 2, shape = RoundedCornerShape(10.dp),
            )

            // ── Motivo de não fechar (apenas Orçamento) ───────────────────────
            if (form.tipo == "ORCAMENTO") {
                OutlinedTextField(
                    value = form.motivoNaoFechar, onValueChange = { form = form.copy(motivoNaoFechar = it) },
                    label = { Text("Motivo de não fechar *") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3, shape = RoundedCornerShape(10.dp),
                    placeholder = { Text("Ex: Cliente achou caro, vai pensar, prefere outra cor...") },
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Color(0xFFFCD34D),
                        focusedBorderColor = Color(0xFFF59E0B),
                        unfocusedContainerColor = Color(0xFFFFFBEB),
                        focusedContainerColor = Color(0xFFFFFBEB),
                    ),
                )
            }

            // ── Observações ───────────────────────────────────────────────────
            OutlinedTextField(
                value = form.observacoes, onValueChange = { form = form.copy(observacoes = it) },
                label = { Text("Observações") }, modifier = Modifier.fillMaxWidth(),
                minLines = 2, shape = RoundedCornerShape(10.dp),
            )

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { tentarSalvar() },
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Blue600),
                shape = RoundedCornerShape(8.dp),
            ) {
                if (isSaving) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                else Text("Registrar", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
        }
    }
}

// ─── Helpers de Composable ────────────────────────────────────────────────────

@Composable
fun VField(
    label: String, value: String, onChange: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    caps: KeyboardCapitalization = KeyboardCapitalization.None,
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) },
        modifier = modifier, singleLine = true, shape = RoundedCornerShape(10.dp),
        keyboardOptions = KeyboardOptions(capitalization = caps, imeAction = ImeAction.Next),
    )
}

// ─── Helpers de formatação ────────────────────────────────────────────────────

fun brl(v: Double): String {
    val s = String.format(java.util.Locale.US, "%.2f", v)
    val parts = s.split(".")
    val intPart = parts[0].reversed().chunked(3).joinToString(".").reversed()
    return "R$ $intPart,${parts[1]}"
}

fun fmtDataSimples(s: String?): String = try {
    if (s == null) "—" else "${s.substring(8, 10)}/${s.substring(5, 7)}"
} catch (_: Exception) { s ?: "—" }

fun fmtDataCompleta(s: String?): String = try {
    if (s == null) "—" else "${s.substring(8, 10)}/${s.substring(5, 7)}/${s.substring(0, 4)}"
} catch (_: Exception) { s ?: "—" }

fun fmtMes(s: String): String = try {
    val meses = listOf("", "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
        "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro")
    val partes = s.split("-")
    val mes = partes[1].toIntOrNull() ?: 0
    "${meses.getOrElse(mes) { s }} de ${partes[0]}"
} catch (_: Exception) { s }
