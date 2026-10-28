# Laboratório do motor de PDF — M0

Módulo Android experimental, separado do aplicativo final. Não é um leitor instalável para uso diário. O código Java é um teste de integração de bibliotecas; o aplicativo continua planejado em Kotlin e Compose.

## Experimento

Avaliar PDFBox-Android 2.0.27.0 para leitura/escrita e usar o PdfRenderer do Android como uma segunda implementação para abrir e renderizar o resultado. A dependência candidata existe apenas em `androidTestImplementation`.

Os testes geram documentos sintéticos temporários, sem arquivos de usuários, e verificam:

- extração de texto antes e depois de salvar;
- preenchimento de um campo de texto AcroForm e preservação do valor;
- gravação e reabertura de uma anotação de destaque;
- preservação binária do original ao salvar uma cópia;
- rejeição de senha incorreta e abertura com senha correta;
- rejeição de arquivo inválido;
- abertura de 1.000 páginas, extração de texto da última página e renderização dessa página pelo Android;
- presença de pixels escuros na renderização, para detectar uma saída totalmente vazia.

Não validam seleção por coordenadas, aparência exata de anotações/formulários, todas as ferramentas, fontes complexas, PDFs digitalizados, salvamento interrompido, documentos externos ou desempenho. Reabrir pelo mesmo motor não demonstra interoperabilidade completa; renderizar pelo Android é apenas uma verificação independente inicial.

## Executar

Requisitos: JDK 17, Gradle 8.13, Android SDK 36, Build Tools 36.0.0 e emulador/aparelho API 26 ou superior. Configure `ANDROID_HOME` ou `local.properties` sem versionar caminhos locais.

```sh
gradle :engine-lab:assembleDebug :engine-lab:assembleDebugAndroidTest :engine-lab:lintDebug
gradle :engine-lab:connectedDebugAndroidTest
```

Ainda não há Gradle Wrapper. Use a versão indicada; a CI instala essa versão explicitamente. Adicionar o Wrapper oficial com checksum é pendência antes da base do aplicativo.

A workflow `Android engine lab` executa em API 26 e 36 e publica relatórios. Os APKs de teste são artefatos experimentais, sem interface de leitor.

## Estado da validação

Compilação, lint e testes instrumentados passaram nas APIs 26 e 36 na [execução 40625377796](https://github.com/Azidales/PDFCrate/actions/runs/40625377796), após corrigir a instalação do SDK na CI. O ambiente local não dispõe de SDK/Gradle/emulador e o proxy do terminal está indisponível; a validação foi realizada nos runners do GitHub.

Esse resultado valida somente o experimento descrito acima. Não aprova o motor para produção nem encerra o M0. O aplicativo de leitura usa o renderizador da plataforma sem incluir PDFBox-Android.

Consulte a [decisão provisória](../docs/adr/0001-avaliacao-motor-pdf.md) para restrições e próximos passos.
