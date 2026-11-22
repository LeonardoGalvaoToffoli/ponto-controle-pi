package com.leonardogalvao.pontocontrole.web;

public record ApiError(String code, String message, String requestId) {
}
