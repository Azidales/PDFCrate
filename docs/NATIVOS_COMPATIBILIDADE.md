# Recursos nativos e compatibilidade — 0.4.0

A decisão do projeto é usar APIs públicas do Android, sem incorporar MuPDF/AGPL nem outro motor de PDF ao aplicativo. O Android 13 do S20 FE é um alvo de leitura e recentes. A versão do Android, sozinha, não garante recursos avançados.

## Capacidades detectadas em execução

| Sistema/módulo | Recursos |
| --- | --- |
| Android 8+ sem módulo moderno | Leitura, zoom, navegação, recentes, retomada, compartilhar e salvar cópia explícita |
| Android 15+ ou Android 12+ com framework-pdf/extensão S 13+ | Senha, busca, seleção/cópia de texto, extração acessível do texto da página, campos de formulário e gravação nativa |
| Framework-pdf/extensão S 18+ | Anotações suportadas, desenho e assinatura desenhada, edição/remoção e desfazer/refazer de anotações |

O app verifica SdkExtensions e escolhe PdfRendererPreV quando o módulo compatível está presente. Nos sistemas novos sem esse backport, usa as APIs modernas de PdfRenderer. A leitura básica permanece disponível nos demais. Ferramentas não oferecidas pelo sistema ficam desabilitadas; Sobre informa a disponibilidade real. O número da versão do Android não deve ser usado para afirmar que um S20 FE específico já possui o módulo.

A compilação usa SDK 36/extensão 19 para referenciar APIs públicas. Isso não aumenta o minSdk 26 nem exige que todos os aparelhos tenham a extensão. Não são usadas APIs ocultas ou reflexão para contornar disponibilidade.

## Texto e senha

A busca navega entre resultados e destaca suas regiões. São mantidos até 5.000 resultados por busca; o contador indica o limite. A pesquisa roda em worker e pode ser cancelada. Não há OCR: imagens sem camada de texto continuam legíveis, mas não ganham texto pesquisável.

A seleção pode começar por toque longo ou pela ferramenta Selecionar texto. O usuário pode arrastar um intervalo e copiar o texto retornado pela seleção nativa. Texto desta página também permite seleção/cópia em um diálogo.

A senha fica somente em memória durante a sessão. Não é colocada em preferências, histórico, argumentos de Activity ou arquivos de recuperação. Cancelar/fechar limpa a referência da sessão. Uma proteção não suportada pode ser indistinguível de senha incorreta para a API; o diálogo explica essa possibilidade. Salvar mantém a proteção por senha.

## Anotações e formulários

As APIs expõem Highlight, FreeText e Stamp. O app usa Highlight para destaque e FreeText para notas/caixas de texto. Desenho, assinatura desenhada, sublinhado e tachado visual usam caminhos vetoriais em Stamp; não são os subtipos semânticos Ink/Underline/StrikeOut. O documento não é convertido em imagens nem perde a camada de texto para exportar esses recursos.

É possível listar e apagar anotações suportadas, editar texto de notas, alterar a cor de destaques e mover/redimensionar. Desfazer/refazer é da sessão de anotações, sem promessa de undo de formulários. A assinatura desenhada não é assinatura digital com certificado. A interface pode visualizar outras anotações existentes sem conseguir editá-las; a gravação nativa deve preservá-las, sujeita ao suporte do sistema.

Os campos nativos de texto, checkbox, rádio, combo e lista são expostos por página. Campos somente leitura não são alterados. Botões de ação, XFA e assinatura digital não são apresentados como edição suportada.

## Gravação

Abrir um PDF nunca importa uma cópia. Salvar cópia é uma ação explícita pelo seletor Android e passa a abrir o documento salvo. Salvar no original prepara uma versão completa editada e um backup antes de substituir o destino. Esses arquivos existem somente por causa da ação explícita de salvar, não como sistema de biblioteca.

Uma gravação bem-sucedida limpa os arquivos de recuperação. Falha/interrupção conserva as versões no armazenamento privado e exibe recuperação na tela de recentes. O usuário pode abrir as alterações, exportar a versão anterior ou descartar a recuperação. O backup não é enviado a servidor, e sua senha não é armazenada. Não existe garantia universal de substituição atômica em provedores Android; se a restauração também falhar, o usuário é alertado e as versões completas continuam preservadas no app.

Quando estatísticas de arquivo estão disponíveis, mudança de tamanho, horário ou inode antes de salvar impede substituir um original alterado externamente. Provedores sem metadados confiáveis não permitem detectar todas as alterações concorrentes. Arquivos somente leitura podem ser editados em memória e exportados por Salvar cópia; a permissão do provedor não é contornada.

## Verificação

A CI inclui Android 8, 13, 15 e Android 16 com imagem da extensão 19. Testes nativos são condicionados às capacidades reais, e os logs registram a extensão e disponibilidade. O conjunto verifica senha errada/correta, seleção por ponto, busca, formulários, exportação ainda criptografada, anotações e round-trip, desfazer/refazer, gravação e recuperação em provedor somente leitura.

PDFBox é usado somente na instrumentação para criar PDFs sintéticos e ler independentemente os resultados. Ele não integra o APK distribuído nem processa documentos reais do usuário. Testes de dispositivo físico, inclusive do S20 FE específico, continuam necessários; não se deve apresentar o emulador Android 13 como validação de todas as versões de firmware Samsung.
