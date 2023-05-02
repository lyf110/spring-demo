package cn.lyf.redis.intercept;

import cn.lyf.redis.anno.RequestLimit;
import cn.lyf.redis.common.Result;
import cn.lyf.redis.common.StatusCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static cn.lyf.redis.common.CommonConstant.CONTENT_TYPE_JSON;

/**
 * @author lyf
 * @version 1.0
 * @classname RequestLimitIntercept
 * @description 接口防刷的请求的拦截器
 * @since 2023/4/12 11:49
 */
@Slf4j
@Component
public class RequestLimitIntercept implements HandlerInterceptor {
    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    @Qualifier(value = "jsonRedisTemplate")
    private RedisTemplate<String, Serializable> redisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 判断handler 所表示的对象是否是HandlerMethod对象
        // 我们只拦截HandlerMethod对象及其子类对象
        if (handler.getClass().isAssignableFrom(HandlerMethod.class)) {
            // HandlerMethod封装方法定义相关的信息，如类、方法、参数等
            HandlerMethod handlerMethod = (HandlerMethod) handler;
            Method method = handlerMethod.getMethod();
            // 方法上存在@RequestLimit注解
            RequestLimit requestLimit = null;
            if (method.isAnnotationPresent(RequestLimit.class)) {
                requestLimit = method.getAnnotation(RequestLimit.class);
            } else if (method.getDeclaringClass().isAnnotationPresent(RequestLimit.class)) {
                requestLimit = method.getDeclaringClass().getAnnotation(RequestLimit.class);
            }

            // 即表示存在RequestLimit注解
            if (requestLimit != null) {
                if (isLimit(request, requestLimit)) {
                    writeToResponse(response, Result.error(StatusCode.REQUEST_LIMIT));
                    return false;
                }
            }
        }

        // 不拦截
        return HandlerInterceptor.super.preHandle(request, response, handler);
    }

    /**
     * 将响应写回给浏览器客户端
     *
     * @param response response
     * @param result   结果信息
     * @param <T>      实际类型
     * @throws IOException IOException
     */
    private <T> void writeToResponse(HttpServletResponse response, Result<T> result) throws IOException {
        response.setCharacterEncoding(StandardCharsets.UTF_8.toString());
        response.setContentType(CONTENT_TYPE_JSON);
        String resultJson;
        try {
            resultJson = objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            try {
                resultJson = objectMapper.writeValueAsString(Result.error(StatusCode.FAILURE));
            } catch (JsonProcessingException ex) {
                throw new RuntimeException(ex);
            }
        }

        response.getWriter().write(resultJson);
    }


    /**
     * 判断请求是否受限
     *
     * @param request      请求
     * @param requestLimit 这里会携带两个参数，一个是限定时间，一个是限定次数
     * @return true: 受限，false：不受限
     */
    private boolean isLimit(HttpServletRequest request, RequestLimit requestLimit) {
        // 受限的redis的缓存key，我们使用ip地址或session来做唯一的key
        String remoteAddr = request.getRemoteAddr();
        String limitKey = "request:limit:";
        if (!ObjectUtils.isEmpty(remoteAddr)) { // 此时我们才使用sessionid来作为唯一的key
            remoteAddr = remoteAddr.replace(":", "-");
            limitKey = limitKey + request.getServletPath() + ":" + remoteAddr;
        } else {
            limitKey = limitKey + request.getServletPath() + ":" + request.getSession().getId();
        }

        // 从缓存中获取，当前的这个请求访问了几次
        Serializable redisCount = redisTemplate.opsForValue().get(limitKey);
        // 表示是第一次访问
        if (redisCount == null) {
            redisTemplate.opsForValue().set(limitKey, 1, requestLimit.second(), TimeUnit.SECONDS);
            log.info("第1次访问: {}", limitKey);
        } else {
            int limitCount = (int) redisCount;
            if (limitCount >= requestLimit.maxCount()) {
                log.info("第{}次访问, 访问受限", limitCount + 1);
                return true;
            }
            redisTemplate.opsForValue().increment(limitKey);
            log.info("第{}次访问", limitCount + 1);
        }

        return false;
    }
}
