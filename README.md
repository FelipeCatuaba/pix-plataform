# Plataforma PIX Assíncrona

Monorepo de uma plataforma PIX que recebe solicitações pela API e processa a comunicação com o parceiro de forma assíncrona.

## Arquitetura

```text
Cliente
  -> pix-api
  -> PostgreSQL: pix_transaction + outbox_event
  -> Outbox Publisher
  -> Kafka: pix.requested
  -> pix-processor
  -> parceiro PIX
  -> PostgreSQL: status + pix_processing_attempt
```

- `pix-api`: recebe `POST /pix`, aplica idempotência e retorna `202 Accepted`.
- `pix-processor`: consome Kafka, chama o parceiro, registra tentativas e atualiza o status.
- PostgreSQL: fonte da verdade para transações, outbox e tentativas.
- Redis: cache auxiliar de idempotência e status.
- Kafka: desacopla a API do processamento.
- WireMock: simula os cenários do parceiro.
- Flyway: cria e evolui o schema.
- OpenTelemetry e Datadog: logs, métricas e traces.

## Estrutura

```text
pix-plataform/
|-- services/
|   |-- pix-api/
|   `-- pix-processor/
|-- infra/
|   |-- flyway/sql/
|   `-- wiremock/mappings/
|-- docs/specs/
|-- docker-compose.yml
`-- pom.xml
```

`infra/` contém apenas arquivos executados pela stack. `docs/specs/` preserva os documentos originais do desafio.

## Decisões principais

### Processamento assíncrono

A API não espera o parceiro. O cliente recebe `PROCESSING` e consulta `GET /pix/{transactionId}` até o resultado final.

```text
PROCESSING -> COMPLETED
PROCESSING -> RETRYING -> COMPLETED
PROCESSING -> RETRYING -> FAILED
PROCESSING -> FAILED
```

### Idempotência

- Redis é uma otimização inicial.
- PostgreSQL possui `UNIQUE(transaction_id)` e decide conflitos de escrita.
- A API trata `DataIntegrityViolationException` como requisição duplicada.
- O processor ignora mensagens de transações com status final.
- Kafka usa `transactionId` como chave para preservar ordenação.

### Outbox, retentativas e DLQ

A transação e o evento são gravados no mesmo commit. O publisher usa `FOR UPDATE SKIP LOCKED`.

Cada chamada ao parceiro é registrada em `pix.pix_processing_attempt`:

- falha intermediária: `RETRYING`;
- sucesso: `COMPLETED`;
- limite atingido: `FAILED` e publicação em `pix.dlq`;
- circuit breaker aberto: `FAILED` e DLQ imediata.

### Redis

- `pix:idempotency:{transactionId}`: TTL de 24 horas.
- `pix:status:{transactionId}`: TTL de 30 segundos.
- O processor invalida o status após atualizações.
- Se Redis estiver indisponível, PostgreSQL continua como fonte da verdade.

## Pré-requisitos

- Docker Desktop com containers Linux.
- Acesso à internet no primeiro build.
- API key do Datadog para enviar telemetria ao Datadog SaaS.

## Configuração e execução

```powershell
Copy-Item .env.example .env
docker compose up --build
```

No `.env`, configure:

```env
DD_API_KEY=sua_api_key
DD_SITE=datadoghq.com
DD_ENV=local
DD_VERSION=1.0.0
```

Para recriar completamente o ambiente:

```powershell
docker compose down -v
docker compose up --build
```

`down -v` remove os dados locais do PostgreSQL, Kafka e Redis.

Para subir apenas dependências:

```powershell
docker compose up -d postgres flyway redis kafka kafka-init kafka-ui wiremock datadog-agent
```

## Acessos locais

| Serviço | Endereço |
|---|---|
| API PIX | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui/index.html |
| Kafka UI | http://localhost:8085 |
| WireMock | http://localhost:8089 |
| Actuator | http://localhost:8080/actuator |

## Banco e Flyway

PostgreSQL cria somente banco e usuário. Flyway executa as migrations de `infra/flyway/sql` antes das aplicações.

A migration inicial cria:

- `pix.pix_transaction`;
- `pix.outbox_event`;
- `pix.pix_processing_attempt`;
- `flyway_schema_history`.

Hibernate usa `ddl-auto=validate`. Novas alterações devem usar migrations:

```text
infra/flyway/sql/V2__descricao_da_alteracao.sql
```

## Kafka

| Tópico | Finalidade | Partições |
|---|---|---:|
| `pix.requested` | Solicitações publicadas pelo outbox. | 3 |
| `pix.dlq` | Falhas finais do processor. | 3 |

O consumer usa commit manual:

```yaml
enable-auto-commit: false
ack-mode: manual_immediate
```

O offset é confirmado após o caso de uso retornar. O Kafka UI permite consultar mensagens, partições, consumer groups, offsets e lag.

## WireMock

O processor chama `POST /partner/pix`. O cenário é selecionado pela `pixKey`:

| `pixKey` | Comportamento |
|---|---|
| `cliente@email.com` | `200 OK` após 2 segundos. |
| `partner-error@pix.test` | `500 Internal Server Error`. |
| `partner-request-timeout@pix.test` | `408 Request Timeout`. |
| `partner-rate-limit@pix.test` | `429 Too Many Requests`. |
| `partner-timeout@pix.test` | Atraso de 10 segundos para acionar timeout. |
| `partner-invalid@pix.test` | `200 OK` com JSON inválido. |

## Cenários de teste

### Saúde

```powershell
curl.exe http://localhost:8080/actuator/health
docker compose ps
```

Resultado esperado: API `UP` e serviços saudáveis.

### Sucesso

```powershell
curl.exe -X POST http://localhost:8080/pix `
  -H "Content-Type: application/json" `
  -H "X-Correlation-Id: teste-sucesso-001" `
  -d '{\"transactionId\":\"tx-sucesso-001\",\"amount\":150.75,\"pixKey\":\"cliente@email.com\",\"description\":\"Cenario de sucesso\"}'
```

```powershell
curl.exe http://localhost:8080/pix/tx-sucesso-001
```

Resultado esperado:

- POST retorna `202 Accepted` e `PROCESSING`;
- status final `COMPLETED`;
- uma tentativa `COMPLETED`;
- outbox `PUBLISHED`;
- nenhuma mensagem na DLQ.

### Idempotência

Repita o POST com o mesmo `transactionId`.

```powershell
docker compose exec postgres psql -U pix -d pixdb -c "select count(*) from pix.pix_transaction where transaction_id = 'tx-sucesso-001';"
docker compose exec postgres psql -U pix -d pixdb -c "select count(*) from pix.outbox_event where aggregate_id = 'tx-sucesso-001';"
```

Ambos devem retornar `1`.

### Requisição inválida

```powershell
curl.exe -i -X POST http://localhost:8080/pix `
  -H "Content-Type: application/json" `
  -d '{\"transactionId\":\"\",\"amount\":0,\"pixKey\":\"\"}'
```

Resultado esperado: HTTP `400` com código `INVALID_REQUEST`.

### Consulta inexistente

```powershell
curl.exe -i http://localhost:8080/pix/transacao-inexistente
```

Resultado esperado: HTTP `404` com código `PIX_NOT_FOUND`.

### Falha, retentativas e DLQ

```powershell
curl.exe -X POST http://localhost:8080/pix `
  -H "Content-Type: application/json" `
  -H "X-Correlation-Id: teste-erro-500" `
  -d '{\"transactionId\":\"tx-erro-500\",\"amount\":50.00,\"pixKey\":\"partner-error@pix.test\",\"description\":\"Erro 500\"}'
```

Resultado esperado:

- tentativas 1 e 2 `RETRYING`;
- tentativa 3 `FAILED`;
- transação `FAILED`;
- mensagem em `pix.dlq`.

Troque a `pixKey` para testar `408`, `429`, timeout e JSON inválido.

### Redis indisponível

```powershell
docker compose stop redis
```

Envie uma transação de sucesso. A API deve continuar usando PostgreSQL.

```powershell
docker compose start redis
```

### Mensagem Kafka duplicada

No Kafka UI, publique novamente em `pix.requested` o payload de uma transação final usando o mesmo `transactionId` como chave.

Resultado esperado:

- mensagem ignorada;
- nenhuma nova chamada ao parceiro;
- nenhuma tentativa adicional.

## Consultas úteis

```powershell
docker compose exec postgres psql -U pix -d pixdb -c "select * from pix.pix_transaction;"
docker compose exec postgres psql -U pix -d pixdb -c "select * from pix.pix_processing_attempt order by created_at;"
docker compose exec postgres psql -U pix -d pixdb -c "select * from pix.outbox_event;"
docker compose exec postgres psql -U pix -d pixdb -c "select * from flyway_schema_history;"
```

## Observabilidade

As aplicações usam OpenTelemetry Java Agent e enviam dados por OTLP para o Datadog Agent.

```powershell
docker compose logs -f pix-api pix-processor
docker compose exec datadog-agent agent status
```

No Datadog, consulte:

- **Logs > Live Tail**;
- **APM > Services**;
- `service:pix-api`;
- `service:pix-processor`;
- `env:local`.

Campos importantes: `transactionId`, `correlationId`, `attempt`, `status` e `errorCode`.

## Métricas

| Métrica | Significado |
|---|---|
| `pix.api.requests` | Requisições recebidas. |
| `pix.api.errors` | Erros da API. |
| `pix.transactions.created` | Transações criadas. |
| `pix.transactions.completed` | Transações concluídas. |
| `pix.transactions.failed` | Transações com falha final. |
| `pix.processor.consumed` | Eventos consumidos. |
| `pix.processor.retries` | Retentativas. |
| `pix.processor.dlq` | Mensagens enviadas para DLQ. |
| `pix.partner.latency` | Latência do parceiro. |
| `pix.partner.errors` | Erros do parceiro. |
| `pix.outbox.pending` | Eventos pendentes selecionados. |
| `pix.outbox.published` | Eventos publicados. |

## Build e validação

```powershell
.\mvnw.cmd clean test
docker compose config --quiet
docker compose ps
```

## Especificações originais

- `docs/specs/Plataforma PIX Assíncrona.pdf`
- `docs/specs/pix-distributed-systems-spec.docx`
