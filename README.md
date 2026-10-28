# PDFCrate

Leitor de PDF nativo para Android. Sem anúncios, IA, rastreadores ou conta obrigatória. Processamento local e funcionamento offline.

## Primeiro APK — 0.1.0-alpha

Leitura de PDFs com navegação, salto de página, zoom por gesto e botões, biblioteca local, favoritos, busca pelo nome, retomada da última página e compartilhamento. Android 8.0 ou superior.

A workflow [PDFCrate APK](https://github.com/Azidales/PDFCrate/actions/workflows/android-apk.yml) gera o arquivo instalável no artefato `PDFCrate-0.1.0-alpha-apk`. A geração e os testes são verificados por execução; consulte o resultado do commit escolhido antes de instalar.

É uma versão inicial de leitura. Anotações, formulários, PDFs com senha e busca no texto fazem parte de etapas posteriores. Veja [funções, instalação e limitações](docs/PRIMEIRO_APK.md).

## Desenvolvimento

Aplicativo em Kotlin e Jetpack Compose, usando o renderizador do Android. JDK 17, Gradle 8.13 e SDK Android 36.

```sh
gradle :app:assembleDebug :app:lintDebug
gradle :app:connectedDebugAndroidTest
```

O [plano da V1](docs/PLANO_V1.md) descreve o escopo completo e os critérios de aceite. O [laboratório do motor](engine-lab/README.md) avalia separadamente alternativas para futura edição; suas bibliotecas experimentais não entram no APK do leitor.
