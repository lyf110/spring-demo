package cn.lyf.knife4j.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * @author lyf
 * @description
 * @since 2023/5/2 15:07:28
 */
@RestController
@RequestMapping(value = "/hello")
@Api(tags = "hello")
public class HelloController {

    @ApiOperation("测试，返回字符串")
    @GetMapping("/say")
    public String sayHello() {
        return "hello, " + UUID.randomUUID();
    }
}
