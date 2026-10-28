# PDFCrate 0.2.0-alpha

Leitor com interface refinada e animações sincronizadas à tela. Android 8.0 (API 26) ou superior.

## Disponível

- Abrir PDFs no seletor Android, por Abrir com ou pelo compartilhamento de outro aplicativo.
- Renderização local com o motor do próprio Android.
- Página anterior/próxima, gesto horizontal na página ajustada e salto para uma página.
- Pinça para zoom, arraste com inércia, toque duplo, zoom animado e ajuste à tela.
- Toque simples para ocultar ou mostrar os controles.
- Miniaturas e cartão para continuar a leitura.
- Biblioteca de cópias locais, busca por nome, favoritos e retomada da última página.
- Compartilhar uma cópia por outro aplicativo.
- Tema claro/escuro conforme o sistema.
- Exemplo de três páginas para experimentar o leitor.
- Sem anúncios, conta, IA, rastreadores ou permissão de internet.

Abrir importa uma cópia para o armazenamento privado do aplicativo. O original não é editado. A cópia continua disponível offline. Remover cópia apaga apenas essa importação; desinstalar o aplicativo remove toda a biblioteca. Limite inicial de importação: 512 MB por arquivo.

## Limitações desta versão

É uma etapa anterior à V1 completa. Não inclui PDFs com senha, busca/seleção de texto, anotações, edição, preenchimento de formulários, impressão, miniaturas ou sumário. Páginas são imagens para leitura; o texto do PDF ainda não é exposto ao TalkBack. Ampliação máxima de 6x, com rasterização limitada a 2.200 pixels no lado maior para controlar memória.

Nenhum motor experimental do laboratório é incorporado ao APK. A decisão de motor para edição permanece em investigação.

## Gerar e instalar

JDK 17, Gradle 8.13 e SDK Android 36:

```sh
gradle :app:assembleDebug :app:lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

A workflow `PDFCrate APK` publica o arquivo instalável no artefato `PDFCrate-0.2.0-alpha-apk` e executa testes instrumentados. O APK é assinado com chave de debug para avaliação; não é uma versão de loja. Builds em outros ambientes podem usar outra chave de debug e exigir desinstalar a versão anterior, o que apaga a biblioteca local. Uma chave estável de distribuição deverá ser configurada antes de releases para usuários.

Os testes também conferem cache, aceleração por hardware e registram tempos de frame durante o zoom. O teste de interface abre o exemplo, muda páginas, salta à última página, aciona zoom/ajuste, retorna à biblioteca, favorita, reabre e remove a cópia. Outro teste confere renderização não vazia, limite de imagem e rejeição de entradas inválidas.
