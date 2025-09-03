package com.yourdomain.exception;

import java.util.List;

public record ErrorResponse(int status, String error, String message, String path, String code, List<String> details) { }
