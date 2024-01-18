package com.g7.framework.reactive.common.util.http

import com.g7.framework.common.dto.Result
import com.g7.framework.framwork.exception.BusinessException
import com.g7.framework.framwork.exception.meta.CodeMeta
import reactor.core.publisher.Mono
import java.util.function.Supplier

/**
 * @author dreamyao
 * @title
 * @date 2022/3/16 3:01 下午
 * @since 1.0.0
 */
// 对失败结果进行默认抛异常处理
fun <T : Result<*>> Mono<T>.onErrorResume(): Mono<T> = this.flatMap { result ->
    if (result.isSuccess) {
        Mono.just(result)
    } else {
        Mono.error(BusinessException(CodeMeta.builder().code(result.code).msgZhCN(result.description).build()))
    }
}