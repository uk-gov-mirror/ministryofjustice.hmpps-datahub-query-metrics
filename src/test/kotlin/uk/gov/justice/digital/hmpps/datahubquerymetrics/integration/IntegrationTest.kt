package uk.gov.justice.digital.hmpps.datahubquerymetrics.integration

import aws.sdk.kotlin.services.athena.AthenaClient
import aws.sdk.kotlin.services.athena.model.BatchGetQueryExecutionResponse
import aws.sdk.kotlin.services.athena.model.ListQueryExecutionsResponse
import aws.sdk.kotlin.services.athena.model.QueryExecution
import aws.sdk.kotlin.services.athena.model.QueryExecutionState
import aws.sdk.kotlin.services.athena.model.QueryExecutionStatistics
import aws.sdk.kotlin.services.athena.model.QueryExecutionStatus
import aws.sdk.kotlin.services.redshiftdata.RedshiftDataClient
import aws.sdk.kotlin.services.redshiftdata.model.DescribeStatementResponse
import aws.sdk.kotlin.services.redshiftdata.model.ExecuteStatementResponse
import aws.sdk.kotlin.services.redshiftdata.model.Field
import aws.sdk.kotlin.services.redshiftdata.model.GetStatementResultResponse
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.util.UriBuilder
import uk.gov.justice.digital.hmpps.datahubquerymetrics.metricsextraction.ExtractionJob
import java.time.Instant
import kotlin.String

@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureWebTestClient
class IntegrationTest {

  @Autowired
  lateinit var webTestClient: WebTestClient

  @Autowired
  lateinit var extractionJob: ExtractionJob

  @MockitoSpyBean
  lateinit var athenaClient: AthenaClient

  @MockitoSpyBean
  lateinit var redshiftDataClient: RedshiftDataClient

  @Test
  fun testExtraction() = runTest {
    generateAthenaResults()
    generateRedshiftResults()

    extractionJob.scheduledFunction()

    val res = webTestClient.get()
      .uri { uriBuilder: UriBuilder ->
        uriBuilder
          .path("/metrics")
          .port(9400)
          .build()
      }
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .returnResult().responseBody.contentToString()
  }

  private suspend fun generateRedshiftResults() {
    doReturn(mock<ExecuteStatementResponse>()).whenever(redshiftDataClient).executeStatement(any())

    val describeResults = mock<DescribeStatementResponse>()
    whenever(describeResults.error).thenReturn(null)
    whenever(describeResults.hasResultSet).thenReturn(true)
    doReturn(describeResults).whenever(redshiftDataClient).describeStatement(any())

    val statementResult = mock<GetStatementResultResponse>()
    val records = listOf(
      generateRedshiftResult(),
      generateRedshiftResult(
        queryExecutionId = 4L,
        completionDateTime = "2026-01-01 00:00:44.000000",
        totalExecutionTimeMillis = (44L * 1000L),
      ),
      generateRedshiftResult(
        queryExecutionId = 3L,
        queryInfo = "/* QUERY_INFO|||dpdId2|||dpdName2|||datasource2|||database2|||catalog2|||reportId2|||reportName2|||false|||NORMAL|||END */",
      ),
    )
    whenever(statementResult.records).thenReturn(records)
    doReturn(statementResult).whenever(redshiftDataClient).getStatementResult(any())
  }

  private fun generateRedshiftResult(
    state: String = "success",
    submissionDateTime: String = "2026-01-01 00:00:00.000000",
    completionDateTime: String = "2026-01-01 00:01:23.000",
    totalExecutionTimeMillis: Long = (83L * 1000L),
    queryInfo: String = "/* QUERY_INFO|||dpdId|||dpdName|||datasource1|||database1|||catalog1|||reportId|||reportName|||false|||NORMAL|||END */",
    queryExecutionId: Long = 234897L,
  ): List<Field> = listOf(
    Field.LongValue(queryExecutionId),
    Field.StringValue(
      """
        $queryInfo
        SELECT * FROM FOO;
      """.trimIndent(),
    ),
    Field.StringValue(submissionDateTime),
    Field.StringValue(completionDateTime),
    Field.StringValue(state),
    Field.LongValue(totalExecutionTimeMillis),
  )

  private suspend fun generateAthenaResults(
    queryIds: List<String> = listOf("abc", "def"),
  ) {
    val listQueryExecutionsResponse = mock<ListQueryExecutionsResponse>()
    whenever(listQueryExecutionsResponse.nextToken).thenReturn(null)
    whenever(listQueryExecutionsResponse.queryExecutionIds).thenReturn(queryIds)
    doReturn(listQueryExecutionsResponse).whenever(athenaClient).listQueryExecutions(any())

    val queryExecutions = listOf(
      generateQueryExecution(),
      generateQueryExecution(
        queryExecutionId = "qeid2",
        completionDateTime = Instant.parse("2026-01-01T00:02:11.000000+01:00"),
        totalExecutionTimeMillis = 131L * 1000L,
      ),
      generateQueryExecution(
        queryExecutionId = "qeid3",
        completionDateTime = Instant.parse("2026-01-01T00:02:41.000000+01:00"),
        totalExecutionTimeMillis = 161L * 1000L,
        queryInfo = "/* QUERY_INFO|||dpdId2|||dpdName2|||datasource2|||database2|||catalog2|||reportId2|||reportName2|||false|||NORMAL|||END */",
      ),
    )
    val batchGetQueryExecutionResponse = mock<BatchGetQueryExecutionResponse>()
    whenever(batchGetQueryExecutionResponse.queryExecutions).thenReturn(queryExecutions)
    doReturn(batchGetQueryExecutionResponse).whenever(athenaClient).batchGetQueryExecution(any())
  }

  private fun generateQueryExecution(
    queryState: QueryExecutionState = QueryExecutionState.Succeeded,
    submissionDateTime: Instant = Instant.parse("2026-01-01T00:00:00.000000+01:00"),
    completionDateTime: Instant = Instant.parse("2026-01-01T00:01:23.000000+01:00"),
    totalExecutionTimeMillis: Long = (83L * 1000L),
    queryInfo: String = "/* QUERY_INFO|||dpdId|||dpdName|||datasource1|||database1|||catalog1|||reportId|||reportName|||false|||NORMAL|||END */",
    queryExecutionId: String = "qeid1",
  ): QueryExecution {
    val queryExecution1Status = mock<QueryExecutionStatus>()
    whenever(queryExecution1Status.state).thenReturn(queryState)
    whenever(queryExecution1Status.submissionDateTime).thenReturn(aws.smithy.kotlin.runtime.time.Instant(submissionDateTime))
    whenever(queryExecution1Status.completionDateTime).thenReturn(aws.smithy.kotlin.runtime.time.Instant(completionDateTime))

    val statistics = mock<QueryExecutionStatistics>()
    whenever(statistics.totalExecutionTimeInMillis).thenReturn(totalExecutionTimeMillis)

    val queryExecution = mock<QueryExecution>()
    whenever(queryExecution.status).thenReturn(queryExecution1Status)
    whenever(queryExecution.query).thenReturn(
      """
      $queryInfo
      SELECT * FROM FOO;
      """.trimIndent(),
    )
    whenever(queryExecution.queryExecutionId).thenReturn(queryExecutionId)
    whenever(queryExecution.statistics).thenReturn(statistics)
    return queryExecution
  }
}
