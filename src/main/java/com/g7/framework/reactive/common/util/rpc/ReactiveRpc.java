package com.g7.framework.reactive.common.util.rpc;

import com.alibaba.dubbo.remoting.exchange.ResponseCallback;
import com.alibaba.dubbo.rpc.RpcContext;
import com.alibaba.dubbo.rpc.RpcException;
import com.alibaba.dubbo.rpc.RpcResult;
import com.alibaba.dubbo.rpc.protocol.dubbo.FutureAdapter;
import com.g7.framework.common.dto.PagedResult;
import com.g7.framework.common.dto.Result;
import com.g7.framework.framwork.exception.BusinessException;
import com.g7.framework.framwork.exception.meta.CodeMeta;
import com.g7.framework.framwork.exception.meta.CommonErrorCode;
import com.g7.framwork.common.util.json.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;

import java.util.Objects;

/**
 * @author dreamyao
 * @title
 * @date 2022/1/25 2:25 下午
 * @since 1.0.0
 */
public class ReactiveRpc {

    private static final Logger logger = LoggerFactory.getLogger(ReactiveRpc.class);

    /**
     * dubbo接口响应式调用 需要使用 @Reference(async = true)
     * dubbo接口返回值 新项目接口返回值为 Result<T> 中的 T 老项目就是对应接口的返回值 T ，如 Mono<T> 这样定义返回结果
     * 如果接口是 PagedResult<T> 则需要接收整个PagedResult<T>的值，如：Mono<PagedResult<T>> 这样定义返回结果
     * @param executor 执行dubbo接口调用
     * @param <T>      返回结果类型
     * @return dubbo接口返回值 新项目接口返回值为 Result<T> 中的 T 老项目就是对应接口的返回值
     */
    @SuppressWarnings("unchecked")
    public static <T> Mono<T> call(Executor executor) {
        return Mono.create(sink -> {
            try {
                // 执行异步调用
                executor.execute();
                // 监听结果回调
                ((FutureAdapter<?>) RpcContext.getContext().getFuture()).getFuture().setCallback(
                        new ResponseCallback() {
                            @Override
                            public void done(Object response) {
                                RpcResult rpcResult = (RpcResult) response;
                                if (rpcResult.hasException()) {
                                    caught(rpcResult.getException());
                                    return;
                                }

                                final Object obj = rpcResult.getValue();
                                if (Objects.isNull(obj)) {
                                    // 老服务的dubbo接口有返回void的情况，返回一个null
                                    if (logger.isDebugEnabled()) {
                                        logger.debug("async rpc result is null.");
                                    }
                                    sink.success(null);
                                    return;
                                }

                                if (logger.isDebugEnabled()) {
                                    logger.debug("async rpc call result is {}", JsonUtils.toJson(obj));
                                }

                                if (obj instanceof PagedResult) {
                                    PagedResult<?> page = (PagedResult<?>) obj;
                                    if (Boolean.FALSE.equals(page.isSuccess())) {
                                        onError(sink, page.getCode(),
                                                page.getDescription());
                                    } else {
                                        sink.success((T) obj);
                                    }
                                } else if (obj instanceof Result) {
                                    Result<T> result = (Result<T>) obj;
                                    if (Boolean.FALSE.equals(result.isSuccess())) {
                                        onError(sink, result.getCode(),
                                                result.getDescription());
                                    } else {
                                        sink.success(result.getData());
                                    }

                                } else {
                                    sink.success((T) obj);
                                }
                            }

                            @Override
                            public void caught(Throwable exception) {
                                logger.error("async rpc call failed", exception);
                                sink.error(onRpcException(exception));
                            }
                        });

                if (logger.isDebugEnabled()) {
                    logger.debug("async rpc call complete");
                }

            } catch (Exception e) {
                logger.error("async rpc call exception", e);
                sink.error(onRpcException(e));
            }
        });
    }

    private static <T> void onError(MonoSink<T> monoSink, String code, String description) {
        isError(code, description);
        monoSink.error(new BusinessException(CodeMeta
                .builder()
                .code(code)
                .msgZhCN(description)
                .build()));
    }

    private static void isError(String code, String description) {
        if (StringUtils.hasLength(code)) {
            int codeInt = Integer.parseInt(code);
            // 错误码2000 到 3000 需要告警
            if (2000 <= codeInt && codeInt <= 3000) {
                logger.error("Rpc return result failed , code is [{}] , description is [{}]", code, description);
            } else {
                logger.info("Rpc return result failed , code is [{}] , description is [{}]", code, description);
            }
        }
    }

    private static Throwable onRpcException(Throwable throwable) {
        if (throwable instanceof RpcException) {
            RpcException rpc = (RpcException) throwable;
            BusinessException exception;
            // dubbo 调用异常
            if (rpc.isTimeout()) {
                exception = new BusinessException(CommonErrorCode.BUSY_SERVICE);
            } else if (rpc.isNetwork()) {
                exception = new BusinessException(CommonErrorCode.NETWORK_CONNECT_FAILED);
            } else if (rpc.isSerialization()) {
                exception = new BusinessException(CommonErrorCode.SERIALIZATION_EXCEPTION);
            } else if (rpc.isForbidded()) {
                exception = new BusinessException(CommonErrorCode.FORBIDDEN_EXCEPTION);
            } else {
                exception = new BusinessException(CommonErrorCode.RPC_CALL_EXCEPTION);
            }
            return exception;
        } else if (throwable instanceof Exception) {
            // 处理网货老的dubbo项目抛出的业务异常
            return new BusinessException(buildCodeMeta(throwable.getMessage()));
        } else {
            return throwable;
        }
    }

    private static CodeMeta buildCodeMeta(String message) {

        if (StringUtils.hasText(message) && message.contains("[_") && message.contains("_]")) {

            int begin = message.indexOf("[_") + 2;
            int end = message.indexOf("_]");

            String errorMessage = message.substring(begin, end);
            String[] split = errorMessage.split(":");

            String code = split[0];
            if (split.length == 2) {
                return new CodeMeta(code, "ERROR", split[1]);
            }

            if (split.length > 2) {
                StringBuilder builder = new StringBuilder();
                for (int i = 1; i < split.length; i++) {
                    builder.append(split[i]);
                }
                return new CodeMeta(code, "ERROR", builder.toString());
            }

            return new CodeMeta(CommonErrorCode.REMOTE_SERVICE.getCode(), errorMessage);
        }

        if (Objects.isNull(message)) {
            message = "java.lang.NullPointerException";
        }
        return CommonErrorCode.SYS_ERROR.setMsgZhCN(message);
    }

    @FunctionalInterface
    public interface Executor {
        void execute();
    }
}
