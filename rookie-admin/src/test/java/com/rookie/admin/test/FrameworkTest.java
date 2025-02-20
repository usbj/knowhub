package com.rookie.admin.test;



import cn.hutool.json.JSONUtil;
import com.rookie.common.cache.RedisCache;
import com.rookie.framework.security.service.TokenService;
import com.rookie.framework.security.mapper.UserInfoMapper;
import com.rookie.framework.security.pojo.UserInfo;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;


@SpringBootTest
public class FrameworkTest {

    @Resource
    PasswordEncoder passwordEncoder;

    @Autowired
    UserInfoMapper userInfoMapper;

    @Value("${redis.base-key}")
    private String BASE_KEY;

    @Autowired
    RedisCache redisCache;

    @Value("${redis.expire-time}")
    private String EXPIRE_TIME;

    @Autowired
    UserDetailsService userDetailsService;

    @Autowired
    TokenService tokenService;

    @Test
    void createAdminPassword(){
//        System.out.println(passwordEncoder.encode("ios507"));
        System.out.println(passwordEncoder.encode("123456"));
    }

    @Test
    void testUserInfoMapper() {
        UserInfo admin = userInfoMapper.selectUserByUsername("admin");
        ArrayList<Integer> roles = userInfoMapper.selectRoleIdByUserId(admin.getUserId());
        ArrayList<Integer> menus = userInfoMapper.selectMenuIdByRoleId(roles);
        ArrayList<String> permKeyById = userInfoMapper.getPermKeyById(menus);
        System.out.println(permKeyById);
    }

    @Test
    void testLombok() {
        UserInfo userInfo = new UserInfo();
        userInfo.setUserId(1L);
        System.out.println(userInfo.getUserId());
    }

    @Test
    void testYmlValue(){
        System.out.println(BASE_KEY);
        System.out.println(EXPIRE_TIME);

    }

    @Test
    void testRedisCache() {
        String admin = JSONUtil.toJsonStr(userInfoMapper.selectUserByUsername("admin"));
        System.out.println(redisCache.setCacheToSetTime("admin", admin));
    }

    @Test
    void testDelete(){
        redisCache.deleteCache("admin");
    }

    @Test
    void testUserDetailService(){
        System.out.println(userDetailsService.loadUserByUsername("admin"));
    }

    @Test
    void testJwtUntil() {
//        String token = tokenService.createJwt("admin", "123456");
//        System.out.println(token);
//        System.out.println(tokenService.jwtVerification(token, "admin", "123456"));
//        System.out.println(tokenService.getUsernameByJwt(token));
    }

}
