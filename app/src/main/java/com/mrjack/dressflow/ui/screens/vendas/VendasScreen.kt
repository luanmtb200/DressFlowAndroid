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

    fun buscarTrajePorCodigo(codigo: String, onResult: (Traje?) -> Unit) {
        viewModelScope.launch {
            try {
                val resp = api.buscarTrajePorCodigo(codigo)
                onResult(if (resp.isSuccessful) resp.body() else null)
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
                    erro.value = parseErro(resp.errorBody()?.string(), resp.code())
                }
            } catch (e: Exception) { erro.value = e.message }
            finally { isSaving.value = false }
        }
    }

    fun atualizarLocacao(id: Int, form: LocacaoForm, onSuccess: () -> Unit) {
        viewModelScope.launch {
            isSaving.value = true
            erro.value = null
            try {
                val resp = api.atualizarLocacao(id, buildBody(form))
                if (resp.isSuccessful) {
                    sucesso.value = "Locação atualizada!"
                    onSuccess()
                } else {
                    erro.value = parseErro(resp.errorBody()?.string(), resp.code())
                }
            } catch (e: Exception) { erro.value = e.message }
            finally { isSaving.value = false }
        }
    }

    private fun parseErro(body: String?, code: Int): String =
        body?.let { try { com.google.gson.JsonParser.parseString(it).asJsonObject.get("error")?.asString } catch (_: Exception) { null } } ?: "Erro $code"

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
            "tamanhoPaleto"    to form.tamanhoPaleto,
            "tamanhoManga"     to form.tamanhoManga,
            "camisa"           to form.camisa,
            "calca"            to form.calca,
            "tamanhoCalca"     to form.tamanhoCalca,
            "cinto"            to form.cinto,
            "sapato"           to form.sapato,
            "tamanhoColete"    to form.tamanhoColete,
            "gravata"          to form.gravata,
            "abotoadura"       to form.abotoadura,
            "torax"            to form.torax,
            "abdomen"          to form.abdomen,
            "quadril"          to form.quadril,
            "panturrilha"      to form.panturrilha,
            "busto"            to form.busto,
            "cintura"          to form.cintura,
            "ajustes"          to form.ajustes,
            "observacoes"      to form.observacoes,
            "motivoNaoFechar"  to form.motivoNaoFechar.ifBlank { null },
            "nomeResponsavel"  to if (form.menorDeIdade) form.nomeResponsavel.ifBlank { null } else null,
            "cpfResponsavel"   to if (form.menorDeIdade) form.cpfResponsavel.ifBlank { null } else null,
            "valorEntrada"     to form.valorEntrada.replace(",", ".").let { if (it.isBlank()) null else it.toDoubleOrNull() },
            "dataLimiteBoleto" to form.dataLimiteBoleto.ifBlank { null },
            "vencimentoBoleto" to form.vencimentoBoleto.ifBlank { null },
            "vendaPaleto"      to if (form.vendaPaleto) true else null,
            "vendaColete"      to if (form.vendaColete) true else null,
            "vendaCalca"       to if (form.vendaCalca) true else null,
            "itensLocados"     to form.itensLocados,
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
    var valorBase: String = "",
    var desconto: String = "0",
    var valor: String = "",
    var valorEntrada: String = "",
    var dataLimiteBoleto: String = "",
    var vencimentoBoleto: String = "",
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
    var gravata: String = "",
    var abotoadura: String = "",
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
    var cpfResponsavel: String = "",
    var vendaPaleto: Boolean = false,
    var vendaColete: Boolean = false,
    var vendaCalca: Boolean = false,
    var itensLocados: String = "",
)

fun Locacao.toForm() = LocacaoForm(
    clienteId       = this.cliente?.id ?: 0,
    clienteNome     = this.cliente?.nome ?: "",
    tipo            = this.tipo,
    traje           = this.traje,
    evento          = this.evento ?: "",
    dataEvento      = this.dataEvento.take(10),
    formaPagamento  = this.formaPagamento,
    valorBase       = this.valor,
    desconto        = "0",
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
    gravata         = this.gravata ?: "",
    abotoadura      = this.abotoadura ?: "",
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

// ─── Data class para traje extra ─────────────────────────────────────────────

data class TrajeExtra(
    val traje: String = "",
    val sexo: String = "M",
    val valorBase: String = "",
    val desconto: String = "0",
    // Masculino - itens locados e tamanhos
    val itensLocados: List<String> = emptyList(),
    val tamanhoPaleto: String = "",
    val tamanhoManga: String = "",
    val tamanhoColete: String = "",
    val calca: String = "",
    val tamanhoCalca: String = "",
    val camisa: String = "",
    val gravata: String = "",
    val cinto: String = "",
    val sapato: String = "",
    val abotoadura: String = "",
    // Medidas corporais (M)
    val torax: String = "",
    val abdomen: String = "",
    val quadril: String = "",
    val panturrilha: String = "",
    // Medidas corporais (F)
    val busto: String = "",
    val cintura: String = "",
    // Geral
    val ajustes: String = "",
    val menorDeIdade: Boolean = false,
    val nomeResponsavel: String = "",
)

// ─── Formulário Nova / Edição de Locação ──────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocacaoFormScreen(
    vm: VendasViewModel = viewModel(),
    clienteIdFixo: Int = 0,
    clienteNomeFixo: String = "",
    eventoInicial: String = "",
    dataEventoInicial: String = "",
    locacaoExistente: Locacao? = null,
    onFechar: () -> Unit,
) {
    val clientes        by vm.clientesBusca.collectAsState()
    val buscandoCliente by vm.buscandoCliente.collectAsState()
    val isSaving        by vm.isSaving.collectAsState()
    val erro            by vm.erro.collectAsState()

    val isEditando = locacaoExistente != null

    var form by remember {
        mutableStateOf(
            locacaoExistente?.toForm() ?: if (clienteIdFixo > 0) LocacaoForm(
                clienteId = clienteIdFixo, clienteNome = clienteNomeFixo,
                evento = eventoInicial, dataEvento = dataEventoInicial,
            ) else LocacaoForm()
        )
    }

    var clienteBusca        by remember { mutableStateOf("") }
    var showClientes        by remember { mutableStateOf(false) }
    var mostrarCompletar    by remember { mutableStateOf(false) }
    var trajeEncontrado     by remember { mutableStateOf<Traje?>(null) }
    var buscandoTraje       by remember { mutableStateOf(false) }
    var clienteIdSalvo      by remember { mutableStateOf(0) }
    var clienteNomeSalvo    by remember { mutableStateOf("") }
    var showPickerEvento    by remember { mutableStateOf(false) }
    var showPickerBoleto    by remember { mutableStateOf(false) }
    var showPickerVencimento by remember { mutableStateOf(false) }
    var showPadronizacao    by remember { mutableStateOf(false) }

    // Extra trajes (múltiplos trajes no mesmo registro)
    var extrasTraje by remember { mutableStateOf<List<TrajeExtra>>(emptyList()) }

    // Auto-busca apenas para vestido (F) — masculino é nome livre
    LaunchedEffect(form.traje, form.sexo) {
        if (form.sexo != "F") { trajeEncontrado = null; buscandoTraje = false; return@LaunchedEffect }
        if (form.traje.length >= 2) {
            delay(600)
            buscandoTraje = true
            vm.buscarTrajePorCodigo(form.traje.trim()) { t ->
                trajeEncontrado = if (t?.tipo == "VESTIDO") t else null
                if (t?.tipo == "VESTIDO") {
                    val precoBase = t.valorAluguel ?: t.valorVenda ?: ""
                    form = form.copy(valorBase = precoBase)
                }
                buscandoTraje = false
            }
        } else if (form.traje.isBlank()) {
            trajeEncontrado = null
        }
    }

    // Valor final calculado
    val valorFinalNum = run {
        val base = form.valorBase.replace(",", ".").toDoubleOrNull() ?: 0.0
        val desc = form.desconto.replace(",", ".").toDoubleOrNull() ?: 0.0
        if (base > 0) base * (1.0 - desc / 100.0) else form.valor.replace(",", ".").toDoubleOrNull() ?: 0.0
    }

    val pickerEvento     = rememberDatePickerState(initialSelectedDateMillis = parseDateToMillis(form.dataEvento))
    val pickerBoleto     = rememberDatePickerState(initialSelectedDateMillis = parseDateToMillis(form.dataLimiteBoleto))
    val pickerVencimento = rememberDatePickerState(initialSelectedDateMillis = parseDateToMillis(form.vencimentoBoleto))

    if (showPickerEvento) DatePickerDialog(
        onDismissRequest = { showPickerEvento = false },
        confirmButton = { TextButton(onClick = {
            pickerEvento.selectedDateMillis?.let { form = form.copy(dataEvento = millisToDateStr(it)) }
            showPickerEvento = false
        }) { Text("OK") } },
        dismissButton = { TextButton(onClick = { showPickerEvento = false }) { Text("Cancelar") } },
    ) { DatePicker(state = pickerEvento) }

    if (showPickerBoleto) DatePickerDialog(
        onDismissRequest = { showPickerBoleto = false },
        confirmButton = { TextButton(onClick = {
            pickerBoleto.selectedDateMillis?.let { form = form.copy(dataLimiteBoleto = millisToDateStr(it)) }
            showPickerBoleto = false
        }) { Text("OK") } },
        dismissButton = { TextButton(onClick = { showPickerBoleto = false }) { Text("Cancelar") } },
    ) { DatePicker(state = pickerBoleto) }

    if (showPickerVencimento) DatePickerDialog(
        onDismissRequest = { showPickerVencimento = false },
        confirmButton = { TextButton(onClick = {
            pickerVencimento.selectedDateMillis?.let { form = form.copy(vencimentoBoleto = millisToDateStr(it)) }
            showPickerVencimento = false
        }) { Text("OK") } },
        dismissButton = { TextButton(onClick = { showPickerVencimento = false }) { Text("Cancelar") } },
    ) { DatePicker(state = pickerVencimento) }

    if (mostrarCompletar) {
        CompletarCadastroScreen(vm = vm, clienteId = clienteIdSalvo, clienteNome = clienteNomeSalvo, onFechar = onFechar)
        return
    }

    fun valorParaSalvar(): String {
        val base = form.valorBase.replace(",", ".").toDoubleOrNull()
        return if (base != null && base > 0)
            String.format(java.util.Locale.US, "%.2f", valorFinalNum)
        else form.valor
    }

    fun tentarSalvar() {
        vm.erro.value = null
        val valorFinal = valorParaSalvar()
        val formFinal = form.copy(valor = valorFinal)
        when {
            formFinal.clienteId == 0       -> vm.erro.value = "Selecione um cliente"
            formFinal.traje.isBlank()      -> vm.erro.value = "Informe o traje"
            formFinal.dataEvento.isBlank() -> vm.erro.value = "Informe a data do evento"
            formFinal.valor.isBlank()      -> vm.erro.value = "Informe o valor"
            formFinal.tipo == "ORCAMENTO" && formFinal.motivoNaoFechar.isBlank() -> vm.erro.value = "Informe o motivo de não fechar"
            formFinal.menorDeIdade && formFinal.nomeResponsavel.isBlank() -> vm.erro.value = "Informe o nome do responsável"
            else -> {
                if (isEditando && locacaoExistente != null) {
                    vm.atualizarLocacao(locacaoExistente.id, formFinal) { onFechar() }
                } else {
                    vm.salvarLocacao(formFinal) {
                        // Criar extras sequencialmente
                        if (extrasTraje.isNotEmpty()) {
                            extrasTraje.forEach { extra ->
                                val extraValor = run {
                                    val b = extra.valorBase.replace(",", ".").toDoubleOrNull() ?: 0.0
                                    val d = extra.desconto.replace(",", ".").toDoubleOrNull() ?: 0.0
                                    if (b > 0) String.format(java.util.Locale.US, "%.2f", b * (1.0 - d / 100.0)) else extra.valorBase
                                }
                                val extraForm = formFinal.copy(
                                    traje = extra.traje,
                                    sexo = extra.sexo,
                                    valorBase = extra.valorBase,
                                    valor = extraValor,
                                    tamanhoPaleto = extra.tamanhoPaleto,
                                    tamanhoManga = extra.tamanhoManga,
                                    tamanhoColete = extra.tamanhoColete,
                                    calca = extra.calca,
                                    tamanhoCalca = extra.tamanhoCalca,
                                    camisa = extra.camisa,
                                    gravata = extra.gravata,
                                    cinto = extra.cinto,
                                    sapato = extra.sapato,
                                    abotoadura = extra.abotoadura,
                                    torax = extra.torax,
                                    abdomen = extra.abdomen,
                                    quadril = extra.quadril,
                                    panturrilha = extra.panturrilha,
                                    busto = extra.busto,
                                    cintura = extra.cintura,
                                    ajustes = extra.ajustes,
                                    menorDeIdade = extra.menorDeIdade,
                                    nomeResponsavel = extra.nomeResponsavel,
                                    itensLocados = extra.itensLocados.joinToString(","),
                                )
                                vm.salvarLocacao(extraForm) {}
                            }
                        }
                        if (formFinal.tipo == "LOCACAO" || formFinal.tipo == "VENDA") {
                            clienteIdSalvo = formFinal.clienteId
                            clienteNomeSalvo = formFinal.clienteNome
                            mostrarCompletar = true
                        } else {
                            onFechar()
                        }
                    }
                }
            }
        }
    }

    val formasPagamento = listOf(
        "Dinheiro", "PIX", "Cartão de Crédito", "Cartão de Débito", "Boleto", "Cheque", "Parceria", "Permuta", "Outro",
    )

    Column(Modifier.fillMaxSize()) {
        Surface(shadowElevation = 2.dp, color = Color.White) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onFechar) { Icon(Icons.Default.Close, null) }
                Text(
                    if (isEditando) "Editar registro" else "Novo registro",
                    fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = { tentarSalvar() },
                    enabled = !isSaving,
                    colors = ButtonDefaults.buttonColors(containerColor = Blue600),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    if (isSaving) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    else Text(if (isEditando) "Salvar" else "Registrar", fontWeight = FontWeight.SemiBold)
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

            // ── TIPO DE REGISTRO ──────────────────────────────────────────────
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

            // ── CLIENTE ───────────────────────────────────────────────────────
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

            // ── DADOS DO EVENTO ───────────────────────────────────────────────
            Text("DADOS DO EVENTO", fontSize = 11.sp, color = Gray500, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                VField("Evento", form.evento, { form = form.copy(evento = it) }, caps = KeyboardCapitalization.Sentences, modifier = Modifier.weight(1.2f))
                Surface(
                    shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, Gray200), color = Color.White,
                    modifier = Modifier.weight(1f).clickable { showPickerEvento = true },
                ) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
                        Text("Data do evento *", fontSize = 12.sp, color = Gray500)
                        Text(
                            if (form.dataEvento.isBlank()) "Selecionar" else fmtDataCompleta(form.dataEvento),
                            fontSize = 14.sp, color = if (form.dataEvento.isBlank()) Gray500 else Gray900,
                        )
                    }
                }
            }

            // ── TRAJE / VESTIDO ───────────────────────────────────────────────
            Text(if (form.sexo == "F") "VESTIDO" else "TRAJE", fontSize = 11.sp, color = Gray500, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = form.traje,
                    onValueChange = { form = form.copy(traje = if (form.sexo == "F") it.uppercase() else it) },
                    label = { Text(if (form.sexo == "F") "Código do vestido *" else "Traje *") },
                    placeholder = { if (form.sexo == "M") Text("Ex: Smoking, Fraque") },
                    modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(10.dp),
                    keyboardOptions = KeyboardOptions(
                        capitalization = if (form.sexo == "F") KeyboardCapitalization.Characters else KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                    trailingIcon = {
                        when {
                            form.sexo == "F" && buscandoTraje -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            form.sexo == "F" && trajeEncontrado != null -> Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF16A34A))
                            else -> {}
                        }
                    },
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Sexo", fontSize = 11.sp, color = Gray500)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("M" to "M", "F" to "F").forEach { (v, label) ->
                            FilterChip(selected = form.sexo == v, onClick = { form = form.copy(sexo = v); trajeEncontrado = null }, label = { Text(label, fontSize = 12.sp) })
                        }
                    }
                }
            }

            // Card do vestido encontrado (apenas F)
            if (form.sexo == "F" && buscandoTraje) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Blue600)
                    Text("Buscando vestido...", fontSize = 12.sp, color = Gray500)
                }
            } else if (form.sexo == "F" && trajeEncontrado != null) {
                val t = trajeEncontrado!!
                Surface(
                    shape = RoundedCornerShape(10.dp), color = Color(0xFFF0F9FF),
                    border = BorderStroke(1.dp, Blue200), modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (!t.imagemUrl.isNullOrBlank()) {
                            coil.compose.AsyncImage(
                                model = t.imagemUrl, contentDescription = t.nome,
                                modifier = Modifier.size(80.dp).clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop,
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(t.nome, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color(0xFF1E3A8A))
                            Text("Cód: ${t.codigo}", fontSize = 11.sp, color = Blue600)
                            val precoExib = t.valorAluguel ?: t.valorVenda
                            if (!precoExib.isNullOrBlank()) Text("R$ $precoExib", fontSize = 12.sp, color = Blue700, fontWeight = FontWeight.Medium)
                        }
                        Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF16A34A), modifier = Modifier.size(22.dp))
                    }
                }
            }

            // ── ITENS LOCADOS (masculino) ──────────────────────────────────────
            if (form.sexo == "M") {
                Text("ITENS LOCADOS", fontSize = 11.sp, color = Gray500, fontWeight = FontWeight.SemiBold)
                LocacaoItemRow("Paletó", form.tamanhoPaleto, { form = form.copy(tamanhoPaleto = it) }, form.vendaPaleto, { form = form.copy(vendaPaleto = it) })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VField("Manga", form.tamanhoManga, { form = form.copy(tamanhoManga = it) }, modifier = Modifier.weight(1f))
                    LocacaoItemRow("Colete", form.tamanhoColete, { form = form.copy(tamanhoColete = it) }, form.vendaColete, { form = form.copy(vendaColete = it) }, modifier = Modifier.weight(1.5f))
                }
                LocacaoItemRow("Calça", form.calca, { form = form.copy(calca = it) }, form.vendaCalca, { form = form.copy(vendaCalca = it) }, tamanhoExtra = form.tamanhoCalca, onTamanhoExtra = { form = form.copy(tamanhoCalca = it) }, labelTamanhoExtra = "Tam. Calça")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VField("Camisa", form.camisa, { form = form.copy(camisa = it) }, modifier = Modifier.weight(1f))
                    VField("Gravata", form.gravata, { form = form.copy(gravata = it) }, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VField("Cinto", form.cinto, { form = form.copy(cinto = it) }, modifier = Modifier.weight(1f))
                    VField("Sapato", form.sapato, { form = form.copy(sapato = it) }, modifier = Modifier.weight(1f))
                    VField("Abotoadura", form.abotoadura, { form = form.copy(abotoadura = it) }, modifier = Modifier.weight(1f))
                }
            }

            // ── MEDIDAS CORPORAIS ─────────────────────────────────────────────
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

            // ── MENOR DE IDADE ────────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Checkbox(checked = form.menorDeIdade, onCheckedChange = { form = form.copy(menorDeIdade = it, nomeResponsavel = if (!it) "" else form.nomeResponsavel) })
                Text("Menor de Idade", fontSize = 14.sp, color = Gray700)
            }
            if (form.menorDeIdade) {
                VField("Nome do responsável *", form.nomeResponsavel, { form = form.copy(nomeResponsavel = it) }, caps = KeyboardCapitalization.Words)
                VField("CPF do responsável", form.cpfResponsavel, { form = form.copy(cpfResponsavel = it) })
            }

            // ── PAGAMENTO ─────────────────────────────────────────────────────
            Text("PAGAMENTO", fontSize = 11.sp, color = Gray500, fontWeight = FontWeight.SemiBold)

            // Valor base + desconto
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = form.valorBase, onValueChange = { form = form.copy(valorBase = it) },
                    label = { Text("Valor base (R$) *") }, modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    singleLine = true, shape = RoundedCornerShape(10.dp),
                )
                OutlinedTextField(
                    value = form.desconto, onValueChange = { form = form.copy(desconto = it) },
                    label = { Text("Desconto %") }, modifier = Modifier.width(110.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    singleLine = true, shape = RoundedCornerShape(10.dp),
                )
            }

            // Valor final
            val baseNum = form.valorBase.replace(",", ".").toDoubleOrNull() ?: 0.0
            val descNum = form.desconto.replace(",", ".").toDoubleOrNull() ?: 0.0
            if (baseNum > 0) {
                Surface(
                    color = if (descNum > 0) Color(0xFFECFDF5) else Color(0xFFF0F9FF),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, if (descNum > 0) Color(0xFF6EE7B7) else Blue200),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("Valor final", fontSize = 12.sp, color = Gray500)
                            if (descNum > 0) Text("(${descNum.toInt()}% de desconto aplicado)", fontSize = 11.sp, color = Color(0xFF059669))
                        }
                        Text(brl(valorFinalNum), fontWeight = FontWeight.Bold, fontSize = 20.sp, color = if (descNum > 0) Color(0xFF059669) else Color(0xFF1E3A8A))
                    }
                }
            } else {
                OutlinedTextField(
                    value = form.valor, onValueChange = { form = form.copy(valor = it) },
                    label = { Text("Valor (R$) *") }, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    singleLine = true, shape = RoundedCornerShape(10.dp),
                )
            }

            // Forma de pagamento
            Text("Forma de pagamento *", fontSize = 12.sp, color = Gray700, fontWeight = FontWeight.Medium)
            formasPagamento.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { fp ->
                        FilterChip(selected = form.formaPagamento == fp, onClick = { form = form.copy(formaPagamento = fp, parcelas = "1") },
                            label = { Text(fp, fontSize = 11.sp) }, modifier = Modifier.weight(1f))
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }

            // ── BOLETO ────────────────────────────────────────────────────────
            if (form.formaPagamento == "Boleto") {
                Text("PARCELAMENTO — BOLETO", fontSize = 11.sp, color = Gray500, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Parcelas:", fontSize = 13.sp, color = Gray700)
                    (1..6).forEach { n ->
                        FilterChip(selected = form.parcelas == n.toString(), onClick = { form = form.copy(parcelas = n.toString()) },
                            label = { Text("${n}x", fontSize = 12.sp) })
                    }
                }
                val numParcelas = form.parcelas.toIntOrNull() ?: 1
                val valorBoleto = valorFinalNum
                if (numParcelas == 1) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = form.valorEntrada, onValueChange = { form = form.copy(valorEntrada = it) },
                            label = { Text("Entrada (R$)") }, modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                            singleLine = true, shape = RoundedCornerShape(10.dp),
                        )
                        val saldo = valorBoleto - (form.valorEntrada.replace(",", ".").toDoubleOrNull() ?: 0.0)
                        Surface(modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp), color = Gray50, border = BorderStroke(1.dp, Gray200)) {
                            Column(Modifier.padding(horizontal = 12.dp, vertical = 14.dp)) {
                                Text("Saldo", fontSize = 12.sp, color = Gray500)
                                Text(brl(if (saldo > 0) saldo else 0.0), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Gray900)
                            }
                        }
                    }
                    Surface(shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, Gray200), color = Color.White,
                        modifier = Modifier.fillMaxWidth().clickable { showPickerBoleto = true }) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 14.dp)) {
                            Text("Data limite para pagamento", fontSize = 12.sp, color = Gray500)
                            Text(if (form.dataLimiteBoleto.isBlank()) "Selecionar data" else fmtDataCompleta(form.dataLimiteBoleto),
                                fontSize = 14.sp, color = if (form.dataLimiteBoleto.isBlank()) Gray500 else Gray900)
                        }
                    }
                } else {
                    val valorParcela = if (numParcelas > 0) valorBoleto / numParcelas else 0.0
                    Surface(shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, Gray200), color = Color.White,
                        modifier = Modifier.fillMaxWidth().clickable { showPickerVencimento = true }) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 14.dp)) {
                            Text("Vencimento da 1ª parcela", fontSize = 12.sp, color = Gray500)
                            Text(if (form.vencimentoBoleto.isBlank()) "Selecionar data" else fmtDataCompleta(form.vencimentoBoleto),
                                fontSize = 14.sp, color = if (form.vencimentoBoleto.isBlank()) Gray500 else Gray900)
                        }
                    }
                    if (form.vencimentoBoleto.isNotBlank() && valorParcela > 0) {
                        Surface(shape = RoundedCornerShape(10.dp), color = Blue50, border = BorderStroke(1.dp, Blue200), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("CRONOGRAMA", fontSize = 10.sp, color = Blue700, fontWeight = FontWeight.SemiBold)
                                repeat(numParcelas) { i ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("${i + 1}ª parcela — ${addMonthsToDate(form.vencimentoBoleto, i)}", fontSize = 12.sp, color = Blue700)
                                        Text(brl(valorParcela), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E3A8A))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── CARTÃO DE CRÉDITO — PARCELAS ──────────────────────────────────
            if (form.formaPagamento == "Cartão de Crédito") {
                Text("PARCELAMENTO", fontSize = 11.sp, color = Gray500, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..5).forEach { n ->
                        FilterChip(selected = form.parcelas == n.toString(), onClick = { form = form.copy(parcelas = n.toString()) },
                            label = { Text("${n}x", fontSize = 12.sp) }, modifier = Modifier.weight(1f))
                    }
                }
            }

            // ── TRAJES EXTRAS ─────────────────────────────────────────────────
            if (!isEditando) {
                Text("TRAJES ADICIONAIS", fontSize = 11.sp, color = Gray500, fontWeight = FontWeight.SemiBold)
                extrasTraje.forEachIndexed { idx, extra ->
                    TrajeExtraCard(
                        extra = extra,
                        index = idx,
                        vm = vm,
                        onUpdate = { extrasTraje = extrasTraje.toMutableList().also { it[idx] = extra } },
                        onRemover = { extrasTraje = extrasTraje.toMutableList().also { it.removeAt(idx) } },
                        onChanged = { novo -> extrasTraje = extrasTraje.toMutableList().also { it[idx] = novo } },
                    )
                }
                OutlinedButton(
                    onClick = { extrasTraje = extrasTraje + TrajeExtra() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Blue200),
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Adicionar outro traje", fontSize = 13.sp, color = Blue600)
                }
            }

            // ── AJUSTES ───────────────────────────────────────────────────────
            OutlinedTextField(
                value = form.ajustes, onValueChange = { form = form.copy(ajustes = it) },
                label = { Text("Ajustes") }, modifier = Modifier.fillMaxWidth(),
                minLines = 2, shape = RoundedCornerShape(10.dp),
            )

            // ── MOTIVO DE NÃO FECHAR (apenas Orçamento) ───────────────────────
            if (form.tipo == "ORCAMENTO") {
                OutlinedTextField(
                    value = form.motivoNaoFechar, onValueChange = { form = form.copy(motivoNaoFechar = it) },
                    label = { Text("Motivo de não fechar *") },
                    modifier = Modifier.fillMaxWidth(), minLines = 3, shape = RoundedCornerShape(10.dp),
                    placeholder = { Text("Ex: Cliente achou caro, vai pensar, prefere outra cor...") },
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = Color(0xFFFCD34D), focusedBorderColor = Color(0xFFF59E0B),
                        unfocusedContainerColor = Color(0xFFFFFBEB), focusedContainerColor = Color(0xFFFFFBEB),
                    ),
                )
            }

            // ── OBSERVAÇÕES ───────────────────────────────────────────────────
            OutlinedTextField(
                value = form.observacoes, onValueChange = { form = form.copy(observacoes = it) },
                label = { Text("Observações") }, modifier = Modifier.fillMaxWidth(),
                minLines = 2, shape = RoundedCornerShape(10.dp),
            )

            // ── RESUMO FINANCEIRO ─────────────────────────────────────────────
            if (valorFinalNum > 0) {
                val totalExtras = extrasTraje.sumOf { e ->
                    val b = e.valorBase.replace(",", ".").toDoubleOrNull() ?: 0.0
                    val d = e.desconto.replace(",", ".").toDoubleOrNull() ?: 0.0
                    if (b > 0) b * (1.0 - d / 100.0) else 0.0
                }
                val totalGeral = valorFinalNum + totalExtras
                Surface(color = Color(0xFF1F2937), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("RESUMO FINANCEIRO", fontSize = 10.sp, color = Color(0xFF9CA3AF), fontWeight = FontWeight.SemiBold)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Traje principal", fontSize = 13.sp, color = Color.White)
                            Text(brl(valorFinalNum), fontSize = 13.sp, color = Color(0xFF86EFAC), fontWeight = FontWeight.Medium)
                        }
                        if (extrasTraje.isNotEmpty()) {
                            extrasTraje.forEachIndexed { i, e ->
                                val v = run { val b = e.valorBase.replace(",", ".").toDoubleOrNull() ?: 0.0; val d = e.desconto.replace(",", ".").toDoubleOrNull() ?: 0.0; if (b > 0) b * (1.0 - d / 100.0) else 0.0 }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Extra ${i + 1}: ${e.traje.ifBlank { "—" }}", fontSize = 12.sp, color = Color(0xFF9CA3AF))
                                    Text(brl(v), fontSize = 12.sp, color = Color(0xFF86EFAC))
                                }
                            }
                            HorizontalDivider(color = Color(0xFF374151))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("TOTAL", fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                Text(brl(totalGeral), fontSize = 16.sp, color = Color(0xFF4ADE80), fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            }

            // ── BOTÃO FAZER PADRONIZAÇÃO ──────────────────────────────────────
            if (!isEditando && form.tipo != "ORCAMENTO") {
                Surface(
                    color = Color(0xFFFFFBEB), shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFFFCD34D)), modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Star, null, tint = Color(0xFFD97706), modifier = Modifier.size(18.dp))
                            Text("Evento de padronização?", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Color(0xFF92400E))
                        }
                        Text("Se este cliente faz parte de um casamento, formatura ou evento em grupo, crie uma padronização para controlar todos os trajes.", fontSize = 12.sp, color = Color(0xFFB45309))
                        Button(
                            onClick = { showPadronizacao = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Default.Star, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Fazer Padronização", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { tentarSalvar() },
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Blue600),
                shape = RoundedCornerShape(8.dp),
            ) {
                if (isSaving) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                else Text(if (isEditando) "Salvar alterações" else "Registrar", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
        }
    }

    if (showPadronizacao) {
        Dialog(
            onDismissRequest = { showPadronizacao = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.White,
            ) {
                com.mrjack.dressflow.ui.screens.padronizacoes.NovaPadronizacaoScreen(
                    vm = androidx.lifecycle.viewmodel.compose.viewModel(),
                    nomeEventoInicial = form.evento,
                    dataEventoInicial = form.dataEvento,
                    onFechar = { showPadronizacao = false },
                )
            }
        }
    }
}

// ─── Card de traje extra ──────────────────────────────────────────────────────

@Composable
fun TrajeExtraCard(
    extra: TrajeExtra,
    index: Int = 0,
    vm: VendasViewModel,
    onUpdate: () -> Unit,
    onRemover: () -> Unit,
    onChanged: (TrajeExtra) -> Unit,
) {
    val titulo = if (extra.sexo == "F") "Vestido ${index + 2}" else "Traje ${index + 2}"
    Surface(color = Gray50, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Gray200), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {

            // ── Título + remover
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(titulo, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Gray700, modifier = Modifier.weight(1f))
                IconButton(onClick = onRemover, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, null, tint = Red500, modifier = Modifier.size(18.dp))
                }
            }

            // ── Menor de Idade
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Checkbox(checked = extra.menorDeIdade, onCheckedChange = { onChanged(extra.copy(menorDeIdade = it, nomeResponsavel = if (!it) "" else extra.nomeResponsavel)) }, modifier = Modifier.size(20.dp))
                Text("Menor de Idade", fontSize = 12.sp, color = Gray700)
            }
            if (extra.menorDeIdade) {
                OutlinedTextField(value = extra.nomeResponsavel, onValueChange = { onChanged(extra.copy(nomeResponsavel = it)) },
                    label = { Text("Nome do responsável (pai/mãe)") }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true, shape = RoundedCornerShape(8.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
            }

            // ── Traje + Sexo
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = extra.traje,
                    onValueChange = { onChanged(extra.copy(traje = it)) },
                    label = { Text(if (extra.sexo == "F") "Código do vestido" else "Traje") },
                    placeholder = { Text(if (extra.sexo == "F") "Ex: V002, V-103" else "Ex: Smoking, Fraque") },
                    modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(8.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("M" to "M", "F" to "F").forEach { (v, label) ->
                        FilterChip(selected = extra.sexo == v, onClick = { onChanged(extra.copy(sexo = v)) }, label = { Text(label, fontSize = 11.sp) })
                    }
                }
            }

            // ── Campos específicos do masculino
            if (extra.sexo == "M") {
                // Itens locados
                Text("ITENS LOCADOS", fontSize = 10.sp, color = Gray500, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf("PALETO" to "Paletó", "COLETE" to "Colete", "CALCA" to "Calça").forEach { (key, label) ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Checkbox(
                                checked = extra.itensLocados.contains(key),
                                onCheckedChange = { checked ->
                                    val next = if (checked) extra.itensLocados + key else extra.itensLocados - key
                                    onChanged(extra.copy(itensLocados = next))
                                },
                                modifier = Modifier.size(20.dp),
                            )
                            Text(label, fontSize = 12.sp, color = Gray700)
                        }
                    }
                }

                // Paletó
                if (extra.itensLocados.contains("PALETO")) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = extra.tamanhoPaleto, onValueChange = { onChanged(extra.copy(tamanhoPaleto = it)) },
                            label = { Text("Paletó") }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(8.dp),
                            placeholder = { Text("Ex: 50, G") })
                        OutlinedTextField(value = extra.tamanhoManga, onValueChange = { onChanged(extra.copy(tamanhoManga = it)) },
                            label = { Text("Manga") }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(8.dp),
                            placeholder = { Text("Ex: 65, M") })
                    }
                }

                // Colete
                if (extra.itensLocados.contains("COLETE")) {
                    OutlinedTextField(value = extra.tamanhoColete, onValueChange = { onChanged(extra.copy(tamanhoColete = it)) },
                        label = { Text("Colete") }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(8.dp),
                        placeholder = { Text("Ex: 50, G") })
                }

                // Calça
                if (extra.itensLocados.contains("CALCA")) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = extra.calca, onValueChange = { onChanged(extra.copy(calca = it)) },
                            label = { Text("Calça") }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(8.dp),
                            placeholder = { Text("Ex: Social") })
                        OutlinedTextField(value = extra.tamanhoCalca, onValueChange = { onChanged(extra.copy(tamanhoCalca = it)) },
                            label = { Text("Tam. calça") }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(8.dp),
                            placeholder = { Text("Ex: 42") })
                    }
                }

                // Outros acessórios
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = extra.camisa, onValueChange = { onChanged(extra.copy(camisa = it)) },
                        label = { Text("Camisa") }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(8.dp),
                        placeholder = { Text("Ex: 42, G") })
                    OutlinedTextField(value = extra.gravata, onValueChange = { onChanged(extra.copy(gravata = it)) },
                        label = { Text("Gravata") }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(8.dp),
                        placeholder = { Text("Ex: Borboleta") })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = extra.cinto, onValueChange = { onChanged(extra.copy(cinto = it)) },
                        label = { Text("Cinto") }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(8.dp),
                        placeholder = { Text("Ex: 90") })
                    OutlinedTextField(value = extra.sapato, onValueChange = { onChanged(extra.copy(sapato = it)) },
                        label = { Text("Sapato") }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(8.dp),
                        placeholder = { Text("Ex: 42") })
                }
                OutlinedTextField(value = extra.abotoadura, onValueChange = { onChanged(extra.copy(abotoadura = it)) },
                    label = { Text("Abotoadura") }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(8.dp),
                    placeholder = { Text("Ex: Prata") })

                // Medidas corporais (M)
                Text("MEDIDAS CORPORAIS", fontSize = 10.sp, color = Gray500, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = extra.torax, onValueChange = { onChanged(extra.copy(torax = it)) },
                        label = { Text("Tórax (cm)") }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(8.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), placeholder = { Text("Ex: 96") })
                    OutlinedTextField(value = extra.abdomen, onValueChange = { onChanged(extra.copy(abdomen = it)) },
                        label = { Text("Abdômen (cm)") }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(8.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), placeholder = { Text("Ex: 90") })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = extra.quadril, onValueChange = { onChanged(extra.copy(quadril = it)) },
                        label = { Text("Quadril (cm)") }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(8.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), placeholder = { Text("Ex: 98") })
                    OutlinedTextField(value = extra.panturrilha, onValueChange = { onChanged(extra.copy(panturrilha = it)) },
                        label = { Text("Panturrilha (cm)") }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(8.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), placeholder = { Text("Ex: 38") })
                }
            }

            // ── Campos específicos do feminino
            if (extra.sexo == "F") {
                Text("MEDIDAS CORPORAIS", fontSize = 10.sp, color = Gray500, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = extra.busto, onValueChange = { onChanged(extra.copy(busto = it)) },
                        label = { Text("Busto (cm)") }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(8.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), placeholder = { Text("Ex: 90") })
                    OutlinedTextField(value = extra.cintura, onValueChange = { onChanged(extra.copy(cintura = it)) },
                        label = { Text("Cintura (cm)") }, modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(8.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), placeholder = { Text("Ex: 70") })
                }
                OutlinedTextField(value = extra.quadril, onValueChange = { onChanged(extra.copy(quadril = it)) },
                    label = { Text("Quadril (cm)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(8.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), placeholder = { Text("Ex: 98") })
            }

            // ── Ajustes
            OutlinedTextField(value = extra.ajustes, onValueChange = { onChanged(extra.copy(ajustes = it)) },
                label = { Text("Ajustes") }, modifier = Modifier.fillMaxWidth(),
                minLines = 2, shape = RoundedCornerShape(8.dp))

            // ── Valor
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = extra.valorBase, onValueChange = { onChanged(extra.copy(valorBase = it)) },
                    label = { Text("Valor (R$)") }, modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true, shape = RoundedCornerShape(8.dp),
                )
                OutlinedTextField(
                    value = extra.desconto, onValueChange = { onChanged(extra.copy(desconto = it)) },
                    label = { Text("Desc. %") }, modifier = Modifier.width(90.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true, shape = RoundedCornerShape(8.dp),
                )
            }

            // ── Valor final
            val valorFinalExtra = run {
                val b = extra.valorBase.replace(",", ".").toDoubleOrNull() ?: 0.0
                val d = extra.desconto.replace(",", ".").toDoubleOrNull() ?: 0.0
                if (b > 0) b * (1.0 - d / 100.0) else 0.0
            }
            if (valorFinalExtra > 0) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Text("Valor final: ", fontSize = 12.sp, color = Gray500)
                    Text("R$ ${"%.2f".format(valorFinalExtra).replace(".", ",")}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Gray900)
                }
            }
        }
    }
}

// ─── Item locado com toggle venda ─────────────────────────────────────────────

@Composable
fun LocacaoItemRow(
    label: String,
    tamanho: String,
    onTamanho: (String) -> Unit,
    isVenda: Boolean,
    onVenda: (Boolean) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    tamanhoExtra: String? = null,
    onTamanhoExtra: ((String) -> Unit)? = null,
    labelTamanhoExtra: String? = null,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        VField(label, tamanho, onTamanho, modifier = Modifier.weight(1f))
        if (tamanhoExtra != null && onTamanhoExtra != null && labelTamanhoExtra != null) {
            VField(labelTamanhoExtra, tamanhoExtra, onTamanhoExtra, modifier = Modifier.weight(1f))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 4.dp)) {
            Text("Venda", fontSize = 10.sp, color = Gray500)
            Checkbox(checked = isVenda, onCheckedChange = onVenda, modifier = Modifier.size(24.dp))
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

// ─── Helpers de formatação e datas ───────────────────────────────────────────

fun parseDateToMillis(date: String): Long? {
    if (date.isBlank()) return null
    return try {
        val p = date.split("-")
        if (p.size == 3) {
            val cal = Calendar.getInstance()
            cal.set(p[0].toInt(), p[1].toInt() - 1, p[2].toInt(), 12, 0, 0)
            cal.timeInMillis
        } else null
    } catch (_: Exception) { null }
}

fun millisToDateStr(millis: Long): String {
    val cal = Calendar.getInstance()
    cal.timeInMillis = millis
    return "%04d-%02d-%02d".format(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
}

fun addMonthsToDate(dateStr: String, months: Int): String = try {
    val p = dateStr.split("-")
    if (p.size == 3) {
        val cal = Calendar.getInstance()
        cal.set(p[0].toInt(), p[1].toInt() - 1, p[2].toInt())
        cal.add(Calendar.MONTH, months)
        "${cal.get(Calendar.DAY_OF_MONTH).toString().padStart(2, '0')}/${(cal.get(Calendar.MONTH) + 1).toString().padStart(2, '0')}/${cal.get(Calendar.YEAR)}"
    } else dateStr
} catch (_: Exception) { dateStr }

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
