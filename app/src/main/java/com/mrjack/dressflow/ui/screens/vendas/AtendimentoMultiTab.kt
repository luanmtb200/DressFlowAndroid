package com.mrjack.dressflow.ui.screens.vendas

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mrjack.dressflow.data.api.NetworkModule
import com.mrjack.dressflow.data.model.Cliente
import com.mrjack.dressflow.ui.theme.*
import kotlinx.coroutines.delay

// ─── Atendimento simultâneo (abas "+") ─────────────────────────────────────
// Equivalente ao LocacaoMultiTab.tsx do web: permite abrir vários
// formulários de Locação/Venda ao mesmo tempo, cada um com seu próprio
// cliente, alternando entre eles por abas sem perder o que já foi digitado.

data class AtendimentoSession(val id: String, val clienteId: Int, val clienteNome: String)

private var atendimentoSeq = 0
private fun novoAtendimentoId(): String = "atendimento-${atendimentoSeq++}"

@Composable
fun NovaLocacaoMultiTab(
    clienteIdInicial: Int,
    clienteNomeInicial: String,
    eventoInicial: String = "",
    dataEventoInicial: String = "",
    onFecharTudo: () -> Unit,
) {
    val sessions = remember {
        mutableStateListOf(AtendimentoSession(novoAtendimentoId(), clienteIdInicial, clienteNomeInicial))
    }
    val primeiraId = remember { sessions.first().id }
    var activeId by remember { mutableStateOf(sessions.first().id) }
    var showPicker by remember { mutableStateOf(false) }

    fun fecharSessao(id: String) {
        val idx = sessions.indexOfFirst { it.id == id }
        if (idx < 0) return
        if (sessions.size <= 1) { onFecharTudo(); return }
        sessions.removeAt(idx)
        if (activeId == id) activeId = sessions.last().id
    }

    Column(Modifier.fillMaxSize()) {
        Surface(color = Color.White, shadowElevation = 1.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                sessions.forEachIndexed { i, s ->
                    AtendimentoTabChip(
                        index = i + 1,
                        nome = s.clienteNome,
                        ativo = s.id == activeId,
                        removivel = sessions.size > 1,
                        onClick = { activeId = s.id },
                        onRemover = { fecharSessao(s.id) },
                    )
                }
                if (sessions.size < 4) {
                    IconButton(onClick = { showPicker = true }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Add, contentDescription = "Novo atendimento simultâneo", tint = Blue600)
                    }
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxSize()) {
            sessions.forEach { s ->
                key(s.id) {
                    val sessionVm: VendasViewModel = viewModel(key = "loc_form_${s.id}")
                    val visivel = s.id == activeId
                    Box(if (visivel) Modifier.fillMaxSize() else Modifier.size(0.dp)) {
                        LocacaoFormScreen(
                            vm = sessionVm,
                            clienteIdFixo = s.clienteId,
                            clienteNomeFixo = s.clienteNome,
                            eventoInicial = if (s.id == primeiraId) eventoInicial else "",
                            dataEventoInicial = if (s.id == primeiraId) dataEventoInicial else "",
                            onFechar = { fecharSessao(s.id) },
                        )
                    }
                }
            }
        }
    }

    if (showPicker) {
        ClientePickerDialog(
            onSelect = { id, nome ->
                val nova = AtendimentoSession(novoAtendimentoId(), id, nome)
                sessions.add(nova)
                activeId = nova.id
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
}

@Composable
private fun AtendimentoTabChip(
    index: Int,
    nome: String,
    ativo: Boolean,
    removivel: Boolean,
    onClick: () -> Unit,
    onRemover: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (ativo) Blue50 else Color.Transparent,
        border = if (ativo) BorderStroke(1.dp, Blue200) else null,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Surface(shape = RoundedCornerShape(4.dp), color = Blue100) {
                Text(
                    "$index",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Blue700,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
            Text(
                nome.ifBlank { "Atendimento" },
                fontSize = 13.sp,
                fontWeight = if (ativo) FontWeight.SemiBold else FontWeight.Normal,
                color = if (ativo) Blue700 else Gray700,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 100.dp),
            )
            if (removivel) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Fechar atendimento",
                    tint = Gray500,
                    modifier = Modifier.size(14.dp).clickable { onRemover() },
                )
            }
        }
    }
}

@Composable
private fun ClientePickerDialog(onSelect: (Int, String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val api = remember { NetworkModule.provideApiService(context) }
    var busca by remember { mutableStateOf("") }
    var clientes by remember { mutableStateOf<List<Cliente>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(busca) {
        if (busca.length < 2) { clientes = emptyList(); loading = false; return@LaunchedEffect }
        loading = true
        delay(300)
        clientes = try {
            api.listarClientes(busca = busca, limit = 20).body()?.data ?: emptyList()
        } catch (_: Exception) { emptyList() }
        loading = false
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), color = Color.White) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Novo atendimento", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Fechar") }
                }
                OutlinedTextField(
                    value = busca,
                    onValueChange = { busca = it },
                    placeholder = { Text("Buscar cliente por nome ou telefone...") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = { if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                )
                if (!loading && busca.length >= 2 && clientes.isEmpty()) {
                    Text("Nenhum cliente encontrado", fontSize = 12.sp, color = Gray500, modifier = Modifier.padding(vertical = 12.dp))
                }
                LazyColumn(
                    modifier = Modifier.heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(clientes, key = { it.id }) { c ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onSelect(c.id, c.nome) }
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Surface(shape = RoundedCornerShape(50), color = Blue100) {
                                Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        c.nome.firstOrNull()?.uppercase() ?: "?",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Blue700,
                                    )
                                }
                            }
                            Column {
                                Text(c.nome, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Gray900)
                                if (!c.telefone.isNullOrBlank()) {
                                    Text(c.telefone, fontSize = 12.sp, color = Gray500)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
