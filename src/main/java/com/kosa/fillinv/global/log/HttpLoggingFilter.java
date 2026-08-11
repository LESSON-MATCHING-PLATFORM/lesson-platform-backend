package com.kosa.fillinv.global.log;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class HttpLoggingFilter extends OncePerRequestFilter {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String REQUEST_ID_MDC_KEY = "requestId";

    private static final List<String> EXCLUDE_URI = List.of(
            "/docs/**",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/swagger-resources/**",
            "/v3/api-docs/**"
    );

    private final AntPathMatcher antPathMatcher = new AntPathMatcher();

    @Override
    protected void doFilterInternal(
            final HttpServletRequest request,
            final HttpServletResponse response,
            final FilterChain filterChain
    ) throws ServletException, IOException {

        final String requestURI = request.getRequestURI();
        if (isExcluded(requestURI)) {
            filterChain.doFilter(request, response);
            return;
        }

        final ContentCachingRequestWrapper cacheRequest = new ContentCachingRequestWrapper(request, 1024 * 1024 * 50); // 50MB까지 캐싱 가능
        final ContentCachingResponseWrapper cacheResponse = new ContentCachingResponseWrapper(response);

        String requestId = resolveRequestId(request);
        MDC.put(REQUEST_ID_MDC_KEY, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        writeRequestLog(cacheRequest);

        // 예외 발생 여부와 관계없이 MDC 컨텍스트가 항상 정리되도록 보장
        try {
            filterChain.doFilter(cacheRequest, cacheResponse);
        } finally {
            writeResponseLog(cacheResponse);
            cacheResponse.copyBodyToResponse();
            MDC.remove(REQUEST_ID_MDC_KEY);
        }
    }

    private String resolveRequestId(HttpServletRequest request) {
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return requestId;
    }

    private void writeRequestLog(final ContentCachingRequestWrapper cacheRequest) {
        final RequestLogMessage requestLog = RequestLogMessage.createInstance(cacheRequest);
        log.info(requestLog.toString());
    }

    private void writeResponseLog(final ContentCachingResponseWrapper cacheResponse) {
        final ResponseLogMessage responseLog = ResponseLogMessage.createInstance(cacheResponse);
        log.info(responseLog.toString());
    }

    private boolean isExcluded(String requestURI) {
        return EXCLUDE_URI.stream()
                .anyMatch(pattern -> antPathMatcher.match(pattern, requestURI));
    }
}
