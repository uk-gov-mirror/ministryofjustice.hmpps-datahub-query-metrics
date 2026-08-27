package uk.gov.justice.digital.hmpps.datahubquerymetrics.metricsextraction

import io.prometheus.metrics.core.metrics.Counter
import io.prometheus.metrics.core.metrics.Histogram
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.datahubquerymetrics.metricsextraction.athena.AthenaMetricsExtractor
import uk.gov.justice.digital.hmpps.datahubquerymetrics.metricsextraction.redshift.RedshiftMetricsExtractor

@Component
class ExtractionJob(
  val athenaMetricsExtractor: AthenaMetricsExtractor,
  val redshiftMetricsExtractor: RedshiftMetricsExtractor,
  val numQueriesCounter: Counter,
  val runtimeHistogram: Histogram,
) {
  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  @Scheduled(cron = "0 0 * * * *")
  fun scheduledFunction() {
    log.info("Starting metrics extraction")
    val results = runBlocking {
      val athenaResults = runCatching {
        athenaMetricsExtractor.extractQueryMetrics()
      }.onFailure { log.error("Error during Athena metrics extraction", it) }.getOrDefault(emptyList())

      val redshiftResults = redshiftMetricsExtractor.extractQueryMetrics()
      redshiftResults.plus(athenaResults)
    }

    results.forEach {
      numQueriesCounter
        .labelValues(it.productId, it.reportOrDashboardId, "${it.productId}::${it.reportOrDashboardId}", it.datasourceName, if (it.hasProbationDatasources) "probation" else "prisons", it.state.name, it.queryEngine.name)
        .inc()

      // If we plot failed executions we'll massively skew results due to them taking very little time
      if (it.state == QueryExecutionStatus.SUCCEEDED) {
        runtimeHistogram.labelValues(it.productId, it.reportOrDashboardId, "${it.productId}::${it.reportOrDashboardId}", it.datasourceName, if (it.hasProbationDatasources) "probation" else "prisons", it.state.name, it.queryEngine.name)
          .observe(it.totalRunTimeMicroseconds / 1_000_000.0)
      }
    }
  }
}
