# PDFCrate — Plano de construção da V1

Status: escopo inicial acordado; implementação ainda não iniciada.

## Objetivo

Leitor de PDF nativo para Android, rápido e confiável, com leitura, anotações e preenchimento de formulários. Sem anúncios, IA, rastreadores ou conta obrigatória. Processamento local e funcionamento offline.

As funções básicas do Acrobat servem como referência de uso; não há compromisso de paridade completa.

## Escopo funcional e critérios de aceite

| ID | Área | Entrega e aceite |
| --- | --- | --- |
| V1-01 | Abrir arquivos | Abrir pelo seletor Android e por intents de outros aplicativos; solicitar senha quando necessário; explicar senha incorreta, arquivo inválido ou acesso indisponível sem encerrar o aplicativo. |
| V1-02 | Biblioteca | Recentes, favoritos, busca por nome e informações do arquivo. Remover do histórico não apaga o documento. Tratar arquivos movidos e permissões revogadas. |
| V1-03 | Leitura | Zoom por gesto, rolagem contínua e página individual, ajuste à largura/tela e rotação da visualização sem alterar o documento. |
| V1-04 | Navegação | Ir para página, miniaturas, sumário incorporado e retomada da última posição após fechar o aplicativo. |
| V1-05 | Texto | Buscar texto, destacar resultados, avançar/voltar entre ocorrências, selecionar e copiar quando permitido pelo documento. PDFs digitalizados sem camada de texto não terão busca nem seleção. |
| V1-06 | Anotações | Marca-texto, sublinhado, tachado, desenho livre, notas e caixas de texto. Selecionar, editar e apagar anotações compatíveis; desfazer/refazer alterações da sessão. |
| V1-07 | Formulários | Preencher AcroForms compatíveis: texto, caixas de seleção, opções e listas. Valores devem permanecer após salvar e reabrir. XFA fora do escopo. |
| V1-08 | Assinatura simples | Desenhar, posicionar e redimensionar assinatura. Não representa assinatura digital com certificado. Armazenamento reutilizável de assinatura não é necessário na V1. |
| V1-09 | Salvamento | Salvar alterações e salvar cópia. Indicar alterações pendentes e falhas de escrita; permitir nova tentativa ou cópia. Não exibir sucesso antes de concluir a gravação. |
| V1-10 | Compartilhamento | Compartilhar PDF salvo com permissão temporária de leitura pelo Android; imprimir pelo serviço de impressão do sistema. |
| V1-11 | Preferências | Tema claro/escuro da interface, manter tela ligada e preferências de leitura persistidas. Tema escuro da interface não implica recolorir as páginas. |

Documentos podem vir de provedores de armazenamento do Android, mas o aplicativo não implementará sincronização própria. Arquivos remotos precisam ser disponibilizados pelo provedor para uso local.

## Experiência e privacidade

- Tela inicial com recentes, favoritos e ação de abrir documento.
- Leitor com controles essenciais; ferramentas de anotação aparecem conforme a tarefa.
- Busca, páginas/sumário e lista de anotações acessíveis a partir do leitor.
- Diálogos claros para senha, alterações pendentes e falhas de acesso ou salvamento.
- TalkBack nos controles, escala de fonte na interface, contraste e alvos de toque adequados.
- A acessibilidade do conteúdo depende também da estrutura do PDF.
- Sem upload de documentos, telemetria ou SDK de publicidade.
- Acesso por Storage Access Framework; evitar permissão ampla de armazenamento.
- Não persistir senhas de documentos por padrão.
- Compartilhamento e impressão são ações explícitas do usuário.
- Limpar cópias temporárias com ciclo de vida definido, preservando compartilhamentos ativos.
- Tratar PDFs como entrada não confiável; manter dependências atualizadas e não executar JavaScript incorporado, anexos ou ações externas automaticamente.
- Não incorporar documentos privados, senhas ou arquivos de teste sem licença ao repositório.

## Base técnica

- Kotlin e Jetpack Compose.
- Estado de interface separado das operações de documento.
- Coroutines para operações fora da thread principal, com cancelamento.
- Histórico, favoritos e posição de leitura em armazenamento local; preferências em DataStore. Room é candidato para os registros estruturados.
- Motor de PDF atrás de uma interface própria: renderização, texto, busca, anotações, formulários e gravação.
- Recursos do Android para seletor de arquivos, compartilhamento e impressão.
- Cache limitado para páginas e miniaturas; evitar carregar todas as páginas em memória.

Versão mínima do Android, target SDK, dependências e licença do projeto serão registrados após a prova técnica. Não assumir que uma API de renderização sozinha atende edição, formulários e salvamento.

## Marcos de construção

### M0 — Viabilidade do motor e decisões

- [ ] Levantar motores candidatos e verificar manutenção, origem, licença, obrigações de distribuição e eventual custo.
- [ ] Montar corpus de PDFs sintéticos ou redistribuíveis: texto, digitalizado, senha, AcroForms, anotações, páginas rotacionadas e arquivo inválido.
- [ ] Demonstrar renderização, seleção/busca, anotação, formulário e gravação/reabertura em Android.
- [ ] Conferir anotações e formulários exportados em pelo menos outro leitor.
- [ ] Medir abertura, rolagem, busca e memória em documentos de 100 e 1.000 páginas, registrando aparelho e tamanho dos arquivos.
- [ ] Definir orçamento de desempenho com base nessas medições.
- [ ] Registrar decisão do motor, licença do projeto e versões Android em ADR.

Saída: motor validado para todo o escopo ou bloqueios documentados para decisão de produto. Não reduzir o escopo silenciosamente.

### M1 — Fundação e leitor

- [ ] Criar projeto Android, Gradle Wrapper e estrutura inicial.
- [ ] Configurar CI para compilação, lint e testes relevantes; gerar APK de debug.
- [ ] Implementar V1-01, V1-03, V1-04 e V1-05.
- [ ] Tratar rotação de tela, recriação de atividade, retomada e cancelamento de operações.

Saída: abrir, ler, navegar e buscar em PDFs compatíveis.

### M2 — Biblioteca e preferências

- [ ] Implementar V1-02 e V1-11.
- [ ] Persistir acesso quando o provedor permitir.
- [ ] Tratar arquivo ausente, permissão revogada e limpeza de histórico.

Saída: retomar documentos e preferências de forma confiável.

### M3 — Anotações e salvamento

- [ ] Implementar V1-06 e V1-09.
- [ ] Validar coordenadas com zoom, rotação e diferentes dimensões de página.
- [ ] Implementar indicação de alterações pendentes e recuperação de falhas de gravação.
- [ ] Validar preservação de páginas, texto, formulários e anotações existentes.

Saída: anotar, salvar, reabrir e conferir em outro leitor sem perda observada no corpus.

### M4 — Formulários, assinatura e saída

- [ ] Implementar V1-07, V1-08 e V1-10.
- [ ] Conferir aparência e valores após reabertura.
- [ ] Validar arquivos sem permissão de escrita e provedores com diferentes capacidades.

Saída: preencher, assinar visualmente, compartilhar e imprimir.

### M5 — Beta e lançamento

- [ ] Executar matriz de compatibilidade e corrigir bloqueios.
- [ ] Revisar TalkBack, fontes ampliadas, temas e telas pequenas/grandes.
- [ ] Validar modo avião, PDFs grandes, memória limitada, pouco espaço e interrupção de salvamento.
- [ ] Verificar ausência de anúncios, rastreadores e uploads.
- [ ] Preparar versão, notas de lançamento, política de privacidade e avisos de licenças.
- [ ] Definir canal de distribuição e assinatura do release; manter chaves fora do Git.
- [ ] Produzir e validar APK de release no canal escolhido.

Saída: V1 instalável com documentação de limitações conhecidas.

## Estratégia de testes

Testes unitários para estado de alterações, histórico e regras de salvamento; testes de integração do motor para leitura/escrita; testes instrumentados dos fluxos principais. Verificação manual em aparelhos reais para gestos, renderização, impressão e acessibilidade.

Matriz mínima: menor versão Android suportada e versão atual definida no M0; telefone e tablet; retrato e paisagem; documentos com texto, digitalizados, senha, formulários, anotações e erros. Toda correção de perda de dados deve ter uma reprodução e verificação de regressão adequadas.

## Critérios de lançamento

- Fluxo abrir → ler → anotar/preencher → salvar → fechar → reabrir validado.
- Compatibilidade de exportação verificada em outro leitor.
- Original preservado quando uma gravação falha, nos provedores testados; limitações de atomicidade documentadas.
- Sem travamentos recorrentes ou perda de dados conhecida na matriz de testes.
- Orçamentos de desempenho definidos no M0 atendidos.
- Funcionalidades locais operacionais em modo avião.
- CI aprovada e artefato de release instalado e testado.

## Fora da V1

Edição de texto/imagens originais, OCR, conversão para Office, scanner, compressão, juntar/dividir PDFs, reorganizar páginas, sincronização própria em nuvem, colaboração, XFA e assinatura digital com certificado. Sem recursos de IA.

## Organização no GitHub

Este documento é a referência de escopo. Cada marco pode ser dividido em issues com os IDs funcionais acima, critérios de aceite e dependências. Mudanças entram por pull requests com descrição do comportamento e validação. Decisões de arquitetura ficam em docs/adr/.

Próxima entrega: M0, prova técnica e seleção do motor de PDF.
