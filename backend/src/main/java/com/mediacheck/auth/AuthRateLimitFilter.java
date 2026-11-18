package com.mediacheck.auth;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {
  private static final int LIMIT=30;
  private static final long WINDOW_MILLIS=60_000L;
  private final ConcurrentHashMap<String,Bucket> buckets=new ConcurrentHashMap<>();
  @Override protected boolean shouldNotFilter(HttpServletRequest request){return !request.getRequestURI().startsWith("/api/v1/auth/");}
  @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
    long now=System.currentTimeMillis();String key=request.getRemoteAddr()+":"+request.getRequestURI();
    Bucket bucket=buckets.compute(key,(k,old)->old==null||now-old.startedAt>=WINDOW_MILLIS?new Bucket(now,1):new Bucket(old.startedAt,old.count+1));
    if(buckets.size()>10_000)buckets.entrySet().removeIf(e->now-e.getValue().startedAt>WINDOW_MILLIS*2);
    if(bucket.count>LIMIT){response.setStatus(429);response.setHeader("Retry-After","60");response.setContentType("application/json");response.getWriter().write("{\"error\":\"rate_limited\"}");return;}
    chain.doFilter(request,response);
  }
  private record Bucket(long startedAt,int count){}
}
