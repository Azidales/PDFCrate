# Identidade estável de assinatura

`certificate.sha256` identifica a chave de distribuição do PDFCrate. É um dado público. A chave privada e sua senha não ficam no Git, nos logs nem nos artefatos públicos da CI.

A CI compila e testa versões debug e gera `app-release-unsigned.apk`. Esse artefato NÃO é instalável até ser assinado. A assinatura final usa `scripts/sign-apk.py`, que exige a chave existente e rejeita um certificado diferente. Não há geração automática nem fallback para debug.

```sh
python3 scripts/sign-apk.py app-release-unsigned.apk PDFCrate.apk \
  --keystore /caminho/privado/pdfcrate-release.p12 \
  --password-file /caminho/privado/keystore-password.txt \
  --build-tools /caminho/android/build-tools/36.0.0
```

Nesta sessão, a chave está em `/workspace/PDFCrate-signing`, fora do checkout. O proprietário precisa guardar o `.p12` e sua senha de recuperação em armazenamento privado durável. O workspace não substitui um backup. Perder a chave impede atualizar instalações assinadas com ela.

O conector GitHub disponível não permite configurar Actions Secrets. Por isso a CI pública entrega uma release sem assinatura e a assinatura é feita no ambiente privado. Automatizar uma release assinada exigirá configurar os segredos por um canal autorizado, sem versionar a chave.

A transição das antigas builds com chave debug aleatória para esta identidade exige uma última desinstalação. Depois, APKs assinados com esta chave e versionCode maior podem atualizar o aplicativo preservando seus dados. Uma futura publicação na loja deve preservar essa identidade ou seguir a política de assinatura da loja.
