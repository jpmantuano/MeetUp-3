package com.elastic.meetup.demo3

import javafx.application.Application.launch
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.apache.http.HttpHost
import org.elasticsearch.action.ActionListener
import org.elasticsearch.action.DocWriteRequest
import org.elasticsearch.action.bulk.BackoffPolicy
import org.elasticsearch.action.bulk.BulkProcessor
import org.elasticsearch.action.bulk.BulkRequest
import org.elasticsearch.action.bulk.BulkResponse
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.action.search.ClearScrollRequest
import org.elasticsearch.action.search.SearchRequest
import org.elasticsearch.action.search.SearchScrollRequest
import org.elasticsearch.client.RestClient
import org.elasticsearch.client.RestClientBuilder
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.common.unit.ByteSizeUnit
import org.elasticsearch.common.unit.ByteSizeValue
import org.elasticsearch.common.unit.TimeValue
import org.elasticsearch.common.xcontent.XContentType
import org.elasticsearch.index.query.QueryBuilders.matchAllQuery
import org.elasticsearch.search.Scroll
import org.elasticsearch.search.SearchHit
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.springframework.boot.autoconfigure.SpringBootApplication
import java.io.File
import java.nio.charset.Charset
import java.security.MessageDigest
import java.util.*

@SpringBootApplication
class Demo3Application

fun main(args: Array<String>) {
    export("/home/jpmantuano/Development/Repository/GoLang/workspace/src/openDataCrawler/articles.json")
}

var succeeded = false

fun import(sourceFile: String) {

    val bulkProcessor = bulkProcessor()

    File(sourceFile)
            .inputStream()
            .bufferedReader()
            .use { reader ->
                val document = reader.readText()

                bulkProcessor.add(IndexRequest("demo", "doc", document.sha1())
                        .opType(DocWriteRequest.OpType.INDEX)
                        .source(document, XContentType.JSON))
            }
}

//fun export(targetFile: String) {

//    val bufferedWriter = File(targetFile)
//            .bufferedWriter()
//
//    bufferedWriter
//            .use {
//                while (hitsQueue.isNotEmpty()) {
//                    writer.write(hitsQueue.poll())
//                    writer.newLine()
//            }
//
//
//    bufferedWriter
//            .use { writer ->
//                while (hitsQueue.isNotEmpty()) {
//                    writer.write(hitsQueue.poll())
//                    writer.newLine()
//                }
//            }
//}

val hitsQueue: Queue<String> = LinkedList()

private const val sourceIndex = "article-v5"

fun export(targetFile: String) {

    val bufferedWriter = File(targetFile)
            .bufferedWriter()

    System.out.println("Reading from Elasticsearch...")

    val esClient = esClient()

    val scroll = Scroll(TimeValue.timeValueSeconds(10L))

    val searchRequest = SearchRequest(sourceIndex)
    searchRequest.scroll(scroll)

    val searchSourceBuilder = SearchSourceBuilder()
    searchSourceBuilder.query(matchAllQuery())
    searchSourceBuilder.size(10000)
    searchRequest.source(searchSourceBuilder)

    var searchResponse = esClient.search(searchRequest)
    var scrollId = searchResponse.scrollId

    System.out.println("Reading batch $scrollId")

    var searchHits: Array<SearchHit>? = searchResponse.hits.hits

    bufferedWriter.use { writer ->
        searchHits?.forEach { hit ->
            writer.write(hit.sourceAsString)
            writer.newLine()
        }
    }

    while (searchHits != null && searchHits.isNotEmpty()) {
        val scrollRequest = SearchScrollRequest(scrollId)

        System.out.println("Reading batch $scrollId")

        scrollRequest.scroll(scroll)
        searchResponse = esClient.searchScroll(scrollRequest)
        scrollId = searchResponse.scrollId
        searchHits = searchResponse.hits.hits

        bufferedWriter.use { writer ->
            searchHits?.forEach { hit ->
                writer.write(hit.sourceAsString)
                writer.newLine()
            }
        }
    }

    val clearScrollRequest = ClearScrollRequest()
    clearScrollRequest.addScrollId(scrollId)

    val clearScrollResponse = esClient.clearScroll(clearScrollRequest)

    succeeded = clearScrollResponse.isSucceeded
}

fun esClient(): RestHighLevelClient {
    val timeOut = 10
    val builder: RestClientBuilder = RestClient.builder(HttpHost("localhost", 9200, "http"))

    builder.setRequestConfigCallback(RestClientBuilder.RequestConfigCallback { requestConfigBuilder ->
        requestConfigBuilder.setConnectTimeout(timeOut * 1000)
        requestConfigBuilder.setSocketTimeout(timeOut * 1000)
        requestConfigBuilder.setConnectionRequestTimeout(0)
    })

    return RestHighLevelClient(builder)
}

private val bulkActions = 500
private val bulkSize = 10L
private val flushInterval = 5L
private val backOffDelay = 3L
private val backOffRetry = 10
private val concurrentRequests = 1

fun bulkProcessor(): BulkProcessor {

    val bulkConsumer = { request: BulkRequest, bulkListener: ActionListener<BulkResponse> -> esClient().bulkAsync(request, bulkListener) }

    return BulkProcessor.builder(bulkConsumer, object : BulkProcessor.Listener {
        override fun beforeBulk(executionId: Long, request: BulkRequest) {
//            logger.info("going to execute bulk of {} requests", request.numberOfActions())
        }

        override fun afterBulk(executionId: Long, request: BulkRequest, response: BulkResponse) {
//            logger.info("bulk executed {} failures", if (response.hasFailures()) "with" else "without")
        }

        override fun afterBulk(executionId: Long, request: BulkRequest, failure: Throwable) {
//            logger.info("error while executing bulk", failure)
        }
    }).setBulkActions(bulkActions)
            .setBulkSize(ByteSizeValue(bulkSize, ByteSizeUnit.MB))
            .setConcurrentRequests(concurrentRequests)
            .setFlushInterval(TimeValue.timeValueSeconds(flushInterval))
            .setBackoffPolicy(BackoffPolicy.exponentialBackoff(TimeValue.timeValueSeconds(backOffDelay), backOffRetry))
            .build()
}

private val SECURE_HASH_ALGORITHM_1 = "SHA-1"

fun String.sha1(): String {
    return this.hashWithAlgorithm(SECURE_HASH_ALGORITHM_1)
}

private val UNICODE_TRANSFORMATION_FORMAT_8_BIT = "UTF-8"

private fun String.hashWithAlgorithm(algorithm: String): String {
    val digest = MessageDigest.getInstance(algorithm)
    val bytes = digest.digest(this.toByteArray(Charset.forName(UNICODE_TRANSFORMATION_FORMAT_8_BIT)))
    return bytes.fold("", { str, it -> str + "%02x".format(it) })
}