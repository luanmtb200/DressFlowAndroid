package com.mrjack.dressflow.ui.screens.meupanel

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mrjack.dressflow.data.api.NetworkModule
import com.mrjack.dressflow.data.model.Solicitacao
import com.mrjack.dressflow.data.model.Vendedor
import com.mrjack.dressflow.ui.components.DatePickerField
import com.mrjack.dressflow.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ── ViewModel ─────────────────────────────────────────────────────────────────

class SolicitacoesViewModel(app: Application) : AndroidViewModel(app) {
    private val api = NetworkModule.provideApiService(app)

    val solicitacoes = MutableStateFlow<List<Solicitacao>>(emptyList())
    val usuarios = MutableStateFlow<List<Vendedor>>(emptyList())
    val isLoading = MutableStateFlow(false)
    val isSaving = MutableStateFlow(false)
    val erro = MutableStateFlow<String?>(null)

    fun carregar() {
        viewModelScope.launch {
            isLoading.value = true
            erro.value = null
            try {
                solicitacoes.value = withContext(Dispatchers.IO) {
                    api.listarSolicitacoes().body() ?: emptyList()
                }
            } catch (e: Exception) {
                erro.value = "Erro ao carregar solicitações: ${e.message}"
            } finally {
                isLoading.value = false
            }
        }
    }

    fun carregarUsuarios() {
        viewModelScope.launch {
            try {
                usuarios.value = withContext(Dispatchers.IO) {
                    api.listarUsuariosParaSolicitacao().body() ?: emptyList()
                }
            } catch (_: Exception) {}
        }
    }

    fun criar(
        titulo: String,
        descricao: String?,
        destinatarioId: Int,
        destinatarioNome: String,
        prazo: String?,
        horarioLimite: String?,
    ) {
        viewModelScope.launch {
            isSaving.value = true
            erro.value = null
            try {
                val body = mapOf<String, Any?>(
                    "titulo" to titulo,
                    "descricao" to descricao,
                    "destinatarioId" to destinatarioId,
                    "destinatarioNome" to destinatarioNome,
                    "prazo" to prazo,
                    "horarioLimite" to horarioLimite,
                )
                withContext(Dispatchers.IO) { api.criarSolicitacao(body) }
                carregar()
            } catch (e: Exception) {
                erro.value = "Erro ao criar solicitação: ${e.message}"
            } finally {
                isSaving.value = false
            }
        }
    }
}

// ── Tela principal ────────────────────────────────────────────────────────────

@Composable
fun SolicitacoesTab(vm: SolicitacoesViewModel = viewModel()) {
    val solicitacoes by vm.solicitacoes.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val isSaving by vm.isSaving.collectAsState()
    val erro by vm.erro.collectAsState()

    var showDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.carregar()
        vm.carregarUsuarios()
    }

    val pendentes = remember(solicitacoes) { solicitacoes.filter { it.status == "PENDENTE" } }
    val aprovadas = remember(solicitacoes) { solicitacoes.filter { it.status == "APROVADA" } }
    val rejeitadas = remember(solicitacoes) { solicitacoes.filter { it.status == "REJEITADA" } }

    Box(Modifier.fillMaxSize()) {
        if (isLoading && solicitacoes.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Blue600)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (erro != null) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Red100),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text(erro!!, modifier = Modifier.padding(12.dp), color = Red500, fontSize = 13.sp)
                        }
                    }
                }

                if (solicitacoes.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                            Text("Nenhuma solicitação. Toque em + para criar.", color = Gray500, fontSize = 14.sp)
                        }
                    }
                }

                if (pendentes.isNotEmpty()) {
                    item {
                        SectionHeader("Pendentes", pendentes.size)
                    }
                    items(pendentes, key = { it.id }) { sol ->
                        SolicitacaoCard(sol)
                    }
                }

                if (aprovadas.isNotEmpty()) {
                    item {
                        SectionHeader("Aprovadas", aprovadas.size)
                    }
                    items(aprovadas, key = { "a${it.id}" }) { sol ->
                        SolicitacaoCard(sol)
                    }
                }

                if (rejeitadas.isNotEmpty()) {
                    item {
                        SectionHeader("Rejeitadas", rejeitadas.size)
                    }
                    items(rejeitadas, key = { "r${it.id}" }) { sol ->
                        SolicitacaoCard(sol)
                    }
                }

                item { Spacer(Modifier.height(64.dp)) }
            }
        }

        FloatingActionButton(
            onClick = { showDialog = true },
            containerColor = Blue600,
            contentColor = Color.White,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = "Nova solicitação")
        }
    }

    if (showDialog) {
        NovaSolicitacaoDialog(
            vm = vm,
            isSaving = isSaving,
            onDismiss = { showDialog = false },
            onCriado = { showDialog = false },
        )
    }
}

// ── Componentes auxiliares ────────────────────────────────────────────────────

@Composable
private fun SectionHeader(titulo: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Gray50)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(titulo.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Gray500, letterSpacing = 0.5.sp)
            Box(
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Gray200).padding(horizontal = 6.dp, vertical = 1.dp),
            ) {
                Text("$count", fontSize = 10.sp, color = Gray700, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun SolicitacaoCard(sol: Solicitacao) {
    val (statusBg, statusFg, statusLabel) = when (sol.status) {
        "PENDENTE" -> Triple(Amber100, Amber500, "Pendente")
        "APROVADA" -> Triple(Green100, Green600, "Aprovada")
        "REJEITADA" -> Triple(Red100, Red500, "Rejeitada")
        else -> Triple(Gray100, Gray500, sol.status)
    }

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Gray200),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(sol.titulo, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Gray900, modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(statusBg).padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(statusLabel, fontSize = 10.sp, color = statusFg, fontWeight = FontWeight.Medium)
                }
            }

            if (!sol.descricao.isNullOrBlank()) {
                Text(sol.descricao, fontSize = 12.sp, color = Gray500, maxLines = 2)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0xFFE9D5FF)).padding(horizontal = 6.dp, vertical = 1.dp)) {
                    Text("Para: ${sol.destinatarioNome}", fontSize = 10.sp, color = Color(0xFF7C3AED), fontWeight = FontWeight.Medium)
                }
                sol.prazo?.let { prazo ->
                    val fmt = try { "${prazo.substring(8, 10)}/${prazo.substring(5, 7)}" } catch (_: Exception) { prazo }
                    Text("Prazo: $fmt", fontSize = 10.sp, color = Gray500)
                }
                sol.horarioLimite?.let { hora ->
                    Text("Horário: $hora", fontSize = 10.sp, color = Gray500)
                }
            }

            if (!sol.motivoRejeicao.isNullOrBlank()) {
                Text("Motivo: ${sol.motivoRejeicao}", fontSize = 11.sp, color = Red500)
            }
        }
    }
}

// ── Diálogo: nova solicitação ────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NovaSolicitacaoDialog(
    vm: SolicitacoesViewModel,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onCriado: () -> Unit,
) {
    val usuarios by vm.usuarios.collectAsState()
    val destinatarios = remember(usuarios) {
        usuarios.filter { it.nivel in listOf("GERENCIA", "DIRETOR") && it.ativo }
    }

    var titulo by remember { mutableStateOf("") }
    var descricao by remember { mutableStateOf("") }
    var prazo by remember { mutableStateOf("") }
    var horarioLimite by remember { mutableStateOf("") }
    var destinatarioId by remember { mutableIntStateOf(0) }
    var destinatarioNome by remember { mutableStateOf("") }
    var expandido by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nova solicitação", fontWeight = FontWeight.Bold, fontSize = 17.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Destinatário
                Text("Destinatário *", fontSize = 12.sp, color = Gray500, fontWeight = FontWeight.Medium)
                ExposedDropdownMenuBox(expanded = expandido, onExpandedChange = { expandido = it }) {
                    OutlinedTextField(
                        value = destinatarioNome.ifBlank { "Selecionar..." },
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expandido) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                    )
                    ExposedDropdownMenu(expanded = expandido, onDismissRequest = { expandido = false }) {
                        destinatarios.forEach { u ->
                            DropdownMenuItem(
                                text = { Text("${u.nome} (${u.nivel})") },
                                onClick = {
                                    destinatarioId = u.id
                                    destinatarioNome = u.nome
                                    expandido = false
                                },
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = titulo,
                    onValueChange = { titulo = it },
                    label = { Text("Título *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                )
                OutlinedTextField(
                    value = descricao,
                    onValueChange = { descricao = it },
                    label = { Text("Descrição") },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                )
                DatePickerField(
                    label = "Prazo (opcional)",
                    value = prazo,
                    onDateSelected = { prazo = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = horarioLimite,
                    onValueChange = { v ->
                        // Formata automaticamente como HH:MM
                        val digits = v.filter { it.isDigit() }.take(4)
                        horarioLimite = when {
                            digits.length <= 2 -> digits
                            else -> "${digits.take(2)}:${digits.drop(2)}"
                        }
                    },
                    label = { Text("Horário limite (HH:MM)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    placeholder = { Text("Ex: 14:30") },
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    vm.criar(
                        titulo.trim(),
                        descricao.trim().ifBlank { null },
                        destinatarioId,
                        destinatarioNome,
                        prazo.ifBlank { null },
                        horarioLimite.ifBlank { null },
                    )
                    onCriado()
                },
                enabled = titulo.isNotBlank() && destinatarioId > 0 && !isSaving,
                colors = ButtonDefaults.buttonColors(containerColor = Blue600),
            ) {
                if (isSaving) CircularProgressIndicator(Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                else Text("Enviar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}
