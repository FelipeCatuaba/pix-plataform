# Decisões arquiteturais e trade-offs

Este documento registra as decisões estratégicas da plataforma, suas motivações, consequências e alternativas.

## Processamento assíncrono

A API persiste a solicitação e retorna `202 Accepted` sem aguardar o parceiro PIX.

```text
PROCESSING -> COMPLETED
PROCESSING -> RETRYING -> COMPLETED
PROCESSING -> RETRYING -> FAILED
PROCESSING -> FAILED
```

Motivação:

- retirar a latência e a disponibilidade do parceiro do caminho crítico da API;
- absorver picos com Kafka;
- permitir retentativas sem manter a conexão HTTP aberta.

Trade-offs:

- o cliente precisa consultar `GET /pix/{transactionId}`;
- consistência é eventual;
- operação e diagnóstico envolvem mais componentes.

Uma chamada síncrona seria mais simples, mas acoplaria o tempo de resposta e a disponibilidade da API ao parceiro.

## PostgreSQL como fonte da verdade

PostgreSQL mantém transações, eventos de outbox e tentativas de processamento. Redis é somente uma otimização.

Motivação:

- garantir consistência e durabilidade;
- manter o sistema funcional quando o cache estiver indisponível;
- resolver conflitos de idempotência com `UNIQUE(transaction_id)`.

Trade-off: leituras que não estiverem em cache atingem o banco relacional.

## Idempotência em múltiplas camadas

A idempotência é protegida por:

1. consulta rápida ao Redis;
2. busca da transação no PostgreSQL;
3. restrição única em `transaction_id`;
4. tratamento de `DataIntegrityViolationException`;
5. verificação do estado final no consumidor Kafka.

Kafka usa `transactionId` como chave para preservar a ordem dos eventos da mesma transação.

Motivação: nenhuma camada isolada cobre simultaneamente concorrência, mensagens duplicadas e indisponibilidade do cache.

Trade-off: existe redundância intencional entre cache, banco e consumidor.

## Transactional Outbox

A transação PIX e o evento são persistidos no mesmo commit. Um publisher separado envia os eventos pendentes ao Kafka.

O publisher usa `FOR UPDATE SKIP LOCKED`, permitindo concorrência entre instâncias sem selecionar o mesmo evento.

Motivação: evitar o dual write entre PostgreSQL e Kafka.

Trade-offs:

- a publicação não é instantânea;
- o publisher precisa ser monitorado;
- Kafka ainda pode receber duplicatas se ocorrer falha depois do envio e antes da marcação como publicado.

Por isso, o consumidor também é idempotente.

## Retentativas, circuit breaker e DLQ

Cada chamada ao parceiro é registrada em `pix.pix_processing_attempt`.

- falha intermediária: `RETRYING`;
- sucesso: `COMPLETED`;
- limite de tentativas: `FAILED` e envio para `pix.dlq`;
- circuit breaker aberto: falha imediata e envio para DLQ.

Motivação:

- tolerar falhas transitórias;
- evitar pressão contínua sobre um parceiro degradado;
- preservar falhas definitivas para análise ou reprocessamento.

Trade-offs:

- retentativas aumentam o tempo total do fluxo;
- parâmetros agressivos podem amplificar a falha do parceiro;
- a DLQ exige processo operacional de inspeção e reprocessamento.

## Confirmação manual do Kafka

O consumidor usa:

```yaml
enable-auto-commit: false
ack-mode: manual_immediate
```

O offset é confirmado depois que o caso de uso retorna.

Motivação: não confirmar uma mensagem antes de seu processamento.

Trade-off: uma falha antes do acknowledgment pode provocar nova entrega, o que reforça a necessidade de idempotência.

## Redis degradável

Chaves utilizadas:

- `pix:idempotency:{transactionId}`: TTL de 24 horas;
- `pix:status:{transactionId}`: TTL de 30 segundos.

O processor invalida o cache de status depois de atualizar a transação. Quando Redis está indisponível, PostgreSQL continua atendendo como fonte da verdade.

Trade-off: a estratégia favorece disponibilidade, aceitando mais carga no banco e possíveis leituras temporariamente desatualizadas dentro do TTL.

## Schema controlado por Flyway

Flyway executa as migrations de `infra/flyway/sql`. Hibernate usa `ddl-auto=validate`.

Novas alterações devem ser versionadas:

```text
infra/flyway/sql/V2__descricao_da_alteracao.sql
```

Motivação: tornar mudanças de banco reproduzíveis, auditáveis e independentes da inicialização da aplicação.

## Observabilidade

OpenTelemetry envia telemetria ao Datadog Agent. Logs carregam identificadores de transação e correlação; métricas evitam `transactionId` como tag para não criar alta cardinalidade.

O tempo assíncrono completo é medido explicitamente por `pix.transaction.end_to_end.duration.seconds`, pois traces HTTP isolados não representam toda a jornada entre API, outbox, Kafka e processor.

