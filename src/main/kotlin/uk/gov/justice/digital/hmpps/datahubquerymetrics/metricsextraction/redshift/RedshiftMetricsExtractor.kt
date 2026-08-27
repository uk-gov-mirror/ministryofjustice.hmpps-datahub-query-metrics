package uk.gov.justice.digital.hmpps.datahubquerymetrics.metricsextraction.redshift

import aws.sdk.kotlin.services.redshiftdata.RedshiftDataClient
import aws.sdk.kotlin.services.redshiftdata.describeStatement
import aws.sdk.kotlin.services.redshiftdata.executeStatement
import aws.sdk.kotlin.services.redshiftdata.getStatementResult
import aws.sdk.kotlin.services.redshiftdata.model.ResultFormatString
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.datahubquerymetrics.metricsextraction.MetricsExtractor
import uk.gov.justice.digital.hmpps.datahubquerymetrics.metricsextraction.QueryEngine
import uk.gov.justice.digital.hmpps.datahubquerymetrics.metricsextraction.QueryExecutionStatus
import uk.gov.justice.digital.hmpps.datahubquerymetrics.metricsextraction.SingleQueryMetricsInfo
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField

@Service
class RedshiftMetricsExtractor(
  @Value($$"${dpr.redshift.clusterid}") private val clusterIdentifier: String,
  @Value($$"${dpr.redshift.secretarn}") private val secretArn: String,
  @Value($$"${dpr.redshift.database}") private val database: String,
  private val redshiftDataClient: RedshiftDataClient,
) : MetricsExtractor {
  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  override suspend fun extractQueryMetrics(): Collection<SingleQueryMetricsInfo> {
    val result = redshiftDataClient.executeStatement {
      clusterIdentifier = this@RedshiftMetricsExtractor.clusterIdentifier
      secretArn = this@RedshiftMetricsExtractor.secretArn
      database = this@RedshiftMetricsExtractor.database
      resultFormat = ResultFormatString.Json
      clientToken = (1..63).map { (('A'..'Z') + ('a'..'z') + ('0'..'9')).random() }.joinToString("") // 64 digit alphanumeric sequence
      sql = """
        SELECT
          qh.query_id,
          qh.query_text,
          qh.start_time,
          qh.end_time,
          qh.status,
          qh.elapsed_time
        FROM sys_query_history qh
        WHERE 
          qh.start_time >= date_trunc('hour', current_timestamp) - interval '2 hour'
          AND qh.start_time <  date_trunc('hour', current_timestamp) - interval '1 hour'
          AND qh.query_text ILIKE '%CREATE EXTERNAL TABLE reports._%'
          AND qh.query_text NOT ILIKE '%sys_query_history%'
        ORDER BY qh.start_time DESC;
      """.trimIndent()
    }

    // Keep polling until query has finished as it's async
    while (true) {
      val describeResult = redshiftDataClient.describeStatement { id = result.id }
      if (describeResult.error?.isNotEmpty() == true) {
        throw IllegalStateException("Error during describe-statement: ${describeResult.error}")
      }
      if (describeResult.hasResultSet!!) {
        break
      }
    }

    var token: String? = null
    val metrics = mutableListOf<SingleQueryMetricsInfo>()
    while (true) {
      val results = redshiftDataClient.getStatementResult {
        id = result.id
        nextToken = token
      }
      token = results.nextToken

      val redshiftTimestampFormatter = DateTimeFormatterBuilder()
        .appendPattern("yyyy-MM-dd HH:mm:ss")
        .appendFraction(ChronoField.NANO_OF_SECOND, 3, 9, true)
        .toFormatter()
      metrics.addAll(
        results.records.map {
          val queryInfo = extractQueryInfo(it[1].asStringValue())
          SingleQueryMetricsInfo(
            it.first().asLongValue().toString(),
            it[1].asStringValue(),
            LocalDateTime.parse(it[2].asStringValue(), redshiftTimestampFormatter).atZone(ZoneOffset.UTC),
            LocalDateTime.parse(it[3].asStringValue(), redshiftTimestampFormatter).atZone(ZoneOffset.UTC),
            QueryExecutionStatus.fromRedshiftState(it[4].asStringValue()),
            it[5].asLongValue(),
            QueryEngine.REDSHIFT,
            queryInfo.productId,
            queryInfo.productName,
            queryInfo.datasourceName,
            queryInfo.datasourceCatalog,
            queryInfo.datasourceName,
            queryInfo.reportOrDashboardId,
            queryInfo.hasProbationDatasources,
          )
        },
      )
      if (token == null) {
        break
      }
    }

    return metrics
  }
}
