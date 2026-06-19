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

### Teste de carga com k6

O arquivo `tests/k6/pix-load-test.js` funciona como uma suíte funcional e de carga. Cada transação usa um identificador
único e é consultada até alcançar `COMPLETED` ou `FAILED`.

Perfis disponíveis:

| Perfil | Cobertura |
|---|---|
| `full` | Executa todos os cenários funcionais e de resiliência. É o padrão. |
| `smoke` | Saúde, sucesso, idempotência, validação e consulta inexistente. |
| `resilience` | Falhas `500`, `408`, `429`, timeout de leitura e JSON inválido. |
| `load` | Carga gradual somente no fluxo de sucesso, com thresholds de P95. |

A suíte `full` valida:

1. `GET /actuator/health` com status `UP`.
2. Criação assíncrona e conclusão com sucesso.
3. Reenvio idempotente do mesmo `transactionId`.
4. Requisição inválida com `INVALID_REQUEST`.
5. Consulta inexistente com `PIX_NOT_FOUND`.
6. Parceiro retornando HTTP `500`.
7. Parceiro retornando HTTP `408`.
8. Parceiro retornando HTTP `429`.
9. Parceiro excedendo o timeout de leitura.
10. Parceiro retornando JSON inválido.

Falhas do parceiro devem terminar em `FAILED` após as retentativas. O detalhamento de tentativas e a publicação na DLQ
continuam verificáveis no PostgreSQL e no Kafka UI.

Métricas próprias do script:

- `pix_create_duration`: tempo do `POST /pix`;
- `pix_end_to_end_duration`: tempo da criação até `COMPLETED` ou `FAILED`;
- `pix_processing_failures`: proporção de fluxos com resultado inesperado;
- `pix_processing_timeouts`: fluxos que não terminaram dentro do limite;
- `pix_functional_failures`: quantidade de cenários funcionais reprovados;
- `pix_scenarios_executed`: quantidade de cenários executados.

Os thresholds de performance são aplicados somente ao perfil `load`:

- P95 do `POST /pix` menor que `50 ms`, validando a retirada do parceiro do caminho crítico;
- P95 fim a fim menor que `4.000 ms`, acompanhando o fluxo completo até o status final.

Eles podem ser alterados com `API_P95_THRESHOLD_MS` e `END_TO_END_P95_THRESHOLD_MS`.

Com k6 instalado:

```powershell
docker compose restart pix-processor
k6 run -e PROFILE=full .\tests\k6\pix-load-test.js
```

Sem instalar k6, execute pela imagem Docker:

```powershell
docker compose restart pix-processor

docker run --rm -i `
  -v "${PWD}:/workspace" `
  -w /workspace `
  -e BASE_URL=http://host.docker.internal:8080 `
  -e PROFILE=full `
  grafana/k6 run tests/k6/pix-load-test.js
```

Teste rápido:

```powershell
docker compose restart pix-processor
k6 run -e PROFILE=smoke .\tests\k6\pix-load-test.js
```

Teste de resiliência:

```powershell
docker compose restart pix-processor
k6 run -e PROFILE=resilience .\tests\k6\pix-load-test.js
```

Teste de carga com 25 usuários virtuais por 2 minutos:

```powershell
k6 run `
  -e PROFILE=load `
  -e VUS=25 `
  -e DURATION=2m `
  -e API_P95_THRESHOLD_MS=50 `
  -e END_TO_END_P95_THRESHOLD_MS=4000 `
  -e PROCESSING_TIMEOUT_SECONDS=45 `
  .\tests\k6\pix-load-test.js
```

Cada execução gera `tests/k6/summary-<perfil>.json`. Os cenários funcionais são sequenciais para não permitir que as
falhas intencionais interfiram umas nas outras. O perfil de carga é separado para que o circuit breaker e os cenários
de erro não distorçam o P95 do caminho de sucesso.

Reinicie o processor antes de cada perfil funcional para limpar o estado em memória do circuit breaker. Esse comando
não remove volumes nem dados persistidos.

O timeout padrão é de 180 segundos por cenário funcional, tolerando pausas temporárias do Docker Desktop. No perfil
`load`, o padrão é 45 segundos. Use `PROCESSING_TIMEOUT_SECONDS` para sobrescrever o valor.

## Consultas úteis

```powershell
docker compose exec postgres psql -U pix -d pixdb -c "select * from pix.pix_transaction;"
docker compose exec postgres psql -U pix -d pixdb -c "select * from pix.pix_processing_attempt order by created_at;"
docker compose exec postgres psql -U pix -d pixdb -c "select * from pix.outbox_event;"
docker compose exec postgres psql -U pix -d pixdb -c "select * from flyway_schema_history;"
```

## Observabilidade

As aplicações usam o OpenTelemetry Java Agent para enviar métricas e traces por OTLP ao Datadog Agent.
Os logs são coletados diretamente dos containers. O Agent inclui somente `pix-api` e `pix-processor`,
evitando o ruído dos containers de infraestrutura.

```powershell
docker compose logs -f pix-api pix-processor
docker compose exec datadog-agent agent status
```

### Logs de uma transação

Em **Logs > Explorer**, use:

```text
env:local (service:pix-api OR service:pix-processor) @transactionId:tx-sucesso-001
```

Se `transactionId` ainda não estiver criado como facet, pesquise pelo texto:

```text
env:local (service:pix-api OR service:pix-processor) "tx-sucesso-001"
```

Os principais campos são `transactionId`, `correlationId`, `attempt`, `status` e `errorCode`.

### Tempo total do fluxo

A métrica `pix.transaction.end_to_end.duration.seconds` começa na entrada HTTP da `pix-api` e termina após:

- persistência da transação e do Outbox;
- publicação e espera no Kafka;
- consumo pelo `pix-processor`;
- chamadas ao parceiro WireMock e eventuais retentativas;
- atualização final no PostgreSQL;
- acknowledgment do offset Kafka.

Ela não inclui o tempo até um cliente externo executar o próximo polling. Essa percepção do cliente é medida por
`pix_end_to_end_duration` no perfil `load` do k6.

Para visualizá-la:

1. Acesse **Metrics > Explorer**.
2. Pesquise por `pix.transaction.end_to_end.duration.seconds`.
3. Selecione a agregação `avg`, `max` ou `p95`.
4. Filtre por `env:local`.
5. Agrupe por `outcome` para separar `completed` e `failed`.
6. Use o período **Past 15 Minutes** logo após executar os testes.

Consultas úteis:

```text
avg:pix.transaction.end_to_end.duration.seconds{env:local}
max:pix.transaction.end_to_end.duration.seconds{env:local}
p95:pix.transaction.end_to_end.duration.seconds{env:local} by {outcome}
```

Na primeira utilização, o Datadog pode levar alguns minutos para disponibilizar a métrica. Caso `p95` não esteja disponível,
abra **Metrics > Summary**, selecione `pix.transaction.end_to_end.duration.seconds` e habilite percentis.

Para descobrir onde o tempo foi gasto, compare:

```text
p95:pix.outbox.duration.seconds{env:local}
p95:pix.processor.duration.seconds{env:local} by {outcome}
p95:pix.partner.latency{env:local}
```

Sugestão de widgets para o dashboard:

| Widget | Consulta |
|---|---|
| P95 total | `p95:pix.transaction.end_to_end.duration.seconds{env:local} by {outcome}` |
| P95 Outbox | `p95:pix.outbox.duration.seconds{env:local}` |
| P95 processor | `p95:pix.processor.duration.seconds{env:local} by {outcome}` |
| P95 parceiro | `p95:pix.partner.latency{env:local}` |
| Concluídas | `sum:pix.transactions.completed{env:local}.as_count()` |
| Falhas | `sum:pix.transactions.failed{env:local}.as_count()` |
| Retentativas | `sum:pix.processor.retries{env:local}.as_count()` |
| DLQ | `sum:pix.processor.dlq{env:local}.as_count()` |

As durações são publicadas explicitamente em segundos. No widget, selecione a unidade `Time > Second`
ou deixe o Datadog fazer a conversão automática para segundos. Não use `transactionId` como tag de métrica, pois isso
criaria alta cardinalidade; para investigar uma transação individual, use os logs correlacionados.

### Traces

Em **APM > Trace Explorer**, filtre:

```text
env:local (service:pix-api OR service:pix-processor)
```

Os traces mostram cada etapa técnica. Para o tempo completo do fluxo assíncrono, use
`pix.transaction.end_to_end.duration.seconds`.

## Métricas

| Métrica | Significado |
|---|---|
| `pix.transaction.end_to_end.duration.seconds` | Entrada HTTP até conclusão e acknowledgment Kafka, com tag `outcome`. |
| `pix.outbox.duration.seconds` | Entrada HTTP até publicação do evento no Kafka. |
| `pix.processor.duration.seconds` | Consumo Kafka até conclusão e acknowledgment. |
| `pix.partner.latency` | Duração das chamadas ao parceiro WireMock. |
| `pix.api.requests` | Requisições recebidas. |
| `pix.api.errors` | Erros da API. |
| `pix.transactions.created` | Transações criadas. |
| `pix.transactions.completed` | Transações concluídas. |
| `pix.transactions.failed` | Transações com falha final. |
| `pix.processor.consumed` | Eventos consumidos. |
| `pix.processor.retries` | Retentativas. |
| `pix.processor.dlq` | Mensagens enviadas para DLQ. |
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

