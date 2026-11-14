# PDFCrate

Leitor de PDF nativo para Android. Sem anúncios, IA, rastreadores ou conta obrigatória. Processamento local, sem permissão de internet e funcionamento offline.

## Versão atual: 0.5.0-alpha

Android 8.0 (API 26) ou superior. A 0.5 mantém as funções da 0.4 e profissionaliza interface, código e build. Veja o [CHANGELOG](CHANGELOG.md).

- **Abrir:** PDFs lidos diretamente no provedor escolhido (seletor do Android, "Abrir com" ou compartilhamento), sem importar cópias.
- **Biblioteca:** recentes com busca, retomada da última página, acesso temporário indicado e recuperação de gravação.
- **Leitura:** zoom por pinça, toque duplo e botões, deslocamento com inércia, troca de página por gesto e salto direto de página.
- **Busca:** barra de busca dedicada, contador e navegação entre resultados.
- **Editar e anotar:** paleta com seleção de texto, nota, desenho, assinatura desenhada, formulários, lista de anotações, desfazer e refazer.
- **Salvar:** salvar no original com recuperação, ou salvar cópia.

Senha, texto, formulários e anotações dependem das APIs nativas do aparelho. O app detecta o que está disponível e mostra só as ferramentas suportadas; a tela Sobre informa o resultado. Veja a [matriz de compatibilidade](docs/NATIVOS_COMPATIBILIDADE.md) e as [funções, instalação e limitações](docs/PRIMEIRO_APK.md).

## Distribuição

A workflow [PDFCrate APK](https://github.com/Azidales/PDFCrate/actions/workflows/android-apk.yml) gera uma release minificada **sem assinatura**, acompanhada do `mapping.txt` do R8. O APK instalável é assinado à parte com a chave fixa, mantida fora do repositório. Veja [recentes e assinatura](docs/RECENTES_E_ASSINATURA.md) e o [procedimento de assinatura](signing/README.md).

## Desenvolvimento

Kotlin e Jetpack Compose sobre o renderizador PDF do Android. JDK 17 e SDK Android 36 com extensão 19. O Gradle Wrapper (8.13, com checksum da distribuição) e o catálogo de versões (`gradle/libs.versions.toml`) estão no repositório.

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:lintDebug :app:lintRelease
./gradlew :app:connectedDebugAndroidTest   # requer emulador ou aparelho
```

| Pacote | Responsabilidade |
| --- | --- |
| `pdf` | Acesso às APIs nativas de PDF, sessão do documento, gravação transacional |
| `library` | Histórico de recentes e tela da biblioteca |
| `reader` | Estado do leitor, visualização da página, barras, painéis e diálogos |
| `ui` | Tema e componentes compartilhados |

Os textos da interface ficam em `app/src/main/res/values/strings.xml`.

O [plano da V1](docs/PLANO_V1.md) descreve o escopo completo e os critérios de aceite. O [laboratório do motor](engine-lab/README.md) avalia separadamente alternativas para futura edição; suas bibliotecas experimentais não entram no APK.
