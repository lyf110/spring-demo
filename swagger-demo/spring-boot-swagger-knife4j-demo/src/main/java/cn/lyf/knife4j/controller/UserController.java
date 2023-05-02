package cn.lyf.knife4j.controller;

import cn.lyf.knife4j.entity.User;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * @author lyf
 * @description
 * @since 2023/5/2 15:07:28
 */
@RestController
@RequestMapping(value = "/user")
@Api(tags = "user")
public class UserController {

    @ApiOperation(value = "save", notes = "保存用户")
    @PostMapping(value = "")
    public String save(@RequestBody User user) {
        System.out.println("user=" + user);
        return "保存成功";
    }
}
