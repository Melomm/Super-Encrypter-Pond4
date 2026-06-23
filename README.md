# Super Encrypter

## Vídeo de explicação


---

## Proposta

O Super Encrypter é um aplicativo Android nativo para criar pastas criptografadas usando arquivos como senha, em vez de senhas digitadas em texto.

A ideia é simples: o usuário escolhe um arquivo-chave, cria uma pasta segura e passa a guardar arquivos dentro dela. Para abrir a pasta depois, ele precisa selecionar novamente o mesmo arquivo-chave. O app não salva a senha, não salva a chave AES e não guarda uma cópia do arquivo usado como chave.

Além da criptografia local, o projeto também inclui GeoLock por GPS, compartilhamento de pastas `.supervault`, histórico de ações, notificações locais e integração com VirusTotal por meio de um backend próprio.

## Problema abordado

Senhas em texto são limitantes e muitas vezes previsíveis. A proposta parte da ideia de que usuários deveriam ter outros métodos para usar como senha, permitindo que qualquer arquivo possa funcionar como chave de acesso.

Com isso, encontrar padrões de senha se torna muito mais difícil, pois o usuário pode escolher algo que não parece uma senha tradicional. Um arquivo comum, como uma imagem, documento ou outro item pessoal, pode proteger uma pasta sem revelar claramente que aquele arquivo também é uma chave.

## Solução proposta

A solução usa um arquivo escolhido pelo usuário como material de entrada para gerar a chave criptográfica da pasta. O app calcula um identificador do arquivo-chave, deriva a chave de criptografia com PBKDF2 e usa AES-GCM para proteger os arquivos importados.

Cada pasta possui seus próprios metadados, salt e arquivos criptografados. O conteúdo original importado não é apagado do celular do usuário, e a cópia protegida fica no armazenamento privado do aplicativo.

O backend em Node.js fica responsável por conversar com o VirusTotal. Assim, a chave da API externa não fica dentro do APK Android. O backend também mantém o histórico dos scans manuais, permitindo mostrar os últimos resultados no aplicativo.

## Etapas da solução

1. O usuário cria uma pasta segura e escolhe um arquivo-chave pelo seletor nativo do Android.
2. O app calcula o hash do arquivo-chave, consulta o backend e salva apenas metadados necessários, como fingerprint, salt e configurações da pasta.
3. Caso o GeoLock esteja ativo, o app registra a localização atual e usa GPS para validar o acesso posteriormente.
4. O usuário importa arquivos para a pasta. O app criptografa cada item localmente com AES-GCM e IV próprio.
5. Para acessar uma pasta trancada, o usuário seleciona novamente o arquivo-chave correto. Se houver GeoLock, a localização também precisa estar dentro do raio configurado.
6. O usuário pode visualizar arquivos suportados, selecionar itens em lote, mover arquivos entre pastas, apagar arquivos e compartilhar conteúdo usando recursos nativos do Android.
7. Scans manuais de vírus podem ser feitos pela aba de scan ou pelo menu de arquivos dentro de uma pasta. O app envia o arquivo ao backend, e o backend consulta o VirusTotal.
8. O histórico dos scans fica disponível na aba de histórico, com opção para limpar os registros.

## Tecnologias utilizadas

- **Kotlin** para o aplicativo Android.
- **Jetpack Compose** e **Material 3** para a interface.
- **Navigation Compose** para navegação entre telas.
- **Room** para banco de dados local no dispositivo.
- **AES-GCM** para criptografia dos arquivos.
- **PBKDF2** para derivação da chave a partir do arquivo-chave.
- **Storage Access Framework** para seleção de arquivos do celular.
- **FileProvider** e Android Sharesheet para compartilhamento.
- **Fused Location Provider** para uso de GPS no GeoLock.
- **Notificações locais** do Android para feedback de ações importantes.
- **Node.js + Express** para o backend próprio.
- **Multer** para receber arquivos enviados pelo app.
- **VirusTotal API** como API externa de análise de arquivos.

## Requisitos do projeto

| Requisito | Como foi atendido |
| --- | --- |
| Implementação mobile | O projeto é um aplicativo Android nativo feito em Kotlin com Jetpack Compose. |
| Múltiplas telas | O app possui tela inicial, criação de pasta, destrancamento, detalhes da pasta, visualização de arquivo, scan manual e histórico. |
| Backend funcional | O app se comunica com um backend próprio em Node.js/Express, usado para scans no VirusTotal e histórico de scans manuais. |
| Banco de dados | O app usa Room localmente para persistir pastas, arquivos e histórico interno. O backend também persiste histórico de scans em `backend/data/scan-history.json`. |
| API externa | A API do VirusTotal é consumida pelo backend para consultar hashes e analisar arquivos enviados manualmente. |
| Sistema de notificações | O app usa notificações locais para informar eventos como criação segura de hash, arquivos protegidos, exportações, bloqueios por GeoLock e conclusão de scans do VirusTotal quando o app está em segundo plano. |
| Compartilhamento | O app permite compartilhar pastas exportadas como `.supervault` e também compartilhar arquivos selecionados usando o compartilhamento nativo do Android. |
| Uso de hardware do celular | O app usa GPS/localização para validar o GeoLock das pastas protegidas. |

## Como executar

### 1. Backend

Entre na pasta do backend:

```bash
cd backend
npm install
```

Crie o arquivo `.env` com a chave do VirusTotal:

```env
VIRUSTOTAL_API_KEY=sua_chave_do_virustotal
PORT=3000
```

Inicie o servidor:

```bash
npm start
```

Endpoints principais:

- `GET /health`
- `POST /scan`
- `POST /scan-file-jobs`
- `GET /scan-file-jobs/:jobId`
- `GET /scan-history`
- `DELETE /scan-history`

O histórico de scans manuais é salvo em:

```text
backend/data/scan-history.json
```

### 2. Aplicativo Android

Abra o projeto no Android Studio, sincronize o Gradle e execute o módulo `app` em um emulador ou celular físico.

Também é possível validar a compilação pelo terminal:

```powershell
.\gradlew.bat :app:compileDebugKotlin
```

Para emulador Android, a URL padrão do backend deve apontar para:

```text
http://10.0.2.2:3000
```

Para celular físico, use o IP do computador na mesma rede, por exemplo:

```text
http://192.168.0.20:3000
```

A configuração fica em:

```text
app/src/main/java/com/superencrypter/util/AppConstants.kt
```

## Fluxo de uso

1. Inicie o backend com a chave do VirusTotal configurada.
2. Abra o app Android.
3. Crie uma pasta segura e selecione um arquivo-chave.
4. Importe arquivos para a pasta.
5. Tranque a pasta.
6. Destranque usando o mesmo arquivo-chave.
7. Use a aba de scan manual para enviar arquivos ao VirusTotal.
8. Consulte o histórico de scans na segunda aba da área de scan.

## Observações de segurança

- A chave da API do VirusTotal fica apenas no backend.
- O Android não chama o VirusTotal diretamente.
- O app não salva o arquivo-chave original.
- O app não salva a chave AES derivada.
- Cada arquivo criptografado usa IV próprio.
- Arquivos descriptografados usados para preview ou compartilhamento devem ser tratados como temporários.
- A exportação `.supervault` não inclui chave AES, arquivo-chave nem arquivos descriptografados.

## Considerações finais sobre a solução desenvolvida

O Super Encrypter entrega um MVP funcional de cofre mobile com criptografia local, autenticação por arquivo-chave, GeoLock, backend próprio, banco de dados, API externa, notificações e compartilhamento nativo.

A solução ainda pode evoluir em pontos como recuperação de acesso, testes automatizados mais amplos e endurecimento do fluxo de arquivos temporários. Mesmo assim, a base desenvolvida já demonstra o funcionamento completo da proposta: proteger pastas no celular usando arquivos como senha e manter integrações sensíveis fora do aplicativo.
