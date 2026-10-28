# ADR 0001 — Avaliação do motor de PDF

Data: 2026-10-28. Estado: investigação; decisão de produção pendente.

## Contexto

A V1 exige renderização, busca/seleção de texto, edição de anotações, preenchimento de AcroForms e salvamento. Nenhuma opção será aprovada somente por renderizar uma página. Custos, licença e manutenção precisam ser compatíveis com a distribuição escolhida.

## Candidatos

| Candidato | Evidência inicial | Pendências |
| --- | --- | --- |
| PdfRenderer do Android | API da plataforma; baseline de renderização sem adicionar motor externo ao aplicativo. | A superfície disponível varia por versão Android; verificar recursos recentes e alternativas nas versões antigas. Não assumir que atende toda a V1 em API 26. |
| PDFBox-Android | README declara Apache 2.0 para o código principal, versão 2.0.27.0, base PDFBox 2.0.27 e API 19+. | Auditar diferenças e correções frente ao upstream, dependências, formatos de imagem e manutenção. A API do GitHub informou último push em 2024-03-18, um sinal para investigação, não prova isolada de abandono. |
| MuPDF | README oficial declara AGPL v3 ou posterior e oferece licenciamento comercial. | Validar APIs Java/Android para todas as operações, empacotamento nativo, ABI, tamanho, suporte a páginas de memória de 16 KB e custos/obrigações de licença. Não incorporado nesta etapa. |

Não é necessário escolher a licença do PDFCrate para testar a alternativa Apache. Também não há autorização implícita para contratar uma licença comercial. A adoção de MuPDF depende de resolver o modelo de distribuição e suas obrigações antes de incorporá-lo.

## Decisão provisória

Criar laboratório Android isolado com PDFBox-Android somente nas dependências dos testes e PdfRenderer como verificação independente de abertura/renderização. Isso permite medir viabilidade sem tornar a versão antiga uma dependência do produto.

O laboratório não aprova o motor, não altera o escopo da V1 e não fixa a versão mínima do aplicativo. API 26 e API 36 são alvos iniciais de teste. O produto ainda será Kotlin/Compose; Java neste módulo reduz configuração durante a investigação da API Java candidata.

## Resultados disponíveis

- Código de testes e CI preparados; execução Android pendente.
- Sem medições de latência ou memória e sem resultados em aparelho físico.
- Sem validação de aparência em Acrobat ou outro leitor de usuário.
- Sem auditoria de vulnerabilidades concluída.
- Proxy local indisponível e SDK Android ausente impediram compilação local; a integração GitHub permaneceu acessível.

## Saída necessária para encerrar M0

1. CI aprovada em ambas as APIs e reprodução em aparelho real.
2. Corpus externo redistribuível, digitalizado, rotacionado, com fontes diversas, anotações e tipos de campo previstos na V1.
3. Validação visual das alterações em outro leitor, incluindo coordenadas, rotação e aparência dos campos.
4. Prova de seleção/busca por posição e das demais ferramentas de anotação.
5. Testes de gravação, falha, permissões e provedores Android.
6. Medições em 100 e 1.000 páginas, PDFs com imagens e memória limitada; definição dos orçamentos.
7. Revisão de segurança e licenças transitivas; escolha do motor e da licença do produto documentada.

## Fontes consultadas

- [PDFBox-Android: README e requisitos](https://github.com/TomRoush/PdfBox-Android/blob/master/README.md).
- [PDFBox-Android: metadados do projeto](https://api.github.com/repos/TomRoush/PdfBox-Android).
- [MuPDF: README e licença](https://github.com/ArtifexSoftware/mupdf/blob/master/README).
- [PdfRenderer: referência oficial a verificar na prova Android](https://developer.android.com/reference/android/graphics/pdf/PdfRenderer).

Os documentos upstream podem mudar; versão 2.0.27.0 está fixada no laboratório. Não interpretar esta comparação como auditoria completa nem como resultado de testes já executados.
