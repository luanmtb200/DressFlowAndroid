package com.mrjack.dressflow.data.model

import com.google.gson.annotations.SerializedName

// ── Auth ──────────────────────────────────────────────────────────────────────
data class LoginRequest(val email: String, val senha: String)
data class LoginResponse(
    val token: String,
    @SerializedName("usuario") val user: UsuarioLogado?,
)
data class UsuarioLogado(
    val id: Int,
    val nome: String,
    val email: String,
    val nivel: String,
    val lojaId: Int,
    val lojaSlug: String?,
    val ativo: Boolean,
    val vendedorId: Int?,
)

// ── Cliente ───────────────────────────────────────────────────────────────────
data class ClientesPage(
    @SerializedName("data") val data: List<Cliente>,
    val total: Int? = null,
    val page: Int? = null,
    val pages: Int? = null,
)

data class Cliente(
    val id: Int,
    val nome: String,
    val telefone: String?,
    val cpf: String?,
    val email: String?,
    val tipoCliente: String?,
    val dataNascimento: String?,
    val cidade: String?,
    val bairro: String?,
    val observacoes: String?,
)

// ── Locação ───────────────────────────────────────────────────────────────────
data class Locacao(
    val id: Int,
    val clienteId: Int? = null,
    val tipo: String,
    val status: String,
    val evento: String?,
    val traje: String,
    val valor: String,
    val formaPagamento: String,
    val parcelas: Int?,
    val dataEvento: String,
    @SerializedName("convertidoEm") val convertidoEm: String?,
    val createdAt: String,
    val cliente: ClienteResumo?,
    val vendedor: VendedorResumo?,
    val tamanhoPaleto: String?,
    val tamanhoManga: String?,
    val camisa: String?,
    val calca: String?,
    val tamanhoCalca: String?,
    val cinto: String?,
    val sapato: String?,
    val abotoadura: String?,
    val gravata: String?,
    val tamanhoColete: String?,
    val torax: String?,
    val abdomen: String?,
    val quadril: String?,
    val panturrilha: String?,
    val busto: String?,
    val cintura: String?,
    val ajustes: String?,
    val observacoes: String?,
    val anotacoes: String?,
    val motivoNaoFechar: String?,
    val motivoCancelamento: String?,
    val boletoPago: Boolean,
    val cancelamentoPendente: Boolean,
    val dataDevolucao: String?,
    val menorDeIdade: Boolean,
    val nomeResponsavel: String?,
    val sexo: String?,
    val valorEntrada: String?,
)

data class ClienteResumo(val id: Int, val nome: String, val telefone: String?, val tipoCliente: String? = null)
data class VendedorResumo(val id: Int, val nome: String)

data class LocacaoListaParams(
    val status: String? = null,
    val tipo: String? = null,
    val busca: String? = null,
)

// ── Agendamento ───────────────────────────────────────────────────────────────
data class Agendamento(
    val id: Int,
    val clienteNome: String,
    val clienteTelefone: String?,
    val tipo: String,
    val status: String,
    val data: String,
    val hora: String?,
    val observacoes: String?,
    val locacaoId: Int?,
    val vendedorId: Int?,
    val usuario: String?,
    val dataEvento: String?,
    val tipoCliente: String?,
    val traje: String?,
)

// ── Padronização ──────────────────────────────────────────────────────────────
data class Padronizacao(
    val id: Int,
    val status: String? = null,
    val tipo: String? = null,
    val tipoEvento: String? = null,
    val ativo: Boolean? = null,
    val nomeEvento: String?,
    val dataEvento: String,
    val totalPadrinhos: Int? = null,
    val numeroPadrinhos: Int? = null,
    val numeroMadrinhas: Int? = null,
    val padrinhosVieram: Int = 0,
    val tipoCapelo: String? = null,
    val observacoes: String? = null,
    val nomeNoivos: String? = null,
    val telefoneNoivos: String? = null,
    val telefoneNoivo: String? = null,
    val telefoneNoiva: String? = null,
    val cerimonialNome: String? = null,
    val cerimonialTelefone: String? = null,
    val corVestidoMadrinhas: String? = null,
    val slug: String? = null,
    val consultor: VendedorResumo? = null,
    val vendedor: VendedorResumo? = null,
    val trajePadrinhos: TrajePadInfo? = null,
    @SerializedName("trajeVestido") val trajeVestido: TrajePadInfo? = null,
    val trajePais: TrajePadInfo? = null,
    val trajeNomePais: String? = null,
    val valorTrajePadrinhos: String? = null,
    val valorTrajePais: String? = null,
    val trajeCompletoPagem: String? = null,
    val valorTrajePagem: String? = null,
    val locacoes: List<PadronizacaoLocacao>? = null,
    val clientes: List<PadClienteInfo>? = null,
    val createdAt: String? = null,
)

data class Traje(
    val id: Int,
    val nome: String,
    val codigo: String,
    val tipo: String,
    val imagemUrl: String? = null,
    val valorAluguel: String? = null,
    val valorVenda: String? = null,
)

data class TrajePadInfo(
    val id: Int,
    val nome: String,
    val imagemUrl: String? = null,
    val valor: String? = null,
)

data class PadClienteInfo(
    val locacaoId: Int,
    val nome: String?,
    val status: String,
    val telefone: String? = null,
    val tipoCliente: String? = null,
    val traje: String? = null,
    val dataEvento: String? = null,
)

data class PadronizacaoLocacao(
    val id: Int,
    val locacaoId: Int,
    val nome: String?,
    val locacao: Locacao?,
)

// ── Dashboard ─────────────────────────────────────────────────────────────────
data class VendasDia(
    val total: Int,
    val totalLocacoes: Int,
    val totalOrcamentos: Int,
    val totalVendas: Int,
    val valorTotal: Double,
)

// ── Vendedor ──────────────────────────────────────────────────────────────────
data class Vendedor(
    val id: Int,
    val nome: String,
    val email: String?,
    val nivel: String,
    val ativo: Boolean,
)

// ── Mural ─────────────────────────────────────────────────────────────────────
data class MuralCanal(
    val id: Int,
    val nome: String,
    val tipo: String,
    val icone: String?,
    val descricao: String?,
    val membros: List<MuralMembro>?,
    val mensagens: List<MuralMensagemResumo>?,
    @SerializedName("_count") val count: MuralCount?,
)

data class MuralMembro(val usuarioId: Int)
data class MuralCount(val mensagens: Int)
data class MuralMensagemResumo(
    val conteudo: String?,
    val autorNome: String,
    val createdAt: String,
    val tipo: String,
)

data class MuralMensagem(
    val id: Int,
    val canalId: Int,
    val autorId: Int,
    val autorNome: String,
    val conteudo: String?,
    val tipo: String,
    val urlArquivo: String?,
    val nomeArquivo: String?,
    val createdAt: String,
)

// ── Traje de Padronização ─────────────────────────────────────────────────────
data class TrajePadronizacao(
    val id: Int,
    val nome: String,
    val descricao: String? = null,
    val imagemUrl: String? = null,
    val valor: String? = null,
)

// ── WhatsApp ──────────────────────────────────────────────────────────────────
data class WhatsAppStatus(val status: String)

data class WaChat(
    val id: String,
    val nome: String?,
    val name: String?,
    val telefone: String?,
    val phoneNumber: String?,
    val unreadCount: Int,
    val isGroup: Boolean?,
    val lastMessage: WaLastMessage?,
) {
    val nomeExibir get() = name ?: nome ?: telefone ?: phoneNumber ?: id
    val telefoneExibir get() = telefone ?: phoneNumber ?: ""
}

data class WaLastMessage(
    val body: String?,
    val fromMe: Boolean,
    val timestamp: Long,
    val type: String?,
)

data class WaMensagem(
    val id: String,
    val body: String?,
    val fromMe: Boolean,
    val timestamp: Long,
    val type: String,
    val hasMedia: Boolean,
    val filename: String?,
)

data class EtiquetaWa(val id: String, val name: String, val hexColor: String?)
data class FotoChatResponse(val url: String?)

// ── Comissão (Meu Painel) ─────────────────────────────────────────────────────
data class ComissaoVendedor(
    val mes: String,
    val totalLocacoes: Int,
    val totalOrcamentos: Int,
    val totalVendas: Int,
    val valorTotal: Double,
    val comissao: Double?,
)

// ── Vendas (Relatórios) ───────────────────────────────────────────────────────
data class VendasMesStats(
    val totalAtendimentos: Int?,
    val totalLocacoes: Int?,
    val totalOrcamentos: Int?,
    val totalVendas: Int?,
)

data class VendasMesData(
    val locacoes: List<Locacao>,
    val stats: VendasMesStats?,
)

data class ProjecaoInfo(
    val valorCalculado: Double?,
    val diasUteisDecorridos: Int?,
    val totalDiasUteisMes: Int?,
)

data class FinanceiroResumo(
    val meta1: Double?,
    val meta2: Double?,
    val metaCustomizada: Boolean?,
    val projecao: ProjecaoInfo?,
)
