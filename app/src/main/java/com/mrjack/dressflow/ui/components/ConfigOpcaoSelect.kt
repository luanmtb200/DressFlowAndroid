package com.mrjack.dressflow.ui.components

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import com.mrjack.dressflow.data.model.ConfigOpcao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

// ─── ViewModel de config-opcoes ───────────────────────────────────────────────

class ConfigOpcoesViewModel(app: Application) : AndroidViewModel(app) {
    private val api = NetworkModule.provideApiService(app)
    val tiposCliente = MutableStateFlow<List<ConfigOpcao>>(emptyList())
    val formasPagamento = MutableStateFlow<List<ConfigOpcao>>(emptyList())
    val isSaving = MutableStateFlow(false)

    init {
        viewModelScope.launch { carregarTudo() }
    }

    private suspend fun carregarTudo() {
        try { tiposCliente.value = api.listarConfigOpcoes("TIPO_CLIENTE").body() ?: emptyList() } catch (_: Exception) {}
        try { formasPagamento.value = api.listarConfigOpcoes("FORMA_PAGAMENTO").body() ?: emptyList() } catch (_: Exception) {}
    }

    fun adicionarTipoCliente(label: String, onDone: (String) -> Unit) {
        viewModelScope.launch {
            isSaving.value = true
            try {
                val opcao = api.criarConfigOpcao(mapOf("categoria" to "TIPO_CLIENTE", "label" to label)).body()
                if (opcao != null) {
                    tiposCliente.value = tiposCliente.value + opcao
                    onDone(opcao.valor)
                }
            } catch (_: Exception) {} finally { isSaving.value = false }
        }
    }

    fun adicionarFormaPagamento(label: String, onDone: (String) -> Unit) {
        viewModelScope.launch {
            isSaving.value = true
            try {
                val opcao = api.criarConfigOpcao(mapOf("categoria" to "FORMA_PAGAMENTO", "label" to label)).body()
                if (opcao != null) {
                    formasPagamento.value = formasPagamento.value + opcao
                    onDone(opcao.label)
                }
            } catch (_: Exception) {} finally { isSaving.value = false }
        }
    }
}

// ─── Tipos padrão ─────────────────────────────────────────────────────────────

val TIPOS_CLIENTE_PADRAO = listOf(
    "NOIVO" to "Noivo", "NOIVA" to "Noiva",
    "PADRINHO" to "Padrinho", "MADRINHA" to "Madrinha",
    "PAGEM" to "Pagem",
    "FORMANDO" to "Formando", "FORMANDA" to "Formanda",
    "PAI_FORMANDO" to "Pai do Formando", "MAE_FORMANDA" to "Mãe da Formanda",
    "DEBUTANTE" to "Debutante", "PRINCIPE_DEBUTANTE" to "Príncipe Debutante",
    "PAI_DEBUTANTE" to "Pai da Debutante", "MAE_DEBUTANTE" to "Mãe da Debutante",
    "OUTROS" to "Outros",
)

val FORMAS_PAGAMENTO_PADRAO = listOf(
    "Dinheiro", "PIX", "Cartão de Crédito", "Cartão de Débito", "Boleto", "Cheque", "Parceria", "Permuta", "Outro"
)

// ─── Componente TipoClienteSelect ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TipoClienteSelect(
    value: String,
    onValueChange: (String) -> Unit,
    podeAdicionar: Boolean,
    vm: ConfigOpcoesViewModel = viewModel(),
    modifier: Modifier = Modifier,
) {
    val customTipos by vm.tiposCliente.collectAsState()
    val isSaving by vm.isSaving.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var novoLabel by remember { mutableStateOf("") }

    val todos = TIPOS_CLIENTE_PADRAO + customTipos.map { it.valor to it.label }
    val displayLabel = todos.find { it.first == value }?.second ?: if (value.isNotBlank()) value else "— Selecionar —"

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = displayLabel, onValueChange = {}, readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    shape = RoundedCornerShape(10.dp), singleLine = true,
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(text = { Text("— Selecionar —") }, onClick = { onValueChange(""); expanded = false })
                    todos.forEach { (v, l) ->
                        DropdownMenuItem(text = { Text(l) }, onClick = { onValueChange(v); expanded = false })
                    }
                }
            }
            if (podeAdicionar) {
                IconButton(onClick = { showAdd = !showAdd }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Add, null, tint = if (showAdd) Color(0xFFEF4444) else Color(0xFF2563EB))
                }
            }
        }
        if (showAdd && podeAdicionar) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = novoLabel, onValueChange = { novoLabel = it },
                    placeholder = { Text("Ex: Pai do Noivo", fontSize = 13.sp) },
                    modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(8.dp),
                )
                Button(
                    onClick = {
                        if (novoLabel.isNotBlank()) {
                            vm.adicionarTipoCliente(novoLabel.trim()) { valor ->
                                onValueChange(valor)
                                novoLabel = ""
                                showAdd = false
                            }
                        }
                    },
                    enabled = !isSaving && novoLabel.isNotBlank(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                ) { Text(if (isSaving) "..." else "Salvar", fontSize = 12.sp) }
            }
        }
    }
}

// ─── Componente FormaPagamentoSelect ─────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormaPagamentoSelect(
    value: String,
    onValueChange: (String) -> Unit,
    podeAdicionar: Boolean,
    vm: ConfigOpcoesViewModel = viewModel(),
    modifier: Modifier = Modifier,
) {
    val customFormas by vm.formasPagamento.collectAsState()
    val isSaving by vm.isSaving.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var novoLabel by remember { mutableStateOf("") }

    val todas = FORMAS_PAGAMENTO_PADRAO + customFormas.map { it.label }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = value.ifBlank { "— Selecionar —" }, onValueChange = {}, readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    shape = RoundedCornerShape(10.dp), singleLine = true,
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(text = { Text("— Selecionar —") }, onClick = { onValueChange(""); expanded = false })
                    todas.forEach { f ->
                        DropdownMenuItem(text = { Text(f) }, onClick = { onValueChange(f); expanded = false })
                    }
                }
            }
            if (podeAdicionar) {
                IconButton(onClick = { showAdd = !showAdd }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Add, null, tint = if (showAdd) Color(0xFFEF4444) else Color(0xFF2563EB))
                }
            }
        }
        if (showAdd && podeAdicionar) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = novoLabel, onValueChange = { novoLabel = it },
                    placeholder = { Text("Ex: Transferência", fontSize = 13.sp) },
                    modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(8.dp),
                )
                Button(
                    onClick = {
                        if (novoLabel.isNotBlank()) {
                            vm.adicionarFormaPagamento(novoLabel.trim()) { label ->
                                onValueChange(label)
                                novoLabel = ""
                                showAdd = false
                            }
                        }
                    },
                    enabled = !isSaving && novoLabel.isNotBlank(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                ) { Text(if (isSaving) "..." else "Salvar", fontSize = 12.sp) }
            }
        }
    }
}
