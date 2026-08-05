package com.jyinshi.identity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jyinshi.identity.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户数据访问。仅做数据存取，业务逻辑放在 service。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
