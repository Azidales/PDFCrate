# PDFCrate

Leitor de PDF nativo para Android. Sem anúncios, IA, rastreadores ou conta obrigatória. Processamento local e funcionamento offline.

## APK atual — 0.4.0-alpha

Leitura direta de PDFs no provedor escolhido, recentes com retomada e compartilhamento, sem importar cópias. Leitor com controles compactos, modo de leitura por toque, navegação por gesto, zoom animado e deslocamento com inércia. Android 8.0 ou superior.

A workflow [PDFCrate APK](https://github.com/Azidales/PDFCrate/actions/workflows/android-apk.yml) gera uma release **sem assinatura**. A distribuição instalável é assinada com a chave fixa, mantida fora do repositório. Veja [recentes e assinatura](docs/RECENTES_E_ASSINATURA.md) e [procedimento de assinatura](signing/README.md).

Busca e seleção de texto, PDFs com senha, formulários e anotações usam as APIs nativas disponíveis no aparelho. A leitura básica continua no Android 8+, incluindo Android 13. Veja [funções, instalação e limitações](docs/PRIMEIRO_APK.md).

Veja [interface e fluidez](docs/INTERFACE_E_FLUIDEZ.md) para detalhes de VSync, cache e medição de frames.

Texto, senha, formulários e anotações são habilitados conforme as APIs nativas presentes no sistema. Veja a [matriz de compatibilidade, incluindo Android 13](docs/NATIVOS_COMPATIBILIDADE.md).

## Desenvolvimento

Aplicativo em Kotlin e Jetpack Compose, usando o renderizador do Android. JDK 17, Gradle 8.13 e SDK Android 36 com extensão 19.

```sh
gradle :app:assembleDebug :app:lintDebug
gradle :app:connectedDebugAndroidTest
```

O [plano da V1](docs/PLANO_V1.md) descreve o escopo completo e os critérios de aceite. O [laboratório do motor](engine-lab/README.md) avalia separadamente alternativas para futura edição; suas bibliotecas experimentais não entram no APK do leitor.
