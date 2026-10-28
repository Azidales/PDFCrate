# Recentes e assinatura — 0.3.0

## Abrir o arquivo original

O PDFCrate abre uma URI de conteúdo pelo seletor Android ou por Abrir com/Compartilhar. O descritor do provedor é entregue diretamente ao PdfRenderer. Não há importação, duplicação do PDF, limite artificial de tamanho de cópia nem fallback que copie o documento para os arquivos privados do app.

O histórico guarda nome, URI, última página, contagem de páginas e tamanho quando fornecido. Não testa cada URI em segundo plano e não remove itens só por estarem temporariamente indisponíveis. Abrir novamente a mesma URI retoma a posição e atualiza os dados do documento.

O seletor solicita acesso de leitura, escrita e persistência. O app retém somente as permissões concedidas pelo provedor. Arquivos somente leitura continuam legíveis. Links recebidos por outros aplicativos podem ter acesso temporário; isso é indicado nos recentes. A concessão de acesso não contorna senha nem restrições do PDF.

Quando o arquivo é movido, removido, fica offline ou perde permissão, o app oferece abri-lo novamente pelo seletor. Ao localizar um novo endereço pelo fluxo de recuperação, mantém a posição anterior dentro dos limites da nova contagem de páginas.

Remover dos recentes só remove metadados e libera permissões persistentes retidas para a referência. Não apaga ou altera o arquivo original. A lista mantém até 50 referências. O arquivo de demonstração é gerado pelo próprio app no cache; não é uma importação de documento do usuário.

Alguns provedores retornam apenas streams sem acesso aleatório. O renderizador exige um descritor seekable; nesses casos o app explica que não consegue ler diretamente e não cria uma cópia silenciosa. O provedor escolhido pode ter seu próprio cache, fora do controle do PDFCrate.

## Atualizações

A CI gera uma release sem assinatura e APKs debug apenas para testes. A versão entregue ao usuário é assinada separadamente com a chave fixa identificada em `signing/certificate.sha256`. O script recusa outra chave, não gera chaves automaticamente e não entrega debug como se fosse distribuição final.

A chave privada está fora do Git e da CI pública. O proprietário deve manter backup privado do `.p12` e de sua senha. Sem essa chave não será possível continuar atualizando instalações assinadas por ela. Veja signing/README.md.

A mudança das versões antigas com chaves debug aleatórias exige uma última desinstalação. Ela apaga os dados internos antigos; preservar documentos necessários antes disso. A partir desta identidade fixa, manter o mesmo pacote, a mesma chave e aumentar versionCode permite atualização com os dados preservados.

## Verificação

Os testes de leitura usam um provedor separado, contido somente no APK de instrumentação. Verificam abertura sem criar PDFs privados, observação de substituição do original, indisponibilidade após remoção do original e preservação dos bytes do provedor ao esquecer um recente.

O teste de atualização grava histórico, instala um APK de versionCode maior com adb install -r e confirma a posição e o acesso ao documento. As versões debug do teste compartilham a mesma chave no job; a identidade dos APKs de distribuição é verificada separadamente com o certificado público fixado.

Busca, seleção, senha, anotações, salvamento e formulários são as próximas etapas. A escolha de um motor de edição ainda precisa resolver manutenção, licença e suporte às versões Android.
