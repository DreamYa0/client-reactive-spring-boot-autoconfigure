package com.g7.framework.reactive.common.util.http

import cn.hutool.core.util.CharsetUtil
import cn.hutool.http.HttpUtil
import com.g7.framework.framwork.exception.BusinessException
import com.g7.framework.framwork.exception.meta.CommonErrorCode
import com.g7.framwork.common.util.json.JsonUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.nio.charset.StandardCharsets

/**
 * @author dreamyao
 * @title
 * @date 2022/1/30 11:38 下午
 * @since 1.0.0
 */
class ReactiveRest(private val webClient: WebClient) {

    private val logger: Logger = LoggerFactory.getLogger(ReactiveRest::class.java)

    /**
     * POST JSON格式数据请求
     * @param url 请求地址 域名+路径
     * @param body 请求对象
     * @return 返回Json字符串格式结果
     */
    fun <T> post(url: String, body: T): Mono<String> {
        if (logger.isDebugEnabled) {
            logger.debug("start reactive http call url: {} , body: {}", url, body)
        }
        val retrieve = webClient
            .post()
            .uri(url)
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .acceptCharset(StandardCharsets.UTF_8)
            .bodyValue(if (body is String) body else JsonUtils.toJson(body))
            .retrieve()

        return onResultResume(retrieve)
    }

    /**
     * POST JSON格式数据请求
     * @param url 请求地址 域名+路径
     * @param body 请求对象
     * @param headers 请求头
     * @return 返回Json字符串格式结果
     */
    fun <T> post(url: String, body: T, headers: HttpHeaders): Mono<String> {
        if (logger.isDebugEnabled) {
            logger.debug("start reactive http call url is {} , body is {} , headers is {}", url, body, headers)
        }
        val retrieve = webClient
            .post()
            .uri(url)
            .headers { hd ->
                run {
                    hd.setAll(headers.toSingleValueMap())
                }
            }
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .acceptCharset(StandardCharsets.UTF_8)
            .bodyValue(if (body is String) body else JsonUtils.toJson(body))
            .retrieve()

        return onResultResume(retrieve)
    }

    /**
     * POST 表单格式数据请求
     * @param url 请求地址 域名+路径
     * @param body 请求表单数据
     * @return 返回Json字符串格式结果
     */
    fun post(url: String, body: Map<String, Any>): Mono<String> {
        if (logger.isDebugEnabled) {
            logger.debug("start reactive http call url is {} , body is {}", url, body)
        }
        val retrieve = webClient
            .post()
            .uri(url)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .accept(MediaType.APPLICATION_JSON)
            .acceptCharset(StandardCharsets.UTF_8)
            .bodyValue(HttpUtil.toParams(body))
            .retrieve()

        return onResultResume(retrieve)
    }

    /**
     * POST 表单格式数据请求
     * @param url 请求地址 域名+路径
     * @param body 请求表单数据
     * @param headers 请求头
     * @return 返回Json字符串格式结果
     */
    fun post(url: String, body: Map<String, Any>, headers: HttpHeaders): Mono<String> {
        if (logger.isDebugEnabled) {
            logger.debug("start reactive http call url is {} , body is {} , headers is {}", url, body, headers)
        }
        val retrieve = webClient
            .post()
            .uri(url)
            .headers { hd ->
                run {
                    hd.setAll(headers.toSingleValueMap())
                }
            }
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .accept(MediaType.APPLICATION_JSON)
            .acceptCharset(StandardCharsets.UTF_8)
            .bodyValue(HttpUtil.toParams(body))
            .retrieve()

        return onResultResume(retrieve)
    }

    /**
     * GET请求
     * @param url 请求地址 域名+路径
     * @param body 请求数据
     * @return 返回Json字符串格式结果
     */
    fun get(url: String, body: Map<String, Any>): Mono<String> {
        if (logger.isDebugEnabled) {
            logger.debug("start reactive http call url: {} , body: {}", url, body)
        }
        val retrieve = webClient
            .get()
            .uri(HttpUtil.urlWithForm(url, body, CharsetUtil.CHARSET_UTF_8, true))
            .accept(MediaType.APPLICATION_JSON)
            .acceptCharset(StandardCharsets.UTF_8)
            .retrieve()

        return onResultResume(retrieve)
    }

    /**
     * GET请求
     * @param url 请求地址 域名+路径
     * @param body 请求数据
     * @param headers 请求头
     * @return 返回Json字符串格式结果
     */
    fun get(url: String, body: Map<String, Any>, headers: HttpHeaders): Mono<String> {
        if (logger.isDebugEnabled) {
            logger.debug("start reactive http call url is {} , body is {} , headers is {}", url, body, headers)
        }
        val retrieve = webClient
            .get()
            .uri(HttpUtil.urlWithForm(url, body, CharsetUtil.CHARSET_UTF_8, true))
            .headers { hd ->
                run {
                    hd.setAll(headers.toSingleValueMap())
                }
            }
            .accept(MediaType.APPLICATION_JSON)
            .acceptCharset(StandardCharsets.UTF_8)
            .retrieve()

        return onResultResume(retrieve)
    }

    private fun onResultResume(retrieve: WebClient.ResponseSpec): Mono<String> {
        return retrieve
            .onStatus(HttpStatus.NOT_FOUND::equals) {
                val meta = CommonErrorCode.HTTP_REQUEST_ERROR
                meta.msgZhCN = "请求路径不存在，请检查请求地址"
                return@onStatus Mono.error(BusinessException(meta))
            }
            .onStatus({ obj: HttpStatus -> obj.is4xxClientError }) {
                val meta = CommonErrorCode.HTTP_REQUEST_ERROR
                meta.msgZhCN = "认证失败，请联系管理员处理，错误代码：${it.statusCode()}"
                return@onStatus Mono.error(BusinessException(meta))
            }
            .onStatus({ obj: HttpStatus -> obj.is5xxServerError }) {
                val meta = CommonErrorCode.HTTP_REQUEST_ERROR
                meta.msgZhCN = "服务内部错误，请稍后重试或联系技术人员，错误代码：${it.statusCode()}"
                return@onStatus Mono.error(BusinessException(meta))
            }
            .onStatus({ obj: HttpStatus -> obj.isError }) {
                val meta = CommonErrorCode.HTTP_REQUEST_ERROR
                meta.msgZhCN = "未知错误，请稍后重试或联系管理员，错误代码：${it.statusCode()}"
                return@onStatus Mono.error(BusinessException(meta))
            }
            .bodyToMono(String::class.java)
            .onErrorResume {
                logger.error("http client call failed", it)
                return@onErrorResume Mono.error(BusinessException(CommonErrorCode.SYS_ERROR))
            }
            .doOnSuccess {
                if (logger.isDebugEnabled) {
                    logger.debug("reactive http call response is {}", it)
                }
            }
    }
}