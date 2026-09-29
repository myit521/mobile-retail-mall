package com.sky.observability;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class TraceIdFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Trace-Id";
    private static final Pattern VALID_TRACE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{7,63}");
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String supplied = request.getHeader(HEADER);
        String traceId = supplied != null && VALID_TRACE.matcher(supplied).matches() ? supplied : UUID.randomUUID().toString();
        MDC.clear(); MDC.put("traceId", traceId); response.setHeader(HEADER, traceId);
        try { chain.doFilter(request, response); } finally { MDC.clear(); }
    }
}
