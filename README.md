# PDFCrate

Leitor de PDF nativo para Android. Sem anúncios, IA, rastreadores ou conta obrigatória. Processamento local e funcionamento offline.

## APK atual — 0.3.0-alpha

Leitura direta de PDFs no provedor escolhido, recentes com retomada e compartilhamento, sem importar cópias. Leitor com controles compactos, modo de leitura por toque, navegação por gesto, zoom animado e deslocamento com inércia. Android 8.0 ou superior.

A workflow [PDFCrate APK](https://github.com/Azidales/PDFCrate/actions/workflows/android-apk.yml) gera uma release **sem assinatura**. A distribuição instalável é assinada com a chave fixa, mantida fora do repositório. Veja [recentes e assinatura](docs/RECENTES_E_ASSINATURA.md) e [procedimento de assinatura](signing/README.md).

É uma versão inicial de leitura. Anotações, formulários, PDFs com senha e busca no texto fazem parte de etapas posteriores. Veja [funções, instalação e limitações](docs/PRIMEIRO_APK.md).

Veja [interface e fluidez](docs/INTERFACE_E_FLUIDEZ.md) para detalhes de VSync, cache e medição de frames.

## Desenvolvimento

Aplicativo em Kotlin e Jetpack Compose, usando o renderizador do Android. JDK 17, Gradle 8.13 e SDK Android 36.

```sh
gradle :app:assembleDebug :app:lintDebug
gradle :app:connectedDebugAndroidTest
```

O [plano da V1](docs/PLANO_V1.md) descreve o escopo completo e os critérios de aceite. O [laboratório do motor](engine-lab/README.md) avalia separadamente alternativas para futura edição; suas bibliotecas experimentais não entram no APK do leitor.
