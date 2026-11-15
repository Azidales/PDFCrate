# Changelog

## 0.5.0-alpha

Profissionalização da 0.4.0-alpha e rolagem contínua.

### Novo

- Rolagem contínua: páginas empilhadas na largura da tela, com inércia, zoom de 1x a 6x sobre o documento inteiro e indicador de página acompanhando a leitura. É o modo padrão; "Rolagem contínua" no menu do leitor alterna para página única, e a escolha fica gravada no aparelho.
- No modo contínuo, setas, salto de página e resultados de busca levam à página (ou ao trecho encontrado); seleção, notas, desenho e assinatura atuam na página tocada.
- Os bitmaps são pedidos só para as páginas visíveis (e uma de cada lado em zoom normal), na largura da tela; acima de 1,5x são refeitos com o dobro da resolução, sem passar de 2.200 px no maior lado.

### Interface

- Leitor com barra superior enxuta: buscar, editar e anotar, salvar (quando há alterações) e menu com texto da página, compartilhar e salvar cópia.
- Barra de busca dedicada, com busca pelo teclado, contador e setas de resultado.
- Modo Editar: paleta com Selecionar, Nota, Desenhar, Assinar, Formulário, Anotações, Desfazer e Refazer. Só aparecem as ferramentas que o aparelho suporta.
- Barra de ações sobre o texto selecionado: copiar, destacar, sublinhar, tachar.
- Dica da ferramenta ativa com Cancelar; Voltar fecha primeiro ferramenta, seleção, busca e modo Editar, depois o documento.
- Texto da página, formulários e anotações em painéis inferiores, com ícones e ações identificadas.
- Confirmações rápidas em snackbar (alterações salvas, cópia salva, texto copiado em Android 12 ou anterior); erros continuam em diálogos com ação de recuperação.
- Diálogo de senha com mostrar/ocultar; ir para página e senha confirmam pelo teclado.
- Sobre mostra a versão real e os recursos disponíveis no aparelho.
- Ícone adaptativo com área segura e versão monocromática; janela inicial sem clarão branco no tema escuro.
- Conjunto de ícones próprio ampliado no mesmo traço; sem novas bibliotecas.

### Código

- Pacotes `pdf`, `library`, `reader` e `ui`; tema em arquivo próprio.
- Todos os textos da interface em `strings.xml`, com plurais.
- Tipos de anotação e falhas de gravação tipados; as mensagens ficam na interface.
- Regras puras extraídas e cobertas por testes JVM: detecção de original alterado fora do app e retenção dos recentes.
- Lint sem avisos nas variantes debug e release.

### Build e distribuição

- Gradle Wrapper 8.13 com checksum da distribuição e catálogo de versões.
- Release com R8 e remoção de recursos não usados: o APK sem assinatura passou de 7,4 MB para 1,5 MB nesta compilação. A camada `pdf`, que escolhe APIs em tempo de execução, é mantida intacta pelas regras do R8.
- `mapping.txt` publicado com a release para ler relatórios de falha.
- Recentes, posições e arquivos de recuperação excluídos de backup e de transferência entre aparelhos.
- CI e scripts usam o Wrapper e executam os testes JVM.
