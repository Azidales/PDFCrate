# PDFCrate 0.4.0-alpha

Android 8.0 (API 26) ou superior. Interface em Kotlin/Compose e leitura com PdfRenderer do Android.

## Disponível

- Abertura direta pelo seletor ou por outro aplicativo, sem importar cópias.
- Recentes por URI, busca pelo nome e retomada da última página.
- Navegação, salto de página, zoom por pinça/toque duplo/botões e arraste com inércia.
- Toque simples para ocultar ou mostrar os controles.
- Compartilhamento do documento escolhido, com concessão temporária de leitura.
- Mensagem de acesso temporário e recuperação de arquivo indisponível.
- Claro/escuro conforme o sistema. Sem anúncios, IA, conta, rastreadores ou internet.

Remover dos recentes não apaga o arquivo. O provedor controla a disponibilidade; arquivos de nuvem podem depender de rede ou da opção offline oferecida pelo próprio provedor. Não há cópia local automática de fallback.

## Atualizações e instalação

A distribuição final usa chave fixa; ela fica fora do Git. A CI pública gera uma release sem assinatura, que precisa passar pelo procedimento em signing/README.md antes de instalar. Não confundir o artefato unsigned com o APK final assinado.

A transição das antigas builds com chave debug aleatória exige uma última desinstalação. Isso apaga os dados internos dessas builds. A partir da nova identidade, manter a mesma chave e aumentar versionCode permite atualizar sem apagar recentes. Guardar backup privado da chave e da senha é indispensável.

## Limitações

Recursos de senha, texto, edição e formulários dependem das APIs nativas presentes no dispositivo; veja docs/NATIVOS_COMPATIBILIDADE.md. Ainda não inclui OCR, impressão, rolagem contínua, miniaturas de páginas, sumário ou assinatura digital com certificado. Zoom limitado a 6x com imagem de até 2.200 pixels no lado maior.

Alguns provedores só oferecem streams sem acesso aleatório e não funcionam com leitura direta por PdfRenderer. O app informa a limitação; não importa o arquivo silenciosamente.

## Desenvolvimento e validação

JDK 17, Gradle 8.13 e SDK Android 36:

```sh
gradle :app:assembleDebug :app:assembleRelease :app:lintDebug :app:lintRelease
gradle :app:connectedDebugAndroidTest
```

A CI verifica os fluxos de leitura em APIs 26 e 36, acesso ao provedor original, substituição/remoção do arquivo externo, cache, tempos de frame e uma atualização real de pacote por adb install -r. Relatórios detalhados acompanham cada execução. O motor experimental PDFBox permanece restrito aos testes do laboratório, fora do APK do leitor.
