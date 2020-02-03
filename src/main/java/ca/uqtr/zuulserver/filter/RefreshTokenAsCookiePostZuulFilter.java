package ca.uqtr.zuulserver.filter;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import javax.servlet.http.Cookie;

import io.micrometer.core.instrument.util.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.zuul.ZuulFilter;
import com.netflix.zuul.context.RequestContext;

/*
Type: Zuul filter.
Functionality: Remove the refresh_token from the response and save in a cookies
               Delete the cookies when there is a request to logout.
*/
@Component
public class RefreshTokenAsCookiePostZuulFilter extends ZuulFilter {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public Object run() {
        final RequestContext ctx = RequestContext.getCurrentContext();
        logger.info("in zuul filter " + ctx.getRequest().getRequestURI());

        final String requestURI = ctx.getRequest().getRequestURI();
        final String requestMethod = ctx.getRequest().getMethod();

        try {
            final InputStream is = ctx.getResponseDataStream();
            String responseBody = IOUtils.toString(is, StandardCharsets.UTF_8);
            if (responseBody.contains("refresh_token")) {
                final Map<String, Object> responseMap = mapper.readValue(responseBody, new TypeReference<Map<String, Object>>() {
                });
                final String refreshToken = responseMap.get("refresh_token").toString();
                responseMap.remove("refresh_token");
                responseBody = mapper.writeValueAsString(responseMap);

                final Cookie cookie = new Cookie("refreshToken", refreshToken);
                cookie.setHttpOnly(true);
                // cookie.setSecure(true);
                cookie.setPath(ctx.getRequest().getContextPath() + "/oauth/token");
                cookie.setMaxAge(2592000); // 30 days

                ctx.getResponse().addCookie(cookie);
                logger.info("refresh token = " + refreshToken);

            }
            if (requestURI.contains("logingout") && requestMethod.equals("DELETE")) {
                final Cookie cookie = new Cookie("refreshToken", "");
                cookie.setMaxAge(0);
                cookie.setPath(ctx.getRequest().getContextPath() + "/oauth/token");
                ctx.getResponse().addCookie(cookie);
            }
            ctx.setResponseBody(responseBody);

        } catch (final IOException e) {
            logger.error("Error occurred in zuul post filter", e);
        }
        return null;
    }

    @Override
    public boolean shouldFilter() {
        return true;
    }

    @Override
    public int filterOrder() {
        return 10;
    }

    @Override
    public String filterType() {
        return "post";
    }

}
