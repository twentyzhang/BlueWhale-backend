package com.twentyzhang.bluewhale.exception;

import com.twentyzhang.bluewhale.common.Result;
import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    /** 自定义 code + message */
    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    /** 默认 code 为 400（业务参数/逻辑错误） */
    public BusinessException(String message) {
        super(message);
        this.code = Result.CODE_BAD_REQUEST;
    }
}
