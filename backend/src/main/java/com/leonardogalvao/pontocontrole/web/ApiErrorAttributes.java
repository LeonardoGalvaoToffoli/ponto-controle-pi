package com.leonardogalvao.pontocontrole.web;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;

import java.util.UUID;

public final class ApiErrorAttributes {

    public static final String ERROR_CODE_ATTRIBUTE = ApiErrorAttributes.class.getName() + ".errorCode";

    private ApiErrorAttributes() {
    }

    public static String requestId() {
        String requestId = MDC.get(RequestIdFilter.MDC_KEY);
        return requestId != null ? requestId : UUID.randomUUID().toString();
    }

    public static void setErrorCode(HttpServletRequest request, String code) {
        request.setAttribute(ERROR_CODE_ATTRIBUTE, code);
    }
}
