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
    val nomeCliente: String?,
    val telefone: String?,
    val dataHora: String?,
    val tipo: String,
    val status: String,
    val observacao: String? = null,
    val tipoCliente: String? = null,
    val vendedorId: Int? = null,
    val dataEvento: String? = null,
    val eventoNome: String? = null,
    val cliente: ClienteResumo? = null,
    val vendedor: VendedorResumo? = null,
)

// ── Tarefa ────────────────────────────────────────────────────────────────────
data class Tarefa(
    val id: Int,
    val titulo: String,
    val descricao: String? = null,
    val prazo: String? = null,
    val recorrencia: String? = null,
    val diaRecorrencia: Int? = null,
    val concluida: Boolean,
    val concluidaHoje: Boolean,
    val concluidaPorNome: String? = null,
    val criadoPorNome: String,
    val criadoPorId: Int,
    val usuarioId: Int,
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
    val autoDestruicao: Boolean = false,
    val conviteStatus: String? = null,
    val conviteDeId: Int? = null,
    val conviteParaId: Int? = null,
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
    val deletaEm: String? = null, // autodestruição
)

data class ConviteSecreto(val canalId: Int, val deId: Int, val deNome: String)

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
    val msgId: String? = null,
    val reaction: String? = null,
    val starred: Boolean = false,
    val pinned: Boolean = false,
)

data class GatewayMensagem(
    val id: Long = 0,
    val telefone: String = "",
    val conteudo: String = "",
    val tipo: String = "texto",
    val direcao: String = "recebida",
    val timestamp: Long = 0,
    val status: String = "",
    val msg_id: String? = null,
) {
    fun toWaMensagem(): WaMensagem {
        val isMedia = tipo in listOf("audio", "image", "video", "document")
        val mappedType = when (tipo) {
            "audio"    -> "ptt"
            "image"    -> "image"
            "video"    -> "video"
            "document" -> "document"
            else       -> "chat"
        }
        return WaMensagem(
            id = msg_id?.takeIf { it.isNotBlank() } ?: "gw-$id",
            body = if (isMedia) null else conteudo.ifBlank { null },
            fromMe = direcao == "enviada",
            timestamp = if (timestamp > 1_000_000_000_000L) timestamp / 1000L else timestamp,
            type = mappedType,
            hasMedia = isMedia,
            filename = null,
            msgId = msg_id?.takeIf { it.isNotBlank() },
        )
    }
}

data class EtiquetaWa(
    val id: String,
    @SerializedName("nome") val name: String = "",
    @SerializedName("cor") val hexColor: String? = null,
)
data class FotoChatResponse(val url: String?)

// ── Galeria ───────────────────────────────────────────────────────────────────
data class GaleriaAlbum(val album: String, val total: Int, val capa: String?)
data class GaleriaFoto(val id: Int, val nome: String, val album: String, val url: String)
data class GaleriaPage(val fotos: List<GaleriaFoto>, val total: Int, val pages: Int, val page: Int)

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

// ── Config Opções (formas pagamento e tipos cliente customizados) ──────────────
data class ConfigOpcao(
    val id: Int,
    val categoria: String,
    val valor: String,
    val label: String,
)

// ── Gateway conversa (todas as conversas do SQLite) ───────────────────────────
data class GatewayConversa(
    val telefone: String = "",
    val ultima_mensagem: String = "",
    val ultimo_timestamp: Long = 0,
    val total_nao_lidas: Int = 0,
    val canal: String = "whatsapp",
    val nome_contato: String? = null,
)

// ── Voucher ────────────────────────────────────────────────────────────────────
data class Voucher(
    val id: Int,
    val percentual: Int,
    val evento: String,
    val validoAte: String,
    val slug: String?,
    val ativo: Boolean?,
)

// ── Proposta ───────────────────────────────────────────────────────────────────
data class PropostaItem(
    val id: Int?,
    val imagemUrl: String,
    val nome: String,
    val descricao: String?,
    val valorDe: Double?,
    val valorPor: Double,
)

data class PropostaOpcional(
    val id: Int?,
    val nome: String,
    val valorDe: Double?,
    val valorPor: Double,
)

data class Proposta(
    val id: Int,
    val turma: String,
    val slug: String?,
    val ativo: Boolean?,
    val itens: List<PropostaItem>?,
    val opcionais: List<PropostaOpcional>?,
)

// ── Integração Google Contatos ──────────────────────────────────────────────────
data class GoogleContactsStatus(
    val connected: Boolean = false,
    val email: String? = null,
)

data class GoogleAuthUrlResponse(
    val url: String = "",
)
