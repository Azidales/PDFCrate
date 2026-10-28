# Interface e fluidez — 0.2.0

## Interface

- Biblioteca com miniaturas locais, busca, favoritos e cartão para continuar a leitura.
- Ação de abrir arquivo em botão flutuante e ações por documento em menu.
- Nome do arquivo, quantidade de páginas e tamanho, quando disponíveis.
- Leitor com ícones identificados para acessibilidade e barra de navegação compacta.
- Toque simples oculta ou mostra os controles; toque duplo amplia/ajusta.
- Claro e escuro acompanham o sistema. Não há publicidade nem recursos de IA.

## VSync

A interface Android já participa do agendamento de frames da plataforma. Esta versão usa `postInvalidateOnAnimation()` para solicitar desenho na próxima oportunidade de VSync. `ValueAnimator` anima zoom e ajuste pelo relógio do Android, sem loop de tempo fixo; `OverScroller` fornece inércia ao arraste.

Não há trava artificial em 60 Hz, nem solicitação de taxa máxima contínua. O Android escolhe a taxa de atualização conforme tela, energia e políticas do aparelho. Usar VSync não garante 60/90/120 FPS: CPU, GPU, coleta de lixo, resolução e complexidade do PDF também afetam o tempo dos frames.

O zoom transforma um bitmap existente em canvas acelerado. Não rasteriza o PDF a cada gesto. A renderização nativa roda em worker, usa imagens com lado maior de até 2.200 pixels e prepara a imagem para desenho antes de entregá-la à tela.

O cache LRU é limitado por bytes: entre 8 e 24 MiB, conforme o heap. A próxima página na direção de leitura é preparada após uma pausa curta, sem bloquear a interface. Bitmaps compartilhados com a tela não são reciclados ao sair do cache. O fechamento do renderizador ocorre fora da thread de interface.

## Verificação

O teste instrumentado de frames verifica canvas acelerado e coleta `FrameMetrics.TOTAL_DURATION` durante seis animações de zoom. Registra quantidade de frames, Hz reportados pela tela, p50/p95 e frames acima do orçamento calculado pela taxa reportada. Não define aprovação como atingir um FPS fixo em emulador.

A workflow executa testes em API 26 e API 36. Relatórios e saídas adicionais de instrumentação são publicados nos artefatos. Esses dados são de teste com documentos sintéticos, não telemetria do aplicativo.

Para concluir uma avaliação de desempenho de produto, ainda será necessário medir em aparelhos físicos de 60/90/120 Hz, com PDFs reais, rolagem prolongada, pouco espaço/memória e modos de economia de energia. Esta versão não afirma ter sido validada nesses aparelhos.

## Instalação de avaliação

Este APK ainda utiliza assinatura de debug. A chave pode mudar entre execuções da CI; nesse caso o Android exige remover a instalação anterior. Isso apaga as cópias locais, favoritos e posições. Compartilhe antes os PDFs que precise preservar; os originais externos não são apagados. Uma chave estável de distribuição continua pendente antes de um release público.
