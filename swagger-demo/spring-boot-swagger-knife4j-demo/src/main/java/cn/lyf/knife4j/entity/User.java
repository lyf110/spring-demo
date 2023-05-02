package cn.lyf.knife4j.entity;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;


/**
 * @author lyf
 * @description
 * @since 2023/5/2 15:11:18
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@ApiModel(value = "User", description = "用户")
public class User implements Serializable {
    private static final long serialVersionUID = 1L;
    @ApiModelProperty(value = "主键id", example = "1", notes = "主键id，一般由数据库自动生成")
    private Long id;
    @ApiModelProperty(value = "用户姓名", example = "张三", required = true, notes = "用户名")
    private String name;
    @ApiModelProperty(value = "用户性别", example = "男", required = true, notes = "用户性别，男或女")
    private String gender;
    @ApiModelProperty(value = "用户年龄", example = "18", required = true, notes = "用户年龄")
    private Integer age;
    @ApiModelProperty(value = "用户地址", example = "浙江省杭州市上城区八卦新村", required = true, notes = "用户所在地址")
    private String address;
}
